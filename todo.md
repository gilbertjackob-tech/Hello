Codex Task: Redesign Hello Stories/Status System + 24h Cloudflare Stories + PC Drive Backup

You are working on the Hello app.

Current architecture:
- Cloudflare is the source of truth for communication features:
  - auth/session
  - profiles
  - contacts
  - chat
  - calls
  - realtime
  - temporary chat attachments
- Family Drive original media remains PC-backed:
  - PC backend is exposed through Cloudflare Tunnel:
    https://home.bookhelloctg.com
  - Drive API base:
    https://home.bookhelloctg.com/hello/api
- Tailscale is no longer required.
- Do not reintroduce Tailscale.
- Do not move Family Drive original photos/videos to Cloudflare R2.

Now implement a production-level Hello Stories/Status system.

The goal:
Create a Snapchat/Facebook-style stories system, but keep Hello’s own identity:
- clean
- rounded
- premium
- family-friendly
- theme-aware
- smooth
- not a Snapchat clone

Use the existing Hello theme system. Do not hardcode colors directly inside components. Use existing theme tokens and add missing semantic tokens where needed.

Important:
- Stories are temporary cloud content.
- Stories live in Cloudflare for 24 hours.
- After expiry, they disappear from story feed.
- Expired stories become eligible for PC Drive backup.
- PC Drive backup should happen through the PC backend when `home.bookhelloctg.com` is online.
- If PC is offline, story backup remains pending.
- Story media should not be stored permanently in Cloudflare after successful PC backup unless the retention policy explicitly keeps it.

==================================================
1. Design Direction
==================================================

For light theme:
- Use a clean Snapchat-inspired visual direction.
- Background: white / very light gray.
- Accent: Snapchat-like yellow through semantic theme token.
- Cards: soft white, rounded, subtle shadow.
- Text: high contrast black/dark gray.
- Secondary text: muted gray.
- Borders: very subtle.
- Story rings: yellow/accent ring.
- Bottom nav: floating white rounded pill.

For dark themes:
- Same layout and behavior.
- Use current theme accent instead of hardcoded yellow.
- Keep backgrounds readable and calm.
- Do not make story backgrounds too bright/noisy.
- Chat/status text must remain readable.

Do not hardcode:
- yellow
- purple
- black
- white
- gradients
inside components.

Use semantic tokens.

Add story-specific theme tokens if missing:
- storyAccent
- storyRingUnseen
- storyRingSeen
- storyCanvasBackground
- storyViewerOverlay
- storyToolRailBackground
- storyPopupBackground
- storyPopupText
- storyBottomSheetBackground
- storyPrimaryButton
- storyPrimaryButtonText
- storyProgressActive
- storyProgressInactive
- storyReplyBackground
- storyReplyText

Light theme values can use:
- storyAccent: #FFDD00
- storyRingUnseen: #FFDD00
- storyRingSeen: #D1D5DB
- storyCanvasBackground: #FFDD00
- storyViewerOverlay: rgba(0,0,0,0.72)
- storyPopupBackground: #FFFFFF
- storyPopupText: #111827
- storyPrimaryButton: #FFDD00
- storyPrimaryButtonText: #111111

Dark themes:
- derive from existing accent/surface tokens.
- keep contrast readable.

==================================================
2. Inbox Page Story Row
==================================================

Current issue:
There is a large status card/bar on the inbox page. Remove it.

Replace it with a compact horizontal circular story row.

Inbox layout order:

Header:
- Avatar
- Hello
- Inbox
- Add button
- Refresh
- Menu

Search bar:
- Search people, groups, files, or messages

Story row:
- Horizontal scroll row.
- It should sit below search bar and above filters.
- It should feel lightweight, not like a big isolated card.

First item:
My Story / Add Story
- circular avatar
- white/raw circle base in light theme
- accent/yellow ring
- floating plus badge at bottom-right
- label: My status or My story
- tapping opens story creation screen

After My Story:
Show users who posted active stories in the last 24 hours:
- circular avatar
- unseen story: accent/yellow ring
- seen story: gray/subtle ring
- label: first name
- compact spacing
- horizontal scroll
- no full-width card

Then filters:
- All
- Unread
- Groups
- Calls
- Files
- Pinned

Then normal chat list.

Acceptance:
- Inbox page has circular story row.
- First bubble is always My Story with plus.
- Story row is compact and premium.
- No large status card remains.

==================================================
3. Status / Stories Main Page
==================================================

Status tab should show:

Top section:
- Title: Hello Stories
- Subtitle or pill: 24 hours
- Header: Status
- Helper line:
  “Status updates disappear after 24 hours.”

My Status card:
- avatar with story ring
- title: My status
- subtitle: Tap to add status update
- button: Add status
- button uses storyPrimaryButton

Below:
Recent updates

If no active stories:
- centered empty state:
  “No status updates”
  “Family stories will appear here for 24 hours.”

If stories exist:
- list of people with active stories
- circular ring avatar
- name
- time posted
- text snippet if text story
- unseen indicator if needed

Use smooth list animations.

==================================================
4. Status Creation / Story Editor
==================================================

Implement a Snapchat-style story creation screen while keeping Hello identity.

Top:
- Close button left
- Title: Create status
- Helper text:
  “Edit directly on the story canvas, then post the final image.”
- Post button top-right:
  - label/icon: Post
  - accent/yellow background in light theme
  - loading state while posting

Main canvas:
- 9:16 story canvas
- large rounded rectangle
- can display:
  - camera preview
  - selected image
  - selected video
  - text status background
- text status default canvas uses storyCanvasBackground or theme gradient
- centered placeholder:
  “Type a status”

Right vertical tool rail:
- Flip
- Flash
- Sounds
- HD Mode
- Selfie Settings
- Timer
- Stickers
- Text
- Draw

Implementation note:
- Tools can be placeholder UI first if actual camera/tool logic is not ready.
- But layout and interaction must be production-quality.
- Do not break current upload/media picker.

Bottom:
- input area:
  “Type directly on the story canvas”
- optional text button/icon
- immersive screen preferred
- bottom nav hidden if it conflicts with story creation

==================================================
5. Story Viewer
==================================================

Story viewer should be full-screen 9:16.

Top:
- progress bars for each story item
- avatar
- name
- time:
  - Just now
  - 2m ago
  - 1h ago
- three-dot menu

Canvas:
- image/video/text story

Bottom overlay for other people’s story:
- reply field:
  “Reply to story...”
- emoji quick reactions
- send icon

Bottom overlay for own story:
- eye icon + views count
- reaction count
- comment count
- delete
- download/save
- share/forward if useful

Viewer must support:
- next story
- previous story
- pause on long press
- resume on release
- tap left/right navigation
- smooth progress animation

==================================================
6. Story Reactions, Comments, Viewers
==================================================

When someone reacts:
Show temporary popup:
- avatar
- “{Name} reacted ❤️ to your story”
- rounded pill/card
- theme surface color
- smooth slide + fade
- auto-dismiss after 2.5s

When someone comments:
Popup:
- avatar
- “{Name} commented: “Nice!””
- tapping opens story comments/reactions sheet

Story owner bottom sheet:
Viewer / Reaction / Comment panel

Sections or tabs:
- Viewed by
- Reactions
- Comments

Viewer row:
- avatar
- name
- viewed time
- reaction emoji if reacted

Comment row:
- avatar
- name
- comment text
- time

Animations:
- bottom sheet slide up
- reaction popup fade/slide
- reaction selection bounce
- smooth count updates

==================================================
7. Cloudflare 24-Hour Story Storage
==================================================

Stories must be stored temporarily in Cloudflare.

When a user posts a story:
1. Upload media/text metadata to Cloudflare.
2. Save story record.
3. Make it visible until expiresAt.
4. Track viewers, reactions, comments live.
5. After expiresAt, hide from story feed.
6. Keep backup-pending package until PC backup succeeds or retention expires.

Cloudflare ownership:
- Story metadata: D1
- Story media/thumbnail: R2
- Viewers/reactions/comments: D1
- Realtime popup events: Worker/Durable Object/WebSocket if available

Do not store Story media in Family Drive immediately.
Drive backup happens after expiry or after story becomes backup-eligible.

Story record fields:
- id
- ownerUserId
- type: text / image / video
- text
- caption
- mediaKey / mediaUrl
- thumbnailKey / thumbnailUrl
- createdAt
- expiresAt
- expired
- visible
- visibility
- allowedUserIds
- storageProvider: cloudflare
- backupStatus: not_required / pending / syncing / completed / failed
- driveFileId
- driveFolderId
- backedUpAt

Viewer fields:
- storyId
- userId
- viewedAt

Reaction fields:
- id
- storyId
- userId
- emoji
- createdAt

Comment fields:
- id
- storyId
- userId
- text
- createdAt

==================================================
8. Story Visibility
==================================================

Story visibility options:
- all
- contacts
- custom
- only_me

Rules:
- User sees only stories allowed for them.
- Expired stories do not appear in story feed.
- Owner can always see own active stories.
- Owner can see views/reactions/comments.
- Other users cannot see owner analytics.
- Custom visibility uses allowedUserIds.

==================================================
9. Cloudflare → PC Drive Backup Flow
==================================================

Goal:
Stories live in Cloudflare for 24 hours, then sync to PC Drive for long-term storage when PC is available.

During first 24 hours:
- Story stays in Cloudflare.
- Viewers/reactions/comments tracked live.
- User can see who viewed/reacted/commented.
- Story appears in story row/feed.

After 24 hours:
- Story becomes expired.
- visible = false
- expired = true
- backupStatus = pending, unless user disabled backup.
- It disappears from story feed.
- Cloudflare keeps story package temporarily until backup succeeds.

When PC Drive is online:
PC backend exposed through:
https://home.bookhelloctg.com/hello/api

Backup process:
1. PC Drive sync checks Cloudflare for expired backup-pending stories.
2. It downloads story media + metadata.
3. It saves media into PC Drive storage.
4. It saves metadata JSON beside media or in PC Drive DB:
   - original story id
   - owner
   - createdAt
   - expiresAt
   - viewers
   - reactions
   - comments
   - caption/text
   - story type
   - visibility summary
5. Cloudflare marks:
   - backupStatus = completed
   - driveFileId
   - driveFolderId
   - backedUpAt
6. After successful backup, Cloudflare can delete or cold-archive media based on retention policy.

If PC Drive is offline:
- backup remains pending.
- retry later.
- show status:
  - Waiting for PC to sync
  - Backed up to Drive
  - Backup failed, retrying

Important:
- Cloudflare Tunnel is forwarding only.
- Drive final storage remains PC.
- Do not upload Family Drive original media to R2.
- Story media can use R2 temporarily because Stories are a cloud feature.

==================================================
10. Worker / API Requirements
==================================================

Add Cloudflare Worker endpoints:

Stories:
- POST /api/stories
- GET /api/stories/feed
- GET /api/stories/:id
- DELETE /api/stories/:id
- POST /api/stories/:id/view
- POST /api/stories/:id/reactions
- DELETE /api/stories/:id/reactions/:reactionId
- POST /api/stories/:id/comments
- DELETE /api/stories/:id/comments/:commentId
- GET /api/stories/:id/analytics

Media:
- POST /api/stories/upload
- GET /api/stories/media/:key or signed URL helper

Backup:
- GET /api/stories/backup/pending
- POST /api/stories/:id/backup/start
- POST /api/stories/:id/backup/complete
- POST /api/stories/:id/backup/failed

Security:
- All endpoints require cloud auth/session.
- Only owner can delete own story.
- Only owner can view analytics.
- Only allowed users can view story media.
- Backup endpoints should require PC sync auth/secret or device token.
- Do not expose all expired stories publicly.

==================================================
11. PC Backend Requirements
==================================================

PC backend should add story backup sync logic.

Use existing PC Drive connection:
https://home.bookhelloctg.com/hello/api

Add/adjust PC routes if needed:
- GET /hello/api/drive/health already exists.
- POST /hello/api/drive/story-backup/sync
- GET /hello/api/drive/story-backup/status

Behavior:
- When PC backend is online, it can sync pending expired stories from Cloudflare.
- Save story media into PC Drive storage.
- Save metadata JSON or DB row.
- Mark Cloudflare backup completed.
- If failed, mark backup failed and retry later.

Do not block chat/story posting if PC is offline.

==================================================
12. Android Implementation
==================================================

Implement or update Android screens:

Inbox:
- add compact story row below search and above filters
- My Story first
- active story users after it
- rings unseen/seen

Status tab:
- Hello Stories page
- My Status card
- Recent updates
- empty state

Story creation:
- 9:16 canvas
- tool rail
- post button
- media/text support
- polished animations

Story viewer:
- full-screen viewer
- progress bars
- reply/reaction area
- own story analytics actions
- bottom sheet for viewers/reactions/comments

Story popups:
- reaction popup
- comment popup
- bottom sheet

Story backup status:
- show subtle status for own expired stories:
  - Waiting for PC to sync
  - Backed up to Drive
  - Backup failed, retrying

==================================================
13. Web Implementation
==================================================

Mirror the same behavior in the web app:

Inbox:
- circular story row
- My Story
- active users

Status page:
- story list
- creation entry
- viewer

Story creation:
- desktop-friendly editor
- 9:16 canvas
- tool rail
- upload image/video/text status
- post button

Story viewer:
- full-screen / centered 9:16 viewer
- progress bars
- reaction/reply
- owner analytics bottom sheet/modal

Do not break current web chat/call cloud routing.

==================================================
14. Motion / Interaction Polish
==================================================

Add smooth transitions:
- story ring press scale: 0.96
- add story button small bounce
- story viewer open: fade + scale/avatar expansion if possible
- bottom sheet slide-up
- reaction popup fade + slide
- story progress bar smooth linear animation
- tool rail expand/collapse
- post button loading state
- story row item entrance stagger
- viewer next/previous transition

Keep animations responsive:
- small UI: 120–180ms
- panels/viewer: 220–300ms
- no heavy blur on low-end devices

==================================================
15. Acceptance Criteria
==================================================

Complete only when:

1. Inbox page has circular story row, not large status card.
2. My Story bubble is first and has plus badge.
3. Users with active stories appear after My Story.
4. Story rings show unseen/seen state.
5. Status tab has Hello Stories page.
6. Story creation screen has 9:16 editor/canvas.
7. Tool rail exists.
8. Story viewer has progress bars.
9. Story owner can see viewers/reactions/comments.
10. Other users can reply/react.
11. Reaction/comment popups are smooth.
12. Stories expire from feed after 24 hours.
13. Cloudflare stores story data during active period.
14. Expired stories become backup-pending.
15. PC Drive sync can back up expired stories.
16. PC offline does not break story posting/viewing.
17. Existing chat/auth/calls still work.
18. Family Drive original media remains PC-only.
19. Theme tokens control all story colors.
20. Light theme is Snapchat-inspired but still Hello.
21. Dark themes remain readable.
22. Android build passes.
23. Web build passes.
24. Worker deploy/type check passes.

==================================================
16. Validation Commands
==================================================

Run:

npm run build
npm --workspace apps/hello run build
npm --workspace apps/browser run build
npm --prefix apps/cloudflare/chat-worker run types
npm --prefix apps/cloudflare/chat-worker run deploy
cd apps/android
.\gradlew.bat :app:assembleDebug --console=plain

If any old unrelated lint errors exist, report separately. Production builds must pass.

==================================================
17. Final Report Required
==================================================

After implementation, report:

1. Files changed.
2. New theme tokens added.
3. New Worker endpoints added.
4. New D1/R2 story schema added.
5. Android screens/components added.
6. Web screens/components added.
7. How 24h expiry works.
8. How PC Drive backup works.
9. Whether PC offline story behavior works.
10. Whether Drive media routing was untouched.
11. Build/deploy results.
12. Remaining caveats.

Important final instruction:
This should feel like Hello Stories, not a Snapchat clone. Use Snapchat/Facebook only as inspiration for story row behavior, circular story flow, 9:16 editor, story viewer, and smooth interactions.