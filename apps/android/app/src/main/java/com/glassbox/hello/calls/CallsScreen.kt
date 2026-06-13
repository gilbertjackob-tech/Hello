package com.glassbox.hello.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import com.glassbox.hello.ui.components.ErrorView
import com.glassbox.hello.ui.components.HelloAvatar
import com.glassbox.hello.ui.components.HelloCallCard
import com.glassbox.hello.ui.components.HelloEmptyState
import com.glassbox.hello.ui.components.HelloPanel
import com.glassbox.hello.ui.components.HelloPill
import com.glassbox.hello.ui.components.HelloTopBar
import com.glassbox.hello.ui.components.LoadingView
import com.glassbox.hello.ui.theme.ChatThemeStore
import com.glassbox.hello.ui.theme.ChatWallpaperBackground
import com.glassbox.hello.ui.theme.HelloColors
import com.glassbox.hello.ui.theme.HelloShapes
import com.glassbox.hello.ui.theme.HelloSpacing
import com.glassbox.hello.ui.theme.HelloThemeRuntime
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

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
        } else if (hasAudio && needsCamera) {
            callViewModel.acceptIncoming(context, forceAudio = true)
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
            avatarUrl = callState.peerAvatar,
            video = callState.signal?.isVideo == true || callState.activeRoom?.type == "video",
            message = callState.message.orEmpty(),
            onAccept = { acceptIncoming() },
            onDecline = { callViewModel.declineCall() },
            modifier = modifier.fillMaxSize()
        )
        CallUiStatus.Outgoing, CallUiStatus.Connecting -> OutgoingCallScreen(
            name = callState.peerName,
            avatarUrl = callState.peerAvatar,
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
                    avatarUrl = callState.peerAvatar,
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
        CallUiStatus.Failed -> CallStatusDialog(
            title = when (callState.status) {
                CallUiStatus.Ended -> "Call ended"
                CallUiStatus.Declined -> "Call declined"
                CallUiStatus.Missed -> "Missed call"
                CallUiStatus.Busy -> "User busy"
                CallUiStatus.Unavailable -> "User unavailable"
                CallUiStatus.Failed -> "Call failed"
                else -> "Call status"
            },
            message = callState.message ?: "Call ended.",
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

@Composable
private fun CallStatusDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelloColors.DarkPanelStrong,
        title = { Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = HelloColors.DarkTextMuted) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = HelloColors.DarkAccent)
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
    avatarUrl: String? = null,
    video: Boolean,
    message: String = "",
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    CallLobbyScreen(
        modifier = modifier.fillMaxSize(),
        name = name,
        avatarUrl = avatarUrl,
        title = name,
        subtitle = message.ifBlank { "Ringing..." },
        status = "Incoming ${if (video) "video" else "audio"} call",
        pulsingAvatar = true,
        wrapControlsInPanel = false,
        controlsPanel = {
            SwipeCallControls(
                video = video,
                onAccept = onAccept,
                onDecline = onDecline
            )
        }
    ) {
    }
}

@Composable
fun OutgoingCallScreen(
    name: String,
    avatarUrl: String? = null,
    video: Boolean,
    message: String = "Calling...",
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    CallLobbyScreen(
        modifier = modifier.fillMaxSize(),
        name = name,
        avatarUrl = avatarUrl,
        title = name,
        subtitle = message,
        status = if (video) "Outgoing video call" else "Outgoing audio call",
        pulsingAvatar = true,
        controlsPanel = {
            LobbyActionRow {
                EndCallButton(onClick = onCancel)
            }
        }
    ) {
    }
}

@Composable
fun ActiveCallScreen(
    name: String,
    avatarUrl: String? = null,
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
    CallThemeBackdrop(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (video) {
            VideoCallSurface(
                name = name,
                avatarUrl = avatarUrl,
                mediaPhase = mediaPhase,
                cameraOff = cameraOff,
                focusLocalVideo = false,
                onToggleFocus = {},
                visualLook = visualLook,
                onAttachLocalRenderer = onAttachLocalRenderer,
                onAttachRemoteRenderer = onAttachRemoteRenderer
            )
        } else {
            AudioCallSurface(
                name = name,
                avatarUrl = avatarUrl,
                durationSeconds = durationSeconds,
                mediaPhase = mediaPhase
            )
        }
        CallBottomDock(
            modifier = Modifier.align(Alignment.BottomCenter),
            title = name,
            subtitle = if (mediaPhase == CallMediaPhase.Connected) formatCallDuration(durationSeconds) else phaseLabel(mediaPhase),
            videoSettingsOpen = video && videoSettingsOpen,
            settingsPanel = {
                VideoCallSettingsPanel(
                    videoQuality = videoQuality,
                    visualLook = visualLook,
                    onSelectQuality = onSelectQuality,
                    onSelectVisualLook = onSelectVisualLook,
                    onClose = onToggleVideoSettings
                )
            },
            controls = {
                CallControlButton(
                    onClick = onMute,
                    active = muted,
                    icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (muted) "Unmute" else "Mute"
                )
                CallControlButton(
                    onClick = onSpeaker,
                    active = speakerOn,
                    icon = if (speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    label = if (speakerOn) "Speaker" else "Earpiece"
                )
                if (video) {
                    CallControlButton(
                        onClick = onCamera,
                        active = !cameraOff,
                        icon = if (cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        label = if (cameraOff) "Camera off" else "Camera"
                    )
                    CallControlButton(
                        onClick = onSwitchCamera,
                        icon = Icons.Default.FlipCameraAndroid,
                        label = "Flip"
                    )
                    CallControlButton(
                        onClick = onToggleVideoSettings,
                        active = videoSettingsOpen,
                        icon = Icons.Default.Settings,
                        label = "Settings"
                    )
                }
            },
            endCall = { EndCallButton(onClick = onEnd) }
        )
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
    CallThemeBackdrop(
        modifier = modifier
            .fillMaxSize()
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
                Icon(if (speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Speaker", tint = HelloColors.AuthText)
            }
            if (video) {
                RoundCallButton(onClick = onCamera, active = cameraOff, size = 64.dp) {
                    Icon(if (cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = "Camera", tint = HelloColors.AuthText)
                }
                RoundCallButton(onClick = onSwitchCamera, size = 64.dp) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch camera", tint = HelloColors.AuthText)
                }
                RoundCallButton(onClick = onToggleVideoSettings, active = videoSettingsOpen, size = 64.dp) {
                    Icon(Icons.Default.Settings, contentDescription = "Video settings", tint = HelloColors.AuthText)
                }
            }
            RoundCallButton(onClick = onEnd, danger = true, size = 82.dp) {
                Icon(Icons.Default.CallEnd, contentDescription = "Leave group call", tint = HelloColors.AuthText)
            }
        }
    }
}

@Composable
private fun VideoCallSurface(
    name: String,
    avatarUrl: String? = null,
    mediaPhase: CallMediaPhase,
    cameraOff: Boolean,
    focusLocalVideo: Boolean,
    onToggleFocus: () -> Unit,
    visualLook: CallVisualLook,
    onAttachLocalRenderer: ((SurfaceViewRenderer) -> Unit)?,
    onAttachRemoteRenderer: ((SurfaceViewRenderer) -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        HelloColors.DarkBg,
                        HelloColors.BgDeep,
                        HelloColors.DarkBg
                    )
                )
            )
    ) {
        if (focusLocalVideo) {
            if (cameraOff) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HelloAvatar(name = "You", size = 120.dp, online = true)
                        Spacer(modifier = Modifier.height(HelloSpacing.Md))
                        Text("Your camera is off", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                WebRtcRenderer(
                    modifier = Modifier.fillMaxSize(),
                    mirror = true,
                    onRenderer = { onAttachLocalRenderer?.invoke(it) }
                )
            }
        } else {
            WebRtcRenderer(
                modifier = Modifier.fillMaxSize(),
                onRenderer = { onAttachRemoteRenderer?.invoke(it) }
            )
        }
        if (mediaPhase != CallMediaPhase.Connected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HelloColors.DarkBg.copy(alpha = 0.84f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HelloAvatar(name = name, size = 112.dp, online = true, imageUrl = avatarUrl)
                    Spacer(modifier = Modifier.height(HelloSpacing.Lg))
                    Text(name, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                    Text(phaseLabel(mediaPhase), color = HelloColors.DarkAccent)
                }
            }
        }
        PinnedVideoPreview(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 18.dp),
            title = if (focusLocalVideo) name else "You",
            isFocused = focusLocalVideo,
            onClick = onToggleFocus
        ) {
            if (focusLocalVideo) {
                WebRtcRenderer(
                    modifier = Modifier.fillMaxSize(),
                    onRenderer = { onAttachRemoteRenderer?.invoke(it) }
                )
            } else if (cameraOff) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera off", color = HelloColors.DarkTextMuted, textAlign = TextAlign.Center)
                }
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
private fun AudioCallSurface(
    name: String,
    avatarUrl: String? = null,
    durationSeconds: Long,
    mediaPhase: CallMediaPhase
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(HelloSpacing.Xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)
        ) {
            CallStatusBadge(
                eyebrow = if (mediaPhase == CallMediaPhase.Connected) "HELLO CONNECTED" else "HELLO AUDIO",
                label = phaseLabel(mediaPhase)
            )
            Box(contentAlignment = Alignment.Center) {
                CallHeroHalo()
                CallAvatar(name = name, avatarUrl = avatarUrl, pulsing = mediaPhase != CallMediaPhase.Connected, size = 170.dp)
            }
            CallGlassPanel(modifier = Modifier.widthIn(max = 320.dp)) {
                Column(
                    modifier = Modifier.padding(horizontal = HelloSpacing.Xl, vertical = HelloSpacing.Lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(name, color = HelloColors.DarkText, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text(
                        text = if (mediaPhase == CallMediaPhase.Connected) formatCallDuration(durationSeconds) else phaseLabel(mediaPhase),
                        color = HelloColors.DarkTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CallBottomDock(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    videoSettingsOpen: Boolean = false,
    settingsPanel: @Composable (() -> Unit)? = null,
    controls: @Composable RowScope.() -> Unit,
    endCall: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        HelloColors.DarkBg.copy(alpha = 0.28f),
                        HelloColors.DarkBg.copy(alpha = 0.70f),
                        HelloColors.DarkBg.copy(alpha = 0.90f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
            Text(subtitle, color = HelloColors.DarkTextMuted, fontWeight = FontWeight.Medium)
        }
        if (videoSettingsOpen && settingsPanel != null) {
            settingsPanel()
        }
        CallGlassPanel(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                controls()
                Box(
                    modifier = Modifier.width(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    endCall()
                }
            }
        }
    }
}

@Composable
private fun CallThemeBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = HelloThemeRuntime.activePalette.value
    val selection = remember(palette.id) {
        ChatThemeStore.selectionForAppTheme(palette.id)
    }
    ChatWallpaperBackground(
        wallpaper = selection.wallpaper,
        opacity = selection.wallpaperOpacity / 100f,
        modifier = modifier,
        darkOverride = palette.isDark
    ) {
        CallBackdropOrnaments(
            paletteId = palette.id,
            accent = HelloColors.AccentStrong,
            warmAccent = HelloColors.WarmAccent
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            palette.bgDeep.copy(alpha = if (palette.isDark) 0.10f else 0.03f),
                            palette.bgDeep.copy(alpha = if (palette.isDark) 0.34f else 0.08f),
                            palette.bgDeep.copy(alpha = if (palette.isDark) 0.54f else 0.14f)
                        )
                    )
                )
        )
        content()
    }
}

@Composable
private fun CallLobbyScreen(
    name: String,
    avatarUrl: String?,
    title: String,
    subtitle: String,
    status: String,
    modifier: Modifier = Modifier,
    pulsingAvatar: Boolean = false,
    wrapControlsInPanel: Boolean = true,
    controlsPanel: @Composable () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    CallThemeBackdrop(modifier = modifier) {
        overlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HelloSpacing.Xl, vertical = HelloSpacing.Xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            CallStatusBadge(
                eyebrow = if (pulsingAvatar) "HELLO LIVE" else "HELLO CALL",
                label = status
            )
            Spacer(modifier = Modifier.weight(0.10f))
            Box(contentAlignment = Alignment.Center) {
                CallHeroHalo()
                CallAvatar(
                    name = name,
                    avatarUrl = avatarUrl,
                    pulsing = pulsingAvatar,
                    size = 184.dp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(title, color = HelloColors.DarkText, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(subtitle, color = HelloColors.DarkTextMuted, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.weight(0.18f))
            if (wrapControlsInPanel) {
                CallGlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = HelloSpacing.Lg, vertical = HelloSpacing.Lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md)
                    ) {
                        controlsPanel()
                    }
                }
            } else {
                controlsPanel()
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LobbyActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun CallGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(18.dp, HelloShapes.Xl, ambientColor = HelloColors.Accent.copy(alpha = 0.18f))
            .clip(HelloShapes.Xl)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        HelloColors.PanelStrong.copy(alpha = 0.94f),
                        HelloColors.PanelStrong.copy(alpha = 0.88f)
                    )
                )
            )
            .border(1.dp, HelloColors.GlassBorder, HelloShapes.Xl)
    ) {
        content()
    }
}

@Composable
private fun CallStatusBadge(eyebrow: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HelloPill(
            text = eyebrow,
            active = true
        )
        Text(
            text = label,
            color = HelloColors.DarkTextMuted,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CallHeroHalo() {
    Box(
        modifier = Modifier
            .size(280.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        HelloColors.AccentStrong.copy(alpha = 0.16f),
                        HelloColors.WarmAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun RowScope.CallControlButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundCallButton(onClick = onClick, active = active, size = 60.dp) {
            Icon(icon, contentDescription = label, tint = HelloColors.AuthText)
        }
        Text(
            text = label,
            color = HelloColors.DarkTextMuted,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EndCallButton(onClick: () -> Unit) {
    RoundCallButton(onClick = onClick, danger = true, size = 64.dp) {
        Icon(Icons.Default.CallEnd, contentDescription = "Drop call", tint = HelloColors.AuthText)
    }
}

@Composable
private fun PinnedVideoPreview(
    title: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .width(132.dp)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, HelloColors.GlassBorder, RoundedCornerShape(22.dp))
            .background(HelloColors.DarkPanelStrong.copy(alpha = 0.86f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(CircleShape)
                .background(HelloColors.DarkBg.copy(alpha = 0.65f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isFocused) Icons.Default.CropFree else Icons.Default.Fullscreen,
                contentDescription = null,
                tint = HelloColors.AuthText,
                modifier = Modifier.size(14.dp)
            )
            Text(title, color = HelloColors.AuthText, fontWeight = FontWeight.SemiBold)
        }
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = HelloColors.DarkTextMuted,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (label, onClick) ->
                CallSettingChip(
                    label = label,
                    selected = label == selectedLabel,
                    onClick = onClick
                )
            }
        }
    }
}

@Composable
private fun CallSettingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val background = if (selected) HelloColors.DarkAccent.copy(alpha = 0.24f) else Color.Transparent
    val borderColor = if (selected) HelloColors.DarkAccent.copy(alpha = 0.86f) else HelloColors.GlassBorder.copy(alpha = 0.55f)
    val textColor = if (selected) HelloColors.DarkText else HelloColors.DarkTextMuted
    Box(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .height(38.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        HelloColors.DarkPanelStrong.copy(alpha = 0.96f),
                        HelloColors.DarkPanel.copy(alpha = 0.92f)
                    )
                )
            )
            .border(1.dp, HelloColors.GlassBorder.copy(alpha = 0.58f), HelloShapes.Lg)
            .padding(horizontal = HelloSpacing.Md, vertical = HelloSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(HelloSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Video settings", color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                Text("${videoQuality.label} quality - ${visualLook.label} look", color = HelloColors.DarkTextMuted)
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

private fun Context.hasPermission(permission: String): Boolean {
    return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun CallStage(
    name: String,
    avatarUrl: String? = null,
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    pulsingAvatar: Boolean = false,
    controls: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    if (pulsingAvatar) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            HelloColors.BgBase,
                            HelloColors.BgDeep,
                            HelloColors.BgBase
                        )
                    )
                )
                .padding(horizontal = HelloSpacing.Xl, vertical = HelloSpacing.Xxl)
        ) {
            CallWallpaperTexture(modifier = Modifier.fillMaxSize())
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(180)) + scaleIn(tween(240), initialScale = 0.96f) + slideInVertically(tween(240)) { it / 12 },
                exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.98f) + slideOutVertically(tween(140)) { it / 10 }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(36.dp))
                    Text(
                        text = name,
                        color = HelloColors.AccentStrong,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Hello - ${label.lowercase()} ♡",
                        color = HelloColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(64.dp))
                    CallAvatar(name = name, avatarUrl = avatarUrl, pulsing = true, size = 148.dp)
                    if (detail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(detail, color = HelloColors.AccentStrong, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    controls()
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        return
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HelloColors.DarkBg.copy(alpha = 0.88f))
            .padding(HelloSpacing.Xxl),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.92f) + slideInVertically(tween(220)) { it / 8 },
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.96f) + slideOutVertically(tween(140)) { it / 10 }
        ) {
            HelloPanel(modifier = Modifier.fillMaxWidth(), strong = true, shape = HelloShapes.Xl) {
                Column(
                    modifier = Modifier.padding(horizontal = HelloSpacing.Xxl, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HelloSpacing.Lg)
                ) {
                    CallAvatar(name = name, avatarUrl = avatarUrl, pulsing = pulsingAvatar)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = HelloColors.DarkText, fontWeight = FontWeight.Bold)
                        Text(name, color = HelloColors.DarkTextMuted)
                        if (detail.isNotBlank()) {
                            Text(detail, color = HelloColors.DarkAccent, fontWeight = FontWeight.Medium)
                        }
                    }
                    controls()
                }
            }
        }
    }
}

@Composable
private fun CallWallpaperTexture(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val dot = Color.White.copy(alpha = 0.30f)
        val line = HelloColors.Accent.copy(alpha = 0.08f)
        val step = 54.dp.toPx()
        var y = 18.dp.toPx()
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 16.dp.toPx() else 42.dp.toPx()
            while (x < size.width) {
                drawCircle(dot, radius = 2.2.dp.toPx(), center = Offset(x, y))
                drawLine(line, Offset(x - 10.dp.toPx(), y + 12.dp.toPx()), Offset(x + 16.dp.toPx(), y + 22.dp.toPx()), strokeWidth = 1.dp.toPx())
                x += step
            }
            row += 1
            y += step
        }
    }
}

@Composable
private fun CallBackdropOrnaments(
    paletteId: String,
    accent: Color,
    warmAccent: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val isCute = paletteId == "cute"
        val softAccent = accent.copy(alpha = if (isCute) 0.14f else 0.10f)
        val softWarm = warmAccent.copy(alpha = if (isCute) 0.16f else 0.08f)
        drawCircle(
            brush = Brush.radialGradient(listOf(softAccent, Color.Transparent)),
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.18f, size.height * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(softWarm, Color.Transparent)),
            radius = size.minDimension * 0.34f,
            center = Offset(size.width * 0.82f, size.height * 0.74f)
        )
        val strokeColor = if (isCute) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.14f)
        drawCircle(
            color = strokeColor,
            radius = size.minDimension * 0.20f,
            center = Offset(size.width * 0.80f, size.height * 0.22f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
        )
        drawCircle(
            color = strokeColor.copy(alpha = strokeColor.alpha * 0.72f),
            radius = size.minDimension * 0.14f,
            center = Offset(size.width * 0.16f, size.height * 0.68f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
        if (isCute) {
            val star = Color.White.copy(alpha = 0.82f)
            listOf(
                Offset(size.width * 0.10f, size.height * 0.30f),
                Offset(size.width * 0.88f, size.height * 0.34f),
                Offset(size.width * 0.26f, size.height * 0.84f),
                Offset(size.width * 0.74f, size.height * 0.58f)
            ).forEach { center ->
                val r = 8.dp.toPx()
                drawLine(star, Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = 1.4.dp.toPx())
                drawLine(star, Offset(center.x, center.y - r), Offset(center.x, center.y + r), strokeWidth = 1.4.dp.toPx())
            }
        }
    }
}

@Composable
private fun CallAvatar(name: String, avatarUrl: String? = null, pulsing: Boolean, size: androidx.compose.ui.unit.Dp = 106.dp) {
    val transition = rememberInfiniteTransition(label = "call-pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Restart),
        label = "pulse-scale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Restart),
        label = "pulse-alpha"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size + 34.dp)) {
        if (pulsing) {
            Box(
                modifier = Modifier
                    .size(size + 12.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(HelloColors.DarkAccent.copy(alpha = pulseAlpha))
            )
        }
        Box(
            modifier = Modifier
                .size(size + 12.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            HelloColors.AccentStrong.copy(alpha = 0.94f),
                            HelloColors.Accent.copy(alpha = 0.78f),
                            HelloColors.WarmAccent.copy(alpha = 0.56f)
                        )
                    )
                )
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(HelloColors.DarkPanelStrong)
                    .padding(3.dp)
            ) {
                HelloAvatar(name = name, size = size, online = true, imageUrl = avatarUrl)
            }
        }
    }
}

@Composable
private fun SwipeCallControls(
    video: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HelloSpacing.Sm)
    ) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val thumbSize = 72.dp
        val maxDrag = with(density) { ((maxWidth - thumbSize) / 2).toPx() }
        val threshold = maxDrag * 0.68f
        var dragTarget by remember { mutableStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        var thresholdHit by remember { mutableStateOf(false) }
        val dragX by animateFloatAsState(
            targetValue = dragTarget,
            animationSpec = tween(if (dragging) 0 else 180),
            label = "call-swipe"
        )
        val progress = if (maxDrag > 0f) (dragX.absoluteValue / maxDrag).coerceIn(0f, 1f) else 0f
        val actionColor = when {
            dragX > 0f -> HelloColors.DarkAccent
            dragX < 0f -> HelloColors.DarkDanger
            else -> HelloColors.PanelStrong
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(CircleShape)
                .background(HelloColors.Text.copy(alpha = 0.58f))
                .pointerInput(maxDrag) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            thresholdHit = false
                        },
                        onDragEnd = {
                            dragging = false
                            when {
                                dragTarget > threshold -> onAccept()
                                dragTarget < -threshold -> onDecline()
                                else -> dragTarget = 0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            dragTarget = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragTarget = (dragTarget + dragAmount.x).coerceIn(-maxDrag, maxDrag)
                            if (!thresholdHit && dragTarget.absoluteValue >= threshold) {
                                thresholdHit = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (thresholdHit && dragTarget.absoluteValue < threshold * 0.75f) {
                                thresholdHit = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PhoneDisabled, contentDescription = "Swipe left to decline", tint = HelloColors.DarkDanger.copy(alpha = 0.86f))
                Icon(if (video) Icons.Default.Videocam else Icons.Default.Call, contentDescription = "Swipe right to accept", tint = HelloColors.DarkAccent.copy(alpha = 0.92f))
            }
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .offset { IntOffset(dragX.roundToInt(), 0) }
                    .clip(CircleShape)
                    .background(actionColor.copy(alpha = 0.68f + progress * 0.32f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        dragX < -threshold -> Icons.Default.PhoneDisabled
                        dragX > threshold && video -> Icons.Default.Videocam
                        else -> Icons.Default.Call
                    },
                    contentDescription = if (dragX < 0f) "Decline call" else "Accept call",
                    tint = if (dragX > 0f) HelloColors.DarkBg else HelloColors.AuthText
                )
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
                    active -> HelloColors.WarmAccent
                    else -> HelloColors.PanelStrong
                }
            )
    ) {
        content()
    }
}
