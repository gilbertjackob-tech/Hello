export interface Env {
  DB: D1Database;
  TEMP_FILES: R2Bucket;
  REALTIME_ROOM: DurableObjectNamespace;
  ENABLE_DEBUG_BINDINGS?: string;
  ENABLE_DEV_RESET?: string;
  DEV_RESET_SECRET?: string;
  TURN_URLS?: string;
  TURN_USERNAME?: string;
  TURN_CREDENTIAL?: string;
  FIREBASE_PROJECT_ID?: string;
  FIREBASE_CLIENT_EMAIL?: string;
  FIREBASE_PRIVATE_KEY?: string;
  FCM_PROJECT_ID?: string;
  FCM_SERVICE_ACCOUNT_JSON?: string;
}

import * as statusApi from "./status";

type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
type JsonObject = { [key: string]: JsonValue };

const ATTACHMENT_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const ONLINE_TTL_MS = 60 * 1000;
const PENDING_ATTACHMENT_USER_ID = "system_attachment_upload";
const PENDING_ATTACHMENT_CONVERSATION_ID = "system_attachment_uploads";
let cloudSchemaReady: Promise<void> | null = null;
let fcmAccessTokenCache: { token: string; expiresAt: number } | null = null;

function json(body: JsonValue, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  headers.set("content-type", "application/json; charset=utf-8");
  headers.set("access-control-allow-origin", "*");
  headers.set("access-control-allow-methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
  headers.set("access-control-allow-headers", "content-type,authorization,x-dev-reset-secret");
  return new Response(JSON.stringify(body), { ...init, headers });
}

function notFound(pathname: string): Response {
  return json({ ok: false, error: "not_found", path: pathname }, { status: 404 });
}

function badRequest(message: string): Response {
  return json({ ok: false, error: message }, { status: 400 });
}

function randomId(prefix: string): string {
  return `${prefix}_${crypto.randomUUID().replace(/-/g, "")}`;
}

async function readJson(request: Request): Promise<JsonObject> {
  try {
    const body = await request.json();
    return body && typeof body === "object" && !Array.isArray(body) ? (body as JsonObject) : {};
  } catch {
    return {};
  }
}

function asString(value: JsonValue | undefined, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function asStringList(value: JsonValue | undefined): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function directKeyForUsers(userIds: string[]): string | null {
  const uniqueIds = [...new Set(userIds.map((id) => id.trim()).filter(Boolean))].sort();
  return uniqueIds.length === 2 ? uniqueIds.join(":") : null;
}

function asBoolean(value: JsonValue | undefined, fallback = false): boolean {
  return typeof value === "boolean" ? value : fallback;
}

function bearerToken(request: Request): string {
  const header = request.headers.get("authorization") || "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || "";
}

function normalizeSecurityAnswer(value: string): string {
  return value.trim().toLowerCase();
}

async function sha256Hex(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function base64Url(bytes: ArrayBuffer | Uint8Array): string {
  const array = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binary = "";
  for (const byte of array) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function jsonBase64Url(value: JsonObject): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

function privateKeyToArrayBuffer(privateKey: string): ArrayBuffer {
  const normalized = privateKey
    .replace(/\\n/g, "\n")
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes.buffer;
}

function randomToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function hashSecurityAnswer(answer: string, salt = randomToken()): Promise<{ hash: string; salt: string }> {
  return {
    salt,
    hash: await sha256Hex(`${salt}:${normalizeSecurityAnswer(answer)}`),
  };
}

function publicUser(row: any): JsonObject {
  const name = row.displayName || row.name || row.display_name || row.id;
  const avatar = row.avatarUrl || row.avatar || row.avatar_url || null;
  return {
    id: row.id,
    name,
    displayName: name,
    username: row.username || null,
    phone: row.phone || null,
    email: row.email || null,
    avatar,
    avatarUrl: avatar,
    about: row.about || null,
    status: row.profileStatus || row.profile_status || null,
    updatedAt: row.updatedAt || row.updated_at || null,
    lastActive: row.lastActive || row.last_active || row.lastSeenAt || row.last_seen_at || null,
    online: row.online === true || row.online === 1,
  };
}

async function userProfileFor(env: Env, userId: string): Promise<any> {
  return env.DB.prepare(
    `
      SELECT u.id,
        COALESCE(p.display_name, u.display_name) AS displayName,
        p.username,
        p.phone,
        p.email,
        COALESCE(p.avatar_url, u.avatar_url) AS avatarUrl,
        p.about,
        p.profile_status AS profileStatus,
        COALESCE(p.updated_at, u.updated_at) AS updatedAt,
        (
          SELECT MAX(d.last_seen_at)
          FROM devices d
          JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
          WHERE d.user_id = u.id
            AND s.revoked_at IS NULL
            AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
        ) AS lastActive,
        CASE WHEN (
          SELECT MAX(d.last_seen_at)
          FROM devices d
          JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
          WHERE d.user_id = u.id
            AND s.revoked_at IS NULL
            AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
        ) > ? THEN 1 ELSE 0 END AS online
      FROM users u
      LEFT JOIN user_profiles p ON p.user_id = u.id
      WHERE u.id = ?
    `,
  ).bind(Date.now() - ONLINE_TTL_MS, userId).first<any>();
}

async function ensureUserProfile(env: Env, userId: string, displayName: string, avatarUrl?: string | null): Promise<void> {
  const now = Date.now();
  await env.DB.prepare(
    `
      INSERT INTO user_profiles (user_id, display_name, avatar_url, updated_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(user_id) DO UPDATE SET
        display_name = COALESCE(excluded.display_name, user_profiles.display_name),
        avatar_url = COALESCE(excluded.avatar_url, user_profiles.avatar_url),
        updated_at = excluded.updated_at
    `,
  ).bind(userId, displayName || null, avatarUrl || null, now).run();
}

async function createSession(env: Env, userId: string, body: JsonObject = {}): Promise<{ token: string; session: JsonObject }> {
  const now = Date.now();
  const token = randomToken();
  const sessionId = randomId("sess");
  const device = body.device && typeof body.device === "object" && !Array.isArray(body.device)
    ? body.device as JsonObject
    : {};
  const deviceId = asString(body.deviceId || device.id) || randomId("dev");
  const deviceName = asString(body.deviceName || device.name, "");
  const platform = asString(body.platform || device.platform, "");
  const expiresAt = now + 90 * 24 * 60 * 60 * 1000;
  await env.DB.prepare(
    `
      INSERT INTO devices (id, user_id, name, platform, created_at, last_seen_at)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        user_id = excluded.user_id,
        name = COALESCE(NULLIF(excluded.name, ''), devices.name),
        platform = COALESCE(NULLIF(excluded.platform, ''), devices.platform),
        last_seen_at = excluded.last_seen_at
    `,
  ).bind(deviceId, userId, deviceName || null, platform || null, now, now).run();
  await env.DB.prepare(
    `
      INSERT INTO sessions (id, user_id, token_hash, device_id, created_at, expires_at)
      VALUES (?, ?, ?, ?, ?, ?)
    `,
  ).bind(sessionId, userId, await sha256Hex(token), deviceId, now, expiresAt).run();
  return {
    token,
    session: { id: sessionId, userId, deviceId, createdAt: now, expiresAt },
  };
}

type AuthContext = { userId: string; sessionId: string; deviceId: string | null };

async function authenticatedUser(env: Env, request: Request): Promise<AuthContext | null> {
  const token = bearerToken(request);
  if (!token) return null;
  const now = Date.now();
  const row = await env.DB.prepare(
    `
      SELECT id, user_id AS userId, device_id AS deviceId
      FROM sessions
      WHERE token_hash = ?
        AND revoked_at IS NULL
        AND (expires_at IS NULL OR expires_at > ?)
    `,
  ).bind(await sha256Hex(token), now).first<any>();
  return row ? { userId: row.userId, sessionId: row.id, deviceId: row.deviceId || null } : null;
}

async function requireAuth(env: Env, request: Request): Promise<AuthContext | Response> {
  const auth = await authenticatedUser(env, request);
  return auth || json({ ok: false, error: "Unauthorized" }, { status: 401 });
}

async function ensureColumn(env: Env, table: string, column: string, definition: string): Promise<void> {
  const info = await env.DB.prepare(`PRAGMA table_info(${table})`).all<any>();
  const hasColumn = (info.results || []).some((row: any) => row.name === column);
  if (!hasColumn) {
    await env.DB.prepare(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`).run();
  }
}

async function ensureCloudAccountSchema(env: Env): Promise<void> {
  if (!cloudSchemaReady) {
    cloudSchemaReady = (async () => {
      await ensureColumn(env, "users", "security_question", "TEXT").catch(() => undefined);
      await ensureColumn(env, "users", "security_answer", "TEXT").catch(() => undefined);
      await ensureColumn(env, "users", "security_answer_hash", "TEXT").catch(() => undefined);
      await ensureColumn(env, "users", "security_answer_salt", "TEXT").catch(() => undefined);
      await ensureColumn(env, "conversations", "direct_key", "TEXT").catch(() => undefined);
      await env.DB.prepare(
        `
          CREATE INDEX IF NOT EXISTS idx_conversations_direct_key
          ON conversations (direct_key)
          WHERE type = 'direct' AND direct_key IS NOT NULL
        `,
      ).run().catch(() => undefined);
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS sessions (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            token_hash TEXT NOT NULL UNIQUE,
            device_id TEXT,
            created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            expires_at INTEGER,
            revoked_at INTEGER
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS devices (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            name TEXT,
            platform TEXT,
            created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            last_seen_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS contacts (
            owner_user_id TEXT NOT NULL,
            contact_user_id TEXT NOT NULL,
            alias TEXT,
            created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            PRIMARY KEY (owner_user_id, contact_user_id)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS user_profiles (
            user_id TEXT PRIMARY KEY,
            display_name TEXT,
            username TEXT,
            phone TEXT,
            email TEXT,
            avatar_url TEXT,
            about TEXT,
            profile_status TEXT,
            updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS user_chat_preferences (
            user_id TEXT PRIMARY KEY,
            read_receipts_enabled INTEGER NOT NULL DEFAULT 1,
            notifications_enabled INTEGER NOT NULL DEFAULT 1,
            updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS message_reactions (
            message_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            emoji TEXT NOT NULL,
            created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            PRIMARY KEY (message_id, user_id, emoji),
            FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE INDEX IF NOT EXISTS idx_message_reactions_message
          ON message_reactions (message_id, created_at)
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS conversation_preferences (
            user_id TEXT NOT NULL,
            conversation_id TEXT NOT NULL,
            muted_until INTEGER,
            pinned INTEGER NOT NULL DEFAULT 0,
            archived INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            PRIMARY KEY (user_id, conversation_id)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS call_sessions (
            id TEXT PRIMARY KEY,
            chat_id TEXT NOT NULL,
            caller_user_id TEXT NOT NULL,
            receiver_user_id TEXT NOT NULL,
            type TEXT NOT NULL DEFAULT 'audio',
            status TEXT NOT NULL DEFAULT 'ringing',
            started_at INTEGER NOT NULL,
            answered_at INTEGER,
            ended_at INTEGER,
            end_reason TEXT,
            created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
          )
        `,
      ).run();
      await ensureColumn(env, "call_sessions", "mode", "TEXT NOT NULL DEFAULT 'direct'").catch(() => undefined);
      await ensureColumn(env, "call_sessions", "max_participants", "INTEGER NOT NULL DEFAULT 2").catch(() => undefined);
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS call_participants (
            call_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            role TEXT NOT NULL,
            joined_at INTEGER,
            left_at INTEGER,
            PRIMARY KEY (call_id, user_id)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS call_events (
            id TEXT PRIMARY KEY,
            call_id TEXT NOT NULL,
            event_type TEXT NOT NULL,
            sender_user_id TEXT,
            receiver_user_id TEXT,
            payload_json TEXT NOT NULL,
            created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS device_push_tokens (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            token TEXT NOT NULL,
            platform TEXT,
            device_name TEXT,
            created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
            UNIQUE (user_id, token)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE TABLE IF NOT EXISTS notification_events (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            type TEXT NOT NULL,
            channel TEXT NOT NULL,
            priority TEXT NOT NULL,
            collapse_key TEXT,
            payload_json TEXT NOT NULL,
            delivery_status TEXT NOT NULL DEFAULT 'realtime_only',
            created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
          )
        `,
      ).run();
      await env.DB.prepare(
        `
          CREATE INDEX IF NOT EXISTS idx_notification_events_user_created
          ON notification_events (user_id, created_at DESC)
        `,
      ).run();
    })();
  }
  return cloudSchemaReady;
}

async function userFor(env: Env, userId: string): Promise<any> {
  const row = await userProfileFor(env, userId);
  return row ? publicUser(row) : null;
}

async function touchPresence(env: Env, auth: AuthContext, at = Date.now()): Promise<void> {
  if (auth.deviceId) {
    await env.DB.prepare("UPDATE devices SET last_seen_at = ? WHERE id = ? AND user_id = ?")
      .bind(at, auth.deviceId, auth.userId)
      .run()
      .catch(() => undefined);
    return;
  }
  await env.DB.prepare("UPDATE devices SET last_seen_at = ? WHERE user_id = ?")
    .bind(at, auth.userId)
    .run()
    .catch(() => undefined);
}

async function userPresencePayload(env: Env, userId: string, online: boolean, at = Date.now()): Promise<JsonObject> {
  const user = await userFor(env, userId);
  return {
    ...(user || { id: userId, name: userId, displayName: userId }),
    id: userId,
    userId,
    online,
    lastActive: at,
    at,
  };
}

async function allUserIds(env: Env): Promise<string[]> {
  const rows = await env.DB.prepare("SELECT id FROM users").all<any>();
  return (rows.results || []).map((row: any) => row.id).filter(Boolean);
}

async function recentOnlineUserIds(env: Env): Promise<string[]> {
  const rows = await env.DB.prepare(
    `
      SELECT DISTINCT d.user_id AS userId
      FROM devices d
      JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
      WHERE d.last_seen_at > ?
        AND s.revoked_at IS NULL
        AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
    `,
  ).bind(Date.now() - ONLINE_TTL_MS).all<any>();
  return (rows.results || []).map((row: any) => row.userId).filter(Boolean);
}

async function broadcastUserPresence(env: Env, userId: string, online: boolean): Promise<void> {
  const at = Date.now();
  const payload = await userPresencePayload(env, userId, online, at);
  const recipients = await allUserIds(env);
  await Promise.all(recipients.map(async (recipientId) => {
    await broadcastToDurableUser(env, recipientId, "user_presence", payload);
    await broadcastToDurableUser(env, recipientId, "user_updated", payload);
    await broadcastToDurableUser(env, recipientId, "presence_updated", payload);
  }));
}

async function broadcastUserProfileUpdate(env: Env, userId: string): Promise<void> {
  const payload = await userPresencePayload(env, userId, true);
  const recipients = await allUserIds(env);
  await Promise.all(recipients.map(async (recipientId) => {
    await broadcastToDurableUser(env, recipientId, "user_updated", payload);
    await broadcastToDurableUser(env, recipientId, "presence_updated", payload);
  }));
}

async function getChatUser(env: Env, userId: string): Promise<Response> {
  const user = await userFor(env, userId);
  if (!user) return json({ ok: false, error: "User not found" }, { status: 404 });
  return json({
    ...user,
    privacy: "everyone",
  });
}

async function uploadUserAvatar(env: Env, request: Request, pathUserId = ""): Promise<Response> {
  const authenticated = await authenticatedUser(env, request);
  const form = await request.formData();
  const userId = (pathUserId || asString((form.get("userId") as string | null) || "")).trim();
  const fileEntry = form.get("file");
  if (!userId) return badRequest("userId is required");
  if (authenticated && authenticated.userId !== userId) {
    return json({ ok: false, error: "Cannot update another user avatar" }, { status: 403 });
  }
  if (!fileEntry || typeof fileEntry === "string") return badRequest("file is required");

  const file = fileEntry as File;
  if (!file.type.startsWith("image/")) return badRequest("avatar must be an image");

  const extension = file.name.includes(".") ? file.name.split(".").pop() : "jpg";
  const key = `avatars/${userId}/profile.${extension}`;
  await env.TEMP_FILES.put(key, file.stream(), {
    httpMetadata: { contentType: file.type || "image/jpeg" },
  });

  const now = Date.now();
  const url = new URL(request.url);
  const avatarUrl = `${url.origin}/api/users/${encodeURIComponent(userId)}/avatar?v=${now}`;
  await env.DB.prepare(
    `
      UPDATE users
      SET avatar_url = ?, updated_at = ?
      WHERE id = ?
    `,
  ).bind(avatarUrl, now, userId).run();
  await ensureUserProfile(env, userId, "", avatarUrl);

  const user = await userFor(env, userId);
  if (!user) return json({ ok: false, error: "User not found" }, { status: 404 });
  await broadcastUserProfileUpdate(env, userId);
  return json({
    id: user.id,
    name: user.name,
    avatar: avatarUrl,
    avatarUrl,
  });
}

async function fetchUserAvatar(env: Env, userId: string): Promise<Response> {
  const prefix = `avatars/${userId}/`;
  const listed = await env.TEMP_FILES.list({ prefix, limit: 1 });
  const key = listed.objects[0]?.key;
  if (!key) return notFound(`/api/chat/users/${userId}/avatar`);
  const object = await env.TEMP_FILES.get(key);
  if (!object) return notFound(`/api/chat/users/${userId}/avatar`);
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("cache-control", "public, max-age=3600");
  headers.set("access-control-allow-origin", "*");
  return new Response(object.body, { headers });
}

async function fetchThemeAsset(env: Env, fileName: string, headOnly = false): Promise<Response> {
  const safeName = fileName.replace(/[^a-z0-9_.-]/gi, "").toLowerCase();
  if (!safeName || !safeName.endsWith(".png")) return notFound(`/api/theme-assets/${fileName}`);
  const key = `themes/${safeName}`;
  const object = await env.TEMP_FILES.get(key);
  if (!object) return notFound(`/api/theme-assets/${safeName}`);
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("content-type", headers.get("content-type") || "image/png");
  headers.set("cache-control", "public, max-age=31536000, immutable");
  headers.set("access-control-allow-origin", "*");
  return new Response(headOnly ? null : object.body, { headers });
}

async function conversationFor(env: Env, conversationId: string, viewerId?: string): Promise<JsonObject | null> {
  const row = await env.DB.prepare(
    `
      SELECT c.id, c.type, c.title, c.direct_key AS directKey, c.updated_at AS updatedAt,
        (
          SELECT body FROM messages
          WHERE conversation_id = c.id AND deleted_at IS NULL
          ORDER BY created_at DESC
          LIMIT 1
        ) AS lastMessage,
        (
          SELECT created_at FROM messages
          WHERE conversation_id = c.id AND deleted_at IS NULL
          ORDER BY created_at DESC
          LIMIT 1
        ) AS lastMessageTime
      FROM conversations c
      WHERE c.id = ?
    `,
  ).bind(conversationId).first<any>();
  if (!row) return null;

  const members = await env.DB.prepare(
    `
      SELECT u.id, u.display_name AS name, u.avatar_url AS avatar
        , (
          SELECT MAX(d.last_seen_at)
          FROM devices d
          JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
          WHERE d.user_id = u.id
            AND s.revoked_at IS NULL
            AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
        ) AS lastActive
        , CASE WHEN (
          SELECT MAX(d.last_seen_at)
          FROM devices d
          JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
          WHERE d.user_id = u.id
            AND s.revoked_at IS NULL
            AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
        ) > ? THEN 1 ELSE 0 END AS online
      FROM conversation_members cm
      JOIN users u ON u.id = cm.user_id
      WHERE cm.conversation_id = ?
      ORDER BY cm.joined_at ASC
    `,
  ).bind(Date.now() - ONLINE_TTL_MS, conversationId).all<any>();
  const participants = members.results || [];
  const unread = viewerId
    ? await env.DB.prepare(
        `
          SELECT COUNT(*) AS total
          FROM messages m
          LEFT JOIN message_receipts r ON r.message_id = m.id AND r.user_id = ?
          WHERE m.conversation_id = ?
            AND m.sender_id != ?
            AND m.deleted_at IS NULL
            AND r.read_at IS NULL
        `,
      ).bind(viewerId, conversationId, viewerId).first<{ total: number }>()
    : null;

  const directOther = row.type === "direct" && viewerId
    ? participants.find((member: any) => member.id !== viewerId)
    : null;
  const directKey = row.directKey || (row.type === "direct" ? directKeyForUsers(participants.map((member: any) => member.id)) : null);
  if (row.type === "direct" && directKey && !row.directKey) {
    await env.DB.prepare("UPDATE conversations SET direct_key = ? WHERE id = ? AND direct_key IS NULL")
      .bind(directKey, conversationId)
      .run()
      .catch(() => undefined);
  }

  return {
    id: row.id,
    type: row.type,
    directKey,
    name: row.title || directOther?.name || participants.map((member: any) => member.name).join(", ") || "Cloud chat",
    avatar: directOther?.avatar || null,
    lastMessage: row.lastMessage || "",
    lastMessageTime: row.lastMessageTime || row.updatedAt,
    unreadCount: unread?.total || 0,
    isGroup: row.type === "group",
    members: participants.map((member: any) => member.id),
    participants: participants.map((member: any) => ({ ...member, online: member.online === 1, lastActive: member.lastActive || null })),
  };
}

async function reactionsForMessages(env: Env, messageIds: string[]): Promise<Record<string, JsonObject[]>> {
  if (messageIds.length === 0) return {};
  const placeholders = messageIds.map(() => "?").join(",");
  const rows = await env.DB.prepare(
    `
      SELECT message_id AS messageId, user_id AS userId, emoji, created_at AS timestamp
      FROM message_reactions
      WHERE message_id IN (${placeholders})
      ORDER BY created_at ASC
    `,
  ).bind(...messageIds).all<any>();
  const grouped: Record<string, JsonObject[]> = {};
  for (const row of rows.results || []) {
    const messageId = String(row.messageId || "");
    if (!messageId) continue;
    if (!grouped[messageId]) grouped[messageId] = [];
    grouped[messageId].push({
      emoji: String(row.emoji || ""),
      userId: String(row.userId || ""),
      timestamp: Number(row.timestamp || 0),
    });
  }
  return grouped;
}

async function receiptStatusForMessages(env: Env, messageIds: string[]): Promise<Record<string, "sent" | "delivered" | "read">> {
  if (messageIds.length === 0) return {};
  const placeholders = messageIds.map(() => "?").join(",");
  const rows = await env.DB.prepare(
    `
      SELECT m.id AS messageId,
        SUM(CASE WHEN cm.user_id != m.sender_id THEN 1 ELSE 0 END) AS recipientCount,
        SUM(CASE WHEN cm.user_id != m.sender_id AND r.delivered_at IS NOT NULL THEN 1 ELSE 0 END) AS deliveredCount,
        SUM(CASE WHEN cm.user_id != m.sender_id AND r.read_at IS NOT NULL THEN 1 ELSE 0 END) AS readCount
      FROM messages m
      JOIN conversation_members cm ON cm.conversation_id = m.conversation_id
      LEFT JOIN message_receipts r ON r.message_id = m.id AND r.user_id = cm.user_id
      WHERE m.id IN (${placeholders})
      GROUP BY m.id
    `,
  ).bind(...messageIds).all<any>();
  const statuses: Record<string, "sent" | "delivered" | "read"> = {};
  for (const row of rows.results || []) {
    const messageId = String(row.messageId || "");
    if (!messageId) continue;
    const recipientCount = Number(row.recipientCount || 0);
    const deliveredCount = Number(row.deliveredCount || 0);
    const readCount = Number(row.readCount || 0);
    statuses[messageId] = recipientCount > 0 && readCount >= recipientCount
      ? "read"
      : recipientCount > 0 && deliveredCount >= recipientCount
        ? "delivered"
        : "sent";
  }
  return statuses;
}

async function mapMessageRows(env: Env, rows: any[]): Promise<JsonObject[]> {
  const messageIds = rows.map((row) => String(row.id));
  const [reactionsByMessage, statusesByMessage] = await Promise.all([
    reactionsForMessages(env, messageIds),
    receiptStatusForMessages(env, messageIds),
  ]);
  return rows.map((row: any) => ({
    id: row.id,
    chatId: row.chatId,
    senderId: row.senderId,
    senderName: row.senderName,
    senderAvatar: row.senderAvatar || null,
    text: row.deletedAt ? "" : row.text,
    timestamp: row.timestamp,
    attachmentUrl: row.attachmentId ? `/api/chat/attachments/${encodeURIComponent(row.attachmentId)}` : null,
    attachmentType: row.attachmentMimeType?.startsWith("image/") ? "image" : row.attachmentId ? "file" : null,
    attachmentName: row.attachmentName || null,
    attachmentSize: row.attachmentSize || null,
    status: statusesByMessage[String(row.id)] || "sent",
    isDeleted: !!row.deletedAt,
    reactions: reactionsByMessage[String(row.id)] || [],
  }));
}

async function messagesFor(env: Env, conversationId: string, limit = 50, offset = 0): Promise<JsonObject[]> {
  const rows = await env.DB.prepare(
    `
      SELECT m.id, m.conversation_id AS chatId, m.sender_id AS senderId,
        COALESCE(u.display_name, m.sender_id) AS senderName,
        u.avatar_url AS senderAvatar,
        COALESCE(m.body, '') AS text,
        m.message_type AS messageType,
        m.created_at AS timestamp,
        m.deleted_at AS deletedAt,
        a.id AS attachmentId,
        a.file_name AS attachmentName,
        a.mime_type AS attachmentMimeType,
        a.size_bytes AS attachmentSize
      FROM messages m
      LEFT JOIN users u ON u.id = m.sender_id
      LEFT JOIN attachments a ON a.message_id = m.id
      WHERE m.conversation_id = ?
      ORDER BY m.created_at DESC
      LIMIT ? OFFSET ?
    `,
  ).bind(conversationId, limit, offset).all<any>();

  return mapMessageRows(env, rows.results ? [...rows.results].reverse() : []);
}

async function messageFor(env: Env, messageId: string): Promise<JsonObject | null> {
  const row = await env.DB.prepare(
    `
      SELECT m.id, m.conversation_id AS chatId, m.sender_id AS senderId,
        COALESCE(u.display_name, m.sender_id) AS senderName,
        u.avatar_url AS senderAvatar,
        COALESCE(m.body, '') AS text,
        m.message_type AS messageType,
        m.created_at AS timestamp,
        m.deleted_at AS deletedAt,
        a.id AS attachmentId,
        a.file_name AS attachmentName,
        a.mime_type AS attachmentMimeType,
        a.size_bytes AS attachmentSize
      FROM messages m
      LEFT JOIN users u ON u.id = m.sender_id
      LEFT JOIN attachments a ON a.message_id = m.id
      WHERE m.id = ?
      LIMIT 1
    `,
  ).bind(messageId).first<any>();
  if (!row) return null;
  return (await mapMessageRows(env, [row]))[0] || null;
}

async function upsertUser(env: Env, body: JsonObject): Promise<Response> {
  const id = asString(body.id || body.userId);
  const name = asString(body.displayName || body.name, id);
  const avatar = asString(body.avatarUrl || body.avatar, "");
  if (!id || !name) return badRequest("id and displayName/name are required");
  const now = Date.now();
  await env.DB.prepare(
    `
      INSERT INTO users (id, display_name, avatar_url, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        display_name = excluded.display_name,
        avatar_url = excluded.avatar_url,
        updated_at = excluded.updated_at
    `,
  ).bind(id, name, avatar || null, now, now).run();
  await ensureUserProfile(env, id, name, avatar || null);
  const user = await userFor(env, id);
  return json(user || { id, name, displayName: name, avatar: avatar || null, avatarUrl: avatar || null });
}

async function registerUser(env: Env, body: JsonObject, request?: Request): Promise<Response> {
  const name = asString(body.name).trim();
  const securityQuestion = asString(body.securityQuestion).trim();
  const securityAnswer = asString(body.securityAnswer).trim();
  if (!name || !securityQuestion || !securityAnswer) return badRequest("Missing fields");

  const existing = await env.DB.prepare(
    `
      SELECT id, security_answer AS securityAnswer, security_answer_hash AS securityAnswerHash
      FROM users
      WHERE LOWER(display_name) = LOWER(?)
    `,
  ).bind(name).first();

  if (existing && (asString((existing as any).securityAnswer) || asString((existing as any).securityAnswerHash))) {
    return json({ ok: false, error: "Username taken" }, { status: 400 });
  }

  const id = existing ? asString((existing as any).id) : randomId("usr");
  const avatar = `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(name)}`;
  const now = Date.now();
  const answerHash = await hashSecurityAnswer(securityAnswer);
  if (existing) {
    await env.DB.prepare(
      `
        UPDATE users
        SET display_name = ?, avatar_url = COALESCE(avatar_url, ?),
          security_question = ?, security_answer = NULL,
          security_answer_hash = ?, security_answer_salt = ?, updated_at = ?
        WHERE id = ?
      `,
    ).bind(name, avatar, securityQuestion, answerHash.hash, answerHash.salt, now, id).run();
  } else {
    await env.DB.prepare(
      `
        INSERT INTO users (
          id, display_name, avatar_url, security_question, security_answer,
          security_answer_hash, security_answer_salt, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?)
      `,
    ).bind(id, name, avatar, securityQuestion, answerHash.hash, answerHash.salt, now, now).run();
  }
  await ensureUserProfile(env, id, name, avatar);

  const user = await userFor(env, id);
  const session = await createSession(env, id, body);
  return json({ user, token: session.token, session: session.session, ...(user || {}) }, { status: 201 });
}

async function getUserQuestion(env: Env, url: URL): Promise<Response> {
  const name = (url.searchParams.get("name") || "").trim();
  if (!name) return badRequest("Username required");
  const user = await env.DB.prepare(
    "SELECT security_question AS securityQuestion FROM users WHERE LOWER(display_name) = LOWER(?)",
  ).bind(name).first<any>();
  if (!user) return json({ ok: false, error: "User not found" }, { status: 404 });
  if (!asString(user.securityQuestion).trim()) {
    return json({ ok: false, error: "User needs registration" }, { status: 404 });
  }
  return json({ securityQuestion: user.securityQuestion });
}

async function loginUser(env: Env, body: JsonObject): Promise<Response> {
  const name = asString(body.name).trim();
  const securityAnswer = asString(body.securityAnswer).trim();
  if (!name || !securityAnswer) return badRequest("Missing fields");
  const user = await env.DB.prepare(
    `
      SELECT id, display_name AS name, avatar_url AS avatar, security_question AS securityQuestion,
        security_answer AS securityAnswer,
        security_answer_hash AS securityAnswerHash,
        security_answer_salt AS securityAnswerSalt
      FROM users
      WHERE LOWER(display_name) = LOWER(?)
    `,
  ).bind(name).first<any>();
  if (!user) return json({ ok: false, error: "User not found" }, { status: 404 });

  let verified = false;
  if (asString(user.securityAnswerHash) && asString(user.securityAnswerSalt)) {
    const candidate = await hashSecurityAnswer(securityAnswer, asString(user.securityAnswerSalt));
    verified = candidate.hash === asString(user.securityAnswerHash);
  } else if (asString(user.securityAnswer)) {
    verified = normalizeSecurityAnswer(asString(user.securityAnswer)) === normalizeSecurityAnswer(securityAnswer);
    if (verified) {
      const upgraded = await hashSecurityAnswer(securityAnswer);
      await env.DB.prepare(
        `
          UPDATE users
          SET security_answer = NULL, security_answer_hash = ?, security_answer_salt = ?, updated_at = ?
          WHERE id = ?
        `,
      ).bind(upgraded.hash, upgraded.salt, Date.now(), user.id).run();
    }
  }
  if (!verified) return json({ ok: false, error: "Incorrect answer" }, { status: 401 });

  const publicProfile = await userFor(env, user.id);
  const session = await createSession(env, user.id, body);
  return json({ user: publicProfile, token: session.token, session: session.session, ...(publicProfile || {}) });
}

async function listUsers(env: Env, url: URL): Promise<Response> {
  const query = (url.searchParams.get("q") || "").trim();
  const statement = query
    ? env.DB.prepare(
        `
          SELECT u.id, COALESCE(p.display_name, u.display_name) AS displayName,
            p.username, p.phone, p.email, COALESCE(p.avatar_url, u.avatar_url) AS avatarUrl,
            p.about, p.profile_status AS profileStatus, COALESCE(p.updated_at, u.updated_at) AS updatedAt,
            (
              SELECT MAX(d.last_seen_at)
              FROM devices d
              JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
              WHERE d.user_id = u.id
                AND s.revoked_at IS NULL
                AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
            ) AS lastActive,
            CASE WHEN (
              SELECT MAX(d.last_seen_at)
              FROM devices d
              JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
              WHERE d.user_id = u.id
                AND s.revoked_at IS NULL
                AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
            ) > ? THEN 1 ELSE 0 END AS online
          FROM users u
          LEFT JOIN user_profiles p ON p.user_id = u.id
          WHERE LOWER(COALESCE(p.display_name, u.display_name)) LIKE LOWER(?)
             OR LOWER(COALESCE(p.username, '')) LIKE LOWER(?)
          ORDER BY COALESCE(p.display_name, u.display_name) ASC
          LIMIT 50
        `,
      ).bind(Date.now() - ONLINE_TTL_MS, `%${query}%`, `%${query}%`)
    : env.DB.prepare(
        `
          SELECT u.id, COALESCE(p.display_name, u.display_name) AS displayName,
            p.username, p.phone, p.email, COALESCE(p.avatar_url, u.avatar_url) AS avatarUrl,
            p.about, p.profile_status AS profileStatus, COALESCE(p.updated_at, u.updated_at) AS updatedAt,
            (
              SELECT MAX(d.last_seen_at)
              FROM devices d
              JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
              WHERE d.user_id = u.id
                AND s.revoked_at IS NULL
                AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
            ) AS lastActive,
            CASE WHEN (
              SELECT MAX(d.last_seen_at)
              FROM devices d
              JOIN sessions s ON s.device_id = d.id AND s.user_id = d.user_id
              WHERE d.user_id = u.id
                AND s.revoked_at IS NULL
                AND (s.expires_at IS NULL OR s.expires_at > unixepoch() * 1000)
            ) > ? THEN 1 ELSE 0 END AS online
          FROM users u
          LEFT JOIN user_profiles p ON p.user_id = u.id
          ORDER BY COALESCE(p.display_name, u.display_name) ASC
          LIMIT 50
        `,
      ).bind(Date.now() - ONLINE_TTL_MS);
  const rows = await statement.all<any>();
  return json((rows.results || []).map(publicUser));
}

async function logoutUser(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  await env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE id = ?").bind(Date.now(), auth.sessionId).run();
  await broadcastUserPresence(env, auth.userId, false);
  return json({ ok: true });
}

async function currentUser(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const user = await userFor(env, auth.userId);
  if (!user) return json({ ok: false, error: "User not found" }, { status: 404 });
  return json({ user, session: { id: auth.sessionId, userId: auth.userId }, ...user });
}

async function updateUserProfile(env: Env, request: Request, userId: string): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  if (auth.userId !== userId) return json({ ok: false, error: "Cannot update another user" }, { status: 403 });
  const body = await readJson(request);
  const current = await userFor(env, userId);
  if (!current) return json({ ok: false, error: "User not found" }, { status: 404 });
  const now = Date.now();
  const displayName = asString(body.displayName || body.name, asString(current.name));
  const username = asString(body.username, "");
  const phone = asString(body.phone, "");
  const email = asString(body.email, "");
  const avatarUrl = asString(body.avatarUrl || body.avatar, "");
  const about = asString(body.about, "");
  const profileStatus = asString(body.status || body.profileStatus, "");
  await env.DB.prepare(
    `
      UPDATE users
      SET display_name = ?, avatar_url = COALESCE(NULLIF(?, ''), avatar_url), updated_at = ?
      WHERE id = ?
    `,
  ).bind(displayName, avatarUrl, now, userId).run();
  await env.DB.prepare(
    `
      INSERT INTO user_profiles (
        user_id, display_name, username, phone, email, avatar_url, about, profile_status, updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(user_id) DO UPDATE SET
        display_name = excluded.display_name,
        username = COALESCE(NULLIF(excluded.username, ''), user_profiles.username),
        phone = COALESCE(NULLIF(excluded.phone, ''), user_profiles.phone),
        email = COALESCE(NULLIF(excluded.email, ''), user_profiles.email),
        avatar_url = COALESCE(NULLIF(excluded.avatar_url, ''), user_profiles.avatar_url),
        about = COALESCE(NULLIF(excluded.about, ''), user_profiles.about),
        profile_status = COALESCE(NULLIF(excluded.profile_status, ''), user_profiles.profile_status),
        updated_at = excluded.updated_at
    `,
  ).bind(
    userId,
    displayName,
    username,
    phone,
    email,
    avatarUrl,
    about,
    profileStatus,
    now,
  ).run();
  await broadcastUserProfileUpdate(env, userId);
  return json(await userFor(env, userId));
}

async function listContacts(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const rows = await env.DB.prepare(
    `
      SELECT c.alias, c.created_at AS createdAt, c.updated_at AS updatedAt,
        u.id, COALESCE(p.display_name, u.display_name) AS displayName,
        p.username, p.phone, p.email, COALESCE(p.avatar_url, u.avatar_url) AS avatarUrl,
        p.about, p.profile_status AS profileStatus
      FROM contacts c
      JOIN users u ON u.id = c.contact_user_id
      LEFT JOIN user_profiles p ON p.user_id = u.id
      WHERE c.owner_user_id = ?
      ORDER BY COALESCE(c.alias, p.display_name, u.display_name) ASC
    `,
  ).bind(auth.userId).all<any>();
  return json((rows.results || []).map((row: any) => ({
    ...publicUser(row),
    alias: row.alias || null,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  })));
}

async function addContact(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const body = await readJson(request);
  const byId = asString(body.contactUserId || body.userId || body.id, "");
  const byName = asString(body.name || body.username, "");
  const contact = byId
    ? await userProfileFor(env, byId)
    : await env.DB.prepare(
        `
          SELECT u.id, COALESCE(p.display_name, u.display_name) AS displayName,
            p.username, p.phone, p.email, COALESCE(p.avatar_url, u.avatar_url) AS avatarUrl,
            p.about, p.profile_status AS profileStatus, COALESCE(p.updated_at, u.updated_at) AS updatedAt
          FROM users u
          LEFT JOIN user_profiles p ON p.user_id = u.id
          WHERE LOWER(COALESCE(p.display_name, u.display_name)) = LOWER(?)
             OR LOWER(COALESCE(p.username, '')) = LOWER(?)
        `,
      ).bind(byName, byName).first<any>();
  if (!contact) return json({ ok: false, error: "Contact user not found" }, { status: 404 });
  if (contact.id === auth.userId) return badRequest("Cannot add yourself as a contact");
  const now = Date.now();
  await env.DB.prepare(
    `
      INSERT INTO contacts (owner_user_id, contact_user_id, alias, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(owner_user_id, contact_user_id) DO UPDATE SET
        alias = COALESCE(excluded.alias, contacts.alias),
        updated_at = excluded.updated_at
    `,
  ).bind(auth.userId, contact.id, asString(body.alias, "") || null, now, now).run();
  return json(publicUser(contact), { status: 201 });
}

async function getChatPreferences(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const userPrefs = await env.DB.prepare(
    `
      SELECT read_receipts_enabled AS readReceiptsEnabled,
        notifications_enabled AS notificationsEnabled,
        updated_at AS updatedAt
      FROM user_chat_preferences
      WHERE user_id = ?
    `,
  ).bind(auth.userId).first<any>();
  const conversationPrefs = await env.DB.prepare(
    `
      SELECT conversation_id AS conversationId, muted_until AS mutedUntil,
        pinned, archived, updated_at AS updatedAt
      FROM conversation_preferences
      WHERE user_id = ?
    `,
  ).bind(auth.userId).all<any>();
  return json({
    readReceiptsEnabled: userPrefs ? userPrefs.readReceiptsEnabled === 1 : true,
    notificationsEnabled: userPrefs ? userPrefs.notificationsEnabled === 1 : true,
    updatedAt: userPrefs?.updatedAt || null,
    conversations: (conversationPrefs.results || []).map((row: any) => ({
      conversationId: row.conversationId,
      mutedUntil: row.mutedUntil || null,
      pinned: row.pinned === 1,
      archived: row.archived === 1,
      updatedAt: row.updatedAt,
    })),
  });
}

async function updateChatPreferences(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const body = await readJson(request);
  const now = Date.now();
  const readReceiptsEnabled = asBoolean(body.readReceiptsEnabled, true) ? 1 : 0;
  const notificationsEnabled = asBoolean(body.notificationsEnabled, true) ? 1 : 0;
  await env.DB.prepare(
    `
      INSERT INTO user_chat_preferences (user_id, read_receipts_enabled, notifications_enabled, updated_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(user_id) DO UPDATE SET
        read_receipts_enabled = excluded.read_receipts_enabled,
        notifications_enabled = excluded.notifications_enabled,
        updated_at = excluded.updated_at
    `,
  ).bind(auth.userId, readReceiptsEnabled, notificationsEnabled, now).run();
  const conversation = body.conversation && typeof body.conversation === "object" && !Array.isArray(body.conversation)
    ? body.conversation as JsonObject
    : null;
  if (conversation) {
    const conversationId = asString(conversation.conversationId || conversation.id, "");
    if (conversationId) {
      await env.DB.prepare(
        `
          INSERT INTO conversation_preferences (user_id, conversation_id, muted_until, pinned, archived, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)
          ON CONFLICT(user_id, conversation_id) DO UPDATE SET
            muted_until = excluded.muted_until,
            pinned = excluded.pinned,
            archived = excluded.archived,
            updated_at = excluded.updated_at
        `,
      ).bind(
        auth.userId,
        conversationId,
        Number(conversation.mutedUntil || 0) || null,
        asBoolean(conversation.pinned, false) ? 1 : 0,
        asBoolean(conversation.archived, false) ? 1 : 0,
        now,
      ).run();
    }
  }
  return getChatPreferences(env, request);
}

function callEventName(input: string): string {
  const normalized = input.trim();
  if (normalized === "call_started") return "call:start";
  if (normalized === "incoming_call") return "call:start";
  if (normalized === "call_accepted") return "call:accepted";
  if (normalized === "call_rejected") return "call:declined";
  if (normalized === "call_ended") return "call:ended";
  if (normalized === "call_busy") return "call:busy";
  if (normalized === "call_missed") return "call:missed";
  if (normalized === "call_unavailable") return "call:unavailable";
  if (normalized === "webrtc_offer") return "call:offer";
  if (normalized === "webrtc_answer") return "call:answer";
  if (normalized === "ice_candidate") return "call:ice-candidate";
  if (normalized === "media_toggle") return "call:media-state";
  if (normalized === "participant_left") return "call:ended";
  return normalized || "call:event";
}

async function callRowFor(env: Env, callId: string): Promise<any> {
  return env.DB.prepare(
    `
      SELECT id, chat_id AS chatId, caller_user_id AS callerId, receiver_user_id AS calleeId,
        type, status, started_at AS startedAt, answered_at AS answeredAt,
        ended_at AS endedAt, end_reason AS endReason,
        COALESCE(mode, 'direct') AS mode,
        COALESCE(max_participants, 2) AS maxParticipants
      FROM call_sessions
      WHERE id = ?
    `,
  ).bind(callId).first<any>();
}

async function callParticipantIds(env: Env, callId: string): Promise<string[]> {
  const rows = await env.DB.prepare(
    "SELECT user_id AS userId FROM call_participants WHERE call_id = ? ORDER BY joined_at IS NULL ASC, joined_at ASC",
  ).bind(callId).all<any>();
  return (rows.results || []).map((row: any) => row.userId).filter(Boolean);
}

async function conversationMemberIds(env: Env, conversationId: string): Promise<string[]> {
  const rows = await env.DB.prepare(
    "SELECT user_id AS userId FROM conversation_members WHERE conversation_id = ?",
  ).bind(conversationId).all<any>();
  return (rows.results || []).map((row: any) => row.userId).filter(Boolean);
}

async function markMessageDelivered(env: Env, messageId: string, userId: string, deliveredAt = Date.now()): Promise<void> {
  await env.DB.prepare(
    `
      INSERT INTO message_receipts (message_id, user_id, delivered_at, read_at)
      VALUES (?, ?, ?, NULL)
      ON CONFLICT(message_id, user_id) DO UPDATE SET
        delivered_at = COALESCE(message_receipts.delivered_at, excluded.delivered_at)
    `,
  ).bind(messageId, userId, deliveredAt).run();
}

async function markConversationMessagesRead(env: Env, conversationId: string, readerId: string): Promise<string[]> {
  const member = await env.DB.prepare(
    "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?",
  ).bind(conversationId, readerId).first<any>();
  if (!member) return [];

  const rows = await env.DB.prepare(
    `
      SELECT m.id
      FROM messages m
      LEFT JOIN message_receipts r ON r.message_id = m.id AND r.user_id = ?
      WHERE m.conversation_id = ?
        AND m.sender_id != ?
        AND m.deleted_at IS NULL
        AND r.read_at IS NULL
    `,
  ).bind(readerId, conversationId, readerId).all<any>();
  const messageIds = (rows.results || []).map((row: any) => String(row.id || "")).filter(Boolean);
  if (messageIds.length === 0) return [];

  const now = Date.now();
  await Promise.all(messageIds.map((messageId) => env.DB.prepare(
    `
      INSERT INTO message_receipts (message_id, user_id, delivered_at, read_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(message_id, user_id) DO UPDATE SET
        delivered_at = COALESCE(message_receipts.delivered_at, excluded.delivered_at),
        read_at = excluded.read_at
    `,
  ).bind(messageId, readerId, now, now).run()));
  return messageIds;
}

async function markUndeliveredMessagesDelivered(env: Env, userId: string): Promise<Record<string, string[]>> {
  const rows = await env.DB.prepare(
    `
      SELECT m.id AS messageId, m.conversation_id AS conversationId
      FROM messages m
      JOIN conversation_members cm ON cm.conversation_id = m.conversation_id AND cm.user_id = ?
      LEFT JOIN message_receipts r ON r.message_id = m.id AND r.user_id = ?
      WHERE m.sender_id != ?
        AND m.deleted_at IS NULL
        AND r.delivered_at IS NULL
    `,
  ).bind(userId, userId, userId).all<any>();
  const grouped: Record<string, string[]> = {};
  const now = Date.now();
  for (const row of rows.results || []) {
    const messageId = String(row.messageId || "");
    const conversationId = String(row.conversationId || "");
    if (!messageId || !conversationId) continue;
    await markMessageDelivered(env, messageId, userId, now);
    if (!grouped[conversationId]) grouped[conversationId] = [];
    grouped[conversationId].push(messageId);
  }
  return grouped;
}

async function broadcastMessageAndChatUpdates(env: Env, conversationId: string, messageIds: string[]): Promise<void> {
  const memberIds = await conversationMemberIds(env, conversationId);
  const messages = (await Promise.all(messageIds.map((messageId) => messageFor(env, messageId))))
    .filter((message): message is JsonObject => !!message);
  await Promise.all(memberIds.flatMap((userId) =>
    messages.map((message) => broadcastToDurableUser(env, userId, "message_updated", message)),
  ));
  await Promise.all(memberIds.map(async (userId) => {
    const payload = await conversationFor(env, conversationId, userId);
    if (payload) await broadcastToDurableUser(env, userId, "chat_updated", payload);
  }));
}

async function isCallParticipant(env: Env, callId: string, userId: string): Promise<boolean> {
  const row = await env.DB.prepare(
    "SELECT 1 FROM call_participants WHERE call_id = ? AND user_id = ?",
  ).bind(callId, userId).first<any>();
  return !!row;
}

function callPayload(row: any, extra: JsonObject = {}): JsonObject {
  return {
    callId: row.id,
    chatId: row.chatId,
    callerId: row.callerId,
    calleeId: row.calleeId,
    fromUserId: extra.fromUserId || row.callerId,
    toUserId: extra.toUserId || row.calleeId,
    type: row.type || "audio",
    callType: row.type || "audio",
    isVideo: row.type === "video",
    mode: row.mode || "direct",
    maxParticipants: row.maxParticipants || (row.mode === "group" ? 4 : 2),
    status: row.status,
    startedAt: row.startedAt,
    answeredAt: row.answeredAt || null,
    endedAt: row.endedAt || null,
    endReason: row.endReason || null,
    ...extra,
  };
}

async function callRoomPayload(env: Env, row: any): Promise<JsonObject> {
  const participantIds = await callParticipantIds(env, row.id);
  return {
    id: row.id,
    callId: row.id,
    roomId: row.id,
    chatId: row.chatId,
    hostId: row.callerId,
    mode: row.mode || "group",
    type: row.type || "audio",
    callType: row.type || "audio",
    status: row.status,
    maxParticipants: row.maxParticipants || 4,
    participantIds,
    participants: participantIds.map((id) => ({ id, isHost: id === row.callerId })),
    createdAt: row.startedAt,
    endedAt: row.endedAt || null,
    endedBy: row.endReason || null,
  };
}

async function recordCallEvent(env: Env, callId: string, event: string, payload: JsonObject): Promise<void> {
  await env.DB.prepare(
    `
      INSERT INTO call_events (id, call_id, event_type, sender_user_id, receiver_user_id, payload_json, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `,
  ).bind(
    asString(payload.eventId, randomId("evt")),
    callId,
    event,
    asString(payload.fromUserId, ""),
    asString(payload.toUserId, ""),
    JSON.stringify(payload),
    Date.now(),
  ).run();
}

async function updateCallStateForEvent(env: Env, callId: string, event: string, reason = ""): Promise<void> {
  const now = Date.now();
  if (event === "call:accepted") {
    await env.DB.prepare(
      "UPDATE call_sessions SET status = 'connecting', answered_at = COALESCE(answered_at, ?), updated_at = ? WHERE id = ?",
    ).bind(now, now, callId).run();
  } else if (event === "call:connected") {
    await env.DB.prepare(
      "UPDATE call_sessions SET status = 'connected', answered_at = COALESCE(answered_at, ?), updated_at = ? WHERE id = ?",
    ).bind(now, now, callId).run();
  } else if (event === "call:declined" || event === "call:busy" || event === "call:unavailable") {
    await env.DB.prepare(
      "UPDATE call_sessions SET status = 'rejected', ended_at = COALESCE(ended_at, ?), end_reason = ?, updated_at = ? WHERE id = ?",
    ).bind(now, reason || "rejected", now, callId).run();
    await env.DB.prepare(
      "UPDATE call_participants SET left_at = COALESCE(left_at, ?) WHERE call_id = ?",
    ).bind(now, callId).run().catch(() => undefined);
  } else if (event === "call:missed") {
    await env.DB.prepare(
      "UPDATE call_sessions SET status = 'missed', ended_at = COALESCE(ended_at, ?), end_reason = ?, updated_at = ? WHERE id = ?",
    ).bind(now, reason || "missed", now, callId).run();
    await env.DB.prepare(
      "UPDATE call_participants SET left_at = COALESCE(left_at, ?) WHERE call_id = ?",
    ).bind(now, callId).run().catch(() => undefined);
  } else if (event === "call:ended" || event === "call:failed") {
    await env.DB.prepare(
      "UPDATE call_sessions SET status = 'ended', ended_at = COALESCE(ended_at, ?), end_reason = ?, updated_at = ? WHERE id = ?",
    ).bind(now, reason || "ended", now, callId).run();
    await env.DB.prepare(
      "UPDATE call_participants SET left_at = COALESCE(left_at, ?) WHERE call_id = ?",
    ).bind(now, callId).run().catch(() => undefined);
  }
}

type NotificationChannel =
  | "calls"
  | "missed_calls"
  | "messages"
  | "mentions"
  | "status_posts"
  | "status_activity"
  | "system"
  | "re_engagement";

type NotificationPriority = "urgent" | "high" | "default" | "low";

async function notificationAllowed(env: Env, userId: string): Promise<boolean> {
  const prefs = await env.DB.prepare(
    "SELECT notifications_enabled AS notificationsEnabled FROM user_chat_preferences WHERE user_id = ?",
  ).bind(userId).first<any>();
  return prefs ? Number(prefs.notificationsEnabled) !== 0 : true;
}

async function fcmAccessToken(env: Env): Promise<string | null> {
  if (fcmAccessTokenCache && fcmAccessTokenCache.expiresAt > Date.now() + 60_000) {
    return fcmAccessTokenCache.token;
  }
  const serviceAccount = env.FCM_SERVICE_ACCOUNT_JSON
    ? JSON.parse(env.FCM_SERVICE_ACCOUNT_JSON) as { client_email?: string; private_key?: string }
    : null;
  const clientEmail = env.FIREBASE_CLIENT_EMAIL || serviceAccount?.client_email || "";
  const privateKey = env.FIREBASE_PRIVATE_KEY || serviceAccount?.private_key || "";
  if (!clientEmail || !privateKey) return null;
  const nowSeconds = Math.floor(Date.now() / 1000);
  const assertion = [
    jsonBase64Url({ alg: "RS256", typ: "JWT" }),
    jsonBase64Url({
      iss: clientEmail,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: nowSeconds,
      exp: nowSeconds + 3600,
    }),
  ].join(".");
  const key = await crypto.subtle.importKey(
    "pkcs8",
    privateKeyToArrayBuffer(privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(assertion),
  );
  const jwt = `${assertion}.${base64Url(signature)}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!response.ok) throw new Error(`FCM OAuth failed: ${response.status}`);
  const body = await response.json().catch(() => null) as any;
  const token = String(body.access_token || "");
  if (!token) throw new Error("FCM OAuth response missing access_token");
  fcmAccessTokenCache = {
    token,
    expiresAt: Date.now() + Number(body.expires_in || 3600) * 1000,
  };
  return token;
}

function fcmData(payload: JsonObject): Record<string, string> {
  const data: Record<string, string> = {};
  for (const [key, value] of Object.entries(payload)) {
    data[key] = value == null ? "" : String(value);
  }
  return data;
}

function fcmTokenError(body: any): boolean {
  const status = String(body?.error?.status || "");
  if (status === "NOT_FOUND") return true;
  const message = String(body?.error?.message || "");
  if (message.includes("UNREGISTERED") || message.includes("NotRegistered")) return true;
  const details = Array.isArray(body?.error?.details) ? body.error.details : [];
  return details.some((detail: any) => {
    const code = String(detail?.errorCode || detail?.status || "");
    return code === "UNREGISTERED" || code === "SENDER_ID_MISMATCH";
  });
}

async function sendAndroidFcm(env: Env, userId: string, payload: JsonObject): Promise<void> {
  const projectId = env.FIREBASE_PROJECT_ID || env.FCM_PROJECT_ID || "";
  const type = String(payload.type || "");
  const channel = String(payload.channel || "system");
  console.log(`[fcm] dispatch start recipient=${userId} type=${type} channel=${channel}`);
  const accessToken = await fcmAccessToken(env).catch((error) => {
    console.warn("FCM access token unavailable", error);
    return null;
  });
  if (!projectId || !accessToken) {
    console.log(`[fcm] dispatch skipped recipient=${userId} type=${type} channel=${channel} reason=${!projectId ? "missing_project_id" : "missing_access_token"}`);
    return;
  }

  const rows = await env.DB.prepare(
    "SELECT id, token, platform FROM device_push_tokens WHERE user_id = ?",
  ).bind(userId).all<any>();
  const tokens = (rows.results || [])
    .map((row: any) => ({
      id: String(row.id || ""),
      token: String(row.token || ""),
      platform: String(row.platform || "android").toLowerCase(),
    }))
    .filter((row) => row.token);
  const androidTokens = tokens.filter((row) => row.platform === "android" || row.platform === "fcm" || row.platform === "");
  console.log(`[fcm] token inventory recipient=${userId} type=${type} channel=${channel} found=${tokens.length} android=${androidTokens.length}`);
  if (androidTokens.length === 0) {
    console.log(`[fcm] dispatch skipped recipient=${userId} type=${type} channel=${channel} reason=no_android_tokens`);
    return;
  }

  const urgent = type === "call_incoming" || channel === "calls";
  await Promise.all(androidTokens.map(async ({ id, token, platform }) => {
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          data: fcmData(payload),
          android: {
            priority: urgent || String(payload.priority || "") === "high" ? "HIGH" : "NORMAL",
            ttl: urgent ? "60s" : "2419200s",
          },
        },
      }),
    });
    const responseText = await response.text().catch(() => "");
    console.log(`[fcm] response recipient=${userId} type=${type} channel=${channel} platform=${platform} status=${response.status}`);
    if (response.ok) return;
    const body = responseText ? (() => {
      try {
        return JSON.parse(responseText);
      } catch {
        return null;
      }
    })() : null;
    if (response.status === 404 || fcmTokenError(body)) {
      await env.DB.prepare("DELETE FROM device_push_tokens WHERE id = ?").bind(id).run().catch(() => undefined);
      console.log(`[fcm] deleted stale token recipient=${userId} tokenId=${id} platform=${platform}`);
    }
    console.warn(`[fcm] send failed recipient=${userId} type=${type} channel=${channel} platform=${platform} status=${response.status} body=${responseText.slice(0, 400)}`);
  }));
}

async function emitUserNotification(env: Env, userId: string, payload: JsonObject & {
  type: string;
  channel: NotificationChannel;
  priority: NotificationPriority;
  collapseKey: string;
}): Promise<void> {
  if (!userId || payload.senderId === userId) return;
  if (!(await notificationAllowed(env, userId))) return;
  const now = Date.now();
  const outgoing = {
    senderId: "",
    senderName: "Hello",
    senderAvatar: null,
    targetId: null,
    targetType: "system",
    groupName: null,
    previewText: "",
    emoji: null,
    deepLink: "hello://notifications",
    sentAt: new Date(now).toISOString(),
    ...payload,
  };
  await env.DB.prepare(
    `
      INSERT INTO notification_events
        (id, user_id, type, channel, priority, collapse_key, payload_json, delivery_status, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, 'realtime_only', ?)
    `,
  ).bind(
    randomId("notif"),
    userId,
    String(outgoing.type),
    String(outgoing.channel),
    String(outgoing.priority),
    String(outgoing.collapseKey),
    JSON.stringify(outgoing),
    now,
  ).run().catch(() => undefined);
  await broadcastToDurableUser(env, userId, "notification", outgoing);
  console.log(`[notification] dispatch recipient=${userId} type=${outgoing.type} channel=${outgoing.channel} collapseKey=${outgoing.collapseKey}`);
  await sendAndroidFcm(env, userId, outgoing).catch((error) => console.warn("FCM send failed", error));
}

async function broadcastToDurableUser(env: Env, userId: string, event: string, payload: JsonObject): Promise<number> {
  const id = env.REALTIME_ROOM.idFromName(`user:${userId}`);
  const response = await env.REALTIME_ROOM.get(id).fetch("https://internal/relay", {
    method: "POST",
    body: JSON.stringify({ event, payload }),
  }).catch(() => null);
  if (!response) return 0;
  const result = await response.json().catch(() => null) as any;
  return Number(result?.sockets || 0);
}

async function broadcastToDurableCall(env: Env, callId: string, event: string, payload: JsonObject): Promise<void> {
  const id = env.REALTIME_ROOM.idFromName(`call:${callId}`);
  await env.REALTIME_ROOM.get(id).fetch("https://internal/relay", {
    method: "POST",
    body: JSON.stringify({ event, payload }),
  }).catch(() => undefined);
}

async function relayCallEvent(env: Env, event: string, payload: JsonObject): Promise<void> {
  const callId = asString(payload.callId || payload.roomId || payload.id, "");
  if (!callId) return;
  const eventId = asString(payload.eventId, "") || randomId("evt");
  const outgoing: JsonObject = { ...payload, callId, roomId: asString(payload.roomId, callId), eventId, timestamp: Date.now(), event };
  await recordCallEvent(env, callId, event, outgoing);
  await updateCallStateForEvent(env, callId, event, asString(outgoing.reason, ""));
  const toUserId = asString(outgoing.toUserId, "");
  if (toUserId) {
    await broadcastToDurableUser(env, toUserId, event, outgoing);
  } else {
    const fromUserId = asString(outgoing.fromUserId, "");
    const participantIds = await callParticipantIds(env, callId);
    await Promise.all(participantIds
      .filter((userId) => userId && userId !== fromUserId)
      .map((userId) => broadcastToDurableUser(env, userId, event, { ...outgoing, toUserId: userId })));
  }
  await broadcastToDurableCall(env, callId, event, outgoing);
}

async function broadcastToCallParticipants(env: Env, callId: string, event: string, payload: JsonObject, excludeUserId = ""): Promise<void> {
  const participantIds = await callParticipantIds(env, callId);
  await Promise.all(participantIds
    .filter((userId) => userId && userId !== excludeUserId)
    .map((userId) => broadcastToDurableUser(env, userId, event, { ...payload, toUserId: userId })));
  await broadcastToDurableCall(env, callId, event, payload);
}

async function startCall(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const body = await readJson(request);
  const callerId = auth.userId;
  const receiverId = asString(body.receiverUserId || body.calleeId || body.toUserId, "");
  const chatId = asString(body.chatId, `direct_${[callerId, receiverId].sort().join("_")}`);
  const type = asString(body.type, "audio") === "video" ? "video" : "audio";
  if (!receiverId) return badRequest("receiverUserId is required");
  if (receiverId === callerId) return badRequest("Cannot call yourself");

  const callId = randomId("call");
  const now = Date.now();
  await env.DB.prepare(
    `
      INSERT INTO call_sessions (id, chat_id, caller_user_id, receiver_user_id, type, status, started_at, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, 'ringing', ?, ?, ?)
    `,
  ).bind(callId, chatId, callerId, receiverId, type, now, now, now).run();
  await env.DB.prepare(
    "INSERT INTO call_participants (call_id, user_id, role, joined_at) VALUES (?, ?, 'caller', ?), (?, ?, 'receiver', NULL)",
  ).bind(callId, callerId, now, callId, receiverId).run();

  const caller = await userFor(env, callerId);
  const receiver = await userFor(env, receiverId);
  const row = await callRowFor(env, callId);
  const payload = callPayload(row, {
    fromUserId: callerId,
    toUserId: receiverId,
    callerName: asString(caller?.name, callerId),
    callerAvatar: asString(caller?.avatar, ""),
    calleeName: asString(receiver?.name, receiverId),
    calleeAvatar: asString(receiver?.avatar, ""),
  });
  await relayCallEvent(env, "call:start", payload);
  await emitUserNotification(env, receiverId, {
    type: "call_incoming",
    senderId: callerId,
    senderName: asString(caller?.name, callerId),
    senderAvatar: asString(caller?.avatar, ""),
    callId,
    chatId,
    callerId,
    calleeId: receiverId,
    isVideo: type === "video",
    targetId: callId,
    targetType: "call",
    previewText: `${asString(caller?.name, callerId)} is calling...`,
    deepLink: `hello://calls/${callId}`,
    channel: "calls",
    priority: "urgent",
    collapseKey: `call_${callId}`,
  });
  return json({ id: callId, callId, ...payload }, { status: 201 });
}

async function startGroupCall(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const body = await readJson(request);
  const hostId = auth.userId;
  const chatId = asString(body.chatId, "");
  const type = asString(body.type || body.callType, "audio") === "video" ? "video" : "audio";
  const invited = asStringList(body.participantIds || body.memberIds)
    .filter((id) => id && id !== hostId);
  const participantIds = [...new Set([hostId, ...invited])].slice(0, 4);
  if (!chatId) return badRequest("chatId is required");
  if (participantIds.length < 2) return badRequest("group call requires at least 2 participants");
  if (participantIds.length > 4) return badRequest("group calls support up to 4 participants");

  const callId = randomId("call");
  const now = Date.now();
  const receiverId = participantIds.find((id) => id !== hostId) || hostId;
  await env.DB.prepare(
    `
      INSERT INTO call_sessions
        (id, chat_id, caller_user_id, receiver_user_id, type, status, started_at, created_at, updated_at, mode, max_participants)
      VALUES (?, ?, ?, ?, ?, 'ringing', ?, ?, ?, 'group', 4)
    `,
  ).bind(callId, chatId, hostId, receiverId, type, now, now, now).run();
  for (const userId of participantIds) {
    await env.DB.prepare(
      "INSERT INTO call_participants (call_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)",
    ).bind(callId, userId, userId === hostId ? "host" : "participant", userId === hostId ? now : null).run();
  }
  const row = await callRowFor(env, callId);
  const room = await callRoomPayload(env, row);
  const payload = {
    ...room,
    room,
    fromUserId: hostId,
    participantIds,
    eventId: randomId("evt"),
    timestamp: now,
    note: "Group calls use max-4 WebRTC mesh. Larger calls need an SFU later.",
  };
  await recordCallEvent(env, callId, "call:room-created", payload);
  await broadcastToCallParticipants(env, callId, "call:room-created", payload, hostId);
  return json(room, { status: 201 });
}

async function joinGroupCall(env: Env, request: Request, callId: string): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const row = await callRowFor(env, callId);
  if (!row) return notFound(`/api/calls/group/${callId}`);
  if (row.mode !== "group") return badRequest("call is not a group call");
  if (!(await isCallParticipant(env, callId, auth.userId))) {
    return json({ ok: false, error: "Forbidden" }, { status: 403 });
  }
  const now = Date.now();
  await env.DB.prepare(
    "UPDATE call_participants SET joined_at = COALESCE(joined_at, ?) WHERE call_id = ? AND user_id = ?",
  ).bind(now, callId, auth.userId).run();
  await env.DB.prepare(
    "UPDATE call_sessions SET status = 'connected', answered_at = COALESCE(answered_at, ?), updated_at = ? WHERE id = ?",
  ).bind(now, now, callId).run();
  const updated = await callRowFor(env, callId);
  const room = await callRoomPayload(env, updated);
  const payload = { ...room, room, roomId: callId, fromUserId: auth.userId, userId: auth.userId, eventId: randomId("evt"), timestamp: now };
  await recordCallEvent(env, callId, "call:room-join", payload);
  await broadcastToCallParticipants(env, callId, "call:room-join", payload, auth.userId);
  return json(room);
}

async function leaveGroupCall(env: Env, request: Request, callId: string): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const row = await callRowFor(env, callId);
  if (!row) return notFound(`/api/calls/group/${callId}`);
  if (!(await isCallParticipant(env, callId, auth.userId))) {
    return json({ ok: false, error: "Forbidden" }, { status: 403 });
  }
  const body = await readJson(request);
  const now = Date.now();
  const ended = asBoolean(body.ended || body.end, false) || auth.userId === row.callerId;
  await env.DB.prepare(
    "UPDATE call_participants SET left_at = COALESCE(left_at, ?) WHERE call_id = ? AND user_id = ?",
  ).bind(now, callId, auth.userId).run();
  if (ended) {
    await env.DB.prepare(
      "UPDATE call_sessions SET status = 'ended', ended_at = COALESCE(ended_at, ?), end_reason = ?, updated_at = ? WHERE id = ?",
    ).bind(now, asString(body.reason, auth.userId), now, callId).run();
  }
  const updated = await callRowFor(env, callId);
  const room = await callRoomPayload(env, updated);
  const payload = { ...room, room, roomId: callId, fromUserId: auth.userId, userId: auth.userId, ended, eventId: randomId("evt"), timestamp: now };
  await recordCallEvent(env, callId, "call:room-leave", payload);
  await broadcastToCallParticipants(env, callId, "call:room-leave", payload, auth.userId);
  return json(room);
}

async function updateCallFromRest(env: Env, request: Request, callId: string, action: string): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const row = await callRowFor(env, callId);
  if (!row) return notFound(`/api/calls/${callId}`);
  if (auth.userId !== row.callerId && auth.userId !== row.calleeId) {
    return json({ ok: false, error: "Forbidden" }, { status: 403 });
  }
  const body = await readJson(request);
  const otherId = auth.userId === row.callerId ? row.calleeId : row.callerId;
  const event = action === "accept" ? "call:accepted"
    : action === "reject" ? "call:declined"
    : "call:ended";
  const payload = callPayload(row, {
    fromUserId: auth.userId,
    toUserId: otherId,
    reason: asString(body.reason, action),
  });
  await relayCallEvent(env, event, payload);
  const updated = await callRowFor(env, callId);
  return json({ ok: true, ...(updated ? callPayload(updated) : { callId }) });
}

async function getCall(env: Env, request: Request, callId: string): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const row = await callRowFor(env, callId);
  if (!row) return notFound(`/api/calls/${callId}`);
  if (auth.userId !== row.callerId && auth.userId !== row.calleeId) {
    return json({ ok: false, error: "Forbidden" }, { status: 403 });
  }
  return json(callPayload(row));
}

async function callHistory(env: Env, request: Request, url: URL): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const userId = url.searchParams.get("userId") || auth.userId;
  if (userId !== auth.userId) return json({ ok: false, error: "Forbidden" }, { status: 403 });
  const rows = await env.DB.prepare(
    `
      SELECT c.id, c.chat_id AS chatId, c.caller_user_id AS callerId, c.receiver_user_id AS calleeId,
        c.type, c.status, c.started_at AS startedAt, c.answered_at AS acceptedAt,
        c.ended_at AS endedAt, c.end_reason AS endReason,
        COALESCE(c.mode, 'direct') AS mode,
        COALESCE(c.max_participants, 2) AS maxParticipants,
        CASE WHEN c.caller_user_id = ? THEN c.receiver_user_id ELSE c.caller_user_id END AS otherUserId,
        u.display_name AS otherName, u.avatar_url AS otherAvatar
      FROM call_sessions c
      LEFT JOIN users u ON u.id = CASE WHEN c.caller_user_id = ? THEN c.receiver_user_id ELSE c.caller_user_id END
      WHERE c.caller_user_id = ? OR c.receiver_user_id = ?
      ORDER BY c.started_at DESC
      LIMIT 50
    `,
  ).bind(userId, userId, userId, userId).all<any>();
  const history = await Promise.all((rows.results || []).map(async (row: any) => ({
    id: row.id,
    callId: row.id,
    chatId: row.chatId,
    callerId: row.callerId,
    calleeId: row.calleeId,
    type: row.type || "audio",
    callType: row.type || "audio",
    mode: row.mode || "direct",
    direction: row.callerId === userId ? "outgoing" : "incoming",
    status: row.status,
    startedAt: row.startedAt,
    acceptedAt: row.acceptedAt || null,
    endedAt: row.endedAt || null,
    durationSeconds: row.endedAt && row.acceptedAt ? Math.max(0, Math.floor((row.endedAt - row.acceptedAt) / 1000)) : null,
    endReason: row.endReason || null,
    otherUser: {
      id: row.otherUserId,
      name: row.otherName || row.otherUserId,
      avatar: row.otherAvatar || null,
    },
    participantIds: await callParticipantIds(env, row.id),
    maxParticipants: row.maxParticipants || 2,
  })));
  return json(history);
}

async function registerDevicePushToken(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const body = await readJson(request);
  const token = asString(body.token, "").trim();
  const platform = asString(body.platform, "android");
  const deviceName = asString(body.deviceName || body.name, "");
  if (!token) return badRequest("token is required");
  const id = asString(body.deviceId || body.id, "") || randomId("pushdev");
  const now = Date.now();
  await env.DB.prepare(
    `
      INSERT INTO device_push_tokens (id, user_id, token, platform, device_name, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(user_id, token) DO UPDATE SET
        platform = excluded.platform,
        device_name = excluded.device_name,
        updated_at = excluded.updated_at
    `,
  ).bind(id, auth.userId, token, platform || null, deviceName || null, now, now).run();
  return json({
    ok: true,
    id,
    userId: auth.userId,
    platform,
    pushDelivery: "not_configured",
    note: "Device token is stored for the future FCM adapter; killed-app calling is not faked.",
  });
}

async function deleteDevicePushToken(env: Env, request: Request, deviceId: string): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  await env.DB.prepare(
    "DELETE FROM device_push_tokens WHERE id = ? AND user_id = ?",
  ).bind(deviceId, auth.userId).run();
  return json({ ok: true, id: deviceId });
}

function iceConfig(env: Env): Response {
  const turnUrls = (env.TURN_URLS || "")
    .split(",")
    .map((url) => url.trim())
    .filter(Boolean);
  const iceServers: JsonObject[] = [
    { urls: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"] },
  ];
  if (turnUrls.length > 0) {
    iceServers.push({
      urls: turnUrls,
      username: env.TURN_USERNAME || "",
      credential: env.TURN_CREDENTIAL || "",
    });
  }
  return json({
    iceServers,
    source: turnUrls.length > 0 ? "turn_env" : "public_stun_default",
  });
}

async function listConversations(env: Env, url: URL): Promise<Response> {
  const userId = url.searchParams.get("userId") || "";
  if (!userId) return badRequest("userId is required");
  const rows = await env.DB.prepare(
    `
      SELECT c.id
      FROM conversations c
      JOIN conversation_members cm ON cm.conversation_id = c.id
      WHERE cm.user_id = ?
      ORDER BY c.updated_at DESC
    `,
  ).bind(userId).all<any>();
  const conversations = (await Promise.all((rows.results || []).map((row: any) => conversationFor(env, row.id, userId))))
    .filter(Boolean) as JsonObject[];
  const deduped = new Map<string, JsonObject>();
  for (const conversation of conversations) {
    const key = conversation.isGroup
      ? asString(conversation.id, "")
      : asString(conversation.directKey, "") || asString(conversation.id, "");
    const existing = deduped.get(key);
    if (!existing || Number(conversation.lastMessageTime || 0) > Number(existing.lastMessageTime || 0)) {
      deduped.set(key, conversation);
    }
  }
  return json([...deduped.values()].sort((a, b) => Number(b.lastMessageTime || 0) - Number(a.lastMessageTime || 0)));
}

async function findDirectConversationByPair(env: Env, userIds: string[]): Promise<string | null> {
  const directKey = directKeyForUsers(userIds);
  if (!directKey) return null;
  const byKey = await env.DB.prepare(
    "SELECT id FROM conversations WHERE type = 'direct' AND direct_key = ? ORDER BY updated_at DESC LIMIT 1",
  ).bind(directKey).first<any>();
  if (byKey?.id) return byKey.id;

  const rows = await env.DB.prepare(
    `
      SELECT c.id
      FROM conversations c
      JOIN conversation_members cm ON cm.conversation_id = c.id
      WHERE c.type = 'direct'
      GROUP BY c.id
      HAVING COUNT(DISTINCT cm.user_id) = 2
        AND SUM(CASE WHEN cm.user_id IN (?, ?) THEN 1 ELSE 0 END) = 2
      ORDER BY c.updated_at DESC
      LIMIT 1
    `,
  ).bind(userIds[0], userIds[1]).all<any>();
  const id = rows.results?.[0]?.id || null;
  if (id) {
    await env.DB.prepare("UPDATE conversations SET direct_key = ? WHERE id = ?")
      .bind(directKey, id)
      .run()
      .catch(() => undefined);
  }
  return id;
}

async function createConversation(env: Env, body: JsonObject): Promise<Response> {
  const isGroup = body.isGroup === true || body.type === "group";
  const type = isGroup ? "group" : "direct";
  const title = asString(body.title || body.name, "");
  const createdBy = asString(body.createdBy || body.currentUserId || body.userId, "");
  const targetUserId = asString(body.targetUserId || body.participantId || body.contactUserId, "");
  const memberIds = asStringList(body.memberIds || body.members)
    .concat(createdBy ? [createdBy] : [])
    .concat(targetUserId ? [targetUserId] : [])
    .filter(Boolean);
  const uniqueMemberIds = [...new Set(memberIds)];
  if (uniqueMemberIds.length === 0) return badRequest("at least one member is required");
  if (type === "direct" && uniqueMemberIds.length !== 2) {
    return badRequest("direct conversations require exactly two distinct members");
  }
  const directKey = type === "direct" ? directKeyForUsers(uniqueMemberIds) : null;
  if (type === "direct" && directKey) {
    const existingId = await findDirectConversationByPair(env, uniqueMemberIds);
    if (existingId) {
      const existing = await conversationFor(env, existingId, createdBy || uniqueMemberIds[0]);
      return json(existing || { id: existingId });
    }
  }
  const id = type === "direct" && directKey
    ? `direct_${directKey.replace(/[^a-zA-Z0-9_-]/g, "_")}`
    : asString(body.id) || randomId("conv");

  const now = Date.now();
  if (createdBy) {
    await env.DB.prepare(
      "INSERT OR IGNORE INTO users (id, display_name, created_at, updated_at) VALUES (?, ?, ?, ?)",
    ).bind(createdBy, asString(body.createdByName, createdBy), now, now).run();
  }
  for (const memberId of uniqueMemberIds) {
    await env.DB.prepare(
      "INSERT OR IGNORE INTO users (id, display_name, created_at, updated_at) VALUES (?, ?, ?, ?)",
    ).bind(memberId, memberId, now, now).run();
  }

  await env.DB.prepare(
    `
      INSERT INTO conversations (id, type, title, created_by, direct_key, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        title = COALESCE(NULLIF(excluded.title, ''), conversations.title),
        direct_key = COALESCE(excluded.direct_key, conversations.direct_key),
        updated_at = excluded.updated_at
    `,
  ).bind(id, type, title || null, createdBy || null, directKey, now, now).run();
  for (const memberId of uniqueMemberIds) {
    await env.DB.prepare(
      "INSERT OR IGNORE INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?, ?, 'member', ?)",
    ).bind(id, memberId, now).run();
  }
  const conversation = await conversationFor(env, id, createdBy || uniqueMemberIds[0]);
  if (conversation) {
    await Promise.all(uniqueMemberIds
      .filter((userId) => userId !== createdBy)
      .map((userId) => broadcastToDurableUser(env, userId, "new_chat", conversation)));
    await Promise.all(uniqueMemberIds
      .map(async (userId) => {
        const payload = await conversationFor(env, id, userId);
        if (payload) await broadcastToDurableUser(env, userId, "chat_updated", payload);
      }));
  }
  return json(conversation || { id }, { status: 201 });
}

async function createMessage(env: Env, conversationId: string, body: JsonObject): Promise<Response> {
  const senderId = asString(body.senderId || body.userId);
  const senderName = asString(body.senderName || body.displayName || body.name, senderId);
  const senderAvatar = asString(body.senderAvatar || body.avatar, "");
  const text = asString(body.text || body.body, "");
  if (!senderId) return badRequest("senderId is required");
  if (!text.trim() && !asString(body.attachmentId)) return badRequest("text or attachmentId is required");

  const now = Date.now();
  const conversationRow = await env.DB.prepare("SELECT id FROM conversations WHERE id = ?").bind(conversationId).first<any>();
  if (!conversationRow) return json({ ok: false, error: "Conversation not found" }, { status: 404 });
  const senderMember = await env.DB.prepare(
    "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?",
  ).bind(conversationId, senderId).first<any>();
  if (!senderMember) return json({ ok: false, error: "Sender is not a conversation member" }, { status: 403 });
  await env.DB.prepare(
    `
      INSERT INTO users (id, display_name, avatar_url, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        display_name = excluded.display_name,
        avatar_url = COALESCE(excluded.avatar_url, users.avatar_url),
        updated_at = excluded.updated_at
    `,
  ).bind(senderId, senderName, senderAvatar || null, now, now).run();
  const messageId = asString(body.id) || randomId("msg");
  await env.DB.prepare(
    `
      INSERT INTO messages (id, conversation_id, sender_id, body, message_type, created_at)
      VALUES (?, ?, ?, ?, ?, ?)
    `,
  ).bind(messageId, conversationId, senderId, text, asString(body.messageType, "text"), now).run();
  await env.DB.prepare("UPDATE conversations SET updated_at = ? WHERE id = ?").bind(now, conversationId).run();

  const attachmentId = asString(body.attachmentId);
  if (attachmentId) {
    await env.DB.prepare("UPDATE attachments SET message_id = ? WHERE id = ?").bind(messageId, attachmentId).run();
  }

  const memberIds = await conversationMemberIds(env, conversationId);
  const message = await messageFor(env, messageId) || {
    id: messageId,
    chatId: conversationId,
    senderId,
    senderName,
    senderAvatar: senderAvatar || null,
    text,
    timestamp: now,
    status: "sent",
  };
  const deliveredRecipientIds: string[] = [];
  await Promise.all(memberIds.map(async (userId) => {
    if (userId === senderId) return;
    const socketCount = await broadcastToDurableUser(env, userId, "receive_message", message);
    if (socketCount > 0) deliveredRecipientIds.push(userId);
  }));
  await Promise.all(deliveredRecipientIds.map((userId) => markMessageDelivered(env, messageId, userId, now)));
  const senderMessage = await messageFor(env, messageId) || message;
  await broadcastToDurableUser(env, senderId, "message_updated", senderMessage);
  await Promise.all(memberIds.map(async (userId) => {
    const payload = await conversationFor(env, conversationId, userId);
    if (payload) await broadcastToDurableUser(env, userId, "chat_updated", payload);
  }));
  await Promise.all(memberIds.map(async (userId) => {
    if (userId === senderId) return;
    const conversation = await conversationFor(env, conversationId, userId);
    const isGroup = conversation?.isGroup === true;
    const previewText = text.trim()
      ? text.trim().slice(0, 50)
      : attachmentId
        ? "Sent an attachment"
        : "New message";
    await emitUserNotification(env, userId, {
      type: "message",
      senderId,
      senderName,
      senderAvatar: senderAvatar || null,
      targetId: conversationId,
      targetType: isGroup ? "group" : "chat",
      groupName: isGroup ? asString(conversation?.name, "Group") : null,
      previewText: isGroup ? `${senderName}: ${previewText}` : previewText,
      deepLink: `hello://chat/${conversationId}`,
      channel: "messages",
      priority: "high",
      collapseKey: `chat_${conversationId}`,
    });
  }));
  return json(message, { status: 201 });
}

async function markMessageRead(env: Env, messageId: string, body: JsonObject): Promise<Response> {
  const userId = asString(body.userId || body.readerId);
  if (!userId) return badRequest("userId is required");
  const row = await env.DB.prepare(
    "SELECT conversation_id AS conversationId FROM messages WHERE id = ? AND deleted_at IS NULL",
  ).bind(messageId).first<any>();
  if (!row) return json({ ok: false, error: "Message not found" }, { status: 404 });
  const member = await env.DB.prepare(
    "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?",
  ).bind(row.conversationId, userId).first<any>();
  if (!member) return json({ ok: false, error: "User is not a conversation member" }, { status: 403 });
  const now = Date.now();
  await env.DB.prepare(
    `
      INSERT INTO message_receipts (message_id, user_id, delivered_at, read_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(message_id, user_id) DO UPDATE SET
        delivered_at = COALESCE(message_receipts.delivered_at, excluded.delivered_at),
        read_at = excluded.read_at
    `,
  ).bind(messageId, userId, now, now).run();
  await broadcastMessageAndChatUpdates(env, String(row.conversationId), [messageId]);
  return json({ ok: true, messageId, userId, readAt: now });
}

async function reactToMessage(env: Env, messageId: string, body: JsonObject): Promise<Response> {
  const userId = asString(body.userId || body.senderId);
  const emoji = asString(body.emoji).trim();
  if (!userId) return badRequest("userId is required");
  if (!emoji) return badRequest("emoji is required");
  if (emoji.length > 32) return badRequest("emoji is too long");

  const messageRow = await env.DB.prepare(
    "SELECT id, conversation_id AS conversationId, sender_id AS senderId FROM messages WHERE id = ? AND deleted_at IS NULL",
  ).bind(messageId).first<any>();
  if (!messageRow) return json({ ok: false, error: "Message not found" }, { status: 404 });

  const member = await env.DB.prepare(
    "SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?",
  ).bind(messageRow.conversationId, userId).first<any>();
  if (!member) return json({ ok: false, error: "User is not a conversation member" }, { status: 403 });

  const now = Date.now();
  await env.DB.prepare(
    "INSERT OR IGNORE INTO users (id, display_name, created_at, updated_at) VALUES (?, ?, ?, ?)",
  ).bind(userId, userId, now, now).run();

  const existing = await env.DB.prepare(
    "SELECT 1 FROM message_reactions WHERE message_id = ? AND user_id = ? AND emoji = ?",
  ).bind(messageId, userId, emoji).first<any>();
  if (existing) {
    await env.DB.prepare(
      "DELETE FROM message_reactions WHERE message_id = ? AND user_id = ? AND emoji = ?",
    ).bind(messageId, userId, emoji).run();
  } else {
    await env.DB.prepare(
      `
        INSERT INTO message_reactions (message_id, user_id, emoji, created_at)
        VALUES (?, ?, ?, ?)
      `,
    ).bind(messageId, userId, emoji, now).run();
  }

  const message = await messageFor(env, messageId);
  if (!message) return json({ ok: false, error: "Message not found" }, { status: 404 });
  const memberIds = await conversationMemberIds(env, String(messageRow.conversationId));
  await Promise.all(memberIds.map((memberId) => broadcastToDurableUser(env, memberId, "message_updated", message)));
  if (String(messageRow.senderId || "") && String(messageRow.senderId) !== userId) {
    const reactor = await userFor(env, userId);
    await emitUserNotification(env, String(messageRow.senderId), {
      type: "message_reaction",
      senderId: userId,
      senderName: asString(reactor?.name, userId),
      senderAvatar: asString(reactor?.avatar, ""),
      targetId: messageId,
      targetType: "chat",
      previewText: `Reacted ${emoji} to your message`,
      emoji,
      deepLink: `hello://chat/${String(messageRow.conversationId)}`,
      channel: "status_activity",
      priority: "default",
      collapseKey: `reaction_${messageId}`,
    });
  }
  return json(message);
}

async function uploadAttachment(env: Env, request: Request): Promise<Response> {
  const form = await request.formData();
  const fileEntry = form.get("file");
  if (!fileEntry || typeof fileEntry === "string") return badRequest("file is required");
  const file = fileEntry as File;
  const attachmentId = randomId("att");
  const key = `chat/${attachmentId}/${file.name}`;
  await env.TEMP_FILES.put(key, file.stream(), {
    httpMetadata: { contentType: file.type || "application/octet-stream" },
  });
  const now = Date.now();
  const expiresAt = now + ATTACHMENT_TTL_MS;
  const messageId = asString((form.get("messageId") as string | null) || "") || `pending:${attachmentId}`;
  if (messageId.startsWith("pending:")) {
    await env.DB.prepare(
      "INSERT OR IGNORE INTO users (id, display_name, created_at, updated_at) VALUES (?, ?, ?, ?)",
    ).bind(PENDING_ATTACHMENT_USER_ID, "System attachment upload", now, now).run();
    await env.DB.prepare(
      `
        INSERT OR IGNORE INTO conversations (id, type, title, created_by, created_at, updated_at)
        VALUES (?, 'group', 'Pending attachment uploads', ?, ?, ?)
      `,
    ).bind(PENDING_ATTACHMENT_CONVERSATION_ID, PENDING_ATTACHMENT_USER_ID, now, now).run();
    await env.DB.prepare(
      "INSERT OR IGNORE INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?, ?, 'member', ?)",
    ).bind(PENDING_ATTACHMENT_CONVERSATION_ID, PENDING_ATTACHMENT_USER_ID, now).run();
    await env.DB.prepare(
      `
        INSERT OR IGNORE INTO messages (id, conversation_id, sender_id, body, message_type, created_at)
        VALUES (?, ?, ?, '', 'attachment_pending', ?)
      `,
    ).bind(messageId, PENDING_ATTACHMENT_CONVERSATION_ID, PENDING_ATTACHMENT_USER_ID, now).run();
  }
  await env.DB.prepare(
    `
      INSERT INTO attachments (id, message_id, r2_key, file_name, mime_type, size_bytes, expires_at, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `,
  ).bind(
    attachmentId,
    messageId,
    key,
    file.name,
    file.type || "application/octet-stream",
    file.size,
    expiresAt,
    now,
  ).run();
  return json({
    id: attachmentId,
    url: `/api/chat/attachments/${encodeURIComponent(attachmentId)}`,
    mimeType: file.type || "application/octet-stream",
    originalName: file.name,
    size: file.size,
    expiresAt,
  }, { status: 201 });
}

async function fetchAttachment(env: Env, attachmentId: string): Promise<Response> {
  const row = await env.DB.prepare(
    "SELECT r2_key AS r2Key, file_name AS fileName, mime_type AS mimeType, expires_at AS expiresAt FROM attachments WHERE id = ?",
  ).bind(attachmentId).first<any>();
  if (!row) return notFound(`/api/chat/attachments/${attachmentId}`);
  if (Number(row.expiresAt) < Date.now()) return json({ ok: false, error: "attachment_expired" }, { status: 410 });
  const object = await env.TEMP_FILES.get(row.r2Key);
  if (!object) return notFound(`/api/chat/attachments/${attachmentId}`);
  const headers = new Headers();
  headers.set("content-type", row.mimeType || "application/octet-stream");
  headers.set("content-disposition", `inline; filename="${String(row.fileName).replace(/"/g, "")}"`);
  headers.set("access-control-allow-origin", "*");
  return new Response(object.body, { headers });
}

async function deleteR2Prefix(bucket: R2Bucket, prefix: string): Promise<number> {
  let cursor: string | undefined;
  let deleted = 0;
  do {
    const listed = await bucket.list({ prefix, cursor, limit: 1000 });
    const keys = listed.objects.map((object) => object.key);
    if (keys.length > 0) {
      await bucket.delete(keys);
      deleted += keys.length;
    }
    cursor = listed.truncated ? listed.cursor : undefined;
  } while (cursor);
  return deleted;
}

async function resetCloudDevData(env: Env, request: Request): Promise<Response> {
  const enabled = env.ENABLE_DEV_RESET === "true";
  const expectedSecret = env.DEV_RESET_SECRET || "";
  const providedSecret = request.headers.get("x-dev-reset-secret") || "";
  if (!enabled || !expectedSecret || providedSecret !== expectedSecret) {
    return json({ ok: false, error: "Forbidden" }, { status: 403 });
  }

  await ensureCloudAccountSchema(env);
  const tables = [
    "notification_events",
    "message_receipts",
    "attachments",
    "messages",
    "conversation_preferences",
    "conversation_members",
    "conversations",
    "contacts",
    "user_chat_preferences",
    "call_events",
    "call_participants",
    "call_sessions",
    "sessions",
    "devices",
    "device_push_tokens",
    "user_profiles",
    "users",
  ];
  const deletedRows: Record<string, number> = {};
  for (const table of tables) {
    const result = await env.DB.prepare(`DELETE FROM ${table}`).run();
    deletedRows[table] = result.meta?.changes || 0;
  }

  const deletedR2 = {
    chat: await deleteR2Prefix(env.TEMP_FILES, "chat/"),
    avatars: await deleteR2Prefix(env.TEMP_FILES, "avatars/"),
  };

  return json({
    ok: true,
    deletedRows,
    deletedR2,
    r2Prefixes: ["chat/", "avatars/"],
    note: "Drive and PC files are not stored in this Worker and were not touched.",
  });
}

async function sendDevTestPush(env: Env, request: Request): Promise<Response> {
  const enabled = env.ENABLE_DEV_RESET === "true";
  const expectedSecret = env.DEV_RESET_SECRET || "";
  const providedSecret = request.headers.get("x-dev-reset-secret") || "";
  if (!enabled || !expectedSecret || providedSecret !== expectedSecret) {
    return json({ ok: false, error: "Forbidden" }, { status: 403 });
  }
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  const body = await readJson(request);
  const targetUserId = asString(body.userId || body.targetUserId, auth.userId);
  const requestedChannel = asString(body.channel, "messages");
  const channel = ([
    "calls",
    "missed_calls",
    "messages",
    "mentions",
    "status_posts",
    "status_activity",
    "system",
    "re_engagement",
  ].includes(requestedChannel) ? requestedChannel : "messages") as NotificationChannel;
  const requestedPriority = asString(body.priority, channel === "calls" ? "urgent" : "high");
  const priority = (["urgent", "high", "default", "low"].includes(requestedPriority) ? requestedPriority : "high") as NotificationPriority;
  const type = asString(body.type, channel === "calls" ? "call_incoming" : "message");
  const targetId = asString(body.targetId, `dev_push_${Date.now()}`);
  const senderId = asString(body.senderId, "system_push_test");
  const extraCallPayload = channel === "calls" ? {
    callId: targetId,
    chatId: asString(body.chatId, `dev_chat_${auth.userId}`),
    callerId: senderId,
    calleeId: targetUserId,
    isVideo: asBoolean(body.isVideo, false),
  } : {};
  await emitUserNotification(env, targetUserId, {
    type,
    senderId,
    senderName: asString(body.senderName, "Hello test"),
    senderAvatar: null,
    targetId,
    targetType: asString(body.targetType, channel === "calls" ? "call" : "system"),
    groupName: null,
    previewText: asString(body.previewText, "This is a test push from Hello"),
    emoji: asString(body.emoji, ""),
    deepLink: asString(body.deepLink, "hello://notifications"),
    channel,
    priority,
    collapseKey: asString(body.collapseKey, `test_${auth.userId}_${channel}`),
    ...extraCallPayload,
  });
  return json({
    ok: true,
    userId: targetUserId,
    channel,
    priority,
    note: "Test push queued directly for the requested user through the same Android FCM path as production events.",
  });
}

async function repairDirectConversations(env: Env, request: Request): Promise<Response> {
  const enabled = env.ENABLE_DEV_RESET === "true";
  const expectedSecret = env.DEV_RESET_SECRET || "";
  const providedSecret = request.headers.get("x-dev-reset-secret") || "";
  if (!enabled || !expectedSecret || providedSecret !== expectedSecret) {
    return json({ ok: false, error: "Forbidden" }, { status: 403 });
  }

  await ensureCloudAccountSchema(env);
  const conversationRows = await env.DB.prepare(
    `
      SELECT c.id, c.created_at AS createdAt, c.updated_at AS updatedAt,
        COUNT(m.id) AS messageCount
      FROM conversations c
      LEFT JOIN messages m ON m.conversation_id = c.id
      WHERE c.type = 'direct'
      GROUP BY c.id
    `,
  ).all<any>();
  const memberRows = await env.DB.prepare(
    `
      SELECT conversation_id AS conversationId, user_id AS userId
      FROM conversation_members
      ORDER BY conversation_id, user_id
    `,
  ).all<any>();
  const membersByConversation = new Map<string, string[]>();
  for (const row of memberRows.results || []) {
    const list = membersByConversation.get(row.conversationId) || [];
    list.push(row.userId);
    membersByConversation.set(row.conversationId, list);
  }

  const groups = new Map<string, any[]>();
  for (const row of conversationRows.results || []) {
    const directKey = directKeyForUsers(membersByConversation.get(row.id) || []);
    if (!directKey) continue;
    await env.DB.prepare("UPDATE conversations SET direct_key = ? WHERE id = ?")
      .bind(directKey, row.id)
      .run()
      .catch(() => undefined);
    const list = groups.get(directKey) || [];
    list.push({ ...row, directKey });
    groups.set(directKey, list);
  }

  let pairsFixed = 0;
  let conversationsDeleted = 0;
  let messagesMoved = 0;
  const details: JsonObject[] = [];

  for (const [directKey, conversations] of groups) {
    if (conversations.length <= 1) continue;
    pairsFixed += 1;
    const sorted = conversations.sort((a, b) => {
      const byMessages = Number(b.messageCount || 0) - Number(a.messageCount || 0);
      if (byMessages !== 0) return byMessages;
      return Number(a.createdAt || 0) - Number(b.createdAt || 0);
    });
    const canonical = sorted[0];
    const duplicates = sorted.slice(1);

    for (const duplicate of duplicates) {
      const move = await env.DB.prepare("UPDATE messages SET conversation_id = ? WHERE conversation_id = ?")
        .bind(canonical.id, duplicate.id)
        .run();
      messagesMoved += move.meta?.changes || 0;
      await env.DB.prepare("DELETE FROM conversation_preferences WHERE conversation_id = ?").bind(duplicate.id).run().catch(() => undefined);
      await env.DB.prepare("DELETE FROM conversation_members WHERE conversation_id = ?").bind(duplicate.id).run();
      const deleted = await env.DB.prepare("DELETE FROM conversations WHERE id = ?").bind(duplicate.id).run();
      conversationsDeleted += deleted.meta?.changes || 0;
    }

    const latest = await env.DB.prepare(
      `
        SELECT COALESCE(MAX(created_at), ?) AS updatedAt
        FROM messages
        WHERE conversation_id = ?
      `,
    ).bind(canonical.updatedAt || Date.now(), canonical.id).first<any>();
    await env.DB.prepare("UPDATE conversations SET direct_key = ?, updated_at = ? WHERE id = ?")
      .bind(directKey, latest?.updatedAt || Date.now(), canonical.id)
      .run();
    details.push({
      directKey,
      canonicalConversationId: canonical.id,
      deletedConversationIds: duplicates.map((item) => item.id).join(","),
    });
  }

  const uniqueIndex = await env.DB.prepare(
    `
      CREATE UNIQUE INDEX IF NOT EXISTS ux_conversations_direct_key
      ON conversations (direct_key)
      WHERE type = 'direct' AND direct_key IS NOT NULL
    `,
  ).run().then(() => true).catch(() => false);

  return json({
    ok: true,
    pairsFixed,
    conversationsDeleted,
    messagesMoved,
    uniqueIndex,
    details,
    note: "Drive and PC files were not touched.",
  });
}

async function getBindingDebug(env: Env): Promise<Response> {
  const d1 = {
    binding: "DB",
    database: "hello_chat_db",
    available: !!env.DB,
    queryOk: false,
  };
  const r2 = {
    binding: "TEMP_FILES",
    bucket: "hello-chat-temp",
    available: !!env.TEMP_FILES,
    listOk: false,
  };
  const durableObject = {
    binding: "REALTIME_ROOM",
    available: !!env.REALTIME_ROOM,
    idCreated: false,
  };

  try {
    const result = await env.DB.prepare("SELECT 1 AS ok").first<{ ok: number }>();
    d1.queryOk = result?.ok === 1;
  } catch {
    d1.queryOk = false;
  }

  try {
    await env.TEMP_FILES.list({ limit: 1 });
    r2.listOk = true;
  } catch {
    r2.listOk = false;
  }

  try {
    env.REALTIME_ROOM.idFromName("debug");
    durableObject.idCreated = true;
  } catch {
    durableObject.idCreated = false;
  }

  return json({
    ok: true,
    service: "hello-chat-worker",
    bindings: {
      d1,
      r2,
      durableObject,
    },
    note: "No secrets, tokens, object keys, or database contents are returned.",
  });
}

export class RealtimeRoom {
  private readonly sockets = new Set<WebSocket>();

  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env,
  ) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return json({ ok: true });
    }

    if (url.pathname === "/relay" && request.method === "POST") {
      const body = await readJson(request);
      this.broadcast(asString(body.event, "call:event"), (body.payload && typeof body.payload === "object" ? body.payload : {}) as JsonObject);
      return json({ ok: true, sockets: this.sockets.size });
    }

    if (url.pathname.endsWith("/health")) {
      return json({
        ok: true,
        service: "hello-realtime-room",
        storage: "durable-object-placeholder",
      });
    }

    if (request.headers.get("upgrade")?.toLowerCase() === "websocket") {
      return this.handleWebSocket(request, url);
    }

    return json(
      {
        ok: true,
        service: "hello-realtime-room",
        message: "Realtime chat/call Durable Object is available for call signaling WebSockets.",
      },
    );
  }

  private async handleWebSocket(request: Request, url: URL): Promise<Response> {
    const token = url.searchParams.get("token") || bearerToken(request);
    if (!token) return json({ ok: false, error: "Unauthorized" }, { status: 401 });
    const auth = await authenticatedUser(this.env, new Request(request.url, { headers: { authorization: `Bearer ${token}` } }));
    if (!auth) return json({ ok: false, error: "Unauthorized" }, { status: 401 });

    const callMatch = url.pathname.match(/^\/api\/calls\/([^/]+)\/ws$/);
    if (callMatch) {
      const call = await callRowFor(this.env, decodeURIComponent(callMatch[1]));
      if (!call) return notFound(url.pathname);
      if (!(await isCallParticipant(this.env, call.id, auth.userId))) {
        return json({ ok: false, error: "Forbidden" }, { status: 403 });
      }
    }

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    server.accept();
    this.sockets.add(server);
    await touchPresence(this.env, auth);
    server.send(JSON.stringify({ event: "connected", payload: { userId: auth.userId, at: Date.now() } }));
    await broadcastUserPresence(this.env, auth.userId, true);
    const deliveredByConversation = await markUndeliveredMessagesDelivered(this.env, auth.userId);
    await Promise.all(Object.entries(deliveredByConversation).map(([conversationId, messageIds]) =>
      broadcastMessageAndChatUpdates(this.env, conversationId, messageIds),
    ));
    await this.sendPresenceSnapshot(server);
    server.addEventListener("message", (event) => {
      this.handleClientMessage(auth.userId, String(event.data)).catch((error) => {
        server.send(JSON.stringify({ event: "error", payload: { error: error?.message || "signaling_error" } }));
      });
    });
    server.addEventListener("close", () => {
      this.sockets.delete(server);
      if (this.sockets.size === 0) {
        broadcastUserPresence(this.env, auth.userId, false).catch(() => undefined);
      }
    });
    server.addEventListener("error", () => {
      this.sockets.delete(server);
      if (this.sockets.size === 0) {
        broadcastUserPresence(this.env, auth.userId, false).catch(() => undefined);
      }
    });
    return new Response(null, { status: 101, webSocket: client });
  }

  private async handleClientMessage(userId: string, raw: string): Promise<void> {
    const data = JSON.parse(raw);
    const event = callEventName(String(data.event || data.type || ""));
    const inputPayload = data.payload && typeof data.payload === "object" ? data.payload : data;
    const payload = inputPayload as JsonObject;
    if (event === "identify" || event === "online" || event === "user_presence") {
      const auth = await authenticatedUser(this.env, new Request("https://internal", {
        headers: { authorization: `Bearer ${asString(payload.token, "")}` },
      })).catch(() => null);
      const at = Date.now();
      const deviceId = auth?.userId === userId ? auth.deviceId : null;
      await touchPresence(this.env, { userId, sessionId: auth?.sessionId || "", deviceId }, at);
      await broadcastUserPresence(this.env, userId, payload.online !== false);
      return;
    }
    if (event === "mark_messages_read") {
      const chatId = asString(payload.chatId || payload.conversationId, "");
      if (!chatId) return;
      const messageIds = await markConversationMessagesRead(this.env, chatId, userId);
      await broadcastMessageAndChatUpdates(this.env, chatId, messageIds);
      return;
    }
    if (event === "join_chat" || event === "leave_chat") return;
    if (event === "typing") {
      const chatId = asString(payload.chatId || payload.conversationId, "");
      if (!chatId) return;
      const memberIds = await conversationMemberIds(this.env, chatId);
      if (!memberIds.includes(userId)) return;
      const outgoing = {
        chatId,
        senderId: userId,
        senderName: asString(payload.senderName || payload.name, userId),
        isTyping: payload.isTyping !== false,
        timestamp: Date.now(),
      };
      await Promise.all(memberIds
        .filter((memberId) => memberId !== userId)
        .map((memberId) => broadcastToDurableUser(this.env, memberId, "user_typing", outgoing)));
      return;
    }
    const callId = asString(payload.callId || payload.roomId || payload.id, "");
    if (!callId) return;
    const call = await callRowFor(this.env, callId);
    if (!call) return;
    if (!(await isCallParticipant(this.env, callId, userId))) return;
    const explicitTargetId = asString(payload.toUserId, "");
    const targetId = explicitTargetId || (call.mode === "group" ? "" : (userId === call.callerId ? call.calleeId : call.callerId));
    const outgoing = {
      ...payload,
      callId,
      roomId: asString(payload.roomId, callId),
      chatId: asString(payload.chatId, call.chatId),
      callerId: asString(payload.callerId, call.callerId),
      calleeId: asString(payload.calleeId, call.calleeId),
      fromUserId: userId,
      toUserId: targetId,
      type: asString(payload.type, call.type || "audio"),
      isVideo: payload.isVideo === true || call.type === "video",
      eventId: asString(payload.eventId, "") || randomId("evt"),
      timestamp: Date.now(),
      event,
    };
    await relayCallEvent(this.env, event, outgoing);
  }

  private async sendPresenceSnapshot(socket: WebSocket): Promise<void> {
    const userIds = await recentOnlineUserIds(this.env);
    for (const userId of userIds) {
      const payload = await userPresencePayload(this.env, userId, true);
      socket.send(JSON.stringify({ event: "user_presence", payload }));
    }
  }

  private broadcast(event: string, payload: JsonObject): void {
    const message = JSON.stringify({ event, payload });
    for (const socket of [...this.sockets]) {
      try {
        socket.send(message);
      } catch {
        this.sockets.delete(socket);
      }
    }
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return json({ ok: true });
    }

    if (url.pathname === "/" || url.pathname === "/health") {
      return json({
        ok: true,
        service: "hello-chat-worker",
        status: "running",
        bindings: {
          d1: "hello_chat_db",
          r2: "hello-chat-temp",
          durableObject: "REALTIME_ROOM",
        },
        note: "Drive media remains on the PC backend and is not stored in this Worker.",
      });
    }

    if (url.pathname === "/debug/bindings" && env.ENABLE_DEBUG_BINDINGS === "true") {
      return getBindingDebug(env);
    }

    if (url.pathname === "/api/dev/reset-cloud" && request.method === "POST") {
      return resetCloudDevData(env, request);
    }

    if (url.pathname === "/api/dev/test-push" && request.method === "POST") {
      await ensureCloudAccountSchema(env);
      return sendDevTestPush(env, request);
    }

    if (url.pathname === "/api/dev/repair-direct-conversations" && request.method === "POST") {
      return repairDirectConversations(env, request);
    }

    if (url.pathname.startsWith("/rooms/")) {
      const roomName = decodeURIComponent(url.pathname.split("/")[2] || "default");
      const roomId = env.REALTIME_ROOM.idFromName(roomName);
      return env.REALTIME_ROOM.get(roomId).fetch(request);
    }

    if (url.pathname === "/api/calls/ws" && request.headers.get("upgrade")?.toLowerCase() === "websocket") {
      await ensureCloudAccountSchema(env);
      const token = url.searchParams.get("token") || bearerToken(request);
      if (!token) return json({ ok: false, error: "Unauthorized" }, { status: 401 });
      const auth = await authenticatedUser(env, new Request(request.url, { headers: { authorization: `Bearer ${token}` } }));
      if (!auth) return json({ ok: false, error: "Unauthorized" }, { status: 401 });
      const roomId = env.REALTIME_ROOM.idFromName(`user:${auth.userId}`);
      return env.REALTIME_ROOM.get(roomId).fetch(request);
    }

    const callWsMatch = url.pathname.match(/^\/api\/calls\/([^/]+)\/ws$/);
    if (callWsMatch && request.headers.get("upgrade")?.toLowerCase() === "websocket") {
      await ensureCloudAccountSchema(env);
      const roomId = env.REALTIME_ROOM.idFromName(`call:${decodeURIComponent(callWsMatch[1])}`);
      return env.REALTIME_ROOM.get(roomId).fetch(request);
    }

    if (url.pathname.startsWith("/api/")) {
      await ensureCloudAccountSchema(env);
    }

    if (url.pathname === "/api/chat/users/upsert" && request.method === "POST") {
      return upsertUser(env, await readJson(request));
    }

    if (url.pathname === "/api/auth/register" && request.method === "POST") {
      return registerUser(env, await readJson(request), request);
    }

    if (url.pathname === "/api/auth/login" && request.method === "POST") {
      return loginUser(env, await readJson(request));
    }

    if (url.pathname === "/api/auth/logout" && request.method === "POST") {
      return logoutUser(env, request);
    }

    if (url.pathname === "/api/auth/me" && request.method === "GET") {
      return currentUser(env, request);
    }

    if (url.pathname === "/api/register" && request.method === "POST") {
      return registerUser(env, await readJson(request), request);
    }

    if (url.pathname === "/api/user-question" && request.method === "GET") {
      return getUserQuestion(env, url);
    }

    if (url.pathname === "/api/login" && request.method === "POST") {
      return loginUser(env, await readJson(request));
    }

    if (url.pathname === "/api/users" && request.method === "GET") {
      return listUsers(env, url);
    }

    const cloudProfileMatch = url.pathname.match(/^\/api\/users\/([^/]+)\/profile$/);
    if (cloudProfileMatch && request.method === "PATCH") {
      return updateUserProfile(env, request, decodeURIComponent(cloudProfileMatch[1]));
    }

    const cloudUserMatch = url.pathname.match(/^\/api\/users\/([^/]+)$/);
    if (cloudUserMatch && request.method === "GET") {
      return getChatUser(env, decodeURIComponent(cloudUserMatch[1]));
    }

    if (cloudUserMatch && request.method === "PATCH") {
      return updateUserProfile(env, request, decodeURIComponent(cloudUserMatch[1]));
    }

    const cloudAvatarMatch = url.pathname.match(/^\/api\/users\/([^/]+)\/avatar$/);
    if (cloudAvatarMatch && request.method === "POST") {
      return uploadUserAvatar(env, request, decodeURIComponent(cloudAvatarMatch[1]));
    }

    if (cloudAvatarMatch && request.method === "GET") {
      return fetchUserAvatar(env, decodeURIComponent(cloudAvatarMatch[1]));
    }

    const themeAssetMatch = url.pathname.match(/^\/api\/theme-assets\/([^/]+)$/);
    if (themeAssetMatch && (request.method === "GET" || request.method === "HEAD")) {
      return fetchThemeAsset(env, decodeURIComponent(themeAssetMatch[1]), request.method === "HEAD");
    }

    if (url.pathname === "/api/contacts" && request.method === "GET") {
      return listContacts(env, request);
    }

    if (url.pathname === "/api/contacts" && request.method === "POST") {
      return addContact(env, request);
    }

    if (url.pathname === "/api/preferences/chat" && request.method === "GET") {
      return getChatPreferences(env, request);
    }

    if (url.pathname === "/api/preferences/chat" && request.method === "PATCH") {
      return updateChatPreferences(env, request);
    }

    if (url.pathname === "/api/devices/register" && request.method === "POST") {
      return registerDevicePushToken(env, request);
    }

    const deviceDeleteMatch = url.pathname.match(/^\/api\/devices\/([^/]+)$/);
    if (deviceDeleteMatch && request.method === "DELETE") {
      return deleteDevicePushToken(env, request, decodeURIComponent(deviceDeleteMatch[1]));
    }

    if (url.pathname === "/api/calls/start" && request.method === "POST") {
      return startCall(env, request);
    }

    if (url.pathname === "/api/statuses" && request.method === "GET") {
      return statusApi.getLegacyStatuses(env, url);
    }

    if (url.pathname === "/api/statuses" && request.method === "POST") {
      return statusApi.createLegacyStatus(env, request);
    }

    const legacyStatusViewMatch = url.pathname.match(/^\/api\/statuses\/([^/]+)\/view$/);
    if (legacyStatusViewMatch && request.method === "POST") {
      return statusApi.viewLegacyStatus(env, request, decodeURIComponent(legacyStatusViewMatch[1]));
    }

    if (url.pathname === "/api/files/upload" && request.method === "POST") {
      return uploadAttachment(env, request);
    }

    if (url.pathname === "/api/status/feed" && request.method === "GET") {
      const auth = await requireAuth(env, request);
      if (auth instanceof Response) return auth;
      return statusApi.getStatusFeed(env, url, auth);
    }

    if (url.pathname === "/api/status/media" && request.method === "POST") {
      const auth = await requireAuth(env, request);
      if (auth instanceof Response) return auth;
      return statusApi.uploadStatusMedia(env, request, auth);
    }

    if (url.pathname === "/api/status" && request.method === "POST") {
      const auth = await requireAuth(env, request);
      if (auth instanceof Response) return auth;
      return statusApi.createStatus(env, request, auth);
    }

    const statusActionMatch = url.pathname.match(/^\/api\/status\/([^/]+)\/(view|react|reply)$/);
    if (statusActionMatch && request.method === "POST") {
      const auth = await requireAuth(env, request);
      if (auth instanceof Response) return auth;
      const statusId = decodeURIComponent(statusActionMatch[1]);
      const action = statusActionMatch[2];
      if (action === "view") return statusApi.viewStatus(env, request, auth, statusId);
      if (action === "react") return statusApi.reactStatus(env, request, auth, statusId);
      return statusApi.replyStatus(env, request, auth, statusId);
    }

    const statusDeleteMatch = url.pathname.match(/^\/api\/status\/([^/]+)$/);
    if (statusDeleteMatch && request.method === "DELETE") {
      const auth = await requireAuth(env, request);
      if (auth instanceof Response) return auth;
      return statusApi.deleteStatus(env, request, auth, decodeURIComponent(statusDeleteMatch[1]));
    }

    if (url.pathname === "/api/status/archive/pending" && request.method === "GET") {
      const auth = await requireAuth(env, request);
      if (auth instanceof Response) return auth;
      return statusApi.getArchivePending(env, url, auth);
    }

    if (url.pathname === "/api/status/archive/ack" && request.method === "POST") {
      const auth = await requireAuth(env, request);
      if (auth instanceof Response) return auth;
      return statusApi.ackArchive(env, request, auth);
    }

    if (url.pathname === "/api/calls/group/start" && request.method === "POST") {
      return startGroupCall(env, request);
    }

    const groupJoinMatch = url.pathname.match(/^\/api\/calls\/group\/([^/]+)\/join$/);
    if (groupJoinMatch && request.method === "POST") {
      return joinGroupCall(env, request, decodeURIComponent(groupJoinMatch[1]));
    }

    const groupLeaveMatch = url.pathname.match(/^\/api\/calls\/group\/([^/]+)\/leave$/);
    if (groupLeaveMatch && request.method === "POST") {
      return leaveGroupCall(env, request, decodeURIComponent(groupLeaveMatch[1]));
    }

    if (url.pathname === "/api/calls/history" && request.method === "GET") {
      return callHistory(env, request, url);
    }

    if ((url.pathname === "/api/calls/ice-config" || url.pathname === "/api/calls/ice-servers") && request.method === "GET") {
      return iceConfig(env);
    }

    const callActionMatch = url.pathname.match(/^\/api\/calls\/([^/]+)\/(accept|reject|end)$/);
    if (callActionMatch && request.method === "POST") {
      return updateCallFromRest(env, request, decodeURIComponent(callActionMatch[1]), callActionMatch[2]);
    }

    const callGetMatch = url.pathname.match(/^\/api\/calls\/([^/]+)$/);
    if (callGetMatch && request.method === "GET") {
      return getCall(env, request, decodeURIComponent(callGetMatch[1]));
    }

    if (url.pathname === "/api/chat/users" && request.method === "GET") {
      return listUsers(env, url);
    }

    const userMatch = url.pathname.match(/^\/api\/chat\/users\/([^/]+)$/);
    if (userMatch && request.method === "GET") {
      return getChatUser(env, decodeURIComponent(userMatch[1]));
    }

    if (url.pathname === "/api/chat/users/avatar" && request.method === "POST") {
      return uploadUserAvatar(env, request);
    }

    const avatarMatch = url.pathname.match(/^\/api\/chat\/users\/([^/]+)\/avatar$/);
    if (avatarMatch && request.method === "GET") {
      return fetchUserAvatar(env, decodeURIComponent(avatarMatch[1]));
    }

    if (url.pathname === "/api/chat/conversations" && request.method === "GET") {
      return listConversations(env, url);
    }

    if (url.pathname === "/api/chat/conversations" && request.method === "POST") {
      return createConversation(env, await readJson(request));
    }

    if (url.pathname === "/api/chat/conversations/direct" && request.method === "POST") {
      return createConversation(env, { ...(await readJson(request)), type: "direct" });
    }

    const conversationMessagesMatch = url.pathname.match(/^\/api\/chat\/conversations\/([^/]+)\/messages$/);
    if (conversationMessagesMatch && request.method === "GET") {
      const conversationId = decodeURIComponent(conversationMessagesMatch[1]);
      const limit = Math.min(Math.max(Number(url.searchParams.get("limit") || 50), 1), 100);
      const offset = Math.max(Number(url.searchParams.get("offset") || 0), 0);
      return json(await messagesFor(env, conversationId, limit, offset));
    }

    if (conversationMessagesMatch && request.method === "POST") {
      const conversationId = decodeURIComponent(conversationMessagesMatch[1]);
      return createMessage(env, conversationId, await readJson(request));
    }

    const readMatch = url.pathname.match(/^\/api\/chat\/messages\/([^/]+)\/read$/);
    if (readMatch && request.method === "POST") {
      return markMessageRead(env, decodeURIComponent(readMatch[1]), await readJson(request));
    }

    const reactMatch = url.pathname.match(/^\/api\/chat\/messages\/([^/]+)\/react$/);
    if (reactMatch && request.method === "POST") {
      return reactToMessage(env, decodeURIComponent(reactMatch[1]), await readJson(request));
    }

    const conversationReactMatch = url.pathname.match(/^\/api\/chat\/conversations\/([^/]+)\/messages\/([^/]+)\/react$/);
    if (conversationReactMatch && request.method === "POST") {
      return reactToMessage(env, decodeURIComponent(conversationReactMatch[2]), await readJson(request));
    }

    if (url.pathname === "/api/chat/attachments/upload" && request.method === "POST") {
      return uploadAttachment(env, request);
    }

    const attachmentMatch = url.pathname.match(/^\/api\/chat\/attachments\/([^/]+)$/);
    if (attachmentMatch && request.method === "GET") {
      return fetchAttachment(env, decodeURIComponent(attachmentMatch[1]));
    }

    if (url.pathname === "/chat/bootstrap") {
      return json({
        ok: true,
        message: "Chat API is live under /api/chat. Drive media remains on the PC backend.",
      });
    }

    if (url.pathname === "/call/bootstrap") {
      return json({
        ok: true,
        message: "Call signaling placeholder. Add Durable Object room signaling and TURN/SFU integration here.",
      });
    }

    if (url.pathname === "/files/bootstrap") {
      return json({
        ok: true,
        message: "Temporary chat attachment placeholder. Use TEMP_FILES R2 only for expiring chat files.",
      });
    }

    return notFound(url.pathname);
  },
};
