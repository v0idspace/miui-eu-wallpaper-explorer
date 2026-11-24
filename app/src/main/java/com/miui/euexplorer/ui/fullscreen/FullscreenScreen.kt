package com.miui.euexplorer.ui.fullscreen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.miui.euexplorer.R
import com.miui.euexplorer.data.DownloadState
import com.miui.euexplorer.domain.WallpaperItem
import com.miui.euexplorer.ui.theme.EuExplorerTheme
import com.miui.euexplorer.ui.theme.LocalExtendedColors
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

@Composable
fun FullscreenRouteScreen(
    initialIndex: Int,
    viewModel: FullscreenViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDownloadItem by remember { mutableStateOf<WallpaperItem?>(null) }

    val legacyWritePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val item = pendingDownloadItem
        pendingDownloadItem = null
        if (granted && item != null) {
            viewModel.downloadWallpaper(item)
        }
    }

    FullscreenContent(
        initialIndex = initialIndex,
        uiState = uiState,
        onBack = onBack,
        onToggleChrome = viewModel::toggleChrome,
        onPageChanged = viewModel::onPageChanged,
        onDownloadClick = { item ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    pendingDownloadItem = item
                    legacyWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    return@FullscreenContent
                }
            }
            viewModel.downloadWallpaper(item)
        },
        modifier = modifier
    )
}

@Composable
fun FullscreenContent(
    initialIndex: Int,
    uiState: FullscreenUiState,
    onBack: () -> Unit,
    onToggleChrome: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onDownloadClick: (WallpaperItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current
    val context = LocalContext.current
    val view = LocalView.current
    val isSystemDark = isSystemInDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }
    var backProgress by remember { mutableFloatStateOf(0f) }

    if (!view.isInEditMode) {
        DisposableEffect(view, isSystemDark) {
            val window = context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val previousLightStatus = controller?.isAppearanceLightStatusBars
            val previousLightNav = controller?.isAppearanceLightNavigationBars
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {
                if (controller != null) {
                    controller.isAppearanceLightStatusBars = previousLightStatus ?: !isSystemDark
                    controller.isAppearanceLightNavigationBars = previousLightNav ?: !isSystemDark
                }
            }
        }
    }

    PredictiveBackHandler { progressFlow ->
        try {
            progressFlow.collect { event ->
                backProgress = event.progress
            }
            onBack()
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    LaunchedEffect(uiState.downloadState) {
        val state = uiState.downloadState
        if (state is DownloadState.Failed) {
            snackbarHostState.showSnackbar("Download failed: ${state.message}")
        }
    }

    val wallpapers = uiState.wallpapers
    if (wallpapers.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(extendedColors.fullscreenCanvas),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, wallpapers.lastIndex)
    ) {
        wallpapers.size
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onPageChanged(page)
        }
    }

    LaunchedEffect(pagerState.currentPage, wallpapers) {
        val idx = pagerState.currentPage
        listOfNotNull(
            wallpapers.getOrNull(idx + 1),
            wallpapers.getOrNull(idx - 1),
            wallpapers.getOrNull(idx + 2)
        ).forEach { wp ->
            val request = ImageRequest.Builder(context)
                .data(wp.fullscreenUrl)
                .placeholderMemoryCacheKey(wp.thumbnailUrl)
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    val currentWallpaper = wallpapers.getOrNull(pagerState.currentPage)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = 1f - (backProgress * 0.10f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (backProgress * 0.25f)
                clip = backProgress > 0f
                shape = RoundedCornerShape((backProgress * 28f).dp)
            }
            .background(extendedColors.fullscreenCanvas)
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> wallpapers.getOrNull(page)?.id ?: page },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val wp = wallpapers.getOrNull(page)
            if (wp != null) {
                ZoomableWallpaperPage(
                    wallpaper = wp,
                    onSingleTap = onToggleChrome
                )
            }
        }

        // Minimal Top Floating Controls (Back + Counter)
        AnimatedVisibility(
            visible = uiState.isChromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = extendedColors.fullscreenOverlayScrim,
                        contentColor = extendedColors.fullscreenOverlayInk
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = extendedColors.fullscreenOverlayScrim,
                    border = BorderStroke(1.dp, extendedColors.fullscreenOverlayBorder)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${wallpapers.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = extendedColors.fullscreenOverlayInk,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Minimal Bottom Action (Download Full Quality Only)
        AnimatedVisibility(
            visible = uiState.isChromeVisible && currentWallpaper != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                currentWallpaper?.let { wallpaper ->
                    DownloadMasterPill(
                        downloadState = uiState.downloadState,
                        onClick = { onDownloadClick(wallpaper) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp)
        )
    }
}

@Composable
private fun ZoomableWallpaperPage(
    wallpaper: WallpaperItem,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember(wallpaper.id) { mutableFloatStateOf(1f) }
    var offset by remember(wallpaper.id) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(wallpaper.id) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val targetScale = 2.5f
                            val maxOffsetX = (size.width * (targetScale - 1f)) / 2f
                            val maxOffsetY = (size.height * (targetScale - 1f)) / 2f
                            scale = targetScale
                            offset = Offset(
                                x = ((size.width / 2f - tapOffset.x) * (targetScale - 1f))
                                    .coerceIn(-maxOffsetX, maxOffsetX),
                                y = ((size.height / 2f - tapOffset.y) * (targetScale - 1f))
                                    .coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        }
                    }
                )
            }
            .pointerInput(wallpaper.id) {
                // Only consume drags for panning when zoomed in or pinching with 2+ fingers;
                // unconsumed 1-finger horizontal drags pass cleanly to HorizontalPager.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pointerCount >= 2 || scale > 1.02f) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                            scale = newScale
                            if (newScale > 1.02f) {
                                val maxX = (size.width * (newScale - 1f)) / 2f
                                val maxY = (size.height * (newScale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            if (pointerCount >= 2 || abs(panChange.x) > 0f || abs(panChange.y) > 0f) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(wallpaper.fullscreenUrl)
                .placeholderMemoryCacheKey(wallpaper.thumbnailUrl)
                .crossfade(200)
                .build(),
            contentDescription = wallpaper.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun DownloadMasterPill(
    downloadState: DownloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current
    val isDownloading = downloadState is DownloadState.Downloading
    val isSaved = downloadState is DownloadState.Saved

    val containerColor = when {
        isSaved -> extendedColors.savedBadge
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        isSaved -> extendedColors.fullscreenCanvas
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Button(
        onClick = onClick,
        enabled = !isDownloading,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = extendedColors.fullscreenOverlayScrim,
            disabledContentColor = extendedColors.fullscreenOverlayInk
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.height(52.dp)
    ) {
        when (downloadState) {
            is DownloadState.Downloading -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (downloadState.percent >= 0) {
                        stringResource(R.string.action_downloading, downloadState.percent)
                    } else {
                        stringResource(R.string.action_downloading_indeterminate)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            is DownloadState.Saved -> {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_saved),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            is DownloadState.Failed -> {
                Icon(
                    painter = painterResource(R.drawable.ic_error_outline),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            DownloadState.Idle -> {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_download),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FullscreenContentPreview() {
    val sample = persistentListOf(
        WallpaperItem(
            id = "1",
            hash = "hash_1",
            title = "Mountain Ridge",
            thumbnailUrl = "",
            fullscreenUrl = "",
            masterDownloadUrl = "",
            fallbackDownloadUrl = "",
            aspectRatioHint = 0.5625f
        )
    )
    EuExplorerTheme {
        FullscreenContent(
            initialIndex = 0,
            uiState = FullscreenUiState(wallpapers = sample, isChromeVisible = true),
            onBack = {},
            onToggleChrome = {},
            onPageChanged = {},
            onDownloadClick = {}
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
