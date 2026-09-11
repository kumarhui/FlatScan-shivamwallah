/*
 * Copyright 2025-2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app.ui.screens.camera

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.fairscan.app.MainViewModel
import org.fairscan.app.R
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation.R0
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.components.CameraPermissionState
import org.fairscan.app.ui.components.CommonPageListState
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.MyScaffold
import org.fairscan.app.ui.components.pageCountText
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.fakeDocument
import org.fairscan.app.ui.screens.idcard.IdCardPreviewDialog
import org.fairscan.app.ui.theme.FairScanTheme
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad

const val CAPTURED_IMAGE_DISPLAY_DURATION = 1500L
const val ANIMATION_DURATION = 200

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    cameraViewModel: CameraViewModel,
    navigation: Navigation,
    liveAnalysisState: LiveAnalysisState,
    importState: ImportState,
    onImageAnalyzed: (ImageProxy) -> Unit,
    onFinalizePressed: () -> Unit,
    cameraPermission: CameraPermissionState,
    onImportClicked: () -> Unit,
    isCameraEnabled: Boolean = true,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val document by viewModel.documentUiModel.collectAsStateWithLifecycle()
    val thumbnailCoords = remember { mutableStateOf(Offset.Zero) }
    var isDebugMode by remember { mutableStateOf(false) }
    val isTorchEnabled by cameraViewModel.isTorchEnabled.collectAsStateWithLifecycle()
    var torchReapplied by remember { mutableStateOf(false) }

    // Camera is OFF by default
    var isCameraActive by remember { mutableStateOf(false) }

    // ID Card Preview Dialog state
    var showIdCardDialog by remember { mutableStateOf(false) }
    var idCardBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    val captureController = remember { CameraCaptureController() }
    DisposableEffect(Unit) {
        onDispose {
            captureController.shutdown()
            torchReapplied = false
        }
    }
    LaunchedEffect(captureController.cameraControl, isTorchEnabled) {
        if (isCameraActive) {
            captureController.cameraControl?.enableTorch(isTorchEnabled)
        }
    }

    val captureState by cameraViewModel.captureState.collectAsStateWithLifecycle()
    if (captureState is CaptureState.CapturePreview) {
        LaunchedEffect(captureState) {
            // Automatically turn off camera stream after capturing one picture
            isCameraActive = false
            delay(CAPTURED_IMAGE_DISPLAY_DURATION)
            cameraViewModel.addProcessedImage()
        }
    }

    var showDetectionError by remember { mutableStateOf(false) }
    LaunchedEffect(captureState) {
        if (captureState is CaptureState.CaptureError) {
            showDetectionError = true
            delay(1000)
            showDetectionError = false
            cameraViewModel.afterCaptureError()
        }
    }

    LaunchedEffect(Unit) {
        cameraViewModel.resetLiveAnalysis()
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraViewModel.cancelImport()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(document.pageCount()) {
        if (!document.isEmpty()) {
            listState.animateScrollToItem(document.lastIndex())
        }
    }

    val onCapture = {
        if (isCameraActive) {
            previewView?.bitmap?.let {
                Log.i("FairScan", "Pressed <Capture>")
                cameraViewModel.onCapturePressed(it)
                captureController.takePicture(
                    onImageCaptured = { imageProxy, opticalMeasures ->
                        cameraViewModel.onImageCaptured(imageProxy, opticalMeasures)
                    }
                )
            } ?: Unit
        } else Unit
    }
    LaunchedEffect(Unit) {
        cameraViewModel.volumeKeyEvent.collect {
            if (isCameraActive) onCapture()
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    CameraScreenScaffold(
        cameraPreview = {
            if (isCameraActive) {
                CameraPreview(
                    onImageAnalyzed = {
                        onImageAnalyzed(it)
                        if (!torchReapplied) {
                            captureController.cameraControl?.enableTorch(isTorchEnabled)
                            torchReapplied = true
                        }
                    },
                    captureController = captureController,
                    onPreviewViewReady = { view ->
                        previewView = view
                        captureController.previewView = view
                    },
                    cameraPermission = cameraPermission,
                    onError = { message, throwable -> cameraViewModel.logError(message, throwable) }
                )
            }
        },
        pageListState =
            CommonPageListState(
                document = document,
                onPageClick = { index -> viewModel.navigateTo(Screen.Main.Document(index)) },
                onPageReorder = { id, index -> viewModel.movePage(id, index) },
                listState = listState,
                showPageNumbers = false,
                onLastItemPosition = { offset -> thumbnailCoords.value = offset },
            ),
        cameraUiState = CameraUiState(
            document.pageCount(),
            liveAnalysisState,
            captureState,
            importState,
            showDetectionError,
            isLandscape = isLandscape,
            isDebugMode,
            isTorchEnabled),
        onCapture = onCapture,
        onFinalizePressed = onFinalizePressed,
        onDebugModeSwitched = { isDebugMode = !isDebugMode },
        onTorchSwitched = {
            cameraViewModel.setTorchEnabled(!isTorchEnabled)
        },
        thumbnailCoords = thumbnailCoords,
        navigation = navigation,
        captureController = captureController,
        isCameraPermissionGranted = cameraPermission.isGranted,
        onRequestCameraPermission = { cameraPermission.request() },
        onImportClicked = onImportClicked,
        isCameraActive = isCameraActive,
        onToggleCameraActive = { isCameraActive = !isCameraActive },
        onIdCardClick = {
            idCardBitmaps = document.pages.mapNotNull { it.thumbnail?.toBitmap() }
            showIdCardDialog = true
        }
    )

    if (showIdCardDialog && idCardBitmaps.isNotEmpty()) {
        IdCardPreviewDialog(
            bitmaps = idCardBitmaps,
            onDismiss = { showIdCardDialog = false }
        )
    }
}

@Composable
private fun CameraScreenScaffold(
    cameraPreview: @Composable () -> Unit,
    pageListState: CommonPageListState,
    cameraUiState: CameraUiState,
    onCapture: () -> Unit,
    onFinalizePressed: () -> Unit,
    onDebugModeSwitched: () -> Unit,
    onTorchSwitched: () -> Unit,
    thumbnailCoords: MutableState<Offset>,
    navigation: Navigation,
    captureController: CameraCaptureController,
    isCameraPermissionGranted: Boolean,
    onRequestCameraPermission: () -> Unit,
    onImportClicked: () -> Unit,
    isCameraActive: Boolean,
    onToggleCameraActive: () -> Unit,
    onIdCardClick: () -> Unit,
) {
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(1000)
            focusPoint = null
        }
    }

    var tapCount by remember { mutableLongStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val tapThreshold = 500L
    val onPageCountClick = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime < tapThreshold) {
            tapCount++
            if (tapCount >= 3) {
                onDebugModeSwitched()
                tapCount = 0
            }
        } else {
            tapCount = 1
        }
        lastTapTime = currentTime
    }

    Box {
        MyScaffold(
            navigation = navigation,
            pageListState = pageListState,
            bottomBar = {
                Bar(
                    pageCount = cameraUiState.pageCount,
                    onFinalizePressed = onFinalizePressed,
                    onImportClicked = onImportClicked,
                    onIdCardClick = onIdCardClick
                )
            }
        ) { modifier ->
            when {
                cameraUiState.importState is ImportState.Selecting -> {
                    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
                cameraUiState.importState is ImportState.Importing -> {
                    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ImportInProgress(cameraUiState.importState, Modifier.fillMaxSize())
                    }
                }
                !isCameraActive -> {
                    CameraOffPlaceholder(
                        pageCount = cameraUiState.pageCount,
                        onTurnOnCamera = onToggleCameraActive,
                        modifier = modifier
                    )
                }
                !isCameraPermissionGranted -> {
                    CameraPermissionRationale(onRequestCameraPermission, modifier)
                }
                else -> {
                    CameraPreviewBox(
                        cameraPreview = cameraPreview,
                        cameraUiState = cameraUiState,
                        focusPoint = focusPoint,
                        onCapture = onCapture,
                        onTorchSwitched = onTorchSwitched,
                        onTurnOffCamera = onToggleCameraActive,
                        modifier = modifier.pointerInput(Unit) {
                            detectTapGestures { offset ->
                                focusPoint = offset
                                captureController.tapToFocus(offset)
                                onPageCountClick()
                            }
                        }
                    )
                }
            }
        }
        // Progress dialog shown while image is being processed after capture
        if (cameraUiState.captureState is CaptureState.Capturing) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { /* Prevent accidental cancel during image processing */ },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Processing image...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (cameraUiState.captureState is CaptureState.CapturePreview) {
            val page = cameraUiState.captureState.capturedPage.pageJpeg.toBitmap()
            CapturedImage(page.asImageBitmap(), thumbnailCoords)
        }
    }
}

@Composable
fun CameraOffPlaceholder(
    pageCount: Int,
    onTurnOnCamera: () -> Unit,
    modifier: Modifier,
    selectedImageBitmap: Bitmap? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                )
                .background(if (selectedImageBitmap != null) Color.Black else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageBitmap != null) {
                Image(
                    bitmap = selectedImageBitmap.asImageBitmap(),
                    contentDescription = "Selected document preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = if (pageCount > 0) Icons.Default.PhotoLibrary else Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = if (pageCount > 0) {
                            LocalResources.current.getQuantityString(
                                R.plurals.importing_photos,
                                pageCount,
                                pageCount
                            ).replace("Importing", "Imported")
                        } else {
                            "Camera is Off"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = if (pageCount > 0) {
                            "Select a thumbnail above to view or turn on camera"
                        } else {
                            "Turn on camera to scan physical documents"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Floating Camera Toggle Button aligned at TopEnd matching Viewfinder
        FilledIconButton(
            onClick = onTurnOnCamera,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Videocam, contentDescription = "Turn on camera")
        }
    }
}

@Composable
fun ImportInProgress(state: ImportState.Importing, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = LocalResources.current.getQuantityString(
                    R.plurals.importing_photos,
                    state.total,
                    state.total
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            if (state.total > 1) {
                LinearProgressIndicator(
                    progress = { state.processed.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewBox(
    cameraPreview: @Composable (() -> Unit),
    cameraUiState: CameraUiState,
    focusPoint: Offset?,
    onCapture: () -> Unit,
    onTorchSwitched: () -> Unit,
    onTurnOffCamera: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 16.dp)
    ) {
        // Rounded Viewfinder Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            CameraPreviewWithOverlay(
                cameraPreview,
                cameraUiState,
                Modifier.fillMaxSize()
            )
            if (cameraUiState.isDebugMode) {
                MessageBox(cameraUiState.liveAnalysisState.inferenceTime)
            }
            FocusOverlay(focusPoint)
        }

        // Floating Close Camera button
        FilledIconButton(
            onClick = onTurnOffCamera,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.VideocamOff, contentDescription = "Turn off camera")
        }

        // Shutter Button anchored at bottom center
        CaptureButton(
            onClick = onCapture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        )

        // Flash/Torch button
        IconButton(
            onClick = onTorchSwitched,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            val torchEnabled = cameraUiState.isTorchEnabled
            Icon(
                imageVector = Icons.Default.Highlight,
                contentDescription =
                    stringResource(
                        if (torchEnabled) R.string.turn_off_torch else R.string.turn_on_torch),
                tint = if (torchEnabled) Color.White else Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun CapturedImage(image: ImageBitmap, thumbnailCoords: MutableState<Offset>) {
    Surface(
        color = Color.Black.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxSize(),
    ) {}

    var isAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(image) {
        delay(CAPTURED_IMAGE_DISPLAY_DURATION - ANIMATION_DURATION)
        isAnimating = true
    }
    var targetOffsetX by remember { mutableFloatStateOf(0f) }
    var targetOffsetY by remember { mutableFloatStateOf(0f) }

    val transition = updateTransition(targetState = isAnimating, label = "captureAnimation")
    val tween = tween<Float>(durationMillis = ANIMATION_DURATION)
    val offsetX by transition.animateFloat({ tween }, "offsetX") { if (it) targetOffsetX else 0f }
    val offsetY by transition.animateFloat({ tween }, "offsetY") { if (it) targetOffsetY else 0f }
    val scale by transition.animateFloat({ tween }, "scale") { if (it) 0.3f else 1f }

    val density = LocalDensity.current
    Box (contentAlignment = Alignment.BottomStart,
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val centerX = bounds.left + bounds.width / 2
                val centerY = bounds.top + bounds.height / 2
                with(density) {
                    targetOffsetX = thumbnailCoords.value.x - centerX
                    targetOffsetY = thumbnailCoords.value.y - centerY
                }
            }
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

@Composable
fun CaptureButton(onClick: () -> Unit, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(80.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(
                    width = 4.dp,
                    color = color,
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(color = color, shape = CircleShape)
        )
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    cameraPreview: @Composable () -> Unit,
    cameraUiState: CameraUiState,
    modifier: Modifier,
) {
    val captureState = cameraUiState.captureState

    var showShutter by remember { mutableStateOf(false) }
    LaunchedEffect(captureState.frozenImage) {
        if (captureState.frozenImage != null) {
            showShutter = true
            delay(200)
            showShutter = false
        }
    }

    Box(
        modifier = modifier
    ) {
        cameraPreview()
        AnalysisOverlay(cameraUiState.liveAnalysisState, cameraUiState.isDebugMode)
        captureState.frozenImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
            )

        }
        if (showShutter) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }
        if (cameraUiState.showCaptureError) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.error_occurred),
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun FocusOverlay(focusPoint: Offset?) {
    if (focusPoint == null) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val size = 80f
        drawRect(
            color = Color.White,
            topLeft = Offset(
                focusPoint.x - size / 2,
                focusPoint.y - size / 2
            ),
            size = Size(size, size),
            style = Stroke(width = 3f)
        )
    }
}

@Composable
fun MessageBox(inferenceTime: Long) {
    Text(
        text = if(inferenceTime == 0L) "" else "Segmentation time: $inferenceTime ms",
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        color = Color.Gray,
    )
}

@Composable
private fun Bar(
    pageCount: Int,
    onFinalizePressed: () -> Unit,
    onImportClicked: () -> Unit,
    onIdCardClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = onImportClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.import_photos),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pageCount > 0) {
                FilledIconButton(
                    onClick = onIdCardClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = "ID Card on A4",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            MainActionButton(
                onClick = onFinalizePressed,
                enabled = pageCount > 0,
                text = pageCountText(pageCount),
                icon = Icons.Default.Done,
            )
        }
    }
}

@Composable
private fun CameraPermissionRationale(onRequestCameraPermission: () -> Unit, modifier:Modifier) {
    Box (modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.camera_permission_rationale),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRequestCameraPermission,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
        }
    }
}

@Preview(name="Pixel 1", showSystemUi = true, device = Devices.PIXEL)
@Preview(name="Pixel 4", showSystemUi = true, device = Devices.PIXEL_4)
@Preview(name="Pixel 9 pro XL", showSystemUi = true, device = Devices.PIXEL_9_PRO_XL)
@Composable
fun CameraScreenPreview() {
    ScreenPreview(CaptureState.Idle)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CameraScreenPreviewWithProcessedImage() {
    val p = Point(0 , 0)
    val quad = Quad(p, p, p, p)
    ScreenPreview(CaptureState.CapturePreview(
        debugImage("uncropped/img01.jpg").toBitmap(),
        CapturedPage(
            debugImage("gallica.bnf.fr-bpt6k5530456s-1.jpg"),
            CompletableDeferred(Jpeg(ByteArray(0))),
            PageMetadata(quad, R0, ColorMode.COLOR, null, null),
            ColorMode.COLOR)))
}

@Preview(showBackground = true, widthDp = 640, heightDp = 320)
@Composable
fun CameraScreenPreviewInLandscapeMode() {
    ScreenPreview(CaptureState.Idle, rotationDegrees = 90f)
}

@Preview
@Composable
fun CameraScreenPreviewWithNoPermission() {
    ScreenPreview(CaptureState.Idle, isCameraPermissionGranted = false)
}

@Preview
@Composable
fun CameraScreenPreviewImporting() {
    ScreenPreview(CaptureState.Idle, ImportState.Importing(2, 3))
}

@Composable
private fun ScreenPreview(
    captureState: CaptureState,
    importState: ImportState = ImportState.Idle,
    rotationDegrees: Float = 0f,
    isCameraPermissionGranted: Boolean = true,
) {
    FairScanTheme {
        val thumbnailCoords = remember { mutableStateOf(Offset.Zero) }
        CameraScreenScaffold(
            cameraPreview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Image(
                        debugImage("uncropped/img01.jpg").toBitmap().asImageBitmap(),
                        modifier=Modifier.rotate(rotationDegrees),
                        contentDescription = null
                    )
                }
            },
            pageListState =
                CommonPageListState(
                    document = fakeDocument(
                        listOf(1, 2)
                            .map { "gallica.bnf.fr-bpt6k5530456s-$it" }
                            .toImmutableList(),
                        LocalContext.current),
                    onPageClick = {},
                    onPageReorder = { _, _ -> },
                    listState = LazyListState(),
                    showPageNumbers = false,
                ),
            cameraUiState = CameraUiState(pageCount = 4, LiveAnalysisState(), captureState,
                importState, false, rotationDegrees > 0, false, false),
            onCapture = {},
            onFinalizePressed = {},
            onDebugModeSwitched = {},
            onTorchSwitched = {},
            thumbnailCoords = thumbnailCoords,
            navigation = dummyNavigation(),
            captureController = CameraCaptureController(),
            isCameraPermissionGranted = isCameraPermissionGranted,
            onRequestCameraPermission = {},
            onImportClicked = {},
            isCameraActive = false,
            onToggleCameraActive = {},
            onIdCardClick = {}
        )
    }
}

@Composable
private fun debugImage(imgName: String): Jpeg {
    val context = LocalContext.current
    return Jpeg(context.assets.open(imgName).readBytes())
}



