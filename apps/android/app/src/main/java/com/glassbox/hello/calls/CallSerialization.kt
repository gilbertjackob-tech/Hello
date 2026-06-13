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
    return toCallSignal(currentSignal = null, currentUserId = null)
}

internal fun JSONObject.toCallSignal(
    currentSignal: CallSignal? = null,
    currentUserId: String? = null
): CallSignal? {
    val currentUser = normalizedJsonString(currentUserId)
    val fallbackCallId = currentSignal?.callId.orEmpty()
    val fallbackChatId = currentSignal?.chatId.orEmpty()
    val fallbackCallerId = currentSignal?.callerId.orEmpty()
    val fallbackCalleeId = currentSignal?.calleeId.orEmpty()
    val fallbackRemoteUserId = when {
        currentSignal == null -> ""
        currentUser.isBlank() -> ""
        fallbackCallerId == currentUser -> fallbackCalleeId
        fallbackCalleeId == currentUser -> fallbackCallerId
        else -> ""
    }

    val callId = normalizedJsonString(optString("callId", optString("id", optString("roomId", fallbackCallId))))
    val chatId = normalizedJsonString(optString("chatId", fallbackChatId))
    val callerId = normalizedJsonString(optString("callerId", optString("fromUserId", fallbackCallerId))).ifBlank { fallbackCallerId }
    val calleeId = normalizedJsonString(optString("calleeId", optString("toUserId", fallbackCalleeId))).ifBlank { fallbackCalleeId }
    val fromUserId = normalizedJsonString(
        optString(
            "fromUserId",
            callerId.ifBlank {
                fallbackRemoteUserId.ifBlank { fallbackCallerId }
            }
        )
    ).ifBlank {
        fallbackRemoteUserId.ifBlank { callerId.ifBlank { fallbackCallerId } }
    }
    val toUserId = normalizedJsonString(
        optString(
            "toUserId",
            calleeId.ifBlank {
                currentUser.ifBlank { fallbackCalleeId }
            }
        )
    ).ifBlank {
        currentUser.ifBlank { calleeId.ifBlank { fallbackCalleeId } }
    }
    if (callId.isBlank() || chatId.isBlank() || fromUserId.isBlank() || toUserId.isBlank()) return null
    val isVideo = optBoolean("isVideo", optString("type", "audio") == "video")
    val offer = optJSONObject("offer")
    val answer = optJSONObject("answer")
    val candidateObject = optJSONObject("candidate")
    return CallSignal(
        eventId = optString("eventId").ifBlank { null },
        callId = callId,
        roomId = normalizedJsonString(optString("roomId")).ifBlank { null },
        chatId = chatId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        callerId = callerId.ifBlank { fromUserId },
        calleeId = calleeId.ifBlank { toUserId },
        callerName = normalizedJsonString(optString("callerName", "Hello call")).ifBlank { "Hello call" },
        callerAvatar = normalizedJsonString(optString("callerAvatar")).ifBlank { null },
        calleeName = normalizedJsonString(optString("calleeName")).ifBlank { null },
        calleeAvatar = normalizedJsonString(optString("calleeAvatar")).ifBlank { null },
        type = normalizedJsonString(optString("type", if (isVideo) "video" else "audio")).ifBlank { if (isVideo) "video" else "audio" },
        isVideo = isVideo,
        offerSdp = offer?.optString("sdp")?.let(::normalizedJsonString)?.ifBlank { null },
        answerSdp = answer?.optString("sdp")?.let(::normalizedJsonString)?.ifBlank { null },
        candidate = candidateObject?.optString("candidate")?.let(::normalizedJsonString)?.ifBlank { null },
        sdpMid = candidateObject?.optString("sdpMid")?.let(::normalizedJsonString)?.ifBlank { null },
        sdpMLineIndex = candidateObject?.takeIf { it.has("sdpMLineIndex") }?.optInt("sdpMLineIndex"),
        reason = normalizedJsonString(optString("reason")).ifBlank { null },
        timestamp = optNullableLong("timestamp"),
        attempt = optInt("attempt", 1),
        event = normalizedJsonString(optString("event")).ifBlank { null }
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
    return (0 until length()).mapNotNull { index -> normalizedJsonString(optString(index)).ifBlank { null } }
}

private fun normalizedJsonString(value: String?): String {
    val normalized = value?.trim().orEmpty()
    return when {
        normalized.equals("null", ignoreCase = true) -> ""
        normalized.equals("undefined", ignoreCase = true) -> ""
        else -> normalized
    }
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
