package br.com.ccgeo.obd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.ccgeo.obd.Conn
import br.com.ccgeo.obd.UiState
import br.com.ccgeo.obd.elm.PidDef
import br.com.ccgeo.obd.elm.Pids
import java.util.Locale

@Composable
fun DashboardScreen(
    st: UiState,
    canExport: Boolean,
    onToggleRecord: () -> Unit,
    onExport: () -> Unit,
) {
    if (st.conn != Conn.CONNECTED) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Conecte o scanner na aba \"Conectar\" para ver os dados do motor em tempo real.",
                textAlign = TextAlign.Center, color = Muted,
            )
        }
        return
    }

    val rpm = Pids.BY_PID[Pids.RPM]?.takeIf { d -> st.supported.any { it.pid == d.pid } }
    val speed = Pids.BY_PID[Pids.SPEED]?.takeIf { d -> st.supported.any { it.pid == d.pid } }
    val others = st.supported.filter { it.pid != Pids.RPM && it.pid != Pids.SPEED }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (rpm != null || speed != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rpm?.let { Gauge(it, st.values[it.pid], Modifier.weight(1f), warnFrom = 6000.0) }
                    speed?.let { Gauge(it, st.values[it.pid], Modifier.weight(1f)) }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    val v = st.voltage
                    Text(
                        "Bateria: " + (v?.let { String.format(Locale.US, "%.1f V", it).replace('.', ',') } ?: "—"),
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            v == null -> Muted
                            v < 11.8 -> Danger
                            v > 14.9 -> Danger
                            else -> Ok
                        },
                    )
                    Text(
                        String.format(Locale.US, "%.1f leituras/s", st.readsPerSec).replace('.', ','),
                        fontSize = 12.sp, color = Muted,
                    )
                }
                if (st.recording) {
                    Button(
                        onClick = onToggleRecord,
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    ) {
                        Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text("${st.recordRows}")
                    }
                } else {
                    OutlinedButton(onClick = onToggleRecord) {
                        Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(16.dp), tint = Danger)
                        Spacer(Modifier.width(4.dp)); Text("Gravar")
                    }
                }
                if (canExport && !st.recording) {
                    OutlinedButton(onClick = onExport) {
                        Icon(Icons.Filled.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("CSV")
                    }
                }
            }
        }

        items(others, key = { it.pid }) { def -> PidTile(def, st.values[def.pid]) }

        if (st.supported.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("O veículo não informou PIDs suportados. Use o Terminal para testar comandos.", color = Muted)
            }
        }
    }
}

@Composable
private fun PidTile(def: PidDef, value: Double?) {
    val warn = when (def.pid) {
        0x05 -> value != null && value >= 105.0   // superaquecimento
        0x42 -> value != null && (value < 11.8 || value > 14.9)
        else -> false
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(def.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    def.format(value),
                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = if (warn) Danger else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Text(def.unit, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}
