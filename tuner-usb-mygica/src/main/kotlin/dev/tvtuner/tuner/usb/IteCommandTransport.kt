package dev.tvtuner.tuner.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-level USB command transport for ITE IT913x / AF9035-family ATSC USB tuners.
 *
 * ─── PROTOCOL REFERENCE ──────────────────────────────────────────────────────
 * Based on: Linux kernel drivers/media/usb/dvb-usb-v2/af9035.c, it913x.c
 *
 * Command TX packet layout (sent on EP_CMD_OUT, e.g. 0x02):
 *   Byte  0   : payload_len = 9 + data_len
 *                (bytes after [0] up to and including last data byte)
 *   Byte  1   : mbox address (0x00 = host interface)
 *   Byte  2   : command ID (CMD_MEM_RD, CMD_MEM_WR, …)
 *   Byte  3   : sequence number (auto-incremented per transfer)
 *   Bytes 4–7 : register/target address, big-endian 32-bit
 *   Byte  8   : addr_len (3 for 24-bit register addresses)
 *   Byte  9   : data_len (bytes to write, or expected bytes to read)
 *   Bytes 10+ : data payload (for writes) or zeros (for reads)
 *   Last  1   : 2's-complement checksum of bytes [0 .. payload_len]
 *
 * Response RX packet (received on EP_CMD_IN, e.g. 0x81):
 *   Byte 0    : length (4 + response_data_len)
 *   Byte 1    : mbox (echoed)
 *   Byte 2    : cmd  (echoed)
 *   Byte 3    : seq  (echoed)
 *   Bytes 4+  : response data (for reads)
 *
 * ─── REGISTER MAP ────────────────────────────────────────────────────────────
 * All addresses are in the IT913x/IT9303 internal register space.
 * Verified against the Linux AF9035 + IT913x kernel driver.
 * ⚠ HARDWARE NOTE: Run USB protocol analysis (usbmon/Wireshark) on your
 *   device if register reads return 0xFF or tuning fails; the exact
 *   addresses differ between IT9303, IT9135, and AF9035 demod variants.
 *
 * ─── USB ENDPOINT LAYOUT ─────────────────────────────────────────────────────
 * Interface 0, Alt-setting 0 (control mode, no TS streaming):
 *   EP 0x02 Bulk-OUT : command requests
 *   EP 0x81 Bulk-IN  : command responses
 * Interface 0, Alt-setting 1 (streaming enabled):
 *   EP 0x84 Bulk-IN  : raw MPEG-2 TS packets (188-byte aligned)
 * Some variants omit alt-settings; in that case all 3 EPs exist on alt 0.
 */
class IteCommandTransport(
    private val connection: UsbDeviceConnection,
    private val epCmdOut: UsbEndpoint,
    private val epCmdIn: UsbEndpoint,
    val epTsIn: UsbEndpoint,
) {
    companion object {
        private const val TAG = "IteCmd"

        private const val CMD_TIMEOUT_MS = 3_000
        private const val RESP_BUF_SIZE  = 64

        // ─── Command IDs ──────────────────────────────────────────────────
        const val CMD_MEM_RD    = 0x00  // Read internal register
        const val CMD_MEM_WR    = 0x01  // Write internal register
        const val CMD_I2C_RD    = 0x02  // Read via demod I2C master
        const val CMD_I2C_WR    = 0x03  // Write via demod I2C master
        const val CMD_FW_BOOT   = 0x13  // Boot loaded firmware
        const val CMD_FW_INFO   = 0x22  // Query firmware version

        // ─── Key IT913x / AF9035 Registers ───────────────────────────────
        /** Firmware version string (4 bytes, major.minor.patch.build). */
        const val REG_FW_VERSION      = 0x001220

        /**
         * Lock status register.
         * Bit 3 = VIT (Viterbi / inner-code) lock → signal found.
         * Bit 2 = AGC lock
         * Polling this at 50 ms intervals after a frequency change tells us
         * whether a valid ATSC signal is present.
         */
        const val REG_LOCK_STATUS     = 0x00d507

        /**
         * TS output enable.
         * Write 0x01 to enable raw MPEG-2 TS output on EP_TS_IN.
         * Write 0x00 to pause streaming (e.g. during frequency changes).
         */
        const val REG_TS_OUTPUT_EN    = 0x00d1b8

        /**
         * Frequency registers — 32-bit center frequency split across 4 bytes.
         * The IT913x/IT9303 stores the frequency in Hz (big-endian).
         * Example: 473 MHz → 473_000_000 Hz → 0x1C28_BE00
         *   REG_FREQ_B3 ← 0x1C   (bits [31:24])
         *   REG_FREQ_B2 ← 0x28   (bits [23:16])
         *   REG_FREQ_B1 ← 0xBE   (bits [15:8])
         *   REG_FREQ_B0 ← 0x00   (bits [7:0])
         */
        const val REG_FREQ_B3         = 0x00d144  // MSB
        const val REG_FREQ_B2         = 0x00d145
        const val REG_FREQ_B1         = 0x00d146
        const val REG_FREQ_B0         = 0x00d147  // LSB

        /**
         * Bandwidth register.
         * 0x01 = 6 MHz (ATSC, used in US/Canada)
         * 0x02 = 7 MHz (DVB-T in some regions)
         * 0x03 = 8 MHz (DVB-T in most of Europe)
         */
        const val REG_BANDWIDTH       = 0x00d140
        const val BW_6MHZ             = 0x01

        /**
         * Retune trigger.
         * Writing 0x01 here starts the frequency change / lock sequence.
         * The demod sets REG_LOCK_STATUS bit 3 when locked.
         */
        const val REG_RETUNE_TRIGGER  = 0x00d160

        /**
         * I2C address of the IT9135/IT9137 RF tuner sitting on the
         * demodulator's internal I2C bus. Used when the tuner is a
         * discrete chip (not integrated) — change if your variant differs.
         */
        const val TUNER_I2C_ADDR      = 0x38
    }

    @Volatile private var seqNum: Int = 0

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Read one byte from an internal register. Returns null on USB error. */
    suspend fun readReg(addr: Int): Int? = withContext(Dispatchers.IO) {
        val pkt = buildPacket(CMD_MEM_RD, addr, addrLen = 3, dataLen = 1)
        if (sendBulk(pkt) < 0) return@withContext null
        val resp = recvBulk(5) ?: return@withContext null
        resp[4].toInt() and 0xFF
    }

    /** Write one byte to an internal register. Returns true on success. */
    suspend fun writeReg(addr: Int, value: Int): Boolean = withContext(Dispatchers.IO) {
        val pkt = buildPacket(CMD_MEM_WR, addr, addrLen = 3, dataLen = 1,
            data = byteArrayOf(value.toByte()))
        sendBulk(pkt) >= 0
    }

    /**
     * Read the firmware version string (e.g. "1.0.5.0").
     * Useful for confirming the device is talking the right protocol variant.
     */
    suspend fun readFirmwareVersion(): String {
        val bytes = (0 until 4).mapNotNull { readReg(REG_FW_VERSION + it) }
        return if (bytes.size == 4) bytes.joinToString(".") else "unknown"
    }

    /**
     * Tune the demodulator to [freqKhz] kHz center frequency (ATSC 6 MHz).
     *
     * Sequence:
     *  1. Gate off TS output to avoid partial packets during retune.
     *  2. Write 32-bit frequency (in Hz) to the four frequency registers.
     *  3. Set bandwidth = 6 MHz.
     *  4. Trigger the retune sequence.
     *  5. TS output re-enabled by caller after lock is confirmed.
     */
    suspend fun setFrequency(freqKhz: Int): Boolean {
        val freqHz = freqKhz.toLong() * 1_000L
        Log.d(TAG, "setFrequency: ${freqKhz} kHz (0x${freqHz.toString(16)})")

        // Pause TS stream while retuning
        writeReg(REG_TS_OUTPUT_EN, 0x00)

        // Write 32-bit frequency in Hz (big-endian across 4 consecutive regs)
        val ok = writeReg(REG_FREQ_B3, ((freqHz shr 24) and 0xFF).toInt()) &&
                 writeReg(REG_FREQ_B2, ((freqHz shr 16) and 0xFF).toInt()) &&
                 writeReg(REG_FREQ_B1, ((freqHz shr 8)  and 0xFF).toInt()) &&
                 writeReg(REG_FREQ_B0, (freqHz and 0xFF).toInt()) &&
                 writeReg(REG_BANDWIDTH, BW_6MHZ) &&
                 writeReg(REG_RETUNE_TRIGGER, 0x01)  // fire!

        if (!ok) Log.e(TAG, "setFrequency: one or more register writes failed")
        return ok
    }

    /** Enable raw TS output on [epTsIn]. */
    suspend fun enableStream(): Boolean = writeReg(REG_TS_OUTPUT_EN, 0x01).also {
        if (it) Log.d(TAG, "TS stream enabled")
    }

    /** Disable raw TS output on [epTsIn]. */
    suspend fun disableStream(): Boolean = writeReg(REG_TS_OUTPUT_EN, 0x00).also {
        if (it) Log.d(TAG, "TS stream disabled")
    }

    /**
     * Poll the lock status register until bit 3 (VIT lock) goes high or
     * [timeoutMs] expires.
     *
     * @return true if the demodulator locked; false if no signal was found
     *         within the given window.
     */
    suspend fun waitForLock(timeoutMs: Long = 2_000L): Boolean =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val status = readReg(REG_LOCK_STATUS)
                if (status != null && (status and 0x08) != 0) {
                    Log.d(TAG, "waitForLock: locked (status=0x${status.toString(16)})")
                    return@withContext true
                }
                Thread.sleep(50)
            }
            Log.d(TAG, "waitForLock: timeout — no signal")
            false
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construct an AF9035-style bulk command packet.
     *
     * @param cmd      command ID (CMD_MEM_RD, CMD_MEM_WR, …)
     * @param addr     32-bit register address
     * @param addrLen  number of significant address bytes (3 for most registers)
     * @param dataLen  number of data bytes (to write, or expected to read)
     * @param data     payload bytes for write commands; unused for reads
     * @param mbox     mailbox select; 0x00 = host interface (default)
     */
    private fun buildPacket(
        cmd: Int,
        addr: Int,
        addrLen: Int = 3,
        dataLen: Int = 0,
        data: ByteArray = ByteArray(0),
        mbox: Int = 0x00,
    ): ByteArray {
        // payload_len covers everything AFTER the length byte, up to last data byte
        val payloadLen = 9 + data.size  // mbox+cmd+seq+addr(4)+addrLen+dataLen+data
        val totalLen = payloadLen + 2   // +1 for the length byte itself, +1 for checksum
        val buf = ByteArray(totalLen)

        buf[0] = payloadLen.toByte()
        buf[1] = mbox.toByte()
        buf[2] = cmd.toByte()
        buf[3] = (seqNum++ and 0xFF).toByte()
        buf[4] = ((addr shr 24) and 0xFF).toByte()
        buf[5] = ((addr shr 16) and 0xFF).toByte()
        buf[6] = ((addr shr 8)  and 0xFF).toByte()
        buf[7] = (addr and 0xFF).toByte()
        buf[8] = addrLen.toByte()
        buf[9] = dataLen.toByte()
        for (i in data.indices) buf[10 + i] = data[i]

        // 2's-complement checksum: sum of buf[0..payloadLen], then negate
        var sum = 0
        for (i in 0..payloadLen) sum += buf[i].toInt() and 0xFF
        buf[payloadLen + 1] = ((-sum) and 0xFF).toByte()

        return buf
    }

    private fun sendBulk(packet: ByteArray): Int {
        val n = connection.bulkTransfer(epCmdOut, packet, packet.size, CMD_TIMEOUT_MS)
        if (n < 0) Log.e(TAG, "sendBulk: error $n (cmd=0x${(packet[2].toInt() and 0xFF).toString(16)})")
        return n
    }

    private fun recvBulk(minBytes: Int): ByteArray? {
        val buf = ByteArray(RESP_BUF_SIZE)
        val n = connection.bulkTransfer(epCmdIn, buf, minBytes.coerceAtLeast(4), CMD_TIMEOUT_MS)
        if (n < 4) {
            Log.e(TAG, "recvBulk: short response ($n bytes)")
            return null
        }
        return buf.copyOf(n)
    }
}
