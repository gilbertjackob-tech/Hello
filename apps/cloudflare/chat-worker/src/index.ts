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

async function userFor(env: Env, userId: string): Promise<any> {
  return env.DB.prepare(
    "SELECT id, display_name AS name, avatar_url AS avatar FROM users WHERE id = ?",
  ).bind(userId).first();
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
  return json({ id, name, displayName: name, avatar: avatar || null, avatarUrl: avatar || null });
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

    if (url.pathname === "/api/chat/users/upsert" && request.method === "POST") {
      return upsertUser(env, await readJson(request));
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
