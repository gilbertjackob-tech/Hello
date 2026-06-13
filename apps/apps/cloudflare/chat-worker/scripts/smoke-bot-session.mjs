import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const BASE_URL = process.env.HELLO_CHAT_BASE_URL || "https://chat.bookhelloctg.com";
const BOT_USER_ID = "usr_bot";
const BOT_USER_NAME = "bot";
const DB_NAME = "hello_chat_db";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const workerDir = path.resolve(__dirname, "..");
const repoRoot = path.resolve(workerDir, "..", "..", "..");

const suffix = new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
const probeName = `codex_bot_probe_${suffix}`;
const probeAnswer = `answer_${suffix}`;
let probeUserId = "";
let probeChatId = "";

async function requestJson(method, pathName, body, token) {
  const response = await fetch(`${BASE_URL}${pathName}`, {
    method,
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { raw: text };
  }
  if (!response.ok) {
    throw new Error(`${method} ${pathName} -> ${response.status}: ${text}`);
  }
  return data;
}

function cloudflareToken() {
  if (process.env.CLOUDFLARE_API_TOKEN) return process.env.CLOUDFLARE_API_TOKEN;
  const envPath = path.join(repoRoot, ".env");
  if (!existsSync(envPath)) return "";
  const raw = readFileSync(envPath, "utf8");
  const match = raw.match(/\$env:CLOUDFLARE_API_TOKEN="([^"]+)"/);
  return match?.[1] || "";
}

function wranglerSql(sql) {
  const token = cloudflareToken();
  if (!token) return null;
  const result = process.platform === "win32"
    ? spawnSync(
        "powershell",
        [
          "-NoProfile",
          "-ExecutionPolicy",
          "Bypass",
          "-EncodedCommand",
          Buffer.from(`$sql = @'\n${sql}\n'@\nnpx wrangler d1 execute ${DB_NAME} --remote --command $sql`, "utf16le").toString("base64"),
        ],
        {
          cwd: workerDir,
          env: { ...process.env, CLOUDFLARE_API_TOKEN: token },
          encoding: "utf8",
        },
      )
    : spawnSync(
        "npx",
        ["wrangler", "d1", "execute", DB_NAME, "--remote", "--command", sql],
        {
          cwd: workerDir,
          env: { ...process.env, CLOUDFLARE_API_TOKEN: token },
          encoding: "utf8",
        },
      );
  if (result.status !== 0) {
    throw new Error(
      `wrangler d1 execute failed (status=${result.status ?? "unknown"}):\n` +
        `${result.error?.message || result.stderr || result.stdout || "no output"}`,
    );
  }
  const start = result.stdout.indexOf("[");
  const end = result.stdout.lastIndexOf("]");
  if (start < 0 || end < start) return null;
  return JSON.parse(result.stdout.slice(start, end + 1));
}

function sqlString(value) {
  return String(value).replaceAll("'", "''");
}

function sessionEvidence(userId, chatId) {
  const user = sqlString(userId);
  const chat = sqlString(chatId);
  const rows = wranglerSql(
    "SELECT " +
      `(SELECT COUNT(*) FROM device_push_tokens WHERE user_id = '${user}') AS active_push_tokens, ` +
      `(SELECT COUNT(*) FROM sessions WHERE user_id = '${user}' AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > unixepoch() * 1000)) AS active_sessions, ` +
      `(SELECT COUNT(*) FROM sessions WHERE user_id = '${user}' AND revoked_at IS NOT NULL) AS revoked_sessions, ` +
      `(SELECT COUNT(*) FROM messages WHERE conversation_id = '${chat}' AND sender_id = '${BOT_USER_ID}' AND body = 'hi') AS bot_hi_replies;`,
  );
  return rows?.[0]?.results?.[0] || null;
}

function cleanupProbe(userId, chatId) {
  const user = sqlString(userId);
  const chat = sqlString(chatId);
  wranglerSql(
    `DELETE FROM message_receipts WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = '${chat}'); ` +
      `DELETE FROM message_reactions WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = '${chat}'); ` +
      `DELETE FROM message_starred_by WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = '${chat}'); ` +
      `DELETE FROM message_deleted_for WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = '${chat}'); ` +
      `DELETE FROM messages WHERE conversation_id = '${chat}'; ` +
      `DELETE FROM conversation_preferences WHERE conversation_id = '${chat}'; ` +
      `DELETE FROM chat_deleted_for WHERE conversation_id = '${chat}'; ` +
      `DELETE FROM conversation_members WHERE conversation_id = '${chat}'; ` +
      `DELETE FROM conversations WHERE id = '${chat}'; ` +
      `DELETE FROM device_push_tokens WHERE user_id = '${user}'; ` +
      `DELETE FROM notification_events WHERE user_id = '${user}'; ` +
      `DELETE FROM sessions WHERE user_id = '${user}'; ` +
      `DELETE FROM devices WHERE user_id = '${user}'; ` +
      `DELETE FROM user_chat_preferences WHERE user_id = '${user}'; ` +
      `DELETE FROM user_profiles WHERE user_id = '${user}'; ` +
      `DELETE FROM users WHERE id = '${user}';`,
  );
}

try {
  const registered = await requestJson("POST", "/api/auth/register", {
    name: probeName,
    securityQuestion: "probe",
    securityAnswer: probeAnswer,
    device: { id: `dev_${suffix}`, name: "Codex smoke device" },
  });
  const token = registered.token;
  probeUserId = registered.user?.id || registered.id;
  if (!token || !probeUserId) throw new Error("Registration did not return a token and user id");

  const users = await requestJson("GET", "/api/users");
  const bot = users.find((user) => user.id === BOT_USER_ID || user.name === BOT_USER_NAME);
  if (!bot) throw new Error("Bot user was not found in /api/users");

  const direct = await requestJson("POST", "/api/chat/conversations/direct", { targetUserId: BOT_USER_ID }, token);
  probeChatId = direct.id;
  if (!probeChatId) throw new Error("Direct bot chat did not return an id");

  await requestJson(
    "POST",
    `/api/chat/conversations/${encodeURIComponent(probeChatId)}/messages`,
    { senderId: probeUserId, text: "hi" },
    token,
  );
  await new Promise((resolve) => setTimeout(resolve, 700));

  const messagesResponse = await requestJson(
    "GET",
    `/api/chat/conversations/${encodeURIComponent(probeChatId)}/messages?limit=10`,
    undefined,
    token,
  );
  const messages = Array.isArray(messagesResponse) ? messagesResponse : messagesResponse.messages || [];
  const sentMessage = messages.find((message) => message.senderId === probeUserId && message.text === "hi");
  const botReply = messages.find((message) => message.senderId === BOT_USER_ID && message.text === "hi");
  if (!sentMessage) throw new Error("Sent message was not found in the message API response");
  if (sentMessage.status !== "delivered") {
    throw new Error(`Sent message status was ${sentMessage.status}, expected delivered`);
  }
  if (!botReply) throw new Error("Bot reply hi was not found in the message API response");

  const push = await requestJson("POST", "/api/devices/register", {
    deviceId: `push_${suffix}`,
    token: `fake_fcm_${suffix}`,
    platform: "android",
    deviceName: "Codex fake FCM",
  }, token);
  if (push.ok !== true) throw new Error("Push token registration did not return ok=true");

  const logout = await requestJson("POST", "/api/auth/logout", {}, token);
  if (logout.ok !== true) throw new Error("Logout did not return ok=true");

  let authMeAfterLogout = 200;
  try {
    await requestJson("GET", "/api/auth/me", undefined, token);
  } catch (error) {
    const match = String(error.message).match(/-> (\d+):/);
    authMeAfterLogout = match ? Number(match[1]) : 0;
  }
  if (authMeAfterLogout !== 401) {
    throw new Error(`Auth/me after logout returned ${authMeAfterLogout}, expected 401`);
  }

  const evidence = sessionEvidence(probeUserId, probeChatId);
  if (evidence) {
    if (evidence.active_push_tokens !== 0) throw new Error(`Active push tokens after logout: ${evidence.active_push_tokens}`);
    if (evidence.active_sessions !== 0) throw new Error(`Active sessions after logout: ${evidence.active_sessions}`);
    if (evidence.revoked_sessions < 1) throw new Error("No revoked session row found after logout");
    if (evidence.bot_hi_replies < 1) throw new Error("No bot hi reply row found in D1");
  }

  console.log(JSON.stringify({
    ok: true,
    baseUrl: BASE_URL,
    botUserId: BOT_USER_ID,
    probeUserId,
    probeChatId,
    sentMessageStatus: sentMessage.status,
    botReplyText: botReply.text,
    pushRegistered: push.ok,
    authMeAfterLogout,
    d1Evidence: evidence || "skipped_no_cloudflare_token",
  }, null, 2));
} finally {
  if (probeUserId && probeChatId && cloudflareToken()) {
    try {
      cleanupProbe(probeUserId, probeChatId);
    } catch (error) {
      console.warn(`Probe cleanup failed: ${error.message}`);
    }
  }
}
