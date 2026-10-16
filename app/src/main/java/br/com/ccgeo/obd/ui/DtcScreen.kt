package br.com.ccgeo.obd.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.ccgeo.obd.Conn
import br.com.ccgeo.obd.DtcItem
import br.com.ccgeo.obd.UiState

@Composable
fun DtcScreen(st: UiState, onRead: () -> Unit, onClear: () -> Unit) {
    if (st.conn != Conn.CONNECTED) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Conecte o scanner para ler os códigos de falha.", textAlign = TextAlign.Center, color = Muted)
        }
        return
    }
    var confirm by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            val mil = st.milOn
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = if (mil == true) Amber else Muted, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when (mil) { true -> "Luz de injeção ACESA"; false -> "Luz de injeção apagada"; null -> "Status da luz desconhecido" },
                            fontWeight = FontWeight.Bold,
                            color = if (mil == true) Amber else MaterialTheme.colorScheme.onSurface,
                        )
                        st.dtcCount?.let { Text("$it código(s) confirmado(s) segundo a ECU", fontSize = 13.sp, color = Muted) }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onRead, enabled = !st.dtcBusy) { Text("Ler códigos") }
                OutlinedButton(onClick = { confirm = true }, enabled = !st.dtcBusy) {
                    Icon(Icons.Filled.DeleteForever, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text("Apagar")
                }
                if (st.dtcBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        st.dtcMsg?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.secondary) } }

        section("Armazenados (confirmados)", st.stored, st.dtcRead)
        section("Pendentes", st.pending, st.dtcRead)
        if (st.permanent.isNotEmpty()) section("Permanentes", st.permanent, st.dtcRead)

        item {
            Text(
                "Apagar os códigos não conserta o defeito: se a causa continuar, eles voltam. " +
                    "Códigos permanentes só somem depois que a própria ECU confirma o reparo.",
                fontSize = 12.sp, color = Muted,
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apagar códigos de falha?") },
            text = {
                Text("Isso apaga os códigos, os dados de congelamento (freeze frame) e zera os monitores de emissão. " +
                    "Faça com o motor desligado e a ignição ligada.")
            },
            confirmButton = { TextButton(onClick = { confirm = false; onClear() }) { Text("Apagar", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, list: List<DtcItem>, read: Boolean) {
    if (!read) return
    item { Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
    if (list.isEmpty()) {
        item { Text("Nenhum", color = Muted, fontSize = 14.sp) }
    } else {
        items(list, key = { title + it.code }) { DtcRow(it) }
    }
}

@Composable
private fun DtcRow(d: DtcItem) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(d.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Amber)
            Spacer(Modifier.width(12.dp))
            Text(d.desc, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val q = Uri.encode("${d.code} código de falha OBD2")
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")))
            }) { Icon(Icons.Filled.Search, "Pesquisar") }
        }
    }
}
