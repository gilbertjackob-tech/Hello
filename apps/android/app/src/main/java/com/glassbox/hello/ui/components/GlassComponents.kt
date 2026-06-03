package com.glassbox.hello.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloDimens
import com.glassbox.hello.ui.theme.HelloMotion

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = HelloDimens.CornerL,
    bgAlpha: Color = HelloColors.GlassBg,
    borderColor: Color = HelloColors.GlassBorder,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgAlpha)
            .border(0.5.dp, borderColor, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

@Composable
fun GlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        cornerRadius = HelloDimens.CornerFull,
        bgAlpha = HelloColors.GlassBgMedium,
        borderColor = HelloColors.GlassBorderStrong
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = HelloColors.TextSecondary,
                modifier = Modifier.size(19.dp)
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = HelloColors.TextPrimary,
                    fontSize = 15.sp
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = HelloColors.TextMuted,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val dotScale by rememberInfiniteTransition(label = "statusPulse").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusDot"
    )
    val dotColor = if (isOnline) HelloColors.OnlineGreen else HelloColors.OfflineGray
    val borderColor = if (isOnline) HelloColors.TealGlow else HelloColors.GlassBorder

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(HelloDimens.CornerFull))
            .background(HelloColors.GlassBg)
            .border(0.5.dp, borderColor, RoundedCornerShape(HelloDimens.CornerFull))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .scale(if (isOnline) dotScale else 1f)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isOnline) HelloColors.TealPrimary else HelloColors.TextMuted,
            maxLines = 1
        )
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = HelloMotion.SpringSnappy,
        label = "filterChipScale"
    )
    val bgColor = if (selected) HelloColors.TealDeep else HelloColors.GlassBg
    val textColor = if (selected) HelloColors.TextOnTeal else HelloColors.TextSecondary
    val borderColor = if (selected) HelloColors.TealPrimary else HelloColors.GlassBorder

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(HelloDimens.CornerFull))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(HelloDimens.CornerFull))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text = label, fontSize = 13.sp, color = textColor, maxLines = 1)
    }
}

@Composable
fun DateSeparator(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(HelloDimens.CornerFull))
                .background(HelloColors.GlassBgMedium)
                .border(0.5.dp, HelloColors.GlassBorder, RoundedCornerShape(HelloDimens.CornerFull))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(text = label, fontSize = 11.sp, color = HelloColors.TextMuted)
        }
    }
}

@Composable
fun ShimmerChatCard(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "chatCardShimmer").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "shimmerAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HelloDimens.ChatCardHeight)
            .clip(RoundedCornerShape(HelloDimens.CornerL))
            .background(HelloColors.GlassBg)
            .border(0.5.dp, HelloColors.GlassBorder, RoundedCornerShape(HelloDimens.CornerL))
            .padding(HelloDimens.SpaceL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HelloDimens.SpaceM)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(HelloColors.BgElevated.copy(alpha = alpha))
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HelloColors.BgElevated.copy(alpha = alpha))
            )
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HelloColors.BgElevated.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun UnreadBadge(count: Int) {
    if (count <= 0) return
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(HelloColors.TealPrimary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else "$count",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = HelloColors.TextOnTeal
        )
    }
}

@Composable
fun ScrollToBottomFab(
    visible: Boolean,
    unreadBelow: Int = 0,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.5f, animationSpec = HelloMotion.SpringBouncy),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.5f)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(HelloColors.TealDeep)
                .border(1.dp, HelloColors.TealGlow, CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "v", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (unreadBelow > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(HelloColors.WarmAccent)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("$unreadBelow", fontSize = 9.sp, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun EmptyInboxState(modifier: Modifier = Modifier) {
    val offsetY by rememberInfiniteTransition(label = "emptyFloat").animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyOffset"
    )

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Chat", fontSize = 42.sp, modifier = Modifier.offset(y = offsetY.dp))
        Spacer(Modifier.height(HelloDimens.SpaceL))
        Text(
            "No conversations yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = HelloColors.TextPrimary
        )
        Spacer(Modifier.height(HelloDimens.SpaceS))
        Text(
            "Start a direct chat with another Hello user.",
            fontSize = 13.sp,
            color = HelloColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}
