package dev.tvtuner.app

import android.content.Intent
import android.content.res.Configuration
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.tvtuner.app.navigation.TvTunerNavHost
import dev.tvtuner.app.pip.PipManager
import dev.tvtuner.core.ui.theme.TvTunerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pipManager: PipManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle USB device attached intent that launched the activity
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
        // Attempt to enter PiP if live TV is active
        pipManager.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipManager.onPipModeChanged(isInPictureInPictureMode)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE,
                android.hardware.usb.UsbDevice::class.java)
            device?.let {
                // Delegated to the tuner manager via the ViewModel graph
                // The UsbTunerBackend listens for this broadcast independently,
                // but we also handle the launch-intent case here.
            }
        }
    }
}
