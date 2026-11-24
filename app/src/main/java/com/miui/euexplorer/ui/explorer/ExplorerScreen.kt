package com.miui.euexplorer.ui.explorer

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.miui.euexplorer.R
import com.miui.euexplorer.data.CatalogCoverageStats
import com.miui.euexplorer.domain.WallpaperItem
import com.miui.euexplorer.ui.theme.EuExplorerTheme
import com.miui.euexplorer.ui.theme.LocalExtendedColors
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun ExplorerRouteScreen(
    viewModel: ExplorerViewModel,
    onWallpaperClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExplorerContent(
        uiState = uiState,
        onWallpaperClick = onWallpaperClick,
        onLoadMore = viewModel::loadMore,
        onReshuffle = viewModel::reshuffle,
        onRetryInitial = viewModel::retryInitial,
        modifier = modifier
    )
}

@Composable
fun ExplorerContent(
    uiState: ExplorerUiState,
    onWallpaperClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onReshuffle: () -> Unit,
    onRetryInitial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()

    val triggerReshuffle: () -> Unit = {
        scope.launch { gridState.scrollToItem(0) }
        onReshuffle()
    }

    val shouldLoadMore by remember(gridState) {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 6
        }
    }

    LaunchedEffect(shouldLoadMore, uiState) {
        if (shouldLoadMore && uiState is ExplorerUiState.Content && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    val isRefreshing = when (uiState) {
        is ExplorerUiState.Content -> uiState.isRefreshing
        is ExplorerUiState.Empty -> uiState.isRefreshing
        else -> false
    }

    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            extendedColors.surfaceGradientEnd
        )
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = triggerReshuffle,
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ExplorerHeader(
                uiState = uiState,
                onReshuffle = triggerReshuffle
            )

            when (uiState) {
                is ExplorerUiState.Loading -> {
                    ExplorerLoadingGrid()
                }

                is ExplorerUiState.Error -> {
                    ExplorerErrorState(
                        message = uiState.message,
                        onRetry = onRetryInitial
                    )
                }

                is ExplorerUiState.Empty -> {
                    ExplorerEmptyState(onRetry = onReshuffle)
                }

                is ExplorerUiState.Content -> {
                    ExplorerGrid(
                        uiState = uiState,
                        gridState = gridState,
                        onWallpaperClick = onWallpaperClick,
                        onRetryLoadMore = onLoadMore
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplorerHeader(
    uiState: ExplorerUiState,
    onReshuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.explorer_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = when (uiState) {
                is ExplorerUiState.Content ->
                    "${uiState.wallpapers.size} wallpapers · ${uiState.coverage.activeTagPoolSize} tags · depth ${uiState.coverage.deepestPageSampled}"
                else -> "Xiaomi EU Catalog"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FilledTonalIconButton(
            onClick = onReshuffle,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle),
                contentDescription = stringResource(R.string.action_reshuffle)
            )
        }
    }
}

@Composable
private fun ExplorerGrid(
    uiState: ExplorerUiState.Content,
    gridState: LazyStaggeredGridState,
    onWallpaperClick: (Int) -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navInsets = WindowInsets.navigationBars.asPaddingValues()

    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 4.dp,
            bottom = navInsets.calculateBottomPadding() + 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = uiState.wallpapers,
            key = { _, item -> item.id },
            contentType = { _, _ -> "wallpaper_tile" }
        ) { index, wallpaper ->
            WallpaperGridTile(
                wallpaper = wallpaper,
                onClick = { onWallpaperClick(index) }
            )
        }

        if (uiState.isLoadingMore) {
            item(span = StaggeredGridItemSpan.FullLine, key = "footer_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
        } else if (uiState.appendErrorMessage != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "footer_retry") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(
                        onClick = onRetryLoadMore,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperGridTile(
    wallpaper: WallpaperItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(wallpaper.aspectRatioHint)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = placeholderColor,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(wallpaper.thumbnailUrl)
                .crossfade(250)
                .build(),
            contentDescription = wallpaper.title,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(placeholderColor),
            error = ColorPainter(placeholderColor),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ExplorerLoadingGrid(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerLow
    )
    val transition = rememberInfiniteTransition(label = "explorer_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "explorer_shimmer_offset"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(translate, translate)
    )

    val ratios = remember { listOf(0.52f, 0.62f, 0.5625f, 0.52f, 0.63f, 0.5625f) }
    val navInsets = WindowInsets.navigationBars.asPaddingValues()

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 4.dp,
            bottom = navInsets.calculateBottomPadding() + 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(ratios.size) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratios[index])
                    .clip(MaterialTheme.shapes.medium)
                    .background(brush)
            )
        }
    }
}

@Composable
private fun ExplorerErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_cloud_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Could not load EU catalog",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun ExplorerEmptyState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_photo_library),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No wallpapers returned",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.height(48.dp)
        ) {
            Text(text = stringResource(R.string.action_reshuffle))
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ExplorerContentPreview() {
    val sample = persistentListOf(
        WallpaperItem(
            id = "1",
            hash = "hash_1",
            title = "Alpine Peak",
            thumbnailUrl = "",
            fullscreenUrl = "",
            masterDownloadUrl = "",
            fallbackDownloadUrl = "",
            aspectRatioHint = 0.5625f
        ),
        WallpaperItem(
            id = "2",
            hash = "hash_2",
            title = "Obsidian Waves",
            thumbnailUrl = "",
            fullscreenUrl = "",
            masterDownloadUrl = "",
            fallbackDownloadUrl = "",
            aspectRatioHint = 0.63f
        )
    )
    EuExplorerTheme {
        ExplorerContent(
            uiState = ExplorerUiState.Content(
                wallpapers = sample,
                isLoadingMore = false,
                isRefreshing = false,
                appendErrorMessage = null,
                coverage = CatalogCoverageStats(
                    loadedCount = 36,
                    activeTagPoolSize = 68,
                    discoveredTagCount = 14,
                    exploredPagesCount = 6,
                    deepestPageSampled = 742
                )
            ),
            onWallpaperClick = {},
            onLoadMore = {},
            onReshuffle = {},
            onRetryInitial = {}
        )
    }
}
