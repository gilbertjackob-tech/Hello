import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type ReactNode, type UIEvent } from "react";
import {
  ArrowLeft,
  Check,
  ChevronLeft,
  ChevronRight,
  Cloud,
  CloudUpload,
  Download,
  Heart,
  Image as ImageIcon,
  Lock,
  Play,
  RefreshCw,
  RotateCcw,
  Trash2,
  X,
} from "lucide-react";
import {
  DRIVE_API_BASE,
  checkDriveHealth,
  deleteDriveItem,
  fetchDriveDeleteLimit,
  fetchDriveItems,
  fetchDriveTrash,
  permanentlyDeleteDriveItem,
  restoreDriveItem,
  uploadDriveFiles,
} from "../api";
import { DriveDeleteLimit, DriveItem, User } from "../types";
import { cn } from "../lib/utils";
import { EmptyState, SkeletonBlock } from "./HelloUi";

type DriveView = "home" | "all" | "trash";
type ToastKind = "info" | "success" | "error";
type PendingAction =
  | { type: "trash"; items: DriveItem[] }
  | { type: "restore"; items: DriveItem[] }
  | { type: "permanent"; items: DriveItem[] };

interface FamilyDrivePaneProps {
  currentUser: User;
  visible: boolean;
}

export function FamilyDrivePane({ currentUser, visible }: FamilyDrivePaneProps) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [view, setView] = useState<DriveView>("home");
  const [items, setItems] = useState<DriveItem[]>([]);
  const [trashItems, setTrashItems] = useState<DriveItem[]>([]);
  const [total, setTotal] = useState(0);
  const [trashTotal, setTrashTotal] = useState(0);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [trashNextCursor, setTrashNextCursor] = useState<number | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [trashHasMore, setTrashHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [trashLoading, setTrashLoading] = useState(false);
  const [trashLoadingMore, setTrashLoadingMore] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState("");
  const [driveOnline, setDriveOnline] = useState<boolean | null>(null);
  const [toast, setToast] = useState<{ kind: ToastKind; message: string } | null>(null);
  const [viewerIndex, setViewerIndex] = useState<number | null>(null);
  const [viewerMode, setViewerMode] = useState<"all" | "trash">("all");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [deleteLimit, setDeleteLimit] = useState<DriveDeleteLimit | null>(null);
  const [busyAction, setBusyAction] = useState(false);
  const [isDraggingUpload, setIsDraggingUpload] = useState(false);
  const [dragSelecting, setDragSelecting] = useState(false);
  const favoritesKey = `hello_drive_favorites_${currentUser.id}`;
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(() => {
    try {
      return new Set(JSON.parse(localStorage.getItem(favoritesKey) || "[]"));
    } catch {
      return new Set();
    }
  });

  const activeItems = view === "trash" ? trashItems : items;
  const viewerItems = viewerMode === "trash" ? trashItems : items;
  const viewerItem = viewerIndex === null ? null : viewerItems[viewerIndex] || null;
  const selectedItems = activeItems.filter((item) => selectedIds.has(item.id));
  const selectionMode = selectedIds.size > 0;

  useEffect(() => {
    try {
      setFavoriteIds(new Set(JSON.parse(localStorage.getItem(favoritesKey) || "[]")));
    } catch {
      setFavoriteIds(new Set());
    }
  }, [favoritesKey]);

  useEffect(() => {
    localStorage.setItem(favoritesKey, JSON.stringify(Array.from(favoriteIds)));
  }, [favoriteIds, favoritesKey]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), toast.kind === "error" ? 7000 : 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (!dragSelecting) return;
    const stop = () => setDragSelecting(false);
    window.addEventListener("pointerup", stop);
    return () => window.removeEventListener("pointerup", stop);
  }, [dragSelecting]);

  useEffect(() => {
    if (viewerIndex === null) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setViewerIndex(null);
      if (event.key === "ArrowLeft") setViewerIndex((current) => (current === null ? current : Math.max(0, current - 1)));
      if (event.key === "ArrowRight") {
        setViewerIndex((current) => (current === null ? current : Math.min(viewerItems.length - 1, current + 1)));
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [viewerIndex, viewerItems.length]);

  const offlineUploadMessage = "PC Drive is offline. Drive upload is unavailable while the PC connection is offline.";

  const refreshDriveHealth = useCallback(async () => {
    try {
      const health = await checkDriveHealth();
      setDriveOnline(Boolean(health.ok));
    } catch {
      setDriveOnline(false);
    }
  }, []);

  const loadDeleteLimit = useCallback(async () => {
    try {
      const limit = await fetchDriveDeleteLimit(currentUser.id);
      setDeleteLimit(limit || null);
    } catch {
      setDeleteLimit(null);
    }
  }, [currentUser.id]);

  const loadItems = useCallback(async (mode: "refresh" | "more" = "refresh") => {
    if (mode === "more") {
      if (!hasMore || !nextCursor || loadingMore) return;
      setLoadingMore(true);
    } else {
      setLoading(true);
    }

    try {
      const response = await fetchDriveItems(60, mode === "more" ? nextCursor : null, mode === "refresh");
      if (mode === "refresh") void loadDeleteLimit();
      setDriveOnline(true);
      setTotal(response.total);
      setNextCursor(response.nextCursor);
      setHasMore(response.hasMore);
      setItems((current) => {
        const nextItems = mode === "more" ? [...current, ...response.items] : response.items;
        return Array.from(new Map(nextItems.map((item) => [item.id, item])).values());
      });
    } catch (err) {
      setDriveOnline(false);
      setToast({ kind: "error", message: err instanceof Error ? err.message : "Drive could not load" });
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [hasMore, loadDeleteLimit, loadingMore, nextCursor]);

  const loadTrash = useCallback(async (mode: "refresh" | "more" = "refresh") => {
    if (mode === "more") {
      if (!trashHasMore || !trashNextCursor || trashLoadingMore) return;
      setTrashLoadingMore(true);
    } else {
      setTrashLoading(true);
    }

    try {
      const response = await fetchDriveTrash(60, mode === "more" ? trashNextCursor : null, mode === "refresh");
      setDriveOnline(true);
      setTrashTotal(response.total);
      setTrashNextCursor(response.nextCursor);
      setTrashHasMore(response.hasMore);
      setTrashItems((current) => {
        const nextItems = mode === "more" ? [...current, ...response.items] : response.items;
        return Array.from(new Map(nextItems.map((item) => [item.id, item])).values());
      });
    } catch (err) {
      setDriveOnline(false);
      setToast({ kind: "error", message: err instanceof Error ? err.message : "Trash could not load" });
    } finally {
      setTrashLoading(false);
      setTrashLoadingMore(false);
    }
  }, [trashHasMore, trashLoadingMore, trashNextCursor]);

  useEffect(() => {
    if (visible && items.length === 0 && !loading) {
      void refreshDriveHealth();
      void loadItems("refresh");
    }
  }, [items.length, loadItems, loading, refreshDriveHealth, visible]);

  useEffect(() => {
    setSelectedIds(new Set());
    setViewerIndex(null);
    if (view === "trash" && trashItems.length === 0 && !trashLoading) {
      void loadTrash("refresh");
    }
  }, [loadTrash, trashItems.length, trashLoading, view]);

  const groups = useMemo(() => groupByMonth(items), [items]);
  const trashGroups = useMemo(() => groupByMonth(trashItems), [trashItems]);

  const openUploadPicker = useCallback(() => {
    if (driveOnline === false) {
      setToast({ kind: "error", message: offlineUploadMessage });
      return;
    }
    fileInputRef.current?.click();
  }, [driveOnline]);

  const uploadFiles = async (files: File[]) => {
    const mediaFiles = files.filter((file) => file.type.startsWith("image/") || file.type.startsWith("video/"));
    if (!mediaFiles.length || uploading) return;
    if (driveOnline === false) {
      setToast({ kind: "error", message: offlineUploadMessage });
      return;
    }

    setUploading(true);
    setToast({ kind: "info", message: `Waiting to upload ${mediaFiles.length} item${mediaFiles.length === 1 ? "" : "s"}...` });
    try {
      for (const [index, file] of mediaFiles.entries()) {
        setUploadProgress(`Uploading ${index + 1} / ${mediaFiles.length}...`);
        await uploadDriveFiles([file], currentUser.id);
      }
      setDriveOnline(true);
      setUploadProgress("Upload complete");
      setToast({ kind: "success", message: `${mediaFiles.length} item${mediaFiles.length === 1 ? "" : "s"} uploaded to Family Drive.` });
      setView("all");
      await loadItems("refresh");
    } catch {
      setDriveOnline(false);
      setToast({ kind: "error", message: offlineUploadMessage });
    } finally {
      window.setTimeout(() => {
        setUploading(false);
        setUploadProgress("");
      }, 700);
    }
  };

  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []);
    event.target.value = "";
    void uploadFiles(files);
  };

  const handleScroll = (event: UIEvent<HTMLDivElement>) => {
    const target = event.currentTarget;
    const distanceFromBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
    if (distanceFromBottom < 420) {
      if (view === "trash") void loadTrash("more");
      else void loadItems("more");
    }
  };

  const toggleFavorite = (itemId: string) => {
    setFavoriteIds((current) => {
      const next = new Set(current);
      if (next.has(itemId)) next.delete(itemId);
      else next.add(itemId);
      return next;
    });
  };

  const toggleSelected = (itemId: string, forceSelected?: boolean) => {
    setSelectedIds((current) => {
      const next = new Set(current);
      const shouldSelect = forceSelected ?? !next.has(itemId);
      if (shouldSelect) next.add(itemId);
      else next.delete(itemId);
      return next;
    });
  };

  const selectAllVisible = () => setSelectedIds(new Set(activeItems.map((item) => item.id)));
  const selectMonth = (monthItems: DriveItem[]) => {
    setSelectedIds((current) => {
      const next = new Set(current);
      const allSelected = monthItems.every((item) => next.has(item.id));
      monthItems.forEach((item) => {
        if (allSelected) next.delete(item.id);
        else next.add(item.id);
      });
      return next;
    });
  };

  const openViewer = (item: DriveItem, mode: "all" | "trash") => {
    const source = mode === "trash" ? trashItems : items;
    const index = source.findIndex((candidate) => candidate.id === item.id);
    setViewerMode(mode);
    setViewerIndex(index >= 0 ? index : 0);
  };

  const runPendingAction = async () => {
    if (!pendingAction || busyAction) return;
    setBusyAction(true);
    try {
      if (pendingAction.type === "trash") {
        let latestLimit: DriveDeleteLimit | null = null;
        for (const item of pendingAction.items) {
          latestLimit = (await deleteDriveItem(item.id, currentUser.id)).deleteLimit || latestLimit;
        }
        setDeleteLimit(latestLimit);
        const trashedIds = new Set(pendingAction.items.map((item) => item.id));
        setItems((current) => current.filter((item) => !trashedIds.has(item.id)));
        setTotal((current) => Math.max(0, current - trashedIds.size));
        setFavoriteIds((current) => new Set(Array.from(current).filter((id) => !trashedIds.has(id))));
        setToast({ kind: "success", message: `${pendingAction.items.length} item${pendingAction.items.length === 1 ? "" : "s"} moved to Trash.` });
        void loadTrash("refresh");
      } else if (pendingAction.type === "restore") {
        const restored: DriveItem[] = [];
        for (const item of pendingAction.items) {
          restored.push(await restoreDriveItem(item.id));
        }
        const restoredIds = new Set(restored.map((item) => item.id));
        setTrashItems((current) => current.filter((item) => !restoredIds.has(item.id)));
        setItems((current) => Array.from(new Map([...restored, ...current].map((item) => [item.id, item])).values()));
        setTrashTotal((current) => Math.max(0, current - restoredIds.size));
        setTotal((current) => current + restoredIds.size);
        setToast({ kind: "success", message: `${restored.length} item${restored.length === 1 ? "" : "s"} restored.` });
      } else {
        for (const item of pendingAction.items) {
          await permanentlyDeleteDriveItem(item.id);
        }
        const deletedIds = new Set(pendingAction.items.map((item) => item.id));
        setTrashItems((current) => current.filter((item) => !deletedIds.has(item.id)));
        setTrashTotal((current) => Math.max(0, current - deletedIds.size));
        setToast({ kind: "success", message: `${pendingAction.items.length} item${pendingAction.items.length === 1 ? "" : "s"} permanently deleted.` });
      }
      setSelectedIds(new Set());
      setViewerIndex(null);
      setPendingAction(null);
    } catch (err) {
      setToast({ kind: "error", message: err instanceof Error ? err.message : "Drive action failed" });
    } finally {
      setBusyAction(false);
    }
  };

  return (
    <div
      className="relative flex h-full flex-col overflow-hidden bg-slate-50 text-slate-900 dark:bg-[#071219] dark:text-[#e9edef]"
      onDragOver={(event) => {
        event.preventDefault();
        if (view !== "trash") setIsDraggingUpload(true);
      }}
      onDragLeave={() => setIsDraggingUpload(false)}
      onDrop={(event) => {
        event.preventDefault();
        setIsDraggingUpload(false);
        if (view !== "trash") void uploadFiles(Array.from(event.dataTransfer.files || []));
      }}
    >
      <input ref={fileInputRef} type="file" accept="image/*,video/*" multiple className="hidden" onChange={handleInputChange} />

      {view === "home" ? (
        <DriveHome
          total={total}
          trashTotal={trashTotal}
          loading={loading}
          onUpload={openUploadPicker}
          onOpenAll={() => setView("all")}
          onOpenTrash={() => setView("trash")}
          driveOnline={driveOnline}
        />
      ) : (
        <DriveLibrary
          view={view}
          groups={view === "trash" ? trashGroups : groups}
          total={view === "trash" ? trashTotal : total}
          loading={view === "trash" ? trashLoading : loading}
          loadingMore={view === "trash" ? trashLoadingMore : loadingMore}
          selectedIds={selectedIds}
          onBack={() => setView("home")}
          onRefresh={() => (view === "trash" ? loadTrash("refresh") : loadItems("refresh"))}
          onScroll={handleScroll}
          onOpenItem={(item) => openViewer(item, view === "trash" ? "trash" : "all")}
          onUpload={openUploadPicker}
          favoriteIds={favoriteIds}
          onToggleFavorite={toggleFavorite}
          driveOnline={driveOnline}
          onToggleSelected={toggleSelected}
          onSelectMonth={selectMonth}
          onStartDragSelect={(itemId) => {
            setDragSelecting(true);
            toggleSelected(itemId, true);
          }}
          onDragSelect={(itemId) => {
            if (dragSelecting) toggleSelected(itemId, true);
          }}
        />
      )}

      {selectionMode ? (
        <SelectionBar
          count={selectedIds.size}
          total={activeItems.length}
          trashMode={view === "trash"}
          onSelectAll={selectAllVisible}
          onClear={() => setSelectedIds(new Set())}
          onTrash={() => setPendingAction({ type: "trash", items: selectedItems })}
          onRestore={() => setPendingAction({ type: "restore", items: selectedItems })}
          onPermanentDelete={() => setPendingAction({ type: "permanent", items: selectedItems })}
        />
      ) : null}

      <div
        className={cn(
          "pointer-events-none absolute inset-4 z-20 rounded-[28px] border-2 border-dashed border-[var(--hello-accent)] bg-[var(--hello-accent-soft)] opacity-0 transition",
          isDraggingUpload && "opacity-100",
        )}
      />

      {uploading ? <UploadToast text={uploadProgress} /> : null}
      {toast ? <ToastBanner kind={toast.kind} message={toast.message} onClose={() => setToast(null)} /> : null}
      {viewerItem ? (
        <DriveViewer
          item={viewerItem}
          favorite={favoriteIds.has(viewerItem.id)}
          trashMode={viewerMode === "trash"}
          hasPrevious={Boolean(viewerIndex && viewerIndex > 0)}
          hasNext={viewerIndex !== null && viewerIndex < viewerItems.length - 1}
          onClose={() => setViewerIndex(null)}
          onPrevious={() => setViewerIndex((current) => (current === null ? current : Math.max(0, current - 1)))}
          onNext={() => setViewerIndex((current) => (current === null ? current : Math.min(viewerItems.length - 1, current + 1)))}
          onToggleFavorite={() => toggleFavorite(viewerItem.id)}
          onRequestDelete={() => setPendingAction({ type: "trash", items: [viewerItem] })}
          onRequestRestore={() => setPendingAction({ type: "restore", items: [viewerItem] })}
          onRequestPermanentDelete={() => setPendingAction({ type: "permanent", items: [viewerItem] })}
        />
      ) : null}
      {pendingAction ? (
        <DriveActionDialog action={pendingAction} deleteLimit={deleteLimit} busy={busyAction} onCancel={() => setPendingAction(null)} onConfirm={runPendingAction} />
      ) : null}
    </div>
  );
}

function DriveHome({
  total,
  trashTotal,
  loading,
  onUpload,
  onOpenAll,
  onOpenTrash,
  driveOnline,
}: {
  total: number;
  trashTotal: number;
  loading: boolean;
  onUpload: () => void;
  onOpenAll: () => void;
  onOpenTrash: () => void;
  driveOnline: boolean | null;
}) {
  return (
    <div className="relative z-20 flex h-full flex-col px-5 py-6">
      <p className="text-xs font-extrabold uppercase tracking-wide text-[var(--hello-accent)]">Hello Drive</p>
      <h1 className="mt-2 text-3xl font-black tracking-tight text-[var(--hello-text)]">Family Drive</h1>
      <p className="mt-3 text-sm leading-6 text-[var(--hello-text-muted)]">All our family memories in one place.</p>
      <p
        className={cn(
          "mt-3 rounded-[14px] px-3 py-2 text-xs font-bold",
          driveOnline === false ? "bg-red-500/10 text-[var(--hello-danger)]" : "bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]",
        )}
      >
        {driveOnline === false ? "PC Drive is offline. Upload waits until the PC connection is back." : "PC Drive is online through Cloudflare Tunnel."}
      </p>

      <button
        type="button"
        onClick={onUpload}
        disabled={driveOnline === false}
        className="mt-7 flex min-h-[52px] w-full items-center justify-center gap-3 rounded-[18px] bg-[var(--hello-accent)] px-4 py-4 text-sm font-extrabold text-white shadow-[0_18px_38px_rgba(15,143,120,0.28)] transition hover:bg-[var(--hello-accent-strong)] focus:outline-none focus:ring-2 focus:ring-[var(--hello-accent)] focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
      >
        <CloudUpload className="h-5 w-5" aria-hidden />
        Upload Photos
      </button>

      <DriveHomeCard icon={<ImageIcon className="h-6 w-6" />} title="All Photos & Videos" subtitle={loading ? "Loading..." : `${total.toLocaleString()} items`} onClick={onOpenAll} />
      <DriveHomeCard icon={<Trash2 className="h-6 w-6" />} title="Trash" subtitle={`${trashTotal.toLocaleString()} recoverable items`} onClick={onOpenTrash} />

      <div className="mt-auto flex items-start justify-center gap-3 pb-8 text-[var(--hello-text-muted)]">
        <Lock className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <p className="text-xs leading-5">Shared trash protects accidental deletes. Daily delete limit: 20 items.</p>
      </div>
    </div>
  );
}

function DriveHomeCard({ icon, title, subtitle, onClick }: { icon: ReactNode; title: string; subtitle: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="hello-card mt-5 flex min-h-[76px] w-full items-center gap-4 p-4 text-left transition hover:translate-y-[-1px] focus:outline-none focus:ring-2 focus:ring-[var(--hello-accent)]">
      <div className="flex h-12 w-12 items-center justify-center rounded-[16px] bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]">{icon}</div>
      <div className="min-w-0 flex-1">
        <h2 className="text-sm font-extrabold text-[var(--hello-text)]">{title}</h2>
        <p className="mt-1 text-xs text-[var(--hello-text-muted)]">{subtitle}</p>
      </div>
      <ChevronRight className="h-5 w-5 text-[var(--hello-text-muted)]" aria-hidden />
    </button>
  );
}

function DriveLibrary({
  view,
  groups,
  total,
  loading,
  loadingMore,
  selectedIds,
  onBack,
  onRefresh,
  onScroll,
  onOpenItem,
  onUpload,
  favoriteIds,
  onToggleFavorite,
  driveOnline,
  onToggleSelected,
  onSelectMonth,
  onStartDragSelect,
  onDragSelect,
}: {
  view: DriveView;
  groups: Record<string, DriveItem[]>;
  total: number;
  loading: boolean;
  loadingMore: boolean;
  selectedIds: Set<string>;
  onBack: () => void;
  onRefresh: () => void;
  onScroll: (event: UIEvent<HTMLDivElement>) => void;
  onOpenItem: (item: DriveItem) => void;
  onUpload: () => void;
  favoriteIds: Set<string>;
  onToggleFavorite: (itemId: string) => void;
  driveOnline: boolean | null;
  onToggleSelected: (itemId: string, forceSelected?: boolean) => void;
  onSelectMonth: (items: DriveItem[]) => void;
  onStartDragSelect: (itemId: string) => void;
  onDragSelect: (itemId: string) => void;
}) {
  const hasItems = Object.keys(groups).length > 0;
  const selectionMode = selectedIds.size > 0;
  const trashMode = view === "trash";

  return (
    <div className="relative z-20 flex h-full flex-col">
      <div className="flex h-16 flex-none items-center gap-2 border-b border-[var(--hello-border)] px-3">
        <button type="button" onClick={onBack} className="flex h-11 w-11 items-center justify-center rounded-full transition hover:bg-black/5 focus:outline-none focus:ring-2 focus:ring-[var(--hello-accent)] dark:hover:bg-white/5" title="Back" aria-label="Back">
          <ArrowLeft className="h-5 w-5" />
        </button>
        <div className="min-w-0 flex-1 text-center">
          <h1 className="truncate text-sm font-extrabold text-[var(--hello-text)]">{trashMode ? "Trash" : "All Photos & Videos"}</h1>
          <p className="text-xs text-[var(--hello-text-muted)]">{total.toLocaleString()} items</p>
        </div>
        <button type="button" onClick={onRefresh} className="flex h-11 w-11 items-center justify-center rounded-full transition hover:bg-black/5 focus:outline-none focus:ring-2 focus:ring-[var(--hello-accent)] dark:hover:bg-white/5" title="Refresh" aria-label="Refresh">
          <RefreshCw className="h-4 w-4" />
        </button>
      </div>

      <div className="flex items-center justify-between px-5 py-3 text-xs text-[var(--hello-text-muted)]">
        <span>{trashMode ? "Shared trash - restore accidental deletes" : driveOnline === false ? "PC Drive offline - uploads unavailable" : "Grouped by month"}</span>
        {!trashMode ? (
          <button type="button" onClick={onUpload} disabled={driveOnline === false} className="min-h-11 rounded-full px-3 font-bold text-[var(--hello-accent)] focus:outline-none focus:ring-2 focus:ring-[var(--hello-accent)] disabled:cursor-not-allowed disabled:opacity-50">
            Upload
          </button>
        ) : null}
      </div>

      <div className="flex-1 overflow-y-auto px-4 pb-24" onScroll={onScroll}>
        {loading && !hasItems ? (
          <div className="grid grid-cols-3 gap-2">
            {Array.from({ length: 15 }).map((_, index) => <SkeletonBlock key={index} className="aspect-square" />)}
          </div>
        ) : !hasItems ? (
          <EmptyState
            icon={trashMode ? <Trash2 className="h-8 w-8" /> : <Cloud className="h-8 w-8" />}
            title={trashMode ? "Trash is empty" : "No photos yet"}
            description={trashMode ? "Deleted Drive items will appear here before permanent removal." : "Upload photos and videos to save them on this PC."}
            action={!trashMode ? <button type="button" onClick={onUpload} disabled={driveOnline === false} className="min-h-11 rounded-full bg-[var(--hello-accent)] px-4 py-2 text-xs font-bold text-white disabled:cursor-not-allowed disabled:opacity-50">Upload Now</button> : undefined}
          />
        ) : (
          <div className="space-y-5">
            {Object.entries(groups).map(([month, monthItems]) => {
              const monthSelected = monthItems.every((item) => selectedIds.has(item.id));
              return (
                <section key={month}>
                  <div className="mb-2 flex min-h-11 items-center justify-between">
                    <button type="button" onClick={() => selectionMode && onSelectMonth(monthItems)} disabled={!selectionMode} className="flex min-h-11 items-center gap-2 rounded-full pr-3 text-left disabled:cursor-default">
                      {selectionMode ? <SelectionDot selected={monthSelected} /> : null}
                      <h2 className="text-sm font-extrabold text-[var(--hello-text)]">{month}</h2>
                    </button>
                    <span className="text-xs text-[var(--hello-text-muted)]">{monthItems.length}</span>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    {monthItems.map((item) => (
                      <DriveTile
                        key={item.id}
                        item={item}
                        trashMode={trashMode}
                        selected={selectedIds.has(item.id)}
                        selectionMode={selectionMode}
                        favorite={favoriteIds.has(item.id)}
                        onOpen={() => onOpenItem(item)}
                        onToggleSelected={() => onToggleSelected(item.id)}
                        onStartDragSelect={() => onStartDragSelect(item.id)}
                        onDragSelect={() => onDragSelect(item.id)}
                        onToggleFavorite={() => onToggleFavorite(item.id)}
                      />
                    ))}
                  </div>
                </section>
              );
            })}
            {loadingMore ? (
              <div className="flex items-center justify-center py-4 text-[var(--hello-accent)]">
                <RefreshCw className="h-5 w-5 animate-spin" aria-hidden />
              </div>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}

function DriveTile({
  item,
  trashMode,
  selected,
  selectionMode,
  favorite,
  onOpen,
  onToggleSelected,
  onStartDragSelect,
  onDragSelect,
  onToggleFavorite,
}: {
  item: DriveItem;
  trashMode: boolean;
  selected: boolean;
  selectionMode: boolean;
  favorite: boolean;
  onOpen: () => void;
  onToggleSelected: () => void;
  onStartDragSelect: () => void;
  onDragSelect: () => void;
  onToggleFavorite: () => void;
}) {
  return (
    <button
      type="button"
      onClick={() => (selectionMode ? onToggleSelected() : onOpen())}
      onContextMenu={(event) => {
        event.preventDefault();
        onToggleSelected();
      }}
      onPointerDown={() => {
        if (selectionMode) onStartDragSelect();
      }}
      onPointerEnter={onDragSelect}
      className={cn(
        "group relative min-h-11 aspect-square overflow-hidden rounded-[12px] bg-slate-200 text-left focus:outline-none focus:ring-2 focus:ring-[var(--hello-accent)] dark:bg-slate-900",
        selected && "ring-2 ring-[var(--hello-accent)]",
      )}
      aria-label={`${selected ? "Selected" : "Open"} ${item.originalName || "Drive media"}`}
      aria-pressed={selectionMode ? selected : undefined}
    >
      {isVideo(item) ? (
        <div className="flex h-full w-full items-center justify-center bg-black/75 text-white">
          <Play className="h-8 w-8 fill-current" aria-hidden />
        </div>
      ) : (
        <img src={resolveDriveUrl(item.thumbnailUrl || item.url)} alt={item.originalName || "Drive media"} className="h-full w-full object-cover transition duration-200 group-hover:scale-105" loading="lazy" />
      )}
      {isVideo(item) ? <span className="absolute bottom-1.5 right-1.5 rounded-full bg-black/60 px-2 py-0.5 text-[10px] font-bold text-white">Video</span> : null}
      {selectionMode ? (
        <div className="absolute left-1.5 top-1.5"><SelectionDot selected={selected} /></div>
      ) : null}
      {!trashMode ? (
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            onToggleFavorite();
          }}
          className={cn(
            "absolute right-1.5 top-1.5 flex h-8 w-8 items-center justify-center rounded-full border border-white/20 bg-black/42 text-white opacity-0 shadow-lg backdrop-blur-md transition hover:scale-105 focus:opacity-100 focus:outline-none focus:ring-2 focus:ring-white group-hover:opacity-100",
            favorite && "opacity-100 text-red-500",
          )}
          aria-label={favorite ? "Remove favorite" : "Add favorite"}
        >
          <Heart className={cn("h-4 w-4", favorite && "fill-current")} />
        </button>
      ) : null}
    </button>
  );
}

function SelectionBar({
  count,
  total,
  trashMode,
  onSelectAll,
  onClear,
  onTrash,
  onRestore,
  onPermanentDelete,
}: {
  count: number;
  total: number;
  trashMode: boolean;
  onSelectAll: () => void;
  onClear: () => void;
  onTrash: () => void;
  onRestore: () => void;
  onPermanentDelete: () => void;
}) {
  return (
    <div className="absolute bottom-4 left-4 right-4 z-30 rounded-[22px] border border-white/12 bg-[#111b21]/96 p-3 text-white shadow-2xl backdrop-blur-xl">
      <div className="flex items-center gap-2">
        <strong className="min-w-0 flex-1 text-sm">{count} selected</strong>
        <button type="button" onClick={onSelectAll} className="min-h-11 rounded-full px-3 text-xs font-bold text-[var(--hello-accent)] focus:outline-none focus:ring-2 focus:ring-[var(--hello-accent)]">
          Select all {total}
        </button>
        <button type="button" onClick={onClear} className="flex h-11 w-11 items-center justify-center rounded-full hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white" aria-label="Clear selection">
          <X className="h-4 w-4" />
        </button>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-xs font-bold">
        {trashMode ? (
          <>
            <ActionButton icon={<RotateCcw className="h-4 w-4" />} label="Restore" onClick={onRestore} />
            <ActionButton danger icon={<Trash2 className="h-4 w-4" />} label="Delete forever" onClick={onPermanentDelete} />
          </>
        ) : (
          <ActionButton danger icon={<Trash2 className="h-4 w-4" />} label="Move to Trash" onClick={onTrash} />
        )}
      </div>
    </div>
  );
}

function ActionButton({ icon, label, onClick, danger = false }: { icon: ReactNode; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button type="button" onClick={onClick} className={cn("flex min-h-11 items-center justify-center gap-2 rounded-[14px] border px-3 py-2.5 transition focus:outline-none focus:ring-2", danger ? "border-red-500/30 text-red-300 hover:bg-red-500/15 focus:ring-red-300" : "border-white/10 hover:bg-white/10 focus:ring-white")}>
      {icon}
      {label}
    </button>
  );
}

function DriveViewer({
  item,
  favorite,
  trashMode,
  hasPrevious,
  hasNext,
  onClose,
  onPrevious,
  onNext,
  onToggleFavorite,
  onRequestDelete,
  onRequestRestore,
  onRequestPermanentDelete,
}: {
  item: DriveItem;
  favorite: boolean;
  trashMode: boolean;
  hasPrevious: boolean;
  hasNext: boolean;
  onClose: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onToggleFavorite: () => void;
  onRequestDelete: () => void;
  onRequestRestore: () => void;
  onRequestPermanentDelete: () => void;
}) {
  const url = resolveDriveUrl(item.url);

  return (
    <div className="absolute inset-0 z-40 flex flex-col bg-black text-white" role="dialog" aria-label="Drive media viewer">
      <div className="flex h-16 flex-none items-center gap-2 px-3">
        <button type="button" onClick={onClose} className="flex h-11 w-11 items-center justify-center rounded-full transition hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white" aria-label="Close viewer">
          <ArrowLeft className="h-5 w-5" />
        </button>
        <div className="min-w-0 flex-1 text-center">
          <p className="truncate text-sm font-bold">{formatDateTime(item.createdAt)}</p>
          <p className="truncate text-xs text-white/60">{item.originalName || "Family Drive media"}</p>
        </div>
        {!trashMode ? (
          <button type="button" onClick={onToggleFavorite} className={cn("flex h-11 w-11 items-center justify-center rounded-full transition hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white", favorite && "text-red-500")} aria-label={favorite ? "Remove favorite" : "Favorite"}>
            <Heart className={cn("h-5 w-5", favorite && "fill-current")} />
          </button>
        ) : null}
        <a href={url} download={item.originalName || true} className="flex h-11 w-11 items-center justify-center rounded-full transition hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white" aria-label="Download">
          <Download className="h-5 w-5" />
        </a>
      </div>

      <div className="relative flex min-h-0 flex-1 items-center justify-center px-3">
        <button type="button" onClick={onPrevious} disabled={!hasPrevious} className="absolute left-3 z-10 flex h-12 w-12 items-center justify-center rounded-full bg-black/48 text-white transition hover:bg-black/70 focus:outline-none focus:ring-2 focus:ring-white disabled:cursor-not-allowed disabled:opacity-30" aria-label="Previous media">
          <ChevronLeft className="h-7 w-7" />
        </button>
        {isVideo(item) ? <video src={url} controls className="max-h-full max-w-full rounded-[16px]" /> : <img src={url} alt={item.originalName || "Drive media"} className="max-h-full max-w-full rounded-[16px] object-contain" />}
        <button type="button" onClick={onNext} disabled={!hasNext} className="absolute right-3 z-10 flex h-12 w-12 items-center justify-center rounded-full bg-black/48 text-white transition hover:bg-black/70 focus:outline-none focus:ring-2 focus:ring-white disabled:cursor-not-allowed disabled:opacity-30" aria-label="Next media">
          <ChevronRight className="h-7 w-7" />
        </button>
      </div>

      <div className="flex-none border-t border-white/10 p-4">
        <p className="truncate text-sm font-bold">{item.originalName || "Family Drive media"}</p>
        <p className="mt-1 text-xs text-white/60">{trashMode ? `Deleted ${formatDateTime(item.deletedAt || 0)}` : "Saved in All Photos & Videos"} - {formatFileSize(item.size)}</p>
        <div className="mt-4 grid grid-cols-3 gap-2 text-xs font-bold">
          {trashMode ? (
            <>
              <ActionButton icon={<RotateCcw className="h-4 w-4" />} label="Restore" onClick={onRequestRestore} />
              <ActionButton danger icon={<Trash2 className="h-4 w-4" />} label="Delete forever" onClick={onRequestPermanentDelete} />
            </>
          ) : (
            <>
              <ActionButton icon={<Heart className={cn("h-4 w-4", favorite && "fill-current")} />} label="Favorite" onClick={onToggleFavorite} />
              <ActionButton danger icon={<Trash2 className="h-4 w-4" />} label="Move to Trash" onClick={onRequestDelete} />
            </>
          )}
          <a href={url} download={item.originalName || true} className="flex min-h-11 items-center justify-center gap-2 rounded-[14px] border border-white/10 px-3 py-2.5 transition hover:bg-white/10 focus:outline-none focus:ring-2 focus:ring-white">
            <Download className="h-4 w-4" />
            Download
          </a>
        </div>
      </div>
    </div>
  );
}

function DriveActionDialog({
  action,
  deleteLimit,
  busy,
  onCancel,
  onConfirm,
}: {
  action: PendingAction;
  deleteLimit: DriveDeleteLimit | null;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const count = action.items.length;
  const title = action.type === "trash" ? "Move to Trash?" : action.type === "restore" ? "Restore from Trash?" : "Delete forever?";
  const body = action.type === "trash"
    ? `This moves ${count} item${count === 1 ? "" : "s"} to shared Trash. ${deleteLimit ? `${deleteLimit.remaining} of ${deleteLimit.limit} trash moves remain today.` : "Family Drive allows 20 trash moves per user each day."}`
    : action.type === "restore"
      ? `This restores ${count} item${count === 1 ? "" : "s"} to the shared family gallery.`
      : `This permanently removes ${count} item${count === 1 ? "" : "s"} from the PC. This cannot be undone.`;
  const confirm = action.type === "trash" ? "Move to Trash" : action.type === "restore" ? "Restore" : "Delete forever";

  return (
    <div className="absolute inset-0 z-[60] flex items-center justify-center bg-black/68 p-5 backdrop-blur-sm" role="dialog" aria-label={title}>
      <div className="w-full max-w-sm rounded-[28px] border border-white/12 bg-[#111b21] p-5 text-white shadow-2xl">
        <div className="flex items-start gap-4">
          <div className={cn("flex h-12 w-12 shrink-0 items-center justify-center rounded-[18px]", action.type === "restore" ? "bg-emerald-500/15 text-emerald-300" : "bg-red-500/15 text-red-300")}>
            {action.type === "restore" ? <RotateCcw className="h-6 w-6" /> : <Trash2 className="h-6 w-6" />}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-extrabold">{title}</h2>
            <p className="mt-2 text-sm leading-6 text-white/62">{body}</p>
          </div>
        </div>
        <div className="mt-6 grid grid-cols-2 gap-3">
          <button type="button" onClick={onCancel} disabled={busy} className="min-h-11 rounded-[16px] border border-white/10 px-4 py-3 text-sm font-bold transition hover:bg-white/8 focus:outline-none focus:ring-2 focus:ring-white disabled:opacity-60">
            Cancel
          </button>
          <button type="button" onClick={onConfirm} disabled={busy} className={cn("min-h-11 rounded-[16px] px-4 py-3 text-sm font-bold text-white transition focus:outline-none focus:ring-2 disabled:opacity-60", action.type === "restore" ? "bg-emerald-600 hover:bg-emerald-700 focus:ring-emerald-300" : "bg-red-500 hover:bg-red-600 focus:ring-red-300")}>
            {busy ? "Working..." : confirm}
          </button>
        </div>
      </div>
    </div>
  );
}

function UploadToast({ text }: { text: string }) {
  return (
    <div className="absolute left-4 right-4 top-4 z-50 rounded-[18px] border border-[var(--hello-accent)]/25 bg-[#111b21]/95 p-3 text-sm text-white shadow-2xl backdrop-blur-xl">
      <div className="flex items-center gap-3">
        <RefreshCw className="h-4 w-4 shrink-0 animate-spin text-[var(--hello-accent)]" />
        <span className="min-w-0 flex-1">{text || "Uploading to Family Drive..."}</span>
      </div>
    </div>
  );
}

function ToastBanner({ kind, message, onClose }: { kind: ToastKind; message: string; onClose: () => void }) {
  return (
    <div className={cn("absolute bottom-5 left-4 right-4 z-50 rounded-[18px] border p-3 text-sm backdrop-blur-xl", kind === "error" ? "border-red-500/20 bg-red-500/12 text-[var(--hello-danger)]" : kind === "success" ? "border-emerald-500/20 bg-emerald-500/12 text-emerald-200" : "border-[var(--hello-accent)]/20 bg-[var(--hello-accent-soft)] text-[var(--hello-text)]")}>
      <div className="flex items-center gap-3">
        {kind === "success" ? <Check className="h-4 w-4 shrink-0" /> : kind === "error" ? <X className="h-4 w-4 shrink-0" /> : <CloudUpload className="h-4 w-4 shrink-0" />}
        <span className="min-w-0 flex-1">{message}</span>
        <button type="button" onClick={onClose} className="min-h-11 rounded-full px-2 font-bold text-[var(--hello-accent)] focus:outline-none focus:ring-2 focus:ring-[var(--hello-accent)]">
          OK
        </button>
      </div>
    </div>
  );
}

function SelectionDot({ selected }: { selected: boolean }) {
  return (
    <span className={cn("flex h-7 w-7 items-center justify-center rounded-full border-2 shadow-lg", selected ? "border-[var(--hello-accent)] bg-[var(--hello-accent)] text-white" : "border-white bg-black/45 text-transparent")}>
      <Check className="h-4 w-4" />
    </span>
  );
}

function groupByMonth(items: DriveItem[]) {
  return items.reduce<Record<string, DriveItem[]>>((acc, item) => {
    const key = item.monthLabel || monthLabelFromTimestamp(item.createdAt);
    acc[key] = acc[key] || [];
    acc[key].push(item);
    return acc;
  }, {});
}

function resolveDriveUrl(value: string) {
  if (!value) return "";
  if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) return value;
  const path = value.startsWith("/") ? value : `/${value}`;
  if (path.startsWith("/hello/api/drive/")) return `${DRIVE_API_BASE}${path.slice("/hello/api".length)}`;
  if (path.startsWith("/api/drive/")) return `${DRIVE_API_BASE}${path.slice("/api".length)}`;
  return path;
}

function isVideo(item: DriveItem) {
  return item.type === "video" || item.mimeType?.startsWith("video/");
}

function monthLabelFromTimestamp(timestamp: number) {
  if (!timestamp) return "Unknown Month";
  return new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric" }).format(new Date(timestamp));
}

function formatDateTime(timestamp: number) {
  if (!timestamp) return "Unknown date";
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(timestamp));
}

function formatFileSize(bytes: number) {
  if (!bytes) return "Unknown size";
  const units = ["B", "KB", "MB", "GB"];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return index === 0 ? `${bytes} B` : `${value.toFixed(1)} ${units[index]}`;
}
