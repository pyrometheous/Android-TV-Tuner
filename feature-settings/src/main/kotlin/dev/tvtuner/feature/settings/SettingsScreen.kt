package dev.tvtuner.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tvtuner.core.data.preferences.AppPreferences
import dev.tvtuner.tuner.core.TunerBackendType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val selectedBackend by viewModel.selectedBackend.collectAsStateWithLifecycle()
    val discovered by viewModel.discoveredTuners.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val networkUrl by viewModel.networkTunerUrl.collectAsStateWithLifecycle()
    val recordingPath by viewModel.recordingPath.collectAsStateWithLifecycle()

    var networkUrlInput by rememberSaveable { mutableStateOf(networkUrl ?: "") }
    var recordingPathInput by rememberSaveable { mutableStateOf(recordingPath ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionHeader("Tuner Source")

            TunerBackendOption(
                label = "Auto (use available device)",
                selected = selectedBackend == AppPreferences.TunerBackend.AUTO,
                onClick = { viewModel.selectTunerBackend(AppPreferences.TunerBackend.AUTO) },
            )
            TunerBackendOption(
                label = "USB MyGica (PT682C family)",
                selected = selectedBackend == AppPreferences.TunerBackend.USB_MYGICA,
                onClick = { viewModel.selectTunerBackend(AppPreferences.TunerBackend.USB_MYGICA) },
                note = "Attach USB-C tuner. Permission dialog will appear.",
            )
            TunerBackendOption(
                label = "Network Tuner (HDHomeRun)",
                selected = selectedBackend == AppPreferences.TunerBackend.NETWORK_HDHR,
                onClick = { viewModel.selectTunerBackend(AppPreferences.TunerBackend.NETWORK_HDHR) },
            )
            TunerBackendOption(
                label = "Demo / Preview Mode",
                selected = selectedBackend == AppPreferences.TunerBackend.FAKE,
                onClick = { viewModel.selectTunerBackend(AppPreferences.TunerBackend.FAKE) },
                note = "Fake data — no real TV. For testing only.",
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.scanForNetworkTuners() },
                    enabled = !isScanning,
                ) {
                    Icon(Icons.Default.Search, null)
                    Text(" Scan for Network Tuners")
                }
                if (isScanning) CircularProgressIndicator(modifier = Modifier.height(24.dp))
            }

            if (discovered.isNotEmpty()) {
                Text(
                    "Found ${discovered.size} tuner(s):",
                    style = MaterialTheme.typography.bodyMedium,
                )
                discovered.forEach { device ->
                    Text(
                        "  • ${device.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()
            SettingsSectionHeader("Network Tuner URL (manual)")
            OutlinedTextField(
                value = networkUrlInput,
                onValueChange = { networkUrlInput = it },
                label = { Text("HDHomeRun IP, e.g. 192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = { viewModel.setNetworkTunerUrl(networkUrlInput) }) {
                Text("Save URL")
            }

            HorizontalDivider()
            SettingsSectionHeader("Recording Storage")
            OutlinedTextField(
                value = recordingPathInput,
                onValueChange = { recordingPathInput = it },
                label = { Text("Storage folder path") },
                placeholder = { Text("Leave blank for default (app external storage)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = { viewModel.setRecordingPath(recordingPathInput) }) {
                Text("Save Path")
            }

            HorizontalDivider()
            SettingsSectionHeader("About Guide Data")
            Text(
                text = """
                    This app builds its TV guide entirely from broadcast metadata (PSIP/ATSC tables) 
                    embedded in the live over-the-air signal. No internet connection is used.
                    
                    Guide quality depends on what each broadcaster transmits:
                    • Some channels broadcast a full 12–48 hour schedule.
                    • Others transmit only current and next program.
                    • Some transmit no guide data at all.
                    
                    Use "Refresh Metadata" in the channel list to retune through channels 
                    and collect the latest guide information from the broadcast signal.
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TunerBackendOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    note: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
