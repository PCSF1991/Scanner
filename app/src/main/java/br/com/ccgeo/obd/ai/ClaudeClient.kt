package br.com.ccgeo.obd.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ChatMsg(
    val role: String,          // "user" | "assistant"
    val text: String,          // o que vai para a API
    val display: String = text // o que aparece na tela
)

data class AiModel(val id: String, val label: String)

object ClaudeModels {
    val ALL = listOf(
        AiModel("claude-haiku-5-5", "Haiku 5.5 — rápido"),
        AiModel("claude-sonnet-5-5", "Sonnet 5.5 — equilibrado"),
        AiModel("claude-opus-5-5", "Opus 5.5 — mais preciso"),
    )
    const val DEFAULT = "claude-sonnet-5-5"
}

/** Chamada direta à Messages API da Anthropic (sem SDK, só HttpURLConnection + org.json). */
class ClaudeClient(private val apiKey: String, private val model: String) {

    suspend fun send(system: String, messages: List<ChatMsg>, maxTokens: Int = 2000): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", maxTokens)
                .put("system", system)
                .put("messages", JSONArray().apply {
                    messages.forEach { m -> put(JSONObject().put("role", m.role).put("content", m.text)) }
                })

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    val apiMsg = try {
                        JSONObject(text).optJSONObject("error")?.optString("message")
                    } catch (_: Exception) { null }
                    throw IOException(
                        when (code) {
                            401 -> "Chave de API inválida. Confira nas configurações da IA."
                            403 -> "Chave sem permissão para este modelo."
                            404 -> "Modelo não encontrado ($model)."
                            429 -> "Limite de uso da API atingido. Aguarde um pouco."
                            529, 503 -> "Serviço da Anthropic sobrecarregado. Tente de novo em instantes."
                            else -> "Erro $code da API" + (apiMsg?.let { ": $it" } ?: "")
                        }
                    )
                }

                val content = JSONObject(text).optJSONArray("content") ?: JSONArray()
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.optJSONObject(i) ?: continue
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }.trim().ifEmpty { throw IOException("Resposta vazia da IA") }
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

        val SYSTEM_PROMPT = """
Você é um mecânico especialista em injeção eletrônica e diagnóstico OBD-II, com muita experiência na frota brasileira (motores flex etanol/gasolina, injeção multiponto e direta, turbo, diesel).
Você recebe dados reais lidos por um scanner ELM327: estatísticas dos sensores, uma série temporal resumida, códigos de falha e uma pré-análise automática por regras (que pode ter falsos positivos).

Responda em português do Brasil, de forma direta e prática, para um usuário técnico que faz a própria manutenção.
Estrutura da primeira resposta:
1. Resumo: 1–2 frases e a gravidade geral (normal, atenção ou crítico).
2. Fora do normal: cada item com o valor medido e a faixa esperada.
3. Causas prováveis, em ordem de probabilidade, ligando os sintomas entre si (ex.: MAP alto + correção negativa).
4. Testes para confirmar: passos que dá para fazer com o próprio scanner ou ferramentas simples, com o valor esperado de cada teste.
5. Se pode rodar com o carro e o que fazer primeiro.

Regras:
- Leve em conta as condições da leitura: motor frio ou quente, marcha lenta ou rodando, ambiente quente, altitude (pela pressão barométrica), proporção de etanol.
- Não invente valores que não foram enviados; quando faltar dado, diga exatamente qual leitura pedir (ex.: "grave 1 minuto a 2.500 rpm").
- Avise com destaque quando algo exigir parar o carro (superaquecimento, falha de ignição forte, alternador sem carga).
- Não use tabelas nem títulos com #. Use parágrafos curtos e listas com "-". Negrito só com **texto**.
- Nas perguntas seguintes, responda só o que foi perguntado, de forma curta.
""".trim()
    }
}
