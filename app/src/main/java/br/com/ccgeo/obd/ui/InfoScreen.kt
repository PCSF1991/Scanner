package br.com.ccgeo.obd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.ccgeo.obd.Conn
import br.com.ccgeo.obd.UiState
import java.util.Locale

@Composable
fun InfoScreen(st: UiState, onReadVin: () -> Unit) {
    if (st.conn != Conn.CONNECTED) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Conecte o scanner para ver as informações do veículo.", textAlign = TextAlign.Center, color = Muted)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Veículo", fontWeight = FontWeight.Bold, color = Amber)
                    InfoRow("Chassi (VIN)", st.vin ?: "—", mono = true)
                    OutlinedButton(onClick = onReadVin) { Text("Ler VIN novamente") }
                    InfoRow("Protocolo", st.info?.protocol ?: "—")
                    InfoRow("Rede CAN", if (st.info?.isCan == true) "Sim" else "Não")
                    InfoRow(
                        "Bateria (ATRV)",
                        st.voltage?.let { String.format(Locale.US, "%.2f V", it).replace('.', ',') } ?: "—",
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Adaptador", fontWeight = FontWeight.Bold, color = Amber)
                    InfoRow("Dispositivo", st.deviceName ?: "—")
                    InfoRow("Conexão", st.link ?: "—")
                    InfoRow("Firmware", st.info?.version ?: "—")
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sensores disponíveis (${st.supported.size})", fontWeight = FontWeight.Bold, color = Amber)
                    st.supported.forEach { Text("• ${it.name} (${it.code})", fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
        SelectionContainer(Modifier.weight(0.6f)) {
            Text(
                value, fontSize = 15.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
