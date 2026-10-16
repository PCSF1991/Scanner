package br.com.ccgeo.obd.bt

import kotlinx.coroutines.channels.Channel

/**
 * Canal de bytes com o adaptador ELM327, independente do tipo de Bluetooth.
 * [incoming] é fechado quando a conexão cai.
 */
interface ObdTransport {
    val label: String
    val incoming: Channel<ByteArray>
    suspend fun connect()
    suspend fun write(data: ByteArray)
    fun close()
}
