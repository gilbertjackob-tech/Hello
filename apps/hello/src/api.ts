import { CallHistoryItem, Chat, DriveItemsResponse, DriveUploadResponse, Message, User } from "./types";

const env = (import.meta as any).env || {};

export const API_BASE = env.VITE_HELLO_API_BASE || "/hello/api";
export const CHAT_CLOUD_BASE_URL = env.VITE_CHAT_CLOUD_BASE_URL || "https://chat.bookhelloctg.com";
export const CHAT_CLOUD_FALLBACK_URL = env.VITE_CHAT_CLOUD_FALLBACK_URL || "https://hello-chat-worker.gilbert-jackob3.workers.dev";
export const CHAT_API_BASE = env.VITE_CHAT_API_BASE || `${CHAT_CLOUD_BASE_URL}/api`;
export const CALL_API_BASE = env.VITE_CALL_API_BASE || `${CHAT_CLOUD_BASE_URL}/api`;
export const DRIVE_API_BASE = env.VITE_DRIVE_API_BASE || "https://home.bookhelloctg.com/hello/api";
export const CLOUD_SESSION_TOKEN_KEY = "hello_cloud_session_token";

function getCloudSessionToken(): string | null {
  return localStorage.getItem(CLOUD_SESSION_TOKEN_KEY);
}

function setCloudSessionToken(token?: string | null) {
  if (token) localStorage.setItem(CLOUD_SESSION_TOKEN_KEY, token);
}

export function cloudAuthHeaders(): Record<string, string> {
  const token = getCloudSessionToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function checkChatCloudHealth(useFallback = false): Promise<{
  ok: boolean;
  service?: string;
  status?: string;
}> {
  const baseUrl = useFallback ? CHAT_CLOUD_FALLBACK_URL : CHAT_CLOUD_BASE_URL;
  const res = await fetch(`${baseUrl}/health`);
  if (!res.ok) throw new Error("Cloud chat health check failed");
  return res.json();
}

export async function checkDriveHealth(): Promise<{
  ok: boolean;
  service?: string;
  storage?: string;
  driveRoot?: string;
}> {
  const res = await fetch(`${DRIVE_API_BASE}/drive/health`);
  if (!res.ok) throw new Error("PC Drive health check failed");
  return res.json();
}

async function fetchCloudChat(path: string, init?: RequestInit): Promise<Response> {
  const primary = await fetch(`${CHAT_CLOUD_BASE_URL}${path}`, init).catch(() => null);
  if (primary?.ok) return primary;
  const fallback = await fetch(`${CHAT_CLOUD_FALLBACK_URL}${path}`, init);
  if (!fallback.ok && primary) return primary;
  return fallback;
}

async function readJsonError(res: Response, fallback: string): Promise<never> {
  const message = await res
    .json()
    .then((body) => body?.error)
    .catch(() => null);
  throw new Error(message || fallback);
}

export async function registerCloudUser(input: {
  name: string;
  securityQuestion: string;
  securityAnswer: string;
}): Promise<User> {
  const res = await fetchCloudChat("/api/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) return readJsonError(res, "Registration failed");
  const data = await res.json();
  setCloudSessionToken(data.token);
  return { ...(data.user || data), sessionToken: data.token };
}

export async function fetchCloudUserQuestion(name: string): Promise<string> {
  const res = await fetchCloudChat(`/api/user-question?name=${encodeURIComponent(name)}`);
  if (!res.ok) return readJsonError(res, "User not found");
  const data = await res.json();
  if (!data.securityQuestion) throw new Error("User needs registration");
  return data.securityQuestion;
}

export async function loginCloudUser(input: {
  name: string;
  securityAnswer: string;
}): Promise<User> {
  const res = await fetchCloudChat("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) return readJsonError(res, "Login failed");
  const data = await res.json();
  setCloudSessionToken(data.token);
  return { ...(data.user || data), sessionToken: data.token };
}

export async function fetchCloudCurrentUser(): Promise<User> {
  const res = await fetchCloudChat("/api/auth/me", {
    headers: cloudAuthHeaders(),
  });
  if (!res.ok) return readJsonError(res, "Cloud account session expired");
  const data = await res.json();
  return { ...(data.user || data), sessionToken: getCloudSessionToken() || undefined };
}

export async function logoutCloudUser(): Promise<void> {
  const res = await fetchCloudChat("/api/auth/logout", {
    method: "POST",
    headers: cloudAuthHeaders(),
  });
  localStorage.removeItem(CLOUD_SESSION_TOKEN_KEY);
  if (!res.ok) return readJsonError(res, "Cloud logout failed");
}

export async function patchCloudUserProfile(userId: string, patch: Partial<User> & {
  displayName?: string;
  about?: string;
  status?: string;
}): Promise<User> {
  const res = await fetchCloudChat(`/api/users/${encodeURIComponent(userId)}/profile`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
    body: JSON.stringify(patch),
  });
  if (!res.ok) return readJsonError(res, "Failed to update profile");
  return res.json();
}

export async function upsertCloudChatUser(user: {
  id: string;
  name: string;
  avatar?: string | null;
}): Promise<User> {
  const token = getCloudSessionToken();
  if (token) {
    return patchCloudUserProfile(user.id, { name: user.name, avatar: user.avatar || undefined });
  }
  const res = await fetchCloudChat("/api/chat/users/upsert", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: user.id, displayName: user.name, avatarUrl: user.avatar }),
  });
  if (!res.ok) throw new Error("Failed to upsert cloud chat user");
  return res.json();
}

export async function fetchCloudConversations(userId: string): Promise<Chat[]> {
  const res = await fetchCloudChat(`/api/chat/conversations?userId=${encodeURIComponent(userId)}`);
  if (!res.ok) throw new Error("Failed to fetch cloud conversations");
  return res.json();
}

export async function createCloudConversation(input: {
  id?: string;
  type?: "direct" | "group";
  title?: string;
  name?: string;
  createdBy: string;
  createdByName?: string;
  memberIds: string[];
}): Promise<Chat> {
  const res = await fetchCloudChat("/api/chat/conversations", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new Error("Failed to create cloud conversation");
  return res.json();
}

export async function fetchCloudMessages(
  conversationId: string,
  limit = 50,
  offset = 0,
): Promise<Message[]> {
  const params = new URLSearchParams({ limit: String(limit), offset: String(offset) });
  const res = await fetchCloudChat(
    `/api/chat/conversations/${encodeURIComponent(conversationId)}/messages?${params.toString()}`,
  );
  if (!res.ok) throw new Error("Failed to fetch cloud messages");
  return res.json();
}

export async function sendCloudMessage(
  conversationId: string,
  input: {
    text: string;
    senderId: string;
    senderName: string;
    senderAvatar?: string | null;
    attachmentId?: string;
  },
): Promise<Message> {
  const res = await fetchCloudChat(`/api/chat/conversations/${encodeURIComponent(conversationId)}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new Error("Failed to send cloud message");
  return res.json();
}

export async function markCloudMessageRead(messageId: string, userId: string): Promise<void> {
  const res = await fetchCloudChat(`/api/chat/messages/${encodeURIComponent(messageId)}/read`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId }),
  });
  if (!res.ok) throw new Error("Failed to mark cloud message read");
}

export async function uploadCloudChatAttachment(
  file: File,
): Promise<{ id: string; url: string; mimeType: string; originalName: string; size: number; expiresAt: number }> {
  const formData = new FormData();
  formData.append("file", file);
  const res = await fetchCloudChat("/api/chat/attachments/upload", {
    method: "POST",
    body: formData,
  });
  if (!res.ok) throw new Error("Failed to upload cloud chat attachment");
  return res.json();
}

export async function uploadCloudUserAvatar(
  userId: string,
  file: File,
): Promise<User> {
  const formData = new FormData();
  formData.append("userId", userId);
  formData.append("file", file);
  const path = getCloudSessionToken()
    ? `/api/users/${encodeURIComponent(userId)}/avatar`
    : "/api/chat/users/avatar";
  const res = await fetchCloudChat(path, {
    method: "POST",
    headers: cloudAuthHeaders(),
    body: formData,
  });
  if (!res.ok) return readJsonError(res, "Failed to upload profile image");
  return res.json();
}

export async function fetchCloudContacts(): Promise<User[]> {
  const res = await fetchCloudChat("/api/contacts", { headers: cloudAuthHeaders() });
  if (!res.ok) return readJsonError(res, "Failed to fetch contacts");
  return res.json();
}

export async function addCloudContact(input: { contactUserId?: string; name?: string; alias?: string }): Promise<User> {
  const res = await fetchCloudChat("/api/contacts", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
    body: JSON.stringify(input),
  });
  if (!res.ok) return readJsonError(res, "Failed to add contact");
  return res.json();
}

export async function fetchCloudChatPreferences(): Promise<{
  readReceiptsEnabled: boolean;
  notificationsEnabled: boolean;
  conversations?: Array<{ conversationId: string; mutedUntil?: number | null; pinned: boolean; archived: boolean }>;
}> {
  const res = await fetchCloudChat("/api/preferences/chat", { headers: cloudAuthHeaders() });
  if (!res.ok) return readJsonError(res, "Failed to fetch chat preferences");
  return res.json();
}

export async function patchCloudChatPreferences(input: {
  readReceiptsEnabled?: boolean;
  notificationsEnabled?: boolean;
  conversation?: { conversationId: string; mutedUntil?: number | null; pinned?: boolean; archived?: boolean };
}) {
  const res = await fetchCloudChat("/api/preferences/chat", {
    method: "PATCH",
    headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
    body: JSON.stringify(input),
  });
  if (!res.ok) return readJsonError(res, "Failed to update chat preferences");
  return res.json();
}

export async function startCloudAudioCall(input: {
  receiverUserId: string;
  chatId: string;
}): Promise<{ callId: string; id: string; status: string }> {
  const res = await fetchCloudChat("/api/calls/start", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
    body: JSON.stringify({ ...input, type: "audio" }),
  });
  if (!res.ok) return readJsonError(res, "Failed to start cloud call");
  return res.json();
}

export async function fetchCloudCallHistory(userId?: string): Promise<CallHistoryItem[]> {
  const path = userId ? `/api/calls/history?userId=${encodeURIComponent(userId)}` : "/api/calls/history";
  const res = await fetchCloudChat(path, { headers: cloudAuthHeaders() });
  if (!res.ok) return readJsonError(res, "Failed to fetch call history");
  return res.json();
}

export function createCloudCallWebSocket(
  onEvent: (event: string, payload: any) => void,
): WebSocket {
  const token = getCloudSessionToken();
  if (!token) throw new Error("Cloud account session required for calls");
  const url = `${CHAT_CLOUD_BASE_URL}/api/calls/ws?token=${encodeURIComponent(token)}`
    .replace(/^https:/, "wss:")
    .replace(/^http:/, "ws:");
  const socket = new WebSocket(url);
  socket.addEventListener("message", (message) => {
    const envelope = JSON.parse(String(message.data));
    if (envelope?.event) onEvent(envelope.event, envelope.payload || envelope);
  });
  return socket;
}

export async function updateUserPrivacy(
  userId: string,
  lastActivePrivacy: "none" | "contacts" | "everyone",
): Promise<void> {
  void userId;
  await patchCloudChatPreferences({
    readReceiptsEnabled: lastActivePrivacy !== "none",
    notificationsEnabled: true,
  }).catch(() => undefined);
}

export async function fetchUserPresence(userId: string): Promise<{
  id: string;
  name: string;
  avatar: string;
  lastActive?: number;
  online: boolean;
  privacy: "none" | "contacts" | "everyone";
}> {
  const res = await fetchCloudChat(`/api/users/${encodeURIComponent(userId)}`);
  if (!res.ok) throw new Error("Failed to fetch user presence");
  return res.json();
}

export async function fetchUser(userId: string): Promise<User> {
  const res = await fetchCloudChat(`/api/users/${encodeURIComponent(userId)}`);
  if (!res.ok) throw new Error("Failed to fetch user");
  return res.json();
}

export async function fetchUsers(query?: string): Promise<User[]> {
  const url = query
    ? `/api/users?q=${encodeURIComponent(query)}`
    : "/api/users";
  const res = await fetchCloudChat(url);
  if (!res.ok) throw new Error("Failed to fetch users");
  return res.json();
}

export async function createDirectChat(
  currentUserId: string,
  targetUserId: string,
  options: {
    currentUserName?: string;
    targetUserName?: string;
  } = {},
): Promise<Chat> {
  const res = await fetchCloudChat("/api/chat/conversations/direct", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      type: "direct",
      createdBy: currentUserId,
      createdByName: options.currentUserName || currentUserId,
      targetUserId,
      memberIds: [currentUserId, targetUserId],
      title: options.targetUserName || "",
    }),
  });
  if (!res.ok) throw new Error("Failed to create direct chat");
  return res.json();
}

export async function fetchChats(userId: string): Promise<Chat[]> {
  const res = await fetchCloudChat(`/api/chat/conversations?userId=${encodeURIComponent(userId)}`);
  if (!res.ok) throw new Error("Failed to fetch chats");
  return res.json();
}

export async function fetchChatAttachments(chatId: string): Promise<{ media: any[]; files: any[]; links: any[] }> {
  const messages = await fetchMessages(chatId);
  const attachments = messages
    .filter((message) => message.attachmentUrl)
    .map((message) => ({
      id: message.id,
      messageId: message.id,
      fileName: message.attachmentName,
      mimeType: message.attachmentType,
      size: message.attachmentSize,
      url: message.attachmentUrl,
      text: message.text,
      senderId: message.senderId,
      senderName: message.senderName,
      createdAt: message.timestamp,
    }));
  return {
    media: attachments.filter((item) => item.mimeType === "image" || String(item.mimeType || "").startsWith("image/")),
    files: attachments.filter((item) => item.mimeType !== "image" && !String(item.mimeType || "").startsWith("image/")),
    links: [],
  };
}

export async function fetchMessages(chatId: string): Promise<Message[]> {
  const res = await fetchCloudChat(`/api/chat/conversations/${encodeURIComponent(chatId)}/messages`);
  if (!res.ok) throw new Error("Failed to fetch messages");
  return res.json();
}

export async function fetchStarredMessages(userId: string): Promise<Message[]> {
  void userId;
  return [];
}

export async function uploadFile(
  file: File,
  uploaderId: string,
): Promise<{ url: string; mimeType: string; originalName: string; size: number }> {
  void uploaderId;
  return uploadCloudChatAttachment(file);
}

export async function fetchDriveItems(
  limit = 60,
  before?: number | null,
): Promise<DriveItemsResponse> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (before) params.set("before", String(before));
  const res = await fetch(`${DRIVE_API_BASE}/drive/items?${params.toString()}`);
  if (!res.ok) throw new Error("Failed to fetch Drive items");
  return res.json();
}

export async function uploadDriveFiles(
  files: File[],
  uploaderId: string,
): Promise<DriveUploadResponse> {
  const formData = new FormData();
  formData.append("uploaderId", uploaderId);
  files.forEach((file) => formData.append("files", file));

  const res = await fetch(`${DRIVE_API_BASE}/drive/upload`, {
    method: "POST",
    body: formData,
  });
  if (!res.ok) {
    const message = await res
      .json()
      .then((body) => body?.error)
      .catch(() => null);
    throw new Error(message || "Failed to upload Drive files");
  }
  return res.json();
}

export async function deleteDriveItem(itemId: string): Promise<void> {
  const res = await fetch(`${DRIVE_API_BASE}/drive/items/${encodeURIComponent(itemId)}`, {
    method: "DELETE",
  });
  if (!res.ok) throw new Error("Failed to delete Drive item");
}

export async function sendMessage(
  chatId: string,
  text: string,
  attachmentUrl?: string,
  attachmentType?: "image" | "file" | "audio",
  attachmentName?: string,
  attachmentSize?: number,
  senderId = "local-user",
  senderName = "Me",
  senderAvatar?: string,
  location?: any,
  replyTo?: { id: string; text: string; senderName: string; senderId?: string },
): Promise<Message> {
  const attachmentId = attachmentUrl?.startsWith("/api/chat/attachments/")
    ? attachmentUrl.split("/").pop()
    : undefined;
  const res = await fetchCloudChat(`/api/chat/conversations/${encodeURIComponent(chatId)}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      text,
      attachmentId,
      senderId,
      senderName,
      senderAvatar,
    }),
  });
  void attachmentType;
  void attachmentName;
  void attachmentSize;
  void location;
  void replyTo;
  if (!res.ok) throw new Error("Failed to send message");
  return res.json();
}

export async function updateLiveLocation(
  chatId: string,
  messageId: string,
  lat: number,
  lng: number,
): Promise<Message> {
  const existing = (await fetchMessages(chatId)).find((message) => message.id === messageId);
  if (!existing) throw new Error("Message not found");
  return { ...existing, location: { ...(existing.location || {}), lat, lng } };
}

export async function createChat(
  name: string,
  isGroup?: boolean,
  members?: string[],
): Promise<Chat> {
  const res = await fetchCloudChat("/api/chat/conversations", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      title: name,
      name,
      type: isGroup ? "group" : "direct",
      isGroup,
      memberIds: members || [],
      createdBy: members?.[0],
      createdByName: members?.[0],
    }),
  });
  if (!res.ok) throw new Error("Failed to create chat");
  return res.json();
}

export async function reactToMessage(
  chatId: string,
  messageId: string,
  emoji: string,
  userId: string,
): Promise<Message> {
  const existing = (await fetchMessages(chatId)).find((message) => message.id === messageId);
  if (!existing) throw new Error("Message not found");
  const current = existing.reactions || [];
  const hasReaction = current.some((reaction) => reaction.emoji === emoji && reaction.userId === userId);
  return {
    ...existing,
    reactions: hasReaction
      ? current.filter((reaction) => !(reaction.emoji === emoji && reaction.userId === userId))
      : [...current, { emoji, userId }],
  };
}

export async function starMessage(
  chatId: string,
  messageId: string,
  userId: string,
): Promise<Message> {
  const existing = (await fetchMessages(chatId)).find((message) => message.id === messageId);
  if (!existing) throw new Error("Message not found");
  const starredBy = existing.starredBy || [];
  return {
    ...existing,
    starredBy: starredBy.includes(userId)
      ? starredBy.filter((id) => id !== userId)
      : [...starredBy, userId],
  };
}

export async function pinMessage(
  chatId: string,
  messageId: string,
  durationDays: number,
): Promise<Message> {
  const existing = (await fetchMessages(chatId)).find((message) => message.id === messageId);
  if (!existing) throw new Error("Message not found");
  return { ...existing, pinnedUntil: Date.now() + durationDays * 24 * 60 * 60 * 1000 };
}

export async function deleteMessage(
  chatId: string,
  messageId: string,
  userId: string,
  type: "for_me" | "for_everyone",
): Promise<Message> {
  const existing = (await fetchMessages(chatId)).find((message) => message.id === messageId);
  if (!existing) throw new Error("Message not found");
  return {
    ...existing,
    text: "",
    isDeleted: true,
    deletedFor: type === "for_me" ? [...(existing.deletedFor || []), userId] : undefined,
  };
}

export async function deleteChat(
  chatId: string,
  userId: string,
): Promise<Chat> {
  return {
    id: chatId,
    name: "Deleted chat",
    deletedFor: [userId],
  };
}

export async function clearChat(
  chatId: string,
  userId: string,
): Promise<{ success: boolean }> {
  void chatId;
  void userId;
  return { success: true };
}
