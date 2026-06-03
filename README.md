# Hello + GlassBox Monorepo

This repository contains the integrated GlassBox browser shell, the Hello web app, the Android native app, and the Cloudflare chat/call Worker.

The local PC runtime is still one integrated server on `127.0.0.1:3000`, with Hello mounted at `/hello`. Chat, auth, contacts, presence, messages, and call signaling now default to the Cloudflare Worker for Web and Android. Family Drive remains on the PC backend and must not be moved into Cloudflare chat storage.

## Quick Start

Run from the repository root:

```
cd p:\Hasnat\mirror_browser
npm run install:all
npm run dev
```
For Final Release APK:
```
adb devices
cd apps\android

.\gradlew.bat :app:assembleRelease --console=plain; if ($LASTEXITCODE -eq 0) { adb install -r app\build\outputs\apk\release\app-release.apk }

.\gradlew.bat :app:assembleRelease --console=plain
adb install -r app\build\outputs\apk\release\app-release.apk
```

for daily testing:
```
cd P:\Hasnat\mirror_browser\apps\android
.\gradlew.bat :app:installDebug --console=plain
```
For only compile check:
```
.\gradlew.bat :app:compileDebugKotlin --console=plain
```
for issue with gradle build- reset :
```

.\gradlew.bat clean assembleRelease --no-daemon; if ($LASTEXITCODE -eq 0) { adb install -r app\build\outputs\apk\release\app-release.apk }
```

Open:

```text
Integrated app: http://127.0.0.1:3000
Hello web app:  http://127.0.0.1:3000/hello
Hello status:   http://127.0.0.1:3000/api/hello/status
```

For PC Drive access from mobile/web, run the PC backend and Cloudflare Tunnel, then open:

```text
https://home.bookhelloctg.com/hello
```

## Common Commands

Run from the repository root unless noted:

```powershell
npm run dev                         # Build Hello + Browser, then start integrated Electron
npm run build                       # Build Hello frontend and GlassBox browser/Electron bundles
npm test                            # Run Browser TypeScript check
npm run browser:dev                 # Start Browser Electron dev mode
npm run hello:dev                   # Start Hello standalone server from apps/hello
npm --workspace apps/hello run build
npm --workspace apps/browser run build
```

Cloudflare Worker commands:

```powershell
npm --prefix apps/cloudflare/chat-worker run types
npm --prefix apps/cloudflare/chat-worker run deploy
```

Android debug build and install:

```powershell
cd apps\android
.\gradlew.bat :app:assembleDebug --console=plain
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Android release build and install require signing properties in `apps/android/gradle.properties`:

```powershell
cd apps\android
.\gradlew.bat clean assembleRelease --no-daemon
adb install -r app\build\outputs\apk\release\app-release.apk
```

APK outputs:

```text
Debug:   apps/android/app/build/outputs/apk/debug/
Release: apps/android/app/build/outputs/apk/release/
```

## Current Backend Defaults

Chat and account flows are Cloudflare-first:

```text
Cloud chat domain:     https://chat.bookhelloctg.com
Worker fallback:       https://hello-chat-worker.gilbert-jackob3.workers.dev
Cloud health:          https://chat.bookhelloctg.com/health
Cloud auth:            /api/auth/register, /api/auth/login, /api/auth/logout, /api/auth/me
Cloud chat API:        /api/chat/...
Cloud realtime socket: /api/calls/ws?token=<cloud-session-token>
```

Drive stays PC-backed:

```text
Web Drive API:     /hello/api/drive/...
Android Drive API: AppConfig.DRIVE_API_BASE
Storage:           data/hello/family-drive/YYYY/MM/
```

Do not route Family Drive photos/videos through Cloudflare R2 chat attachments. Cloudflare R2 is only for temporary chat/profile/avatar objects under safe prefixes such as `chat/` and `avatars/`.

## Repository Map

```text
README.md                                Monorepo operations guide
package.json                             Root npm workspace scripts
package-lock.json                        Root dependency lock

apps/browser/                            GlassBox Electron browser app
apps/browser/src/App.tsx                 Browser shell and Hello iframe mode
apps/browser/src/main/apiServer.ts       Integrated Express server on 127.0.0.1:3000
apps/browser/src/main/main.ts            Electron main process and permissions
apps/browser/src/server/tabManager.ts    BrowserView lifecycle and tab permissions
apps/browser/src/server/                 Browser APIs, automation, memory, site packs
apps/browser/scripts/                    Dev, Electron, CLI, verification scripts
apps/browser/dist/                       Built browser renderer output
apps/browser/dist-electron/              Built Electron main/preload output

apps/hello/                              Hello web app
apps/hello/server.ts                     Mountable Hello Express + Socket.IO backend
apps/hello/src/App.tsx                   Hello app frame, rail, active panes
apps/hello/src/api.ts                    Cloud chat API client and PC Drive API client
apps/hello/src/SocketContext.tsx         Cloud WebSocket by default, PC Socket.IO only when enabled
apps/hello/src/components/Sidebar.tsx    Chats, contacts, profile, settings, calls, Drive rail
apps/hello/src/components/ChatWindow.tsx Chat body and message composer
apps/hello/src/components/FamilyDrivePane.tsx Web Family Drive media library
apps/hello/src/components/CallOverlay.tsx Web call UI and Cloudflare signaling
apps/hello/src/mediaPermissions.ts       Camera/mic preflight and diagnostics
apps/hello/dist/                         Built Hello frontend output

apps/android/                            Android native app
apps/android/app/src/main/java/com/glassbox/hello/auth/     Cloud auth
apps/android/app/src/main/java/com/glassbox/hello/chat/     Cloud chat, inbox, rooms
apps/android/app/src/main/java/com/glassbox/hello/calls/    Cloud call signaling/WebRTC
apps/android/app/src/main/java/com/glassbox/hello/familydrive/ PC-backed Drive
apps/android/app/src/main/java/com/glassbox/hello/network/  Realtime socket handling
apps/android/app/src/main/java/com/glassbox/hello/core/     AppConfig, preferences, sessions

apps/cloudflare/chat-worker/             Cloudflare Worker for chat/auth/calls
apps/cloudflare/chat-worker/src/index.ts Worker routes, D1, Durable Object, R2
apps/cloudflare/chat-worker/migrations/  D1 schema migrations
apps/cloudflare/chat-worker/wrangler.toml Worker bindings and deploy config

data/browser/                            GlassBox browser runtime data
data/hello/hello.db                      Local Hello SQLite database
data/hello/uploads/                      Local uploaded files and avatars
data/hello/family-drive/                 Family Drive media files
data/hello/cache/                        Cache
data/hello/logs/                         Logs
```

Do not treat `dist/`, `dist-electron/`, `data/*.db`, `data/**/uploads`, `data/**/family-drive`, logs, or cache files as source changes unless a release process explicitly asks for them.

## Runtime Architecture

Integrated local runtime:

```text
npm run dev
  -> npm run build
  -> npm --workspace apps/browser run electron:integrated
  -> Electron loads apps/browser/dist-electron/main/main.cjs
  -> apps/browser/src/main/apiServer.ts starts Express on 127.0.0.1:3000
  -> apps/hello/server.ts mounts Hello under /hello
```

Main surfaces:

```text
GlassBox Browser:
  React shell in apps/browser/src/App.tsx
  Native Electron BrowserView tabs managed by apps/browser/src/server/tabManager.ts

Hello Web:
  React iframe in apps/browser/src/App.tsx
  iframe src="/hello"
  Hello frontend served from apps/hello/dist in integrated mode

Android:
  Native Compose app in apps/android
  Cloud auth/chat/calls by default
  Drive uploads/listing through the PC backend
```

Important integrated PC routes:

```text
/                         GlassBox browser shell
/api/...                  GlassBox Browser APIs
/api/hello/status         Integrated Hello mount status
/api/hello/open           Browser API response for Hello mode
/api/hello/mode/browser   Browser mode switch endpoint
/api/hello/mode/hello     Hello mode switch endpoint

/hello                    Hello UI
/hello/api/...            Local Hello REST API for PC-backed features
/hello/api/files/:fileId  Local file/avatar download route
/hello/api/drive/items    Family Drive listing
/hello/api/drive/upload   Family Drive upload
/hello/api/dev/reset       DEV-only full local Hello reset
/hello/api/dev/reset-local DEV-only full local Hello reset alias
/hello/socket.io          Local Socket.IO endpoint, legacy/PC mode
/hello/uploads/...        Local static upload directory
```

Compatibility routes for older stored Hello data:

```text
/api/files/:fileId        Serves old avatar/message URLs saved before /hello mounting
/uploads/...              Serves old upload URLs saved before /hello mounting
```

Keep these compatibility routes unless all existing rows in `data/hello/hello.db` have been migrated to `/hello/...` paths.

## Cloud Chat And Presence

Web chat in `apps/hello/src/api.ts` uses `fetchCloudChat(...)`, which tries `CHAT_CLOUD_BASE_URL` first and then `CHAT_CLOUD_FALLBACK_URL`. The local `CHAT_API_BASE` constant is legacy/backward compatibility for old local chat paths; do not move Web chat back to `/hello/api`.

Web realtime in `apps/hello/src/SocketContext.tsx` uses Cloudflare WebSocket by default. PC Socket.IO is only used when:

```text
VITE_ENABLE_PC_SOCKET=true
```

Standard realtime event names:

```text
user_presence
user_updated
receive_message
chat_updated
new_chat
user_typing
```

`presence_updated` may still be accepted as a legacy compatibility alias, but new code should use `user_presence`.

Android cloud chat:

```text
Cloud auth:      apps/android/app/src/main/java/com/glassbox/hello/auth/
Cloud chat REST: apps/android/app/src/main/java/com/glassbox/hello/chat/CloudChatApi.kt
Cloud chat repo: apps/android/app/src/main/java/com/glassbox/hello/chat/CloudChatRepository.kt
Realtime:        apps/android/app/src/main/java/com/glassbox/hello/network/SocketManager.kt
Cloud toggle:    HelloPreferences.KEY_CLOUD_CHAT_ENABLED, default true
```

Android opens the Cloudflare WebSocket with the saved cloud session token, sends `identify`/`online`, listens for `user_presence`, and refreshes the inbox on cloud message/chat/presence events.

## Cloudflare Worker

Worker source:

```text
apps/cloudflare/chat-worker/src/index.ts
```

Worker bindings:

```text
DB             D1 database: hello_chat_db
TEMP_FILES     R2 bucket: hello-chat-temp
REALTIME_ROOM  Durable Object namespace
```

Worker routes include:

```text
GET  /health
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
GET  /api/users
GET  /api/users/:id
PATCH /api/users/:id/profile
POST /api/users/:id/avatar
GET  /api/contacts
POST /api/contacts
GET  /api/chat/conversations?userId=<id>
POST /api/chat/conversations
GET  /api/chat/conversations/:id/messages
POST /api/chat/conversations/:id/messages
POST /api/chat/messages/:id/read
POST /api/chat/attachments/upload
GET  /api/chat/attachments/:id
POST /api/calls/start
GET  /api/calls/history
GET  /api/calls/ice-config
WS   /api/calls/ws?token=<cloud-session-token>
WS   /api/calls/:callId/ws
```

DEV-only cloud reset:

```text
POST /api/dev/reset-cloud
```

Security requirements:

```text
ENABLE_DEV_RESET=true
DEV_RESET_SECRET=<secret>
Header: x-dev-reset-secret: <secret>
```

Without both the env flag and matching header secret, the endpoint returns `403`. The reset deletes Cloudflare D1 chat/account/call rows and only deletes R2 objects under `chat/` and `avatars/`. It does not touch Drive, PC files, or local data.

After using the cloud reset endpoint, remove `ENABLE_DEV_RESET` and `DEV_RESET_SECRET` from `wrangler.toml` or Worker environment variables, run `wrangler types`, and redeploy. The live endpoint should return `403` when reset is disabled.

Worker maintenance:

```powershell
npm --prefix apps/cloudflare/chat-worker run types
npm --prefix apps/cloudflare/chat-worker run deploy
```

If Cloudflare route tests return placeholder behavior or `404 not_found`, deploy the Worker first and recheck `/health`.

## DEV Reset

There are two reset surfaces because cloud chat data and Family Drive data live in different places.

Cloud reset:

```text
POST https://chat.bookhelloctg.com/api/dev/reset-cloud
```

Cloud reset requires:

```text
ENABLE_DEV_RESET=true
DEV_RESET_SECRET=<secret>
Header: x-dev-reset-secret: <secret>
```

It deletes these D1 tables:

```text
message_receipts
messages
attachments
conversation_members
conversations
contacts
user_chat_preferences
conversation_preferences
call_events
call_participants
call_sessions
sessions
devices
device_push_tokens
user_profiles
users
```

It also deletes only safe R2 chat/profile prefixes:

```text
chat/
avatars/
```

Local Hello reset:

```text
POST /hello/api/dev/reset
POST /hello/api/dev/reset-local
```

Local reset requires the same protection:

```text
ENABLE_DEV_RESET=true
DEV_RESET_SECRET=<secret>
Header: x-dev-reset-secret: <secret>
```

It clears local Hello account/chat/call/status tables, `file_attachments`, `drive_items`, `data/hello/uploads`, and `data/hello/family-drive`. It does not touch GlassBox browser data, Drive outside the configured `family-drive` directory, or arbitrary PC files.

Full clean-slate expectation after both resets:

```text
Cloud D1: users=0, conversations=0, messages=0, attachments=0
Local DB: users=0, chats=0, messages=0, file_attachments=0, call_logs=0, statuses=0, drive_items=0
Local uploads: only .gitkeep or empty
Local family-drive: empty
Cloud reset endpoint: 403 after disabling reset env
```

## Frontend Path Rules

Current Web constants:

```text
API_BASE                 VITE_HELLO_API_BASE || "/hello/api"
CHAT_CLOUD_BASE_URL     VITE_CHAT_CLOUD_BASE_URL || "https://chat.bookhelloctg.com"
CHAT_CLOUD_FALLBACK_URL VITE_CHAT_CLOUD_FALLBACK_URL || "https://hello-chat-worker.gilbert-jackob3.workers.dev"
CALL_API_BASE           VITE_CALL_API_BASE || "https://chat.bookhelloctg.com/api"
DRIVE_API_BASE          VITE_DRIVE_API_BASE || "https://home.bookhelloctg.com/hello/api"
VITE_ENABLE_PC_SOCKET   false by default; true enables /hello/socket.io
```

Avoid hardcoding machine-specific URLs in source:

```text
http://localhost:3000
http://127.0.0.1:3000
localhost
127.0.0.1
3420
```

Use Cloudflare-backed URLs for chat/auth/calls and the PC Drive tunnel URL for Drive. Local development may override Drive with `VITE_DRIVE_API_BASE=http://127.0.0.1:3000/hello/api`.

## Camera, Microphone, And Calls

Camera/microphone permission handling is split across Electron, iframe policy, server headers, and Hello WebRTC code.

Electron shell permission files:

```text
apps/browser/src/main/main.ts
apps/browser/src/server/tabManager.ts
```

Allowed Electron permissions:

```text
media
camera
microphone
display-capture
```

Allowed trusted Hello origins:

```text
http://127.0.0.1:3000/hello
http://localhost:3000/hello
https://home.bookhelloctg.com/hello
```

Do not allow camera/mic for arbitrary BrowserView tabs. The tab permission handler must deny permissions unless the requesting URL is trusted Hello.

Iframe policy:

```tsx
<iframe
  src="/hello"
  allow="camera; microphone; autoplay; display-capture; clipboard-read; clipboard-write"
/>
```

Server policy:

```text
Permissions-Policy: camera=(self), microphone=(self), fullscreen=(self), display-capture=(self)
```

Hello media diagnostic helper:

```text
apps/hello/src/mediaPermissions.ts
```

The visible `Test Camera/Mic` control belongs in:

```text
Hello Settings -> Camera / Microphone
```

Call signaling is Cloudflare-backed by default. Android has `ENABLE_PC_CALL_SIGNALING = false`; Web call APIs use the Cloudflare chat Worker call routes.

Common call test matrix:

```text
1. Desktop Electron integrated:
   npm run dev
   Open Hello mode
   Settings -> Camera / Microphone -> Test Camera/Mic

2. PC browser:
   Open http://127.0.0.1:3000/hello
   Settings -> Camera / Microphone -> Test Camera/Mic

3. Phone browser:
   Open https://home.bookhelloctg.com/hello
   Settings -> Camera / Microphone -> Test Camera/Mic

4. Direct call:
   Login as two cloud users from two devices
   Start an audio call
   Confirm prompt appears, local stream starts, and signaling moves past permission setup
```

Expected media errors should include the browser error name:

```text
NotAllowedError       Permission denied
NotFoundError         No camera or microphone found
NotReadableError      Device already in use
OverconstrainedError  Selected device/constraint cannot be satisfied
SecurityError         HTTPS/trusted local origin required
NotSupportedError     Browser API unavailable
```

## Family Drive

Family Drive is a central family photo/video library for Hello mobile and web. It intentionally avoids folders, sub-folders, passwords, recent sections, and shared-folder logic. All media is shown latest-to-oldest and grouped by month.

Drive media must stay on the PC backend. It should not be routed through the Cloudflare chat/call backend or temporary attachment storage.

Backend:

```text
apps/hello/server.ts
POST /hello/api/drive/upload
GET  /hello/api/drive/items?limit=60
GET  /hello/api/drive/items/:itemId/file
DELETE /hello/api/drive/items/:itemId
```

Storage:

```text
data/hello/family-drive/YYYY/MM/
```

Uploaded Drive media is physically stored on the PC. The SQLite metadata row lives in `data/hello/hello.db` table `drive_items`. Deleting an item from Drive removes the database entry from the visible library and deletes the stored file from disk when it still exists.

Frontend surfaces:

```text
apps/hello/src/components/FamilyDrivePane.tsx
apps/hello/src/api.ts
apps/android/app/src/main/java/com/glassbox/hello/familydrive/
```

Current Drive features:

```text
Web and Android:
  Upload photos/videos
  Latest-to-oldest month grouping
  Full-screen media viewer
  Favorite heart overlay
  Download action
  Delete action

Android native:
  If the PC/server is offline, selected uploads are saved to a local pending queue.
  Pending items stay visible using their local URI.
  The top-right sync badge retries pending uploads.
  WorkManager retries pending uploads when network connectivity is available.
  A Family Drive notification is shown after pending uploads complete.
```

## Images, Avatars, And Uploads

Local Hello files are stored in SQLite plus disk:

```text
apps/hello/server.ts
data/hello/hello.db
data/hello/uploads/
```

Older rows may contain image URLs like:

```text
/api/files/file_wwlkz64rk
/uploads/example.jpg
```

The integrated mounted app prefers:

```text
/hello/api/files/file_wwlkz64rk
/hello/uploads/example.jpg
```

Because existing users and chats can still reference old paths, `apps/hello/server.ts` serves both old and new routes. If images disappear after a merge, check both:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/api/files/file_wwlkz64rk
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello/api/files/file_wwlkz64rk
```

Both should return `200` with an image content type when the file exists in `data/hello/uploads`.

## Android Notes

Current app identity:

```text
applicationId: com.glassbox.hello
versionName:   1.0
versionCode:   1
minSdk:        24
targetSdk:     36
```

Important config:

```text
apps/android/app/src/main/java/com/glassbox/hello/core/AppConfig.kt
```

Current Android network targets:

```text
SERVER_ORIGIN                 https://home.bookhelloctg.com
CHAT_CLOUD_BASE_URL           https://chat.bookhelloctg.com
CHAT_CLOUD_FALLBACK_URL       https://hello-chat-worker.gilbert-jackob3.workers.dev
CHAT_API_BASE                 https://chat.bookhelloctg.com/api
CALL_API_BASE                 https://chat.bookhelloctg.com/api
DRIVE_API_BASE                https://home.bookhelloctg.com/hello/api
HELLO_WEB_URL                 SERVER_ORIGIN/hello
ENABLE_PC_CALL_SIGNALING      false
```

Debug APK:

```powershell
cd apps\android
.\gradlew.bat :app:assembleDebug --console=plain
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Release APK requires these Gradle properties:

```text
HELLO_UPLOAD_STORE_FILE
HELLO_UPLOAD_STORE_PASSWORD
HELLO_UPLOAD_KEY_ALIAS
HELLO_UPLOAD_KEY_PASSWORD
```

Release build:

```powershell
cd apps\android
.\gradlew.bat clean assembleRelease --no-daemon
```

Optional WebRTC relay forcing:

```powershell
.\gradlew.bat :app:assembleDebug -PwebrtcForceRelay=true --console=plain
```

or:

```powershell
$env:WEBRTC_FORCE_RELAY="true"
.\gradlew.bat :app:assembleDebug --console=plain
```

## Mode And State Preservation

The integrated Browser shell must preserve state when switching between GlassBox Browser and Hello.

Important file:

```text
apps/browser/src/App.tsx
```

Current contracts:

```text
Browser/Hello mode is stored in localStorage key: gb-app-mode
Hello iframe remains mounted even when hidden
BrowserView remains alive and is resized to 0x0 when Hello is visible
BrowserView is restored to previous visible bounds when Browser mode returns
Refreshing while in Hello mode must reopen the shell in Hello mode
```

Do not reintroduce conditional mounting like:

```tsx
{appMode === "hello" && <iframe src="/hello" />}
```

That destroys Hello React state, active chats, sockets, pending text, and call UI when switching back to Browser mode.

Hello app state persistence:

```text
apps/hello/src/App.tsx
apps/hello/src/components/Sidebar.tsx
```

Current Hello localStorage keys:

```text
whatsclone_user_real
hello_cloud_session_token
whatsclone_permissions
whatsclone_active_rail_tab
whatsclone_active_chat_<userId>
```

Logout must call Cloudflare logout, clear `hello_cloud_session_token`, disconnect the Cloudflare websocket through user state teardown, clear current user/auth state, and return to the login screen.

## Data And Persistence

Runtime data is local and should be handled carefully:

```text
data/browser/              GlassBox browser data
data/hello/hello.db        Local Hello SQLite database
data/hello/uploads/        Local uploaded files and avatars
data/hello/family-drive/   Family Drive media files
data/hello/cache/          Cache
data/hello/logs/           Logs
```

Source files should not depend on machine-specific absolute paths. Runtime location constants live in:

```text
apps/browser/src/server/paths.ts
apps/browser/src/main/memoryDb.ts
apps/hello/server.ts
```

Do not commit user runtime database changes, uploaded media, backups, logs, cache files, or generated build output during normal code patches.

## Verification

Core checks:

```powershell
npm test
npm run build
npm --workspace apps/hello run build
npm --workspace apps/browser run build
cd apps\android
.\gradlew.bat :app:assembleDebug --console=plain
```

Worker checks:

```powershell
npm --prefix apps/cloudflare/chat-worker run types
npm --prefix apps/cloudflare/chat-worker run deploy
```

Route checks after `npm run dev`:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/api/hello/status
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello/api/health
Invoke-WebRequest -UseBasicParsing https://chat.bookhelloctg.com/health
```

Permissions-Policy check:

```powershell
(Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello).Headers
```

Port/process check:

```powershell
Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
Get-Process node,electron -ErrorAction SilentlyContinue
```

Cloud chat smoke checklist with PC backend off:

```text
1. Web user registers/logs in through Cloudflare.
2. Android user registers/logs in through Cloudflare.
3. Web sees Android online through user_presence.
4. Android sees Web online through user_presence.
5. Web sends a message; Android receives receive_message without refresh.
6. Android sends a message; Web receives receive_message without refresh.
7. Refresh Web; message history loads from Cloudflare.
8. Web logout invalidates Cloudflare token and returns to login.
```

Latest validated live smoke from this repo:

```text
Date:                         2026-06-02
Cloud domain:                 https://chat.bookhelloctg.com
PC backend for chat:           off
Web sees Android active:       yes
Android sees Web active:       yes
Web -> Android realtime:       yes
Android -> Web realtime:       yes
History loads from Cloudflare: yes
Android debug build:           yes
Logout source path wired:      yes
Manual browser-click logout:   not rerun in latest smoke
Reset without secret:          403
Cloud D1 after cleanup:        users=0, conversations=0, messages=0, attachments=0
Local Hello DB after cleanup:  users=0, chats=0, messages=0, file_attachments=0, call_logs=0, statuses=0, drive_items=0
```

## Merge And Patch Checklist

Before merging future updates:

```text
1. Check git state:
   git status -sb

2. Inspect source diff:
   git diff -- README.md apps/browser apps/hello apps/android apps/cloudflare package.json package-lock.json

3. Avoid broad staging if runtime files changed:
   Do not use git add -A when data/, uploads/, logs/, cache/, dist/, or backups appear.

4. Keep the local integrated server contract:
   127.0.0.1:3000
   /hello
   /hello/api
   /hello/socket.io

5. Keep Cloudflare chat default:
   https://chat.bookhelloctg.com
   /api/auth
   /api/chat
   /api/calls/ws

6. Keep Drive PC-backed:
   /hello/api/drive
   data/hello/family-drive/YYYY/MM/

7. Keep old media compatibility routes:
   /api/files/:fileId
   /uploads/...

8. Keep Electron camera/mic permission allowlist limited to trusted Hello origins.

9. Re-run checks:
   npm test
   npm run build
   .\gradlew.bat :app:assembleDebug --console=plain

10. For mobile PC Drive changes, verify:
    https://home.bookhelloctg.com/hello/api/drive/health
```

When resolving merge conflicts, protect these contracts first:

```text
Cloudflare chat default:       chat.bookhelloctg.com
Single integrated PC server:   127.0.0.1:3000
Hello mount path:              /hello
Hello PC API path:             /hello/api
Hello PC socket path:          /hello/socket.io
Legacy file route:             /api/files/:fileId
Browser mode iframe:           src="/hello"
Drive storage:                 data/hello/family-drive/
```

## Publishing To GitHub

Current target remote:

```text
https://github.com/gilbertjackob-tech/Hello
```

Check remotes and branch:

```powershell
git remote -v
git branch --show-current
```

Recommended source-only staging pattern:

```powershell
git status -sb
git add -- README.md package.json package-lock.json apps/browser apps/hello apps/android apps/cloudflare
git status -sb
git commit -m "update hello glassbox cloud chat docs"
git push origin main
```

Before committing, inspect `git status -sb` and avoid staging runtime data under `data/`, generated `dist/`, uploaded media, cache, logs, or local database files.

If a normal push reports up-to-date but remote `main` does not advance:

```powershell
git rev-parse HEAD
git ls-remote origin refs/heads/main
git push origin HEAD:main
```

## Troubleshooting

Web app does not open:

```text
Run npm run dev from the repo root.
Check http://127.0.0.1:3000/api/hello/status.
Check port 3000 is listening.
```

Cloud chat does not sync:

```text
Check https://chat.bookhelloctg.com/health.
Check the browser has hello_cloud_session_token after login.
Check WebSocket URL is wss://chat.bookhelloctg.com/api/calls/ws?token=...
Check Android CloudSessionManager has a token.
Check Worker was deployed after source changes.
```

Reset endpoint returns 403:

```text
That is expected unless ENABLE_DEV_RESET=true and x-dev-reset-secret matches DEV_RESET_SECRET.
This applies to both /api/dev/reset-cloud and /hello/api/dev/reset.
Do not leave reset enabled after a cleanup. Disable the env vars and redeploy the Worker if cloud reset was enabled.
```

Camera/mic prompt does not appear:

```text
Check /hello is loaded over trusted localhost or HTTPS.
Check iframe allow policy in apps/browser/src/App.tsx.
Check Permissions-Policy header from /hello.
Check Electron permission logs from apps/browser/src/main/main.ts and tabManager.ts.
Use Hello Settings -> Camera / Microphone -> Test Camera/Mic.
```

Images or avatars are broken:

```text
Check whether stored URL starts with /api/files or /hello/api/files.
Check both compatibility routes return 200.
Check data/hello/uploads contains the actual file.
Check apps/hello/server.ts still registers /api/files/:fileId when basePath is /hello.
```

Phone cannot connect to PC features:

```text
Use the Cloudflare Tunnel HTTPS URL, not localhost.
Phone localhost means the phone itself, not the PC.
Verify Cloudflare Tunnel forwards `home.bookhelloctg.com` to local 127.0.0.1:3000.
Drive requires the PC backend to be reachable.
```

Socket.IO fails:

```text
For Cloudflare chat, Web and Android should use the Cloudflare WebSocket.
For legacy PC socket mode, client path must be /hello/socket.io and server socketPath must be /hello/socket.io.
Do not use standalone localhost socket URLs from phone/mobile.
```
