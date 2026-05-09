package com.glassbox.hello.calls

import com.glassbox.hello.chat.ChatModels

enum class CallUiStatus {
    Idle,
    Outgoing,
    Incoming,
    Connecting,
    Active,
    Ended,
    Declined,
    Missed,
    Busy,
    Unavailable,
    Failed,
    PermissionDenied
}

enum class CallMediaPhase {
    Idle,
    Preparing,
    Ringing,
    Connecting,
    Connected,
    Reconnecting,
    Closed,
    Error
}

data class CallSignal(
    val eventId: String? = null,
    val callId: String,
    val roomId: String? = null,
    val chatId: String,
    val fromUserId: String,
    val toUserId: String,
    val callerId: String,
    val calleeId: String,
    val callerName: String,
    val callerAvatar: String? = null,
    val calleeName: String? = null,
    val calleeAvatar: String? = null,
    val type: String,
    val isVideo: Boolean,
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val reason: String? = null,
    val timestamp: Long? = null,
    val attempt: Int = 1,
    val event: String? = null
)

data class CallIceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class CallParticipant(
    val id: String,
    val name: String = "Hello user",
    val avatar: String? = null,
    val joinedAt: Long? = null,
    val leftAt: Long? = null,
    val isHost: Boolean = false,
    val mediaState: CallMediaState? = null
)

data class CallMediaState(
    val audioMuted: Boolean = false,
    val videoOff: Boolean = false,
    val screenSharing: Boolean = false,
    val quality: String? = null,
    val beautyMode: String? = null
)

data class CallRoom(
    val id: String,
    val callId: String? = null,
    val chatId: String,
    val hostId: String,
    val mode: String = "group",
    val type: String = "video",
    val status: String = "ringing",
    val maxParticipants: Int = 4,
    val participantIds: List<String> = emptyList(),
    val participants: List<CallParticipant> = emptyList(),
    val createdAt: Long = 0,
    val endedAt: Long? = null,
    val endedBy: String? = null
)

data class RoomSignal(
    val roomId: String,
    val fromUserId: String,
    val toUserId: String,
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)

data class CallUiState(
    val status: CallUiStatus = CallUiStatus.Idle,
    val signal: CallSignal? = null,
    val activeRoom: CallRoom? = null,
    val peerName: String = "Hello call",
    val peerAvatar: String? = null,
    val message: String? = null,
    val durationSeconds: Long = 0,
    val history: List<ChatModels.CallHistoryItem> = emptyList(),
    val loadingHistory: Boolean = false,
    val historyError: String? = null,
    val socketConnected: Boolean = false,
    val nativeMediaReady: Boolean = false,
    val mediaPhase: CallMediaPhase = CallMediaPhase.Idle,
    val muted: Boolean = false,
    val speakerOn: Boolean = true,
    val cameraOff: Boolean = false,
    val roomParticipants: List<String> = emptyList(),
    val debugEvents: List<String> = emptyList(),
    val debugLastEvent: String? = null,
    val debugOfferReceived: Boolean = false,
    val debugAnswerSent: Boolean = false,
    val debugIceSentCount: Int = 0,
    val debugIceReceivedCount: Int = 0,
    val debugTurnConfigured: Boolean = false,
    val debugForceRelay: Boolean = false,
    val debugCandidateType: String? = null,
    val debugIceConfigUsed: String? = null,
    val debugPeerState: String? = null,
    val debugIceState: String? = null,
    val debugLastError: String? = null,
    val debugCameraStatus: String? = null
)
