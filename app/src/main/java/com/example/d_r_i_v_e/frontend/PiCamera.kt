package com.example.d_r_i_v_e.frontend

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.d_r_i_v_e.backend.PiCameraController
import com.example.d_r_i_v_e.backend.PiCameraState

@androidx.annotation.OptIn(UnstableApi::class)
internal class PiCameraHandle(
    private val controller: PiCameraController,
    private val stateHolder: State<PiCameraState>,
    val enabled: MutableState<Boolean>
) {
    val state: PiCameraState get() = stateHolder.value
    fun setPiIp(value: String) = controller.setPiIp(value)
    fun connectToPi() = controller.connectToPi()
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberPiCamera(isDrivingActive: Boolean): PiCameraHandle {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { PiCameraController(context) }
    val state = controller.state.collectAsState()
    val enabled = rememberSaveable { mutableStateOf(false) }
    val shouldRun by rememberUpdatedState(enabled.value && isDrivingActive)

    LaunchedEffect(enabled.value, isDrivingActive) {
        if (shouldRun && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            controller.startML()
        } else {
            controller.stopML()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (shouldRun) controller.startML()
                Lifecycle.Event.ON_STOP -> controller.stopML()
                Lifecycle.Event.ON_DESTROY -> controller.close()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.close()
        }
    }

    return remember(controller, state, enabled) { PiCameraHandle(controller, state, enabled) }
}

private enum class PiTone(val color: Color) {
    Off(Color(0xFF8E8E93)),
    Ok(Color(0xFF34C759)),
    Pending(Color(0xFFFFCC00)),
    Error(Color(0xFFFF6B6B))
}

private fun PiCameraState.isFailure(): Boolean =
    message.startsWith("Cannot") || message.contains("failed")

private fun piSummary(enabled: Boolean, isDrivingActive: Boolean, state: PiCameraState): Pair<String, PiTone> = when {
    !enabled -> "Off" to PiTone.Off
    state.ipError -> "Invalid Pi IP address" to PiTone.Error
    state.isPlaying -> "Live · ${state.piIp}" to PiTone.Ok
    state.isFailure() -> state.message to PiTone.Error
    state.isBusy || state.isRequested -> state.message to PiTone.Pending
    state.message == "Pi connected" -> "Connected · starts with session" to PiTone.Ok
    !isDrivingActive -> "Starts with your driving session" to PiTone.Off
    else -> state.message to PiTone.Pending
}

@Composable
internal fun PiCameraBar(
    handle: PiCameraHandle,
    isDrivingActive: Boolean,
    modifier: Modifier = Modifier
) {
    var enabled by handle.enabled
    var showSettings by remember { mutableStateOf(false) }
    val (status, tone) = piSummary(enabled, isDrivingActive, handle.state)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF232325))
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Videocam,
            contentDescription = null,
            tint = if (enabled) Color.White else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Pi Camera", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(tone.color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = status,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (enabled) {
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Pi camera settings",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = { enabled = it },
            modifier = Modifier.scale(0.8f)
        )
    }

    if (showSettings && enabled) {
        PiCameraSettingsDialog(
            handle = handle,
            isDrivingActive = isDrivingActive,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun PiCameraSettingsDialog(
    handle: PiCameraHandle,
    isDrivingActive: Boolean,
    onDismiss: () -> Unit
) {
    val state = handle.state
    val locked = state.isBusy || state.isRequested
    val accent = Color(0xFFAAAAFF)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Raspberry Pi camera", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.piIp,
                    onValueChange = handle::setPiIp,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pi IP address") },
                    singleLine = true,
                    isError = state.ipError,
                    enabled = !locked,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.LightGray,
                        focusedLabelColor = Color.LightGray,
                        unfocusedLabelColor = Color.LightGray
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.message,
                    color = if (state.ipError || state.isFailure()) Color(0xFFFF6B6B) else Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        state.isRequested -> "End the driving session to change the IP address."
                        !isDrivingActive -> "The Pi camera starts and stops with your driving session."
                        else -> "Tap the Pi video on the live feed to enlarge it."
                    },
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = accent) }
        },
        dismissButton = {
            TextButton(onClick = handle::connectToPi, enabled = !locked) {
                Text("Test connection", color = if (locked) Color.Gray else accent)
            }
        }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PiCameraPip(handle: PiCameraHandle, modifier: Modifier = Modifier) {
    val state = handle.state
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .animateContentSize()
            .then(if (expanded) Modifier.fillMaxWidth(0.65f) else Modifier.width(128.dp))
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable { expanded = !expanded },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.player = state.player
                view.keepScreenOn = state.isPlaying
            },
            onRelease = { view ->
                view.keepScreenOn = false
                view.player = null
            },
            modifier = Modifier.matchParentSize()
        )
        if (!state.isPlaying) {
            if (state.isRequested && !state.isFailure()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Pi offline", color = Color.Gray, fontSize = 10.sp)
            }
        }
        Text(
            text = "PI",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
