package com.glassbox.hello.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import com.glassbox.hello.chat.VoiceRecordingState
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloDimens
import com.glassbox.hello.ui.theme.HelloMotion
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
    val actionScale = animateFloatAsState(
        targetValue = if (hasPayload || voiceState.active) 1.05f else 1f,
        animationSpec = HelloMotion.SpringSnappy,
        label = "composerActionScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        HelloColors.PanelStrong.copy(alpha = 0.94f),
                        HelloColors.BgBase.copy(alpha = 0.98f)
                    )
                )
            )
            .padding(
                start = HelloDimens.SpaceL,
                end = HelloDimens.SpaceL,
                top = HelloDimens.SpaceM,
                bottom = HelloDimens.SpaceL
            )
    ) {
        Column(modifier = Modifier.animateContentSize()) {
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
                horizontalArrangement = Arrangement.spacedBy(HelloDimens.SpaceS)
            ) {
                HelloIconButton(
                    onClick = onToggleEmoji,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Icon(
                        Icons.Default.EmojiEmotions,
                        contentDescription = "Emoji",
                        tint = if (showEmojiRow) HelloColors.AccentStrong else HelloColors.Accent
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(HelloDimens.CornerXL))
                        .background(HelloColors.PanelStrong)
                        .border(1.2.dp, HelloColors.BorderStrong, RoundedCornerShape(HelloDimens.CornerXL))
                        .padding(horizontal = HelloDimens.SpaceS)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ComposerInput(
                            value = text,
                            onValueChange = onTextChange,
                            placeholder = placeholder,
                            enterSends = enterSends,
                            onKeyboardSend = onKeyboardSend,
                            modifier = Modifier.weight(1f)
                        )
                        
                        HelloIconButton(onClick = onAttach) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = HelloColors.Accent)
                        }
                    }
                }

                HelloIconButton(
                    onClick = onSendOrRecord,
                    active = hasPayload || voiceState.active,
                    modifier = Modifier
                        .scale(actionScale.value)
                        .padding(bottom = 2.dp)
                ) {
                    val sending = hasPayload
                    Icon(
                        if (sending) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                        contentDescription = if (sending) "Send message" else "Voice note",
                        tint = if (sending || voiceState.active) HelloColors.AccentStrong else HelloColors.Accent
                    )
                }
            }
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
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = HelloColors.Text),
        modifier = modifier
            .heightIn(min = 44.dp, max = 124.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        keyboardOptions = if (enterSends) KeyboardOptions(imeAction = ImeAction.Send) else KeyboardOptions.Default,
        keyboardActions = if (enterSends) KeyboardActions(onSend = { onKeyboardSend() }) else KeyboardActions.Default,
        singleLine = false,
        maxLines = 5,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = HelloColors.TextMuted,
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
