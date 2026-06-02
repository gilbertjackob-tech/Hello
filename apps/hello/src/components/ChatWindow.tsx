import React, {
  useEffect,
  useState,
  useRef,
  FormEvent,
  ChangeEvent,
} from "react";
import { format } from "date-fns";
import { motion } from "motion/react";
import {
  Bot,
  Contact,
  FileText,
  ImagePlus,
  MonitorSmartphone,
  MoreVertical,
  Search,
  Send,
  Smile,
  Paperclip,
  X,
  Mic,
  Video,
  Phone,
  Menu,
  Check,
  CheckCheck,
  ChevronDown,
  Star,
  Pin,
  MapPin,
  Clock,
  Download,
  Sparkles,
} from "lucide-react";
import { useSocket } from "../SocketContext";
import type { Chat, Message, User, Contact as ContactType } from "../types";
import { LocationShareModal } from "./LocationShareModal";
import { ForwardModal } from "./ForwardModal";
import { ContactInfoPanel } from "./ContactInfoPanel";
import { AnimatedMessageBubble } from "./AnimatedMessageBubble";
import { AnimatedMessageList } from "./AnimatedMessageList";
import { useOptimisticMessage } from "../hooks/useOptimisticMessage";
import {
  fetchMessages,
  sendMessage,
  reactToMessage,
  starMessage,
  pinMessage,
  deleteMessage,
  uploadFile,
  resolveCloudChatUrl,
} from "../api";
import { cn, formatLastActive } from "../lib/utils";
import { useTheme } from "../ThemeContext";
import { useToast } from "../ToastContext";
import { EmptyState, SkeletonBlock } from "./HelloUi";
import {
  describeMediaAccessError,
  requestUserMediaWithDiagnostics,
} from "../mediaPermissions";

function formatBytes(bytes?: number, decimals = 2) {
  if (!bytes) return "0 Bytes";
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}

interface ChatWindowProps {
  key?: React.Key;
  chat: Chat;
  currentUser: User;
  onToggleSidebar?: () => void;
  isSidebarOpen?: boolean;
}

export function ChatWindow({
  chat,
  currentUser,
  onToggleSidebar,
  isSidebarOpen,
}: ChatWindowProps) {
  const { pushToast } = useToast();
  const {
    messages,
    setMessages,
    addOptimisticMessage,
    confirmMessage,
    failMessage,
  } = useOptimisticMessage([]);
  const [text, setText] = useState("");
  const [messagesLoading, setMessagesLoading] = useState(true);
  const [pendingAttachment, setPendingAttachment] = useState<{
    url: string;
    type: "image" | "file";
    name: string;
    file?: File;
  } | null>(null);
  const [hoveredMsgId, setHoveredMsgId] = useState<string | null>(null);
  const [optionsMsgId, setOptionsMsgId] = useState<string | null>(null);
  const [typingUsers, setTypingUsers] = useState<string[]>([]);
  const [showLocationModal, setShowLocationModal] = useState(false);
  const [forwardingMessage, setForwardingMessage] = useState<Message | null>(
    null,
  );
  const [showSearch, setShowSearch] = useState(false);
  const [showContactInfo, setShowContactInfo] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [attachmentMenuOpen, setAttachmentMenuOpen] = useState(false);

  // Swipe to reply states
  const [replyingToMessage, setReplyingToMessage] = useState<Message | null>(
    null,
  );
  const [touchStart, setTouchStart] = useState<{ x: number; y: number } | null>(
    null,
  );
  const [swipeOffset, setSwipeOffset] = useState<{
    id: string;
    x: number;
  } | null>(null);

  const handleTouchStart = (e: React.TouchEvent, msg: Message) => {
    if (msg.senderId === "system" || msg.senderId === "cli-bot") return;
    setTouchStart({ x: e.touches[0].clientX, y: e.touches[0].clientY });
  };

  const handleTouchMove = (e: React.TouchEvent, msg: Message) => {
    if (!touchStart) return;
    const currentX = e.touches[0].clientX;
    const deltaX = currentX - touchStart.x;

    // Only allow swipe to right
    if (deltaX > 0 && deltaX < 80) {
      setSwipeOffset({ id: msg.id, x: deltaX });
    }
  };

  const handleTouchEnd = (msg: Message) => {
    if (swipeOffset && swipeOffset.id === msg.id && swipeOffset.x > 50) {
      setReplyingToMessage(msg);
    }
    setTouchStart(null);
    setSwipeOffset(null);
  };

  const typingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { socket } = useSocket();
  const { enterIsSend, chatWallpaper, chatWallpaperOpacity } = useTheme();
  const bottomRef = useRef<HTMLDivElement>(null);

  const [otherUserPresence, setOtherUserPresence] = useState<{
    online: boolean;
    lastActive?: number;
    privacy: string;
  } | null>(null);

  useEffect(() => {
    // If 1-on-1 chat, find the other user's presence
    if (!chat.isGroup && chat.members) {
      const otherId = chat.members.find((m) => m !== currentUser.id);
      if (otherId) {
        import("../api").then((api) => {
          api
            .fetchUserPresence(otherId)
            .then((data) => {
              setOtherUserPresence({
                online: data.online,
                lastActive: data.lastActive,
                privacy: data.privacy,
              });
            })
            .catch((err) => {
              console.error("Could not fetch presence", err);
            });
        });
      } else {
        setOtherUserPresence(null);
      }
    } else {
      setOtherUserPresence(null);
    }
  }, [chat.id, chat.isGroup, chat.members, currentUser.id]);

  useEffect(() => {
    // Check for any active live locations we sent
    const activeLiveMsgs = messages.filter(
      (m) =>
        m.senderId === currentUser.id &&
        m.location?.isLive &&
        m.location.expiresAt &&
        m.location.expiresAt > Date.now(),
    );

    if (activeLiveMsgs.length === 0) return;

    if (!("geolocation" in navigator)) return;

    const watchId = navigator.geolocation.watchPosition(
      async (position) => {
        const { latitude, longitude } = position.coords;
        for (const msg of activeLiveMsgs) {
          // If expired, skip
          if (msg.location?.expiresAt && msg.location.expiresAt < Date.now())
            continue;

          try {
            // We use dynamic import to avoid altering the top of file manually for updateLiveLocation
            const api = await import("../api");
            await api.updateLiveLocation(chat.id, msg.id, latitude, longitude);
          } catch (e) {
            console.error("Failed to update live location", e);
          }
        }
      },
      (err) => console.warn(err),
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 5000 },
    );

    return () => navigator.geolocation.clearWatch(watchId);
  }, [messages, currentUser.id, chat.id]);

  const handleShareLocation = (isLive: boolean, durationMinutes?: number, manualLocation?: string) => {
    if (manualLocation) {
        const locationData = {
          lat: 0,
          lng: 0,
          manualText: manualLocation,
          isLive: false,
        };

        setShowLocationModal(false);
        sendMessage(
          chat.id,
          "",
          undefined,
          undefined,
          undefined,
          undefined,
          currentUser.id,
          currentUser.name,
          currentUser.avatar,
          locationData,
        ).then(msg => setMessages((prev) => [...prev, msg]))
         .catch(e => console.error("Failed to share location", e));
        return;
    }

    if (!("geolocation" in navigator)) {
      pushToast({
        title: "Location is unavailable",
        description: "This browser does not support geolocation.",
        tone: "warning",
      });
      return;
    }

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const { latitude, longitude } = position.coords;
        const locationData = {
          lat: latitude,
          lng: longitude,
          isLive,
          expiresAt:
            isLive && durationMinutes
              ? Date.now() + durationMinutes * 60000
              : undefined,
        };

        setShowLocationModal(false);
        try {
          const msg = await sendMessage(
            chat.id,
            "",
            undefined,
            undefined,
            undefined,
            undefined,
            currentUser.id,
            currentUser.name,
            currentUser.avatar,
            locationData,
          );
          setMessages((prev) => [...prev, msg]);
        } catch (e) {
          console.error("Failed to share location", e);
        }
      },
      (error) => {
        console.error(error);
        pushToast({
          title: "Location permission is blocked",
          description: "Allow location access and try again.",
          tone: "error",
        });
      },
    );
  };

  // ... (Rest of ChatWindow)
  useEffect(() => {
    let mounted = true;
    // Only show loading if we don't have any messages cached yet
    if (messages.length === 0) {
      setMessagesLoading(true);
    }
    fetchMessages(chat.id)
      .then((msgs) => {
        if (mounted) setMessages(msgs);
      })
      .catch(console.error)
      .finally(() => {
        if (mounted) setMessagesLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [chat.id]);

  useEffect(() => {
    if (!socket) return;

    socket.emit("join_chat", chat.id);
    socket.emit("mark_messages_read", {
      chatId: chat.id,
      readerId: currentUser.id,
    });

    const handleNewMessage = (msg: Message) => {
      console.log("CLIENT SOCKET MSG DEBUG:", {
        receivedChatId: msg.chatId,
        currentOpenChatId: chat.id,
        senderName: msg.senderName,
      });

      if (msg.chatId === chat.id) {
        setMessages((prev) => {
          if (prev.find((m) => m.id === msg.id)) return prev;
          return [...prev, msg];
        });
        if (msg.senderId !== currentUser.id) {
          socket.emit("mark_messages_read", {
            chatId: chat.id,
            readerId: currentUser.id,
          });
        }
      }
    };

    const handleMessageUpdated = (msg: Message) => {
      if (msg.chatId === chat.id) {
        setMessages((prev) =>
          prev.map((m) => (m.id === msg.id ? { ...m, ...msg } : m)),
        );
      }
    };

    const handleUserTyping = (data: {
      chatId: string;
      senderName: string;
      isTyping?: boolean;
    }) => {
      if (data.chatId === chat.id && data.senderName !== currentUser.name) {
        if (data.isTyping === false) {
          setTypingUsers((prev) =>
            prev.filter((name) => name !== data.senderName),
          );
        } else {
          setTypingUsers((prev) => {
            if (!prev.includes(data.senderName)) {
              return [...prev, data.senderName];
            }
            return prev;
          });
          // Also set a fallback timeout to remove them automatically just in case
          setTimeout(() => {
            setTypingUsers((prev) => prev.filter((n) => n !== data.senderName));
          }, 3000);
        }
      }
    };

    const handlePresenceUpdated = (data: {
      id?: string;
      userId: string;
      online: boolean;
      lastActive: number;
      privacy?: string;
    }) => {
      const presenceUserId = data.userId || data.id;
      // If we are looking at 1-1 chat
      if (
        !chat.isGroup &&
        chat.members &&
        presenceUserId &&
        chat.members.includes(presenceUserId) &&
        presenceUserId !== currentUser.id
      ) {
        setOtherUserPresence({
          online: data.online,
          lastActive: data.lastActive,
          privacy: data.privacy || "everyone",
        });
      }
    };

    socket.on("receive_message", handleNewMessage);
    socket.on("message_updated", handleMessageUpdated);
    socket.on("user_typing", handleUserTyping);
    socket.on("user_presence", handlePresenceUpdated);
    socket.on("presence_updated", handlePresenceUpdated);

    return () => {
      socket.off("receive_message", handleNewMessage);
      socket.off("message_updated", handleMessageUpdated);
      socket.off("user_typing", handleUserTyping);
      socket.off("user_presence", handlePresenceUpdated);
      socket.off("presence_updated", handlePresenceUpdated);
      socket.emit("leave_chat", chat.id);
    };
  }, [socket, chat.id]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleTextChange = (e: ChangeEvent<HTMLInputElement>) => {
    setText(e.target.value);

    if (socket) {
      socket.emit("typing", {
        chatId: chat.id,
        senderName: currentUser.name,
        isTyping: true,
      });

      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }

      typingTimeoutRef.current = setTimeout(() => {
        socket.emit("typing", {
          chatId: chat.id,
          senderName: currentUser.name,
          isTyping: false,
        });
      }, 1500);
    }
  };

  const handleSend = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!text.trim() && !pendingAttachment) return;

    if (socket) {
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
      socket.emit("typing", {
        chatId: chat.id,
        senderName: currentUser.name,
        isTyping: false,
      });
    }

    const currentText = text;
    const currentAttachment = pendingAttachment;
    const currentReply = replyingToMessage
      ? {
          id: replyingToMessage.id,
          text: replyingToMessage.text || "Attachment",
          senderName: replyingToMessage.senderName,
          senderId: replyingToMessage.senderId,
        }
      : undefined;

    // Add optimistic message immediately for instant UI feedback
    const tempId = addOptimisticMessage(
      {
        senderId: currentUser.id,
        senderName: currentUser.name,
        senderAvatar: currentUser.avatar,
        text: currentText || " ",
        attachmentUrl: currentAttachment?.url,
        attachmentType: currentAttachment?.type,
        attachmentName: currentAttachment?.name,
        replyTo: currentReply,
      },
      chat.id
    );

    setText("");
    setPendingAttachment(null);
    setReplyingToMessage(null);

    try {
      let attachmentUrl = currentAttachment?.url;
      let attachmentType = currentAttachment?.type;
      let attachmentName = currentAttachment?.name;
      let attachmentSize;

      if (currentAttachment?.file) {
        // Upload the file
        const uploaded = await uploadFile(
          currentAttachment.file,
          currentUser.id,
        );
        attachmentUrl = uploaded.url;
        attachmentType = uploaded.mimeType.startsWith("image/")
          ? "image"
          : "file";
        attachmentName = uploaded.originalName || currentAttachment.name;
        attachmentSize = uploaded.size;
      }

      const realMessage = await sendMessage(
        chat.id,
        currentText || " ",
        attachmentUrl,
        attachmentType,
        attachmentName,
        attachmentSize,
        currentUser.id,
        currentUser.name,
        currentUser.avatar,
        undefined,
        currentReply,
      );

      // Confirm optimistic message with real message data
      confirmMessage(tempId, realMessage);
    } catch (e) {
      console.error(e);
      failMessage(tempId);
      pushToast({
        title: "Message failed to send",
        description: String(e),
        tone: "error",
        durationMs: 4200,
      });
    }
  };

  const handleFileSelect = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async () => {
      const base64Url = reader.result as string;
      const isImage = file.type.startsWith("image/");
      setPendingAttachment({
        url: base64Url,
        type: isImage ? "image" : "file",
        name: file.name,
        file: file,
      });
    };
    reader.readAsDataURL(file);
    e.target.value = "";
  };

  const handleAttachmentAction = (action: string) => {
    setAttachmentMenuOpen(false);
    if (action === "photo" || action === "camera" || action === "file") {
      fileInputRef.current?.click();
      return;
    }
    if (action === "location") {
      setShowLocationModal(true);
      return;
    }
    if (action === "contact") {
      pushToast({
        title: "Contact sharing opens from your contacts rail",
        description: "Pick a contact there, then come back to send it here.",
        tone: "loading",
      });
      return;
    }
    const labels: Record<string, string> = {
      browser_tab: "Share current browser tab",
      screenshot: "Share browser screenshot",
      automation_result: "Send automation result",
      summarize: "Ask GlassBox to summarize page",
    };
    pushToast({
      title: labels[action] || "Coming soon",
      description: "The UI is staged. Backend wiring can land without changing this conversation flow.",
      tone: "loading",
      durationMs: 4200,
    });
  };

  const handleReact = async (msgId: string, emoji: string) => {
    const previousMessages = messages;
    setMessages((prev) =>
      prev.map((message) => {
        if (message.id !== msgId) return message;
        const current = message.reactions || [];
        const hasReaction = current.some(
          (reaction) => reaction.emoji === emoji && reaction.userId === currentUser.id,
        );
        return {
          ...message,
          reactions: hasReaction
            ? current.filter((reaction) => !(reaction.emoji === emoji && reaction.userId === currentUser.id))
            : [...current, { emoji, userId: currentUser.id }],
        };
      }),
    );
    try {
      const updated = await reactToMessage(chat.id, msgId, emoji, currentUser.id);
      setMessages((prev) =>
        prev.map((message) => (message.id === updated.id ? { ...message, ...updated } : message)),
      );
    } catch (err) {
      console.error(err);
      setMessages(previousMessages);
    }
  };

  const handleStar = async (msgId: string) => {
    try {
      await starMessage(chat.id, msgId, currentUser.id);
      setOptionsMsgId(null);
    } catch (err) {
      console.error(err);
    }
  };

  const handlePin = async (msgId: string, durationDays: number) => {
    try {
      await pinMessage(chat.id, msgId, durationDays);
      setOptionsMsgId(null);
    } catch (err) {
      console.error(err);
    }
  };

  const handleDelete = async (
    msgId: string,
    type: "for_me" | "for_everyone",
  ) => {
    try {
      await deleteMessage(chat.id, msgId, currentUser.id, type);
      // For 'for_me', it doesn't trigger a broadcast so we must manually filter locally if we don't rely only on the 'message_updated' socket.
      // Wait, let's just trigger a re-fetch or optimistically update the state.
      setMessages((prev) =>
        prev.map((m) =>
          m.id === msgId
            ? type === "for_me"
              ? { ...m, deletedFor: [...(m.deletedFor || []), currentUser.id] }
              : {
                  ...m,
                  isDeleted: true,
                  text: "This message was deleted",
                  attachmentUrl: undefined,
                  attachmentType: undefined,
                }
            : m,
        ),
      );
      setOptionsMsgId(null);
    } catch (err) {
      console.error(err);
    }
  };

  const initiateCall = (isVideo: boolean) => {
    if (!socket) return;
    const other = !chat.isGroup
      ? chat.participants?.find((p) => p.id !== currentUser.id)
      : undefined;
    if (chat.isGroup) {
      const participants = (chat.participants || []).filter((p) => p.id !== currentUser.id);
      window.dispatchEvent(
        new CustomEvent("START_GROUP_CALL", {
          detail: {
            chatId: chat.id,
            chatName,
            participantIds: participants.map((p) => p.id),
            isVideo,
          },
        }),
      );
      return;
    }
    if (!other) return;

    window.dispatchEvent(
      new CustomEvent("START_CALL", {
        detail: {
          chatId: chat.id,
          calleeId: other.id,
          calleeName: other.name,
          calleeAvatar: other.avatar,
          isVideo,
        },
      }),
    );
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      if (enterIsSend) {
        if (!e.shiftKey) {
          e.preventDefault();
          if (text.trim() || pendingAttachment) {
            handleSend(e as unknown as FormEvent);
          }
        }
      } else {
        // Just let it act normally, though input text doesn't break lines.
        // Prevent submission since it's inside a form perhaps? No, if enterIsSend=false, we don't submit.
        e.preventDefault();
      }
    }
  };

  const isRecordingRef = useRef(false);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingDuration, setRecordingDuration] = useState(0);
  const [recordingCancelled, setRecordingCancelled] = useState(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const startRecording = async (e: React.MouseEvent | React.TouchEvent) => {
    e.preventDefault();
    if (isRecordingRef.current) return;
    isRecordingRef.current = true;
    setRecordingCancelled(false);
    try {
      const stream = await requestUserMediaWithDiagnostics({ audio: true });
      if (!isRecordingRef.current) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      streamRef.current = stream;
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];

      mediaRecorder.ondataavailable = (evt) => {
        if (evt.data.size > 0) {
          audioChunksRef.current.push(evt.data);
        }
      };

      mediaRecorder.onstop = async () => {
        const mimeType = mediaRecorder.mimeType || "audio/webm";
        const audioBlob = new Blob(audioChunksRef.current, {
          type: mimeType,
        });

        streamRef.current?.getTracks().forEach((track) => track.stop());

        if (audioChunksRef.current.length > 0) {
          if (recordingCancelled) {
            setIsRecording(false);
            isRecordingRef.current = false;
            if (timerRef.current) clearInterval(timerRef.current);
            pushToast({
              title: "Voice note discarded",
              tone: "warning",
            });
            return;
          }
          try {
            const ext = mimeType.includes("mp4") ? "mp4" : mimeType.includes("ogg") ? "ogg" : "webm";
            const audioFile = new File(
              [audioBlob],
              `audio_${Date.now()}.${ext}`,
              { type: mimeType },
            );
            const uploaded = await uploadFile(audioFile, currentUser.id);

            await sendMessage(
              chat.id,
              " ",
              uploaded.url,
              "audio",
              uploaded.originalName,
              uploaded.size,
              currentUser.id,
              currentUser.name,
              currentUser.avatar,
            );
          } catch (e) {
            console.error(e);
            pushToast({
              title: "Voice note failed",
              description: "The recording was captured, but the upload failed.",
              tone: "error",
            });
          }
        }
        setIsRecording(false);
        isRecordingRef.current = false;
        if (timerRef.current) clearInterval(timerRef.current);
      };

      mediaRecorder.start();
      setIsRecording(true);
      setRecordingDuration(0);

      timerRef.current = setInterval(() => {
        setRecordingDuration((prev) => prev + 1);
      }, 1000);
    } catch (err) {
      console.error("Error accessing microphone:", err);
      pushToast({
        title: "Microphone unavailable",
        description: describeMediaAccessError(err, "Could not access microphone."),
        tone: "error",
        durationMs: 4600,
      });
      isRecordingRef.current = false;
      setIsRecording(false);
    }
  };

  const stopRecording = (e: React.MouseEvent | React.TouchEvent) => {
    e.preventDefault();
    isRecordingRef.current = false;
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.stop();
    }
  };

  const cancelRecording = () => {
    setRecordingCancelled(true);
    isRecordingRef.current = false;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.stop();
    } else {
      setIsRecording(false);
      if (timerRef.current) clearInterval(timerRef.current);
    }
  };

  const formatDuration = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  const otherParticipant = !chat.isGroup 
    ? chat.participants?.find((p) => p.id !== currentUser.id)
    : null;

  const chatName = otherParticipant ? otherParticipant.name : chat.name;
  const chatAvatar = otherParticipant?.avatar || chat.avatar;

  const renderPresence = () => {
    if (typingUsers.length > 0) return null;
    if (chat.isGroup) return null;

    const onlineStatus = otherUserPresence?.online ?? otherParticipant?.online;
    const privacy = otherUserPresence?.privacy ?? "everyone";
    const lastActive = otherUserPresence?.lastActive ?? otherParticipant?.lastActive;

    if (privacy === "none") return null;

    if (privacy === "contacts") {
      const savedContacts = localStorage.getItem("whatsclone_contacts");
      const localContacts = savedContacts
        ? (JSON.parse(savedContacts) as ContactType[])
        : [];
      const isContact = localContacts.some(
        (c: ContactType) => c.name === chatName,
      );
      if (!isContact) return null;
    }

    if (onlineStatus) {
      return (
        <p className="text-[13px] text-emerald-500 dark:text-[#aebac1] font-medium truncate">
          online
        </p>
      );
    } else if (lastActive) {
      return (
        <p className="text-[13px] text-slate-500 dark:text-[#aebac1] truncate">
          {formattedLastSeen(lastActive)}
        </p>
      );
    }
  };

  const formattedLastSeen = (timestamp?: number | null) => {
    if (!timestamp) return "Last active unknown";
    const date = new Date(timestamp);
    if (Number.isNaN(date.getTime())) return "Last active unknown";
    return `Last active ${formatLastActive(timestamp)}`;
  };

  const getWallpaperStyles = () => {
    let bg = "";
    switch (chatWallpaper) {
      case "solid-dark": return { backgroundColor: "#0f172a" };
      case "solid-light": return { backgroundColor: "#f1f5f9" };
      case "emerald": return { backgroundColor: "#064e3b" };
      case "rose": return { backgroundColor: "#881337" };
      case "ocean": return { backgroundColor: "#0c4a6e" };
      case "texture-paper": bg = "url('https://images.unsplash.com/photo-1628155930542-3c7a64e2c833?auto=format&fit=crop&w=1000&q=80')"; break;
      case "texture-wood": bg = "url('https://images.unsplash.com/photo-1550684848-fac1c5b4e853?auto=format&fit=crop&w=1000&q=80')"; break;
      case "texture-dots": bg = "radial-gradient(#9ca3af 1px, transparent 1px)"; break;
      case "texture-lines": bg = "repeating-linear-gradient(45deg, #00000010, #00000010 10px, transparent 10px, transparent 20px)"; break;
      case "img-cute": bg = "url('https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?auto=format&fit=crop&w=1000&q=80')"; break;
      case "img-romantic": bg = "url('https://images.unsplash.com/photo-1518199266791-5375a83190b7?auto=format&fit=crop&w=1000&q=80')"; break;
      case "img-professional": bg = "url('https://images.unsplash.com/photo-1557683316-973673baf926?auto=format&fit=crop&w=1000&q=80')"; break;
      case "img-nature": bg = "url('https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=1000&q=80')"; break;
      case "img-space": bg = "url('https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=1000&q=80')"; break;
      default: return { backgroundColor: "var(--tw-colors-slate-200)", backgroundImage: "url('https://web.whatsapp.com/img/bg-chat-tile-dark_a4be512e7195b6b733d9110b408f075d.png')" }; // Using a default simple background pattern or fallback color
    }
    
    return {
       backgroundImage: bg,
       backgroundSize: bg.startsWith("url") ? "cover" : bg.includes("radial") ? "20px 20px" : "auto",
       backgroundPosition: "center",
    };
  };

  return (
    <div className="flex flex-col h-full w-full flex-1 min-h-0 transition-colors duration-300">
      {showLocationModal && (
        <LocationShareModal
          onClose={() => setShowLocationModal(false)}
          onShare={handleShareLocation}
        />
      )}

      {forwardingMessage && (
        <ForwardModal
          message={forwardingMessage}
          currentUser={currentUser}
          onClose={() => setForwardingMessage(null)}
          onForwardSuccess={() => {
            // Optional: provide some form of toast or just close
            setForwardingMessage(null);
          }}
        />
      )}

      {showContactInfo && (
        <ContactInfoPanel
          chat={chat}
          currentUser={currentUser}
          onClose={() => setShowContactInfo(false)}
          onSearch={() => {
            setShowContactInfo(false);
            setShowSearch(true);
          }}
          onClearChat={async () => {
             if (window.confirm("Are you sure you want to clear messages in this chat locally?")) {
                 try {
                   const { clearChat } = await import("../api");
                   await clearChat(chat.id, currentUser.id);
                   setMessages([]);
                   setShowContactInfo(false);
                 } catch (err) {
                   console.error("Failed to clear chat", err);
                 }
             }
          }}
          onDeleteChat={async () => {
             if (window.confirm("Are you sure you want to delete this chat locally?")) {
                 try {
                   const { deleteChat } = await import("../api");
                   await deleteChat(chat.id, currentUser.id);
                   if (onToggleSidebar) onToggleSidebar();
                 } catch (err) {
                   console.error("Failed to delete chat", err);
                 }
             }
          }}
        />
      )}

      {/* Header */}
      <header className="h-16 flex-none bg-white dark:bg-[#202c33] flex items-center px-6 justify-between z-10 relative transition-colors duration-300">
        <div className="flex items-center space-x-4 pl-10 md:pl-0">
          {!isSidebarOpen && onToggleSidebar && (
            <button
              onClick={onToggleSidebar}
              className="hidden md:flex absolute left-4 w-8 h-8 rounded-full bg-slate-100 dark:bg-slate-700 text-slate-800 dark:text-slate-200 items-center justify-center hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
            >
              <Menu className="w-4 h-4" />
            </button>
          )}
          <div 
            className="flex items-center space-x-4 cursor-pointer"
            onClick={() => setShowContactInfo(true)}
          >
            <div className="w-10 h-10 rounded-full bg-slate-300 dark:bg-[#111b21] flex items-center justify-center text-slate-700 dark:text-[#aebac1] font-bold overflow-hidden shrink-0">
              {chatAvatar ? (
                <img
                  src={chatAvatar}
                  alt="Avatar"
                  className="w-full h-full object-cover"
                />
              ) : (
                chatName.charAt(0).toUpperCase()
              )}
            </div>
            <div className="min-w-0">
              <h3 className="font-medium text-[16px] leading-tight text-slate-800 dark:text-[#e9edef] truncate">
                {chatName}
              </h3>
            {typingUsers.length > 0 ? (
              <p className="text-[13px] text-[#00a884] font-medium truncate flex items-center">
                {typingUsers.join(", ")}{" "}
                {typingUsers.length === 1 ? "is" : "are"} typing
                <span className="flex items-center ml-1 space-x-[1px]">
                  <span className="w-1 h-1 bg-[#00a884] rounded-full animate-bounce [animation-delay:-0.3s]"></span>
                  <span className="w-1 h-1 bg-[#00a884] rounded-full animate-bounce [animation-delay:-0.15s]"></span>
                  <span className="w-1 h-1 bg-[#00a884] rounded-full animate-bounce"></span>
                </span>
              </p>
            ) : chat.isGroup ? (
              <p className="text-[13px] text-slate-500 dark:text-[#8696a0] truncate max-w-[150px] md:max-w-[300px]">
                {chat.members?.join(", ") || "You, Members..."}
              </p>
            ) : (
              renderPresence()
            )}
            </div>
          </div>
        </div>
        <div className="flex items-center space-x-4 md:space-x-6 text-slate-500 dark:text-[#aebac1]">
          <div className="flex items-center bg-slate-100 dark:bg-[#2a3942] rounded-full overflow-hidden cursor-pointer px-1 py-0.5">
            <button
              onClick={() => initiateCall(true)}
              className="hover:bg-slate-200 dark:hover:bg-[#374248] rounded-full p-2 transition-colors flex items-center"
              title="Video Call"
            >
              <Video className="w-5 h-5" />
            </button>
            <button
              onClick={() => initiateCall(false)}
              className="hover:bg-slate-200 dark:hover:bg-[#374248] rounded-full p-2 transition-colors flex items-center"
              title="Voice Call"
            >
              <Phone className="w-5 h-5" />
            </button>
            <div className="w-px h-5 bg-slate-300 dark:bg-[#374248] mx-1"></div>
            <button className="hover:bg-slate-200 dark:hover:bg-[#374248] rounded-full p-1 transition-colors px-2">
              <svg
                viewBox="0 0 24 24"
                width="16"
                height="16"
                fill="currentColor"
              >
                <path d="M7 10l5 5 5-5z"></path>
              </svg>
            </button>
          </div>

          <button
            className="hover:text-slate-700 dark:hover:text-[#e9edef] transition-colors hidden md:block"
            title="Search"
            onClick={() => {
              setShowSearch(!showSearch);
              if (showSearch) setSearchQuery("");
            }}
          >
            <Search className="w-5 h-5" />
          </button>
          <button
            className="hover:text-slate-700 dark:hover:text-[#e9edef] transition-colors hidden md:block"
            title="More options"
            onClick={() =>
              pushToast({
                title: "Chat actions",
                description: "Per-chat settings will be expanded in the next pass.",
                tone: "loading",
              })
            }
          >
            <MoreVertical className="w-5 h-5" />
          </button>
        </div>
      </header>

      {/* Search Bar */}
      {showSearch && (
        <div className="bg-slate-50 dark:bg-[#202c33] border-b border-slate-200 dark:border-[#2f3b43] px-4 py-2 flex items-center shadow-sm z-10 relative">
          <div className="flex-1 flex items-center bg-white dark:bg-[#2a3942] rounded-md px-3 py-1.5 border border-slate-200 dark:border-transparent focus-within:ring-1 focus-within:ring-[#00a884] transition-all">
            <Search className="w-4 h-4 text-slate-500 dark:text-[#aebac1] mr-2" />
            <input
              type="text"
              placeholder="Search messages..."
              className="bg-transparent border-none outline-none text-sm w-full text-slate-800 dark:text-[#e9edef] placeholder-slate-400 dark:placeholder-slate-500"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              autoFocus
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery("")}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 ml-2"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>
          <button
            onClick={() => {
              setShowSearch(false);
              setSearchQuery("");
            }}
            className="ml-3 text-sm text-slate-600 dark:text-[#aebac1] hover:text-slate-800 dark:hover:text-[#e9edef]"
          >
            Cancel
          </button>
        </div>
      )}

      {/* Pinned Messages Banner */}
      {messages
        .filter((m) => !m.deletedFor?.includes(currentUser.id))
        .filter((m) => m.pinnedUntil && m.pinnedUntil > Date.now()).length >
        0 && (
        <div
          className="bg-slate-100 dark:bg-[#202c33] border-b border-slate-200 dark:border-[#2f3b43] px-4 py-2 flex items-center justify-between cursor-pointer"
          onClick={() => {
            // Scroll to message or show all pinned messages
            const pinnedMsgs = messages
              .filter((m) => !m.deletedFor?.includes(currentUser.id))
              .filter((m) => m.pinnedUntil && m.pinnedUntil > Date.now());
            const latestPinned = pinnedMsgs[pinnedMsgs.length - 1];
            pushToast({
              title: "Pinned message",
              description: latestPinned.text || "Attachment",
              tone: "success",
              durationMs: 4200,
            });
          }}
        >
          <div className="flex items-center text-sm overflow-hidden">
            <Pin className="w-4 h-4 text-slate-500 dark:text-[#8696a0] mr-3 shrink-0" />
            <div className="flex flex-col min-w-0">
              <span className="font-semibold text-slate-700 dark:text-[#e9edef] text-[13px] truncate">
                Pinned Message
              </span>
              <span className="text-slate-500 dark:text-[#8696a0] truncate text-[13px]">
                {
                  messages
                    .filter((m) => !m.deletedFor?.includes(currentUser.id))
                    .filter((m) => m.pinnedUntil && m.pinnedUntil > Date.now())
                    .pop()?.text
                }
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Messages */}
      <div className="flex-1 min-h-0 relative overflow-hidden flex flex-col bg-[#EFEAE2] dark:bg-[#0b141a]">
        <div 
           className="absolute inset-0 z-0 pointer-events-none transition-all duration-300" 
           style={{ ...getWallpaperStyles(), opacity: (chatWallpaperOpacity ?? 100) / 100 }} 
        />
        <section
          className="flex-1 min-h-0 overflow-y-auto p-4 md:p-8 space-y-4 z-10 relative custom-scrollbar"
        >
        <div className="flex justify-center">
          <span className="bg-white/80 dark:bg-slate-800/80 px-3 py-1 rounded-md text-[10px] text-slate-500 dark:text-slate-400 uppercase tracking-widest shadow-sm">
            Today
          </span>
        </div>

        {messagesLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 6 }).map((_, index) => (
              <div
                key={index}
                className={cn("flex", index % 2 === 0 ? "justify-start" : "justify-end")}
              >
                <div className="max-w-[78%]">
                  <SkeletonBlock className="mb-2 h-3 w-16 rounded-full" />
                  <SkeletonBlock className="h-24 w-56 rounded-[22px]" />
                </div>
              </div>
            ))}
          </div>
        ) : messages
            .filter((m) => !m.deletedFor?.includes(currentUser.id))
            .filter(
              (m) =>
                !searchQuery ||
                (m.text &&
                  m.text.toLowerCase().includes(searchQuery.toLowerCase())),
            ).length === 0 ? (
          <EmptyState
            icon={<MessageCircleMore className="h-8 w-8" />}
            title={searchQuery ? "No messages match that search" : "No messages yet"}
            description={
              searchQuery
                ? "Try another keyword or clear message search."
                : "Start the conversation with text, voice, location, files, or a GlassBox share."
            }
            className="mx-auto mt-16 max-w-md"
          />
        ) : (
        messages
          .filter((m) => !m.deletedFor?.includes(currentUser.id))
          .filter(
            (m) =>
              !searchQuery ||
              (m.text &&
                m.text.toLowerCase().includes(searchQuery.toLowerCase())),
          )
          .map((msg, idx, arr) => {
            const isMe =
              msg.senderId === currentUser.id || msg.senderId === "local-user";
            const isTerminal =
              msg.senderId === "cli-bot" || msg.senderId === "system";
            const showName =
              !isMe && (idx === 0 || arr[idx - 1].senderId !== msg.senderId);

            return (
              <div
                key={msg.id}
                id={`msg-${msg.id}`}
                className={cn(
                  "flex relative transition-all duration-300 ease-out",
                  isMe ? "justify-end" : "justify-start",
                )}
                style={{
                  transform:
                    swipeOffset?.id === msg.id
                      ? `translateX(${swipeOffset.x}px)`
                      : "translateX(0px)",
                }}
                onMouseEnter={() => setHoveredMsgId(msg.id)}
                onMouseLeave={() => setHoveredMsgId(null)}
                onTouchStart={(e) => handleTouchStart(e, msg)}
                onTouchMove={(e) => handleTouchMove(e, msg)}
                onTouchEnd={() => handleTouchEnd(msg)}
              >
                {/* Avatar for Incoming Messages */}
                {!isMe && !isTerminal && (
                  <div className="w-8 h-8 rounded-full bg-slate-200 dark:bg-slate-700 shrink-0 mr-2 md:mr-3 mt-auto mb-1 flex items-center justify-center overflow-hidden border border-slate-100 dark:border-slate-800">
                    {msg.senderAvatar ? (
                      <img
                        src={msg.senderAvatar}
                        alt={msg.senderName}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <span className="text-slate-500 dark:text-slate-400 text-xs font-bold">
                        {msg.senderName.charAt(0).toUpperCase()}
                      </span>
                    )}
                  </div>
                )}
                {isTerminal && !isMe && (
                  <div className="w-8 shrink-0 mr-2 md:mr-3" />
                )}

                {/* Message Bubble Container */}
                <div className="relative group flex items-center max-w-[85%] md:max-w-[75%]">
                  {/* Reaction Picker (Shows on hover) */}
                  {hoveredMsgId === msg.id && !isTerminal && (
                    <div
                      className={cn(
                        "absolute -top-8 bg-white dark:bg-slate-800 shadow-md rounded-full px-2 py-1 flex space-x-2 z-20 transition-all border border-slate-100 dark:border-slate-700",
                        isMe ? "right-0" : "left-0",
                      )}
                    >
                      {["👍", "❤️", "😂", "😮", "😢"].map((emoji) => (
                        <button
                          key={emoji}
                          onClick={() => handleReact(msg.id, emoji)}
                          className="hover:scale-125 transition-transform text-sm"
                          title="React"
                        >
                          {emoji}
                        </button>
                      ))}
                    </div>
                  )}

                  <div
                    className={cn(
                      "p-2 px-3 shadow-sm relative w-full",
                      isMe
                        ? "bg-[#D9FDD3] dark:bg-[#005c4b] rounded-lg rounded-tr-none text-slate-800 dark:text-[#e9edef]"
                        : isTerminal
                          ? "bg-slate-800 dark:bg-[#202c33] text-white rounded-lg shadow-md border border-slate-700 dark:border-[#2f3b43] font-mono text-sm leading-tight"
                          : "bg-white dark:bg-[#202c33] rounded-lg rounded-tl-none text-slate-800 dark:text-[#e9edef]",
                    )}
                  >
                    {showName && (
                      <div
                        className={cn(
                          "mb-1",
                          isTerminal
                            ? "border-b border-slate-200 dark:border-[#2f3b43]"
                            : "",
                        )}
                      >
                        <span
                          className={cn(
                            "font-medium",
                            isTerminal
                              ? "text-[10px] font-mono text-slate-500 dark:text-[#8696a0]"
                              : "text-[13px] text-emerald-600 dark:text-[#53bdeb]",
                          )}
                        >
                          {isTerminal
                            ? "API EVENT :: OUTBOUND"
                            : `~ ${msg.senderName}`}
                        </span>
                      </div>
                    )}

                    {msg.replyTo && (
                      <div
                        className="mb-2 w-full max-w-sm rounded-md bg-black/5 dark:bg-white/5 border-l-4 border-[#00a884] p-2 flex flex-col cursor-pointer"
                        onClick={() => {
                          // Attempt to scroll to the original message
                          const element = document.getElementById(
                            `msg-${msg.replyTo!.id}`,
                          );
                          if (element) {
                            element.scrollIntoView({
                              behavior: "smooth",
                              block: "center",
                            });
                            element.classList.add(
                              "bg-emerald-100",
                              "dark:bg-emerald-900/40",
                            );
                            setTimeout(() => {
                              element.classList.remove(
                                "bg-emerald-100",
                                "dark:bg-emerald-900/40",
                              );
                            }, 1500);
                          }
                        }}
                      >
                        <span className="font-semibold text-xs text-[#00a884] mb-0.5">
                          {msg.replyTo.senderId === currentUser.id
                            ? "You"
                            : msg.replyTo.senderName}
                        </span>
                        <span className="text-xs text-slate-600 dark:text-slate-300 truncate">
                          {msg.replyTo.text}
                        </span>
                      </div>
                    )}

                    {msg.location && (
                      <div
                        className="mb-2 mt-1 bg-slate-200 dark:bg-slate-700 w-full sm:w-[260px] h-32 rounded-md flex flex-col items-center justify-center text-slate-500 cursor-pointer overflow-hidden relative shadow-sm border border-slate-300 dark:border-slate-600"
                        onClick={() => {
                          if (msg.location!.manualText && msg.location!.manualText.startsWith("http")) {
                            window.open(msg.location!.manualText);
                          } else if (msg.location!.manualText) {
                            window.open(`https://maps.google.com/?q=${encodeURIComponent(msg.location!.manualText)}`);
                          } else {
                            window.open(`https://maps.google.com/?q=${msg.location!.lat},${msg.location!.lng}`);
                          }
                        }}
                      >
                        <MapPin className="w-8 h-8 text-red-500 mb-2 drop-shadow" />
                        <span className="text-xs font-semibold text-slate-700 dark:text-slate-200 flex-none max-w-[90%] truncate px-2">
                          {msg.location.isLive
                            ? "Live Location"
                            : msg.location.manualText ? msg.location.manualText : "Location Shared"}
                        </span>
                        {msg.location.isLive &&
                        msg.location.expiresAt &&
                        msg.location.expiresAt > Date.now() ? (
                          <div className="flex items-center space-x-1 mt-1 text-[10px] text-emerald-600 dark:text-emerald-400 font-bold bg-emerald-100 dark:bg-emerald-900/30 px-2 py-0.5 rounded-full flex-none">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                            <span>Live updating...</span>
                          </div>
                        ) : msg.location.isLive ? (
                          <span className="text-[10px] mt-1 text-slate-400">
                            Live ended
                          </span>
                        ) : null}
                      </div>
                    )}

                    {msg.attachmentUrl && (
                      <div className="mb-2 mt-1">
                        {msg.attachmentType === "image" ? (
                          <div className="relative group rounded-md overflow-hidden bg-black/5 dark:bg-white/5">
                            <img
                              src={resolveCloudChatUrl(msg.attachmentUrl)}
                              alt={msg.attachmentName || "attachment"}
                              className="rounded-md max-h-64 sm:max-h-80 object-contain w-auto hover:opacity-90 transition-opacity"
                            />
                            <a
                              href={resolveCloudChatUrl(msg.attachmentUrl)}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="absolute top-2 right-2 bg-black/50 text-white p-1.5 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                              title="Download/Open"
                            >
                              <Download className="w-4 h-4" />
                            </a>
                          </div>
                        ) : msg.attachmentType === "audio" ? (
                          <div className="bg-black/5 dark:bg-white/5 p-2 rounded-lg">
                            <audio
                              controls
                              src={resolveCloudChatUrl(msg.attachmentUrl)}
                              className="max-w-[200px] h-10 outline-none"
                            />
                            {msg.attachmentSize && (
                              <p className="text-[10px] text-slate-500 mt-1 pl-1">{formatBytes(msg.attachmentSize)}</p>
                            )}
                          </div>
                        ) : (
                          <div className="flex flex-col space-y-2 bg-black/5 dark:bg-white/5 p-3 rounded-xl max-w-full sm:max-w-sm">
                            <div className="flex items-center space-x-3">
                              <div className="bg-white dark:bg-slate-800 p-2 rounded-lg shrink-0 text-indigo-500 dark:text-indigo-400">
                                {msg.attachmentName?.toLowerCase().endsWith(".pdf") ? (
                                  <span className="font-bold text-xs uppercase tracking-wider text-red-500">PDF</span>
                                ) : msg.attachmentName?.toLowerCase().endsWith(".mp4") || msg.attachmentName?.toLowerCase().endsWith(".mov") ? (
                                  <span className="font-bold text-xs uppercase tracking-wider text-blue-500">Video</span>
                                ) : (
                                  <Paperclip className="w-6 h-6" />
                                )}
                              </div>
                              <div className="flex-1 min-w-0">
                                <p className="text-sm font-semibold truncate dark:text-[#e9edef] text-slate-800 mb-0.5">
                                  {msg.attachmentName || "Document"}
                                </p>
                                <p className="text-[11px] font-medium text-slate-500 dark:text-[#8696a0] uppercase flex items-center space-x-1 border border-slate-300 dark:border-slate-700 w-fit px-1.5 rounded bg-black/5 dark:bg-white/5">
                                  <span>{msg.attachmentName?.split('.').pop() || "FILE"}</span>
                                  {msg.attachmentSize ? (
                                    <>
                                      <span className="mx-1">•</span>
                                      <span>{formatBytes(msg.attachmentSize)}</span>
                                    </>
                                  ) : null}
                                </p>
                              </div>
                            </div>
                            <a
                              href={resolveCloudChatUrl(msg.attachmentUrl)}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="flex items-center justify-center space-x-2 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 p-2 rounded-lg font-bold text-xs hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors"
                            >
                              <Download className="w-4 h-4" />
                              <span>Download / Open</span>
                            </a>
                          </div>
                        )}
                      </div>
                    )}

                    <div
                      className={cn(
                        "text-[14px] leading-relaxed break-words pr-16 pb-1 inline-block",
                        msg.isDeleted &&
                          "italic text-slate-500 dark:text-slate-400 font-light",
                      )}
                    >
                      {searchQuery && !msg.isDeleted && msg.text ? (
                        <span
                          dangerouslySetInnerHTML={{
                            __html: msg.text.replace(
                              new RegExp(
                                `(${searchQuery.replace(
                                  /[.*+?^${}()|[\\]\\\\]/g,
                                  "\\\\$&",
                                )})`,
                                "gi",
                              ),
                              '<mark class="bg-yellow-200 dark:bg-yellow-800/60 rounded px-0.5 text-inherit">$1</mark>',
                            ),
                          }}
                        />
                      ) : (
                        msg.text
                      )}
                    </div>

                    {/* Message Options Chevron (Shows on hover) */}
                    {hoveredMsgId === msg.id && !isTerminal && (
                      <button
                        onClick={() =>
                          setOptionsMsgId(
                            optionsMsgId === msg.id ? null : msg.id,
                          )
                        }
                        className="absolute top-1 right-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 focus:outline-none transition-colors"
                      >
                        <ChevronDown className="w-5 h-5" />
                      </button>
                    )}

                    {/* Options Dropdown */}
                    {optionsMsgId === msg.id && (
                      <div className="absolute top-6 right-2 bg-white dark:bg-[#202c33] shadow-lg rounded-md border border-slate-100 dark:border-[#2f3b43] py-1 w-40 z-30 flex flex-col text-sm text-slate-700 dark:text-[#e9edef]">
                        {!msg.isDeleted && (
                          <>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                setReplyingToMessage(msg);
                                setOptionsMsgId(null);
                              }}
                              className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center"
                            >
                              Reply
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleStar(msg.id);
                              }}
                              className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center"
                            >
                              {msg.starredBy?.includes(currentUser.id)
                                ? "Unstar"
                                : "Star"}
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handlePin(msg.id, 1);
                              }}
                              className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center"
                            >
                              Pin for 24 hours
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handlePin(msg.id, 7);
                              }}
                              className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center"
                            >
                              Pin for 7 days
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handlePin(msg.id, 30);
                              }}
                              className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center"
                            >
                              Pin for 30 days
                            </button>
                          </>
                        )}
                        {msg.pinnedUntil && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handlePin(msg.id, 0);
                            }}
                            className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center text-red-500"
                          >
                            Unpin
                          </button>
                        )}
                        {!msg.isDeleted && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setForwardingMessage(msg);
                              setOptionsMsgId(null);
                            }}
                            className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center"
                          >
                            Forward
                          </button>
                        )}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDelete(msg.id, "for_me");
                          }}
                          className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center text-red-500"
                        >
                          Delete for me
                        </button>
                        {isMe && !msg.isDeleted && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleDelete(msg.id, "for_everyone");
                            }}
                            className="px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-[#111b21] flex justify-between items-center text-red-500 font-medium"
                          >
                            Delete for everyone
                          </button>
                        )}
                      </div>
                    )}

                    <div
                      className={cn(
                        "absolute bottom-1 right-2 flex justify-end items-center space-x-1",
                        isMe
                          ? "text-slate-500 dark:text-[#8696a0]"
                          : isTerminal
                            ? "text-slate-400"
                            : "text-slate-400 dark:text-[#8696a0]",
                      )}
                    >
                      {msg.starredBy?.includes(currentUser.id) && (
                        <Star className="w-3 h-3 fill-current text-slate-400 dark:text-[#8696a0]" />
                      )}
                      <p className="text-[10px] font-medium">
                        {format(new Date(msg.timestamp), "HH:mm")}
                      </p>
                      {isMe && (
                        <span
                          className={cn(
                            "ml-0.5",
                            msg.status === "read"
                              ? "text-[#53bdeb]"
                              : "text-slate-400 dark:text-[#8696a0]",
                          )}
                        >
                          {msg.status === "read" ||
                          msg.status === "delivered" ? (
                            <CheckCheck className="w-[15px] h-[15px]" />
                          ) : (
                            <Check className="w-[15px] h-[15px]" />
                          )}
                        </span>
                      )}
                    </div>

                    {/* Render Reactions */}
                    {msg.reactions && msg.reactions.length > 0 && (
                      <div
                        className={cn(
                          "absolute -bottom-3 flex space-x-1",
                          isMe ? "right-1" : "left-1",
                        )}
                      >
                        <div className="bg-white dark:bg-slate-700 px-1.5 py-0.5 rounded-full shadow-sm text-[10px] border border-slate-100 dark:border-slate-600 flex items-center space-x-0.5 whitespace-nowrap z-10 text-slate-700 dark:text-slate-200">
                          {Array.from(
                            new Set(msg.reactions.map((r) => r.emoji)),
                          ).map((em) => (
                            <span key={em}>{em}</span>
                          ))}
                          <span className="ml-1 font-medium text-slate-500 dark:text-slate-400">
                            {msg.reactions.length}
                          </span>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          }))}
        <div ref={bottomRef} />
        </section>
      </div>

      {/* Input Area */}
      <footer className="hello-safe-bottom hello-panel-strong flex-none z-10 mx-3 mb-3 mt-2 flex flex-col rounded-[24px] px-3 pt-3 transition-colors duration-300">
        {/* Reply Preview Bar */}
        {replyingToMessage && (
          <div className="relative mx-1 mt-1 flex items-center justify-between rounded-[18px] border border-[var(--hello-border)] bg-black/5 px-4 py-3 dark:bg-white/5">
            <div className="flex flex-col text-sm pr-6 truncate overflow-hidden">
              <span className="font-semibold text-[var(--hello-accent)]">
                {replyingToMessage.senderId === currentUser.id
                  ? "You"
                  : replyingToMessage.senderName}
              </span>
              <span className="text-slate-500 dark:text-slate-400 truncate">
                {replyingToMessage.text || "Attachment"}
              </span>
            </div>
            <button
              onClick={() => setReplyingToMessage(null)}
              className="text-slate-400 hover:text-slate-600 transition p-2 absolute right-2 top-2"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        )}

        {pendingAttachment && (
          <div className="relative px-2 mb-3 mt-1">
            <div className="hello-input relative flex w-max items-center space-x-4 rounded-[18px] p-3 pr-8">
              <button
                type="button"
                onClick={() => setPendingAttachment(null)}
                className="absolute -right-2 -top-2 z-10 rounded-full bg-slate-800 p-1 text-white shadow-md hover:bg-slate-700 dark:bg-[#374248] dark:hover:bg-[#2f3b43]"
              >
                <X className="w-3 h-3" />
              </button>
              {pendingAttachment.type === "image" ? (
                <img
                  src={pendingAttachment.url}
                  alt="Preview"
                  className="h-16 w-16 object-cover rounded shadow-sm"
                />
              ) : (
                <div className="h-16 w-16 bg-white dark:bg-[#202c33] rounded shadow-sm flex items-center justify-center">
                  <Paperclip className="w-6 h-6 text-slate-400 dark:text-[#8696a0]" />
                </div>
              )}
              <div className="flex flex-col justify-center max-w-[150px] md:max-w-[200px]">
                <span className="text-sm font-semibold truncate text-slate-700 dark:text-[#e9edef]">
                  {pendingAttachment.name}
                </span>
                <span className="text-xs text-slate-500 dark:text-[#8696a0] uppercase">
                  {pendingAttachment.type}
                </span>
              </div>
            </div>
          </div>
        )}

        <form
          onSubmit={handleSend}
          className="relative flex w-full items-end gap-3 pb-3"
        >
          <div className="relative shrink-0">
            <button
              type="button"
              onClick={() => setAttachmentMenuOpen((prev) => !prev)}
              className="hello-pill flex h-12 w-12 items-center justify-center transition hover:text-[var(--hello-text)]"
              title="Attachment menu"
            >
              <Paperclip className="h-5 w-5" />
            </button>
            {attachmentMenuOpen && (
              <div className="hello-panel-strong absolute bottom-14 left-0 z-30 grid w-72 grid-cols-2 gap-2 rounded-[24px] p-3">
                {[
                  { key: "photo", label: "Photo", icon: ImagePlus },
                  { key: "camera", label: "Camera", icon: Video },
                  { key: "file", label: "File", icon: FileText },
                  { key: "contact", label: "Contact", icon: Contact },
                  { key: "location", label: "Location", icon: MapPin },
                  { key: "browser_tab", label: "Browser Tab", icon: MonitorSmartphone },
                  { key: "screenshot", label: "Screenshot", icon: ImagePlus },
                  { key: "automation_result", label: "Automation Result", icon: Bot },
                  { key: "summarize", label: "Summarize Page", icon: Sparkles },
                ].map(({ key, label, icon: Icon }) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => handleAttachmentAction(key)}
                    className="flex min-h-[74px] flex-col items-start justify-between rounded-[18px] border border-[var(--hello-border)] bg-black/5 px-3 py-3 text-left transition hover:bg-[var(--hello-accent-soft)] dark:bg-white/5"
                  >
                    <Icon className="h-4 w-4 text-[var(--hello-accent)]" />
                    <span className="text-xs font-semibold text-[var(--hello-text)]">{label}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileSelect}
            className="hidden"
          />

          <div className="hello-input flex-1 rounded-[20px] px-4 py-3 relative">
            <div className="flex items-center gap-3">
              <button
                type="button"
                className="shrink-0 text-[var(--hello-text-muted)] transition hover:text-[var(--hello-text)]"
                title="Emoji picker"
                onClick={() =>
                  pushToast({
                    title: "Emoji picker",
                    description: "UI placeholder ready for the next pass.",
                    tone: "loading",
                  })
                }
              >
                <Smile className="h-5 w-5" />
              </button>
            {isRecording ? (
              <div className="flex flex-1 items-center justify-between text-[15px] animate-pulse">
                <div className="flex items-center gap-2 text-red-500">
                  <span className="h-2.5 w-2.5 rounded-full bg-red-500"></span>
                  <span className="font-mono">
                    {formatDuration(recordingDuration)}
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm text-[var(--hello-text-muted)]">Release to send</span>
                  <button
                    type="button"
                    onClick={cancelRecording}
                    className="rounded-full bg-red-500/12 px-3 py-1 text-xs font-semibold text-red-500 transition hover:bg-red-500/18"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <input
                type="text"
                placeholder="Write a message, drop a file, or share from GlassBox"
                className="flex-1 bg-transparent text-[15px] text-[var(--hello-text)] placeholder:text-[var(--hello-text-muted)]"
                value={text}
                onChange={handleTextChange}
                onKeyDown={handleKeyDown}
              />
            )}
            </div>
            {!isRecording && (
              <div className="mt-2 flex items-center justify-between text-[11px] text-[var(--hello-text-muted)]">
                <span>{enterIsSend ? "Enter sends" : "Enter keeps drafting"}</span>
                <span>{pendingAttachment ? "Attachment ready" : "Hold mic for voice note"}</span>
              </div>
            )}
          </div>

          <button
            type="button"
            onClick={(e) => {
              if (text.trim() || pendingAttachment)
                handleSend(e as unknown as FormEvent);
            }}
            onMouseDown={
              !text.trim() && !pendingAttachment ? startRecording : undefined
            }
            onMouseUp={
              !text.trim() && !pendingAttachment ? stopRecording : undefined
            }
            onMouseLeave={
              !text.trim() && !pendingAttachment && isRecording
                ? stopRecording
                : undefined
            }
            onTouchStart={
              !text.trim() && !pendingAttachment ? startRecording : undefined
            }
            onTouchEnd={
              !text.trim() && !pendingAttachment ? stopRecording : undefined
            }
            className={cn(
              "shrink-0 flex h-12 w-12 items-center justify-center rounded-full transition-colors select-none",
              isRecording
                ? "scale-110 bg-red-100 text-red-500 dark:bg-red-900/30"
                : "bg-[var(--hello-accent)] text-white shadow-[0_12px_26px_rgba(15,143,120,0.28)] hover:bg-[var(--hello-accent-strong)]",
            )}
          >
            {text.trim() || pendingAttachment ? (
              <Send className="w-6 h-6" />
            ) : (
              <Mic className="w-6 h-6" />
            )}
          </button>
        </form>
      </footer>
    </div>
  );
}
