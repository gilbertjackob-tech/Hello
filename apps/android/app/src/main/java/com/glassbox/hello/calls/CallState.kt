package com.glassbox.hello.calls

data class CloudCallState(
    val callId: String,
    val status: String,
    val callerUserId: String,
    val receiverUserId: String,
    val startedAt: Long,
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val endReason: String? = null
)
