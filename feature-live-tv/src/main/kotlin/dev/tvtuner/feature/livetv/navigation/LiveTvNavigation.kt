package dev.tvtuner.feature.livetv.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.tvtuner.feature.livetv.LiveTvScreen

const val ROUTE_LIVE_TV = "live_tv"

fun NavGraphBuilder.liveTvScreen(navController: NavController) {
    composable(ROUTE_LIVE_TV) {
        LiveTvScreen(
            onOpenGuide = { navController.navigate("guide") },
            onOpenRecordings = { navController.navigate("recordings") },
            onEnterPip = { /* Handled by MainActivity.onUserLeaveHint via PipManager */ },
        )
    }
}
