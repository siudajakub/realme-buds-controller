package dev.vibe.realmebuds.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpoProtocolTest {
    @Test
    fun parseHex_acceptsCommonSeparatorsAndLowercase() {
        val parsed = OpoProtocol.parseHex("aa:0b-7f\n00")

        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x0B, 0x7F, 0x00),
            parsed,
        )
    }

    @Test
    fun parseHex_rejectsEmptyAndOddLengthInput() {
        assertNull(OpoProtocol.parseHex(""))
        assertNull(OpoProtocol.parseHex("A A 1"))
    }

    @Test
    fun summarize_parsesRfcommBatteryLevels() {
        val summary = OpoProtocol.summarize(
            OpoProtocol.parseHex("AA 0F 00 00 06 81 22 08 00 00 03 01 64 02 D2 03 28")!!,
        )

        assertNotNull(summary)
        assertEquals("Bateria", summary!!.title)
        assertEquals(BatteryLevels(left = 100, right = 82, caseLevel = 40), summary.batteryLevels)
    }

    @Test
    fun summarize_fallsBackToOpoV1BatteryLayout() {
        val summary = OpoProtocol.summarize(
            OpoProtocol.parseHex("AA 0E 00 00 06 81 00 00 00 00 00 00 2D 00 37 00")!!,
        )

        assertNotNull(summary)
        assertEquals("Bateria", summary!!.title)
        assertEquals(BatteryLevels(left = 45, right = 55, caseLevel = null), summary.batteryLevels)
    }

    @Test
    fun summarize_parsesStructuredFirmwareVersion() {
        val summary = OpoProtocol.summarize(
            firmwarePacket("L,1,ignored,R,2,1.2.3"),
        )

        assertNotNull(summary)
        assertEquals("Firmware", summary!!.title)
        assertEquals("1.2.3", summary.firmwareVersion)
    }

    @Test
    fun summarize_returnsRawFirmwareWhenTripletsAreUnavailable() {
        val summary = OpoProtocol.summarize(
            firmwarePacket("realme-buds-build-42"),
        )

        assertNotNull(summary)
        assertEquals("Firmware", summary!!.title)
        assertEquals("realme-buds-build-42", summary.firmwareVersion)
    }

    @Test
    fun setAnc_buildsExpectedOpoV1Frame() {
        val frame = OpoProtocol.setAnc(AncMode.Transparency)

        assertEquals("AA 0A 00 00 04 04 42 03 00 01 01 02", frame.toProtocolHex())
    }

    @Test
    fun rfcommAncFrames_includeSequencePayloadAndMode() {
        assertEquals(
            "AA 09 00 00 04 82 34 02 00 00 F2",
            OpoProtocol.rfcommQueryAnc(seq = 0x34).toProtocolHex(),
        )
        // NOTE: payload starts with 0x03 here, but the validated OPO-v1 setAnc frame
        // uses 0x01 (see setAnc_buildsExpectedOpoV1Frame). This asserts the value the app
        // currently sends to hardware; confirm on the real earbuds which one ANC accepts.
        assertEquals(
            "AA 0A 00 00 04 04 7F 03 00 03 01 04",
            OpoProtocol.rfcommSetAnc(mode = AncMode.On, seq = 0x7F).toProtocolHex(),
        )
    }

    @Test
    fun rfcommTouchConfigFrame_encodesSideTypeAndAction() {
        val frame = OpoProtocol.rfcommSetTouchConfig(
            side = TouchSide.Right,
            type = TouchType.Tap3,
            action = TouchAction.Next,
            seq = 0x11,
        )

        assertEquals("AA 0C 00 00 01 04 11 05 00 01 02 01 03 06", frame.toProtocolHex())
    }

    // ── allowedActionsFor tests ─────────────────────────────────────────

    @Test
    fun allowedActionsFor_bothHoldReturnsOnlyOffAndGameMode() {
        val actions = OpoProtocol.allowedActionsFor(TouchSide.Both, TouchType.Hold)
        assertEquals(listOf(TouchAction.Off, TouchAction.GameMode), actions)
    }

    @Test
    fun allowedActionsFor_leftTap2ReturnsFullList() {
        val actions = OpoProtocol.allowedActionsFor(TouchSide.Left, TouchType.Tap2)
        assertEquals(
            listOf(
                TouchAction.Off,
                TouchAction.PlayPause,
                TouchAction.Previous,
                TouchAction.Next,
                TouchAction.VolumeUp,
                TouchAction.VolumeDown,
                TouchAction.VoiceAssistant,
            ),
            actions,
        )
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    fun summarize_returnsNullForPacketShorterThanSixBytes() {
        assertNull(OpoProtocol.summarize(byteArrayOf(0xAA.toByte(), 0x03, 0x00)))
    }

    @Test
    fun summarize_returnsNullWhenHeaderIsNotAA() {
        assertNull(OpoProtocol.summarize(byteArrayOf(0xBB.toByte(), 0x07, 0x00, 0x00, 0x06, 0x81.toByte())))
    }

    // ── toHexString ─────────────────────────────────────────────────────

    @Test
    fun toHexString_formatsWithUppercaseAndSpaces() {
        val hex = with(OpoProtocol) {
            byteArrayOf(0xAA.toByte(), 0x0B, 0xFF.toByte()).toHexString()
        }
        assertEquals("AA 0B FF", hex)
    }

    // ── Battery with case level 0 ───────────────────────────────────────

    @Test
    fun summarize_batteryWithCaseLevelZeroTreatsAsNull() {
        // Battery payload: 0x00, count=0x03,
        //   left: index=0x01 value=0x50 (80%), right: index=0x02 value=0x32 (50%), case: index=0x03 value=0x00 (0%)
        val summary = OpoProtocol.summarize(
            OpoProtocol.parseHex("AA 0F 00 00 06 81 22 08 00 00 03 01 50 02 32 03 00")!!,
        )
        assertNotNull(summary)
        assertEquals(BatteryLevels(left = 80, right = 50, caseLevel = null), summary!!.batteryLevels)
    }

    // ── Firmware with empty / blank payload ─────────────────────────────

    @Test
    fun summarize_firmwareWithBlankPayloadReturnsNullVersion() {
        val summary = OpoProtocol.summarize(firmwarePacket("   "))
        assertNotNull(summary)
        assertEquals("Firmware", summary!!.title)
        assertNull(summary.firmwareVersion)
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private fun ByteArray.toProtocolHex(): String = with(OpoProtocol) { this@toProtocolHex.toHexString() }

    private fun firmwarePacket(rawFirmware: String): ByteArray {
        val raw = rawFirmware.toByteArray(Charsets.UTF_8)
        val payload = byteArrayOf(0x00, 0x01) + raw + byteArrayOf(0x00, 0x00)
        return ByteArray(9 + payload.size).also { bytes ->
            bytes[0] = 0xAA.toByte()
            bytes[1] = (bytes.size - 2).toByte()
            bytes[4] = 0x05
            bytes[5] = 0x81.toByte()
            bytes[6] = 0x09
            bytes[7] = (payload.size and 0xFF).toByte()
            bytes[8] = ((payload.size shr 8) and 0xFF).toByte()
            payload.copyInto(bytes, destinationOffset = 9)
        }
    }

}
