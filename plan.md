I want to split the app architecture into Drive, Chat, and Calling.

Drive:
- Do not upload Drive photos/videos to Cloudflare.
- Drive photos/videos should only be permanently stored on the PC backend.
- If PC is offline, selected media should be saved as local pending upload records on Android.
- Show pending media in Drive grid with a sync/refresh badge.
- Show warning: “Please don’t delete the original photos/videos until upload is complete.”
- Use WorkManager to retry upload when PC backend is reachable.
- When upload completes, replace pending badge with green tick.
- Send local notification: “Pending uploads completed. Your photos/videos are saved to PC.”
- Drive must still show latest to oldest and grouped month-wise.

Chat:
- Move chat text/message metadata to an always-online temporary Cloudflare backend.
- Use Cloudflare Worker as API.
- Use D1 for users, conversations, messages, delivery status, read status.
- Use Durable Objects/WebSockets for realtime chat rooms and online presence.
- Use R2 only for temporary chat attachments/files/images.
- Add lifecycle cleanup for old temporary files.
- Mobile app should cache recent chat data locally.

Calling:
- Add WebRTC app-to-app/web calling.
- Use Cloudflare Worker + Durable Object only for signaling and call room state.
- Use direct peer-to-peer WebRTC when possible.
- Add TURN/SFU fallback for restrictive networks.
- Prepare Cloudflare Realtime TURN/SFU integration.
- Do not route Drive media through the call/chat backend.

Domains:
- chat.bookhelloctg.com = chat API/realtime
- call.bookhelloctg.com = call signaling
- home.bookhelloctg.com = PC backend for Drive upload when PC online

Do not modify unrelated features except where needed to connect chat/call/drive routing.