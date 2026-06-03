import { createContext, useContext, useEffect, useRef, useState, ReactNode } from "react";
import { useSocket } from "./SocketContext";
import { Message, CallData, User } from "./types";

interface NotificationContextType {
  notificationsEnabled: boolean;
  requestPermission: () => Promise<void>;
  toggleNotifications: (enabled: boolean) => void;
}

const NotificationContext = createContext<NotificationContextType>({
  notificationsEnabled: false,
  requestPermission: async () => {},
  toggleNotifications: () => {},
});

export const useNotifications = () => useContext(NotificationContext);

type HelloNotificationPayload = {
  type?: string;
  senderId?: string;
  senderName?: string;
  targetId?: string;
  targetType?: string;
  groupName?: string | null;
  previewText?: string;
  emoji?: string | null;
  deepLink?: string;
  channel?: string;
  priority?: "urgent" | "high" | "default" | "low";
  collapseKey?: string;
};

const CATEGORY_STORAGE_PREFIX = "hello_notifications_";
const DEFAULT_ENABLED_CHANNELS = new Set([
  "calls",
  "missed_calls",
  "messages",
  "mentions",
  "status_posts",
  "status_activity",
  "system",
  "re_engagement",
]);

function channelEnabled(channel = "system") {
  const key = `${CATEGORY_STORAGE_PREFIX}${channel}`;
  const stored = localStorage.getItem(key);
  return stored == null ? DEFAULT_ENABLED_CHANNELS.has(channel) : stored === "true";
}

function titleForPayload(payload: HelloNotificationPayload) {
  const name = payload.senderName || "Hello";
  switch (payload.type) {
    case "call_incoming": return `${name} is calling...`;
    case "call_missed": return `Missed call from ${name}`;
    case "mention": return `${name} mentioned you`;
    case "reply": return `${name} replied to you`;
    case "status_post": return `${name} just posted a moment`;
    case "status_reaction": return `${name} reacted ${payload.emoji || ""}`;
    case "status_reply": return `${name} replied to your status`;
    case "archive_complete": return "Today's moments are safely saved";
    case "re_engagement": return "Share a moment";
    default: return payload.groupName || name;
  }
}

function bodyForPayload(payload: HelloNotificationPayload) {
  if (payload.previewText) return payload.previewText;
  if (payload.type === "call_incoming") return "Click to answer";
  if (payload.type === "status_post") return "Open Today Pulse";
  if (payload.type === "archive_complete") return "Saved to your PC";
  return "Open Hello";
}

export function NotificationProvider({ children, currentUser }: { children: ReactNode; currentUser: User | null }) {
  const [notificationsEnabled, setNotificationsEnabled] = useState(() => {
    return localStorage.getItem("whatsclone_notifications") === "true";
  });
  const { socket } = useSocket();
  const lastShownRef = useRef<Record<string, number>>({});

  useEffect(() => {
    if (typeof window !== "undefined" && "Notification" in window) {
      if (Notification.permission === "granted" && localStorage.getItem("whatsclone_notifications") === null) {
        setNotificationsEnabled(true);
      } else if (Notification.permission === "denied") {
        setNotificationsEnabled(false);
      }
    }
  }, []);

  const requestPermission = async () => {
    if (typeof window !== "undefined" && "Notification" in window) {
      const permission = await Notification.requestPermission();
      const enabled = permission === "granted";
      setNotificationsEnabled(enabled);
      localStorage.setItem("whatsclone_notifications", String(enabled));
    }
  };

  const toggleNotifications = async (enabled: boolean) => {
    if (enabled && typeof window !== "undefined" && "Notification" in window && Notification.permission !== "granted") {
      await requestPermission();
    } else {
      setNotificationsEnabled(enabled);
      localStorage.setItem("whatsclone_notifications", String(enabled));
    }
  };

  useEffect(() => {
    if (!socket || !notificationsEnabled || !currentUser) return;

    const notify = (title: string, options?: NotificationOptions & { channel?: string }) => {
      if (typeof window !== "undefined" && "Notification" in window && Notification.permission === "granted") {
        if (document.hidden && channelEnabled(options?.channel)) {
          const tag = options?.tag || title;
          const now = Date.now();
          if (now - (lastShownRef.current[tag] || 0) < 1500) return;
          lastShownRef.current[tag] = now;
          new Notification(title, {
            icon: '/whatsapp.png',
            silent: options?.channel === "status_activity" || options?.channel === "system" || options?.channel === "re_engagement",
            ...options
          });
        }
      }
    };

    const notifyUrgent = (title: string, options?: NotificationOptions) => {
      if (typeof window !== "undefined" && "Notification" in window && Notification.permission === "granted") {
        new Notification(title, {
          icon: '/whatsapp.png',
          requireInteraction: true,
          tag: options?.tag,
          ...options
        });
      }
    };

    const handleNotification = (payload: HelloNotificationPayload) => {
      if (payload.senderId === currentUser.id) return;
      const channel = payload.channel || "system";
      const title = titleForPayload(payload);
      const body = bodyForPayload(payload);
      const tag = payload.collapseKey || `${channel}-${payload.targetId || payload.type || "hello"}`;
      const options = {
        body,
        tag,
        channel,
        data: { deepLink: payload.deepLink, targetId: payload.targetId, targetType: payload.targetType },
      };
      if (channel === "calls" || payload.priority === "urgent") {
        notifyUrgent(title, options);
      } else {
        notify(title, options);
      }
    };

    const handleNewMessage = (msg: Message) => {
      if (msg.senderId !== currentUser.id) {
        handleNotification({
          type: "message",
          senderId: msg.senderId,
          senderName: msg.senderName || "Unknown",
          targetId: msg.chatId,
          targetType: "chat",
          previewText: msg.text || (msg.attachmentUrl ? `Sent a ${msg.attachmentType?.split('/')[0]}` : "New message"),
          channel: "messages",
          priority: "high",
          collapseKey: `chat_${msg.chatId}`,
        });
      }
    };

    const handleIncomingCall = (call: CallData) => {
      if (call.calleeId === currentUser.id) {
        handleNotification({
          type: "call_incoming",
          senderId: call.callerId,
          senderName: call.callerName || "Someone",
          targetId: call.callId,
          targetType: "call",
          previewText: `Incoming ${call.isVideo ? "video " : ""}call`,
          channel: "calls",
          priority: "urgent",
          collapseKey: `call_${call.callId}`,
        });
      }
    };

    const handleStatusAdded = (data: { userId: string, id: string, userName: string }) => {
      if (data.userId !== currentUser.id) {
        handleNotification({
          type: "status_post",
          senderId: data.userId,
          senderName: data.userName,
          targetId: data.id,
          targetType: "status",
          channel: "status_posts",
          priority: "default",
          collapseKey: `status_post_${data.userId}`,
        });
      }
    };

    socket.on("notification", handleNotification);
    socket.on("receive_message", handleNewMessage);
    socket.on("call:start", handleIncomingCall);
    socket.on("status_added", handleStatusAdded);

    return () => {
      socket.off("notification", handleNotification);
      socket.off("receive_message", handleNewMessage);
      socket.off("call:start", handleIncomingCall);
      socket.off("status_added", handleStatusAdded);
    };
  }, [socket, currentUser, notificationsEnabled]);

  return (
    <NotificationContext.Provider value={{ notificationsEnabled, requestPermission, toggleNotifications }}>
      {children}
    </NotificationContext.Provider>
  );
}
