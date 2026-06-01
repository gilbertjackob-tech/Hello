Focus only on Drive Tab offline/pending upload behavior.

Current Drive logic:
- Users upload photos/videos from mobile.
- Server/PC stores uploaded media centrally.
- Drive displays all photos/videos latest to oldest and grouped month-wise.
- No folders, no password, no recent section.

Now add offline/pending upload support.

Required behavior:

1. If the PC/server is online:
   - Upload selected photos/videos normally to the backend.
   - Save them on PC central storage.
   - Show them in Drive as synced items.

2. If the PC/server is offline, unreachable, or upload fails because connection is unavailable:
   - Do not discard the upload.
   - Add selected photos/videos to a local pending upload queue on Android.
   - Show those items immediately in the Drive grid using local thumbnail/URI.
   - Display them latest to oldest and grouped month-wise with normal items.
   - Add a small refresh/sync icon badge at the top-right corner of each pending image/video thumbnail.
   - Show this user message after local pending upload is created:
     “Saved locally. Waiting for PC connection. Please don’t delete the original photos/videos until upload is complete.”

3. Pending item states:
   - PENDING_LOCAL
   - UPLOADING
   - SYNCED
   - FAILED_RETRYABLE

4. Upload retry system:
   - Use Android WorkManager.
   - Retry pending uploads automatically when network is available.
   - Also retry when Drive tab is opened.
   - Also allow manual retry when user taps the refresh/sync icon on a pending item.
   - Keep pending items visible until the backend confirms successful upload.
   - After success, replace the local pending item with the synced server item or mark it as synced and remove the refresh icon.

5. Notification:
   - Create Android notification channel for Drive uploads.
   - When pending uploads are completed successfully, send a local push notification:
     Title: “Family Drive”
     Body: “Pending uploads completed. Your photos/videos are saved to PC.”
   - If multiple items completed, include count:
     “12 pending uploads completed and saved to PC.”

6. Local storage:
   - Store pending queue metadata locally using Room or DataStore.
   - Metadata should include:
     id
     localUri
     displayName
     mimeType
     mediaType photo/video
     size
     createdAt
     monthKey
     status
     retryCount
     lastError
   - If possible, persist URI permission or copy selected files into app-scoped local pending cache for safer retry.
   - If copying files is too large, keep local URI and show the warning not to delete originals until upload completes.

7. UI:
   - Drive Home should remain simple.
   - No folders.
   - No password.
   - No recent section.
   - Main card: All Photos & Videos.
   - Grid: latest to oldest, grouped by month.
   - Pending thumbnails show refresh/sync badge top-right.
   - Uploading thumbnails may show circular progress.
   - Synced thumbnails show no badge.

8. Backend:
   - Keep existing Drive APIs:
     POST /hello/api/drive/upload
     GET /hello/api/drive/items?limit=60
     GET /hello/api/drive/items/:itemId/file
   - Do not change unrelated chat/auth/browser features.

9. Testing:
   - Test with PC/server online: upload succeeds directly.
   - Test with PC/server offline: item appears as pending with refresh icon.
   - Restart app: pending items still visible.
   - Turn PC/server online: pending uploads auto-upload.
   - After upload success: refresh icon disappears.
   - Notification appears after pending uploads complete.