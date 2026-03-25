package dev.tvtuner.feature.settings.onboarding

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tvtuner.feature.settings.SettingsViewModel
import dev.tvtuner.feature.livetv.ScanViewModel
import dev.tvtuner.feature.livetv.ScanUiState
import dev.tvtuner.tuner.core.TunerState

private const val STEP_WELCOME = 0
private const val STEP_TUNER = 1
private const val STEP_SCAN = 2
private const val STEP_DONE = 3
private const val TOTAL_STEPS = 4

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    scanViewModel: ScanViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }

    val discovered by settingsViewModel.discoveredTuners.collectAsStateWithLifecycle()
    val isScanning by settingsViewModel.isScanning.collectAsStateWithLifecycle()
    val tunerState by settingsViewModel.tunerState.collectAsStateWithLifecycle()
    val scanState by scanViewModel.uiState.collectAsStateWithLifecycle()

    val selectedDeviceId: String? = when (val ts = tunerState) {
        is TunerState.Ready -> ts.device.id
        is TunerState.Tuned -> ts.device.id
        else -> null
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / TOTAL_STEPS },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Step content
            AnimatedContent(targetState = step, label = "onboarding_step") { currentStep ->
                when (currentStep) {
                    STEP_WELCOME -> WelcomeStep()
                    STEP_TUNER -> TunerStep(
                        discovered = discovered,
                        isScanning = isScanning,
                        selectedDeviceId = selectedDeviceId,
                        onScan = { settingsViewModel.scanForNetworkTuners() },
                        onSelect = { settingsViewModel.selectDevice(it) },
                    )
                    STEP_SCAN -> ChannelScanStep(
                        scanState = scanState,
                        onStartScan = { scanViewModel.startScan() },
                    )
                    STEP_DONE -> DoneStep()
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > STEP_WELCOME) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f),
                    ) { Text("Back") }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                val nextLabel = when {
                    step == STEP_SCAN && scanState is ScanUiState.Scanning -> "Scanning…"
                    step == STEP_DONE -> "Start watching!"
                    else -> "Next"
                }
                val nextEnabled = step != STEP_SCAN ||
                    scanState !is ScanUiState.Scanning

                Button(
                    onClick = {
                        if (step == STEP_DONE) {
                            settingsViewModel.completeOnboarding()
                            onOnboardingComplete()
                        } else {
                            step++
                        }
                    },
                    enabled = nextEnabled,
                    modifier = Modifier.weight(1f),
                ) { Text(nextLabel) }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Default.Tv, null, modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(
            "Welcome to TV Tuner",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = """
                Watch free over-the-air TV with your USB-C or network tuner.
                
                No subscription. No internet required.
                All channel data comes directly from the broadcast signal.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TunerStep(
    discovered: List<dev.tvtuner.tuner.core.TunerDevice>,
    isScanning: Boolean,
    selectedDeviceId: String?,
    onScan: () -> Unit,
    onSelect: (dev.tvtuner.tuner.core.TunerDevice) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Find your tuner", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Attach a USB-C tuner or connect an HDHomeRun on your Wi-Fi network, then tap Scan.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onScan, enabled = !isScanning) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Search, null)
            }
            Text("  Scan for tuners")
        }
        discovered.forEach { device ->
            val isSelected = device.id == selectedDeviceId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(device) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
                if (isSelected) {
                    Text(
                        "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (discovered.isEmpty() && !isScanning) {
            Text(
                "No tuner found yet. You can still proceed and add one later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChannelScanStep(
    scanState: ScanUiState,
    onStartScan: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Scan for channels", style = MaterialTheme.typography.headlineSmall)
        Text(
            "A channel scan tunes through each broadcast frequency and picks up all available stations.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (scanState) {
            is ScanUiState.Idle -> {
                Button(onClick = onStartScan) { Text("Start Channel Scan") }
            }
            is ScanUiState.Scanning -> {
                LinearProgressIndicator(
                    progress = { scanState.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Scanning ${scanState.currentFreqKhz / 1000} MHz… (${scanState.channelsFound} found)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            is ScanUiState.Complete -> {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Found ${scanState.totalFound} channel(s)!",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            is ScanUiState.Error -> {
                Text(
                    "Scan failed: ${scanState.message}. You can start again or skip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onStartScan) { Text("Try Again") }
            }
        }
    }
}

@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Default.Check, null, modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text("You're all set!", style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center)
        Text(
            text = "Your channels have been saved. Tap below to start watching live TV.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
