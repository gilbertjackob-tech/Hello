# Hello + GlassBox Monorepo

This repository contains the integrated GlassBox browser shell and the Hello chat app. The production-style integrated runtime uses one local server at `127.0.0.1:3000`, with Hello mounted under `/hello`.

## Quick Start

Run these commands from the repository root:

```powershell
cd p:\Hasnat\mirror_browser
npm run install:all
npm run dev
tailscale serve --bg 3000
```

`npm run dev` starts the integrated PC backend and web app on `127.0.0.1:3000`. `tailscale serve --bg 3000` publishes the same local server through Tailscale HTTPS, so Hello is available at `/hello`.

Android release install from `apps/android`:

```powershell
adb devices
.\gradlew.bat clean assembleRelease --no-daemon; if ($LASTEXITCODE -eq 0) { adb install -r app\build\outputs\apk\release\app-release.apk }
```

Release APK output:

```text
apps/android/app/build/outputs/apk/release/
```

Common commands:

```powershell
npm run dev          # Build Hello + Browser, then start integrated Electron
npm run build        # Build Hello frontend and GlassBox Electron/browser bundles
npm test             # Run the Browser TypeScript check
npm run browser:dev  # Start Browser Electron dev mode with Vite renderer
npm run hello:dev    # Start Hello standalone server from apps/hello
```

Useful URLs after `npm run dev`:

```text
Integrated local app: http://127.0.0.1:3000
Hello local app:      http://127.0.0.1:3000/hello
Hello API status:     http://127.0.0.1:3000/api/hello/status
Tailscale HTTPS:      https://desktop-8u23cj0.tail69a9e8.ts.net/hello
```

## Repository Map

```text
README.md                         Monorepo operations guide
package.json                      Root npm workspace scripts
package-lock.json                 Root dependency lock

apps/browser/                     GlassBox Electron browser app
apps/browser/src/App.tsx          Main React shell for Browser and Hello mode
apps/browser/src/main/main.ts     Electron main process and shell permissions
apps/browser/src/main/apiServer.ts Integrated Express server on 127.0.0.1:3000
apps/browser/src/preload/         Electron preload bridge
apps/browser/src/server/          BrowserView tabs, automation APIs, site packs
apps/browser/src/server/tabManager.ts BrowserView lifecycle and tab permissions
apps/browser/scripts/             Dev, Electron, CLI, and verification scripts
apps/browser/dist/                Built Browser renderer output
apps/browser/dist-electron/       Built Electron main/preload output

apps/hello/                       Hello chat app
apps/hello/server.ts              Mountable Hello Express + Socket.IO backend
apps/hello/src/App.tsx            Hello app frame, navigation, active panes
apps/hello/src/api.ts             Hello frontend API client, base path `/hello/api`
apps/hello/src/SocketContext.tsx  Socket.IO client, path `/hello/socket.io`
apps/hello/src/mediaPermissions.ts Camera/mic preflight and error diagnostics
apps/hello/src/components/        Hello UI components
apps/hello/src/components/CallOverlay.tsx WebRTC call UI and signaling logic
apps/hello/src/components/ChatWindow.tsx Chat body, message send, voice note capture
apps/hello/src/components/FamilyDrivePane.tsx Web Family Drive media library
apps/hello/src/components/Sidebar.tsx Chats, profile, settings, calls, contacts
apps/hello/dist/                  Built Hello frontend output

data/browser/                     GlassBox runtime data
data/hello/                       Hello runtime data
data/hello/hello.db               Hello SQLite database used by integrated app
data/hello/uploads/               Hello uploaded files and avatars
data/hello/family-drive/          Family Drive photos/videos by YYYY/MM
data/hello/cache/                 Hello cache directory
data/hello/logs/                  Hello logs directory
```

Do not treat `dist/`, `dist-electron/`, `data/*.db`, `data/**/uploads`, logs, or cache files as source changes unless a release process explicitly asks for them.

## Runtime Architecture

The integrated app starts from the Browser package:

```text
root npm run dev
  -> npm run build
  -> npm --workspace apps/browser run electron:integrated
  -> Electron loads apps/browser/dist-electron/main/main.cjs
  -> apps/browser/src/main/apiServer.ts starts Express on 127.0.0.1:3000
  -> apps/hello/server.ts is mounted into that Express server at /hello



.\gradlew.bat clean assembleRelease --no-daemon; if ($LASTEXITCODE -eq 0) { adb install -r app\build\outputs\apk\release\app-release.apk }

```

The app has two main surfaces:

```text
GlassBox Browser mode:
  React shell in apps/browser/src/App.tsx
  Native Electron BrowserView tabs managed by apps/browser/src/server/tabManager.ts

Hello mode:
  React iframe in apps/browser/src/App.tsx
  iframe src="/hello"
  Hello frontend served by apps/hello/dist or Vite middleware
```

Important integrated routes:

```text
/                         GlassBox browser shell
/api/...                  GlassBox Browser APIs
/api/hello/status         Integrated Hello mount status
/api/hello/open           Browser API response for Hello mode
/api/hello/mode/browser   Browser mode switch endpoint
/api/hello/mode/hello     Hello mode switch endpoint

/hello                    Hello UI
/hello/api/...            Hello REST API
/hello/api/files/:fileId  Hello file/avatar download route
/hello/api/drive/items    Family Drive paginated media listing
/hello/api/drive/upload   Family Drive photo/video upload route
/hello/socket.io          Hello Socket.IO endpoint
/hello/uploads/...        Hello static upload directory
```

Compatibility routes for older stored Hello data:

```text
/api/files/:fileId        Serves old avatar/message URLs saved before /hello mounting
/uploads/...              Serves old upload URLs saved before /hello mounting
```

Keep these compatibility routes unless all existing rows in `data/hello/hello.db` have been migrated to `/hello/...` paths.

## Browser And Hello Integration Points

When changing Hello mounting, check these files together:

```text
apps/browser/src/main/apiServer.ts
apps/browser/src/App.tsx
apps/browser/src/main/main.ts
apps/browser/src/server/tabManager.ts
apps/hello/server.ts
apps/hello/src/api.ts
apps/hello/src/SocketContext.tsx
apps/hello/src/components/CallOverlay.tsx
```

Current frontend path rules:

```text
Default local API:      API_BASE = "/hello/api"
Chat API client:        CHAT_API_BASE = VITE_CHAT_API_BASE || API_BASE
Call API client:        CALL_API_BASE = VITE_CALL_API_BASE || CHAT_API_BASE
Drive API client:       DRIVE_API_BASE = VITE_DRIVE_API_BASE || API_BASE
Chat Socket.IO client:  VITE_CHAT_SOCKET_ORIGIN || window.location.origin
Chat Socket.IO path:    VITE_CHAT_SOCKET_PATH || "/hello/socket.io"
Hello file uploads:     Chat/status attachments go through CHAT_API_BASE
Hello Drive paths:      Drive photos/videos go through DRIVE_API_BASE only
```

Avoid hardcoding:

```text
http://localhost:3000
http://127.0.0.1:3000
localhost
127.0.0.1
3420
```

Use relative paths for app calls so the same build works on:

```text
Electron integrated app
PC browser at http://127.0.0.1:3000/hello
Phone browser through Tailscale HTTPS at /hello
```

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

Allowed origins/routes:

```text
http://127.0.0.1:3000/hello
http://localhost:3000/hello
https://*.ts.net/hello
https://*.tailnet.ts.net/hello
```

Do not allow camera/mic for arbitrary BrowserView tabs. The tab permission handler must continue to deny permissions unless the requesting URL is trusted Hello.

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

This helper provides:

```text
requestUserMediaWithDiagnostics()
testCameraMicrophoneAccess()
describeMediaAccessError()
getMediaCaptureReadinessError()
```

The visible `Test Camera/Mic` control belongs in Hello Settings:

```text
apps/hello/src/components/Sidebar.tsx
Settings -> Camera / Microphone
```

Keep it out of the chat call button row unless the product design changes.

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
   Open https://desktop-8u23cj0.tail69a9e8.ts.net/hello
   Settings -> Camera / Microphone -> Test Camera/Mic

4. Direct call:
   Login as two users from two devices
   Start an audio call
   Confirm prompt appears, local stream starts, and the call moves past permission setup
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

## Images, Avatars, And Uploads

Hello stores image/file metadata in SQLite and files on disk.

Important backend paths:

```text
apps/hello/server.ts
data/hello/hello.db
data/hello/uploads/
```

Important frontend image use:

```text
apps/hello/src/components/Sidebar.tsx
apps/hello/src/components/ChatWindow.tsx
apps/hello/src/components/ContactInfoPanel.tsx
apps/hello/src/components/StatusPane.tsx
apps/hello/src/components/CallOverlay.tsx
```

Known compatibility requirement:

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

Because existing users and chats can still reference the old paths, `apps/hello/server.ts` serves both old and new routes. If images disappear after a merge, check:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/api/files/file_wwlkz64rk
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello/api/files/file_wwlkz64rk
```

Both should return `200` with an image content type when the file exists in `data/hello/uploads`.

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

Uploaded Drive media is physically stored on the PC under `data/hello/family-drive/YYYY/MM/`. The SQLite metadata row lives in `data/hello/hello.db` table `drive_items`. Deleting an item from Drive removes the database entry from the visible library and deletes the stored file from disk when it still exists.

The upload endpoint accepts `multipart/form-data` field `files` with images/videos and `uploaderId`. The server stores file metadata in SQLite table `drive_items`, returns month metadata, and uses cursor pagination via the `before` query parameter.

Frontend surfaces:

```text
apps/hello/src/components/FamilyDrivePane.tsx
apps/hello/src/api.ts
apps/android/app/src/main/java/com/glassbox/hello/familydrive/
```

The web rail shows Drive as the visible second tab. The Android bottom navigation replaces the visible Calls tab with Drive. Existing call code remains in the codebase for active call overlays and call-related flows.

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
  Pending items stay visible in the grid using their local URI.
  The top-right sync badge retries pending uploads.
  WorkManager retries pending uploads when network connectivity is available.
  A Family Drive notification is shown after pending uploads complete.
```

## Split Runtime Targets

The app is prepared for separate Drive, Chat, and Calling backends while preserving the current local/Tailscale server as the default:

```text
Drive:
  Target domain: home.bookhelloctg.com
  Web env:       VITE_DRIVE_API_BASE
  Android:       AppConfig.DRIVE_API_BASE
  Storage:       PC backend only, data/hello/family-drive/YYYY/MM/

Chat:
  Target domain: chat.bookhelloctg.com
  Web env:       VITE_CHAT_API_BASE, VITE_CHAT_SOCKET_ORIGIN, VITE_CHAT_SOCKET_PATH
  Android:       AppConfig.CHAT_API_BASE, AppConfig.CHAT_SOCKET_ORIGIN, AppConfig.CHAT_SOCKET_PATH
  Storage goal:  Cloudflare Worker + D1 metadata + Durable Objects/WebSockets + temporary R2 attachments

Calling:
  Target domain: call.bookhelloctg.com
  Web env:       VITE_CALL_API_BASE
  Android:       AppConfig.CALL_API_BASE
  Routing goal:  Cloudflare Worker + Durable Object signaling, direct WebRTC first, TURN/SFU fallback later
```

Until those Cloudflare services are deployed, these targets intentionally default to the existing `/hello/api` and `/hello/socket.io` backend so web and native builds remain runnable.

## Data And Persistence

Runtime data is local and should be handled carefully during merges:

```text
data/browser/              GlassBox browser data
data/hello/hello.db        Hello SQLite database
data/hello/uploads/        Uploaded files and avatars
data/hello/family-drive/   Family Drive media files
data/hello/cache/          Cache
data/hello/logs/           Logs
```

Source files should not depend on machine-specific absolute paths. Runtime location constants are in:

```text
apps/browser/src/server/paths.ts
apps/browser/src/main/memoryDb.ts
apps/hello/server.ts
```

Do not commit user runtime database changes, uploaded media, backups, logs, or cache files during normal code patches.

## Merge And Patch Checklist

Before merging future updates:

```text
1. Check git state:
   git status -sb

2. Inspect source diff:
   git diff -- apps/browser apps/hello README.md package.json package-lock.json

3. Avoid broad staging if runtime files changed:
   Do not use git add -A when data/, uploads/, logs/, cache/, dist/, or backups appear.

4. Keep Hello mounted at /hello unless intentionally changing the product contract.

5. Keep relative Hello frontend paths:
   /hello/api
   /hello/socket.io
   /hello/api/files/:fileId

6. Keep old media compatibility routes:
   /api/files/:fileId
   /uploads/...

7. Keep Electron camera/mic permission allowlist limited to trusted Hello origins.

8. Re-run checks:
   npm test
   npm run build

9. Re-test key routes:
   http://127.0.0.1:3000/api/hello/status
   http://127.0.0.1:3000/hello
   http://127.0.0.1:3000/hello/api/users

10. For Tailscale/mobile changes, verify:
    https://desktop-8u23cj0.tail69a9e8.ts.net/hello
```

When resolving merge conflicts, protect these contracts first:

```text
Single integrated server: 127.0.0.1:3000
Hello mount path:        /hello
Hello API path:          /hello/api
Hello socket path:       /hello/socket.io
Legacy file route:       /api/files/:fileId
Browser mode iframe:     src="/hello"
```

## Mode And State Preservation

The integrated Browser shell must preserve state when switching between GlassBox Browser and Hello.

Important Browser shell state files:

```text
apps/browser/src/App.tsx
```

Current contracts:

```text
Browser/Hello mode is stored in localStorage key: gb-app-mode
Hello iframe remains mounted even when hidden
BrowserView remains alive and is resized to 0x0 when Hello is visible
BrowserView is restored to its previous visible bounds when Browser mode returns
Refreshing while in Hello mode must reopen the shell in Hello mode
```

Do not reintroduce conditional mounting like this:

```tsx
{appMode === 'hello' && <iframe src="/hello" />}
```

That destroys Hello React state, active chats, sockets, pending text, and call UI when switching back to Browser mode. The iframe should stay mounted and only change visibility/layout classes.

Hello app state persistence lives in:

```text
apps/hello/src/App.tsx
apps/hello/src/components/Sidebar.tsx
```

Current Hello localStorage keys:

```text
whatsclone_user_real
whatsclone_permissions
whatsclone_active_rail_tab
whatsclone_active_chat_<userId>
```

After a full refresh inside Hello, the app should restore the current user, active rail tab, and selected chat when that chat still exists for the user.

## Verification Commands

Run from the repository root:

```powershell
npm test
npm run build
```

Route checks:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/api/hello/status
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello/api/users
```

Permissions-Policy check:

```powershell
(Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello).Headers
```

Old/new image route check:

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/api/files/file_wwlkz64rk
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000/hello/api/files/file_wwlkz64rk
```

Port/process check:

```powershell
Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
Get-Process node,electron -ErrorAction SilentlyContinue
```

## Publishing To GitHub

Current target remote:

```text
https://github.com/gilbertjackob-tech/Hello
```

Check remotes:

```powershell
git remote -v
git branch --show-current
```

Recommended source-only staging pattern:

```powershell
git status -sb
git add -- README.md apps/browser/src/App.tsx apps/browser/src/main/main.ts apps/browser/src/server/tabManager.ts apps/hello/server.ts apps/hello/src/App.tsx apps/hello/src/api.ts apps/hello/src/types.ts apps/hello/src/CallContext.tsx apps/hello/src/SocketContext.tsx apps/hello/src/components/CallOverlay.tsx apps/hello/src/components/ChatWindow.tsx apps/hello/src/components/FamilyDrivePane.tsx apps/hello/src/components/PermissionsModal.tsx apps/hello/src/components/Sidebar.tsx apps/hello/src/mediaPermissions.ts apps/android/app/src/main/java/com/glassbox/hello/familydrive
git commit -m "fix hello media permissions and document integration"
git push origin main
```

If a normal push reports up-to-date but remote `main` does not advance:

```powershell
git rev-parse HEAD
git ls-remote origin refs/heads/main
git push origin HEAD:main
```

## Troubleshooting

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
Check whether the stored URL starts with /api/files or /hello/api/files.
Check both compatibility routes return 200.
Check data/hello/uploads contains the actual file.
Check apps/hello/server.ts still registers /api/files/:fileId when basePath is /hello.
```

Phone cannot connect:

```text
Use the Tailscale HTTPS URL, not localhost.
Phone localhost means the phone itself, not the PC.
Verify Tailscale Serve is forwarding to local 127.0.0.1:3000.
```

Socket.IO fails:

```text
Client path must be /hello/socket.io.
Server socketPath must be /hello/socket.io.
Do not use a standalone localhost socket URL from phone/mobile.
```
