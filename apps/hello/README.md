# Hello PC Backend

This package hosts the local PC backend used by Hello Web and Family Drive.

Current architecture:

- Cloud account, contacts, chat, preferences, messages, and calls use the Cloudflare chat Worker.
- Cloudflare D1 stores account/chat/call data.
- Cloudflare R2 stores temporary chat attachments and profile avatars only.
- Family Drive photos and videos remain on the owner PC.
- Cloudflare Tunnel forwards PC Drive requests to this backend when the PC is online.

## PC Drive Endpoint

Production/default Drive endpoint:

```text
https://home.bookhelloctg.com/hello/api
```

Health check:

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

## Run Locally

```bash
npm install
npm run dev
```

Local URLs:

```text
http://localhost:3000
http://localhost:3000/hello
http://localhost:3000/hello/api/drive/health
```

## Cloudflare Tunnel

Configure the owner PC tunnel as:

```text
Public hostname: home.bookhelloctg.com
Service:         http://localhost:3000
```

Start the PC backend, then run or install the `cloudflared` tunnel service.

## Local Development Overrides

Web:

```text
VITE_DRIVE_API_BASE=http://127.0.0.1:3000/hello/api
```

Android:

```powershell
.\gradlew.bat :app:assembleDebug -PdrivePcBaseUrl=http://127.0.0.1:3000/hello/api
```

Production defaults should remain `https://home.bookhelloctg.com/hello/api`.

## PC Off Behavior

When the owner PC or tunnel is offline:

- Cloud account/chat/calls continue through Cloudflare.
- Family Drive fetch/upload is offline.
- Android keeps selected Drive media as local pending uploads.
- Web Drive upload is unavailable while PC Drive is offline.
- Family Drive media is not uploaded to Cloudflare R2.
