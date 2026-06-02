import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { io, Socket } from 'socket.io-client';
import { User } from './types';
import { CHAT_CLOUD_BASE_URL, CLOUD_SESSION_TOKEN_KEY } from './api';

const env = (import.meta as any).env || {};
const CHAT_SOCKET_ORIGIN = env.VITE_CHAT_SOCKET_ORIGIN || window.location.origin;
const CHAT_SOCKET_PATH = env.VITE_CHAT_SOCKET_PATH || "/hello/socket.io";
const ENABLE_PC_SOCKET = env.VITE_ENABLE_PC_SOCKET === "true";

interface SocketContextType {
  socket: RealtimeSocket | null;
  isConnected: boolean;
}

const SocketContext = createContext<SocketContextType>({ socket: null, isConnected: false });

export const useSocket = () => useContext(SocketContext);

type RealtimeHandler = (...args: any[]) => void;

type RealtimeSocket = {
  on: (event: string, handler: RealtimeHandler) => void;
  off: (event: string, handler?: RealtimeHandler) => void;
  emit: (event: string, payload?: any) => void;
  disconnect: () => void;
};

function createCloudRealtimeSocket(currentUser: User): RealtimeSocket {
  const listeners = new Map<string, Set<RealtimeHandler>>();
  let ws: WebSocket | null = null;
  let closed = false;
  const pendingSends: string[] = [];

  const dispatch = (event: string, payload?: any) => {
    listeners.get(event)?.forEach((handler) => handler(payload));
  };

  const connect = () => {
    const token = localStorage.getItem(CLOUD_SESSION_TOKEN_KEY);
    if (!token) {
      window.setTimeout(() => dispatch("disconnect"), 0);
      return;
    }
    const url = `${CHAT_CLOUD_BASE_URL}/api/calls/ws?token=${encodeURIComponent(token)}`
      .replace(/^https:/, "wss:")
      .replace(/^http:/, "ws:");
    ws = new WebSocket(url);
    ws.addEventListener("open", () => {
      dispatch("connect");
      const payload = { userId: currentUser.id, name: currentUser.name, online: true, platform: "web" };
      ws?.send(JSON.stringify({ event: "identify", payload }));
      ws?.send(JSON.stringify({ event: "online", payload }));
      while (pendingSends.length > 0 && ws?.readyState === WebSocket.OPEN) {
        ws.send(pendingSends.shift()!);
      }
    });
    ws.addEventListener("close", () => {
      dispatch("disconnect");
      if (!closed) window.setTimeout(connect, 1500);
    });
    ws.addEventListener("error", () => dispatch("disconnect"));
    ws.addEventListener("message", (message) => {
      try {
        const envelope = JSON.parse(String(message.data));
        if (envelope?.event) dispatch(envelope.event, envelope.payload || envelope);
      } catch (error) {
        console.warn("Failed to parse cloud realtime event", error);
      }
    });
  };

  connect();

  return {
    on(event, handler) {
      const handlers = listeners.get(event) || new Set<RealtimeHandler>();
      handlers.add(handler);
      listeners.set(event, handlers);
    },
    off(event, handler) {
      if (!handler) {
        listeners.delete(event);
        return;
      }
      const handlers = listeners.get(event);
      handlers?.delete(handler);
      if (handlers?.size === 0) listeners.delete(event);
    },
    emit(event, payload = {}) {
      const body = JSON.stringify({ event, payload });
      if (ws?.readyState === WebSocket.OPEN) {
        ws.send(body);
      } else {
        pendingSends.push(body);
      }
    },
    disconnect() {
      closed = true;
      ws?.close(1000, "client_disconnect");
      ws = null;
      listeners.clear();
    },
  };
}

export const SocketProvider = ({ children, currentUser }: { children: ReactNode; currentUser: User | null }) => {
  const [socket, setSocket] = useState<RealtimeSocket | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    if (!currentUser?.id) {
      setSocket(null);
      setIsConnected(false);
      return;
    }

    if (!ENABLE_PC_SOCKET) {
      const cloudSocket = createCloudRealtimeSocket(currentUser);
      cloudSocket.on("connect", () => setIsConnected(true));
      cloudSocket.on("disconnect", () => setIsConnected(false));
      setSocket(cloudSocket);
      return () => {
        cloudSocket.disconnect();
        setSocket(null);
        setIsConnected(false);
      };
    }

    if (!currentUser?.id) {
      setSocket(null);
      setIsConnected(false);
      return;
    }

    const socketInstance = io(CHAT_SOCKET_ORIGIN, {
        path: CHAT_SOCKET_PATH,
        transports: ["websocket", "polling"],
        reconnectionAttempts: 5,
        reconnectionDelay: 1000,
    });

    const identify = () => {
      if (currentUser?.id) {
        socketInstance.emit("identify", currentUser.id);
      }
    };

    socketInstance.on('connect', () => {
      setIsConnected(true);
      identify();
    });

    socketInstance.io.on("reconnect", identify);

    socketInstance.on('disconnect', () => {
      setIsConnected(false);
    });

    setSocket(socketInstance as unknown as RealtimeSocket);

    return () => {
      socketInstance.io.off("reconnect", identify);
      socketInstance.disconnect();
    };
  }, [currentUser?.id]);

  return (
    <SocketContext.Provider value={{ socket, isConnected }}>
      {children}
    </SocketContext.Provider>
  );
};
