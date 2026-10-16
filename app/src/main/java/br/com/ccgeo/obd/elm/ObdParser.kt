package br.com.ccgeo.obd.elm

/** Interpretação das respostas de texto do ELM327 (ATE0 ATS0 ATH0 ATCAF1). */
object ObdParser {

    private val ERRORS = listOf(
        "NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS ERROR", "BUS BUSY",
        "FB ERROR", "DATA ERROR", "STOPPED", "ERROR", "?", "TIMEOUT", "BUFFER FULL",
    )

    private val MULTI = Regex("^([0-9A-F]):([0-9A-F]*)$")
    private val HEX = Regex("^[0-9A-F]+$")

    /** Linhas úteis, sem espaços, eco, "SEARCHING..." e "OK". */
    fun lines(raw: String): List<String> =
        raw.uppercase()
            .replace('\r', '\n')
            .split('\n')
            .map { it.replace(" ", "").replace("\t", "").trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("SEARCHING") || it.startsWith("BUSINIT") || it == "OK" }

    fun errorOf(raw: String): String? {
        val ls = lines(raw).map { it.replace(".", "") }
        if (ls.isEmpty()) return "SEM RESPOSTA"
        if (ls.any { l -> HEX.matches(l) || MULTI.matches(l) }) return null
        return ERRORS.firstOrNull { e -> ls.any { it.contains(e.replace(" ", "")) } } ?: ls.first()
    }

    /**
     * Converte a resposta em quadros de bytes, um por ECU/linha.
     * Respostas multiquadro CAN ("0:...", "1:...") são concatenadas num só quadro.
     */
    fun frames(raw: String): List<IntArray> {
        val ls = lines(raw)
        val out = mutableListOf<IntArray>()
        val multi = StringBuilder()
        for (l in ls) {
            val m = MULTI.matchEntire(l)
            when {
                m != null -> multi.append(m.groupValues[2])
                // cabeçalho de comprimento de mensagem multiquadro (ex.: "014")
                l.length == 3 && HEX.matches(l) -> {
                    if (multi.isNotEmpty()) { out += hexToBytes(multi.toString()); multi.clear() }
                }
                HEX.matches(l) && l.length % 2 == 0 -> {
                    if (multi.isNotEmpty()) { out += hexToBytes(multi.toString()); multi.clear() }
                    out += hexToBytes(l)
                }
            }
        }
        if (multi.isNotEmpty()) out += hexToBytes(multi.toString())
        return out
    }

    fun hexToBytes(s: String): IntArray {
        val clean = if (s.length % 2 == 1) s.dropLast(1) else s
        return IntArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16) }
    }

    /** Dados de um PID do modo 01: bytes após "41 PID". Usa a primeira ECU que respondeu. */
    fun mode01Data(raw: String, pid: Int): IntArray? =
        frames(raw).firstOrNull { it.size >= 2 && it[0] == 0x41 && it[1] == pid }
            ?.let { it.copyOfRange(2, it.size) }

    /** Bitmask de PIDs suportados (0100, 0120, 0140...). Une as respostas de todas as ECUs. */
    fun supportedPids(raw: String, base: Int): Set<Int> {
        val set = mutableSetOf<Int>()
        for (f in frames(raw)) {
            if (f.size < 6 || f[0] != 0x41 || f[1] != base) continue
            for (byteIdx in 0 until 4) {
                val b = f[2 + byteIdx]
                for (bit in 0 until 8) {
                    if (b and (0x80 shr bit) != 0) set += base + byteIdx * 8 + bit + 1
                }
            }
        }
        return set
    }

    /** Códigos de falha dos modos 03 / 07 / 0A. */
    fun dtcs(raw: String, isCan: Boolean): List<String> {
        val result = linkedSetOf<String>()
        for (f in frames(raw)) {
            if (f.isEmpty() || f[0] !in listOf(0x43, 0x47, 0x4A)) continue
            var i = if (isCan) 2 else 1 // CAN traz um byte de contagem após o 43
            while (i + 1 < f.size) {
                val a = f[i]; val b = f[i + 1]
                if (a != 0 || b != 0) result += decodeDtc(a, b)
                i += 2
            }
        }
        return result.toList()
    }

    fun decodeDtc(a: Int, b: Int): String {
        val type = "PCBU"[(a shr 6) and 0x3]
        val d1 = (a shr 4) and 0x3
        val d2 = (a and 0xF).toString(16).uppercase()
        val rest = b.toString(16).uppercase().padStart(2, '0')
        return "$type$d1$d2$rest"
    }

    /** VIN (modo 09 PID 02). Funciona em CAN e nos protocolos antigos. */
    fun vin(raw: String): String? {
        val sb = StringBuilder()
        for (f in frames(raw)) {
            if (f.size < 3 || f[0] != 0x49 || f[1] != 0x02) continue
            for (i in 3 until f.size) {
                val c = f[i]
                val ch = c.toChar()
                if (ch in '0'..'9' || ch in 'A'..'Z') sb.append(ch)
            }
        }
        val s = sb.toString()
        return if (s.length >= 17) s.takeLast(17) else s.ifEmpty { null }
    }

    /** Status do monitor (PID 01): MIL acesa e quantidade de códigos. */
    fun monitorStatus(raw: String): Pair<Boolean, Int>? {
        val d = mode01Data(raw, 0x01) ?: return null
        if (d.isEmpty()) return null
        return ((d[0] and 0x80) != 0) to (d[0] and 0x7F)
    }

    /** Tensão de ATRV ("12.4V") */
    fun voltage(raw: String): Double? =
        Regex("([0-9]+[.,][0-9]+)\\s*V").find(raw.uppercase())?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
}
