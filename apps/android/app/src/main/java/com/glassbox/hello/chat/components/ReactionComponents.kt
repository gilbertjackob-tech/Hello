package com.glassbox.hello.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.glassbox.hello.ui.theme.*
import kotlinx.coroutines.delay
import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Easing

// Conversion helper for Android Interpolator to Compose Easing
fun android.view.animation.Interpolator.toEasing() = Easing { x -> getInterpolation(x) }

data class Reaction(val emoji: String, val count: Int, val selectedByMe: Boolean = false)

val DEFAULT_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

// ─── Reaction Bar (shown on long press) ────────────────────────────────────

@Composable
fun ReactionBar(
    visible: Boolean,
    onReactionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(
            initialScale = 0.7f,
            animationSpec = tween(200, easing = OvershootInterpolator(1.5f).toEasing())
        ),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.7f, animationSpec = tween(100))
    ) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(HelloDimens.CornerFull))
                .background(HelloColors.GlassBgStrong)
                .border(0.5.dp, HelloColors.GlassBorderStrong, RoundedCornerShape(HelloDimens.CornerFull))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            DEFAULT_REACTIONS.forEachIndexed { index, emoji ->
                var triggered by remember { mutableStateOf(false) }

                // Stagger each emoji pop-in
                var emojiVisible by remember { mutableStateOf(false) }
                LaunchedEffect(visible) {
                    if (visible) {
                        delay(index * 35L)  // 35ms stagger between each emoji
                        emojiVisible = true
                    } else {
                        emojiVisible = false
                    }
                }

                val scale by animateFloatAsState(
                    targetValue = if (triggered) 1.3f else if (emojiVisible) 1f else 0f,
                    animationSpec = if (triggered) HelloMotion.SpringBouncy else HelloMotion.SpringSnappy,
                    label = "emojiScale_$index",
                    finishedListener = { triggered = false }
                )

                Text(
                    text = emoji,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .scale(scale)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            triggered = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReactionSelected(emoji)
                        }
                        .padding(4.dp)
                )
            }
        }
    }
}

// ─── Reaction Chips (under message bubble) ──────────────────────────────────

@Composable
fun ReactionRow(
    reactions: List<Reaction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        reactions.forEach { reaction ->
            ReactionChip(reaction = reaction)
        }
    }
}

@Composable
fun ReactionChip(
    reaction: Reaction,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(reaction.emoji) {
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = HelloMotion.SpringBouncy,
        label = "reactionChipScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(HelloDimens.CornerFull))
            .background(if (reaction.selectedByMe) HelloColors.TealDeep else HelloColors.GlassBgMedium)
            .border(
                0.5.dp,
                if (reaction.selectedByMe) HelloColors.TealPrimary else HelloColors.GlassBorder,
                RoundedCornerShape(HelloDimens.CornerFull)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(text = reaction.emoji, fontSize = 12.sp)
        if (reaction.count > 1) {
            Text(
                text = reaction.count.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (reaction.selectedByMe) HelloColors.TextOnTeal else HelloColors.TextSecondary
            )
        }
    }
}
