# Hello Status — The $1B "Today Pulse" Plan (v2)
*Rewritten for real behavioral pull, not just features*

---

## The Big Idea

WhatsApp Status gets used but nobody loves it. Snapchat Stories feel public and performative.
BeReal was a moment — then people got tired. Every social status product fails families because
they're built for audiences, not belonging.

**Hello Status is the only status product where your memories don't live on someone else's server.
They live in your home.**

That single sentence is the product. Everything else serves it.

The 24-hour cloud window removes the pressure of permanence. The PC archive means nothing is
truly lost. Together they answer the two fears that stop people from posting:
*"What if I regret this forever?"* and *"What if I lose this moment?"*

Both fears are gone. So people post.

---

## The Core Behavioral Loop
*(This is what was missing from v1)*

Most status products die because they have a **viewer loop** but no **poster loop**. People
watch but don't post. Here's the exact psychology that fixes it:

```
SEE OTHERS POST
      ↓
Feel warmth / mild FOMO
      ↓
One-tap entry with zero pressure
      ↓
POST YOUR MOMENT
      ↓
Notification: "Dad reacted ❤️ to your moment"
      ↓
Open app → see WHO watched (this is the hook)
      ↓
Feel seen and validated
      ↓
WANT TO POST AGAIN
```

Every design decision must serve this loop. If a feature doesn't tighten the loop, it doesn't
ship in v1.

### The Three Posting Blockers (and how we remove them)

| Blocker | Why people don't post | Our fix |
|---|---|---|
| Cringe fear | "My family will judge this" | Private-by-default + ephemeral 24h cloud |
| Effort | "Opening a camera is too many taps" | Morning prompt notification → camera in 2 taps |
| Permanence anxiety | "What if I regret this later?" | Clear "gone from cloud in Xh" + "saved to YOUR PC only" |

---

## Product Experience

### 1. The Feed — "Today Pulse"

The feed is not a grid. It is a **ring timeline** of family members arranged by recency.

- Large, warm profile rings. Active = full-color glow ring. Posted today = colored border.
  Not posted in 3+ days = subtle grey fade (creates the soft gap signal — not a notification,
  just visible absence)
- Tapping a ring immediately full-screens into their story. No loading screen, cache-first.
- Below the rings: "Add Yours" prompt chains and a daily prompt card.
- At the bottom: "Saved on Your PC" — a read-only archive row. Separate, clearly labelled.
  Not mixed with live statuses.

**The gap mechanic:** If a close family member (frequent poster) hasn't posted in 3 days, their
ring gets a gentle pulse animation — not a red dot, not a notification — just a visible warmth
signal. Subconscious pull without pressure.

### 2. The Viewer Experience — Make It Feel Like Being Seen

Viewer identity is the #1 psychological driver of status posting. Seeing "Mum watched your
moment" is why people post again. We lean all the way into this.

**For your own statuses:**
- Full viewer list with names, profile photos, and timestamps
- Reaction breakdown per viewer (not just totals)
- "4 family members haven't seen this yet" soft indicator
- "This moment is saved to your PC" badge — green lock icon
- "Cloud copy expires in 3h" countdown — creates urgency to view others before they're gone
- Long-press on a viewer name → quick reply bubble

**For others' statuses:**
- Full-screen vertical media, no chrome
- Segmented progress bar at top (WhatsApp-style but cleaner)
- Tap left/right to navigate segments
- Press-hold to pause
- Swipe down to close
- Reaction tray swipe-up (emoji + text reply in one motion)
- "This was saved to their PC" or "Expires in Xh" badge — privacy reassurance

### 3. Status Studio — Post Without Thinking

Two entry modes:

**Quick Post (default, 2 taps):**
- Tap the "+" ring on your own avatar → camera opens immediately
- Snap → auto-crop to 9:16 → tap Post
- Done. No studio unless you want it.

**Studio Mode (optional):**
- Text canvas: font picker, colour picker, gradient backgrounds, emoji overlay
- Photo canvas: crop/reposition, brightness/contrast, vignette filters
- Caption bar: drag to reposition, font/size/colour
- Sticker tray: static emoji + animated family-specific stickers (home, food, travel, heart)
- Quick templates (one-tap fill): "At Work 💼", "Family Dinner 🍽️", "Just Got Home 🏠",
  "Morning ☀️", "Weekend Vibes 🌿", "Miss Everyone ❤️"
- Poll overlay: "Yes/No" or custom binary question stamped onto any media

Studio opens ONLY when user explicitly taps the edit icon after snap. Never forced.

### 4. Daily Prompts — The Non-Cringe Upload Driver

Every day at a time the user sets (default: 8am), a single gentle prompt card appears at the
top of the feed. Examples:

- "What's your view right now?" → tap → camera opens pointing outward
- "Show us dinner tonight"
- "One word for your mood today"
- "A corner of your home"
- "Anything making you smile?"

**Family-specific prompts (generated from household data, never from cloud):**
- On someone's birthday: "Happy birthday [Name] — post them a moment"
- After a family member posts: "Reply with your moment"
- Seasonal: "First day of Ramadan — share your sehri"

Prompts are **local-generated** — the app suggests based on time, day, and local calendar.
No cloud ML required. No privacy risk.

### 5. "Add Yours" Private Chains

A family member posts a moment and tags it "Add Yours: Morning views". Others see the chain
card in the feed and can tap to add their own version. The chain lives as a group story.

- Maximum 24 hours from the first post
- All chain members see all replies
- Great for holidays, trips, shared meals across households
- Chain archived together on PC when any member is the host PC

### 6. The Privacy Narrative — Our Actual Differentiator

This belongs in the UI, not just the terms of service.

Every status shows one of three badges:

| Badge | Meaning | Colour |
|---|---|---|
| 🔒 Saved to your PC | PC was online, archived. Cloud copy will delete. | Green |
| ⏳ Saving when PC connects | PC offline, will archive on next connection | Amber |
| 🌥️ Cloud only — expires in Xh | PC was offline past 24h. This moment will be gone | Red |

When a moment expires without archiving: a tombstone card replaces it — "This moment wasn't
saved to your PC. It's gone from the cloud." Honest. No pretending. Builds trust.

**Onboarding pitch (shown once on first open):**
> "Your moments are for your family — not for us. They live on the cloud for 24 hours so
> everyone can see them. Then they move to your home PC forever. We never keep them."

---

## Notification Strategy
*(Critical — was completely absent from v1)*

Notifications are the re-engagement engine. Without them the loop breaks. Every notification
must feel personal, not system-generated.

### Trigger Notifications

| Event | Notification copy | Timing |
|---|---|---|
| Family member posts | "Mum just posted a moment ☀️" | Immediate |
| Someone reacts to yours | "Dad reacted ❤️ to your moment" | Immediate |
| Someone replies to yours | "Riya replied to your status" | Immediate |
| Viewer milestone | "5 family members watched your moment" | At threshold |
| Chain invite | "Join Mum's 'Morning Views' chain" | Immediate |

### Re-engagement Notifications

| Trigger | Notification copy | Timing |
|---|---|---|
| User hasn't posted in 3 days | "The family hasn't heard from you in a while 👋" | Day 3, 10am |
| Daily prompt ready | "Today's prompt: What's your view right now?" | User's set time |
| Family active but user silent | "3 family members posted today" | 8pm if no post |
| PC archive completed | "Today's moments are safely saved to your PC 🔒" | After archive |
| Memory resurface | "A moment from this day last year" | Anniversary |

### Notification Rules
- Maximum 3 notifications per day per user. No spam.
- User sets quiet hours in settings (default: 10pm–8am)
- Each notification type can be toggled individually
- "Moments" tone — never "alerts", never "reminders", always warm family language

---

## Technical Architecture

### Cloudflare Layer (Always-Online)

**D1 Tables:**
```
statuses          — id, ownerId, type, text, promptId, chainId, audience,
                    expiresAt, archivedAt, archiveState, replyCount,
                    reactionSummary, viewCount, createdAt
status_media      — id, statusId, url (R2 key), mediaType, width, height,
                    durationSeconds, expiresAt, deletedAt
status_views      — id, statusId, viewerId, viewedAt, completedAt
status_reactions  — id, statusId, reactorId, emoji, reactedAt
status_replies    — id, statusId, senderId, text, mediaUrl, sentAt
status_chains     — id, promptText, creatorId, expiresAt, memberIds
status_archive_jobs — id, statusId, ownerId, state, createdAt, ackedAt
```

**R2 Storage:** `statuses/{ownerId}/{statusId}/{filename}` with signed upload URLs.
Client compresses images to 1080×1920 JPEG/WebP before upload. Video max 30s, 25MB.

**Cron Jobs:**
- Every 15 minutes: delete expired R2 media, tombstone metadata, clean old views/reactions
- Daily: generate prompt cards for active households, trigger memory resurface notifications

**APIs:**
```
GET    /api/status/feed                    — household feed, cache-first
POST   /api/status                         — create status
POST   /api/status/media                   — upload media (returns signed R2 URL)
POST   /api/status/:id/view                — mark viewed
POST   /api/status/:id/react               — react
POST   /api/status/:id/reply               — reply
DELETE /api/status/:id                     — delete own status
GET    /api/status/archive/pending         — PC pulls this to archive
POST   /api/status/archive/ack             — PC confirms archive, cloud deletes media
POST   /api/status/chain                   — create Add Yours chain
POST   /api/status/chain/:id/join          — join chain
GET    /api/notifications/status           — unread status notifications
```

**Realtime (Durable Object WebSocket):**
Events: `status_added`, `status_viewed`, `status_reacted`, `status_replied`,
`status_deleted`, `status_archived`, `chain_joined`

### PC Backend (Durable Archive)

- Polls `/api/status/archive/pending` every 5 minutes when online
- Downloads R2 media, stores under `pc_status_archive/{year}/{month}/{statusId}/`
- Writes SQLite metadata: statusId, ownerId, originalExpiresAt, archivedAt, localPath
- POSTs `/api/status/archive/ack` → Cloudflare deletes R2 media early
- If PC offline past 24h: Cloudflare sets `archiveState = expired_without_archive`,
  PC records the tombstone on next sync with no media

**Archive never touches Drive routes.** Status archive is its own storage path and its own
SQLite table. No cross-contamination.

### Android App

- `StatusFeedViewModel` — Cloudflare feed with Room cache, sub-500ms first paint
- `StatusUploaderService` — background upload with retry queue, compression before upload
- `StatusViewerActivity` — full-screen vertical, gesture system (tap/hold/swipe)
- `StatusStudioActivity` — studio UI, optional from camera flow
- `PCArchiveBadgeHelper` — computes badge state from expiresAt + archiveState
- `NotificationDispatcher` — handles status push payload, builds warm-copy notifications
- `LocalPromptEngine` — generates daily prompts from local calendar + household context,
  no cloud call needed

### Web App

- Replace `StatusPane.tsx` with `TodayPulseFeed.tsx` — cloud-first, ring timeline layout
- `StatusStudioWeb.tsx` — canvas editor, same feature set as Android
- `StatusViewerModal.tsx` — full-screen overlay, keyboard shortcuts (arrow keys, space pause)
- `PCArchiveSectionWeb.tsx` — read-only, clearly separated from live feed
- `useStatusFeed` hook — SWR-based, stale-while-revalidate, 30s refresh interval

### Shared Types (extend StatusItem)

```typescript
interface StatusItem {
  id: string
  ownerId: string
  type: 'text' | 'photo' | 'video' | 'poll'
  text?: string
  media?: StatusMedia[]
  expiresAt: string            // ISO — cloud expiry
  archivedAt?: string          // ISO — when PC archived
  archiveState: 'pending' | 'archived' | 'expired_without_archive'
  savedToPc: boolean
  audience: 'household' | 'contacts'
  promptId?: string
  chainId?: string
  reactionSummary: Record<string, number>
  replyCount: number
  viewedByMe: boolean
  viewCount: number
}
```

---

## Rollout Phases

### Phase 1 — Foundation (Get It Working)
- D1 schema + migrations, R2 upload/download, cleanup cron
- Core APIs: create, view, react, delete, archive ACK
- Basic full-screen viewer on web + Android
- PC archive pull loop
- Push notification infrastructure (FCM/Web Push)
- Archive state badges in UI
- Success: create → view → delete → archive cycle works end-to-end

### Phase 2 — Make It Feel Wow
- Status Studio parity (web + Android)
- Daily prompts (local engine, no ML)
- Add Yours chains
- Full viewer list with reaction breakdown
- Reply flow in viewer tray
- Warm notification copy live
- Gap mechanic in feed rings
- "Saved to PC" / "Expires in Xh" badges
- Sub-500ms feed paint from cache

### Phase 3 — The Retention Loop
- Memory resurface notifications ("This day last year")
- Family streak system (household posts X days in a row)
- Prompt calendar — preview this week's prompts
- "Post from a PC Drive moment" surface (local-only suggestions, never auto-upload)
- Archive browser on PC — scrollable timeline of all saved moments
- Notification fine-tuning — quiet hours, per-type toggles

### Phase 4 — Production Hardening
- Upload retry queue with exponential backoff
- Offline draft queue (compose when offline, post on reconnect)
- Abuse limits: rate limits per user, media scan pre-upload
- Storage cost dashboard
- Cleanup audit log — provable "no cloud media after 24h"
- Migration tool from old PC-only statuses
- Full observability: Cloudflare Analytics + custom event tracking

---

## Success Metrics

### Speed
- Feed first paint from cache: **< 500ms**
- Cloud feed refresh on normal mobile: **< 1.5s**
- Post flow (snap to posted): **< 5 seconds**

### Engagement (30 days post-launch)
- DAU/MAU of status feature: **> 40%** (WhatsApp Status is ~30%)
- Average posts per active user per week: **> 3**
- Notification open rate: **> 35%**
- Viewer-to-poster conversion (saw a status → posted same day): **> 25%**

### Trust
- Zero cloud media remaining after 24h: **100%** (audited, not estimated)
- Archive success rate when PC online within 24h: **> 99%**
- User-reported "I trust this with family moments": tracked via in-app pulse survey

---

## What Makes This $1B

It's not the features. Features can be copied in 3 months.

It's the only status product that says:
**"Your family memories belong to your family."**

Every design decision — the PC archive, the "saved to your PC" badge, the 24-hour ephemeral
cloud, the private-by-default audience, the local prompt engine — points at the same thing.

When a mother posts a photo of her child, she shouldn't have to wonder who owns that image,
where it's stored, or whether it'll haunt her years later. Hello Status answers all three
questions before she even has to ask.

That's the moat. That's why people choose it over WhatsApp. And that's why — once they
start — they keep posting.