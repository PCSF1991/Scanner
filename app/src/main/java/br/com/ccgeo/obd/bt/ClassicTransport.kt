package br.com.ccgeo.obd.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Bluetooth clássico (SPP / RFCOMM) — a maioria dos ELM327 v1.5/v2.1 "OBDII". */
@SuppressLint("MissingPermission")
class ClassicTransport(
    private val adapter: BluetoothAdapter?,
    private val device: BluetoothDevice,
) : ObdTransport {

    override val label = "Bluetooth clássico (SPP)"
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)

    private var socket: BluetoothSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readerJob: Job? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            try { adapter?.cancelDiscovery() } catch (_: Exception) {}

            val attempts: List<() -> BluetoothSocket> = listOf(
                { device.createRfcommSocketToServiceRecord(SPP_UUID) },
                { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
                {
                    // Fallback para clones que não anunciam SDP corretamente (canal 1).
                    device.javaClass
                        .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        .invoke(device, 1) as BluetoothSocket
                },
            )

            var lastError: Exception? = null
            for (make in attempts) {
                var s: BluetoothSocket? = null
                try {
                    s = make()
                    s.connect()
                    socket = s
                    break
                } catch (e: Exception) {
                    lastError = e
                    try { s?.close() } catch (_: Exception) {}
                }
            }

            val s = socket ?: throw IOException(
                "Não foi possível abrir o canal SPP: ${lastError?.message ?: "erro desconhecido"}",
                lastError
            )

            readerJob = scope.launch {
                val buf = ByteArray(1024)
                val input = s.inputStream
                try {
                    while (isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n > 0) incoming.trySend(buf.copyOf(n))
                    }
                } catch (_: IOException) {
                } finally {
                    incoming.close()
                }
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        withContext(Dispatchers.IO) {
            val out = socket?.outputStream ?: throw IOException("Não conectado")
            out.write(data)
            out.flush()
        }
    }

    override fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        readerJob?.cancel()
        scope.cancel()
        incoming.close()
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
