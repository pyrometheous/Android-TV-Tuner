package dev.tvtuner.app

import android.content.Intent
import android.content.res.Configuration
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.tvtuner.app.navigation.TvTunerNavHost
import dev.tvtuner.app.pip.PipManager
import dev.tvtuner.core.ui.theme.TvTunerTheme
import dev.tvtuner.feature.settings.SettingsViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pipManager: PipManager

    /**
     * SettingsViewModel is the entry point for device selection/auto-detection.
     * Using activityViewModels scope ensures a single instance handles both
     * the USB attach event here and the compose UI in SettingsScreen.
     */
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle USB_DEVICE_ATTACHED that caused this Activity to be launched
        handleUsbIntent(intent)

        setContent {
            TvTunerTheme {
                TvTunerNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipManager.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipManager.onPipModeChanged(isInPictureInPictureMode)
    }

    /**
     * When the OS delivers a USB_DEVICE_ATTACHED intent (either on launch or
     * via onNewIntent), trigger auto-selection on the SettingsViewModel.
     * The ViewModel calls TunerManager.autoSelectUsbIfAvailable() which will:
     *   • silently open the device if USB permission was previously granted, or
     *   • show the system USB permission dialog on first connection.
     */
    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            settingsViewModel.onUsbDeviceAttached()
        }
    }
}
