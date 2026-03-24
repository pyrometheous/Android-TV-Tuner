package dev.tvtuner.feature.livetv.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.tvtuner.feature.guide.navigation.ROUTE_GUIDE
import dev.tvtuner.feature.livetv.LiveTvScreen
import dev.tvtuner.feature.recordings.navigation.ROUTE_RECORDINGS

const val ROUTE_LIVE_TV = "live_tv"

fun NavGraphBuilder.liveTvScreen(navController: NavController) {
    composable(ROUTE_LIVE_TV) {
        LiveTvScreen(
            onOpenGuide = { navController.navigate(ROUTE_GUIDE) },
            onOpenRecordings = { navController.navigate(ROUTE_RECORDINGS) },
            onEnterPip = { /* Handled by MainActivity.onUserLeaveHint via PipManager */ },
        )
    }
}
