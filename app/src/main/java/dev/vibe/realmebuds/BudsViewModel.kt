package dev.vibe.realmebuds

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import dev.vibe.realmebuds.bluetooth.AncMode
import dev.vibe.realmebuds.bluetooth.OpoBluetoothClient
import dev.vibe.realmebuds.bluetooth.OpoProtocol
import dev.vibe.realmebuds.bluetooth.OpoProtocol.toHexString
import dev.vibe.realmebuds.bluetooth.TouchAction
import dev.vibe.realmebuds.bluetooth.TouchSide
import dev.vibe.realmebuds.bluetooth.TouchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class BudsViewModel(application: Application) : AndroidViewModel(application), OpoBluetoothClient.Listener {
    private val client = OpoBluetoothClient(application.applicationContext, this)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _uiState = MutableStateFlow(
        BudsUiState(permissionsGranted = client.hasRuntimePermissions()),
    )
    val uiState: StateFlow<BudsUiState> = _uiState.asStateFlow()

    init {
        val prefs = application.getSharedPreferences("realme_buds_settings", Context.MODE_PRIVATE)
        val savedModes = prefs.getStringSet("enabled_tile_modes", null)
        val initialModes = if (savedModes != null) {
            savedModes.mapNotNull { name -> AncMode.entries.find { it.name == name } }
        } else {
            listOf(AncMode.On, AncMode.Transparency, AncMode.Off)
        }
        _uiState.update { it.copy(enabledTileModes = initialModes) }
    }

    fun refreshPermissions() {
        _uiState.update { state -> state.copy(permissionsGranted = client.hasRuntimePermissions()) }
    }

    fun connect() {
        if (!client.hasRuntimePermissions()) {
            onStatus("Brakuje uprawnień Bluetooth")
            return
        }
        client.connectToBondedHeadphones()
    }

    fun disconnect() {
        client.disconnect()
    }

    fun updateCustomHex(value: String) {
        _uiState.update { state -> state.copy(customHexInput = value) }
    }

    fun toggleTileMode(mode: AncMode) {
        _uiState.update { state ->
            val current = state.enabledTileModes.toMutableList()
            if (current.contains(mode)) {
                if (current.size > 1) current.remove(mode)
            } else {
                current.add(mode)
                current.sortBy { it.ordinal } // Keep them in logical order
            }

            val prefs = getApplication<Application>().getSharedPreferences("realme_buds_settings", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("enabled_tile_modes", current.map { it.name }.toSet()).apply()

            state.copy(enabledTileModes = current)
        }
    }

    fun setAncMode(mode: AncMode) {
        client.setAnc(mode)
    }

    fun sendCustomHex() {
        val data = OpoProtocol.parseHex(_uiState.value.customHexInput)
        if (data == null) {
            onStatus("HEX ma nieprawidłowy format")
            return
        }
        client.sendCustomPacket("CUSTOM", data)
    }

    fun cycleTouchSide() {
        _uiState.update { state ->
            val nextSide = TouchSide.entries.nextAfter(state.selectedTouchSide)
            val nextType = if (nextSide == TouchSide.Both) TouchType.Hold else state.selectedTouchType
            state.copy(
                selectedTouchSide = nextSide,
                selectedTouchType = nextType,
                selectedTouchAction = state.selectedTouchAction.coerceFor(nextSide, nextType),
            )
        }
    }

    fun cycleTouchType() {
        _uiState.update { state ->
            val nextType = if (state.selectedTouchSide == TouchSide.Both) {
                TouchType.Hold
            } else {
                TouchType.entries.nextAfter(state.selectedTouchType)
            }
            state.copy(
                selectedTouchType = nextType,
                selectedTouchAction = state.selectedTouchAction.coerceFor(state.selectedTouchSide, nextType),
            )
        }
    }

    fun cycleTouchAction() {
        _uiState.update { state ->
            val allowedActions = OpoProtocol.allowedActionsFor(state.selectedTouchSide, state.selectedTouchType)
            state.copy(selectedTouchAction = allowedActions.nextAfter(state.selectedTouchAction))
        }
    }

    fun sendTouchConfig() {
        val state = _uiState.value
        client.setTouchConfig(
            side = state.selectedTouchSide,
            type = state.selectedTouchType,
            action = state.selectedTouchAction,
        )
    }

    override fun onStatus(message: String) {
        _uiState.update { state ->
            state.copy(status = message).withLog("• $message")
        }
    }

    override fun onConnectingChanged(connecting: Boolean) {
        _uiState.update { state -> state.copy(connecting = connecting) }
    }

    override fun onConnectionDiagnostics(
        bondedDeviceFound: Boolean,
        opoUuidFound: Boolean,
        transport: String,
    ) {
        _uiState.update { state ->
            state.copy(
                bondedDeviceFound = bondedDeviceFound,
                opoUuidFound = opoUuidFound,
                transport = transport,
            )
        }
    }

    override fun onConnected(deviceName: String) {
        _uiState.update { state ->
            state.copy(
                connected = true,
                connecting = false,
                deviceName = deviceName,
                status = "Połączono",
            ).withLog("• Połączono: $deviceName")
        }
    }

    override fun onDisconnected() {
        _uiState.update { state ->
            state.copy(
                connected = false,
                connecting = false,
                deviceName = null,
                status = "Nie połączono",
            ).withLog("• Rozłączono")
        }
    }

    override fun onPacketReceived(source: String, data: ByteArray) {
        val summary = OpoProtocol.summarize(data)

        if (summary?.ancMode != null) {
            _uiState.update { state -> state.copy(ancMode = summary.ancMode) }
            val prefs = getApplication<Application>().getSharedPreferences("realme_buds_settings", Context.MODE_PRIVATE)
            prefs.edit().putInt("anc_mode", summary.ancMode.ordinal).apply()
        }

        _uiState.update { state ->
            val battery = summary?.batteryLevels
            state.copy(
                firmwareVersion = summary?.firmwareVersion ?: state.firmwareVersion,
                leftBattery = if (battery != null) battery.left ?: state.leftBattery else state.leftBattery,
                rightBattery = if (battery != null) battery.right ?: state.rightBattery else state.rightBattery,
                caseBattery = if (battery != null) battery.caseLevel ?: state.caseBattery else state.caseBattery,
            ).withLog("$source  ${data.toHexString()}${summary?.let { "  (${it.title})" } ?: ""}")
        }
    }

    override fun onCleared() {
        client.disconnect()
        super.onCleared()
    }

    private fun BudsUiState.withLog(line: String): BudsUiState {
        val timestamp = LocalTime.now().format(timeFormatter)
        return copy(logLines = (listOf("[$timestamp] $line") + logLines).take(MAX_LOG_LINES))
    }

    private fun TouchAction.coerceFor(side: TouchSide, type: TouchType): TouchAction {
        val allowedActions = OpoProtocol.allowedActionsFor(side, type)
        return if (this in allowedActions) this else allowedActions.first()
    }

    private fun <T> List<T>.nextAfter(current: T): T {
        val index = indexOf(current)
        return this[(index + 1).floorMod(size)]
    }

    private fun Int.floorMod(modulus: Int): Int =
        ((this % modulus) + modulus) % modulus

    companion object {
        private const val MAX_LOG_LINES = 120
    }
}
