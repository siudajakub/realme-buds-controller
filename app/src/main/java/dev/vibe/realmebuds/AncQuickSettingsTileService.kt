package dev.vibe.realmebuds

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.vibe.realmebuds.bluetooth.AncMode
import dev.vibe.realmebuds.bluetooth.OpoBluetoothClient

class AncQuickSettingsTileService : TileService() {
    private var client: OpoBluetoothClient? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateUI()
    }

    private fun updateUI(connecting: Boolean = false) {
        val preferences = getSharedPreferences("realme_buds_settings", MODE_PRIVATE)
        val ancOrdinal = preferences.getInt("anc_mode", AncMode.Off.ordinal)
        val ancLabel = when (ancOrdinal) {
            AncMode.On.ordinal -> "ANC"
            AncMode.Transparency.ordinal -> "Przezroczystość"
            AncMode.Off.ordinal -> "Normalny"
            else -> "Nieznany"
        }

        qsTile?.apply {
            label = "Realme ANC"
            if (connecting) {
                subtitle = "Przełączam..."
                state = Tile.STATE_UNAVAILABLE
            } else {
                subtitle = ancLabel
                state = Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val preferences = getSharedPreferences("realme_buds_settings", MODE_PRIVATE)
        val currentOrdinal = preferences.getInt("anc_mode", AncMode.Off.ordinal)
        
        val savedModes = preferences.getStringSet("enabled_tile_modes", null)
        val enabledModes = if (savedModes != null && savedModes.isNotEmpty()) {
            savedModes.mapNotNull { name -> AncMode.entries.find { it.name == name } }.sortedBy { it.ordinal }
        } else {
            listOf(AncMode.On, AncMode.Transparency, AncMode.Off)
        }
        
        val currentMode = AncMode.entries.find { it.ordinal == currentOrdinal } ?: AncMode.Off
        
        val currentIndex = enabledModes.indexOf(currentMode)
        val nextMode = if (currentIndex != -1) {
            enabledModes[(currentIndex + 1) % enabledModes.size]
        } else {
            enabledModes.first()
        }

        updateUI(connecting = true)

        client?.disconnect()

        val listener = object : OpoBluetoothClient.Listener {
            override fun onStatus(message: String) {}
            override fun onConnectingChanged(connecting: Boolean) {}
            override fun onConnectionDiagnostics(bondedDeviceFound: Boolean, opoUuidFound: Boolean, transport: String) {}

            override fun onConnected(deviceName: String) {
                client?.setAnc(nextMode)
                
                preferences.edit().putInt("anc_mode", nextMode.ordinal).apply()

                handler.postDelayed({
                    finishSequence()
                }, 1500)
            }

            override fun onDisconnected() {
                handler.post { finishSequence() }
            }

            override fun onPacketReceived(source: String, data: ByteArray) {}
        }

        val newClient = OpoBluetoothClient(this, listener)
        client = newClient
        newClient.connectToBondedHeadphones(skipInit = true)

        handler.postDelayed({
            finishSequence()
        }, 5000)
    }

    private fun finishSequence() {
        client?.disconnect()
        client = null
        updateUI(connecting = false)
    }

    override fun onDestroy() {
        finishSequence()
        super.onDestroy()
    }
}
