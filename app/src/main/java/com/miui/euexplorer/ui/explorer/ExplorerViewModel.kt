package com.miui.euexplorer.ui.explorer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miui.euexplorer.data.CatalogCoverageStats
import com.miui.euexplorer.data.WallpaperRepository
import com.miui.euexplorer.domain.WallpaperItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
sealed interface ExplorerUiState {
    data object Loading : ExplorerUiState

    @Immutable
    data class Content(
        val wallpapers: ImmutableList<WallpaperItem>,
        val isLoadingMore: Boolean,
        val isRefreshing: Boolean,
        val appendErrorMessage: String?,
        val coverage: CatalogCoverageStats
    ) : ExplorerUiState

    @Immutable
    data class Empty(val isRefreshing: Boolean) : ExplorerUiState

    @Immutable
    data class Error(val message: String) : ExplorerUiState
}

class ExplorerViewModel(
    private val repository: WallpaperRepository
) : ViewModel() {

    val uiState: StateFlow<ExplorerUiState> = repository.sessionState
        .map { session ->
            when {
                session.isInitialLoading && session.wallpapers.isEmpty() -> ExplorerUiState.Loading
                session.errorMessage != null && session.wallpapers.isEmpty() ->
                    ExplorerUiState.Error(session.errorMessage)
                session.wallpapers.isEmpty() -> ExplorerUiState.Empty(session.isRefreshing)
                else -> ExplorerUiState.Content(
                    wallpapers = session.wallpapers,
                    isLoadingMore = session.isLoadingMore,
                    isRefreshing = session.isRefreshing,
                    appendErrorMessage = session.appendErrorMessage,
                    coverage = session.coverage
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExplorerUiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureInitialLoaded()
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            repository.loadMore()
        }
    }

    fun reshuffle() {
        viewModelScope.launch {
            repository.reshuffle()
        }
    }

    fun retryInitial() {
        viewModelScope.launch {
            repository.loadInitial()
        }
    }

    class Factory(
        private val repository: WallpaperRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExplorerViewModel(repository) as T
        }
    }
}
