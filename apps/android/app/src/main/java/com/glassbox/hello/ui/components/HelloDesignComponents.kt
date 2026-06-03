package com.glassbox.hello.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.glassbox.hello.core.UrlResolver
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloDimens
import com.glassbox.hello.ui.theme.HelloMotion
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing

@Composable
fun HelloScreenBackground(
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    auth: Boolean = false,
    content: @Composable () -> Unit
) {
    val background = when {
        auth -> Brush.verticalGradient(listOf(HelloColors.AuthBg, HelloColors.AuthBg))
        dark -> Brush.verticalGradient(listOf(HelloColors.DarkBgStrong, HelloColors.DarkBg))
        else -> Brush.verticalGradient(listOf(HelloColors.BgStrong, HelloColors.Bg))
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(HelloSpacing.ScreenPadding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun HelloBrandMark(
    modifier: Modifier = Modifier,
    dark: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(HelloShapes.Lg)
                .background(if (dark) HelloColors.DarkAccent else HelloColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "H",
                color = if (dark) HelloColors.DarkBg else Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
        Column {
            Text(
                text = "Hello",
                color = if (dark) HelloColors.DarkText else HelloColors.Text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Private family messenger",
                color = if (dark) HelloColors.DarkTextMuted else HelloColors.TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun HelloPanel(
    modifier: Modifier = Modifier,
    strong: Boolean = true,
    dark: Boolean = true,
    auth: Boolean = false,
    shape: Shape = HelloShapes.Lg,
    content: @Composable () -> Unit
) {
    val color = when {
        auth -> HelloColors.AuthPanel
        dark && strong -> HelloColors.DarkPanelStrong
        dark -> HelloColors.DarkPanel
        strong -> HelloColors.PanelStrong
        else -> HelloColors.Panel
    }
    val border = when {
        auth -> HelloColors.AuthBorder
        dark && strong -> HelloColors.DarkBorderStrong
        dark -> HelloColors.DarkBorder
        strong -> HelloColors.BorderStrong
        else -> HelloColors.Border
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, border),
        shadowElevation = if (strong) 10.dp else 4.dp,
        tonalElevation = 0.dp,
        content = content
    )
}

@Composable
fun HelloRail(
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .height(HelloSpacing.BottomRailHeight)
            .fillMaxWidth()
            .background(if (dark) HelloColors.DarkPanel else HelloColors.Panel)
            .border(1.dp, if (dark) HelloColors.DarkBorder else HelloColors.Border),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun HelloIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    dark: Boolean = true,
    content: @Composable () -> Unit
) {
    val bg = if (active) {
        if (dark) HelloColors.DarkAccentSoft else HelloColors.AccentSoft
    } else {
        Color.Transparent
    }
    TextButton(
        onClick = onClick,
        modifier = modifier
            .size(HelloSpacing.IconButton)
            .clip(CircleShape)
            .background(bg),
        content = { content() }
    )
}

@Composable
fun HelloSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search people, groups, files, or messages",
    dark: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HelloShapes.Pill)
            .background(if (dark) HelloColors.GlassBgMedium else HelloColors.PanelStrong)
            .border(1.dp, if (dark) HelloColors.GlassBorderStrong else HelloColors.Border, HelloShapes.Pill)
            .padding(horizontal = HelloSpacing.InputHorizontal, vertical = HelloSpacing.InputVertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) Spacer(modifier = Modifier.width(HelloSpacing.Md))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (dark) HelloColors.DarkText else HelloColors.Text
            ),
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) HelloColors.DarkTextMuted else HelloColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        )
        if (trailing != null) Spacer(modifier = Modifier.width(HelloSpacing.Md))
        trailing?.invoke()
    }
}

@Composable
fun HelloFilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    dark: Boolean = true
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.97f,
        animationSpec = HelloMotion.SpringSnappy,
        label = "helloFilterChipScale"
    )
    val bg = if (active) {
        if (dark) HelloColors.TealDeep else HelloColors.Accent
    } else {
        if (dark) HelloColors.GlassBg else HelloColors.PanelMuted
    }
    val fg = if (active) HelloColors.TextOnTeal else if (dark) HelloColors.TextSecondary else HelloColors.TextMuted
    Surface(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = HelloShapes.Pill,
        color = bg,
        border = BorderStroke(1.dp, if (active) HelloColors.TealPrimary else if (dark) HelloColors.GlassBorder else HelloColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    modifier = Modifier
                        .clip(HelloShapes.Pill)
                        .background(if (active) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun HelloAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = HelloDimens.AvatarM,
    online: Boolean = false,
    dark: Boolean = true,
    imageUrl: String? = null
) {
    val dotSize = (size.value * 0.28f).dp
    Box(modifier = modifier.size(size + dotSize / 4)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(HelloColors.BgElevated)
                .border(1.dp, HelloColors.GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val resolved = UrlResolver.resolve(imageUrl)
            if (resolved != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(resolved)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = "$name avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = { AvatarInitial(name = name, dark = dark) },
                    loading = { AvatarInitial(name = name, dark = dark) }
                )
            } else {
                AvatarInitial(name = name, dark = dark)
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(HelloColors.BgDeep)  // gap ring
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(HelloColors.OnlineGreen)
                )
            }
        }
    }
}

@Composable
private fun AvatarInitial(name: String, dark: Boolean) {
    Text(
        text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
        color = if (dark) HelloColors.DarkTextMuted else HelloColors.Text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun HelloChatCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    time: String = "",
    unreadCount: Int = 0,
    active: Boolean = false,
    dark: Boolean = true,
    avatarUrl: String? = null,
    online: Boolean = false
) {
    val pressScale by animateFloatAsState(
        targetValue = if (active) 1f else 0.985f,
        animationSpec = HelloMotion.SpringSnappy,
        label = "chatCardScale"
    )
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale),
        cornerRadius = HelloDimens.CornerL,
        bgAlpha = if (dark) HelloColors.GlassBg else HelloColors.PanelStrong,
        borderColor = if (active) HelloColors.TealPrimary else HelloColors.GlassBorder
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HelloDimens.SpaceL, vertical = HelloDimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloDimens.SpaceM)
        ) {
            HelloAvatar(name = title, dark = dark, imageUrl = avatarUrl, online = online)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (dark) HelloColors.DarkText else HelloColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unreadCount > 0 || active) {
                            HelloColors.TealPrimary
                        } else if (dark) HelloColors.DarkTextMuted else HelloColors.TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(HelloSpacing.Xs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) {
                        HelloColors.TealLight
                    } else if (dark) HelloColors.DarkTextMuted else HelloColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (unreadCount > 0) {
                UnreadBadge(count = unreadCount)
            }
        }
    }
}


@Composable
fun HelloMessageBubble(
    text: String,
    mine: Boolean,
    modifier: Modifier = Modifier,
    senderName: String? = null,
    time: String = "",
    dark: Boolean = true
) {
    val bubbleColor = when {
        mine && dark -> HelloColors.MessageMineDark
        mine -> HelloColors.MessageMine
        dark -> HelloColors.MessageOtherDark
        else -> HelloColors.MessageOther
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HelloSpacing.Xs),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = if (mine) HelloShapes.MessageMine else HelloShapes.MessageOther,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.78f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!senderName.isNullOrBlank() && !mine) {
                    Text(senderName, style = MaterialTheme.typography.bodySmall, color = if (dark) HelloColors.ReadReceipt else HelloColors.Accent)
                    Spacer(modifier = Modifier.height(HelloSpacing.Xs))
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = if (dark) HelloColors.AuthText else HelloColors.Text)
                if (time.isNotBlank()) {
                    Text(
                        time,
                        modifier = Modifier.align(Alignment.End),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) HelloColors.AuthMuted else HelloColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun HelloComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Write a message, drop a file, or share from GlassBox",
    dark: Boolean = true
) {
    HelloPanel(
        modifier = modifier.fillMaxWidth(),
        strong = true,
        dark = dark,
        shape = HelloShapes.Composer
    ) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            HelloSearchBar(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                dark = dark,
                modifier = Modifier.weight(1f)
            )
            HelloPrimaryButton(text = "Send", onClick = onSend, modifier = Modifier.width(82.dp))
        }
    }
}

@Composable
fun HelloPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    auth: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = if (auth) HelloShapes.AuthInput else HelloShapes.Sm,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (auth) HelloColors.AuthAccent else HelloColors.Accent,
            contentColor = Color.White,
            disabledContainerColor = (if (auth) HelloColors.AuthAccentStrong else HelloColors.AccentStrong).copy(alpha = 0.42f),
            disabledContentColor = Color.White.copy(alpha = 0.78f)
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = HelloSpacing.Xs)
        )
    }
}

@Composable
fun HelloTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    auth: Boolean = false,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val dark = auth
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        shape = if (auth) HelloShapes.AuthInput else HelloShapes.Md,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (auth) HelloColors.AuthAccent else HelloColors.Accent,
            unfocusedBorderColor = if (auth) HelloColors.AuthBorder else HelloColors.Border,
            focusedContainerColor = if (auth) HelloColors.AuthInput else HelloColors.PanelStrong,
            unfocusedContainerColor = if (auth) HelloColors.AuthInput else HelloColors.PanelStrong,
            focusedTextColor = if (dark) HelloColors.AuthText else HelloColors.Text,
            unfocusedTextColor = if (dark) HelloColors.AuthText else HelloColors.Text,
            focusedLabelColor = if (auth) HelloColors.AuthAccent else HelloColors.Accent,
            unfocusedLabelColor = if (dark) HelloColors.AuthMuted else HelloColors.TextMuted,
            cursorColor = if (auth) HelloColors.AuthAccent else HelloColors.Accent
        )
    )
}

@Composable
fun HelloScreen(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable () -> Unit
) {
    HelloScreenBackground(modifier = modifier, dark = true) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = contentAlignment
        ) {
            content()
        }
    }
}

@Composable
fun HelloTopBar(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = false, shape = HelloShapes.HeaderPanel) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HelloColors.DarkAccent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(HelloSpacing.Xs))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = HelloColors.DarkText,
                    fontWeight = FontWeight.Bold
                )
            }
            trailing()
        }
    }
}

@Composable
fun HelloBottomNav(
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        if (dark) HelloColors.BgBase.copy(alpha = 0.95f) else HelloColors.Bg.copy(alpha = 0.95f)
                    )
                )
            )
            .padding(
                start = HelloDimens.SpaceL,
                end = HelloDimens.SpaceL,
                top = 8.dp,
                bottom = 12.dp
            )
    ) {
        Row(
            modifier = Modifier
                .height(HelloDimens.BottomNavHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(HelloDimens.CornerXL))
                .background(HelloColors.GlassBgMedium)
                .border(0.5.dp, HelloColors.GlassBorder, RoundedCornerShape(HelloDimens.CornerXL))
                .padding(horizontal = HelloDimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            content = content
        )
    }
}

@Composable
fun HelloBadge(
    text: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .clip(HelloShapes.Pill)
            .background(if (danger) HelloColors.DarkDanger else HelloColors.DarkAccent)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun HelloPill(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    danger: Boolean = false
) {
    val color = when {
        danger -> HelloColors.DarkDanger.copy(alpha = 0.18f)
        active -> HelloColors.DarkAccentSoft
        else -> HelloColors.DarkPanelMuted
    }
    val textColor = when {
        danger -> HelloColors.DarkDanger
        active -> HelloColors.DarkAccentStrong
        else -> HelloColors.DarkTextMuted
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        modifier = modifier
            .clip(HelloShapes.Pill)
            .background(color)
            .border(1.dp, if (active) HelloColors.DarkAccent else HelloColors.DarkBorder, HelloShapes.Pill)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun HelloSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = HelloColors.DarkTextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun HelloListItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = HelloColors.DarkText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HelloColors.DarkTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun HelloEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(HelloSpacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = HelloColors.DarkText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(HelloSpacing.Sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = HelloColors.DarkTextMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(modifier = Modifier.height(HelloSpacing.Lg))
            action()
        }
    }
}

@Composable
fun HelloLoadingState(
    modifier: Modifier = Modifier,
    label: String = "Loading..."
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = HelloColors.DarkAccent)
        Spacer(modifier = Modifier.height(HelloSpacing.Lg))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HelloColors.DarkTextMuted)
    }
}

@Composable
fun HelloErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    HelloEmptyState(
        title = "Something went wrong",
        message = message,
        modifier = modifier,
        action = onRetry?.let {
            { HelloPrimaryButton(text = "Retry", onClick = it, modifier = Modifier.widthIn(min = 140.dp, max = 220.dp)) }
        }
    )
}

@Composable
fun HelloSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        Column(modifier = Modifier.padding(vertical = HelloSpacing.Sm), content = content)
    }
}

@Composable
fun HelloSettingsRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    HelloListItem(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        leading = leading,
        trailing = trailing,
        onClick = onClick
    )
}

@Composable
fun HelloCallCard(
    name: String,
    detail: String,
    time: String,
    missed: Boolean,
    video: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        HelloListItem(
            title = name,
            subtitle = detail,
            onClick = onClick,
            leading = { HelloAvatar(name = name, online = !missed) },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(time, style = MaterialTheme.typography.labelSmall, color = HelloColors.DarkTextMuted)
                    Spacer(modifier = Modifier.height(HelloSpacing.Xs))
                    HelloPill(if (video) "Video" else "Audio", active = !missed, danger = missed)
                }
            }
        )
    }
}

@Composable
fun HelloCallControls(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = true, shape = HelloShapes.HeaderPanel) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Lg),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun HelloStatusAvatarRing(
    name: String,
    modifier: Modifier = Modifier,
    seen: Boolean = false,
    online: Boolean = false,
    imageUrl: String? = null
) {
    Box(
        modifier = modifier
            .size(62.dp)
            .clip(CircleShape)
            .border(2.dp, if (seen) HelloColors.StoryRingSeen else HelloColors.StoryRingUnseen, CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        HelloAvatar(name = name, size = 52.dp, online = online, imageUrl = imageUrl)
    }
}

@Composable
fun HelloFileCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    HelloPanel(modifier = modifier.fillMaxWidth(), strong = false, shape = HelloShapes.Md) {
        Row(
            modifier = Modifier.padding(HelloSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(HelloShapes.Md)
                    .background(HelloColors.DarkAccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = HelloColors.DarkAccent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = HelloColors.DarkText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = HelloColors.DarkTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
