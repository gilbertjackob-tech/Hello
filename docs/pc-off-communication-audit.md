# PC-Off Communication Audit

This audit tracks the ownership boundary for the "PC off but communication still works" milestone.

| Area | Storage owner | PC off works | Responsible routes/files |
| --- | --- | --- | --- |
| Auth/login/session | Cloudflare D1 `users`, `sessions`, `devices`; Android local cloud session cache | Yes | Worker `/api/auth/register`, `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`; `apps/cloudflare/chat-worker/src/index.ts`; Android `CloudAuthApi.kt`, `CloudSessionManager.kt`; web `apps/hello/src/api.ts` |
| Security questions | Cloudflare D1 `users.security_question`; answer stored as salted hash in `security_answer_hash/security_answer_salt` | Yes | Worker auth handlers in `apps/cloudflare/chat-worker/src/index.ts`; migration `apps/cloudflare/chat-worker/migrations/0003_cloud_account_data.sql` |
| Current user | Cloudflare D1 profile rows plus Android `SessionManager`/`CloudSessionManager`, web `localStorage` | Yes | Worker `/api/auth/me`; Android `HelloApp.kt`, `ChatScreen.kt`; web `App.tsx`, `api.ts` |
| Profile data | Cloudflare D1 `users`, `user_profiles` | Yes | Worker `/api/users`, `/api/users/:id`, `/api/users/:id/profile`; Android `CloudUserRepository.kt`; web `patchCloudUserProfile` in `api.ts` |
| Profile avatar | Cloudflare R2 under `avatars/{userId}/profile.*`; URL in Cloudflare D1 | Yes | Worker `/api/users/:id/avatar`; Android `CloudUserRepository.uploadAvatar`; web `uploadCloudUserAvatar`; not Drive |
| Contacts/family users | Cloudflare D1 `contacts`; user discovery from `users/user_profiles` | Yes | Worker `/api/contacts`, `/api/users`; Android `PeopleScreen.kt`, `CloudUserRepository.kt`; web `fetchCloudContacts`, `addCloudContact` |
| Chat preferences | Cloudflare D1 `user_chat_preferences`, `conversation_preferences`; UI-only prefs stay local | Yes | Worker `/api/preferences/chat`; Android `SettingsScreen.kt`, `CloudUserRepository.kt`; web `fetchCloudChatPreferences`, `patchCloudChatPreferences` |
| Conversation list | Cloudflare D1 `conversations`, `conversation_members`; Android cloud chat cache | Yes | Worker `/api/chat/conversations`; Android `CloudChatRepository.kt`; web `fetchCloudConversations`, `fetchChats` |
| Messages | Cloudflare D1 `messages`; Android recent message cache | Yes | Worker `/api/chat/conversations/:id/messages`; Android `CloudChatRepository.kt`; web `fetchCloudMessages`, `sendCloudMessage` |
| Message receipts | Cloudflare D1 `message_receipts` | Yes | Worker `/api/chat/messages/:id/read`; web `markCloudMessageRead`; Android `CloudChatApi.markRead` |
| Chat attachments | Cloudflare R2 temp objects under `chat/{attachmentId}/...`; D1 `attachments` with expiry | Yes | Worker `/api/chat/attachments/upload`, `/api/chat/attachments/:id`; Android `CloudChatApi.uploadAttachment`; web `uploadCloudChatAttachment` |
| Themes/settings/preferences | Device-local only | Yes | Android `HelloPreferences.kt`, Settings shared prefs; web `ThemeContext.tsx`, `NotificationContext.tsx` |
| Drive photos/videos | PC backend SQLite/disk only; never Cloudflare | No, upload becomes pending | Android `DrivePcApiClient.kt`, `FamilyDriveRepository.kt`; web Drive routes use `DRIVE_API_BASE`; backend `/hello/api/drive/*` |
| Pending Drive uploads | Android local pending store; WorkManager retries to PC | Yes as pending state | Android `FamilyDrivePendingStore.kt`, `FamilyDriveUploadWorker.kt`, `FamilyDriveScreen.kt` |
| Call signaling | Deferred; PC socket path gated off by default for this milestone | Not part of milestone | Android `AppConfig.ENABLE_PC_CALL_SIGNALING=false`; web `VITE_ENABLE_PC_SOCKET` gate in `SocketContext.tsx`; future Durable Object phase |

Notes:
- Cloudflare R2 is used for chat temporary attachments and profile avatars only.
- Family Drive photos/videos are not routed to Cloudflare. Android keeps only pending metadata and local URI references until the PC backend is reachable.
- `wrangler d1 migrations apply hello_chat_db --remote` is currently blocked by Cloudflare API auth code `10000`. The Worker contains guarded schema bootstrap so the app is not blocked, but the Cloudflare token permissions still need to be fixed for normal migration operations.
