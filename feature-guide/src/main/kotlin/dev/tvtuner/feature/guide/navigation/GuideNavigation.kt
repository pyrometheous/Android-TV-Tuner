package dev.tvtuner.feature.guide.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.tvtuner.feature.guide.GuideScreen
import dev.tvtuner.feature.livetv.navigation.ROUTE_LIVE_TV

const val ROUTE_GUIDE = "guide"

fun NavGraphBuilder.guideScreen(navController: NavController) {
    composable(ROUTE_GUIDE) {
        GuideScreen(
            onBack = { navController.popBackStack() },
            onTuneToChannel = {
                navController.navigate(ROUTE_LIVE_TV) {
                    launchSingleTop = true
                }
            },
        )
    }
}
