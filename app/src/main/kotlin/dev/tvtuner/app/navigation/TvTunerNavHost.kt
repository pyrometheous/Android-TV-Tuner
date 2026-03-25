package dev.tvtuner.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.tvtuner.feature.guide.navigation.guideScreen
import dev.tvtuner.feature.livetv.navigation.liveTvScreen
import dev.tvtuner.feature.recordings.navigation.recordingsGraph
import dev.tvtuner.feature.settings.navigation.settingsScreen
import dev.tvtuner.app.navigation.onboarding.ROUTE_ONBOARDING_GRAPH
import dev.tvtuner.app.navigation.onboarding.onboardingGraph

@Composable
fun TvTunerNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ROUTE_ONBOARDING_GRAPH,
    ) {
        onboardingGraph(navController)
        liveTvScreen(navController)
        guideScreen(navController)
        recordingsGraph(navController)
        settingsScreen(navController)
    }
}
