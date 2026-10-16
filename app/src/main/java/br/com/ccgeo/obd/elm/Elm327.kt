package br.com.ccgeo.obd.elm

import br.com.ccgeo.obd.bt.ObdTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

data class ElmInfo(
    val version: String,
    val protocol: String,
    val protocolNumber: String,
    val isCan: Boolean,
)

/** Protocolo de comandos do ELM327: envia uma linha e lê até o prompt '>'. */
class Elm327(
    private val transport: ObdTransport,
    private val onTraffic: (String) -> Unit,
) {
    private val mutex = Mutex()
    var isCan = true
        private set

    /** Envia um comando e devolve a resposta bruta (sem o '>'). Retorna "TIMEOUT" se o adaptador não responder. */
    suspend fun send(cmd: String, timeoutMs: Long = 2500, log: Boolean = true): String = mutex.withLock {
        while (transport.incoming.tryReceive().isSuccess) { /* descarta lixo pendente */ }
        if (log) onTraffic("> $cmd")
        transport.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
        val resp = withTimeoutOrNull(timeoutMs) { readUntilPrompt() }
        val clean = (resp ?: "TIMEOUT")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cmd, ignoreCase = true) } // remove eco
            .joinToString("\n")
        if (log) onTraffic(clean.ifEmpty { "(vazio)" })
        clean
    }

    private suspend fun readUntilPrompt(): String {
        val sb = StringBuilder()
        while (true) {
            val chunk = transport.incoming.receiveCatching().getOrNull()
                ?: throw IOException("Conexão com o adaptador perdida")
            for (b in chunk) {
                val c = (b.toInt() and 0xFF).toChar()
                if (c == '>') return sb.toString()
                if (c != '\u0000') sb.append(c)
            }
        }
    }

    /** Sequência de inicialização. Lança IOException se o carro/ECU não responder. */
    suspend fun initialize(onStep: (String) -> Unit): ElmInfo {
        onStep("Reiniciando adaptador…")
        var version = send("ATZ", 4000)
        delay(600)
        if (version.contains("TIMEOUT")) {
            // Alguns clones não respondem ao ATZ pela BLE — tenta ATWS / ATI
            version = send("ATI", 2000)
        }
        send("ATE0"); send("ATL0"); send("ATS0"); send("ATH0")
        send("ATAT1")   // temporização adaptativa
        send("ATST64")  // timeout ~400 ms por resposta
        send("ATSP0")   // protocolo automático

        onStep("Procurando protocolo do veículo (ignição ligada)…")
        val probe = send("0100", 12_000)
        val err = ObdParser.errorOf(probe)
        if (err != null) {
            throw IOException(
                when {
                    err.contains("UNABLE") || err.contains("NODATA") || err.contains("NO DATA") ->
                        "A ECU não respondeu. Ligue a ignição (ou o motor) e tente de novo."
                    err.contains("TIMEOUT") -> "O adaptador não respondeu. Verifique se ele está bem encaixado na tomada OBD."
                    else -> "Erro do adaptador: $err"
                }
            )
        }

        val dpn = send("ATDPN").trim().let { if (it.length == 2 && it.startsWith("A")) it.substring(1) else it }
        val dp = send("ATDP").replace("AUTO,", "").trim()
        isCan = dpn.firstOrNull()?.let { it in "6789AB" } ?: true

        return ElmInfo(
            version = version.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "?",
            protocol = dp,
            protocolNumber = dpn,
            isCan = isCan,
        )
    }

    suspend fun supportedPids(): Set<Int> {
        val set = mutableSetOf<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            val raw = send("01" + base.toString(16).uppercase().padStart(2, '0'), 3000, log = false)
            val s = ObdParser.supportedPids(raw, base)
            if (s.isEmpty()) break
            set += s.filter { it % 0x20 != 0 }
            if ((base + 0x20) !in s) break
            base += 0x20
        }
        return set
    }

    suspend fun readPid(def: PidDef): Double? {
        val raw = send(def.code, 1500, log = false)
        return ObdParser.mode01Data(raw, def.pid)?.let { def.decode(it) }
    }

    suspend fun voltage(): Double? = ObdParser.voltage(send("ATRV", 1500, log = false))

    suspend fun vin(): String? = ObdParser.vin(send("0902", 5000))

    suspend fun monitorStatus(): Pair<Boolean, Int>? = ObdParser.monitorStatus(send("0101", 2000))

    suspend fun storedDtcs(): List<String> = ObdParser.dtcs(send("03", 5000), isCan)
    suspend fun pendingDtcs(): List<String> = ObdParser.dtcs(send("07", 5000), isCan)
    suspend fun permanentDtcs(): List<String> = ObdParser.dtcs(send("0A", 5000), isCan)

    /** Modo 04: apaga códigos e desliga a MIL. */
    suspend fun clearDtcs(): Boolean {
        val r = send("04", 5000)
        return r.contains("44") && ObdParser.errorOf(r) == null
    }
}
