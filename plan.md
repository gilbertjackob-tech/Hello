
Now properly wire the WEB app so Web + Android use the SAME Cloudflare backend for all communication.

Important architecture:
- Web chat/account/users/contacts/profile/messages/attachments must use Cloudflare.
- Android chat/account/users/contacts/profile/messages/attachments must use the same Cloudflare D1/R2 data.
- PC/local /hello/api must NOT be used for chat/account/user/profile/contact/message storage.
- PC backend/Tailscale is only for Family Drive photos/videos final storage.
- Drive photos/videos must never go to Cloudflare.

Cloud backend:
Production:
https://chat.bookhelloctg.com

Fallback:
https://hello-chat-worker.gilbert-jackob3.workers.dev

Cloud storage ownership:
- Auth/session → Cloudflare Worker + D1
- Users/profiles → Cloudflare Worker + D1
- Contacts → Cloudflare Worker + D1
- Conversations/chats → Cloudflare Worker + D1
- Messages → Cloudflare Worker + D1
- Message receipts → Cloudflare Worker + D1
- Chat attachments/images/files → Cloudflare Worker + R2
- Profile avatars → Cloudflare Worker + R2
- Realtime chat/call signaling → Cloudflare Durable Object WebSocket
- Drive photos/videos → PC backend only

Main problems to fix:
1. Web does not show active phone user.
2. Web chat flickers between blank view and placeholder chat frame.
3. Web logout does not work properly.
4. Web and Android must use the same cloud user/chat database.
5. Need dev-only way to remove/reset all cloud users/chats/test data.

Tasks:

1. Audit current web wiring.
Check these files:
- apps/hello/src/api.ts
- apps/hello/src/SocketContext.tsx
- apps/hello/src/components/Sidebar.tsx
- apps/hello/src/components/ChatWindow.tsx
- apps/hello/src/components/ChatRoom or equivalent
- auth/login/register components
- settings/profile/avatar components

Find any usage of:
- /hello/api/users
- /hello/api/chats
- /hello/api/files
- Socket.IO local PC socket
- localhost
- 127.0.0.1
- VITE_ENABLE_PC_SOCKET defaulting to true
- CHAT_API_BASE used for chat/account
- API_BASE used for chat/account
- local PC logout route

2. Web API wiring rules.
In apps/hello/src/api.ts, these must use Cloudflare by default:
- register/login/logout
- current user / me
- fetch users
- fetch single user
- user presence
- contacts
- add/remove contacts
- fetch conversations/chats
- create chat
- create direct chat
- fetch messages
- send message
- message receipts/read status
- typing events if HTTP-backed
- upload chat attachment
- upload profile avatar
- fetch/patch chat preferences
- call APIs

Only these can use PC backend / DRIVE_API_BASE:
- fetchDriveItems
- uploadDriveFiles
- deleteDriveItem
- any Family Drive file fetch route

3. WebSocket wiring.
In apps/hello/src/SocketContext.tsx:
- Cloudflare WebSocket must be default.
- PC Socket.IO must be opt-in only.

Correct rule:
- VITE_ENABLE_PC_SOCKET=true → use old PC Socket.IO for dev only.
- Otherwise use Cloudflare WebSocket.

The WebSocket URL should use the cloud token:
wss://chat.bookhelloctg.com/api/calls/ws?token=<cloud_session_token>

Do not default to local PC Socket.IO.

4. Active phone user / presence.
Fix presence so Web sees Android online users.

When Web connects:
- get cloud session token
- connect Cloudflare WebSocket
- send identify/hello event if required
- mark current user online in Durable Object/presence layer

When Android connects:
- it should do the same cloud identification

Worker should broadcast presence events to all relevant clients:
- user_presence
- user_updated
- chat_updated
- receive_message
- new_chat
- user_typing

Web Sidebar should listen to those events and update visible users/contacts without needing the Add Contact dialog open.

Do not use PC Socket.IO presence for cloud users.

5. Fix chat flickering.
Current issue: chat view flickers between blank and placeholder frame.

Fix rules:
- Do not clear selectedChat while cloud fetch is pending.
- Do not clear messages to empty placeholder before new messages arrive.
- Load cached conversations/messages first.
- Then refresh from Cloudflare.
- Keep previous UI visible during refresh.
- Show small loading indicator, not a blank chat frame.
- If cloud fetch fails, keep cached messages and show a small inline error.
- Only show empty placeholder if there is truly no selected chat.

Check React state effects for loops:
- selectedChat
- chats
- messages
- currentUser
- socket connection
- auth token validation

Prevent repeated fetch → clear → placeholder → fetch loops.

6. Fix logout.
Web logout must:
- call POST https://chat.bookhelloctg.com/api/auth/logout with Bearer token
- clear cloud session token
- clear cached current user
- clear cached chats/messages/contacts if needed
- clear socket connection
- reset React auth state
- navigate to login/register screen

Do not use local /hello/api/logout for cloud users.

7. Remove/reset all users and chats.
Add a DEV-ONLY Cloudflare Worker reset endpoint or script.

Endpoint:
POST /api/dev/reset-cloud

Security:
- Must require ENABLE_DEV_RESET=true env variable.
- Must require header:
  x-dev-reset-secret: <secret>
- Must return 403 when disabled/missing secret.
- Must never be shown in public UI.

It should delete cloud test data from D1:
- message_receipts
- messages
- attachments
- conversation_members
- conversations
- contacts
- user_chat_preferences
- conversation_preferences
- call_events
- call_participants
- call_sessions
- sessions
- devices
- device_push_tokens
- user_profiles
- users

For R2:
- If safe, delete test chat attachments/avatar objects under chat/profile test prefixes.
- Do not delete Drive files.
- Do not touch PC Drive routes.

8. Make Web and Android data compatible.
Verify that Web and Android use same IDs and response shapes:
- user.id
- conversation.id
- message.id
- senderId
- participant user IDs
- attachment URLs
- avatar URLs
- createdAt/updatedAt timestamps

If Android sends a message, Web must render it.
If Web sends a message, Android must render it.
If Android user is online, Web must show active/online user.
If Web user logs out, Android should see presence update.

9. Worker compatibility.
If Worker event names or endpoint shapes are missing, add/fix them.
Do not route chat back to PC.

Required cloud endpoints should work:
- POST /api/auth/register
- POST /api/auth/login
- POST /api/auth/logout
- GET /api/auth/me
- GET /api/users
- GET /api/users/:id
- GET /api/contacts
- POST /api/contacts
- GET /api/chat/conversations
- POST /api/chat/conversations
- GET /api/chat/conversations/:id/messages
- POST /api/auth/register
- POST /api/auth/login
- POST /api/auth/logout
- GET /api/auth/me
- GET /api/users
- GET /api/users /api/chat/conversations/:id/messages
- POST /api/chat/messages/:id/read
- POST /api/chat/attachments/upload
- GET /api/preferences/chat
- PATCH /api/preferences/chat
- WebSocket /api/calls/ws or current cloud realtime socket path

10. Validation commands.
Run:
npm run build
npm --workspace apps/hello run build
npm --workspace apps/browser run build
npm --prefix apps/cloudflare/chat-worker run types
npm --prefix apps/cloudflare/chat-worker run deploy

If lint has unrelated old errors, report them separately, but production build must pass.

11. Live smoke test.
With PC backend OFF:
- Open Web app.
- Open Android app.
- Register/login Web user.
- Register/login Android user.
- Web should see Android user.
- Android should see Web user.
- Android online presence should show on Web.
- Send message Android → Web.
- Send message Web → Android.
- No flickering blank/placeholder chat view.
- Logout works on Web.
- Login again works.
- Chat history still loads from Cloudflare.
- Chat attachment upload works through R2.
- Profile avatar upload works through R2.
- Drive shows PC offline/pending only.

Important:
Do NOT “fix” this by routing chat/users/messages to local /hello/api.
Do NOT make PC Socket.IO default again.
Do NOT upload Drive media to Cloudflare.
The goal is one shared cloud communication database/storage for both Web and Android.