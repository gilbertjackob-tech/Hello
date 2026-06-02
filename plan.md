Important: The fix is NOT to go back to /hello/api or local SQLite. The fix is stable Cloudflare D1 conversation identity.

use the 3 error images to get better idea.

Current symptoms:
1. On PC web app, I can find the mobile user from the active/user list.
2. But when I click that mobile user, the chat page becomes blank, so I cannot send a message.
3. From mobile, I cannot normally find the PC/web user unless I manually search by name.
4. When mobile sends “hi” to the PC/web user, it appears on web.
5. But when mobile sends another message, a new chat card/conversation appears again.
6. Every new message creates another chat with the same user.
7. It should use ONE direct chat/conversation between the same two users.

Goal:
Web and Android must use the same Cloudflare D1 chat database, same users, same conversations, same messages, and same stable direct conversation ID.

Do NOT route chat/users/messages back to local /hello/api.
Do NOT use PC Socket.IO as default.
Do NOT touch Drive media routing.
Drive stays PC-only.

Root fix required:
Implement stable direct conversation identity and proper find-or-create behavior.

Backend / Worker tasks:

1. Audit Cloudflare conversation schema.
Check:
- conversations
- conversation_members
- messages
- message_receipts

For direct one-to-one chats, add/ensure a stable key:
- type = "direct"
- directKey = sorted user IDs joined with ":"
Example:
userA:userB sorted lexicographically.

2. Add unique constraint/index if possible:
- UNIQUE(type, directKey)
or equivalent D1-safe uniqueness.

3. Fix POST /api/chat/conversations and/or POST /api/chat/conversations/direct:
When creating a direct chat:
- accept targetUserId / participantId
- calculate directKey from currentUserId + targetUserId
- first query existing direct conversation by directKey
- if exists, return the existing conversation with participants and latest message
- if not exists, create one conversation and two conversation_members
- do not create duplicate direct conversations for the same two users

4. Fix message sending:
When sending a message:
- message must require an existing conversationId
- if Android currently sends targetUserId only, repository must first call getOrCreateDirectConversation(targetUserId)
- then send message to that returned conversation.id
- never create a new conversation per message

5. Add duplicate cleanup migration/dev repair:
There are already duplicate conversations in my D1.
Add a DEV-only repair endpoint or script:
POST /api/dev/repair-direct-conversations

Security:
- requires ENABLE_DEV_RESET=true
- requires x-dev-reset-secret

Repair logic:
- find duplicate direct conversations with same participant pair
- choose canonical conversation:
  - preferably one with most messages
  - or oldest createdAt
- move messages from duplicates to canonical conversation
- move receipts if needed
- delete duplicate conversation_members
- delete duplicate conversations
- rebuild/update latestMessage/updatedAt
- return report: pairsFixed, conversationsDeleted, messagesMoved

6. Add full reset endpoint if not already added:
POST /api/dev/reset-cloud
Same security:
- ENABLE_DEV_RESET=true
- x-dev-reset-secret
Deletes all test users/chats/sessions/messages/contacts/preferences/calls from D1.
Do not touch Drive.
Do not touch PC files.

Web tasks:

7. Fix active/mobile user click behavior.
When clicking a user from active list or discovery list:
- do not set selectedChat to a raw User object
- call getOrCreateDirectConversation(user.id)
- wait for returned conversation
- add/update it in chats list
- set selectedChat to that conversation
- load messages for that conversation.id
- open ChatWindow with real conversation object

If the conversation is loading:
- keep UI stable
- show small loading state
- do not show blank page
- do not show placeholder frame unless no user/chat is selected.

8. Fix web chat list dedupe.
When fetching/rendering chats:
- dedupe by conversation.id
- for direct conversations, also dedupe by directKey / participant pair if returned
- if duplicates exist in response, merge them client-side temporarily but backend repair should fix permanently.

9. Fix web send message.
When selected target is a user:
- first ensure conversation exists
- then send message to conversation.id
When selectedChat is conversation:
- send to selectedChat.id
Do not create a new conversation on every send.

Android tasks:

10. Fix Android contact/user discovery.
Android should list Web/PC cloud users from:
GET /api/users
and/or GET /api/contacts
using Cloudflare only.

If user is not a contact but exists in users, search should find them.
If user is active/online, they should appear in active list.

11. Fix Android direct message flow.
When mobile sends message to a user:
- getOrCreateDirectConversation(targetUserId)
- cache returned conversation.id
- send all future messages to same conversation.id
- do not call create conversation for every message
- update local cache conversation mapping:
  directKey -> conversationId

12. Fix Android chat list dedupe.
- dedupe by conversation.id
- directKey if available
- do not display repeated cards for the same user pair

Realtime / presence tasks:

13. Standardize event names across Worker, Web, Android.
Use:
- user_presence
- user_updated
- receive_message
- chat_updated
- new_chat
- user_typing

If old names exist like presence_updated, support both temporarily.

14. When a message is sent:
Worker should broadcast:
- receive_message to other participant(s)
- chat_updated to all participants
- not new_chat every time unless the conversation was newly created

15. When direct conversation is created:
Worker should broadcast:
- new_chat only once for newly created conversation
- chat_updated for updates

Validation:

16. Build:
npm run build
npm --workspace apps/hello run build
npm --workspace apps/browser run build
npm --prefix apps/cloudflare/chat-worker run types
npm --prefix apps/cloudflare/chat-worker run deploy
cd apps/android
.\gradlew.bat :app:assembleDebug --console=plain

17. Live test with PC backend OFF:
- Web user login.
- Android user login.
- Web sees Android user in users/active list.
- Click Android user on Web.
Expected:
  - real direct conversation opens
  - no blank page
  - message box usable

- Send Web → Android:
Expected:
  - Android receives inside same conversation

- Send Android → Web:
Expected:
  - Web receives inside same conversation

- Send multiple messages Android → Web:
Expected:
  - still ONE chat card
  - messages append inside same chat
  - no duplicate Nowshin cards

- Refresh web:
Expected:
  - same single conversation loads
  - history is preserved

- Logout/login:
Expected:
  - no stale session
  - same cloud user identity
  - no duplicate local/SQLite user mixing

Report:
- Did backend add stable directKey? yes/no
- Did createDirect return existing conversation? yes/no
- Did duplicate repair run? yes/no
- Number of duplicate conversations removed
- Web active user click fixed? yes/no
- Android repeated-message duplicate chat fixed? yes/no
- Web ↔ Android same conversation verified? yes/no