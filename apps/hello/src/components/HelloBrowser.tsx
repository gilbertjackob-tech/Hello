import { FormEvent, ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Bot,
  Code2,
  Download,
  FileCode2,
  Globe2,
  History,
  KeyRound,
  LayoutPanelTop,
  Loader2,
  MousePointerClick,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Square,
  TerminalSquare,
  Trash2,
  UserPlus,
  X,
} from "lucide-react";
import { cn } from "../lib/utils";

type BrowserTab = {
  tabId: string;
  profileId: string;
  url: string;
  title: string;
  domHash?: string;
};

type BrowserProfile = {
  id: string;
  name: string;
  email?: string | null;
};

type BrowserPanel = "history" | "downloads" | "passwords" | "dom" | "api";
type ApiMode = "query" | "parse" | "request" | "html" | "screenshot" | "a11y" | "targets" | "action";
type ActionMode = "click" | "type" | "scroll" | "wait" | "evaluate";

const ZERO_BOUNDS = { x: 0, y: 0, width: 0, height: 0 };
const GLASSBOX_STATUS_ENDPOINT = "/api/hello/status";

function normalizeNavigationInput(input: string) {
  const value = input.trim();
  if (!value) return "";
  if (/^(https?:|file:|about:)/i.test(value)) return value;

  const looksLikeHost =
    /^[a-z0-9.-]+\.[a-z]{2,}(?::\d+)?(?:\/.*)?$/i.test(value) ||
    /^localhost(?::\d+)?(?:\/.*)?$/i.test(value) ||
    /^\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?(?:\/.*)?$/i.test(value);

  if (looksLikeHost) {
    return `https://${value}`;
  }

  return `https://duckduckgo.com/?q=${encodeURIComponent(value)}`;
}

function getFilename(download: any) {
  return download.file_name || download.filename || download.name || "download";
}

function getTimestamp(item: any) {
  return item.last_visited || item.timestamp || item.created_at || item.updated_at;
}

function formatTimestamp(value: unknown) {
  if (!value) return "";
  const date = typeof value === "number" ? new Date(value) : new Date(String(value));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function sendBrowserViewMessage(tabId: string | null, bounds = ZERO_BOUNDS, active = false) {
  if (typeof window === "undefined") return;
  if (window.parent === window) return;

  window.parent.postMessage(
    {
      source: "glassbox-hello-browser",
      type: "glassbox:browser-view",
      active,
      tabId,
      bounds,
      coordinateSpace: "iframe",
    },
    window.location.origin,
  );
}

async function readJsonResponse(response: Response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    return { text };
  }
}

function IconButton({
  label,
  onClick,
  disabled,
  children,
  active,
}: {
  label: string;
  onClick?: () => void;
  disabled?: boolean;
  children: ReactNode;
  active?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
      className={cn(
        "inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[var(--hello-border)] text-[var(--hello-text-muted)] transition",
        active
          ? "bg-[var(--hello-accent)] text-white"
          : "bg-[var(--hello-panel-muted)] hover:border-[var(--hello-border-strong)] hover:text-[var(--hello-text)]",
        disabled && "cursor-not-allowed opacity-45 hover:border-[var(--hello-border)] hover:text-[var(--hello-text-muted)]",
      )}
    >
      {children}
    </button>
  );
}

export function HelloBrowser() {
  const [shellConnected, setShellConnected] = useState<boolean | null>(null);
  const [profiles, setProfiles] = useState<BrowserProfile[]>([]);
  const [activeProfileId, setActiveProfileId] = useState("default");
  const [tabs, setTabs] = useState<BrowserTab[]>([]);
  const [activeTabId, setActiveTabId] = useState<string | null>(null);
  const [addressInput, setAddressInput] = useState("");
  const [statusText, setStatusText] = useState("");
  const [busy, setBusy] = useState(false);
  const [activePanel, setActivePanel] = useState<BrowserPanel>("api");
  const [history, setHistory] = useState<any[]>([]);
  const [downloads, setDownloads] = useState<any[]>([]);
  const [passwords, setPasswords] = useState<any[]>([]);
  const [domSnapshot, setDomSnapshot] = useState<any[]>([]);
  const [actionTargets, setActionTargets] = useState<any[]>([]);
  const [panelSearch, setPanelSearch] = useState("");
  const [profileFormOpen, setProfileFormOpen] = useState(false);
  const [profileName, setProfileName] = useState("");
  const [profileEmail, setProfileEmail] = useState("");
  const [profileStartUrl, setProfileStartUrl] = useState("https://accounts.google.com/");
  const [passwordForm, setPasswordForm] = useState({ origin: "", username: "", password: "" });
  const [apiMode, setApiMode] = useState<ApiMode>("parse");
  const [selectorInput, setSelectorInput] = useState("button, a, input");
  const [requestMethod, setRequestMethod] = useState("GET");
  const [requestUrl, setRequestUrl] = useState("");
  const [requestHeaders, setRequestHeaders] = useState("{}");
  const [requestBody, setRequestBody] = useState("");
  const [actionMode, setActionMode] = useState<ActionMode>("click");
  const [actionSelector, setActionSelector] = useState("");
  const [actionText, setActionText] = useState("");
  const [apiOutput, setApiOutput] = useState<any>(null);
  const liveViewRef = useRef<HTMLDivElement>(null);
  const activeTabRef = useRef<string | null>(null);
  const initializedRef = useRef(false);

  const activeTab = useMemo(
    () => tabs.find((tab) => tab.tabId === activeTabId) || null,
    [tabs, activeTabId],
  );
  const activeProfile = profiles.find((profile) => profile.id === activeProfileId);

  const postBounds = useCallback((tabId = activeTabRef.current, active = true) => {
    if (!tabId || !liveViewRef.current || shellConnected !== true) {
      sendBrowserViewMessage(tabId || null, ZERO_BOUNDS, false);
      return;
    }

    const rect = liveViewRef.current.getBoundingClientRect();
    const bounds = {
      x: Math.round(rect.x),
      y: Math.round(rect.y),
      width: Math.max(1, Math.round(rect.width)),
      height: Math.max(1, Math.round(rect.height)),
    };

    sendBrowserViewMessage(tabId, bounds, active);
  }, [shellConnected]);

  const fetchProfiles = useCallback(async () => {
    const response = await fetch("/api/profiles");
    if (!response.ok) throw new Error("profiles_unavailable");
    const data = await response.json();
    setProfiles(Array.isArray(data) ? data : []);
    return Array.isArray(data) ? data as BrowserProfile[] : [];
  }, []);

  const fetchTabs = useCallback(async (profileId = activeProfileId, preferredTabId?: string | null) => {
    const response = await fetch("/api/tabs");
    if (!response.ok) throw new Error("tabs_unavailable");
    const data = await response.json();
    const profileTabs = Array.isArray(data)
      ? data.filter((tab: BrowserTab) => tab.profileId === profileId)
      : [];

    setTabs(profileTabs);
    setActiveTabId((current) => {
      if (profileTabs.length === 0) return null;
      if (preferredTabId && profileTabs.some((tab: BrowserTab) => tab.tabId === preferredTabId)) return preferredTabId;
      if (current && profileTabs.some((tab: BrowserTab) => tab.tabId === current)) return current;
      return profileTabs[0].tabId;
    });

    return profileTabs as BrowserTab[];
  }, [activeProfileId]);

  const refreshLocalPanels = useCallback(async (profileId = activeProfileId, tabId = activeTabRef.current) => {
    if (shellConnected !== true) return;

    const query = panelSearch.trim();
    const params = new URLSearchParams({ profileId });
    if (query) params.set("q", query);

    const jobs: Array<Promise<void>> = [
      fetch(`/api/memory/history?${params.toString()}`)
        .then((res) => res.json())
        .then((data) => setHistory(Array.isArray(data) ? data : []))
        .catch(() => setHistory([])),
      fetch(`/api/memory/downloads?${params.toString()}`)
        .then((res) => res.json())
        .then((data) => setDownloads(Array.isArray(data) ? data : []))
        .catch(() => setDownloads([])),
      fetch(`/api/passwords?profileId=${encodeURIComponent(profileId)}`)
        .then((res) => res.json())
        .then((data) => setPasswords(Array.isArray(data) ? data : []))
        .catch(() => setPasswords([])),
    ];

    if (tabId) {
      jobs.push(
        fetch(`/api/tabs/${encodeURIComponent(tabId)}/dom`)
          .then((res) => res.json())
          .then((data) => setDomSnapshot(Array.isArray(data) ? data : []))
          .catch(() => setDomSnapshot([])),
      );
      jobs.push(
        fetch(`/api/tabs/${encodeURIComponent(tabId)}/action-targets`)
          .then((res) => res.json())
          .then((data) => setActionTargets(Array.isArray(data?.targets) ? data.targets : []))
          .catch(() => setActionTargets([])),
      );
    } else {
      setDomSnapshot([]);
      setActionTargets([]);
    }

    await Promise.all(jobs);
  }, [activeProfileId, panelSearch, shellConnected]);

  const ensureTab = useCallback(async (profileId: string) => {
    const profileTabs = await fetchTabs(profileId);
    if (profileTabs.length > 0) return profileTabs[0].tabId;

    const response = await fetch("/api/tabs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ profileId }),
    });
    const data = await readJsonResponse(response);
    const tabId = data.tabId || data.id;
    await fetchTabs(profileId, tabId);
    return tabId as string;
  }, [fetchTabs]);

  const initialize = useCallback(async () => {
    setStatusText("");
    try {
      const shellResponse = await fetch(GLASSBOX_STATUS_ENDPOINT);
      if (!shellResponse.ok) {
        setShellConnected(false);
        return;
      }

      setShellConnected(true);
      const settingsResponse = await fetch("/api/settings");
      const settings = settingsResponse.ok ? await settingsResponse.json() : {};
      const profileId = settings.activeProfileId || "default";

      setActiveProfileId(profileId);
      await fetchProfiles();
      const tabId = await ensureTab(profileId);
      setActiveTabId(tabId);
      activeTabRef.current = tabId;
      await refreshLocalPanels(profileId, tabId);
      requestAnimationFrame(() => postBounds(tabId));
    } catch {
      setShellConnected(false);
    }
  }, [ensureTab, fetchProfiles, postBounds, refreshLocalPanels]);

  useEffect(() => {
    if (initializedRef.current) return;
    initializedRef.current = true;
    void initialize();
  }, [initialize]);

  useEffect(() => {
    activeTabRef.current = activeTabId;
  }, [activeTabId]);

  useEffect(() => {
    if (!activeTab) {
      setAddressInput("");
      return;
    }

    if (document.activeElement?.tagName !== "INPUT") {
      setAddressInput(activeTab.url || "");
    }
    setRequestUrl(activeTab.url || "");
  }, [activeTab?.url, activeTab?.tabId]);

  useEffect(() => {
    if (shellConnected !== true) return;
    const id = window.setInterval(() => {
      void fetchTabs(activeProfileId, activeTabRef.current);
      void refreshLocalPanels(activeProfileId, activeTabRef.current);
    }, 1800);

    return () => window.clearInterval(id);
  }, [activeProfileId, fetchTabs, refreshLocalPanels, shellConnected]);

  useEffect(() => {
    if (shellConnected !== true) return;
    const node = liveViewRef.current;
    if (!node) return;

    const sync = () => postBounds(activeTabRef.current, true);
    const observer = new ResizeObserver(sync);
    observer.observe(node);
    sync();

    window.addEventListener("resize", sync);
    window.addEventListener("scroll", sync, true);

    return () => {
      observer.disconnect();
      window.removeEventListener("resize", sync);
      window.removeEventListener("scroll", sync, true);
      sendBrowserViewMessage(activeTabRef.current, ZERO_BOUNDS, false);
    };
  }, [postBounds, shellConnected]);

  useEffect(() => {
    if (shellConnected === true && activeTabId) {
      requestAnimationFrame(() => postBounds(activeTabId, true));
    }
  }, [activeTabId, postBounds, shellConnected]);

  useEffect(() => () => {
    sendBrowserViewMessage(activeTabRef.current, ZERO_BOUNDS, false);
  }, []);

  const createTab = async (url?: string, profileId = activeProfileId) => {
    setBusy(true);
    try {
      const response = await fetch("/api/tabs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ profileId, initialUrl: url || undefined }),
      });
      const data = await readJsonResponse(response);
      const tabId = data.tabId || data.id;
      await fetchTabs(profileId, tabId);
      setActiveTabId(tabId);
      await refreshLocalPanels(profileId, tabId);
      requestAnimationFrame(() => postBounds(tabId));
    } finally {
      setBusy(false);
    }
  };

  const closeTab = async (tabId: string) => {
    await fetch(`/api/tabs/${encodeURIComponent(tabId)}`, { method: "DELETE" });
    const remaining = await fetchTabs(activeProfileId);
    const nextTabId = remaining[0]?.tabId || null;
    setActiveTabId(nextTabId);
    if (nextTabId) {
      requestAnimationFrame(() => postBounds(nextTabId));
    } else {
      sendBrowserViewMessage(tabId, ZERO_BOUNDS, false);
    }
  };

  const focusTab = async (tabId: string) => {
    setActiveTabId(tabId);
    activeTabRef.current = tabId;
    await refreshLocalPanels(activeProfileId, tabId);
    requestAnimationFrame(() => postBounds(tabId));
  };

  const navigate = async (event?: FormEvent, overrideInput?: string) => {
    event?.preventDefault();
    const target = normalizeNavigationInput(overrideInput || addressInput);
    if (!target) return;

    let tabId = activeTabId;
    if (!tabId) {
      await createTab(target);
      return;
    }

    setBusy(true);
    setStatusText("Loading");
    try {
      postBounds(tabId);
      const response = await fetch(`/api/tabs/${encodeURIComponent(tabId)}/action/navigate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: target }),
      });
      const data = await readJsonResponse(response);
      if (!response.ok || data.error || data.success === false) {
        throw new Error(data.error || data.reason || "Navigation failed");
      }

      setAddressInput(data.url || target);
      await fetchTabs(activeProfileId, tabId);
      await refreshLocalPanels(activeProfileId, tabId);
      setStatusText("");
    } catch (error: any) {
      setStatusText(error?.message || "Navigation failed");
    } finally {
      setBusy(false);
      requestAnimationFrame(() => postBounds(tabId));
    }
  };

  const runNavigationCommand = async (command: "back" | "forward" | "reload" | "stop") => {
    if (!activeTabId) return;
    setBusy(true);
    try {
      const response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/${command}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: command === "reload" ? JSON.stringify({ hard: false }) : undefined,
      });
      const data = await readJsonResponse(response);
      if (!response.ok || data.success === false || data.error) {
        setStatusText(data.reason || data.error || `${command} failed`);
      } else {
        setStatusText("");
      }
      window.setTimeout(() => {
        void fetchTabs(activeProfileId, activeTabId);
        void refreshLocalPanels(activeProfileId, activeTabId);
      }, 250);
      requestAnimationFrame(() => postBounds(activeTabId));
    } finally {
      setBusy(false);
    }
  };

  const switchProfile = async (profileId: string) => {
    setActiveProfileId(profileId);
    setBusy(true);
    try {
      await fetch("/api/settings", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ activeProfileId: profileId }),
      });

      const nextTabId = await ensureTab(profileId);
      setActiveTabId(nextTabId);
      await refreshLocalPanels(profileId, nextTabId);
      requestAnimationFrame(() => postBounds(nextTabId));
    } finally {
      setBusy(false);
    }
  };

  const createProfile = async () => {
    const name = profileName.trim();
    if (!name) {
      setStatusText("Profile name is required");
      return;
    }
    if (profileEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(profileEmail.trim())) {
      setStatusText("Enter a valid email");
      return;
    }

    setBusy(true);
    try {
      const createResponse = await fetch("/api/profiles", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name,
          email: profileEmail.trim() || undefined,
        }),
      });
      const profile = await readJsonResponse(createResponse);
      if (!createResponse.ok || profile.error) {
        throw new Error(profile.message || profile.error || "Profile creation failed");
      }

      const openResponse = await fetch(`/api/profiles/${encodeURIComponent(profile.id)}/open`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: profileStartUrl.trim() || "https://accounts.google.com/" }),
      });
      const openData = await readJsonResponse(openResponse);
      if (!openResponse.ok || openData.error) {
        throw new Error(openData.error || "Profile open failed");
      }

      await fetchProfiles();
      await switchProfile(profile.id);
      const tabId = openData.tabId || openData.id;
      await fetchTabs(profile.id, tabId);
      setActiveTabId(tabId);
      setProfileFormOpen(false);
      setProfileName("");
      setProfileEmail("");
      setStatusText("");
      requestAnimationFrame(() => postBounds(tabId));
    } catch (error: any) {
      setStatusText(error?.message || "Profile creation failed");
    } finally {
      setBusy(false);
    }
  };

  const detectProfileEmail = async () => {
    if (!activeProfileId) return;
    setBusy(true);
    try {
      const response = await fetch(`/api/profiles/${encodeURIComponent(activeProfileId)}/detect-email`, {
        method: "POST",
      });
      const data = await readJsonResponse(response);
      if (!response.ok || data.error || !data.success) {
        setStatusText(data.reason || data.error || "Email not detected");
        return;
      }
      await fetchProfiles();
      setStatusText(`Detected ${data.email}`);
    } finally {
      setBusy(false);
    }
  };

  const savePassword = async () => {
    if (!passwordForm.origin.trim() || !passwordForm.username.trim() || !passwordForm.password) return;
    await fetch("/api/passwords", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ profileId: activeProfileId, ...passwordForm }),
    });
    setPasswordForm({ origin: "", username: "", password: "" });
    await refreshLocalPanels(activeProfileId, activeTabId);
  };

  const deletePassword = async (passwordId: string) => {
    await fetch(`/api/passwords/${encodeURIComponent(passwordId)}`, { method: "DELETE" });
    await refreshLocalPanels(activeProfileId, activeTabId);
  };

  const runApiTool = async () => {
    if (!activeTabId) return;
    setBusy(true);
    setApiOutput(null);

    try {
      let response: Response;
      if (apiMode === "query") {
        response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/query`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ selector: selectorInput.trim(), limit: 50 }),
        });
      } else if (apiMode === "parse") {
        response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/parse`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ includeActionTargets: true }),
        });
      } else if (apiMode === "request") {
        let parsedHeaders = {};
        if (requestHeaders.trim()) {
          parsedHeaders = JSON.parse(requestHeaders);
        }
        response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/request`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            method: requestMethod,
            url: requestUrl,
            headers: parsedHeaders,
            body: requestBody || undefined,
          }),
        });
      } else if (apiMode === "html") {
        response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/html`);
      } else if (apiMode === "screenshot") {
        response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/screenshot?format=json`);
      } else if (apiMode === "a11y") {
        response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/a11y`);
      } else if (apiMode === "targets") {
        response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/action-targets`);
      } else {
        const payload =
          actionMode === "evaluate"
            ? { script: actionText }
            : actionMode === "scroll"
              ? { direction: actionText || "down", amount: 640 }
              : actionMode === "wait"
                ? { selector: actionSelector, timeoutMs: 5000 }
                : { selector: actionSelector, text: actionText, clearFirst: true };

        response = await fetch(`/api/tabs/${encodeURIComponent(activeTabId)}/action/${actionMode}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
      }

      const output = await readJsonResponse(response);
      setApiOutput(output);
      if (!response.ok || output.error) {
        setStatusText(output.error || "API request failed");
      } else {
        setStatusText("");
      }
      await refreshLocalPanels(activeProfileId, activeTabId);
    } catch (error: any) {
      setApiOutput({ error: error?.message || "API request failed" });
      setStatusText(error?.message || "API request failed");
    } finally {
      setBusy(false);
    }
  };

  if (shellConnected === null) {
    return (
      <div className="flex h-full items-center justify-center bg-[var(--hello-bg)] text-[var(--hello-text-muted)]">
        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
        Connecting to GlassBox
      </div>
    );
  }

  if (shellConnected === false) {
    return (
      <div className="flex h-full items-center justify-center bg-[var(--hello-bg)] p-6">
        <div className="hello-panel-strong max-w-md rounded-2xl p-6 text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]">
            <LayoutPanelTop className="h-6 w-6" />
          </div>
          <h2 className="text-lg font-semibold text-[var(--hello-text)]">GlassBox Shell Required</h2>
          <p className="mt-2 text-sm leading-6 text-[var(--hello-text-muted)]">
            Browser control is available when Hello is opened inside the GlassBox desktop shell at /hello.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--hello-bg)] text-[var(--hello-text)]">
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[var(--hello-border)] bg-[var(--hello-panel-strong)] px-3 py-2">
        <div className="flex items-center gap-1">
          <IconButton label="Back" onClick={() => void runNavigationCommand("back")} disabled={!activeTabId || busy}>
            <ArrowLeft className="h-4 w-4" />
          </IconButton>
          <IconButton label="Forward" onClick={() => void runNavigationCommand("forward")} disabled={!activeTabId || busy}>
            <ArrowRight className="h-4 w-4" />
          </IconButton>
          <IconButton label="Reload" onClick={() => void runNavigationCommand("reload")} disabled={!activeTabId || busy}>
            <RefreshCw className={cn("h-4 w-4", busy && "animate-spin")} />
          </IconButton>
          <IconButton label="Stop" onClick={() => void runNavigationCommand("stop")} disabled={!activeTabId}>
            <Square className="h-3.5 w-3.5" />
          </IconButton>
        </div>

        <form onSubmit={navigate} className="flex min-w-[220px] flex-1 items-center gap-2 rounded-xl border border-[var(--hello-border)] bg-[var(--hello-panel-muted)] px-3 py-1.5">
          <Search className="h-4 w-4 shrink-0 text-[var(--hello-text-muted)]" />
          <input
            value={addressInput}
            onChange={(event) => setAddressInput(event.target.value)}
            className="min-w-0 flex-1 bg-transparent text-sm text-[var(--hello-text)] placeholder:text-[var(--hello-text-muted)]"
            placeholder="Search or enter address"
          />
          {busy ? <Loader2 className="h-4 w-4 animate-spin text-[var(--hello-accent)]" /> : null}
        </form>

        <select
          value={activeProfileId}
          onChange={(event) => void switchProfile(event.target.value)}
          className="h-9 max-w-[190px] rounded-lg border border-[var(--hello-border)] bg-[var(--hello-panel-muted)] px-2 text-xs font-semibold text-[var(--hello-text)]"
          title={activeProfile?.email || activeProfile?.name || "Profile"}
        >
          {profiles.map((profile) => (
            <option key={profile.id} value={profile.id}>
              {profile.email || profile.name}
            </option>
          ))}
        </select>

        <IconButton label="Create Profile" onClick={() => setProfileFormOpen((open) => !open)} active={profileFormOpen}>
          <UserPlus className="h-4 w-4" />
        </IconButton>
        <IconButton label="Detect Email" onClick={() => void detectProfileEmail()} disabled={!activeProfileId || busy}>
          <ShieldCheck className="h-4 w-4" />
        </IconButton>
      </div>

      {profileFormOpen ? (
        <div className="grid shrink-0 gap-2 border-b border-[var(--hello-border)] bg-[var(--hello-panel)] p-3 md:grid-cols-[1fr_1fr_1.2fr_auto]">
          <input
            value={profileName}
            onChange={(event) => setProfileName(event.target.value)}
            placeholder="Profile name"
            className="hello-input px-3 py-2 text-sm"
          />
          <input
            value={profileEmail}
            onChange={(event) => setProfileEmail(event.target.value)}
            placeholder="Email"
            className="hello-input px-3 py-2 text-sm"
          />
          <input
            value={profileStartUrl}
            onChange={(event) => setProfileStartUrl(event.target.value)}
            placeholder="Start URL"
            className="hello-input px-3 py-2 text-sm"
          />
          <button
            type="button"
            onClick={() => void createProfile()}
            disabled={busy}
            className="inline-flex items-center justify-center gap-2 rounded-lg bg-[var(--hello-accent)] px-3 py-2 text-xs font-semibold text-white disabled:opacity-60"
          >
            <Plus className="h-4 w-4" />
            Create
          </button>
        </div>
      ) : null}

      <div className="flex min-h-0 flex-1 flex-col md:flex-row">
        <aside className="flex max-h-40 shrink-0 flex-col border-b border-[var(--hello-border)] bg-[var(--hello-panel)] md:h-full md:max-h-none md:w-[280px] md:border-b-0 md:border-r">
          <div className="flex items-center justify-between px-3 py-2">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wide text-[var(--hello-text-muted)]">
              <Globe2 className="h-4 w-4 text-[var(--hello-accent)]" />
              Tabs
            </div>
            <IconButton label="New Tab" onClick={() => void createTab()} disabled={busy}>
              <Plus className="h-4 w-4" />
            </IconButton>
          </div>
          <div className="custom-scrollbar flex min-h-0 flex-1 gap-1 overflow-auto px-2 pb-2 md:flex-col">
            {tabs.map((tab) => (
              <button
                key={tab.tabId}
                type="button"
                onClick={() => void focusTab(tab.tabId)}
                className={cn(
                  "group flex min-w-[220px] items-center gap-2 rounded-lg border px-2 py-2 text-left transition md:min-w-0",
                  tab.tabId === activeTabId
                    ? "border-[var(--hello-accent)] bg-[var(--hello-accent-soft)]"
                    : "border-transparent hover:border-[var(--hello-border)] hover:bg-[var(--hello-panel-muted)]",
                )}
              >
                <Globe2 className="h-4 w-4 shrink-0 text-[var(--hello-text-muted)]" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-xs font-semibold text-[var(--hello-text)]">{tab.title || "New Tab"}</span>
                  <span className="block truncate text-[10px] text-[var(--hello-text-muted)]">{tab.url}</span>
                </span>
                <span
                  role="button"
                  tabIndex={0}
                  onClick={(event) => {
                    event.stopPropagation();
                    void closeTab(tab.tabId);
                  }}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      event.stopPropagation();
                      void closeTab(tab.tabId);
                    }
                  }}
                  className="rounded-md p-1 text-[var(--hello-text-muted)] opacity-70 hover:bg-[var(--hello-panel-strong)] hover:text-[var(--hello-danger)] group-hover:opacity-100"
                  title="Close tab"
                  aria-label="Close tab"
                >
                  <X className="h-3.5 w-3.5" />
                </span>
              </button>
            ))}
          </div>
        </aside>

        <main className="flex min-h-0 min-w-0 flex-1 flex-col">
          <div
            ref={liveViewRef}
            className="relative min-h-[320px] flex-[1.4] overflow-hidden bg-black md:min-h-0"
          >
            {!activeTabId ? (
              <div className="absolute inset-0 flex items-center justify-center text-sm text-white/60">
                No active tab
              </div>
            ) : null}
          </div>

          <section className="flex min-h-[260px] flex-[1] flex-col border-t border-[var(--hello-border)] bg-[var(--hello-panel-strong)]">
            <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[var(--hello-border)] px-3 py-2">
              {([
                ["api", TerminalSquare, "API"],
                ["history", History, "History"],
                ["downloads", Download, "Downloads"],
                ["passwords", KeyRound, "Passwords"],
                ["dom", Code2, "DOM"],
              ] as const).map(([id, Icon, label]) => (
                <button
                  key={id}
                  type="button"
                  onClick={() => setActivePanel(id)}
                  className={cn(
                    "inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-semibold transition",
                    activePanel === id
                      ? "bg-[var(--hello-accent)] text-white"
                      : "text-[var(--hello-text-muted)] hover:bg-[var(--hello-accent-soft)] hover:text-[var(--hello-text)]",
                  )}
                >
                  <Icon className="h-3.5 w-3.5" />
                  {label}
                </button>
              ))}
              <div className="min-w-0 flex-1" />
              {statusText ? <span className="max-w-[320px] truncate text-xs text-[var(--hello-warning)]">{statusText}</span> : null}
              {activePanel !== "api" && activePanel !== "passwords" ? (
                <input
                  value={panelSearch}
                  onChange={(event) => setPanelSearch(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") void refreshLocalPanels(activeProfileId, activeTabId);
                  }}
                  placeholder="Filter"
                  className="hello-input h-8 w-full max-w-[220px] px-3 text-xs"
                />
              ) : null}
            </div>

            <div className="custom-scrollbar min-h-0 flex-1 overflow-auto p-3">
              {activePanel === "history" ? (
                <div className="grid gap-2 lg:grid-cols-2">
                  {history.map((item, index) => (
                    <button
                      key={`${item.url}-${index}`}
                      type="button"
                      onClick={() => {
                        setAddressInput(item.url || "");
                        void navigate(undefined, item.url || "");
                      }}
                      className="rounded-lg border border-[var(--hello-border)] bg-[var(--hello-panel-muted)] p-3 text-left hover:border-[var(--hello-border-strong)]"
                    >
                      <div className="truncate text-xs font-semibold text-[var(--hello-text)]">{item.title || item.url}</div>
                      <div className="mt-1 truncate text-[10px] text-[var(--hello-text-muted)]">{item.url}</div>
                      <div className="mt-2 text-[10px] text-[var(--hello-text-muted)]">{formatTimestamp(getTimestamp(item))}</div>
                    </button>
                  ))}
                  {history.length === 0 ? <EmptyBrowserPanel text="No history for this profile." /> : null}
                </div>
              ) : null}

              {activePanel === "downloads" ? (
                <div className="grid gap-2 lg:grid-cols-2">
                  {downloads.map((item, index) => (
                    <div key={`${getFilename(item)}-${index}`} className="rounded-lg border border-[var(--hello-border)] bg-[var(--hello-panel-muted)] p-3">
                      <div className="truncate text-xs font-semibold text-[var(--hello-text)]">{getFilename(item)}</div>
                      <div className="mt-1 truncate text-[10px] text-[var(--hello-text-muted)]">{item.url || item.path}</div>
                      <div className="mt-2 text-[10px] text-[var(--hello-text-muted)]">{formatTimestamp(getTimestamp(item))}</div>
                    </div>
                  ))}
                  {downloads.length === 0 ? <EmptyBrowserPanel text="No downloads for this profile." /> : null}
                </div>
              ) : null}

              {activePanel === "passwords" ? (
                <div className="space-y-3">
                  <div className="grid gap-2 md:grid-cols-[1fr_1fr_1fr_auto]">
                    <input
                      value={passwordForm.origin}
                      onChange={(event) => setPasswordForm((current) => ({ ...current, origin: event.target.value }))}
                      placeholder="Origin"
                      className="hello-input px-3 py-2 text-xs"
                    />
                    <input
                      value={passwordForm.username}
                      onChange={(event) => setPasswordForm((current) => ({ ...current, username: event.target.value }))}
                      placeholder="Username"
                      className="hello-input px-3 py-2 text-xs"
                    />
                    <input
                      value={passwordForm.password}
                      onChange={(event) => setPasswordForm((current) => ({ ...current, password: event.target.value }))}
                      placeholder="Password"
                      type="password"
                      className="hello-input px-3 py-2 text-xs"
                    />
                    <button
                      type="button"
                      onClick={() => void savePassword()}
                      className="inline-flex items-center justify-center gap-2 rounded-lg bg-[var(--hello-accent)] px-3 py-2 text-xs font-semibold text-white"
                    >
                      <KeyRound className="h-3.5 w-3.5" />
                      Save
                    </button>
                  </div>
                  <div className="grid gap-2 lg:grid-cols-2">
                    {passwords.map((entry) => (
                      <div key={entry.id} className="flex items-center gap-3 rounded-lg border border-[var(--hello-border)] bg-[var(--hello-panel-muted)] p-3">
                        <div className="min-w-0 flex-1">
                          <div className="truncate text-xs font-semibold text-[var(--hello-text)]">{entry.origin}</div>
                          <div className="truncate text-[10px] text-[var(--hello-text-muted)]">{entry.username}</div>
                        </div>
                        <button
                          type="button"
                          onClick={() => void deletePassword(entry.id)}
                          className="rounded-lg p-2 text-[var(--hello-danger)] hover:bg-[var(--hello-panel-strong)]"
                          title="Remove"
                          aria-label="Remove"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    ))}
                    {passwords.length === 0 ? <EmptyBrowserPanel text="No saved passwords for this profile." /> : null}
                  </div>
                </div>
              ) : null}

              {activePanel === "dom" ? (
                <div className="grid gap-2 xl:grid-cols-2">
                  {domSnapshot.map((node, index) => (
                    <div key={`${node.selector || node.tag}-${index}`} className="rounded-lg border border-[var(--hello-border)] bg-[var(--hello-panel-muted)] p-3">
                      <div className="flex items-center justify-between gap-3">
                        <div className="truncate text-xs font-semibold text-[var(--hello-text)]">{node.text || node.aria || node.placeholder || node.selector || node.tag}</div>
                        <span className="shrink-0 rounded bg-[var(--hello-accent-soft)] px-2 py-0.5 text-[10px] font-semibold text-[var(--hello-accent)]">{node.tag}</span>
                      </div>
                      <div className="mt-1 truncate font-mono text-[10px] text-[var(--hello-text-muted)]">{node.selector}</div>
                    </div>
                  ))}
                  {domSnapshot.length === 0 ? <EmptyBrowserPanel text="No DOM snapshot captured yet." /> : null}
                </div>
              ) : null}

              {activePanel === "api" ? (
                <div className="grid min-h-full gap-3 xl:grid-cols-[360px_1fr]">
                  <div className="space-y-3">
                    <div className="grid grid-cols-2 gap-2">
                      {([
                        ["parse", FileCode2, "Parse"],
                        ["query", Search, "Query"],
                        ["request", Bot, "Request"],
                        ["html", Code2, "HTML"],
                        ["screenshot", LayoutPanelTop, "Shot"],
                        ["a11y", ShieldCheck, "A11y"],
                        ["targets", MousePointerClick, "Targets"],
                        ["action", TerminalSquare, "Action"],
                      ] as const).map(([id, Icon, label]) => (
                        <button
                          key={id}
                          type="button"
                          onClick={() => setApiMode(id)}
                          className={cn(
                            "inline-flex items-center justify-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold",
                            apiMode === id
                              ? "border-[var(--hello-accent)] bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]"
                              : "border-[var(--hello-border)] text-[var(--hello-text-muted)] hover:text-[var(--hello-text)]",
                          )}
                        >
                          <Icon className="h-3.5 w-3.5" />
                          {label}
                        </button>
                      ))}
                    </div>

                    {apiMode === "query" ? (
                      <input
                        value={selectorInput}
                        onChange={(event) => setSelectorInput(event.target.value)}
                        placeholder="CSS selector"
                        className="hello-input w-full px-3 py-2 text-xs"
                      />
                    ) : null}

                    {apiMode === "request" ? (
                      <div className="space-y-2">
                        <div className="grid grid-cols-[90px_1fr] gap-2">
                          <select
                            value={requestMethod}
                            onChange={(event) => setRequestMethod(event.target.value)}
                            className="hello-input px-2 py-2 text-xs"
                          >
                            {["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"].map((method) => (
                              <option key={method} value={method}>{method}</option>
                            ))}
                          </select>
                          <input
                            value={requestUrl}
                            onChange={(event) => setRequestUrl(event.target.value)}
                            placeholder="Request URL"
                            className="hello-input px-3 py-2 text-xs"
                          />
                        </div>
                        <textarea
                          value={requestHeaders}
                          onChange={(event) => setRequestHeaders(event.target.value)}
                          rows={3}
                          className="hello-input w-full resize-none px-3 py-2 font-mono text-xs"
                        />
                        <textarea
                          value={requestBody}
                          onChange={(event) => setRequestBody(event.target.value)}
                          rows={4}
                          placeholder="Body"
                          className="hello-input w-full resize-none px-3 py-2 font-mono text-xs"
                        />
                      </div>
                    ) : null}

                    {apiMode === "action" ? (
                      <div className="space-y-2">
                        <select
                          value={actionMode}
                          onChange={(event) => setActionMode(event.target.value as ActionMode)}
                          className="hello-input w-full px-2 py-2 text-xs"
                        >
                          {["click", "type", "scroll", "wait", "evaluate"].map((mode) => (
                            <option key={mode} value={mode}>{mode}</option>
                          ))}
                        </select>
                        {actionMode !== "evaluate" && actionMode !== "scroll" ? (
                          <input
                            value={actionSelector}
                            onChange={(event) => setActionSelector(event.target.value)}
                            placeholder="Selector"
                            className="hello-input w-full px-3 py-2 text-xs"
                          />
                        ) : null}
                        <textarea
                          value={actionText}
                          onChange={(event) => setActionText(event.target.value)}
                          rows={4}
                          placeholder={actionMode === "evaluate" ? "JavaScript" : actionMode === "scroll" ? "down, up, left, right" : "Text"}
                          className="hello-input w-full resize-none px-3 py-2 font-mono text-xs"
                        />
                      </div>
                    ) : null}

                    <button
                      type="button"
                      onClick={() => void runApiTool()}
                      disabled={!activeTabId || busy}
                      className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-[var(--hello-accent)] px-3 py-2 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <TerminalSquare className="h-4 w-4" />}
                      Run
                    </button>

                    {actionTargets.length > 0 ? (
                      <div className="max-h-52 space-y-1 overflow-auto rounded-lg border border-[var(--hello-border)] p-2">
                        {actionTargets.slice(0, 16).map((target) => (
                          <button
                            key={target.targetId || target.selector}
                            type="button"
                            onClick={() => {
                              setApiMode("action");
                              setActionMode(target.kind === "input" ? "type" : "click");
                              setActionSelector(target.selector || "");
                            }}
                            className="block w-full truncate rounded-md px-2 py-1.5 text-left text-[11px] text-[var(--hello-text-muted)] hover:bg-[var(--hello-accent-soft)] hover:text-[var(--hello-text)]"
                          >
                            {target.label || target.selector}
                          </button>
                        ))}
                      </div>
                    ) : null}
                  </div>

                  <pre className="custom-scrollbar min-h-[260px] overflow-auto rounded-lg border border-[var(--hello-border)] bg-[#0b1020] p-3 text-xs leading-5 text-slate-100">
                    {JSON.stringify(apiOutput || { tabId: activeTabId, profileId: activeProfileId }, null, 2)}
                  </pre>
                </div>
              ) : null}
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}

function EmptyBrowserPanel({ text }: { text: string }) {
  return (
    <div className="flex min-h-24 items-center justify-center rounded-lg border border-dashed border-[var(--hello-border)] px-4 py-6 text-center text-xs text-[var(--hello-text-muted)]">
      {text}
    </div>
  );
}
