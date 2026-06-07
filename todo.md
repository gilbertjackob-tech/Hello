Codex Task: Build Hello Family Drive Frontend Flow First, Then Connect Backend
You are working on the Hello mobile app.

use "hello drive sequence of frontend.jpeg" strictly for understanding frontend sequence of flow. you may follow hello theme style as hello has already many themes but the desgin must be fully same.


I am providing a Figma-style reference image named:
“Hello Family Drive — Upload & Sharing Flow”

Use the image as the main frontend guide.

Important:
Do not jump directly into backend first.
First build the sequential frontend screens and interactions exactly as a polished prototype.
After the frontend flow is complete and stable, connect the backend APIs.

Current architecture:
- Chat/auth/calls are Cloudflare-backed.
- Family Drive media is PC-backed.
- PC Drive API base:
  https://home.bookhelloctg.com/hello/api
- Tailscale must not be used.
- Drive original photos/videos must not be uploaded to Cloudflare R2.
- Cloudflare Tunnel only forwards Drive requests to the PC backend.
- Android pending uploads must work when PC is offline.

Main goal:
Implement the Family Drive upload + sharing frontend flow shown in the reference image.

The flow should feel:
- simple for older users
- powerful for 200+ photo family event uploads
- smooth and animated
- clean, dark, premium
- easy to understand
- not technical

Use existing Hello theme tokens.
Do not hardcode colors.
If needed, add semantic Drive tokens.

==================================================
PART 1 — FRONTEND FIRST
==================================================

Build the full frontend sequence first using mock/demo data where needed.

Do not wait for backend to finish before making the screens.

The frontend sequence from the image:

1. Drive Home
2. Select Photos
3. Choose Event
4. Choose Possible Audiences
5. Batch Sort Screen
6. Batch Sort Progress / Next Circle
7. Move Remaining Shortcut
8. Choose People / Custom
9. Upload Summary / Confirmation
10. Pending Uploads / Offline
11. Event View
12. Circle Management
13. Create / Edit Circle
14. Synced Success / Backup Complete

Important:
The old Step 4 “Choose Upload Mode” is removed.
Flow should go directly:

Select Photos
→ Choose Event
→ Choose Possible Audiences

So the new sequence is:

Drive Home
→ Select Photos
→ Choose Event
→ Choose Possible Audiences
→ Batch Sort / or simple audience assignment
→ Upload Summary
→ Pending/Upload/Sync
→ Event View

==================================================
1. Drive Home Screen
==================================================

Purpose:
Main entry point for family photos/videos.

UI from image:
- Header: Family Drive
- Upload button:
  “Upload Photos / Videos”
- Events section:
  show event cards with thumbnail, event name, date, item count
- Circles section:
  circular avatars/groups
- Recent uploads
- Pending uploads indicator if any
- Bottom navigation still matches Hello app style

Small details:
- Upload button should be visually strong.
- Event cards should have soft rounded corners.
- “See all” links should be subtle.
- Circle avatars should be horizontally scrollable.
- Pending uploads should show small yellow/cloud badge.
- PC Drive status should be visible somewhere subtle:
  PC On / PC Offline

Interactions:
- Tap Upload → Select Photos screen
- Tap Event → Event View
- Tap Circle → Circle details/event filtered view
- Tap Pending Uploads → Pending Uploads screen
- Pull/refresh should update Drive health and latest items

Empty state:
If no events:
- show friendly message:
  “No memories yet”
  “Upload photos to start your Family Drive.”

==================================================
2. Select Photos Screen
==================================================

Purpose:
User selects many photos/videos from gallery.

UI from image:
- Header:
  back/cancel
  title: Select Photos
  selected count
  Next button
- Grid of photo thumbnails
- Yellow check indicators on selected items
- Select all button at bottom
- Next button with count:
  “Next (120)”

Small details:
- Thumbnail grid should be smooth and dense.
- Selected image should show:
  border
  check badge
  slight dim/overlay
- Tap selects/deselects.
- Long press enters select mode.
- Swipe/drag across thumbnails should select multiple smoothly.
- Keep selection count updated live.
- Videos show duration badge.
- Large images should use thumbnails, not full resolution.

Interactions:
- Tap thumbnail → select/deselect
- Drag over thumbnails → multi-select
- Select all → selects all loaded media
- Next → Choose Event

Performance:
- Use stable keys.
- Lazy grid.
- No full-res image loading in grid.

==================================================
3. Choose Event Screen
==================================================

Purpose:
Choose where the memories belong.

UI from image:
- Header: Choose Event
- New Event card at top
- Existing event cards:
  thumbnail
  event name
  date
- Daily Memories / default event option

User-facing wording:
“Where should these memories go?”

Options:
- New Event
- Existing events
- Daily Memories
- Skip if needed

If user skips:
Auto-place into:
“Daily Memories”
or
“Uploads from {Month Year}”

Interactions:
- Tap New Event → inline event name input or modal
- Tap existing event → Choose Possible Audiences screen

Small details:
- Event card press animation.
- Recent events first.
- Search event option optional for later.

==================================================
4. Choose Possible Audiences Screen
==================================================

Purpose:
Before sorting 200 photos, user selects the groups/audiences involved in this batch.

This replaces the old “Choose Upload Mode” step.

UI from image:
- Header: Choose Possible Audiences
- Question:
  “Who are these photos for?”
- Subtitle:
  “Select one or more destinations.”
- Audience cards:
  My Family
  Wife’s Family / Close Friends / Parent Family depending actual user data
  Shared Family
  Only Me
  Choose People
- Continue button

Important:
Do not show too many options.
Use user’s saved circles + Only Me + Choose People.

Small details:
- Each audience card has:
  icon/avatar
  title
  member count
  checkbox
- Selected cards show accent border/check.
- Only Me can be selected with other groups only if user intentionally chooses; allow but make it clear.
- Choose People opens custom people picker.

Interactions:
- Select one or more circles
- Continue → Batch Sort Screen
- If only one audience selected, user can skip sorting and go directly to Upload Summary
- If multiple selected, go to Batch Sort Screen

Important product rule:
User is not entering metadata.
User is only choosing event and who can see.

==================================================
5. Batch Sort Screen
==================================================

Purpose:
For large event uploads, sort selected photos into the chosen audiences.

UI from image:
- Header: Sort Photos
- Top chips:
  Unsorted count
  My Family count
  Wife’s Family count
  Shared Family count
  Only Me count
- Grid of thumbnails
- Selected items highlighted
- Bottom instruction card:
  “Tip: Tap or drag across photos to select multiple.”
- Primary button:
  “Set 42 photos for My Family”

Small details:
- Audience chip active state should be clear.
- Unsorted chip should show remaining number.
- Selected photo border should use current audience color/accent.
- Smooth animation when selected photos leave unsorted.
- Undo button after setting a batch.
- Selected count should update instantly.

Interactions:
- Tap audience chip → choose which audience you are sorting for
- Tap photo → select
- Drag/swipe over photos → select multiple
- Tap primary button → assign selected photos to current audience
- After assignment:
  selected photos move from Unsorted to that audience
  counts update
  show small toast:
  “42 photos set for My Family”
- Continue sorting remaining photos

Important:
Use wording:
“Set photos for My Family”
not “Move to My Family”
because visibility is being set, not physically moving files.

Advanced:
Allow photo to be assigned to multiple audiences later, but MVP can set primary visibility per batch.

==================================================
6. Batch Sort Progress / Next Circle Screen
==================================================

Purpose:
Continue sorting remaining photos across selected audiences.

UI from image:
- Same Sort Photos grid
- Top chips show counts:
  Total / grouped / selected / skipped
- Active audience highlighted
- Bottom button:
  “Set 60 photos for Wife’s Family”

Interactions:
- After setting first audience, automatically suggest next audience
- User can manually switch audience chip
- Keep unsorted count visible
- Allow undo last assignment

Small details:
- Progress should feel rewarding.
- Maybe show:
  “158 left to sort”
  “2 groups selected”
- Use subtle progress animation.

==================================================
7. Move Remaining Shortcut
==================================================

Purpose:
When only a few photos remain, let user finish quickly.

UI from image:
Modal:
- dimmed background
- icon/hourglass
- text:
  “Only 3 groups left”
or better:
  “Only 20 photos left”
- question:
  “Set remaining to Only Me?”
- Buttons:
  Cancel
  Set Remaining

Rules:
- Only show when remaining unsorted count is small
  or when only one audience remains.
- Do not force it.
- User can cancel and continue manual sorting.

Interactions:
- Set Remaining → assign all unsorted photos to chosen audience
- Cancel → return to sorting

==================================================
8. Choose People / Custom Screen
==================================================

Purpose:
Select exact people from chat/search when circles are not enough.

UI from image:
- Header: Choose People
- Search field:
  “Search by name or username”
- Recent chats / contacts avatar row
- User cards
- Selected chips at bottom
- Toggle:
  “Save as a circle”
- Done button with count:
  “Done (15)”

Small details:
- Search by username must work.
- Recent chats should show first.
- Selected users appear as chips.
- Save as circle toggle should ask for circle name if enabled.
- User can select/deselect easily.

Interactions:
- Tap person → selected
- Search → filter cloud users
- Done → returns selected people as custom audience
- Save as circle → creates reusable circle

Important:
People are selected from cloud users/chat contacts.
Do not use local SQLite users.

==================================================
9. Upload Summary / Confirmation
==================================================

Purpose:
Confirm event and visibility breakdown before upload/sync.

UI from image:
- Header: Upload Summary
- Event card
- Total photos/videos
- Audience breakdown:
  My Family: 68
  Wife’s Family: 32
  Shared Family: 78
  Only Me: 20
- Upload button
- Review button

Small details:
- Show estimated total size if available.
- Show PC Drive status:
  PC Online → Upload now
  PC Offline → Save pending
- If PC offline, button text should be:
  “Save Pending”
- If PC online:
  “Upload”

Interactions:
- Upload → actual upload/pending logic
- Review → return to sorting screen

==================================================
10. Pending Uploads / Offline Screen
==================================================

Purpose:
When PC is offline, reassure user and keep photos pending.

UI from image:
- Header: Pending Uploads
- Warning card:
  “Please don’t delete the original photos/videos until upload is complete.”
- Pending batches:
  event thumbnail
  event name
  item count
  size
  status:
  Waiting for PC Drive
- Sync icon/spinner
- Footer text:
  “Make sure PC Drive is running and connected to the internet.”

Small details:
- Status should be calm, not scary.
- Use yellow warning only for important message.
- Each pending batch card should show retry/progress.
- If original file missing:
  show:
  “Original file missing. Upload cannot complete.”

Interactions:
- Pull refresh → check PC Drive health
- Retry button optional
- Tap pending batch → pending details

Backend later:
- When PC comes online, WorkManager syncs.

==================================================
11. Event View Screen
==================================================

Purpose:
Show event photos visible to current user.

UI from image:
- Header: Event name
- Filter chips:
  All visible
  Uploaded by me
  Shared with me
- Photo grid
- Selected photos show check badges
- Floating/bottom action bar in selection mode:
  Change Audience
  Move Event
  Delete

Small details:
- Grid thumbnails should be rounded.
- Selection mode should feel smooth.
- Show upload progress overlay if batch syncing.
- Show visibility badge on owned photos:
  “Visible to: My Family”
- Do not show hidden counts.

Interactions:
- Tap photo → open viewer
- Long press → selection mode
- Select multiple
- Change audience → audience picker
- Delete → trash confirmation
- Move Event → choose event

Privacy:
Only show photos visible to current user:
- uploaded by user
- shared directly
- shared to circle user belongs to

==================================================
12. Circle Management Screen
==================================================

Purpose:
Manage saved sharing groups.

UI from image:
- Header: My Circles
- Add button
- Circle cards:
  icon
  name
  member count
  member avatars
- Create Circle card

Examples:
- My Family
- Wife’s Family
- Shared Family
- Close People

Small details:
- Member avatars overlap slightly.
- Cards are rounded.
- Press opens circle details.
- Add button opens Create/Edit Circle.

Interactions:
- Tap circle → edit/view members
- Tap plus → create circle

==================================================
13. Create / Edit Circle Screen
==================================================

Purpose:
Create or edit a saved audience group.

UI from image:
- Header: Create Circle
- Save button
- Circle name field
- Add people search
- selected member chips/avatars
- Permissions section:
  “Who can add photos?”
  Options:
  Can manage
  Can add
  Can only see

Use simple wording:
- Can manage
- Can add
- Can only see

Do not show:
- ACL
- permission schema
- owner/contributor/viewer terms in UI

Interactions:
- Search people
- Add/remove members
- Save
- Delete circle if allowed

==================================================
14. Synced Success / Backup Complete Screen
==================================================

Purpose:
Celebration when pending uploads finish.

UI from image:
- Green check
- Confetti/subtle particles
- Text:
  “Pending uploads completed”
  “Your photos/videos are saved to PC.”
- Small security note:
  “All files are safe and backed up.”
- Button:
  View Event
- Secondary:
  Back to Home

Small details:
- Smooth success animation
- Auto-dismiss optional after few seconds
- Notification should also appear:
  “Pending uploads completed. Your photos/videos are saved to PC.”

==================================================
15. Micro-Interactions and Smoothness
==================================================

Add polish:
- screen transitions: fade + slide
- cards press scale 0.98
- buttons bounce subtly
- photo selection checkmark pop
- drag/swipe selection highlight follows finger
- modal slide/fade
- count changes animate
- progress indicators smooth
- pending sync spinner subtle
- success check animation

Do not overdo animations.
Keep it smooth on mid-range phones.

Suggested timing:
- small interactions: 120–180ms
- modal transitions: 220–300ms
- screen transitions: 250ms

==================================================
PART 2 — BACKEND CONNECTION AFTER FRONTEND
==================================================

After frontend flow is complete with mock/demo state, connect backend.

Backend principles:
- User only chooses event and audience.
- All metadata is automatic.
- Missing metadata must not block upload.
- Drive media remains PC-only.

Automatic metadata:
Backend should store when available:
- uploaderUserId
- eventId
- uploadBatchId
- fileName
- mimeType
- fileSize
- mediaType
- width
- height
- durationMs for videos
- storagePath
- thumbnailPath
- uploadedAt
- takenAt from EXIF if available
- location from EXIF only if privacy allows
- syncStatus
- caption optional

If missing:
- store null
- continue upload

Do not ask user to manually enter:
- uploader
- date/time
- location
- camera/device
- file size
- EXIF

==================================================
Backend Tables
==================================================

Add/update PC backend tables:

drive_events:
- id
- name
- createdByUserId
- coverItemId
- startDate
- endDate
- locationName
- createdAt
- updatedAt

drive_items:
- id
- batchId
- eventId
- uploaderUserId
- mediaType
- mimeType
- fileName
- fileSize
- width
- height
- durationMs
- storagePath
- thumbnailPath
- takenAt
- uploadedAt
- createdAt
- updatedAt
- deletedAt
- syncStatus
- caption
- locationName
- latitude
- longitude
- locationPrivacy
- exifJson

drive_circles:
- id
- name
- ownerUserId
- createdAt
- updatedAt

drive_circle_members:
- circleId
- userId
- role
- createdAt

drive_item_circles:
- itemId
- circleId
- createdAt

drive_item_people:
- itemId
- userId
- role
- createdAt

drive_upload_batches:
- id
- uploaderUserId
- eventId
- totalItems
- status
- createdAt
- completedAt

Indexes:
- drive_items(eventId)
- drive_items(uploaderUserId)
- drive_items(takenAt)
- drive_items(uploadedAt)
- drive_item_circles(circleId)
- drive_item_people(userId)
- drive_upload_batches(status)

==================================================
Drive API Routes
==================================================

Base:
https://home.bookhelloctg.com/hello/api

Health:
GET /drive/health

Events:
GET /drive/events
POST /drive/events
PATCH /drive/events/:id
GET /drive/events/:id/items

Circles:
GET /drive/circles
POST /drive/circles
PATCH /drive/circles/:id
DELETE /drive/circles/:id
POST /drive/circles/:id/members
DELETE /drive/circles/:id/members/:userId

Items:
GET /drive/items
GET /drive/items/:id
POST /drive/upload
PATCH /drive/items/:id
DELETE /drive/items/:id

Visibility:
POST /drive/items/:itemId/circles
DELETE /drive/items/:itemId/circles/:circleId
POST /drive/items/:itemId/people
DELETE /drive/items/:itemId/people/:userId

Batch:
POST /drive/upload-batches
GET /drive/upload-batches/:id
PATCH /drive/upload-batches/:id

Pending/sync:
POST /drive/pending/sync
GET /drive/pending/status

Upload route must accept:
- files[]
- eventId or eventName
- circleIds[]
- allowedUserIds[]
- batchId
- clientLocalIds

If no audience selected:
default to Only Me.

Visibility:
GET /drive/items and event items must return only visible media:
- current user is uploader
- or current user is directly allowed
- or current user belongs to an attached circle

Do not show hidden counts.

==================================================
Android Pending Upload Backend Integration
==================================================

When PC Drive is offline:
Android stores pending upload locally:

local pending record:
- localId
- localUri
- fileName
- mimeType
- fileSize
- eventId or eventName
- selectedCircleIds
- selectedUserIds
- takenAt if available
- createdAt
- pendingStatus
- retryCount

When PC Drive comes online:
- WorkManager uploads files
- sends event/audience metadata
- backend creates events/items/visibility rows
- local pending item becomes synced
- green tick appears
- notification appears

If original file is missing:
- mark failed
- show:
  “Original file missing. Upload cannot complete.”

==================================================
Web Implementation
==================================================

After Android flow, mirror the same in web where practical:
- Drive Home
- Upload
- Choose Event
- Choose Audience
- Choose People
- Upload Summary
- Event View
- Circle Management

If web pending upload is not implemented:
- show Drive upload unavailable while PC Drive is offline
- do not upload Drive media to Cloudflare R2

==================================================
Validation
==================================================

Run:

npm run build
npm --workspace apps/hello run build
npm --workspace apps/browser run build
cd apps/android
.\gradlew.bat :app:assembleDebug --console=plain

If PC backend changed, test:

Invoke-RestMethod https://home.bookhelloctg.com/hello/api/drive/health

If Worker is not changed, do not deploy Worker.

==================================================
Live Tests
==================================================

Test 1: Simple upload
- Select 10 photos
- Choose Event: Daily Memories
- Audience: My Family
Expected:
- upload succeeds if PC online
- or pending if PC offline

Test 2: Large event sorting
- Select 200 photos
- Choose Event: Eid 2026
- Choose audiences:
  My Family, Wife’s Family, Shared Family, Only Me
- Sort by swipe-select
- Set batches
- Move remaining
Expected:
- counts correct
- summary correct
- no lag

Test 3: Choose People
- Search username
- select people
- optionally save circle
Expected:
- audience saved correctly

Test 4: Event visibility
- login as different users
Expected:
- same event shows only allowed photos

Test 5: PC offline
- stop PC backend
- upload
Expected:
- pending upload created
- warning shown
- no crash

Test 6: PC online again
- start backend
- tunnel online
Expected:
- pending upload syncs
- green tick
- notification

==================================================
Final Report
==================================================

Report:
1. Frontend screens implemented.
2. Small interactions implemented.
3. Swipe/drag multi-select status.
4. Backend tables added.
5. API routes added.
6. Pending upload handling.
7. Metadata extraction behavior.
8. What happens when metadata is missing.
9. Build results.
10. Remaining caveats.

Do not:
- make users manually enter metadata
- upload Family Drive media to Cloudflare R2
- use Tailscale
- break chat/auth/calls
- make the UI complex for older users