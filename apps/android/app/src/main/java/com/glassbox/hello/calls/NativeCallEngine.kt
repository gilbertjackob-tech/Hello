package com.glassbox.hello.calls

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.glassbox.hello.debug.AppLog as Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import com.glassbox.hello.core.AppConfig
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class NativeCallEngine : CallMediaEngine {
    data class OutgoingSignal(
        val sdp: String? = null,
        val sdpType: String? = null,
        val candidate: String? = null,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null
    )

    override var onOfferReady: ((OutgoingSignal) -> Unit)? = null
    override var onAnswerReady: ((OutgoingSignal) -> Unit)? = null
    override var onIceCandidateReady: ((OutgoingSignal) -> Unit)? = null
    override var onPhaseChanged: ((CallMediaPhase) -> Unit)? = null
    override var onError: ((String) -> Unit)? = null
    override var onDebugEvent: ((String) -> Unit)? = null

    private var initialized = false
    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private val queuedRemoteIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var wantsVideo = false
    private var preferredVideoProfile = VideoQualityProfile.Auto
    private val configuredIceServers = mutableListOf<PeerConnection.IceServer>()
    private val mediaStartLock = Any()
    @Volatile
    private var mediaStarting = false
    @Volatile
    private var queuedRemoteOfferSdp: String? = null
    @Volatile
    private var queuedRemoteOfferRetryCount = 0
    @Volatile
    private var queuedRemoteAnswerSdp: String? = null
    @Volatile
    private var queuedRemoteAnswerRetryCount = 0
    @Volatile
    private var pendingLocalOfferSignal: OutgoingSignal? = null
    @Volatile
    private var pendingLocalOfferRetryCount = 0
    @Volatile
    private var remoteAnswerApplyInProgress = false
    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var previousMicrophoneMute: Boolean? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val peerThread = HandlerThread("HelloCallPeerThread").also { it.start() }
    private val peerHandler = Handler(peerThread.looper)

    override fun setIceServers(iceServers: List<CallIceServer>) {
        synchronized(configuredIceServers) {
            configuredIceServers.clear()
            iceServers.forEach { server ->
                server.urls.forEach { url ->
                    val builder = PeerConnection.IceServer.builder(url)
                    if (!server.username.isNullOrBlank() || !server.credential.isNullOrBlank()) {
                        builder.setUsername(server.username.orEmpty())
                        builder.setPassword(server.credential.orEmpty())
                    }
                    configuredIceServers += builder.createIceServer()
                }
            }
        }
        debug("ice_config count=${iceServers.sumOf { it.urls.size }} forceRelay=${AppConfig.WEBRTC_FORCE_RELAY}")
    }

    override fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        val startedAt = SystemClock.elapsedRealtime()
        localRenderer = renderer
        val base = eglBase
        if (base == null) {
            debugCamera("localRenderer deferred until egl is ready")
            Log.d(TAG, "local_renderer_attach_deferred elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return
        }
        initializeRenderer(renderer, base)
        renderer.setMirror(true)
        videoTrack?.let { track ->
            try {
                track.addSink(renderer)
                debugCamera("local renderer sink attached after video track creation")
            } catch (error: Exception) {
                debugCamera("failed to attach local renderer sink: ${error.message}", error)
            }
        }
        Log.d(TAG, "local_renderer_attach_complete hasVideoTrack=${videoTrack != null} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    override fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        val startedAt = SystemClock.elapsedRealtime()
        remoteRenderer = renderer
        val base = eglBase
        if (base == null) {
            debug("remoteRenderer deferred until egl is ready")
            Log.d(TAG, "remote_renderer_attach_deferred elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return
        }
        initializeRenderer(renderer, base)
        renderer.setMirror(false)
        remoteVideoTrack?.let { track ->
            try {
                track.addSink(renderer)
                debug("remote renderer sink attached after remote track creation")
            } catch (error: Exception) {
                debug("failed to attach remote renderer sink: ${error.message}", error)
            }
        }
        Log.d(TAG, "remote_renderer_attach_complete hasRemoteTrack=${remoteVideoTrack != null} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    override fun startOutgoing(context: Context?, isVideo: Boolean) {
        val startedAt = SystemClock.elapsedRealtime()
        val appContext = context?.applicationContext ?: return fail("Android context unavailable")
        if (!beginMediaStart("outgoing")) return
        wantsVideo = isVideo
        queuedRemoteOfferSdp = null
        queuedRemoteAnswerSdp = null
        queuedRemoteAnswerRetryCount = 0
        pendingLocalOfferSignal = null
        pendingLocalOfferRetryCount = 0
        if (!preparePeer(appContext, isVideo)) {
            endMediaStart()
            return
        }
        endMediaStart()
        Log.d(TAG, "media_start_outgoing_ready isVideo=$isVideo elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
        peerHandler.post {
            createOutgoingOfferOnPeerThread()
        }
    }

    private fun createOutgoingOfferOnPeerThread() {
        val pc = peerConnection ?: return fail("WebRTC peer connection unavailable")
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                debug("sdp_op=createOffer success ${sdpSummary(description)} ${peerSummary(peerConnection)}")
                logSdpDiagnostics("createOffer", description.description)
                if (description.description.isNullOrBlank()) {
                    fail("createOffer produced empty SDP")
                    return
                }
                debug("sdp_op=setLocalDescription.offer start ${sdpSummary(description)} ${peerSummary(peerConnection)}")
                peerHandler.post {
                    pc.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            val committedOffer = pc.localDescription
                            debug("sdp_op=setLocalDescription.offer success committed=${sdpSummary(committedOffer)} ${peerSummary(pc)}")
                            val offerSdp = normalizeSdp(committedOffer?.description ?: description.description)
                            logSdpDiagnostics("localOfferCommitted", offerSdp)
                            pendingLocalOfferSignal = OutgoingSignal(
                                sdp = offerSdp,
                                sdpType = committedOffer?.type?.canonicalForm() ?: description.type.canonicalForm()
                            )
                            pendingLocalOfferRetryCount = 0
                            dispatchPendingLocalOfferIfReady("local_offer_set")
                        }

                        override fun onSetFailure(error: String) {
                            failSdpOperation("setLocalDescription.offer", error, description)
                        }
                    }, description)
                }
            }

            override fun onCreateFailure(error: String) = failSdpOperation("createOffer", error, null)
        }, mediaConstraints())
    }

    override fun startIncoming(context: Context?, isVideo: Boolean, offerSdp: String?) {
        val startedAt = SystemClock.elapsedRealtime()
        val appContext = context?.applicationContext ?: return fail("Android context unavailable")
        if (!beginMediaStart("incoming")) return
        wantsVideo = isVideo
        queuedRemoteOfferRetryCount = 0
        if (!offerSdp.isNullOrBlank()) queuedRemoteOfferSdp = offerSdp
        if (!preparePeer(appContext, isVideo)) {
            endMediaStart()
            return
        }
        endMediaStart()
        val pendingOffer = queuedRemoteOfferSdp
        if (!pendingOffer.isNullOrBlank()) {
            queuedRemoteOfferSdp = null
            acceptOffer(pendingOffer)
        } else {
            onPhaseChanged?.invoke(CallMediaPhase.Connecting)
        }
        Log.d(TAG, "media_start_incoming_ready isVideo=$isVideo hasOffer=${!offerSdp.isNullOrBlank()} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    override fun acceptOffer(offerSdp: String) {
        peerHandler.post {
            acceptOfferOnPeerThread(offerSdp)
        }
    }

    private fun acceptOfferOnPeerThread(offerSdp: String) {
        val normalizedOfferSdp = normalizeSdp(offerSdp)
        if (normalizedOfferSdp.isBlank()) {
            fail("Received empty offer SDP")
            return
        }
        val pc = peerConnection ?: run {
            queuedRemoteOfferSdp = normalizedOfferSdp
            return
        }
        if (pc.signalingState() == PeerConnection.SignalingState.CLOSED) {
            queuedRemoteOfferSdp = normalizedOfferSdp
            scheduleRemoteOfferRetry("peer_closed")
            return
        }
        if (normalizedOfferSdp != offerSdp) {
            debug("sdp_op=setRemoteDescription.offer normalized originalLength=${offerSdp.length} normalizedLength=${normalizedOfferSdp.length}")
        }
        logSdpDiagnostics("remoteOfferBeforeSet", normalizedOfferSdp)
        val offer = SessionDescription(SessionDescription.Type.OFFER, normalizedOfferSdp)
        debug("sdp_op=setRemoteDescription.offer start ${sdpSummary(offer)} ${peerSummary(pc)}")
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                debug("sdp_op=setRemoteDescription.offer success ${peerSummary(pc)}")
                remoteDescriptionSet = true
                queuedRemoteOfferSdp = null
                queuedRemoteOfferRetryCount = 0
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(description: SessionDescription) {
                        debug("sdp_op=createAnswer success ${sdpSummary(description)} ${peerSummary(pc)}")
                        logSdpDiagnostics("createAnswer", description.description)
                        if (description.description.isNullOrBlank()) {
                            fail("createAnswer produced empty SDP")
                            return
                        }
                        debug("sdp_op=setLocalDescription.answer start ${sdpSummary(description)} ${peerSummary(pc)}")
                        pc.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                val committedAnswer = pc.localDescription
                                debug("sdp_op=setLocalDescription.answer success committed=${sdpSummary(committedAnswer)} ${peerSummary(pc)}")
                                val answerSdp = normalizeSdp(committedAnswer?.description ?: description.description)
                                logSdpDiagnostics("localAnswerCommitted", answerSdp)
                                flushQueuedIce()
                                onAnswerReady?.invoke(
                                    OutgoingSignal(
                                        sdp = answerSdp,
                                        sdpType = committedAnswer?.type?.canonicalForm() ?: description.type.canonicalForm()
                                    )
                                )
                            }

                            override fun onSetFailure(error: String) {
                                failSdpOperation("setLocalDescription.answer", error, description)
                            }
                        }, description)
                    }

                    override fun onCreateFailure(error: String) = failSdpOperation("createAnswer", error, null)
                }, mediaConstraints())
            }

            override fun onSetFailure(error: String) {
                val normalizedError = error.trim()
                if (shouldRetryRemoteOffer(normalizedError)) {
                    queuedRemoteOfferSdp = normalizedOfferSdp
                    Log.w(
                        TAG,
                        "[CALL_TRACE] android queued_offer set_remote_failed_retry error=$normalizedError attempt=${queuedRemoteOfferRetryCount + 1}"
                    )
                    scheduleRemoteOfferRetry("set_remote_failure")
                    return
                }
                failSdpOperation("setRemoteDescription.offer", error, offer)
            }
        }, offer)
    }

    override fun acceptAnswer(answerSdp: String) {
        peerHandler.post {
            acceptAnswerOnPeerThread(answerSdp)
        }
    }

    private fun acceptAnswerOnPeerThread(answerSdp: String) {
        val normalizedAnswerSdp = normalizeSdp(answerSdp)
        if (normalizedAnswerSdp.isBlank()) {
            fail("Received empty answer SDP")
            return
        }
        if (normalizedAnswerSdp != answerSdp) {
            debug("sdp_op=setRemoteDescription.answer normalized originalLength=${answerSdp.length} normalizedLength=${normalizedAnswerSdp.length}")
        }
        logSdpDiagnostics("remoteAnswerReceived", normalizedAnswerSdp)
        queuedRemoteAnswerSdp = normalizedAnswerSdp
        val pc = peerConnection ?: run {
            Log.w(TAG, "[CALL_TRACE] android queued_answer peer_not_ready")
            return
        }
        val remoteDescription = pc.remoteDescription
        if (remoteDescription?.type == SessionDescription.Type.ANSWER) {
            Log.d(
                TAG,
                "[CALL_TRACE] android ignored duplicate/late answer remote=${sdpSummary(remoteDescription)} signalingState=${pc.signalingState()}"
            )
            queuedRemoteAnswerSdp = null
            queuedRemoteAnswerRetryCount = 0
            return
        }
        if (pc.signalingState() == PeerConnection.SignalingState.STABLE) {
            Log.d(
                TAG,
                "[CALL_TRACE] android queued_answer peer_stable_without_answer local=${sdpSummary(pc.localDescription)} remote=${sdpSummary(remoteDescription)}"
            )
            scheduleRemoteAnswerRetry("stable_without_remote_answer")
            return
        }
        if (!canApplyRemoteAnswer(pc)) {
            Log.d(
                TAG,
                "[CALL_TRACE] android queued_answer local_offer_not_ready signalingState=${pc.signalingState()} hasLocalDescription=${pc.localDescription != null}"
            )
            scheduleRemoteAnswerRetry("local_offer_not_ready")
            return
        }
        if (remoteAnswerApplyInProgress) {
            Log.d(TAG, "[CALL_TRACE] android queued_answer apply_in_progress")
            return
        }
        remoteAnswerApplyInProgress = true
        val answer = SessionDescription(SessionDescription.Type.ANSWER, normalizedAnswerSdp)
        logSdpDiagnostics("remoteAnswerBeforeSet", answer.description)
        debug("sdp_op=setRemoteDescription.answer start ${sdpSummary(answer)} ${peerSummary(pc)}")
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteAnswerApplyInProgress = false
                debug("sdp_op=setRemoteDescription.answer success ${peerSummary(pc)}")
                remoteDescriptionSet = true
                queuedRemoteAnswerSdp = null
                queuedRemoteAnswerRetryCount = 0
                flushQueuedIce()
            }

            override fun onSetFailure(error: String) {
                remoteAnswerApplyInProgress = false
                val normalizedError = error.trim()
                if (shouldRetryRemoteAnswer(normalizedError)) {
                    Log.w(
                        TAG,
                        "[CALL_TRACE] android queued_answer set_remote_failed_retry error=$normalizedError attempt=${queuedRemoteAnswerRetryCount + 1}"
                    )
                    scheduleRemoteAnswerRetry("set_remote_failure")
                    return
                }
                failSdpOperation("setRemoteDescription.answer", error, answer)
            }
        }, answer)
    }

    override fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex ?: 0, candidate)
        val pc = peerConnection
        if (pc != null && pc.remoteDescription != null) {
            runCatching {
                pc.addIceCandidate(ice)
            }.onFailure { error ->
                debug("addIceCandidate failed error=${error.message}", error)
            }
        } else {
            synchronized(queuedRemoteIce) {
                queuedRemoteIce += ice
            }
            debug("queued remote ICE candidate (remote description not set yet)")
        }
    }

    private fun canApplyRemoteAnswer(pc: PeerConnection): Boolean {
        if (pendingLocalOfferSignal != null) return false
        val localDescription = pc.localDescription ?: return false
        if (localDescription.type != SessionDescription.Type.OFFER) return false
        if (localDescription.description.isNullOrBlank()) return false
        return pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER
    }

    private fun canDispatchLocalOffer(pc: PeerConnection): Boolean {
        val localDescription = pc.localDescription ?: return false
        if (localDescription.type != SessionDescription.Type.OFFER) return false
        if (localDescription.description.isNullOrBlank()) return false
        return pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER
    }

    private fun shouldRetryRemoteOffer(error: String): Boolean {
        if (queuedRemoteOfferRetryCount >= MAX_REMOTE_OFFER_RETRIES) return false
        return error.contains("SessionDescription is NULL", ignoreCase = true)
    }

    private fun scheduleRemoteOfferRetry(reason: String) {
        val pendingOffer = queuedRemoteOfferSdp?.takeIf { it.isNotBlank() } ?: return
        if (queuedRemoteOfferRetryCount >= MAX_REMOTE_OFFER_RETRIES) {
            fail("Remote offer could not be applied after retrying")
            return
        }
        queuedRemoteOfferRetryCount += 1
        val attempt = queuedRemoteOfferRetryCount
        Log.d(TAG, "[CALL_TRACE] android schedule_offer_apply_retry reason=$reason attempt=$attempt")
        mainHandler.postDelayed({
            val currentOffer = queuedRemoteOfferSdp
            if (currentOffer.isNullOrBlank() || currentOffer != pendingOffer) return@postDelayed
            peerHandler.post { acceptOfferOnPeerThread(currentOffer) }
        }, REMOTE_OFFER_RETRY_DELAY_MS)
    }

    private fun dispatchPendingLocalOfferIfReady(trigger: String) {
        val pendingOffer = pendingLocalOfferSignal ?: return
        val pc = peerConnection ?: return
        if (!canDispatchLocalOffer(pc)) {
            Log.d(
                TAG,
                "[CALL_TRACE] android pending_offer deferred trigger=$trigger signalingState=${pc.signalingState()} hasLocalDescription=${pc.localDescription != null}"
            )
            schedulePendingLocalOfferRetry("offer_not_committed")
            return
        }
        pendingLocalOfferSignal = null
        pendingLocalOfferRetryCount = 0
        Log.d(TAG, "[CALL_TRACE] android dispatch_offer trigger=$trigger")
        onOfferReady?.invoke(pendingOffer)
        applyQueuedRemoteAnswerIfReady("offer_dispatched")
    }

    private fun schedulePendingLocalOfferRetry(reason: String) {
        if (pendingLocalOfferSignal == null) return
        if (pendingLocalOfferRetryCount >= MAX_LOCAL_OFFER_RETRIES) {
            fail("Local offer could not be committed for signaling")
            return
        }
        pendingLocalOfferRetryCount += 1
        val attempt = pendingLocalOfferRetryCount
        Log.d(TAG, "[CALL_TRACE] android schedule_offer_retry reason=$reason attempt=$attempt")
        mainHandler.postDelayed({
            if (pendingLocalOfferSignal == null) return@postDelayed
            dispatchPendingLocalOfferIfReady("offer_retry_$attempt")
        }, LOCAL_OFFER_RETRY_DELAY_MS)
    }

    private fun applyQueuedRemoteAnswerIfReady(trigger: String) {
        val pendingAnswer = queuedRemoteAnswerSdp?.takeIf { it.isNotBlank() } ?: return
        val pc = peerConnection ?: return
        if (!canApplyRemoteAnswer(pc)) {
            Log.d(
                TAG,
                "[CALL_TRACE] android queued_answer deferred trigger=$trigger signalingState=${pc.signalingState()} hasLocalDescription=${pc.localDescription != null}"
            )
            return
        }
        queuedRemoteAnswerRetryCount = 0
        Log.d(TAG, "[CALL_TRACE] android apply_queued_answer trigger=$trigger")
        acceptAnswer(pendingAnswer)
    }

    private fun shouldRetryRemoteAnswer(error: String): Boolean {
        if (queuedRemoteAnswerRetryCount >= MAX_REMOTE_ANSWER_RETRIES) return false
        return error.contains("SessionDescription is NULL", ignoreCase = true)
    }

    private fun scheduleRemoteAnswerRetry(reason: String) {
        val pendingAnswer = queuedRemoteAnswerSdp?.takeIf { it.isNotBlank() } ?: return
        if (queuedRemoteAnswerRetryCount >= MAX_REMOTE_ANSWER_RETRIES) {
            fail("Remote answer could not be applied after retrying")
            return
        }
        queuedRemoteAnswerRetryCount += 1
        val attempt = queuedRemoteAnswerRetryCount
        Log.d(TAG, "[CALL_TRACE] android schedule_answer_retry reason=$reason attempt=$attempt")
        mainHandler.postDelayed({
            val currentAnswer = queuedRemoteAnswerSdp
            if (currentAnswer.isNullOrBlank() || currentAnswer != pendingAnswer) return@postDelayed
            peerHandler.post { acceptAnswerOnPeerThread(currentAnswer) }
        }, REMOTE_ANSWER_RETRY_DELAY_MS)
    }

    override fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    override fun setCameraOff(off: Boolean) {
        videoTrack?.setEnabled(!off)
    }

    override fun setSpeaker(context: Context, on: Boolean) {
        val audio = ensureCallAudio(context, on)
        audio.isSpeakerphoneOn = on
        debug("speaker=${if (on) "on" else "off"}")
    }

    override fun setPreferredVideoProfile(profile: VideoQualityProfile) {
        val startedAt = SystemClock.elapsedRealtime()
        if (preferredVideoProfile == profile) {
            Log.d(TAG, "video_profile_skip profile=${profile.name} reason=already_selected")
            return
        }
        preferredVideoProfile = profile
        peerHandler.post { applyCaptureProfile(profile) }
        Log.d(TAG, "video_profile_schedule profile=${profile.name} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    override fun switchCamera() {
        val startedAt = SystemClock.elapsedRealtime()
        videoCapturer?.switchCamera(null)
        Log.d(TAG, "camera_switch_scheduled hasCapturer=${videoCapturer != null} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    override fun dispose() {
        mainHandler.removeCallbacksAndMessages(null)
        onPhaseChanged?.invoke(CallMediaPhase.Closed)
        localRenderer?.let { renderer ->
            try {
                videoTrack?.removeSink(renderer)
            } catch (error: Exception) {
                debugCamera("failed to remove local renderer sink: ${error.message}", error)
            }
        }
        videoTrack?.dispose()
        audioTrack?.dispose()
        videoCapturer?.let {
            try {
                it.stopCapture()
            } catch (_: Exception) {
            }
            it.dispose()
        }
        textureHelper?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        releaseCallAudio()
        localRenderer = null
        remoteRenderer = null
        peerConnection = null
        videoCapturer = null
        videoTrack = null
        remoteVideoTrack = null
        audioTrack = null
        videoSource = null
        audioSource = null
        textureHelper = null
        queuedRemoteIce.clear()
        remoteDescriptionSet = false
        queuedRemoteOfferSdp = null
        queuedRemoteOfferRetryCount = 0
        queuedRemoteAnswerSdp = null
        queuedRemoteAnswerRetryCount = 0
        pendingLocalOfferSignal = null
        pendingLocalOfferRetryCount = 0
        remoteAnswerApplyInProgress = false
        endMediaStart()
    }

    private fun preparePeer(context: Context, isVideo: Boolean): Boolean {
        if (!initialize(context)) return false
        disposePeerOnly()
        onPhaseChanged?.invoke(CallMediaPhase.Preparing)
        eglBase?.let { base ->
            localRenderer?.let { renderer ->
                initializeRenderer(renderer, base)
                renderer.setMirror(true)
            }
            remoteRenderer?.let { renderer ->
                initializeRenderer(renderer, base)
                renderer.setMirror(false)
            }
        }
        val factory = factory ?: return failAndReturn("WebRTC factory unavailable")
        val iceServers = synchronized(configuredIceServers) {
            configuredIceServers.toList().ifEmpty {
                listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            }
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            if (AppConfig.WEBRTC_FORCE_RELAY) {
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
                debug("iceTransportPolicy=relay")
            }
        }
        peerConnection = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    onIceCandidateReady?.invoke(
                        OutgoingSignal(
                            candidate = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex
                        )
                    )
                }

                override fun onAddStream(stream: MediaStream) {
                    stream.videoTracks.firstOrNull()?.let { track ->
                        remoteVideoTrack = track
                        remoteRenderer?.let { renderer ->
                            track.addSink(renderer)
                        } ?: Log.w(TAG, "[CALL_TRACE] android remoteRenderer not attached yet; remote stream sink will attach later")
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver) {
                    (transceiver.receiver.track() as? AudioTrack)?.let { track ->
                        track.setEnabled(true)
                        debug("remoteAudioTrack=received")
                    }
                    (transceiver.receiver.track() as? VideoTrack)?.let { track ->
                        remoteVideoTrack = track
                        remoteRenderer?.let { renderer ->
                            track.addSink(renderer)
                        } ?: Log.w(TAG, "[CALL_TRACE] android remoteRenderer not attached yet; remote track sink will attach later")
                    }
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    debug("iceState=$state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> onPhaseChanged?.invoke(CallMediaPhase.Connected)
                        PeerConnection.IceConnectionState.CHECKING -> onPhaseChanged?.invoke(CallMediaPhase.Connecting)
                        PeerConnection.IceConnectionState.DISCONNECTED -> onPhaseChanged?.invoke(CallMediaPhase.Reconnecting)
                        PeerConnection.IceConnectionState.FAILED -> fail("Call network connection failed")
                        PeerConnection.IceConnectionState.CLOSED -> onPhaseChanged?.invoke(CallMediaPhase.Closed)
                        else -> Unit
                    }
                }

                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                    debug("peerState=$state")
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> onPhaseChanged?.invoke(CallMediaPhase.Connected)
                        PeerConnection.PeerConnectionState.CONNECTING -> onPhaseChanged?.invoke(CallMediaPhase.Connecting)
                        PeerConnection.PeerConnectionState.DISCONNECTED -> onPhaseChanged?.invoke(CallMediaPhase.Reconnecting)
                        PeerConnection.PeerConnectionState.FAILED -> fail("Call network connection failed")
                        PeerConnection.PeerConnectionState.CLOSED -> onPhaseChanged?.invoke(CallMediaPhase.Closed)
                        else -> Unit
                    }
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState) {
                    debug("signalingState=$state")
                    if (state == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                        dispatchPendingLocalOfferIfReady("signaling_change")
                        applyQueuedRemoteAnswerIfReady("signaling_change")
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                override fun onRemoveStream(stream: MediaStream) = Unit
                override fun onDataChannel(channel: DataChannel) = Unit
                override fun onRenegotiationNeeded() = Unit
            }
        )
        if (peerConnection == null) {
            return failAndReturn("WebRTC peer connection unavailable")
        }
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            disposePeerOnly()
            return failAndReturn("Microphone permission is needed for calls")
        }
        ensureCallAudio(context, speakerOn = true)
        try {
            audioSource = factory.createAudioSource(MediaConstraints())
            audioTrack = factory.createAudioTrack("hello_audio", audioSource).also {
                it.setEnabled(true)
                peerConnection?.addTrack(it, listOf("hello"))
                debug("localAudioTrack=created")
            }
        } catch (error: Exception) {
            disposePeerOnly()
            return failAndReturn("Could not start microphone for call")
        }
        if (isVideo) {
            if (!startVideoCapture(context, factory)) return false
        }
        onPhaseChanged?.invoke(CallMediaPhase.Connecting)
        queuedRemoteAnswerSdp?.takeIf { it.isNotBlank() }?.let { acceptAnswer(it) }
        return true
    }

    private fun initialize(context: Context): Boolean {
        if (initialized) return true
        return try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            val base = EglBase.create()
            val encoderFactory = DefaultVideoEncoderFactory(base.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(base.eglBaseContext)
            val peerFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            eglBase = base
            factory = peerFactory
            initialized = true
            true
        } catch (error: Exception) {
            Log.e(TAG, "[CALL_TRACE] android initialize failed: ${error.message}", error)
            fail("Could not initialize call engine")
            false
        }
    }

    private fun disposePeerOnly() {
        disposeVideoOnly()
        audioTrack?.dispose()
        audioSource?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        releaseCallAudio()
        peerConnection = null
        audioTrack = null
        audioSource = null
        queuedRemoteIce.clear()
        remoteDescriptionSet = false
        queuedRemoteOfferRetryCount = 0
        remoteAnswerApplyInProgress = false
    }

    private fun disposeVideoOnly() {
        localRenderer?.let { renderer ->
            try {
                videoTrack?.removeSink(renderer)
            } catch (error: Exception) {
                Log.w(TAG, "[CALL_CAMERA] failed to remove local renderer sink: ${error.message}", error)
            }
        }
        videoTrack?.dispose()
        videoCapturer?.let {
            try {
                it.stopCapture()
            } catch (_: Exception) {
            }
            it.dispose()
        }
        textureHelper?.dispose()
        videoSource?.dispose()
        videoCapturer = null
        videoTrack = null
        remoteVideoTrack = null
        videoSource = null
        textureHelper = null
    }

    private fun startVideoCapture(context: Context, factory: PeerConnectionFactory): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            disposePeerOnly()
            return failAndReturn("Camera permission is needed for video calls")
        }
        val base = eglBase ?: run {
            disposePeerOnly()
            return failAndReturn("Video engine is unavailable")
        }
        val capturers = createVideoCapturers(context)
        if (capturers.isEmpty()) {
            disposePeerOnly()
            return failAndReturn("No camera is available")
        }
        val failureReasons = mutableListOf<String>()
        for ((index, candidate) in capturers.withIndex()) {
            val capturer = candidate.capturer
            try {
                videoCapturer = capturer
                val createdVideoSource = factory.createVideoSource(capturer.isScreencast)
                val createdTextureHelper = SurfaceTextureHelper.create("HelloCameraThread", base.eglBaseContext)
                videoSource = createdVideoSource
                textureHelper = createdTextureHelper
                capturer.initialize(createdTextureHelper, context, createdVideoSource.capturerObserver)
                if (!startCaptureWithFallback(candidate)) {
                    failureReasons += "${candidate.api}/${candidate.facing}/${candidate.deviceName}: no supported capture format started"
                    debugCamera("api=${candidate.api} device=${candidate.deviceName} facing=${candidate.facing} result=all_resolutions_failed")
                    disposeVideoOnly()
                    continue
                }
                videoTrack = factory.createVideoTrack("hello_video", createdVideoSource).also { track ->
                    localRenderer?.let { renderer ->
                        track.addSink(renderer)
                        debugCamera("local renderer sink attached during video track creation")
                    } ?: debugCamera("localRenderer not attached yet; preview sink will attach later")
                    peerConnection?.addTrack(track, listOf("hello"))
                }
                capturers.drop(index + 1).forEach { it.capturer.dispose() }
                return true
            } catch (error: Exception) {
                if (error is SecurityException) {
                    disposePeerOnly()
                    return failAndReturn("Camera permission is needed for video calls")
                }
                failureReasons += "${candidate.api}/${candidate.facing}/${candidate.deviceName}: ${error.message ?: error.javaClass.simpleName}"
                debugCamera("api=${candidate.api} device=${candidate.deviceName} facing=${candidate.facing} result=fail error=${error.message}", error)
            }
            disposeVideoOnly()
        }
        disposePeerOnly()
        val details = failureReasons.takeLast(4).joinToString("; ").ifBlank { "all camera attempts failed" }
        return failAndReturn("Camera is busy or unavailable after trying Camera2 and Camera1 front/back: $details")
    }

    private fun createVideoCapturers(context: Context): List<CameraCandidate> {
        val capturers = mutableListOf<CameraCandidate>()
        if (Camera2Enumerator.isSupported(context)) {
            capturers += createCapturers(Camera2Enumerator(context), "Camera2")
        }
        capturers += createCapturers(Camera1Enumerator(false), "Camera1")
        return capturers
    }

    private fun createCapturers(enumerator: org.webrtc.CameraEnumerator, api: String): List<CameraCandidate> {
        val orderedDevices = enumerator.deviceNames
            .sortedBy { if (enumerator.isFrontFacing(it)) 0 else 1 }
        val capturers = mutableListOf<CameraCandidate>()
        for (deviceName in orderedDevices) {
            val facing = if (enumerator.isFrontFacing(deviceName)) "front" else "back"
            val capturer = try {
                enumerator.createCapturer(deviceName, cameraEventsHandler(api, deviceName, facing))
            } catch (error: RuntimeException) {
                debugCamera("api=$api device=$deviceName facing=$facing result=create_fail error=${error.message}", error)
                null
            }
            if (capturer != null) capturers += CameraCandidate(api, deviceName, facing, capturer)
        }
        return capturers
    }

    private fun flushQueuedIce() {
        val pc = peerConnection
        if (pc == null || pc.remoteDescription == null) {
            debug("flushQueuedIce skipped: peer or remote description not ready")
            return
        }
        synchronized(queuedRemoteIce) {
            if (queuedRemoteIce.isNotEmpty()) {
                debug("flushing ${queuedRemoteIce.size} queued ICE candidates")
                queuedRemoteIce.forEach { pc.addIceCandidate(it) }
                queuedRemoteIce.clear()
            }
        }
    }

    private fun mediaConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (wantsVideo) "true" else "false"))
    }

    private fun ensureCallAudio(context: Context, speakerOn: Boolean): AudioManager {
        val audio = audioManager ?: (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also {
            audioManager = it
            previousAudioMode = it.mode
            previousSpeakerphoneOn = it.isSpeakerphoneOn
            previousMicrophoneMute = it.isMicrophoneMute
        }
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isMicrophoneMute = false
        audio.isSpeakerphoneOn = speakerOn
        debug("audioRoute mode=communication speaker=$speakerOn")
        return audio
    }

    private fun releaseCallAudio() {
        val audio = audioManager ?: return
        previousMicrophoneMute?.let { audio.isMicrophoneMute = it }
        previousSpeakerphoneOn?.let { audio.isSpeakerphoneOn = it }
        previousAudioMode?.let { audio.mode = it }
        debug("audioRoute=restored")
        audioManager = null
        previousAudioMode = null
        previousSpeakerphoneOn = null
        previousMicrophoneMute = null
    }

    private fun startCaptureWithFallback(candidate: CameraCandidate): Boolean {
        val formats = captureFormatsFor(preferredVideoProfile)
        for ((width, height, fps) in formats) {
            try {
                debugCamera("api=${candidate.api} device=${candidate.deviceName} facing=${candidate.facing} resolution=${width}x$height fps=$fps result=attempt")
                candidate.capturer.startCapture(width, height, fps)
                debugCamera("api=${candidate.api} device=${candidate.deviceName} facing=${candidate.facing} resolution=${width}x$height fps=$fps result=success")
                return true
            } catch (error: Exception) {
                debugCamera("api=${candidate.api} device=${candidate.deviceName} facing=${candidate.facing} resolution=${width}x$height fps=$fps result=fail error=${error.message}", error)
            }
        }
        return false
    }

    private fun applyCaptureProfile(profile: VideoQualityProfile) {
        if (preferredVideoProfile != profile) return
        val capturer = videoCapturer ?: return
        val formats = captureFormatsFor(profile)
        for ((width, height, fps) in formats) {
            try {
                runCatching { capturer.stopCapture() }
                debugCamera("quality=${profile.name} resolution=${width}x$height fps=$fps result=reconfigure_attempt")
                capturer.startCapture(width, height, fps)
                debugCamera("quality=${profile.name} resolution=${width}x$height fps=$fps result=reconfigure_success")
                return
            } catch (error: Exception) {
                debugCamera("quality=${profile.name} resolution=${width}x$height fps=$fps result=reconfigure_fail error=${error.message}", error)
            }
        }
    }

    private fun captureFormatsFor(profile: VideoQualityProfile): List<Triple<Int, Int, Int>> {
        return when (profile) {
            VideoQualityProfile.DataSaver -> listOf(
                Triple(320, 240, 12),
                Triple(480, 360, 12),
                Triple(640, 480, 12)
            )
            VideoQualityProfile.Balanced -> listOf(
                Triple(640, 480, 15),
                Triple(960, 540, 15),
                Triple(1280, 720, 15)
            )
            VideoQualityProfile.Hd -> listOf(
                Triple(1280, 720, 24),
                Triple(960, 540, 20),
                Triple(640, 480, 18)
            )
            VideoQualityProfile.Auto -> listOf(
                Triple(960, 540, 15),
                Triple(640, 480, 15),
                Triple(1280, 720, 18),
                Triple(320, 240, 12)
            )
        }
    }

    private fun cameraEventsHandler(api: String, deviceName: String, facing: String) = object : CameraVideoCapturer.CameraEventsHandler {
        override fun onCameraError(errorDescription: String) {
            debugCamera("api=$api device=$deviceName facing=$facing result=async_error error=$errorDescription")
            fail("Camera error: $errorDescription")
        }

        override fun onCameraDisconnected() {
            debugCamera("api=$api device=$deviceName facing=$facing result=disconnected")
            fail("Camera disconnected")
        }

        override fun onCameraFreezed(errorDescription: String) {
            debugCamera("api=$api device=$deviceName facing=$facing result=frozen error=$errorDescription")
            fail("Camera frozen: $errorDescription")
        }

        override fun onCameraOpening(cameraName: String) {
            debugCamera("api=$api device=$cameraName facing=$facing result=opening")
        }

        override fun onFirstFrameAvailable() {
            debugCamera("api=$api device=$deviceName facing=$facing result=first_frame")
        }

        override fun onCameraClosed() {
            debugCamera("api=$api device=$deviceName facing=$facing result=closed")
        }
    }

    private fun fail(message: String) {
        debug("error=$message")
        onPhaseChanged?.invoke(CallMediaPhase.Error)
        onError?.invoke(message)
    }

    private fun failSdpOperation(operation: String, error: String, description: SessionDescription?) {
        val message = "$operation failed: ${error.ifBlank { "unknown SDP error" }}"
        logSdpDiagnostics("${operation}.failure", description?.description)
        debug("sdp_op=$operation failure error=${error.ifBlank { "unknown" }} ${sdpSummary(description)} ${peerSummary(peerConnection)}")
        fail(message)
    }

    private fun sdpSummary(description: SessionDescription?): String {
        val type = description?.type?.canonicalForm() ?: "none"
        val sdp = description?.description
        val length = sdp?.length ?: -1
        val hasAudio = sdp?.contains("m=audio") == true
        val hasVideo = sdp?.contains("m=video") == true
        val fingerprint = sdp?.contains("a=fingerprint") == true
        val iceUfrag = sdp?.contains("a=ice-ufrag") == true
        return "sdpType=$type sdpLength=$length hasAudio=$hasAudio hasVideo=$hasVideo hasFingerprint=$fingerprint hasIceUfrag=$iceUfrag"
    }

    private fun normalizeSdp(sdp: String?): String {
        val raw = sdp.orEmpty()
        if (raw.isBlank()) return ""
        val normalizedLines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
            .split('\n')
            .joinToString("\r\n") { it.trimEnd() }
        return "$normalizedLines\r\n"
    }

    private fun logSdpDiagnostics(label: String, sdp: String?) {
        val raw = sdp.orEmpty()
        if (raw.isBlank()) {
            debug("sdp_diag=$label empty=true thread=${Thread.currentThread().name}")
            return
        }
        val normalized = normalizeSdp(raw)
        val lines = normalized
            .replace("\r\n", "\n")
            .trimEnd()
            .split('\n')
        val crlfCount = countOccurrences(raw, "\r\n")
        val lfCount = raw.count { it == '\n' }
        val bareLfCount = (lfCount - crlfCount).coerceAtLeast(0)
        val mediaLines = lines.filter { it.startsWith("m=") }
        val mids = lines.filter { it.startsWith("a=mid:") }
        val bundle = lines.firstOrNull { it.startsWith("a=group:BUNDLE") }.orEmpty()
        val setup = lines.filter { it.startsWith("a=setup:") }
        val directions = lines.filter {
            it == "a=sendrecv" || it == "a=sendonly" || it == "a=recvonly" || it == "a=inactive"
        }
        val codecs = lines.filter { it.startsWith("a=rtpmap:") }
        val fmtp = lines.filter { it.startsWith("a=fmtp:") }
        val extmaps = lines.filter { it.startsWith("a=extmap:") }
        val msids = lines.filter { it.startsWith("a=msid") }
        val candidates = lines.count { it.startsWith("a=candidate:") }
        val endOfCandidates = lines.count { it == "a=end-of-candidates" }
        val hasRtcpMux = lines.any { it == "a=rtcp-mux" }
        val hasRtcpRsize = lines.any { it == "a=rtcp-rsize" }
        val hasIcePwd = lines.any { it.startsWith("a=ice-pwd:") }
        val hasIceUfrag = lines.any { it.startsWith("a=ice-ufrag:") }
        val hasFingerprint = lines.any { it.startsWith("a=fingerprint:") }
        val hasSsrc = lines.any { it.startsWith("a=ssrc:") }
        val hasSsrcGroup = lines.any { it.startsWith("a=ssrc-group:") }
        debug(
            "sdp_diag=$label rawLength=${raw.length} normalizedLength=${normalized.length} " +
                "crlf=$crlfCount lf=$lfCount bareLf=$bareLfCount lines=${lines.size} " +
                "m=${clip(mediaLines.joinToString("|"))} mids=${clip(mids.joinToString("|"))} " +
                "bundle=${clip(bundle)} setup=${clip(setup.joinToString("|"))} directions=${clip(directions.joinToString("|"))} " +
                "codecs=${clip(codecs.joinToString("|"), 360)} fmtpCount=${fmtp.size} extmapCount=${extmaps.size} " +
                "msid=${clip(msids.joinToString("|"))} candidates=$candidates endOfCandidates=$endOfCandidates " +
                "rtcpMux=$hasRtcpMux rtcpRsize=$hasRtcpRsize icePwd=$hasIcePwd iceUfrag=$hasIceUfrag " +
                "fingerprint=$hasFingerprint ssrc=$hasSsrc ssrcGroup=$hasSsrcGroup thread=${Thread.currentThread().name}"
        )
        logSdpDump(label, normalized)
    }

    private fun logSdpDump(label: String, normalizedSdp: String) {
        val redacted = normalizedSdp
            .replace("\r\n", "\n")
            .lineSequence()
            .map { line ->
                when {
                    line.startsWith("a=ice-pwd:") -> "a=ice-pwd:<redacted>"
                    line.startsWith("a=ice-ufrag:") -> "a=ice-ufrag:<redacted>"
                    line.startsWith("a=fingerprint:") -> "a=fingerprint:<redacted>"
                    line.startsWith("a=candidate:") -> "a=candidate:<redacted>"
                    else -> line
                }
            }
            .joinToString("\\r\\n")
        redacted.chunked(850).forEachIndexed { index, chunk ->
            debug("sdp_dump=$label chunk=$index text=$chunk")
        }
    }

    private fun countOccurrences(text: String, token: String): Int {
        if (token.isEmpty()) return 0
        var count = 0
        var index = text.indexOf(token)
        while (index >= 0) {
            count += 1
            index = text.indexOf(token, index + token.length)
        }
        return count
    }

    private fun clip(value: String, limit: Int = 220): String {
        return if (value.length <= limit) value else "${value.take(limit)}..."
    }

    private fun peerSummary(pc: PeerConnection?): String {
        if (pc == null) return "peer=null"
        val local = pc.localDescription
        val remote = pc.remoteDescription
        return "signalingState=${pc.signalingState()} iceState=${pc.iceConnectionState()} peerState=${pc.connectionState()} local=${sdpSummary(local)} remote=${sdpSummary(remote)} thread=${Thread.currentThread().name}"
    }

    private fun debug(message: String, error: Throwable? = null) {
        if (error == null) {
            Log.d(TAG, "[CALL_TRACE] android $message")
        } else {
            Log.w(TAG, "[CALL_TRACE] android $message", error)
        }
        onDebugEvent?.invoke(message)
    }

    private fun debugCamera(message: String, error: Throwable? = null) {
        if (error == null) {
            Log.d(TAG, "[CALL_CAMERA] $message")
        } else {
            Log.w(TAG, "[CALL_CAMERA] $message", error)
        }
        onDebugEvent?.invoke("cameraStatus=$message")
    }

    private fun beginMediaStart(mode: String): Boolean {
        synchronized(mediaStartLock) {
            if (mediaStarting) {
                Log.d(TAG, "[CALL_TRACE] android media_start_skip reason=already_starting mode=$mode")
                return false
            }
            mediaStarting = true
            Log.d(TAG, "[CALL_TRACE] android media_start mode=$mode")
            return true
        }
    }

    private fun endMediaStart() {
        synchronized(mediaStartLock) {
            mediaStarting = false
        }
    }

    private fun failAndReturn(message: String): Boolean {
        fail(message)
        return false
    }

    private fun initializeRenderer(renderer: SurfaceViewRenderer, base: EglBase) {
        try {
            renderer.init(base.eglBaseContext, null)
        } catch (error: IllegalStateException) {
            debug("renderer init skipped error=${error.message}", error)
        } catch (error: RuntimeException) {
            debug("renderer init failed error=${error.message}", error)
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    private data class CameraCandidate(
        val api: String,
        val deviceName: String,
        val facing: String,
        val capturer: CameraVideoCapturer
    )

    private companion object {
        const val TAG = "HelloCallEngine"
        const val MAX_REMOTE_OFFER_RETRIES = 6
        const val REMOTE_OFFER_RETRY_DELAY_MS = 150L
        const val MAX_REMOTE_ANSWER_RETRIES = 6
        const val REMOTE_ANSWER_RETRY_DELAY_MS = 150L
        const val MAX_LOCAL_OFFER_RETRIES = 12
        const val LOCAL_OFFER_RETRY_DELAY_MS = 150L
    }
}
