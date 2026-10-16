package br.com.ccgeo.obd.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

/** Bluetooth Low Energy — versões "Bluetooth 4.0 / iOS" do ELM327. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
) : ObdTransport {

    override val label = "Bluetooth LE"
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var chunkSize = 20
    private val ready = CompletableDeferred<Unit>()
    private val writeAck = Channel<Int>(Channel.CONFLATED)

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (!g.requestMtu(185)) g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                if (!ready.isCompleted) {
                    ready.completeExceptionally(IOException("Conexão BLE recusada (status $status)"))
                }
                incoming.close()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) chunkSize = (mtu - 3).coerceIn(20, 180)
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                ready.completeExceptionally(IOException("Falha ao descobrir serviços BLE"))
                return
            }
            val pair = pickCharacteristics(g.services)
            if (pair == null) {
                ready.completeExceptionally(IOException("Serviço serial do ELM327 não encontrado neste dispositivo"))
                return
            }
            notifyChar = pair.first
            writeChar = pair.second
            val n = pair.first
            g.setCharacteristicNotification(n, true)
            val cccd = n.getDescriptor(CCCD)
            if (cccd == null) {
                ready.complete(Unit)
                return
            }
            val value = if (n.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
            } else {
                cccd.value = value
                g.writeDescriptor(cccd)
            }
            if (!ok) ready.complete(Unit) // segue mesmo assim; alguns clones já notificam
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (!ready.isCompleted) ready.complete(Unit)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeAck.trySend(status)
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            incoming.trySend(value.copyOf())
        }

        // Android 12 e anteriores
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                c.value?.let { incoming.trySend(it.copyOf()) }
            }
        }
    }

    override suspend fun connect() {
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw IOException("connectGatt retornou nulo")
        try {
            withTimeout(15_000) { ready.await() }
        } catch (e: Exception) {
            close()
            throw if (e is IOException) e else IOException("Tempo esgotado na conexão BLE", e)
        }
    }

    override suspend fun write(data: ByteArray) {
        val g = gatt ?: throw IOException("Não conectado")
        val c = writeChar ?: throw IOException("Característica de escrita ausente")
        val noResp = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 &&
            c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0
        val type = if (noResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            val chunk = data.copyOfRange(offset, end)
            while (writeAck.tryReceive().isSuccess) { /* descarta ACK antigo */ }
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, chunk, type) == BluetoothGatt.GATT_SUCCESS
            } else {
                c.writeType = type
                c.value = chunk
                g.writeCharacteristic(c)
            }
            if (!started) throw IOException("Falha ao escrever via BLE")
            withTimeoutOrNull(1500) { writeAck.receive() }
            offset = end
        }
    }

    override fun close() {
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        incoming.close()
    }

    /** Escolhe (notify, write). Primeiro os UUIDs conhecidos de clones ELM327, depois busca genérica. */
    private fun pickCharacteristics(services: List<BluetoothGattService>): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        for ((svc, notifyId, writeId) in KNOWN) {
            val s = services.firstOrNull { it.uuid == svc } ?: continue
            val n = s.getCharacteristic(notifyId) ?: continue
            val w = s.getCharacteristic(writeId) ?: continue
            return n to w
        }
        val candidates = services.filterNot { it.uuid.toString().lowercase().let { u -> STANDARD.any { p -> u.startsWith(p) } } }
        for (s in candidates) {
            val n = s.characteristics.firstOrNull { it.canNotify() }
            val w = s.characteristics.firstOrNull { it.canWrite() }
            if (n != null && w != null) return n to w
        }
        return null
    }

    private fun BluetoothGattCharacteristic.canNotify() =
        properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

    private fun BluetoothGattCharacteristic.canWrite() =
        properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

    companion object {
        private fun u16(s: String): UUID = UUID.fromString("0000$s-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = u16("2902")

        private val KNOWN: List<Triple<UUID, UUID, UUID>> = listOf(
            Triple(u16("fff0"), u16("fff1"), u16("fff2")),
            Triple(u16("ffe0"), u16("ffe1"), u16("ffe1")),
            Triple(u16("18f0"), u16("2af0"), u16("2af1")),
            Triple(
                UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"),
                UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"),
                UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"),
            ),
        )

        // Serviços genéricos (GAP, GATT, Device Info, Battery) não são o canal serial.
        private val STANDARD = listOf("00001800", "00001801", "0000180a", "0000180f")
    }
}
