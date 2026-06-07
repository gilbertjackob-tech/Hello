export interface Reaction {
  emoji: string;
  userId: string;
}

export interface User {
  id: string;
  name: string;
  avatar: string;
  username?: string;
  phone?: string;
  email?: string;
  online?: boolean;
  lastActive?: number;
  securityQuestion?: string;
  securityAnswer?: string;
  sessionToken?: string;
}

export interface Contact {
  id: string;
  name: string;
  avatar?: string;
  phone?: string;
  isBlocked?: boolean;
}

export interface Chat {
  id: string;
  type?: "direct" | "group";
  directKey?: string | null;
  name: string;
  avatar?: string;
  lastMessage?: string;
  lastMessageTime?: number;
  unreadCount?: number;
  isGroup?: boolean;
  members?: string[];
  participants?: User[];
  deletedFor?: string[];
}

export type CallStatus =
  | "idle"
  | "outgoing_calling"
  | "trying_to_reach"
  | "outgoing_ringing"
  | "incoming_ringing"
  | "connecting"
  | "connected"
  | "reconnecting"
  | "ended"
  | "cancelled"
  | "declined"
  | "missed"
  | "failed"
  | "busy"
  | "unavailable";

export type CallMode = "direct" | "group";

export interface CallParticipant {
  id: string;
  name: string;
  avatar?: string;
  joinedAt?: number;
  leftAt?: number;
  isHost?: boolean;
  mediaState?: CallMediaState;
}

export interface CallMediaState {
  audioMuted: boolean;
  videoOff: boolean;
  screenSharing: boolean;
  quality?: "auto" | "720p" | "1080p" | "2k";
  beautyMode?: string;
}

export interface CallQualityStats {
  label: "unknown" | "poor" | "fair" | "good";
  rttMs?: number;
  jitterMs?: number;
  packetLossPercent?: number;
  bitrateKbps?: number;
  updatedAt: number;
}

export interface CallRoom {
  id: string;
  chatId: string;
  hostId: string;
  mode: CallMode;
  type: "audio" | "video";
  status: "ringing" | "active" | "ended";
  maxParticipants: number;
  participantIds: string[];
  participants?: CallParticipant[];
  createdAt: number;
  endedAt?: number;
  endedBy?: string;
}

export interface CallFeatureSupport {
  screenShare: boolean;
  recording: boolean;
  captions: boolean;
  pictureInPicture: boolean;
  outputDeviceSelect: boolean;
}

export interface CallData {
  callId: string;
  roomId?: string;
  chatId: string;
  callerId: string;
  callerName: string;
  callerAvatar?: string;
  calleeId: string;
  calleeName?: string;
  calleeAvatar?: string;
  isVideo: boolean;
  mode?: CallMode;
  status?: CallStatus;
  offer?: RTCSessionDescriptionInit;
}

export interface CallHistoryItem {
  id: string;
  roomId?: string;
  mode?: CallMode;
  participantIds?: string[];
  endedBy?: string;
  chatId: string;
  callerId: string;
  calleeId: string;
  type: "audio" | "video";
  direction: "incoming" | "outgoing";
  status: CallStatus;
  startedAt: number;
  ringingAt?: number;
  acceptedAt?: number;
  connectedAt?: number;
  endedAt?: number;
  durationSeconds?: number;
  endReason?: string;
  otherUser: User;
}

export interface DriveItem {
  id: string;
  batchId?: string | null;
  eventId?: string | null;
  eventName?: string | null;
  circleIds?: string[];
  favorite?: boolean;
  url: string;
  thumbnailUrl?: string;
  originalName?: string;
  mimeType?: string;
  type: "image" | "video" | string;
  size: number;
  uploaderId?: string;
  createdAt: number;
  monthKey?: string;
  monthLabel?: string;
  deletedAt?: number | null;
  deletedBy?: string | null;
}

export interface DriveItemsResponse {
  items: DriveItem[];
  nextCursor: number | null;
  hasMore: boolean;
  total: number;
}

export interface DriveDeleteLimit {
  limit: number;
  used: number;
  remaining: number;
  deleteDay: string;
}

export interface DriveDeleteResponse {
  ok: boolean;
  item?: DriveItem;
  deleteLimit?: DriveDeleteLimit;
}

export interface DriveUploadResponse {
  items: DriveItem[];
  count: number;
  batchId?: string;
  eventId?: string;
}

export interface DriveCircleMember {
  userId: string;
  role?: string;
  name?: string | null;
  username?: string | null;
  avatar?: string | null;
}

export interface DriveCircle {
  id: string;
  name: string;
  ownerUserId?: string | null;
  memberCount: number;
  members: DriveCircleMember[];
  createdAt: number;
  updatedAt: number;
}

export interface DriveEvent {
  id: string;
  name: string;
  createdByUserId?: string | null;
  coverItemId?: string | null;
  itemCount: number;
  circleIds?: string[];
  createdAt: number;
  updatedAt: number;
}

export interface DriveDeletePollVote {
  pollId: string;
  userId: string;
  vote: "delete" | "keep" | string;
  createdAt: number;
  updatedAt: number;
}

export interface DriveDeletePoll {
  id: string;
  targetType: "circle" | "event" | string;
  targetId: string;
  circleId: string;
  startedByUserId?: string | null;
  endsAt: number;
  status: "open" | "passed" | "failed" | "applied" | string;
  createdAt: number;
  resolvedAt?: number | null;
  deleteVotes?: number;
  keepVotes?: number;
  votes?: DriveDeletePollVote[];
}

export interface LocationData {
  lat: number;
  lng: number;
  isLive?: boolean;
  expiresAt?: number;
}

export interface Message {
  id: string;
  chatId: string;
  senderId: string;
  senderName: string;
  senderAvatar?: string;
  text: string;
  messageType?: string;
  timestamp: number;
  reactions?: Reaction[];
  attachmentUrl?: string;
  attachmentType?: "image" | "file" | "audio";
  attachmentName?: string;
  attachmentSize?: number;
  status?: "sent" | "delivered" | "read";
  starredBy?: string[];
  pinnedUntil?: number;
  isDeleted?: boolean;
  deletedFor?: string[];
  location?: LocationData;
  replyTo?: { id: string; text: string; senderName: string; senderId?: string };
  callInfo?: {
    callId: string;
    chatId?: string;
    callerId?: string;
    calleeId?: string;
    callType?: "audio" | "video" | string;
    status?: "ended" | "missed" | "declined" | "busy" | "unavailable" | "failed" | "cancelled" | string;
    startedAt?: number;
    answeredAt?: number;
    endedAt?: number;
    durationSeconds?: number;
    endReason?: string;
    mode?: CallMode | string;
  };
}
