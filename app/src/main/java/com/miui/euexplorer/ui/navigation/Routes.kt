package com.miui.euexplorer.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ExplorerRoute

@Serializable
data class FullscreenRoute(
    val initialIndex: Int
)
