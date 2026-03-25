package dev.tvtuner.feature.livetv

import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.common.C
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * A Media3 [BaseDataSource] backed by a [Channel] of raw MPEG-2 TS bytes.
 *
 * ExoPlayer's [androidx.media3.exoplayer.source.ProgressiveMediaSource] reads
 * a MPEG-2 TS stream as a sequence of bytes. This DataSource bridges between
 * that pull-based API and the push-based [kotlinx.coroutines.channels.Channel]
 * that the USB read loop writes into.
 *
 * Thread-safety: ExoPlayer calls [read] from a background IO thread; the USB
 * read coroutine fills [tsChannel] from its own IO context — no shared mutable
 * state beyond the Channel itself (which is thread-safe).
 */
class TsLiveDataSource(
    private val tsChannel: Channel<ByteArray>,
) : BaseDataSource(/* isNetwork = */ false) {

    private var leftover: ByteArray = ByteArray(0)
    private var leftoverPos: Int = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        leftover = ByteArray(0)
        leftoverPos = 0
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        // Drain any bytes left from the previous chunk first
        while (leftoverPos >= leftover.size) {
            // Block until the channel has the next TS chunk from the USB read loop.
            // Returns C.RESULT_END_OF_INPUT if the channel is closed (stream stopped).
            val next = runBlocking { tsChannel.receiveCatching().getOrNull() }
                ?: return C.RESULT_END_OF_INPUT
            leftover    = next
            leftoverPos = 0
        }

        val available = leftover.size - leftoverPos
        val toCopy    = minOf(length, available)
        System.arraycopy(leftover, leftoverPos, buffer, offset, toCopy)
        leftoverPos += toCopy
        bytesTransferred(toCopy)
        return toCopy
    }

    override fun getUri(): Uri = Uri.EMPTY

    override fun close() {
        transferEnded()
    }
}
