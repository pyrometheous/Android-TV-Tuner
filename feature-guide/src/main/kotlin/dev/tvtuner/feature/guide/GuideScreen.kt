package dev.tvtuner.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.core.data.db.entity.GuideEntryEntity
import dev.tvtuner.core.ui.components.ChannelLogoBadge
import dev.tvtuner.core.ui.components.ErrorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CHANNEL_COL_WIDTH = 80.dp
private val ROW_HEIGHT = 64.dp
private val PIXELS_PER_MINUTE = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onTuneToChannel: (ChannelEntity) -> Unit = {},
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val entries by viewModel.guideEntries.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TV Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (channels.isEmpty()) {
            ErrorState(
                message = "No channels found. Run a channel scan first.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            GuideGrid(
                channels = channels,
                entries = entries,
                windowStartMs = viewModel.guideWindowStart,
                onChannelClick = onTuneToChannel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun GuideGrid(
    channels: List<ChannelEntity>,
    entries: List<GuideEntryEntity>,
    windowStartMs: Long,
    onChannelClick: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entriesByChannel = entries.groupBy { it.channelId }
    val timeScrollState = rememberScrollState()

    Row(modifier = modifier) {
        // Fixed channel column
        LazyColumn(modifier = Modifier.width(CHANNEL_COL_WIDTH)) {
            item { Spacer(Modifier.height(ROW_HEIGHT)) } // time header spacer
            items(channels) { channel ->
                ChannelCell(
                    channel = channel,
                    onClick = { onChannelClick(channel) },
                    modifier = Modifier.height(ROW_HEIGHT),
                )
            }
        }

        // Scrollable program grid
        Column(modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(timeScrollState)) {
            // Time header
            TimeHeader(windowStartMs = windowStartMs)
            // Program rows
            channels.forEach { channel ->
                ProgramRow(
                    channel = channel,
                    programs = entriesByChannel[channel.id] ?: emptyList(),
                    windowStartMs = windowStartMs,
                    modifier = Modifier.height(ROW_HEIGHT),
                )
            }
        }
    }
}

@Composable
private fun TimeHeader(windowStartMs: Long) {
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    Row(modifier = Modifier.height(ROW_HEIGHT).fillMaxWidth()) {
        for (i in 0..3) {
            val t = windowStartMs + i * 60 * 60 * 1000L
            Box(
                modifier = Modifier
                    .width(PIXELS_PER_MINUTE * 60)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = fmt.format(Date(t)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelCell(
    channel: ChannelEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogoBadge(callsign = channel.callsign, size = 36.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = channel.virtualChannelDisplay,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgramRow(
    channel: ChannelEntity,
    programs: List<GuideEntryEntity>,
    windowStartMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .width(PIXELS_PER_MINUTE * 240) // 4 hour default
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "No guide data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            programs.forEach { program ->
                val durationMinutes = (program.durationMs / 60_000).toInt().coerceAtLeast(1)
                val startOffset = ((program.startTimeMs - windowStartMs) / 60_000)
                    .toInt().coerceAtLeast(0)
                if (startOffset > 0) {
                    Spacer(Modifier.width(PIXELS_PER_MINUTE * startOffset))
                }
                ProgramCell(
                    program = program,
                    widthDp = PIXELS_PER_MINUTE * durationMinutes,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ProgramCell(
    program: GuideEntryEntity,
    widthDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(widthDp)
            .padding(horizontal = 1.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!program.subtitle.isNullOrBlank()) {
                Text(
                    text = program.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
