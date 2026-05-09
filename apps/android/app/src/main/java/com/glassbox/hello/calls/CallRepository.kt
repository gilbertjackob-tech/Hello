package com.glassbox.hello.calls

import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.network.HelloApi
import com.glassbox.hello.network.HelloApiClient

class CallRepository(
    private val api: HelloApi = HelloApiClient()
) : CallRepositoryContract {
    override suspend fun loadHistory(userId: String): Result<List<ChatModels.CallHistoryItem>> {
        return api.fetchCalls(userId)
    }

    override suspend fun createDirectCall(
        callerId: String,
        calleeId: String,
        chatId: String,
        type: String
    ): Result<String> {
        return api.createCall(
            callerId = callerId,
            calleeId = calleeId,
            chatId = chatId,
            type = type,
            status = "outgoing_calling",
            startedAt = System.currentTimeMillis()
        )
    }

    override suspend fun loadIceServers(): Result<List<CallIceServer>> {
        return api.fetchCallIceServers()
    }

    override suspend fun createGroupRoom(
        chatId: String,
        hostId: String,
        type: String,
        participantIds: List<String>
    ): Result<CallRoom> {
        return api.createCallRoom(chatId, hostId, type, participantIds)
    }

    override suspend fun joinGroupRoom(roomId: String, userId: String): Result<CallRoom> {
        return api.joinCallRoom(roomId, userId)
    }

    override suspend fun leaveGroupRoom(roomId: String, userId: String, ended: Boolean): Result<CallRoom> {
        return api.leaveCallRoom(roomId, userId, ended)
    }
}
