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
 * Command TX packet layout (sent on EP_CMD_OUT, e.g. 0x02) — AF9035 protocol:
 *   Byte  0   : header byte = REQ_HDR_LEN(4) + wlen + CHECKSUM_LEN(2) - 1
 *   Byte  1   : mbox = (reg >> 16) & 0xFF  (high byte of 24-bit address)
 *   Byte  2   : command ID (CMD_MEM_RD, CMD_MEM_WR, …)
 *   Byte  3   : sequence number (auto-incremented per transfer)
 *   Bytes 4+  : wbuf = [dataLen, 0x02, 0x00, 0x00, regHi, regLo, data…]
 *               where regHi = (reg >> 8) & 0xFF, regLo = reg & 0xFF
 *   Last  2   : 16-bit checksum = ~(alternating big-endian sum of buf[1..n])
 *               placed at buf[buf[0]-1] (hi) and buf[buf[0]] (lo)
 *
 * Response RX packet (received on EP_CMD_IN, e.g. 0x81)
 *   ACK_HDR_LEN = 3:
 *   Byte 0    : echoed header
 *   Byte 1    : echoed mbox
 *   Byte 2    : echoed cmd
 *   Bytes 3+  : response data (for reads; data starts at offset 3)
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
         * I2C address of the IT9135 RF tuner on the demodulator's internal
         * I2C bus. Frequency programming via this address requires the
         * proprietary ITE tuner driver (it913x.c) which is not in the
         * mainline Linux kernel. USB traffic capture is needed to reverse-
         * engineer the PLL programming sequence.
         */
        const val TUNER_I2C_ADDR      = 0x38
    }

    @Volatile private var seqNum: Int = 0

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Read one byte from an internal register. Returns null on USB error. */
    suspend fun readReg(addr: Int): Int? = withContext(Dispatchers.IO) {
        val pkt = buildPacket(CMD_MEM_RD, addr, dataLen = 1)
        if (sendBulk(pkt) < 0) return@withContext null
        // ACK_HDR_LEN = 3: response data starts at index 3
        val resp = recvBulk(4) ?: return@withContext null
        resp[3].toInt() and 0xFF
    }

    /** Write one byte to an internal register. Returns true on success. */
    suspend fun writeReg(addr: Int, value: Int): Boolean = withContext(Dispatchers.IO) {
        val pkt = buildPacket(CMD_MEM_WR, addr, dataLen = 1,
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
     * Applies the AF9033 retune sequence (bandwidth + FSM reset) so the
     * demodulator can acquire a new carrier. RF tuner frequency programming
     * (IT9135) still requires the proprietary ITE tuner driver; once USB
     * traffic can be captured we can add those I2C writes here.
     *
     * Sequence (from Linux af9033.c set_frontend):
     *  1. Pause TS output.
     *  2. Set 6 MHz ATSC bandwidth (reg 0x80f904).
     *  3. Clear demod status registers.
     *  4. Select VHF / UHF band (reg 0x80004b).
     *  5. Reset FSM to begin acquisition (reg 0x800000).
     */
    suspend fun setFrequency(freqKhz: Int): Boolean {
        Log.d(TAG, "setFrequency: $freqKhz kHz")

        // Pause TS stream while retuning
        writeReg(REG_TS_OUTPUT_EN, 0x00)

        // AF9033 / IT9135 demod-side retune sequence
        writeReg(0x80f904, 0x00)   // bandwidth bits[1:0] = 0x00 → 6 MHz (ATSC)
        writeReg(0x800040, 0x00)   // clear status
        writeReg(0x800047, 0x00)   // clear channel status
        writeReg(0x80f999, 0x00)   // clear lock flags
        val band = if (freqKhz <= 230_000) 0x00 else 0x01  // 0=VHF, 1=UHF
        writeReg(0x80004b, band)
        val ok = writeReg(0x800000, 0x00)  // reset FSM → triggers acquisition

        if (!ok) Log.e(TAG, "setFrequency: AF9033 FSM reset write failed")
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
     * Packet layout (from Linux af9035.c af9035_ctrl_msg):
     *   buf[0]   = REQ_HDR_LEN(4) + wlen + CHECKSUM_LEN(2) - 1
     *   buf[1]   = mbox = (addr >> 16) & 0xFF
     *   buf[2]   = cmd
     *   buf[3]   = seqNum++
     *   buf[4]   = dataLen  (wbuf[0]: bytes to read/write)
     *   buf[5]   = 0x02     (wbuf[1]: register access type)
     *   buf[6]   = 0x00     (wbuf[2]: padding)
     *   buf[7]   = 0x00     (wbuf[3]: padding)
     *   buf[8]   = (addr >> 8) & 0xFF  (wbuf[4]: address high byte)
     *   buf[9]   = addr & 0xFF          (wbuf[5]: address low byte)
     *   buf[10+] = data bytes (for writes; absent for reads)
     *   buf[n-1] = checksum high byte
     *   buf[n]   = checksum low byte
     *
     * Checksum: 16-bit bitwise NOT of alternating big-endian byte sum over
     *           buf[1..buf[0]-2], per af9035_checksum() in the Linux driver.
     *
     * @param cmd     command ID (CMD_MEM_RD, CMD_MEM_WR, …)
     * @param addr    24-bit register address; bits [23:16] become the mbox byte
     * @param dataLen bytes expected back (reads) or bytes to write (writes)
     * @param data    payload for write commands; empty for reads
     */
    private fun buildPacket(
        cmd: Int,
        addr: Int,
        dataLen: Int = 0,
        data: ByteArray = ByteArray(0),
    ): ByteArray {
        // Split the 24-bit address: mbox = bits[23:16], 16-bit reg in wbuf
        val mbox  = (addr shr 16) and 0xFF
        val regHi = (addr shr 8)  and 0xFF
        val regLo = addr and 0xFF

        // wbuf occupies 6 fixed bytes plus any write data
        val wlen = 6 + data.size
        // buf[0] = REQ_HDR_LEN(4) + wlen + CHECKSUM_LEN(2) - 1
        val headerByte = wlen + 5
        // Total packet = REQ_HDR_LEN(4) + wlen + CHECKSUM_LEN(2)
        val pktLen = wlen + 6
        val buf = ByteArray(pktLen)

        buf[0] = headerByte.toByte()
        buf[1] = mbox.toByte()
        buf[2] = cmd.toByte()
        buf[3] = (seqNum++ and 0xFF).toByte()
        buf[4] = dataLen.toByte()     // wbuf[0]: bytes to read/write
        buf[5] = 0x02                 // wbuf[1]: register access type
        buf[6] = 0x00                 // wbuf[2]: padding
        buf[7] = 0x00                 // wbuf[3]: padding
        buf[8] = regHi.toByte()       // wbuf[4]: address bits[15:8]
        buf[9] = regLo.toByte()       // wbuf[5]: address bits[7:0]
        for (i in data.indices) buf[10 + i] = data[i]

        // 16-bit checksum: ~(alternating big-endian sum of buf[1..buf[0]-2])
        // Place result at buf[pktLen-2] (hi) and buf[pktLen-1] (lo)
        val checksumStart = pktLen - 2
        var checksum = 0
        for (i in 1 until checksumStart) {
            val b = buf[i].toInt() and 0xFF
            checksum += if (i % 2 == 1) b shl 8 else b
        }
        checksum = checksum.inv() and 0xFFFF
        buf[checksumStart]     = (checksum shr 8).toByte()
        buf[checksumStart + 1] = (checksum and 0xFF).toByte()

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
