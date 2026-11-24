// ARQUIVO: SincronizacaoService.kt
package com.example.myapplication

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.app.TaskStackBuilder
import com.example.myapplication.MonitorRepository.Status
import com.example.myapplication.MonitorRepository.TipoLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.GZIPOutputStream
import kotlin.math.min
import kotlin.random.Random

class SincronizacaoService : Service() {

    companion object {
        // Canais definidos em Notificacoes.kt (compartilhados com a tela de Notificações)
        const val CHANNEL_ID = Notificacoes.CANAL_SERVICO
        const val CHANNEL_ALERTAS_ID = Notificacoes.CANAL_ALERTAS
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_ALERTA_ID = 1002
        const val NOTIFICATION_RESUMO_ID = 1003

        // Ações recebidas via Intent (pela tela ou pelos botões da notificação)
        const val ACTION_INICIAR = "com.example.myapplication.action.INICIAR"
        const val ACTION_PAUSAR = "com.example.myapplication.action.PAUSAR"
        const val ACTION_RETOMAR = "com.example.myapplication.action.RETOMAR"
        const val ACTION_PARAR = "com.example.myapplication.action.PARAR"
        const val ACTION_ALTERAR_INTERVALO = "com.example.myapplication.action.INTERVALO"
        const val EXTRA_INTERVALO = "extra_intervalo_segundos"

        private val ETAPAS = listOf(
            "Lendo nível de bateria",
            "Verificando conectividade",
            "Coletando telemetria",
            "Compactando pacote (GZIP)",
            "Enviando ao servidor"
        )
    }

    // Escopo dedicado de Corrotinas em thread de I/O (nunca bloqueia a Main Thread)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var jobTrabalho: Job? = null

    @Volatile private var pausado = false
    @Volatile private var intervaloSegundos = 5
    private var inicioEm = 0L
    private var falhasSeguidas = 0
    private var alertaBateriaEnviado = false
    private var pacoteAtual = ByteArray(0)
    private var redeDisponivel = true

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    // ------------------------------------------------------------------ Ciclo de vida

    override fun onCreate() {
        super.onCreate()
        MonitorRepository.log(TipoLog.CICLO_DE_VIDA, "onCreate() — instância do Service criada")
        criarCanaisDeNotificacao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MonitorRepository.log(
            TipoLog.CICLO_DE_VIDA,
            "onStartCommand() — ação: ${intent?.action?.substringAfterLast('.') ?: "nenhuma (recriado pelo sistema)"}, startId=$startId"
        )
        when (intent?.action) {
            ACTION_PAUSAR -> pausar()
            ACTION_RETOMAR -> retomar()
            ACTION_PARAR -> {
                MonitorRepository.log(TipoLog.NOTIFICACAO, "Botão \"Parar\" acionado pela notificação")
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_ALTERAR_INTERVALO -> {
                intervaloSegundos = intent.getIntExtra(EXTRA_INTERVALO, intervaloSegundos)
                MonitorRepository.atualizarEstado { it.copy(intervaloSegundos = intervaloSegundos) }
                MonitorRepository.log(TipoLog.SISTEMA, "Intervalo entre ciclos alterado para ${intervaloSegundos}s")
            }
            else -> iniciar(intent)
        }
        // START_STICKY: se o sistema matar o processo por falta de memória, o serviço é recriado
        return START_STICKY
    }

    override fun onDestroy() {
        // Cancela todas as corrotinas para evitar vazamento de memória e consumo de bateria
        serviceScope.cancel()
        val estado = MonitorRepository.estado.value
        val duracao = if (inicioEm > 0) (System.currentTimeMillis() - inicioEm) / 1000 else 0
        MonitorRepository.log(
            TipoLog.CICLO_DE_VIDA,
            "onDestroy() — serviço encerrado e corrotinas canceladas após ${duracao}s"
        )
        MonitorRepository.atualizarEstado {
            it.copy(status = Status.PARADO, tarefaAtual = "Serviço encerrado", progresso = 0, threadAtual = "—")
        }
        if (estado.ciclo > 0) enviarNotificacaoDeResumo(estado, duracao)
        super.onDestroy()
    }

    // Serviço iniciado (não vinculado): não oferece Binder
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        MonitorRepository.log(
            TipoLog.SISTEMA,
            "onTaskRemoved() — app removido dos recentes, mas o Foreground Service continua vivo"
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val nivel = if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) "app em segundo plano" else "pressão de memória"
        MonitorRepository.log(TipoLog.SISTEMA, "onTrimMemory($level) — sistema pediu para liberar memória ($nivel)")
    }

    /** Android 15+: serviços do tipo dataSync têm limite de ~6h por dia. Ao estourar, o sistema chama isto. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        MonitorRepository.log(
            TipoLog.AVISO,
            "onTimeout() — limite diário do tipo dataSync atingido (Android 15+). Encerrando para respeitar a bateria."
        )
        stopSelf()
    }

    // ------------------------------------------------------------------ Controle

    private fun iniciar(intent: Intent?) {
        if (jobTrabalho?.isActive == true) {
            MonitorRepository.log(TipoLog.AVISO, "Serviço já está em execução — novo início ignorado")
            return
        }
        if (intent == null) {
            MonitorRepository.log(TipoLog.SISTEMA, "Serviço recriado automaticamente pelo sistema (efeito do START_STICKY)")
        }
        intervaloSegundos = intent?.getIntExtra(EXTRA_INTERVALO, intervaloSegundos) ?: intervaloSegundos
        inicioEm = System.currentTimeMillis()
        pausado = false
        falhasSeguidas = 0
        alertaBateriaEnviado = false
        MonitorRepository.atualizarEstado {
            MonitorRepository.EstadoServico(
                status = Status.EXECUTANDO,
                inicioEm = inicioEm,
                intervaloSegundos = intervaloSegundos,
                tarefaAtual = "Preparando primeiro ciclo"
            )
        }

        // Promove o serviço para Foreground nos primeiros 5 segundos (obrigatório no Android)
        val notificacao = construirNotificacao()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notificacao)
        }
        MonitorRepository.log(TipoLog.NOTIFICACAO, "startForeground() — notificação fixa publicada, serviço agora é de primeiro plano")

        jobTrabalho = serviceScope.launch { executarCiclos() }
    }

    private fun pausar() {
        if (jobTrabalho?.isActive != true) return
        pausado = true
        MonitorRepository.atualizarEstado { it.copy(status = Status.PAUSADO, tarefaAtual = "Pausado pelo usuário") }
        MonitorRepository.log(TipoLog.AVISO, "Sincronização PAUSADA — a corrotina continua viva, mas não executa etapas")
        atualizarNotificacao()
    }

    private fun retomar() {
        if (jobTrabalho?.isActive != true) return
        pausado = false
        MonitorRepository.atualizarEstado { it.copy(status = Status.EXECUTANDO, tarefaAtual = "Retomando...") }
        MonitorRepository.log(TipoLog.SUCESSO, "Sincronização RETOMADA")
        atualizarNotificacao()
    }

    // ------------------------------------------------------------------ Trabalho em segundo plano

    private suspend fun executarCiclos() {
        val escopo = kotlin.coroutines.coroutineContext
        while (escopo.isActive) {
            aguardarSePausado()
            val ciclo = MonitorRepository.estado.value.ciclo + 1
            val inicioCiclo = System.currentTimeMillis()
            MonitorRepository.atualizarEstado { it.copy(ciclo = ciclo) }
            MonitorRepository.log(TipoLog.TAREFA, "▶ Ciclo #$ciclo iniciado")

            for ((indice, nome) in ETAPAS.withIndex()) {
                aguardarSePausado()
                MonitorRepository.atualizarEstado {
                    it.copy(
                        tarefaAtual = nome,
                        etapa = indice + 1,
                        progresso = indice * 100 / ETAPAS.size,
                        threadAtual = Thread.currentThread().name
                    )
                }
                atualizarNotificacao()
                executarEtapa(indice)
                delay(intervaloSegundos * 1000L / ETAPAS.size)
            }

            MonitorRepository.atualizarEstado { it.copy(progresso = 100, tarefaAtual = "Ciclo #$ciclo concluído") }
            atualizarNotificacao()
            MonitorRepository.log(
                TipoLog.SUCESSO,
                "✔ Ciclo #$ciclo concluído em ${System.currentTimeMillis() - inicioCiclo} ms"
            )
        }
    }

    private suspend fun aguardarSePausado() {
        while (pausado) delay(300)
    }

    private suspend fun executarEtapa(indice: Int) {
        when (indice) {
            0 -> lerBateria()
            1 -> verificarRede()
            2 -> coletarTelemetria()
            3 -> compactarPacote()
            4 -> enviarAoServidor()
        }
    }

    /** Etapa 1 — leitura REAL da bateria do aparelho via BatteryManager. */
    private fun lerBateria() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val nivel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val carregando = bm.isCharging
        val texto = "$nivel%" + if (carregando) " ⚡" else ""
        MonitorRepository.atualizarEstado { it.copy(bateria = texto) }
        MonitorRepository.log(TipoLog.TAREFA, "Bateria em $nivel% (${if (carregando) "carregando" else "na bateria"})")

        if (nivel in 1..20 && !carregando && !alertaBateriaEnviado) {
            alertaBateriaEnviado = true
            MonitorRepository.log(TipoLog.AVISO, "Bateria baixa! Em um app real, reduziríamos a frequência de sincronização")
            enviarAlertaBateriaBaixa(nivel)
        }
    }

    /** Etapa 2 — verificação REAL do tipo de conexão via ConnectivityManager. */
    private fun verificarRede() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val tipo = when {
            caps == null -> "Sem conexão"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Outra"
        }
        redeDisponivel = caps != null
        val medida = if (caps?.isMeteredCompat() == true) " (tarifada)" else ""
        MonitorRepository.atualizarEstado { it.copy(rede = tipo) }
        if (redeDisponivel) {
            MonitorRepository.log(TipoLog.TAREFA, "Conectividade: $tipo$medida")
        } else {
            MonitorRepository.log(TipoLog.AVISO, "Sem conexão — o envio deste ciclo será adiado")
        }
    }

    private fun NetworkCapabilities.isMeteredCompat(): Boolean =
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

    /** Etapa 3 — monta um pacote de telemetria com dados reais do processo + leituras simuladas. */
    private fun coletarTelemetria() {
        val rt = Runtime.getRuntime()
        val memoriaUsadaMb = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0)
        val leituras = (1..150).joinToString(",") {
            """{"sensor":"acelerometro","t":${System.currentTimeMillis()},"x":${Random.nextInt(-10, 10)},"y":${Random.nextInt(-10, 10)},"z":${Random.nextInt(-10, 10)}}"""
        }
        val json = """{"dispositivo":"${Build.MODEL}","android":${Build.VERSION.SDK_INT},"memoriaMb":${"%.1f".format(Locale.US, memoriaUsadaMb)},"leituras":[$leituras]}"""
        pacoteAtual = json.toByteArray()
        MonitorRepository.log(
            TipoLog.TAREFA,
            "Telemetria coletada: 150 leituras, ${pacoteAtual.size} bytes — memória do app: ${"%.1f".format(memoriaUsadaMb)} MB"
        )
    }

    /** Etapa 4 — compressão REAL com GZIP, mostrando a economia de dados. */
    private fun compactarPacote() {
        val original = pacoteAtual.size
        val saida = ByteArrayOutputStream()
        GZIPOutputStream(saida).use { it.write(pacoteAtual) }
        pacoteAtual = saida.toByteArray()
        val economia = 100 - (pacoteAtual.size * 100 / original.coerceAtLeast(1))
        val resumo = "$original B → ${pacoteAtual.size} B (-$economia%)"
        MonitorRepository.atualizarEstado { it.copy(ultimaCompressao = resumo) }
        MonitorRepository.log(TipoLog.TAREFA, "Pacote compactado com GZIP: $resumo")
    }

    /** Etapa 5 — envio simulado com falhas aleatórias e retentativa com backoff exponencial. */
    private suspend fun enviarAoServidor() {
        if (!redeDisponivel) return
        delay(Random.nextLong(150, 450)) // latência simulada
        val falhou = MonitorRepository.simularFalhaDeRede || Random.nextInt(100) < 8
        if (falhou) {
            falhasSeguidas++
            val espera = min(1 shl falhasSeguidas, 16) // 2s, 4s, 8s, 16s...
            MonitorRepository.atualizarEstado { it.copy(falhas = it.falhas + 1) }
            MonitorRepository.log(
                TipoLog.ERRO,
                "Falha no envio (HTTP 503 simulado). Backoff exponencial: nova tentativa em ${espera}s"
            )
            MonitorRepository.atualizarEstado { it.copy(tarefaAtual = "Aguardando backoff (${espera}s)") }
            atualizarNotificacao()
            delay(espera * 1000L)
        } else {
            falhasSeguidas = 0
            val kb = pacoteAtual.size / 1024.0
            MonitorRepository.atualizarEstado {
                it.copy(kbEnviados = it.kbEnviados + kb, pacotesEnviados = it.pacotesEnviados + 1)
            }
            MonitorRepository.log(TipoLog.SUCESSO, "Pacote enviado ao servidor (${"%.2f".format(kb)} KB) — HTTP 200")
        }
    }

    // ------------------------------------------------------------------ Notificações

    private fun criarCanaisDeNotificacao() {
        Notificacoes.criarCanais(this)
        MonitorRepository.log(TipoLog.NOTIFICACAO, "2 canais de notificação garantidos (serviço: LOW, alertas: DEFAULT)")
    }

    private fun pendingIntentServico(acao: String, codigo: Int): PendingIntent =
        PendingIntent.getService(
            this, codigo,
            Intent(this, SincronizacaoService::class.java).setAction(acao),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Tocar na notificação abre a tela do Serviço, com a tela inicial por baixo (botão voltar funciona). */
    private fun pendingIntentAbrirApp(): PendingIntent =
        TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(Intent(this, ServicoActivity::class.java))
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!

    private fun construirNotificacao(): android.app.Notification {
        val e = MonitorRepository.estado.value
        val titulo = when {
            pausado -> "⏸ Sincronização pausada"
            e.ciclo == 0 -> "Sincronização de Alta Performance"
            else -> "Sincronizando — ciclo #${e.ciclo}"
        }
        val texto = if (pausado) "Toque em Retomar para continuar" else "Etapa ${e.etapa}/${e.totalEtapas}: ${e.tarefaAtual}"
        val detalhes = """
            $texto
            🔋 Bateria: ${e.bateria}   📶 Rede: ${e.rede}
            📦 Enviados: ${e.pacotesEnviados} pacotes (${"%.2f".format(e.kbEnviados)} KB)
            🗜 Última compressão: ${e.ultimaCompressao}
            ⚠ Falhas: ${e.falhas}   ⏱ Intervalo: ${e.intervaloSegundos}s
        """.trimIndent()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setSubText("Coletando telemetria")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detalhes))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setColor(0xFF1E88E5.toInt())
            .setOngoing(true) // Impede que o usuário deslize e apague enquanto ativo
            .setOnlyAlertOnce(true) // Atualizações não fazem som/vibração de novo
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setWhen(inicioEm)
            .setUsesChronometer(true) // Cronômetro mostra há quanto tempo o serviço está ativo
            .setShowWhen(true)
            .setContentIntent(pendingIntentAbrirApp()) // Toque abre o app
            .setProgress(100, e.progresso, false) // Barra de progresso da etapa atual do ciclo

        if (pausado) {
            builder.addAction(android.R.drawable.ic_media_play, "Retomar", pendingIntentServico(ACTION_RETOMAR, 2))
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "Pausar", pendingIntentServico(ACTION_PAUSAR, 1))
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", pendingIntentServico(ACTION_PARAR, 3))
        return builder.build()
    }

    private fun atualizarNotificacao() {
        notificationManager.notify(NOTIFICATION_ID, construirNotificacao())
    }

    private fun enviarAlertaBateriaBaixa(nivel: Int) {
        val n = NotificationCompat.Builder(this, CHANNEL_ALERTAS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Bateria baixa ($nivel%)")
            .setContentText("A sincronização continua, mas considere conectar o carregador.")
            .setContentIntent(pendingIntentAbrirApp())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ALERTA_ID, n)
        MonitorRepository.log(TipoLog.NOTIFICACAO, "Alerta de bateria baixa enviado no canal de alertas")
    }

    private fun enviarNotificacaoDeResumo(e: MonitorRepository.EstadoServico, duracao: Long) {
        val resumo = "${e.ciclo} ciclos • ${e.pacotesEnviados} pacotes • ${"%.2f".format(e.kbEnviados)} KB • ${e.falhas} falhas • ${duracao}s"
        val n = NotificationCompat.Builder(this, CHANNEL_ALERTAS_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Sincronização encerrada")
            .setContentText(resumo)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Resumo da sessão:\n$resumo"))
            .setContentIntent(pendingIntentAbrirApp())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_RESUMO_ID, n)
        MonitorRepository.log(TipoLog.NOTIFICACAO, "Notificação de resumo publicada: $resumo")
    }
}
