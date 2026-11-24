package com.miui.euexplorer.data

import androidx.compose.runtime.Immutable
import com.miui.euexplorer.domain.WallpaperItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class CatalogSessionState(
    val wallpapers: ImmutableList<WallpaperItem> = persistentListOf(),
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val appendErrorMessage: String? = null,
    val coverage: CatalogCoverageStats = CatalogCoverageStats()
)

/**
 * Single source of truth for the endless random session stream shared between
 * the Explorer grid and the Fullscreen pager.
 */
class WallpaperRepository(
    private val engine: EndlessRandomCatalogEngine
) {
    private val _sessionState = MutableStateFlow(CatalogSessionState())
    val sessionState: StateFlow<CatalogSessionState> = _sessionState.asStateFlow()

    private val fetchMutex = Mutex()

    suspend fun ensureInitialLoaded() {
        val current = _sessionState.value
        if (current.wallpapers.isNotEmpty() || !current.isInitialLoading) return
        loadInitial()
    }

    suspend fun loadInitial() {
        if (!fetchMutex.tryLock()) return
        try {
            _sessionState.update {
                it.copy(
                    isInitialLoading = it.wallpapers.isEmpty(),
                    errorMessage = null,
                    appendErrorMessage = null
                )
            }
            val result = engine.fetchNextBatch()
            val stats = engine.snapshotStats()
            result.fold(
                onSuccess = { batch ->
                    _sessionState.update { current ->
                        val merged = (current.wallpapers + batch).distinctBy { it.id }.toImmutableList()
                        current.copy(
                            wallpapers = merged,
                            isInitialLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                            coverage = stats
                        )
                    }
                },
                onFailure = { err ->
                    _sessionState.update { current ->
                        current.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            errorMessage = err.localizedMessage ?: "Unable to reach Xiaomi EU catalog"
                        )
                    }
                }
            )
        } finally {
            fetchMutex.unlock()
        }
    }

    suspend fun loadMore() {
        val snapshot = _sessionState.value
        if (snapshot.isInitialLoading || snapshot.isRefreshing || snapshot.isLoadingMore) return
        if (!fetchMutex.tryLock()) return
        try {
            _sessionState.update { it.copy(isLoadingMore = true, appendErrorMessage = null) }
            val result = engine.fetchNextBatch()
            val stats = engine.snapshotStats()
            result.fold(
                onSuccess = { batch ->
                    _sessionState.update { current ->
                        val merged = (current.wallpapers + batch).distinctBy { it.id }.toImmutableList()
                        current.copy(
                            wallpapers = merged,
                            isLoadingMore = false,
                            appendErrorMessage = null,
                            coverage = stats
                        )
                    }
                },
                onFailure = { err ->
                    _sessionState.update { current ->
                        current.copy(
                            isLoadingMore = false,
                            appendErrorMessage = err.localizedMessage ?: "Failed to load more wallpapers"
                        )
                    }
                }
            )
        } finally {
            fetchMutex.unlock()
        }
    }

    suspend fun reshuffle() {
        fetchMutex.withLock {
            _sessionState.update {
                it.copy(
                    isRefreshing = true,
                    errorMessage = null,
                    appendErrorMessage = null
                )
            }
            engine.resetSessionFeedKeepCalibration()
            val result = engine.fetchNextBatch()
            val stats = engine.snapshotStats()
            result.fold(
                onSuccess = { batch ->
                    _sessionState.update {
                        CatalogSessionState(
                            wallpapers = batch.toImmutableList(),
                            isInitialLoading = false,
                            isRefreshing = false,
                            coverage = stats
                        )
                    }
                },
                onFailure = { err ->
                    _sessionState.update { current ->
                        current.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            errorMessage = if (current.wallpapers.isEmpty()) {
                                err.localizedMessage ?: "Unable to reach Xiaomi EU catalog"
                            } else {
                                null
                            },
                            appendErrorMessage = if (current.wallpapers.isNotEmpty()) {
                                err.localizedMessage ?: "Refresh failed"
                            } else {
                                null
                            }
                        )
                    }
                }
            )
        }
    }
}
