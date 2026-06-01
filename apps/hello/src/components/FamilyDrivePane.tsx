import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type UIEvent } from "react";
import {
  ArrowLeft,
  ChevronRight,
  Cloud,
  CloudUpload,
  Download,
  Heart,
  Image as ImageIcon,
  Lock,
  Play,
  RefreshCw,
  Trash2,
  X,
} from "lucide-react";
import { deleteDriveItem, fetchDriveItems, uploadDriveFiles } from "../api";
import { DriveItem, User } from "../types";
import { cn } from "../lib/utils";
import { EmptyState, SkeletonBlock } from "./HelloUi";

type DriveView = "home" | "all";

interface FamilyDrivePaneProps {
  currentUser: User;
  visible: boolean;
}

export function FamilyDrivePane({ currentUser, visible }: FamilyDrivePaneProps) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [view, setView] = useState<DriveView>("home");
  const [items, setItems] = useState<DriveItem[]>([]);
  const [total, setTotal] = useState(0);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState("");
  const [error, setError] = useState("");
  const [viewerItem, setViewerItem] = useState<DriveItem | null>(null);
  const [deleteCandidate, setDeleteCandidate] = useState<DriveItem | null>(null);
  const [deletingId, setDeletingId] = useState("");
  const [isDragging, setIsDragging] = useState(false);
  const favoritesKey = `hello_drive_favorites_${currentUser.id}`;
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(() => {
    try {
      return new Set(JSON.parse(localStorage.getItem(favoritesKey) || "[]"));
    } catch {
      return new Set();
    }
  });

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

  const loadItems = useCallback(async (mode: "refresh" | "more" = "refresh") => {
    if (mode === "more") {
      if (!hasMore || !nextCursor || loadingMore) return;
      setLoadingMore(true);
    } else {
      setLoading(true);
      setError("");
    }

    try {
      const response = await fetchDriveItems(60, mode === "more" ? nextCursor : null);
      setTotal(response.total);
      setNextCursor(response.nextCursor);
      setHasMore(response.hasMore);
      setItems((current) => {
        const nextItems = mode === "more" ? [...current, ...response.items] : response.items;
        return Array.from(new Map(nextItems.map((item) => [item.id, item])).values());
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Drive could not load");
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [hasMore, loadingMore, nextCursor]);

  useEffect(() => {
    if (visible && items.length === 0 && !loading) {
      void loadItems("refresh");
    }
  }, [items.length, loadItems, loading, visible]);

  const groups = useMemo(() => {
    return items.reduce<Record<string, DriveItem[]>>((acc, item) => {
      const key = item.monthLabel || monthLabelFromTimestamp(item.createdAt);
      acc[key] = acc[key] || [];
      acc[key].push(item);
      return acc;
    }, {});
  }, [items]);

  const uploadFiles = async (files: File[]) => {
    const mediaFiles = files.filter((file) => file.type.startsWith("image/") || file.type.startsWith("video/"));
    if (!mediaFiles.length || uploading) return;

    setUploading(true);
    setUploadProgress(`Uploading ${mediaFiles.length} item${mediaFiles.length === 1 ? "" : "s"}...`);
    setError("");
    try {
      await uploadDriveFiles(mediaFiles, currentUser.id);
      setUploadProgress("Upload complete");
      setView("all");
      await loadItems("refresh");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed");
    } finally {
      window.setTimeout(() => {
        setUploading(false);
        setUploadProgress("");
      }, 800);
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
      void loadItems("more");
    }
  };

  const toggleFavorite = (itemId: string) => {
    setFavoriteIds((current) => {
      const next = new Set(current);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      return next;
    });
  };

  const confirmDelete = async () => {
    if (!deleteCandidate || deletingId) return;

    setDeletingId(deleteCandidate.id);
    setError("");
    try {
      await deleteDriveItem(deleteCandidate.id);
      setItems((current) => current.filter((item) => item.id !== deleteCandidate.id));
      setTotal((current) => Math.max(0, current - 1));
      setFavoriteIds((current) => {
        const next = new Set(current);
        next.delete(deleteCandidate.id);
        return next;
      });
      if (viewerItem?.id === deleteCandidate.id) setViewerItem(null);
      setDeleteCandidate(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed");
    } finally {
      setDeletingId("");
    }
  };

  return (
    <div
      className="relative flex h-full flex-col overflow-hidden bg-slate-50 text-slate-900 dark:bg-[#071219] dark:text-[#e9edef]"
      onDragOver={(event) => {
        event.preventDefault();
        setIsDragging(true);
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setIsDragging(false);
        void uploadFiles(Array.from(event.dataTransfer.files || []));
      }}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*,video/*"
        multiple
        className="hidden"
        onChange={handleInputChange}
      />

      {view === "home" ? (
        <DriveHome
          total={total}
          loading={loading}
          onUpload={() => fileInputRef.current?.click()}
          onOpenAll={() => setView("all")}
        />
      ) : (
        <DriveLibrary
          groups={groups}
          total={total}
          loading={loading}
          loadingMore={loadingMore}
          onBack={() => setView("home")}
          onRefresh={() => loadItems("refresh")}
          onScroll={handleScroll}
          onOpenItem={setViewerItem}
          onUpload={() => fileInputRef.current?.click()}
          favoriteIds={favoriteIds}
          onToggleFavorite={toggleFavorite}
        />
      )}

      <div
        className={cn(
          "pointer-events-none absolute inset-4 z-20 rounded-[28px] border-2 border-dashed border-[var(--hello-accent)] bg-[var(--hello-accent-soft)] opacity-0 transition",
          isDragging && "opacity-100",
        )}
      />

      {uploading ? <UploadOverlay text={uploadProgress} /> : null}
      {error ? <ErrorBanner message={error} onClose={() => setError("")} /> : null}
      {viewerItem ? (
        <DriveViewer
          item={viewerItem}
          favorite={favoriteIds.has(viewerItem.id)}
          onClose={() => setViewerItem(null)}
          onToggleFavorite={() => toggleFavorite(viewerItem.id)}
          onRequestDelete={() => setDeleteCandidate(viewerItem)}
        />
      ) : null}
      {deleteCandidate ? (
        <DeleteConfirmOverlay
          item={deleteCandidate}
          busy={deletingId === deleteCandidate.id}
          onCancel={() => setDeleteCandidate(null)}
          onConfirm={confirmDelete}
        />
      ) : null}
    </div>
  );
}

function DriveHome({
  total,
  loading,
  onUpload,
  onOpenAll,
}: {
  total: number;
  loading: boolean;
  onUpload: () => void;
  onOpenAll: () => void;
}) {
  return (
    <div className="relative z-20 flex h-full flex-col px-5 py-6">
      <p className="text-xs font-extrabold uppercase tracking-wide text-[var(--hello-accent)]">Hello Drive</p>
      <h1 className="mt-2 text-3xl font-black tracking-tight text-[var(--hello-text)]">Drive</h1>
      <p className="mt-3 text-sm leading-6 text-[var(--hello-text-muted)]">
        All our family memories in one place.
      </p>

      <button
        type="button"
        onClick={onUpload}
        className="mt-7 flex w-full items-center justify-center gap-3 rounded-[18px] bg-[var(--hello-accent)] px-4 py-4 text-sm font-extrabold text-white shadow-[0_18px_38px_rgba(15,143,120,0.28)] transition hover:bg-[var(--hello-accent-strong)]"
      >
        <CloudUpload className="h-5 w-5" />
        Upload Photos
      </button>

      <button
        type="button"
        onClick={onOpenAll}
        className="hello-card mt-12 flex w-full items-center gap-4 p-4 text-left transition hover:translate-y-[-1px]"
      >
        <div className="flex h-12 w-12 items-center justify-center rounded-[16px] bg-[var(--hello-accent-soft)] text-[var(--hello-accent)]">
          <ImageIcon className="h-6 w-6" />
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="text-sm font-extrabold text-[var(--hello-text)]">All Photos & Videos</h2>
          <p className="mt-1 text-xs text-[var(--hello-text-muted)]">
            {loading ? "Loading..." : `${total.toLocaleString()} items`}
          </p>
        </div>
        <ChevronRight className="h-5 w-5 text-[var(--hello-text-muted)]" />
      </button>

      <div className="mt-auto flex items-start justify-center gap-3 pb-8 text-[var(--hello-text-muted)]">
        <Lock className="mt-0.5 h-4 w-4 shrink-0" />
        <p className="text-xs leading-5">
          Centrally stored and safe.
          <br />
          Latest to oldest.
        </p>
      </div>
    </div>
  );
}

function DriveLibrary({
  groups,
  total,
  loading,
  loadingMore,
  onBack,
  onRefresh,
  onScroll,
  onOpenItem,
  onUpload,
  favoriteIds,
  onToggleFavorite,
}: {
  groups: Record<string, DriveItem[]>;
  total: number;
  loading: boolean;
  loadingMore: boolean;
  onBack: () => void;
  onRefresh: () => void;
  onScroll: (event: UIEvent<HTMLDivElement>) => void;
  onOpenItem: (item: DriveItem) => void;
  onUpload: () => void;
  favoriteIds: Set<string>;
  onToggleFavorite: (itemId: string) => void;
}) {
  const hasItems = Object.keys(groups).length > 0;

  return (
    <div className="relative z-20 flex h-full flex-col">
      <div className="flex h-16 flex-none items-center gap-2 border-b border-[var(--hello-border)] px-3">
        <button
          type="button"
          onClick={onBack}
          className="flex h-10 w-10 items-center justify-center rounded-full transition hover:bg-black/5 dark:hover:bg-white/5"
          title="Back"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>
        <div className="min-w-0 flex-1 text-center">
          <h1 className="truncate text-sm font-extrabold text-[var(--hello-text)]">All Photos & Videos</h1>
          <p className="text-xs text-[var(--hello-text-muted)]">{total.toLocaleString()} items</p>
        </div>
        <button
          type="button"
          onClick={onRefresh}
          className="flex h-10 w-10 items-center justify-center rounded-full transition hover:bg-black/5 dark:hover:bg-white/5"
          title="Refresh"
        >
          <RefreshCw className="h-4 w-4" />
        </button>
      </div>

      <div className="flex items-center justify-between px-5 py-3 text-xs text-[var(--hello-text-muted)]">
        <span>Grouped by month</span>
        <button type="button" onClick={onUpload} className="font-bold text-[var(--hello-accent)]">
          Upload
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 pb-6" onScroll={onScroll}>
        {loading && !hasItems ? (
          <div className="grid grid-cols-3 gap-2">
            {Array.from({ length: 15 }).map((_, index) => (
              <SkeletonBlock key={index} className="aspect-square" />
            ))}
          </div>
        ) : !hasItems ? (
          <EmptyState
            icon={<Cloud className="h-8 w-8" />}
            title="No photos yet"
            description="Upload photos and videos to save them on this PC."
            action={
              <button
                type="button"
                onClick={onUpload}
                className="rounded-full bg-[var(--hello-accent)] px-4 py-2 text-xs font-bold text-white"
              >
                Upload Now
              </button>
            }
          />
        ) : (
          <div className="space-y-5">
            {Object.entries(groups).map(([month, monthItems]) => (
              <section key={month}>
                <div className="mb-2 flex items-center justify-between">
                  <h2 className="text-sm font-extrabold text-[var(--hello-text)]">{month}</h2>
                  <span className="text-xs text-[var(--hello-text-muted)]">{monthItems.length}</span>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  {monthItems.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => onOpenItem(item)}
                      className="group relative aspect-square overflow-hidden rounded-[12px] bg-slate-200 dark:bg-slate-900"
                    >
                      {isVideo(item) ? (
                        <div className="flex h-full w-full items-center justify-center bg-black/75 text-white">
                          <Play className="h-8 w-8 fill-current" />
                        </div>
                      ) : (
                        <img
                          src={resolveDriveUrl(item.thumbnailUrl || item.url)}
                          alt={item.originalName || "Drive media"}
                          className="h-full w-full object-cover transition duration-200 group-hover:scale-105"
                          loading="lazy"
                        />
                      )}
                      {isVideo(item) ? (
                        <span className="absolute bottom-1.5 right-1.5 rounded-full bg-black/60 px-2 py-0.5 text-[10px] font-bold text-white">
                          Video
                        </span>
                      ) : null}
                      <button
                        type="button"
                        onClick={(event) => {
                          event.stopPropagation();
                          onToggleFavorite(item.id);
                        }}
                        className={cn(
                          "absolute right-1.5 top-1.5 flex h-7 w-7 items-center justify-center rounded-full border border-white/20 bg-black/42 text-white opacity-0 shadow-lg backdrop-blur-md transition hover:scale-105 group-hover:opacity-100",
                          favoriteIds.has(item.id) && "opacity-100 text-red-500",
                        )}
                        title={favoriteIds.has(item.id) ? "Remove favorite" : "Add favorite"}
                      >
                        <Heart
                          className={cn("h-4 w-4", favoriteIds.has(item.id) && "fill-current")}
                        />
                      </button>
                    </button>
                  ))}
                </div>
              </section>
            ))}
            {loadingMore ? (
              <div className="flex items-center justify-center py-4 text-[var(--hello-accent)]">
                <RefreshCw className="h-5 w-5 animate-spin" />
              </div>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}

function DriveViewer({
  item,
  favorite,
  onClose,
  onToggleFavorite,
  onRequestDelete,
}: {
  item: DriveItem;
  favorite: boolean;
  onClose: () => void;
  onToggleFavorite: () => void;
  onRequestDelete: () => void;
}) {
  const url = resolveDriveUrl(item.url);

  return (
    <div className="absolute inset-0 z-40 flex flex-col bg-black text-white">
      <div className="flex h-16 flex-none items-center gap-2 px-3">
        <button
          type="button"
          onClick={onClose}
          className="flex h-10 w-10 items-center justify-center rounded-full transition hover:bg-white/10"
          title="Back"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>
        <div className="min-w-0 flex-1 text-center">
          <p className="truncate text-sm font-bold">{formatDateTime(item.createdAt)}</p>
          <p className="truncate text-xs text-white/60">{item.originalName || "Family Drive media"}</p>
        </div>
        <button
          type="button"
          onClick={onToggleFavorite}
          className={cn(
            "flex h-10 w-10 items-center justify-center rounded-full transition hover:bg-white/10",
            favorite && "text-red-500",
          )}
          title={favorite ? "Remove favorite" : "Favorite"}
        >
          <Heart className={cn("h-5 w-5", favorite && "fill-current")} />
        </button>
        <a
          href={url}
          download={item.originalName || true}
          className="flex h-10 w-10 items-center justify-center rounded-full transition hover:bg-white/10"
          title="Download"
        >
          <Download className="h-5 w-5" />
        </a>
        <button
          type="button"
          onClick={onRequestDelete}
          className="flex h-10 w-10 items-center justify-center rounded-full text-red-400 transition hover:bg-red-500/15 hover:text-red-300"
          title="Delete"
        >
          <Trash2 className="h-5 w-5" />
        </button>
      </div>

      <div className="flex min-h-0 flex-1 items-center justify-center px-3">
        {isVideo(item) ? (
          <video src={url} controls className="max-h-full max-w-full rounded-[16px]" />
        ) : (
          <img src={url} alt={item.originalName || "Drive media"} className="max-h-full max-w-full rounded-[16px] object-contain" />
        )}
      </div>

      <div className="flex-none border-t border-white/10 p-4">
        <p className="truncate text-sm font-bold">{item.originalName || "Family Drive media"}</p>
        <p className="mt-1 text-xs text-white/60">Saved in All Photos & Videos - {formatFileSize(item.size)}</p>
        <div className="mt-4 grid grid-cols-3 gap-2 text-xs font-bold">
          <button
            type="button"
            onClick={onToggleFavorite}
            className={cn(
              "flex items-center justify-center gap-2 rounded-[14px] border border-white/10 px-3 py-2.5 transition hover:bg-white/10",
              favorite && "border-red-500/40 bg-red-500/10 text-red-300",
            )}
          >
            <Heart className={cn("h-4 w-4", favorite && "fill-current")} />
            Favorite
          </button>
          <a
            href={url}
            download={item.originalName || true}
            className="flex items-center justify-center gap-2 rounded-[14px] border border-white/10 px-3 py-2.5 transition hover:bg-white/10"
          >
            <Download className="h-4 w-4" />
            Download
          </a>
          <button
            type="button"
            onClick={onRequestDelete}
            className="flex items-center justify-center gap-2 rounded-[14px] border border-red-500/30 px-3 py-2.5 text-red-300 transition hover:bg-red-500/15"
          >
            <Trash2 className="h-4 w-4" />
            Delete
          </button>
        </div>
      </div>
    </div>
  );
}

function DeleteConfirmOverlay({
  item,
  busy,
  onCancel,
  onConfirm,
}: {
  item: DriveItem;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="absolute inset-0 z-[60] flex items-center justify-center bg-black/68 p-5 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-[28px] border border-white/12 bg-[#111b21] p-5 text-white shadow-2xl">
        <div className="flex items-start gap-4">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[18px] bg-red-500/15 text-red-300">
            <Trash2 className="h-6 w-6" />
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-extrabold">Delete from Drive?</h2>
            <p className="mt-2 text-sm leading-6 text-white/62">
              This removes {item.originalName || "this item"} from the central family library.
            </p>
          </div>
        </div>
        <div className="mt-6 grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="rounded-[16px] border border-white/10 px-4 py-3 text-sm font-bold transition hover:bg-white/8 disabled:opacity-60"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className="rounded-[16px] bg-red-500 px-4 py-3 text-sm font-bold text-white transition hover:bg-red-600 disabled:opacity-60"
          >
            {busy ? "Deleting..." : "Delete"}
          </button>
        </div>
      </div>
    </div>
  );
}

function UploadOverlay({ text }: { text: string }) {
  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/72 p-6">
      <div className="hello-panel-strong flex w-full max-w-xs flex-col items-center rounded-[28px] p-8 text-center">
        <div className="relative flex h-28 w-28 items-center justify-center rounded-full border-4 border-[var(--hello-accent-soft)]">
          <CloudUpload className="h-10 w-10 text-[var(--hello-accent)]" />
        </div>
        <h2 className="mt-5 text-lg font-extrabold text-[var(--hello-text)]">Uploading...</h2>
        <p className="mt-2 text-sm text-[var(--hello-text-muted)]">{text || "Saving to central library"}</p>
      </div>
    </div>
  );
}

function ErrorBanner({ message, onClose }: { message: string; onClose: () => void }) {
  return (
    <div className="absolute bottom-5 left-4 right-4 z-50 rounded-[18px] border border-red-500/20 bg-red-500/12 p-3 text-sm text-[var(--hello-danger)] backdrop-blur-xl">
      <div className="flex items-center gap-3">
        <X className="h-4 w-4 shrink-0" />
        <span className="min-w-0 flex-1">{message}</span>
        <button type="button" onClick={onClose} className="font-bold text-[var(--hello-accent)]">
          OK
        </button>
      </div>
    </div>
  );
}

function resolveDriveUrl(value: string) {
  if (!value) return "";
  if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) return value;
  return value.startsWith("/") ? value : `/${value}`;
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
