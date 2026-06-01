export interface Env {
  DB: D1Database;
  TEMP_FILES: R2Bucket;
  REALTIME_ROOM: DurableObjectNamespace;
}

type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };

function json(body: JsonValue, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  headers.set("content-type", "application/json; charset=utf-8");
  headers.set("access-control-allow-origin", "*");
  headers.set("access-control-allow-methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
  headers.set("access-control-allow-headers", "content-type,authorization");
  return new Response(JSON.stringify(body), { ...init, headers });
}

function notFound(pathname: string): Response {
  return json({ ok: false, error: "not_found", path: pathname }, { status: 404 });
}

export class RealtimeRoom {
  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env,
  ) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return json({ ok: true });
    }

    if (url.pathname.endsWith("/health")) {
      return json({
        ok: true,
        service: "hello-realtime-room",
        storage: "durable-object-placeholder",
      });
    }

    return json(
      {
        ok: true,
        service: "hello-realtime-room",
        message: "Realtime chat/call Durable Object placeholder. WebSocket signaling will be added here later.",
      },
    );
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return json({ ok: true });
    }

    if (url.pathname === "/" || url.pathname === "/health") {
      return json({
        ok: true,
        service: "hello-chat-worker",
        bindings: {
          d1: "hello_chat_db",
          r2: "hello-chat-temp",
          durableObject: "REALTIME_ROOM",
        },
        note: "Drive media remains on the PC backend and is not stored in this Worker.",
      });
    }

    if (url.pathname.startsWith("/rooms/")) {
      const roomName = decodeURIComponent(url.pathname.split("/")[2] || "default");
      const roomId = env.REALTIME_ROOM.idFromName(roomName);
      return env.REALTIME_ROOM.get(roomId).fetch(request);
    }

    if (url.pathname === "/chat/bootstrap") {
      return json({
        ok: true,
        message: "Chat API placeholder. Add D1-backed users, conversations, messages, delivery, and read-state routes here.",
      });
    }

    if (url.pathname === "/call/bootstrap") {
      return json({
        ok: true,
        message: "Call signaling placeholder. Add Durable Object room signaling and TURN/SFU integration here.",
      });
    }

    if (url.pathname === "/files/bootstrap") {
      return json({
        ok: true,
        message: "Temporary chat attachment placeholder. Use TEMP_FILES R2 only for expiring chat files.",
      });
    }

    return notFound(url.pathname);
  },
};
