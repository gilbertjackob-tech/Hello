export interface Env {
  DB: D1Database;
  TEMP_FILES: R2Bucket;
  REALTIME_ROOM: DurableObjectNamespace;
  ENABLE_DEBUG_BINDINGS?: string;
}

type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
type JsonObject = { [key: string]: JsonValue };

const ATTACHMENT_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const PENDING_ATTACHMENT_USER_ID = "system_attachment_upload";
const PENDING_ATTACHMENT_CONVERSATION_ID = "system_attachment_uploads";
let cloudSchemaReady: Promise<void> | null = null;

function json(body: JsonValue, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  headers.set("content-type", "application/json; charset=utf-8");
  headers.set("access-control-allow-origin", "*");
  headers.set("access-control-allow-methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
  headers.set("access-control-allow-headers", "content-type,authorization");
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
    online: false,
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
        COALESCE(p.updated_at, u.updated_at) AS updatedAt
      FROM users u
      LEFT JOIN user_profiles p ON p.user_id = u.id
      WHERE u.id = ?
    `,
  ).bind(userId).first<any>();
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

async function authenticatedUser(env: Env, request: Request): Promise<{ userId: string; sessionId: string } | null> {
  const token = bearerToken(request);
  if (!token) return null;
  const now = Date.now();
  const row = await env.DB.prepare(
    `
      SELECT id, user_id AS userId
      FROM sessions
      WHERE token_hash = ?
        AND revoked_at IS NULL
        AND (expires_at IS NULL OR expires_at > ?)
    `,
  ).bind(await sha256Hex(token), now).first<any>();
  return row ? { userId: row.userId, sessionId: row.id } : null;
}

async function requireAuth(env: Env, request: Request): Promise<{ userId: string; sessionId: string } | Response> {
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
    })();
  }
  return cloudSchemaReady;
}

async function userFor(env: Env, userId: string): Promise<any> {
  const row = await userProfileFor(env, userId);
  return row ? publicUser(row) : null;
}

async function getChatUser(env: Env, userId: string): Promise<Response> {
  const user = await userFor(env, userId);
  if (!user) return json({ ok: false, error: "User not found" }, { status: 404 });
  return json({
    ...user,
    online: false,
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

  const url = new URL(request.url);
  const avatarUrl = `${url.origin}/api/users/${encodeURIComponent(userId)}/avatar`;
  const now = Date.now();
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

async function conversationFor(env: Env, conversationId: string, viewerId?: string): Promise<JsonObject | null> {
  const row = await env.DB.prepare(
    `
      SELECT c.id, c.type, c.title, c.updated_at AS updatedAt,
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
      FROM conversation_members cm
      JOIN users u ON u.id = cm.user_id
      WHERE cm.conversation_id = ?
      ORDER BY cm.joined_at ASC
    `,
  ).bind(conversationId).all<any>();
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

  return {
    id: row.id,
    name: row.title || directOther?.name || participants.map((member: any) => member.name).join(", ") || "Cloud chat",
    avatar: directOther?.avatar || null,
    lastMessage: row.lastMessage || "",
    lastMessageTime: row.lastMessageTime || row.updatedAt,
    unreadCount: unread?.total || 0,
    isGroup: row.type === "group",
    members: participants.map((member: any) => member.id),
    participants,
  };
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

  return (rows.results || []).reverse().map((row: any) => ({
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
    status: "sent",
    isDeleted: !!row.deletedAt,
  }));
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
            p.about, p.profile_status AS profileStatus, COALESCE(p.updated_at, u.updated_at) AS updatedAt
          FROM users u
          LEFT JOIN user_profiles p ON p.user_id = u.id
          WHERE LOWER(COALESCE(p.display_name, u.display_name)) LIKE LOWER(?)
             OR LOWER(COALESCE(p.username, '')) LIKE LOWER(?)
          ORDER BY COALESCE(p.display_name, u.display_name) ASC
          LIMIT 50
        `,
      ).bind(`%${query}%`, `%${query}%`)
    : env.DB.prepare(
        `
          SELECT u.id, COALESCE(p.display_name, u.display_name) AS displayName,
            p.username, p.phone, p.email, COALESCE(p.avatar_url, u.avatar_url) AS avatarUrl,
            p.about, p.profile_status AS profileStatus, COALESCE(p.updated_at, u.updated_at) AS updatedAt
          FROM users u
          LEFT JOIN user_profiles p ON p.user_id = u.id
          ORDER BY COALESCE(p.display_name, u.display_name) ASC
          LIMIT 50
        `,
      );
  const rows = await statement.all<any>();
  return json((rows.results || []).map(publicUser));
}

async function logoutUser(env: Env, request: Request): Promise<Response> {
  const auth = await requireAuth(env, request);
  if (auth instanceof Response) return auth;
  await env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE id = ?").bind(Date.now(), auth.sessionId).run();
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
  const conversations = await Promise.all((rows.results || []).map((row: any) => conversationFor(env, row.id, userId)));
  return json(conversations.filter(Boolean) as JsonObject[]);
}

async function createConversation(env: Env, body: JsonObject): Promise<Response> {
  const id = asString(body.id) || randomId("conv");
  const isGroup = body.isGroup === true || body.type === "group";
  const type = isGroup ? "group" : "direct";
  const title = asString(body.title || body.name, "");
  const createdBy = asString(body.createdBy || body.currentUserId || body.userId, "");
  const memberIds = asStringList(body.memberIds || body.members).concat(createdBy ? [createdBy] : []).filter(Boolean);
  const uniqueMemberIds = [...new Set(memberIds)];
  if (uniqueMemberIds.length === 0) return badRequest("at least one member is required");

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
      INSERT INTO conversations (id, type, title, created_by, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        title = COALESCE(NULLIF(excluded.title, ''), conversations.title),
        updated_at = excluded.updated_at
    `,
  ).bind(id, type, title || null, createdBy || null, now, now).run();
  for (const memberId of uniqueMemberIds) {
    await env.DB.prepare(
      "INSERT OR IGNORE INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?, ?, 'member', ?)",
    ).bind(id, memberId, now).run();
  }
  const conversation = await conversationFor(env, id, createdBy || uniqueMemberIds[0]);
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
  await env.DB.prepare(
    "INSERT OR IGNORE INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?, ?, 'member', ?)",
  ).bind(conversationId, senderId, now).run();

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

  const message = (await messagesFor(env, conversationId, 1, 0)).find((item) => item.id === messageId) || {
    id: messageId,
    chatId: conversationId,
    senderId,
    senderName,
    senderAvatar: senderAvatar || null,
    text,
    timestamp: now,
    status: "sent",
  };
  return json(message, { status: 201 });
}

async function markMessageRead(env: Env, messageId: string, body: JsonObject): Promise<Response> {
  const userId = asString(body.userId || body.readerId);
  if (!userId) return badRequest("userId is required");
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
  return json({ ok: true, messageId, userId, readAt: now });
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
  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env,
  ) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return json({ ok: true });
    }

    if (url.pathname.endsWith("/health")) {
      return json({
        ok: true,
        service: "hello-realtime-room",
        storage: "durable-object-placeholder",
      });
    }

    return json(
      {
        ok: true,
        service: "hello-realtime-room",
        message: "Realtime chat/call Durable Object placeholder. WebSocket signaling will be added here later.",
      },
    );
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

    if (url.pathname.startsWith("/rooms/")) {
      const roomName = decodeURIComponent(url.pathname.split("/")[2] || "default");
      const roomId = env.REALTIME_ROOM.idFromName(roomName);
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
