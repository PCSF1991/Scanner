package br.com.ccgeo.obd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.ccgeo.obd.Conn
import br.com.ccgeo.obd.UiState
import br.com.ccgeo.obd.ai.ChatMsg
import br.com.ccgeo.obd.ai.ClaudeModels
import br.com.ccgeo.obd.ai.Finding
import br.com.ccgeo.obd.ai.Report
import br.com.ccgeo.obd.ai.Severity

@Composable
fun AiScreen(
    st: UiState,
    onOpen: () -> Unit,
    onAnalyze: () -> Unit,
    onAsk: (String) -> Unit,
    onReset: () -> Unit,
    onSaveSettings: (key: String?, model: String, vehicle: String) -> Unit,
) {
    LaunchedEffect(Unit) { onOpen() }
    var showSettings by remember { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(st.aiChat.size, st.aiBusy) {
        if (st.aiChat.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }

    if (st.conn != Conn.CONNECTED && st.report == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Conecte o scanner para a IA analisar os parâmetros do motor.", textAlign = TextAlign.Center, color = Muted)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScoreCard(st.report, st.aiBusy, st.aiKeySet, onAnalyze, onSettings = { showSettings = true })
            }
            st.report?.findings?.let { list ->
                item { SectionTitle("Diagnóstico automático (no aparelho)") }
                items(list) { FindingCard(it) }
            }

            if (st.aiChat.isNotEmpty() || st.aiBusy || st.aiError != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Análise da IA (Claude)", Modifier.weight(1f))
                        if (st.aiChat.isNotEmpty() && !st.aiBusy) {
                            TextButton(onClick = onReset) { Text("Nova conversa") }
                        }
                    }
                }
                items(st.aiChat) { ChatBubble(it) }
                if (st.aiBusy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("A IA está analisando os dados…", color = Muted)
                        }
                    }
                }
                st.aiError?.let { e -> item { Text(e, color = Danger, fontSize = 14.sp) } }
            }
        }

        if (st.aiChat.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Pergunte sobre os dados…") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() && !st.aiBusy) { onAsk(input.trim()); input = "" }
                    }),
                )
                IconButton(
                    onClick = { if (input.isNotBlank()) { onAsk(input.trim()); input = "" } },
                    enabled = !st.aiBusy && input.isNotBlank(),
                ) { Icon(Icons.Filled.Send, "Enviar") }
            }
        }
    }

    if (showSettings) {
        AiSettingsDialog(st, onDismiss = { showSettings = false }) { key, model, vehicle ->
            onSaveSettings(key, model, vehicle)
            showSettings = false
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontWeight = FontWeight.Bold, color = Amber, modifier = modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun ScoreCard(r: Report?, busy: Boolean, keySet: Boolean, onAnalyze: () -> Unit, onSettings: () -> Unit) {
    val score = r?.score ?: 0
    val color = when {
        r == null || r.samples == 0 -> Muted
        score >= 80 -> Ok
        score >= 50 -> Amber
        else -> Danger
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 9.dp.toPx()
                        val d = size.minDimension - stroke
                        val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                        drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                        drawArc(color, -90f, 360f * score / 100f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Text(if (r == null || r.samples == 0) "—" else "$score", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r?.headline ?: "Aguardando dados", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (r != null && r.samples > 0) {
                        Text(
                            "${r.samples} leituras · ${r.seconds} s · motor ${if (r.warm) "quente" else "frio"}",
                            fontSize = 12.sp, color = Muted,
                        )
                    }
                    Text("Atualiza sozinho enquanto esta aba está aberta.", fontSize = 12.sp, color = Muted)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Configurar IA") }
            }
            Button(onClick = if (keySet) onAnalyze else onSettings, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (keySet) "Analisar com IA (Claude)" else "Configurar IA (Claude)")
            }
            Text(
                "Dica: deixe o motor esquentar e grave alguns segundos em marcha lenta e alguns a 2.500 rpm antes de analisar.",
                fontSize = 12.sp, color = Muted,
            )
        }
    }
}

@Composable
private fun FindingCard(f: Finding) {
    val (icon, color) = when (f.severity) {
        Severity.CRITICO -> Icons.Filled.Error to Danger
        Severity.ATENCAO -> Icons.Filled.Warning to Amber
        Severity.INFO -> Icons.Filled.Info to Teal
        Severity.OK -> Icons.Filled.CheckCircle to Ok
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(f.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(f.detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                f.action?.let { Text("→ $it", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = color) }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMsg) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .fillMaxWidth(if (mine) 0.85f else 1f)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(14.dp),
                )
                .padding(12.dp)
        ) {
            if (mine) {
                Text(m.display, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                SelectionContainer { Text(markdown(m.display), fontSize = 14.sp, lineHeight = 20.sp) }
            }
        }
    }
}

/** Markdown mínimo: **negrito**, títulos (#) e listas. */
private fun markdown(src: String): AnnotatedString = buildAnnotatedString {
    val lines = src.lines()
    lines.forEachIndexed { idx, raw ->
        var line = raw
        var heading = false
        if (line.trimStart().startsWith("#")) { line = line.trimStart().trimStart('#').trim(); heading = true }
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            val indent = line.length - line.trimStart().length
            line = " ".repeat(indent) + "• " + line.trimStart().substring(2)
        }
        if (heading) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Amber)) { appendBold(line) }
        else appendBold(line)
        if (idx < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendBold(line: String) {
    val parts = line.split("**")
    parts.forEachIndexed { i, p ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p) } else append(p)
    }
}

@Composable
private fun AiSettingsDialog(st: UiState, onDismiss: () -> Unit, onSave: (String?, String, String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(st.aiModel) }
    var vehicle by remember { mutableStateOf(st.vehicle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar IA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vehicle, onValueChange = { vehicle = it },
                    label = { Text("Veículo") },
                    placeholder = { Text("Ex.: Fiat Argo 1.0 Firefly 2021 flex") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text(if (st.aiKeySet) "Chave da API (salva — deixe vazio p/ manter)" else "Chave da API da Anthropic") },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Crie a chave em console.anthropic.com › API Keys. O uso é cobrado na sua conta da Anthropic.", fontSize = 12.sp, color = Muted)
                Text("Modelo", fontWeight = FontWeight.SemiBold)
                ClaudeModels.ALL.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { model = m.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model == m.id, onClick = { model = m.id })
                        Text(m.label, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key.takeIf { it.isNotBlank() }, model, vehicle) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

