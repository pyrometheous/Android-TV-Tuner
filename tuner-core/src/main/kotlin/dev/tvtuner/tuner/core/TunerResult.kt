package dev.tvtuner.tuner.core

/**
 * Sealed result type used throughout the tuner pipeline.
 */
sealed class TunerResult<out T> {
    data class Success<T>(val value: T) : TunerResult<T>()
    data class Failure(val error: TunerError) : TunerResult<Nothing>()
}

sealed class TunerError(open val message: String, open val cause: Throwable? = null) {
    data class PermissionDenied(override val message: String) : TunerError(message)
    data class DeviceNotFound(override val message: String) : TunerError(message)
    data class DeviceOpenFailed(override val message: String, override val cause: Throwable? = null) : TunerError(message, cause)
    data class TuneFailed(override val message: String, override val cause: Throwable? = null) : TunerError(message, cause)
    data class StreamError(override val message: String, override val cause: Throwable? = null) : TunerError(message, cause)
    data class UnsupportedFirmware(override val message: String) : TunerError(message)
    data class NotImplemented(override val message: String = "Not yet implemented — see DEVELOPMENT_STATUS.md") : TunerError(message)
    data class Unknown(override val message: String, override val cause: Throwable? = null) : TunerError(message, cause)
}

fun <T> TunerResult<T>.getOrNull(): T? = (this as? TunerResult.Success)?.value
fun <T> TunerResult<T>.errorOrNull(): TunerError? = (this as? TunerResult.Failure)?.error
