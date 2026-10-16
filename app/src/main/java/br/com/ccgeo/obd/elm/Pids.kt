package br.com.ccgeo.obd.elm

/** Definição de um PID do modo 01 (SAE J1979). */
class PidDef(
    val pid: Int,
    val name: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val decimals: Int,
    val bytes: Int,
    val fast: Boolean = false,
    val formula: (IntArray) -> Double,
) {
    val code: String get() = "01" + pid.toString(16).uppercase().padStart(2, '0')

    fun decode(data: IntArray): Double? = if (data.size >= bytes) formula(data) else null

    fun format(v: Double?): String =
        if (v == null) "—" else String.format(java.util.Locale.US, "%.${decimals}f", v).replace('.', ',')
}

object Pids {
    private fun ab(d: IntArray) = d[0] * 256.0 + d[1]
    private fun pct(d: IntArray) = d[0] * 100.0 / 255.0
    private fun temp(d: IntArray) = d[0] - 40.0
    private fun trim(d: IntArray) = (d[0] - 128.0) * 100.0 / 128.0

    const val RPM = 0x0C
    const val SPEED = 0x0D
    const val COOLANT = 0x05
    const val MODULE_VOLTAGE = 0x42

    val ALL: List<PidDef> = listOf(
        PidDef(0x0C, "Rotação", "rpm", 0.0, 8000.0, 0, 2, fast = true) { ab(it) / 4.0 },
        PidDef(0x0D, "Velocidade", "km/h", 0.0, 240.0, 0, 1, fast = true) { it[0].toDouble() },
        PidDef(0x05, "Temp. arrefecimento", "°C", -40.0, 130.0, 0, 1) { temp(it) },
        PidDef(0x04, "Carga do motor", "%", 0.0, 100.0, 0, 1, fast = true) { pct(it) },
        PidDef(0x11, "Borboleta", "%", 0.0, 100.0, 0, 1, fast = true) { pct(it) },
        PidDef(0x0B, "Pressão coletor (MAP)", "kPa", 0.0, 255.0, 0, 1) { it[0].toDouble() },
        PidDef(0x10, "Fluxo de ar (MAF)", "g/s", 0.0, 300.0, 2, 2) { ab(it) / 100.0 },
        PidDef(0x0F, "Temp. ar admissão", "°C", -40.0, 100.0, 0, 1) { temp(it) },
        PidDef(0x0E, "Avanço de ignição", "°", -64.0, 64.0, 1, 1) { it[0] / 2.0 - 64.0 },
        PidDef(0x06, "Correção curta B1", "%", -100.0, 100.0, 1, 1) { trim(it) },
        PidDef(0x07, "Correção longa B1", "%", -100.0, 100.0, 1, 1) { trim(it) },
        PidDef(0x08, "Correção curta B2", "%", -100.0, 100.0, 1, 1) { trim(it) },
        PidDef(0x09, "Correção longa B2", "%", -100.0, 100.0, 1, 1) { trim(it) },
        PidDef(0x2F, "Nível de combustível", "%", 0.0, 100.0, 0, 1) { pct(it) },
        PidDef(0x52, "Etanol no combustível", "%", 0.0, 100.0, 0, 1) { pct(it) },
        PidDef(0x42, "Tensão do módulo", "V", 0.0, 18.0, 2, 2) { ab(it) / 1000.0 },
        PidDef(0x5C, "Temp. do óleo", "°C", -40.0, 150.0, 0, 1) { temp(it) },
        PidDef(0x46, "Temp. ambiente", "°C", -40.0, 60.0, 0, 1) { temp(it) },
        PidDef(0x33, "Pressão barométrica", "kPa", 0.0, 255.0, 0, 1) { it[0].toDouble() },
        PidDef(0x0A, "Pressão combustível", "kPa", 0.0, 765.0, 0, 1) { it[0] * 3.0 },
        PidDef(0x23, "Pressão do rail", "kPa", 0.0, 655350.0, 0, 2) { ab(it) * 10.0 },
        PidDef(0x5E, "Consumo", "L/h", 0.0, 50.0, 1, 2) { ab(it) / 20.0 },
        PidDef(0x49, "Pedal acelerador", "%", 0.0, 100.0, 0, 1) { pct(it) },
        PidDef(0x44, "Lambda comandado", "λ", 0.0, 2.0, 3, 2) { ab(it) * 2.0 / 65536.0 },
        PidDef(0x2C, "EGR comandada", "%", 0.0, 100.0, 0, 1) { pct(it) },
        PidDef(0x1F, "Tempo desde partida", "s", 0.0, 65535.0, 0, 2) { ab(it) },
        PidDef(0x21, "Distância com MIL acesa", "km", 0.0, 65535.0, 0, 2) { ab(it) },
        PidDef(0x31, "Distância desde limpeza", "km", 0.0, 65535.0, 0, 2) { ab(it) },
    )

    val BY_PID: Map<Int, PidDef> = ALL.associateBy { it.pid }
}
