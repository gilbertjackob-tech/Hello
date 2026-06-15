package com.glassbox.hello.chat.components

import com.glassbox.hello.chat.ChatModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatUiHelpersTest {
    @Test
    fun imageCluster_groupsConsecutiveImageOnlyMessagesAcrossTimeGaps() {
        val messages = listOf(
            image("one", timestamp = 1_000L),
            image("two", timestamp = 30_000_000L),
            image("three", timestamp = 80_000_000L)
        )

        val timeline = buildImageClusterTimeline(messages)

        assertEquals(listOf("one", "two", "three"), timeline.clustersByLeadIndex[0]?.map { it.id })
        assertEquals(0, timeline.followerToLeadIndex[1])
        assertEquals(0, timeline.followerToLeadIndex[2])
    }

    @Test
    fun imageCluster_stopsAtTextCaptionOrAttachmentBreaks() {
        val messages = listOf(
            image("one"),
            image("captioned", text = "caption"),
            image("two"),
            file("file"),
            image("three"),
            image("four")
        )

        val timeline = buildImageClusterTimeline(messages)

        assertFalse(timeline.clustersByLeadIndex.containsKey(0))
        assertEquals(listOf("three", "four"), timeline.clustersByLeadIndex[4]?.map { it.id })
        assertEquals(4, timeline.followerToLeadIndex[5])
    }

    @Test
    fun imageCluster_stopsAtDifferentSender() {
        val messages = listOf(
            image("one", senderId = "a"),
            image("two", senderId = "b"),
            image("three", senderId = "b")
        )

        val timeline = buildImageClusterTimeline(messages)

        assertFalse(timeline.clustersByLeadIndex.containsKey(0))
        assertEquals(listOf("two", "three"), timeline.clustersByLeadIndex[1]?.map { it.id })
        assertEquals(1, timeline.followerToLeadIndex[2])
    }

    private fun image(
        id: String,
        senderId: String = "sender",
        timestamp: Long = 1_000L,
        text: String = ""
    ): ChatModels.Message {
        return message(
            id = id,
            senderId = senderId,
            timestamp = timestamp,
            text = text,
            attachmentUrl = "https://example.test/$id.jpg",
            attachmentType = "image/jpeg",
            attachmentName = "$id.jpg"
        )
    }

    private fun file(id: String): ChatModels.Message {
        return message(
            id = id,
            attachmentUrl = "https://example.test/$id.pdf",
            attachmentType = "application/pdf",
            attachmentName = "$id.pdf"
        )
    }

    private fun message(
        id: String,
        senderId: String = "sender",
        timestamp: Long = 1_000L,
        text: String = "",
        attachmentUrl: String? = null,
        attachmentType: String? = null,
        attachmentName: String? = null
    ): ChatModels.Message {
        return ChatModels.Message(
            id = id,
            chatId = "chat",
            senderId = senderId,
            senderName = "Sender",
            text = text,
            timestamp = timestamp,
            attachmentUrl = attachmentUrl,
            attachmentType = attachmentType,
            attachmentName = attachmentName
        )
    }
}
