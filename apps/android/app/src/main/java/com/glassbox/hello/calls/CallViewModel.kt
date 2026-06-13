package com.glassbox.hello.calls

import android.content.Context
import android.os.SystemClock
import com.glassbox.hello.debug.AppLog as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.chat.otherParticipant
import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.core.User
import com.glassbox.hello.debug.HelloDebugLog
import com.glassbox.hello.network.SocketManager
import com.glassbox.hello.notifications.IncomingCallRinger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer

class CallViewModel(
    private var repository: CallRepositoryContract = CallRepository(),
    private var socketManager: CallSocket = SocketManager.getInstance(),
    private val callEngine: CallMediaEngine = NativeCallEngine()
) : ViewModel() {
    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()
    var onSessionRevoked: ((JSONObject) -> Unit)? = null

    private var currentUser: User? = null
    private var appContext: Context? = null
    private var timerJob: Job? = null
    private var missedTimeoutJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var terminalResetJob: Job? = null
    private var historyRefreshJob: Job? = null
    private var startCallJob: Job? = null
    private val pendingOffers = mutableMapOf<String, CallSignal>()
    private val seenCallEventIds = linkedSetOf<String>()
    private val startedCallIds = linkedSetOf<String>()
    private var iceConfigLoaded = false
    private var incomingMediaStartCallId: String? = null

    init {
        socketManager.onConnectedChanged = { connected ->
            _state.value = _state.value.copy(socketConnected = connected)
            if (!connected && _state.value.status in setOf(CallUiStatus.Outgoing, CallUiStatus.Connecting, CallUiStatus.Active)) {
                _state.value = _state.value.copy(mediaPhase = CallMediaPhase.Reconnecting, message = "Network reconnecting...")
            }
        }
        socketManager.onCallEvent = { event, payload -> handleSocketEvent(event, payload) }
        if (socketManager is SocketManager) {
            (socketManager as SocketManager).onSessionRevoked = { payload -> onSessionRevoked?.invoke(payload) }
        }
        callEngine.onOfferReady = { outgoing ->
            activeSignalWith(outgoing)?.let {
                if (!it.offerSdp.isNullOrBlank() && startedCallIds.add(it.callId)) {
                    addDebug("ANDROID: emit call:start")
                    socketManager.startCall(it.toJson())
                    if (startedCallIds.size > 64) {
                        val keep = startedCallIds.toList().takeLast(32)
                        startedCallIds.clear()
                        startedCallIds.addAll(keep)
                    }
                }
                addDebug("ANDROID: emit call:offer hasOfferSdp=${!it.offerSdp.isNullOrBlank()} callId=${it.callId}")
                socketManager.sendOffer(it.toJson())
            }
        }
        callEngine.onAnswerReady = { outgoing ->
            activeSignalWith(outgoing)?.let {
                addDebug("ANDROID: answer created hasAnswerSdp=${!it.answerSdp.isNullOrBlank()} callId=${it.callId}")
                _state.value = _state.value.copy(debugAnswerSent = true)
                addDebug("ANDROID: emit call:answer")
                socketManager.sendAnswer(it.toJson())
            }
        }
        callEngine.onIceCandidateReady = { outgoing ->
            activeSignalWith(outgoing)?.let {
                val nextIceSent = _state.value.debugIceSentCount + 1
                val candidateType = candidateTypeLabel(it.candidate)
                _state.value = _state.value.copy(debugIceSentCount = nextIceSent, debugCandidateType = candidateType)
                addDebug("ANDROID: ICE sent count=$nextIceSent type=$candidateType callId=${it.callId}")
                socketManager.sendIceCandidate(it.toJson())
            }
        }
        callEngine.onDebugEvent = null
        callEngine.onPhaseChanged = { phase ->
            val active = phase == CallMediaPhase.Connected
            _state.value = _state.value.copy(
                mediaPhase = phase,
                nativeMediaReady = active,
                status = if (active) CallUiStatus.Active else _state.value.status,
                message = when (phase) {
                    CallMediaPhase.Preparing -> "Preparing secure media..."
                    CallMediaPhase.Ringing -> "Ringing..."
                    CallMediaPhase.Connecting -> "Connecting..."
                    CallMediaPhase.Connected -> "Connected"
                    CallMediaPhase.Reconnecting -> "Reconnecting..."
                    CallMediaPhase.Error -> _state.value.message ?: "Call media failed"
                    else -> _state.value.message
                }
            )
            if (active) {
                connectionTimeoutJob?.cancel()
                activeSignal()?.let { socketManager.connected(it.toJson()) }
                startTimer()
            }
        }
        callEngine.onError = { message ->
            val reason = failureReason(message)
            _state.value = _state.value.copy(debugLastError = reason)
            addDebug("ANDROID: media error reason=$reason message=$message")
            activeSignal()?.let {
                socketManager.failed(it.copy(reason = reason).toJson())
            }
            terminal(if (reason == "permission_denied") CallUiStatus.PermissionDenied else CallUiStatus.Failed, message, _state.value.signal)
        }
    }

    fun connect(user: User) {
        currentUser = user
        if (socketManager !is SocketManager) {
            addDebug("ANDROID: switching signaling transport to local socket")
            socketManager.disconnect()
            socketManager = SocketManager.getInstance().also { localSocket ->
                localSocket.onConnectedChanged = { connected ->
                    _state.value = _state.value.copy(socketConnected = connected)
                    if (!connected && _state.value.status in setOf(CallUiStatus.Outgoing, CallUiStatus.Connecting, CallUiStatus.Active)) {
                        _state.value = _state.value.copy(mediaPhase = CallMediaPhase.Reconnecting, message = "Network reconnecting...")
                    }
                }
                localSocket.onCallEvent = { event, payload -> handleSocketEvent(event, payload) }
                localSocket.onSessionRevoked = { payload -> onSessionRevoked?.invoke(payload) }
            }
        }
        HelloDebugLog.d("Call", "connectLocal userId=${user.id}")
        socketManager.connect(user)
    }

    fun connect(context: Context, user: User) {
        currentUser = user
        appContext = context.applicationContext
        HelloDebugLog.d("Call", "connectCloud userId=${user.id}")
        repository = CloudCallRepository(context.applicationContext)
        if (socketManager is CallSignalingClient) {
            addDebug("ANDROID: reconnecting existing cloud signaling transport")
            socketManager.disconnect()
        } else {
            addDebug("ANDROID: switching signaling transport to cloud socket")
            socketManager.disconnect()
        }
        socketManager = CallSignalingClient(context.applicationContext).also { cloudSocket ->
            cloudSocket.onConnectedChanged = { connected ->
                _state.value = _state.value.copy(socketConnected = connected)
                if (!connected && _state.value.status in setOf(CallUiStatus.Outgoing, CallUiStatus.Connecting, CallUiStatus.Active)) {
                    _state.value = _state.value.copy(mediaPhase = CallMediaPhase.Reconnecting, message = "Network reconnecting...")
                }
            }
            cloudSocket.onCallEvent = { event, payload -> handleSocketEvent(event, payload) }
            cloudSocket.onSessionRevoked = { payload -> onSessionRevoked?.invoke(payload) }
        }
        socketManager.connect(user)
    }

    private fun addDebug(message: String) {
        // Production builds keep call signaling behavior but do not collect or expose debug traces.
    }

    private fun parseCandidateType(candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        return Regex("\\btyp\\s+(\\w+)", RegexOption.IGNORE_CASE).find(candidate)?.groupValues?.get(1)
    }

    private fun candidateTypeLabel(candidate: String?): String {
        return parseCandidateType(candidate)?.lowercase() ?: "unknown"
    }

    private suspend fun ensureSignalingReady(context: Context?, user: User): Boolean {
        if (socketManager.isConnected()) return true
        addDebug("ANDROID: signaling reconnect requested before call start")
        if (context != null) {
            connect(context, user)
        } else {
            connect(user)
        }
        repeat(30) {
            if (socketManager.isConnected()) {
                addDebug("ANDROID: signaling connected after reconnect wait")
                return true
            }
            delay(200)
        }
        addDebug("ANDROID: signaling still disconnected after reconnect wait")
        return socketManager.isConnected()
    }

    fun callDebugText(): String {
        return "Debug diagnostics are disabled."
    }

    fun loadHistory(userId: String) {
        viewModelScope.launch {
            HelloDebugLog.d("Call", "loadHistory userId=$userId")
            _state.value = _state.value.copy(loadingHistory = true, historyError = null)
            val result = repository.loadHistory(userId)
            _state.value = if (result.isSuccess) {
                HelloDebugLog.d("Call", "loadHistory success userId=$userId count=${result.getOrNull().orEmpty().size}")
                _state.value.copy(history = result.getOrNull().orEmpty(), loadingHistory = false, historyError = null)
            } else {
                HelloDebugLog.w("Call", "loadHistory failure userId=$userId error=${result.exceptionOrNull()?.message}")
                _state.value.copy(
                    loadingHistory = false,
                    historyError = result.exceptionOrNull()?.message ?: "Failed to load calls"
                )
            }
        }
    }

    fun startCall(context: Context?, chat: ChatModels.Chat, user: User, isVideo: Boolean) {
        val startedAt = SystemClock.elapsedRealtime()
        HelloDebugLog.d("Call", "startCall chatId=${chat.id} userId=${user.id} isGroup=${chat.isGroup} isVideo=$isVideo")
        Log.d(TAG, "call_start_requested chatId=${chat.id} isGroup=${chat.isGroup} isVideo=$isVideo")
        appContext = context?.applicationContext ?: appContext
        if (chat.isGroup) {
            startGroupCall(context, chat, user, isVideo)
            return
        }
        val other = chat.otherParticipant(user.id)
        if (other == null) {
            terminal(CallUiStatus.Failed, "Select a direct chat before starting a call.", null)
            return
        }
        if (startCallJob?.isActive == true) {
            addDebug("ANDROID: ignored duplicate direct start while call setup is already running")
            return
        }
        if (isCallOccupying(_state.value.status)) {
            terminal(CallUiStatus.Busy, "Already in a call", _state.value.signal)
            return
        }

        _state.value = _state.value.copy(
            status = CallUiStatus.Outgoing,
            peerName = other.name,
            peerAvatar = other.avatar,
            message = "Starting call...",
            socketConnected = socketManager.isConnected(),
            mediaPhase = CallMediaPhase.Preparing
        )

        startCallJob = viewModelScope.launch {
            if (!ensureSignalingReady(context, user)) {
                terminal(
                    CallUiStatus.Failed,
                    "Call service is still connecting. Check your cloud chat session and retry.",
                    null
                )
                return@launch
            }
            val type = if (isVideo) "video" else "audio"
            Log.d(TAG, "call_start_signaling_ready chatId=${chat.id} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            val result = repository.createDirectCall(
                callerId = user.id,
                calleeId = other.id,
                chatId = chat.id,
                type = type
            )
            val callId = result.getOrElse {
                terminal(CallUiStatus.Failed, callStartErrorMessage(it.message), null)
                return@launch
            }
            val signal = CallSignal(
                callId = callId,
                chatId = chat.id,
                fromUserId = user.id,
                toUserId = other.id,
                callerId = user.id,
                calleeId = other.id,
                callerName = user.name,
                callerAvatar = user.avatar,
                calleeName = other.name,
                calleeAvatar = other.avatar,
                type = type,
                isVideo = isVideo
            )
            _state.value = CallUiState(
                status = CallUiStatus.Outgoing,
                signal = signal,
                peerName = other.name,
                peerAvatar = other.avatar,
                message = "Calling...",
                socketConnected = socketManager.isConnected(),
                mediaPhase = CallMediaPhase.Preparing
            )
            addDebug("ANDROID: outgoing call created callId=$callId type=$type to=${other.id}")
            Log.d(TAG, "call_start_created callId=$callId type=$type elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            if (startedCallIds.add(signal.callId)) {
                addDebug("ANDROID: emit call:start")
                socketManager.startCall(signal.toJson())
                if (startedCallIds.size > 64) {
                    val keep = startedCallIds.toList().takeLast(32)
                    startedCallIds.clear()
                    startedCallIds.addAll(keep)
                }
            }
            prepareIceConfigSafely()
            runCatching {
                callEngine.startOutgoing(context, isVideo)
                Log.d(TAG, "call_start_media_bootstrap callId=$callId isVideo=$isVideo elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    debugLastError = error.message ?: "outgoing_start_failed",
                    message = "Calling..."
                )
                addDebug("ANDROID: media bootstrap warning error=${error.message}")
            }
            startMissedTimeout(signal)
            startConnectionTimeout(signal)
        }.also { job ->
            job.invokeOnCompletion {
                if (startCallJob === job) {
                    startCallJob = null
                }
            }
        }
    }

    private fun startGroupCall(context: Context?, chat: ChatModels.Chat, user: User, isVideo: Boolean) {
        if (startCallJob?.isActive == true) {
            addDebug("ANDROID: ignored duplicate group start while call setup is already running")
            return
        }
        if (isCallOccupying(_state.value.status)) {
            terminal(CallUiStatus.Busy, "Already in a call", _state.value.signal)
            return
        }
        val memberIds = (chat.members ?: chat.participants?.map { it.id }.orEmpty())
            .filter { it.isNotBlank() && it != user.id }
            .distinct()
        if (memberIds.isEmpty()) {
            terminal(CallUiStatus.Failed, "Group call requires at least one other chat member.", null)
            return
        }
        if (memberIds.size > 3) {
            terminal(CallUiStatus.Failed, "Group calls support up to 4 participants.", null)
            return
        }

        _state.value = _state.value.copy(
            status = CallUiStatus.Outgoing,
            peerName = chat.name.ifBlank { "Group call" },
            peerAvatar = null,
            message = "Starting group call...",
            socketConnected = socketManager.isConnected(),
            mediaPhase = CallMediaPhase.Preparing
        )

        startCallJob = viewModelScope.launch {
            runCatching {
                if (!ensureSignalingReady(context, user)) {
                    terminal(
                        CallUiStatus.Failed,
                        "Call service is still connecting. Check your cloud chat session and retry.",
                        null
                    )
                    return@launch
                }
                val type = "audio"
                val room = repository.createGroupRoom(
                    chatId = chat.id,
                    hostId = user.id,
                    type = type,
                    participantIds = memberIds
                ).getOrElse {
                    terminal(CallUiStatus.Failed, callStartErrorMessage(it.message), null)
                    return@launch
                }
                val activeRoom = room.copy(participantIds = room.participantIds.ifEmpty { listOf(user.id) + memberIds })
                _state.value = CallUiState(
                    status = CallUiStatus.Active,
                    activeRoom = activeRoom,
                    peerName = chat.name.ifBlank { "Group call" },
                    message = "Group call live",
                    socketConnected = socketManager.isConnected(),
                    mediaPhase = CallMediaPhase.Connected,
                    nativeMediaReady = true,
                    roomParticipants = activeRoom.participantIds
                )
                socketManager.createRoom(
                    JSONObject()
                        .put("room", activeRoom.toJson())
                        .put("roomId", activeRoom.id)
                        .put("fromUserId", user.id)
                        .put("participantIds", JSONArray(activeRoom.participantIds))
                )
                callEngine.startOutgoing(context, isVideo = false)
                startTimer()
            }.onFailure { error ->
                addDebug("ANDROID: group start failed error=${error.message}")
                terminal(CallUiStatus.Failed, error.message ?: "Could not start group call", _state.value.signal)
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (startCallJob === job) {
                    startCallJob = null
                }
            }
        }
    }

    fun acceptIncoming(context: Context?, forceAudio: Boolean = false) {
        val startedAt = SystemClock.elapsedRealtime()
        val user = currentUser ?: return
        appContext = context?.applicationContext ?: appContext
        HelloDebugLog.d("Call", "acceptIncoming userId=${user.id} callId=${_state.value.signal?.callId ?: _state.value.activeRoom?.id} forceAudio=$forceAudio")
        Log.d(TAG, "call_accept_requested callId=${_state.value.signal?.callId ?: _state.value.activeRoom?.id} forceAudio=$forceAudio")
        _state.value.activeRoom?.let {
            IncomingCallRinger.stop(it.id)
            acceptIncomingGroup(context, user, it)
            return
        }
        val signal = _state.value.signal ?: return
        IncomingCallRinger.stop(signal.callId)
        val acceptedSignal = if (forceAudio && signal.isVideo) {
            signal.copy(type = "audio", isVideo = false)
        } else {
            signal
        }
        val accept = acceptedSignal.copy(fromUserId = user.id, toUserId = signal.callerId)
        addDebug("ANDROID: accept clicked")
        _state.value = _state.value.copy(
            status = CallUiStatus.Connecting,
            signal = acceptedSignal,
            message = if (forceAudio && signal.isVideo) "Camera permission denied; joining with audio..." else "Connecting...",
            mediaPhase = CallMediaPhase.Preparing
        )
        viewModelScope.launch {
            prepareIceConfigSafely()
            runCatching {
                socketManager.acceptCall(accept.toJson())
                Log.d(TAG, "call_accept_signal_sent callId=${acceptedSignal.callId} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            }.onFailure { error ->
                addDebug("ANDROID: accept signal failed error=${error.message}")
                terminal(CallUiStatus.Failed, error.message ?: "Could not accept call", acceptedSignal)
                return@launch
            }
            startConnectionTimeout(acceptedSignal)
            if (acceptedSignal.offerSdp.isNullOrBlank()) {
                addDebug("ANDROID: waiting for offer after accept")
            } else {
                startIncomingMedia(acceptedSignal)
                Log.d(TAG, "call_accept_start_incoming_media callId=${acceptedSignal.callId} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            }
        }
    }

    fun showIncomingCall(signal: CallSignal) {
        HelloDebugLog.d("Call", "showIncomingCall callId=${signal.callId} caller=${signal.callerId} callee=${signal.calleeId} isVideo=${signal.isVideo}")
        val current = _state.value
        if (current.status != CallUiStatus.Idle && current.signal?.callId != signal.callId) {
            currentUser?.id?.let { userId ->
                socketManager.busy(signal.copy(fromUserId = userId, toUserId = signal.callerId, reason = "busy").toJson())
            }
            return
        }
        addDebug("ANDROID: external incoming call callId=${signal.callId}")
        _state.value = CallUiState(
            status = CallUiStatus.Incoming,
            signal = signal,
            peerName = signal.callerName,
            peerAvatar = signal.callerAvatar,
            message = incomingRingingMessage(signal),
            socketConnected = socketManager.isConnected(),
            mediaPhase = CallMediaPhase.Ringing,
            debugOfferReceived = !signal.offerSdp.isNullOrBlank()
        )
        IncomingCallRinger.start(appContext, signal.callId)
        startIncomingTimeout(signal)
    }

    private fun acceptIncomingGroup(context: Context?, user: User, room: CallRoom) {
        viewModelScope.launch {
            runCatching {
                val joined = repository.joinGroupRoom(room.id, user.id).getOrElse {
                    terminal(CallUiStatus.Failed, it.message ?: "Could not join group call", null)
                    return@launch
                }
                val nextRoom = joined.copy(participantIds = joined.participantIds.ifEmpty { room.participantIds })
                _state.value = _state.value.copy(
                    status = CallUiStatus.Active,
                    activeRoom = nextRoom,
                    peerName = _state.value.peerName,
                    message = "Group call live",
                    mediaPhase = CallMediaPhase.Connected,
                    nativeMediaReady = true,
                    roomParticipants = nextRoom.participantIds
                )
                socketManager.joinRoom(
                    JSONObject()
                        .put("roomId", nextRoom.id)
                        .put("fromUserId", user.id)
                        .put("userId", user.id)
                )
                callEngine.startOutgoing(context, nextRoom.type == "video")
                startTimer()
            }.onFailure { error ->
                addDebug("ANDROID: group accept failed error=${error.message}")
                terminal(CallUiStatus.Failed, error.message ?: "Could not join group call", null)
            }
        }
    }

    fun declineCall() {
        val user = currentUser
        val room = _state.value.activeRoom
        if (user != null && room != null) {
            socketManager.leaveRoom(
                JSONObject()
                    .put("roomId", room.id)
                    .put("fromUserId", user.id)
                    .put("userId", user.id)
                    .put("reason", "declined")
            )
        }
        val signal = _state.value.signal
        if (user != null && signal != null) {
            socketManager.declineCall(signal.copy(fromUserId = user.id, toUserId = remoteUserId(signal, user.id), reason = "declined").toJson())
        }
        IncomingCallRinger.stop(signal?.callId ?: room?.id)
        terminal(CallUiStatus.Declined, "Call declined", signal)
    }

    fun endCall(reason: String = "ended_by_caller") {
        val user = currentUser
        val room = _state.value.activeRoom
        if (user != null && room != null) {
            viewModelScope.launch {
                repository.leaveGroupRoom(room.id, user.id, reason == "ended")
            }
            socketManager.leaveRoom(
                JSONObject()
                    .put("roomId", room.id)
                    .put("fromUserId", user.id)
                    .put("userId", user.id)
                    .put("ended", reason == "ended")
                    .put("reason", reason)
            )
            IncomingCallRinger.stop(room.id)
            terminal(CallUiStatus.Ended, "Group call ended", null)
            return
        }
        val signal = _state.value.signal
        if (user != null && signal != null) {
            socketManager.endCall(signal.copy(fromUserId = user.id, toUserId = remoteUserId(signal, user.id), reason = reason).toJson())
        }
        IncomingCallRinger.stop(signal?.callId ?: room?.id)
        terminal(CallUiStatus.Ended, "Call ended", signal)
    }

    fun dismissCallOverlay() {
        resetToIdle()
    }

    private fun resetToIdle() {
        missedTimeoutJob?.cancel()
        connectionTimeoutJob?.cancel()
        terminalResetJob?.cancel()
        stopTimer()
        callEngine.dispose()
        IncomingCallRinger.stop(_state.value.signal?.callId ?: _state.value.activeRoom?.id)
        pendingOffers.clear()
        startedCallIds.remove(_state.value.signal?.callId)
        incomingMediaStartCallId = null
        _state.value = _state.value.copy(
            status = CallUiStatus.Idle,
            signal = null,
            activeRoom = null,
            message = null,
            durationSeconds = 0,
            nativeMediaReady = false,
            mediaPhase = CallMediaPhase.Idle,
            roomParticipants = emptyList(),
            debugOfferReceived = false,
            debugAnswerReceived = false,
            debugAnswerSent = false,
            debugIceSentCount = 0,
            debugIceReceivedCount = 0,
            debugCandidateType = null,
            debugPeerState = null,
            debugIceState = null,
            debugLastError = null,
            debugCameraStatus = null
        )
    }

    fun toggleMute() {
        val next = !_state.value.muted
        callEngine.setMuted(next)
        _state.value = _state.value.copy(muted = next)
    }

    fun toggleSpeaker(context: Context) {
        val next = !_state.value.speakerOn
        callEngine.setSpeaker(context.applicationContext, next)
        _state.value = _state.value.copy(speakerOn = next)
    }

    fun toggleCamera() {
        val startedAt = SystemClock.elapsedRealtime()
        val next = !_state.value.cameraOff
        callEngine.setCameraOff(next)
        _state.value = _state.value.copy(cameraOff = next)
        Log.d(TAG, "camera_toggle cameraOff=$next elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    fun switchCamera() {
        val startedAt = SystemClock.elapsedRealtime()
        callEngine.switchCamera()
        Log.d(TAG, "camera_switch_requested elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    fun setVideoQuality(profile: VideoQualityProfile) {
        val startedAt = SystemClock.elapsedRealtime()
        if (_state.value.selectedVideoQuality == profile) {
            Log.d(TAG, "video_quality_skip profile=${profile.name} reason=already_selected")
            return
        }
        _state.value = _state.value.copy(selectedVideoQuality = profile)
        callEngine.setPreferredVideoProfile(profile)
        Log.d(TAG, "video_quality_apply profile=${profile.name} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    fun setVisualLook(look: CallVisualLook) {
        if (_state.value.selectedVisualLook == look) return
        _state.value = _state.value.copy(selectedVisualLook = look)
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        val startedAt = SystemClock.elapsedRealtime()
        callEngine.attachLocalRenderer(renderer)
        Log.d(TAG, "local_renderer_attach_requested elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        val startedAt = SystemClock.elapsedRealtime()
        callEngine.attachRemoteRenderer(renderer)
        Log.d(TAG, "remote_renderer_attach_requested elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    private fun handleSocketEvent(event: String, payload: JSONObject) {
        val user = currentUser
        if (!shouldProcessSignal(event, payload)) return
        addDebug("ANDROID: recv $event hasOfferSdp=${payload.hasOfferSdp()} hasAnswerSdp=${payload.hasAnswerSdp()} hasIce=${payload.hasIce()}")
        when (event) {
            "call:ack" -> handleAck(payload)
            "call:start" -> payload.toCallSignalOrLog(event)?.let { handleIncomingStart(user, it) }
            "call:offer" -> payload.toCallSignalOrLog(event)?.let { handleOffer(it) }
            "call:answer" -> {
                val startedAt = SystemClock.elapsedRealtime()
                val signal = payload.toCallSignalOrLog(event) ?: return
                val currentCallId = _state.value.signal?.callId
                if (currentCallId != signal.callId) {
                    addDebug("ANDROID: ignored stale answer currentCallId=${currentCallId ?: "none"} incomingCallId=${signal.callId}")
                    return
                }
                if (_state.value.signal?.callId == signal.callId) {
                    missedTimeoutJob?.cancel()
                    _state.value = _state.value.copy(
                        status = CallUiStatus.Connecting,
                        message = "Connecting...",
                        mediaPhase = CallMediaPhase.Connecting,
                        debugAnswerReceived = true
                    )
                    startConnectionTimeout(signal)
                    Log.d(TAG, "call_answer_transition callId=${signal.callId} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                }
                signal.answerSdp?.let {
                    addDebug("ANDROID: acceptAnswer called")
                    callEngine.acceptAnswer(it)
                    Log.d(TAG, "call_answer_sdp_applied callId=${signal.callId} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                }
            }
            "call:ice-candidate" -> payload.toCallSignalOrLog(event)?.let { signal ->
                val currentCallId = _state.value.signal?.callId
                if (currentCallId != signal.callId) {
                    addDebug("ANDROID: ignored stale ice currentCallId=${currentCallId ?: "none"} incomingCallId=${signal.callId}")
                    return@let
                }
                signal.candidate?.let {
                    val nextIceReceived = _state.value.debugIceReceivedCount + 1
                    val candidateType = candidateTypeLabel(it)
                    _state.value = _state.value.copy(debugIceReceivedCount = nextIceReceived, debugCandidateType = candidateType)
                    addDebug("ANDROID: ICE received count=$nextIceReceived type=$candidateType callId=${signal.callId}")
                    callEngine.addIceCandidate(it, signal.sdpMid, signal.sdpMLineIndex)
                }
            }
            "call:ringing" -> {
                val signal = payload.toCallSignalOrLog(event) ?: return
                if (_state.value.signal?.callId == signal.callId) {
                    _state.value = _state.value.copy(status = CallUiStatus.Outgoing, message = "Ringing...", mediaPhase = CallMediaPhase.Ringing)
                } else {
                    addDebug("ANDROID: ignored stale ringing currentCallId=${_state.value.signal?.callId ?: "none"} incomingCallId=${signal.callId}")
                }
            }
            "call:accepted" -> {
                val startedAt = SystemClock.elapsedRealtime()
                val signal = payload.toCallSignalOrLog(event) ?: return
                missedTimeoutJob?.cancel()
                if (_state.value.signal?.callId == signal.callId) {
                    val current = _state.value
                    if (current.status != CallUiStatus.Connecting || current.mediaPhase != CallMediaPhase.Connecting || current.message != "Connecting...") {
                        _state.value = current.copy(status = CallUiStatus.Connecting, message = "Connecting...", mediaPhase = CallMediaPhase.Connecting)
                    }
                    startConnectionTimeout(signal)
                    Log.d(TAG, "call_accepted_transition callId=${signal.callId} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                } else {
                    addDebug("ANDROID: ignored stale accepted currentCallId=${_state.value.signal?.callId ?: "none"} incomingCallId=${signal.callId}")
                }
            }
            "call:connected" -> {
                val startedAt = SystemClock.elapsedRealtime()
                val signal = payload.toCallSignalOrLog(event) ?: return
                missedTimeoutJob?.cancel()
                connectionTimeoutJob?.cancel()
                if (_state.value.signal?.callId == signal.callId) {
                    val current = _state.value
                    if (current.status != CallUiStatus.Active || current.mediaPhase != CallMediaPhase.Connected || !current.nativeMediaReady || current.message != "Connected") {
                        _state.value = current.copy(status = CallUiStatus.Active, message = "Connected", mediaPhase = CallMediaPhase.Connected, nativeMediaReady = true)
                    }
                    Log.d(TAG, "call_connected_transition callId=${signal.callId} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                    startTimer()
                } else {
                    addDebug("ANDROID: ignored stale connected currentCallId=${_state.value.signal?.callId ?: "none"} incomingCallId=${signal.callId}")
                }
            }
            "call:busy" -> payload.toCallSignalOrLog(event)?.let { terminalIfCurrent(it, CallUiStatus.Busy, "User busy") }
            "call:missed" -> payload.toCallSignalOrLog(event)?.let { terminalIfCurrent(it, CallUiStatus.Missed, "No answer") }
            "call:unavailable" -> payload.toCallSignalOrLog(event)?.let { terminalIfCurrent(it, CallUiStatus.Unavailable, "User unavailable") }
            "call:declined" -> payload.toCallSignalOrLog(event)?.let { terminalIfCurrent(it, CallUiStatus.Declined, "Call declined") }
            "call:ended" -> payload.toCallSignalOrLog(event)?.let { terminalIfCurrent(it, CallUiStatus.Ended, "Call ended") }
            "call:failed" -> payload.toCallSignalOrLog(event)?.let { signal ->
                terminalIfCurrent(signal, CallUiStatus.Failed, payload.optString("reason", "Call failed"))
            }
            "call:history-updated" -> currentUser?.id?.let { scheduleHistoryRefresh(it) }
            "call:room-created" -> handleRoomCreated(payload)
            "call:room-join" -> handleRoomJoin(payload)
            "call:room-leave" -> handleRoomLeave(payload)
            "call:room-full" -> terminal(CallUiStatus.Failed, "This group call is full.", null)
            "call:participant-state" -> Unit
            "call:room-offer", "call:room-answer", "call:room-ice-candidate" -> Unit
        }
    }

    private fun shouldProcessSignal(event: String, payload: JSONObject): Boolean {
        if (event == "call:ack") return true
        val eventId = payload.optString("eventId")
        if (eventId.isBlank()) {
            Log.w(TAG, "[CALL_TRACE] android missing_event_id event=$event callId=${payload.optString("callId")}")
        } else {
            if (!seenCallEventIds.add(eventId)) {
                Log.d(TAG, "[CALL_TRACE] android duplicate event=$event callId=${payload.optString("callId")} eventId=$eventId")
                return false
            }
            if (seenCallEventIds.size > 256) {
                val keep = seenCallEventIds.toList().takeLast(128)
                seenCallEventIds.clear()
                seenCallEventIds.addAll(keep)
            }
        }
        if (event in ACK_EVENTS) {
            val userId = currentUser?.id
            if (!eventId.isNullOrBlank() && userId != null) {
                socketManager.ack(
                    JSONObject()
                        .put("eventId", eventId)
                        .put("callId", payload.optString("callId"))
                        .put("fromUserId", userId)
                        .put("toUserId", payload.optString("fromUserId"))
                        .put("receivedBy", userId)
                        .put("status", "received")
                )
            }
        }
        Log.d(TAG, "[CALL_TRACE] android process event=$event callId=${payload.optString("callId")} eventId=$eventId")
        return true
    }

    private fun handleAck(payload: JSONObject) {
        Log.d(TAG, "[CALL_TRACE] android ack eventId=${payload.optString("eventId")} callId=${payload.optString("callId")} by=${payload.optString("receivedBy")}")
    }

    private fun JSONObject.toCallSignalOrLog(event: String): CallSignal? {
        val signal = toCallSignal(currentSignal = _state.value.signal, currentUserId = currentUser?.id)
        if (signal == null) {
            Log.w(TAG, "[CALL_TRACE] android malformed event=$event payload=$this")
            addDebug("ANDROID: malformed $event payload")
        }
        return signal
    }

    private fun JSONObject.hasOfferSdp(): Boolean = optJSONObject("offer")?.optString("sdp")?.isNotBlank() == true

    private fun JSONObject.hasAnswerSdp(): Boolean = optJSONObject("answer")?.optString("sdp")?.isNotBlank() == true

    private fun JSONObject.hasIce(): Boolean = optJSONObject("candidate")?.optString("candidate")?.isNotBlank() == true

    private suspend fun ensureIceConfig() {
        if (iceConfigLoaded) return
        repository.loadIceServers()
            .onSuccess {
                callEngine.setIceServers(it)
                _state.value = _state.value.copy(
                    debugTurnConfigured = it.any { server -> server.urls.any { url -> url.startsWith("turn:", ignoreCase = true) } },
                    debugForceRelay = AppConfig.WEBRTC_FORCE_RELAY,
                    debugIceConfigUsed = it.joinToString(separator = ", ") { server ->
                        val urls = server.urls.joinToString(",")
                        if (server.username.isNullOrBlank() && server.credential.isNullOrBlank()) urls else "$urls (auth)"
                    }
                )
                iceConfigLoaded = true
            }
            .onFailure {
                Log.w(TAG, "[CALL_TRACE] android ice_config fallback=${it.message}")
                _state.value = _state.value.copy(
                    debugTurnConfigured = false,
                    debugForceRelay = AppConfig.WEBRTC_FORCE_RELAY,
                    debugIceConfigUsed = null
                )
                iceConfigLoaded = true
            }
    }

    private suspend fun prepareIceConfigSafely() {
        runCatching { ensureIceConfig() }
            .onFailure { error ->
                _state.value = _state.value.copy(
                    debugLastError = error.message ?: "ice_config_failed"
                )
                addDebug("ANDROID: ice config warning error=${error.message}")
            }
    }

    private fun handleIncomingStart(user: User?, signal: CallSignal) {
        if (user == null || signal.toUserId != user.id) return
        if (isCallOccupying(_state.value.status)) {
            socketManager.busy(signal.copy(fromUserId = user.id, toUserId = signal.callerId, reason = "busy").toJson())
            return
        }
        addDebug("ANDROID: recv call:start callId=${signal.callId}")
        val pendingOffer = pendingOffers.remove(signal.callId)
        val incoming = if (pendingOffer?.offerSdp != null) {
            addDebug("ANDROID: matched early offer from queue")
            signal.copy(offerSdp = pendingOffer.offerSdp, fromUserId = pendingOffer.fromUserId, toUserId = pendingOffer.toUserId)
        } else {
            signal
        }
        _state.value = _state.value.copy(
            status = CallUiStatus.Incoming,
            signal = incoming,
            peerName = incoming.callerName,
            peerAvatar = incoming.callerAvatar,
            message = incomingRingingMessage(incoming),
            mediaPhase = CallMediaPhase.Ringing,
            debugOfferReceived = !incoming.offerSdp.isNullOrBlank()
        )
        socketManager.ringing(signal.copy(fromUserId = user.id, toUserId = signal.callerId).toJson())
        IncomingCallRinger.start(appContext, incoming.callId)
        startIncomingTimeout(incoming)
    }

    private fun handleOffer(signal: CallSignal) {
        addDebug("ANDROID: recv call:offer hasOfferSdp=${!signal.offerSdp.isNullOrBlank()}")
        val current = _state.value.signal
        if (current?.callId != signal.callId) {
            pendingOffers[signal.callId] = signal
            if (pendingOffers.size > 12) {
                val keep = pendingOffers.entries.toList().takeLast(6)
                pendingOffers.clear()
                keep.forEach { entry -> pendingOffers[entry.key] = entry.value }
            }
            addDebug("ANDROID: queued offer")
            return
        }
        val merged = current.copy(
            offerSdp = signal.offerSdp,
            fromUserId = signal.fromUserId,
            toUserId = signal.toUserId
        )
        _state.value = _state.value.copy(
            signal = merged,
            debugOfferReceived = !signal.offerSdp.isNullOrBlank(),
            message = if (_state.value.status == CallUiStatus.Incoming) {
                incomingRingingMessage(merged)
            } else {
                _state.value.message
            }
        )
        if (_state.value.status == CallUiStatus.Connecting || _state.value.status == CallUiStatus.Active) {
            signal.offerSdp?.let {
                if (incomingMediaStartCallId == signal.callId) {
                    addDebug("ANDROID: acceptOffer called")
                    callEngine.acceptOffer(it)
                } else {
                    startIncomingMedia(merged)
                }
            }
        }
    }

    private fun terminalIfCurrent(signal: CallSignal, status: CallUiStatus, message: String) {
        val currentCallId = _state.value.signal?.callId ?: _state.value.activeRoom?.id
        if (!currentCallId.isNullOrBlank() && currentCallId != signal.callId) {
            addDebug("ANDROID: ignored stale terminal event status=$status callId=${signal.callId} currentCallId=$currentCallId")
            return
        }
        terminal(status, message, signal)
    }

    private fun terminal(status: CallUiStatus, message: String, signal: CallSignal?) {
        HelloDebugLog.w(
            "Call",
            "terminal status=$status previousStatus=${_state.value.status} callId=${signal?.callId ?: _state.value.signal?.callId ?: _state.value.activeRoom?.id} phase=${_state.value.mediaPhase} message=${HelloDebugLog.snippet(message)}"
        )
        startCallJob?.cancel()
        startCallJob = null
        missedTimeoutJob?.cancel()
        connectionTimeoutJob?.cancel()
        terminalResetJob?.cancel()
        historyRefreshJob?.cancel()
        stopTimer()
        callEngine.dispose()
        incomingMediaStartCallId = null
        IncomingCallRinger.stop(signal?.callId ?: _state.value.activeRoom?.id)
        val terminalSignal = signal ?: _state.value.signal
        val terminalRoom = _state.value.activeRoom
        _state.value = _state.value.copy(
            status = status,
            signal = terminalSignal,
            activeRoom = terminalRoom,
            message = message,
            mediaPhase = CallMediaPhase.Closed,
            nativeMediaReady = false
        )
        currentUser?.id?.let { userId ->
            scheduleHistoryRefresh(userId, 500L)
        }
        terminalResetJob = viewModelScope.launch {
            delay(900)
            val current = _state.value
            val sameCall = terminalSignal?.callId == null || current.signal?.callId == terminalSignal.callId
            val sameRoom = terminalRoom?.id == null || current.activeRoom?.id == terminalRoom.id
            if (current.status == status && sameCall && sameRoom) {
                resetToIdle()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.value = _state.value.copy(durationSeconds = _state.value.durationSeconds + 1)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun startMissedTimeout(signal: CallSignal) {
        missedTimeoutJob?.cancel()
        missedTimeoutJob = viewModelScope.launch {
            delay(15_000)
            if (_state.value.signal?.callId == signal.callId && _state.value.status == CallUiStatus.Outgoing) {
                _state.value = _state.value.copy(message = "Trying to reach...")
            }
            delay(30_000)
            if (_state.value.signal?.callId == signal.callId && _state.value.status == CallUiStatus.Outgoing) {
                socketManager.missed(signal.copy(reason = "no_answer").toJson())
                terminal(CallUiStatus.Missed, "No answer", signal)
            }
        }
    }

    private fun startIncomingTimeout(signal: CallSignal) {
        missedTimeoutJob?.cancel()
        missedTimeoutJob = viewModelScope.launch {
            delay(45_000)
            val current = _state.value
            if (current.signal?.callId == signal.callId && current.status == CallUiStatus.Incoming) {
                currentUser?.id?.let { userId ->
                    socketManager.missed(signal.copy(fromUserId = userId, toUserId = signal.callerId, reason = "missed").toJson())
                }
                terminal(CallUiStatus.Missed, "Missed call", current.signal)
            }
        }
    }

    private fun startConnectionTimeout(signal: CallSignal) {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(30_000)
            val current = _state.value
            if (current.signal?.callId == signal.callId && current.status == CallUiStatus.Connecting) {
                val failed = activeSignal()?.copy(reason = "connection_timeout")
                _state.value = _state.value.copy(debugLastError = "connection_timeout", message = "Call connection timed out")
                addDebug("ANDROID: connection timeout while connecting")
                failed?.let { socketManager.failed(it.toJson()) }
                terminal(CallUiStatus.Failed, "Call connection timed out", current.signal)
            }
        }
    }

    private fun startIncomingMedia(signal: CallSignal) {
        val context = appContext
        if (context == null) {
            addDebug("ANDROID: startIncoming skipped reason=no_context")
            terminal(CallUiStatus.Failed, "Could not accept call", signal)
            return
        }
        if (incomingMediaStartCallId == signal.callId) {
            signal.offerSdp?.let {
                addDebug("ANDROID: acceptOffer called")
                callEngine.acceptOffer(it)
            }
            return
        }
        incomingMediaStartCallId = signal.callId
        runCatching {
            addDebug("ANDROID: startIncoming called hasOfferSdp=${!signal.offerSdp.isNullOrBlank()}")
            callEngine.startIncoming(context, signal.isVideo, signal.offerSdp)
        }.onFailure { error ->
            incomingMediaStartCallId = null
            addDebug("ANDROID: startIncoming failed error=${error.message}")
            terminal(CallUiStatus.Failed, error.message ?: "Could not accept call", signal)
        }
    }

    private fun scheduleHistoryRefresh(userId: String, delayMs: Long = 250L) {
        historyRefreshJob?.cancel()
        historyRefreshJob = viewModelScope.launch {
            delay(delayMs)
            loadHistory(userId)
        }
    }

    private fun activeSignal(): CallSignal? = _state.value.signal?.let { signal ->
        val self = currentUser?.id ?: signal.fromUserId
        signal.copy(fromUserId = self, toUserId = remoteUserId(signal, self))
    }

    private fun activeSignalWith(outgoing: NativeCallEngine.OutgoingSignal): CallSignal? {
        return activeSignal()?.copy(
            offerSdp = outgoing.sdp?.takeIf { outgoing.sdpType == "offer" },
            answerSdp = outgoing.sdp?.takeIf { outgoing.sdpType == "answer" },
            candidate = outgoing.candidate,
            sdpMid = outgoing.sdpMid,
            sdpMLineIndex = outgoing.sdpMLineIndex
        )
    }

    private fun remoteUserId(signal: CallSignal, selfId: String): String {
        return if (signal.callerId == selfId) signal.calleeId else signal.callerId
    }

    private fun incomingRingingMessage(signal: CallSignal): String {
        return if (signal.offerSdp.isNullOrBlank()) "Ringing..." else "Ready to answer"
    }

    private fun isCallOccupying(status: CallUiStatus): Boolean {
        return status in setOf(
            CallUiStatus.Outgoing,
            CallUiStatus.Incoming,
            CallUiStatus.Connecting,
            CallUiStatus.Active
        )
    }

    private fun callStartErrorMessage(raw: String?): String {
        val message = raw.orEmpty()
        return when {
            message.contains("Failed to connect", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ->
                "Could not create the Cloudflare call. Check Cloud Account and internet connectivity."
            message.isNotBlank() -> message
            else -> "Could not create call"
        }
    }

    private fun failureReason(message: String): String {
        val normalized = message.lowercase()
        return when {
            "permission" in normalized -> "permission_denied"
            "camera" in normalized -> "camera_unavailable"
            "microphone" in normalized -> "microphone_unavailable"
            "network" in normalized || "timeout" in normalized -> "network_lost"
            else -> "failed"
        }
    }

    private fun handleRoomCreated(payload: JSONObject) {
        val user = currentUser ?: return
        val room = payload.toRoomFromEvent() ?: return
        val callerId = payload.optString("fromUserId", room.hostId)
        if (callerId == user.id || !room.participantIds.contains(user.id)) return
        if (isCallOccupying(_state.value.status)) {
            socketManager.leaveRoom(
                JSONObject()
                    .put("roomId", room.id)
                    .put("fromUserId", user.id)
                    .put("userId", user.id)
                    .put("reason", "busy")
            )
            return
        }
        _state.value = CallUiState(
            status = CallUiStatus.Incoming,
            activeRoom = room,
            peerName = "Group call",
            message = "Incoming group ${room.type} call",
            socketConnected = socketManager.isConnected(),
            mediaPhase = CallMediaPhase.Ringing,
            roomParticipants = room.participantIds
        )
    }

    private fun handleRoomJoin(payload: JSONObject) {
        val room = payload.toRoomFromEvent() ?: return
        val current = _state.value.activeRoom ?: return
        if (current.id != room.id) return
        _state.value = _state.value.copy(
            activeRoom = room,
            roomParticipants = room.participantIds.ifEmpty { _state.value.roomParticipants }
        )
    }

    private fun handleRoomLeave(payload: JSONObject) {
        val current = _state.value.activeRoom ?: return
        val room = payload.toRoomFromEvent()
        val ended = payload.optBoolean("ended", room?.status == "ended")
        if (payload.optString("roomId", room?.id.orEmpty()) != current.id) return
        if (ended) {
            terminal(CallUiStatus.Ended, "Group call ended", null)
            return
        }
        val nextParticipants = room?.participantIds
            ?: _state.value.roomParticipants.filter { it != payload.optString("userId") }
        _state.value = _state.value.copy(
            activeRoom = room ?: current.copy(participantIds = nextParticipants),
            roomParticipants = nextParticipants
        )
    }

    override fun onCleared() {
        socketManager.disconnect()
        callEngine.dispose()
        stopTimer()
        IncomingCallRinger.stop(_state.value.signal?.callId ?: _state.value.activeRoom?.id)
        missedTimeoutJob?.cancel()
        connectionTimeoutJob?.cancel()
        terminalResetJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "HelloCallViewModel"
        val ACK_EVENTS = setOf(
            "call:start",
            "call:offer",
            "call:answer",
            "call:ice-candidate",
            "call:accepted",
            "call:connected",
            "call:busy",
            "call:missed",
            "call:unavailable",
            "call:declined",
            "call:failed",
            "call:ended"
        )
    }
}
