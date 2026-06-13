import { useEffect, useMemo, useRef, useState, useCallback } from "react";
import type { MutableRefObject } from "react";
import { motion, AnimatePresence } from "motion/react";
import {
  Camera,
  Captions,
  Download,
  Gauge,
  Image as ImageIcon,
  Maximize2,
  Mic,
  MicOff,
  Minimize2,
  MonitorOff,
  MonitorUp,
  MoreVertical,
  Phone,
  PhoneOff,
  PictureInPicture,
  Radio,
  RotateCcw,
  Settings,
  Sparkles,
  Users,
  Video as VideoIcon,
  VideoOff,
} from "lucide-react";
import { useSocket } from "../SocketContext";
import {
  CallData,
  CallFeatureSupport,
  CallMediaState,
  CallQualityStats,
  CallRoom,
  CallStatus,
  User,
} from "../types";
import { cn } from "../lib/utils";
import {
  describeMediaAccessError,
  getMediaCaptureReadinessError,
  requestUserMediaWithDiagnostics,
} from "../mediaPermissions";
import { CALL_API_BASE, cloudAuthHeaders } from "../api";

const DEFAULT_ICE_SERVERS: RTCIceServer[] = [
  {
    urls: [
      "stun:stun.l.google.com:19302",
      "stun:stun1.l.google.com:19302",
      "stun:stun2.l.google.com:19302",
    ],
  },
];
const FORCE_RELAY = (import.meta as any).env?.VITE_WEBRTC_FORCE_RELAY === "true";
const CALL_DEBUG_UI = false;
const CALL_SIGNAL_REST_FALLBACK_EVENTS = new Set([
  "call:accepted",
  "call:answer",
  "call:ice-candidate",
  "call:connected",
  "call:failed",
  "call:ended",
]);

const applyRelayPolicy = (config: RTCConfiguration): RTCConfiguration =>
  FORCE_RELAY ? { ...config, iceTransportPolicy: "relay" } : config;

const getIceConfiguration = (): RTCConfiguration => {
  const raw = (import.meta as any).env?.VITE_ICE_SERVERS_JSON;
  if (!raw) return applyRelayPolicy({ iceServers: DEFAULT_ICE_SERVERS });
  try {
    const parsed = JSON.parse(raw);
    const iceServers = Array.isArray(parsed) ? parsed : parsed?.iceServers;
    if (Array.isArray(iceServers) && iceServers.length > 0) {
      return applyRelayPolicy({ iceServers });
    }
  } catch (err) {
    console.warn("Invalid VITE_ICE_SERVERS_JSON; falling back to default STUN", err);
  }
  return applyRelayPolicy({ iceServers: DEFAULT_ICE_SERVERS });
};

const getCallFeatureSupport = (): CallFeatureSupport => {
  const speechWindow = window as typeof window & {
    SpeechRecognition?: unknown;
    webkitSpeechRecognition?: unknown;
  };

  return {
    screenShare: !!navigator.mediaDevices?.getDisplayMedia,
    recording: typeof MediaRecorder !== "undefined",
    captions: !!(
      speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition
    ),
    pictureInPicture: !!document.pictureInPictureEnabled,
    outputDeviceSelect:
      typeof HTMLMediaElement !== "undefined" &&
      "setSinkId" in HTMLMediaElement.prototype,
  };
};

type BeautyMode =
  | "off"
  | "bw"
  | "vivid"
  | "dreamy"
  | "beauty"
  | "warm"
  | "cool"
  | "gold"
  | "comic";

type CameraFacingMode = "user" | "environment";

type StartCallDetail = {
  chatId: string;
  calleeId: string;
  calleeName: string;
  calleeAvatar?: string;
  isVideo: boolean;
};

type StartGroupCallDetail = {
  chatId: string;
  chatName: string;
  participantIds: string[];
  isVideo: boolean;
};

type SignalPayload = {
  eventId?: string;
  callId: string;
  chatId: string;
  fromUserId: string;
  toUserId: string;
  callerId?: string;
  calleeId?: string;
  type?: "audio" | "video";
  timestamp?: number;
  attempt?: number;
  event?: string;
  offer?: RTCSessionDescriptionInit;
  answer?: RTCSessionDescriptionInit;
  candidate?: RTCIceCandidateInit;
  reason?: string;
  audioMuted?: boolean;
  videoOff?: boolean;
  screenSharing?: boolean;
  quality?: "auto" | "720p" | "1080p" | "2k";
  beautyMode?: BeautyMode;
  roomId?: string;
  mediaState?: CallMediaState;
  stats?: CallQualityStats;
};

type CallDebugState = {
  callId?: string;
  direction?: "incoming" | "outgoing";
  localUserId?: string;
  remoteUserId?: string;
  type?: "audio" | "video";
  events: string[];
  startSent?: boolean;
  startReceived?: boolean;
  offerSent?: boolean;
  offerReceived?: boolean;
  answerSent?: boolean;
  answerReceived?: boolean;
  acceptedSent?: boolean;
  acceptedReceived?: boolean;
  ackReceivedEvents?: string[];
  localDescriptionSet?: boolean;
  remoteDescriptionSet?: boolean;
  iceSentCount: number;
  iceReceivedCount: number;
  signalingState?: string;
  iceGatheringState?: string;
  iceConnectionState?: string;
  connectionState?: string;
  lastError?: string;
  iceServers?: RTCIceServer[];
  turnConfigured?: boolean;
  localCandidateTypes?: string[];
  remoteCandidateTypes?: string[];
  relayCandidateGenerated?: boolean;
  relayCandidateReceived?: boolean;
  selectedCandidatePair?: string;
  selectedCandidatePairExists?: boolean;
  currentRoundTripTime?: number;
  availableOutgoingBitrate?: number;
  packetsSent?: number;
  packetsReceived?: number;
};

const createInitialCallDebug = (): CallDebugState => ({
  events: [],
  iceSentCount: 0,
  iceReceivedCount: 0,
});

const hasTurnServer = (iceServers?: RTCIceServer[]) =>
  (iceServers || []).some((server) => {
    const urls = Array.isArray(server.urls) ? server.urls : [server.urls];
    return urls.some((url) => String(url).toLowerCase().startsWith("turn:"));
  });

const isExpectedPublicStunTimeout = (event: RTCPeerConnectionIceErrorEvent) =>
  event.errorCode === 701 &&
  String(event.url || "").toLowerCase().startsWith("stun:");

const parseIceCandidate = (candidate?: string) => {
  const parts = String(candidate || "").trim().split(/\s+/);
  const protocol = (parts[2] || "").toLowerCase();
  const address = parts[4] || "";
  const port = parts[5] || "";
  const typIndex = parts.indexOf("typ");
  const type = typIndex >= 0 ? parts[typIndex + 1] || "unknown" : "unknown";
  const maskedAddress = address
    ? address.includes(":")
      ? `${address.split(":").slice(0, 2).join(":")}:...`
      : address.replace(/\d+$/, "x")
    : "unknown";
  return { type, protocol, address: maskedAddress, port };
};

const uniqueCandidateTypes = (current: string[] | undefined, next: string) =>
  Array.from(new Set([...(current || []), next].filter(Boolean)));

interface CallOverlayProps {
  currentUser: User;
}

export function CallOverlay({ currentUser }: CallOverlayProps) {
  const { socket } = useSocket();
  const [incomingCall, setIncomingCall] = useState<CallData | null>(null);
  const [activeCall, setActiveCall] = useState<CallData | null>(null);
  const [callStatus, setCallStatus] = useState<CallStatus>("idle");
  const [isMuted, setIsMuted] = useState(false);
  const [isVideoOff, setIsVideoOff] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const [isScreenSharing, setIsScreenSharing] = useState(false);
  const [remoteScreenSharing, setRemoteScreenSharing] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [callDuration, setCallDuration] = useState(0);
  const [hasError, setHasError] = useState("");
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [screenStream, setScreenStream] = useState<MediaStream | null>(null);
  const [videoInputs, setVideoInputs] = useState<MediaDeviceInfo[]>([]);
  const [audioInputs, setAudioInputs] = useState<MediaDeviceInfo[]>([]);
  const [selectedVideoDeviceId, setSelectedVideoDeviceId] = useState<string>("");
  const [selectedAudioDeviceId, setSelectedAudioDeviceId] = useState<string>("");
  const [videoQuality, setVideoQuality] = useState<"auto" | "720p" | "1080p" | "2k">("720p");
  const [beautyMode, setBeautyMode] = useState<BeautyMode>("off");
  const [remoteQuality, setRemoteQuality] = useState<"auto" | "720p" | "1080p" | "2k" | undefined>();
  const [remoteBeautyMode, setRemoteBeautyMode] = useState<BeautyMode | undefined>();
  const [localResolution, setLocalResolution] = useState("");
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [showMoreControls, setShowMoreControls] = useState(false);
  const [showStatsBadge, setShowStatsBadge] = useState(false);
  const [isSwapped, setIsSwapped] = useState(false);
  const [cameraFacingMode, setCameraFacingMode] =
    useState<CameraFacingMode>("user");
  const [featureSupport] = useState<CallFeatureSupport>(() => getCallFeatureSupport());
  const [callQuality, setCallQuality] = useState<CallQualityStats>({
    label: "unknown",
    updatedAt: Date.now(),
  });
  const [isCallRecording, setIsCallRecording] = useState(false);
  const [captionsEnabled, setCaptionsEnabled] = useState(false);
  const [captionText, setCaptionText] = useState("");
  const [activeGroupRoom, setActiveGroupRoom] = useState<CallRoom | null>(null);
  const [iceConfiguration, setIceConfiguration] = useState<RTCConfiguration>(() => getIceConfiguration());
  const [callDebug, setCallDebug] = useState<CallDebugState>(() => createInitialCallDebug());
  const [showCallDebug, setShowCallDebug] = useState(false);
  const iceConfigurationRef = useRef<RTCConfiguration>(iceConfiguration);
  const [groupParticipantStates, setGroupParticipantStates] = useState<
    Record<string, CallMediaState>
  >({});

  const toggleSwap = (e?: { stopPropagation: () => void }) => {
    e?.stopPropagation();
    setIsSwapped((prev) => !prev);
  };

  // Audio Context for ringtones
  const audioCtxRef = useRef<AudioContext | null>(null);
  const ringerIntervalRef = useRef<number | null>(null);

  const playRingtone = useCallback((type: "incoming" | "outgoing") => {
    try {
      if (!audioCtxRef.current) {
          audioCtxRef.current = new (window.AudioContext || (window as any).webkitAudioContext)();
      }
      const ctx = audioCtxRef.current;
      if (ctx.state === "suspended") ctx.resume();

      const playBeep = () => {
          if (ctx.state === "suspended") return;
          
          if (type === "outgoing") {
              const osc1 = ctx.createOscillator();
              const osc2 = ctx.createOscillator();
              const gain = ctx.createGain();
              osc1.connect(gain);
              osc2.connect(gain);
              gain.connect(ctx.destination);
              
              osc1.type = "sine";
              osc2.type = "sine";
              osc1.frequency.value = 440;
              osc2.frequency.value = 480;
              
              gain.gain.setValueAtTime(0, ctx.currentTime);
              gain.gain.linearRampToValueAtTime(0.03, ctx.currentTime + 0.1);
              gain.gain.setValueAtTime(0.03, ctx.currentTime + 2.0);
              gain.gain.linearRampToValueAtTime(0, ctx.currentTime + 2.1);
              
              osc1.start(ctx.currentTime);
              osc2.start(ctx.currentTime);
              osc1.stop(ctx.currentTime + 2.1);
              osc2.stop(ctx.currentTime + 2.1);
          } else {
              const notes = [523.25, 659.25, 783.99, 1046.50];
              const noteDuration = 0.15;
              
              const playSequence = (offset: number) => {
                  notes.forEach((freq, i) => {
                      const osc = ctx.createOscillator();
                      const gain = ctx.createGain();
                      osc.connect(gain);
                      gain.connect(ctx.destination);
                      osc.type = "sine";
                      osc.frequency.value = freq;
                      
                      const startTime = ctx.currentTime + offset + (i * noteDuration);
                      gain.gain.setValueAtTime(0, startTime);
                      gain.gain.linearRampToValueAtTime(0.1, startTime + 0.02);
                      gain.gain.exponentialRampToValueAtTime(0.001, startTime + noteDuration);
                      
                      osc.start(startTime);
                      osc.stop(startTime + noteDuration);
                  });
              };
              
              playSequence(0);
              playSequence(notes.length * noteDuration + 0.1);
          }
      };

      if (ringerIntervalRef.current) window.clearInterval(ringerIntervalRef.current);
      playBeep();
      ringerIntervalRef.current = window.setInterval(playBeep, type === "incoming" ? 3000 : 4000);
    } catch(err) { console.warn("AudioContext failed", err); }
  }, []);

  const stopRingtone = useCallback(() => {
    if (ringerIntervalRef.current) {
        window.clearInterval(ringerIntervalRef.current);
        ringerIntervalRef.current = null;
    }
  }, []);


  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteStreamRef = useRef<MediaStream | null>(null);
  const screenStreamRef = useRef<MediaStream | null>(null);
  const cameraVideoTrackRef = useRef<MediaStreamTrack | null>(null);
  const isScreenSharingRef = useRef(false);

  const beautyCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const beautyAnimationRef = useRef<number | null>(null);
  const beautyStreamRef = useRef<MediaStream | null>(null);
  const beautyTrackRef = useRef<MediaStreamTrack | null>(null);
  const beautyVideoRef = useRef<HTMLVideoElement | null>(null);
  const beautyBlurCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const beautySoftCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const beautySharpenCanvasRef = useRef<HTMLCanvasElement | null>(null);

  const pendingOffersRef = useRef<Map<string, SignalPayload>>(new Map());
  const pendingAnswersRef = useRef<Map<string, SignalPayload>>(new Map());
  const pendingIceCandidatesRef = useRef<Map<string, RTCIceCandidateInit[]>>(
    new Map(),
  );
  const pendingAcksRef = useRef<Map<string, { event: string; callId: string; sentAt: number }>>(new Map());
  const seenCallEventIdsRef = useRef<Set<string>>(new Set());
  const peerUserIdRef = useRef<string | null>(null);
  const pendingAcceptedIncomingCallIdRef = useRef<string | null>(null);
  const answeringOfferCallIdsRef = useRef<Set<string>>(new Set());
  const connectedAtRef = useRef<number | null>(null);
  const activeCallRef = useRef<CallData | null>(null);
  const incomingCallRef = useRef<CallData | null>(null);
  const callStatusRef = useRef<CallStatus>("idle");
  const callDebugRef = useRef<CallDebugState>(callDebug);
  const timeoutRefs = useRef<number[]>([]);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const statsIntervalRef = useRef<number | null>(null);
  const previousOutboundStatsRef = useRef<{ bytes: number; timestamp: number } | null>(null);
  const callRecorderRef = useRef<MediaRecorder | null>(null);
  const callRecordingChunksRef = useRef<Blob[]>([]);
  const captionRecognitionRef = useRef<any>(null);
  const activeGroupRoomRef = useRef<CallRoom | null>(null);
  const groupPeerConnectionsRef = useRef<Map<string, RTCPeerConnection>>(new Map());
  const groupPendingIceRef = useRef<Map<string, RTCIceCandidateInit[]>>(new Map());
  const [groupRemoteStreams, setGroupRemoteStreams] = useState<Record<string, MediaStream>>({});

  const containerRef = useRef<HTMLDivElement>(null);
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const minimizedRemoteVideoRef = useRef<HTMLVideoElement>(null);

  const createEventId = () =>
    `web_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`;

  const updateCallDebug = useCallback((patch: Partial<CallDebugState>) => {
    setCallDebug((prev) => ({ ...prev, ...patch }));
  }, []);

  const addCallDebug = useCallback((message: string, extra?: Record<string, unknown>) => {
    const line = `[${new Date().toLocaleTimeString()}] ${message}${
      extra ? ` ${JSON.stringify(extra)}` : ""
    }`;
    console.debug("[CALL_DEBUG]", line);
    setCallDebug((prev) => ({
      ...prev,
      events: [...prev.events.slice(-80), line],
    }));
  }, []);

  const beginCallDebug = useCallback((patch: Partial<CallDebugState>) => {
    const iceServers = iceConfigurationRef.current.iceServers || DEFAULT_ICE_SERVERS;
    const next = {
      ...createInitialCallDebug(),
      ...patch,
      iceServers,
      turnConfigured: hasTurnServer(iceServers),
    };
    setCallDebug(next);
    setShowCallDebug(false);
  }, []);

  const collectIceStats = useCallback(async (pc: RTCPeerConnection | null, context: string) => {
    if (!pc) return;
    try {
      const stats = await pc.getStats();
      let selectedPair: any = null;
      let localCandidate: any = null;
      let remoteCandidate: any = null;

      stats.forEach((report: any) => {
        if (
          report.type === "candidate-pair" &&
          (report.selected || report.nominated || report.state === "succeeded")
        ) {
          selectedPair = report;
        }
      });

      if (selectedPair) {
        localCandidate = stats.get(selectedPair.localCandidateId);
        remoteCandidate = stats.get(selectedPair.remoteCandidateId);
      }

      if (!selectedPair) {
        addCallDebug("WEB: No working ICE candidate pair selected", { context });
        updateCallDebug({ selectedCandidatePairExists: false });
        return;
      }

      const selectedCandidatePair = [
        `${localCandidate?.candidateType || "unknown"}/${localCandidate?.protocol || "unknown"}`,
        "->",
        `${remoteCandidate?.candidateType || "unknown"}/${remoteCandidate?.protocol || "unknown"}`,
        `state=${selectedPair.state || "unknown"}`,
      ].join(" ");
      updateCallDebug({
        selectedCandidatePair,
        selectedCandidatePairExists: true,
        currentRoundTripTime: selectedPair.currentRoundTripTime,
        availableOutgoingBitrate: selectedPair.availableOutgoingBitrate,
        packetsSent: selectedPair.packetsSent,
        packetsReceived: selectedPair.packetsReceived,
      });
      addCallDebug("WEB: selected ICE candidate pair", {
        selectedCandidatePair,
        currentRoundTripTime: selectedPair.currentRoundTripTime,
        availableOutgoingBitrate: selectedPair.availableOutgoingBitrate,
        packetsSent: selectedPair.packetsSent,
        packetsReceived: selectedPair.packetsReceived,
      });
    } catch (err) {
      addCallDebug("WEB: getStats failed", {
        context,
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }, [addCallDebug, updateCallDebug]);

  useEffect(() => {
    callDebugRef.current = callDebug;
  }, [callDebug]);

  useEffect(() => {
    activeCallRef.current = activeCall;
  }, [activeCall]);

  useEffect(() => {
    activeGroupRoomRef.current = activeGroupRoom;
  }, [activeGroupRoom]);

  useEffect(() => {
    incomingCallRef.current = incomingCall;
  }, [incomingCall]);

  useEffect(() => {
    if (callStatus === "incoming_ringing") {
        playRingtone("incoming");
    } else if (callStatus === "outgoing_ringing") {
        playRingtone("outgoing");
    } else {
        stopRingtone();
    }
    callStatusRef.current = callStatus;
  }, [callStatus, playRingtone, stopRingtone]);

  useEffect(() => {
    if (callStatus !== "connecting") return;
    const timeoutId = window.setTimeout(() => {
      if (callStatusRef.current !== "connecting") return;
      const diagnosis = diagnoseCallFailure(pcRef.current);
      updateCallDebug({ lastError: diagnosis.reason });
      addCallDebug("WEB: connecting longer than 10 seconds", { reason: diagnosis.reason });
      void collectIceStats(pcRef.current, "connecting_timeout");
      if (CALL_DEBUG_UI) setShowCallDebug(true);
    }, 10000);
    return () => window.clearTimeout(timeoutId);
  }, [addCallDebug, callStatus, collectIceStats, updateCallDebug]);

  useEffect(() => {
    screenStreamRef.current = screenStream;
  }, [screenStream]);

  useEffect(() => {
    isScreenSharingRef.current = isScreenSharing;
  }, [isScreenSharing]);

  useEffect(() => {
    let cancelled = false;
    fetch(`${CALL_API_BASE}/calls/ice-config`)
      .then((res) => (res.ok ? res.json() : null))
      .then((config) => {
        if (!cancelled && Array.isArray(config?.iceServers) && config.iceServers.length > 0) {
          setIceConfiguration(applyRelayPolicy({ iceServers: config.iceServers }));
          updateCallDebug({
            iceServers: config.iceServers,
            turnConfigured: hasTurnServer(config.iceServers),
          });
          addCallDebug("WEB: ICE config loaded", {
            count: config.iceServers.length,
            turnConfigured: hasTurnServer(config.iceServers),
          });
        }
      })
      .catch((err) => {
        console.warn("Could not load ICE config; using STUN fallback", err);
        updateCallDebug({
          iceServers: DEFAULT_ICE_SERVERS,
          turnConfigured: false,
          lastError: `ice_config_fallback: ${err instanceof Error ? err.message : String(err)}`,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [addCallDebug, updateCallDebug]);

  useEffect(() => {
    iceConfigurationRef.current = iceConfiguration;
  }, [iceConfiguration]);

  useEffect(() => {
    const updateResolution = () => {
      const track = localStreamRef.current?.getVideoTracks()[0] || cameraVideoTrackRef.current;
      const settings = track?.getSettings();
      if (settings?.width && settings?.height) {
        setLocalResolution(`${settings.width}x${settings.height}`);
      } else {
        setLocalResolution("");
      }
    };
    const interval = window.setInterval(updateResolution, 2000);
    return () => clearInterval(interval);
  }, [localStream, videoQuality]);

  const clearTimers = () => {
    timeoutRefs.current.forEach((id) => window.clearTimeout(id));
    timeoutRefs.current = [];
    if (reconnectTimeoutRef.current) {
      window.clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
  };

  const stopStatsMonitor = () => {
    if (statsIntervalRef.current) {
      window.clearInterval(statsIntervalRef.current);
      statsIntervalRef.current = null;
    }
    previousOutboundStatsRef.current = null;
  };

  const stopCallRecording = () => {
    const recorder = callRecorderRef.current;
    if (recorder && recorder.state !== "inactive") {
      recorder.stop();
    }
    callRecorderRef.current = null;
    setIsCallRecording(false);
  };

  const stopCaptions = () => {
    try {
      captionRecognitionRef.current?.stop?.();
    } catch {
      // Browser speech recognizers can throw if stop is called after ending.
    }
    captionRecognitionRef.current = null;
    setCaptionsEnabled(false);
  };

  const stopTracks = (stream: MediaStream | null) => {
    stream?.getTracks().forEach((track) => track.stop());
  };

  const stopBeauty = () => {
    if (beautyAnimationRef.current) window.clearTimeout(beautyAnimationRef.current);
    beautyAnimationRef.current = null;
    if (beautyVideoRef.current) {
      beautyVideoRef.current.srcObject = null;
      if (beautyVideoRef.current.parentNode) {
        beautyVideoRef.current.parentNode.removeChild(beautyVideoRef.current);
      }
      beautyVideoRef.current = null;
    }
    stopTracks(beautyStreamRef.current);
    beautyStreamRef.current = null;
    beautyTrackRef.current = null;
  };

  const resetState = () => {
    clearTimers();
    stopStatsMonitor();
    stopCallRecording();
    stopCaptions();
    if (pcRef.current) {
      pcRef.current.close();
      pcRef.current = null;
    }
    groupPeerConnectionsRef.current.forEach((pc) => pc.close());
    groupPeerConnectionsRef.current.clear();
    groupPendingIceRef.current.clear();
    stopRingtone();
    stopBeauty();
    stopTracks(screenStreamRef.current);
    stopTracks(localStreamRef.current);
    localStreamRef.current = null;
    remoteStreamRef.current = null;
    screenStreamRef.current = null;
    cameraVideoTrackRef.current = null;
    setLocalStream(null);
    setRemoteStream(null);
    setScreenStream(null);
    peerUserIdRef.current = null;
    pendingAcceptedIncomingCallIdRef.current = null;
    connectedAtRef.current = null;
    activeCallRef.current = null;
    incomingCallRef.current = null;
    setIncomingCall(null);
    setActiveCall(null);
    setCallDuration(0);
    setCallStatus("idle");
    setIsMuted(false);
    setIsVideoOff(false);
    setIsMinimized(false);
    setIsScreenSharing(false);
    setRemoteScreenSharing(false);
    setVideoQuality("720p");
    setBeautyMode("off");
    setRemoteQuality(undefined);
    setRemoteBeautyMode(undefined);
    setLocalResolution("");
    setShowAdvanced(false);
    setShowMoreControls(false);
    setShowStatsBadge(false);
    setCameraFacingMode("user");
    setCallQuality({ label: "unknown", updatedAt: Date.now() });
    setCaptionText("");
    setActiveGroupRoom(null);
    setGroupParticipantStates({});
    setGroupRemoteStreams({});
    pendingIceCandidatesRef.current.clear();
    pendingOffersRef.current.clear();
    pendingAnswersRef.current.clear();
    answeringOfferCallIdsRef.current.clear();
  };

  const queueIceCandidate = (
    callId: string,
    candidate: RTCIceCandidateInit,
  ) => {
    const queued = pendingIceCandidatesRef.current.get(callId) || [];
    queued.push(candidate);
    pendingIceCandidatesRef.current.set(callId, queued);
    addCallDebug("WEB: queued remote ICE before remote description", {
      callId,
      queuedCount: queued.length,
    });
  };

  const flushQueuedIceCandidates = async (callId: string) => {
    const pc = pcRef.current;
    if (!pc?.remoteDescription) return;

    const queued = pendingIceCandidatesRef.current.get(callId);
    if (!queued?.length) return;

    pendingIceCandidatesRef.current.delete(callId);
    for (const candidate of queued) {
      try {
        await pc.addIceCandidate(new RTCIceCandidate(candidate));
        addCallDebug("WEB: add queued ICE candidate success", { callId });
      } catch (err) {
        console.error("Failed to add queued ICE candidate", err);
        updateCallDebug({
          lastError: `ice_candidate_failed: ${err instanceof Error ? err.message : String(err)}`,
        });
      }
    }
  };

  const flushQueuedAnswer = async (callId: string) => {
    const queued = pendingAnswersRef.current.get(callId);
    const pc = pcRef.current;
    if (!queued?.answer || !pc) return;
    pendingAnswersRef.current.delete(callId);
    try {
      if (pc.signalingState !== "stable") {
        await pc.setRemoteDescription(new RTCSessionDescription(queued.answer));
        updateCallDebug({ remoteDescriptionSet: true });
        addCallDebug("WEB: setRemoteDescription queued answer success", {
          hasAnswerSdp: !!queued.answer?.sdp,
        });
        await flushQueuedIceCandidates(callId);
      }
      setCallStatus("connecting");
    } catch (err) {
      console.error("Failed to apply queued answer", err);
      updateCallDebug({
        lastError: `remote_description_failed: ${err instanceof Error ? err.message : String(err)}`,
      });
      if (CALL_DEBUG_UI) setShowCallDebug(true);
    }
  };

  const handleAck = (data: { eventId?: string; callId?: string; receivedBy?: string; status?: string }) => {
    if (!data.eventId) return;
    const pending = pendingAcksRef.current.get(data.eventId);
    if (pendingAcksRef.current.delete(data.eventId)) {
      if (pending?.event) {
        setCallDebug((prev) => ({
          ...prev,
          ackReceivedEvents: uniqueCandidateTypes(prev.ackReceivedEvents, pending.event),
        }));
      }
      console.debug("[CALL_TRACE] ack received", {
        side: "web",
        event: pending?.event,
        callId: data.callId,
        eventId: data.eventId,
        receivedBy: data.receivedBy,
        status: data.status,
      });
    }
  };

  const finishAfterStatus = (status: CallStatus, message?: string) => {
    setCallStatus(status);
    if (message && CALL_DEBUG_UI) setHasError(message);
    const timeoutId = window.setTimeout(() => {
      setHasError("");
      resetState();
    }, 2500);
    timeoutRefs.current.push(timeoutId);
  };

  const createCallLog = async (data: Omit<CallData, "callId">) => {
    const res = await fetch(`${CALL_API_BASE}/calls/start`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
      body: JSON.stringify({
        receiverUserId: data.calleeId,
        chatId: data.chatId,
        type: data.isVideo ? "video" : "audio",
      }),
    });
    if (!res.ok) throw new Error("Failed to create call log");
    return res.json() as Promise<SignalPayload & { id?: string; callId?: string }>;
  };

  const relaySignalByRest = async (event: string, data: SignalPayload, fallbackFor?: string) => {
    if (!data.callId || !data.toUserId) return;
    const payload = {
      ...data,
      event,
      eventId: createEventId(),
      fallbackFor: fallbackFor || data.eventId,
      timestamp: Date.now(),
    };
    const res = await fetch(`${CALL_API_BASE}/calls/${encodeURIComponent(data.callId)}/signal`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
      body: JSON.stringify({ event, payload }),
    });
    if (!res.ok) throw new Error(`REST call signal failed: ${res.status}`);
    addCallDebug(`WEB: REST fallback relayed ${event}`, {
      callId: data.callId,
      fallbackFor: payload.fallbackFor,
      hasAnswerSdp: !!payload.answer?.sdp,
      hasIce: !!payload.candidate?.candidate,
    });
  };

  const emitSignal = (event: string, data: SignalPayload) => {
    if (!socket || !data.fromUserId || !data.toUserId) return;
    const payload = {
      ...data,
      eventId: data.eventId || createEventId(),
      timestamp: data.timestamp || Date.now(),
      attempt: data.attempt || 1,
      event,
    };
    console.debug("[CALL_TRACE]", {
      side: "web",
      event,
      callId: payload.callId,
      from: payload.fromUserId,
      to: payload.toUserId,
      eventId: payload.eventId,
      hasOfferSdp: !!payload.offer?.sdp,
      hasAnswerSdp: !!payload.answer?.sdp,
      hasIce: !!payload.candidate?.candidate,
    });
    if (["call:start", "call:offer", "call:answer", "call:ice-candidate", "call:accepted", "call:connected", "call:failed", "call:ended"].includes(event)) {
      setCallDebug((prev) => ({
        ...prev,
        callId: payload.callId || prev.callId,
        localUserId: payload.fromUserId || prev.localUserId,
        remoteUserId: payload.toUserId || prev.remoteUserId,
        type: payload.type || prev.type,
        startSent: event === "call:start" ? true : prev.startSent,
        offerSent: event === "call:offer" ? true : prev.offerSent,
        answerSent: event === "call:answer" ? true : prev.answerSent,
        acceptedSent: event === "call:accepted" ? true : prev.acceptedSent,
        iceSentCount: event === "call:ice-candidate" ? prev.iceSentCount + 1 : prev.iceSentCount,
        lastError: event === "call:failed" ? payload.reason || prev.lastError : prev.lastError,
      }));
      addCallDebug(`WEB: emit ${event}`, {
        callId: payload.callId,
        hasOfferSdp: !!payload.offer?.sdp,
        hasAnswerSdp: !!payload.answer?.sdp,
        hasIce: !!payload.candidate?.candidate,
        reason: payload.reason,
      });
    }
    socket.emit(event, payload);
    if (["call:start", "call:offer", "call:answer", "call:ice-candidate", "call:accepted", "call:connected", "call:failed", "call:ended"].includes(event)) {
      pendingAcksRef.current.set(payload.eventId, { event, callId: payload.callId, sentAt: Date.now() });
      window.setTimeout(() => {
        if (pendingAcksRef.current.has(payload.eventId)) {
          console.warn("[CALL_TRACE] missing ack", { side: "web", event, callId: payload.callId, eventId: payload.eventId });
          if (CALL_SIGNAL_REST_FALLBACK_EVENTS.has(event)) {
            relaySignalByRest(event, payload, payload.eventId).catch((error) => {
              console.warn("[CALL_TRACE] REST fallback failed", {
                side: "web",
                event,
                callId: payload.callId,
                eventId: payload.eventId,
                error: error instanceof Error ? error.message : String(error),
              });
              addCallDebug("WEB: REST fallback failed", {
                event,
                callId: payload.callId,
                error: error instanceof Error ? error.message : String(error),
              });
            });
          }
        }
      }, event === "call:ice-candidate" ? 1800 : 3000);
    }
  };

  const ackSignal = (data: SignalPayload) => {
    if (!socket || !data.eventId || !data.callId || !data.chatId || !data.fromUserId) return;
    socket.emit("call:ack", {
      eventId: data.eventId,
      callId: data.callId,
      chatId: data.chatId,
      fromUserId: currentUser.id,
      toUserId: data.fromUserId,
      receivedBy: currentUser.id,
      status: "received",
      timestamp: Date.now(),
    });
  };

  const shouldProcessSignal = (event: string, data: Partial<SignalPayload>) => {
    if (!data.callId || !data.chatId || !data.fromUserId || !data.toUserId) {
      console.warn("Dropped malformed call event", event, data);
      return false;
    }
    if (data.eventId) {
      if (seenCallEventIdsRef.current.has(data.eventId)) {
        console.debug("[CALL_TRACE] ignored duplicate", { side: "web", event, callId: data.callId, eventId: data.eventId });
        ackSignal(data as SignalPayload);
        return false;
      }
      seenCallEventIdsRef.current.add(data.eventId);
    } else {
      console.warn("Call event missing eventId", event, data);
    }
    ackSignal(data as SignalPayload);
    console.debug("[CALL_TRACE]", {
      side: "web",
      event,
      callId: data.callId,
      from: data.fromUserId,
      to: data.toUserId,
      eventId: data.eventId,
      hasOfferSdp: !!data.offer?.sdp,
      hasAnswerSdp: !!data.answer?.sdp,
      hasIce: !!data.candidate?.candidate,
    });
    if (["call:start", "call:offer", "call:answer", "call:ice-candidate", "call:accepted", "call:connected", "call:failed", "call:ended"].includes(event)) {
      setCallDebug((prev) => ({
        ...prev,
        callId: data.callId || prev.callId,
        localUserId: currentUser.id,
        remoteUserId: data.fromUserId || prev.remoteUserId,
        type: data.type || prev.type,
        startReceived: event === "call:start" ? true : prev.startReceived,
        offerReceived: event === "call:offer" ? true : prev.offerReceived,
        answerReceived: event === "call:answer" ? true : prev.answerReceived,
        acceptedReceived: event === "call:accepted" ? true : prev.acceptedReceived,
        iceReceivedCount: event === "call:ice-candidate" ? prev.iceReceivedCount + 1 : prev.iceReceivedCount,
        lastError: event === "call:failed" ? data.reason || prev.lastError : prev.lastError,
      }));
      addCallDebug(`WEB: recv ${event}`, {
        callId: data.callId,
        hasOfferSdp: !!data.offer?.sdp,
        hasAnswerSdp: !!data.answer?.sdp,
        hasIce: !!data.candidate?.candidate,
        reason: data.reason,
      });
    }
    return true;
  };

  const buildSignal = (
    call: CallData,
    toUserId = peerUserIdRef.current,
  ): SignalPayload | null => {
    if (!toUserId) return null;
    return {
      callId: call.callId,
      chatId: call.chatId,
      fromUserId: currentUser.id,
      toUserId,
      callerId: call.callerId,
      calleeId: call.calleeId,
      type: call.isVideo ? "video" : "audio",
    };
  };

  function diagnoseCallFailure(pc?: RTCPeerConnection | null) {
    const debug = callDebugRef.current;
    if (connectedAtRef.current) {
      return {
        reason: "network_lost",
        message: "Call connection was lost after connecting.",
      };
    }
    if (!debug.answerReceived && !debug.answerSent) {
      return {
        reason: "answer_missing",
        message: "Handshake failed: missing answer.",
      };
    }
    if (debug.remoteDescriptionSet && (pc?.iceConnectionState === "checking" || pc?.iceConnectionState === "failed")) {
      if (!debug.turnConfigured) {
        return {
          reason: "stun_only_ice_failed",
          message: "ICE failed with STUN only. Configure TURN for reliable PC to mobile calling.",
        };
      }
      if (!debug.relayCandidateGenerated && !debug.relayCandidateReceived) {
        return {
          reason: "turn_no_relay_candidate",
          message: "TURN configured but no relay candidate generated. Check TURN URL, credentials, and firewall.",
        };
      }
      return {
        reason: "relay_candidate_failed",
        message: "TURN relay candidate exists but connection failed. Check TURN reachability and UDP/TCP/TLS ports.",
      };
    }
    if (debug.answerReceived || debug.answerSent) {
      if (debug.iceReceivedCount === 0) {
        return {
          reason: "ice_missing",
          message: "No ICE candidates received from receiver.",
        };
      }
      return {
        reason: "ice_failed",
        message: "ICE connection failed: no working candidate pair. TURN server may be required.",
      };
    }
    return {
      reason: "connection_failed",
      message: "Call failed.",
    };
  }

  const emitForActiveCall = (event: string, extra: Partial<SignalPayload> = {}) => {
    const call = activeCallRef.current;
    if (!call) return;
    const signal = buildSignal(call);
    if (signal) emitSignal(event, { ...signal, ...extra });
  };

  const emitMediaState = (
    audioMuted = isMuted,
    videoOff = isVideoOff,
    screenSharing = isScreenSharing,
    quality = videoQuality,
    beauty = beautyMode
  ) => {
    emitForActiveCall("call:media-state", {
      audioMuted,
      videoOff,
      screenSharing,
      quality,
      beautyMode: beauty,
    });
  };

  const getIncomingOffer = (call: CallData) => {
    const offerData = pendingOffersRef.current.get(call.callId);
    const offer = call.offer || offerData?.offer;
    const fromUserId = offerData?.fromUserId || call.callerId;
    return offer && fromUserId ? { offer, fromUserId } : null;
  };

  const answerIncomingOffer = async (
    call: CallData,
    offerData: { offer: RTCSessionDescriptionInit; fromUserId: string },
  ) => {
    const pc = pcRef.current;
    if (!pc) {
      addCallDebug("WEB: incoming offer arrived before peer connection was ready", {
        callId: call.callId,
      });
      return;
    }
    if (!offerData.offer?.sdp) throw new Error("Incoming call offer is not ready");
    if (answeringOfferCallIdsRef.current.has(call.callId)) {
      addCallDebug("WEB: answer already in progress for incoming offer", {
        callId: call.callId,
      });
      return;
    }
    if (pc.signalingState === "stable" && pc.localDescription?.type === "answer") {
      addCallDebug("WEB: incoming offer already answered", {
        callId: call.callId,
      });
      pendingAcceptedIncomingCallIdRef.current = null;
      return;
    }
    const acceptSignal = buildSignal(call, offerData.fromUserId);
    if (!acceptSignal) throw new Error("Could not build accept signal");
    answeringOfferCallIdsRef.current.add(call.callId);
    try {
      await pc.setRemoteDescription(new RTCSessionDescription(offerData.offer));
      updateCallDebug({ remoteDescriptionSet: true });
      addCallDebug("WEB: setRemoteDescription offer success", {
        callId: call.callId,
        hasOfferSdp: !!offerData.offer.sdp,
      });
      await flushQueuedIceCandidates(call.callId);
      const answer = await pc.createAnswer();
      addCallDebug("WEB: createAnswer success", { callId: call.callId, hasSdp: !!answer.sdp });
      await pc.setLocalDescription(answer);
      updateCallDebug({ localDescriptionSet: true });
      addCallDebug("WEB: setLocalDescription answer success", { callId: call.callId });
      const committedAnswer = pc.localDescription?.toJSON() as RTCSessionDescriptionInit | undefined;
      if (!committedAnswer?.sdp) throw new Error("Could not create a valid call answer");
      addCallDebug("WEB: emitting committed answer", {
        callId: call.callId,
        type: committedAnswer.type,
        sdpLength: committedAnswer.sdp.length,
      });
      emitSignal("call:answer", { ...acceptSignal, answer: committedAnswer });
      pendingAcceptedIncomingCallIdRef.current = null;
    } finally {
      answeringOfferCallIdsRef.current.delete(call.callId);
    }
  };

  const getVideoConstraints = (quality: "auto" | "720p" | "1080p" | "2k"): MediaTrackConstraints | boolean => {
    if (quality === "2k") return { width: { ideal: 2560 }, height: { ideal: 1440 }, frameRate: { ideal: 30, max: 30 } };
    if (quality === "1080p") return { width: { ideal: 1920 }, height: { ideal: 1080 }, frameRate: { ideal: 30, max: 30 } };
    if (quality === "720p") return { width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30, max: 30 } };
    return true;
  };

  const withSelectedVideoDevice = (constraints: MediaTrackConstraints | boolean) => {
    if (!selectedVideoDeviceId) return constraints;
    if (constraints === true) return { deviceId: { exact: selectedVideoDeviceId } };
    return { ...constraints, deviceId: { exact: selectedVideoDeviceId } };
  };

  const withFacingMode = (
    constraints: MediaTrackConstraints | boolean,
    facingMode: CameraFacingMode,
    exact = false,
  ) => {
    const facing = exact ? { exact: facingMode } : { ideal: facingMode };
    if (constraints === true) return { facingMode: facing };
    return { ...constraints, facingMode: facing };
  };

  const getAudioConstraints = (): MediaTrackConstraints => {
    const constraints: MediaTrackConstraints = {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    };
    if (selectedAudioDeviceId) {
      constraints.deviceId = { exact: selectedAudioDeviceId };
    }
    return constraints;
  };

  const getCameraStreamWithFallback = async (
    isVideo: boolean,
    quality: "auto" | "720p" | "1080p" | "2k",
    facingMode: CameraFacingMode = cameraFacingMode,
  ) => {
    const audioConstraints = getAudioConstraints();
    if (!isVideo) {
      return {
        stream: await requestUserMediaWithDiagnostics({ audio: audioConstraints, video: false }),
        quality: "auto" as const,
      };
    }
    
    const qualityOrder = quality === "2k" ? ["2k", "1080p", "720p", "auto"] as const
      : quality === "1080p" ? ["1080p", "720p", "auto"] as const
      : quality === "720p" ? ["720p", "auto"] as const
      : ["auto"] as const;

    let lastError: unknown = null;

    for (const q of qualityOrder) {
      try {
        const stream = await requestUserMediaWithDiagnostics({
          audio: audioConstraints,
          video: selectedVideoDeviceId
            ? withSelectedVideoDevice(getVideoConstraints(q))
            : withFacingMode(getVideoConstraints(q), facingMode),
        });
        return { stream, quality: q };
      } catch (err) {
        lastError = err;
        const errorName = err && typeof err === "object" && "name" in err ? (err as Error).name : "";
        if (["NotAllowedError", "NotFoundError", "NotReadableError", "SecurityError", "NotSupportedError"].includes(errorName)) {
          throw err;
        }
        console.warn("Video quality failed, trying fallback", q, err);
      }
    }
    throw lastError || new Error("Could not access camera");
  };

  const tuneVideoSender = (sender: RTCRtpSender, quality: "auto" | "720p" | "1080p" | "2k") => {
    const params = sender.getParameters();
    if (!params.encodings) params.encodings = [{}];
    if (!params.encodings.length) params.encodings.push({});

    if (quality === "2k") {
      params.encodings[0].maxBitrate = 6000000;
      params.encodings[0].maxFramerate = 30;
    } else if (quality === "1080p") {
      params.encodings[0].maxBitrate = 3500000;
      params.encodings[0].maxFramerate = 30;
    } else if (quality === "720p") {
      params.encodings[0].maxBitrate = 1800000;
      params.encodings[0].maxFramerate = 30;
    } else {
       delete params.encodings[0].maxBitrate;
       delete params.encodings[0].maxFramerate;
    }
    sender.setParameters(params).catch(console.error);
  };

  const ensureWorkCanvas = (ref: MutableRefObject<HTMLCanvasElement | null>) => {
    if (!ref.current) ref.current = document.createElement("canvas");
    return ref.current;
  };

  const resizeCanvasTo = (target: HTMLCanvasElement, width: number, height: number) => {
    if (target.width !== width) target.width = width;
    if (target.height !== height) target.height = height;
  };

  const getInitialCanvasSize = (track: MediaStreamTrack | null) => {
    const settings = track?.getSettings();
    const width = typeof settings?.width === "number" ? settings.width : 1280;
    const height = typeof settings?.height === "number" ? settings.height : 720;
    return { width, height };
  };

  const getBeautyCssFilter = (mode?: BeautyMode) => {
    if (mode === "bw") return "grayscale(1) contrast(1.18) brightness(1.05)";
    if (mode === "vivid") return "brightness(1.1) contrast(1.14) saturate(1.34) hue-rotate(-2deg)";
    if (mode === "dreamy") return "brightness(1.12) saturate(0.94) contrast(0.98)";
    if (mode === "beauty") return "brightness(1.08) contrast(1.06) saturate(1.12)";
    if (mode === "warm") return "sepia(0.25) saturate(1.35) brightness(1.08) hue-rotate(-8deg)";
    if (mode === "cool") return "saturate(1.25) brightness(1.05) hue-rotate(14deg)";
    if (mode === "gold") return "sepia(0.45) saturate(1.55) contrast(1.08) brightness(1.06)";
    if (mode === "comic") return "contrast(1.55) saturate(1.8) brightness(1.06)";
    return "none";
  };

  const applySkinPixelPass = (
    ctx: CanvasRenderingContext2D,
    blurCtx: CanvasRenderingContext2D,
    width: number,
    height: number,
  ) => {
    const left = Math.max(0, Math.floor(width * 0.26));
    const top = Math.max(0, Math.floor(height * 0.16));
    const boxWidth = Math.min(width - left, Math.floor(width * 0.48));
    const boxHeight = Math.min(height - top, Math.floor(height * 0.62));
    if (boxWidth <= 0 || boxHeight <= 0) return;

    let sourceData: ImageData;
    let blurData: ImageData;
    try {
      sourceData = ctx.getImageData(left, top, boxWidth, boxHeight);
      blurData = blurCtx.getImageData(left, top, boxWidth, boxHeight);
    } catch {
      return;
    }

    const data = sourceData.data;
    const smooth = blurData.data;
    const faceCx = boxWidth * 0.5;
    const faceCy = boxHeight * 0.34;
    const faceRx = boxWidth * 0.44;
    const faceRy = boxHeight * 0.34;
    const neckCx = boxWidth * 0.5;
    const neckCy = boxHeight * 0.72;
    const neckRx = boxWidth * 0.36;
    const neckRy = boxHeight * 0.18;

    for (let y = 0; y < boxHeight; y += 1) {
      for (let x = 0; x < boxWidth; x += 1) {
        const faceDistance = ((x - faceCx) ** 2) / (faceRx ** 2) + ((y - faceCy) ** 2) / (faceRy ** 2);
        const neckDistance = ((x - neckCx) ** 2) / (neckRx ** 2) + ((y - neckCy) ** 2) / (neckRy ** 2);
        const ovalWeight = Math.max(
          faceDistance < 1 ? 1 - faceDistance : 0,
          neckDistance < 1 ? (1 - neckDistance) * 0.55 : 0,
        );
        if (ovalWeight <= 0) continue;

        const idx = (y * boxWidth + x) * 4;
        const r = data[idx];
        const g = data[idx + 1];
        const b = data[idx + 2];
        const maxChannel = Math.max(r, g, b);
        const minChannel = Math.min(r, g, b);
        const isSkinLike =
          r > 55 &&
          g > 34 &&
          b > 22 &&
          r > b * 1.05 &&
          r >= g * 0.9 &&
          g >= b * 0.72 &&
          maxChannel - minChannel > 12;

        if (!isSkinLike) continue;

        const blend = Math.min(0.24, 0.08 + ovalWeight * 0.2);
        const tone = Math.min(0.12, ovalWeight * 0.1);
        const sr = smooth[idx];
        const sg = smooth[idx + 1];
        const sb = smooth[idx + 2];

        data[idx] = Math.min(255, r * (1 - blend) + sr * blend + 8 * tone);
        data[idx + 1] = Math.min(255, g * (1 - blend) + sg * blend + 5 * tone);
        data[idx + 2] = Math.min(255, b * (1 - blend) + sb * blend + 2 * tone);
      }
    }

    ctx.putImageData(sourceData, left, top);
  };

  const drawBeautyFrame = (
    mode: BeautyMode,
    video: HTMLVideoElement,
    canvas: HTMLCanvasElement,
    ctx: CanvasRenderingContext2D,
  ) => {
    const width = video.videoWidth;
    const height = video.videoHeight;
    if (!width || !height) return;

    resizeCanvasTo(canvas, width, height);
    ctx.clearRect(0, 0, width, height);
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";

    if (mode === "bw") {
      ctx.filter = "grayscale(1) contrast(1.18) brightness(1.05)";
      ctx.drawImage(video, 0, 0, width, height);
      ctx.filter = "none";
      return;
    }

    if (mode === "vivid") {
      ctx.filter = "brightness(1.11) contrast(1.18) saturate(1.42) hue-rotate(-3deg)";
      ctx.drawImage(video, 0, 0, width, height);
      ctx.filter = "none";
      return;
    }

    if (mode === "dreamy") {
      ctx.filter = "brightness(1.16) saturate(0.9) blur(0.8px) contrast(0.96)";
      ctx.drawImage(video, 0, 0, width, height);
      ctx.filter = "none";
      return;
    }

    if (mode === "warm" || mode === "cool" || mode === "gold" || mode === "comic") {
      ctx.filter = getBeautyCssFilter(mode);
      ctx.drawImage(video, 0, 0, width, height);
      ctx.filter = "none";
      if (mode === "comic") {
        ctx.save();
        ctx.globalCompositeOperation = "multiply";
        ctx.globalAlpha = 0.18;
        ctx.fillStyle = "rgba(255,255,255,0.22)";
        for (let y = 0; y < height; y += 5) {
          ctx.fillRect(0, y, width, 1);
        }
        ctx.restore();
      }
      return;
    }

    ctx.filter = getBeautyCssFilter("beauty");
    ctx.drawImage(video, 0, 0, width, height);
    ctx.filter = "none";

    const blurCanvas = ensureWorkCanvas(beautyBlurCanvasRef);
    resizeCanvasTo(blurCanvas, width, height);
    const blurCtx = blurCanvas.getContext("2d");
    if (blurCtx) {
      const softCanvas = ensureWorkCanvas(beautySoftCanvasRef);
      const softWidth = Math.max(1, Math.floor(width * 0.5));
      const softHeight = Math.max(1, Math.floor(height * 0.5));
      resizeCanvasTo(softCanvas, softWidth, softHeight);
      const softCtx = softCanvas.getContext("2d");
      blurCtx.clearRect(0, 0, width, height);
      blurCtx.imageSmoothingEnabled = true;
      blurCtx.imageSmoothingQuality = "high";
      if (softCtx) {
        softCtx.clearRect(0, 0, softWidth, softHeight);
        softCtx.imageSmoothingEnabled = true;
        softCtx.imageSmoothingQuality = "high";
        softCtx.filter = "brightness(1.08) saturate(1.06)";
        softCtx.drawImage(video, 0, 0, softWidth, softHeight);
        softCtx.filter = "none";
        blurCtx.drawImage(softCanvas, 0, 0, width, height);
      } else {
        blurCtx.drawImage(video, 0, 0, width, height);
      }

      applySkinPixelPass(ctx, blurCtx, width, height);
    }

    const sharpenCanvas = ensureWorkCanvas(beautySharpenCanvasRef);
    resizeCanvasTo(sharpenCanvas, width, height);
    const sharpenCtx = sharpenCanvas.getContext("2d");
    if (sharpenCtx) {
      sharpenCtx.clearRect(0, 0, width, height);
      sharpenCtx.filter = "contrast(1.18) saturate(1.08)";
      sharpenCtx.drawImage(video, 0, 0, width, height);
      sharpenCtx.filter = "none";

      ctx.save();
      ctx.globalCompositeOperation = "overlay";
      ctx.globalAlpha = 0.16;
      ctx.drawImage(sharpenCanvas, 0, 0, width, height);
      ctx.restore();
    }

    ctx.save();
    ctx.globalCompositeOperation = "soft-light";
    ctx.fillStyle = "rgba(255, 232, 210, 0.08)";
    ctx.fillRect(0, 0, width, height);
    ctx.restore();
  };

  const applyBeautyMode = (_mode: BeautyMode, srcTrack: MediaStreamTrack | null = cameraVideoTrackRef.current) => {
    // Filters are intentionally CSS-only. Replacing WebRTC tracks with canvas
    // capture streams caused lag and call drops on mobile browsers.
    stopBeauty();
    return srcTrack;
  };

  const buildPreviewStream = (
    videoTrack: MediaStreamTrack | null,
    audioTracks: MediaStreamTrack[] = localStreamRef.current?.getAudioTracks() || [],
  ) => {
    const previewTracks = [...audioTracks, ...(videoTrack ? [videoTrack] : [])];
    const previewStream = new MediaStream(previewTracks);
    localStreamRef.current = previewStream;
    setLocalStream(previewStream);
    if (localVideoRef.current) localVideoRef.current.srcObject = previewStream;
    return previewStream;
  };

  const refreshVideoInputs = async () => {
    try {
      if (!navigator.mediaDevices?.enumerateDevices) return;
      const devices = await navigator.mediaDevices.enumerateDevices();
      const cameras = devices.filter((device) => device.kind === "videoinput");
      const microphones = devices.filter((device) => device.kind === "audioinput");
      
      const uniqueCameras: MediaDeviceInfo[] = [];
      const seenIds = new Set<string>();
      for (const cam of cameras) {
          if (cam.deviceId && !seenIds.has(cam.deviceId)) {
              seenIds.add(cam.deviceId);
              uniqueCameras.push(cam);
          }
      }
      // If none had deviceId (e.g. permission not granted), fallback to all cameras
      const finalCameras = uniqueCameras.length > 0 ? uniqueCameras : cameras;
      
      setVideoInputs(finalCameras);
      if (!selectedVideoDeviceId && finalCameras[0]?.deviceId) {
        setSelectedVideoDeviceId(finalCameras[0].deviceId);
      }
      setAudioInputs(microphones);
      if (!selectedAudioDeviceId && microphones[0]?.deviceId) {
        setSelectedAudioDeviceId(microphones[0].deviceId);
      }
    } catch (err) {
      console.error("Could not enumerate media devices", err);
    }
  };

  const startStatsMonitor = (pc: RTCPeerConnection, call: CallData) => {
    stopStatsMonitor();
    statsIntervalRef.current = window.setInterval(async () => {
      try {
        const report = await pc.getStats();
        let rttMs: number | undefined;
        let jitterMs: number | undefined;
        let packetsLost = 0;
        let packetsReceived = 0;
        let outboundBytes: number | undefined;
        let outboundTimestamp: number | undefined;

        report.forEach((stat: any) => {
          if (stat.type === "candidate-pair" && stat.state === "succeeded") {
            if (typeof stat.currentRoundTripTime === "number") {
              rttMs = Math.round(stat.currentRoundTripTime * 1000);
            }
          }
          if (stat.type === "inbound-rtp" && !stat.isRemote) {
            if (typeof stat.jitter === "number") {
              jitterMs = Math.round(stat.jitter * 1000);
            }
            packetsLost += stat.packetsLost || 0;
            packetsReceived += stat.packetsReceived || 0;
          }
          if (stat.type === "outbound-rtp" && !stat.isRemote) {
            outboundBytes = (outboundBytes || 0) + (stat.bytesSent || 0);
            outboundTimestamp = stat.timestamp;
          }
        });

        let bitrateKbps: number | undefined;
        if (outboundBytes !== undefined && outboundTimestamp !== undefined) {
          const previous = previousOutboundStatsRef.current;
          if (previous && outboundTimestamp > previous.timestamp) {
            bitrateKbps = Math.max(
              0,
              Math.round(((outboundBytes - previous.bytes) * 8) / (outboundTimestamp - previous.timestamp)),
            );
          }
          previousOutboundStatsRef.current = {
            bytes: outboundBytes,
            timestamp: outboundTimestamp,
          };
        }

        const totalPackets = packetsLost + packetsReceived;
        const packetLossPercent = totalPackets
          ? Math.round((packetsLost / totalPackets) * 1000) / 10
          : undefined;
        const label: CallQualityStats["label"] =
          (packetLossPercent ?? 0) > 8 || (rttMs ?? 0) > 450
            ? "poor"
            : (packetLossPercent ?? 0) > 3 || (rttMs ?? 0) > 220
              ? "fair"
              : "good";
        const nextStats: CallQualityStats = {
          label,
          rttMs,
          jitterMs,
          packetLossPercent,
          bitrateKbps,
          updatedAt: Date.now(),
        };
        setCallQuality(nextStats);
        const signal = buildSignal(call);
        if (signal) emitSignal("call:stats", { ...signal, stats: nextStats });
      } catch (err) {
        console.warn("Could not read WebRTC stats", err);
      }
    }, 2500);
  };

  const attachPeerHandlers = (pc: RTCPeerConnection, call: CallData) => {
    const markConnected = () => {
      if (!connectedAtRef.current) connectedAtRef.current = Date.now();
      setCallStatus("connected");
      updateCallDebug({
        signalingState: pc.signalingState,
        iceGatheringState: pc.iceGatheringState,
        iceConnectionState: pc.iceConnectionState,
        connectionState: pc.connectionState,
      });
      addCallDebug("WEB: WebRTC connected", {
        iceConnectionState: pc.iceConnectionState,
        connectionState: pc.connectionState,
      });
      startStatsMonitor(pc, call);
      const signal = buildSignal(call);
      if (signal) emitSignal("call:connected", signal);
    };

    const markReconnecting = () => {
      setCallStatus("reconnecting");
      addCallDebug("WEB: WebRTC reconnecting", {
        iceConnectionState: pc.iceConnectionState,
        connectionState: pc.connectionState,
      });
    };

    const markFailed = () => {
      const diagnosis = diagnoseCallFailure(pc);
      updateCallDebug({
        lastError: diagnosis.reason,
        signalingState: pc.signalingState,
        iceGatheringState: pc.iceGatheringState,
        iceConnectionState: pc.iceConnectionState,
        connectionState: pc.connectionState,
      });
      addCallDebug("WEB: WebRTC failed", {
        reason: diagnosis.reason,
        iceConnectionState: pc.iceConnectionState,
        connectionState: pc.connectionState,
      });
      void collectIceStats(pc, diagnosis.reason);
      const signal = buildSignal(call);
      if (signal) emitSignal("call:failed", { ...signal, reason: diagnosis.reason });
      if (CALL_DEBUG_UI) setShowCallDebug(true);
      finishAfterStatus("failed", diagnosis.message);
    };

    pc.ontrack = (event) => {
      const stream = event.streams[0];
      remoteStreamRef.current = stream;
      setRemoteStream(stream);
      if (remoteVideoRef.current) remoteVideoRef.current.srcObject = stream;
      if (minimizedRemoteVideoRef.current) minimizedRemoteVideoRef.current.srcObject = stream;
      addCallDebug("WEB: remote track received", {
        streams: event.streams.length,
        trackKind: event.track.kind,
      });
    };

    pc.onicecandidate = (event) => {
      const signal = buildSignal(call);
      if (event.candidate && signal) {
        const parsed = parseIceCandidate(event.candidate.candidate);
        setCallDebug((prev) => ({
          ...prev,
          localCandidateTypes: uniqueCandidateTypes(prev.localCandidateTypes, parsed.type),
          relayCandidateGenerated: parsed.type === "relay" ? true : prev.relayCandidateGenerated,
        }));
        console.debug("[CALL_ICE]", {
          side: "web",
          direction: "local",
          ...parsed,
          candidate: event.candidate.candidate,
        });
        addCallDebug("WEB: local ICE candidate generated", parsed);
        emitSignal("call:ice-candidate", {
          ...signal,
          candidate: event.candidate.toJSON(),
        });
      }
    };

    pc.onicecandidateerror = (event) => {
      const details = {
        address: event.address,
        port: event.port,
        url: event.url,
        errorCode: event.errorCode,
        errorText: event.errorText,
      };
      if (isExpectedPublicStunTimeout(event)) {
        addCallDebug("WEB: public STUN candidate timed out; continuing ICE gathering", details);
        return;
      }
      addCallDebug("WEB: icecandidateerror", details);
      updateCallDebug({ lastError: `icecandidateerror_${event.errorCode}` });
    };

    pc.onconnectionstatechange = () => {
      updateCallDebug({
        signalingState: pc.signalingState,
        iceGatheringState: pc.iceGatheringState,
        iceConnectionState: pc.iceConnectionState,
        connectionState: pc.connectionState,
      });
      addCallDebug("WEB: connectionState changed", {
        connectionState: pc.connectionState,
      });
      console.debug("[CALL_TRACE]", {
        side: "web",
        peerState: pc.connectionState,
        callId: call.callId,
      });
      if (pc.connectionState === "connected") {
        markConnected();
      } else if (pc.connectionState === "disconnected") {
        markReconnecting();
      } else if (pc.connectionState === "failed") {
        markFailed();
      }
    };

    pc.oniceconnectionstatechange = () => {
      updateCallDebug({
        signalingState: pc.signalingState,
        iceGatheringState: pc.iceGatheringState,
        iceConnectionState: pc.iceConnectionState,
        connectionState: pc.connectionState,
      });
      addCallDebug("WEB: iceConnectionState changed", {
        iceConnectionState: pc.iceConnectionState,
      });
      console.debug("[CALL_TRACE]", {
        side: "web",
        iceState: pc.iceConnectionState,
        callId: call.callId,
      });
      if (
        pc.iceConnectionState === "connected" ||
        pc.iceConnectionState === "completed"
      ) {
        markConnected();
      } else if (pc.iceConnectionState === "disconnected") {
        markReconnecting();
      } else if (pc.iceConnectionState === "failed") {
        markFailed();
      }
    };

    pc.onsignalingstatechange = () => {
      updateCallDebug({ signalingState: pc.signalingState });
      addCallDebug("WEB: signalingState changed", { signalingState: pc.signalingState });
    };

    pc.onicegatheringstatechange = () => {
      updateCallDebug({ iceGatheringState: pc.iceGatheringState });
      addCallDebug("WEB: iceGatheringState changed", { iceGatheringState: pc.iceGatheringState });
    };
  };

  const flushGroupIceCandidates = async (peerId: string) => {
    const pc = groupPeerConnectionsRef.current.get(peerId);
    if (!pc?.remoteDescription) return;
    const queued = groupPendingIceRef.current.get(peerId);
    if (!queued?.length) return;
    groupPendingIceRef.current.delete(peerId);
    for (const candidate of queued) {
      try {
        await pc.addIceCandidate(new RTCIceCandidate(candidate));
      } catch (err) {
        console.error("Failed to add group ICE candidate", err);
      }
    }
  };

  const createGroupPeerConnection = (peerId: string, room: CallRoom) => {
    const existing = groupPeerConnectionsRef.current.get(peerId);
    if (existing) return existing;
    const pc = new RTCPeerConnection(iceConfigurationRef.current);
    groupPeerConnectionsRef.current.set(peerId, pc);

    localStreamRef.current?.getTracks().forEach((track) => {
      pc.addTrack(track, localStreamRef.current as MediaStream);
    });

    pc.ontrack = (event) => {
      const stream = event.streams[0];
      if (!stream) return;
      setGroupRemoteStreams((prev) => ({ ...prev, [peerId]: stream }));
    };

    pc.onicecandidate = (event) => {
      if (!event.candidate || !socket) return;
      socket.emit("call:room-ice-candidate", {
        roomId: room.id,
        fromUserId: currentUser.id,
        toUserId: peerId,
        candidate: event.candidate.toJSON(),
      });
    };

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === "failed" || pc.connectionState === "closed") {
        groupPeerConnectionsRef.current.delete(peerId);
        setGroupRemoteStreams((prev) => {
          const next = { ...prev };
          delete next[peerId];
          return next;
        });
      }
    };

    return pc;
  };

  const connectGroupMesh = async (room: CallRoom) => {
    if (!socket || !localStreamRef.current || room.status === "ended") return;
    for (const peerId of room.participantIds) {
      if (peerId === currentUser.id) continue;
      if (groupPeerConnectionsRef.current.has(peerId)) continue;
      const pc = createGroupPeerConnection(peerId, room);
      if (currentUser.id < peerId) {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        socket.emit("call:room-offer", {
          roomId: room.id,
          fromUserId: currentUser.id,
          toUserId: peerId,
          offer,
        });
      }
    }
  };

  const startNoAnswerTimeout = (call: CallData) => {
    const noticeId = window.setTimeout(() => {
      if (
        activeCallRef.current?.callId === call.callId &&
        callStatusRef.current === "outgoing_calling"
      ) {
        setCallStatus("trying_to_reach");
      }
    }, 10000);

    const missedId = window.setTimeout(() => {
      const currentCall = activeCallRef.current;
      const status = callStatusRef.current;
      if (
        currentCall?.callId === call.callId &&
        (status === "outgoing_calling" ||
          status === "trying_to_reach" ||
          status === "outgoing_ringing")
      ) {
        emitSignal("call:missed", {
          callId: call.callId,
          chatId: call.chatId,
          fromUserId: call.callerId,
          toUserId: call.calleeId,
          reason: "no_answer",
        });
        finishAfterStatus("missed", "No answer");
      }
    }, 30000);

    timeoutRefs.current.push(noticeId, missedId);
  };

  const startIncomingTimeout = (call: CallData) => {
    const timeoutId = window.setTimeout(() => {
      if (incomingCallRef.current?.callId !== call.callId) return;
      setIncomingCall(null);
      setCallStatus("idle");
    }, 30000);
    timeoutRefs.current.push(timeoutId);
  };

  useEffect(() => {
    if (!socket) return;

    const handleIncomingCall = (data: CallData & SignalPayload) => {
      if (!shouldProcessSignal("call:start", data)) return;
      if (data.toUserId && data.toUserId !== currentUser.id) return;
      if (data.callerId === currentUser.id) return;

      const isSameCall =
        activeCallRef.current?.callId === data.callId ||
        incomingCallRef.current?.callId === data.callId;

      if (!isSameCall && (activeCallRef.current || incomingCallRef.current)) {
        emitSignal("call:busy", {
          callId: data.callId,
          chatId: data.chatId,
          fromUserId: currentUser.id,
          toUserId: data.callerId,
          callerId: data.callerId,
          calleeId: data.calleeId,
          type: data.type || (data.isVideo ? "video" : "audio"),
          reason: "busy",
        });
        return;
      }

      if (isSameCall) {
        addCallDebug("WEB: ignored duplicate call:start", { callId: data.callId });
        return;
      }

      beginCallDebug({
        callId: data.callId,
        direction: "incoming",
        localUserId: currentUser.id,
        remoteUserId: data.callerId,
        type: data.type || (data.isVideo ? "video" : "audio"),
        startReceived: true,
      });
      addCallDebug("WEB: received call:start", {
        callId: data.callId,
        fromUserId: data.fromUserId,
      });

      const pendingOffer = pendingOffersRef.current.get(data.callId);
      const call = {
        ...pendingOffer,
        ...data,
        callerName: data.callerName || pendingOffer?.callerName,
        callerAvatar: data.callerAvatar || pendingOffer?.callerAvatar,
        calleeName: data.calleeName || pendingOffer?.calleeName,
        calleeAvatar: data.calleeAvatar || pendingOffer?.calleeAvatar,
        offer: pendingOffer?.offer,
        status: "incoming_ringing" as CallStatus,
      };
      peerUserIdRef.current = data.callerId;
      incomingCallRef.current = call;
      setIncomingCall(call);
      setCallStatus("incoming_ringing");
      emitSignal("call:ringing", {
        callId: data.callId,
        chatId: data.chatId,
        fromUserId: currentUser.id,
        toUserId: data.callerId,
        callerId: data.callerId,
        calleeId: data.calleeId,
        type: data.type || (data.isVideo ? "video" : "audio"),
      });
      startIncomingTimeout(call);
    };

    const handleOffer = (data: SignalPayload) => {
      if (!shouldProcessSignal("call:offer", data)) return;
      if (data.toUserId !== currentUser.id || !data.offer?.sdp) {
        console.warn("[CALL_TRACE] dropped invalid offer", { side: "web", callId: data.callId, eventId: data.eventId });
        updateCallDebug({ lastError: "invalid_offer" });
        return;
      }

      pendingOffersRef.current.set(data.callId, data);
      addCallDebug("WEB: queued offer", {
        callId: data.callId,
        hasOfferSdp: !!data.offer?.sdp,
      });
      console.debug("[CALL_TRACE] queued offer", { side: "web", callId: data.callId, eventId: data.eventId });
      const mergedIncomingCall = {
        callId: data.callId,
        chatId: data.chatId,
        callerId: data.callerId,
        callerName: data.callerName || "Hello call",
        callerAvatar: data.callerAvatar,
        calleeId: data.calleeId,
        calleeName: data.calleeName,
        calleeAvatar: data.calleeAvatar,
        isVideo: data.isVideo === true || data.type === "video",
        offer: data.offer,
        status: "incoming_ringing" as CallStatus,
      };
      setIncomingCall((prev) => {
        if (prev?.callId === data.callId) {
          const next = {
            ...prev,
            callerName: data.callerName || prev.callerName,
            callerAvatar: data.callerAvatar || prev.callerAvatar,
            calleeName: data.calleeName || prev.calleeName,
            calleeAvatar: data.calleeAvatar || prev.calleeAvatar,
            offer: data.offer,
          };
          incomingCallRef.current = next;
          return next;
        }
        if (prev || activeCallRef.current) return prev;
        peerUserIdRef.current = data.callerId;
        emitSignal("call:ringing", {
          callId: data.callId,
          chatId: data.chatId,
          fromUserId: currentUser.id,
          toUserId: data.callerId,
          callerId: data.callerId,
          calleeId: data.calleeId,
          type: data.type || (data.isVideo ? "video" : "audio"),
          callerName: data.callerName,
          callerAvatar: data.callerAvatar,
          calleeName: data.calleeName,
          calleeAvatar: data.calleeAvatar,
          isVideo: data.isVideo,
        });
        startIncomingTimeout(mergedIncomingCall);
        addCallDebug("WEB: bootstrapped incoming call from offer", {
          callId: data.callId,
        });
        setCallStatus("incoming_ringing");
        incomingCallRef.current = mergedIncomingCall;
        return mergedIncomingCall;
      });

      const activeCall = activeCallRef.current;
      if (
        activeCall?.callId === data.callId &&
        pendingAcceptedIncomingCallIdRef.current === data.callId
      ) {
        const activeIncomingCall: CallData = {
          ...activeCall,
          callerName: data.callerName || activeCall.callerName,
          callerAvatar: data.callerAvatar || activeCall.callerAvatar,
          calleeName: data.calleeName || activeCall.calleeName,
          calleeAvatar: data.calleeAvatar || activeCall.calleeAvatar,
          offer: data.offer,
        };
        void answerIncomingOffer(activeIncomingCall, {
          offer: data.offer,
          fromUserId: data.fromUserId || data.callerId || activeCall.callerId,
        }).catch((err) => {
          console.error(err);
          updateCallDebug({
            lastError: `incoming_answer_failed: ${err instanceof Error ? err.message : String(err)}`,
          });
          if (CALL_DEBUG_UI) setShowCallDebug(true);
          const signal = buildSignal(activeIncomingCall, data.fromUserId || data.callerId || activeCall.callerId);
          if (signal) emitSignal("call:failed", { ...signal, reason: "failed" });
          finishAfterStatus("failed", "Call failed");
        });
      }
    };

    const handleCallAnswer = async (data: SignalPayload) => {
      if (!shouldProcessSignal("call:answer", data)) return;
      if (!isCurrentCallEvent(data)) {
        addCallDebug("WEB: ignored stale answer", {
          callId: data.callId,
          currentCallId: activeCallRef.current?.callId,
        });
        return;
      }
      if (!data.callId || data.toUserId !== currentUser.id || !data.answer?.sdp) {
        console.warn("[CALL_TRACE] dropped invalid answer", { side: "web", callId: data.callId, eventId: data.eventId });
        updateCallDebug({ lastError: "invalid_answer" });
        return;
      }
      try {
        const pc = pcRef.current;
        if (!pc) {
          pendingAnswersRef.current.set(data.callId, data);
          addCallDebug("WEB: queued answer because peer is not ready", {
            callId: data.callId,
            hasAnswerSdp: !!data.answer?.sdp,
          });
          console.debug("[CALL_TRACE] queued answer", { side: "web", callId: data.callId, eventId: data.eventId });
          return;
        }
        if (pc.signalingState !== "stable") {
          await pc.setRemoteDescription(new RTCSessionDescription(data.answer));
          updateCallDebug({
            remoteDescriptionSet: true,
            signalingState: pc.signalingState,
            iceGatheringState: pc.iceGatheringState,
            iceConnectionState: pc.iceConnectionState,
            connectionState: pc.connectionState,
          });
          addCallDebug("WEB: setRemoteDescription answer success", {
            hasAnswerSdp: !!data.answer?.sdp,
          });
          await flushQueuedIceCandidates(data.callId);
        } else {
          console.warn("Ignoring answer while peer connection is already stable", data.callId);
          addCallDebug("WEB: ignored answer because peer connection is already stable", {
            callId: data.callId,
          });
        }
        setCallStatus("connecting");
      } catch (err) {
        console.error(err);
        updateCallDebug({
          lastError: `remote_description_failed: ${err instanceof Error ? err.message : String(err)}`,
        });
        addCallDebug("WEB: setRemoteDescription answer failed", {
          error: err instanceof Error ? err.message : String(err),
        });
        if (CALL_DEBUG_UI) setShowCallDebug(true);
      }
    };

    const handleIceCandidate = async (data: SignalPayload) => {
      if (!shouldProcessSignal("call:ice-candidate", data)) return;
      if (!isCurrentCallEvent(data)) {
        addCallDebug("WEB: ignored stale ICE", {
          callId: data.callId,
          currentCallId: activeCallRef.current?.callId,
        });
        return;
      }
      if (data.toUserId !== currentUser.id || !data.candidate?.candidate) {
        console.warn("[CALL_TRACE] dropped invalid ICE", { side: "web", callId: data.callId, eventId: data.eventId });
        updateCallDebug({ lastError: "invalid_ice_candidate" });
        return;
      }
      const parsed = parseIceCandidate(data.candidate.candidate);
      setCallDebug((prev) => ({
        ...prev,
        remoteCandidateTypes: uniqueCandidateTypes(prev.remoteCandidateTypes, parsed.type),
        relayCandidateReceived: parsed.type === "relay" ? true : prev.relayCandidateReceived,
      }));
      console.debug("[CALL_ICE]", {
        side: "web",
        direction: "remote",
        ...parsed,
      });
      addCallDebug("WEB: remote ICE candidate received", parsed);
      if (!pcRef.current || !pcRef.current.remoteDescription) {
        queueIceCandidate(data.callId, data.candidate);
        console.debug("[CALL_TRACE] queued ice", { side: "web", callId: data.callId, eventId: data.eventId });
        return;
      }
      try {
        await pcRef.current.addIceCandidate(new RTCIceCandidate(data.candidate));
        addCallDebug("WEB: addIceCandidate success", { callId: data.callId });
      } catch (err) {
        console.error(err);
        updateCallDebug({
          lastError: `ice_candidate_failed: ${err instanceof Error ? err.message : String(err)}`,
        });
        addCallDebug("WEB: addIceCandidate failed", {
          error: err instanceof Error ? err.message : String(err),
        });
      }
    };

    const handleRinging = (data: SignalPayload) => {
      if (!shouldProcessSignal("call:ringing", data)) return;
      if (!isCurrentCallEvent(data)) return;
      if (data.toUserId !== currentUser.id) return;
      addCallDebug("WEB: received call:ringing", { callId: data.callId });
      if (["outgoing_calling", "trying_to_reach", "idle"].includes(callStatusRef.current)) {
        setCallStatus("outgoing_ringing");
      }
    };

    const handleAccepted = (data: SignalPayload) => {
      if (!shouldProcessSignal("call:accepted", data)) return;
      if (!isCurrentCallEvent(data)) return;
      if (data.toUserId !== currentUser.id) return;
      addCallDebug("WEB: received call:accepted", { callId: data.callId });
      if (["outgoing_calling", "trying_to_reach", "outgoing_ringing", "idle"].includes(callStatusRef.current)) {
        setCallStatus("connecting");
      }
    };

    const handleConnected = (data: SignalPayload) => {
      if (!shouldProcessSignal("call:connected", data)) return;
      if (!isCurrentCallEvent(data)) return;
      if (data.toUserId !== currentUser.id) return;
      if (!connectedAtRef.current) connectedAtRef.current = Date.now();
      addCallDebug("WEB: received call:connected", { callId: data.callId });
      if (callStatusRef.current !== "connected") {
        setCallStatus("connected");
      }
    };

    const handleReconnecting = (data: SignalPayload) => {
      if (!shouldProcessSignal("call:reconnecting", data)) return;
      if (!isCurrentCallEvent(data)) return;
      if (data.toUserId !== currentUser.id) return;
      setCallStatus("reconnecting");
    };

    const isCurrentCallEvent = (data?: SignalPayload) => {
      if (!data?.callId) return true;
      return (
        activeCallRef.current?.callId === data.callId ||
        incomingCallRef.current?.callId === data.callId
      );
    };

    const endFromRemote = (status: CallStatus, message?: string, data?: SignalPayload) => {
      if (!isCurrentCallEvent(data)) return;
      const finalStatus =
        data?.reason === "ended_by_caller" || data?.reason === "cancelled"
          ? "cancelled"
          : status;
      finishAfterStatus(finalStatus, message);
    };

    const handleRemoteScreenStarted = (data: SignalPayload) => {
      if (!isCurrentCallEvent(data) || data.toUserId !== currentUser.id) return;
      setRemoteScreenSharing(true);
    };

    const handleRemoteScreenStopped = (data: SignalPayload) => {
      if (!isCurrentCallEvent(data) || data.toUserId !== currentUser.id) return;
      setRemoteScreenSharing(false);
    };

    const handleMediaState = (data: SignalPayload) => {
      if (!isCurrentCallEvent(data) || data.toUserId !== currentUser.id) return;
      if (typeof data.screenSharing === "boolean") {
        setRemoteScreenSharing(data.screenSharing);
      }
      if (data.quality) setRemoteQuality(data.quality);
      if (data.beautyMode) setRemoteBeautyMode(data.beautyMode);
    };

    const handleDisconnect = () => {
      if (!activeCallRef.current) return;
      setCallStatus("reconnecting");
      reconnectTimeoutRef.current = window.setTimeout(() => {
        const call = activeCallRef.current;
        if (!call) return;
        const signal = buildSignal(call);
        if (signal) emitSignal("call:ended", { ...signal, reason: "network_lost" });
        finishAfterStatus("failed", "Connection lost");
      }, 10000);
    };

    const handleReconnect = () => {
      if (reconnectTimeoutRef.current) {
        window.clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
      if (activeCallRef.current && callStatusRef.current === "reconnecting") {
        setCallStatus(connectedAtRef.current ? "connected" : "connecting");
      }
    };

    const startOutgoingCall = async (e: Event) => {
      const { chatId, calleeId, calleeName, calleeAvatar, isVideo } = (
        e as CustomEvent<StartCallDetail>
      ).detail;

      if (!calleeId) {
        setHasError("Select a direct chat before starting a call.");
        window.setTimeout(() => setHasError(""), 3000);
        return;
      }

      const readinessError = getMediaCaptureReadinessError();
      if (readinessError) {
        setHasError(describeMediaAccessError(readinessError));
        window.setTimeout(() => setHasError(""), 5000);
        return;
      }

      const baseCall = {
        chatId,
        callerId: currentUser.id,
        callerName: currentUser.name,
        callerAvatar: currentUser.avatar,
        calleeId,
        calleeName,
        calleeAvatar,
        isVideo,
      };

      beginCallDebug({
        direction: "outgoing",
        localUserId: currentUser.id,
        remoteUserId: calleeId,
        type: isVideo ? "video" : "audio",
      });
      addCallDebug("WEB: outgoing call requested", { chatId, calleeId, isVideo });

      try {
        const callPayload = await createCallLog(baseCall);
        const callId = callPayload.callId || callPayload.id;
        if (!callId) throw new Error("Call log did not return a call id");
        updateCallDebug({ callId });
        addCallDebug("WEB: create call log success", { callId });
        const call: CallData = { ...baseCall, callId, status: "outgoing_calling" };
        peerUserIdRef.current = calleeId;
        activeCallRef.current = call;
        setActiveCall(call);
        setIsMinimized(false);
        setCallStatus("outgoing_calling");

        const pc = new RTCPeerConnection(iceConfigurationRef.current);
        pcRef.current = pc;
        updateCallDebug({
          signalingState: pc.signalingState,
          iceGatheringState: pc.iceGatheringState,
          iceConnectionState: pc.iceConnectionState,
          connectionState: pc.connectionState,
        });
        addCallDebug("WEB: RTCPeerConnection created", {
          iceServers: iceConfigurationRef.current.iceServers?.length || 0,
          turnConfigured: hasTurnServer(iceConfigurationRef.current.iceServers),
        });
        
        const { stream: obtainedStream, quality: obtainedQuality } = await getCameraStreamWithFallback(isVideo, videoQuality);
        addCallDebug("WEB: local media acquired", {
          audioTracks: obtainedStream.getAudioTracks().length,
          videoTracks: obtainedStream.getVideoTracks().length,
          quality: obtainedQuality,
        });
        const cameraTrack = obtainedStream.getVideoTracks()[0] || null;
        localStreamRef.current = obtainedStream;
        cameraVideoTrackRef.current = cameraTrack;
        setVideoQuality(obtainedQuality);
        
        const finalVideoTrack = applyBeautyMode(beautyMode, cameraTrack);
        const audioTracks = obtainedStream.getAudioTracks();
        
        buildPreviewStream(finalVideoTrack || cameraTrack, audioTracks);
        void refreshVideoInputs();

        audioTracks.forEach((track) => pc.addTrack(track, obtainedStream));
        if (finalVideoTrack) {
           const sender = pc.addTrack(finalVideoTrack, obtainedStream);
           tuneVideoSender(sender, obtainedQuality);
        }
        addCallDebug("WEB: local tracks added", {
          audioTracks: audioTracks.length,
          videoTrack: !!finalVideoTrack,
        });

        attachPeerHandlers(pc, call);

        addCallDebug("WEB: createOffer started");
        const offer = await pc.createOffer();
        addCallDebug("WEB: createOffer success", { hasSdp: !!offer.sdp });
        await pc.setLocalDescription(offer);
        updateCallDebug({ localDescriptionSet: true });
        addCallDebug("WEB: setLocalDescription offer success");

        const signal = buildSignal(call, calleeId);
        const committedOffer = pc.localDescription?.toJSON() as RTCSessionDescriptionInit | undefined;
        if (!signal || !committedOffer?.sdp) throw new Error("Could not create a valid call offer");
        emitSignal("call:start", signal);
        emitSignal("call:offer", { ...signal, offer: committedOffer });
        void flushQueuedAnswer(callId);
        startNoAnswerTimeout(call);
      } catch (err) {
        console.error("Failed to start call", err);
        updateCallDebug({
          lastError: `start_call_failed: ${err instanceof Error ? err.message : String(err)}`,
        });
        addCallDebug("WEB: outgoing call failed before signaling completed", {
          error: err instanceof Error ? err.message : String(err),
        });
        if (CALL_DEBUG_UI) setShowCallDebug(true);
        setHasError(err instanceof Error ? describeMediaAccessError(err, err.message) : describeMediaAccessError(err));
        resetState();
      }
    };

    const startGroupCall = async (e: Event) => {
      const { chatId, participantIds } = (
        e as CustomEvent<StartGroupCallDetail>
      ).detail;
      const isVideo = false;
      const invitedIds = Array.from(new Set(participantIds)).filter(
        (id) => id && id !== currentUser.id,
      );

      if (invitedIds.length > 3) {
        setHasError("Group calls are limited to 4 participants in this mesh version.");
        window.setTimeout(() => setHasError(""), 4000);
        return;
      }
      const readinessError = getMediaCaptureReadinessError();
      if (readinessError) {
        setHasError(describeMediaAccessError(readinessError));
        window.setTimeout(() => setHasError(""), 5000);
        return;
      }

      try {
        const res = await fetch(`${CALL_API_BASE}/calls/group/start`, {
          method: "POST",
          headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
          body: JSON.stringify({
            chatId,
            participantIds: invitedIds,
            type: "audio",
          }),
        });
        if (!res.ok) {
          const error = await res.json().catch(() => ({}));
          throw new Error(error.error || "Could not create group call");
        }
        const room = (await res.json()) as CallRoom & { callId?: string };
        setActiveGroupRoom(room);
        setGroupParticipantStates({
          [currentUser.id]: {
            audioMuted: isMuted,
            videoOff: !isVideo || isVideoOff,
            screenSharing: false,
            quality: videoQuality,
            beautyMode,
          },
        });
        setCallStatus("connected");
        setIsMinimized(false);

        const { stream: obtainedStream, quality: obtainedQuality } =
          await getCameraStreamWithFallback(isVideo, videoQuality);
        const cameraTrack = obtainedStream.getVideoTracks()[0] || null;
        localStreamRef.current = obtainedStream;
        cameraVideoTrackRef.current = cameraTrack;
        setVideoQuality(obtainedQuality);
        buildPreviewStream(applyBeautyMode(beautyMode, cameraTrack) || cameraTrack, obtainedStream.getAudioTracks());
        void refreshVideoInputs();
        void connectGroupMesh(room);
      } catch (err) {
        console.error(err);
        setHasError(err instanceof Error ? err.message : "Could not start group call.");
        window.setTimeout(() => setHasError(""), 4000);
      }
    };

    const handleRoomCreated = (data: {
      room?: CallRoom;
      chatName?: string;
      fromUserId?: string;
      participantIds?: string[];
    }) => {
      const room = data.room;
      if (!room || data.fromUserId === currentUser.id) return;
      if (!room.participantIds.includes(currentUser.id)) return;
      if (activeCallRef.current || activeGroupRoomRef.current) {
        socket.emit("call:room-leave", {
          roomId: room.id,
          userId: currentUser.id,
          reason: "busy",
        });
        return;
      }
      setActiveGroupRoom(room);
      setCallStatus("incoming_ringing");
      setHasError(`Incoming group ${room.type} call${data.chatName ? ` in ${data.chatName}` : ""}`);
      window.setTimeout(() => setHasError(""), 4000);
    };

    const handleRoomJoin = (data: { roomId?: string; userId?: string }) => {
      if (!data.roomId || activeGroupRoomRef.current?.id !== data.roomId || !data.userId) return;
      setActiveGroupRoom((prev) =>
        prev
          ? {
              ...prev,
              status: "active",
              participantIds: Array.from(new Set([...prev.participantIds, data.userId!])),
            }
          : prev,
      );
      const room = activeGroupRoomRef.current;
      if (room) {
        void connectGroupMesh({
          ...room,
          status: "active",
          participantIds: Array.from(new Set([...room.participantIds, data.userId])),
        });
      }
    };

    const handleRoomLeave = (data: { roomId?: string; userId?: string; ended?: boolean }) => {
      if (!data.roomId || activeGroupRoomRef.current?.id !== data.roomId) return;
      if (data.ended) {
        finishAfterStatus("ended", "Group call ended");
        return;
      }
      if (data.userId) {
        groupPeerConnectionsRef.current.get(data.userId)?.close();
        groupPeerConnectionsRef.current.delete(data.userId);
        setGroupRemoteStreams((prev) => {
          const next = { ...prev };
          delete next[data.userId!];
          return next;
        });
      }
      setActiveGroupRoom((prev) =>
        prev
          ? {
              ...prev,
              participantIds: prev.participantIds.filter((id) => id !== data.userId),
            }
          : prev,
      );
    };

    const handleParticipantState = (data: {
      roomId?: string;
      userId?: string;
      mediaState?: CallMediaState;
    }) => {
      if (
        !data.roomId ||
        activeGroupRoomRef.current?.id !== data.roomId ||
        !data.userId ||
        !data.mediaState
      ) {
        return;
      }
      setGroupParticipantStates((prev) => ({
        ...prev,
        [data.userId!]: data.mediaState!,
      }));
    };

    const handleRoomOffer = async (data: SignalPayload & { roomId?: string }) => {
      const room = activeGroupRoomRef.current;
      if (!room || data.roomId !== room.id || data.toUserId !== currentUser.id || !data.offer) return;
      try {
        const pc = createGroupPeerConnection(data.fromUserId, room);
        await pc.setRemoteDescription(new RTCSessionDescription(data.offer));
        await flushGroupIceCandidates(data.fromUserId);
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        socket.emit("call:room-answer", {
          roomId: room.id,
          fromUserId: currentUser.id,
          toUserId: data.fromUserId,
          answer,
        });
      } catch (err) {
        console.error("Could not answer group offer", err);
      }
    };

    const handleRoomAnswer = async (data: SignalPayload & { roomId?: string }) => {
      const room = activeGroupRoomRef.current;
      if (!room || data.roomId !== room.id || data.toUserId !== currentUser.id || !data.answer) return;
      const pc = groupPeerConnectionsRef.current.get(data.fromUserId);
      if (!pc) return;
      try {
        await pc.setRemoteDescription(new RTCSessionDescription(data.answer));
        await flushGroupIceCandidates(data.fromUserId);
      } catch (err) {
        console.error("Could not apply group answer", err);
      }
    };

    const handleRoomIceCandidate = async (data: SignalPayload & { roomId?: string }) => {
      const room = activeGroupRoomRef.current;
      if (!room || data.roomId !== room.id || data.toUserId !== currentUser.id || !data.candidate) return;
      const pc = groupPeerConnectionsRef.current.get(data.fromUserId);
      if (!pc || !pc.remoteDescription) {
        const queued = groupPendingIceRef.current.get(data.fromUserId) || [];
        queued.push(data.candidate);
        groupPendingIceRef.current.set(data.fromUserId, queued);
        return;
      }
      try {
        await pc.addIceCandidate(new RTCIceCandidate(data.candidate));
      } catch (err) {
        console.error("Could not add group ICE candidate", err);
      }
    };

    window.addEventListener("START_CALL", startOutgoingCall);
    window.addEventListener("START_GROUP_CALL", startGroupCall);
    socket.on("call:start", handleIncomingCall);
    socket.on("call:offer", handleOffer);
    socket.on("call:answer", handleCallAnswer);
    socket.on("call:ice-candidate", handleIceCandidate);
    socket.on("call:ringing", handleRinging);
    socket.on("call:accepted", handleAccepted);
    socket.on("call:connected", handleConnected);
    socket.on("call:reconnecting", handleReconnecting);
    socket.on("call:ack", handleAck);
    socket.on("call:screen-share-started", handleRemoteScreenStarted);
    socket.on("call:screen-share-stopped", handleRemoteScreenStopped);
    socket.on("call:media-state", handleMediaState);
    socket.on("call:room-created", handleRoomCreated);
    socket.on("call:room-join", handleRoomJoin);
    socket.on("call:room-leave", handleRoomLeave);
    socket.on("call:participant-state", handleParticipantState);
    socket.on("call:room-offer", handleRoomOffer);
    socket.on("call:room-answer", handleRoomAnswer);
    socket.on("call:room-ice-candidate", handleRoomIceCandidate);
    socket.on("disconnect", handleDisconnect);
    socket.on("connect", handleReconnect);
    socket.on("call:busy", (data) => shouldProcessSignal("call:busy", data) && endFromRemote("busy", "User busy", data));
    socket.on("call:missed", (data) => shouldProcessSignal("call:missed", data) && endFromRemote("missed", "No answer", data));
    socket.on("call:unavailable", (data) =>
      shouldProcessSignal("call:unavailable", data) && endFromRemote("unavailable", "User unavailable", data),
    );
    socket.on("call:declined", (data) =>
      shouldProcessSignal("call:declined", data) && endFromRemote("declined", "Call declined", data),
    );
    socket.on("call:ended", (data) => shouldProcessSignal("call:ended", data) && endFromRemote("ended", undefined, data));
    socket.on("call:failed", (data) => shouldProcessSignal("call:failed", data) && endFromRemote("failed", "Call failed", data));

    return () => {
      window.removeEventListener("START_CALL", startOutgoingCall);
      window.removeEventListener("START_GROUP_CALL", startGroupCall);
      socket.off("call:start", handleIncomingCall);
      socket.off("call:offer", handleOffer);
      socket.off("call:answer", handleCallAnswer);
      socket.off("call:ice-candidate", handleIceCandidate);
      socket.off("call:ringing", handleRinging);
      socket.off("call:accepted", handleAccepted);
      socket.off("call:connected", handleConnected);
      socket.off("call:reconnecting", handleReconnecting);
      socket.off("call:ack", handleAck);
      socket.off("call:screen-share-started", handleRemoteScreenStarted);
      socket.off("call:screen-share-stopped", handleRemoteScreenStopped);
      socket.off("call:media-state", handleMediaState);
      socket.off("call:room-created", handleRoomCreated);
      socket.off("call:room-join", handleRoomJoin);
      socket.off("call:room-leave", handleRoomLeave);
      socket.off("call:participant-state", handleParticipantState);
      socket.off("call:room-offer", handleRoomOffer);
      socket.off("call:room-answer", handleRoomAnswer);
      socket.off("call:room-ice-candidate", handleRoomIceCandidate);
      socket.off("disconnect", handleDisconnect);
      socket.off("connect", handleReconnect);
      socket.off("call:busy");
      socket.off("call:missed");
      socket.off("call:unavailable");
      socket.off("call:declined");
      socket.off("call:ended");
      socket.off("call:failed");
    };
  }, [socket, currentUser]);

  useEffect(() => {
    let interval: number | undefined;
    if (activeCall && callStatus === "connected") {
      interval = window.setInterval(() => setCallDuration((duration) => duration + 1), 1000);
    }
    return () => {
      if (interval) window.clearInterval(interval);
    };
  }, [activeCall, callStatus]);

  useEffect(() => {
    if (localVideoRef.current && localStream) {
      localVideoRef.current.srcObject = localStream;
    }
    if (remoteVideoRef.current && remoteStream) {
      remoteVideoRef.current.srcObject = remoteStream;
    }
    if (minimizedRemoteVideoRef.current && remoteStream) {
      minimizedRemoteVideoRef.current.srcObject = remoteStream;
    }
  }, [activeCall, isMinimized, isVideoOff, localStream, remoteStream]);

  useEffect(() => {
    const handleFullscreenChange = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", handleFullscreenChange);
  }, []);

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      const call = activeCallRef.current;
      const room = activeGroupRoomRef.current;
      if (call || room) {
        event.preventDefault();
        event.returnValue = "";
      }
    };

    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [socket, currentUser.id]);

  const acceptCall = async () => {
    if (!socket || !incomingCall) return;
    addCallDebug("WEB: accept clicked", { callId: incomingCall.callId });

    const readinessError = getMediaCaptureReadinessError();
    if (readinessError) {
      setHasError(describeMediaAccessError(readinessError));
      window.setTimeout(() => {
        setHasError("");
        declineCall();
      }, 3000);
      return;
    }

    const offerData = getIncomingOffer(incomingCall);
    clearTimers();
    peerUserIdRef.current = offerData?.fromUserId || incomingCall.callerId;
    pendingAcceptedIncomingCallIdRef.current = incomingCall.callId;
    beginCallDebug({
      callId: incomingCall.callId,
      direction: "incoming",
      localUserId: currentUser.id,
      remoteUserId: offerData?.fromUserId || incomingCall.callerId,
      type: incomingCall.isVideo ? "video" : "audio",
      startReceived: true,
      offerReceived: !!offerData,
    });
    addCallDebug("WEB: accept clicked", { callId: incomingCall.callId });
    setActiveCall(incomingCall);
    activeCallRef.current = incomingCall;
    setIncomingCall(null);
    incomingCallRef.current = null;
    setIsMinimized(false);
    setCallStatus("connecting");

    try {
      const acceptSignal = buildSignal(
        incomingCall,
        offerData?.fromUserId || incomingCall.callerId,
      );
      if (!acceptSignal) throw new Error("Could not build accept signal");
      emitSignal("call:accepted", acceptSignal);

      const pc = new RTCPeerConnection(iceConfigurationRef.current);
      pcRef.current = pc;
      addCallDebug("WEB: RTCPeerConnection created for incoming call", {
        iceServers: iceConfigurationRef.current.iceServers?.length || 0,
        turnConfigured: hasTurnServer(iceConfigurationRef.current.iceServers),
      });

      const { stream: obtainedStream, quality: obtainedQuality } = await getCameraStreamWithFallback(incomingCall.isVideo, videoQuality);
      addCallDebug("WEB: local media acquired", {
        audioTracks: obtainedStream.getAudioTracks().length,
        videoTracks: obtainedStream.getVideoTracks().length,
        quality: obtainedQuality,
      });
      const cameraTrack = obtainedStream.getVideoTracks()[0] || null;
      localStreamRef.current = obtainedStream;
      cameraVideoTrackRef.current = cameraTrack;
      setVideoQuality(obtainedQuality);
      
      const finalVideoTrack = applyBeautyMode(beautyMode, cameraTrack);
      const audioTracks = obtainedStream.getAudioTracks();

      buildPreviewStream(finalVideoTrack || cameraTrack, audioTracks);
      void refreshVideoInputs();

      audioTracks.forEach((track) => pc.addTrack(track, obtainedStream));
      if (finalVideoTrack) {
         const sender = pc.addTrack(finalVideoTrack, obtainedStream);
         tuneVideoSender(sender, obtainedQuality);
      }
      addCallDebug("WEB: local tracks added", {
        audioTracks: audioTracks.length,
        videoTrack: !!finalVideoTrack,
      });

      attachPeerHandlers(pc, incomingCall);
        const latestOffer = getIncomingOffer(incomingCall) || offerData;
        if (latestOffer) {
          await answerIncomingOffer(incomingCall, latestOffer);
        } else {
        addCallDebug("WEB: accept waiting for remote offer", {
          callId: incomingCall.callId,
        });
      }
    } catch (err) {
      console.error(err);
      updateCallDebug({
        lastError: `incoming_answer_failed: ${err instanceof Error ? err.message : String(err)}`,
      });
      if (CALL_DEBUG_UI) setShowCallDebug(true);
      const signal = buildSignal(
        incomingCall,
        offerData?.fromUserId || incomingCall.callerId,
      );
      if (signal) emitSignal("call:failed", { ...signal, reason: "failed" });
      finishAfterStatus("failed", "Call failed");
    }
  };

  const declineCall = () => {
    if (!incomingCall) return;
    emitSignal("call:declined", {
      callId: incomingCall.callId,
      chatId: incomingCall.chatId,
      fromUserId: currentUser.id,
      toUserId: incomingCall.callerId,
      reason: "declined",
    });
    finishAfterStatus("idle", "Call declined");
  };

  const endActiveCall = () => {
    if (!activeCall) return;
    const signal = buildSignal(activeCall);
    if (signal) {
      emitSignal("call:ended", {
        ...signal,
        reason: connectedAtRef.current ? "ended" : "ended_by_caller",
      });
    }
    finishAfterStatus("ended", "Call ended");
  };

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  const toggleMute = () => {
    const audioTrack = localStreamRef.current?.getAudioTracks()[0];
    if (audioTrack) {
      audioTrack.enabled = !audioTrack.enabled;
      const nextMuted = !audioTrack.enabled;
      setIsMuted(nextMuted);
      emitMediaState(nextMuted, isVideoOff, isScreenSharing);
      emitGroupMediaState(nextMuted, isVideoOff, isScreenSharing);
    }
  };

  const toggleVideo = () => {
    const videoTrack = cameraVideoTrackRef.current || localStreamRef.current?.getVideoTracks()[0];
    if (videoTrack) {
      videoTrack.enabled = !videoTrack.enabled;
      const nextVideoOff = !videoTrack.enabled;
      setIsVideoOff(nextVideoOff);
      emitMediaState(isMuted, nextVideoOff, isScreenSharing);
      emitGroupMediaState(isMuted, nextVideoOff, isScreenSharing);
    }
  };

  const findVideoSender = () =>
    pcRef.current?.getSenders().find((sender) => sender.track?.kind === "video");

  const replaceLocalVideoTrack = async (
    nextTrack: MediaStreamTrack,
    nextQuality = videoQuality,
  ) => {
    const oldVideoTrack =
      cameraVideoTrackRef.current || localStreamRef.current?.getVideoTracks()[0];
    cameraVideoTrackRef.current = nextTrack;

    const trackToUse = applyBeautyMode(beautyMode, nextTrack);
    if (!isScreenSharing) {
      const directSender = findVideoSender();
      if (directSender) {
        await directSender.replaceTrack(trackToUse);
        tuneVideoSender(directSender, nextQuality);
      }
      groupPeerConnectionsRef.current.forEach((pc) => {
        const sender = pc.getSenders().find((item) => item.track?.kind === "video");
        if (sender) {
          sender.replaceTrack(trackToUse).catch(console.error);
          tuneVideoSender(sender, nextQuality);
        }
      });
    }

    const audioTracks = localStreamRef.current?.getAudioTracks() || [];
    if (!isScreenSharing) buildPreviewStream(trackToUse || nextTrack, audioTracks);
    oldVideoTrack?.stop();
  };

  const startScreenShare = async () => {
    if (!activeCall?.isVideo) return;
    if (!featureSupport.screenShare) {
      setHasError("Screen sharing is not supported on this browser.");
      window.setTimeout(() => setHasError(""), 3000);
      return;
    }
    
    try {
      const displayStream = await navigator.mediaDevices.getDisplayMedia({
        video: true,
        audio: true,
      });
      const screenVideoTrack = displayStream.getVideoTracks()[0];
      if (!screenVideoTrack) {
        stopTracks(displayStream);
        setHasError("No screen video track was selected.");
        window.setTimeout(() => setHasError(""), 3500);
        return;
      }

      cameraVideoTrackRef.current =
        cameraVideoTrackRef.current || localStreamRef.current?.getVideoTracks()[0] || null;

      const sender = findVideoSender();
      if (sender) {
        await sender.replaceTrack(screenVideoTrack);
        tuneVideoSender(sender, "1080p");
      } else if (pcRef.current) {
        const sender2 = pcRef.current.addTrack(screenVideoTrack, displayStream);
        tuneVideoSender(sender2, "1080p");
      }

      stopTracks(screenStreamRef.current);
      screenStreamRef.current = displayStream;
      setScreenStream(displayStream);
      setLocalStream(displayStream);
      setIsScreenSharing(true);
      emitForActiveCall("call:screen-share-started");
      emitMediaState(isMuted, isVideoOff, true);
      screenVideoTrack.onended = () => {
        void stopScreenShare();
      };
    } catch (err) {
      console.error("Screen share failed", err);
      setHasError(err instanceof Error ? describeMediaAccessError(err, "Could not start screen sharing.") : "Could not start screen sharing.");
      window.setTimeout(() => setHasError(""), 3500);
    }
  };

  const stopScreenShare = async () => {
    const call = activeCallRef.current;
    if (!call || !screenStreamRef.current) return;
    try {
      const cameraTrack = cameraVideoTrackRef.current;
      const sender = findVideoSender();
      if (sender && cameraTrack && !isVideoOff) {
        const trackToUse = applyBeautyMode(beautyMode, cameraTrack);
        await sender.replaceTrack(trackToUse);
        tuneVideoSender(sender, videoQuality);
      } else if (sender && isVideoOff) {
        await sender.replaceTrack(null);
      }
    } catch (err) {
      console.error("Could not restore camera after screen share", err);
    } finally {
      stopTracks(screenStreamRef.current);
      screenStreamRef.current = null;
      setScreenStream(null);
      
      const originalCamTrack = cameraVideoTrackRef.current;
      const previewTrack = beautyMode !== "off" && originalCamTrack && !isVideoOff
        ? applyBeautyMode(beautyMode, originalCamTrack)
        : originalCamTrack;
      const audioTracks = localStreamRef.current?.getAudioTracks() || [];
      buildPreviewStream(previewTrack || originalCamTrack, audioTracks);
      setIsScreenSharing(false);
      emitForActiveCall("call:screen-share-stopped");
      emitMediaState(isMuted, isVideoOff, false);
    }
  };

  const switchCamera = async () => {
    if (!activeCall?.isVideo && activeGroupRoom?.type !== "video") return;
    const nextFacing: CameraFacingMode =
      cameraFacingMode === "user" ? "environment" : "user";

    try {
      let nextStream: MediaStream;
      try {
        nextStream = await requestUserMediaWithDiagnostics({
          video: withFacingMode(getVideoConstraints(videoQuality), nextFacing, true),
          audio: false,
        });
      } catch {
        nextStream = await requestUserMediaWithDiagnostics({
          video: withFacingMode(getVideoConstraints(videoQuality), nextFacing),
          audio: false,
        });
      }
      const nextTrack = nextStream.getVideoTracks()[0];
      if (!nextTrack) {
        stopTracks(nextStream);
        return;
      }

      setCameraFacingMode(nextFacing);
      setSelectedVideoDeviceId("");
      await replaceLocalVideoTrack(nextTrack);
      void refreshVideoInputs();
    } catch (err) {
      console.error("Could not switch camera", err);
      setHasError(
        err instanceof Error
          ? describeMediaAccessError(err, nextFacing === "environment" ? "Could not switch to rear camera." : "Could not switch to front camera.")
          : nextFacing === "environment"
            ? "Could not switch to rear camera."
            : "Could not switch to front camera.",
      );
      window.setTimeout(() => setHasError(""), 3000);
    }
  };

  const switchCameraConfig = async (newQuality: "auto" | "720p" | "1080p" | "2k") => {
      try {
          const { stream: newStream, quality: obtainedQuality } = await getCameraStreamWithFallback(true, newQuality);
          const newCameraTrack = newStream.getVideoTracks()[0];
          if (!newCameraTrack) return;
          
          setVideoQuality(obtainedQuality);
          await replaceLocalVideoTrack(newCameraTrack, obtainedQuality);
          emitMediaState(isMuted, isVideoOff, isScreenSharing, obtainedQuality, beautyMode);
      } catch(err) {
          console.error(err);
          setHasError("Could not change video quality");
          setTimeout(() => setHasError(""), 3000);
      }
  };

  const changeBeautyMode = (newMode: BeautyMode) => {
      setBeautyMode(newMode);
      stopBeauty();
      emitMediaState(isMuted, isVideoOff, isScreenSharing, videoQuality, newMode);
      emitGroupMediaState(isMuted, isVideoOff, isScreenSharing);
  };

  const toggleFullscreen = async () => {
    try {
      if (!document.fullscreenElement) {
        await containerRef.current?.requestFullscreen();
      } else {
        await document.exitFullscreen();
      }
    } catch (err) {
      console.error("Fullscreen failed", err);
    }
  };

  const togglePiP = async () => {
    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
      } else if (remoteVideoRef.current && document.pictureInPictureEnabled && !remoteVideoRef.current.disablePictureInPicture) {
        await remoteVideoRef.current.requestPictureInPicture();
      }
    } catch (err) {
      console.error(err);
      setHasError("Picture-in-Picture is not supported or was blocked");
      setTimeout(() => setHasError(""), 3000);
    }
  };

  const minimizeCall = async () => {
    try {
      if (document.fullscreenElement) {
        await document.exitFullscreen();
      }
    } catch (err) {
      console.error("Could not exit fullscreen before minimizing", err);
    }
    setIsMinimized(true);
  };

  const joinGroupRoom = async () => {
    if (!activeGroupRoom || !socket) return;
    if (activeGroupRoom.participantIds.length >= activeGroupRoom.maxParticipants) {
      setHasError("This group call is full.");
      window.setTimeout(() => setHasError(""), 3000);
      return;
    }
    try {
      const res = await fetch(
        `${CALL_API_BASE}/calls/group/${activeGroupRoom.id}/join`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
          body: JSON.stringify({ userId: currentUser.id }),
        },
      );
      if (!res.ok) {
        const error = await res.json().catch(() => ({}));
        throw new Error(error.error || "Could not join group call");
      }
      const room = (await res.json()) as CallRoom;
      setActiveGroupRoom(room);
      setCallStatus("connected");

      const { stream: obtainedStream, quality: obtainedQuality } =
        await getCameraStreamWithFallback(room.type === "video", videoQuality);
      const cameraTrack = obtainedStream.getVideoTracks()[0] || null;
      localStreamRef.current = obtainedStream;
      cameraVideoTrackRef.current = cameraTrack;
      setVideoQuality(obtainedQuality);
      buildPreviewStream(applyBeautyMode(beautyMode, cameraTrack) || cameraTrack, obtainedStream.getAudioTracks());
      void refreshVideoInputs();
      void connectGroupMesh(room);
    } catch (err) {
      console.error(err);
      setHasError(err instanceof Error ? describeMediaAccessError(err, err.message) : describeMediaAccessError(err));
      window.setTimeout(() => setHasError(""), 4000);
    }
  };

  const leaveGroupRoom = async (ended = false) => {
    const room = activeGroupRoomRef.current;
    if (!room) return;
    try {
      await fetch(`${CALL_API_BASE}/calls/group/${room.id}/leave`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...cloudAuthHeaders() },
        body: JSON.stringify({ userId: currentUser.id, ended }),
      });
    } catch (err) {
      console.error(err);
    } finally {
      resetState();
    }
  };

  const emitGroupMediaState = (
    audioMuted = isMuted,
    videoOff = isVideoOff,
    screenSharing = isScreenSharing,
  ) => {
    const room = activeGroupRoomRef.current;
    if (!room || !socket) return;
    const mediaState: CallMediaState = {
      audioMuted,
      videoOff,
      screenSharing,
      quality: videoQuality,
      beautyMode,
    };
    setGroupParticipantStates((prev) => ({ ...prev, [currentUser.id]: mediaState }));
    socket.emit("call:participant-state", {
      roomId: room.id,
      fromUserId: currentUser.id,
      userId: currentUser.id,
      mediaState,
    });
  };

  const toggleCallRecording = () => {
    if (!featureSupport.recording) {
      setHasError("Local recording is not supported by this browser.");
      window.setTimeout(() => setHasError(""), 3000);
      return;
    }
    if (isCallRecording) {
      stopCallRecording();
      return;
    }

    const tracks = [
      ...(remoteStreamRef.current?.getTracks() || []),
      ...(localStreamRef.current?.getAudioTracks() || []),
      ...(activeGroupRoomRef.current ? localStreamRef.current?.getVideoTracks() || [] : []),
    ];
    if (!tracks.length) {
      setHasError("No media tracks available to record yet.");
      window.setTimeout(() => setHasError(""), 3000);
      return;
    }

    try {
      const recorder = new MediaRecorder(new MediaStream(tracks));
      callRecordingChunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) callRecordingChunksRef.current.push(event.data);
      };
      recorder.onstop = () => {
        const blob = new Blob(callRecordingChunksRef.current, {
          type: recorder.mimeType || "video/webm",
        });
        if (!blob.size) return;
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = `glasschat_call_${Date.now()}.webm`;
        anchor.click();
        URL.revokeObjectURL(url);
      };
      recorder.start(1000);
      callRecorderRef.current = recorder;
      setIsCallRecording(true);
    } catch (err) {
      console.error(err);
      setHasError("Could not start local recording.");
      window.setTimeout(() => setHasError(""), 3000);
    }
  };

  const takeSnapshot = () => {
    const source =
      remoteVideoRef.current?.videoWidth ? remoteVideoRef.current : localVideoRef.current;
    if (!source?.videoWidth || !source.videoHeight) {
      setHasError("No video frame is available for snapshot.");
      window.setTimeout(() => setHasError(""), 3000);
      return;
    }
    const canvas = document.createElement("canvas");
    canvas.width = source.videoWidth;
    canvas.height = source.videoHeight;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.drawImage(source, 0, 0, canvas.width, canvas.height);
    canvas.toBlob((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `glasschat_snapshot_${Date.now()}.png`;
      anchor.click();
      URL.revokeObjectURL(url);
    }, "image/png");
  };

  const toggleCaptions = () => {
    if (!featureSupport.captions) {
      setHasError("Live captions are not supported by this browser.");
      window.setTimeout(() => setHasError(""), 3000);
      return;
    }
    if (captionsEnabled) {
      stopCaptions();
      return;
    }
    const speechWindow = window as typeof window & {
      SpeechRecognition?: any;
      webkitSpeechRecognition?: any;
    };
    const Recognition =
      speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition;
    try {
      const recognition = new Recognition();
      recognition.continuous = true;
      recognition.interimResults = true;
      recognition.onresult = (event: any) => {
        const text = Array.from(event.results)
          .slice(-2)
          .map((result: any) => result[0]?.transcript || "")
          .join(" ")
          .trim();
        if (text) setCaptionText(text);
      };
      recognition.onerror = () => {
        setCaptionsEnabled(false);
      };
      recognition.onend = () => {
        if (captionRecognitionRef.current) {
          try {
            captionRecognitionRef.current.start();
          } catch {
            setCaptionsEnabled(false);
          }
        }
      };
      captionRecognitionRef.current = recognition;
      recognition.start();
      setCaptionsEnabled(true);
    } catch (err) {
      console.error(err);
      setHasError("Could not start live captions.");
      window.setTimeout(() => setHasError(""), 3000);
    }
  };

  const displayCall = activeCall || incomingCall;
  const isCaller = displayCall?.callerId === currentUser.id;
  const hasIncomingOffer = incomingCall ? !!getIncomingOffer(incomingCall) : false;
  const otherName = useMemo(() => {
    if (!displayCall) return "Call";
    return isCaller
      ? displayCall.calleeName || "Call"
      : displayCall.callerName || "Call";
  }, [displayCall, isCaller]);
  const otherAvatar = isCaller ? displayCall?.calleeAvatar : displayCall?.callerAvatar;
  const statusLabel =
    callStatus === "outgoing_calling"
      ? "Calling..."
      : callStatus === "trying_to_reach"
        ? "Trying to reach user..."
      : callStatus === "outgoing_ringing" || callStatus === "incoming_ringing"
        ? "Ringing..."
        : callStatus === "connecting"
          ? "Connecting..."
          : callStatus === "connected"
            ? formatDuration(callDuration)
            : callStatus === "reconnecting"
              ? "Reconnecting..."
              : callStatus === "busy"
                ? "User busy"
                : callStatus === "missed"
                  ? "No answer"
                  : callStatus === "declined"
                    ? "Call declined"
                    : callStatus === "unavailable"
                      ? "User unavailable"
                      : callStatus === "failed"
                        ? "Call failed"
                        : callStatus === "cancelled"
                          ? "Call cancelled"
                          : callStatus === "ended"
                            ? "Call ended"
                            : "";
  const callDebugText = useMemo(() => {
    const iceServers = callDebug.iceServers || [];
    const acked = (event: string) => callDebug.ackReceivedEvents?.includes(event) ? "yes" : "no";
    const startRemoteResponse = callDebug.acceptedReceived
      ? "accepted"
      : callDebug.events.some((line) => line.includes("call:ringing"))
        ? "ringing"
        : "none";
    const offerRemoteResponse = callDebug.answerReceived
      ? "answer_received"
      : callDebug.acceptedReceived
        ? "accepted_waiting_answer"
        : "none";
    const lines = [
      `Call ID: ${callDebug.callId || "unknown"}`,
      `Direction: ${callDebug.direction || "unknown"}`,
      `Local user: ${callDebug.localUserId || currentUser.id}`,
      `Remote user: ${callDebug.remoteUserId || "unknown"}`,
      `Call type: ${callDebug.type || "unknown"}`,
      `call:start: sent=${callDebug.startSent ? "yes" : "no"}, ack=${acked("call:start")}, received=${callDebug.startReceived ? "yes" : "no"}, remote_response=${startRemoteResponse}`,
      `call:offer: sent=${callDebug.offerSent ? "yes" : "no"}, ack=${acked("call:offer")}, received=${callDebug.offerReceived ? "yes" : "no"}, remote_response=${offerRemoteResponse}`,
      `call:accepted: sent=${callDebug.acceptedSent ? "yes" : "no"}, ack=${acked("call:accepted")}, received=${callDebug.acceptedReceived ? "yes" : "no"}`,
      `call:answer: sent=${callDebug.answerSent ? "yes" : "no"}, ack=${acked("call:answer")}, received=${callDebug.answerReceived ? "yes" : "no"}, setRemoteDescription=${callDebug.remoteDescriptionSet ? "success" : "not set"}`,
      `ICE: sent=${callDebug.iceSentCount}, received=${callDebug.iceReceivedCount}, selectedPair=${callDebug.selectedCandidatePairExists ? "yes" : "no"}`,
      `Local candidate types generated: ${(callDebug.localCandidateTypes || []).join(", ") || "none"}`,
      `Remote candidate types received: ${(callDebug.remoteCandidateTypes || []).join(", ") || "none"}`,
      `Relay candidate generated: ${callDebug.relayCandidateGenerated ? "yes" : "no"}`,
      `Relay candidate received: ${callDebug.relayCandidateReceived ? "yes" : "no"}`,
      `Selected candidate pair: ${callDebug.selectedCandidatePair || "No working ICE candidate pair selected."}`,
      `currentRoundTripTime: ${callDebug.currentRoundTripTime ?? "unknown"}`,
      `availableOutgoingBitrate: ${callDebug.availableOutgoingBitrate ?? "unknown"}`,
      `packetsSent: ${callDebug.packetsSent ?? "unknown"}`,
      `packetsReceived: ${callDebug.packetsReceived ?? "unknown"}`,
      `Force relay mode: ${FORCE_RELAY ? "yes" : "no"}`,
      `setLocalDescription status: ${callDebug.localDescriptionSet ? "success" : "not set"}`,
      `setRemoteDescription status: ${callDebug.remoteDescriptionSet ? "success" : "not set"}`,
      `WebRTC signalingState: ${callDebug.signalingState || "unknown"}`,
      `WebRTC iceGatheringState: ${callDebug.iceGatheringState || "unknown"}`,
      `WebRTC iceConnectionState: ${callDebug.iceConnectionState || "unknown"}`,
      `WebRTC connectionState: ${callDebug.connectionState || "unknown"}`,
      `Last error: ${callDebug.lastError || "none"}`,
      `TURN configured: ${callDebug.turnConfigured ? "yes" : "no"}`,
      `ICE config used: ${iceServers.length ? JSON.stringify(iceServers) : "default Google STUN"}`,
      "",
      "Events:",
      ...callDebug.events,
    ];
    return lines.join("\n");
  }, [callDebug, currentUser.id]);

  const renderAvatar = (sizeClass: string) => (
    <div className={cn("rounded-full bg-slate-800 flex items-center justify-center overflow-hidden border border-slate-700 shadow-xl", sizeClass)}>
      {otherAvatar ? (
        <img src={otherAvatar} alt={otherName} className="w-full h-full object-cover" />
      ) : (
        <span className="text-white font-bold">{otherName.charAt(0).toUpperCase()}</span>
      )}
    </div>
  );

  if (!incomingCall && !activeCall && !activeGroupRoom && !hasError && !(CALL_DEBUG_UI && showCallDebug)) return null;

  if (activeCall && isMinimized) {
    const showMiniVideo = activeCall.isVideo && remoteStream?.getVideoTracks()[0]?.enabled;
    return (
      <AnimatePresence>
        {hasError && (
          <motion.div 
            initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }}
            className="fixed top-4 left-1/2 -translate-x-1/2 w-11/12 max-w-md bg-amber-100 border border-amber-300 text-amber-900 px-4 py-3 rounded-lg shadow-2xl z-50">
            <p className="font-bold text-sm">Call notice</p>
            <p className="text-xs mt-1">{hasError}</p>
            {CALL_DEBUG_UI && (
              <button
                onClick={() => setShowCallDebug(true)}
                className="mt-2 text-xs font-semibold underline"
              >
                Show Call Debug
              </button>
            )}
          </motion.div>
        )}
        {CALL_DEBUG_UI && (
          <button
            onClick={() => setShowCallDebug(true)}
            className="fixed top-4 right-4 z-50 rounded-full border border-slate-700 bg-slate-950/90 px-3 py-1.5 text-xs font-semibold text-slate-100 shadow-lg"
          >
            Debug
          </button>
        )}
        {CALL_DEBUG_UI && showCallDebug && (
          <CallDebugPanel
            debugText={callDebugText}
            onClose={() => setShowCallDebug(false)}
          />
        )}
        <motion.div
          drag
          dragMomentum={false}
          whileDrag={{ scale: 1.05 }}
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.8 }}
          role="button"
          tabIndex={0}
          onClick={() => setIsMinimized(false)}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") setIsMinimized(false);
          }}
          style={{ position: 'fixed', zIndex: 40, bottom: 20, right: 20 }}
          className={cn(
            "bg-slate-950/95 text-white shadow-2xl border border-slate-700 backdrop-blur-md cursor-pointer",
            activeCall.isVideo
              ? "w-48 h-32 sm:w-64 sm:h-40 rounded-xl overflow-hidden"
              : "w-80 rounded-full px-4 py-3",
          )}
        >
          {activeCall.isVideo ? (
            <>
              {showMiniVideo ? (
                <video
                  ref={minimizedRemoteVideoRef}
                  autoPlay
                  playsInline
                  className="absolute inset-0 w-full h-full object-cover"
                />
              ) : (
                <div className="absolute inset-0 flex items-center justify-center bg-slate-900">
                  {renderAvatar("w-16 h-16 text-2xl")}
                </div>
              )}
              {remoteQuality && remoteQuality !== "auto" && showMiniVideo && (
                 <div className="absolute top-2 right-2 px-1.5 py-0.5 bg-black/60 rounded text-[10px] uppercase font-bold tracking-wider">{remoteQuality}</div>
              )}
              <div className="absolute inset-x-0 bottom-0 p-2 bg-gradient-to-t from-black/80 to-transparent">
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold">{otherName}</p>
                    <p className="text-[10px] text-emerald-300 font-mono flex items-center gap-1">
                      {statusLabel}
                      {remoteScreenSharing && <MonitorUp className="w-3 h-3 text-sky-400" />}
                    </p>
                  </div>
                  <button
                    onClick={(event) => {
                      event.stopPropagation();
                      endActiveCall();
                    }}
                    className="w-9 h-9 rounded-full bg-red-500 hover:bg-red-600 transition-colors flex items-center justify-center shrink-0"
                    title="End call"
                  >
                    <PhoneOff className="w-4 h-4 text-white" />
                  </button>
                </div>
              </div>
            </>
          ) : (
            <div className="flex items-center gap-3">
              {renderAvatar("w-11 h-11 text-lg shrink-0")}
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold">{otherName}</p>
                <p className="text-xs text-emerald-300 font-mono">{statusLabel}</p>
              </div>
              {isMuted && <MicOff className="w-4 h-4 text-amber-300 shrink-0" />}
              <button
                onClick={(event) => {
                  event.stopPropagation();
                  endActiveCall();
                }}
                className="w-10 h-10 rounded-full bg-red-500 hover:bg-red-600 transition-colors flex items-center justify-center shrink-0"
                title="End call"
              >
                <PhoneOff className="w-5 h-5 text-white" />
              </button>
            </div>
          )}
        </motion.div>
      </AnimatePresence>
    );
  }

  return (
    <AnimatePresence>
    <motion.div 
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      className="hello-app-ambient fixed inset-0 z-50 flex items-center justify-center bg-[var(--hello-bg)] p-4">
      {CALL_DEBUG_UI && (
        <button
          onClick={() => setShowCallDebug(true)}
          className="absolute right-4 top-4 z-[60] rounded-full border border-slate-700 bg-slate-950/90 px-3 py-1.5 text-xs font-semibold text-slate-100 shadow-lg"
        >
          Debug
        </button>
      )}
      {CALL_DEBUG_UI && showCallDebug && (
        <CallDebugPanel
          debugText={callDebugText}
          onClose={() => setShowCallDebug(false)}
        />
      )}
      {hasError && (
        <motion.div initial={{ y: -50, opacity: 0 }} animate={{ y: 0, opacity: 1 }} className="absolute top-4 left-1/2 -translate-x-1/2 w-11/12 max-w-md bg-amber-100 border border-amber-300 text-amber-900 px-4 py-3 rounded-lg shadow-2xl z-50">
          <p className="font-bold text-sm">Call notice</p>
          <p className="text-xs mt-1">{hasError}</p>
          {CALL_DEBUG_UI && (
            <button
              onClick={() => setShowCallDebug(true)}
              className="mt-2 text-xs font-semibold underline"
            >
              Show Call Debug
            </button>
          )}
        </motion.div>
      )}

      {incomingCall && !activeCall && (
        <motion.div 
          initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.9, opacity: 0 }}
          className="hello-panel-strong mx-4 flex w-[320px] flex-col items-center rounded-[var(--hello-radius-xl)] p-8 shadow-2xl">
          <div className="relative mb-6 flex h-24 w-24 items-center justify-center overflow-hidden rounded-full bg-[var(--hello-accent-soft)] text-4xl shadow-inner">
            <span className="absolute inset-0 rounded-full animate-ping bg-[var(--hello-accent)] opacity-20" />
            {otherAvatar ? (
              <img src={otherAvatar} alt={otherName} className="w-full h-full object-cover relative z-10" />
            ) : (
              <span className="relative z-10 font-bold text-[var(--hello-accent)]">{otherName.charAt(0).toUpperCase()}</span>
            )}
          </div>
          <h2 className="mb-2 text-xl font-bold text-[var(--hello-text)]">
            Incoming {incomingCall.isVideo ? "Video" : "Audio"} Call
          </h2>
          <p className="mb-2 text-[var(--hello-text-muted)]">{otherName}</p>
          <p className="mb-8 text-sm font-mono text-[var(--hello-accent)]">
            {hasIncomingOffer ? statusLabel : "Preparing call..."}
          </p>

          <div className="flex space-x-6">
            <button onClick={declineCall} className="w-14 h-14 rounded-full bg-red-500 flex items-center justify-center text-white hover:bg-red-600 hover:scale-105 transition-all shadow-lg hover:shadow-red-500/50">
              <PhoneOff className="w-6 h-6" />
            </button>
            <button
              onClick={acceptCall}
              title={hasIncomingOffer ? "Accept call" : "Accept and wait for caller media"}
              className={cn(
                "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg",
                hasIncomingOffer
                  ? "bg-emerald-500 hover:bg-emerald-600 hover:scale-105 hover:shadow-emerald-500/50 animate-pulse"
                  : "bg-emerald-600 hover:bg-emerald-700 hover:scale-105 opacity-90",
              )}
            >
              {incomingCall.isVideo ? <VideoIcon className="w-6 h-6" /> : <Phone className="w-6 h-6" />}
            </button>
          </div>
        </motion.div>
      )}

      {activeGroupRoom && !activeCall && !incomingCall && (
        <motion.div
          initial={{ scale: 0.96, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          exit={{ scale: 0.96, opacity: 0 }}
          className="w-full h-full md:w-[88%] md:h-[90%] md:max-w-6xl md:rounded-2xl bg-slate-950 flex flex-col shadow-2xl border border-slate-800 overflow-hidden"
        >
          <div className="px-4 sm:px-6 py-4 border-b border-slate-800 flex items-center justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2 text-slate-400 text-xs uppercase tracking-[0.2em]">
                <Users className="w-4 h-4" />
                Mesh group call
              </div>
              <h2 className="text-white text-xl sm:text-2xl font-bold truncate">
                {activeGroupRoom.type === "video" ? "Video" : "Audio"} room
              </h2>
            </div>
            <div className="flex items-center gap-2 text-xs">
              <span className="px-3 py-1.5 rounded-full bg-emerald-500/10 text-emerald-300 border border-emerald-500/30">
                {activeGroupRoom.participantIds.length}/{activeGroupRoom.maxParticipants}
              </span>
              <span className="px-3 py-1.5 rounded-full bg-slate-900 text-slate-300 border border-slate-700 capitalize">
                {callStatus === "incoming_ringing" ? "Invite" : activeGroupRoom.status}
              </span>
            </div>
          </div>

          <div className="flex-1 p-4 sm:p-6 grid grid-cols-1 sm:grid-cols-2 gap-4 overflow-y-auto">
            {activeGroupRoom.participantIds.map((participantId) => {
              const mediaState = groupParticipantStates[participantId];
              const isSelf = participantId === currentUser.id;
              const participantStream = groupRemoteStreams[participantId];
              const hasParticipantVideo =
                !!participantStream?.getVideoTracks().some((track) => track.enabled);
              return (
                <div
                  key={participantId}
                  className="relative min-h-[220px] rounded-2xl bg-slate-900 border border-slate-800 overflow-hidden flex items-center justify-center"
                >
                  {isSelf && activeGroupRoom.type === "video" && localStream && !isVideoOff ? (
                    <video
                      ref={localVideoRef}
                      autoPlay
                      playsInline
                      muted
                      style={{ transform: "scaleX(-1)", filter: getBeautyCssFilter(beautyMode) }}
                      className="absolute inset-0 w-full h-full object-cover"
                    />
                  ) : !isSelf && activeGroupRoom.type === "video" && hasParticipantVideo ? (
                    <video
                      ref={(node) => {
                        if (node && node.srcObject !== participantStream) {
                          node.srcObject = participantStream;
                        }
                      }}
                      autoPlay
                      playsInline
                      className="absolute inset-0 w-full h-full object-cover"
                    />
                  ) : (
                    <div className="flex flex-col items-center text-center">
                      <div className="w-20 h-20 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-white text-2xl font-bold mb-3">
                        {isSelf ? currentUser.name.charAt(0).toUpperCase() : participantId.slice(0, 2).toUpperCase()}
                      </div>
                      <p className="text-white font-semibold">
                        {isSelf ? "You" : `Participant ${participantId.slice(0, 6)}`}
                      </p>
                      <p className="text-xs text-slate-500 mt-1">
                        {mediaState?.videoOff ? "Camera off" : "Waiting for media"}
                      </p>
                    </div>
                  )}
                  {!isSelf && participantStream && (
                    <audio
                      ref={(node) => {
                        if (node && node.srcObject !== participantStream) {
                          node.srcObject = participantStream;
                        }
                      }}
                      autoPlay
                    />
                  )}

                  <div className="absolute left-3 bottom-3 flex items-center gap-2">
                    <span className="px-2 py-1 rounded-full bg-black/70 text-white text-xs">
                      {isSelf ? "You" : participantId.slice(0, 8)}
                    </span>
                    {mediaState?.audioMuted && (
                      <span className="w-8 h-8 rounded-full bg-amber-500/90 flex items-center justify-center">
                        <MicOff className="w-4 h-4 text-white" />
                      </span>
                    )}
                    {mediaState?.videoOff && activeGroupRoom.type === "video" && (
                      <span className="w-8 h-8 rounded-full bg-slate-700 flex items-center justify-center">
                        <VideoOff className="w-4 h-4 text-white" />
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          <div className="p-4 sm:p-6 border-t border-slate-800 flex flex-wrap justify-center gap-3 bg-slate-950">
            {callStatus === "incoming_ringing" && (
              <button
                onClick={() => void joinGroupRoom()}
                className="h-14 px-6 rounded-full bg-emerald-500 hover:bg-emerald-600 text-white font-bold flex items-center gap-2"
              >
                <Phone className="w-5 h-5" />
                Join
              </button>
            )}
            <button
              onClick={toggleMute}
              className={cn(
                "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all",
                isMuted ? "bg-slate-200 text-slate-950" : "bg-slate-800 hover:bg-slate-700",
              )}
            >
              {isMuted ? <MicOff className="w-6 h-6" /> : <Mic className="w-6 h-6" />}
            </button>
            {activeGroupRoom.type === "video" && (
              <button
                onClick={toggleVideo}
                className={cn(
                  "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all",
                  isVideoOff ? "bg-slate-200 text-slate-950" : "bg-slate-800 hover:bg-slate-700",
                )}
              >
                {isVideoOff ? <VideoOff className="w-6 h-6" /> : <VideoIcon className="w-6 h-6" />}
              </button>
            )}
            <button
              onClick={toggleCallRecording}
              className={cn(
                "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all",
                isCallRecording ? "bg-red-500" : "bg-slate-800 hover:bg-slate-700",
              )}
              title={featureSupport.recording ? "Local recording" : "Recording unsupported"}
            >
              <Radio className="w-6 h-6" />
            </button>
            {activeGroupRoom.type === "video" && (
              <button
                onClick={takeSnapshot}
                className="w-14 h-14 rounded-full flex items-center justify-center text-white transition-all bg-slate-800 hover:bg-slate-700"
                title="Snapshot"
              >
                <ImageIcon className="w-6 h-6" />
              </button>
            )}
            <button
              onClick={() => void leaveGroupRoom(activeGroupRoom.hostId === currentUser.id)}
              className="w-16 h-16 rounded-full bg-red-500 flex items-center justify-center text-white hover:bg-red-600"
              title="Leave group call"
            >
              <PhoneOff className="w-7 h-7" />
            </button>
          </div>
        </motion.div>
      )}

      {activeCall && (
        <motion.div
          initial={{ scale: 0.95, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.95, opacity: 0 }} transition={{ duration: 0.2 }}
          ref={containerRef}
          className="w-full h-full md:w-[85%] md:h-[90%] md:max-w-6xl md:rounded-2xl bg-black flex flex-col items-center shadow-[0_0_60px_rgba(0,0,0,0.6)] border border-slate-700 relative overflow-hidden"
        >
          <div className="absolute top-4 left-4 z-40 flex items-center gap-2">
            <button
              onClick={() => void minimizeCall()}
              className="w-11 h-11 rounded-full bg-slate-900/80 border border-slate-700 text-white flex items-center justify-center hover:bg-slate-800 transition-colors backdrop-blur-md"
              title="Minimize call"
            >
              <Minimize2 className="w-5 h-5" />
            </button>
            <button
              onClick={toggleFullscreen}
              className="w-11 h-11 rounded-full bg-slate-900/80 border border-slate-700 text-white flex items-center justify-center hover:bg-slate-800 transition-colors backdrop-blur-md"
              title={isFullscreen ? "Exit fullscreen" : "Fullscreen"}
            >
              <Maximize2 className="w-5 h-5" />
            </button>
            {activeCall.isVideo && (
                <button
                  onClick={togglePiP}
                  className="w-11 h-11 rounded-full bg-slate-900/80 border border-slate-700 text-white flex items-center justify-center hover:bg-slate-800 transition-colors backdrop-blur-md"
                  title="Picture-in-Picture"
                >
                  <PictureInPicture className="w-5 h-5" />
                </button>
            )}
            <button
              onClick={() => setShowStatsBadge((prev) => !prev)}
              className={cn(
                "w-11 h-11 rounded-full border text-white flex items-center justify-center transition-colors backdrop-blur-md",
                showStatsBadge
                  ? "bg-emerald-500/90 border-emerald-300"
                  : "bg-slate-900/80 border-slate-700 hover:bg-slate-800",
              )}
              title={showStatsBadge ? "Hide call quality" : "Show call quality"}
            >
              <Gauge className="w-5 h-5" />
            </button>
          </div>
          
          <div className="absolute top-4 right-4 z-40 flex flex-col items-end gap-2 text-xs font-mono">
            {callStatus === "connected" && showStatsBadge && (
              <span
                className={cn(
                  "px-2 py-1 rounded uppercase tracking-widest backdrop-blur-md border flex items-center gap-1",
                  callQuality.label === "poor"
                    ? "bg-red-500/85 text-white border-red-300/40"
                    : callQuality.label === "fair"
                      ? "bg-amber-500/85 text-slate-950 border-amber-200/50"
                      : "bg-emerald-500/80 text-white border-emerald-300/40",
                )}
              >
                <Gauge className="w-3 h-3" />
                {callQuality.label}
                {typeof callQuality.rttMs === "number" ? ` ${callQuality.rttMs}ms` : ""}
              </span>
            )}
            {remoteQuality && remoteQuality !== "auto" && activeCall.isVideo && (
                 <span className="px-2 py-1 bg-black/60 text-white rounded uppercase tracking-widest backdrop-blur-md border border-slate-700/50">{remoteQuality}</span>
            )}
            {remoteBeautyMode && remoteBeautyMode !== "off" && activeCall.isVideo && (
                 <span className="px-2 py-1 bg-pink-500/80 text-white rounded flex items-center gap-1 backdrop-blur-md"><Sparkles className="w-3 h-3"/> Filter ON</span>
            )}
            {remoteScreenSharing && (
               <span className="px-2 py-1 bg-emerald-500/90 text-white rounded shadow-lg backdrop-blur-md flex items-center gap-1">
                 <MonitorUp className="w-3 h-3" /> {otherName} sharing screen
               </span>
            )}
            {isScreenSharing && (
               <span className="px-2 py-1 bg-sky-500/90 text-white rounded shadow-lg backdrop-blur-md flex items-center gap-1">
                 <MonitorUp className="w-3 h-3" /> You are sharing screen
               </span>
            )}
            {isCallRecording && (
              <span className="px-2 py-1 bg-red-500/90 text-white rounded shadow-lg backdrop-blur-md flex items-center gap-1">
                <Radio className="w-3 h-3" /> Recording locally
              </span>
            )}
          </div>

          <div 
            className={cn(
               "absolute transition-all duration-300",
               isSwapped ? "z-30 w-32 h-48 sm:w-48 sm:h-64 bg-slate-800 rounded-xl overflow-hidden shadow-2xl border border-slate-700 cursor-pointer bottom-[100px] right-[20px]" : "inset-0 flex items-center justify-center bg-slate-900/50 w-full h-full z-10"
            )}
            onClick={isSwapped ? toggleSwap : undefined}
          >
            <div 
               className="absolute inset-0 opacity-30 z-0 bg-cover bg-center" 
               style={{ 
                   backgroundImage: otherAvatar
                     ? `linear-gradient(rgba(15,23,42,0.82), rgba(15,23,42,0.82)), url(${otherAvatar})`
                     : "linear-gradient(135deg, #020617, #111827)",
               }} 
            />
            <video
              ref={remoteVideoRef}
              autoPlay
              playsInline
              onDoubleClick={toggleFullscreen}
              style={{
                filter: getBeautyCssFilter(remoteBeautyMode),
              }}
              className={cn(
                "w-full h-full cursor-pointer relative z-10",
                isSwapped ? "object-cover" : "object-contain bg-black",
                (!activeCall.isVideo || !remoteStream?.getVideoTracks()[0]?.enabled) && "hidden",
              )}
            />
            {(!activeCall.isVideo || !remoteStream?.getVideoTracks()[0]?.enabled) && (
              <div className="flex flex-col items-center px-4 sm:px-6 text-center z-10 p-2 relative h-full justify-center">
                <div className={cn("rounded-full bg-slate-800 border-4 border-slate-700 flex items-center justify-center text-white shadow-2xl relative overflow-hidden", isSwapped ? "w-16 h-16 text-2xl mb-2" : "w-32 h-32 text-5xl mb-6")}>
                  <div className="absolute inset-0 rounded-full bg-indigo-500/20 blur-xl animate-pulse" />
                  {otherAvatar ? (
                    <img src={otherAvatar} alt={otherName} className="w-full h-full object-cover relative z-10" />
                  ) : (
                    <span className="relative z-10">{otherName.charAt(0).toUpperCase()}</span>
                  )}
                </div>
                {!isSwapped && (
                  <>
                    <h2 className="text-white text-3xl font-bold mb-2">{otherName}</h2>
                    <p className="text-emerald-400 font-mono tracking-widest flex items-center justify-center gap-2">
                      {statusLabel}
                    </p>
                  </>
                )}
                {activeCall.isVideo && !remoteStream?.getVideoTracks()[0]?.enabled && callStatus === "connected" && !isSwapped && (
                    <p className="mt-4 text-slate-400 text-sm flex items-center justify-center gap-2 relative z-20 bg-black/40 px-3 py-1.5 rounded-full border border-slate-700/50">
                        <VideoOff className="w-4 h-4 text-amber-400" /> {otherName} turned off their camera
                    </p>
                )}
              </div>
            )}
          </div>

          {activeCall.isVideo && (
            <motion.div 
               drag={!isSwapped}
               dragConstraints={containerRef}
               dragElastic={0}
               dragMomentum={false}
               animate={isSwapped ? { x: 0, y: 0 } : undefined}
               className={cn(
                 "absolute transition-all duration-300 overflow-hidden shadow-2xl border border-slate-700 bg-slate-800",
                 !isSwapped ? "z-30 w-32 h-48 sm:w-48 sm:h-64 rounded-xl cursor-move bottom-[100px] right-[20px]" : "inset-0 flex items-center justify-center w-full h-full z-10 rounded-none border-0"
               )}
               style={!isSwapped ? { bottom: "100px", right: "20px" } : { bottom: 0, right: 0 }}
               onClick={!isSwapped ? toggleSwap : undefined}
            >
              <video
                ref={localVideoRef}
                autoPlay
                playsInline
                muted
                style={{
                  transform: isScreenSharing ? "none" : "scaleX(-1)",
                  filter: isScreenSharing ? "none" : getBeautyCssFilter(beautyMode),
                }}
                className={cn("w-full h-full pointer-events-none", isSwapped ? "object-contain bg-black" : "object-cover bg-slate-900", isVideoOff && !isScreenSharing && "hidden")}
              />
              {isVideoOff && !isScreenSharing && (
                <div className="w-full h-full flex flex-col items-center justify-center text-slate-500 bg-slate-900 pointer-events-none">
                  <VideoOff className={cn("opacity-50", isSwapped ? "w-16 h-16 mb-4" : "w-8 h-8 mb-2")} />
                  <span className={cn("font-mono uppercase", isSwapped ? "text-lg" : "text-[10px]")}>Camera Off</span>
                </div>
              )}
            </motion.div>
          )}

          {captionsEnabled && captionText && (
            <div className="absolute left-1/2 -translate-x-1/2 bottom-28 z-40 max-w-[90%] rounded-2xl bg-black/75 border border-white/10 px-4 py-3 text-center text-white text-sm sm:text-base shadow-2xl">
              {captionText}
            </div>
          )}

          <div className="absolute bottom-0 inset-x-0 p-4 sm:p-6 flex flex-wrap justify-center gap-3 sm:gap-5 bg-gradient-to-t from-slate-900 via-slate-900/80 to-transparent z-40">
            <button
              onClick={toggleMute}
              className={cn(
                "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg",
                isMuted
                  ? "bg-slate-200 text-slate-900"
                  : "bg-slate-800 hover:bg-slate-700 backdrop-blur-md border border-slate-700",
              )}
              title={isMuted ? "Unmute" : "Mute"}
            >
              {isMuted ? <MicOff className="w-6 h-6" /> : <Mic className="w-6 h-6" />}
            </button>

            {activeCall.isVideo && (
              <button
                onClick={toggleVideo}
                className={cn(
                  "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg",
                  isVideoOff
                    ? "bg-slate-200 text-slate-900"
                    : "bg-slate-800 hover:bg-slate-700 backdrop-blur-md border border-slate-700",
                )}
                title={isVideoOff ? "Turn camera on" : "Turn camera off"}
              >
                {isVideoOff ? <VideoOff className="w-6 h-6" /> : <VideoIcon className="w-6 h-6" />}
              </button>
            )}

            {showMoreControls && activeCall.isVideo && featureSupport.screenShare && (
              <button
                onClick={() => {
                  if (isScreenSharing) void stopScreenShare();
                  else void startScreenShare();
                }}
                className={cn(
                  "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg",
                  isScreenSharing
                    ? "bg-sky-500 hover:bg-sky-600"
                    : "bg-slate-800 hover:bg-slate-700 backdrop-blur-md border border-slate-700",
                )}
                title={isScreenSharing ? "Stop screen share" : "Share screen"}
              >
                {isScreenSharing ? <MonitorOff className="w-6 h-6" /> : <MonitorUp className="w-6 h-6" />}
              </button>
            )}

            {showMoreControls && (
            <button
              onClick={toggleCallRecording}
              className={cn(
                "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg border",
                isCallRecording
                  ? "bg-red-500 border-red-400 hover:bg-red-600"
                  : "bg-slate-800 hover:bg-slate-700 backdrop-blur-md border-slate-700",
                !featureSupport.recording && "opacity-50",
              )}
              title={featureSupport.recording ? "Record locally" : "Recording unsupported"}
            >
              {isCallRecording ? <Download className="w-6 h-6" /> : <Radio className="w-6 h-6" />}
            </button>
            )}

            {showMoreControls && (
            <button
              onClick={toggleCaptions}
              className={cn(
                "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg border",
                captionsEnabled
                  ? "bg-cyan-500 border-cyan-300"
                  : "bg-slate-800 hover:bg-slate-700 backdrop-blur-md border-slate-700",
                !featureSupport.captions && "opacity-50",
              )}
              title={featureSupport.captions ? "Browser captions" : "Captions unsupported"}
            >
              <Captions className="w-6 h-6" />
            </button>
            )}

            {showMoreControls && activeCall.isVideo && (
              <button
                onClick={takeSnapshot}
                className="w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg bg-slate-800 hover:bg-slate-700 backdrop-blur-md border border-slate-700"
                title="Save snapshot locally"
              >
                <ImageIcon className="w-6 h-6" />
              </button>
            )}

            {activeCall.isVideo && (
              <button
                onClick={() => void switchCamera()}
                className="w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg bg-slate-800 hover:bg-slate-700 backdrop-blur-md border border-slate-700"
                title={
                  cameraFacingMode === "user"
                    ? "Switch to rear camera"
                    : "Switch to front camera"
                }
              >
                <RotateCcw className="w-6 h-6" />
              </button>
            )}

            {showMoreControls && activeCall.isVideo && (
               <div className="relative">
                 <button
                   onClick={() => setShowAdvanced(!showAdvanced)}
                   className={cn(
                     "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg backdrop-blur-md border border-slate-700",
                     showAdvanced ? "bg-indigo-500" : "bg-slate-800 hover:bg-slate-700"
                   )}
                   title="Video Settings"
                 >
                   <Settings className="w-6 h-6" />
                 </button>
                 
                 {showAdvanced && (
                    <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-4 w-72 bg-slate-800/95 backdrop-blur-md border border-slate-700 rounded-2xl shadow-2xl p-4 flex flex-col gap-4 animate-in slide-in-from-bottom-2 fade-in">
                        <div>
                            <p className="text-xs font-semibold text-slate-400 mb-2 uppercase tracking-wider">Video Quality</p>
                            <div className="flex gap-1 bg-slate-900/50 p-1 rounded-lg">
                                {["auto", "720p", "1080p", "2k"].map(q => (
                                    <button 
                                      key={q}
                                      onClick={() => void switchCameraConfig(q as any)}
                                      className={cn(
                                          "flex-1 text-xs py-1.5 rounded-md font-medium transition-all capitalize",
                                          videoQuality === q ? "bg-indigo-500 text-white shadow-sm" : "text-slate-400 hover:text-slate-200 hover:bg-slate-700/50"
                                      )}
                                    >
                                        {q}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div>
                            <p className="text-xs font-semibold text-slate-400 mb-2 uppercase tracking-wider flex items-center gap-1">
                                <Sparkles className="w-3 h-3" /> Beauty Filter
                            </p>
                            <div className="flex flex-wrap gap-1 bg-slate-900/50 p-1 rounded-lg">
                                {[
                                  ["off", "Off"],
                                  ["warm", "Warm"],
                                  ["cool", "Cool"],
                                  ["bw", "Mono"],
                                ].map(([mode, label]) => (
                                    <button 
                                      key={mode}
                                      onClick={() => changeBeautyMode(mode as BeautyMode)}
                                      className={cn(
                                          "px-3 flex-1 min-w-[70px] text-xs py-1.5 rounded-md font-medium transition-all capitalize",
                                          beautyMode === mode ? "bg-pink-500 text-white shadow-sm" : "text-slate-400 hover:text-slate-200 hover:bg-slate-700/50"
                                      )}
                                    >
                                        {label}
                                    </button>
                                ))}
                            </div>
                            {isScreenSharing && beautyMode !== "off" && (
                                <p className="text-[10px] text-amber-400 mt-2 text-center">Disabled while screen sharing</p>
                            )}
                        </div>
                        <div className="grid grid-cols-1 gap-2">
                            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                                Camera
                                <select
                                  value={selectedVideoDeviceId}
                                  onChange={(event) => setSelectedVideoDeviceId(event.target.value)}
                                  className="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs text-slate-200 outline-none"
                                >
                                  {videoInputs.length ? (
                                    videoInputs.map((device, index) => (
                                      <option key={device.deviceId || index} value={device.deviceId}>
                                        {device.label || `Camera ${index + 1}`}
                                      </option>
                                    ))
                                  ) : (
                                    <option value="">Default camera</option>
                                  )}
                                </select>
                            </label>
                            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                                Microphone
                                <select
                                  value={selectedAudioDeviceId}
                                  onChange={(event) => setSelectedAudioDeviceId(event.target.value)}
                                  className="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2 py-2 text-xs text-slate-200 outline-none"
                                >
                                  {audioInputs.length ? (
                                    audioInputs.map((device, index) => (
                                      <option key={device.deviceId || index} value={device.deviceId}>
                                        {device.label || `Microphone ${index + 1}`}
                                      </option>
                                    ))
                                  ) : (
                                    <option value="">Default microphone</option>
                                  )}
                                </select>
                            </label>
                        </div>
                        {localResolution && (
                             <div className="pt-2 border-t border-slate-700 text-center">
                                 <p className="text-xs text-slate-500 font-mono">Current: <span className="text-slate-300">{localResolution}</span></p>
                             </div>
                        )}
                    </div>
                 )}
               </div>
            )}

            <button
              onClick={() => {
                setShowMoreControls((prev) => !prev);
                setShowAdvanced(false);
              }}
              className={cn(
                "w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg backdrop-blur-md border border-slate-700",
                showMoreControls ? "bg-white text-slate-950" : "bg-slate-800 hover:bg-slate-700",
              )}
              title={showMoreControls ? "Hide tools" : "More tools"}
            >
              <MoreVertical className="w-6 h-6" />
            </button>

            <button
              onClick={endActiveCall}
              className="w-16 h-16 rounded-full bg-red-500 flex items-center justify-center text-white hover:bg-red-600 hover:scale-105 transition-all shadow-lg hover:shadow-red-500/50"
              title="End call"
            >
              <PhoneOff className="w-7 h-7" />
            </button>

            {showMoreControls && activeCall.isVideo && !videoInputs.length && (
              <button
                onClick={() => void refreshVideoInputs()}
                className="w-14 h-14 rounded-full flex items-center justify-center text-white transition-all shadow-lg bg-slate-800 hover:bg-slate-700 backdrop-blur-md border border-slate-700"
                title="Detect cameras"
              >
                <Camera className="w-6 h-6" />
              </button>
            )}
          </div>
        </motion.div>
      )}
    </motion.div>
    </AnimatePresence>
  );
}

function CallDebugPanel({
  debugText,
  onClose,
}: {
  debugText: string;
  onClose: () => void;
}) {
  const copyDebug = () => {
    void navigator.clipboard?.writeText(debugText).catch((err) => {
      console.warn("Could not copy call debug logs", err);
    });
  };

  return (
    <div className="fixed inset-0 z-[90] flex items-center justify-center bg-black/70 p-4">
      <div className="w-full max-w-2xl rounded-2xl border border-slate-700 bg-slate-950 text-slate-100 shadow-2xl">
        <div className="flex items-center justify-between gap-3 border-b border-slate-800 px-4 py-3">
          <div>
            <p className="text-sm font-bold">Call Debug</p>
            <p className="text-xs text-slate-400">Screenshot or copy this when a call gets stuck.</p>
          </div>
          <button
            onClick={onClose}
            className="rounded-full border border-slate-700 px-3 py-1 text-xs text-slate-200 hover:bg-slate-800"
          >
            Close
          </button>
        </div>
        <pre className="max-h-[68vh] overflow-auto whitespace-pre-wrap break-words px-4 py-3 text-[11px] leading-relaxed text-slate-200">
          {debugText}
        </pre>
        <div className="flex justify-end gap-2 border-t border-slate-800 px-4 py-3">
          <button
            onClick={copyDebug}
            className="rounded-full bg-emerald-500 px-4 py-2 text-xs font-semibold text-slate-950 hover:bg-emerald-400"
          >
            Copy Debug Logs
          </button>
        </div>
      </div>
    </div>
  );
}
