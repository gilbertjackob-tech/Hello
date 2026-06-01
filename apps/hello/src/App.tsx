/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */
import { useCallback, useEffect, useState } from "react";
import { SocketProvider } from "./SocketContext";
import { Sidebar } from "./components/Sidebar";
import { ChatWindow } from "./components/ChatWindow";
import { CallOverlay } from "./components/CallOverlay";
import { AuthScreen } from "./components/AuthScreen";
import { HelloBrowser } from "./components/HelloBrowser";
import { Chat, User } from "./types";
import { ThemeProvider } from "./ThemeContext";
import { NotificationProvider } from "./NotificationContext";
import { ToastProvider } from "./ToastContext";
import { cn } from "./lib/utils";
import { API_BASE } from "./api";
import {
  Menu,
  ArrowLeft,
  MessageSquare,
  Cloud,
  CircleDashed,
  Users,
  Settings,
  UserCircle2,
  Globe2,
} from "lucide-react";
import { PermissionsModal } from "./components/PermissionsModal";

const ACTIVE_CHAT_STORAGE_PREFIX = "whatsclone_active_chat";
const ACTIVE_RAIL_TAB_STORAGE_KEY = "whatsclone_active_rail_tab";
const VALID_RAIL_TABS = new Set([
  "chats",
  "drive",
  "calls",
  "status",
  "contacts",
  "communities",
  "profile",
  "settings",
  "starred",
  "browser",
]);

function getInitialRailTab() {
  const saved = localStorage.getItem(ACTIVE_RAIL_TAB_STORAGE_KEY);
  return saved && VALID_RAIL_TABS.has(saved) ? saved : "chats";
}

export default function App() {
  const [activeChat, setActiveChat] = useState<Chat | null>(null);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [currentUser, setCurrentUser] = useState<User | null>(() => {
    const saved = localStorage.getItem("whatsclone_user_real");
    return saved ? JSON.parse(saved) : null;
  });

  const [hasRequestedPermissions, setHasRequestedPermissions] = useState(() => {
    return localStorage.getItem("whatsclone_permissions") === "true";
  });

  const [activeRailTab, setActiveRailTab] = useState<string>(getInitialRailTab);
  const savedActiveChatId = currentUser
    ? localStorage.getItem(`${ACTIVE_CHAT_STORAGE_PREFIX}_${currentUser.id}`)
    : null;
  const isBrowserRailTab = activeRailTab === "browser";

  const selectChat = useCallback((chat: Chat | null) => {
    setActiveChat(chat);
    if (!currentUser) return;

    const storageKey = `${ACTIVE_CHAT_STORAGE_PREFIX}_${currentUser.id}`;
    if (chat?.id) {
      localStorage.setItem(storageKey, chat.id);
    } else {
      localStorage.removeItem(storageKey);
    }
  }, [currentUser]);

  useEffect(() => {
    if (!currentUser) {
      localStorage.removeItem("whatsclone_user_real");
      return;
    }

    localStorage.setItem("whatsclone_user_real", JSON.stringify(currentUser));

    let cancelled = false;

    async function validateCurrentUser() {
      try {
        const res = await fetch(`${API_BASE}/users/${currentUser.id}`);

        if (cancelled) return;

        if (res.status === 404) {
          localStorage.removeItem("whatsclone_user_real");
          setCurrentUser(null);
          return;
        }

        if (!res.ok) {
          console.error("Failed to validate current user", res.status);
        }
      } catch (err) {
        if (!cancelled) {
          console.error("Failed to validate current user", err);
        }
      }
    }

    validateCurrentUser();

    return () => {
      cancelled = true;
    };
  }, [currentUser]);

  useEffect(() => {
    if (VALID_RAIL_TABS.has(activeRailTab)) {
      localStorage.setItem(ACTIVE_RAIL_TAB_STORAGE_KEY, activeRailTab);
    }
  }, [activeRailTab]);

  if (!currentUser) {
    return <AuthScreen onAuthSuccess={setCurrentUser} />;
  }

  return (
    <ThemeProvider>
      <ToastProvider>
        <SocketProvider currentUser={currentUser}>
          <NotificationProvider currentUser={currentUser}>
          {currentUser && !hasRequestedPermissions && (
            <PermissionsModal
              onDone={() => {
                localStorage.setItem("whatsclone_permissions", "true");
                setHasRequestedPermissions(true);
              }}
            />
          )}
          <div className="relative flex h-[100dvh] w-full overflow-hidden bg-transparent text-[var(--hello-text)] transition-colors duration-300">
            <div className="pointer-events-none absolute inset-0">
              <div className="absolute left-[-10%] top-[-15%] h-[280px] w-[280px] rounded-full bg-emerald-300/20 blur-3xl dark:bg-emerald-500/10" />
              <div className="absolute bottom-[-10%] right-[-6%] h-[260px] w-[260px] rounded-full bg-amber-200/30 blur-3xl dark:bg-cyan-500/10" />
            </div>
            <div className="relative flex h-full w-full flex-col overflow-hidden px-2 pb-2 pt-2 sm:px-3 sm:pb-3 sm:pt-3 md:flex-row md:gap-3">
              <div className="hello-panel-strong flex h-full w-full flex-col overflow-hidden rounded-[32px] md:flex-row md:min-h-0">
                <CallOverlay currentUser={currentUser} />

          {/* Global Navigation (Bottom on Mobile, Left Rail on Desktop) */}
          <div
            className={cn(
              "z-30 flex shrink-0 order-3 transition-colors duration-300 md:order-1",
              "border-t border-[var(--hello-border)] bg-[var(--hello-panel)] hello-safe-bottom md:h-full md:w-[76px] md:flex-col md:items-center md:border-r md:border-t-0 md:px-0 md:py-5",
              "h-[72px] w-full px-2",
              activeChat ? "hidden md:flex" : "flex",
            )}
          >
            <div className="flex md:flex-col gap-1 sm:gap-2 md:gap-4 w-full h-full justify-around md:justify-start items-center text-slate-500 dark:text-[#aebac1]">
              <button
                onClick={() => setActiveRailTab("chats")}
                className={cn(
                  "relative rounded-full p-2.5 transition-colors",
                  activeRailTab === "chats"
                    ? "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                    : "hover:bg-black/5 dark:hover:bg-white/5",
                )}
              >
                <MessageSquare className="w-5 sm:w-6 h-5 sm:h-6 fill-transparent stroke-current dark:text-[#e9edef]" />
                <span className="absolute top-1 right-1 bg-emerald-500 text-white text-[9px] sm:text-[10px] w-3.5 sm:w-4 h-3.5 sm:h-4 rounded-full flex items-center justify-center font-bold">
                  25
                </span>
              </button>

              <button
                onClick={() => {
                  setActiveRailTab("drive");
                  selectChat(null);
                }}
                className={cn(
                  "rounded-full p-2.5 transition-colors",
                  activeRailTab === "drive"
                    ? "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                    : "hover:bg-black/5 dark:hover:bg-white/5",
                )}
                title="Drive"
              >
                <Cloud className="w-5 sm:w-6 h-5 sm:h-6" />
              </button>

              <button
                onClick={() => setActiveRailTab("status")}
                className={cn(
                  "rounded-full p-2.5 transition-colors",
                  activeRailTab === "status"
                    ? "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                    : "hover:bg-black/5 dark:hover:bg-white/5",
                )}
                title="Status"
              >
                <CircleDashed className="w-5 sm:w-6 h-5 sm:h-6" />
              </button>

              <button
                onClick={() => setActiveRailTab("contacts")}
                className={cn(
                  "rounded-full p-2.5 transition-colors",
                  activeRailTab === "contacts"
                    ? "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                    : "hover:bg-black/5 dark:hover:bg-white/5",
                )}
                title="Contacts"
              >
                <UserCircle2 className="w-5 sm:w-6 h-5 sm:h-6" />
              </button>

              <button
                onClick={() => setActiveRailTab("communities")}
                className={cn(
                  "rounded-full p-2.5 transition-colors",
                  activeRailTab === "communities"
                    ? "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                    : "hover:bg-black/5 dark:hover:bg-white/5",
                )}
                title="Communities"
              >
                <Users className="w-5 sm:w-6 h-5 sm:h-6" />
              </button>

              <button
                onClick={() => {
                  setActiveRailTab("browser");
                  selectChat(null);
                }}
                className={cn(
                  "rounded-full p-2.5 transition-colors",
                  activeRailTab === "browser"
                    ? "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                    : "hover:bg-black/5 dark:hover:bg-white/5",
                )}
                title="Browser"
              >
                <Globe2 className="w-5 sm:w-6 h-5 sm:h-6" />
              </button>

              <div className="hidden md:block flex-1"></div>

              <button
                onClick={() => setActiveRailTab("settings")}
                className={cn(
                  "rounded-full p-2.5 transition-colors",
                  activeRailTab === "settings"
                    ? "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                    : "hover:bg-black/5 dark:hover:bg-white/5",
                )}
              >
                <Settings className="w-5 sm:w-6 h-5 sm:h-6" />
              </button>

              <button
                onClick={() => setActiveRailTab("profile")}
                className={cn(
                  "h-8 w-8 shrink-0 cursor-pointer overflow-hidden rounded-full border-2 transition-colors sm:h-9 sm:w-9 md:mt-2",
                  activeRailTab === "profile"
                    ? "border-[var(--hello-accent)]"
                    : "border-transparent hover:border-[var(--hello-border-strong)]",
                )}
              >
                {currentUser.avatar ? (
                  <img
                    src={currentUser.avatar}
                    alt="Me"
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <UserCircle2 className="w-full h-full" />
                )}
              </button>
            </div>
          </div>

          {/* Sidebar */}
          <div
            className={cn(
              "z-20 order-1 flex flex-col border-[var(--hello-border)] transition-all duration-300 md:order-2 md:border-r md:min-h-0",
              isBrowserRailTab
                ? "hidden"
                : !activeChat
                  ? "flex-1 w-full min-h-0 md:h-full md:w-[350px] md:flex-none"
                  : "hidden md:flex md:w-[350px] md:h-full md:flex-none",
              !isBrowserRailTab && !isSidebarOpen && "md:w-0 md:hidden",
            )}
          >
            <Sidebar
              activeChatId={activeChat?.id}
              restoreActiveChatId={savedActiveChatId || undefined}
              onSelectChat={selectChat}
              currentUser={currentUser}
              onUpdateUser={setCurrentUser}
              activeRailTab={activeRailTab}
              setActiveRailTab={setActiveRailTab}
            />
          </div>

          {/* Main Chat Area */}
          <div
            className={cn(
              "relative order-2 flex h-full flex-1 flex-col min-h-0 overflow-hidden bg-transparent transition-colors duration-300 md:order-3",
              !activeChat && !isBrowserRailTab && "hidden md:flex",
            )}
          >
            {/* Minimal top bar when sidebar is collapsed on desktop, or mobile back button */}
            {activeChat && (
              <div className="absolute top-3 left-4 z-50 md:hidden">
                <button
                  onClick={() => selectChat(null)}
                  className="hello-panel-strong flex h-11 w-11 items-center justify-center rounded-full text-[var(--hello-text)]"
                >
                  <ArrowLeft className="w-5 h-5" />
                </button>
              </div>
            )}

            {/* Desktop menu toggle (floating when closed) */}
            {!isSidebarOpen && (
              <div className="absolute top-3 left-4 z-50 hidden md:flex">
                <button
                  onClick={() => setIsSidebarOpen(true)}
                  className="hello-panel-strong flex h-11 w-11 items-center justify-center rounded-full text-[var(--hello-text)] transition hover:scale-[1.02]"
                >
                  <Menu className="w-5 h-5" />
                </button>
              </div>
            )}

            {isBrowserRailTab ? (
              <HelloBrowser />
            ) : activeChat ? (
              <ChatWindow
                key={activeChat.id}
                chat={activeChat}
                currentUser={currentUser}
                onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
                isSidebarOpen={isSidebarOpen}
              />
            ) : (
              <div className="relative flex h-full w-full flex-col items-center justify-center overflow-hidden bg-[radial-gradient(circle_at_top,rgba(15,143,120,0.1),transparent_35%),linear-gradient(180deg,rgba(255,255,255,0.34),transparent)] px-8 text-center transition-colors duration-300 dark:bg-[radial-gradient(circle_at_top,rgba(40,192,164,0.08),transparent_30%),linear-gradient(180deg,rgba(255,255,255,0.02),transparent)]">
                {/* When no chat is active, we STILL need to toggle sidebar on desktop if they collapse it */}
                {!isSidebarOpen && (
                  <button
                    onClick={() => setIsSidebarOpen(true)}
                    className="hello-panel-strong absolute left-4 top-4 z-50 flex h-11 w-11 items-center justify-center rounded-full text-[var(--hello-text)] transition hover:scale-[1.02]"
                  >
                    <Menu className="w-5 h-5" />
                  </button>
                )}

                <div className="mb-8 flex h-36 w-36 items-center justify-center rounded-[32px] border border-white/30 bg-white/45 shadow-[var(--hello-shadow)] backdrop-blur-xl transition-colors duration-300 dark:border-white/8 dark:bg-white/5">
                  <svg
                    viewBox="0 0 100 100"
                    width="100"
                    height="100"
                    className="text-[var(--hello-accent)]/50"
                  >
                    <path
                      fill="currentColor"
                      d="M50,10A40,40,0,1,0,90,50,40.045,40.045,0,0,0,50,10ZM50,85A35,35,0,1,1,85,50,35.039,35.039,0,0,1,50,85Z"
                    ></path>
                  </svg>
                </div>
                <h2 className="text-3xl font-semibold tracking-tight text-[var(--hello-text)]">
                  Hello for GlassBox
                </h2>
                <p className="mt-4 max-w-md text-sm leading-7 text-[var(--hello-text-muted)]">
                  Keep chat, calls, files, and GlassBox sharing in one premium workspace. Pick a conversation or start a new one from the rail.
                </p>
                <div className="mt-8 flex flex-wrap items-center justify-center gap-3 text-xs font-semibold">
                  <span className="hello-pill px-3 py-2">Smart Messenger</span>
                  <span className="hello-pill px-3 py-2">Calls + Status</span>
                  <span className="hello-pill px-3 py-2">GlassBox Share</span>
                </div>
              </div>
            )}
          </div>
              </div>
            </div>
          </div>
          </NotificationProvider>
        </SocketProvider>
      </ToastProvider>
    </ThemeProvider>
  );
}
