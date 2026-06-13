package com.glassbox.hello.calls

import android.content.Context
import com.glassbox.hello.core.User
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer

interface CallSocket {
    var onCallEvent: ((String, JSONObject) -> Unit)?
    var onConnectedChanged: ((Boolean) -> Unit)?

    fun connect(user: User)
    fun disconnect()
    fun startCall(payload: JSONObject)
    fun ringing(payload: JSONObject)
    fun acceptCall(payload: JSONObject)
    fun connected(payload: JSONObject)
    fun ack(payload: JSONObject)
    fun declineCall(payload: JSONObject)
    fun busy(payload: JSONObject)
    fun missed(payload: JSONObject)
    fun failed(payload: JSONObject)
    fun endCall(payload: JSONObject)
    fun sendOffer(payload: JSONObject)
    fun sendAnswer(payload: JSONObject)
    fun sendIceCandidate(payload: JSONObject)
    fun createRoom(payload: JSONObject)
    fun joinRoom(payload: JSONObject)
    fun leaveRoom(payload: JSONObject)
    fun participantState(payload: JSONObject)
    fun sendRoomOffer(payload: JSONObject)
    fun sendRoomAnswer(payload: JSONObject)
    fun sendRoomIceCandidate(payload: JSONObject)
    fun isConnected(): Boolean
}

interface CallMediaEngine {
    var onOfferReady: ((NativeCallEngine.OutgoingSignal) -> Unit)?
    var onAnswerReady: ((NativeCallEngine.OutgoingSignal) -> Unit)?
    var onIceCandidateReady: ((NativeCallEngine.OutgoingSignal) -> Unit)?
    var onPhaseChanged: ((CallMediaPhase) -> Unit)?
    var onError: ((String) -> Unit)?
    var onDebugEvent: ((String) -> Unit)?

    fun setIceServers(iceServers: List<CallIceServer>)
    fun startOutgoing(context: Context?, isVideo: Boolean)
    fun startIncoming(context: Context?, isVideo: Boolean, offerSdp: String?)
    fun acceptOffer(offerSdp: String)
    fun acceptAnswer(answerSdp: String)
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?)
    fun setMuted(muted: Boolean)
    fun setCameraOff(off: Boolean)
    fun setSpeaker(context: Context, on: Boolean)
    fun setPreferredVideoProfile(profile: VideoQualityProfile)
    fun switchCamera()
    fun attachLocalRenderer(renderer: SurfaceViewRenderer)
    fun attachRemoteRenderer(renderer: SurfaceViewRenderer)
    fun dispose()
}

interface CallRepositoryContract {
    suspend fun loadHistory(userId: String): Result<List<com.glassbox.hello.chat.ChatModels.CallHistoryItem>>
    suspend fun createDirectCall(callerId: String, calleeId: String, chatId: String, type: String): Result<String>
    suspend fun loadIceServers(): Result<List<CallIceServer>>
    suspend fun createGroupRoom(chatId: String, hostId: String, type: String, participantIds: List<String>): Result<CallRoom>
    suspend fun joinGroupRoom(roomId: String, userId: String): Result<CallRoom>
    suspend fun leaveGroupRoom(roomId: String, userId: String, ended: Boolean): Result<CallRoom>
}
