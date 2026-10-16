# C&C Scanner OBD

App Android (Kotlin + Jetpack Compose) para scanners ELM327 como o **KUULAA OBDII** — funciona com a versão Bluetooth clássico (SPP) e com a versão BLE (Bluetooth 4.0).

## Funções
- **Conectar**: busca pareados + descoberta clássica + varredura BLE; detecta sozinho o tipo de link; reconexão ao último scanner.
- **Painel**: RPM e velocidade em mostradores; temperatura, carga, borboleta, MAP, MAF, avanço, correções de combustível, nível de tanque, % de etanol (flex), temp. do óleo etc. — só os PIDs que o carro suporta. Tensão da bateria (ATRV).
- **Gravação CSV**: grava as leituras e exporta CSV (separador `;`, vírgula decimal — abre direto no Excel PT-BR).
- **Falhas**: status da luz de injeção, códigos armazenados / pendentes / permanentes com descrição em português, botão de pesquisa e apagar códigos (modo 04) com confirmação.
- **IA**: diagnóstico automático no aparelho (sem internet) com nota de 0–100 e achados por gravidade — mistura (fuel trims), vácuo/MAP, temperatura, carga elétrica, marcha lenta, códigos — e análise completa pelo Claude (API da Anthropic) com conversa para perguntas. Configure a chave da API e o veículo no ícone de engrenagem.
- **Info**: VIN (chassi), protocolo, firmware do adaptador, lista de sensores suportados.
- **Terminal**: envio livre de comandos AT/OBD com atalhos.

## Gerar o APK

### Opção 1 — GitHub Actions (sem instalar nada)
1. Crie um repositório no GitHub e envie o conteúdo desta pasta (incluindo `.github/`).
2. A aba **Actions** roda o workflow *Build APK* a cada push.
3. Baixe o artefato `CCScannerOBD-apk` → `app-release.apk` (assinado com chave de debug, instala direto).

### Opção 2 — Android Studio
Abra a pasta, aguarde o Gradle sincronizar e use **Build ▸ Build APK(s)**.

## Requisitos
- Android 8.0+ (minSdk 26), targetSdk 35.
- Scanner na tomada OBD-II e ignição ligada.

## Estrutura
```
app/src/main/java/br/com/ccgeo/obd/
  bt/   ClassicTransport (SPP), BleTransport (GATT FFF0/FFE0/18F0/Vgate), DeviceScanner
  elm/  Elm327 (init/comandos), ObdParser (respostas, DTC, VIN), Pids, DtcDb
  ui/   telas Compose
  ObdViewModel.kt, MainActivity.kt
```
