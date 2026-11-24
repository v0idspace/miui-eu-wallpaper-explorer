package com.miui.euexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.miui.euexplorer.ui.explorer.ExplorerRouteScreen
import com.miui.euexplorer.ui.explorer.ExplorerViewModel
import com.miui.euexplorer.ui.fullscreen.FullscreenRouteScreen
import com.miui.euexplorer.ui.fullscreen.FullscreenViewModel
import com.miui.euexplorer.ui.navigation.ExplorerRoute
import com.miui.euexplorer.ui.navigation.FullscreenRoute
import com.miui.euexplorer.ui.theme.EuExplorerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        val container = (application as EuExplorerApp).container

        setContent {
            val isDark = isSystemInDarkTheme()
            EuExplorerTheme(darkTheme = isDark) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = ExplorerRoute,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable<ExplorerRoute> {
                        val explorerViewModel: ExplorerViewModel = viewModel(
                            factory = ExplorerViewModel.Factory(container.wallpaperRepository)
                        )
                        ExplorerRouteScreen(
                            viewModel = explorerViewModel,
                            onWallpaperClick = { index ->
                                navController.navigate(FullscreenRoute(initialIndex = index))
                            }
                        )
                    }

                    composable<FullscreenRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<FullscreenRoute>()
                        val fullscreenViewModel: FullscreenViewModel = viewModel(
                            factory = FullscreenViewModel.Factory(
                                repository = container.wallpaperRepository,
                                downloader = container.fullQualityDownloader
                            )
                        )
                        FullscreenRouteScreen(
                            initialIndex = route.initialIndex,
                            viewModel = fullscreenViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
