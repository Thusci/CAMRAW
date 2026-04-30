package com.camraw.app.ui

import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.camraw.app.CameraAppController
import com.camraw.app.ui.theme.GlassBottomSheet
import com.camraw.app.ui.theme.GlassControl
import com.camraw.app.ui.theme.LiquidGlassSurface
import com.camraw.app.ui.theme.PreviewBackdrop
import com.camraw.app.ui.theme.ShutterButton
import com.camraw.core.camera.api.CameraCapabilities
import com.camraw.core.camera.api.CameraCapability
import com.camraw.core.camera.api.CameraConnectionType
import com.camraw.core.camera.api.CameraDeviceInfo
import com.camraw.core.camera.api.CameraError
import com.camraw.core.camera.api.CameraSettingDescriptor
import com.camraw.core.camera.api.CameraSession
import com.camraw.core.camera.api.CapabilityState
import com.camraw.core.camera.api.CaptureFormat
import com.camraw.core.camera.api.CaptureState
import com.camraw.core.camera.api.FocusPoint
import com.camraw.core.camera.api.PreviewSurface
import com.camraw.core.camera.api.SettingValue
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
                onCapture = { format -> controller.captureAsync(format) },
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
    onCapture: (CaptureFormat) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var backdrop by remember { mutableStateOf(PreviewBackdrop()) }
    var showSettings by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var showHistogram by remember { mutableStateOf(false) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val panelOpen = showSettings || showDebug
    val scope = rememberCoroutineScope()
    val captureState = session.capture?.captureState?.collectAsStateWithLifecycle()?.value ?: CaptureState.Idle
    val previewState = session.preview?.previewState?.collectAsStateWithLifecycle()?.value
        ?: com.camraw.core.camera.api.PreviewState()
    val capabilities by session.capabilities.collectAsStateWithLifecycle()
    val supportedFormats = remember(capabilities) { capabilities.supportedCaptureFormats() }
    var selectedFormat by remember(session.sessionId) { mutableStateOf(supportedFormats.firstOrNull() ?: CaptureFormat.Jpeg) }

    LaunchedEffect(supportedFormats) {
        if (selectedFormat !in supportedFormats) {
            selectedFormat = supportedFormats.firstOrNull() ?: CaptureFormat.Jpeg
        }
    }

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
            .pointerInput(session.sessionId, previewSize, panelOpen) {
                detectTapGestures { offset ->
                    if (panelOpen) return@detectTapGestures
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
        if (!panelOpen) {
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
                selectedFormat = selectedFormat,
                supportedFormats = supportedFormats,
                onFormatSelected = { selectedFormat = it },
                onCapture = { onCapture(selectedFormat) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            )
        }
        ErrorBanner(
            error = error ?: previewState.error,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 82.dp, start = 16.dp, end = 16.dp),
        )
        if (showHistogram && !panelOpen) {
            HistogramPill(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 18.dp, bottom = 34.dp),
            )
        }
        PanelScrim(
            visible = panelOpen,
            onDismiss = {
                showSettings = false
                showDebug = false
            },
        )
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
private fun PanelScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
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
        materialOpacity = 0.48f,
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
    selectedFormat: CaptureFormat,
    supportedFormats: List<CaptureFormat>,
    onFormatSelected: (CaptureFormat) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CaptureFormatSelector(
            formats = supportedFormats,
            selectedFormat = selectedFormat,
            backdrop = backdrop,
            onSelect = onFormatSelected,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShutterButton(busy = busy, backdrop = backdrop, onClick = onCapture)
        }
    }
}

@Composable
private fun CaptureFormatSelector(
    formats: List<CaptureFormat>,
    selectedFormat: CaptureFormat,
    backdrop: PreviewBackdrop,
    onSelect: (CaptureFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (formats.isEmpty()) return
    LiquidGlassSurface(
        modifier = modifier,
        backdrop = backdrop,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        blurRadius = 22.dp,
        tonalOpacity = 0.42f,
        materialOpacity = 0.56f,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            formats.forEach { format ->
                CaptureFormatChip(
                    format = format,
                    selected = format == selectedFormat,
                    onClick = { onSelect(format) },
                )
            }
        }
    }
}

@Composable
private fun CaptureFormatChip(
    format: CaptureFormat,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMedium),
        label = "format-chip-scale",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = format.displayLabel(),
            color = if (selected) Color(0xFFB9ECFF) else Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
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
    val proSettings = settings
        .filter { it.id in ProSettingOrder }
        .sortedBy { ProSettingOrder.indexOf(it.id) }
    val selectableSettings = proSettings.filter {
        it.state == CapabilityState.Available && it.writable && it.availableValues.isNotEmpty()
    }
    var activeSettingId by remember(session.sessionId) { mutableStateOf<String?>(null) }
    LaunchedEffect(selectableSettings.map { it.id }) {
        if (activeSettingId !in selectableSettings.map { it.id }) {
            activeSettingId = selectableSettings.firstOrNull()?.id
        }
    }
    val activeSetting = selectableSettings.firstOrNull { it.id == activeSettingId }
        ?: selectableSettings.firstOrNull()

    GlassBottomSheet(visible = visible, backdrop = backdrop, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PRO",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                GlassControl(Icons.Rounded.Close, "Close", backdrop = backdrop, onClick = onClose)
            }
            Spacer(Modifier.height(16.dp))
            if (selectableSettings.isEmpty()) {
                Text(
                    text = proSettings.firstOrNull()?.userReadableReason ?: "当前设备未开放手动拍摄参数",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                ProSettingStrip(
                    settings = selectableSettings,
                    activeSettingId = activeSetting?.id,
                    backdrop = backdrop,
                    onSelect = { activeSettingId = it.id },
                )
                Spacer(Modifier.height(18.dp))
                activeSetting?.let { setting ->
                    ProParameterWheel(
                        setting = setting,
                        backdrop = backdrop,
                        onValueCommitted = { value ->
                            scope.launch { session.settings?.writeSetting(setting.id, value) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProSettingStrip(
    settings: List<CameraSettingDescriptor>,
    activeSettingId: String?,
    backdrop: PreviewBackdrop,
    onSelect: (CameraSettingDescriptor) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        settings.forEach { setting ->
            ProSettingChip(
                setting = setting,
                selected = setting.id == activeSettingId,
                backdrop = backdrop,
                onClick = { onSelect(setting) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProSettingChip(
    setting: CameraSettingDescriptor,
    selected: Boolean,
    backdrop: PreviewBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMedium),
        label = "pro-chip-scale",
    )
    LiquidGlassSurface(
        modifier = modifier
            .height(58.dp)
            .widthIn(min = 58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        backdrop = backdrop,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        blurRadius = if (selected) 28.dp else 18.dp,
        tonalOpacity = if (selected) 0.70f else 0.46f,
        materialOpacity = if (selected) 0.56f else 0.44f,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = setting.shortLabel(),
                color = if (selected) Color(0xFFB9ECFF) else Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = setting.currentValue.displayLabel(),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProParameterWheel(
    setting: CameraSettingDescriptor,
    backdrop: PreviewBackdrop,
    onValueCommitted: (SettingValue) -> Unit,
) {
    val values = setting.availableValues
    val selectedIndex = values.indexOfFirst { value -> setting.currentValue.sameValueAs(value) }
        .takeIf { it >= 0 }
        ?: 0
    val choicesKey = values.joinToString("|") { it.debugValue }
    val density = LocalDensity.current
    val tickSpacingPx = with(density) { 34.dp.toPx() }
    val tickStroke = with(density) { 1.25.dp.toPx() }
    val selectedStroke = with(density) { 2.dp.toPx() }
    val smallTick = with(density) { 12.dp.toPx() }
    val mediumTick = with(density) { 22.dp.toPx() }
    val selectedTick = with(density) { 36.dp.toPx() }
    val centerTop = with(density) { 16.dp.toPx() }
    val textBaseline = with(density) { 88.dp.toPx() }
    val labelTextSize = with(density) { 11.sp.toPx() }
    val selectedTextSize = with(density) { 13.sp.toPx() }
    val maxOffset = ((values.size - 1).coerceAtLeast(0) * tickSpacingPx)
    var wheelOffset by remember(setting.id, choicesKey) { mutableStateOf(selectedIndex * tickSpacingPx) }
    var dragging by remember(setting.id, choicesKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val liveIndex = if (values.isEmpty()) {
        0
    } else {
        (wheelOffset / tickSpacingPx).roundToInt().coerceIn(0, values.lastIndex)
    }
    val liveValue = values.getOrNull(liveIndex) ?: setting.currentValue
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }
    val selectedTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    LaunchedEffect(setting.id, choicesKey, selectedIndex, tickSpacingPx) {
        if (!dragging) {
            wheelOffset = selectedIndex * tickSpacingPx
        }
    }

    LiquidGlassSurface(
        backdrop = backdrop,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        blurRadius = 30.dp,
        tonalOpacity = 0.64f,
        materialOpacity = 0.62f,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = setting.shortLabel(),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = liveValue.displayLabel(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = setting.displayName,
                    color = Color.White.copy(alpha = 0.52f),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                dragging = true
                                wheelOffset = (wheelOffset - delta).coerceIn(0f, maxOffset)
                            },
                            onDragStopped = { velocity ->
                                dragging = false
                                val projectedOffset = (wheelOffset - velocity * 0.10f).coerceIn(0f, maxOffset)
                                val targetIndex = if (values.isEmpty()) {
                                    0
                                } else {
                                    (projectedOffset / tickSpacingPx).roundToInt().coerceIn(0, values.lastIndex)
                                }
                                val targetOffset = targetIndex * tickSpacingPx
                                scope.launch {
                                    val anim = Animatable(wheelOffset)
                                    anim.animateTo(
                                        targetValue = targetOffset,
                                        animationSpec = spring(
                                            dampingRatio = 0.78f,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    ) {
                                        wheelOffset = value.coerceIn(0f, maxOffset)
                                    }
                                    values.getOrNull(targetIndex)?.let { targetValue ->
                                        if (!setting.currentValue.sameValueAs(targetValue)) {
                                            onValueCommitted(targetValue)
                                        }
                                    }
                                }
                            },
                        ),
                ) {
                    val centerX = size.width / 2f
                    val railY = centerTop + selectedTick / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.16f),
                        start = Offset(0f, railY),
                        end = Offset(size.width, railY),
                        strokeWidth = 1.dp.toPx(),
                    )
                    values.forEachIndexed { index, value ->
                        val x = centerX + index * tickSpacingPx - wheelOffset
                        if (x > -tickSpacingPx && x < size.width + tickSpacingPx) {
                            val distance = abs(index - liveIndex).coerceAtMost(6)
                            val alpha = (1f - distance * 0.13f).coerceIn(0.18f, 1f)
                            val selected = index == liveIndex
                            val major = selected || index == 0 || values.size <= 14 || index % 2 == 0
                            val tickHeight = when {
                                selected -> selectedTick
                                major -> mediumTick
                                else -> smallTick
                            }
                            val color = if (selected) Color(0xFFB9ECFF) else Color.White.copy(alpha = 0.52f * alpha)
                            drawLine(
                                color = color,
                                start = Offset(x, railY - tickHeight / 2f),
                                end = Offset(x, railY + tickHeight / 2f),
                                strokeWidth = if (selected) selectedStroke else tickStroke,
                            )
                            val shouldDrawLabel = selected || value.isAuto() || values.size <= 12 || index % 2 == 0
                            if (shouldDrawLabel) {
                                val paint = if (selected) selectedTextPaint else textPaint
                                paint.textSize = if (selected) selectedTextSize else labelTextSize
                                paint.color = if (selected) {
                                    android.graphics.Color.rgb(185, 236, 255)
                                } else {
                                    android.graphics.Color.argb((190 * alpha).toInt(), 255, 255, 255)
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    value.displayLabel(maxLength = 8),
                                    x,
                                    textBaseline,
                                    paint,
                                )
                            }
                        }
                    }
                    drawLine(
                        color = Color(0xFFB9ECFF).copy(alpha = 0.94f),
                        start = Offset(centerX, 4.dp.toPx()),
                        end = Offset(centerX, 72.dp.toPx()),
                        strokeWidth = 1.6.dp.toPx(),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = 3.5.dp.toPx(),
                        center = Offset(centerX, railY),
                    )
                }
            }
        }
    }
}

private val ProSettingOrder = listOf("iso", "shutter", "ev", "wb")

private const val CameraPreviewBufferWidth = 1280
private const val CameraPreviewBufferHeight = 720

private fun CameraCapabilities.supportedCaptureFormats(): List<CaptureFormat> {
    return buildList {
        if (isSupported(CameraCapability.CaptureJpeg)) add(CaptureFormat.Jpeg)
        if (isSupported(CameraCapability.CaptureHeic)) add(CaptureFormat.Heic)
        if (isSupported(CameraCapability.CaptureRaw)) add(CaptureFormat.Raw)
        if (isSupported(CameraCapability.CaptureRawJpeg) && isSupported(CameraCapability.CaptureJpeg)) {
            add(CaptureFormat.RawAndJpeg)
        }
    }
}

private fun CaptureFormat.displayLabel(): String {
    return when (this) {
        CaptureFormat.Jpeg -> "JPEG"
        CaptureFormat.Heic -> "HEIC"
        CaptureFormat.Raw -> "RAW"
        CaptureFormat.RawAndJpeg -> "J+RAW"
        CaptureFormat.PreviewJpeg -> "PREV"
    }
}

private fun CameraSettingDescriptor.shortLabel(): String {
    return when (id) {
        "iso" -> "ISO"
        "shutter" -> "S"
        "ev" -> "EV"
        "wb" -> "WB"
        else -> displayName
    }
}

private fun SettingValue?.displayLabel(maxLength: Int = Int.MAX_VALUE): String {
    val value = this
    val raw = value?.label?.ifBlank { value.debugValue }.orEmpty().ifBlank { "--" }
    val label = if (raw.equals("auto", ignoreCase = true)) "AUTO" else raw
    return if (label.length <= maxLength) {
        label
    } else {
        "${label.take((maxLength - 2).coerceAtLeast(1))}..."
    }
}

private fun SettingValue?.sameValueAs(other: SettingValue): Boolean {
    return this?.debugValue == other.debugValue || this?.label == other.label
}

private fun SettingValue.isAuto(): Boolean {
    return debugValue.equals("auto", ignoreCase = true) || label.equals("auto", ignoreCase = true)
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
        materialOpacity = 0.48f,
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
                        texture.setDefaultBufferSize(
                            CameraPreviewBufferWidth,
                            CameraPreviewBufferHeight,
                        )
                        surface = Surface(texture)
                        scope.launch {
                            runCatching {
                                session.preview?.bind(
                                    PreviewSurface(
                                        nativeSurface = surface ?: return@runCatching,
                                        width = CameraPreviewBufferWidth,
                                        height = CameraPreviewBufferHeight,
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
                        texture.setDefaultBufferSize(
                            CameraPreviewBufferWidth,
                            CameraPreviewBufferHeight,
                        )
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
