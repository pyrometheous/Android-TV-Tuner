package dev.tvtuner.app.navigation.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import dev.tvtuner.feature.livetv.navigation.ROUTE_LIVE_TV
import dev.tvtuner.feature.settings.onboarding.OnboardingScreen

const val ROUTE_ONBOARDING_GRAPH = "onboarding_graph"
const val ROUTE_ONBOARDING = "onboarding"

fun NavGraphBuilder.onboardingGraph(navController: NavController) {
    navigation(startDestination = ROUTE_ONBOARDING, route = ROUTE_ONBOARDING_GRAPH) {
        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(ROUTE_LIVE_TV) {
                        popUpTo(ROUTE_ONBOARDING_GRAPH) { inclusive = true }
                    }
                }
            )
        }
    }
}
