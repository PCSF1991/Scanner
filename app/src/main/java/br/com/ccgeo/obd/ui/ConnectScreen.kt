package br.com.ccgeo.obd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.ccgeo.obd.Conn
import br.com.ccgeo.obd.UiState
import br.com.ccgeo.obd.bt.FoundDevice

@Composable
fun ConnectScreen(
    st: UiState,
    devices: List<FoundDevice>,
    scanning: Boolean,
    permsOk: Boolean,
    btOn: Boolean,
    onRequestPerms: () -> Unit,
    onEnableBt: () -> Unit,
    onOpenBtSettings: () -> Unit,
    onScan: () -> Unit,
    onConnect: (FoundDevice) -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(st, onReconnect, onDisconnect) }

        if (!permsOk) {
            item {
                InfoCard(
                    "Permissão de Bluetooth necessária",
                    "O app precisa de permissão para buscar e conectar ao scanner.",
                    "Permitir", onRequestPerms,
                )
            }
        } else if (!btOn) {
            item { InfoCard("Bluetooth desligado", "Ative o Bluetooth para encontrar o scanner.", "Ativar", onEnableBt) }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onScan, enabled = !scanning && st.conn != Conn.CONNECTING) {
                        Icon(Icons.Filled.BluetoothSearching, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (scanning) "Buscando…" else "Buscar dispositivos")
                    }
                    OutlinedButton(onClick = onOpenBtSettings) {
                        Icon(Icons.Filled.Settings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Parear")
                    }
                    if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            if (devices.isEmpty()) {
                item {
                    Text(
                        if (scanning) "Procurando…" else "Toque em \"Buscar dispositivos\".",
                        color = Muted, fontSize = 14.sp,
                    )
                }
            }
            items(devices, key = { it.address }) { d -> DeviceRow(d, enabled = st.conn != Conn.CONNECTING) { onConnect(d) } }
        }

        item { HelpCard() }
    }
}

@Composable
private fun StatusCard(st: UiState, onReconnect: () -> Unit, onDisconnect: () -> Unit) {
    val (color, title) = when (st.conn) {
        Conn.CONNECTED -> Ok to "Conectado"
        Conn.CONNECTING -> Amber to "Conectando"
        Conn.ERROR -> Danger to "Falha"
        Conn.OFF -> Muted to "Desconectado"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bluetooth, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
                if (st.conn == Conn.CONNECTING) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            st.deviceName?.let { Text("$it${st.link?.let { l -> " · $l" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (st.status.isNotBlank() && st.status != title) Text(st.status, fontSize = 14.sp)

            when (st.conn) {
                Conn.CONNECTED, Conn.CONNECTING -> OutlinedButton(onClick = onDisconnect) {
                    Icon(Icons.Filled.LinkOff, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (st.conn == Conn.CONNECTING) "Cancelar" else "Desconectar")
                }
                else -> st.lastDevice?.let { d ->
                    Button(onClick = onReconnect) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Reconectar ${d.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: FoundDevice, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        border = if (d.looksLikeObd) BorderStroke(1.dp, Amber) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(d.address, color = Muted, fontSize = 12.sp)
                val tags = buildList {
                    if (d.looksLikeObd) add("provável scanner")
                    if (d.classic) add("clássico")
                    if (d.ble) add("BLE")
                    if (d.bonded) add("pareado")
                }.joinToString(" · ")
                Text(tags, color = if (d.looksLikeObd) Amber else Muted, fontSize = 12.sp)
            }
            d.rssi?.let { Text("$it dBm", color = Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun HelpCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Como conectar o KUULAA", fontWeight = FontWeight.Bold)
            Text("1. Encaixe o scanner na tomada OBD-II (sob o painel, lado do motorista). A luz POWER acende.", fontSize = 14.sp)
            Text("2. Ligue a ignição (ou o motor).", fontSize = 14.sp)
            Text("3. Versão Bluetooth clássico: pareie em \"Parear\" — nome costuma ser OBDII/OBD2, senha 1234 ou 0000. Depois toque nele aqui.", fontSize = 14.sp)
            Text("4. Versão BLE (Bluetooth 4.0): não precisa parear, basta tocar no dispositivo encontrado na busca.", fontSize = 14.sp)
            Text("Luzes: LINK = Bluetooth conectado · OBD = comunicação com a ECU · POWER = alimentado.", fontSize = 13.sp, color = Muted)
        }
    }
}
