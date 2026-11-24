# Sync Monitor 📱

Aplicativo Android criado para as atividades da apostila **Código de Alta Performance: Mobile**.
Cada conceito tem a **sua própria tela**, acessada por um **menu inicial**. Assim, na apresentação, cada pessoa mostra uma tela sem misturar assuntos.

---

## 👥 Equipe

| Tópico | Integrante | Tela do app |
|---|---|---|
| 1. O problema da bateria (Doze e App Standby) | **Mayumi** | 🔋 Bateria e Doze Mode |
| 2. Manifesto, permissões e canais | **Daniel** | 🔔 Notificações e permissões |
| 3. O serviço e seu ciclo de vida | **Vinicius** | ⚙️ Foreground Service + 📜 Log |
| 4. Main Thread vs Corrotinas | **Rodrigo** | 🧵 Main Thread vs Corrotinas |
| 5. Notificação rica | **Fabricia** | ⚙️ Foreground Service + gaveta de notificações |
| 6. Etapas do ciclo, falhas e backoff | **Heloiza** | ⚙️ Foreground Service + 📜 Log |
| 7. Desafio 1 — Listeners e sensores | **Vitor** | 📱 Desafio 1 — Acelerômetro |

---

## 🧭 Navegação

| Sync Monitor (menu inicial) | status do serviço no topo |
|---|---|
|MÓDULO 2| |
| 📱 Desafio 1 — Acelerômetro | SensorActivity |
|MÓDULO 2| |
| ⚙️ Foreground Service (Passo 1)| ServicoActivity |
| 📜 Log de atividades (Passo 2) | LogActivity |
| 🔔 Notificações (Passo 3)      | NotificacaoActivity |
| 🧵 Main Thread vs Corrotinas(4)| ThreadsActivity |
| 🔋 Bateria e Doze Mode (5)     | BateriaActivity |

---

Todas as telas seguem o mesmo padrão:
1. **Barra superior** com título, módulo/passo e a seta **←** para voltar ao menu.
2. Cartão **"📘 O que esta tela demonstra"**, que explica o conceito em linguagem simples.
3. O conteúdo e as demonstrações daquele conceito.
4. Um **registro (log)** no rodapé, mostrando o que aconteceu naquela tela.

---

## 📱 As telas

### Menu inicial — `MainActivity`
Mostra os cartões de navegação agrupados por módulo e um indicador do **status do serviço** (executando, pausado ou parado). Tocar no indicador abre a tela do serviço.

### 📱 Desafio 1 — Acelerômetro (Módulo 2) — `SensorActivity`
- **Padrão Observer**: a tela implementa `SensorEventListener` e recebe os eventos em `onSensorChanged()`, sem polling.
- O registro é feito no **`onResume()`** e o cancelamento no **`onPause()`**.
- Mostra X, Y e Z ao vivo com barras, magnitude, eventos recebidos, **frequência real (Hz)**, orientação e a ficha técnica do sensor.
- Seletor de frequência: `NORMAL`, `UI`, `GAME` e `FASTEST`.
- Chave **"Simular erro: NÃO cancelar no onPause()"** para o contraste no **Android Profiler**. Por segurança, o listener é sempre cancelado no `onDestroy()`.

### ⚙️ Foreground Service (Passo 1) — `ServicoActivity`
- **Iniciar**, **Pausar/Retomar** e **Parar** o `SincronizacaoService`.
- Status, cronômetro, ciclo e etapa, tarefa atual, barra de progresso e a **thread** usada.
- **Métricas em tempo real**: bateria, rede, pacotes, KB enviados, compressão e falhas.
- Slider de **intervalo entre ciclos** (2 a 10 s) e a chave **"Simular falha de rede"**.
- Cartão **"Última atividade"** com um atalho para o log completo.

### 📜 Log de atividades (Passo 2) — `LogActivity`
- Tudo o que o serviço faz em segundo plano, com **tipo (cor)**, horário e **thread**.
- Filtros: Todos, Ciclo de vida, Tarefas, Avisos e erros, Notificações e sistema.
- **Exportar** (compartilha o texto) e **Limpar**.

### 🔔 Notificações e permissões (Passo 3) — `NotificacaoActivity`
- Situação de `POST_NOTIFICATIONS` e das notificações do app, mais a versão do Android.
- Botões para **solicitar a permissão** e **abrir as configurações**.
- Lista dos **canais de notificação** com a importância de cada um.
- **Notificação de teste** no canal de alertas.
- **Anatomia** da notificação do serviço (progresso, cronômetro, BigTextStyle, botões...).

### 🧵 Main Thread vs Corrotinas (Passo 4) — `ThreadsActivity`
- **Termômetro da interface**: um contador e um círculo animados pela Main Thread.
- **❌ Executar na Main Thread** (`Thread.sleep`): a tela congela por 3 s.
- **✅ Executar com Corrotina em `Dispatchers.IO`**: a tela continua fluida, e a mensagem mostra a thread usada.

### 🔋 Bateria e Doze Mode (Passo 5) — `BateriaActivity`
- Nível da bateria, carregamento, economia de energia, **Doze** ativo, otimização de bateria e **App Standby Bucket**.
- Atalho para as configurações de otimização de bateria.
- Comparação **Foreground Service × WorkManager × Bound Service**.

---

## ⚙️ O serviço por dentro (`SincronizacaoService`)

Cada ciclo executa 5 etapas em uma **Corrotina no `Dispatchers.IO`**:

| # | Etapa | Real ou simulada? |
|---|---|---|
| 1 | Nível de bateria (`BatteryManager`) | ✅ Real |
| 2 | Tipo de rede (`ConnectivityManager`) | ✅ Real |
| 3 | Telemetria: JSON com 150 leituras e a memória do app | 🔶 Simulada |
| 4 | Compressão **GZIP** | ✅ Real |
| 5 | Envio ao servidor (8% de falha e **backoff exponencial**) | 🔶 Simulado |

**Notificação:**
- Dois canais: um silencioso para o serviço e outro de alertas.
- Barra de progresso, cronômetro, texto expandido e os botões **Pausar/Retomar** e **Parar**.
- Tocar nela abre a tela do serviço, com o menu por baixo (`TaskStackBuilder`).
- Ao encerrar, publica uma notificação de **resumo**.

### Como as peças se conectam
```
 ServicoActivity ──Intent(ação)──▶ SincronizacaoService
       ▲  LogActivity  MainActivity        │
       │        ▲          ▲               │ log() / atualizarEstado()
       └────────┴──────────┴── collect ── MonitorRepository (StateFlow) ◀┘
```
- As telas **enviam comandos** ao serviço por `Intent`: `INICIAR`, `PAUSAR`, `RETOMAR`, `PARAR` e `INTERVALO`.
- O serviço **publica estado e logs** no `MonitorRepository`, um singleton com `StateFlow`.
- Cada tela **observa** esses fluxos apenas enquanto está visível (`repeatOnLifecycle(STARTED)`).

---

## 🗂 Estrutura do projeto

```
MyApplication/
├── README.md                              ← este arquivo
├── ROTEIRO_APRESENTACAO.md / .docx        ← roteiro da equipe
├── FALAS_APRESENTACAO.md / .docx          ← falas de cada integrante
└── app/src/main/
    ├── AndroidManifest.xml                ← permissões, 7 telas e o <service>
    ├── java/com/example/myapplication/
    │   ├── MainActivity.kt          ← menu inicial
    │   ├── SensorActivity.kt        ← Desafio 1 (acelerômetro)
    │   ├── ServicoActivity.kt       ← controle do serviço
    │   ├── LogActivity.kt           ← log de atividades
    │   ├── NotificacaoActivity.kt   ← permissões e canais
    │   ├── ThreadsActivity.kt       ← Main Thread vs Corrotinas
    │   ├── BateriaActivity.kt       ← bateria e Doze
    │   ├── SincronizacaoService.kt  ← o Foreground Service
    │   ├── MonitorRepository.kt     ← estado e logs compartilhados (StateFlow)
    │   ├── Notificacoes.kt          ← criação dos canais de notificação
    │   ├── LogAdapter.kt            ← lista colorida de logs
    │   ├── MiniLog.kt               ← registro do rodapé de cada tela
    │   └── TelaUtils.kt             ← padrão comum de tela (barra superior + voltar)
    └── res/
        ├── layout/activity_*.xml    ← um layout por tela
        ├── layout/item_log.xml      ← linha do log
        ├── drawable/ic_voltar.xml   ← seta de voltar
        └── values/colors.xml        ← cores dos logs e status
```

---

## 🔍 Logcat: filtros úteis

| Filtro | O que mostra |
|---|---|
| `tag:SERVICE_CUSTOM` | O Foreground Service e as telas do Guia Prático 4 |
| `tag:SENSOR_DESAFIO` | Registro e cancelamento do listener do acelerômetro |
| `tag:THREADS_DEMO` / `tag:NOTIFICACOES_DEMO` / `tag:BATERIA_DEMO` | Registros das telas de demonstração |
| `package:mine` | Todos os logs do app |

---

## 🔐 Permissões

| Permissão | Por quê |
|---|---|
| `FOREGROUND_SERVICE` | Permite rodar um serviço em primeiro plano |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+: tipo obrigatório do serviço (`dataSync`) |
| `POST_NOTIFICATIONS` | Android 13+: exibir notificações (pedida em tempo de execução) |
| `INTERNET` | Necessária para o app enxergar a rede ativa |
| `ACCESS_NETWORK_STATE` | Ler o tipo de conexão |

---

## ⚠ Problemas comuns

| Sintoma | Solução |
|---|---|
| A notificação não aparece | Abra **🔔 Notificações** e confira a permissão. |
| *Unresolved reference* após mudar o Gradle | Clique em **Sync Now**. |
| O acelerômetro fica parado no emulador | Use **⋯ → Virtual sensors** para girar o aparelho. |
| A rede aparece como "Sem conexão" | Confira se o emulador tem internet. |
| O serviço some depois de ~6h (Android 15+) | É o limite do tipo `dataSync`, tratado em `onTimeout()`. |

---
