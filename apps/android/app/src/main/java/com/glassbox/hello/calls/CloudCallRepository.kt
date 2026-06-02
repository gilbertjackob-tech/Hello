package com.glassbox.hello.calls

import android.content.Context
import com.glassbox.hello.chat.ChatModels

class CloudCallRepository(context: Context) : CallRepositoryContract {
    private val api = CloudCallApi(context.applicationContext)

    override suspend fun loadHistory(userId: String): Result<List<ChatModels.CallHistoryItem>> {
        return api.history(userId)
    }

    override suspend fun createDirectCall(
        callerId: String,
        calleeId: String,
        chatId: String,
        type: String
    ): Result<String> {
        return api.startCall(callerId, calleeId, chatId, type)
    }

    override suspend fun loadIceServers(): Result<List<CallIceServer>> {
        return api.iceServers()
    }

    override suspend fun createGroupRoom(
        chatId: String,
        hostId: String,
        type: String,
        participantIds: List<String>
    ): Result<CallRoom> {
        return api.createGroupRoom(chatId, hostId, type, participantIds)
    }

    override suspend fun joinGroupRoom(roomId: String, userId: String): Result<CallRoom> {
        return api.joinGroupRoom(roomId, userId)
    }

    override suspend fun leaveGroupRoom(roomId: String, userId: String, ended: Boolean): Result<CallRoom> {
        return api.leaveGroupRoom(roomId, userId, ended)
    }
}
