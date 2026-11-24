package com.miui.euexplorer.ui.fullscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miui.euexplorer.data.DownloadState
import com.miui.euexplorer.data.FullQualityDownloader
import com.miui.euexplorer.data.WallpaperRepository
import com.miui.euexplorer.domain.WallpaperItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FullscreenUiState(
    val wallpapers: ImmutableList<WallpaperItem> = persistentListOf(),
    val isChromeVisible: Boolean = true,
    val downloadState: DownloadState = DownloadState.Idle
)

class FullscreenViewModel(
    private val repository: WallpaperRepository,
    private val downloader: FullQualityDownloader
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FullscreenUiState(wallpapers = repository.sessionState.value.wallpapers)
    )
    val uiState: StateFlow<FullscreenUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var resetSavedJob: Job? = null

    init {
        viewModelScope.launch {
            repository.sessionState.collect { session ->
                _uiState.update { it.copy(wallpapers = session.wallpapers) }
            }
        }
    }

    fun toggleChrome() {
        _uiState.update { it.copy(isChromeVisible = !it.isChromeVisible) }
    }

    fun onPageChanged(pageIndex: Int) {
        if (_uiState.value.downloadState is DownloadState.Saved ||
            _uiState.value.downloadState is DownloadState.Failed
        ) {
            _uiState.update { it.copy(downloadState = DownloadState.Idle) }
        }
        val total = _uiState.value.wallpapers.size
        if (total > 0 && pageIndex >= total - 4) {
            viewModelScope.launch {
                repository.loadMore()
            }
        }
    }

    fun downloadWallpaper(wallpaper: WallpaperItem) {
        if (downloadJob?.isActive == true) return
        resetSavedJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloader.download(wallpaper).collect { state ->
                _uiState.update { it.copy(downloadState = state) }
                if (state is DownloadState.Saved) {
                    resetSavedJob = launch {
                        delay(2_600L)
                        _uiState.update { current ->
                            if (current.downloadState is DownloadState.Saved) {
                                current.copy(downloadState = DownloadState.Idle)
                            } else {
                                current
                            }
                        }
                    }
                }
            }
        }
    }

    class Factory(
        private val repository: WallpaperRepository,
        private val downloader: FullQualityDownloader
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FullscreenViewModel(repository, downloader) as T
        }
    }
}
