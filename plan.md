• Proposed Plan


  # Android Hello Chat Premium Room Refactor

  ## Summary

  - Work only in apps/android; do not edit apps/hello or apps/browser.
  - First fix current baseline compile failures in ChatRoomScreen.kt: invalid return@onTyping labels and the AnimatedVisibility receiver error.
  - Refactor the room into focused Compose files under com.glassbox.hello.chat.components, while keeping backend routes and existing HelloApi wire
    contracts unchanged.
  - Preserve real behavior only: no fake download states, no unsupported rich link scraping, no non-working buttons.

  ## Key Changes

  - ChatRoomScreen.kt becomes the coordinator for state, launchers, socket registration, send/reply/delete actions, media viewer state, and call
    overlays.
  - Add component files for ChatHeader, MessageList, MessageBubble, ChatComposer, AttachmentTray, MessageActionSheet, MediaCards, and MediaViewer; remove
    or stop using the old corrupted/duplicate bubble code.
  - Add lightweight chat UI helpers for grouping, date separators, sender proximity, best-effort unread separator, deleted-for-me filtering, pinned/
    starred status, reaction grouping, file type labels, and URL detection.
  - Improve ChatViewModel without breaking callers by adding optional optimistic temp-id replacement support for text and attachment sends, plus a
    defensive pagination guard when the backend returns duplicate/full history.
  - Extend SocketManager with scoped listener registration for message updates and typing, keeping existing callback properties compatible;
    ChatRoomScreen will register/remove listeners in DisposableEffect.

  ## Behavior

  - Message list: compact premium bubbles, sender/time grouping, day separators, bubble tails, inline reactions, reply preview, deleted state, pinned/
    starred badges, status ticks, jump-to-bottom, load-older trigger, smooth safe animations, and no duplicate optimistic/server messages.
  - Realtime: consume receive_message, message_updated, and user_typing; emit debounced typing start/stop; emit mark_messages_read on room open, relevant
    incoming messages, and visible/latest message changes.
    record voice flow with timer/cancel/send, and smooth IME padding.
  - Media: images open in full-screen viewer; file/PDF/doc cards open via ACTION_VIEW; audio uses MediaPlayer play/pause/progress with lifecycle cleanup;
  - No real download progress because the current app opens backend URLs through Android intents and has no download manager/progress API.
  - No edited indicator unless an existing message field is discovered during implementation; current ChatModels.Message has no edited timestamp/flag.
  - Contact sharing remains text-backed because there is no structured contact message schema.
  - Audio waveform is visual/progress-based, not decoded waveform analysis.

  ## Test Plan

  - Run .\gradlew.bat :app:compileDebugKotlin after implementation.
  - Manual checks: open chat, send/receive text, verify realtime update, typing indicator, reactions, reply preview, copy, star/unstar, pin/unpin, delete
    modes, image send/viewer, file/PDF/doc open card, audio send/play, location/contact/link cards, jump-to-bottom, load older, keyboard layout,
    reconnect without duplicate listeners, and rotation/recreate if supported.
  - Baseline note: current compile already fails in ChatRoomScreen.kt; those errors are part of the first implementation patch.

  ## Assumptions

  - Existing Hello dark teal identity remains the visual base, upgraded rather than cloned from WhatsApp/Messenger.
  - Existing dirty working-tree changes are user-owned and must not be reverted.
  - Backend/socket event names found during audit are authoritative: receive_message, message_updated, user_typing, typing, mark_messages_read,
    join_chat, and leave_chat.
