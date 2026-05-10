package com.glassbox.hello.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.theme.ChatColorOption
import com.glassbox.hello.ui.theme.ChatThemeOption
import com.glassbox.hello.ui.theme.ChatThemeSelection
import com.glassbox.hello.ui.theme.ChatThemeStore
import com.glassbox.hello.ui.theme.ChatWallpaperBackground
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import com.glassbox.hello.ui.theme.rememberChatTheme

private enum class ChatThemePage {
    Home, Color, Wallpaper, Preview
}

@Composable
fun ChatThemeRoute(
    userId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedTheme by rememberChatTheme(context, userId)
    var page by remember { mutableStateOf(ChatThemePage.Home) }
    var draft by remember(savedTheme) { mutableStateOf(savedTheme) }
    var previewItems by remember { mutableStateOf(listOf(savedTheme)) }
    var previewIndex by remember { mutableIntStateOf(0) }

    fun openPreview(items: List<ChatThemeSelection>, selected: ChatThemeSelection) {
        previewItems = items.ifEmpty { listOf(selected) }
        previewIndex = previewItems.indexOfFirst { it == selected }.coerceAtLeast(0)
        draft = selected
        page = ChatThemePage.Preview
    }

    when (page) {
        ChatThemePage.Home -> ChatThemeScreen(
            selected = savedTheme,
            draft = draft,
            onBack = onBack,
            onThemeClick = { theme ->
                val selection = ChatThemeStore.selectionForTheme(theme)
                val candidates = ChatThemeStore.Themes.map(ChatThemeStore::selectionForTheme)
                openPreview(candidates, selection)
            },
            onOpenColor = { page = ChatThemePage.Color },
            onOpenWallpaper = { page = ChatThemePage.Wallpaper },
            modifier = modifier
        )
        ChatThemePage.Color -> ChatColorScreen(
            selected = draft,
            onBack = { page = ChatThemePage.Home },
            onColorSelected = { color ->
                draft = draft.copy(
                    colorId = color.id,
                    incomingArgb = ChatThemeStore.companionIncomingArgb(color.id, draft.darkMode),
                    themeId = "custom"
                )
            },
            onPreview = {
                val candidates = ChatThemeStore.Colors.map {
                    draft.copy(
                        colorId = it.id,
                        incomingArgb = ChatThemeStore.companionIncomingArgb(it.id, draft.darkMode),
                        themeId = "custom"
                    )
                }
                openPreview(candidates, draft)
            },
            modifier = modifier
        )
        ChatThemePage.Wallpaper -> WallpaperScreen(
            selected = draft,
            onBack = { page = ChatThemePage.Home },
            onWallpaperClick = { wallpaper ->
                val selection = draft.copy(wallpaper = wallpaper, themeId = "custom")
                val candidates = ChatThemeStore.Wallpapers.map { draft.copy(wallpaper = it, themeId = "custom") }
                openPreview(candidates, selection)
            },
            modifier = modifier
        )
        ChatThemePage.Preview -> ThemePreviewScreen(
            items = previewItems,
            initialIndex = previewIndex,
            onBack = { page = ChatThemePage.Home },
            onApply = { selection ->
                ChatThemeStore.save(context, userId, selection)
                draft = selection
                page = ChatThemePage.Home
            },
            modifier = modifier
        )
    }
}

@Composable
fun ChatThemeScreen(
    selected: ChatThemeSelection,
    draft: ChatThemeSelection,
    onBack: () -> Unit,
    onThemeClick: (ChatThemeOption) -> Unit,
    onOpenColor: () -> Unit,
    onOpenWallpaper: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .navigationBarsPadding()
            .background(HelloColors.DarkBg)
    ) {
        ThemeTopBar(title = "Chat theme", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)
        ) {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = HelloSpacing.Lg),
                    horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                ) {
                    items(ChatThemeStore.Themes) { theme ->
                        ThemeCard(
                            theme = theme,
                            selected = selected.themeId == theme.id,
                            onClick = { onThemeClick(theme) }
                        )
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = HelloSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                ) {
                    Text("Customize", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                    CustomizeRow(
                        title = "Chat color",
                        subtitle = ChatThemeStore.colorById(draft.colorId).label,
                        swatch = draft.color,
                        icon = Icons.Default.Palette,
                        onClick = onOpenColor
                    )
                    CustomizeRow(
                        title = "Wallpaper",
                        subtitle = draft.wallpaper.replaceFirstChar { it.uppercase() },
                        swatch = Color.Transparent,
                        icon = Icons.Default.Wallpaper,
                        onClick = onOpenWallpaper
                    )
                    Text(
                        text = "The chat color and wallpaper will both change.",
                        color = HelloColors.DarkTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ChatColorScreen(
    selected: ChatThemeSelection,
    onBack: () -> Unit,
    onColorSelected: (ChatColorOption) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .navigationBarsPadding()
            .background(HelloColors.DarkBg)
    ) {
        ThemeTopBar(title = "Chat color", onBack = onBack, onConfirm = onPreview)
        ThemePreviewPanel(selection = selected, modifier = Modifier.padding(horizontal = HelloSpacing.Lg))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .padding(HelloSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)
        ) {
            items(ChatThemeStore.Colors) { option ->
                ColorCircle(
                    option = option,
                    selected = option.id == selected.colorId,
                    onClick = { onColorSelected(option) }
                )
            }
        }
    }
}

@Composable
fun WallpaperScreen(
    selected: ChatThemeSelection,
    onBack: () -> Unit,
    onWallpaperClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .navigationBarsPadding()
            .background(HelloColors.DarkBg)
    ) {
        ThemeTopBar(title = "Wallpaper", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(HelloSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            items(ChatThemeStore.Wallpapers) { wallpaper ->
                WallpaperThumbnail(
                    wallpaper = wallpaper,
                    selected = selected.wallpaper == wallpaper,
                    opacity = selected.wallpaperOpacity / 100f,
                    darkMode = selected.darkMode,
                    onClick = { onWallpaperClick(wallpaper) }
                )
            }
        }
    }
}

@Composable
fun ThemePreviewScreen(
    items: List<ChatThemeSelection>,
    initialIndex: Int,
    onBack: () -> Unit,
    onApply: (ChatThemeSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    var index by remember(items, initialIndex) { mutableIntStateOf(initialIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))) }
    var drag by remember { mutableFloatStateOf(0f) }
    var selection by remember(items, index) { mutableStateOf(items.getOrElse(index) { ChatThemeSelection() }) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .navigationBarsPadding()
            .background(HelloColors.DarkBg)
    ) {
        ThemeTopBar(title = "Preview", onBack = onBack, onConfirm = { onApply(selection) })
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(items) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            drag += amount
                            change.consume()
                        },
                        onDragEnd = {
                            if (drag < -80f && index < items.lastIndex) index += 1
                            if (drag > 80f && index > 0) index -= 1
                            drag = 0f
                        }
                    )
                }
        ) {
            FullChatPreview(selection = selection, modifier = Modifier.fillMaxSize())
            PreviewModeToggle(
                darkMode = selection.darkMode,
                onDarkModeChange = { dark ->
                    selection = selection.copy(
                        darkMode = dark,
                        incomingArgb = ChatThemeStore.companionIncomingArgb(selection.colorId, dark),
                        themeId = "custom"
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(HelloSpacing.Lg)
            )
            OpacitySelector(
                selected = selection.wallpaperOpacity,
                onSelected = { opacity -> selection = selection.copy(wallpaperOpacity = opacity, themeId = "custom") },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = HelloSpacing.Md)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HelloSpacing.Md),
            horizontalArrangement = Arrangement.Center
        ) {
            items.forEachIndexed { dotIndex, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (dotIndex == index) 9.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (dotIndex == index) HelloColors.DarkAccent else HelloColors.DarkTextMuted.copy(alpha = 0.45f))
                )
            }
        }
    }
}

@Composable
fun ThemeCard(
    theme: ChatThemeOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val selection = ChatThemeStore.selectionForTheme(theme)
    Column(
        modifier = Modifier.width(142.dp),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(22.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White else HelloColors.DarkBorder,
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable(onClick = onClick)
        ) {
            ChatWallpaperBackground(
                wallpaper = selection.wallpaper,
                opacity = selection.wallpaperOpacity / 100f,
                darkOverride = selection.darkMode,
                modifier = Modifier.fillMaxSize()
            ) {
                MiniMessagePreview(selection = selection, modifier = Modifier.align(Alignment.Center))
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = HelloColors.DarkAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Text(theme.label, color = HelloColors.DarkText, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun ColorCircle(
    option: ChatColorOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(option.color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = option.label, tint = Color.White)
        }
    }
}

@Composable
fun WallpaperThumbnail(
    wallpaper: String,
    selected: Boolean,
    opacity: Float,
    darkMode: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(18.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else HelloColors.DarkBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        ChatWallpaperBackground(
            wallpaper = wallpaper,
            opacity = opacity,
            darkOverride = darkMode,
            modifier = Modifier.fillMaxSize()
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = HelloColors.DarkAccent, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                text = wallpaper.replaceFirstChar { it.uppercase() },
                color = HelloColors.DarkText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(HelloShapes.Pill)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ThemeTopBar(
    title: String,
    onBack: () -> Unit,
    onConfirm: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HelloIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HelloColors.DarkText)
        }
        Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onConfirm != null) {
            HelloIconButton(onClick = onConfirm, active = true) {
                Icon(Icons.Default.Check, contentDescription = "Apply", tint = Color.White)
            }
        } else {
            HelloIconButton(onClick = {}, active = false) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = HelloColors.DarkTextMuted)
            }
        }
    }
}

@Composable
private fun CustomizeRow(
    title: String,
    subtitle: String,
    swatch: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = HelloColors.DarkPanelStrong,
        border = androidx.compose.foundation.BorderStroke(1.dp, HelloColors.DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HelloSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (swatch == Color.Transparent) HelloColors.DarkAccentSoft else swatch),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text(subtitle, color = HelloColors.DarkTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ThemePreviewPanel(selection: ChatThemeSelection, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, HelloColors.DarkBorder, RoundedCornerShape(24.dp))
    ) {
        ChatWallpaperBackground(
            wallpaper = selection.wallpaper,
            opacity = selection.wallpaperOpacity / 100f,
            darkOverride = selection.darkMode,
            modifier = Modifier.fillMaxSize()
        ) {
            MiniMessagePreview(selection = selection, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun MiniMessagePreview(selection: ChatThemeSelection, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewBubble(text = "Hi, are we still on for dinner?", color = selection.incomingColor, textColor = selection.incomingTextColor, alignEnd = false)
        PreviewBubble(text = "Yes. I booked the place.", color = selection.color, alignEnd = true)
        PreviewBubble(text = "Perfect.", color = selection.incomingColor, textColor = selection.incomingTextColor, alignEnd = false)
    }
}

@Composable
private fun FullChatPreview(selection: ChatThemeSelection, modifier: Modifier = Modifier) {
    ChatWallpaperBackground(
        wallpaper = selection.wallpaper,
        opacity = selection.wallpaperOpacity / 100f,
        darkOverride = selection.darkMode,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xxl),
            verticalArrangement = Arrangement.Center
        ) {
            PreviewBubble("This wallpaper feels clean.", selection.incomingColor, selection.incomingTextColor, alignEnd = false)
            Spacer(Modifier.height(10.dp))
            PreviewBubble("The color also matches the chat.", selection.color, selection.outgoingTextColor, alignEnd = true)
            Spacer(Modifier.height(10.dp))
            PreviewBubble("Apply it when you are ready.", selection.incomingColor, selection.incomingTextColor, alignEnd = false)
        }
    }
}

@Composable
private fun PreviewBubble(
    text: String,
    color: Color,
    textColor: Color = ChatThemeStore.readableTextOn(color),
    alignEnd: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(18.dp))
                .background(color.copy(alpha = 0.94f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun PreviewModeToggle(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(HelloShapes.Pill)
            .background(Color.Black.copy(alpha = 0.36f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), HelloShapes.Pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PreviewModeChip(label = "Dark", active = darkMode, onClick = { onDarkModeChange(true) })
        PreviewModeChip(label = "White", active = !darkMode, onClick = { onDarkModeChange(false) })
    }
}

@Composable
private fun PreviewModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) Color(0xFF071219) else Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(HelloShapes.Pill)
            .background(if (active) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    )
}

@Composable
private fun OpacitySelector(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(100, 88, 76, 64, 52, 40)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Opacity", color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        options.forEach { option ->
            val active = selected == option
            Text(
                text = "$option",
                color = if (active) Color(0xFF071219) else Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(HelloShapes.Pill)
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { onSelected(option) }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
    }
}
