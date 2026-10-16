package br.com.ccgeo.obd.ai

import br.com.ccgeo.obd.elm.Pids
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

enum class Severity { OK, INFO, ATENCAO, CRITICO }

data class Finding(
    val severity: Severity,
    val title: String,
    val detail: String,
    val action: String? = null,
)

/** Uma leitura completa do painel num instante. */
data class Sample(val timeMs: Long, val values: Map<Int, Double>, val voltage: Double?)

data class Stats(val min: Double, val mean: Double, val max: Double, val sd: Double, val last: Double, val n: Int)

data class DtcInput(
    val milOn: Boolean?,
    val read: Boolean,
    val stored: List<Pair<String, String>>,
    val pending: List<Pair<String, String>>,
    val permanent: List<Pair<String, String>>,
)

data class Report(
    val score: Int,
    val headline: String,
    val findings: List<Finding>,
    val samples: Int,
    val seconds: Int,
    val idleSamples: Int,
    val warm: Boolean,
)

/**
 * Diagnóstico local por regras (funciona sem internet).
 * Os limites são os usados na prática de oficina para injeção eletrônica a gasolina/flex.
 */
object Diagnostics {

    private const val RPM = 0x0C
    private const val SPEED = 0x0D
    private const val ECT = 0x05
    private const val LOAD = 0x04
    private const val TPS = 0x11
    private const val MAP = 0x0B
    private const val MAF = 0x10
    private const val IAT = 0x0F
    private const val TIMING = 0x0E
    private const val STFT1 = 0x06
    private const val LTFT1 = 0x07
    private const val STFT2 = 0x08
    private const val LTFT2 = 0x09
    private const val FUEL = 0x2F
    private const val ETHANOL = 0x52
    private const val MODULE_V = 0x42
    private const val AMBIENT = 0x46
    private const val BARO = 0x33
    private const val OIL = 0x5C
    private const val RUNTIME = 0x1F
    private const val DIST_MIL = 0x21
    private const val DIST_CLR = 0x31

    fun stats(samples: List<Sample>, pid: Int): Stats? {
        val v = samples.mapNotNull { it.values[pid] }
        return statsOf(v)
    }

    private fun voltageStats(samples: List<Sample>): Stats? =
        statsOf(samples.mapNotNull { it.voltage }) ?: stats(samples, MODULE_V)

    private fun statsOf(v: List<Double>): Stats? {
        if (v.isEmpty()) return null
        val mean = v.average()
        val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
        return Stats(v.min(), mean, v.max(), sd, v.last(), v.size)
    }

    private fun isIdle(s: Sample): Boolean {
        val rpm = s.values[RPM] ?: return false
        val speed = s.values[SPEED] ?: 0.0
        return rpm in 400.0..1500.0 && speed < 2.0
    }

    private fun f(v: Double, d: Int = 0) = String.format(Locale.US, "%.${d}f", v).replace('.', ',')

    fun analyze(samples: List<Sample>, dtc: DtcInput): Report {
        val out = mutableListOf<Finding>()
        val seconds = if (samples.size >= 2) ((samples.last().timeMs - samples.first().timeMs) / 1000).toInt() else 0
        val idle = samples.filter(::isIdle)

        val rpm = stats(samples, RPM)
        val running = (rpm?.last ?: 0.0) > 350.0
        val ect = stats(samples, ECT)
        val warm = (ect?.last ?: 0.0) >= 80.0

        if (samples.isEmpty()) {
            out.add(Finding(Severity.INFO, "Sem leituras ainda", "Aguarde alguns segundos com o scanner conectado para juntar dados."))
        }

        // ---------- Códigos de falha ----------
        if (dtc.milOn == true) {
            out.add(Finding(Severity.CRITICO, "Luz de injeção acesa", "A ECU confirmou defeito com a lâmpada de anomalia (MIL) ligada.", "Veja os códigos abaixo ou na aba Falhas."))
        }
        dtc.stored.forEach { (c, d) -> out.add(Finding(Severity.ATENCAO, "Código $c", d, "Código confirmado pela ECU.")) }
        dtc.permanent.forEach { (c, d) -> out.add(Finding(Severity.ATENCAO, "Código permanente $c", d, "Só sai depois que a ECU confirmar o reparo.")) }
        dtc.pending.forEach { (c, d) -> out.add(Finding(Severity.INFO, "Código pendente $c", d, "Detectado uma vez; vira confirmado se repetir.")) }
        if (!dtc.read) out.add(Finding(Severity.INFO, "Códigos ainda não lidos", "A leitura de códigos é feita automaticamente ao abrir esta aba."))

        stats(samples, DIST_MIL)?.let { if (it.last > 0) out.add(Finding(Severity.ATENCAO, "Rodou ${f(it.last)} km com a luz acesa", "Distância registrada pela ECU com a MIL ligada.")) }
        stats(samples, DIST_CLR)?.let {
            if (it.last < 50) out.add(Finding(
                Severity.INFO, "Códigos apagados há pouco (${f(it.last)} km)",
                "Monitores de emissão provavelmente ainda não estão prontos; defeitos intermitentes podem não ter reaparecido.",
                "Rode alguns dias (cidade e estrada) e leia os códigos de novo.",
            ))
        }

        // ---------- Temperatura ----------
        ect?.let {
            when {
                it.last >= 110 -> out.add(Finding(Severity.CRITICO, "Superaquecimento (${f(it.last)} °C)", "Risco de danificar junta do cabeçote.", "Desligue o motor e verifique nível de água, eletroventilador e bomba d'água."))
                it.last >= 103 -> out.add(Finding(Severity.ATENCAO, "Temperatura alta (${f(it.last)} °C)", "Acima da faixa normal de 85–100 °C.", "Confira se o eletroventilador liga e o nível do reservatório."))
                running && it.last < 80 -> out.add(Finding(Severity.INFO, "Motor ainda frio (${f(it.last)} °C)", "Com o motor frio a marcha lenta e as correções de mistura ficam alteradas.", "Para um diagnóstico melhor, repita acima de 85 °C."))
                else -> out.add(Finding(Severity.OK, "Temperatura normal (${f(it.last)} °C)", "Faixa de trabalho."))
            }
            val runtime = stats(samples, RUNTIME)?.last
            if (running && runtime != null && runtime > 900 && it.max < 75) {
                out.add(Finding(Severity.ATENCAO, "Motor não esquenta", "Após ${f(runtime / 60)} min ligado continua abaixo de 75 °C.", "Suspeita de válvula termostática presa aberta (P0128)."))
            }
        }
        stats(samples, OIL)?.let { if (it.last > 130) out.add(Finding(Severity.CRITICO, "Óleo muito quente (${f(it.last)} °C)", "Acima de 130 °C o óleo perde proteção.", "Reduza a carga e verifique arrefecimento.")) }

        // ---------- Sistema de carga ----------
        voltageStats(samples)?.let { v ->
            if (running) {
                when {
                    v.mean < 13.0 -> out.add(Finding(Severity.CRITICO, "Alternador não está carregando (${f(v.mean, 1)} V)", "Com motor ligado deveria ficar entre 13,5 e 14,7 V.", "Verifique correia, conector e regulador do alternador."))
                    v.mean < 13.4 -> out.add(Finding(Severity.ATENCAO, "Carga baixa (${f(v.mean, 1)} V)", "Abaixo do ideal com motor ligado.", "Teste o alternador com faróis e A/C ligados."))
                    v.mean > 15.0 -> out.add(Finding(Severity.CRITICO, "Sobrecarga (${f(v.mean, 1)} V)", "Tensão alta pode danificar módulos e a bateria.", "Regulador de tensão com defeito."))
                    v.mean > 14.8 -> out.add(Finding(Severity.ATENCAO, "Tensão alta (${f(v.mean, 1)} V)", "Um pouco acima do normal."))
                    else -> out.add(Finding(Severity.OK, "Sistema de carga normal (${f(v.mean, 1)} V)", "Alternador carregando corretamente."))
                }
                if (v.max - v.min > 1.0) out.add(Finding(Severity.ATENCAO, "Tensão oscilando", "Variou ${f(v.min, 1)}–${f(v.max, 1)} V.", "Mau contato no alternador, terra ou bateria."))
            } else {
                when {
                    v.last < 11.8 -> out.add(Finding(Severity.CRITICO, "Bateria descarregada (${f(v.last, 1)} V)", "Pode não dar partida.", "Recarregue e teste a bateria."))
                    v.last < 12.2 -> out.add(Finding(Severity.ATENCAO, "Bateria fraca (${f(v.last, 1)} V)", "Carga em torno de 50 %."))
                    else -> out.add(Finding(Severity.OK, "Bateria OK (${f(v.last, 1)} V)", "Motor desligado."))
                }
            }
        }

        // ---------- Mistura (fuel trims) ----------
        val base = if (idle.size >= 5) idle else samples
        val st1 = stats(base, STFT1); val lt1 = stats(base, LTFT1)
        val st2 = stats(base, STFT2); val lt2 = stats(base, LTFT2)
        val total1 = if (st1 != null || lt1 != null) (st1?.mean ?: 0.0) + (lt1?.mean ?: 0.0) else null
        val total2 = if (st2 != null || lt2 != null) (st2?.mean ?: 0.0) + (lt2?.mean ?: 0.0) else null
        val mapIdle = stats(idle, MAP)
        val baro = stats(samples, BARO)?.mean ?: 101.0
        val mapRatio = mapIdle?.mean?.div(baro)
        val ethanol = stats(samples, ETHANOL)?.last

        var rich = false
        var lean = false
        if (total1 != null) {
            val lt = lt1?.mean ?: 0.0
            when {
                lt <= -20 || total1 <= -25 -> { rich = true; out.add(Finding(Severity.CRITICO, "Mistura muito rica (correção ${f(total1, 1)} %)", "A ECU está retirando muito combustível.", richCauses(mapRatio, ethanol))) }
                lt <= -10 || total1 <= -15 -> { rich = true; out.add(Finding(Severity.ATENCAO, "Mistura rica (correção ${f(total1, 1)} %)", "Curta ${f(st1?.mean ?: 0.0, 1)} % + longa ${f(lt, 1)} %: a ECU está tirando combustível.", richCauses(mapRatio, ethanol))) }
                lt >= 20 || total1 >= 25 -> { lean = true; out.add(Finding(Severity.CRITICO, "Mistura muito pobre (correção +${f(total1, 1)} %)", "A ECU está no limite para enriquecer.", leanCauses(samples))) }
                lt >= 10 || total1 >= 15 -> { lean = true; out.add(Finding(Severity.ATENCAO, "Mistura pobre (correção +${f(total1, 1)} %)", "Curta ${f(st1?.mean ?: 0.0, 1)} % + longa ${f(lt, 1)} %: a ECU está adicionando combustível.", leanCauses(samples))) }
                else -> out.add(Finding(Severity.OK, "Mistura equilibrada (correção ${f(total1, 1)} %)", "Correções dentro de ±10 %."))
            }
            st1?.let { if (it.sd > 8) out.add(Finding(Severity.INFO, "Correção curta oscilando muito", "Desvio de ${f(it.sd, 1)} %.", "Sonda lambda lenta ou falha de ignição intermitente.")) }
        }
        if (total1 != null && total2 != null && abs(total1 - total2) > 10) {
            out.add(Finding(Severity.ATENCAO, "Diferença entre bancos", "Banco 1 ${f(total1, 1)} % × banco 2 ${f(total2, 1)} %.", "Problema localizado em um lado: injetor, vazamento de admissão/escape ou sonda."))
        }

        // ---------- Vácuo / carga em marcha lenta ----------
        if (mapIdle != null && mapRatio != null && idle.size >= 5) {
            if (mapRatio > 0.6) {
                val load = stats(idle, LOAD)?.mean
                val extra = when {
                    rich -> "MAP alto junto com mistura rica sugere sensor MAP lendo acima do real ou mangueira do MAP obstruída/solta."
                    lean -> "MAP alto com mistura pobre indica entrada falsa de ar (vazamento de vácuo)."
                    else -> "Pode ser carga extra (A/C, eletroventilador) ou motor frio."
                }
                out.add(Finding(
                    if (warm) Severity.ATENCAO else Severity.INFO,
                    "Vácuo baixo em marcha lenta (MAP ${f(mapIdle.mean)} kPa)",
                    "Esperado 30–45 kPa com motor quente; está a ${f(mapRatio * 100)} % da pressão atmosférica" +
                        (load?.let { ", carga ${f(it)} %" } ?: "") + ". $extra",
                    "Teste: ignição ligada e motor desligado, o MAP deve marcar ≈ ${f(baro)} kPa. Repita com A/C desligado e motor quente.",
                ))
            } else if (warm) {
                out.add(Finding(Severity.OK, "Vácuo de marcha lenta normal (MAP ${f(mapIdle.mean)} kPa)", "Admissão sem sinais de vazamento."))
            }
        }

        // ---------- Marcha lenta ----------
        stats(idle, RPM)?.let { r ->
            if (idle.size >= 5) {
                if (warm && r.mean > 1050) out.add(Finding(Severity.ATENCAO, "Marcha lenta alta (${f(r.mean)} rpm)", "Com motor quente o normal é 700–950 rpm.", "A/C ligado, entrada falsa de ar, corpo de borboleta sujo ou sem aprendizado."))
                if (r.mean < 600) out.add(Finding(Severity.ATENCAO, "Marcha lenta baixa (${f(r.mean)} rpm)", "Risco de o motor apagar.", "Corpo de borboleta sujo ou atuador de marcha lenta."))
                if (r.sd > 60) out.add(Finding(Severity.ATENCAO, "Marcha lenta instável", "Variação de ±${f(r.sd)} rpm.", "Falha de ignição (velas, cabos, bobinas), injetor ou vazamento de vácuo."))
                else out.add(Finding(Severity.OK, "Marcha lenta estável (${f(r.mean)} ± ${f(r.sd)} rpm)", "Sem oscilações relevantes."))
            }
        }
        stats(idle, TIMING)?.let { if (idle.size >= 5 && it.mean < 0) out.add(Finding(Severity.ATENCAO, "Avanço negativo em marcha lenta (${f(it.mean, 1)}°)", "A ECU está atrasando a ignição.", "Possível detonação, combustível ruim ou rotação forçada pela ECU.")) }
        stats(idle, MAF)?.let { if (idle.size >= 5 && it.mean < 1.0) out.add(Finding(Severity.ATENCAO, "Fluxo de ar baixo (${f(it.mean, 2)} g/s)", "Valor baixo demais para marcha lenta.", "Sensor MAF sujo ou entrada de ar após o sensor.")) }
        stats(idle, TPS)?.let { if (idle.size >= 5 && warm && it.mean > 30) out.add(Finding(Severity.INFO, "Borboleta muito aberta em marcha lenta (${f(it.mean)} %)", "A ECU abre mais a borboleta para manter a rotação.", "Corpo de borboleta sujo ou carga extra.")) }

        // ---------- Admissão / ambiente ----------
        val iat = stats(samples, IAT)
        val amb = stats(samples, AMBIENT)
        if (iat != null) {
            if (iat.mean > 70) out.add(Finding(Severity.ATENCAO, "Ar de admissão muito quente (${f(iat.mean)} °C)", "Reduz potência e favorece detonação."))
            else if (amb != null && iat.mean - amb.mean > 25) out.add(Finding(Severity.INFO, "Ar de admissão aquecido (${f(iat.mean)} °C, ambiente ${f(amb.mean)} °C)", "Normal parado em marcha lenta (calor do cofre)."))
        }

        // ---------- Combustível ----------
        ethanol?.let { out.add(Finding(Severity.INFO, "Etanol estimado: ${f(it)} %", "Proporção calculada pela ECU (flex).")) }
        stats(samples, FUEL)?.let { if (it.last < 10) out.add(Finding(Severity.ATENCAO, "Combustível na reserva (${f(it.last)} %)", "Rodar na reserva prejudica a bomba.")) }

        // ---------- Placar ----------
        var score = 100
        out.forEach { score -= when (it.severity) { Severity.CRITICO -> 25; Severity.ATENCAO -> 10; else -> 0 } }
        score = score.coerceIn(0, 100)
        val crit = out.count { it.severity == Severity.CRITICO }
        val warn = out.count { it.severity == Severity.ATENCAO }
        val headline = when {
            samples.isEmpty() -> "Aguardando dados"
            crit > 0 -> "$crit problema(s) crítico(s) e $warn ponto(s) de atenção"
            warn > 0 -> "$warn ponto(s) de atenção"
            else -> "Nenhuma anomalia detectada"
        }
        val sorted = out.sortedBy { when (it.severity) { Severity.CRITICO -> 0; Severity.ATENCAO -> 1; Severity.INFO -> 2; Severity.OK -> 3 } }
        return Report(score, headline, sorted, samples.size, seconds, idle.size, warm)
    }

    private fun richCauses(mapRatio: Double?, ethanol: Double?): String = buildString {
        append("Causas prováveis: ")
        val causes = mutableListOf<String>()
        if (mapRatio != null && mapRatio > 0.6) causes += "sensor MAP/mangueira do MAP"
        causes += "válvula do cânister travada aberta"
        if (ethanol != null) causes += "aprendizado de etanol errado (rode após abastecer)"
        causes += "injetor pingando"
        causes += "pressão de combustível alta (regulador)"
        causes += "sensor de temperatura do motor lendo frio"
        append(causes.joinToString("; "))
        append('.')
    }

    private fun leanCauses(samples: List<Sample>): String {
        val idleTrim = samples.filter(::isIdle).mapNotNull { s -> s.values[LTFT1]?.plus(s.values[STFT1] ?: 0.0) }.takeIf { it.size >= 5 }?.average()
        val cruiseTrim = samples.filter { (it.values[RPM] ?: 0.0) > 2000 }.mapNotNull { s -> s.values[LTFT1]?.plus(s.values[STFT1] ?: 0.0) }.takeIf { it.size >= 5 }?.average()
        return if (idleTrim != null && cruiseTrim != null) {
            if (idleTrim - cruiseTrim > 8) "Pobre principalmente em marcha lenta e melhora com rotação: típico de entrada falsa de ar (mangueiras de vácuo, junta do coletor, servo-freio)."
            else "Pobre também em rotação alta: verifique pressão/vazão de combustível (bomba, filtro, regulador) e injetores entupidos."
        } else "Causas prováveis: entrada falsa de ar, pressão de combustível baixa, injetores sujos, sensor MAF/MAP. Acelere a 2.500 rpm por 30 s para comparar."
    }

    /** Texto compacto com os dados para enviar ao Claude. */
    fun contextText(
        samples: List<Sample>,
        report: Report,
        dtc: DtcInput,
        vehicle: String,
        vin: String?,
        protocol: String?,
    ): String = buildString {
        appendLine("VEÍCULO: ${vehicle.ifBlank { "não informado" }}")
        vin?.let { appendLine("VIN: $it") }
        protocol?.let { appendLine("Protocolo OBD: $it") }
        appendLine("Janela de leitura: ${report.seconds} s, ${report.samples} amostras, ${report.idleSamples} em marcha lenta parada. Motor quente (≥80 °C): ${if (report.warm) "sim" else "não"}.")
        appendLine()
        appendLine("SENSORES (mín / média / máx / desvio | média em marcha lenta):")
        val idle = samples.filter(::isIdle)
        for (def in Pids.ALL) {
            val s = stats(samples, def.pid) ?: continue
            val i = stats(idle, def.pid)
            append("- ${def.name} [${def.unit}]: ${f(s.min, def.decimals)} / ${f(s.mean, def.decimals)} / ${f(s.max, def.decimals)} / ±${f(s.sd, 1)}")
            i?.let { append(" | lenta ${f(it.mean, def.decimals)}") }
            appendLine()
        }
        voltageStats(samples)?.let { appendLine("- Tensão na tomada OBD (ATRV) [V]: ${f(it.min, 1)} / ${f(it.mean, 1)} / ${f(it.max, 1)}") }
        appendLine()
        appendLine("CÓDIGOS DE FALHA: ${if (!dtc.read) "não lidos" else ""}")
        appendLine("- Luz de injeção (MIL): ${when (dtc.milOn) { true -> "acesa"; false -> "apagada"; null -> "desconhecido" }}")
        if (dtc.read) {
            appendLine("- Armazenados: ${dtc.stored.joinToString { "${it.first} (${it.second})" }.ifEmpty { "nenhum" }}")
            appendLine("- Pendentes: ${dtc.pending.joinToString { "${it.first} (${it.second})" }.ifEmpty { "nenhum" }}")
            appendLine("- Permanentes: ${dtc.permanent.joinToString { "${it.first} (${it.second})" }.ifEmpty { "nenhum" }}")
        }
        appendLine()
        appendLine("PRÉ-ANÁLISE AUTOMÁTICA POR REGRAS (pode conter falsos positivos):")
        report.findings.filter { it.severity != Severity.OK }.forEach { appendLine("- [${it.severity}] ${it.title}: ${it.detail}") }
        appendLine()
        appendLine("ÚLTIMAS LEITURAS (série temporal resumida, a cada ~5 s):")
        val keys = listOf(RPM, SPEED, ECT, LOAD, MAP, STFT1, LTFT1, TIMING, TPS).filter { k -> samples.any { it.values.containsKey(k) } }
        appendLine("t(s);" + keys.joinToString(";") { Pids.BY_PID[it]?.name ?: it.toString() })
        val t0 = samples.firstOrNull()?.timeMs ?: 0L
        var lastT = -10_000L
        for (s in samples.takeLast(200)) {
            if (s.timeMs - lastT < 5000) continue
            lastT = s.timeMs
            appendLine(((s.timeMs - t0) / 1000).toString() + ";" + keys.joinToString(";") { k -> s.values[k]?.let { f(it, 1) } ?: "" })
        }
    }

    /** Resumo curto das leituras atuais (para perguntas de acompanhamento). */
    fun snapshotText(samples: List<Sample>): String {
        val last = samples.lastOrNull() ?: return ""
        return "Leituras atuais: " + last.values.entries.mapNotNull { (pid, v) ->
            Pids.BY_PID[pid]?.let { "${it.name} ${f(v, it.decimals)} ${it.unit}" }
        }.joinToString("; ") + (last.voltage?.let { "; Bateria ${f(it, 1)} V" } ?: "")
    }
}
