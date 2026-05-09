import { createContext, useContext, type ReactNode } from "react";
import type { CallStatus } from "./types";

interface LegacyCallData {
  callId: string;
  chatId: string;
  callerId: string;
  calleeId: string;
  callerName: string;
  callerAvatar?: string;
  isVideo: boolean;
  toUserId?: string;
  status?: CallStatus;
}

interface CallContextType {
  activeCall: LegacyCallData | null;
  incomingCall: LegacyCallData | null;
  callStatus: CallStatus;
  callDuration: number;
  isMuted: boolean;
  isVideoOff: boolean;
  isMinimized: boolean;
  isScreenSharing: boolean;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  startCall: (chatId: string, calleeId: string, isVideo: boolean) => void;
  acceptCall: () => void;
  declineCall: () => void;
  endCall: () => void;
  toggleMute: () => void;
  toggleVideo: () => void;
  toggleScreenShare: () => void;
  setMinimized: (v: boolean) => void;
  hasError: string;
  clearError: () => void;
}

const inactive = () => {
  console.warn("Legacy CallContext is inactive. Use components/CallOverlay.tsx for calls.");
};

const inactiveCallContext: CallContextType = {
  activeCall: null,
  incomingCall: null,
  callStatus: "idle",
  callDuration: 0,
  isMuted: false,
  isVideoOff: false,
  isMinimized: false,
  isScreenSharing: false,
  localStream: null,
  remoteStream: null,
  startCall: inactive,
  acceptCall: inactive,
  declineCall: inactive,
  endCall: inactive,
  toggleMute: inactive,
  toggleVideo: inactive,
  toggleScreenShare: inactive,
  setMinimized: inactive,
  hasError: "",
  clearError: inactive,
};

const CallContext = createContext<CallContextType>(inactiveCallContext);

export function CallProvider({ children }: { children: ReactNode; currentUser?: unknown }) {
  return <CallContext.Provider value={inactiveCallContext}>{children}</CallContext.Provider>;
}

export const useCall = () => useContext(CallContext);
