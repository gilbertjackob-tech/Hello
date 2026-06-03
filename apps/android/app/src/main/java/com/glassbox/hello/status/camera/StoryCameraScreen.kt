package com.glassbox.hello.status.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.glassbox.hello.ui.components.HelloIconButton
import com.glassbox.hello.ui.components.HelloPrimaryButton
import com.glassbox.hello.ui.components.HelloSearchBar
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun StoryCameraScreen(
    currentUserId: String,
    onClose: () -> Unit,
    onPosted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryCameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val faceLandmarker = remember { StoryFaceLandmarker.create(context.applicationContext) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setPermission(granted)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
            if (bytes != null) viewModel.openDraft(it, bytes, "gallery-story.jpg")
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermission(granted)
        if (!granted) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(faceLandmarker) {
        onDispose { faceLandmarker.close() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        if (state.draft == null) {
            if (state.permissionGranted) {
                CameraPreview(
                    lensFacing = state.lensFacing,
                    flashMode = state.flashMode,
                    hdMode = state.hdMode,
                    onImageCaptureReady = { imageCapture = it },
                    onReady = { viewModel.setCameraReady(it) },
                    onError = { viewModel.setError(it) },
                    modifier = Modifier.fillMaxSize()
                )
                LiveEffectOverlay(state)
                if (state.gridEnabled) StoryGrid(modifier = Modifier.fillMaxSize())
            } else {
                PermissionEmptyState(
                    onGrant = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onGallery = { galleryLauncher.launch("image/*") }
                )
            }
            StoryCameraChrome(
                state = state,
                countdown = countdown,
                onClose = onClose,
                onGallery = { galleryLauncher.launch("image/*") },
                onCapture = {
                    val capture = imageCapture ?: return@StoryCameraChrome
                    scope.launch {
                        if (state.timerSeconds > 0) {
                            for (next in state.timerSeconds downTo 1) {
                                countdown = next
                                delay(1000)
                            }
                        }
                        countdown = 0
                        capturePhoto(context, capture, viewModel)
                    }
                },
                onTool = { tool ->
                    when (tool) {
                        StoryTool.Flip -> viewModel.flipCamera()
                        StoryTool.Flash -> viewModel.toggleFlash()
                        StoryTool.Hd -> viewModel.toggleHd()
                        StoryTool.Selfie -> viewModel.selectCategory(StoryEffectCategory.Face)
                        StoryTool.Timer -> viewModel.cycleTimer()
                        StoryTool.GreenScreen -> viewModel.toggleGreenScreen()
                        StoryTool.Grid -> viewModel.toggleGrid()
                    }
                },
                onToggleTools = { viewModel.toggleTools() },
                onCategory = { viewModel.selectCategory(it) },
                onEffect = { viewModel.selectEffect(it) }
            )
        } else {
            StoryEditor(
                state = state,
                onClose = onClose,
                onRetake = { viewModel.retake() },
                onCaption = { viewModel.updateCaption(it) },
                onSticker = { viewModel.updateSticker(it) },
                onCategory = { viewModel.selectCategory(it) },
                onEffect = { viewModel.selectEffect(it) },
                onSaveDraft = { viewModel.saveDraft(context) },
                onPost = { viewModel.post(context, currentUserId, onPosted) }
            )
        }
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    flashMode: Int,
    hdMode: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onReady: (Boolean) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    LaunchedEffect(lensFacing, flashMode, hdMode) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(if (hdMode) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .build()
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            onImageCaptureReady(capture)
            onReady(true)
        }.onFailure {
            onReady(false)
            onError(it.message ?: "Camera unavailable")
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

private fun capturePhoto(context: Context, imageCapture: ImageCapture, viewModel: StoryCameraViewModel) {
    val dir = File(context.cacheDir, "stories").apply { mkdirs() }
    val file = File(dir, "hello-story-${System.currentTimeMillis()}.jpg")
    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        output,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri ?: Uri.fromFile(file)
                viewModel.openDraft(uri, file.readBytes(), file.name)
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.setError(exception.message ?: "Capture failed")
            }
        }
    )
}

@Composable
private fun StoryCameraChrome(
    state: StoryCameraState,
    countdown: Int,
    onClose: () -> Unit,
    onGallery: () -> Unit,
    onCapture: () -> Unit,
    onTool: (StoryTool) -> Unit,
    onToggleTools: () -> Unit,
    onCategory: (StoryEffectCategory) -> Unit,
    onEffect: (StoryEffect) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(HelloSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            HelloIconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close stories camera", tint = Color.White)
            }
            Surface(shape = HelloShapes.Pill, color = Color.White.copy(alpha = 0.18f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.FilterVintage, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(state.selectedEffect.label, color = Color.White, maxLines = 1)
                }
            }
        }
        StoryToolRail(
            state = state,
            onTool = onTool,
            onToggleTools = onToggleTools,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = HelloSpacing.Md)
        )
        if (countdown > 0) {
            Text(
                countdown.toString(),
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (state.error != null) {
            Text(
                state.error,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
                    .background(Color.Black.copy(alpha = 0.5f), HelloShapes.Pill)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        BottomLensDeck(
            selectedCategory = state.selectedCategory,
            selectedEffect = state.selectedEffect,
            onCategory = onCategory,
            onEffect = onEffect,
            onGallery = onGallery,
            onCapture = onCapture,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun StoryToolRail(
    state: StoryCameraState,
    onTool: (StoryTool) -> Unit,
    onToggleTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = if (state.expandedTools) {
        listOf(StoryTool.Flip, StoryTool.Flash, StoryTool.Hd, StoryTool.Selfie, StoryTool.Timer, StoryTool.GreenScreen, StoryTool.Grid)
    } else {
        listOf(StoryTool.Flip, StoryTool.Flash, StoryTool.Timer)
    }
    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.34f), HelloShapes.Pill).padding(8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tools.forEach { tool ->
            ToolButton(tool = tool, state = state, expanded = state.expandedTools, onClick = { onTool(tool) })
        }
        HelloIconButton(onClick = onToggleTools) {
            Icon(if (state.expandedTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "More story tools", tint = Color.White)
        }
    }
}

@Composable
private fun ToolButton(tool: StoryTool, state: StoryCameraState, expanded: Boolean, onClick: () -> Unit) {
    val label = when (tool) {
        StoryTool.Flip -> "Flip"
        StoryTool.Flash -> if (state.flashMode == ImageCapture.FLASH_MODE_OFF) "Flash" else "Flash On"
        StoryTool.Hd -> if (state.hdMode) "HD Mode" else "Fast Mode"
        StoryTool.Selfie -> "Selfie"
        StoryTool.Timer -> if (state.timerSeconds == 0) "Timer" else "${state.timerSeconds}s"
        StoryTool.GreenScreen -> if (state.greenScreen) "Green On" else "Green Screen"
        StoryTool.Grid -> if (state.gridEnabled) "Grid On" else "Grid"
    }
    val icon = when (tool) {
        StoryTool.Flip -> Icons.Default.FlipCameraAndroid
        StoryTool.Flash -> Icons.Default.FlashOn
        StoryTool.Hd -> Icons.Default.HighQuality
        StoryTool.Selfie -> Icons.Default.Person
        StoryTool.Timer -> Icons.Default.Timer
        StoryTool.GreenScreen -> Icons.Default.Wallpaper
        StoryTool.Grid -> Icons.Default.GridOn
    }
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (expanded) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun BottomLensDeck(
    selectedCategory: StoryEffectCategory,
    selectedEffect: StoryEffect,
    onCategory: (StoryEffectCategory) -> Unit,
    onEffect: (StoryEffect) -> Unit,
    onGallery: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.48f))
            .padding(bottom = 18.dp, top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item { DeckSideButton("Memories", Icons.Default.PhotoLibrary, onGallery) }
            items(StoryEffects.all.filter { it.category == selectedCategory }, key = { it.id }) { effect ->
                EffectBubble(effect = effect, selected = effect.id == selectedEffect.id, onClick = { onEffect(effect) })
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(28.dp))
            CaptureButton(onClick = onCapture)
            Icon(Icons.Default.EmojiEmotions, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
            items(StoryEffectCategory.entries) { category ->
                Text(
                    category.label,
                    color = if (category == selectedCategory) Color.White else Color.White.copy(alpha = 0.46f),
                    fontWeight = if (category == selectedCategory) FontWeight.Black else FontWeight.Bold,
                    modifier = Modifier
                        .clip(HelloShapes.Pill)
                        .background(if (category == selectedCategory) Color.Black.copy(alpha = 0.44f) else Color.Transparent)
                        .clickable { onCategory(category) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DeckSideButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(34.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EffectBubble(effect: StoryEffect, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(if (selected) 76.dp else 58.dp)
                .clip(CircleShape)
                .background(effect.accent)
                .border(if (selected) 6.dp else 2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (effect.faceAware) Icon(Icons.Default.Person, contentDescription = null, tint = Color.Black.copy(alpha = 0.7f))
            if (effect.backgroundAware) Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color.Black.copy(alpha = 0.7f))
        }
        Text(effect.label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CaptureButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(86.dp)
            .clip(CircleShape)
            .border(7.dp, Color.White, CircleShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
    }
}

@Composable
private fun LiveEffectOverlay(state: StoryCameraState) {
    val effect = state.selectedEffect
    if (!effect.faceAware && !effect.backgroundAware) return
    Box(modifier = Modifier.fillMaxSize()) {
        if (effect.backgroundAware) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(effect.accent.copy(alpha = if (state.greenScreen) 0.22f else 0.12f))
            )
        }
        if (effect.faceAware) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.42f)
                drawCircle(effect.accent.copy(alpha = 0.72f), radius = size.width * 0.23f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx()))
            }
        }
    }
}

@Composable
private fun StoryGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = Color.White.copy(alpha = 0.22f)
        drawLine(color, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = 1.dp.toPx())
        drawLine(color, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = 1.dp.toPx())
        drawLine(color, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = 1.dp.toPx())
        drawLine(color, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun StoryEditor(
    state: StoryCameraState,
    onClose: () -> Unit,
    onRetake: () -> Unit,
    onCaption: (String) -> Unit,
    onSticker: (String?) -> Unit,
    onCategory: (StoryEffectCategory) -> Unit,
    onEffect: (StoryEffect) -> Unit,
    onSaveDraft: () -> Unit,
    onPost: () -> Unit
) {
    val draft = state.draft ?: return
    var stickerOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(draft.sourceUri).crossfade(true).build(),
            contentDescription = "Story draft",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        LiveEditorEffect(draft)
        TextEditorOverlay(draft = draft, onCaption = onCaption)
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(HelloSpacing.Lg),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EditorAction("Sticker", Icons.Default.EmojiEmotions) { stickerOpen = !stickerOpen }
            EditorAction("Save", Icons.Default.Save) { onSaveDraft() }
        }
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(HelloSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HelloIconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
            TextButton(onClick = onRetake) { Text("Retake", color = Color.White, fontWeight = FontWeight.Bold) }
        }
        if (stickerOpen) {
            LazyRow(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.58f), HelloShapes.Pill).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(listOf("*", "HELLO", "WOW", "DAY", "LOVE")) { sticker ->
                    Text(
                        sticker,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { onSticker(sticker); stickerOpen = false }.padding(12.dp)
                    )
                }
            }
        }
        EditorEffectDeck(
            selectedCategory = state.selectedCategory,
            selectedEffect = draft.selectedEffect,
            onCategory = onCategory,
            onEffect = onEffect,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        HelloPrimaryButton(
            text = if (state.posting) "Posting..." else "Post Story",
            onClick = onPost,
            enabled = !state.posting,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 158.dp).width(156.dp)
        )
        if (state.posting) CircularProgressIndicator(color = HelloColors.StoryAccent, modifier = Modifier.align(Alignment.Center))
        if (state.error != null) Text(state.error, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 86.dp).background(Color.Black.copy(alpha = 0.62f), HelloShapes.Pill).padding(12.dp))
    }
}

@Composable
private fun EditorEffectDeck(
    selectedCategory: StoryEffectCategory,
    selectedEffect: StoryEffect,
    onCategory: (StoryEffectCategory) -> Unit,
    onEffect: (StoryEffect) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(bottom = 18.dp, top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(StoryEffects.all.filter { it.category == selectedCategory }, key = { it.id }) { effect ->
                EffectBubble(effect = effect, selected = effect.id == selectedEffect.id, onClick = { onEffect(effect) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
            items(StoryEffectCategory.entries) { category ->
                Text(
                    category.label,
                    color = if (category == selectedCategory) Color.White else Color.White.copy(alpha = 0.46f),
                    fontWeight = if (category == selectedCategory) FontWeight.Black else FontWeight.Bold,
                    modifier = Modifier
                        .clip(HelloShapes.Pill)
                        .background(if (category == selectedCategory) Color.Black.copy(alpha = 0.44f) else Color.Transparent)
                        .clickable { onCategory(category) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LiveEditorEffect(draft: StoryDraft) {
    if (draft.selectedEffect.backgroundAware) {
        Box(modifier = Modifier.fillMaxSize().background(draft.selectedEffect.accent.copy(alpha = 0.16f)))
    }
    if (draft.selectedEffect.faceAware) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(draft.selectedEffect.accent.copy(alpha = 0.75f), radius = size.width * 0.23f, center = Offset(size.width / 2f, size.height * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx()))
        }
    }
    if (!draft.sticker.isNullOrBlank()) {
        Text(draft.sticker, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 150.dp, start = 260.dp))
    }
}

@Composable
private fun TextEditorOverlay(draft: StoryDraft, onCaption: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HelloSearchBar(
            value = draft.caption,
            onValueChange = onCaption,
            placeholder = "Add text",
            modifier = Modifier
                .offset {
                    IntOffset(
                        (draft.textOffsetX * 180f).roundToInt(),
                        (draft.textOffsetY * 360f).roundToInt()
                    )
                }
                .padding(horizontal = 38.dp)
                .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume() } },
            dark = true
        )
    }
}

@Composable
private fun EditorAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).background(Color.Black.copy(alpha = 0.36f), HelloShapes.Pill).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

@Composable
private fun PermissionEmptyState(onGrant: () -> Unit, onGallery: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(HelloSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Camera access is needed for Hello Stories.", color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(18.dp))
        HelloPrimaryButton("Enable camera", onClick = onGrant)
        Spacer(modifier = Modifier.height(10.dp))
        Surface(onClick = onGallery, shape = HelloShapes.Pill, color = Color.White.copy(alpha = 0.12f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                Text("Use gallery", color = Color.White)
            }
        }
    }
}
