import express from "express";
import { Server as SocketIOServer } from "socket.io";
import { createServer, type Server as HttpServer } from "http";
import path from "path";
import fs from "fs";
import Database from "better-sqlite3";
import multer from "multer";
import cors from "cors";

// Environment variables
const HOST = process.env.HOST || "0.0.0.0";
const PORT = Number(process.env.PORT || 3000);
let DB_PATH =
  process.env.DATABASE_PATH || path.join(process.cwd(), "data", "app.db");
let UPLOAD_DIR =
  process.env.UPLOAD_DIR || path.join(process.cwd(), "uploads");
let FAMILY_DRIVE_DIR =
  process.env.FAMILY_DRIVE_DIR || path.join(process.cwd(), "data", "family-drive");
let HELLO_ROOT = process.cwd();
let HELLO_API_PATH = "/api";
let db: any;
let upload: any;
let driveUpload: any;

export interface MountHelloOptions {
  basePath?: string;
  apiPath?: string;
  socketPath?: string;
  uploadsPath?: string;
  familyDrivePath?: string;
  dbPath?: string;
  dataDir?: string;
  helloRoot?: string;
  serveFrontend?: boolean;
}

function normalizeBasePath(basePath: string | undefined) {
  if (!basePath || basePath === "/") return "";
  return basePath.startsWith("/") ? basePath.replace(/\/+$/, "") : `/${basePath.replace(/\/+$/, "")}`;
}

function normalizeMountPath(mountPath: string | undefined, fallback: string) {
  const value = mountPath || fallback;
  return value.startsWith("/") ? value : `/${value}`;
}

function initializeHelloRuntime(options: MountHelloOptions = {}) {
  const dataDir = options.dataDir || path.dirname(options.dbPath || DB_PATH);
  HELLO_ROOT = options.helloRoot || HELLO_ROOT;
  DB_PATH = options.dbPath || process.env.DATABASE_PATH || path.join(dataDir, "app.db");
  UPLOAD_DIR = options.uploadsPath || process.env.UPLOAD_DIR || path.join(dataDir, "uploads");
  FAMILY_DRIVE_DIR = options.familyDrivePath || process.env.FAMILY_DRIVE_DIR || path.join(dataDir, "family-drive");
  HELLO_API_PATH = normalizeMountPath(options.apiPath, "/api");

  if (!fs.existsSync(path.dirname(DB_PATH))) {
    fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });
  }
  if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
  }
  if (!fs.existsSync(FAMILY_DRIVE_DIR)) {
    fs.mkdirSync(FAMILY_DRIVE_DIR, { recursive: true });
  }

  db = new Database(DB_PATH);

  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      name TEXT,
      securityQuestion TEXT,
      securityAnswer TEXT,
      avatar TEXT,
      phone TEXT,
      lastActive INTEGER,
      lastActivePrivacy TEXT
    );

    CREATE TABLE IF NOT EXISTS chats (
      id TEXT PRIMARY KEY,
      name TEXT,
      avatar TEXT,
      lastMessage TEXT,
      lastMessageTime INTEGER,
      unreadCount INTEGER,
      isGroup INTEGER
    );

    CREATE TABLE IF NOT EXISTS chat_members (
      chatId TEXT,
      userId TEXT,
      PRIMARY KEY (chatId, userId)
    );
    
    CREATE TABLE IF NOT EXISTS chat_deleted_for (
      chatId TEXT,
      userId TEXT,
      PRIMARY KEY (chatId, userId)
    );

    CREATE TABLE IF NOT EXISTS chat_read_state (
      chatId TEXT,
      userId TEXT,
      lastReadAt INTEGER,
      PRIMARY KEY (chatId, userId)
    );

    CREATE TABLE IF NOT EXISTS messages (
      id TEXT PRIMARY KEY,
      chatId TEXT,
      senderId TEXT,
      senderName TEXT,
      senderAvatar TEXT,
      text TEXT,
      timestamp INTEGER,
      attachmentUrl TEXT,
      attachmentType TEXT,
      attachmentName TEXT,
      attachmentSize INTEGER,
      status TEXT,
      pinnedUntil INTEGER,
      isDeleted INTEGER,
      location TEXT,
      replyTo TEXT
    );

    CREATE TABLE IF NOT EXISTS reactions (
      messageId TEXT,
      emoji TEXT,
      userId TEXT,
      PRIMARY KEY (messageId, emoji, userId)
    );

    CREATE TABLE IF NOT EXISTS message_starred_by (
      messageId TEXT,
      userId TEXT,
      PRIMARY KEY (messageId, userId)
    );

    CREATE TABLE IF NOT EXISTS message_deleted_for (
      messageId TEXT,
      userId TEXT,
      PRIMARY KEY (messageId, userId)
    );

    CREATE TABLE IF NOT EXISTS file_attachments (
      fileId TEXT PRIMARY KEY,
      originalName TEXT,
      storedName TEXT,
      mimeType TEXT,
      size INTEGER,
      path TEXT,
      uploaderId TEXT,
      createdAt INTEGER
    );

    CREATE TABLE IF NOT EXISTS drive_items (
      id TEXT PRIMARY KEY,
      originalName TEXT,
      storedName TEXT,
      mimeType TEXT,
      type TEXT,
      size INTEGER,
      path TEXT,
      uploaderId TEXT,
      createdAt INTEGER,
      monthKey TEXT,
      deletedAt INTEGER
    );

    CREATE INDEX IF NOT EXISTS idx_drive_items_latest
      ON drive_items (deletedAt, createdAt DESC, id DESC);

    CREATE INDEX IF NOT EXISTS idx_drive_items_month
      ON drive_items (deletedAt, monthKey, createdAt DESC);

    CREATE TABLE IF NOT EXISTS call_logs (
      id TEXT PRIMARY KEY,
      roomId TEXT,
      mode TEXT,
      callerId TEXT,
      calleeId TEXT,
      chatId TEXT,
      type TEXT,
      status TEXT,
      participantIds TEXT,
      createdAt INTEGER,
      startedAt INTEGER,
      ringingAt INTEGER,
      acceptedAt INTEGER,
      connectedAt INTEGER,
      answeredAt INTEGER,
      endedAt INTEGER,
      durationSeconds INTEGER,
      endReason TEXT,
      endedBy TEXT
    );

    CREATE TABLE IF NOT EXISTS call_rooms (
      id TEXT PRIMARY KEY,
      chatId TEXT,
      hostId TEXT,
      mode TEXT,
      type TEXT,
      status TEXT,
      participantIds TEXT,
      maxParticipants INTEGER,
      createdAt INTEGER,
      endedAt INTEGER,
      endedBy TEXT
    );

    CREATE TABLE IF NOT EXISTS statuses (
      id TEXT PRIMARY KEY,
      userId TEXT,
      text TEXT,
      attachmentUrl TEXT,
      attachmentType TEXT,
      backgroundColor TEXT,
      duration INTEGER,
      timestamp INTEGER
    );

    CREATE TABLE IF NOT EXISTS status_views (
      statusId TEXT,
      userId TEXT,
      timestamp INTEGER,
      PRIMARY KEY (statusId, userId)
    );
  `);

  [
    ["roomId", "TEXT"],
    ["mode", "TEXT"],
    ["participantIds", "TEXT"],
    ["endedBy", "TEXT"],
  ].forEach(([column, definition]) => ensureColumn("call_logs", column, definition));

  try { db.prepare("ALTER TABLE users ADD COLUMN email TEXT").run(); } catch(e){}
  try { db.prepare("ALTER TABLE users ADD COLUMN phone TEXT").run(); } catch(e){}
  try { db.prepare("ALTER TABLE users ADD COLUMN avatar TEXT").run(); } catch(e){}
  try { db.prepare("ALTER TABLE users ADD COLUMN lastActive INTEGER").run(); } catch(e){}
  try { db.prepare("ALTER TABLE users ADD COLUMN lastActivePrivacy TEXT").run(); } catch(e){}
  try { db.prepare("ALTER TABLE messages ADD COLUMN attachmentName TEXT").run(); } catch(e){}
  try { db.prepare("ALTER TABLE messages ADD COLUMN attachmentSize INTEGER").run(); } catch(e){}
  try { db.prepare("ALTER TABLE call_logs ADD COLUMN createdAt INTEGER").run(); } catch(e){}
  try { db.prepare("ALTER TABLE call_logs ADD COLUMN ringingAt INTEGER").run(); } catch(e){}
  try { db.prepare("ALTER TABLE call_logs ADD COLUMN acceptedAt INTEGER").run(); } catch(e){}
  try { db.prepare("ALTER TABLE call_logs ADD COLUMN connectedAt INTEGER").run(); } catch(e){}
  try { db.prepare("ALTER TABLE call_logs ADD COLUMN endReason TEXT").run(); } catch(e){}

  try { db.prepare("ALTER TABLE drive_items ADD COLUMN monthKey TEXT").run(); } catch(e){}
  try { db.prepare("ALTER TABLE drive_items ADD COLUMN deletedAt INTEGER").run(); } catch(e){}
  try { db.prepare("CREATE INDEX IF NOT EXISTS idx_drive_items_latest ON drive_items (deletedAt, createdAt DESC, id DESC)").run(); } catch(e){}
  try { db.prepare("CREATE INDEX IF NOT EXISTS idx_drive_items_month ON drive_items (deletedAt, monthKey, createdAt DESC)").run(); } catch(e){}

  const storage = multer.diskStorage({
    destination: (_req, _file, cb) => {
      cb(null, UPLOAD_DIR);
    },
    filename: (_req, file, cb) => {
      const ext = path.extname(file.originalname);
      const safeName = sanitizeFilename(path.basename(file.originalname, ext));
      cb(null, `${safeName}-${Date.now()}${ext}`);
    },
  });
  upload = multer({ storage });

  const driveStorage = multer.diskStorage({
    destination: (_req, _file, cb) => {
      const now = new Date();
      const year = String(now.getFullYear());
      const month = String(now.getMonth() + 1).padStart(2, "0");
      const targetDir = path.join(FAMILY_DRIVE_DIR, year, month);
      fs.mkdirSync(targetDir, { recursive: true });
      cb(null, targetDir);
    },
    filename: (_req, file, cb) => {
      const ext = path.extname(file.originalname);
      const safeName = sanitizeFilename(path.basename(file.originalname, ext)) || "drive-media";
      const random = Math.random().toString(36).slice(2, 8);
      cb(null, `${safeName}-${Date.now()}-${random}${ext}`);
    },
  });

  driveUpload = multer({
    storage: driveStorage,
    limits: {
      // Enough for family videos, but still prevents accidental huge uploads.
      fileSize: Number(process.env.FAMILY_DRIVE_MAX_FILE_MB || 512) * 1024 * 1024,
      files: Number(process.env.FAMILY_DRIVE_MAX_FILES || 50),
    },
    fileFilter: (_req, file, cb) => {
      if (file.mimetype.startsWith("image/") || file.mimetype.startsWith("video/")) {
        cb(null, true);
        return;
      }
      cb(new Error("Only photos and videos are allowed in Drive"));
    },
  });
}

function ensureColumn(table: string, column: string, definition: string) {
  const columns = db.prepare(`PRAGMA table_info(${table})`).all() as any[];
  if (!columns.some((c) => c.name === column)) {
    db.prepare(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`).run();
  }
}

// Helper to sanitize filenames
function sanitizeFilename(name: string) {
  return name.replace(/[^a-zA-Z0-9.-]/g, "_");
}

function sendStoredFile(fileId: string, res: express.Response) {
  const file = db
    .prepare("SELECT * FROM file_attachments WHERE fileId = ?")
    .get(fileId) as any;
  if (!file || !fs.existsSync(file.path)) {
    res.status(404).json({ error: "File not found" });
    return;
  }

  res.setHeader("Content-Type", file.mimeType);
  res.sendFile(path.resolve(file.path));
}

function getDriveMonthKey(timestamp: number) {
  const d = new Date(timestamp);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function getDriveMonthLabel(monthKey: string) {
  const [year, month] = monthKey.split("-").map(Number);
  const date = new Date(year || 1970, Math.max((month || 1) - 1, 0), 1);
  return date.toLocaleString("en-US", { month: "long", year: "numeric" });
}

function driveItemToResponse(row: any) {
  const monthKey = row.monthKey || getDriveMonthKey(Number(row.createdAt || Date.now()));
  return {
    id: row.id,
    url: `${HELLO_API_PATH}/drive/items/${row.id}/file`,
    thumbnailUrl: `${HELLO_API_PATH}/drive/items/${row.id}/file`,
    originalName: row.originalName,
    mimeType: row.mimeType,
    type: row.type,
    size: Number(row.size || 0),
    uploaderId: row.uploaderId,
    createdAt: Number(row.createdAt || 0),
    monthKey,
    monthLabel: getDriveMonthLabel(monthKey),
  };
}

function sendDriveItemFile(itemId: string, res: express.Response) {
  const item = db
    .prepare("SELECT * FROM drive_items WHERE id = ? AND deletedAt IS NULL")
    .get(itemId) as any;
  if (!item || !fs.existsSync(item.path)) {
    res.status(404).json({ error: "Drive item not found" });
    return;
  }

  res.setHeader("Content-Type", item.mimeType);
  res.setHeader("Cache-Control", "public, max-age=31536000, immutable");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.sendFile(path.resolve(item.path));
}

const connectedSockets = new Map<string, string>();

function loadChats() {
  const chats = db.prepare("SELECT * FROM chats").all() as any[];
  const allUsers = db.prepare("SELECT id, name, avatar, phone, email, lastActive, lastActivePrivacy FROM users").all() as any[];
  
  for (const chat of chats) {
    chat.isGroup = !!chat.isGroup;
    
    // Get member user IDs
    const memberIds = db
      .prepare("SELECT userId FROM chat_members WHERE chatId = ?")
      .all(chat.id)
      .map((r: any) => r.userId);
      
    chat.members = memberIds;
      
    // Map to full user objects
    chat.participants = memberIds.map(uid => {
      const user = allUsers.find(u => u.id === uid) || { id: uid, name: "Unknown User" };
      // Omit sensitive data if any
      delete user.securityAnswer;
      // Add online status based on connectedSockets
      return {
        ...user,
        online: Array.from(connectedSockets.values()).includes(uid),
        privacy: user.lastActivePrivacy || "everyone"
      };
    });

    chat.deletedFor = db
      .prepare("SELECT userId FROM chat_deleted_for WHERE chatId = ?")
      .all(chat.id)
      .map((r: any) => r.userId);
  }
  return chats;
}

function readStateFor(chatId: string, userId: string) {
  return db
    .prepare("SELECT lastReadAt FROM chat_read_state WHERE chatId = ? AND userId = ?")
    .get(chatId, userId) as { lastReadAt?: number } | undefined;
}

function upsertReadState(chatId: string, userId: string, lastReadAt: number) {
  db.prepare(
    `
      INSERT INTO chat_read_state (chatId, userId, lastReadAt)
      VALUES (?, ?, ?)
      ON CONFLICT(chatId, userId)
      DO UPDATE SET lastReadAt = MAX(COALESCE(chat_read_state.lastReadAt, 0), excluded.lastReadAt)
    `,
  ).run(chatId, userId, lastReadAt);
}

function withUnreadCount(chat: any, userId?: string | null) {
  if (!chat || !userId) return chat;
  const lastReadAt = Number(readStateFor(chat.id, userId)?.lastReadAt || 0);
  const row = db
    .prepare(
      "SELECT COUNT(*) as total FROM messages WHERE chatId = ? AND senderId != ? AND timestamp > ? AND COALESCE(isDeleted, 0) = 0",
    )
    .get(chat.id, userId, lastReadAt) as { total?: number };
  return {
    ...chat,
    unreadCount: Number(row?.total || 0),
  };
}

function loadChatForUser(chatId: string, userId: string) {
  return loadChats()
    .filter((chat: any) => chat.members?.includes(userId))
    .map((chat: any) => withUnreadCount(chat, userId))
    .find((chat: any) => chat.id === chatId);
}

function loadUsers() {
  return db.prepare("SELECT * FROM users").all();
}

function loadMessages(chatId?: string) {
  let msgs;
  if (chatId) {
    msgs = db
      .prepare("SELECT * FROM messages WHERE chatId = ?")
      .all(chatId) as any[];
  } else {
    msgs = db.prepare("SELECT * FROM messages").all() as any[];
  }

  for (const msg of msgs) {
    msg.isDeleted = !!msg.isDeleted;
    msg.location = msg.location ? JSON.parse(msg.location) : undefined;
    msg.replyTo = msg.replyTo ? JSON.parse(msg.replyTo) : undefined;
    msg.reactions = db
      .prepare("SELECT emoji, userId FROM reactions WHERE messageId = ?")
      .all(msg.id);
    msg.starredBy = db
      .prepare("SELECT userId FROM message_starred_by WHERE messageId = ?")
      .all(msg.id)
      .map((r: any) => r.userId);
    msg.deletedFor = db
      .prepare("SELECT userId FROM message_deleted_for WHERE messageId = ?")
      .all(msg.id)
      .map((r: any) => r.userId);
  }
  return msgs;
}

function isUserOnline(userId: string) {
  return Array.from(connectedSockets.values()).includes(userId);
}

function getCall(callId: string) {
  return db.prepare("SELECT * FROM call_logs WHERE id = ?").get(callId) as any;
}

function parseJsonArray(value: any): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(String(value));
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function getRoom(roomId: string) {
  const room = db.prepare("SELECT * FROM call_rooms WHERE id = ?").get(roomId) as any;
  if (!room) return null;
  return {
    ...room,
    maxParticipants: room.maxParticipants || 4,
    participantIds: parseJsonArray(room.participantIds),
  };
}

function getChatMemberIds(chatId: string) {
  return db
    .prepare("SELECT userId FROM chat_members WHERE chatId = ?")
    .all(chatId)
    .map((r: any) => String(r.userId));
}

function isMember(chatId: string, userId: string) {
  return !!db
    .prepare("SELECT 1 FROM chat_members WHERE chatId = ? AND userId = ?")
    .get(chatId, userId);
}

function getCallHistoryForUser(userId: string) {
  const calls = db
    .prepare(
      "SELECT * FROM call_logs WHERE callerId = ? OR calleeId = ? ORDER BY createdAt DESC, startedAt DESC LIMIT 50",
    )
    .all(userId, userId) as any[];
  const users = db
    .prepare(
      "SELECT id, name, avatar, phone, email, lastActive, lastActivePrivacy FROM users",
    )
    .all() as any[];
  const usersById = new Map(users.map((user) => [user.id, user]));

  return calls.map((call) => {
    const direction = call.callerId === userId ? "outgoing" : "incoming";
    const otherUserId = direction === "outgoing" ? call.calleeId : call.callerId;
    const other = usersById.get(otherUserId) || {
      id: otherUserId,
      name: "Unknown User",
    };

    return {
      id: call.id,
      roomId: call.roomId,
      mode: call.mode || "direct",
      participantIds: parseJsonArray(call.participantIds),
      endedBy: call.endedBy,
      chatId: call.chatId,
      callerId: call.callerId,
      calleeId: call.calleeId,
      type: call.type,
      direction,
      status: call.status,
      startedAt: call.startedAt,
      ringingAt: call.ringingAt,
      acceptedAt: call.acceptedAt || call.answeredAt,
      connectedAt: call.connectedAt,
      endedAt: call.endedAt,
      durationSeconds: call.durationSeconds,
      endReason: call.endReason,
      otherUser: {
        id: other.id,
        name: other.name,
        avatar: other.avatar,
        phone: other.phone,
        email: other.email,
        online: isUserOnline(other.id),
        lastActive: other.lastActive,
        privacy: other.lastActivePrivacy || "everyone",
      },
    };
  });
}

function validateCallSignal(data: any): { ok: true; call: any } | { ok: false; reason: string } {
  if (!data?.callId || !data?.chatId || !data?.fromUserId || !data?.toUserId) {
    return { ok: false, reason: "missing_required_fields" };
  }

  const call = getCall(String(data.callId));
  if (!call) return { ok: false, reason: "call_not_found" };
  if (call.chatId !== data.chatId) return { ok: false, reason: "chat_mismatch" };

  const participantIds = [call.callerId, call.calleeId];
  if (!participantIds.includes(data.fromUserId)) {
    return { ok: false, reason: "invalid_from_user" };
  }
  if (!participantIds.includes(data.toUserId)) {
    return { ok: false, reason: "invalid_to_user" };
  }
  if (data.fromUserId === data.toUserId) {
    return { ok: false, reason: "same_from_to_user" };
  }
  if (!isMember(data.chatId, data.fromUserId) || !isMember(data.chatId, data.toUserId)) {
    return { ok: false, reason: "not_chat_members" };
  }

  return { ok: true, call };
}

const recentCallEventIds = new Map<string, number>();
const CALL_EVENT_TTL_MS = 5 * 60 * 1000;

function createCallEventId() {
  return `evt_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`;
}

function normalizeCallSignal(event: string, data: any, call: any) {
  return {
    ...data,
    eventId: data.eventId || createCallEventId(),
    callId: data.callId,
    chatId: data.chatId,
    fromUserId: data.fromUserId,
    toUserId: data.toUserId,
    callerId: data.callerId || call.callerId,
    calleeId: data.calleeId || call.calleeId,
    type: data.type || call.type || (data.isVideo ? "video" : "audio"),
    timestamp: data.timestamp || Date.now(),
    attempt: data.attempt || 1,
    event,
  };
}

function normalizeServerCallEvent(
  event: string,
  data: any,
  call: any,
  reason: string,
  toUserId?: string,
  fromUserId?: string,
) {
  return {
    eventId: createCallEventId(),
    callId: call?.id || data?.callId || "unknown_call",
    chatId: call?.chatId || data?.chatId || "unknown_chat",
    fromUserId: fromUserId || data?.fromUserId || "server",
    toUserId: toUserId || data?.fromUserId || data?.callerId || data?.calleeId || "unknown_user",
    callerId: call?.callerId || data?.callerId || data?.fromUserId || "unknown_caller",
    calleeId: call?.calleeId || data?.calleeId || data?.toUserId || "unknown_callee",
    type: call?.type || data?.type || (data?.isVideo ? "video" : "audio"),
    timestamp: Date.now(),
    attempt: Number(data?.attempt || 1),
    event,
    reason,
  };
}

function isDuplicateCallEvent(data: any) {
  const eventId = String(data?.eventId || "");
  if (!eventId) return false;
  const now = Date.now();
  for (const [id, seenAt] of recentCallEventIds) {
    if (now - seenAt > CALL_EVENT_TTL_MS) recentCallEventIds.delete(id);
  }
  if (recentCallEventIds.has(eventId)) return true;
  recentCallEventIds.set(eventId, now);
  return false;
}

function getIceServersFromEnv() {
  const splitUrls = (value?: string) =>
    String(value || "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  const iceServers: any[] = [];
  const stunUrls = splitUrls(process.env.WEBRTC_STUN_URLS || "stun:stun.l.google.com:19302");
  if (stunUrls.length) iceServers.push({ urls: stunUrls });
  const turnUrls = splitUrls(process.env.WEBRTC_TURN_URLS);
  if (turnUrls.length) {
    iceServers.push({
      urls: turnUrls,
      username: process.env.WEBRTC_TURN_USERNAME || "",
      credential: process.env.WEBRTC_TURN_CREDENTIAL || "",
    });
  }
  return {
    iceServers,
    turnConfigured: turnUrls.length > 0,
    stunUrls,
    turnUrls,
  };
}

function finalStatusFromReason(reason: string, wasConnected: boolean) {
  if (wasConnected) return "ended";
  if (reason === "cancelled" || reason === "ended_by_caller") return "cancelled";
  if (reason === "busy") return "busy";
  if (reason === "missed" || reason === "no_answer") return "missed";
  if (reason === "declined") return "declined";
  if (reason === "unavailable") return "unavailable";
  if (
    reason === "failed" ||
    reason === "network_lost" ||
    reason === "connection_timeout" ||
    reason === "permission_denied" ||
    reason === "camera_unavailable" ||
    reason === "microphone_unavailable"
  ) return "failed";
  return "ended";
}

export async function mountHello(
  hostApp: any,
  httpServer: HttpServer,
  options: MountHelloOptions = {},
) {
  const basePath = normalizeBasePath(options.basePath);
  const socketPath = normalizeMountPath(options.socketPath, basePath ? `${basePath}/socket.io` : "/socket.io");
  const frontendMountPath = basePath || "/";
  initializeHelloRuntime(options);

  const app = express.Router();

  app.use((_req, res, next) => {
    res.setHeader(
      "Permissions-Policy",
      "camera=(self), microphone=(self), fullscreen=(self), display-capture=(self)",
    );
    next();
  });

  if (process.env.CORS_ORIGIN) {
    app.use(cors({ origin: process.env.CORS_ORIGIN }));
  } else {
    app.use(cors());
  }

  app.use(express.json({ limit: "50mb" }));
  app.use(express.urlencoded({ limit: "50mb", extended: true }));
  app.use("/uploads", express.static(UPLOAD_DIR));

  if (basePath) {
    hostApp.use("/uploads", express.static(UPLOAD_DIR));
    hostApp.get("/api/files/:fileId", (req: express.Request, res: express.Response) => {
      sendStoredFile(req.params.fileId, res);
    });
    hostApp.get("/api/drive/items/:itemId/file", (req: express.Request, res: express.Response) => {
      sendDriveItemFile(req.params.itemId, res);
    });
  }

  const io = new SocketIOServer(httpServer, {
    path: socketPath,
    cors: { origin: "*", methods: ["GET", "POST"] },
  });

  io.on("connection", (socket) => {
    socket.on("identify", (userId: string) => {
      connectedSockets.set(socket.id, userId);
      socket.join(userId);
      const allChats = loadChats();
      const userChats = allChats.filter((c: any) => c.members?.includes(userId));
      userChats.forEach((c: any) => socket.join(c.id));

      const user = db
        .prepare("SELECT * FROM users WHERE id = ?")
        .get(userId) as any;
      if (user) {
        db.prepare("UPDATE users SET lastActive = ? WHERE id = ?").run(
          Date.now(),
          userId,
        );
        io.emit("presence_updated", {
          userId,
          online: true,
          lastActive: Date.now(),
          privacy: user.lastActivePrivacy || "everyone",
        });

        const messages = loadMessages();
        const userChats = loadChats();
        messages.forEach((m: any) => {
          if (m.status === "sent" && m.senderId !== userId) {
            const chat = userChats.find((c) => c.id === m.chatId);
            if (
              chat &&
              (chat.members?.includes(userId) || chat.id === "global")
            ) {
              db.prepare(
                "UPDATE messages SET status = 'delivered' WHERE id = ?",
              ).run(m.id);
              m.status = "delivered";
              io.to(m.chatId).emit("message_updated", m);
            }
          }
        });
      }
    });

    socket.on("join_chat", (chatId: string) => socket.join(chatId));
    socket.on("leave_chat", (chatId: string) => socket.leave(chatId));

    socket.on("typing", (data) =>
      socket.to(data.chatId).emit("user_typing", data),
    );
    const failCallSignal = (data: any, reason = "invalid_signal") => {
      const call = data?.callId ? getCall(String(data.callId)) : null;
      const targetUserId = data?.fromUserId || data?.callerId || data?.calleeId || call?.callerId;
      const payload = normalizeServerCallEvent(
        "call:failed",
        data,
        call,
        reason,
        targetUserId,
        "server",
      );
      console.log(`[CALL_TRACE] blocked event=call:failed reason=${reason} callId=${payload.callId || data?.callId || "unknown"}`);
      if (targetUserId) io.in(targetUserId).emit("call:failed", payload);
      else socket.emit("call:failed", payload);
    };

    const emitCallHistoryUpdated = (call: any, status: string) => {
      if (!call?.callerId || !call?.calleeId) return;
      const payload = {
        callId: call.id,
        chatId: call.chatId,
        callerId: call.callerId,
        calleeId: call.calleeId,
        status,
      };
      io.in(call.callerId).emit("call:history-updated", payload);
      io.in(call.calleeId).emit("call:history-updated", payload);
    };

    const routeValidatedSignal = (event: string, data: any) => {
      const validation = validateCallSignal(data);
      if ("reason" in validation) {
        console.log(`[CALL_TRACE] blocked event=${event} reason=${validation.reason} callId=${data?.callId || data?.id || "unknown"}`);
        failCallSignal(data, validation.reason);
        return null;
      }
      const payload = normalizeCallSignal(event, data, validation.call);
      if (isDuplicateCallEvent(payload)) {
        console.log("[WEBRTC_SIGNAL_DUPLICATE]", {
          event,
          eventId: payload.eventId,
          callId: payload.callId,
        });
        return validation.call;
      }

      console.log(`[CALL_TRACE] route ${event} from=${payload.fromUserId} to=${payload.toUserId} callId=${payload.callId} chatId=${payload.chatId} hasOfferSdp=${!!payload.offer?.sdp} hasAnswerSdp=${!!payload.answer?.sdp} hasIce=${!!payload.candidate?.candidate}`);
      io.in(payload.toUserId).emit(event, payload);
      return validation.call;
    };

    // Modern WebRTC signaling
    socket.on("call:start", (data) => {
      console.log(`[CALL_TRACE] route call:start from=${data?.fromUserId} to=${data?.toUserId} callId=${data?.callId || "unknown"}`);
      const validation = validateCallSignal(data);
      if (!validation.ok) {
        failCallSignal(data);
        return;
      }

      if (!isUserOnline(data.toUserId)) {
        const now = Date.now();
        db.prepare(
          "UPDATE call_logs SET status = ?, endedAt = ?, endReason = ? WHERE id = ?",
        ).run("unavailable", now, "unavailable", data.callId);
        io.in(data.fromUserId).emit(
          "call:unavailable",
          normalizeServerCallEvent("call:unavailable", data, validation.call, "unavailable", data.fromUserId, data.toUserId),
        );
        emitCallHistoryUpdated(validation.call, "unavailable");
        return;
      }

      db.prepare(
        "UPDATE call_logs SET status = ?, startedAt = COALESCE(startedAt, ?), createdAt = COALESCE(createdAt, ?) WHERE id = ?",
      ).run("outgoing_calling", Date.now(), Date.now(), data.callId);
      routeValidatedSignal("call:start", data);
    });

    socket.on("call:offer", (data) => routeValidatedSignal("call:offer", data));
    socket.on("call:answer", (data) => routeValidatedSignal("call:answer", data));
    socket.on("call:ice-candidate", (data) =>
      routeValidatedSignal("call:ice-candidate", data),
    );
    socket.on("call:reconnecting", (data) =>
      routeValidatedSignal("call:reconnecting", data),
    );
    socket.on("call:ack", (data) => {
      if (!data?.eventId || !data?.callId || !data?.fromUserId || !data?.toUserId) return;
      console.log("[CALL_TRACE]", {
        side: "server",
        action: "ack",
        eventId: data.eventId,
        callId: data.callId,
        fromUserId: data.fromUserId,
        toUserId: data.toUserId,
      });
      io.in(data.toUserId).emit("call:ack", {
        ...data,
        status: data.status || "received",
        timestamp: data.timestamp || Date.now(),
      });
    });
    socket.on("call:screen-share-started", (data) =>
      routeValidatedSignal("call:screen-share-started", data),
    );
    socket.on("call:screen-share-stopped", (data) =>
      routeValidatedSignal("call:screen-share-stopped", data),
    );
    socket.on("call:media-state", (data) =>
      routeValidatedSignal("call:media-state", data),
    );

    socket.on("call:room-created", (data) => {
      const actorId = String(data?.fromUserId || data?.userId || "");
      const room = getRoom(String(data?.roomId || data?.id || data?.room?.id || ""));
      if (!room) return failCallSignal(data, "room_not_found");
      if (!isMember(room.chatId, actorId)) {
        return failCallSignal(data, "not_chat_member");
      }
      socket.join(room.id);
      room.participantIds.forEach((userId: string) => {
        io.in(userId).emit("call:room-created", { ...data, room });
      });
    });

    socket.on("call:room-join", (data) => {
      const actorId = String(data?.fromUserId || data?.userId || "");
      const room = getRoom(String(data?.roomId || ""));
      if (!room) return failCallSignal(data, "room_not_found");
      if (!isMember(room.chatId, actorId)) {
        return failCallSignal(data, "not_chat_member");
      }
      if (!room.participantIds.includes(actorId)) {
        if (room.participantIds.length >= 4) {
          socket.emit("call:room-full", { roomId: room.id, maxParticipants: 4 });
          return;
        }
        const participantIds = [...room.participantIds, actorId];
        db.prepare("UPDATE call_rooms SET status = ?, participantIds = ? WHERE id = ?")
          .run("active", JSON.stringify(participantIds), room.id);
      }
      socket.join(room.id);
      io.in(room.id).emit("call:room-join", { ...data, userId: actorId, room: getRoom(room.id) });
    });

    socket.on("call:room-leave", (data) => {
      const actorId = String(data?.fromUserId || data?.userId || "");
      const room = getRoom(String(data?.roomId || ""));
      if (!room) return;
      socket.leave(room.id);
      const participantIds = room.participantIds.filter((id: string) => id !== actorId);
      const ended = participantIds.length <= 1 || data.end === true || data.ended === true;
      db.prepare("UPDATE call_rooms SET status = ?, participantIds = ?, endedAt = COALESCE(endedAt, ?), endedBy = COALESCE(endedBy, ?) WHERE id = ?")
        .run(ended ? "ended" : "active", JSON.stringify(participantIds), ended ? Date.now() : null, ended ? actorId : null, room.id);
      io.in(room.id).emit("call:room-leave", { ...data, userId: actorId, ended, room: getRoom(room.id) });
    });

    socket.on("call:participant-state", (data) => {
      const actorId = String(data?.fromUserId || data?.userId || "");
      const room = getRoom(String(data?.roomId || ""));
      if (!room || !isMember(room.chatId, actorId)) return;
      io.in(room.id).emit("call:participant-state", { ...data, userId: actorId });
    });

    socket.on("call:stats", (data) => {
      const room = getRoom(String(data?.roomId || ""));
      if (room) io.in(room.id).emit("call:stats", data);
      else if (data.toUserId) io.in(data.toUserId).emit("call:stats", data);
    });

    const routeRoomSignal = (event: string, data: any) => {
      const actorId = String(data?.fromUserId || "");
      const targetId = String(data?.toUserId || "");
      const room = getRoom(String(data?.roomId || ""));
      if (!room || !actorId || !targetId) return failCallSignal(data, "room_signal_invalid");
      if (!room.participantIds.includes(actorId) || !room.participantIds.includes(targetId)) {
        return failCallSignal(data, "room_participant_invalid");
      }
      if (!isMember(room.chatId, actorId) || !isMember(room.chatId, targetId)) {
        return failCallSignal(data, "not_chat_member");
      }
      io.in(targetId).emit(event, data);
    };

    socket.on("call:room-offer", (data) => routeRoomSignal("call:room-offer", data));
    socket.on("call:room-answer", (data) => routeRoomSignal("call:room-answer", data));
    socket.on("call:room-ice-candidate", (data) =>
      routeRoomSignal("call:room-ice-candidate", data),
    );

    socket.on("call:ringing", (data) => {
      console.log(`[CALL_TRACE] route call:ringing from=${data?.fromUserId} to=${data?.toUserId} callId=${data?.callId || "unknown"}`);
      const call = routeValidatedSignal("call:ringing", data);
      if (!call) return;
      db.prepare(
        "UPDATE call_logs SET status = ?, ringingAt = COALESCE(ringingAt, ?) WHERE id = ?",
      ).run("outgoing_ringing", Date.now(), data.callId);
    });

    socket.on("call:accepted", (data) => {
      console.log(`[CALL_TRACE] route call:accepted from=${data?.fromUserId} to=${data?.toUserId} callId=${data?.callId || "unknown"}`);
      const call = routeValidatedSignal("call:accepted", data);
      if (!call) return;
      db.prepare(
        "UPDATE call_logs SET status = ?, acceptedAt = COALESCE(acceptedAt, ?), answeredAt = COALESCE(answeredAt, ?) WHERE id = ?",
      ).run("connecting", Date.now(), Date.now(), data.callId);
    });

    socket.on("call:connected", (data) => {
      console.log(`[CALL_TRACE] route call:connected from=${data?.fromUserId} to=${data?.toUserId} callId=${data?.callId || "unknown"}`);
      const call = routeValidatedSignal("call:connected", data);
      if (!call) return;
      const now = Date.now();
      db.prepare(
        "UPDATE call_logs SET status = ?, connectedAt = COALESCE(connectedAt, ?), acceptedAt = COALESCE(acceptedAt, ?), answeredAt = COALESCE(answeredAt, ?) WHERE id = ?",
      ).run("connected", now, now, now, data.callId);
    });

    socket.on("call:busy", (data) => {
      console.log("[CALL_BUSY]", {
        callId: data?.callId,
        chatId: data?.chatId,
        callerId: data?.callerId,
        calleeId: data?.calleeId,
        fromUserId: data?.fromUserId,
        toUserId: data?.toUserId,
      });
      const validation = validateCallSignal({
        ...data,
        fromUserId: data.fromUserId || data.calleeId,
        toUserId: data.toUserId || data.callerId,
      });
      if (!validation.ok) {
        failCallSignal(data);
        return;
      }
      const now = Date.now();
      db.prepare(
        "UPDATE call_logs SET status = ?, endedAt = ?, endReason = ? WHERE id = ?",
      ).run("busy", now, "busy", data.callId);
      io.in(validation.call.callerId).emit(
        "call:busy",
        normalizeServerCallEvent("call:busy", data, validation.call, "busy", validation.call.callerId, validation.call.calleeId),
      );
      emitCallHistoryUpdated(validation.call, "busy");
    });

    socket.on("call:missed", (data) => {
      console.log("[CALL_MISSED]", {
        callId: data?.callId,
        chatId: data?.chatId,
        callerId: data?.callerId,
        calleeId: data?.calleeId,
        fromUserId: data?.fromUserId,
        toUserId: data?.toUserId,
      });
      const validation = validateCallSignal({
        ...data,
        fromUserId: data.fromUserId || data.callerId,
        toUserId: data.toUserId || data.calleeId,
      });
      if (!validation.ok) {
        failCallSignal(data);
        return;
      }
      if ((data.fromUserId || data.callerId) !== validation.call.callerId) {
        failCallSignal(data);
        return;
      }
      if (validation.call.endedAt) {
        return;
      }
      const now = Date.now();
      db.prepare(
        "UPDATE call_logs SET status = ?, endedAt = ?, endReason = ? WHERE id = ?",
      ).run("missed", now, "no_answer", data.callId);
      io.in(validation.call.callerId).emit(
        "call:missed",
        normalizeServerCallEvent("call:missed", data, validation.call, "no_answer", validation.call.callerId, validation.call.calleeId),
      );
      io.in(validation.call.calleeId).emit(
        "call:missed",
        normalizeServerCallEvent("call:missed", data, validation.call, "no_answer", validation.call.calleeId, validation.call.callerId),
      );
      emitCallHistoryUpdated(validation.call, "missed");
    });

    socket.on("call:unavailable", (data) => {
      const validation = validateCallSignal(data);
      if (!validation.ok) {
        failCallSignal(data);
        return;
      }
      const now = Date.now();
      db.prepare(
        "UPDATE call_logs SET status = ?, endedAt = ?, endReason = ? WHERE id = ?",
      ).run("unavailable", now, "unavailable", data.callId);
      io.in(validation.call.callerId).emit(
        "call:unavailable",
        normalizeServerCallEvent("call:unavailable", data, validation.call, "unavailable", validation.call.callerId, validation.call.calleeId),
      );
      emitCallHistoryUpdated(validation.call, "unavailable");
    });

    socket.on("call:declined", (data) => {
      const validation = validateCallSignal(data);
      if (!validation.ok) {
        failCallSignal(data);
        return;
      }
      const now = Date.now();
      db.prepare(
        "UPDATE call_logs SET status = ?, endedAt = ?, endReason = ? WHERE id = ?",
      ).run("declined", now, "declined", data.callId);
      io.in(validation.call.callerId).emit(
        "call:declined",
        normalizeServerCallEvent("call:declined", data, validation.call, "declined", validation.call.callerId, validation.call.calleeId),
      );
      emitCallHistoryUpdated(validation.call, "declined");
    });

    socket.on("call:reject", (data) => {
      const normalized = {
        ...data,
        fromUserId: data.fromUserId || data.calleeId,
        toUserId: data.toUserId || data.callerId,
      };
      const validation = validateCallSignal(normalized);
      if (!validation.ok) {
        failCallSignal(data);
        return;
      }
      const now = Date.now();
      db.prepare(
        "UPDATE call_logs SET status = ?, endedAt = ?, endReason = ? WHERE id = ?",
      ).run("declined", now, "declined", normalized.callId);
      io.in(validation.call.callerId).emit(
        "call:declined",
        normalizeServerCallEvent("call:declined", normalized, validation.call, "declined", validation.call.callerId, validation.call.calleeId),
      );
      emitCallHistoryUpdated(validation.call, "declined");
    });

    const handleCallEnded = (data: any) => {
      console.log("[CALL_END]", {
        callId: data?.callId,
        chatId: data?.chatId,
        fromUserId: data?.fromUserId,
        toUserId: data?.toUserId,
        reason: data?.reason,
      });
      const call = routeValidatedSignal("call:ended", data);
      if (!call) return;
      const now = Date.now();
      const reason = data.reason || "ended";
      const connectedAt = call.connectedAt || data.connectedAt;
      const wasConnected = !!connectedAt;
      const status = finalStatusFromReason(reason, wasConnected);
      const durationSeconds = wasConnected
        ? Math.max(0, Math.floor((now - Number(connectedAt)) / 1000))
        : null;

      db.prepare(
        "UPDATE call_logs SET status = ?, endedAt = ?, durationSeconds = COALESCE(?, durationSeconds), endReason = ? WHERE id = ?",
      ).run(status, now, durationSeconds, reason, data.callId);
      emitCallHistoryUpdated(call, status);
    };
    socket.on("call:end", handleCallEnded);
    socket.on("call:ended", handleCallEnded);

    socket.on("call:failed", (data) => {
      console.log(`[CALL_TRACE] route call:failed from=${data?.fromUserId} to=${data?.toUserId} callId=${data?.callId || "unknown"} reason=${data?.reason || "failed"}`);
      const call = routeValidatedSignal("call:failed", data);
      if (!call) return;
      const now = Date.now();
      db.prepare(
        "UPDATE call_logs SET status = ?, endedAt = ?, endReason = ? WHERE id = ?",
      ).run("failed", now, data.reason || "failed", data.callId);
      emitCallHistoryUpdated(call, "failed");
    });

    socket.on(
      "mark_messages_read",
      (data: { chatId: string; readerId: string }) => {
        upsertReadState(data.chatId, data.readerId, Date.now());
        const msgs = db
          .prepare(
            "SELECT * FROM messages WHERE chatId = ? AND senderId != ? AND status != 'read'",
          )
          .all(data.chatId, data.readerId) as any[];
        const updateStmt = db.prepare(
          "UPDATE messages SET status = 'read' WHERE id = ?",
        );
        db.transaction(() => {
          msgs.forEach((m) => {
            updateStmt.run(m.id);
            m.status = "read";
            m.location = m.location ? JSON.parse(m.location) : undefined;
            m.replyTo = m.replyTo ? JSON.parse(m.replyTo) : undefined;
            m.reactions = db
              .prepare(
                "SELECT emoji, userId FROM reactions WHERE messageId = ?",
              )
              .all(m.id);
            m.starredBy = db
              .prepare(
                "SELECT userId FROM message_starred_by WHERE messageId = ?",
              )
              .all(m.id)
              .map((r: any) => r.userId);
            m.deletedFor = db
              .prepare(
                "SELECT userId FROM message_deleted_for WHERE messageId = ?",
              )
              .all(m.id)
              .map((r: any) => r.userId);
            io.to(data.chatId).emit("message_updated", m);
          });
        })();
        const personalized = loadChatForUser(data.chatId, data.readerId);
        if (personalized) {
          io.to(data.readerId).emit("chat_updated", personalized);
        }
      },
    );

    socket.on("disconnect", () => {
      const userId = connectedSockets.get(socket.id);
      if (userId) {
        connectedSockets.delete(socket.id);
        const isStillOnline = Array.from(connectedSockets.values()).includes(
          userId,
        );
        if (!isStillOnline) {
          const user = db
            .prepare("SELECT * FROM users WHERE id = ?")
            .get(userId) as any;
          if (user) {
            db.prepare("UPDATE users SET lastActive = ? WHERE id = ?").run(
              Date.now(),
              userId,
            );
            io.emit("presence_updated", {
              userId,
              online: false,
              lastActive: Date.now(),
              privacy: user.lastActivePrivacy || "everyone",
            });
          }
        }
      }
    });

    // Handle any message update emitted directly from clients using API or Socket context
    socket.on(
      "update_message_status",
      (data: { chatId: string; messageId: string; status: string }) => {
        // Some frontends might try to emit updates directly
      },
    );
  });

  // Health check
  app.get("/api/health", (req, res) => {
    res.json({
      ok: true,
      host: HOST,
      port: PORT,
      database: "ok",
      uploadDir: "ok",
    });
  });

  // File Upload API
  app.post("/api/files/upload", upload.single("file"), (req, res) => {
    if (!req.file) {
      res.status(400).json({ error: "No file uploaded" });
      return;
    }
    const uploaderId = req.body.uploaderId || "unknown";
    const fileId = "file_" + Math.random().toString(36).substr(2, 9);

    db.prepare(
      `
      INSERT INTO file_attachments 
      (fileId, originalName, storedName, mimeType, size, path, uploaderId, createdAt)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `,
    ).run(
      fileId,
      req.file.originalname,
      req.file.filename,
      req.file.mimetype,
      req.file.size,
      req.file.path,
      uploaderId,
      Date.now(),
    );

    // Always return relative path so frontend works seamlessly via relative endpoints
    res.json({
      fileId,
      url: `${HELLO_API_PATH}/files/${fileId}`,
      originalName: req.file.originalname,
      mimeType: req.file.mimetype,
      size: req.file.size,
    });
  });

  // Family Drive: central PC-backed photo/video library. No folders, no passwords.
  app.get("/api/drive/items", (req, res) => {
    const limit = Math.min(Math.max(Number(req.query.limit || 60), 1), 120);
    const before = Number(req.query.before || Date.now() + 1);

    const rows = db
      .prepare(
        `
        SELECT * FROM drive_items
        WHERE deletedAt IS NULL AND createdAt < ?
        ORDER BY createdAt DESC, id DESC
        LIMIT ?
      `,
      )
      .all(before, limit + 1) as any[];

    const visibleRows = rows.slice(0, limit);
    const hasMore = rows.length > limit;
    const nextCursor = hasMore && visibleRows.length
      ? Number(visibleRows[visibleRows.length - 1].createdAt)
      : null;
    const totalRow = db
      .prepare("SELECT COUNT(*) as total FROM drive_items WHERE deletedAt IS NULL")
      .get() as { total?: number };

    res.setHeader("Cache-Control", "no-store");
    res.json({
      items: visibleRows.map(driveItemToResponse),
      nextCursor,
      hasMore,
      total: Number(totalRow?.total || 0),
    });
  });

  app.get("/api/drive/months", (_req, res) => {
    const rows = db
      .prepare(
        `
        SELECT monthKey, COUNT(*) as count, MAX(createdAt) as latest
        FROM drive_items
        WHERE deletedAt IS NULL
        GROUP BY monthKey
        ORDER BY latest DESC
      `,
      )
      .all() as any[];

    res.setHeader("Cache-Control", "no-store");
    res.json({
      months: rows.map((row) => ({
        monthKey: row.monthKey,
        monthLabel: getDriveMonthLabel(row.monthKey),
        count: Number(row.count || 0),
        latest: Number(row.latest || 0),
      })),
    });
  });

  app.post("/api/drive/upload", driveUpload.array("files", 50), (req, res) => {
    const files = (req.files || []) as Express.Multer.File[];
    if (!files.length) {
      res.status(400).json({ error: "No photos or videos uploaded" });
      return;
    }

    const uploaderId = req.body.uploaderId || "unknown";
    const insert = db.prepare(
      `
      INSERT INTO drive_items
      (id, originalName, storedName, mimeType, type, size, path, uploaderId, createdAt, monthKey, deletedAt)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
    `,
    );

    const uploadStart = Date.now();
    const uploaded = files.map((file, index) => {
      const id = "drive_" + Math.random().toString(36).slice(2, 11);
      const createdAt = uploadStart + index;
      const type = file.mimetype.startsWith("video/") ? "video" : "image";
      const monthKey = getDriveMonthKey(createdAt);
      insert.run(
        id,
        file.originalname,
        file.filename,
        file.mimetype,
        type,
        file.size,
        file.path,
        uploaderId,
        createdAt,
        monthKey,
      );

      const row = db.prepare("SELECT * FROM drive_items WHERE id = ?").get(id);
      return driveItemToResponse(row);
    });

    res.json({ items: uploaded, count: uploaded.length });
  });

  app.get("/api/drive/items/:itemId/file", (req, res) => {
    sendDriveItemFile(req.params.itemId, res);
  });

  app.delete("/api/drive/items/:itemId", (req, res) => {
    db.prepare("UPDATE drive_items SET deletedAt = ? WHERE id = ?").run(Date.now(), req.params.itemId);
    res.json({ ok: true });
  });

  app.get("/api/files/:fileId", (req, res) => {
    sendStoredFile(req.params.fileId, res);
  });

  app.delete("/api/files/:fileId", (req, res) => {
    const file = db
      .prepare("SELECT * FROM file_attachments WHERE fileId = ?")
      .get(req.params.fileId) as any;
    if (!file) {
      res.status(404).json({ error: "File not found" });
      return;
    }
    if (fs.existsSync(file.path)) {
      fs.unlinkSync(file.path);
    }
    db.prepare("DELETE FROM file_attachments WHERE fileId = ?").run(
      file.fileId,
    );
    res.json({ success: true });
  });

  // General Routes
  app.get("/api/chats/messages/starred", (req, res) => {
    const { userId } = req.query;
    if (!userId) {
      res.status(400).json({ error: "userId query param is required" });
      return;
    }
    const msgs = loadMessages();
    const starredMessages = msgs.filter(
      (m: any) => m.starredBy && m.starredBy.includes(String(userId)),
    );
    res.json(starredMessages);
  });

  app.get("/api/users", (req, res) => {
    const q = req.query.q as string;
    let usersQuery =
      "SELECT id, name, avatar, phone, lastActive, lastActivePrivacy FROM users";
    let users;
    if (q) {
      users = db
        .prepare(
          `${usersQuery} WHERE LOWER(name) LIKE '%' || ? || '%' ORDER BY name ASC`,
        )
        .all(q.toLowerCase());
    } else {
      users = db.prepare(`${usersQuery} ORDER BY name ASC`).all();
    }

    // Add online status
    const result = users.map((u: any) => ({
      ...u,
      online: Array.from(connectedSockets.values()).includes(u.id),
      privacy: u.lastActivePrivacy || "everyone",
    }));

    res.json(result);
  });

  app.get("/api/users/:userId", (req, res) => {
    const user = db
      .prepare("SELECT * FROM users WHERE id = ?")
      .get(req.params.userId) as any;
    if (!user) {
      res.status(404).json({ error: "User not found" });
      return;
    }
    const isOnline = Array.from(connectedSockets.values()).includes(
      req.params.userId,
    );
    res.json({
      id: user.id,
      name: user.name,
      avatar: user.avatar,
      lastActive: user.lastActive,
      online: isOnline,
      privacy: user.lastActivePrivacy || "everyone",
    });
  });

  app.put("/api/users/:userId/profile", (req, res) => {
    const { name, avatar, phone, email } = req.body;
    const userId = req.params.userId;
    const user = db.prepare("SELECT * FROM users WHERE id = ?").get(userId) as any;
    if (!user) {
      res.status(404).json({ error: "User not found" });
      return;
    }
    
    if (name !== undefined) {
      db.prepare("UPDATE users SET name = ? WHERE id = ?").run(name, userId);
      user.name = name;
    }
    if (avatar !== undefined) {
      db.prepare("UPDATE users SET avatar = ? WHERE id = ?").run(avatar, userId);
      user.avatar = avatar;
    }
    if (phone !== undefined) {
      db.prepare("UPDATE users SET phone = ? WHERE id = ?").run(phone, userId);
      user.phone = phone;
    }
    if (email !== undefined) {
      db.prepare("UPDATE users SET email = ? WHERE id = ?").run(email, userId);
      user.email = email;
    }
    
    user.online = Array.from(connectedSockets.values()).includes(userId);
    user.privacy = user.lastActivePrivacy || "everyone";

    io.emit("user_updated", user);
    io.emit("presence_updated", {
      userId,
      online: user.online,
      lastActive: user.lastActive,
      privacy: user.privacy
    });

    res.json(user);
  });

  app.post("/api/users/:userId/privacy", (req, res) => {
    const { lastActivePrivacy } = req.body;
    const userId = req.params.userId;
    const user = db
      .prepare("SELECT * FROM users WHERE id = ?")
      .get(userId) as any;
    if (!user) {
      res.status(404).json({ error: "User not found" });
      return;
    }
    if (lastActivePrivacy) {
      db.prepare("UPDATE users SET lastActivePrivacy = ? WHERE id = ?").run(
        lastActivePrivacy,
        userId,
      );
      const isOnline = Array.from(connectedSockets.values()).includes(userId);
      user.lastActivePrivacy = lastActivePrivacy;
      io.emit("presence_updated", {
        userId,
        online: isOnline,
        lastActive: user.lastActive,
        privacy: user.lastActivePrivacy,
      });
      res.json(user);
    } else {
      res.status(400).json({ error: "Invalid payload" });
    }
  });

  app.post("/api/export", (req, res) => {
    const { userId } = req.body;
    if (!userId) return res.status(400).json({ error: "userId required" });
    const user = db.prepare("SELECT * FROM users WHERE id = ?").get(userId);
    if (!user) return res.status(404).json({ error: "User not found" });

    const chats = loadChats().filter(
      (c) => c.members?.includes(userId) || c.id === "global",
    );
    const chatIds = chats.map((c) => c.id);
    const messages = loadMessages().filter((m) => chatIds.includes(m.chatId));
    res.json({ user, chats, messages });
  });

  app.post("/api/import", (req, res) => {
    const { userId, data } = req.body;
    if (!userId || !data || !data.user)
      return res.status(400).json({ error: "Invalid format" });

    db.transaction(() => {
      const existingUser = db
        .prepare("SELECT id FROM users WHERE id = ?")
        .get(userId);
      if (existingUser) {
        db.prepare(
          "UPDATE users SET name = ?, securityQuestion = ?, securityAnswer = ?, avatar = ?, phone = ?, lastActivePrivacy = ? WHERE id = ?",
        ).run(
          data.user.name,
          data.user.securityQuestion,
          data.user.securityAnswer,
          data.user.avatar,
          data.user.phone,
          data.user.lastActivePrivacy,
          userId,
        );
      } else {
        // If importing completely new user? The flow typically replaces existing.
      }

      const insertChat = db.prepare(
        "INSERT OR REPLACE INTO chats (id, name, avatar, lastMessage, lastMessageTime, unreadCount, isGroup) VALUES (?, ?, ?, ?, ?, ?, ?)",
      );
      const insertMember = db.prepare(
        "INSERT OR IGNORE INTO chat_members (chatId, userId) VALUES (?, ?)",
      );
      if (data.chats) {
        data.chats.forEach((c: any) => {
          insertChat.run(
            c.id,
            c.name,
            c.avatar,
            c.lastMessage,
            c.lastMessageTime,
            c.unreadCount,
            c.isGroup ? 1 : 0,
          );
          if (c.members)
            c.members.forEach((uid: string) => {
              insertMember.run(c.id, uid);
              upsertReadState(c.id, uid, Number(c.lastMessageTime || 0));
            });
        });
      }

      const insertMsg = db.prepare(
        "INSERT OR REPLACE INTO messages (id, chatId, senderId, senderName, senderAvatar, text, timestamp, attachmentUrl, attachmentType, status, pinnedUntil, isDeleted, location, replyTo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
      );
      if (data.messages) {
        data.messages.forEach((m: any) => {
          insertMsg.run(
            m.id,
            m.chatId,
            m.senderId,
            m.senderName,
            m.senderAvatar,
            m.text,
            m.timestamp,
            m.attachmentUrl,
            m.attachmentType,
            m.status,
            m.pinnedUntil,
            m.isDeleted ? 1 : 0,
            m.location ? JSON.stringify(m.location) : null,
            m.replyTo ? JSON.stringify(m.replyTo) : null,
          );
        });
      }
    })();

    res.json({ success: true });
  });

  app.post("/api/register", (req, res) => {
    const { name, securityQuestion, securityAnswer } = req.body;
    if (!name || !securityQuestion || !securityAnswer)
      return res.status(400).json({ error: "Missing fields" });
    const existing = db
      .prepare("SELECT id FROM users WHERE LOWER(name) = LOWER(?)")
      .get(name);
    if (existing) return res.status(400).json({ error: "Username taken" });

    const id = "usr_" + Math.random().toString(36).substr(2, 9);
    const avatar = `https://api.dicebear.com/7.x/avataaars/svg?seed=${name}`;
    db.prepare(
      "INSERT INTO users (id, name, securityQuestion, securityAnswer, avatar) VALUES (?, ?, ?, ?, ?)",
    ).run(id, name, securityQuestion, securityAnswer, avatar);

    res.status(201).json({ id, name, securityQuestion, avatar });
  });

  app.get("/api/user-question", (req, res) => {
    const { name } = req.query;
    if (!name) return res.status(400).json({ error: "Username required" });
    const user = db
      .prepare(
        "SELECT securityQuestion FROM users WHERE LOWER(name) = LOWER(?)",
      )
      .get(name) as any;
    if (!user) return res.status(404).json({ error: "User not found" });
    res.json({ securityQuestion: user.securityQuestion });
  });

  app.post("/api/login", (req, res) => {
    const { name, securityAnswer } = req.body;
    const user = db
      .prepare(
        "SELECT * FROM users WHERE LOWER(name) = LOWER(?) AND LOWER(securityAnswer) = LOWER(?)",
      )
      .get(name, securityAnswer) as any;
    if (!user) return res.status(401).json({ error: "Incorrect answer" });
    delete user.securityAnswer;
    res.json(user);
  });

  app.get("/api/debug/chats", (req, res) => {
    const allChats = loadChats();
    const result = allChats.map(c => ({
      chatId: c.id,
      isGroup: c.isGroup,
      name: c.name,
      members: c.participants?.map((p: any) => ({ id: p.id, name: p.name })) || [],
      messageCount: loadMessages(c.id).length,
      lastMessage: c.lastMessage
    }));
    res.json(result);
  });

  app.get("/api/chats", (req, res) => {
    const userId = req.query.userId as string;
    const allChats = loadChats();
    if (userId) {
      return res.json(
        allChats
          .filter((c: any) => c.members?.includes(userId))
          .map((c: any) => withUnreadCount(c, userId)),
      );
    }
    res.json(allChats);
  });

  app.get("/api/chats/:chatId/attachments", (req, res) => {
    const chatId = req.params.chatId;
    const msgs = db.prepare("SELECT * FROM messages WHERE chatId = ? ORDER BY timestamp DESC").all(chatId) as any[];

    const media: any[] = [];
    const files: any[] = [];
    const links: any[] = [];

    const urlRegex = /(https?:\/\/[^\s]+)/g;

    for (const m of msgs) {
      if (m.isDeleted) continue;

      if (m.attachmentUrl && m.attachmentType) {
        let size = 0;
        const match = m.attachmentUrl.match(/\/(?:hello\/)?api\/files\/(file_[a-z0-9]+)/);
        if (match) {
           const f = db.prepare("SELECT size FROM file_attachments WHERE fileId = ?").get(match[1]) as any;
           if (f) size = f.size;
        }

        const item = {
          id: m.id,
          messageId: m.id,
          fileName: m.attachmentName || "Unknown File",
          mimeType: m.attachmentType,
          size: size,
          url: m.attachmentUrl,
          senderId: m.senderId,
          senderName: m.senderName,
          createdAt: m.timestamp
        };
        if (m.attachmentType.startsWith('image/') || m.attachmentType.startsWith('video/')) {
          media.push(item);
        } else {
          files.push(item);
        }
      }

      if (m.text) {
        const urls = m.text.match(urlRegex);
        if (urls) {
          for (const url of urls) {
            links.push({
              messageId: m.id,
              url,
              text: m.text,
              senderId: m.senderId,
              senderName: m.senderName,
              createdAt: m.timestamp
            });
          }
        }
      }
    }

    res.json({ media, files, links });
  });

  app.get("/api/chats/:chatId/messages", (req, res) => {
    res.json(loadMessages(req.params.chatId));
  });

  app.post("/api/chats/:chatId/messages", (req, res) => {
    const chatId = req.params.chatId;
    let {
      senderId,
      senderName,
      senderAvatar,
      text,
      attachmentUrl,
      attachmentType,
      attachmentName,
      attachmentSize,
      location,
      replyTo,
    } = req.body;

    if ((!text || text.trim() === "") && !attachmentUrl && !location) {
      return res.status(400).json({ error: "Content required" });
    }

    const chatMembers = db
      .prepare("SELECT userId FROM chat_members WHERE chatId = ?")
      .all(chatId)
      .map((r: any) => r.userId);

    console.log("SERVER SEND MSG DEBUG:", {
      chatId,
      senderId,
      senderName,
      chatMembers,
    });

    const chat = db
      .prepare("SELECT * FROM chats WHERE id = ?")
      .get(chatId) as any;
    if (!chat) return res.status(404).json({ error: "Chat not found" });

    const isMember = db.prepare("SELECT 1 FROM chat_members WHERE chatId = ? AND userId = ?").get(chatId, senderId);
    if (!isMember) {
      return res.status(403).json({ error: "Sender is not a member of this chat" });
    }

    // Handle base64 attachment extraction to filesystem
    if (attachmentUrl && attachmentUrl.startsWith("data:")) {
      try {
        const matches = attachmentUrl.match(
          /^data:([a-zA-Z0-9]+\/[a-zA-Z0-9-.+]+);base64,(.+)$/,
        );
        if (matches && matches.length === 3) {
          const mimeType = matches[1];
          const base64Data = matches[2];
          const extension = mimeType.split("/")[1] || "bin";
          const filename = `file_${Date.now()}_${Math.random().toString(36).substring(2)}.${extension}`;
          const filepath = path.join(UPLOAD_DIR, filename);
          fs.writeFileSync(filepath, base64Data, "base64");
          // Store it as a proper file attachment entry to keep it consistent
          const fileId = "file_" + Math.random().toString(36).substr(2, 9);
          attachmentUrl = `${HELLO_API_PATH}/files/${fileId}`;
          attachmentName = filename;
          attachmentSize = Buffer.byteLength(base64Data, 'base64');
          db.prepare(
            `
            INSERT INTO file_attachments 
            (fileId, originalName, storedName, mimeType, size, path, uploaderId, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          `,
          ).run(
            fileId,
            filename,
            filename,
            mimeType,
            0,
            filepath,
            senderId || "system",
            Date.now(),
          );
        }
      } catch (e) {
        console.error("Failed to save base64 attachment", e);
      }
    }

    const id = "msg_" + Math.random().toString(36).substr(2, 9);
    const timestamp = Date.now();
    const finalLocation = location ? JSON.stringify(location) : null;
    const finalReplyTo = replyTo ? JSON.stringify(replyTo) : null;

    db.prepare(
      "INSERT INTO messages (id, chatId, senderId, senderName, senderAvatar, text, timestamp, attachmentUrl, attachmentType, attachmentName, attachmentSize, status, location, replyTo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ).run(
      id,
      chatId,
      senderId || "api-user",
      senderName || "API User",
      senderAvatar,
      text || (location ? "📍 Location shared" : ""),
      timestamp,
      attachmentUrl,
      attachmentType,
      attachmentName || null,
      attachmentSize || null,
      "sent",
      finalLocation,
      finalReplyTo,
    );

    db.prepare(
      "UPDATE chats SET lastMessage = ?, lastMessageTime = ? WHERE id = ?",
    ).run(text || (location ? "📍 Location" : ""), timestamp, chatId);

    if (senderId) {
      upsertReadState(chatId, senderId, timestamp);
    }

    const fullMsg = loadMessages(chatId).find((m: any) => m.id === id);
    const updatedChat = loadChats().find((c) => c.id === chatId);

    io.to(chatId).emit("receive_message", fullMsg);
    if (updatedChat && updatedChat.members) {
      updatedChat.members.forEach((uid: string) => {
        const personalized = loadChatForUser(chatId, uid);
        if (personalized) {
          io.to(uid).emit("chat_updated", personalized);
        }
      });
    }

    const members = updatedChat?.members || [];
    const onlineOthers = members.filter(
      (m: string) =>
        m !== senderId && Array.from(connectedSockets.values()).includes(m),
    );
    if (
      onlineOthers.length > 0 ||
      (chatId === "global" && connectedSockets.size > 1)
    ) {
      db.prepare("UPDATE messages SET status = 'delivered' WHERE id = ?").run(
        id,
      );
      fullMsg.status = "delivered";
      io.to(chatId).emit("message_updated", fullMsg);
    }

    res.status(201).json(fullMsg);
  });

  app.put("/api/chats/:chatId/messages/:messageId/location", (req, res) => {
    const { chatId, messageId } = req.params;
    const { lat, lng } = req.body;

    const message = loadMessages(chatId).find((m: any) => m.id === messageId);
    if (!message) return res.status(404).json({ error: "Message not found" });

    if (message.location && message.location.isLive) {
      if (
        message.location.expiresAt &&
        message.location.expiresAt < Date.now()
      ) {
        return res.status(400).json({ error: "Expired" });
      }
      message.location.lat = lat;
      message.location.lng = lng;
      db.prepare("UPDATE messages SET location = ? WHERE id = ?").run(
        JSON.stringify(message.location),
        messageId,
      );
      io.to(chatId).emit("message_updated", message);
      res.json(message);
    } else {
      res.status(400).json({ error: "Not a live location" });
    }
  });

  app.post("/api/chats/direct", (req, res) => {
    const { currentUserId, targetUserId } = req.body;
    if (!currentUserId || !targetUserId)
      return res
        .status(400)
        .json({ error: "currentUserId and targetUserId required" });

    // Find existing direct chat between exact these two users
    const existingChatRow = db.prepare(`
      SELECT c.*
      FROM chats c
      JOIN chat_members m1 ON m1.chatId = c.id AND m1.userId = ?
      JOIN chat_members m2 ON m2.chatId = c.id AND m2.userId = ?
      WHERE c.isGroup = 0
      AND (
        SELECT COUNT(*)
        FROM chat_members cm
        WHERE cm.chatId = c.id
      ) = 2
      LIMIT 1;
    `).get(currentUserId, targetUserId) as any;

    if (existingChatRow) {
      const existingChat = loadChats().find(c => c.id === existingChatRow.id);
      if (existingChat) {
        return res.json(loadChatForUser(existingChat.id, currentUserId) || existingChat);
      }
    }

    // Get target user to name the chat (chats table has name)
    const targetUser = db
      .prepare("SELECT name, avatar FROM users WHERE id = ?")
      .get(targetUserId) as any;
    const currentUserInfo = db
      .prepare("SELECT name, avatar FROM users WHERE id = ?")
      .get(currentUserId) as any;
    if (!targetUser || !currentUserInfo)
      return res.status(404).json({ error: "User not found" });

    const id = "chat_" + Math.random().toString(36).substr(2, 9);
    db.prepare(
      "INSERT INTO chats (id, name, lastMessage, lastMessageTime, unreadCount, isGroup) VALUES (?, ?, ?, ?, ?, ?)",
    ).run(id, "Direct Chat", "", Date.now(), 0, 0);

    const insertMember = db.prepare(
      "INSERT INTO chat_members (chatId, userId) VALUES (?, ?)",
    );
    insertMember.run(id, currentUserId);
    upsertReadState(id, currentUserId, Date.now());
    io.in(currentUserId).socketsJoin(id);
    if (currentUserId !== targetUserId) {
      insertMember.run(id, targetUserId);
      upsertReadState(id, targetUserId, 0);
      io.in(targetUserId).socketsJoin(id);
    }

    const newChat = loadChats().find((c) => c.id === id);
    if (newChat && newChat.members) {
      newChat.members.forEach((uid: string) => {
        const personalized = loadChatForUser(id, uid);
        if (personalized) {
          io.to(uid).emit("new_chat", personalized);
        }
      });
    }
    res.status(201).json(loadChatForUser(id, currentUserId) || newChat);
  });

  app.post("/api/chats", (req, res) => {
    const { name, isGroup, members } = req.body;
    if (!name) return res.status(400).json({ error: "Chat name required" });

    const id = "chat_" + Math.random().toString(36).substr(2, 9);
    db.prepare(
      "INSERT INTO chats (id, name, lastMessage, lastMessageTime, unreadCount, isGroup) VALUES (?, ?, ?, ?, ?, ?)",
    ).run(id, name, "", Date.now(), 0, isGroup ? 1 : 0);

    const insertMember = db.prepare(
      "INSERT INTO chat_members (chatId, userId) VALUES (?, ?)",
    );
    if (members) members.forEach((uid: string) => {
      insertMember.run(id, uid);
      upsertReadState(id, uid, 0);
      io.in(uid).socketsJoin(id);
    });

    const newChat = loadChats().find((c) => c.id === id);
    if (newChat && newChat.members) {
      newChat.members.forEach((uid: string) => {
        const personalized = loadChatForUser(id, uid);
        if (personalized) {
          io.to(uid).emit("new_chat", personalized);
        }
      });
    }
    res.status(201).json(members?.[0] ? loadChatForUser(id, members[0]) || newChat : newChat);
  });

  app.post("/api/chats/:chatId/messages/:messageId/react", (req, res) => {
    const { chatId, messageId } = req.params;
    const { emoji, userId } = req.body;

    const existing = db
      .prepare(
        "SELECT * FROM reactions WHERE messageId = ? AND emoji = ? AND userId = ?",
      )
      .get(messageId, emoji, userId);
    if (existing) {
      db.prepare(
        "DELETE FROM reactions WHERE messageId = ? AND emoji = ? AND userId = ?",
      ).run(messageId, emoji, userId);
    } else {
      db.prepare(
        "INSERT INTO reactions (messageId, emoji, userId) VALUES (?, ?, ?)",
      ).run(messageId, emoji, userId);
    }

    const message = loadMessages(chatId).find((m: any) => m.id === messageId);
    io.to(chatId).emit("message_updated", message);
    res.json(message);
  });

  app.post("/api/chats/:chatId/messages/:messageId/star", (req, res) => {
    const { chatId, messageId } = req.params;
    const { userId } = req.body;

    const existing = db
      .prepare(
        "SELECT * FROM message_starred_by WHERE messageId = ? AND userId = ?",
      )
      .get(messageId, userId);
    if (existing) {
      db.prepare(
        "DELETE FROM message_starred_by WHERE messageId = ? AND userId = ?",
      ).run(messageId, userId);
    } else {
      db.prepare(
        "INSERT INTO message_starred_by (messageId, userId) VALUES (?, ?)",
      ).run(messageId, userId);
    }

    const message = loadMessages(chatId).find((m: any) => m.id === messageId);
    io.to(chatId).emit("message_updated", message);
    res.json(message);
  });

  app.post("/api/chats/:chatId/messages/:messageId/pin", (req, res) => {
    const { chatId, messageId } = req.params;
    const { durationDays } = req.body;

    const pinnedUntil =
      durationDays === 0
        ? null
        : Date.now() + durationDays * 24 * 60 * 60 * 1000;
    db.prepare("UPDATE messages SET pinnedUntil = ? WHERE id = ?").run(
      pinnedUntil,
      messageId,
    );

    const message = loadMessages(chatId).find((m: any) => m.id === messageId);
    io.to(chatId).emit("message_updated", message);
    res.json(message);
  });

  app.delete("/api/chats/:chatId/messages/:messageId", (req, res) => {
    const { chatId, messageId } = req.params;
    const { userId, type } = req.body;

    const message = loadMessages(chatId).find((m: any) => m.id === messageId);
    if (!message) return res.status(404).json({ error: "Not found" });

    if (type === "for_everyone") {
      if (message.senderId !== userId)
        return res.status(403).json({ error: "Forbidden" });
      db.prepare(
        "UPDATE messages SET isDeleted = 1, text = 'This message was deleted', attachmentUrl = NULL, attachmentType = NULL WHERE id = ?",
      ).run(messageId);
      const updated = loadMessages(chatId).find((m: any) => m.id === messageId);
      io.to(chatId).emit("message_updated", updated);
      res.json(updated);
    } else {
      db.prepare(
        "INSERT INTO message_deleted_for (messageId, userId) VALUES (?, ?) ON CONFLICT DO NOTHING",
      ).run(messageId, userId);
      message.deletedFor.push(userId);
      res.json(message);
    }
  });

  app.delete("/api/chats/:chatId/clear", (req, res) => {
    const { chatId } = req.params;
    const { userId } = req.body;
    
    // Find all messages in this chat, and add them to message_deleted_for for this user
    const msgs = db.prepare("SELECT id FROM messages WHERE chatId = ?").all(chatId) as any[];
    
    const insertStmt = db.prepare("INSERT INTO message_deleted_for (messageId, userId) VALUES (?, ?) ON CONFLICT DO NOTHING");
    db.transaction(() => {
      for (const m of msgs) {
        insertStmt.run(m.id, userId);
      }
    })();
    
    res.json({ success: true, chatId });
  });

  app.delete("/api/chats/:chatId", (req, res) => {
    const { chatId } = req.params;
    const { userId } = req.body;
    db.prepare(
      "INSERT INTO chat_deleted_for (chatId, userId) VALUES (?, ?) ON CONFLICT DO NOTHING",
    ).run(chatId, userId);
    res.json({ success: true, chatId });
  });

  app.get("/api/calls", (req, res) => {
    const userId = String(req.query.userId || "");
    if (!userId) return res.status(400).json({ error: "userId is required" });
    res.json(getCallHistoryForUser(userId));
  });

  app.get("/api/calls/ice-config", (_req, res) => {
    res.json(getIceServersFromEnv());
  });

  app.get("/api/calls/:userId", (req, res) => {
    res.json(getCallHistoryForUser(req.params.userId));
  });

  app.post("/api/calls", (req, res) => {
    const { callerId, calleeId, chatId, type, status, startedAt, roomId, mode, participantIds } = req.body;
    if (!callerId || !calleeId || !chatId) {
      return res
        .status(400)
        .json({ error: "callerId, calleeId, and chatId are required" });
    }
    if (callerId === calleeId) {
      return res.status(400).json({ error: "callerId and calleeId must differ" });
    }
    if (!isMember(chatId, callerId) || !isMember(chatId, calleeId)) {
      return res.status(403).json({ error: "call participants must be chat members" });
    }

    const id = "call_" + Math.random().toString(36).substr(2, 9);
    const now = Date.now();
    console.log("[CALL_CREATE]", {
      id,
      chatId,
      callerId,
      calleeId,
      type,
      status,
    });
    db.prepare(
      "INSERT INTO call_logs (id, roomId, mode, callerId, calleeId, chatId, type, status, participantIds, createdAt, startedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    ).run(
      id,
      roomId || null,
      mode || "direct",
      callerId,
      calleeId,
      chatId,
      type,
      status,
      participantIds ? JSON.stringify(participantIds) : null,
      now,
      startedAt || now,
    );
    res.status(201).json({ id });
  });

  app.patch("/api/calls/:id", (req, res) => {
    const { id } = req.params;
    const { status, ringingAt, acceptedAt, connectedAt, answeredAt, endedAt, durationSeconds, endReason, endedBy } = req.body;
    let updateFields: string[] = [];
    let params: any[] = [];

    if (status) { updateFields.push("status = ?"); params.push(status); }
    if (ringingAt) { updateFields.push("ringingAt = ?"); params.push(ringingAt); }
    if (acceptedAt) { updateFields.push("acceptedAt = ?"); params.push(acceptedAt); }
    if (connectedAt) { updateFields.push("connectedAt = ?"); params.push(connectedAt); }
    if (answeredAt) { updateFields.push("answeredAt = ?"); params.push(answeredAt); }
    if (endedAt) { updateFields.push("endedAt = ?"); params.push(endedAt); }
    if (durationSeconds !== undefined) { updateFields.push("durationSeconds = ?"); params.push(durationSeconds); }
    if (endReason) { updateFields.push("endReason = ?"); params.push(endReason); }
    if (endedBy) { updateFields.push("endedBy = ?"); params.push(endedBy); }

    if (updateFields.length > 0) {
      params.push(id);
      db.prepare(`UPDATE call_logs SET ${updateFields.join(", ")} WHERE id = ?`).run(...params);
    }
    res.json({ success: true });
  });

  app.post("/api/call-rooms", (req, res) => {
    const { chatId, hostId, type = "video", participantIds = [] } = req.body;
    if (!chatId || !hostId) {
      return res.status(400).json({ error: "chatId and hostId are required" });
    }
    if (!isMember(chatId, hostId)) {
      return res.status(403).json({ error: "host must be a chat member" });
    }
    const memberIds = getChatMemberIds(chatId);
    const invitedIds = Array.from(new Set([hostId, ...participantIds.map(String)]))
      .filter((id) => memberIds.includes(id));
    if (invitedIds.length < 2) {
      return res.status(400).json({ error: "group call requires at least 2 chat members" });
    }
    if (participantIds.length > 4 || invitedIds.length > 4) {
      return res.status(400).json({ error: "group calls support up to 4 participants" });
    }

    const id = "room_" + Math.random().toString(36).slice(2, 11);
    const callId = "call_" + Math.random().toString(36).slice(2, 11);
    const now = Date.now();
    db.prepare(
      "INSERT INTO call_rooms (id, chatId, hostId, mode, type, status, participantIds, maxParticipants, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ).run(id, chatId, hostId, "group", type, "ringing", JSON.stringify(invitedIds), 4, now);
    db.prepare(
      "INSERT INTO call_logs (id, roomId, mode, callerId, calleeId, chatId, type, status, participantIds, createdAt, startedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    ).run(callId, id, "group", hostId, invitedIds.find((p) => p !== hostId) || hostId, chatId, type, "outgoing_calling", JSON.stringify(invitedIds), now, now);
    res.status(201).json({ ...getRoom(id), callId });
  });

  app.get("/api/call-rooms/:id", (req, res) => {
    const room = getRoom(req.params.id);
    if (!room) return res.status(404).json({ error: "room not found" });
    res.json(room);
  });

  app.post("/api/call-rooms/:id/join", (req, res) => {
    const room = getRoom(req.params.id);
    const userId = String(req.body.userId || "");
    if (!room) return res.status(404).json({ error: "room not found" });
    if (!userId || !isMember(room.chatId, userId)) {
      return res.status(403).json({ error: "user must be a chat member" });
    }
    const participantIds = Array.from(new Set([...room.participantIds, userId]));
    if (participantIds.length > 4) {
      return res.status(400).json({ error: "group calls support up to 4 participants" });
    }
    db.prepare("UPDATE call_rooms SET status = ?, participantIds = ? WHERE id = ?").run("active", JSON.stringify(participantIds), room.id);
    res.json(getRoom(room.id));
  });

  app.post("/api/call-rooms/:id/leave", (req, res) => {
    const room = getRoom(req.params.id);
    const userId = String(req.body.userId || "");
    if (!room) return res.status(404).json({ error: "room not found" });
    const participantIds = room.participantIds.filter((id: string) => id !== userId);
    const ended = participantIds.length <= 1 || req.body.end === true || req.body.ended === true;
    db.prepare("UPDATE call_rooms SET status = ?, participantIds = ?, endedAt = COALESCE(endedAt, ?), endedBy = COALESCE(endedBy, ?) WHERE id = ?")
      .run(ended ? "ended" : "active", JSON.stringify(participantIds), ended ? Date.now() : null, ended ? userId : null, room.id);
    res.json(getRoom(room.id));
  });

  app.get("/api/statuses", (req, res) => {
    const userId = String(req.query.userId || "");
    if (!userId) return res.status(400).json({ error: "userId is required" });
    
    // For simplicity, let's get all statuses within last 24 hours.
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    const statuses = db.prepare(`
      SELECT s.*, u.name as userName, u.avatar as userAvatar 
      FROM statuses s
      LEFT JOIN users u ON s.userId = u.id
      WHERE s.timestamp >= ?
      ORDER BY s.timestamp ASC
    `).all(cutoff);

    const views = db.prepare(`SELECT * FROM status_views`).all();
    
    const mapped = statuses.map((s: any) => {
      s.views = views.filter((v: any) => v.statusId === s.id).map((v: any) => ({ userId: v.userId, timestamp: v.timestamp }));
      return s;
    });

    res.json(mapped);
  });

  app.post("/api/statuses", (req, res) => {
    const { userId, text, attachmentUrl, attachmentType, backgroundColor, duration } = req.body;
    if (!userId) return res.status(400).json({ error: "userId is required" });
    
    const id = "status_" + Math.random().toString(36).substr(2, 9);
    const timestamp = Date.now();
    
    db.prepare(
      "INSERT INTO statuses (id, userId, text, attachmentUrl, attachmentType, backgroundColor, duration, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    ).run(id, userId, text || "", attachmentUrl || "", attachmentType || "", backgroundColor || "#000000", duration || 5000, timestamp);
    
    const user = db.prepare("SELECT name FROM users WHERE id = ?").get(userId) as any;
    io.emit("status_added", { userId, id, userName: user?.name || "Someone" });

    res.status(201).json({ id, timestamp });
  });

  app.post("/api/statuses/:id/view", (req, res) => {
    const { id } = req.params;
    const { userId } = req.body;
    if (!userId) return res.status(400).json({ error: "userId is required" });
    
    db.prepare(
      "INSERT INTO status_views (statusId, userId, timestamp) VALUES (?, ?, ?) ON CONFLICT DO NOTHING"
    ).run(id, userId, Date.now());
    
    res.json({ success: true });
  });

  app.post("/api/dev/reset", (req, res) => {
    db.prepare("DELETE FROM messages").run();
    db.prepare("DELETE FROM chat_read_state").run();
    db.prepare("DELETE FROM chat_members").run();
    db.prepare("DELETE FROM chats").run();
    
    // Re-add "group_main" if needed or leave empty.
    const id = "group_main";
    db.prepare(
      "INSERT INTO chats (id, name, lastMessage, lastMessageTime, unreadCount, isGroup) VALUES (?, ?, ?, ?, ?, ?)",
    ).run(id, "Community Chat", "", Date.now(), 0, 1);
    
    // Add all existing users to group_main
    const users = db.prepare("SELECT id FROM users").all() as any[];
    const insertMember = db.prepare(
      "INSERT INTO chat_members (chatId, userId) VALUES (?, ?)",
    );
    for (const u of users) {
      insertMember.run(id, u.id);
      upsertReadState(id, u.id, 0);
    }
    
    res.json({ message: "Database chats and messages reset successfully." });
  });

  // Global socket error handler
  io.engine.on("connection_error", (err) => {
    console.log("Socket connection error:", err);
  });

  hostApp.use(frontendMountPath, app);

  if (options.serveFrontend !== false && process.env.NODE_ENV !== "production") {
    const { createServer: createViteServer } = await import("vite");
    const vite = await createViteServer({
      root: HELLO_ROOT,
      base: basePath ? `${basePath}/` : "/",
      server: { middlewareMode: true },
      appType: "spa",
    });
    hostApp.use(frontendMountPath, vite.middlewares);
  } else if (options.serveFrontend !== false) {
    const distPath = path.join(HELLO_ROOT, "dist");
    hostApp.use(frontendMountPath, express.static(distPath));
    const fallbackPath = basePath ? `${basePath}/*` : "*";
    hostApp.get(fallbackPath, (_req, res) => res.sendFile(path.join(distPath, "index.html")));
  }

  return {
    basePath,
    apiPath: HELLO_API_PATH,
    socketPath,
    databasePath: DB_PATH,
    uploadsPath: UPLOAD_DIR,
    familyDrivePath: FAMILY_DRIVE_DIR,
  };
}

export async function startStandaloneHello(port = PORT, host = HOST) {
  const app = express();
  const httpServer = createServer(app);

  await mountHello(app, httpServer, {
    basePath: "",
    apiPath: "/api",
    socketPath: "/socket.io",
    dataDir: path.join(process.cwd(), "data"),
    dbPath: process.env.DATABASE_PATH || path.join(process.cwd(), "data", "app.db"),
    uploadsPath: process.env.UPLOAD_DIR || path.join(process.cwd(), "uploads"),
    familyDrivePath: process.env.FAMILY_DRIVE_DIR || path.join(process.cwd(), "data", "family-drive"),
    helloRoot: process.cwd(),
  });

  httpServer.listen(port, host, () => {
    console.log(`\n=================================`);
    console.log(`  Hello Local Server     `);
    console.log(`=================================`);
    console.log(`Local Access:      http://localhost:${port}`);
    console.log(`Network/Tailscale: http://${host}:${port}`);
    console.log(`Database Path:     ${DB_PATH}`);
    console.log(`Uploads Directory: ${UPLOAD_DIR}`);
    console.log(`=================================\n`);
  });
}

if (process.env.HELLO_STANDALONE === "1" || process.argv.includes("--standalone")) {
  startStandaloneHello().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
