package com.glassbox.hello.calls

import org.json.JSONArray
import org.json.JSONObject

internal fun CallSignal.toJson(): JSONObject = JSONObject().apply {
    eventId?.let { put("eventId", it) }
    put("callId", callId)
    roomId?.let { put("roomId", it) }
    put("chatId", chatId)
    put("fromUserId", fromUserId)
    put("toUserId", toUserId)
    put("callerId", callerId)
    put("calleeId", calleeId)
    put("callerName", callerName)
    put("callerAvatar", callerAvatar)
    put("calleeName", calleeName)
    put("calleeAvatar", calleeAvatar)
    put("type", type)
    put("isVideo", isVideo)
    offerSdp?.let { put("offer", JSONObject().put("type", "offer").put("sdp", it)) }
    answerSdp?.let { put("answer", JSONObject().put("type", "answer").put("sdp", it)) }
    candidate?.let {
        put(
            "candidate",
            JSONObject()
                .put("candidate", it)
                .put("sdpMid", sdpMid)
                .put("sdpMLineIndex", sdpMLineIndex ?: 0)
        )
    }
    reason?.let { put("reason", it) }
    timestamp?.let { put("timestamp", it) }
    put("attempt", attempt)
    event?.let { put("event", it) }
}

internal fun JSONObject.toCallSignal(): CallSignal? {
    val callId = optString("callId", optString("id", ""))
    val chatId = optString("chatId", "")
    val callerId = optString("callerId", optString("fromUserId", ""))
    val calleeId = optString("calleeId", optString("toUserId", ""))
    val fromUserId = optString("fromUserId", callerId)
    val toUserId = optString("toUserId", calleeId)
    if (callId.isBlank() || chatId.isBlank() || fromUserId.isBlank() || toUserId.isBlank()) return null
    val isVideo = optBoolean("isVideo", optString("type", "audio") == "video")
    val offer = optJSONObject("offer")
    val answer = optJSONObject("answer")
    val candidateObject = optJSONObject("candidate")
    return CallSignal(
        eventId = optString("eventId").ifBlank { null },
        callId = callId,
        roomId = optString("roomId").ifBlank { null },
        chatId = chatId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        callerId = callerId.ifBlank { fromUserId },
        calleeId = calleeId.ifBlank { toUserId },
        callerName = optString("callerName", "Hello call"),
        callerAvatar = optString("callerAvatar").ifBlank { null },
        calleeName = optString("calleeName").ifBlank { null },
        calleeAvatar = optString("calleeAvatar").ifBlank { null },
        type = optString("type", if (isVideo) "video" else "audio"),
        isVideo = isVideo,
        offerSdp = offer?.optString("sdp")?.ifBlank { null },
        answerSdp = answer?.optString("sdp")?.ifBlank { null },
        candidate = candidateObject?.optString("candidate")?.ifBlank { null },
        sdpMid = candidateObject?.optString("sdpMid")?.ifBlank { null },
        sdpMLineIndex = candidateObject?.takeIf { it.has("sdpMLineIndex") }?.optInt("sdpMLineIndex"),
        reason = optString("reason").ifBlank { null },
        timestamp = optNullableLong("timestamp"),
        attempt = optInt("attempt", 1),
        event = optString("event").ifBlank { null }
    )
}

internal fun CallRoom.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    callId?.let { put("callId", it) }
    put("chatId", chatId)
    put("hostId", hostId)
    put("mode", mode)
    put("type", type)
    put("status", status)
    put("maxParticipants", maxParticipants)
    put("participantIds", JSONArray(participantIds))
    put("createdAt", createdAt)
    endedAt?.let { put("endedAt", it) }
    endedBy?.let { put("endedBy", it) }
}

internal fun JSONObject.toCallRoom(): CallRoom? {
    val id = optString("id", optString("roomId", ""))
    val chatId = optString("chatId", "")
    val hostId = optString("hostId", "")
    if (id.isBlank() || chatId.isBlank() || hostId.isBlank()) return null
    val participantIds = optJSONArray("participantIds").toStringList()
    val participants = optJSONArray("participants").toParticipants()
    return CallRoom(
        id = id,
        callId = optString("callId").ifBlank { null },
        chatId = chatId,
        hostId = hostId,
        mode = optString("mode", "group"),
        type = optString("type", "video"),
        status = optString("status", "ringing"),
        maxParticipants = optInt("maxParticipants", 4),
        participantIds = participantIds,
        participants = participants,
        createdAt = optLong("createdAt", 0),
        endedAt = optNullableLong("endedAt"),
        endedBy = optString("endedBy").ifBlank { null }
    )
}

internal fun JSONObject.toRoomFromEvent(): CallRoom? {
    return optJSONObject("room")?.toCallRoom() ?: toCallRoom()
}

internal fun RoomSignal.toJson(): JSONObject = JSONObject().apply {
    put("roomId", roomId)
    put("fromUserId", fromUserId)
    put("toUserId", toUserId)
    offerSdp?.let { put("offer", JSONObject().put("type", "offer").put("sdp", it)) }
    answerSdp?.let { put("answer", JSONObject().put("type", "answer").put("sdp", it)) }
    candidate?.let {
        put(
            "candidate",
            JSONObject()
                .put("candidate", it)
                .put("sdpMid", sdpMid)
                .put("sdpMLineIndex", sdpMLineIndex ?: 0)
        )
    }
}

internal fun JSONObject.toRoomSignal(): RoomSignal? {
    val roomId = optString("roomId", "")
    val fromUserId = optString("fromUserId", optString("userId", ""))
    val toUserId = optString("toUserId", "")
    if (roomId.isBlank() || fromUserId.isBlank() || toUserId.isBlank()) return null
    val offer = optJSONObject("offer")
    val answer = optJSONObject("answer")
    val candidateObject = optJSONObject("candidate")
    return RoomSignal(
        roomId = roomId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        offerSdp = offer?.optString("sdp")?.ifBlank { null },
        answerSdp = answer?.optString("sdp")?.ifBlank { null },
        candidate = candidateObject?.optString("candidate")?.ifBlank { null },
        sdpMid = candidateObject?.optString("sdpMid")?.ifBlank { null },
        sdpMLineIndex = candidateObject?.takeIf { it.has("sdpMLineIndex") }?.optInt("sdpMLineIndex")
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).ifBlank { null } }
}

private fun JSONArray?.toParticipants(): List<CallParticipant> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let {
            CallParticipant(
                id = it.optString("id"),
                name = it.optString("name", "Hello user"),
                avatar = it.optString("avatar").ifBlank { null },
                joinedAt = it.optNullableLong("joinedAt"),
                leftAt = it.optNullableLong("leftAt"),
                isHost = it.optBoolean("isHost", false)
            )
        }
    }.filter { it.id.isNotBlank() }
}

private fun JSONObject.optNullableLong(name: String): Long? {
    return if (has(name) && !isNull(name)) optLong(name) else null
}
