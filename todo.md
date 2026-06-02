Objective: Completely remove Tailscale dependency and replace it with a Cloudflare Tunnel based PC Drive connection, while keeping the current cloud communication architecture intact.

Project architecture target:
1. Communication is fully cloud-backed:
   - Auth/session/profile/contacts/chat/preferences/calls use Cloudflare.
   - Cloudflare Worker + D1 stores account/chat/call data.
   - Cloudflare R2 stores only temporary chat attachments/profile avatars.
   - Cloudflare Durable Objects/WebSocket handles realtime chat/call signaling.

2. Family Drive remains PC-backed:
   - Original Drive photos/videos must remain stored on the owner’s PC.
   - Drive media must NOT be uploaded to Cloudflare R2.
   - Drive media must NOT be routed through chat.bookhelloctg.com.
   - Cloudflare Tunnel is only a secure forwarding bridge to the PC backend when the PC is online.

3. Tailscale must be removed as a requirement:
   - No user should need Tailscale installed.
   - No app feature should require a Tailscale URL.
   - No documentation or UI should tell users to run `tailscale serve`.
   - PC Drive access should use `home.bookhelloctg.com` through Cloudflare Tunnel.

New Drive connection model:
- PC backend runs locally on the owner PC.
- PC backend is exposed through Cloudflare Tunnel:
  https://home.bookhelloctg.com
- Android/Web Drive API base should default to:
  https://home.bookhelloctg.com/hello/api
- If the PC backend or tunnel is offline, Drive must enter offline/pending mode.
- Pending Drive uploads should stay locally on the mobile device until the PC backend becomes reachable.
- When the PC backend becomes reachable, pending uploads should automatically sync to the PC.
- Chat/auth/profile/contacts/messages/calls must continue working even when the PC is off.

Critical rules:
1. Do NOT move Family Drive photos/videos to Cloudflare R2.
2. Do NOT upload Drive media to `chat.bookhelloctg.com`.
3. Do NOT change the existing Cloudflare chat/auth/call routing.
4. Cloudflare Tunnel must only forward requests to the PC backend.
5. PC off must never create a global app error.
6. PC off should only affect Drive sync/fetch.
7. Chat, account, contacts, profile, messaging, and calling must continue through Cloudflare.
8. Tailscale must not remain the default anywhere.

Tasks:

A. Audit and remove Tailscale references

Search the entire repo for:
- tailscale
- Tailscale
- tailnet
- ts.net
- serve --bg
- `tailscale serve`
- VITE_TAILSCALE
- AppConfig Tailscale references
- hardcoded Tailscale URLs
- PC backend assumptions tied to Tailscale
- DRIVE_API_BASE defaults
- documentation mentioning Tailscale as required

For each match:
- Remove it if obsolete.
- Replace it with Cloudflare Tunnel wording/config if still relevant.
- Keep Tailscale only as an explicitly documented legacy/manual fallback if absolutely necessary, never as default.

B. Replace Drive PC endpoint defaults

Production/default Drive endpoint must become:

https://home.bookhelloctg.com/hello/api

Android:
- Add or update:
  AppConfig.DRIVE_PC_BASE_URL = "https://home.bookhelloctg.com/hello/api"
- Make sure DrivePcApi / DrivePcApiClient use this value by default.
- Do not use Tailscale URLs as default.

Web:
- Make `VITE_DRIVE_API_BASE` default to:
  https://home.bookhelloctg.com/hello/api
- Web Drive helpers must use this endpoint for Drive only.
- Chat/auth/call helpers must continue using:
  https://chat.bookhelloctg.com

Local development override:
- Keep a clean dev override option.
- Android debug/dev builds may override Drive endpoint to:
  http://127.0.0.1:3000/hello/api
  or LAN IP if needed.
- Web `.env.local` may override:
  VITE_DRIVE_API_BASE=http://127.0.0.1:3000/hello/api
- Production/default must remain:
  https://home.bookhelloctg.com/hello/api

C. Add or standardize PC Drive health endpoint

Drive health should check:

GET https://home.bookhelloctg.com/hello/api/drive/health

If missing, add this route to the PC backend:

GET /hello/api/drive/health

Expected response:

{
  "ok": true,
  "service": "pc-drive",
  "storage": "local-pc",
  "driveRoot": "data/hello/family-drive"
}

This endpoint should:
- Not require Tailscale.
- Not touch Cloudflare R2.
- Confirm only the PC Drive backend/storage is reachable.

D. Android Drive offline/pending behavior

When the Drive PC endpoint is unreachable:
- Do not crash.
- Do not show a global app connection error.
- Save selected photos/videos as local pending upload records.
- Show pending media inside the Drive UI.
- Add sync/refresh badge on pending thumbnails.
- Show this warning exactly:

“Please don’t delete the original photos/videos until upload is complete.”

Retry behavior:
- Use WorkManager.
- Retry when network becomes available.
- Retry when app opens.
- Retry when PC Drive health becomes reachable.
- Retry periodically with reasonable backoff.

After successful upload:
- Upload file to PC backend through:
  https://home.bookhelloctg.com/hello/api
- Mark pending item as synced.
- Replace pending badge with green tick.
- Send local notification:

“Pending uploads completed. Your photos/videos are saved to PC.”

E. Web Drive offline behavior

If the PC Drive endpoint is offline:
- Show PC Drive as offline.
- Do not show global app connection error.
- Chat/account/calls must keep working.
- Do not route Drive upload to Cloudflare R2.
- If web pending upload is not implemented, clearly show Drive upload unavailable while PC is offline.
- If web pending is implemented, keep it separate from Cloudflare chat storage.

F. UI wording cleanup

Remove user-facing Tailscale wording.

Replace:
- “Tailscale”
- “tailnet”
- “tailscale serve”
- “Tailscale URL”

With:
- “PC Drive connection”
- “PC Drive is offline”
- “PC Drive is online”
- “Cloudflare Tunnel”
- “home.bookhelloctg.com”
- “PC online/offline”

Settings / Network screen should show separate statuses:
- Cloud Account
- Cloud Chat
- Cloud Calls
- PC Drive

PC Drive should mention:
- Online through Cloudflare Tunnel
- Offline / pending sync
- Never say Tailscale required

G. Documentation update

Add:

docs/pc-drive-cloudflare-tunnel.md

This document must explain:

1. Purpose:
- Cloudflare Tunnel replaces Tailscale for PC Drive access.
- Drive files still remain on the owner’s PC.
- Cloudflare Tunnel is only a secure forwarding bridge.
- Drive media is not stored in Cloudflare R2.

2. Final architecture:

Mobile/Web
→ https://home.bookhelloctg.com
→ Cloudflare Tunnel
→ owner PC localhost:3000
→ data/hello/family-drive

3. Setup steps:
- Install `cloudflared` on the owner PC.
- Create a Cloudflare Tunnel.
- Add public hostname:
  home.bookhelloctg.com
- Point service to:
  http://localhost:3000
- Start PC backend:
  npm run dev
- Run/install cloudflared tunnel service.

4. PC off behavior:
- Drive endpoint becomes unavailable.
- Android Drive uploads stay pending locally.
- User should not delete original phone media until upload completes.
- When PC is back online, pending uploads sync automatically.

5. What Cloudflare stores:
- Cloudflare stores account/chat/call data.
- Cloudflare R2 stores temporary chat attachments/profile avatars only.
- Cloudflare does NOT store Family Drive original photos/videos.

6. Troubleshooting:
- Check:
  https://home.bookhelloctg.com/hello/api/drive/health
- If it fails:
  - PC backend may be off.
  - cloudflared tunnel may be stopped.
  - localhost:3000 may not be running.
  - DNS/tunnel route may be misconfigured.

H. Cloudflare Tunnel setup notes

Do not implement tunnel creation in code.
Only document and configure the app to use the tunnel domain.

Target tunnel route:

Public hostname:
home.bookhelloctg.com

Service:
http://localhost:3000

I. Validation commands

Run and report results:

npm run build
npm --workspace apps/hello run build
npm --workspace apps/browser run build
npm --prefix apps/cloudflare/chat-worker run types
cd apps/android
.\gradlew.bat :app:assembleDebug --console=plain

If Worker code is not changed, no Worker deploy is required.
If Worker code is changed, also run:

npm --prefix apps/cloudflare/chat-worker run deploy

J. Live validation

Test A: PC backend OFF

Conditions:
- Do not run `npm run dev`.
- Do not use Tailscale.
- Cloudflare chat Worker remains live.

Expected:
- Android app opens.
- Web app opens if hosted/served.
- Cloud Account is online.
- Cloud Chat is online.
- Cloud Calls are available.
- PC Drive status is offline.
- Drive upload becomes pending.
- No global connection error appears.
- No Tailscale is required.

Test B: PC backend ON + Cloudflare Tunnel ON

Conditions:
- Start PC backend:
  npm run dev
- Cloudflare Tunnel is running.
- Public health check works:
  https://home.bookhelloctg.com/hello/api/drive/health

Expected:
- Android PC Drive status becomes online.
- Pending uploads sync to PC.
- Pending badge changes to green tick.
- Completion notification appears:
  “Pending uploads completed. Your photos/videos are saved to PC.”
- Files are stored under the PC Drive local storage path.

Test C: Web

Expected:
- Web chat works without Tailscale.
- Web calls/signaling work without Tailscale.
- Web Drive uses:
  https://home.bookhelloctg.com/hello/api
- No `ts.net`, `tailnet`, or Tailscale URL is used as a default.
- Drive remains PC-only.

K. Final report required

After implementation, report:

1. All Tailscale references found.
2. Which references were removed.
3. Which references were replaced with Cloudflare Tunnel wording.
4. Final Android Drive base URL.
5. Final Web Drive base URL.
6. Whether PC Drive health endpoint exists and works.
7. Whether Android pending Drive upload still works.
8. Whether chat/auth/calls remained Cloudflare-backed.
9. Build results.
10. Any remaining caveats.

Do not:
- Move Drive media to Cloudflare storage.
- Modify Cloudflare chat/auth/call routing unless necessary.
- Reintroduce PC/Tailscale as a communication dependency.
- Use `/hello/api` for cloud chat/account/users/messages.
- Break local dev overrides.