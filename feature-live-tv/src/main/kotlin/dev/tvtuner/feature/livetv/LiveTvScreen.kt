package dev.tvtuner.feature.livetv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import dev.tvtuner.core.ui.components.LoadingOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(
    onOpenGuide: () -> Unit,
    onOpenRecordings: () -> Unit,
    onEnterPip: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val overlayVisible by viewModel.overlayVisible.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val signal by viewModel.signalMetrics.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val player = viewModel.getPlayer()
    val scope = rememberCoroutineScope()

    // Auto-tune to last watched channel on first load
    LaunchedEffect(channels) {
        if (channels.isNotEmpty() && currentChannel == null) {
            viewModel.tuneToChannel(channels.first())
        }
    }

    // Auto-hide overlay after 4 seconds
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            delay(4_000)
            viewModel.hideOverlay()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { viewModel.toggleOverlay() },
    ) {
        // ExoPlayer surface
        PlayerSurface(
            player = player,
            modifier = Modifier.fillMaxSize(),
        )

        // Buffering indicator
        LoadingOverlay(
            isLoading = playbackState is PlaybackState.Buffering,
            modifier = Modifier.fillMaxSize(),
        ) {}

        // Error state
        if (playbackState is PlaybackState.Error) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (playbackState as PlaybackState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }

        // Channel overlay
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LiveTvOverlay(
                channel = currentChannel,
                signalQuality = signal?.qualityPercent,
                isFavorite = currentChannel?.isFavorite == true,
                onChannelUp = { viewModel.channelUp() },
                onChannelDown = { viewModel.channelDown() },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onOpenGuide = onOpenGuide,
                onOpenRecordings = onOpenRecordings,
                onEnterPip = onEnterPip,
            )
        }
    }
}

@Composable
private fun PlayerSurface(
    player: androidx.media3.exoplayer.ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                this.player = player
            }
        },
        modifier = modifier,
        update = { view -> view.player = player },
    )
}

@Composable
private fun LiveTvOverlay(
    channel: dev.tvtuner.core.data.db.entity.ChannelEntity?,
    signalQuality: Int?,
    isFavorite: Boolean,
    onChannelUp: () -> Unit,
    onChannelDown: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenRecordings: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Channel info row
        if (channel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.virtualChannelDisplay,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                if (signalQuality != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = "Signal",
                            tint = if (signalQuality > 70) Color.Green else Color.Yellow,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$signalQuality%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onChannelUp) {
                Icon(Icons.Default.KeyboardArrowUp, "Channel Up", tint = Color.White)
            }
            IconButton(onClick = onChannelDown) {
                Icon(Icons.Default.KeyboardArrowDown, "Channel Down", tint = Color.White)
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenGuide) {
                Icon(Icons.Default.GridView, "Guide", tint = Color.White)
            }
            IconButton(onClick = onEnterPip) {
                Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White)
            }
            IconButton(onClick = { /* TODO: record */ }) {
                Icon(Icons.Default.Circle, "Record", tint = Color.Red)
            }
        }
    }
}
