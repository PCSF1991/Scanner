package br.com.ccgeo.obd

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.ccgeo.obd.bt.BleTransport
import br.com.ccgeo.obd.bt.ClassicTransport
import br.com.ccgeo.obd.bt.DeviceScanner
import br.com.ccgeo.obd.bt.FoundDevice
import br.com.ccgeo.obd.bt.ObdTransport
import br.com.ccgeo.obd.elm.DtcDb
import br.com.ccgeo.obd.elm.Elm327
import br.com.ccgeo.obd.elm.ElmInfo
import br.com.ccgeo.obd.elm.PidDef
import br.com.ccgeo.obd.elm.Pids
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Conn { OFF, CONNECTING, CONNECTED, ERROR }

data class DtcItem(val code: String, val desc: String)

data class UiState(
    val conn: Conn = Conn.OFF,
    val status: String = "Desconectado",
    val deviceName: String? = null,
    val link: String? = null,
    val info: ElmInfo? = null,
    val voltage: Double? = null,
    val vin: String? = null,
    val supported: List<PidDef> = emptyList(),
    val values: Map<Int, Double> = emptyMap(),
    val readsPerSec: Double = 0.0,
    val milOn: Boolean? = null,
    val dtcCount: Int? = null,
    val stored: List<DtcItem> = emptyList(),
    val pending: List<DtcItem> = emptyList(),
    val permanent: List<DtcItem> = emptyList(),
    val dtcBusy: Boolean = false,
    val dtcRead: Boolean = false,
    val dtcMsg: String? = null,
    val recording: Boolean = false,
    val recordRows: Int = 0,
    val terminal: List<String> = emptyList(),
    val lastDevice: FoundDevice? = null,
)

@SuppressLint("MissingPermission")
class ObdViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication<Application>()
    val adapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    val scanner = DeviceScanner(app, adapter)

    private val prefs = app.getSharedPreferences("obd", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState(lastDevice = loadLastDevice()))
    val state: StateFlow<UiState> = _state

    private var transport: ObdTransport? = null
    private var elm: Elm327? = null
    private var connectJob: Job? = null
    private var pollJob: Job? = null
    private var pollWanted = false

    private val recordRows = ArrayList<String>()
    private var recordHeader: List<PidDef> = emptyList()

    // ---------- Conexão ----------

    fun startScan() = scanner.start()
    fun stopScan() = scanner.stop()

    fun reconnectLast() {
        _state.value.lastDevice?.let { connect(it) }
    }

    fun connect(d: FoundDevice) {
        scanner.stop()
        connectJob?.cancel()
        closeLink()
        connectJob = viewModelScope.launch {
            _state.update {
                it.copy(conn = Conn.CONNECTING, status = "Conectando a ${d.name}…", deviceName = d.name,
                    info = null, supported = emptyList(), values = emptyMap(), vin = null,
                    stored = emptyList(), pending = emptyList(), permanent = emptyList(), dtcRead = false, dtcMsg = null)
            }
            val a = adapter
            if (a == null) { fail("Este aparelho não tem Bluetooth"); return@launch }
            val dev = a.getRemoteDevice(d.address)

            val order = buildList<ObdTransport> {
                if (d.classic || !d.ble) add(ClassicTransport(a, dev))
                if (d.ble) add(BleTransport(ctx, dev))
            }
            var lastErr: Exception? = null
            for (t in order) {
                try {
                    status("Abrindo ${t.label}…")
                    t.connect()
                    transport = t
                    break
                } catch (e: Exception) {
                    if (e is CancellationException) { t.close(); throw e }
                    lastErr = e
                    t.close()
                }
            }
            val t = transport
            if (t == null) {
                fail("Falha na conexão: ${lastErr?.message ?: "desconhecida"}. " +
                    "Verifique se o scanner está na tomada OBD e com a luz POWER acesa.")
                return@launch
            }
            _state.update { it.copy(link = t.label) }

            val e = Elm327(t) { line -> appendTerminal(line) }
            try {
                val info = e.initialize { s -> status(s) }
                status("Lendo PIDs suportados…")
                val sup = e.supportedPids()
                val defs = Pids.ALL.filter { it.pid in sup }
                val volt = e.voltage()
                val mon = e.monitorStatus()
                elm = e
                saveLastDevice(d)
                _state.update {
                    it.copy(
                        conn = Conn.CONNECTED,
                        status = "Conectado",
                        info = info,
                        supported = defs,
                        voltage = volt,
                        milOn = mon?.first,
                        dtcCount = mon?.second,
                        lastDevice = d,
                    )
                }
                if (pollWanted) startPolling()
                // VIN em segundo plano (nem todo carro responde)
                elm?.vin()?.let { v -> _state.update { it.copy(vin = v) } }
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                fail(ex.message ?: "Erro na inicialização")
                closeLink(keepState = true)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        closeLink()
        _state.update { it.copy(conn = Conn.OFF, status = "Desconectado", values = emptyMap(), readsPerSec = 0.0) }
    }

    private fun closeLink(keepState: Boolean = false) {
        pollJob?.cancel(); pollJob = null
        transport?.close(); transport = null
        elm = null
        if (!keepState && _state.value.recording) stopRecording()
    }

    private fun onLinkLost(msg: String) {
        closeLink(keepState = true)
        _state.update { it.copy(conn = Conn.ERROR, status = msg, readsPerSec = 0.0) }
    }

    private fun status(s: String) = _state.update { it.copy(status = s) }
    private fun fail(s: String) = _state.update { it.copy(conn = Conn.ERROR, status = s) }

    // ---------- Painel em tempo real ----------

    /** Chamado pela tela: só faz leitura contínua quando o Painel está visível. */
    fun setPolling(active: Boolean) {
        pollWanted = active
        if (active) startPolling() else { pollJob?.cancel(); pollJob = null }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        val e = elm ?: return
        pollJob = viewModelScope.launch {
            val fails = HashMap<Int, Int>()
            var active = _state.value.supported
            var slowIdx = 0
            var cycle = 0
            var reads = 0
            var t0 = System.currentTimeMillis()
            try {
                while (isActive) {
                    val fast = active.filter { it.fast }
                    val slow = active.filterNot { it.fast }
                    val batch = buildList<PidDef> {
                        addAll(fast)
                        if (slow.isNotEmpty()) {
                            repeat(minOf(2, slow.size)) { add(slow[slowIdx++ % slow.size]) }
                        }
                    }.distinctBy { it.pid }

                    val updates = HashMap<Int, Double>()
                    for (p in batch) {
                        val v = e.readPid(p)
                        reads++
                        if (v != null) { updates[p.pid] = v; fails[p.pid] = 0 }
                        else {
                            val n = (fails[p.pid] ?: 0) + 1
                            fails[p.pid] = n
                            if (n >= 4) active = active.filterNot { it.pid == p.pid } // PID não responde
                        }
                    }
                    val volt = if (cycle % 8 == 0) e.voltage() else null
                    val now = System.currentTimeMillis()
                    val rate = if (now - t0 >= 2000) {
                        val r = reads * 1000.0 / (now - t0); reads = 0; t0 = now; r
                    } else null

                    _state.update { s ->
                        s.copy(
                            values = s.values + updates,
                            voltage = volt ?: s.voltage,
                            readsPerSec = rate ?: s.readsPerSec,
                        )
                    }
                    if (_state.value.recording) appendRecordRow()
                    cycle++
                    if (active.isEmpty()) kotlinx.coroutines.delay(500)
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                onLinkLost("Conexão perdida: ${ex.message}")
            }
        }
    }

    // ---------- Códigos de falha ----------

    fun readDtcs() {
        val e = elm ?: return
        viewModelScope.launch {
            _state.update { it.copy(dtcBusy = true, dtcMsg = null) }
            try {
                val mon = e.monitorStatus()
                val stored = e.storedDtcs().map { DtcItem(it, DtcDb.describe(it)) }
                val pending = e.pendingDtcs().map { DtcItem(it, DtcDb.describe(it)) }
                val perm = if (e.isCan) e.permanentDtcs().map { DtcItem(it, DtcDb.describe(it)) } else emptyList()
                _state.update {
                    it.copy(
                        dtcBusy = false, dtcRead = true,
                        stored = stored, pending = pending, permanent = perm,
                        milOn = mon?.first ?: it.milOn, dtcCount = mon?.second ?: it.dtcCount,
                        dtcMsg = if (stored.isEmpty() && pending.isEmpty() && perm.isEmpty()) "Nenhum código de falha encontrado" else null,
                    )
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                _state.update { it.copy(dtcBusy = false) }
                onLinkLost("Conexão perdida: ${ex.message}")
            }
        }
    }

    fun clearDtcs() {
        val e = elm ?: return
        viewModelScope.launch {
            _state.update { it.copy(dtcBusy = true, dtcMsg = null) }
            try {
                val ok = e.clearDtcs()
                _state.update {
                    it.copy(dtcBusy = false, dtcMsg = if (ok) "Códigos apagados. A luz de injeção deve apagar." else "A ECU recusou o comando (tente com o motor desligado e ignição ligada).")
                }
                if (ok) readDtcs()
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                _state.update { it.copy(dtcBusy = false) }
                onLinkLost("Conexão perdida: ${ex.message}")
            }
        }
    }

    fun readVin() {
        val e = elm ?: return
        viewModelScope.launch {
            try {
                val v = e.vin()
                _state.update { it.copy(vin = v ?: "Não informado pela ECU") }
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                onLinkLost("Conexão perdida: ${ex.message}")
            }
        }
    }

    // ---------- Terminal ----------

    fun sendRaw(cmd: String) {
        val c = cmd.trim().uppercase()
        if (c.isEmpty()) return
        val e = elm
        if (e == null) { appendTerminal("! Não conectado"); return }
        viewModelScope.launch {
            try { e.send(c, 5000) } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                onLinkLost("Conexão perdida: ${ex.message}")
            }
        }
    }

    fun clearTerminal() = _state.update { it.copy(terminal = emptyList()) }

    private fun appendTerminal(line: String) =
        _state.update { it.copy(terminal = (it.terminal + line).takeLast(500)) }

    // ---------- Gravação CSV ----------

    fun toggleRecording() {
        if (_state.value.recording) stopRecording() else {
            recordRows.clear()
            recordHeader = _state.value.supported
            _state.update { it.copy(recording = true, recordRows = 0) }
        }
    }

    private fun stopRecording() = _state.update { it.copy(recording = false) }

    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun appendRecordRow() {
        val s = _state.value
        val row = buildString {
            append(tsFmt.format(Date()))
            for (p in recordHeader) { append(';'); append(p.format(s.values[p.pid])) }
            append(';'); append(s.voltage?.let { String.format(Locale.US, "%.1f", it).replace('.', ',') } ?: "")
        }
        recordRows += row
        _state.update { it.copy(recordRows = recordRows.size) }
    }

    fun hasRecording() = recordRows.isNotEmpty()

    fun suggestedCsvName(): String =
        "obd_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"

    fun exportCsv(uri: Uri, onDone: (String) -> Unit) {
        val header = buildString {
            append("data_hora")
            for (p in recordHeader) append(";${p.name} (${p.unit})")
            append(";Bateria ATRV (V)")
        }
        val rows = ArrayList(recordRows)
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM p/ Excel
                        out.write((header + "\r\n").toByteArray(Charsets.UTF_8))
                        for (r in rows) out.write((r + "\r\n").toByteArray(Charsets.UTF_8))
                    }
                    "CSV salvo (${rows.size} linhas)"
                } catch (e: Exception) {
                    "Erro ao salvar: ${e.message}"
                }
            }
            onDone(msg)
        }
    }

    // ---------- Persistência ----------

    private fun saveLastDevice(d: FoundDevice) {
        prefs.edit()
            .putString("addr", d.address).putString("name", d.name)
            .putBoolean("classic", d.classic).putBoolean("ble", d.ble)
            .apply()
    }

    private fun loadLastDevice(): FoundDevice? {
        val addr = prefs.getString("addr", null) ?: return null
        return FoundDevice(
            address = addr,
            name = prefs.getString("name", null) ?: addr,
            classic = prefs.getBoolean("classic", true),
            ble = prefs.getBoolean("ble", false),
            bonded = true,
        )
    }

    override fun onCleared() {
        scanner.stop()
        closeLink()
        super.onCleared()
    }
}
