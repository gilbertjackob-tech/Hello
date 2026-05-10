package com.glassbox.hello.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloCallCard
import com.glassbox.hello.ui.components.HelloEmptyState
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallsScreen(
    currentUserId: String,
    callViewModel: CallViewModel,
    modifier: Modifier = Modifier
) {
    val callState by callViewModel.state.collectAsState()
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(currentUserId, reloadToken) {
        callViewModel.loadHistory(currentUserId)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(HelloColors.DarkBg)
            .padding(horizontal = HelloSpacing.Lg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HelloTopBar(
                eyebrow = "HELLO CALLS",
                title = "Calls",
                modifier = Modifier.padding(top = HelloSpacing.Sm, bottom = HelloSpacing.Md)
            ) {
                HelloPill("Calls", active = true)
            }

            when {
                callState.loadingHistory -> LoadingView(modifier = Modifier.weight(1f))
                callState.historyError != null -> ErrorView(
                    message = callState.historyError ?: "Failed to load calls",
                    modifier = Modifier.weight(1f),
                    onRetry = { reloadToken += 1 }
                )
                callState.history.isEmpty() -> HelloEmptyState(
                    title = "No calls yet",
                    message = "Your audio and video calls will appear here.",
                    modifier = Modifier.weight(1f)
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                    callState.history.forEach { call ->
                        HelloCallCard(
                            name = call.otherUser.name,
                            detail = "${call.direction.replaceFirstChar { it.uppercase() }} ${call.type} call - ${call.status}",
                            time = formatCallTime(call.startedAt),
                            missed = call.status == "missed" || call.status == "unavailable" || call.status == "failed",
                            video = call.type == "video",
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalCallOverlay(
    callViewModel: CallViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val callState by callViewModel.state.collectAsState()
    var permissionDialog by remember { mutableStateOf(false) }
    var videoSettingsOpen by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val needsCamera = callState.signal?.isVideo == true || callState.activeRoom?.type == "video"
        val hasAudio = grants[Manifest.permission.RECORD_AUDIO] == true ||
            context.hasPermission(Manifest.permission.RECORD_AUDIO)
        val hasCamera = !needsCamera || grants[Manifest.permission.CAMERA] == true ||
            context.hasPermission(Manifest.permission.CAMERA)
        if (hasAudio && hasCamera) {
            callViewModel.acceptIncoming(context)
        } else {
            permissionDialog = true
        }
    }

    fun acceptIncoming() {
        val needsCamera = callState.signal?.isVideo == true || callState.activeRoom?.type == "video"
        val permissions = if (needsCamera) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        if (permissions.all { context.hasPermission(it) }) {
            callViewModel.acceptIncoming(context)
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    when (callState.status) {
        CallUiStatus.Incoming -> IncomingCallScreen(
            name = callState.peerName,
            video = callState.signal?.isVideo == true || callState.activeRoom?.type == "video",
            message = callState.message.orEmpty(),
            onAccept = { acceptIncoming() },
            onDecline = { callViewModel.declineCall() },
            modifier = modifier.fillMaxSize()
        )
        CallUiStatus.Outgoing, CallUiStatus.Connecting -> OutgoingCallScreen(
            name = callState.peerName,
            video = callState.signal?.isVideo == true,
            message = callState.message ?: "Calling...",
            onCancel = { callViewModel.endCall("ended_by_caller") },
            modifier = modifier.fillMaxSize()
        )
        CallUiStatus.Active -> {
            val room = callState.activeRoom
            if (room != null) {
                GroupActiveCallScreen(
                    name = callState.peerName,
                    video = room.type == "video",
                    durationSeconds = callState.durationSeconds,
                    participants = callState.roomParticipants,
                    muted = callState.muted,
                    speakerOn = callState.speakerOn,
                    cameraOff = callState.cameraOff,
                    videoQuality = callState.selectedVideoQuality,
                    visualLook = callState.selectedVisualLook,
                    videoSettingsOpen = videoSettingsOpen,
                    onToggleVideoSettings = { videoSettingsOpen = !videoSettingsOpen },
                    onMute = { callViewModel.toggleMute() },
                    onSpeaker = { callViewModel.toggleSpeaker(context) },
                    onCamera = { callViewModel.toggleCamera() },
                    onSwitchCamera = { callViewModel.switchCamera() },
                    onSelectQuality = { callViewModel.setVideoQuality(it) },
                    onSelectVisualLook = { callViewModel.setVisualLook(it) },
                    onEnd = { callViewModel.endCall("ended") },
                    modifier = modifier.fillMaxSize()
                )
            } else {
                ActiveCallScreen(
                    name = callState.peerName,
                    video = callState.signal?.isVideo == true,
                    durationSeconds = callState.durationSeconds,
                    mediaPhase = callState.mediaPhase,
                    muted = callState.muted,
                    speakerOn = callState.speakerOn,
                    cameraOff = callState.cameraOff,
                    videoQuality = callState.selectedVideoQuality,
                    visualLook = callState.selectedVisualLook,
                    videoSettingsOpen = videoSettingsOpen,
                    onToggleVideoSettings = { videoSettingsOpen = !videoSettingsOpen },
                    onMute = { callViewModel.toggleMute() },
                    onSpeaker = { callViewModel.toggleSpeaker(context) },
                    onCamera = { callViewModel.toggleCamera() },
                    onSwitchCamera = { callViewModel.switchCamera() },
                    onSelectQuality = { callViewModel.setVideoQuality(it) },
                    onSelectVisualLook = { callViewModel.setVisualLook(it) },
                    onAttachLocalRenderer = { callViewModel.attachLocalRenderer(it) },
                    onAttachRemoteRenderer = { callViewModel.attachRemoteRenderer(it) },
                    onEnd = { callViewModel.endCall("ended") },
                    modifier = modifier.fillMaxSize()
                )
            }
        }
        CallUiStatus.Ended,
        CallUiStatus.Declined,
        CallUiStatus.Missed,
        CallUiStatus.Busy,
        CallUiStatus.Unavailable,
        CallUiStatus.Failed -> CallResultDialog(
            message = callState.message ?: "Call ended",
            onDismiss = { callViewModel.dismissCallOverlay() }
        )
        CallUiStatus.PermissionDenied -> PermissionRequiredDialog(
            message = callState.message ?: "Camera/microphone permission is needed for calls.",
            onOpenSettings = {
                callViewModel.dismissCallOverlay()
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            },
            onDismiss = { callViewModel.dismissCallOverlay() }
        )
        CallUiStatus.Idle -> Unit
    }

    if (permissionDialog) {
        AlertDialog(
            onDismissRequest = { permissionDialog = false },
            containerColor = HelloColors.DarkPanelStrong,
            title = { Text("Camera/microphone permission", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
            text = { Text("Camera/microphone permission is needed for calls.", color = HelloColors.DarkTextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    permissionDialog = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("Open Settings", color = HelloColors.DarkAccent) }
            },
            dismissButton = {
                TextButton(onClick = { permissionDialog = false }) {
                    Text("Cancel", color = HelloColors.DarkTextMuted)
                }
            }
        )
    }

}

@Composable
private fun PermissionRequiredDialog(
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Camera/microphone permission", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = HelloColors.DarkAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HelloColors.DarkTextMuted)
            }
        }
    )
}

private fun formatCallTime(timestamp: Long): String {
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}

@Composable
fun IncomingCallScreen(
    name: String,
    video: Boolean,
    message: String = "",
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    CallStage(
        modifier = modifier,
        name = name,
        label = "Incoming ${if (video) "Video" else "Audio"} Call",
        detail = message.ifBlank { "Ringing" }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundCallButton(onClick = onDecline, danger = true, size = 68.dp) {
                Icon(Icons.Default.PhoneDisabled, contentDescription = "Decline", tint = HelloColors.AuthText)
            }
            RoundCallButton(onClick = onAccept, active = true, size = 68.dp) {
                Icon(if (video) Icons.Default.Videocam else Icons.Default.Call, contentDescription = "Accept", tint = HelloColors.DarkBg)
            }
        }
    }
}

@Composable
fun OutgoingCallScreen(
    name: String,
    video: Boolean,
    message: String = "Calling...",
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    CallStage(
        modifier = modifier,
        name = name,
        label = if (video) "Video Call" else "Audio Call",
        detail = message
    ) {
        RoundCallButton(onClick = onCancel, danger = true, size = 72.dp) {
            Icon(Icons.Default.CallEnd, contentDescription = "Cancel", tint = HelloColors.AuthText)
        }
    }
}

@Composable
fun ActiveCallScreen(
    name: String,
    video: Boolean,
    durationSeconds: Long = 0,
    mediaPhase: CallMediaPhase = CallMediaPhase.Connected,
    muted: Boolean = false,
    speakerOn: Boolean = true,
    cameraOff: Boolean = false,
    videoQuality: VideoQualityProfile = VideoQualityProfile.Auto,
    visualLook: CallVisualLook = CallVisualLook.Natural,
    videoSettingsOpen: Boolean = false,
    onToggleVideoSettings: () -> Unit = {},
    onMute: () -> Unit = {},
    onSpeaker: () -> Unit = {},
    onCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onSelectQuality: (VideoQualityProfile) -> Unit = {},
    onSelectVisualLook: (CallVisualLook) -> Unit = {},
    onAttachLocalRenderer: ((SurfaceViewRenderer) -> Unit)? = null,
    onAttachRemoteRenderer: ((SurfaceViewRenderer) -> Unit)? = null,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
    ) {
        if (video) {
            VideoCallSurface(
                name = name,
                mediaPhase = mediaPhase,
                cameraOff = cameraOff,
                visualLook = visualLook,
                onAttachLocalRenderer = onAttachLocalRenderer,
                onAttachRemoteRenderer = onAttachRemoteRenderer
            )
        } else {
            AudioCallSurface(name = name, durationSeconds = durationSeconds, mediaPhase = mediaPhase)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(HelloColors.DarkBg.copy(alpha = 0.72f))
                .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
        ) {
            Text(
                text = if (mediaPhase == CallMediaPhase.Connected) formatCallDuration(durationSeconds) else phaseLabel(mediaPhase),
                color = HelloColors.DarkTextMuted,
                fontWeight = FontWeight.Medium
            )
            if (video) {
                if (videoSettingsOpen) {
                    VideoCallSettingsPanel(
                        videoQuality = videoQuality,
                        visualLook = visualLook,
                        onSelectQuality = onSelectQuality,
                        onSelectVisualLook = onSelectVisualLook,
                        onClose = onToggleVideoSettings
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundCallButton(onClick = onMute, active = muted, size = 64.dp) {
                    Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Mute", tint = HelloColors.AuthText)
                }
                RoundCallButton(onClick = onSpeaker, active = speakerOn, size = 64.dp) {
                    Icon(if (speakerOn) Icons.Default.Speaker else Icons.Default.SpeakerNotesOff, contentDescription = "Speaker", tint = HelloColors.AuthText)
                }
                if (video) {
                    RoundCallButton(onClick = onCamera, active = cameraOff, size = 64.dp) {
                        Icon(if (cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = "Camera", tint = HelloColors.AuthText)
                    }
                    RoundCallButton(onClick = onSwitchCamera, size = 64.dp) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = HelloColors.AuthText)
                    }
                    RoundCallButton(onClick = onToggleVideoSettings, active = videoSettingsOpen, size = 64.dp) {
                        Icon(Icons.Default.Settings, contentDescription = "Video settings", tint = HelloColors.AuthText)
                    }
                } else {
                    RoundCallButton(onClick = {}, size = 64.dp) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "Audio", tint = HelloColors.DarkTextMuted)
                    }
                }
                RoundCallButton(onClick = onEnd, danger = true, size = 72.dp) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = HelloColors.AuthText)
                }
            }
        }
    }
}

@Composable
fun GroupActiveCallScreen(
    name: String,
    video: Boolean,
    durationSeconds: Long,
    participants: List<String>,
    muted: Boolean,
    speakerOn: Boolean,
    cameraOff: Boolean,
    videoQuality: VideoQualityProfile = VideoQualityProfile.Auto,
    visualLook: CallVisualLook = CallVisualLook.Natural,
    videoSettingsOpen: Boolean = false,
    onToggleVideoSettings: () -> Unit = {},
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSelectQuality: (VideoQualityProfile) -> Unit = {},
    onSelectVisualLook: (CallVisualLook) -> Unit = {},
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg)
            .padding(HelloSpacing.Xl)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)
        ) {
            Text("Mesh group call", color = HelloColors.DarkAccent, fontWeight = FontWeight.Bold)
            Text(name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(formatCallDuration(durationSeconds), color = HelloColors.DarkTextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(HelloSpacing.Sm)) {
                participants.take(4).forEachIndexed { index, participant ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HelloAvatar(name = participant, size = 64.dp, online = true)
                        Text(
                            text = if (index == 0) "Host" else "Joined",
                            color = HelloColors.DarkTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Text(
                text = if (video && cameraOff) "Camera off" else if (video) "Video call" else "Audio call",
                color = HelloColors.DarkTextMuted
            )
            if (video) {
                if (videoSettingsOpen) {
                    VideoCallSettingsPanel(
                        videoQuality = videoQuality,
                        visualLook = visualLook,
                        onSelectQuality = onSelectQuality,
                        onSelectVisualLook = onSelectVisualLook,
                        onClose = onToggleVideoSettings
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = HelloSpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundCallButton(onClick = onMute, active = muted, size = 64.dp) {
                Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Mute", tint = HelloColors.AuthText)
            }
            RoundCallButton(onClick = onSpeaker, active = speakerOn, size = 64.dp) {
                Icon(if (speakerOn) Icons.Default.Speaker else Icons.Default.SpeakerNotesOff, contentDescription = "Speaker", tint = HelloColors.AuthText)
            }
            if (video) {
                RoundCallButton(onClick = onCamera, active = cameraOff, size = 64.dp) {
                    Icon(if (cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = "Camera", tint = HelloColors.AuthText)
                }
                RoundCallButton(onClick = onSwitchCamera, size = 64.dp) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = HelloColors.AuthText)
                }
                RoundCallButton(onClick = onToggleVideoSettings, active = videoSettingsOpen, size = 64.dp) {
                    Icon(Icons.Default.Settings, contentDescription = "Video settings", tint = HelloColors.AuthText)
                }
            }
            RoundCallButton(onClick = onEnd, danger = true, size = 72.dp) {
                Icon(Icons.Default.CallEnd, contentDescription = "Leave group call", tint = HelloColors.AuthText)
            }
        }
    }
}

@Composable
private fun VideoCallSurface(
    name: String,
    mediaPhase: CallMediaPhase,
    cameraOff: Boolean,
    visualLook: CallVisualLook,
    onAttachLocalRenderer: ((SurfaceViewRenderer) -> Unit)?,
    onAttachRemoteRenderer: ((SurfaceViewRenderer) -> Unit)?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        WebRtcRenderer(
            modifier = Modifier.fillMaxSize(),
            onRenderer = { onAttachRemoteRenderer?.invoke(it) }
        )
        if (mediaPhase != CallMediaPhase.Connected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HelloColors.DarkBg.copy(alpha = 0.84f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HelloAvatar(name = name, size = 112.dp, online = true)
                    Spacer(modifier = Modifier.height(HelloSpacing.Lg))
                    Text(name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                    Text(phaseLabel(mediaPhase), color = HelloColors.DarkAccent)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(HelloSpacing.Xl)
                .size(width = 116.dp, height = 168.dp)
                .clip(HelloShapes.Lg)
                .background(HelloColors.DarkPanelStrong),
            contentAlignment = Alignment.Center
        ) {
            if (cameraOff) {
                Text("Camera off", color = HelloColors.DarkTextMuted, textAlign = TextAlign.Center)
            } else {
                WebRtcRenderer(
                    modifier = Modifier.fillMaxSize(),
                    mirror = true,
                    onRenderer = { onAttachLocalRenderer?.invoke(it) }
                )
            }
        }
        VideoLookOverlay(look = visualLook, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun AudioCallSurface(name: String, durationSeconds: Long, mediaPhase: CallMediaPhase) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(HelloSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HelloAvatar(name = name, size = 128.dp, online = true)
        Spacer(modifier = Modifier.height(HelloSpacing.Lg))
        Text(name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
        Text(
            text = if (mediaPhase == CallMediaPhase.Connected) formatCallDuration(durationSeconds) else phaseLabel(mediaPhase),
            color = HelloColors.DarkTextMuted
        )
    }
}

@Composable
private fun WebRtcRenderer(
    modifier: Modifier,
    mirror: Boolean = false,
    onRenderer: (SurfaceViewRenderer) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).also { renderer ->
                renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                renderer.setMirror(mirror)
                onRenderer(renderer)
            }
        },
        update = { it.setMirror(mirror) }
    )
}

private fun formatCallDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%02d:%02d".format(minutes, remaining)
}

private fun phaseLabel(phase: CallMediaPhase): String = when (phase) {
    CallMediaPhase.Preparing -> "Preparing media..."
    CallMediaPhase.Ringing -> "Ringing..."
    CallMediaPhase.Connecting -> "Connecting..."
    CallMediaPhase.Connected -> "Connected"
    CallMediaPhase.Reconnecting -> "Reconnecting..."
    CallMediaPhase.Error -> "Call media failed"
    CallMediaPhase.Closed -> "Call ended"
    CallMediaPhase.Idle -> "Ready"
}

@Composable
private fun CallSelectorRow(
    title: String,
    selectedLabel: String,
    options: List<Pair<String, () -> Unit>>
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$title: $selectedLabel",
            color = HelloColors.DarkTextMuted,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (label, onClick) ->
                HelloPill(
                    text = label,
                    active = label == selectedLabel,
                    modifier = Modifier.clickable(onClick = onClick)
                )
            }
        }
    }
}

@Composable
private fun VideoCallSettingsPanel(
    videoQuality: VideoQualityProfile,
    visualLook: CallVisualLook,
    onSelectQuality: (VideoQualityProfile) -> Unit,
    onSelectVisualLook: (CallVisualLook) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HelloShapes.Lg)
            .background(HelloColors.DarkPanelStrong.copy(alpha = 0.92f))
            .padding(HelloSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Video settings", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text("Resolution and filter", color = HelloColors.DarkTextMuted)
            }
            TextButton(onClick = onClose) {
                Text("Close", color = HelloColors.DarkAccent)
            }
        }
        CallSelectorRow(
            title = "Quality",
            selectedLabel = videoQuality.label,
            options = VideoQualityProfile.entries.map { it.label to { onSelectQuality(it) } }
        )
        CallSelectorRow(
            title = "Filter",
            selectedLabel = visualLook.label,
            options = CallVisualLook.entries.map { it.label to { onSelectVisualLook(it) } }
        )
    }
}

@Composable
private fun VideoLookOverlay(look: CallVisualLook, modifier: Modifier = Modifier) {
    val tint = when (look) {
        CallVisualLook.Natural -> HelloColors.DarkBg.copy(alpha = 0f)
        CallVisualLook.Vivid -> androidx.compose.ui.graphics.Color(0x121FC6FF)
        CallVisualLook.Warm -> androidx.compose.ui.graphics.Color(0x14FFB36B)
        CallVisualLook.Cool -> androidx.compose.ui.graphics.Color(0x102D6CFF)
        CallVisualLook.Clean -> androidx.compose.ui.graphics.Color(0x10FFFFFF)
    }
    if (tint.alpha == 0f) return
    Box(
        modifier = modifier
            .background(tint)
    )
}

@Composable
private fun CallResultDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text("Call status", color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = HelloColors.DarkAccent)
            }
        }
    )
}

private fun Context.hasPermission(permission: String): Boolean {
    return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun CallStage(
    name: String,
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg.copy(alpha = 0.96f))
            .padding(HelloSpacing.Xxl),
        contentAlignment = Alignment.Center
    ) {
        HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Xl) {
            Column(
                modifier = Modifier.padding(horizontal = HelloSpacing.Xxl, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)
            ) {
                HelloAvatar(name = name, size = 106.dp, online = true)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                    Text(name, color = HelloColors.DarkTextMuted)
                    Text(detail, color = HelloColors.DarkAccent, fontWeight = FontWeight.Medium)
                }
                controls()
            }
        }
    }
}

@Composable
private fun RoundCallButton(
    onClick: () -> Unit,
    active: Boolean = false,
    danger: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    danger -> HelloColors.DarkDanger
                    active -> HelloColors.DarkAccent
                    else -> HelloColors.DarkPanelStrong
                }
            )
    ) {
        content()
    }
}
