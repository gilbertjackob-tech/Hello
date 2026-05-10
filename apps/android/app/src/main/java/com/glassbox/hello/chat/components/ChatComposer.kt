package com.glassbox.hello.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import com.glassbox.hello.chat.VoiceRecordingState
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    showEmojiRow: Boolean,
    onToggleEmoji: () -> Unit,
    onEmoji: (String) -> Unit,
    onAttach: () -> Unit,
    onSendOrRecord: () -> Unit,
    voiceState: VoiceRecordingState,
    recordingElapsedSeconds: Long,
    onCancelVoice: () -> Unit,
    hasPayload: Boolean,
    placeholder: String,
    enterSends: Boolean,
    onKeyboardSend: () -> Unit
) {
    HelloPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Xs),
        strong = true,
        shape = HelloShapes.Lg
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = HelloSpacing.Sm, vertical = HelloSpacing.Xs)
                .animateContentSize()
        ) {
            if (voiceState.active) {
                VoiceRecordingBar(elapsedSeconds = recordingElapsedSeconds, onCancel = onCancelVoice)
            }
            voiceState.error?.let {
                Text(
                    text = it,
                    color = HelloColors.DarkDanger,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Xs)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)
            ) {
                HelloIconButton(onClick = onToggleEmoji) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = if (showEmojiRow) HelloColors.DarkAccent else HelloColors.DarkTextMuted)
                }
                HelloIconButton(onClick = onAttach) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = HelloColors.DarkTextMuted)
                }
                ComposerInput(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = placeholder,
                    enterSends = enterSends,
                    onKeyboardSend = onKeyboardSend,
                    modifier = Modifier.weight(1f)
                )
                HelloIconButton(onClick = onSendOrRecord, active = hasPayload || voiceState.active) {
                    Icon(
                        if (hasPayload) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                        contentDescription = if (hasPayload) "Send message" else "Voice note",
                        tint = if (hasPayload || voiceState.active) HelloColors.DarkAccent else HelloColors.DarkTextMuted
                    )
                }
            }
            Text(
                text = if (hasPayload) "Ready to send" else "Emoji, GIFs, stickers, files, and voice notes",
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 56.dp, top = 4.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun ComposerInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enterSends: Boolean,
    onKeyboardSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = HelloColors.DarkText),
        modifier = modifier
            .heightIn(min = 44.dp, max = 124.dp)
            .clip(HelloShapes.ComposerInput)
            .background(HelloColors.DarkBgStrong)
            .border(1.dp, HelloColors.DarkBorderStrong, HelloShapes.ComposerInput)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        keyboardOptions = if (enterSends) KeyboardOptions(imeAction = ImeAction.Send) else KeyboardOptions.Default,
        keyboardActions = if (enterSends) KeyboardActions(onSend = { onKeyboardSend() }) else KeyboardActions.Default,
        singleLine = false,
        maxLines = 5,
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = HelloColors.DarkTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun VoiceRecordingBar(elapsedSeconds: Long, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HelloSpacing.Sm, vertical = HelloSpacing.Xs)
            .clip(HelloShapes.Md)
            .background(Color(0x33FF7B84))
            .border(1.dp, Color(0x55FF7B84), HelloShapes.Md)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(HelloShapes.Pill)
                .background(HelloColors.DarkDanger)
        )
        Text(
            text = "Recording ${formatDuration(elapsedSeconds)}",
            color = HelloColors.DarkText,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Cancel",
            color = HelloColors.DarkDanger,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onCancel)
        )
    }
}
