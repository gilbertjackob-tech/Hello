# PC Drive Cloudflare Tunnel

## Purpose

Cloudflare Tunnel is the PC Drive connection for Hello. It replaces the old private-network requirement for Family Drive access.

Family Drive photos and videos still remain on the owner PC. Cloudflare Tunnel is only a secure forwarding bridge from the public hostname to the local PC backend. Family Drive original media is not stored in Cloudflare R2.

## Final Architecture

```text
Mobile/Web
-> https://home.bookhelloctg.com
-> Cloudflare Tunnel
-> owner PC localhost:3000
-> data/hello/family-drive
```

Cloudflare stores account, chat, contact, preference, and call data through the chat Worker and D1. Cloudflare R2 stores temporary chat attachments and profile avatars only.

## Setup

1. Install `cloudflared` on the owner PC.
2. Create a Cloudflare Tunnel in the Cloudflare dashboard or with `cloudflared`.
3. Add this public hostname:
   `home.bookhelloctg.com`
4. Point the tunnel service to:
   `http://localhost:3000`
5. Start the PC backend:
   `npm run dev`
6. Run the tunnel or install it as a service on the owner PC.

The app Drive API default is:

```text
https://home.bookhelloctg.com/hello/api
```

Local development may override Drive only:

```text
VITE_DRIVE_API_BASE=http://127.0.0.1:3000/hello/api
./gradlew.bat :app:assembleDebug -PdrivePcBaseUrl=http://127.0.0.1:3000/hello/api
```

## PC Off Behavior

When the owner PC or tunnel is offline, the Drive endpoint is unavailable. Chat, account, contacts, profile, messaging, and calls continue through Cloudflare.

Android keeps selected Drive photos and videos as local pending uploads. Users should not delete original phone media until upload completes. When the PC comes back online, pending uploads retry and sync to the PC.

Web Drive upload is unavailable while PC Drive is offline. Web Drive does not upload Family Drive media to Cloudflare R2.

## Health Check

Check PC Drive reachability:

```text
https://home.bookhelloctg.com/hello/api/drive/health
```

Expected response:

```json
{
  "ok": true,
  "service": "pc-drive",
  "storage": "local-pc",
  "driveRoot": "data/hello/family-drive"
}
```

If the health check fails:

- The owner PC may be off.
- The PC backend may not be running on `localhost:3000`.
- The `cloudflared` tunnel may be stopped.
- DNS or the tunnel route may be misconfigured.

## Tunnel Route

Public hostname:

```text
home.bookhelloctg.com
```

Service:

```text
http://localhost:3000
```
