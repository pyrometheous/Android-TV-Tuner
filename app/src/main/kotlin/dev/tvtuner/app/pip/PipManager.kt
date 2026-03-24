package dev.tvtuner.app.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Picture-in-Picture state and entry/exit.
 * The actual "should we PiP?" decision is driven by whether live TV is active.
 */
@Singleton
class PipManager @Inject constructor() {

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode

    private var liveTvActive = false

    fun notifyLiveTvActive(active: Boolean) {
        liveTvActive = active
    }

    fun onUserLeaveHint(activity: Activity) {
        if (!liveTvActive) return
        enterPip(activity)
    }

    fun enterPip(activity: Activity) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(true)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
        activity.enterPictureInPictureMode(params)
    }

    fun onPipModeChanged(isInPip: Boolean) {
        _isInPipMode.value = isInPip
    }
}
