package dev.vibe.realmebuds.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.vibe.realmebuds.bluetooth.OpoProtocol.toHexString
import dev.vibe.realmebuds.bluetooth.TouchAction
import dev.vibe.realmebuds.bluetooth.TouchSide
import dev.vibe.realmebuds.bluetooth.TouchType
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class OpoBluetoothClient(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(message: String)
        fun onScanningChanged(scanning: Boolean)
        fun onConnectingChanged(connecting: Boolean)
        fun onConnectionDiagnostics(
            bondedDeviceFound: Boolean,
            opoUuidFound: Boolean,
            transport: String,
        )
        fun onConnected(deviceName: String)
        fun onDisconnected()
        fun onPacketReceived(source: String, data: ByteArray)
        fun onBleAdvertisement(summary: String)
    }

    private sealed interface Operation {
        data class WritePacket(val label: String, val data: ByteArray) : Operation
        data class Delay(val millis: Long) : Operation
    }

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val operationQueue = ArrayDeque<Operation>()
    private val writeLock = Any()
    private val disposed = AtomicBoolean(false)

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var ioThread: Thread? = null
    private var currentOperation: Operation? = null
    private var seqNum = 0
    private var isScanning = false
    private var isConnecting = false

    private val connectionTimeoutRunnable = Runnable {
        if (isConnecting) {
            failConnection("Timeout połączenia RFCOMM")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            listener.onBleAdvertisement(result.toSummary())
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            listener.onScanningChanged(false)
            listener.onStatus("Diagnostyka BLE nie powiodła się: $errorCode")
        }
    }

    fun hasRuntimePermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToBondedHeadphones() {
        if (!hasRuntimePermissions()) {
            listener.onStatus("Brakuje uprawnień Bluetooth")
            return
        }

        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            listener.onStatus("Bluetooth jest wyłączony")
            return
        }

        stopBleDiagnostics()
        disposed.set(true)
        disconnectSocket(notify = false)
        operationQueue.clear()
        currentOperation = null
        disposed.set(false)
        listener.onConnectionDiagnostics(
            bondedDeviceFound = false,
            opoUuidFound = false,
            transport = "RFCOMM",
        )

        val target = bluetoothAdapter.bondedDevices
            ?.filter { device -> isLikelyTarget(device.safeName()) }
            ?.sortedWith(compareByDescending<BluetoothDevice> { device ->
                isExactTarget(device.safeName())
            }.thenBy { device -> device.safeName().orEmpty() })
            ?.firstOrNull()

        if (target == null) {
            listener.onStatus("Brak sparowanych słuchawek")
            return
        }

        val hasOpoUuid = target.uuids
            ?.any { parcelUuid -> parcelUuid.uuid == OpoProtocol.OPO_SERVICE_UUID }
            ?: false

        listener.onConnectionDiagnostics(
            bondedDeviceFound = true,
            opoUuidFound = hasOpoUuid,
            transport = "RFCOMM",
        )

        if (!hasOpoUuid) {
            listener.onStatus("Brak UUID 0000079A")
            return
        }

        bluetoothAdapter.cancelDiscovery()
        isConnecting = true
        listener.onConnectingChanged(true)
        listener.onStatus("Łączę RFCOMM: ${target.safeName() ?: target.address}")
        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS)

        ioThread = Thread {
            runRfcommConnection(target)
        }.also { thread ->
            thread.name = "RealmeBudsRfcomm"
            thread.start()
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleDiagnostics() {
        if (!hasRuntimePermissions()) {
            listener.onStatus("Brakuje uprawnień Bluetooth")
            return
        }

        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            listener.onStatus("Bluetooth jest wyłączony")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            listener.onStatus("Skaner BLE niedostępny")
            return
        }

        if (isScanning) return
        isScanning = true
        listener.onScanningChanged(true)
        listener.onStatus("Diagnostyka BLE aktywna")
        scanner.startScan(
            null,
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback,
        )
        handler.postDelayed({ stopBleDiagnostics("Diagnostyka BLE zakończona") }, BLE_DIAGNOSTICS_MS)
    }

    fun stopBleDiagnostics() {
        stopBleDiagnostics(message = null)
    }

    fun disconnect() {
        disposed.set(true)
        stopBleDiagnostics()
        handler.removeCallbacks(connectionTimeoutRunnable)
        isConnecting = false
        listener.onConnectingChanged(false)
        operationQueue.clear()
        currentOperation = null
        disconnectSocket(notify = true)
    }

    fun setAnc(mode: AncMode) {
        enqueue(Operation.WritePacket("QUERY ANC", OpoProtocol.rfcommQueryAnc(nextSeq())))
        enqueue(Operation.Delay(500))
        enqueue(Operation.WritePacket("ANC: ${mode.label}", OpoProtocol.rfcommSetAnc(mode, nextSeq())))
    }

    fun queryBattery() {
        enqueue(Operation.WritePacket("Bateria", OpoProtocol.rfcommBatteryRequest(nextSeq())))
    }

    fun queryDeviceInfo() {
        listener.onStatus("Info test: eksperymentalna komenda diagnostyczna")
        enqueue(Operation.WritePacket("Info test", OpoProtocol.deviceInfoQuery()))
    }

    fun queryEq() {
        listener.onStatus("EQ test: eksperymentalna komenda diagnostyczna")
        enqueue(Operation.WritePacket("EQ test", OpoProtocol.eqQuery()))
    }

    fun setTouchConfig(
        side: TouchSide,
        type: TouchType,
        action: TouchAction,
    ) {
        enqueue(
            Operation.WritePacket(
                "Gest: ${side.label}/${type.label}/${action.label}",
                OpoProtocol.rfcommSetTouchConfig(side, type, action, nextSeq()),
            ),
        )
    }

    fun sendCustomPacket(label: String, data: ByteArray) {
        enqueue(Operation.WritePacket(label, data))
    }

    @SuppressLint("MissingPermission")
    private fun runRfcommConnection(device: BluetoothDevice) {
        var reportCleanDisconnect = false
        try {
            val activeSocket = device.createRfcommSocketToServiceRecord(OpoProtocol.OPO_SERVICE_UUID)
            socket = activeSocket
            activeSocket.connect()
            outputStream = activeSocket.outputStream
            handler.post {
                handler.removeCallbacks(connectionTimeoutRunnable)
                isConnecting = false
                listener.onConnectingChanged(false)
                listener.onConnected(device.safeName() ?: "realme Buds")
                listener.onStatus("Połączono przez RFCOMM")
                queueInitialization()
            }

            readLoop(activeSocket.inputStream)
            reportCleanDisconnect = true
        } catch (exception: IOException) {
            if (!disposed.get()) {
                handler.post { failConnection("Błąd socketu: ${exception.cleanMessage()}") }
            }
        } catch (exception: SecurityException) {
            if (!disposed.get()) {
                handler.post { failConnection("Brak uprawnień RFCOMM: ${exception.cleanMessage()}") }
            }
        } finally {
            cleanupSocket()
            if (!disposed.get() && reportCleanDisconnect) {
                handler.post {
                    listener.onStatus("Rozłączono RFCOMM")
                    listener.onDisconnected()
                }
            }
        }
    }

    private fun queueInitialization() {
        enqueue(Operation.WritePacket("Firmware", OpoProtocol.rfcommFirmwareRequest(nextSeq())))
        enqueue(Operation.Delay(250))
        enqueue(Operation.WritePacket("Konfiguracja", OpoProtocol.rfcommConfigurationRequest(nextSeq())))
        enqueue(Operation.Delay(250))
        enqueue(Operation.WritePacket("Bateria", OpoProtocol.rfcommBatteryRequest(nextSeq())))
    }

    private fun readLoop(inputStream: InputStream) {
        val buffer = ByteArray(4096)
        while (!disposed.get()) {
            val read = inputStream.read(buffer)
            if (read < 0) break
            val data = buffer.copyOf(read)
            handler.post {
                listener.onPacketReceived("RX RFCOMM", data)
            }
        }
    }

    private fun enqueue(operation: Operation) {
        operationQueue.add(operation)
        processNextOperation()
    }

    private fun processNextOperation() {
        if (currentOperation != null) return
        val next = if (operationQueue.isEmpty()) return else operationQueue.removeFirst()
        currentOperation = next

        when (next) {
            is Operation.Delay -> handler.postDelayed({ finishCurrentOperation() }, next.millis)
            is Operation.WritePacket -> writePacket(next.label, next.data)
        }
    }

    private fun finishCurrentOperation() {
        if (currentOperation == null) return
        currentOperation = null
        processNextOperation()
    }

    private fun writePacket(label: String, data: ByteArray) {
        val activeOutput = outputStream
        if (activeOutput == null || socket?.isConnected != true) {
            listener.onStatus("Nie połączono z RFCOMM")
            finishCurrentOperation()
            return
        }

        listener.onStatus("TX $label")
        listener.onPacketReceived("TX RFCOMM $label", data)

        Thread {
            try {
                synchronized(writeLock) {
                    activeOutput.write(data)
                    activeOutput.flush()
                }
            } catch (exception: IOException) {
                handler.post { failConnection("Błąd zapisu RFCOMM: ${exception.cleanMessage()}") }
            } finally {
                handler.postDelayed({ finishCurrentOperation() }, WRITE_DELAY_MS)
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun stopBleDiagnostics(message: String?) {
        if (!isScanning) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        listener.onScanningChanged(false)
        if (message != null) listener.onStatus(message)
    }

    private fun failConnection(message: String) {
        handler.removeCallbacks(connectionTimeoutRunnable)
        isConnecting = false
        listener.onConnectingChanged(false)
        operationQueue.clear()
        currentOperation = null
        listener.onStatus(message)
        disconnectSocket(notify = false)
    }

    private fun disconnectSocket(notify: Boolean) {
        cleanupSocket()
        if (notify) listener.onDisconnected()
    }

    private fun cleanupSocket() {
        runCatching { outputStream?.close() }
        outputStream = null
        runCatching { socket?.close() }
        socket = null
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String? =
        runCatching { name }.getOrNull()

    private fun isLikelyTarget(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalized = name.lowercase()
        return normalized.contains("realme buds air 5 pro") ||
            normalized.contains("buds air 5 pro") ||
            normalized.contains("realme buds")
    }

    private fun isExactTarget(name: String?): Boolean =
        name?.equals("realme Buds Air 5 Pro", ignoreCase = true) == true

    private fun ScanResult.toSummary(): String {
        val record = scanRecord
        val name = record?.deviceName ?: device.safeName() ?: "bez nazwy"
        val services = record?.serviceUuids
            ?.joinToString(",") { parcelUuid -> parcelUuid.uuid.shortUuid() }
            .orEmpty()
        val manufacturerData = record?.manufacturerSpecificData?.let { sparseArray ->
            (0 until sparseArray.size()).joinToString(",") { index ->
                val key = sparseArray.keyAt(index)
                val value = sparseArray.valueAt(index).toHexString()
                "%04X:$value".format(key)
            }
        }.orEmpty()
        val serviceData = record?.serviceData
            ?.entries
            ?.joinToString(",") { entry ->
                "${entry.key.uuid.shortUuid()}:${entry.value.toHexString()}"
            }
            .orEmpty()

        return buildString {
            append(name)
            append(" RSSI=")
            append(rssi)
            append(" ")
            append(device.address)
            if (services.isNotBlank()) append(" services=[$services]")
            if (manufacturerData.isNotBlank()) append(" mfg=[$manufacturerData]")
            if (serviceData.isNotBlank()) append(" data=[$serviceData]")
        }
    }

    private fun UUID.shortUuid(): String {
        val value = toString()
        return when {
            value.endsWith("-0000-1000-8000-00805f9b34fb") -> value.substring(4, 8).uppercase()
            value.endsWith("-d102-11e1-9b23-00025b00a5a5") -> value.substring(0, 8).uppercase()
            else -> value
        }
    }

    private fun Throwable.cleanMessage(): String =
        message ?: javaClass.simpleName

    private fun nextSeq(): Int = seqNum++ and 0xff

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 12_000L
        private const val BLE_DIAGNOSTICS_MS = 15_000L
        private const val WRITE_DELAY_MS = 150L
    }
}
