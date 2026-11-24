# Sync Monitor — Roteiro de apresentação (Mayumi, Daniel, Vinicius, Rodrigo, Fabricia, Heloiza e Vitor)

**Tema:** Código de Alta Performance: Mobile — Guia Prático 4 (Módulo 4) e Desafio 1 (Módulo 2)
**App:** Sync Monitor (`com.example.myapplication`)
**Duração sugerida:** 3 a 4 minutos por pessoa (~25 min no total)

> **Antes de começar:** rode o app no emulador e deixe o **Logcat** aberto com o filtro `tag:SERVICE_CUSTOM`. Na tela **Log de atividades**, toque em **Limpar**. Sempre volte ao **menu inicial** entre uma pessoa e outra: cada pessoa apresenta a **sua tela**.

---

## Mapa do app

| Tela (no menu inicial) | Quem apresenta | Arquivo principal |
|---|---|---|
| 🔋 Bateria e Doze Mode | Mayumi | `BateriaActivity.kt` |
| 🔔 Notificações e permissões | Daniel | `NotificacaoActivity.kt`, `AndroidManifest.xml` |
| ⚙️ Foreground Service | Vinicius, Fabricia e Heloiza | `ServicoActivity.kt`, `SincronizacaoService.kt` |
| 📜 Log de atividades | Vinicius e Heloiza | `LogActivity.kt`, `MonitorRepository.kt` |
| 🧵 Main Thread vs Corrotinas | Rodrigo | `ThreadsActivity.kt` |
| 📱 Desafio 1 — Acelerômetro | Vitor | `SensorActivity.kt` |

Toda tela tem, no topo, o cartão **"📘 O que esta tela demonstra"**. Use-o como ponto de partida da sua fala.

---

## 1. Mayumi — O problema: tarefas longas e bateria
**Tela:** menu inicial → **🔋 Bateria e Doze Mode**

- Apresente o app pelo **menu inicial**: cada cartão é um conceito, organizado por módulo.
- O que é um **Service**: um componente sem tela, feito para operações longas (música, GPS, sincronização).
- Por que o Android restringe o segundo plano: **Doze Mode** e **App Standby Buckets**.
- **Demonstração:** leia o cartão **"Situação atual do aparelho"**: bateria, economia de energia, Doze, otimização e **bucket** do app (ACTIVE enquanto está em uso).
- Use o cartão **"Qual ferramenta usar?"** para comparar **Foreground Service**, **WorkManager** e **Bound Service**.
- Conclua: escolhemos um **Foreground Service** porque a tarefa é contínua e o usuário precisa *ver* que ela está rodando.

## 2. Daniel — Manifesto, permissões e canais
**Tela:** **🔔 Notificações e permissões** (+ `AndroidManifest.xml` no Android Studio)

- No manifesto:
  - `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_DATA_SYNC` (Android 14+ exige o tipo);
  - `POST_NOTIFICATIONS` (Android 13+);
  - `INTERNET` e `ACCESS_NETWORK_STATE`.
- A tag `<service>` usa `exported="false"` (nenhum outro app pode iniciá-lo) e `foregroundServiceType="dataSync"`.
- **Demonstração:** cartão **"Situação da permissão"** e botão **Solicitar permissão**. Explique o que acontece se o usuário negar: o serviço roda, mas a notificação fica invisível.
- Cartão **"Canais de notificação"**: "Sincronização em andamento" (LOW, sem som) e "Alertas" (DEFAULT, com som). O usuário pode silenciar um sem afetar o outro.
- Toque em **🔔 Enviar notificação de teste** e mostre a notificação na gaveta.

## 3. Vinicius — Ciclo de vida do Service
**Telas:** **⚙️ Foreground Service** → **📜 Log de atividades** (filtro "Ciclo de vida")

- `onCreate()` → `onStartCommand()` → `onDestroy()`, e mais `onBind()`, que retorna `null` porque é um serviço iniciado, não vinculado.
- `startForeground()` precisa ser chamado em até **5 segundos**, senão o app cai.
- `START_STICKY`: o sistema recria o serviço se matar o processo.
- Callbacks extras: `onTaskRemoved()`, `onTrimMemory()` e `onTimeout()` (Android 15: limite de ~6h/dia para `dataSync`).
- **Demonstração:**
  1. Na tela **Foreground Service**, toque em **▶ Iniciar Serviço**.
  2. Toque em **📜 Ver log completo →** e use o filtro **"Ciclo de vida"** para mostrar a ordem dos callbacks.
  3. Volte, toque em **Parar Serviço** e mostre o `onDestroy()` no log.

## 4. Rodrigo — Corrotinas e threads (a armadilha clássica)
**Tela:** **🧵 Main Thread vs Corrotinas**

- **Um Service NÃO cria uma thread sozinho**: `onStartCommand` roda na Main Thread.
- No código: `CoroutineScope(SupervisorJob() + Dispatchers.IO)` e `serviceScope.cancel()` no `onDestroy`.
- Explique `delay()` (suspende sem bloquear) versus `Thread.sleep()` (bloqueia).
- **Demonstração ao vivo:**
  1. Mostre o **termômetro da interface**: o contador e o círculo giram, atualizados pela Main Thread.
  2. Toque em **❌ Executar na Main Thread**. O contador e o círculo **congelam** por 3 s.
  3. Toque em **✅ Executar com Corrotina em Dispatchers.IO**. Tudo continua fluido, e a mensagem mostra a thread `DefaultDispatcher-worker-N`.
  4. Explique o **ANR** (Application Not Responding) acima de 5 s.

## 5. Fabricia — Notificação rica
**Telas:** **⚙️ Foreground Service** (serviço rodando) + gaveta de notificações + **🔔 Notificações** (cartão "Anatomia")

- Recursos usados:
  - barra de progresso (`setProgress`);
  - cronômetro (`setUsesChronometer`);
  - texto expandido (`BigTextStyle`);
  - `setOngoing`, `setOnlyAlertOnce` e `setForegroundServiceBehavior(IMMEDIATE)`.
- **Botões de ação** com `PendingIntent.getService`: **Pausar/Retomar** e **Parar**.
- `setContentIntent`: tocar na notificação abre a **tela do serviço**, e o voltar leva ao menu (`TaskStackBuilder`).
- **Demonstração:**
  1. Inicie o serviço, aperte Home, abra a gaveta e expanda a notificação.
  2. Use **Pausar** e **Retomar** por ela.
  3. Toque na notificação para voltar ao app.
  4. Toque em **Parar** e mostre a notificação de **resumo** (ciclos, pacotes, KB, falhas, duração).

## 6. Heloiza — O trabalho em segundo plano e a robustez
**Telas:** **⚙️ Foreground Service** (métricas) → **📜 Log de atividades** (filtros "Tarefas" e "Avisos e erros")

Cada ciclo executa 5 etapas:
1. **Bateria**: leitura real via `BatteryManager`.
2. **Rede**: leitura real via `ConnectivityManager`.
3. **Telemetria**: um JSON com 150 leituras e a memória do app.
4. **Compressão GZIP real**: geralmente economiza mais de 80%.
5. **Envio ao servidor**: simulado, com 8% de falha aleatória.

- **Demonstração:**
  1. Acompanhe as **métricas** e mude o **intervalo** no slider com o serviço rodando.
  2. Ligue **"Simular falha de rede"** e mostre o **backoff exponencial** (2 s, 4 s, 8 s...) no filtro **"Avisos e erros"**.
  3. Toque em **Exportar** para compartilhar o log completo.
- **Arquitetura:** o `MonitorRepository` usa `StateFlow`, e as telas o observam com `repeatOnLifecycle(STARTED)`. Assim, várias telas leem o mesmo estado do serviço.

## 7. Vitor — Desafio 1: Listeners e ciclo de vida de sensores
**Tela:** **📱 Desafio 1 — Acelerômetro** (+ Android Profiler)

- **Padrão Observer**: o acelerômetro é o *Sujeito* e a tela é o *Observador* (`SensorEventListener`). Não há polling: a tela fica parada até o sistema chamar `onSensorChanged()`.
- O **registro** é feito no `onResume()` e o **cancelamento** no `onPause()`. Isso evita gasto de bateria e **Memory Leak**.
- **Demonstração:**
  1. Mostre X, Y e Z ao vivo, a magnitude (~9,81 m/s² = 1 g) e a frequência em Hz. Troque entre **NORMAL**, **UI**, **GAME** e **FASTEST**.
  2. Gire o aparelho em **⋯ → Virtual sensors** no emulador.
  3. Abra o **Profiler** (View → Tool Windows → Profiler) e a linha **CPU**, com **FASTEST**.
  4. Aperte **Home**: a CPU do app **cai para ~0%**.
  5. Ligue **"Simular erro: NÃO cancelar no onPause()"** e repita: a CPU **continua ativa**. Esse é o problema evitado.
- **Encerramento:** volte ao menu inicial e resuma os temas.

---

## Perguntas que a turma pode fazer
- *Por que não usar só uma Thread?* Corrotinas são leves, canceláveis e estruturadas (cancelar o escopo cancela tudo).
- *O que acontece se eu não chamar startForeground?* O sistema lança `ForegroundServiceDidNotStartInTimeException` e encerra o app.
- *Por que dois canais?* O usuário pode silenciar os alertas sem esconder a notificação do serviço.
- *E se o usuário negar a notificação?* O serviço roda, mas a notificação não aparece.
- *Por que exported="false"?* Segurança: impede que outros apps iniciem ou parem o serviço.
- *Por que registrar o sensor no onResume e não no onCreate?* O onResume e o onPause espelham a tela visível. Registrar no onCreate deixaria o sensor ativo com a tela em segundo plano.
