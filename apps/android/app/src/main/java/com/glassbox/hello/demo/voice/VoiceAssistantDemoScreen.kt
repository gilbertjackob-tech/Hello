package com.glassbox.hello.demo.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class DemoLanguage(val label: String, val recognizerLanguage: String?) {
    Auto("Auto", null),
    Bangla("বাংলা", "bn-BD"),
    English("English", "en-US")
}

private enum class DemoPhase {
    Idle,
    PermissionRequired,
    SpeechUnavailable,
    WaitingForHello,
    ListeningForCommand,
    Recognized,
    Error
}

private enum class RecognitionMode {
    Wake,
    Command
}

@Composable
fun VoiceAssistantDemoScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<String>() }
    var language by remember { mutableStateOf(DemoLanguage.Auto) }
    var phase by remember { mutableStateOf(DemoPhase.Idle) }
    var livePartial by remember { mutableStateOf("") }
    var finalCommand by remember { mutableStateOf("") }
    var draftCommand by remember { mutableStateOf("") }
    var helperText by remember { mutableStateOf("Say Hello or tap the orb to capture a command.") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var serviceAvailable by remember { mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context)) }
    var hasAudioPermission by remember {
        mutableStateOf(context.hasAudioPermission())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        phase = if (granted) DemoPhase.Idle else DemoPhase.PermissionRequired
        helperText = if (granted) {
            "Microphone is ready. Say Hello or tap to start."
        } else {
            "Microphone permission is required for this isolated voice demo."
        }
    }

    fun resetUi(nextPhase: DemoPhase = DemoPhase.Idle) {
        recognizer?.cancel()
        livePartial = ""
        draftCommand = ""
        phase = nextPhase
        helperText = when (nextPhase) {
            DemoPhase.Idle -> "Say Hello or tap the orb to capture a command."
            DemoPhase.PermissionRequired -> "Microphone permission is required for this isolated voice demo."
            DemoPhase.SpeechUnavailable -> "Android speech recognition is not available on this device."
            else -> helperText
        }
    }

    fun startRecognition(mode: RecognitionMode) {
        serviceAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!serviceAvailable) {
            resetUi(DemoPhase.SpeechUnavailable)
            return
        }
        if (!hasAudioPermission) {
            phase = DemoPhase.PermissionRequired
            helperText = "Grant microphone permission, then try again."
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val activeRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            recognizer = it
        }
        livePartial = ""
        if (mode == RecognitionMode.Command) {
            draftCommand = ""
            phase = DemoPhase.ListeningForCommand
            helperText = "Listening for your Bangla or English command."
        } else {
            phase = DemoPhase.WaitingForHello
            helperText = "Waiting for Hello, হ্যালো, or হেলো."
        }
        activeRecognizer.cancel()

        activeRecognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults.bestSpeechResult()
                    if (text.isBlank()) return
                    livePartial = text
                    if (mode == RecognitionMode.Wake && text.isWakeWord()) {
                        helperText = "Hello detected. Start speaking your command."
                        activeRecognizer.cancel()
                        scope.launch {
                            delay(250)
                            startRecognition(RecognitionMode.Command)
                        }
                    } else if (mode == RecognitionMode.Command) {
                        draftCommand = text
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text = results.bestSpeechResult()
                    livePartial = text
                    if (mode == RecognitionMode.Wake) {
                        if (text.isWakeWord()) {
                            helperText = "Hello detected. Start speaking your command."
                            scope.launch {
                                delay(200)
                                startRecognition(RecognitionMode.Command)
                            }
                        } else {
                            phase = DemoPhase.Error
                            helperText = "Wake word was not heard. Try Hello, হ্যালো, or tap the orb."
                        }
                    } else {
                        if (text.isNotBlank()) {
                            finalCommand = text
                            draftCommand = ""
                            history.add(0, text)
                            while (history.size > 8) history.removeAt(history.lastIndex)
                            phase = DemoPhase.Recognized
                            helperText = "Recognized command is ready to copy."
                        } else {
                            phase = DemoPhase.Error
                            helperText = "No command text was recognized. Try again."
                        }
                    }
                }

                override fun onError(error: Int) {
                    if (mode == RecognitionMode.Command && draftCommand.isNotBlank()) {
                        phase = DemoPhase.Recognized
                        helperText = "Only draft speech was available before recognition stopped."
                    } else {
                        phase = DemoPhase.Error
                        helperText = speechErrorMessage(error)
                    }
                }
            }
        )

        try {
            activeRecognizer.startListening(recognizerIntent(language))
        } catch (exception: RuntimeException) {
            phase = DemoPhase.Error
            helperText = exception.message ?: "Speech recognition could not start."
        }
    }

    LaunchedEffect(Unit) {
        if (!serviceAvailable) {
            phase = DemoPhase.SpeechUnavailable
            helperText = "Android speech recognition is not available on this device."
        } else if (!hasAudioPermission) {
            phase = DemoPhase.PermissionRequired
            helperText = "Microphone permission is required for this isolated voice demo."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }

    VoiceAssistantDemoContent(
        phase = phase,
        language = language,
        livePartial = livePartial,
        finalCommand = finalCommand,
        draftCommand = draftCommand,
        helperText = helperText,
        history = history,
        modifier = modifier,
        onLanguageSelected = {
            language = it
            resetUi(if (hasAudioPermission) DemoPhase.Idle else DemoPhase.PermissionRequired)
        },
        onStartWake = { startRecognition(RecognitionMode.Wake) },
        onCaptureCommand = { startRecognition(RecognitionMode.Command) },
        onStop = { resetUi() },
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onCopy = {
            val text = finalCommand.ifBlank { draftCommand }
            if (text.isNotBlank()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Voice command", text))
                helperText = "Copied recognized command."
            }
        }
    )
}

@Composable
private fun VoiceAssistantDemoContent(
    phase: DemoPhase,
    language: DemoLanguage,
    livePartial: String,
    finalCommand: String,
    draftCommand: String,
    helperText: String,
    history: List<String>,
    modifier: Modifier,
    onLanguageSelected: (DemoLanguage) -> Unit,
    onStartWake: () -> Unit,
    onCaptureCommand: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit,
    onCopy: () -> Unit
) {
    val isListening = phase == DemoPhase.WaitingForHello || phase == DemoPhase.ListeningForCommand
    val isCommandListening = phase == DemoPhase.ListeningForCommand
    val recognizedText = finalCommand.ifBlank { draftCommand }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050B12), Color(0xFF091721))))
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = HelloSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)
    ) {
        item {
            Spacer(modifier = Modifier.height(HelloSpacing.Md))
            Text(
                text = "Voice assistant demo",
                color = HelloColors.DarkText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = phaseLabel(phase),
                color = phaseColor(phase),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HelloSpacing.Xs)
            )
        }
        item {
            LanguageSelector(selected = language, onSelected = onLanguageSelected)
        }
        item {
            GlowingVoiceOrb(
                listening = isListening,
                commandListening = isCommandListening,
                onClick = onCaptureCommand
            )
        }
        item {
            Text(
                text = helperText,
                color = HelloColors.DarkTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HelloSpacing.Md)
            )
        }
        item {
            ActionControls(
                phase = phase,
                hasRecognizedText = recognizedText.isNotBlank(),
                onStartWake = onStartWake,
                onCaptureCommand = onCaptureCommand,
                onStop = onStop,
                onRequestPermission = onRequestPermission,
                onCopy = onCopy
            )
        }
        item {
            TranscriptPanel(
                livePartial = livePartial,
                finalCommand = finalCommand,
                draftCommand = draftCommand,
                history = history,
                onCopy = onCopy
            )
        }
        item { Spacer(modifier = Modifier.height(HelloSpacing.Xxl)) }
    }
}

@Composable
private fun LanguageSelector(
    selected: DemoLanguage,
    onSelected: (DemoLanguage) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DemoLanguage.entries.forEach { option ->
            val active = selected == option
            TextButton(
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (active) HelloColors.DarkAccentSoft else Color.Transparent,
                    contentColor = if (active) HelloColors.DarkAccentStrong else HelloColors.DarkTextMuted
                )
            ) {
                Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GlowingVoiceOrb(
    listening: Boolean,
    commandListening: Boolean,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "voice-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (commandListening) 820 else 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glow = when {
        commandListening -> 1f
        listening -> 0.75f
        else -> 0.42f
    }
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF72F6D4).copy(alpha = 0.32f * glow),
                        Color(0xFF4A7CFF).copy(alpha = 0.22f * glow),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * pulse
                ),
                radius = radius * pulse,
                center = center
            )
            drawCircle(
                color = Color(0xFF40D7FF).copy(alpha = 0.18f * glow),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = 8.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color(0xFF51E6C8).copy(alpha = 0.88f),
                        Color(0xFF2468FF).copy(alpha = 0.48f)
                    ),
                    center = center,
                    radius = radius * 0.62f
                ),
                radius = radius * 0.48f,
                center = center
            )
        }
        Icon(
            imageVector = if (commandListening) Icons.Default.GraphicEq else Icons.Default.Mic,
            contentDescription = "Capture command",
            tint = Color(0xFF061018),
            modifier = Modifier.size(46.dp)
        )
    }
}

@Composable
private fun ActionControls(
    phase: DemoPhase,
    hasRecognizedText: Boolean,
    onStartWake: () -> Unit,
    onCaptureCommand: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (phase) {
            DemoPhase.PermissionRequired -> {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("Grant microphone", modifier = Modifier.padding(start = HelloSpacing.Sm))
                }
            }
            DemoPhase.SpeechUnavailable -> {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Reset", modifier = Modifier.padding(start = HelloSpacing.Sm))
                }
            }
            else -> {
                Button(
                    onClick = onStartWake,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HelloColors.DarkAccent)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("Say Hello / Tap to start", modifier = Modifier.padding(start = HelloSpacing.Sm))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)
                ) {
                    OutlinedButton(
                        onClick = onCaptureCommand,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                        Text("Capture", modifier = Modifier.padding(start = HelloSpacing.Xs))
                    }
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Text("Stop", modifier = Modifier.padding(start = HelloSpacing.Xs))
                    }
                }
                OutlinedButton(
                    onClick = onCopy,
                    enabled = hasRecognizedText,
                    modifier = Modifier.widthIn(min = 180.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text("Copy command", modifier = Modifier.padding(start = HelloSpacing.Sm))
                }
            }
        }
    }
}

@Composable
private fun TranscriptPanel(
    livePartial: String,
    finalCommand: String,
    draftCommand: String,
    history: List<String>,
    onCopy: () -> Unit
) {
    HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Lg) {
        Column(
            modifier = Modifier.padding(HelloSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Transcript",
                    color = HelloColors.DarkText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (draftCommand.isNotBlank() && finalCommand.isBlank()) {
                    HelloPill("Draft", active = true)
                } else if (finalCommand.isNotBlank()) {
                    HelloPill("Final", active = true)
                }
            }
            TranscriptBlock(
                label = "Live partial",
                value = livePartial.ifBlank { "No live speech yet." },
                muted = livePartial.isBlank()
            )
            TranscriptBlock(
                label = "Recognized command",
                value = finalCommand.ifBlank { draftCommand.ifBlank { "No command captured yet." } },
                muted = finalCommand.isBlank() && draftCommand.isBlank(),
                large = true,
                onClick = onCopy
            )
            Text(
                "Recent commands",
                color = HelloColors.DarkText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (history.isEmpty()) {
                Text("Recognized commands will appear here.", color = HelloColors.DarkTextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)) {
                    history.forEach { item ->
                        Surface(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                item,
                                color = HelloColors.DarkText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(HelloSpacing.Md)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBlock(
    label: String,
    value: String,
    muted: Boolean,
    large: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Xs)) {
        Text(label, color = HelloColors.DarkTextMuted, style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
                .padding(HelloSpacing.Md)
        ) {
            Text(
                value,
                color = if (muted) HelloColors.DarkTextMuted else HelloColors.DarkText,
                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (large && !muted) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun Context.hasAudioPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private fun recognizerIntent(language: DemoLanguage): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        language.recognizerLanguage?.let {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, it)
        }
    }
}

private fun Bundle?.bestSpeechResult(): String {
    val matches = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        .orEmpty()
    return matches.firstOrNull().orEmpty().trim()
}

private fun String.isWakeWord(): Boolean {
    val normalized = trim().lowercase(Locale.ROOT)
    return normalized.contains("hello") ||
        normalized.contains("hey hello") ||
        normalized.contains("হ্যালো") ||
        normalized.contains("হেলো")
}

private fun phaseLabel(phase: DemoPhase): String {
    return when (phase) {
        DemoPhase.Idle -> "Idle"
        DemoPhase.PermissionRequired -> "Permission required"
        DemoPhase.SpeechUnavailable -> "Speech service unavailable"
        DemoPhase.WaitingForHello -> "Waiting for Hello"
        DemoPhase.ListeningForCommand -> "Listening for command"
        DemoPhase.Recognized -> "Recognized"
        DemoPhase.Error -> "Error / retry"
    }
}

private fun phaseColor(phase: DemoPhase): Color {
    return when (phase) {
        DemoPhase.Error, DemoPhase.PermissionRequired, DemoPhase.SpeechUnavailable -> HelloColors.DarkDanger
        DemoPhase.Recognized -> HelloColors.DarkAccentStrong
        else -> HelloColors.DarkAccent
    }
}

private fun speechErrorMessage(error: Int): String {
    return when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording failed. Try again."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error. Try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK -> "Network error from the speech service."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network timeout."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match was found. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Tap Stop, then retry."
        SpeechRecognizer.ERROR_SERVER -> "Speech service error. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard before timeout."
        else -> "Speech recognition stopped with error $error."
    }
}
