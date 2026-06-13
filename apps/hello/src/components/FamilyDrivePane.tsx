import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  createDriveCircle,
  createDriveDeletePoll,
  createDriveEvent,
  deleteDriveCircle,
  deleteDriveEvent,
  deleteDriveItem,
  fetchChats,
  fetchDriveCircles,
  fetchDriveDeletePolls,
  fetchDriveEvents,
  fetchDriveFavorites,
  fetchDriveItems,
  fetchDriveTrash,
  fetchUsers,
  leaveDriveCircle,
  permanentlyDeleteDriveItem,
  renameDriveEvent,
  restoreDriveItem,
  setDriveFavorite,
  uploadDriveCircleAvatar,
  uploadDriveFiles,
  voteDriveDeletePoll,
} from "../api";
import type { Chat, DriveCircle, DriveDeletePoll, DriveEvent, DriveItem, User } from "../types";
import {
  ArrowLeft,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Crown,
  Download,
  Eye,
  FolderOpen,
  Heart,
  Image as ImageIcon,
  LogOut,
  MoreVertical,
  Plus,
  ShieldAlert,
  Trash2,
  Upload,
  UserCog,
  Users,
  X,
} from "lucide-react";

interface FamilyDrivePaneProps {
  currentUser: User;
  visible: boolean;
}

type DriveView = "circles" | "events" | "items" | "trash";
type MemberRole = "view" | "add" | "manage";
type CircleMenuAction = "rename" | "members" | "avatar" | "delete" | "leave" | "request-delete";

export function FamilyDrivePane({ currentUser, visible }: FamilyDrivePaneProps) {
  const [view, setView] = useState<DriveView>("circles");
  const [circles, setCircles] = useState<DriveCircle[]>([]);
  const [selectedCircleId, setSelectedCircleId] = useState<string | null>(null);
  const [events, setEvents] = useState<DriveEvent[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
  const [items, setItems] = useState<DriveItem[]>([]);
  const [trashItems, setTrashItems] = useState<DriveItem[]>([]);
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [polls, setPolls] = useState<DriveDeletePoll[]>([]);
  const [contacts, setContacts] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [circleName, setCircleName] = useState("");
  const [eventName, setEventName] = useState("");
  const [contactSearch, setContactSearch] = useState("");
  const [selectedContactRoles, setSelectedContactRoles] = useState<Record<string, MemberRole>>({});
  const [openCircleMenuId, setOpenCircleMenuId] = useState<string | null>(null);
  const [memberEditorOpen, setMemberEditorOpen] = useState(false);
  const [memberEditorRoles, setMemberEditorRoles] = useState<Record<string, MemberRole>>({});
  const [avatarCircleId, setAvatarCircleId] = useState<string | null>(null);
  const avatarCircleIdRef = useRef<string | null>(null);
  const avatarInputRef = useRef<HTMLInputElement | null>(null);

  const selectedCircle = useMemo(
    () => circles.find((circle) => circle.id === selectedCircleId) || null,
    [circles, selectedCircleId],
  );
  const selectedEvent = useMemo(
    () => events.find((event) => event.id === selectedEventId) || null,
    [events, selectedEventId],
  );

  useEffect(() => {
    if (!visible) return;
    void refreshCircles();
    void refreshFavorites();
    void loadContacts();
  }, [visible, currentUser.id]);

  useEffect(() => {
    if (!visible || !selectedCircleId) return;
    void refreshEvents(selectedCircleId);
    void refreshPolls(selectedCircleId);
  }, [visible, selectedCircleId, currentUser.id]);

  useEffect(() => {
    if (!visible || !selectedCircleId) return;
    if (view === "trash") {
      void refreshTrash(selectedCircleId, selectedEventId);
    } else if (view === "items") {
      void refreshItems(selectedCircleId, selectedEventId);
    }
  }, [visible, view, selectedCircleId, selectedEventId, currentUser.id]);

  async function refreshCircles() {
    setLoading(true);
    try {
      const nextCircles = await fetchDriveCircles(currentUser.id);
      setCircles(nextCircles);
      setSelectedCircleId((current) => current && nextCircles.some((circle) => circle.id === current) ? current : null);
      if (!nextCircles.length) {
        setSelectedEventId(null);
        setEvents([]);
        setItems([]);
        setTrashItems([]);
        setPolls([]);
      }
    } finally {
      setLoading(false);
    }
  }

  async function refreshEvents(circleId: string) {
    const nextEvents = await fetchDriveEvents(currentUser.id, circleId);
    setEvents(nextEvents);
    setSelectedEventId((current) => current && nextEvents.some((event) => event.id === current) ? current : nextEvents[0]?.id || null);
  }

  async function refreshItems(circleId: string, eventId?: string | null) {
    const response = await fetchDriveItems(currentUser.id, 120, null, true, { circleId, eventId });
    setItems(response.items);
  }

  async function refreshTrash(circleId: string, eventId?: string | null) {
    const response = await fetchDriveTrash(currentUser.id, 120, null, false, { circleId, eventId });
    setTrashItems(response.items);
  }

  async function refreshFavorites() {
    const ids = await fetchDriveFavorites(currentUser.id);
    setFavoriteIds(new Set(ids));
  }

  async function refreshPolls(circleId: string) {
    const nextPolls = await fetchDriveDeletePolls(currentUser.id, circleId);
    setPolls(nextPolls);
  }

  async function loadContacts() {
    const [chats, users] = await Promise.all([
      fetchChats(currentUser.id).catch(() => [] as Chat[]),
      fetchUsers().catch(() => [] as User[]),
    ]);
    const nextContacts = [...chats.flatMap((chat) => chat.participants || []), ...users]
      .filter((user) => user.id && user.id !== currentUser.id)
      .reduce<User[]>((acc, user) => {
        if (!acc.some((existing) => existing.id === user.id)) acc.push(user);
        return acc;
      }, [])
      .sort((a, b) => displayHandle(a).localeCompare(displayHandle(b)));
    setContacts(nextContacts);
  }

  async function handleCreateCircle() {
    const name = circleName.trim();
    if (!name) return;
    const members = contacts
      .filter((contact) => Boolean(selectedContactRoles[contact.id]))
      .map((contact) => ({
        userId: contact.id,
        role: selectedContactRoles[contact.id],
        name: contact.name,
        username: contact.username || null,
        avatar: contact.avatar || null,
      }));
    await createDriveCircle({
      userId: currentUser.id,
      name,
      members: [
        {
          userId: currentUser.id,
          role: "owner",
          name: currentUser.name,
          username: currentUser.username || null,
          avatar: currentUser.avatar || null,
        },
        ...members,
      ],
    });
    setCircleName("");
    setContactSearch("");
    setSelectedContactRoles({});
    await refreshCircles();
  }

  async function handleCreateEvent() {
    if (!selectedCircleId || !eventName.trim()) return;
    await createDriveEvent({ userId: currentUser.id, circleId: selectedCircleId, name: eventName.trim() });
    setEventName("");
    await refreshEvents(selectedCircleId);
  }

  async function handleRenameEvent(event: DriveEvent) {
    const nextName = window.prompt("Rename event", event.name)?.trim();
    if (!nextName || nextName === event.name) return;
    await renameDriveEvent(event.id, currentUser.id, nextName);
    if (selectedCircleId) await refreshEvents(selectedCircleId);
  }

  async function handleRenameCircle(circle: DriveCircle) {
    if (!canManageCircle(circle, currentUser.id)) return;
    const nextName = window.prompt("Rename circle", circle.name)?.trim();
    if (!nextName || nextName === circle.name) return;
    await createDriveCircle({
      userId: currentUser.id,
      id: circle.id,
      name: nextName,
      members: circle.members.map((member) => ({
        userId: member.userId,
        role: member.role || "view",
        name: member.name || null,
        username: member.username || null,
        avatar: member.avatar || null,
      })),
    });
    await refreshCircles();
  }

  function openMemberEditor(circle: DriveCircle) {
    if (!canManageCircle(circle, currentUser.id)) return;
    setSelectedCircleId(circle.id);
    setMemberEditorRoles(
      circle.members.reduce<Record<string, MemberRole>>((acc, member) => {
        if (member.userId !== circle.ownerUserId) {
          acc[member.userId] = normalizeMemberRole(member.role);
        }
        return acc;
      }, {}),
    );
    setMemberEditorOpen(true);
  }

  async function handleSaveMembers() {
    if (!selectedCircle || !canManageCircle(selectedCircle, currentUser.id)) return;
    const currentMembers = new Map(selectedCircle.members.map((member) => [member.userId, member]));
    const ownerId = selectedCircle.ownerUserId || currentUser.id;
    const members = [
      {
        userId: ownerId,
        role: "owner",
        name: currentMembers.get(ownerId)?.name || currentUser.name,
        username: currentMembers.get(ownerId)?.username || currentUser.username || null,
        avatar: currentMembers.get(ownerId)?.avatar || currentUser.avatar || null,
      },
      ...Object.entries(memberEditorRoles).map(([userId, role]) => {
        const member = currentMembers.get(userId);
        const contact = contacts.find((candidate) => candidate.id === userId);
        return {
          userId,
          role,
          name: member?.name || contact?.name || userId,
          username: member?.username || contact?.username || null,
          avatar: member?.avatar || contact?.avatar || null,
        };
      }),
    ];
    await createDriveCircle({
      userId: currentUser.id,
      id: selectedCircle.id,
      name: selectedCircle.name,
      members,
    });
    setMemberEditorOpen(false);
    await refreshCircles();
  }

  async function handleCircleAvatarSelected(files: FileList | null) {
    const file = files?.[0];
    const circleId = avatarCircleIdRef.current || avatarCircleId;
    if (!file || !circleId) return;
    const updated = await uploadDriveCircleAvatar(circleId, currentUser.id, file);
    setCircles((current) => (current.map((circle) => circle.id === updated.id ? updated : circle)));
    avatarCircleIdRef.current = null;
    setAvatarCircleId(null);
    if (avatarInputRef.current) avatarInputRef.current.value = "";
  }

  async function handleDeleteEvent(event: DriveEvent) {
    if (!selectedCircleId) return;
    try {
      await deleteDriveEvent(event.id, currentUser.id);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to delete event";
      if (!message.toLowerCase().includes("poll")) throw error;
      await createDriveDeletePoll({
        userId: currentUser.id,
        targetType: "event",
        targetId: event.id,
        circleId: selectedCircleId,
      });
    }
    await refreshEvents(selectedCircleId);
    await refreshPolls(selectedCircleId);
  }

  async function handleDeleteCircle(circle: DriveCircle) {
    if (!canManageCircle(circle, currentUser.id)) {
      await createDriveDeletePoll({
        userId: currentUser.id,
        targetType: "circle",
        targetId: circle.id,
      });
      await refreshPolls(circle.id);
      return;
    }
    try {
      await deleteDriveCircle(circle.id, currentUser.id);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to delete circle";
      if (!message.toLowerCase().includes("poll")) throw error;
      await createDriveDeletePoll({
        userId: currentUser.id,
        targetType: "circle",
        targetId: circle.id,
      });
    }
    await refreshCircles();
    if (circle.id === selectedCircleId) {
      setView("circles");
    }
  }

  async function handleLeaveCircle(circle: DriveCircle) {
    await leaveDriveCircle(circle.id, currentUser.id);
    await refreshCircles();
    if (circle.id === selectedCircleId) {
      setSelectedCircleId(null);
      setSelectedEventId(null);
      setMemberEditorOpen(false);
    }
    setView("circles");
  }

  function handleCircleMenuAction(circle: DriveCircle, action: CircleMenuAction) {
    setOpenCircleMenuId(null);
    if (action === "rename") void handleRenameCircle(circle);
    if (action === "members") openMemberEditor(circle);
    if (action === "avatar") {
      avatarCircleIdRef.current = circle.id;
      setAvatarCircleId(circle.id);
      avatarInputRef.current?.click();
    }
    if (action === "delete" || action === "request-delete") void handleDeleteCircle(circle);
    if (action === "leave") void handleLeaveCircle(circle);
  }

  async function handleUpload(files: FileList | null) {
    if (!files || !selectedCircleId) return;
    const activeEvent = selectedEventId || events[0]?.id || null;
    if (!activeEvent) {
      window.alert("Create an event in this circle first.");
      return;
    }
    setUploading(true);
    try {
      await uploadDriveFiles(Array.from(files), currentUser.id, {
        circleIds: [selectedCircleId],
        eventId: activeEvent,
        eventName: selectedEvent?.name || events.find((event) => event.id === activeEvent)?.name || "Daily Memories",
        batchId: `web_drive_batch_${Date.now()}`,
      });
      setView("items");
      await refreshEvents(selectedCircleId);
      await refreshItems(selectedCircleId, activeEvent);
    } finally {
      setUploading(false);
    }
  }

  async function handleToggleFavorite(item: DriveItem) {
    const nextFavorite = !favoriteIds.has(item.id);
    await setDriveFavorite(item.id, currentUser.id, nextFavorite);
    setFavoriteIds((current) => {
      const next = new Set(current);
      if (nextFavorite) next.add(item.id);
      else next.delete(item.id);
      return next;
    });
  }

  async function handleDeleteItem(item: DriveItem) {
    const confirmed = window.confirm(`Move "${item.originalName || "this item"}" to shared Trash?`);
    if (!confirmed) return;
    const securityAnswer = window.prompt("Enter your security answer to confirm delete.")?.trim();
    if (!securityAnswer) return;
    await deleteDriveItem(item.id, currentUser.id, securityAnswer);
    if (selectedCircleId) await refreshItems(selectedCircleId, selectedEventId);
  }

  async function handleRestoreItem(item: DriveItem) {
    await restoreDriveItem(item.id, currentUser.id);
    if (selectedCircleId) await refreshTrash(selectedCircleId, selectedEventId);
  }

  async function handlePermanentDelete(item: DriveItem) {
    const confirmed = window.confirm(`Permanently delete "${item.originalName || "this item"}"?`);
    if (!confirmed) return;
    await permanentlyDeleteDriveItem(item.id, currentUser.id);
    if (selectedCircleId) await refreshTrash(selectedCircleId, selectedEventId);
  }

  async function handleVote(pollId: string, vote: "delete" | "keep") {
    await voteDriveDeletePoll(pollId, currentUser.id, vote);
    if (selectedCircleId) await refreshPolls(selectedCircleId);
    if (selectedCircleId) await refreshCircles();
  }

  const filteredContacts = useMemo(() => {
    const query = contactSearch.trim().toLowerCase();
    if (!query) return contacts;
    return contacts.filter((contact) =>
      [contact.name, contact.username ? `@${contact.username}` : "", contact.id]
        .join(" ")
        .toLowerCase()
        .includes(query),
    );
  }, [contactSearch, contacts]);

  const selectedContacts = useMemo(
    () => contacts.filter((contact) => Boolean(selectedContactRoles[contact.id])),
    [contacts, selectedContactRoles],
  );

  const circleContacts = useMemo(() => {
    if (!selectedCircle) return contacts;
    const existingIds = new Set(selectedCircle.members.map((member) => member.userId));
    const mappedMembers = selectedCircle.members
      .filter((member) => member.userId !== selectedCircle.ownerUserId)
      .map<User>((member) => ({
        id: member.userId,
        name: member.name || member.username || member.userId,
        username: member.username || undefined,
        avatar: member.avatar || undefined,
      }));
    return [
      ...mappedMembers,
      ...contacts.filter((contact) => !existingIds.has(contact.id)),
    ].sort((a, b) => displayHandle(a).localeCompare(displayHandle(b)));
  }, [contacts, selectedCircle]);

  const selectedCircleMember = selectedCircle?.members.find((member) => member.userId === currentUser.id) || null;
  const selectedCircleCanManage = selectedCircle ? canManageCircle(selectedCircle, currentUser.id) : false;
  const selectedCircleCanUpload = selectedCircle ? canUploadToCircle(selectedCircle, currentUser.id) : false;

  if (!visible) return null;

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--hello-surface)] text-[var(--hello-text)]">
      <input
        ref={avatarInputRef}
        className="hidden"
        type="file"
        accept="image/*"
        onChange={(event) => void handleCircleAvatarSelected(event.target.files)}
      />
      <div className="border-b border-[var(--hello-border)] px-5 py-4">
        <div className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--hello-muted)]">Hello Drive</div>
        <div className="mt-1 flex items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-extrabold">{selectedCircle ? selectedCircle.name : "Circles"}</h1>
            <p className="text-sm text-[var(--hello-muted)]">
              {selectedCircle
                ? `${selectedCircle.memberCount} members - ${roleLabel(selectedCircleMember?.role)}`
                : "Choose a circle to open its dedicated Drive workspace."}
            </p>
          </div>
          <div className="flex items-center gap-2">
          {selectedCircle ? (
            <button
              type="button"
              onClick={() => {
                setSelectedCircleId(null);
                setSelectedEventId(null);
                setView("circles");
                setMemberEditorOpen(false);
              }}
              className="inline-flex items-center gap-2 rounded-full border border-[var(--hello-border)] bg-white px-4 py-2 text-sm font-semibold text-[var(--hello-text)]"
            >
              <ArrowLeft className="h-4 w-4" />
              Circles
            </button>
          ) : null}
          {selectedCircleId && selectedCircleCanUpload ? (
            <label className="inline-flex cursor-pointer items-center gap-2 rounded-full bg-[var(--hello-accent)] px-4 py-2 text-sm font-semibold text-white">
              <Upload className="h-4 w-4" />
              {uploading ? "Uploading..." : "Upload"}
              <input className="hidden" type="file" accept="image/*,video/*" multiple onChange={(event) => void handleUpload(event.target.files)} />
            </label>
          ) : null}
          {selectedCircle ? (
            <CircleActionsMenu
              circle={selectedCircle}
              currentUserId={currentUser.id}
              open={openCircleMenuId === selectedCircle.id}
              onToggle={() => setOpenCircleMenuId((current) => current === selectedCircle.id ? null : selectedCircle.id)}
              onAction={(action) => handleCircleMenuAction(selectedCircle, action)}
            />
          ) : null}
          </div>
        </div>
      </div>

      <div className={`grid min-h-0 flex-1 gap-0 overflow-hidden ${selectedCircle ? "md:grid-cols-[360px_minmax(0,1fr)]" : "md:grid-cols-[390px_minmax(0,1fr)]"}`}>
        <aside className="min-h-0 overflow-y-auto border-r border-[var(--hello-border)] bg-[linear-gradient(180deg,rgba(255,255,255,0.65),rgba(255,255,255,0.35))] p-4 custom-scrollbar md:p-5">
          <div className="rounded-[28px] border border-pink-200/80 bg-white/85 p-5 shadow-[0_18px_50px_rgba(166,63,111,0.10)] backdrop-blur">
            <div className="mb-2 flex items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2 text-sm font-semibold text-[var(--hello-text)]">
                  <Users className="h-4 w-4" />
                  Create Circle
                </div>
                <p className="mt-1 text-xs leading-5 text-[var(--hello-muted)]">
                  Give the circle a name, choose people, and set permissions before saving.
                </p>
              </div>
              <div className="rounded-full bg-pink-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-pink-500">
                Draft
              </div>
            </div>

            <div className="space-y-3">
              <div>
                <label className="mb-1.5 block text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--hello-muted)]">
                  Circle name
                </label>
                <input
                  value={circleName}
                  onChange={(event) => setCircleName(event.target.value)}
                  placeholder="Family trip, Tests, School"
                  className="w-full rounded-2xl border border-[var(--hello-border)] bg-white px-4 py-3 text-sm outline-none transition placeholder:text-[var(--hello-muted)] focus:border-pink-300 focus:ring-2 focus:ring-pink-100"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--hello-muted)]">
                  Find people
                </label>
                <input
                  value={contactSearch}
                  onChange={(event) => setContactSearch(event.target.value)}
                  placeholder="Search by name or user id"
                  className="w-full rounded-2xl border border-[var(--hello-border)] bg-white px-4 py-3 text-sm outline-none transition placeholder:text-[var(--hello-muted)] focus:border-pink-300 focus:ring-2 focus:ring-pink-100"
                />
              </div>
            </div>

            <div className="mt-4 flex items-center justify-between gap-3">
              <div className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--hello-muted)]">
                Members
              </div>
              <div className="text-xs text-[var(--hello-muted)]">
                {selectedContacts.length ? `${selectedContacts.length} selected` : "No members selected"}
              </div>
            </div>

            {selectedContacts.length ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {selectedContacts.map((contact) => (
                  <span
                    key={contact.id}
                    className="inline-flex items-center gap-2 rounded-full border border-pink-200 bg-pink-50 px-3 py-1.5 text-xs font-semibold text-pink-600"
                  >
                    <span className="max-w-[120px] truncate">{contact.name}</span>
                    <span className="rounded-full bg-white px-2 py-0.5 text-[10px] uppercase tracking-[0.12em] text-pink-500">
                      {selectedContactRoles[contact.id]}
                    </span>
                  </span>
                ))}
              </div>
            ) : null}

            <div className="mt-4 max-h-56 overflow-auto rounded-2xl border border-[var(--hello-border)] bg-white/95 p-3 custom-scrollbar">
              {filteredContacts.map((contact) => {
                const checked = Boolean(selectedContactRoles[contact.id]);
                return (
                  <label
                    key={contact.id}
                    className={`flex cursor-pointer items-center gap-3 rounded-2xl px-3 py-3 transition ${
                      checked ? "border border-pink-200 bg-pink-50/80 shadow-sm" : "hover:bg-pink-50/60"
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() =>
                        setSelectedContactRoles((current) => {
                          const next = { ...current };
                          if (checked) delete next[contact.id];
                          else next[contact.id] = "add";
                          return next;
                        })
                      }
                    />
                    <Avatar user={contact} />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-semibold text-[var(--hello-text)]">{contact.name}</div>
                      <div className="truncate text-xs text-[var(--hello-muted)]">{contact.username ? `@${contact.username}` : contact.id}</div>
                    </div>
                    {checked ? (
                      <select
                        value={selectedContactRoles[contact.id]}
                        onChange={(event) =>
                          setSelectedContactRoles((current) => ({
                            ...current,
                            [contact.id]: event.target.value as MemberRole,
                          }))
                        }
                        onClick={(event) => event.stopPropagation()}
                        className="min-w-[112px] rounded-full border border-[var(--hello-border)] bg-white px-3 py-1.5 text-xs font-semibold text-[var(--hello-text)] outline-none"
                      >
                        <option value="manage">Admin</option>
                        <option value="add">Contributor</option>
                        <option value="view">Viewer</option>
                      </select>
                    ) : null}
                  </label>
                );
              })}
              {!filteredContacts.length ? (
                <div className="rounded-2xl border border-dashed border-[var(--hello-border)] px-4 py-5 text-sm text-[var(--hello-muted)]">
                  No users found for this search.
                </div>
              ) : null}
            </div>
            <button
              type="button"
              onClick={() => void handleCreateCircle()}
              disabled={!circleName.trim()}
              className="mt-4 w-full rounded-full bg-[linear-gradient(135deg,var(--hello-accent),#ec4899)] px-4 py-3 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(236,72,153,0.22)] transition hover:brightness-105 disabled:opacity-50"
            >
              Create Circle
            </button>
          </div>

          <div className={`mt-5 space-y-3 ${selectedCircle ? "" : "hidden"}`}>
            {circles.map((circle) => (
              <button
                key={circle.id}
                type="button"
                onClick={() => {
                  setSelectedCircleId(circle.id);
                  setSelectedEventId(null);
                  setView("events");
                }}
                className={`w-full rounded-3xl border p-4 text-left shadow-sm transition ${
                  selectedCircleId === circle.id ? "border-pink-400 bg-pink-50" : "border-[var(--hello-border)] bg-white/80"
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold">{circle.name}</div>
                    <div className="truncate text-xs text-[var(--hello-muted)]">{circle.memberCount} members</div>
                  </div>
                  <CircleActionsMenu
                    circle={circle}
                    currentUserId={currentUser.id}
                    open={openCircleMenuId === circle.id}
                    onToggle={() => setOpenCircleMenuId((current) => current === circle.id ? null : circle.id)}
                    onAction={(action) => handleCircleMenuAction(circle, action)}
                  />
                </div>
                <div className="mt-3 flex -space-x-2">
                  {circle.members.slice(0, 5).map((member) => (
                    <MemberAvatar key={`${circle.id}-${member.userId}`} member={member} />
                  ))}
                </div>
              </button>
            ))}
            {!loading && !circles.length ? (
              <div className="rounded-3xl border border-dashed border-[var(--hello-border)] bg-white/60 p-5 text-sm text-[var(--hello-muted)]">
                No circles yet. Create one and add chat contacts.
              </div>
            ) : null}
          </div>
        </aside>

        <section className="flex min-h-0 flex-col">
          {!selectedCircle ? (
            <CircleIndex
              circles={circles}
              loading={loading}
              currentUserId={currentUser.id}
              openMenuId={openCircleMenuId}
              onOpen={(circle) => {
                setSelectedCircleId(circle.id);
                setSelectedEventId(null);
                setMemberEditorOpen(false);
                setView("events");
              }}
              onToggleMenu={(circleId) => setOpenCircleMenuId((current) => current === circleId ? null : circleId)}
              onAction={(circle, action) => handleCircleMenuAction(circle, action)}
            />
          ) : (
            <>
              <div className="border-b border-[var(--hello-border)] px-5 py-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <h2 className="text-xl font-bold">{selectedCircle.name}</h2>
                    <p className="text-sm text-[var(--hello-muted)]">
                      {selectedCircle.members.map((member) => member.username ? `@${member.username}` : member.name || "Member").join(", ")}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button type="button" onClick={() => setView("events")} className={tabClass(view === "events")}>Events</button>
                    <button type="button" onClick={() => setView("items")} className={tabClass(view === "items")}>Drive</button>
                    <button type="button" onClick={() => setView("trash")} className={tabClass(view === "trash")}>Trash</button>
                  </div>
                </div>
              </div>

              <div className="min-h-0 flex-1 overflow-auto p-5">
                {memberEditorOpen ? (
                  <MemberManagementPanel
                    circle={selectedCircle}
                    contacts={circleContacts}
                    roles={memberEditorRoles}
                    onChangeRoles={setMemberEditorRoles}
                    onCancel={() => setMemberEditorOpen(false)}
                    onSave={() => void handleSaveMembers()}
                  />
                ) : null}

                {view === "events" ? (
                  <div className="space-y-4">
                    <div className="rounded-2xl border border-[var(--hello-border)] bg-white/80 p-4 shadow-sm">
                      <div className="mb-3 flex items-center gap-2 text-sm font-semibold">
                        <Plus className="h-4 w-4" />
                        Create Event
                      </div>
                      <div className="flex gap-3">
                        <input
                          value={eventName}
                          onChange={(event) => setEventName(event.target.value)}
                          placeholder="New event name"
                          className="flex-1 rounded-xl border border-[var(--hello-border)] bg-white px-3 py-2 text-sm outline-none"
                        />
                        <button
                          type="button"
                          onClick={() => void handleCreateEvent()}
                          disabled={!eventName.trim()}
                          className="rounded-full bg-[var(--hello-accent)] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                        >
                          Create
                        </button>
                      </div>
                    </div>

                    <div className="grid gap-3">
                      {events.map((event) => (
                        <div key={event.id} className="rounded-2xl border border-[var(--hello-border)] bg-white/80 p-4 shadow-sm">
                          <div className="flex items-center justify-between gap-3">
                            <button
                              type="button"
                              onClick={() => {
                                setSelectedEventId(event.id);
                                setView("items");
                              }}
                              className="min-w-0 text-left"
                            >
                              <div className="truncate text-sm font-semibold">{event.name}</div>
                              <div className="text-xs text-[var(--hello-muted)]">{event.itemCount} items</div>
                            </button>
                            <div className="flex gap-2">
                              {selectedCircleCanManage ? (
                                <button type="button" className="rounded-full px-3 py-1 text-xs font-medium text-[var(--hello-muted)] hover:bg-pink-50" onClick={() => void handleRenameEvent(event)}>Rename</button>
                              ) : null}
                              <button type="button" className="rounded-full px-3 py-1 text-xs font-medium text-red-600 hover:bg-red-50" onClick={() => void handleDeleteEvent(event)}>
                                {selectedCircleCanManage ? "Delete" : "Request delete"}
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                      {!events.length ? <EmptyState title="No events yet" subtitle="Create a shared event, then upload media into it." compact /> : null}
                    </div>

                    <div className="rounded-2xl border border-[var(--hello-border)] bg-white/80 p-4 shadow-sm">
                      <div className="mb-3 flex items-center gap-2 text-sm font-semibold">
                        <ShieldAlert className="h-4 w-4" />
                        Delete Polls
                      </div>
                      <div className="space-y-3">
                        {polls.map((poll) => (
                          <div key={poll.id} className="rounded-xl border border-[var(--hello-border)] bg-white p-3">
                            <div className="text-sm font-semibold">{poll.targetType === "circle" ? "Circle delete" : "Event delete"}</div>
                            <div className="text-xs text-[var(--hello-muted)]">
                              Status: {poll.status} · Delete {poll.deleteVotes || 0} · Keep {poll.keepVotes || 0}
                            </div>
                            {poll.status === "open" ? (
                              <div className="mt-3 flex gap-2">
                                <button type="button" className="rounded-full bg-red-500 px-3 py-1 text-xs font-semibold text-white" onClick={() => void handleVote(poll.id, "delete")}>Vote delete</button>
                                <button type="button" className="rounded-full bg-slate-200 px-3 py-1 text-xs font-semibold text-slate-700" onClick={() => void handleVote(poll.id, "keep")}>Keep</button>
                              </div>
                            ) : null}
                          </div>
                        ))}
                        {!polls.length ? <div className="text-sm text-[var(--hello-muted)]">No delete polls in this circle.</div> : null}
                      </div>
                    </div>
                  </div>
                ) : null}

                {view === "items" ? (
                  <DriveItemsGrid
                    title={selectedEvent?.name || `${selectedCircle.name} Drive`}
                    subtitle={selectedEvent ? "Shared files in this event" : "All files shared in this circle"}
                    items={items}
                    favoriteIds={favoriteIds}
                    onToggleFavorite={(item) => void handleToggleFavorite(item)}
                    onDelete={(item) => void handleDeleteItem(item)}
                  />
                ) : null}

                {view === "trash" ? (
                  <DriveItemsGrid
                    title="Shared Trash"
                    subtitle="Deleted items remain visible to circle members."
                    items={trashItems}
                    favoriteIds={favoriteIds}
                    trashMode
                    onToggleFavorite={(item) => void handleToggleFavorite(item)}
                    onRestore={(item) => void handleRestoreItem(item)}
                    onPermanentDelete={(item) => void handlePermanentDelete(item)}
                  />
                ) : null}
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  );
}

function CircleIndex(props: {
  circles: DriveCircle[];
  loading: boolean;
  currentUserId: string;
  openMenuId: string | null;
  onOpen: (circle: DriveCircle) => void;
  onToggleMenu: (circleId: string) => void;
  onAction: (circle: DriveCircle, action: CircleMenuAction) => void;
}) {
  const { circles, loading, currentUserId, openMenuId, onOpen, onToggleMenu, onAction } = props;
  return (
    <div className="min-h-0 overflow-auto p-5 custom-scrollbar">
      <div className="mb-5 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold">Your Circles</h2>
          <p className="text-sm text-[var(--hello-muted)]">Every box opens a separate circle Drive with its own events, files, members, and delete requests.</p>
        </div>
        <div className="rounded-full border border-[var(--hello-border)] bg-white px-3 py-1 text-xs font-semibold text-[var(--hello-muted)]">
          {circles.length} circles
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 2xl:grid-cols-3">
        {circles.map((circle) => (
          <CircleBox
            key={circle.id}
            circle={circle}
            currentUserId={currentUserId}
            menuOpen={openMenuId === circle.id}
            onOpen={() => onOpen(circle)}
            onToggleMenu={() => onToggleMenu(circle.id)}
            onAction={(action) => onAction(circle, action)}
          />
        ))}
      </div>
      {!loading && !circles.length ? (
        <EmptyState title="No circles yet" subtitle="Create a circle, add people, and their shared Drive will appear here." />
      ) : null}
    </div>
  );
}

function CircleBox(props: {
  circle: DriveCircle;
  currentUserId: string;
  menuOpen: boolean;
  onOpen: () => void;
  onToggleMenu: () => void;
  onAction: (action: CircleMenuAction) => void;
}) {
  const { circle, currentUserId, menuOpen, onOpen, onToggleMenu, onAction } = props;
  const myRole = circle.members.find((member) => member.userId === currentUserId)?.role;
  const admin = canManageCircle(circle, currentUserId);
  return (
    <div className="group relative rounded-[8px] border border-[var(--hello-border)] bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-pink-200 hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-left">
          <div className="flex items-center gap-3">
            {circle.avatarUrl ? (
              <img src={circle.avatarUrl} alt={circle.name} className="h-11 w-11 shrink-0 rounded-[8px] object-cover" />
            ) : (
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[8px] bg-pink-50 text-pink-600">
                <FolderOpen className="h-5 w-5" />
              </div>
            )}
            <div className="min-w-0">
              <div className="truncate text-sm font-bold text-[var(--hello-text)]">{circle.name}</div>
              <div className="truncate text-xs text-[var(--hello-muted)]">{circle.memberCount} members - {roleLabel(myRole)}</div>
            </div>
          </div>
        </button>
        <CircleActionsMenu
          circle={circle}
          currentUserId={currentUserId}
          open={menuOpen}
          onToggle={onToggleMenu}
          onAction={onAction}
        />
      </div>
      <button type="button" onClick={onOpen} className="mt-4 block w-full text-left">
        <div className="flex -space-x-2">
          {circle.members.slice(0, 6).map((member) => (
            <MemberAvatar key={`${circle.id}-${member.userId}`} member={member} />
          ))}
        </div>
        <div className="mt-4 flex items-center justify-between gap-3">
          <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
            {admin ? <Crown className="h-3 w-3" /> : <Eye className="h-3 w-3" />}
            {admin ? "Admin tools" : "Viewer access"}
          </span>
          <span className="text-xs font-semibold text-pink-600">Open Drive</span>
        </div>
      </button>
    </div>
  );
}

function CircleActionsMenu(props: {
  circle: DriveCircle;
  currentUserId: string;
  open: boolean;
  onToggle: () => void;
  onAction: (action: CircleMenuAction) => void;
}) {
  const { circle, currentUserId, open, onToggle, onAction } = props;
  const admin = canManageCircle(circle, currentUserId);
  return (
    <div className="relative">
      <button
        type="button"
        className="rounded-full p-2 text-[var(--hello-muted)] hover:bg-pink-50"
        onClick={(event) => {
          event.stopPropagation();
          onToggle();
        }}
        title="Circle actions"
      >
        <MoreVertical className="h-4 w-4" />
      </button>
      {open ? (
        <div className="absolute right-0 top-10 z-20 w-56 overflow-hidden rounded-[8px] border border-[var(--hello-border)] bg-white py-1 text-sm shadow-xl">
          {admin ? (
            <>
              <MenuButton icon={<FolderOpen className="h-4 w-4" />} label="Rename circle" onClick={() => onAction("rename")} />
              <MenuButton icon={<ImageIcon className="h-4 w-4" />} label="Profile picture" onClick={() => onAction("avatar")} />
              <MenuButton icon={<UserCog className="h-4 w-4" />} label="Members and roles" onClick={() => onAction("members")} />
              <MenuButton danger icon={<Trash2 className="h-4 w-4" />} label="Delete circle" onClick={() => onAction("delete")} />
            </>
          ) : (
            <MenuButton danger icon={<ShieldAlert className="h-4 w-4" />} label="Request delete" onClick={() => onAction("request-delete")} />
          )}
          <MenuButton icon={<LogOut className="h-4 w-4" />} label="Leave circle" onClick={() => onAction("leave")} />
        </div>
      ) : null}
    </div>
  );
}

function MenuButton({ icon, label, danger = false, onClick }: { icon: ReactNode; label: string; danger?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      className={`flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-pink-50 ${danger ? "text-red-600" : "text-[var(--hello-text)]"}`}
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
    >
      {icon}
      <span>{label}</span>
    </button>
  );
}

function MemberManagementPanel(props: {
  circle: DriveCircle;
  contacts: User[];
  roles: Record<string, MemberRole>;
  onChangeRoles: (roles: Record<string, MemberRole>) => void;
  onCancel: () => void;
  onSave: () => void;
}) {
  const { circle, contacts, roles, onChangeRoles, onCancel, onSave } = props;
  const ownerId = circle.ownerUserId;
  return (
    <div className="mb-5 rounded-[8px] border border-pink-200 bg-white p-4 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <div className="text-sm font-bold">Members and roles</div>
          <div className="text-xs text-[var(--hello-muted)]">Admins can rename, upload, manage people, and delete. Viewers can open the Drive and request deletion.</div>
        </div>
        <div className="flex gap-2">
          <button type="button" className="rounded-full border border-[var(--hello-border)] px-4 py-2 text-xs font-semibold" onClick={onCancel}>Cancel</button>
          <button type="button" className="rounded-full bg-[var(--hello-accent)] px-4 py-2 text-xs font-semibold text-white" onClick={onSave}>Save roles</button>
        </div>
      </div>
      <div className="grid gap-2 md:grid-cols-2">
        {contacts.map((contact) => {
          const isOwner = contact.id === ownerId;
          const checked = isOwner || Boolean(roles[contact.id]);
          return (
            <label key={contact.id} className={`flex items-center gap-3 rounded-[8px] border px-3 py-3 ${checked ? "border-pink-200 bg-pink-50/70" : "border-[var(--hello-border)] bg-white"}`}>
              <input
                type="checkbox"
                disabled={isOwner}
                checked={checked}
                onChange={() => {
                  const next = { ...roles };
                  if (roles[contact.id]) delete next[contact.id];
                  else next[contact.id] = "view";
                  onChangeRoles(next);
                }}
              />
              <Avatar user={contact} />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-semibold">{contact.name}</div>
                <div className="truncate text-xs text-[var(--hello-muted)]">{isOwner ? "Creator admin" : contact.username ? `@${contact.username}` : contact.id}</div>
              </div>
              {isOwner ? (
                <span className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-pink-600">Owner</span>
              ) : checked ? (
                <select
                  value={roles[contact.id] || "view"}
                  onChange={(event) => onChangeRoles({ ...roles, [contact.id]: event.target.value as MemberRole })}
                  className="rounded-full border border-[var(--hello-border)] bg-white px-3 py-1.5 text-xs font-semibold outline-none"
                >
                  <option value="manage">Admin</option>
                  <option value="add">Contributor</option>
                  <option value="view">Viewer</option>
                </select>
              ) : null}
            </label>
          );
        })}
      </div>
    </div>
  );
}

function DriveItemsGrid(props: {
  title: string;
  subtitle: string;
  items: DriveItem[];
  favoriteIds: Set<string>;
  trashMode?: boolean;
  onToggleFavorite: (item: DriveItem) => void;
  onDelete?: (item: DriveItem) => void;
  onRestore?: (item: DriveItem) => void;
  onPermanentDelete?: (item: DriveItem) => void;
}) {
  const { title, subtitle, items, favoriteIds, trashMode, onToggleFavorite, onDelete, onRestore, onPermanentDelete } = props;
  const [activeItemId, setActiveItemId] = useState<string | null>(null);
  const activeIndex = useMemo(() => items.findIndex((item) => item.id === activeItemId), [items, activeItemId]);
  const activeItem = activeIndex >= 0 ? items[activeIndex] : null;
  if (!items.length) return <EmptyState title={title} subtitle={`No items here yet. ${subtitle}`} compact />;
  return (
    <div>
      <div className="mb-4">
        <h3 className="text-lg font-bold">{title}</h3>
        <p className="text-sm text-[var(--hello-muted)]">{subtitle}</p>
      </div>
      <div className="rounded-[34px] border border-white/80 bg-white/90 p-3 shadow-[0_30px_90px_rgba(15,23,42,0.12)] backdrop-blur">
        <div className="grid auto-rows-[82px] grid-cols-2 gap-3 md:auto-rows-[92px] md:grid-cols-4 xl:auto-rows-[110px] xl:grid-cols-6">
          {items.map((item) => {
            const isFavorite = favoriteIds.has(item.id);
            const tile = driveCollageTile(item.id, item.type);
            return (
              <article
                key={item.id}
                className={`group overflow-hidden border border-white/80 bg-white/75 shadow-[0_18px_40px_rgba(15,23,42,0.08)] ${tile.shellClass}`}
                style={{ borderRadius: tile.radius }}
              >
                <button
                  type="button"
                  className={`relative block w-full overflow-hidden bg-slate-100 text-left ${tile.mediaClass}`}
                  onClick={() => setActiveItemId(item.id)}
                >
                  {item.type === "video" ? (
                    <>
                      <video className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]" src={item.thumbnailUrl || item.url} muted playsInline />
                      <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(15,23,42,0.02),rgba(15,23,42,0.38))]" />
                      <div className="absolute bottom-3 left-3 inline-flex items-center gap-1 rounded-full bg-black/55 px-3 py-1 text-[11px] font-semibold text-white">
                        <ImageIcon className="h-3 w-3" />
                        Video
                      </div>
                    </>
                  ) : (
                    <>
                      <img className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]" src={item.thumbnailUrl || item.url} alt={item.originalName || "Drive item"} />
                      <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(255,255,255,0),rgba(15,23,42,0.08))]" />
                    </>
                  )}
                </button>
                <div className="space-y-3 p-4">
                  <div>
                    <div className="truncate text-sm font-semibold">{item.originalName || item.eventName || "Untitled item"}</div>
                    <div className="text-xs text-[var(--hello-muted)]">{item.eventName || "No event"}{item.favorite ? " - Favorite" : ""}</div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button type="button" className={`rounded-full px-3 py-1 text-xs font-semibold ${isFavorite ? "bg-pink-100 text-pink-700" : "bg-slate-100 text-slate-700"}`} onClick={() => onToggleFavorite(item)}>
                      <Heart className="mr-1 inline h-3 w-3" />
                      {isFavorite ? "Loved" : "Love"}
                    </button>
                    {!trashMode && onDelete ? (
                      <button type="button" className="rounded-full bg-red-50 px-3 py-1 text-xs font-semibold text-red-600" onClick={() => onDelete(item)}>Delete</button>
                    ) : null}
                    {trashMode && onRestore ? (
                      <button type="button" className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700" onClick={() => onRestore(item)}>Restore</button>
                    ) : null}
                    {trashMode && onPermanentDelete ? (
                      <button type="button" className="rounded-full bg-red-50 px-3 py-1 text-xs font-semibold text-red-600" onClick={() => onPermanentDelete(item)}>Permanent</button>
                    ) : null}
                  </div>
                </div>
            </article>
          );
        })}
        </div>
      </div>
      {activeItem ? (
        <DriveItemLightbox
          item={activeItem}
          isFavorite={favoriteIds.has(activeItem.id)}
          trashMode={trashMode}
          hasPrevious={activeIndex > 0}
          hasNext={activeIndex < items.length - 1}
          onClose={() => setActiveItemId(null)}
          onPrevious={() => activeIndex > 0 && setActiveItemId(items[activeIndex - 1]?.id || null)}
          onNext={() => activeIndex < items.length - 1 && setActiveItemId(items[activeIndex + 1]?.id || null)}
          onToggleFavorite={() => onToggleFavorite(activeItem)}
          onDelete={onDelete ? () => onDelete(activeItem) : undefined}
          onRestore={onRestore ? () => onRestore(activeItem) : undefined}
          onPermanentDelete={onPermanentDelete ? () => onPermanentDelete(activeItem) : undefined}
        />
      ) : null}
    </div>
  );
}

function DriveItemLightbox(props: {
  item: DriveItem;
  isFavorite: boolean;
  trashMode?: boolean;
  hasPrevious: boolean;
  hasNext: boolean;
  onClose: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onToggleFavorite: () => void;
  onDelete?: () => void;
  onRestore?: () => void;
  onPermanentDelete?: () => void;
}) {
  const {
    item,
    isFavorite,
    trashMode,
    hasPrevious,
    hasNext,
    onClose,
    onPrevious,
    onNext,
    onToggleFavorite,
    onDelete,
    onRestore,
    onPermanentDelete,
  } = props;

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
      if (event.key === "ArrowLeft" && hasPrevious) onPrevious();
      if (event.key === "ArrowRight" && hasNext) onNext();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [hasNext, hasPrevious, onClose, onNext, onPrevious]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/82 p-4 backdrop-blur-sm" onClick={onClose}>
      <div className="relative flex max-h-[92vh] w-full max-w-6xl flex-col overflow-hidden rounded-[32px] border border-white/10 bg-slate-950 text-white shadow-[0_35px_120px_rgba(15,23,42,0.65)]" onClick={(event) => event.stopPropagation()}>
        <div className="flex items-center justify-between gap-3 border-b border-white/10 px-5 py-4">
          <div className="min-w-0">
            <div className="truncate text-base font-semibold">{item.originalName || item.eventName || "Drive media"}</div>
            <div className="text-xs text-white/60">{item.eventName || "Circle Drive"} - {formatDriveDate(item.createdAt)}</div>
          </div>
          <button type="button" className="rounded-full p-2 text-white/80 hover:bg-white/10 hover:text-white" onClick={onClose}>
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="relative flex min-h-0 flex-1 items-center justify-center bg-[radial-gradient(circle_at_top,rgba(255,255,255,0.08),transparent_48%)] px-3 py-4 sm:px-5">
          <button type="button" onClick={onPrevious} disabled={!hasPrevious} className="absolute left-3 top-1/2 z-10 -translate-y-1/2 rounded-full bg-black/45 p-3 text-white transition hover:bg-black/65 disabled:cursor-not-allowed disabled:opacity-35">
            <ChevronLeft className="h-5 w-5" />
          </button>
          <div className="flex max-h-full w-full items-center justify-center overflow-hidden rounded-[28px] border border-white/10 bg-black/30">
            {item.type === "video" ? (
              <video className="max-h-[68vh] w-full bg-black object-contain" src={item.url} controls autoPlay playsInline />
            ) : (
              <img className="max-h-[68vh] w-full object-contain" src={item.url} alt={item.originalName || "Drive item"} />
            )}
          </div>
          <button type="button" onClick={onNext} disabled={!hasNext} className="absolute right-3 top-1/2 z-10 -translate-y-1/2 rounded-full bg-black/45 p-3 text-white transition hover:bg-black/65 disabled:cursor-not-allowed disabled:opacity-35">
            <ChevronRight className="h-5 w-5" />
          </button>
        </div>
        <div className="flex flex-wrap items-center gap-2 border-t border-white/10 px-5 py-4">
          {!trashMode ? (
            <>
              <button type="button" className={`rounded-full px-4 py-2 text-sm font-semibold ${isFavorite ? "bg-pink-500 text-white" : "bg-white/10 text-white"}`} onClick={onToggleFavorite}>
                <Heart className="mr-2 inline h-4 w-4" />
                {isFavorite ? "Loved" : "Love"}
              </button>
              {onDelete ? (
                <button type="button" className="rounded-full bg-red-500/90 px-4 py-2 text-sm font-semibold text-white" onClick={onDelete}>Move to trash</button>
              ) : null}
            </>
          ) : (
            <>
              {onRestore ? (
                <button type="button" className="rounded-full bg-emerald-500/90 px-4 py-2 text-sm font-semibold text-white" onClick={onRestore}>Restore</button>
              ) : null}
              {onPermanentDelete ? (
                <button type="button" className="rounded-full bg-red-500/90 px-4 py-2 text-sm font-semibold text-white" onClick={onPermanentDelete}>Delete forever</button>
              ) : null}
            </>
          )}
          <a href={item.url} download={item.originalName || "family-drive-media"} className="rounded-full bg-white/10 px-4 py-2 text-sm font-semibold text-white">
            <Download className="mr-2 inline h-4 w-4" />
            Download
          </a>
        </div>
      </div>
    </div>
  );
}

function driveCollageTile(itemId: string, itemType: DriveItem["type"]) {
  const patterns = [
    { shellClass: "col-span-2 row-span-3 md:col-span-2 xl:col-span-3", mediaClass: "aspect-[1.52/1]", radius: "34px 34px 26px 26px" },
    { shellClass: "col-span-2 row-span-2 md:col-span-2 xl:col-span-2", mediaClass: "aspect-[1.05/1]", radius: "28px 36px 26px 30px" },
    { shellClass: "col-span-1 row-span-2 xl:col-span-1", mediaClass: "aspect-[0.82/1]", radius: "32px 24px 32px 20px" },
    { shellClass: "col-span-1 row-span-3 xl:col-span-1", mediaClass: "aspect-[0.72/1]", radius: "24px 34px 24px 34px" },
    { shellClass: "col-span-2 row-span-2 md:col-span-2 xl:col-span-2", mediaClass: "aspect-[1.18/1]", radius: "30px 24px 34px 26px" },
    { shellClass: "col-span-1 row-span-2 xl:col-span-1", mediaClass: "aspect-[0.84/1]", radius: "22px 30px 20px 32px" },
  ];
  const hash = Array.from(itemId).reduce((sum, char) => sum + char.charCodeAt(0), 0);
  const base = patterns[hash % patterns.length];
  if (itemType === "video") {
    return { ...base, mediaClass: "aspect-[1/1]" };
  }
  return base;
}

function formatDriveDate(timestamp?: number | null) {
  if (!timestamp) return "Saved recently";
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(timestamp);
}

function EmptyState({ title, subtitle, compact = false }: { title: string; subtitle: string; compact?: boolean }) {
  return (
    <div className={`rounded-2xl border border-dashed border-[var(--hello-border)] bg-white/70 text-center ${compact ? "p-6" : "m-6 p-10"}`}>
      <ImageIcon className="mx-auto h-8 w-8 text-[var(--hello-muted)]" />
      <h3 className="mt-3 text-lg font-bold">{title}</h3>
      <p className="mt-2 text-sm text-[var(--hello-muted)]">{subtitle}</p>
    </div>
  );
}

function Avatar({ user }: { user: User }) {
  if (user.avatar) {
    return <img src={user.avatar} alt={user.name} className="h-10 w-10 rounded-full object-cover" />;
  }
  return <div className="flex h-10 w-10 items-center justify-center rounded-full bg-pink-100 text-sm font-bold text-pink-700">{user.name.slice(0, 1).toUpperCase()}</div>;
}

function MemberAvatar({ member }: { member: DriveCircle["members"][number] }) {
  if (member.avatar) {
    return <img src={member.avatar} alt={member.name || member.username || member.userId} className="h-8 w-8 rounded-full border-2 border-white object-cover" title={member.username ? `@${member.username}` : member.name || "Member"} />;
  }
  const label = member.username || member.name || member.userId;
  return <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-pink-100 text-[10px] font-bold text-pink-700" title={label}>{label.slice(0, 1).toUpperCase()}</div>;
}

function displayHandle(user: User) {
  return user.username ? `@${user.username}` : user.name;
}

function normalizeMemberRole(role?: string | null): MemberRole {
  if (role === "owner" || role === "manage") return "manage";
  if (role === "add") return "add";
  return "view";
}

function canManageCircle(circle: DriveCircle, userId: string) {
  const member = circle.members.find((candidate) => candidate.userId === userId);
  return circle.ownerUserId === userId || member?.role === "owner" || member?.role === "manage";
}

function canUploadToCircle(circle: DriveCircle, userId: string) {
  const member = circle.members.find((candidate) => candidate.userId === userId);
  return circle.ownerUserId === userId || member?.role === "owner" || member?.role === "manage" || member?.role === "add";
}

function roleLabel(role?: string | null) {
  if (role === "owner") return "Creator admin";
  if (role === "manage") return "Admin";
  if (role === "add") return "Contributor";
  return "Viewer";
}

function tabClass(active: boolean) {
  return `rounded-full px-4 py-2 text-sm font-semibold ${active ? "bg-[var(--hello-accent)] text-white" : "bg-white text-[var(--hello-text)]"}`;
}
