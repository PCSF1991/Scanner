package br.com.ccgeo.obd

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.ccgeo.obd.ui.AppTheme
import br.com.ccgeo.obd.ui.ConnectScreen
import br.com.ccgeo.obd.ui.Danger
import br.com.ccgeo.obd.ui.DashboardScreen
import br.com.ccgeo.obd.ui.DtcScreen
import br.com.ccgeo.obd.ui.InfoScreen
import br.com.ccgeo.obd.ui.Muted
import br.com.ccgeo.obd.ui.Ok
import br.com.ccgeo.obd.ui.Amber
import br.com.ccgeo.obd.ui.TerminalScreen

class MainActivity : ComponentActivity() {
    private val vm: ObdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AppTheme { App(vm) } }
    }
}

private fun requiredPerms(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

private fun hasPerms(ctx: Context) = requiredPerms().all {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Conectar", Icons.Filled.Bluetooth),
    Tab("Painel", Icons.Filled.Speed),
    Tab("Falhas", Icons.Filled.Warning),
    Tab("Info", Icons.Filled.Info),
    Tab("Terminal", Icons.Filled.Code),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: ObdViewModel) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsStateWithLifecycle()
    val devices by vm.scanner.devices.collectAsStateWithLifecycle()
    val scanning by vm.scanner.scanning.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var permsOk by remember { mutableStateOf(hasPerms(ctx)) }
    var btOn by remember { mutableStateOf(vm.adapter?.isEnabled == true) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permsOk = hasPerms(ctx)
        btOn = vm.adapter?.isEnabled == true
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permsOk = hasPerms(ctx)
        btOn = vm.adapter?.isEnabled == true
        if (permsOk && btOn) vm.startScan()
    }
    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        btOn = vm.adapter?.isEnabled == true
        if (permsOk && btOn) vm.startScan()
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri) { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(Unit) {
        if (!permsOk) permLauncher.launch(requiredPerms())
        else if (btOn) vm.scanner.loadBonded()
    }

    // Leitura contínua só com o Painel aberto (libera o barramento para as outras telas).
    LaunchedEffect(tab, st.conn) { vm.setPolling(tab == 1 && st.conn == Conn.CONNECTED) }

    // Ao conectar, vai direto para o Painel.
    LaunchedEffect(st.conn) { if (st.conn == Conn.CONNECTED && tab == 0) tab = 1 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("C&C Scanner OBD", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            st.status,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = when (st.conn) {
                                Conn.CONNECTED -> Ok
                                Conn.CONNECTING -> Amber
                                Conn.ERROR -> Danger
                                Conn.OFF -> Muted
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null, Modifier.size(22.dp)) },
                        label = { Text(t.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> ConnectScreen(
                    st = st,
                    devices = devices,
                    scanning = scanning,
                    permsOk = permsOk,
                    btOn = btOn,
                    onRequestPerms = { permLauncher.launch(requiredPerms()) },
                    onEnableBt = {
                        try {
                            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (_: SecurityException) {
                            permLauncher.launch(requiredPerms())
                        }
                    },
                    onOpenBtSettings = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    onScan = { vm.startScan() },
                    onConnect = { vm.connect(it) },
                    onReconnect = { vm.reconnectLast() },
                    onDisconnect = { vm.disconnect() },
                )
                1 -> DashboardScreen(
                    st = st,
                    canExport = st.recordRows > 0,
                    onToggleRecord = { vm.toggleRecording() },
                    onExport = { csvLauncher.launch(vm.suggestedCsvName()) },
                )
                2 -> DtcScreen(st, onRead = { vm.readDtcs() }, onClear = { vm.clearDtcs() })
                3 -> InfoScreen(st, onReadVin = { vm.readVin() })
                4 -> TerminalScreen(
                    lines = st.terminal,
                    connected = st.conn == Conn.CONNECTED,
                    onSend = { vm.sendRaw(it) },
                    onClear = { vm.clearTerminal() },
                )
            }
        }
    }
}
