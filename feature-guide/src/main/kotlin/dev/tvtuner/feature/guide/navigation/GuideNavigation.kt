package dev.tvtuner.feature.guide.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.tvtuner.feature.guide.GuideScreen

const val ROUTE_GUIDE = "guide"

fun NavGraphBuilder.guideScreen(navController: NavController) {
    composable(ROUTE_GUIDE) {
        GuideScreen(
            onBack = { navController.popBackStack() },
            onTuneToChannel = {
                navController.navigate("live_tv") {
                    launchSingleTop = true
                }
            },
        )
    }
}
