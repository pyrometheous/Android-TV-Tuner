package dev.tvtuner.feature.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tvtuner.core.data.db.entity.RecordingEntity
import dev.tvtuner.core.data.db.entity.RecordingStatus
import dev.tvtuner.core.ui.components.ChannelLogoBadge
import dev.tvtuner.core.ui.components.ErrorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onBack: () -> Unit,
    onPlayRecording: (RecordingEntity) -> Unit = {},
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val active by viewModel.activeRecordings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { inner ->
        if (recordings.isEmpty()) {
            ErrorState(
                message = "No recordings yet.\nStart recording from the live TV screen.",
                modifier = Modifier.fillMaxSize().padding(inner),
            )
        } else {
            RecordingsLibrary(
                recordings = recordings,
                activeIds = active.map { it.id }.toSet(),
                onPlay = onPlayRecording,
                onDelete = { viewModel.deleteRecording(it.id) },
                modifier = Modifier.fillMaxSize().padding(inner),
            )
        }
    }
}

@Composable
private fun RecordingsLibrary(
    recordings: List<RecordingEntity>,
    activeIds: Set<Long>,
    onPlay: (RecordingEntity) -> Unit,
    onDelete: (RecordingEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val watching = recordings.filter { it.watchedPositionMs > 0 && !it.isWatched }
    val recent = recordings.filter { it.id !in watching.map { r -> r.id }.toSet() }
        .sortedByDescending { it.startTimeMs }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (watching.isNotEmpty()) {
            item {
                SectionHeader("Continue Watching")
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(watching) { rec ->
                        RecordingCard(
                            recording = rec,
                            isActive = rec.id in activeIds,
                            onPlay = onPlay,
                            onDelete = onDelete,
                            compact = true,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        item { SectionHeader("Recently Recorded") }
        items(recent) { rec ->
            RecordingCard(
                recording = rec,
                isActive = rec.id in activeIds,
                onPlay = onPlay,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun RecordingCard(
    recording: RecordingEntity,
    isActive: Boolean,
    onPlay: (RecordingEntity) -> Unit,
    onDelete: (RecordingEntity) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.then(
            if (compact) Modifier.width(200.dp) else Modifier.fillMaxWidth()
        ),
        onClick = { onPlay(recording) },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogoBadge(callsign = recording.channelDisplayName, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!recording.subtitle.isNullOrBlank()) {
                        Text(
                            text = recording.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = dateFmt.format(Date(recording.startTimeMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isActive) {
                        Text(
                            "● Recording",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = { onPlay(recording) }) {
                    Icon(Icons.Default.PlayArrow, "Play", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onDelete(recording) }) {
                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(20.dp))
                }
            }

            // Resume progress bar
            if (recording.watchedPositionMs > 0 && recording.durationMs > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { recording.watchedPositionMs.toFloat() / recording.durationMs },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
