package dev.vibe.realmebuds

import dev.vibe.realmebuds.bluetooth.AncMode
import dev.vibe.realmebuds.bluetooth.TouchAction
import dev.vibe.realmebuds.bluetooth.TouchSide
import dev.vibe.realmebuds.bluetooth.TouchType

data class BudsUiState(
    val permissionsGranted: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String? = null,
    val status: String = "Nie połączono",
    val firmwareVersion: String? = null,
    val bondedDeviceFound: Boolean = false,
    val opoUuidFound: Boolean = false,
    val transport: String = "RFCOMM",
    val customHexInput: String = "",
    val selectedTouchSide: TouchSide = TouchSide.Left,
    val selectedTouchType: TouchType = TouchType.Tap2,
    val selectedTouchAction: TouchAction = TouchAction.PlayPause,
    val leftBattery: Int? = null,
    val rightBattery: Int? = null,
    val caseBattery: Int? = null,
    val ancMode: AncMode? = null,
    val enabledTileModes: List<AncMode> = listOf(AncMode.On, AncMode.Transparency, AncMode.Off),
    val logLines: List<String> = emptyList(),
)
