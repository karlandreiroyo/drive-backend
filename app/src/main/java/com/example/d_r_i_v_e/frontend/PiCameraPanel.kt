package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PiCameraPanel(isDrivingActive: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { PiCameraController(context) }
    val state by controller.state.collectAsState()
    var enabled by rememberSaveable { mutableStateOf(false) }
    val shouldRun by rememberUpdatedState(enabled && isDrivingActive)

    LaunchedEffect(enabled, isDrivingActive) {
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Raspberry Pi camera", color = Color.White, fontSize = 13.sp)
                Text(
                    text = if (enabled) "Toggle OFF to hide" else "Toggle ON to connect & stream",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        if (enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.piIp,
                    onValueChange = controller::setPiIp,
                    modifier = Modifier.weight(1f),
                    label = { Text("Pi IP address") },
                    singleLine = true,
                    isError = state.ipError,
                    enabled = !state.isBusy && !state.isRequested,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.LightGray,
                        focusedLabelColor = Color.LightGray,
                        unfocusedLabelColor = Color.LightGray
                    )
                )
                TextButton(
                    onClick = controller::connectToPi,
                    enabled = !state.isBusy && !state.isRequested,
                    modifier = Modifier.widthIn(min = 76.dp)
                ) { Text("Connect", color = if (state.isBusy || state.isRequested) Color.Gray else Color(0xFFAAAAFF)) }
            }
            Text(
                text = if (!isDrivingActive && !state.isBusy && !state.ipError) {
                    "${state.message}\nUse Start Driving Session to start Camera/ML."
                } else state.message,
                color = if (state.ipError) Color(0xFFFF6B6B) else Color.LightGray,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (isDrivingActive) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color.Black),
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
                    if (state.isRequested && !state.isPlaying && state.player != null) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    }
                }
                Text("Pi video above · Phone monitoring below", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}
