package com.camraw.app.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.camraw.app.CameraAppController
import com.camraw.app.ui.theme.GlassBottomSheet
import com.camraw.app.ui.theme.GlassControl
import com.camraw.app.ui.theme.GlassTextButton
import com.camraw.app.ui.theme.LiquidGlassSurface
import com.camraw.app.ui.theme.PreviewBackdrop
import com.camraw.app.ui.theme.ShutterButton
import com.camraw.core.camera.api.CameraConnectionType
import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CameraError
import com.camraw.core.camera.api.CameraSession
import com.camraw.core.camera.api.CapabilityState
import com.camraw.core.camera.api.CaptureFormat
import com.camraw.core.camera.api.CaptureState
import com.camraw.core.camera.api.FocusPoint
import com.camraw.core.camera.api.PreviewSurface
import com.camraw.core.camera.api.SettingValue
import kotlinx.coroutines.launch

@Composable
fun CamrawApp(
    controller: CameraAppController,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
) {
    val session by controller.activeSession.collectAsStateWithLifecycle()
    val devices by controller.devices.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val error by controller.lastError.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (session == null) {
            DevicePickerScreen(
                devices = devices,
                status = status,
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = onRequestCameraPermission,
                onConnect = controller::connectAsync,
            )
        } else {
            CameraScreen(
                session = session!!,
                busy = busy,
                status = status,
                error = error,
                onCapture = { controller.captureAsync(CaptureFormat.Jpeg) },
                onRequestCameraPermission = onRequestCameraPermission,
                onDisconnect = controller::closeSessionAsync,
            )
        }
    }
}

@Composable
private fun DevicePickerScreen(
    devices: List<CameraDeviceInfo>,
    status: String,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onConnect: (CameraDeviceInfo) -> Unit,
) {
    val backdrop = PreviewBackdrop(luminance = 0.34f)
    Box(Modifier.fillMaxSize()) {
        FakeSensorAnimation(Modifier.fillMaxSize(), quiet = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "CAMRAW",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices) { device ->
                    DeviceRow(
                        device = device,
                        backdrop = backdrop,
                        permissionReady = hasCameraPermission,
                        onRequestCameraPermission = onRequestCameraPermission,
                        onConnect = onConnect,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: CameraDeviceInfo,
    backdrop: PreviewBackdrop,
    permissionReady: Boolean,
    onRequestCameraPermission: () -> Unit,
    onConnect: (CameraDeviceInfo) -> Unit,
) {
    val needsPermission = device.requiresPermission && !permissionReady
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (needsPermission) onRequestCameraPermission() else onConnect(device)
            },
        backdrop = backdrop,
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (device.connectionType == CameraConnectionType.Virtual) {
                    Icons.Rounded.BugReport
                } else {
                    Icons.Rounded.PhotoCamera
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = device.capabilitySummary.joinToString(" / ").ifBlank { device.providerName },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (needsPermission) "Grant" else "Open",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFB9ECFF),
            )
        }
    }
}

@Composable
private fun CameraScreen(
    session: CameraSession,
    busy: Boolean,
    status: String,
    error: CameraError?,
    onCapture: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var backdrop by remember { mutableStateOf(PreviewBackdrop()) }
    var showSettings by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var showHistogram by remember { mutableStateOf(false) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val captureState = session.capture?.captureState?.collectAsStateWithLifecycle()?.value ?: CaptureState.Idle
    val previewState = session.preview?.previewState?.collectAsStateWithLifecycle()?.value
        ?: com.camraw.core.camera.api.PreviewState()

    LaunchedEffect(session.sessionId) {
        session.preview?.frames?.collect { frame ->
            backdrop = PreviewBackdrop(luminance = frame.luminance, temperature = 0.48f + frame.luminance * 0.18f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { previewSize = it }
            .pointerInput(session.sessionId, previewSize) {
                detectTapGestures { offset ->
                    val width = previewSize.width.coerceAtLeast(1)
                    val height = previewSize.height.coerceAtLeast(1)
                    scope.launch {
                        session.focus?.focusAt(
                            FocusPoint(
                                normalizedX = offset.x / width,
                                normalizedY = offset.y / height,
                            ),
                        )
                    }
                }
            },
    ) {
        CameraPreviewHost(session = session, modifier = Modifier.fillMaxSize())
        if (session.deviceInfo.connectionType == CameraConnectionType.Virtual) {
            FakeSensorAnimation(Modifier.fillMaxSize(), quiet = false)
        }
        if (showGrid) {
            GridOverlay(Modifier.fillMaxSize())
        }
        TopStatusBar(
            session = session,
            backdrop = backdrop,
            status = status,
            onDisconnect = onDisconnect,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(14.dp),
        )
        RightToolbar(
            backdrop = backdrop,
            showGrid = showGrid,
            showHistogram = showHistogram,
            onToggleGrid = { showGrid = !showGrid },
            onToggleHistogram = { showHistogram = !showHistogram },
            onSettings = { showSettings = true },
            onDebug = { showDebug = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
        )
        BottomCaptureBar(
            backdrop = backdrop,
            busy = busy || captureState !is CaptureState.Idle,
            onCapture = onCapture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        )
        ErrorBanner(
            error = error ?: previewState.error,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 82.dp, start = 16.dp, end = 16.dp),
        )
        if (showHistogram) {
            HistogramPill(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, bottom = 34.dp),
            )
        }
        SettingsPanel(
            visible = showSettings,
            session = session,
            backdrop = backdrop,
            onClose = { showSettings = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        DebugPanel(
            visible = showDebug,
            session = session,
            backdrop = backdrop,
            onClose = { showDebug = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopStatusBar(
    session: CameraSession,
    backdrop: PreviewBackdrop,
    status: String,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewState = session.preview?.previewState?.collectAsStateWithLifecycle()?.value
        ?: com.camraw.core.camera.api.PreviewState()
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        backdrop = backdrop,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.deviceInfo.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${session.deviceInfo.providerName} / ${"%.1f".format(previewState.fps)} fps / $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlassControl(
                icon = Icons.Rounded.Cameraswitch,
                contentDescription = "Switch camera",
                backdrop = backdrop,
                onClick = onDisconnect,
            )
        }
    }
}

@Composable
private fun RightToolbar(
    backdrop: PreviewBackdrop,
    showGrid: Boolean,
    showHistogram: Boolean,
    onToggleGrid: () -> Unit,
    onToggleHistogram: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassControl(Icons.Rounded.GridOn, "Grid", selected = showGrid, backdrop = backdrop, onClick = onToggleGrid)
        GlassControl(Icons.Rounded.GraphicEq, "Histogram", selected = showHistogram, backdrop = backdrop, onClick = onToggleHistogram)
        GlassControl(Icons.Rounded.Tune, "Settings", backdrop = backdrop, onClick = onSettings)
        GlassControl(Icons.Rounded.BugReport, "Debug", backdrop = backdrop, onClick = onDebug)
    }
}

@Composable
private fun BottomCaptureBar(
    backdrop: PreviewBackdrop,
    busy: Boolean,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShutterButton(busy = busy, backdrop = backdrop, onClick = onCapture)
    }
}

@Composable
private fun SettingsPanel(
    visible: Boolean,
    session: CameraSession,
    backdrop: PreviewBackdrop,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = session.settings?.settings?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val scope = rememberCoroutineScope()
    GlassBottomSheet(visible = visible, backdrop = backdrop, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Controls",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                GlassControl(Icons.Rounded.Close, "Close", backdrop = backdrop, onClick = onClose)
            }
            Spacer(Modifier.height(16.dp))
            settings.filter { it.state == CapabilityState.Available }.forEach { setting ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(setting.displayName, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        setting.availableValues.take(5).forEach { value ->
                            GlassTextButton(
                                text = value.label,
                                backdrop = backdrop,
                            ) {
                                scope.launch { session.settings?.writeSetting(setting.id, value) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugPanel(
    visible: Boolean,
    session: CameraSession,
    backdrop: PreviewBackdrop,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capabilities by session.capabilities.collectAsStateWithLifecycle()
    GlassBottomSheet(visible = visible, backdrop = backdrop, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxHeight(0.72f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Debug",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                GlassControl(Icons.Rounded.Close, "Close", backdrop = backdrop, onClick = onClose)
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn {
                item {
                    Text(
                        text = capabilities.toDebugJson(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    error: CameraError?,
    backdrop: PreviewBackdrop,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        LiquidGlassSurface(
            backdrop = backdrop,
            contentPadding = PaddingValues(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFFFD089))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = error?.userMessageZh.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HistogramPill(backdrop: PreviewBackdrop, modifier: Modifier = Modifier) {
    LiquidGlassSurface(
        modifier = modifier,
        backdrop = backdrop,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Luma ${"%02d".format((backdrop.luminance * 100).toInt())}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CameraPreviewHost(session: CameraSession, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    if (session.deviceInfo.connectionType == CameraConnectionType.Virtual) {
        LaunchedEffect(session.sessionId) {
            session.preview?.bind(PreviewSurface(Any(), 1, 1, 0))
        }
        DisposableEffect(session.sessionId) {
            onDispose { scope.launch { session.preview?.unbind() } }
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        texture.setDefaultBufferSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
                        surface = Surface(texture)
                        scope.launch {
                            runCatching {
                                session.preview?.bind(
                                    PreviewSurface(
                                        nativeSurface = surface ?: return@runCatching,
                                        width = width,
                                        height = height,
                                        rotationDegrees = when (display?.rotation) {
                                            Surface.ROTATION_90 -> 90
                                            Surface.ROTATION_180 -> 180
                                            Surface.ROTATION_270 -> 270
                                            else -> 0
                                        },
                                    ),
                                )
                            }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        texture.setDefaultBufferSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        val oldSurface = surface
                        surface = null
                        scope.launch {
                            session.preview?.unbind()
                            oldSurface?.release()
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

@Composable
private fun FakeSensorAnimation(modifier: Modifier = Modifier, quiet: Boolean) {
    val transition = rememberInfiniteTransition(label = "fake-sensor")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (quiet) 9000 else 4200), RepeatMode.Reverse),
        label = "phase",
    )
    Canvas(modifier = modifier) {
        drawRect(Color.Black)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF4F6FFF).copy(alpha = 0.28f + phase * 0.12f),
                    Color(0xFF071013),
                    Color.Black,
                ),
                center = Offset(size.width * (0.35f + phase * 0.35f), size.height * 0.38f),
                radius = size.maxDimension * 0.82f,
            ),
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f)),
                start = Offset(0f, size.height * 0.35f),
                end = Offset(0f, size.height),
            ),
        )
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = Color.White.copy(alpha = 0.22f)
        val stroke = Stroke(width = 1.dp.toPx())
        listOf(1f / 3f, 2f / 3f).forEach { fraction ->
            drawLine(color, Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height), stroke.width)
            drawLine(color, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), stroke.width)
        }
        drawLine(
            Color.White.copy(alpha = 0.30f),
            Offset(size.width / 2f - 16.dp.toPx(), size.height / 2f),
            Offset(size.width / 2f + 16.dp.toPx(), size.height / 2f),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            Color.White.copy(alpha = 0.30f),
            Offset(size.width / 2f, size.height / 2f - 16.dp.toPx()),
            Offset(size.width / 2f, size.height / 2f + 16.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )
    }
}
