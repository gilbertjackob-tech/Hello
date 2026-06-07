package com.glassbox.hello.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun ChatHeader(
    title: String,
    subtitle: String,
    avatarUrl: String?,
    onBack: () -> Unit,
    onOpenContactInfo: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    videoCallEnabled: Boolean = false,
    onMore: () -> Unit
) {
    val subtitleIsTyping = subtitle.contains("typing", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        HelloColors.BgDeep.copy(alpha = 0.92f),
                        HelloColors.PanelStrong.copy(alpha = 0.96f),
                        HelloColors.BgBase.copy(alpha = 0.72f)
                    )
                )
            )
            .border(1.dp, HelloColors.Border, androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .padding(top = 8.dp, bottom = 12.dp, start = HelloSpacing.Sm, end = HelloSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HelloIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.AccentStrong)
        }
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onOpenContactInfo),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HelloAvatar(name = title, online = subtitle == "Online", size = 42.dp, imageUrl = avatarUrl)
            Spacer(modifier = Modifier.width(HelloSpacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = HelloColors.AccentStrong,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (subtitleIsTyping) HelloColors.DarkAccentStrong else HelloColors.TextSecondary,
                    fontWeight = if (subtitleIsTyping) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (videoCallEnabled) {
            HelloIconButton(onClick = onVideoCall) {
                Icon(Icons.Default.Videocam, contentDescription = "Video call", tint = HelloColors.Accent)
            }
        }
        HelloIconButton(onClick = onAudioCall) {
            Icon(Icons.Default.Call, contentDescription = "Audio call", tint = HelloColors.Accent)
        }
        HelloIconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = HelloColors.Accent)
        }
    }
}
