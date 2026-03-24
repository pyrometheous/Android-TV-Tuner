package dev.tvtuner.feature.recordings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import dev.tvtuner.feature.recordings.RecordingsScreen

const val ROUTE_RECORDINGS = "recordings"
const val ROUTE_RECORDINGS_GRAPH = "recordings_graph"

fun NavGraphBuilder.recordingsGraph(navController: NavController) {
    navigation(startDestination = ROUTE_RECORDINGS, route = ROUTE_RECORDINGS_GRAPH) {
        composable(ROUTE_RECORDINGS) {
            RecordingsScreen(
                onBack = { navController.popBackStack() },
                onPlayRecording = { /* TODO: navigate to playback screen */ },
            )
        }
    }
}
