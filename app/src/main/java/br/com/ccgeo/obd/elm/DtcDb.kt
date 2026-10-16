package br.com.ccgeo.obd.elm

/** Descrições dos códigos genéricos SAE J2012 mais comuns (PT-BR). */
object DtcDb {

    fun describe(code: String): String {
        MAP[code]?.let { return it }
        if (code.length == 5) {
            // P0300-P0312: falha de ignição no cilindro N
            if (code.startsWith("P030") || code.startsWith("P031")) {
                val n = code.substring(3).toIntOrNull()
                if (n != null && n in 1..12) return "Falha de ignição (misfire) no cilindro $n"
            }
            if (code.startsWith("P020") || code.startsWith("P021")) {
                val n = code.substring(2).toIntOrNull()?.minus(200)
                if (n != null && n in 1..12) return "Circuito do injetor do cilindro $n"
            }
            if (code.startsWith("P035")) {
                val n = code.substring(4).toIntOrNull()
                if (n != null && n in 1..8) return "Circuito primário/secundário da bobina de ignição $n"
            }
            val second = code[1]
            if (second == '1' || second == '3') return "Código específico do fabricante — consulte o manual da montadora"
            when (code[0]) {
                'P' -> return "Trem de força (motor/câmbio) — código genérico sem descrição local"
                'C' -> return "Chassi (freios/suspensão/direção)"
                'B' -> return "Carroceria (airbag, conforto, iluminação)"
                'U' -> return "Rede de comunicação entre módulos"
            }
        }
        return "Sem descrição"
    }

    private val MAP: Map<String, String> = mapOf(
        "P0010" to "Atuador de comando de válvulas (admissão) – circuito, banco 1",
        "P0011" to "Comando de válvulas (admissão) – avanço excessivo/desempenho, banco 1",
        "P0012" to "Comando de válvulas (admissão) – atraso excessivo, banco 1",
        "P0014" to "Comando de válvulas (escape) – avanço excessivo/desempenho, banco 1",
        "P0016" to "Correlação virabrequim × comando de válvulas, banco 1 sensor A",
        "P0030" to "Aquecedor sonda lambda – circuito de controle, B1 S1",
        "P0036" to "Aquecedor sonda lambda – circuito de controle, B1 S2",
        "P0087" to "Pressão do rail/sistema de combustível muito baixa",
        "P0088" to "Pressão do rail/sistema de combustível muito alta",
        "P0089" to "Regulador de pressão de combustível – desempenho",
        "P0093" to "Vazamento grande no sistema de combustível",
        "P0100" to "Sensor de fluxo de ar (MAF) – circuito",
        "P0101" to "Sensor de fluxo de ar (MAF) – faixa/desempenho",
        "P0102" to "Sensor de fluxo de ar (MAF) – sinal baixo",
        "P0103" to "Sensor de fluxo de ar (MAF) – sinal alto",
        "P0105" to "Sensor de pressão absoluta do coletor (MAP) – circuito",
        "P0106" to "Sensor MAP/barométrico – faixa/desempenho",
        "P0107" to "Sensor MAP/barométrico – sinal baixo",
        "P0108" to "Sensor MAP/barométrico – sinal alto",
        "P0110" to "Sensor de temperatura do ar de admissão – circuito",
        "P0112" to "Sensor de temperatura do ar de admissão – sinal baixo",
        "P0113" to "Sensor de temperatura do ar de admissão – sinal alto",
        "P0115" to "Sensor de temperatura do líquido de arrefecimento – circuito",
        "P0116" to "Sensor de temperatura do arrefecimento – faixa/desempenho",
        "P0117" to "Sensor de temperatura do arrefecimento – sinal baixo",
        "P0118" to "Sensor de temperatura do arrefecimento – sinal alto",
        "P0120" to "Sensor de posição da borboleta/pedal A – circuito",
        "P0121" to "Sensor de posição da borboleta A – faixa/desempenho",
        "P0122" to "Sensor de posição da borboleta A – sinal baixo",
        "P0123" to "Sensor de posição da borboleta A – sinal alto",
        "P0125" to "Temperatura do arrefecimento insuficiente para controle em malha fechada",
        "P0128" to "Termostato – temperatura abaixo da regulagem",
        "P0130" to "Sonda lambda – circuito, B1 S1",
        "P0131" to "Sonda lambda – tensão baixa, B1 S1",
        "P0132" to "Sonda lambda – tensão alta, B1 S1",
        "P0133" to "Sonda lambda – resposta lenta, B1 S1",
        "P0134" to "Sonda lambda – sem atividade, B1 S1",
        "P0135" to "Aquecedor da sonda lambda – circuito, B1 S1",
        "P0136" to "Sonda lambda – circuito, B1 S2",
        "P0137" to "Sonda lambda – tensão baixa, B1 S2",
        "P0138" to "Sonda lambda – tensão alta, B1 S2",
        "P0140" to "Sonda lambda – sem atividade, B1 S2",
        "P0141" to "Aquecedor da sonda lambda – circuito, B1 S2",
        "P0170" to "Correção de combustível – banco 1",
        "P0171" to "Mistura pobre – banco 1",
        "P0172" to "Mistura rica – banco 1",
        "P0174" to "Mistura pobre – banco 2",
        "P0175" to "Mistura rica – banco 2",
        "P0181" to "Sensor de temperatura do combustível – faixa/desempenho",
        "P0190" to "Sensor de pressão do rail – circuito",
        "P0191" to "Sensor de pressão do rail – faixa/desempenho",
        "P0219" to "Sobrerrotação do motor",
        "P0230" to "Relé/circuito primário da bomba de combustível",
        "P0234" to "Sobrepressão do turbo",
        "P0236" to "Sensor de pressão do turbo – faixa/desempenho",
        "P0299" to "Turbo – pressão insuficiente (underboost)",
        "P0300" to "Falhas de ignição aleatórias/múltiplos cilindros",
        "P0325" to "Sensor de detonação 1 – circuito",
        "P0327" to "Sensor de detonação 1 – sinal baixo",
        "P0328" to "Sensor de detonação 1 – sinal alto",
        "P0335" to "Sensor de rotação do virabrequim (CKP) – circuito",
        "P0336" to "Sensor de rotação do virabrequim – faixa/desempenho",
        "P0340" to "Sensor de fase do comando (CMP) – circuito",
        "P0341" to "Sensor de fase do comando – faixa/desempenho",
        "P0380" to "Velas aquecedoras – circuito",
        "P0400" to "Recirculação de gases (EGR) – fluxo",
        "P0401" to "EGR – fluxo insuficiente",
        "P0402" to "EGR – fluxo excessivo",
        "P0403" to "EGR – circuito de controle",
        "P0404" to "EGR – faixa/desempenho",
        "P0405" to "Sensor de posição da EGR – sinal baixo",
        "P0420" to "Eficiência do catalisador abaixo do limite – banco 1",
        "P0430" to "Eficiência do catalisador abaixo do limite – banco 2",
        "P0440" to "Sistema de controle de emissões evaporativas (cânister)",
        "P0441" to "Cânister – fluxo de purga incorreto",
        "P0442" to "Cânister – vazamento pequeno",
        "P0443" to "Válvula de purga do cânister – circuito",
        "P0446" to "Cânister – circuito de ventilação",
        "P0455" to "Cânister – vazamento grande",
        "P0456" to "Cânister – vazamento muito pequeno",
        "P0458" to "Válvula de purga do cânister – sinal baixo",
        "P0461" to "Sensor de nível de combustível – faixa/desempenho",
        "P0462" to "Sensor de nível de combustível – sinal baixo",
        "P0463" to "Sensor de nível de combustível – sinal alto",
        "P0480" to "Eletroventilador 1 – circuito de controle",
        "P0500" to "Sensor de velocidade do veículo (VSS)",
        "P0501" to "Sensor de velocidade – faixa/desempenho",
        "P0505" to "Controle de marcha lenta – sistema",
        "P0506" to "Marcha lenta abaixo do esperado",
        "P0507" to "Marcha lenta acima do esperado",
        "P0560" to "Tensão do sistema (bateria)",
        "P0562" to "Tensão do sistema baixa",
        "P0563" to "Tensão do sistema alta",
        "P0571" to "Interruptor do freio – circuito",
        "P0600" to "Falha no link de comunicação serial",
        "P0601" to "Módulo de controle – erro de checksum da memória",
        "P0603" to "Módulo de controle – erro na memória KAM",
        "P0604" to "Módulo de controle – erro na memória RAM",
        "P0605" to "Módulo de controle – erro na memória ROM",
        "P0606" to "Processador do módulo de controle",
        "P0627" to "Bomba de combustível – circuito de controle aberto",
        "P0641" to "Referência de tensão de sensores A – circuito",
        "P0645" to "Relé da embreagem do ar-condicionado – circuito",
        "P0650" to "Lâmpada de anomalia (MIL) – circuito",
        "P0688" to "Relé principal do módulo – sensoriamento",
        "P0700" to "Sistema de controle da transmissão (ver códigos do câmbio)",
        "P0705" to "Sensor de faixa da transmissão – circuito",
        "P0715" to "Sensor de rotação de entrada da transmissão",
        "P0720" to "Sensor de rotação de saída da transmissão",
        "P0730" to "Relação de marcha incorreta",
        "P0740" to "Embreagem do conversor de torque – circuito",
        "P0750" to "Solenoide de mudança A",
        "P2002" to "Filtro de partículas (DPF) – eficiência abaixo do limite, B1",
        "P2096" to "Correção pós-catalisador – sistema muito pobre, B1",
        "P2097" to "Correção pós-catalisador – sistema muito rico, B1",
        "P2135" to "Correlação dos sensores de posição da borboleta/pedal A/B",
        "P2138" to "Correlação dos sensores do pedal do acelerador D/E",
        "P2187" to "Mistura pobre em marcha lenta – banco 1",
        "P2188" to "Mistura rica em marcha lenta – banco 1",
        "P2195" to "Sonda lambda presa em pobre – B1 S1",
        "P2196" to "Sonda lambda presa em rico – B1 S1",
        "P2263" to "Turbo – desempenho do sistema",
        "P2279" to "Vazamento de ar na admissão",
        "P242F" to "Filtro de partículas (DPF) – restrição por acúmulo de cinzas",
        "U0001" to "Rede CAN de alta velocidade",
        "U0073" to "Barramento de comunicação A desligado (bus off)",
        "U0100" to "Perda de comunicação com o módulo do motor (ECM/PCM)",
        "U0101" to "Perda de comunicação com o módulo do câmbio (TCM)",
        "U0121" to "Perda de comunicação com o módulo do ABS",
        "U0140" to "Perda de comunicação com o módulo da carroceria (BCM)",
        "U0155" to "Perda de comunicação com o painel de instrumentos",
    )
}
