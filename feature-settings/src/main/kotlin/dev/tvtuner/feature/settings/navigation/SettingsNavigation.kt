package dev.tvtuner.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.tvtuner.feature.settings.SettingsScreen

const val ROUTE_SETTINGS = "settings"

fun NavGraphBuilder.settingsScreen(navController: NavHostController) {
    composable(ROUTE_SETTINGS) {
        SettingsScreen(onBack = { navController.popBackStack() })
    }
}
