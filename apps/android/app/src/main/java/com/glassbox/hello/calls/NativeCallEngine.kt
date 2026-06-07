package com.glassbox.hello.calls

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
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
    private var queuedRemoteAnswerSdp: String? = null
    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var previousMicrophoneMute: Boolean? = null

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
        val base = eglBase ?: return
        localRenderer = renderer
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
    }

    override fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        val base = eglBase ?: return
        remoteRenderer = renderer
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
    }

    override fun startOutgoing(context: Context?, isVideo: Boolean) {
        val appContext = context?.applicationContext ?: return fail("Android context unavailable")
        if (!beginMediaStart("outgoing")) return
        wantsVideo = isVideo
        queuedRemoteOfferSdp = null
        queuedRemoteAnswerSdp = null
        if (!preparePeer(appContext, isVideo)) {
            endMediaStart()
            return
        }
        endMediaStart()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        onOfferReady?.invoke(OutgoingSignal(sdp = description.description, sdpType = description.type.canonicalForm()))
                    }
                }, description)
            }

            override fun onCreateFailure(error: String) = fail(error)
        }, mediaConstraints())
    }

    override fun startIncoming(context: Context?, isVideo: Boolean, offerSdp: String?) {
        val appContext = context?.applicationContext ?: return fail("Android context unavailable")
        if (!beginMediaStart("incoming")) return
        wantsVideo = isVideo
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
    }

    override fun acceptOffer(offerSdp: String) {
        val pc = peerConnection ?: run {
            queuedRemoteOfferSdp = offerSdp
            return
        }
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushQueuedIce()
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(description: SessionDescription) {
                        pc.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                onAnswerReady?.invoke(OutgoingSignal(sdp = description.description, sdpType = description.type.canonicalForm()))
                            }
                        }, description)
                    }

                    override fun onCreateFailure(error: String) = fail(error)
                }, mediaConstraints())
            }

            override fun onSetFailure(error: String) = fail(error)
        }, offer)
    }

    override fun acceptAnswer(answerSdp: String) {
        val pc = peerConnection ?: run {
            Log.w(TAG, "[CALL_TRACE] android queued_answer peer_not_ready")
            queuedRemoteAnswerSdp = answerSdp
            return
        }
        if (remoteDescriptionSet && pc.signalingState() == PeerConnection.SignalingState.STABLE) {
            Log.d(TAG, "[CALL_TRACE] android ignored duplicate answer while stable")
            return
        }
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                queuedRemoteAnswerSdp = null
                flushQueuedIce()
            }

            override fun onSetFailure(error: String) = fail(error)
        }, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
    }

    override fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex ?: 0, candidate)
        if (remoteDescriptionSet) {
            peerConnection?.addIceCandidate(ice)
        } else {
            queuedRemoteIce += ice
        }
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
        preferredVideoProfile = profile
        applyCaptureProfile()
    }

    override fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    override fun dispose() {
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
        queuedRemoteAnswerSdp = null
        endMediaStart()
    }

    private fun preparePeer(context: Context, isVideo: Boolean): Boolean {
        initialize(context)
        disposePeerOnly()
        onPhaseChanged?.invoke(CallMediaPhase.Preparing)
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

                override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
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

    private fun initialize(context: Context) {
        if (initialized) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        eglBase = EglBase.create()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        initialized = true
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
                videoSource = factory.createVideoSource(capturer.isScreencast)
                textureHelper = SurfaceTextureHelper.create("HelloCameraThread", eglBase!!.eglBaseContext)
                capturer.initialize(textureHelper, context, videoSource!!.capturerObserver)
                if (!startCaptureWithFallback(candidate)) {
                    failureReasons += "${candidate.api}/${candidate.facing}/${candidate.deviceName}: no supported capture format started"
                    debugCamera("api=${candidate.api} device=${candidate.deviceName} facing=${candidate.facing} result=all_resolutions_failed")
                    disposeVideoOnly()
                    continue
                }
                videoTrack = factory.createVideoTrack("hello_video", videoSource).also { track ->
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
        queuedRemoteIce.forEach { peerConnection?.addIceCandidate(it) }
        queuedRemoteIce.clear()
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

    private fun applyCaptureProfile() {
        val capturer = videoCapturer ?: return
        val formats = captureFormatsFor(preferredVideoProfile)
        for ((width, height, fps) in formats) {
            try {
                runCatching { capturer.stopCapture() }
                debugCamera("quality=${preferredVideoProfile.name} resolution=${width}x$height fps=$fps result=reconfigure_attempt")
                capturer.startCapture(width, height, fps)
                debugCamera("quality=${preferredVideoProfile.name} resolution=${width}x$height fps=$fps result=reconfigure_success")
                return
            } catch (error: Exception) {
                debugCamera("quality=${preferredVideoProfile.name} resolution=${width}x$height fps=$fps result=reconfigure_fail error=${error.message}", error)
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
        } catch (_: IllegalStateException) {
        } catch (_: RuntimeException) {
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
    }
}
