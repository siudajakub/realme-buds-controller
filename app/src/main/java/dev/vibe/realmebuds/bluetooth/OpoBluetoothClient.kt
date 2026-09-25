package dev.vibe.realmebuds.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.vibe.realmebuds.bluetooth.TouchAction
import dev.vibe.realmebuds.bluetooth.TouchSide
import dev.vibe.realmebuds.bluetooth.TouchType
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

class OpoBluetoothClient(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(message: String)
        fun onConnectingChanged(connecting: Boolean)
        fun onConnectionDiagnostics(
            bondedDeviceFound: Boolean,
            opoUuidFound: Boolean,
            transport: String,
        )
        fun onConnected(deviceName: String)
        fun onDisconnected()
        fun onPacketReceived(source: String, data: ByteArray)
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
    private var isConnecting = false

    private val connectionTimeoutRunnable = Runnable {
        if (isConnecting) {
            failConnection("Timeout połączenia RFCOMM")
        }
    }

    fun hasRuntimePermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }

    @SuppressLint("MissingPermission")
    fun connectToBondedHeadphones(skipInit: Boolean = false) {
        if (!hasRuntimePermissions()) {
            listener.onStatus("Brakuje uprawnień Bluetooth")
            return
        }

        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            listener.onStatus("Bluetooth jest wyłączony")
            return
        }

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
            runRfcommConnection(target, skipInit)
        }.also { thread ->
            thread.name = "RealmeBudsRfcomm"
            thread.start()
        }
    }

    fun disconnect() {
        disposed.set(true)
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
    private fun runRfcommConnection(device: BluetoothDevice, skipInit: Boolean = false) {
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
                if (!skipInit) queueInitialization()
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

    private fun Throwable.cleanMessage(): String =
        message ?: javaClass.simpleName

    private fun nextSeq(): Int = seqNum++ and 0xff

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 12_000L
        private const val WRITE_DELAY_MS = 150L
    }
}
