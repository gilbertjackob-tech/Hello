# Hello — Notification System Design
*Calls · Chats · Stories · Reactions*

---

## Principles

1. **Calls always get through.** No other notification does.
2. **Messages feel personal, never spammy.** Bundle aggressively after the first.
3. **Status activity is warm, never urgent.** Soft delivery, never during quiet hours.
4. **Copy sounds like a person sent it**, not a system.
5. **User controls everything.** Per-category, per-contact, per-group.

---

## Notification Categories & Android Channels

Android 8+ requires declared channels. These are ours:

| Channel ID | Label | Importance | Sound | Vibration | Bypass DND |
|---|---|---|---|---|---|
| `calls` | Incoming Calls | URGENT | Ringtone | Strong | Yes (default) |
| `missed_calls` | Missed Calls | HIGH | Short chime | Light | No |
| `messages` | Messages | HIGH | Message tone | Yes | No |
| `mentions` | Mentions & Replies | HIGH | Message tone | Yes | No |
| `status_posts` | New Moments | DEFAULT | Soft chime | No | No |
| `status_activity` | Reactions & Views | DEFAULT | None | No | No |
| `system` | App Updates | LOW | None | No | No |
| `re_engagement` | Family Nudges | LOW | None | No | No |

Web Push uses the same logical categories, mapped to Notification API `tag` for grouping.

---

## 1. Calls

Calls get the highest priority in the entire system. A missed family call is a real event.

### Incoming Call

- **Channel:** `calls`
- **UI:** Full-screen intent on Android (locks screen taken over). Heads-up on web.
- **Sound:** Ringtone (user-selectable, default: warm tone, not harsh)
- **Vibration:** Strong repeating pattern
- **Bypass DND:** Yes by default. User can disable in settings.
- **Lock screen:** Visible even when phone is locked
- **Actions:** `Answer` (green button) · `Decline` (red button) · `Message` (quick reply)
- **Copy:**
  ```
  [Name] is calling...
  ```
- **Timeout:** Rings for 45 seconds, then auto-declines and fires missed call notification
- **If already in a call:** Shows heads-up "Incoming call from [Name]" with option to switch

### Missed Call

- **Channel:** `missed_calls`
- **Sound:** Single short chime
- **Vibration:** One short pulse
- **Actions:** `Call back` · `Send message`
- **Copy:**
  ```
  Missed call from [Name]
  [timestamp]
  ```
- **Grouping:** If 3 missed calls from same person: "3 missed calls from [Name]"
- **Deep link:** Opens call screen with [Name] pre-dialled

### Call Ended (Multi-Device)

- No notification. Silently update call log in app only.

---

## 2. Chats

### New Message — 1:1

- **Channel:** `messages`
- **Sound:** Yes (first message in a session)
- **Subsequent messages:** No sound for first 3 subsequent messages, then silence for that thread
- **Copy:**
  ```
  [Name]
  [Message preview — up to 50 chars, truncate with …]
  ```
- **Expanded view (Android):** Shows last 3 messages in the thread
- **Actions:** `Reply` (inline text field) · `Mark as read`
- **Grouping rule:** After 2 unread from same person, bundle:
  ```
  [Name] · 4 messages
  [Latest preview]
  ```
- **Deep link:** Opens that chat thread, scrolled to unread

### New Message — Group

- **Channel:** `messages`
- **Sound:** Yes (first message only per session)
- **Copy:**
  ```
  [Group name]
  [Sender name]: [Message preview]
  ```
- **Grouping:** Bundle after 3 messages:
  ```
  [Group name] · 6 new messages
  [Latest sender]: [latest preview]
  ```
- **Muted groups:** No notification at all (not even silent). Mute badge shown in app.

### Reply to Your Message

- **Channel:** `mentions` (higher priority than regular group message)
- **Sound:** Yes
- **Copy:**
  ```
  [Name] replied to you
  "[Your message preview]" → "[Their reply preview]"
  ```
- **Never bundled.** Always individual. Replies to you are personal.

### Mention (@you in a group)

- **Channel:** `mentions`
- **Sound:** Yes
- **Copy:**
  ```
  [Name] mentioned you in [Group]
  "[Message with @mention]"
  ```
- **Overrides group mute.** Mentions always notify even in muted groups. (Toggleable.)

### Message Reaction

- **Channel:** `status_activity` (low-key, no sound)
- **Sound:** None
- **Copy:**
  ```
  [Name] reacted [emoji] to your message
  ```
- **Bundling:** After 3 reactions to the same message:
  ```
  [Name] and 2 others reacted to your message
  ```
- **After 10 reactions:** Stop notifying. User is popular, they know.
- **Deep link:** Opens chat, scrolled to that message, reactions tray open

### Voice/Media Message

- Treated same as text message, copy changes:
  ```
  [Name] sent you a voice message 🎙️
  [Name] sent a photo 📷
  [Name] sent a video 🎬
  ```

---

## 3. Status / Stories

Status notifications are warm and optional-feeling. Never urgent. Never during quiet hours.

### Someone Posts a New Status

- **Channel:** `status_posts`
- **Sound:** Soft chime
- **Copy:**
  ```
  [Name] just posted a moment ☀️
  ```
- **Throttle:** Max **1 notification per contact per 6 hours**. If they post 3 times in
  an hour, you get one notification for the first, the rest appear silently in-feed.
- **Bundling (if 3+ people post while phone is idle):**
  ```
  Mum, Dad and 2 others posted moments today
  ```
- **Deep link:** Opens Today Pulse feed, auto-scrolled to that person's ring

### Reaction to Your Status

- **Channel:** `status_activity`
- **Sound:** None
- **Copy:**
  ```
  [Name] reacted [emoji] to your moment
  ```
- **Bundling:** After 3 reactions:
  ```
  [Name] and 2 others reacted to your moment
  ```
- **Deep link:** Opens your own status viewer, reactions tray open

### Reply to Your Status

- **Channel:** `messages` (same priority as a chat message — it IS a conversation)
- **Sound:** Yes
- **Copy:**
  ```
  [Name] replied to your status
  "[Reply preview]"
  ```
- **Never bundled.** Always individual.

### Viewer Milestone

- **Channel:** `status_activity`
- **Sound:** None
- **Only fire at thresholds:** 5, 10, everyone in household
- **Copy:**
  ```
  5 family members have seen your moment 👀
  Everyone's seen your moment ✓
  ```

### Add Yours Chain Invite

- **Channel:** `status_posts`
- **Sound:** Soft chime
- **Copy:**
  ```
  [Name] started a chain: "[Chain Title]"
  Add your moment before it expires
  ```
- **Actions:** `Add Mine` (deep link straight to camera) · `View Chain`

### PC Archive Completed

- **Channel:** `system`
- **Sound:** None
- **Frequency:** Max once per day, only if statuses were actually archived
- **Copy:**
  ```
  Today's moments are safely saved to your PC 🔒
  ```
- **Deep link:** Opens PC archive section

### Re-engagement Nudge (Hasn't Posted in 3+ Days)

- **Channel:** `re_engagement`
- **Sound:** None
- **Copy (rotated, never same message twice in a row):**
  ```
  The family hasn't heard from you in a while 👋
  Share a moment — even a quick one
  What's going on today? The family would love to know
  ```
- **Rules:**
  - Only fire if user has been active (opened app) in last 7 days — don't nudge inactive users
  - Max once every 3 days
  - Never on Friday evening / weekends (configurable)
  - If user posts, cancel any scheduled nudge

---

## 4. Notification Settings Architecture

### Global Settings

```
Notifications
├── Quiet Hours
│   ├── Enable quiet hours          [toggle]
│   ├── From                        [time picker, default 10:00 PM]
│   └── To                          [time picker, default 8:00 AM]
│
├── Do Not Disturb
│   ├── Allow calls during DND      [toggle, default ON]
│   └── Allow mentions during DND   [toggle, default ON]
│
└── Notification Sound              [global tone picker]
```

### Per Category Settings

```
Calls               [toggle] + [sound picker] + [bypass DND toggle]
Messages            [toggle] + [sound picker] + [preview: show/hide content]
Mentions & Replies  [toggle] + [sound picker]
New Moments         [toggle] + [sound picker]
Reactions & Views   [toggle]
Family Nudges       [toggle] + [frequency: daily/every 3 days/weekly/off]
System              [toggle]
```

### Per Contact Override

Long-press a contact → Notification settings:
```
[Name]
├── Calls from [Name]    [Always notify / Default / Never]
├── Messages from [Name] [Always notify / Default / Muted]
└── Moments from [Name]  [Always notify / Default / Silent]
```

### Per Group Override

Tap group info → Notifications:
```
Mute [Group Name]
├── For 1 hour
├── For 8 hours
├── For 1 week
├── Until I turn it back on
└── [Mentions still notify — toggle]
```

---

## 5. Push Payload Structure

All push notifications from Cloudflare Worker use this base payload:

```json
{
  "type": "call_incoming | call_missed | message | mention | reply |
           status_post | status_reaction | status_reply | status_view_milestone |
           chain_invite | archive_complete | re_engagement",
  "senderId": "user_abc",
  "senderName": "Mum",
  "senderAvatar": "https://cdn.hello.app/avatars/user_abc.jpg",
  "targetId": "chat_xyz | status_id_123 | null",
  "targetType": "chat | group | status | system",
  "groupName": "Family Group | null",
  "previewText": "Hey are you coming tonight?",
  "emoji": "❤️ | null",
  "deepLink": "hello://chat/chat_xyz | hello://status/status_id_123 | hello://calls",
  "channel": "messages",
  "priority": "urgent | high | default | low",
  "collapseKey": "chat_chat_xyz",
  "sentAt": "2025-06-03T14:22:00Z"
}
```

**`collapseKey`** is critical — same key replaces the previous notification of that type
on the device rather than stacking. Use `chat_{threadId}` for messages, `status_{statusId}`
for status activity.

---

## 6. Delivery Stack

```
Event occurs (message sent, call initiated, reaction added)
          ↓
Cloudflare Worker handles API request
          ↓
Trigger push notification job (async, non-blocking)
          ↓
Check user notification preferences in D1
Check quiet hours + DND state
Check rate limits + throttle rules
          ↓
FCM HTTP v1 API → Android device
Web Push API → Browser (service worker)
WebSocket (Durable Object) → PC Electron app (local notification)
          ↓
Device receives, renders per channel settings
```

**For calls only:** Use FCM high-priority `data` message (not `notification`), which wakes
the app even in Doze mode on Android. The app then shows the full-screen call UI itself.
This is required for call reliability on modern Android.

**Delivery guarantee:** If FCM delivery fails (device offline), the message is queued for
up to 28 days for non-call types, 1 minute for call types (stale call notifications are
useless and confusing — always set TTL=60s for `call_incoming`).

---

## 7. In-App Notification Centre

For everything the user missed or dismissed:

```
Notification Centre
├── Calls           — missed calls + timestamps, call-back action
├── Messages        — unread threads summary
├── Moments         — status activity: new posts, reactions, replies, milestones
└── System          — archive completions, app updates
```

- Badge count on bottom nav tab icon: unread calls + messages only (not status)
- Status tab has its own unread ring animation — no badge count needed
- "Mark all as read" per section
- Auto-clear read notifications after 7 days

---

## 8. Edge Cases

| Scenario | Behaviour |
|---|---|
| User is currently in the app | Show in-app heads-up banner only. No push notification. |
| User is on a call, message arrives | Silent push only. No sound. Badge updates. |
| Multiple devices (phone + web) | Deliver to all active devices. If user reads on one, cancel on others via `collapseKey` + server-side read ACK |
| App uninstalled but push token exists | FCM returns `NotRegistered` → delete token from D1 |
| Status poster deletes status before viewer opens notification | Deep link lands on feed (status gone), show "This moment has been removed" toast |
| Call notification arrives after call already answered on another device | Cloudflare sends `call_cancelled` push immediately on answer → dismiss notification |
