package dev.vibe.realmebuds.bluetooth

import java.util.Locale
import java.util.UUID

enum class AncMode(
    val label: String,
    internal val modeByte: Int,
) {
    On("ANC", 0x04),
    Transparency("Przezroczystość", 0x02),
    Off("Normalny", 0x01),
}

enum class TouchSide(
    val label: String,
    internal val code: Int,
) {
    Left("Lewa", 0x01),
    Right("Prawa", 0x02),
    Both("Obie", 0x04),
}

enum class TouchType(
    val label: String,
    internal val code: Int,
) {
    Tap2("2x tap", 0x0201),
    Tap3("3x tap", 0x0301),
    Hold("Przytrzymaj", 0x0401),
}

enum class TouchAction(
    val label: String,
    internal val code: Int,
) {
    Off("Wyłączone", 0x00),
    PlayPause("Play/pauza", 0x01),
    Previous("Poprzedni", 0x05),
    Next("Następny", 0x06),
    VolumeUp("Głośniej", 0x0B),
    VolumeDown("Ciszej", 0x0C),
    VoiceAssistant("Asystent", 0x04),
    GameMode("Tryb gry", 0x11),
}

data class BatteryLevels(
    val left: Int? = null,
    val right: Int? = null,
    val caseLevel: Int? = null,
)

data class PacketSummary(
    val title: String,
    val batteryLevels: BatteryLevels? = null,
    val firmwareVersion: String? = null,
    val ancMode: AncMode? = null,
)

object OpoProtocol {
    val OPO_SERVICE_UUID: UUID = UUID.fromString("0000079a-d102-11e1-9b23-00025b00a5a5")

    fun setAnc(mode: AncMode): ByteArray = byteArrayOf(
        0xAA.toByte(),
        0x0A,
        0x00,
        0x00,
        0x04,
        0x04,
        0x42,
        0x03,
        0x00,
        0x01,
        0x01,
        mode.modeByte.toByte(),
    )

    fun rfcommFirmwareRequest(seq: Int): ByteArray =
        rfcommMessage(command = 0x0105, seq = seq)

    fun rfcommBatteryRequest(seq: Int): ByteArray =
        rfcommMessage(command = 0x0106, seq = seq)

    fun rfcommConfigurationRequest(seq: Int): ByteArray =
        rfcommMessage(command = 0x0108, seq = seq, payload = byteArrayOf(0x02, 0x03, 0x01))

    fun rfcommQueryAnc(seq: Int): ByteArray =
        rfcommMessage(command = 0x8204, seq = seq, payload = byteArrayOf(0x00, 0xF2.toByte()))

    fun rfcommSetAnc(mode: AncMode, seq: Int): ByteArray =
        rfcommMessage(command = 0x0404, seq = seq, payload = byteArrayOf(0x03, 0x01, mode.modeByte.toByte()))

    fun rfcommSetTouchConfig(
        side: TouchSide,
        type: TouchType,
        action: TouchAction,
        seq: Int,
    ): ByteArray = rfcommMessage(
        command = 0x0401,
        seq = seq,
        payload = byteArrayOf(
            0x01,
            side.code.toByte(),
            (type.code and 0xff).toByte(),
            ((type.code shr 8) and 0xff).toByte(),
            action.code.toByte(),
        ),
    )

    fun summarize(bytes: ByteArray): PacketSummary? {
        if (bytes.size < 6 || bytes[0] != 0xAA.toByte()) return null
        val command = (bytes[4].toInt() and 0xff) or ((bytes[5].toInt() and 0xff) shl 8)
        return when (command) {
            0x8106 -> PacketSummary(
                title = "Bateria",
                batteryLevels = parseRfcommBattery(bytes) ?: parseOpoV1Battery(bytes),
            )

            0x8105 -> PacketSummary(title = "Firmware", firmwareVersion = parseFirmware(bytes))
            0x8108 -> PacketSummary(title = "Konfiguracja")
            0x8401 -> PacketSummary(title = "ACK konfiguracji")
            0x8204, 0x0404 -> {
                val mode = if (bytes.size > 11) {
                    val modeByte = bytes[11].toInt()
                    AncMode.entries.find { it.modeByte == modeByte }
                } else null
                PacketSummary(title = "ANC", ancMode = mode)
            }
            0x0105, 0x0106, 0x0108 -> PacketSummary(title = "Żądanie")
            0x8100, 0x8500 -> PacketSummary(title = "ACK rejestracji")
            else -> PacketSummary(
                title = "Odpowiedź cmd=0x${command.hex4()}",
            )
        }
    }

    fun allowedActionsFor(side: TouchSide, type: TouchType): List<TouchAction> {
        return if (side == TouchSide.Both && type == TouchType.Hold) {
            listOf(TouchAction.Off, TouchAction.GameMode)
        } else {
            listOf(
                TouchAction.Off,
                TouchAction.PlayPause,
                TouchAction.Previous,
                TouchAction.Next,
                TouchAction.VolumeUp,
                TouchAction.VolumeDown,
                TouchAction.VoiceAssistant,
            )
        }
    }

    private fun rfcommMessage(
        command: Int,
        seq: Int,
        payload: ByteArray = byteArrayOf(),
    ): ByteArray {
        val size = 9 + payload.size
        return ByteArray(size).also { bytes ->
            bytes[0] = 0xAA.toByte()
            bytes[1] = (size - 2).toByte()
            bytes[2] = 0x00
            bytes[3] = 0x00
            bytes[4] = (command and 0xff).toByte()
            bytes[5] = ((command shr 8) and 0xff).toByte()
            bytes[6] = (seq and 0xff).toByte()
            bytes[7] = (payload.size and 0xff).toByte()
            bytes[8] = ((payload.size shr 8) and 0xff).toByte()
            payload.copyInto(bytes, destinationOffset = 9)
        }
    }

    private fun parseRfcommBattery(bytes: ByteArray): BatteryLevels? {
        val payloadLength = bytes.payloadLengthOrNull() ?: return null
        if (payloadLength <= 0 || bytes.size < 9 + payloadLength) return null
        val payload = bytes.copyOfRange(9, 9 + payloadLength)
        if (payload.size < 2 || payload[0] != 0x00.toByte()) return null

        var left: Int? = null
        var right: Int? = null
        var caseLevel: Int? = null
        for (index in 2 until payload.size - 1 step 2) {
            val batteryIndex = (payload[index].toInt() and 0xff) - 1
            val batteryLevel = payload[index + 1].toInt() and 0x7f
            when (batteryIndex) {
                0 -> left = batteryLevel
                1 -> right = batteryLevel
                2 -> caseLevel = batteryLevel.takeIf { it > 0 }
            }
        }
        return BatteryLevels(left = left, right = right, caseLevel = caseLevel)
    }

    private fun parseOpoV1Battery(bytes: ByteArray): BatteryLevels? {
        if (bytes.size >= 16) {
            return BatteryLevels(
                left = bytes[12].unsignedPercent(),
                right = bytes[14].unsignedPercent(),
                caseLevel = bytes[15].unsignedPercent().takeIf { it > 0 },
            )
        }
        return null
    }

    private fun parseFirmware(bytes: ByteArray): String? {
        val payloadLength = bytes.payloadLengthOrNull() ?: return null
        if (payloadLength <= 2 || bytes.size < 9 + payloadLength) return null
        val payload = bytes.copyOfRange(9, 9 + payloadLength)
        if (payload.firstOrNull() != 0x00.toByte()) return null

        val raw = payload.copyOfRange(2, payload.size)
            .dropLastWhile { byte -> byte == 0x00.toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)
            .trim()
        if (raw.isBlank()) return null

        val parts = raw.split(",")
        if (parts.size % 3 != 0) return raw

        val versions = mutableListOf<String>()
        for (index in parts.indices step 3) {
            val versionType = parts.getOrNull(index + 1)
            val version = parts.getOrNull(index + 2)
            if (versionType == "2" && !version.isNullOrBlank()) {
                versions.add(version)
                if (version.contains(".")) break
            }
        }
        return versions.takeIf { it.isNotEmpty() }?.joinToString(".") ?: raw
    }

    fun parseHex(input: String): ByteArray? {
        val cleaned = input.replace(Regex("[^0-9A-Fa-f]"), "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        return runCatching {
            ByteArray(cleaned.length / 2) { index ->
                cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    fun ByteArray.toHexString(): String =
        joinToString(" ") { byte -> "%02X".format(Locale.ROOT, byte.toInt() and 0xff) }

    private fun Int.hex4(): String = toString(16).uppercase(Locale.ROOT).padStart(4, '0')

    private fun ByteArray.payloadLengthOrNull(): Int? {
        if (size < 9) return null
        return (this[7].toInt() and 0xff) or ((this[8].toInt() and 0xff) shl 8)
    }

    private fun Byte.unsignedPercent(): Int {
        val value = toInt() and 0xff
        return value.coerceIn(0, 100)
    }
}
