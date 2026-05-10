package com.glassbox.hello.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

private const val STICKER_PREFIX = "::sticker::"

private enum class PickerTab(val label: String) {
    Emoji("Emoji"),
    Gif("GIF"),
    Sticker("Sticker")
}

private val emojiCategories = linkedMapOf(
    "Smileys" to listOf("😀","😁","😂","🤣","😊","😍","😘","😎","🤩","🥳","😴","🤔","😇","😭","😡","🥶","🥵","🤯","🥺","😬"),
    "People" to listOf("👋","🤝","👏","🙌","🫶","💪","🙏","🫡","✍️","💃","🕺","🧘","🏃","🧠","👀","🫂","👑","💄","💼","🎓"),
    "Nature" to listOf("🌞","🌙","⭐","🔥","🌈","⚡","🌊","🌴","🌸","🌺","🌻","🍀","🍁","🪴","🐶","🐱","🦊","🐼","🦋","🐬"),
    "Food" to listOf("☕","🍵","🍕","🍔","🍟","🌮","🍣","🍜","🍩","🍪","🍫","🍿","🍓","🍉","🍍","🥑","🍇","🥤","🍰","🍨"),
    "Travel" to listOf("🏠","🏙️","🛫","🚗","🚀","⛵","🏖️","🏔️","🗺️","🎡","🏝️","🛵","🚲","🚆","🧳","📍","🌆","🌃","🏕️","🗽"),
    "Objects" to listOf("📱","💻","⌚","🎧","📷","🎥","🕹️","💡","📚","✉️","🧸","🎁","🪄","🪩","🎨","🎸","🥁","🏆","💎","🪙")
)

private val stickerPack = listOf(
    "🥹✨",
    "😎🔥",
    "🥳🎉",
    "🫶💖",
    "😭💔",
    "🤯⚡",
    "👀💫",
    "🤝🌟",
    "🙌🎊",
    "💃🪩",
    "🕺🎶",
    "😴💤",
    "🐼💚",
    "🦋🌈",
    "🌸💕",
    "☕🫰"
)

fun createStickerMessageText(sticker: String): String = "$STICKER_PREFIX$sticker"

fun isStickerMessage(text: String): Boolean = text.startsWith(STICKER_PREFIX)

fun stickerPayload(text: String): String = text.removePrefix(STICKER_PREFIX).ifBlank { "✨" }

@Composable
fun EmojiStickerPickerSheet(
    onEmojiSelected: (String) -> Unit,
    onStickerSelected: (String) -> Unit,
    onPickGif: () -> Unit
) {
    var tab by remember { mutableStateOf(PickerTab.Emoji) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HelloSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Text("Express", color = HelloColors.DarkText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
            PickerTab.entries.forEach { entry ->
                Text(
                    text = entry.label,
                    color = if (tab == entry) HelloColors.DarkAccentStrong else HelloColors.DarkTextMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(HelloShapes.Pill)
                        .background(if (tab == entry) HelloColors.DarkAccentSoft else HelloColors.DarkPanelMuted)
                        .clickable { tab = entry }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        when (tab) {
            PickerTab.Emoji -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md),
                modifier = Modifier.fillMaxWidth()
            ) {
                emojiCategories.forEach { (label, values) ->
                    item(label) {
                        Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                            Text(label, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                values.forEach { emoji ->
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier
                                            .clip(HelloShapes.Pill)
                                            .background(HelloColors.DarkPanelMuted)
                                            .clickable { onEmojiSelected(emoji) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PickerTab.Gif -> HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
                Column(
                    modifier = Modifier.padding(HelloSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                        Icon(Icons.Default.GifBox, contentDescription = null, tint = HelloColors.DarkAccent)
                        Text("Send a GIF from your device", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Pick an animated GIF from storage and send it as a real attachment. No fake catalog buttons.",
                        color = HelloColors.DarkTextMuted
                    )
                    Row(
                        modifier = Modifier
                            .clip(HelloShapes.Pill)
                            .background(HelloColors.DarkAccentSoft)
                            .clickable(onClick = onPickGif)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = HelloColors.DarkAccentStrong)
                        Text("Pick GIF", color = HelloColors.DarkAccentStrong, fontWeight = FontWeight.Bold)
                    }
                }
            }
            PickerTab.Sticker -> {
                Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                    Text("Quick sticker pack", color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        stickerPack.forEach { sticker ->
                            Box(
                                modifier = Modifier
                                    .clip(HelloShapes.Lg)
                                    .background(HelloColors.DarkPanelMuted)
                                    .clickable { onStickerSelected(sticker) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(sticker, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
