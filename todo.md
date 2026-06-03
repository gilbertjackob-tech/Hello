# Snapchat-Style Hello Stories Camera Plan

## Summary
Build a camera-first Stories composer for Android, using the Snapchat reference as the interaction model. The first implementation will support photos only, with an advanced AR foundation: CameraX live preview, MediaPipe face landmarks/mesh, live filter/background/effect previews, gallery import, final 9:16 export, Cloudflare story upload, and the existing 24-hour story feed/viewer.

## Key Changes
- Replace the current text-first create-status dialog with a full-screen `StoryCameraScreen`.
- Add CameraX dependencies and a new capture stack: live preview, front/back flip, flash, timer, grid, gallery/memories picker, capture button, and collapsed/expanded right-side tool rail.
- Add MediaPipe Tasks Vision Face Landmarker as the AR engine for face-aware effects. Use it for face masks, face outline/shape overlays, beauty/smoothing, stickers anchored to landmarks, and background/green-screen style effects.
- Keep v1 photo-only. Do not implement video recording yet, but design `StoryDraft` and `StoryEffect` models with a `mediaType` field so video can be added later without rewriting the editor.
- Add a bottom lens/effect carousel with categories matching the reference style: `Favorites`, `For You`, `Aesthetic`, `Games`, `Backgrounds`, `Face`.
- Support both camera capture and gallery image import. Imported images go through the same editor/effects/export path as captured photos.
- Export the final story as a 1080x1920 JPEG with all selected effects, overlays, stickers, text, crop, background, and face overlays baked in.
- Wire Android to Cloudflare `/api/stories/upload` and `/api/stories` using media IDs instead of the older `/statuses` text-only path. Keep `/statuses` only as a fallback while migrating.
- Update the story feed/viewer models to understand Cloudflare story media arrays, analytics, reactions/comments, and 24-hour expiry.

## Implementation Details
- Create a `status/camera` package with:
  - `StoryCameraScreen`: full-screen Snapchat-like capture UI.
  - `StoryCameraViewModel`: permissions, CameraX state, selected lens/effect, capture, gallery import, upload/post state.
  - `StoryEffectEngine`: applies color filters, overlays, stickers, backgrounds, and MediaPipe face landmark effects.
  - `StoryExportRenderer`: renders the final 9:16 bitmap for upload.
- Keep existing story list/viewer entry points, but make “Add status / My story” open `StoryCameraScreen`.
- Use `PreviewView` inside Compose via `AndroidView`; use `ImageCapture` for still capture and `ImageAnalysis` for face landmark frames.
- Right rail tools for v1: Flip, Flash, HD Mode, Selfie Settings, Timer, Green Screen, Grid. Show disabled-looking labels only if the action is actually implemented; otherwise omit it.
- Editor after capture/import must support: retake, save draft to cache, text tool, sticker tool, effect carousel, crop/position, background replace, post.
- Store only temporary camera/gallery working files in app cache. Uploaded story media lives in Cloudflare R2 and expires through the story lifecycle.
- Add graceful fallback: if MediaPipe initialization fails, camera capture and non-face filters still work, and face effects are hidden.

## Test Plan
- Build checks:
  - `.\gradlew.bat :app:compileDebugKotlin --console=plain`
  - `.\gradlew.bat :app:assembleDebug --console=plain`
- Manual Android checks:
  - First launch asks camera permission and opens camera after permission grant.
  - Front/back flip, flash, timer, grid, gallery import, and capture work on a real device.
  - Face effects appear only when a face is detected and stay anchored while the user moves.
  - Background and color filters preview live and match the exported story.
  - Posting uploads to Cloudflare, appears in the Status tab and chat story strip, expires after 24 hours, and records views.
  - If camera permission is denied, show a useful empty state with gallery import still available.
- Regression checks:
  - Existing chat camera attachment flow still works.
  - Existing story viewer still opens old `/statuses` stories during migration.
  - App does not crash on devices without a front camera or with low memory.

## Assumptions
- User chose advanced AR effects, so the plan uses MediaPipe face landmarks rather than basic ML Kit-only detection.
- User chose photo-first, so video capture, sounds, multi-snap video, and director mode are intentionally out of v1.
- Effects should be real and exportable, not decorative UI labels.
- Cloudflare remains the source of truth for 24-hour stories; PC Drive backup can remain a later sync layer unless explicitly prioritized.
