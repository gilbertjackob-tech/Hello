import React, { useEffect, useState, FormEvent, useCallback, useRef } from "react";
import { format } from "date-fns";
import {
  MessageSquarePlus,
  MoreVertical,
  Search,
  File,
  Bell,
  MessageCircleMore,
  Paintbrush2,
  User as UserIcon,
  UserCircle2,
  ArrowLeft,
  Camera,
  Users,
  UserPlus,
  X,
  Phone,
  CircleDashed,
  Star,
  Pin,
  ChevronDown,
  RefreshCw,
  Video as VideoIcon,
  WifiOff,
} from "lucide-react";
import { useSocket } from "../SocketContext";
import { CallHistoryItem, Chat, User, Contact } from "../types";
import {
  fetchChats,
  createChat,
  fetchStarredMessages,
  fetchUsers,
  createDirectChat,
  upsertCloudChatUser,
  uploadCloudUserAvatar,
  fetchCloudContacts,
  addCloudContact,
  fetchCloudCallHistory,
  logoutCloudUser,
  CLOUD_SESSION_TOKEN_KEY,
} from "../api";
import { cn, formatLastActive } from "../lib/utils";
import { useTheme } from "../ThemeContext";
import { useNotifications } from "../NotificationContext";
import { describeMediaAccessError, testCameraMicrophoneAccess } from "../mediaPermissions";
import { useToast } from "../ToastContext";
import { EmptyState, FilterChip, SkeletonBlock } from "./HelloUi";

interface SidebarProps {
  activeChatId?: string;
  restoreActiveChatId?: string;
  onSelectChat: (chat: Chat | null) => void;
  currentUser: User;
  onUpdateUser: (user: User | null) => void;
  activeRailTab: string;
  setActiveRailTab: (tab: string) => void;
}

import { StatusPane } from "./StatusPane";

function directKeyForChat(chat: Chat, currentUserId: string): string | null {
  if (chat.isGroup) return null;
  if (chat.directKey) return chat.directKey;
  const memberIds = chat.members?.length
    ? chat.members
    : chat.participants?.map((participant) => participant.id) || [];
  const uniqueIds = Array.from(new Set(memberIds.filter(Boolean))).sort();
  if (uniqueIds.length === 2) return uniqueIds.join(":");
  const otherId = chat.participants?.find((participant) => participant.id !== currentUserId)?.id;
  return otherId ? [currentUserId, otherId].sort().join(":") : null;
}

function mergeChats(chats: Chat[], currentUserId: string): Chat[] {
  const merged = new Map<string, Chat>();
  for (const chat of chats) {
    const key = directKeyForChat(chat, currentUserId) || chat.id;
    const existing = merged.get(key);
    if (!existing || (chat.lastMessageTime || 0) >= (existing.lastMessageTime || 0)) {
      merged.set(key, {
        ...existing,
        ...chat,
        unreadCount: chat.unreadCount ?? existing?.unreadCount ?? 0,
      });
    }
  }
  return Array.from(merged.values()).sort((a, b) => (b.lastMessageTime || 0) - (a.lastMessageTime || 0));
}

const NOTIFICATION_CATEGORY_PREFS = [
  { key: "calls", label: "Calls" },
  { key: "missed_calls", label: "Missed calls" },
  { key: "messages", label: "Messages" },
  { key: "mentions", label: "Mentions" },
  { key: "status_posts", label: "New moments" },
  { key: "status_activity", label: "Reactions/views" },
  { key: "system", label: "System" },
  { key: "re_engagement", label: "Nudges" },
];

export function Sidebar({
  activeChatId,
  restoreActiveChatId,
  onSelectChat,
  currentUser,
  onUpdateUser,
  activeRailTab,
  setActiveRailTab,
}: SidebarProps) {
  const { pushToast } = useToast();
  const [chats, setChats] = useState<Chat[]>([]);
  const materializedContactsRef = useRef<string>("");
  const [chatListLoading, setChatListLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [chatFilter, setChatFilter] = useState<"all" | "unread" | "groups" | "calls" | "files" | "pinned">("all");
  const showProfile = activeRailTab === "profile";
  const showSettings = activeRailTab === "settings";
  const showDrive = activeRailTab === "drive";
  const showCalls = activeRailTab === "calls";
  const showStatus = activeRailTab === "status";
  const showCommunities = activeRailTab === "communities";
  const showStarred = activeRailTab === "starred";

  // Options popup state should remain
  const [showOptionsPopup, setShowOptionsPopup] = useState(false);

  // Starred messages
  const [starredMessages, setStarredMessages] = useState<any[]>([]);

  useEffect(() => {
    if (showStarred) {
      fetchStarredMessages(currentUser.id)
        .then(setStarredMessages)
        .catch(console.error);
    }
  }, [showStarred, currentUser.id]);

  // Contacts and forms
  const [showContacts, setShowContacts] = useState(false);
  const [showNewGroup, setShowNewGroup] = useState(false);
  const [showAddContact, setShowAddContact] = useState(false);
  const [newContactName, setNewContactName] = useState("");
  const [newContactPhone, setNewContactPhone] = useState("");
  const [usersToChat, setUsersToChat] = useState<User[]>([]);
  const [userSearchQuery, setUserSearchQuery] = useState("");
  const [avatarCropSource, setAvatarCropSource] = useState("");
  const [avatarCropFileName, setAvatarCropFileName] = useState("avatar.jpg");
  const [avatarCropZoom, setAvatarCropZoom] = useState(1.15);
  const [avatarCropX, setAvatarCropX] = useState(50);
  const [avatarCropY, setAvatarCropY] = useState(50);
  const [isSavingAvatar, setIsSavingAvatar] = useState(false);
  const [permissionTestStatus, setPermissionTestStatus] = useState("");
  const [permissionTestBusy, setPermissionTestBusy] = useState(false);
  const [chatTypingPreview, setChatTypingPreview] = useState<Record<string, string>>({});
  const [pinnedChatIds, setPinnedChatIds] = useState<string[]>(() => {
    const saved = localStorage.getItem(`whatsclone_pinned_chats_${currentUser.id}`);
    return saved ? JSON.parse(saved) : [];
  });

  const {
    theme,
    setTheme,
    enterIsSend,
    setEnterIsSend,
    chatWallpaper,
    setChatWallpaper,
    chatWallpaperOpacity,
    setChatWallpaperOpacity
  } = useTheme();

  const { notificationsEnabled, toggleNotifications } = useNotifications();
  const [notificationCategories, setNotificationCategories] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(
      NOTIFICATION_CATEGORY_PREFS.map((item) => [
        item.key,
        localStorage.getItem(`hello_notifications_${item.key}`) !== "false",
      ]),
    ),
  );

  const toggleNotificationCategory = (key: string) => {
    setNotificationCategories((prev) => {
      const nextValue = !(prev[key] ?? true);
      localStorage.setItem(`hello_notifications_${key}`, String(nextValue));
      return { ...prev, [key]: nextValue };
    });
  };

  const [privacy, setPrivacy] = useState<"none" | "contacts" | "everyone">(
    "everyone",
  );

  useEffect(() => {
    import("../api").then((api) => {
      api
        .fetchUserPresence(currentUser.id)
        .then((data) => {
          setPrivacy(data.privacy);
        })
        .catch(console.error);
    });
  }, [currentUser.id]);

  const handlePrivacyChange = (
    newPrivacy: "none" | "contacts" | "everyone",
  ) => {
    setPrivacy(newPrivacy);
    import("../api").then((api) => {
      api.updateUserPrivacy(currentUser.id, newPrivacy).catch(console.error);
    });
  };

  const [contacts, setContacts] = useState<Contact[]>(() => {
    const saved = localStorage.getItem("whatsclone_contacts");
    if (saved) return JSON.parse(saved);
    return [
      { id: "c1", name: "Alice Smith", phone: "+1 555-0100", isBlocked: false },
      { id: "c2", name: "Bob Jones", phone: "+1 555-0200", isBlocked: false },
    ];
  });
  const [cloudContactsLoaded, setCloudContactsLoaded] = useState(false);

  const { socket, isConnected } = useSocket();

  const sortUsersForDiscovery = useCallback(
    (users: User[]) =>
      users
        .filter((u) => u.id !== currentUser.id)
        .sort((a, b) => {
          if (a.online !== b.online) return a.online ? -1 : 1;
          return a.name.localeCompare(b.name);
        }),
    [currentUser.id]
  );

  const refreshUserDiscovery = useCallback(
    async (query = userSearchQuery) => {
      try {
        const users = await fetchUsers(query);
        setUsersToChat(sortUsersForDiscovery(users));
      } catch (err) {
        console.error("Failed to fetch users", err);
      }
    },
    [userSearchQuery, sortUsersForDiscovery]
  );

  useEffect(() => {
    localStorage.setItem("whatsclone_contacts", JSON.stringify(contacts));
  }, [contacts]);

  useEffect(() => {
    let cancelled = false;
    setCloudContactsLoaded(false);
    fetchCloudContacts()
      .then((cloudContacts) => {
        if (cancelled) return;
        setContacts(cloudContacts.map((user) => ({
          id: user.id,
          name: user.name,
          avatar: user.avatar,
          phone: user.phone,
          isBlocked: false,
        })));
        setCloudContactsLoaded(true);
      })
      .catch((err) => {
        console.warn("Using cached local contacts", err);
        if (!cancelled) setCloudContactsLoaded(false);
      });
    return () => {
      cancelled = true;
    };
  }, [currentUser.id]);

  useEffect(() => {
    if (!cloudContactsLoaded) return;
    if (chatListLoading || contacts.length === 0) return;
    if (!localStorage.getItem(CLOUD_SESSION_TOKEN_KEY)) return;

    const existingDirectKeys = new Set(
      chats
        .map((chat) => directKeyForChat(chat, currentUser.id))
        .filter((key): key is string => Boolean(key)),
    );
    const missingContacts = contacts
      .filter((contact) => contact.id && contact.id !== currentUser.id && !contact.isBlocked)
      .filter((contact) => !existingDirectKeys.has([currentUser.id, contact.id].sort().join(":")))
      .slice(0, 40);

    if (missingContacts.length === 0) return;
    const signature = `${currentUser.id}:${missingContacts.map((contact) => contact.id).sort().join(",")}`;
    if (materializedContactsRef.current === signature) return;
    materializedContactsRef.current = signature;

    let cancelled = false;
    (async () => {
      const materialized: Chat[] = [];
      for (const contact of missingContacts) {
        try {
          const chat = await createDirectChat(currentUser.id, contact.id, {
            currentUserName: currentUser.name,
            targetUserName: contact.name,
          });
          materialized.push(chat);
        } catch (err) {
          console.warn("Failed to materialize contact chat", contact.id, err);
        }
      }
      if (!cancelled && materialized.length > 0) {
        setChats((prev) => mergeChats([...materialized, ...prev], currentUser.id));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [chatListLoading, chats, cloudContactsLoaded, contacts, currentUser.id, currentUser.name]);

  useEffect(() => {
    localStorage.setItem(
      `whatsclone_pinned_chats_${currentUser.id}`,
      JSON.stringify(pinnedChatIds),
    );
  }, [currentUser.id, pinnedChatIds]);

  useEffect(() => {
    let cancelled = false;
    setChatListLoading(true);

    fetchChats(currentUser.id)
      .then((loadedChats) => {
        if (cancelled) return;
        setChats(mergeChats(loadedChats, currentUser.id));

        if (!activeChatId && restoreActiveChatId) {
          const restoredChat = loadedChats.find((chat) => (
            chat.id === restoreActiveChatId &&
            !chat.deletedFor?.includes(currentUser.id)
          ));
          if (restoredChat) {
            onSelectChat(restoredChat);
          }
        }
      })
      .catch(console.error)
      .finally(() => {
        if (!cancelled) {
          setChatListLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [activeChatId, currentUser.id, onSelectChat, restoreActiveChatId]);

  useEffect(() => {
    if (!socket) return;

    socket.on("chat_updated", (updatedChat: Chat) => {
      setChats((prev) => {
        const idx = prev.findIndex((c) => c.id === updatedChat.id);
        let showNote = false;
        if (idx !== -1) {
          const oldTime = prev[idx].lastMessageTime || 0;
          if (
            updatedChat.lastMessageTime &&
            updatedChat.lastMessageTime > oldTime
          ) {
            showNote = true;
          }
          const newChats = [...prev];
          newChats[idx] = updatedChat;
          if (showNote && activeChatId !== updatedChat.id) {
            if (
              "Notification" in window &&
              Notification.permission === "granted"
            ) {
              new window.Notification(updatedChat.name, {
                body: updatedChat.lastMessage,
                icon: updatedChat.avatar,
              });
            }
          }
          return mergeChats(newChats, currentUser.id);
        }
        return mergeChats([updatedChat, ...prev], currentUser.id);
      });
    });

    socket.on("new_chat", (newChat: Chat) => {
      setChats((prev) => mergeChats([newChat, ...prev], currentUser.id));
    });

    socket.on("user_typing", (data: { chatId: string; senderName: string; isTyping?: boolean }) => {
      setChatTypingPreview((prev) => {
        if (data.isTyping === false) {
          const next = { ...prev };
          delete next[data.chatId];
          return next;
        }
        return { ...prev, [data.chatId]: `${data.senderName} is typing...` };
      });

      if (data.isTyping !== false) {
        window.setTimeout(() => {
          setChatTypingPreview((prev) => {
            if (prev[data.chatId] !== `${data.senderName} is typing...`) {
              return prev;
            }
            const next = { ...prev };
            delete next[data.chatId];
            return next;
          });
        }, 2200);
      }
    });

    const handleUserUpdate = (data?: Partial<User> & { userId?: string; lastActive?: number }) => {
      const updatedUserId = data?.id || data?.userId;
      if (updatedUserId) {
        const patchUser = (user: User): User =>
          user.id === updatedUserId
            ? { ...user, ...data, id: user.id, online: data.online ?? user.online, lastActive: data.lastActive ?? user.lastActive }
            : user;
        setUsersToChat((prev) => sortUsersForDiscovery(prev.map(patchUser)));
        setChats((prev) =>
          prev.map((chat) => ({
            ...chat,
            participants: chat.participants?.map(patchUser),
          })),
        );
      }
    };

    socket.on("user_presence", handleUserUpdate);
    socket.on("user_updated", handleUserUpdate);
    socket.on("presence_updated", handleUserUpdate);

    return () => {
      socket.off("chat_updated");
      socket.off("new_chat");
      socket.off("user_typing");
      socket.off("user_presence", handleUserUpdate);
      socket.off("user_updated", handleUserUpdate);
      socket.off("presence_updated", handleUserUpdate);
    };
  }, [socket, currentUser.id, activeChatId, sortUsersForDiscovery]);

  useEffect(() => {
    if (!activeChatId) return;
    setChats((prev) =>
      mergeChats(
        prev.map((chat) =>
          chat.id === activeChatId ? { ...chat, unreadCount: 0 } : chat,
        ),
        currentUser.id,
      ),
    );
  }, [activeChatId, currentUser.id]);

  const handleNewChat = () => {
    setShowContacts(true);
    setShowAddContact(true);
    setShowOptionsPopup(false);
    setUserSearchQuery("");
    refreshUserDiscovery("");
  };

  const handleCreateContactChat = async (contact: Contact) => {
    const targetDirectKey = [currentUser.id, contact.id].sort().join(":");
    const existing = chats.find((chat) => directKeyForChat(chat, currentUser.id) === targetDirectKey);
    if (existing) {
      onSelectChat(existing);
      setShowContacts(false);
      setActiveRailTab("chats");
      return;
    }
    const newChat = await createDirectChat(currentUser.id, contact.id, {
      currentUserName: currentUser.name,
      targetUserName: contact.name,
    });
    setChats((prev) => mergeChats([newChat, ...prev], currentUser.id));
    onSelectChat(newChat);
    setShowContacts(false);
    setActiveRailTab("chats");
  };

  const handleCreateGroup = async () => {
    const name = window.prompt("Enter new group name:");
    if (name) {
      const selectedMembers = contacts.slice(0, 2).map((c) => c.name);
      selectedMembers.push("You");
      const newChat = await createChat(name, true, selectedMembers);
      onSelectChat(newChat);
      setShowNewGroup(false);
      setActiveRailTab("chats");
    }
  };

  useEffect(() => {
    if (showAddContact) {
      refreshUserDiscovery(userSearchQuery);
    }
  }, [showAddContact, userSearchQuery, refreshUserDiscovery]);

  const handleStartDirectChat = async (targetUserId: string) => {
    const targetUser = usersToChat.find((user) => user.id === targetUserId);
    const targetDirectKey = [currentUser.id, targetUserId].sort().join(":");
    const existingChat = chats.find((chat) => directKeyForChat(chat, currentUser.id) === targetDirectKey);

    try {
      const newChat = existingChat || await createDirectChat(currentUser.id, targetUserId, {
        currentUserName: currentUser.name,
        targetUserName: targetUser?.name,
      });
      const participants = newChat.participants?.length
        ? newChat.participants
        : [
            currentUser,
            targetUser || {
              id: targetUserId,
              name: newChat.name || "Unknown user",
              avatar: newChat.avatar || "",
              online: false,
            },
          ];
      const normalizedChat: Chat = {
        ...newChat,
        directKey: newChat.directKey || targetDirectKey,
        name: newChat.name || targetUser?.name || "Direct chat",
        avatar: newChat.avatar || targetUser?.avatar,
        isGroup: false,
        members: newChat.members?.length ? newChat.members : [currentUser.id, targetUserId],
        participants,
        lastMessage: newChat.lastMessage || "",
        lastMessageTime: newChat.lastMessageTime || Date.now(),
        unreadCount: newChat.unreadCount || 0,
      };
      setChats((prev) => {
        return mergeChats([normalizedChat, ...prev], currentUser.id);
      });
      onSelectChat(normalizedChat);
      setShowAddContact(false);
      setShowContacts(false);
      setActiveRailTab("chats");
    } catch (err) {
      console.error(err);
      pushToast({
        title: "Could not open chat",
        description: "The contact is still visible. Try refresh users, then open the chat again.",
        tone: "error",
      });
    }
  };

  const handleCreateContact = async (e: FormEvent) => {
    e.preventDefault();
    if (newContactName.trim() && newContactPhone.trim()) {
      const localContact = {
        id: "c" + Date.now(),
        name: newContactName,
        phone: newContactPhone,
        isBlocked: false,
      };
      try {
        const cloudContact = await addCloudContact({ name: newContactName });
        setContacts([
          ...contacts,
          {
            id: cloudContact.id,
            name: cloudContact.name,
            avatar: cloudContact.avatar,
            phone: cloudContact.phone || newContactPhone,
            isBlocked: false,
          },
        ]);
      } catch (err) {
        console.warn("Contact saved locally until cloud user exists", err);
        setContacts([...contacts, localContact]);
      }
      setShowAddContact(false);
      setNewContactName("");
      setNewContactPhone("");
    }
  };

  const deleteContact = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm("Delete this contact?")) {
      setContacts(contacts.filter((c) => c.id !== id));
    }
  };

  const toggleBlockContact = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setContacts(
      contacts.map((c) =>
        c.id === id ? { ...c, isBlocked: !c.isBlocked } : c,
      ),
    );
  };

  const visibleChats = mergeChats(chats, currentUser.id).filter((chat) => !chat.deletedFor?.includes(currentUser.id));
  const chatMatchesSearch = (chat: Chat) => {
    const needle = search.toLowerCase();
    if (!needle) return true;
    const names = [
      chat.name,
      ...(chat.participants?.map((participant) => participant.name) || []),
      chat.lastMessage || "",
    ]
      .join(" ")
      .toLowerCase();
    return names.includes(needle);
  };
  const chatHasFileHint = (chat: Chat) =>
    /image|photo|audio|voice|file|document|pdf|video|attachment/i.test(chat.lastMessage || "");
  const chatHasCallHint = (chat: Chat) =>
    /call|missed|ringing|video|audio/i.test(chat.lastMessage || "");
  const filteredChats = visibleChats
    .filter(chatMatchesSearch)
    .filter((chat) => {
      if (chatFilter === "unread") return Boolean(chat.unreadCount);
      if (chatFilter === "groups") return Boolean(chat.isGroup);
      if (chatFilter === "calls") return chatHasCallHint(chat);
      if (chatFilter === "files") return chatHasFileHint(chat);
      if (chatFilter === "pinned") return pinnedChatIds.includes(chat.id);
      return true;
    })
    .sort((a, b) => {
      const aPinned = pinnedChatIds.includes(a.id) ? 1 : 0;
      const bPinned = pinnedChatIds.includes(b.id) ? 1 : 0;
      if (aPinned !== bPinned) return bPinned - aPinned;
      return (b.lastMessageTime || 0) - (a.lastMessageTime || 0);
    });
  const filterCounts = {
    all: visibleChats.length,
    unread: visibleChats.filter((chat) => Boolean(chat.unreadCount)).length,
    groups: visibleChats.filter((chat) => Boolean(chat.isGroup)).length,
    calls: visibleChats.filter(chatHasCallHint).length,
    files: visibleChats.filter(chatHasFileHint).length,
    pinned: visibleChats.filter((chat) => pinnedChatIds.includes(chat.id)).length,
  };
  const togglePinnedChat = (chatId: string) => {
    setPinnedChatIds((prev) =>
      prev.includes(chatId) ? prev.filter((id) => id !== chatId) : [chatId, ...prev],
    );
  };

  const isContactsTab = activeRailTab === "contacts";
  const contactsVisible = showContacts || isContactsTab;

  // Calls pane state
  const [callLogs, setCallLogs] = useState<CallHistoryItem[]>([]);
  const [selectedCallLog, setSelectedCallLog] = useState<CallHistoryItem | null>(null);
  const refreshCallLogs = useCallback(async () => {
    if (typeof window === "undefined") return;
    try {
      setCallLogs(await fetchCloudCallHistory(currentUser.id));
    } catch (err) {
      console.error(err);
      setCallLogs([]);
    }
  }, [currentUser.id]);

  useEffect(() => {
    if (showCalls) {
      void refreshCallLogs();
    }
  }, [showCalls, refreshCallLogs]);

  useEffect(() => {
    if (!socket) return;
    const handleHistoryUpdated = () => {
      if (showCalls) void refreshCallLogs();
    };
    socket.on("call:history-updated", handleHistoryUpdated);
    return () => {
      socket.off("call:history-updated", handleHistoryUpdated);
    };
  }, [socket, showCalls, refreshCallLogs]);

  const formatDuration = (seconds?: number | null) => {
    if (seconds === undefined || seconds === null) return "";
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  const formatCallTimestamp = (timestamp?: number) => {
    if (!timestamp) return "";
    const date = new Date(timestamp);
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    const isSameDate = (a: Date, b: Date) =>
      a.getFullYear() === b.getFullYear() &&
      a.getMonth() === b.getMonth() &&
      a.getDate() === b.getDate();
    if (isSameDate(date, today)) return `Today ${format(date, "h:mm a")}`;
    if (isSameDate(date, yesterday)) return `Yesterday ${format(date, "h:mm a")}`;
    return format(date, "MMM d");
  };

  const formatCallStatus = (log: CallHistoryItem) => {
    if (log.status === "ended") return log.durationSeconds ? `Ended - ${formatDuration(log.durationSeconds)}` : "Ended";
    if (log.status === "missed") return "Missed";
    if (log.status === "declined") return "Declined";
    if (log.status === "cancelled") return "Cancelled";
    if (log.status === "busy") return "Busy";
    if (log.status === "unavailable") return "Unavailable";
    if (log.status === "failed") return "Failed";
    if (log.endReason === "no_answer") return "No answer";
    return log.status.replace(/_/g, " ");
  };

  const startCallFromLog = async (log: CallHistoryItem, isVideo: boolean) => {
    const otherUser = log.otherUser;
    const allChats = await fetchChats(currentUser.id);
    const matchingChat = allChats.find((chat) => chat.id === log.chatId);
    if (!matchingChat) return;
    const other = !matchingChat.isGroup
      ? matchingChat.participants?.find((p) => p.id !== currentUser.id)
      : undefined;
    onSelectChat(matchingChat);
    setTimeout(() => {
      window.dispatchEvent(
        new CustomEvent("START_CALL", {
          detail: {
            chatId: matchingChat.id,
            calleeId: other?.id || otherUser?.id,
            calleeName: other?.name || otherUser?.name || "Call",
            calleeAvatar: other?.avatar || otherUser?.avatar,
            isVideo,
          },
        }),
      );
    }, 300);
  };

  const testCameraMic = async () => {
    setPermissionTestBusy(true);
    setPermissionTestStatus("Testing camera/mic...");
    try {
      const result = await testCameraMicrophoneAccess();
      setPermissionTestStatus(result);
      pushToast({ title: "Camera and microphone look ready", description: result, tone: "success" });
    } catch (error) {
      const message = describeMediaAccessError(error);
      setPermissionTestStatus(message);
      pushToast({ title: "Permission test failed", description: message, tone: "error", durationMs: 4600 });
    } finally {
      setPermissionTestBusy(false);
    }
  };

  const openAvatarCropper = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      setAvatarCropSource(String(reader.result || ""));
      setAvatarCropFileName(file.name || "avatar.jpg");
      setAvatarCropZoom(1.15);
      setAvatarCropX(50);
      setAvatarCropY(50);
    };
    reader.onerror = () => {
      pushToast({
        title: "Could not read that photo",
        description: "Try another image file.",
        tone: "error",
      });
    };
    reader.readAsDataURL(file);
  };

  const buildCroppedAvatarFile = async (): Promise<File> => {
    if (!avatarCropSource) throw new Error("No avatar selected");
    const image = document.createElement("img");
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve();
      image.onerror = () => reject(new Error("Could not load selected image"));
      image.src = avatarCropSource;
    });

    const size = 640;
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Could not prepare avatar crop");

    const baseScale = Math.max(size / image.width, size / image.height);
    const scale = baseScale * avatarCropZoom;
    const drawWidth = image.width * scale;
    const drawHeight = image.height * scale;
    const maxOffsetX = Math.max(0, drawWidth - size);
    const maxOffsetY = Math.max(0, drawHeight - size);
    const dx = -maxOffsetX * (avatarCropX / 100);
    const dy = -maxOffsetY * (avatarCropY / 100);

    ctx.fillStyle = "#0f172a";
    ctx.fillRect(0, 0, size, size);
    ctx.drawImage(image, dx, dy, drawWidth, drawHeight);

    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (result) => (result ? resolve(result) : reject(new Error("Could not crop avatar"))),
        "image/jpeg",
        0.9,
      );
    });
    const safeName = avatarCropFileName.replace(/\.[^.]+$/, "") || "avatar";
    const finalName = `${safeName}_profile.jpg`;
    try {
      return new File([blob], finalName, { type: "image/jpeg" });
    } catch {
      const fallback = blob as Blob & { name?: string; lastModified?: number };
      fallback.name = finalName;
      fallback.lastModified = Date.now();
      return fallback as File;
    }
  };

  const saveCroppedAvatar = async () => {
    setIsSavingAvatar(true);
    try {
      const croppedFile = await buildCroppedAvatarFile();
      const updatedUser = await uploadCloudUserAvatar(currentUser.id, croppedFile);
      const newAvatarUrl = updatedUser.avatar;
      const nextUser = { ...currentUser, ...updatedUser, avatar: newAvatarUrl };
      localStorage.setItem("whatsclone_user_real", JSON.stringify(nextUser));
      onUpdateUser(nextUser);
      setAvatarCropSource("");
      pushToast({ title: "Profile photo updated", tone: "success" });
    } catch (err) {
      console.error("Failed to update avatar", err);
      pushToast({
        title: "Profile photo update failed",
        description: err instanceof Error ? err.message : "Please try again.",
        tone: "error",
      });
    } finally {
      setIsSavingAvatar(false);
    }
  };

  return (
    <div className="relative z-10 flex h-full w-full flex-col overflow-hidden bg-transparent transition-colors duration-300">
      <div className="hello-muted-panel m-3 mb-2 rounded-[24px] px-4 py-4">
        <div className="flex flex-row items-center justify-between">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-[var(--hello-text-muted)]">
              Hello Inbox
            </p>
            <h1 className="mt-1 text-[24px] font-extrabold tracking-tight text-[var(--hello-text)]">
              Chats
            </h1>
          </div>
          <div className="relative flex items-center space-x-2 text-[var(--hello-text-muted)]">
            <div
              className={cn(
                "rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.22em]",
                isConnected
                  ? "bg-emerald-500/12 text-emerald-600 dark:text-emerald-300"
                  : "bg-rose-500/12 text-rose-500",
              )}
              title={isConnected ? "WebSocket Connected" : "Disconnected"}
            >
              {isConnected ? "Live" : "Offline"}
            </div>
            <button
              onClick={handleNewChat}
              title="New Chat"
              className="hello-pill p-2 transition hover:text-[var(--hello-text)]"
            >
              <MessageSquarePlus className="h-5 w-5" />
            </button>
            <button
              onClick={() => setShowOptionsPopup(!showOptionsPopup)}
              title="More"
              className="hello-pill p-2 transition hover:text-[var(--hello-text)]"
            >
              <MoreVertical className="h-5 w-5" />
            </button>

            {showOptionsPopup && (
              <div className="hello-panel-strong absolute right-0 top-12 z-50 w-52 rounded-[20px] py-2">
                <button
                  className="w-full px-4 py-3 text-left text-sm font-medium text-[var(--hello-text)] transition hover:bg-black/5 dark:hover:bg-white/5"
                  onClick={() => {
                    setShowNewGroup(true);
                    setShowOptionsPopup(false);
                  }}
                >
                  New group
                </button>
                <button
                  className="w-full px-4 py-3 text-left text-sm font-medium text-[var(--hello-text)] transition hover:bg-black/5 dark:hover:bg-white/5"
                  onClick={() => {
                    setActiveRailTab("starred");
                    setShowOptionsPopup(false);
                  }}
                >
                  Starred messages
                </button>
                <button
                  className="w-full px-4 py-3 text-left text-sm font-medium text-[var(--hello-text)] transition hover:bg-black/5 dark:hover:bg-white/5"
                  onClick={() => {
                    setActiveRailTab("settings");
                    setShowOptionsPopup(false);
                  }}
                >
                  Settings
                </button>
                <button
                  className="w-full px-4 py-3 text-left text-sm font-medium text-[var(--hello-text)] transition hover:bg-black/5 dark:hover:bg-white/5"
                  onClick={() => setShowOptionsPopup(false)}
                >
                  Close menu
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="mt-4">
          <div
            className={cn(
              "hello-input flex items-center gap-3 rounded-[18px] px-4 py-3",
              !isConnected && "border-rose-300/40",
            )}
          >
            <Search className="h-4 w-4 text-[var(--hello-text-muted)]" />
            <input
              type="text"
              placeholder="Search people, groups, files, or messages"
              className="min-w-0 flex-1 bg-transparent text-sm"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            {!isConnected ? <WifiOff className="h-4 w-4 text-rose-500" /> : null}
          </div>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <FilterChip label="All" active={chatFilter === "all"} count={filterCounts.all} onClick={() => setChatFilter("all")} />
          <FilterChip label="Unread" active={chatFilter === "unread"} count={filterCounts.unread} onClick={() => setChatFilter("unread")} />
          <FilterChip label="Groups" active={chatFilter === "groups"} count={filterCounts.groups} onClick={() => setChatFilter("groups")} />
          <FilterChip label="Calls" active={chatFilter === "calls"} count={filterCounts.calls} onClick={() => setChatFilter("calls")} />
          <FilterChip label="Files" active={chatFilter === "files"} count={filterCounts.files} onClick={() => setChatFilter("files")} />
          <FilterChip label="Pinned" active={chatFilter === "pinned"} count={filterCounts.pinned} onClick={() => setChatFilter("pinned")} />
        </div>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto px-3 pb-3 custom-scrollbar">
        <div className="space-y-2">
        {chatListLoading ? (
          Array.from({ length: 7 }).map((_, index) => (
            <div key={index} className="hello-card flex items-center gap-3 px-4 py-3">
              <SkeletonBlock className="h-12 w-12 rounded-full" />
              <div className="min-w-0 flex-1 space-y-2">
                <SkeletonBlock className="h-4 w-32 rounded-full" />
                <SkeletonBlock className="h-3 w-48 rounded-full" />
              </div>
            </div>
          ))
        ) : filteredChats.length === 0 ? (
          <EmptyState
            icon={<MessageCircleMore className="h-8 w-8" />}
            title={
              !isConnected
                ? "You are reconnecting"
                : search
                  ? "Nothing matches that search"
                  : chatFilter === "pinned"
                    ? "No pinned chats yet"
                    : "No chats to show"
            }
            description={
              !isConnected
                ? "Hello will keep your current state and refresh the inbox when the socket reconnects."
                : search
                  ? "Try another name, file, or message keyword."
                  : chatFilter === "pinned"
                    ? "Pin important conversations so they stay anchored at the top."
                    : "Start a direct chat or make a group."
            }
            className="mt-4 min-h-[280px]"
          />
        ) : (
          filteredChats.map((chat) => {
          const isActive = activeChatId === chat.id;
          const otherParticipant = !chat.isGroup 
            ? chat.participants?.find((p) => p.id !== currentUser.id)
            : null;
            
          const chatName = otherParticipant ? otherParticipant.name : chat.name;
          const chatAvatar = otherParticipant?.avatar || chat.avatar;
          const isOnline = otherParticipant ? otherParticipant.online : false;

          let subtitle = chatTypingPreview[chat.id] || chat.lastMessage;
          if (!subtitle) {
             subtitle = isOnline 
               ? "Online" 
               : otherParticipant?.lastActive 
                 ? `Last active ${formatLastActive(otherParticipant.lastActive)}` 
                 : "Tap to get started";
          }

          const isPinned = pinnedChatIds.includes(chat.id);

          return (
            <div
              key={chat.id}
              onClick={() => onSelectChat(chat)}
              className={cn(
                "hello-card group flex cursor-pointer items-center gap-3 px-4 py-3 transition hover:translate-y-[-1px]",
                isActive
                  ? "hello-glow border-transparent bg-[var(--hello-panel-strong)]"
                  : "hover:border-[var(--hello-border-strong)]",
              )}
            >
              <div className="relative flex h-12 w-12 shrink-0 items-center justify-center overflow-visible rounded-full bg-slate-300 text-lg font-bold text-slate-800 dark:bg-[#202c33] dark:text-[#aebac1]">
                {chatAvatar ? (
                  <img
                    src={chatAvatar}
                    alt="Avatar"
                    className="w-full h-full object-cover rounded-full"
                  />
                ) : (
                  chatName.charAt(0).toUpperCase()
                )}
                {isOnline && (
                  <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-white bg-emerald-500 dark:border-[#111b21]"></span>
                )}
              </div>
              <div className="relative min-w-0 flex-1 py-1">
                <div className="flex justify-between items-baseline mb-1">
                  <h4
                    className={cn(
                      "mr-2 truncate text-[15px] font-bold",
                      isActive
                        ? "text-[var(--hello-text)]"
                        : "text-[var(--hello-text)]",
                    )}
                  >
                    {chatName}
                  </h4>
                  <span className={cn("shrink-0 text-[11px] font-semibold", chat.unreadCount ? "text-[var(--hello-accent)]" : "text-[var(--hello-text-muted)]")}>
                    {chat.lastMessageTime
                      ? format(new Date(chat.lastMessageTime), "HH:mm")
                      : ""}
                  </span>
                </div>
                <div className="flex justify-between items-baseline">
                  <p
                    className={cn(
                      "mr-4 truncate text-sm",
                      chatTypingPreview[chat.id]
                        ? "font-semibold text-[var(--hello-accent)]"
                        : "text-[var(--hello-text-muted)]",
                    )}
                  >
                    {subtitle}
                  </p>
                  <div className="ml-2 flex shrink-0 items-center gap-2">
                    {isPinned ? <Pin className="h-3.5 w-3.5 text-[var(--hello-accent)]" /> : null}
                    {chat.unreadCount ? (
                    <span className="min-w-[22px] rounded-full bg-[var(--hello-accent)] px-1.5 py-0.5 text-center text-[11px] font-bold text-white">
                      {chat.unreadCount}
                    </span>
                    ) : null}
                  </div>
                </div>
                <div className="mt-3 flex items-center gap-2 opacity-0 transition group-hover:opacity-100">
                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      togglePinnedChat(chat.id);
                    }}
                    className={cn(
                      "hello-pill inline-flex items-center gap-1.5 px-2.5 py-1 text-[11px] font-semibold transition hover:text-[var(--hello-text)]",
                      isPinned && "border-transparent bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]",
                    )}
                  >
                    <Pin className="h-3.5 w-3.5" />
                    {isPinned ? "Pinned" : "Pin"}
                  </button>
                  <button
                    type="button"
                    onClick={async (event) => {
                      event.stopPropagation();
                      try {
                        const { deleteChat } = await import("../api");
                        await deleteChat(chat.id, currentUser.id);
                        setChats((prev) =>
                          prev.map((candidate) =>
                            candidate.id === chat.id
                              ? {
                                  ...candidate,
                                  deletedFor: [
                                    ...(candidate.deletedFor || []),
                                    currentUser.id,
                                  ],
                                }
                              : candidate,
                          ),
                        );
                        if (activeChatId === chat.id) {
                          onSelectChat(null);
                        }
                        pushToast({ title: "Chat removed locally", tone: "success" });
                      } catch (error) {
                        console.error("Failed to delete chat", error);
                        pushToast({
                          title: "Could not delete that chat",
                          description: "Try again in a moment.",
                          tone: "error",
                        });
                      }
                    }}
                    className="hello-pill inline-flex items-center gap-1.5 px-2.5 py-1 text-[11px] font-semibold transition hover:text-rose-500"
                  >
                    <ChevronDown className="h-3.5 w-3.5" />
                    Remove
                  </button>
                </div>
              </div>
            </div>
          );
        })
        )}
        </div>
      </div>

      {/* Profile Pane */}
      <div
        className={cn(
          "absolute inset-0 bg-slate-50 dark:bg-[#111b21] z-20 flex flex-col transition-transform duration-300 transform",
          showProfile ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div
          className="h-28 bg-indigo-600 dark:bg-[#202c33] text-white flex items-end px-6 pb-4 cursor-pointer"
          onClick={() => setActiveRailTab("chats")}
        >
          <div className="flex items-center space-x-6 text-[#e9edef]">
            <ArrowLeft className="w-6 h-6 hover:scale-110 transition-transform" />
            <h2 className="text-[19px] font-medium">Profile</h2>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto p-0">
          <div className="flex justify-center py-8 relative">
            <label className="w-40 h-40 rounded-full bg-slate-200 dark:bg-slate-800 flex items-center justify-center text-slate-400 cursor-pointer overflow-hidden border-2 border-transparent hover:border-indigo-400 transition-colors group relative">
              {currentUser.avatar ? (
                <img
                  src={currentUser.avatar}
                  alt="Avatar"
                  className="w-full h-full object-cover"
                />
              ) : (
                <UserCircle2 className="w-32 h-32" />
              )}
              <div className="absolute inset-0 bg-black/40 hidden group-hover:flex flex-col items-center justify-center text-white text-xs">
                <Camera className="w-8 h-8 mb-1" />
                CHANGE
              </div>
              <input
                type="file"
                className="hidden"
                accept="image/*"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  e.currentTarget.value = "";
                  if (file) openAvatarCropper(file);
                }}
              />
            </label>
          </div>
          <div className="space-y-6">
            <div className="bg-white dark:bg-slate-800 p-4 rounded-lg shadow-sm">
              <label className="text-xs font-bold text-indigo-600 dark:text-indigo-400 uppercase tracking-wider mb-2 block">
                Your Name
              </label>
              <input
                type="text"
                value={currentUser.name}
                onChange={(e) =>
                  onUpdateUser({ ...currentUser, name: e.target.value })
                }
                onBlur={async (e) => {
                   try {
                     await upsertCloudChatUser({ ...currentUser, name: e.target.value });
                     pushToast({ title: "Profile name updated", tone: "success" });
                   } catch (err) {
                     console.error("Failed to update name", err);
                     pushToast({
                       title: "Profile name update failed",
                       description: "Please try again.",
                       tone: "error",
                     });
                   }
                }}
                className="text-sm font-semibold text-slate-800 dark:text-slate-200 w-full outline-none bg-transparent"
              />
            </div>
            <div className="p-4 rounded-lg text-xs text-slate-500 dark:text-slate-400 leading-relaxed bg-white dark:bg-slate-800 shadow-sm">
              This is not your username or pin. This name will be visible to
              your WhatsClone Web contacts.
            </div>
          </div>
        </div>
      </div>

      {avatarCropSource && (
        <div className="absolute inset-0 z-40 bg-black/70 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-white dark:bg-[#202c33] rounded-2xl shadow-2xl border border-slate-200 dark:border-[#2f3b43] overflow-hidden">
            <div className="p-4 flex items-center justify-between border-b border-slate-100 dark:border-[#2f3b43]">
              <h3 className="font-bold text-slate-900 dark:text-[#e9edef]">
                Set profile photo
              </h3>
              <button
                onClick={() => setAvatarCropSource("")}
                className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-[#111b21]"
                title="Cancel"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-5">
              <div className="mx-auto w-56 h-56 rounded-full overflow-hidden bg-slate-900 border-4 border-white dark:border-slate-700 shadow-inner">
                <img
                  src={avatarCropSource}
                  alt="Crop preview"
                  className="w-full h-full object-cover"
                  style={{
                    transform: `scale(${avatarCropZoom})`,
                    transformOrigin: `${avatarCropX}% ${avatarCropY}%`,
                  }}
                />
              </div>
              <div className="mt-5 space-y-4">
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Zoom
                  <input
                    type="range"
                    min="1"
                    max="2.5"
                    step="0.01"
                    value={avatarCropZoom}
                    onChange={(event) => setAvatarCropZoom(Number(event.target.value))}
                    className="mt-2 w-full accent-[#00a884]"
                  />
                </label>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Horizontal position
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={avatarCropX}
                    onChange={(event) => setAvatarCropX(Number(event.target.value))}
                    className="mt-2 w-full accent-[#00a884]"
                  />
                </label>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Vertical position
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={avatarCropY}
                    onChange={(event) => setAvatarCropY(Number(event.target.value))}
                    className="mt-2 w-full accent-[#00a884]"
                  />
                </label>
              </div>
              <button
                onClick={() => void saveCroppedAvatar()}
                disabled={isSavingAvatar}
                className="mt-6 w-full py-3 rounded-xl bg-[#00a884] hover:bg-[#008f72] disabled:bg-slate-400 text-white font-bold transition-colors"
              >
                {isSavingAvatar ? "Saving..." : "Save photo"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Settings Pane */}
      <div
        className={cn(
          "absolute inset-0 bg-slate-50 dark:bg-[#111b21] z-20 flex flex-col transition-transform duration-300 transform",
          showSettings ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div
          className="h-28 bg-indigo-600 dark:bg-[#202c33] text-white flex items-end px-6 pb-4 cursor-pointer"
          onClick={() => setActiveRailTab("chats")}
        >
          <div className="flex items-center space-x-6 text-[#e9edef]">
            <ArrowLeft className="w-6 h-6 hover:scale-110 transition-transform" />
            <h2 className="text-[19px] font-medium">Settings</h2>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto">
          {/* Settings Profile Header */}
          <div
            className="flex items-center space-x-4 p-4 bg-white dark:bg-[#111b21] cursor-pointer hover:bg-slate-50 dark:hover:bg-[#202c33]"
            onClick={() => setActiveRailTab("profile")}
          >
            <div className="w-14 h-14 rounded-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center text-slate-400 shrink-0">
              {currentUser.avatar ? (
                <img
                  src={currentUser.avatar}
                  alt="Profile"
                  className="w-full h-full rounded-full object-cover"
                />
              ) : (
                <UserCircle2 className="w-10 h-10" />
              )}
            </div>
            <div className="min-w-0">
              <h3 className="font-bold text-slate-800 dark:text-slate-200 truncate">
                {currentUser.name}
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                Exploring real-time messaging...
              </p>
            </div>
          </div>

          <div className="mt-4 space-y-4 px-4">
            <div className="hello-card p-4">
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <h4 className="text-sm font-semibold text-[var(--hello-text)]">Appearance</h4>
                  <p className="text-xs text-[var(--hello-text-muted)]">Theme, chat wallpaper, and typing ergonomics.</p>
                </div>
                <Paintbrush2 className="h-4 w-4 text-[var(--hello-accent)]" />
              </div>
              <div className="space-y-4 text-sm font-medium">
                <div className="flex items-center justify-between gap-4">
                  <span>Theme</span>
                  <select
                    value={theme}
                    onChange={(e) => setTheme(e.target.value as any)}
                    className="hello-input rounded-xl px-3 py-2 text-xs"
                  >
                    <option value="system">System</option>
                    <option value="cute">Cute theme</option>
                    <option value="light">Light</option>
                    <option value="dark">Dark</option>
                  </select>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <span>Enter sends</span>
                  <label className="flex cursor-pointer items-center">
                    <div className="relative">
                      <input
                        type="checkbox"
                        className="sr-only"
                        checked={enterIsSend}
                        onChange={(e) => setEnterIsSend(e.target.checked)}
                      />
                      <div className={cn("block h-6 w-10 rounded-full transition-colors", enterIsSend ? "bg-[var(--hello-accent)]" : "bg-slate-300 dark:bg-slate-600")} />
                      <div className={cn("absolute left-1 top-1 h-4 w-4 rounded-full bg-white transition-transform", enterIsSend && "translate-x-4")} />
                    </div>
                  </label>
                </div>
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <span>Wallpaper</span>
                    <select
                      value={chatWallpaper}
                      onChange={(e) => setChatWallpaper(e.target.value)}
                      className="hello-input rounded-xl px-3 py-2 text-xs"
                    >
                      <option value="cute-theme">Cute theme</option>
                      <option value="default">Default</option>
                      <option value="solid-dark">Solid Dark</option>
                      <option value="solid-light">Solid Light</option>
                      <option value="emerald">Emerald</option>
                      <option value="rose">Rose</option>
                      <option value="ocean">Ocean Blue</option>
                      <option value="texture-paper">Crumpled Paper</option>
                      <option value="texture-wood">Wood Grain</option>
                      <option value="texture-dots">Polka Dots</option>
                      <option value="texture-lines">Diagonal Lines</option>
                      <option value="img-professional">Professional Abstract</option>
                      <option value="img-nature">Mountain Landscape</option>
                      <option value="img-space">Deep Space</option>
                    </select>
                  </div>
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-xs text-[var(--hello-text-muted)]">Wallpaper opacity</span>
                    <div className="flex items-center gap-2">
                      <input
                        type="range"
                        min="10"
                        max="100"
                        value={chatWallpaperOpacity ?? 100}
                        onChange={(e) => setChatWallpaperOpacity(parseInt(e.target.value, 10))}
                        className="w-28 accent-[var(--hello-accent)]"
                      />
                      <span className="w-8 text-right text-xs text-[var(--hello-text-muted)]">{chatWallpaperOpacity ?? 100}%</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="hello-card p-4">
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <h4 className="text-sm font-semibold text-[var(--hello-text)]">Calls and notifications</h4>
                  <p className="text-xs text-[var(--hello-text-muted)]">Permissions, ring readiness, and desktop alerts.</p>
                </div>
                <Bell className="h-4 w-4 text-[var(--hello-accent)]" />
              </div>
              <div className="space-y-4 text-sm font-medium">
                <div className="flex items-center justify-between gap-4">
                  <span>Last active privacy</span>
                  <select
                    value={privacy}
                    onChange={(e) => handlePrivacyChange(e.target.value as any)}
                    className="hello-input rounded-xl px-3 py-2 text-xs"
                  >
                    <option value="everyone">Everyone</option>
                    <option value="contacts">My Contacts</option>
                    <option value="none">Nobody</option>
                  </select>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <span>Desktop notifications</span>
                  <button
                    type="button"
                    onClick={() => toggleNotifications(!notificationsEnabled)}
                    className={cn(
                      "rounded-full px-3 py-1.5 text-xs font-semibold transition",
                      notificationsEnabled
                        ? "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                        : "hello-pill",
                    )}
                  >
                    {notificationsEnabled ? "On" : "Off"}
                  </button>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  {NOTIFICATION_CATEGORY_PREFS.map((item) => {
                    const enabled = notificationCategories[item.key] ?? true;
                    return (
                      <button
                        key={item.key}
                        type="button"
                        onClick={() => toggleNotificationCategory(item.key)}
                        className={cn(
                          "rounded-xl border px-3 py-2 text-left text-xs font-semibold transition",
                          enabled
                            ? "border-[var(--hello-accent)] bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                            : "border-[var(--hello-border)] text-[var(--hello-text-muted)]",
                        )}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>
                <div className="rounded-2xl border border-[var(--hello-border)] bg-black/5 px-3 py-3 dark:bg-white/5">
                  <div className="flex items-center justify-between gap-3">
                    <span>Camera / microphone</span>
                    <button
                      type="button"
                      onClick={() => void testCameraMic()}
                      disabled={permissionTestBusy}
                      className="rounded-full bg-[var(--hello-accent)] px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-[var(--hello-accent-strong)] disabled:opacity-60"
                    >
                      Test Camera/Mic
                    </button>
                  </div>
                  {permissionTestStatus ? (
                    <p className="mt-2 text-xs leading-6 text-[var(--hello-text-muted)]">
                      {permissionTestStatus}
                    </p>
                  ) : null}
                </div>
              </div>
            </div>

            <div className="hello-card p-4">
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <h4 className="text-sm font-semibold text-[var(--hello-text)]">Account</h4>
                  <p className="text-xs text-[var(--hello-text-muted)]">End this cloud session and return to login.</p>
                </div>
                <UserCircle2 className="h-4 w-4 text-[var(--hello-accent)]" />
              </div>
              <button
                type="button"
                onClick={async () => {
                  try {
                    await logoutCloudUser();
                  } catch (err) {
                    console.error("Cloud logout failed:", err);
                  }
                  localStorage.removeItem("whatsclone_user_real");
                  localStorage.removeItem(CLOUD_SESSION_TOKEN_KEY);
                  onSelectChat(null);
                  setActiveRailTab("chats");
                  onUpdateUser(null);
                }}
                className="flex w-full items-center justify-center rounded-2xl border border-red-500/30 px-4 py-3 text-sm font-semibold text-red-500 transition hover:bg-red-500/10"
              >
                Log out
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Contacts Pane */}
      <div
        className={cn(
          "absolute inset-0 bg-slate-50 dark:bg-[#111b21] z-20 flex flex-col transition-transform duration-300 transform",
          contactsVisible ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div
          className={cn(
            isContactsTab
              ? "h-16 flex-none bg-slate-50 dark:bg-[#111b21] flex flex-row items-center px-4 transition-colors duration-300"
              : "h-28 bg-indigo-600 dark:bg-[#202c33] text-white flex items-end px-6 pb-4 cursor-pointer",
          )}
          onClick={() => {
            if (showContacts) setShowContacts(false);
          }}
        >
          {isContactsTab ? (
            <h1 className="text-[22px] font-bold text-slate-800 dark:text-[#e9edef]">
              Contacts
            </h1>
          ) : (
            <div className="flex items-center space-x-6 text-[#e9edef]">
              <ArrowLeft className="w-6 h-6 hover:scale-110 transition-transform" />
              <h2 className="text-[19px] font-medium">New Chat</h2>
            </div>
          )}
        </div>
        {!showAddContact ? (
          <div className="flex-1 overflow-y-auto">
            <div
              className="p-4 border-b border-slate-100 dark:border-slate-800 cursor-pointer hover:bg-slate-200 dark:hover:bg-slate-800 flex items-center space-x-4 text-indigo-600 dark:text-indigo-400 font-bold"
              onClick={() => setShowAddContact(true)}
            >
              <div className="w-10 h-10 rounded-full bg-indigo-100 dark:bg-indigo-900 flex items-center justify-center">
                <UserPlus className="w-5 h-5" />
              </div>
              <span>New Chat</span>
            </div>
            <div
              className="p-4 border-b border-slate-100 dark:border-slate-800 cursor-pointer hover:bg-slate-200 dark:hover:bg-slate-800 flex items-center space-x-4 text-indigo-600 dark:text-indigo-400 font-bold"
              onClick={() => {
                setShowNewGroup(true);
                setShowContacts(false);
              }}
            >
              <div className="w-10 h-10 rounded-full bg-indigo-100 dark:bg-indigo-900 flex items-center justify-center">
                <Users className="w-5 h-5" />
              </div>
              <span>New Group</span>
            </div>

            <div className="py-2 px-6 text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest mt-2">
              Contacts on WhatsClone
            </div>
            {contacts.map((c) => (
              <div
                key={c.id}
                className="p-4 border-b border-slate-100 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer flex items-center justify-between group"
                onClick={() => handleCreateContactChat(c)}
              >
                <div className="flex items-center space-x-4">
                  <div className="w-10 h-10 bg-indigo-600 rounded-full flex items-center justify-center text-white font-bold overflow-hidden text-sm">
                    {c.avatar ? (
                      <img
                        src={c.avatar}
                        alt="Avatar"
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      c.name.charAt(0).toUpperCase()
                    )}
                  </div>
                  <div>
                    <h3
                      className={cn(
                        "font-semibold text-slate-800 dark:text-slate-200",
                        c.isBlocked &&
                          "line-through text-slate-400 dark:text-slate-600",
                      )}
                    >
                      {c.name}
                    </h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {c.phone || "No phone"}
                    </p>
                  </div>
                </div>
                <div className="hidden group-hover:flex items-center space-x-3 text-slate-400">
                  <button
                    className="text-[10px] uppercase font-bold hover:text-red-500 transition-colors"
                    onClick={(e) => toggleBlockContact(c.id, e)}
                  >
                    {c.isBlocked ? "Unblock" : "Block"}
                  </button>
                  <button
                    className="text-[10px] uppercase font-bold hover:text-red-500 transition-colors"
                    onClick={(e) => deleteContact(c.id, e)}
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          /* New Chat Form */
          <div className="flex-1 overflow-y-auto p-6 flex flex-col bg-slate-50 dark:bg-slate-900">
            <div className="flex items-center justify-between mb-6">
              <h3 className="font-bold text-lg text-slate-800 dark:text-slate-200">
                New Chat
              </h3>
              <div className="flex items-center space-x-2">
                <button
                  onClick={() => refreshUserDiscovery(userSearchQuery)}
                  className="text-xs font-bold text-indigo-600 hover:text-indigo-800 bg-indigo-50 hover:bg-indigo-100 dark:bg-indigo-900/30 dark:hover:bg-indigo-900/50 dark:text-indigo-400 px-3 py-1.5 rounded-full transition-colors flex items-center"
                >
                  <RefreshCw className="w-3 h-3 mr-1" />
                  Refresh users
                </button>
                <button
                  onClick={() => setShowAddContact(false)}
                  className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            <div className="relative mb-6">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 transform -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search Hello users by name or username"
                value={userSearchQuery}
                onChange={(e) => setUserSearchQuery(e.target.value)}
                className="w-full pl-9 pr-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-sm text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            {usersToChat.length === 0 ? (
              <div className="mt-4 rounded-2xl border border-dashed border-slate-300/80 bg-white/70 px-5 py-8 text-center dark:border-slate-700 dark:bg-slate-800/60">
                <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-300">
                  <UserPlus className="h-5 w-5" />
                </div>
                <div className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                  No Hello users found
                </div>
                <div className="mt-1 text-sm text-slate-500">
                  Try another name or username, then refresh users.
                </div>
              </div>
            ) : (
              <div className="flex flex-col space-y-4">
                {/* Online Users */}
                {usersToChat.filter((u) => u.online).length > 0 && (
                  <div>
                    <h4 className="text-xs font-bold text-indigo-600 dark:text-indigo-400 uppercase mb-2">
                      Online users
                    </h4>
                    <div className="space-y-1">
                      {usersToChat
                        .filter((u) => u.online)
                        .sort((a,b) => a.name.localeCompare(b.name))
                        .map((u) => (
                          <div
                            key={u.id}
                            className="flex items-center space-x-3 p-3 rounded-lg cursor-pointer hover:bg-slate-200 dark:hover:bg-slate-800"
                            onClick={() => handleStartDirectChat(u.id)}
                          >
                            <div className="relative w-10 h-10 rounded-full bg-slate-300 dark:bg-slate-700">
                              {u.avatar ? (
                                <img
                                  src={u.avatar}
                                  alt="avatar"
                                  className="w-full h-full rounded-full object-cover"
                                />
                              ) : (
                                <UserIcon className="w-full h-full p-2 text-slate-500" />
                              )}
                              <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 border-2 border-slate-50 dark:border-slate-900 rounded-full" />
                            </div>
                            <div>
                              <p className="font-semibold text-slate-800 dark:text-slate-200 text-sm">
                                {u.name}
                              </p>
                              {u.username ? (
                                <p className="text-xs text-indigo-500 dark:text-indigo-300">
                                  @{u.username}
                                </p>
                              ) : u.phone && (
                                <p className="text-xs text-slate-500">
                                  {u.phone}
                                </p>
                              )}
                            </div>
                          </div>
                        ))}
                    </div>
                  </div>
                )}

                {/* Offline Users */}
                {usersToChat.filter((u) => !u.online).length > 0 && (
                  <div>
                    <h4 className="text-xs font-bold text-slate-400 uppercase mb-2">
                      {usersToChat.filter((u) => u.online).length > 0
                        ? "Offline users"
                        : "All users"}
                    </h4>
                    <div className="space-y-1">
                      {usersToChat
                        .filter((u) => !u.online)
                        .sort((a,b) => a.name.localeCompare(b.name))
                        .map((u) => (
                          <div
                            key={u.id}
                            className="flex items-center space-x-3 p-3 rounded-lg cursor-pointer hover:bg-slate-200 dark:hover:bg-slate-800"
                            onClick={() => handleStartDirectChat(u.id)}
                          >
                            <div className="w-10 h-10 rounded-full bg-slate-300 dark:bg-slate-700">
                              {u.avatar ? (
                                <img
                                  src={u.avatar}
                                  alt="avatar"
                                  className="w-full h-full rounded-full object-cover"
                                />
                              ) : (
                                <UserIcon className="w-full h-full p-2 text-slate-500" />
                              )}
                            </div>
                            <div>
                              <p className="font-semibold text-slate-800 dark:text-slate-200 text-sm">
                                {u.name}
                              </p>
                              {u.username ? (
                                <p className="text-xs text-indigo-500 dark:text-indigo-300">
                                  @{u.username}
                                </p>
                              ) : u.phone ? (
                                <p className="text-xs text-slate-500">
                                  {u.phone}
                                </p>
                              ) : (
                                <p className="text-xs text-slate-400 italic">
                                  Offline
                                </p>
                              )}
                            </div>
                          </div>
                        ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            <div className="mt-8 pt-4 border-t border-slate-200 dark:border-slate-700">
              <h4 className="text-xs font-bold text-slate-400 uppercase mb-4">
                Manual contact (Fallback)
              </h4>
              <form onSubmit={handleCreateContact} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-600 dark:text-slate-400 uppercase mb-1">
                    Name
                  </label>
                  <input
                    required
                    type="text"
                    value={newContactName}
                    onChange={(e) => setNewContactName(e.target.value)}
                    className="w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-4 py-2 text-sm text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    placeholder="e.g. John Doe"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-600 dark:text-slate-400 uppercase mb-1">
                    Phone / Email (Optional)
                  </label>
                  <input
                    type="text"
                    value={newContactPhone}
                    onChange={(e) => setNewContactPhone(e.target.value)}
                    className="w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-4 py-2 text-sm text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    placeholder="e.g. +1 555-0000"
                  />
                </div>
                <button
                  type="submit"
                  className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2 px-4 rounded-lg shadow-sm transition-colors mt-4 text-sm"
                >
                  Save Contact
                </button>
              </form>
            </div>
          </div>
        )}
      </div>

      {/* New Group Pane */}
      <div
        className={cn(
          "absolute inset-0 bg-slate-50 dark:bg-[#111b21] z-20 flex flex-col transition-transform duration-300 transform",
          showNewGroup ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div
          className="h-28 bg-indigo-600 dark:bg-[#202c33] text-white flex items-end px-6 pb-4 cursor-pointer"
          onClick={() => setShowNewGroup(false)}
        >
          <div className="flex items-center space-x-6 text-[#e9edef]">
            <ArrowLeft className="w-6 h-6 hover:scale-110 transition-transform" />
            <h2 className="text-[19px] font-medium">Add group participants</h2>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto p-6 flex flex-col items-center">
          <div className="w-32 h-32 rounded-full bg-slate-200 dark:bg-slate-800 flex items-center justify-center text-slate-400 dark:text-slate-500 mb-6 border-4 border-white dark:border-slate-900 shadow-sm cursor-pointer hover:bg-slate-300 dark:hover:bg-slate-700 transition-colors">
            <Camera className="w-10 h-10" />
          </div>
          <button
            onClick={handleCreateGroup}
            className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3 px-6 rounded-lg shadow-md transition-colors flex items-center justify-center space-x-2"
          >
            <Users className="w-5 h-5" />
            <span>Create Mock Group</span>
          </button>
          <p className="text-xs text-slate-500 dark:text-slate-400 text-center mt-6">
            This will create a group with a few of your contacts automatically.
          </p>
        </div>
      </div>

      {/* Calls Pane */}
      <div
        className={cn(
          "absolute inset-0 bg-slate-50 dark:bg-[#111b21] z-20 flex flex-col transition-transform duration-300 transform",
          showCalls ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="h-16 flex-none bg-slate-50 dark:bg-[#111b21] flex flex-row items-center px-4 transition-colors duration-300">
          <h1 className="text-[22px] font-bold text-slate-800 dark:text-[#e9edef]">
            Calls
          </h1>
        </div>
        <div className="flex-1 overflow-y-auto">
          {callLogs.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-center text-slate-500 dark:text-[#8696a0] h-full">
              <Phone className="w-16 h-16 mb-4 text-slate-300 dark:text-slate-600" />
              <p>No calls yet</p>
            </div>
          ) : (
            <div className="flex flex-col">
               {callLogs.map((log) => {
                 const isIncoming = log.direction
                   ? log.direction === "incoming"
                   : log.calleeId === currentUser.id;
                 const otherUser = log.otherUser;
                 const otherName = otherUser?.name || (isIncoming ? log.callerId : log.calleeId);
                 const typeLabel = log.type === "video" ? "Video" : "Audio";
                 
                 // Get display text for status/duration
                 let statusText: string = log.status;
                 if (log.status === "ended" && log.durationSeconds !== undefined && log.durationSeconds !== null) {
                    const mins = Math.floor(log.durationSeconds / 60);
                    const secs = log.durationSeconds % 60;
                    statusText = `Duration: ${mins}:${secs.toString().padStart(2, "0")}`;
                 } else if (log.status === "declined") {
                    statusText = "Call declined";
                 } else if (log.status === "missed") {
                    statusText = "Missed call";
                 } else if (log.status === "busy") {
                    statusText = "User busy";
                 } else if (log.status === "unavailable") {
                    statusText = "Unavailable";
                 }
                 const isProblemStatus = ["declined", "missed", "busy", "failed", "unavailable", "cancelled"].includes(log.status);

                 return (
                   <div key={log.id} onClick={() => setSelectedCallLog(log)} className="p-4 border-b border-slate-200 dark:border-slate-800 flex items-center hover:bg-slate-100 dark:hover:bg-slate-800 transition cursor-pointer">
                     <div className="w-12 h-12 rounded-full bg-slate-300 dark:bg-slate-700 flex items-center justify-center text-slate-500 shrink-0 overflow-hidden">
                       {otherUser?.avatar ? (
                         <img src={otherUser.avatar} alt={otherName} className="w-full h-full object-cover" />
                       ) : (
                         <UserIcon className="w-6 h-6" />
                       )}
                     </div>
                     <div className="flex-1 ml-4 line-clamp-1">
                       <div className="flex items-center justify-between">
                         <h3 className={cn("font-bold text-[15px] dark:text-slate-200 text-slate-800", isProblemStatus ? "text-red-500" : "")}>
                           {otherName}
                         </h3>
                         <span className="text-xs text-slate-400">
                           {format(new Date(log.startedAt), "MMM d, HH:mm")}
                         </span>
                       </div>
                       <div className="flex items-center justify-between mt-1">
                         <div className="flex items-center space-x-2 text-sm text-slate-500">
                            {isIncoming ? (
                               <ArrowLeft className={cn("w-4 h-4", isProblemStatus ? "text-red-500" : "text-emerald-500")} />
                            ) : (
                               <ArrowLeft className={cn("w-4 h-4 rotate-180", isProblemStatus ? "text-red-500" : "text-emerald-500")} />
                            )}
                            <span className="capitalize">{typeLabel} - {formatCallStatus(log) || statusText}</span>
                         </div>
                         <div className="flex items-center space-x-2">
                           <button
                             onClick={(event) => {
                               event.stopPropagation();
                               void startCallFromLog(log, false);
                             }}
                             className="text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 p-2 rounded-full transition"
                             title="Audio call"
                           >
                              <Phone className="w-5 h-5" />
                           </button>
                           <button
                             onClick={(event) => {
                               event.stopPropagation();
                               void startCallFromLog(log, true);
                             }}
                             className="text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 p-2 rounded-full transition"
                             title="Video call"
                           >
                              <VideoIcon className="w-5 h-5" />
                           </button>
                         </div>
                       </div>
                     </div>
                   </div>
                 );
               })}
            </div>
          )}
        </div>
        {selectedCallLog && (
          <div className="absolute inset-0 z-30 bg-black/45 flex items-center justify-center p-4">
            <div className="w-full max-w-sm rounded-xl bg-white dark:bg-slate-900 shadow-2xl border border-slate-200 dark:border-slate-700 overflow-hidden">
              <div className="flex justify-end p-3">
                <button
                  onClick={() => setSelectedCallLog(null)}
                  className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800"
                  title="Close"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="px-6 pb-6 flex flex-col items-center text-center">
                <div className="w-24 h-24 rounded-full bg-slate-300 dark:bg-slate-700 flex items-center justify-center overflow-hidden mb-4">
                  {selectedCallLog.otherUser?.avatar ? (
                    <img src={selectedCallLog.otherUser.avatar} alt={selectedCallLog.otherUser.name} className="w-full h-full object-cover" />
                  ) : (
                    <UserIcon className="w-10 h-10 text-slate-500" />
                  )}
                </div>
                <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">
                  {selectedCallLog.otherUser?.name || "Call"}
                </h2>
                <p className="text-sm text-slate-500 capitalize mt-1">
                  {selectedCallLog.direction} {selectedCallLog.type} call - {formatCallStatus(selectedCallLog)}
                </p>

                <div className="w-full mt-6 text-left text-sm space-y-2 text-slate-600 dark:text-slate-300">
                  <div className="flex justify-between gap-4">
                    <span>Started</span>
                    <span>{formatCallTimestamp(selectedCallLog.startedAt)}</span>
                  </div>
                  {selectedCallLog.ringingAt && (
                    <div className="flex justify-between gap-4">
                      <span>Rang</span>
                      <span>{format(new Date(selectedCallLog.ringingAt), "MMM d, h:mm a")}</span>
                    </div>
                  )}
                  {selectedCallLog.acceptedAt && (
                    <div className="flex justify-between gap-4">
                      <span>Accepted</span>
                      <span>{format(new Date(selectedCallLog.acceptedAt), "MMM d, h:mm a")}</span>
                    </div>
                  )}
                  {selectedCallLog.connectedAt && (
                    <div className="flex justify-between gap-4">
                      <span>Connected</span>
                      <span>{format(new Date(selectedCallLog.connectedAt), "MMM d, h:mm a")}</span>
                    </div>
                  )}
                  {selectedCallLog.endedAt && (
                    <div className="flex justify-between gap-4">
                      <span>Ended</span>
                      <span>{format(new Date(selectedCallLog.endedAt), "MMM d, h:mm a")}</span>
                    </div>
                  )}
                  {selectedCallLog.durationSeconds !== undefined && selectedCallLog.durationSeconds !== null && (
                    <div className="flex justify-between gap-4">
                      <span>Duration</span>
                      <span>{formatDuration(selectedCallLog.durationSeconds)}</span>
                    </div>
                  )}
                  {selectedCallLog.endReason && (
                    <div className="flex justify-between gap-4">
                      <span>Reason</span>
                      <span className="capitalize">{selectedCallLog.endReason.replace(/_/g, " ")}</span>
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-3 w-full mt-6">
                  <button
                    onClick={() => void startCallFromLog(selectedCallLog, false)}
                    className="py-3 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-semibold flex items-center justify-center gap-2"
                  >
                    <Phone className="w-4 h-4" />
                    Call
                  </button>
                  <button
                    onClick={() => void startCallFromLog(selectedCallLog, true)}
                    className="py-3 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white font-semibold flex items-center justify-center gap-2"
                  >
                    <VideoIcon className="w-4 h-4" />
                    Video
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Status Pane */}
      <div
        className={cn(
          "absolute inset-0 bg-white dark:bg-[#111b21] z-20 flex flex-col transition-transform duration-300 transform",
          showStatus ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="h-16 flex-none bg-slate-50 dark:bg-[#111b21] flex flex-row items-center px-4 transition-colors duration-300 border-b border-slate-200 dark:border-slate-800">
          <h1 className="text-[22px] font-bold text-slate-800 dark:text-[#e9edef]">
            Status
          </h1>
        </div>
        <StatusPane currentUser={currentUser} />
      </div>

      {/* Communities Pane */}
      <div
        className={cn(
          "absolute inset-0 bg-white dark:bg-[#111b21] z-20 flex flex-col transition-transform duration-300 transform",
          showCommunities ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="h-16 flex-none bg-slate-50 dark:bg-[#111b21] flex flex-row items-center px-4 transition-colors duration-300">
          <h1 className="text-[22px] font-bold text-slate-800 dark:text-[#e9edef]">
            Communities
          </h1>
        </div>
        <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-500 dark:text-[#8696a0]">
          <Users className="w-16 h-16 mb-4 text-slate-300 dark:text-slate-600" />
          <p>Introducing communities</p>
        </div>
      </div>

      {/* Starred Pane */}
      <div
        className={cn(
          "absolute inset-0 bg-white dark:bg-[#111b21] z-20 flex flex-col transition-transform duration-300 transform",
          showStarred ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div
          className="h-28 bg-indigo-600 dark:bg-[#202c33] text-white flex items-end px-6 pb-4 cursor-pointer"
          onClick={() => setActiveRailTab("chats")}
        >
          <div className="flex items-center space-x-6 text-[#e9edef]">
            <ArrowLeft className="w-6 h-6 hover:scale-110 transition-transform" />
            <h2 className="text-[19px] font-medium">Starred messages</h2>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto bg-slate-100 dark:bg-[#111b21]">
          {starredMessages.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-center text-slate-500 dark:text-[#8696a0] h-full">
              <div className="w-24 h-24 bg-slate-200 dark:bg-[#202c33] rounded-full flex items-center justify-center mb-6">
                <Star className="w-10 h-10 text-slate-400 dark:text-[#8696a0] fill-current" />
              </div>
              <p className="text-sm">No starred messages</p>
            </div>
          ) : (
            <div className="p-4 space-y-4">
              {starredMessages.map((msg) => (
                <div
                  key={msg.id}
                  className="bg-white dark:bg-[#202c33] p-3 rounded-lg shadow-sm border border-slate-200 dark:border-[#2f3b43]"
                >
                  <div className="flex justify-between items-center mb-2">
                    <div className="flex items-center space-x-2">
                      <span className="font-medium text-slate-800 dark:text-[#e9edef] text-sm">
                        {msg.senderName}
                      </span>
                      <span className="text-slate-500 dark:text-[#8696a0] text-xs">
                        &rarr; You
                      </span>
                    </div>
                    <span className="text-slate-500 dark:text-[#8696a0] text-xs">
                      {format(new Date(msg.timestamp), "MMM d, yyyy HH:mm")}
                    </span>
                  </div>
                  <div className="text-slate-700 dark:text-[#d1d7db] text-sm">
                    {msg.text}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
