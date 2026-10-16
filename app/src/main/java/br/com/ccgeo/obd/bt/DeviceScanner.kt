package br.com.ccgeo.obd.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class FoundDevice(
    val address: String,
    val name: String,
    val classic: Boolean,
    val ble: Boolean,
    val bonded: Boolean,
    val rssi: Int? = null,
) {
    /** Nomes típicos de adaptadores ELM327 (KUULAA aparece como "OBDII" / "OBD2" / "V-LINK"). */
    val looksLikeObd: Boolean
        get() {
            val n = name.uppercase()
            return OBD_HINTS.any { n.contains(it) }
        }

    companion object {
        private val OBD_HINTS = listOf("OBD", "ELM", "V-LINK", "VLINK", "KUULAA", "VEEPEAK", "KONNWEI", "ICAR", "SCAN", "CAR")
    }
}

/** Busca dispositivos pareados + descoberta clássica + varredura BLE em paralelo. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class DeviceScanner(private val context: Context, private val adapter: BluetoothAdapter?) {

    private val _devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices: StateFlow<List<FoundDevice>> = _devices

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    dev?.let { add(it, ble = false, rssi = if (rssi == Short.MIN_VALUE.toInt()) null else rssi) }
                }
            }
        }
    }

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            add(result.device, ble = true, rssi = result.rssi, advName = result.scanRecord?.deviceName)
        }
    }

    fun loadBonded() {
        val bonded = try { adapter?.bondedDevices.orEmpty() } catch (_: SecurityException) { emptySet() }
        for (d in bonded) {
            val type = d.type
            add(
                d,
                ble = type == BluetoothDevice.DEVICE_TYPE_LE,
                rssi = null,
                classicHint = type != BluetoothDevice.DEVICE_TYPE_LE,
                bleHint = type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL,
            )
        }
    }

    fun start(durationMs: Long = 12_000) {
        val a = adapter ?: return
        if (!a.isEnabled) return
        stop()
        _devices.value = emptyList()
        loadBonded()
        _scanning.value = true

        try {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
            a.startDiscovery()
        } catch (_: Exception) {}

        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            a.bluetoothLeScanner?.startScan(null, settings, leCallback)
        } catch (_: Exception) {}

        handler.postDelayed({ stop() }, durationMs)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        try { adapter?.cancelDiscovery() } catch (_: Exception) {}
        try { adapter?.bluetoothLeScanner?.stopScan(leCallback) } catch (_: Exception) {}
        if (receiverRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        _scanning.value = false
    }

    private fun add(
        d: BluetoothDevice,
        ble: Boolean,
        rssi: Int?,
        advName: String? = null,
        classicHint: Boolean = !ble,
        bleHint: Boolean = ble,
    ) {
        val name = (try { d.name } catch (_: SecurityException) { null }) ?: advName
        if (name.isNullOrBlank()) return // ignora anúncios sem nome
        val bonded = try { d.bondState == BluetoothDevice.BOND_BONDED } catch (_: SecurityException) { false }
        _devices.update { list ->
            val old = list.firstOrNull { it.address == d.address }
            val merged = FoundDevice(
                address = d.address,
                name = name,
                classic = (old?.classic ?: false) || classicHint,
                ble = (old?.ble ?: false) || bleHint,
                bonded = bonded || (old?.bonded ?: false),
                rssi = rssi ?: old?.rssi,
            )
            (list.filterNot { it.address == d.address } + merged)
                .sortedWith(compareByDescending<FoundDevice> { it.looksLikeObd }
                    .thenByDescending { it.bonded }
                    .thenByDescending { it.rssi ?: -200 })
        }
    }
}
