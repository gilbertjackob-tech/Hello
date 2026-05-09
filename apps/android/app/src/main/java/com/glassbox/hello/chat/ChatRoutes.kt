package com.glassbox.hello.chat

import com.glassbox.hello.chat.ChatModels.Chat

sealed interface ChatRoute {
    data object List : ChatRoute
    data class Room(val chat: Chat) : ChatRoute
    data class ContactInfo(val chat: Chat) : ChatRoute
    data class SharedContent(val chat: Chat, val mode: ChatSharedContentMode) : ChatRoute
}

enum class ChatSharedContentMode(val title: String) {
    Media("Shared media"),
    Files("Shared files"),
    Links("Shared links")
}

enum class AttachmentAction {
    Gallery,
    Camera,
    File,
    Location,
    Contact,
    Audio
}

enum class ComposerMode {
    Text,
    Attachment,
    Voice
}

data class VoiceRecordingState(
    val active: Boolean = false,
    val startedAt: Long = 0L,
    val filePath: String? = null,
    val error: String? = null
)
