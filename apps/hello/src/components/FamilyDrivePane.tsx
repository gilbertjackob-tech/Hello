import { useEffect, useMemo, useState } from "react";
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
  uploadDriveFiles,
  voteDriveDeletePoll,
} from "../api";
import type { Chat, DriveCircle, DriveDeletePoll, DriveEvent, DriveItem, User } from "../types";
import { Heart, Image as ImageIcon, LogOut, Plus, ShieldAlert, Trash2, Users } from "lucide-react";

interface FamilyDrivePaneProps {
  currentUser: User;
  visible: boolean;
}

type DriveView = "circles" | "events" | "items" | "trash";
type MemberRole = "view" | "add" | "manage";

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
      setSelectedCircleId((current) => current && nextCircles.some((circle) => circle.id === current) ? current : nextCircles[0]?.id || null);
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
    setView("circles");
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

  if (!visible) return null;

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--hello-surface)] text-[var(--hello-text)]">
      <div className="border-b border-[var(--hello-border)] px-5 py-4">
        <div className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--hello-muted)]">Hello Drive</div>
        <div className="mt-1 flex items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-extrabold">All Circles</h1>
            <p className="text-sm text-[var(--hello-muted)]">Shared media lives inside circles and events.</p>
          </div>
          {selectedCircleId ? (
            <label className="inline-flex cursor-pointer items-center gap-2 rounded-full bg-[var(--hello-accent)] px-4 py-2 text-sm font-semibold text-white">
              <Plus className="h-4 w-4" />
              {uploading ? "Uploading..." : "Upload"}
              <input className="hidden" type="file" accept="image/*,video/*" multiple onChange={(event) => void handleUpload(event.target.files)} />
            </label>
          ) : null}
        </div>
      </div>

      <div className="grid min-h-0 flex-1 gap-0 overflow-hidden md:grid-cols-[360px_minmax(0,1fr)]">
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
                        <option value="view">Can view</option>
                        <option value="add">Can add</option>
                        <option value="manage">Can manage</option>
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

          <div className="mt-5 space-y-3">
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
                  <div className="flex gap-2">
                    <button
                      type="button"
                      className="rounded-full p-2 text-[var(--hello-muted)] hover:bg-white"
                      onClick={(event) => {
                        event.stopPropagation();
                        void handleDeleteCircle(circle);
                      }}
                      title="Delete circle"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      className="rounded-full p-2 text-[var(--hello-muted)] hover:bg-white"
                      onClick={(event) => {
                        event.stopPropagation();
                        void handleLeaveCircle(circle);
                      }}
                      title="Leave circle"
                    >
                      <LogOut className="h-4 w-4" />
                    </button>
                  </div>
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
            <EmptyState title="Select a circle" subtitle="Circles replace the old global photo library. Pick one to manage events and uploads." />
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
                    <button type="button" onClick={() => setView("items")} className={tabClass(view === "items")}>Media</button>
                    <button type="button" onClick={() => setView("trash")} className={tabClass(view === "trash")}>Trash</button>
                  </div>
                </div>
              </div>

              <div className="min-h-0 flex-1 overflow-auto p-5">
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
                              <button type="button" className="rounded-full px-3 py-1 text-xs font-medium text-[var(--hello-muted)] hover:bg-pink-50" onClick={() => void handleRenameEvent(event)}>Rename</button>
                              <button type="button" className="rounded-full px-3 py-1 text-xs font-medium text-red-600 hover:bg-red-50" onClick={() => void handleDeleteEvent(event)}>Delete</button>
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
                    title={selectedEvent?.name || "Circle media"}
                    subtitle={selectedEvent ? "Shared media in this event" : "All shared media in this circle"}
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
  if (!items.length) return <EmptyState title={title} subtitle={`No items here yet. ${subtitle}`} compact />;
  return (
    <div>
      <div className="mb-4">
        <h3 className="text-lg font-bold">{title}</h3>
        <p className="text-sm text-[var(--hello-muted)]">{subtitle}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {items.map((item) => {
          const isFavorite = favoriteIds.has(item.id);
          return (
            <div key={item.id} className="overflow-hidden rounded-2xl border border-[var(--hello-border)] bg-white shadow-sm">
              <div className="aspect-[4/3] bg-slate-100">
                {item.type === "video" ? (
                  <video className="h-full w-full object-cover" src={item.thumbnailUrl || item.url} muted playsInline />
                ) : (
                  <img className="h-full w-full object-cover" src={item.thumbnailUrl || item.url} alt={item.originalName || "Drive item"} />
                )}
              </div>
              <div className="space-y-3 p-4">
                <div>
                  <div className="truncate text-sm font-semibold">{item.originalName || item.eventName || "Untitled item"}</div>
                  <div className="text-xs text-[var(--hello-muted)]">{item.eventName || "No event"}{item.favorite ? " · Favorite" : ""}</div>
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
            </div>
          );
        })}
      </div>
    </div>
  );
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

function tabClass(active: boolean) {
  return `rounded-full px-4 py-2 text-sm font-semibold ${active ? "bg-[var(--hello-accent)] text-white" : "bg-white text-[var(--hello-text)]"}`;
}
