package com.example.myapplication

import android.util.Log
import androidx.annotation.ColorRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Ponte de comunicação Service -> Tela.
 *
 * O Service escreve aqui e a MainActivity observa os StateFlows. Como é um "object"
 * (singleton do processo), Activity e Service enxergam a mesma instância sem precisar
 * de Bound Service, BroadcastReceiver ou banco de dados.
 */
object MonitorRepository {

    const val TAG = "SERVICE_CUSTOM"
    private const val MAX_LOGS = 300

    /** Categorias de log exibidas com cores diferentes na tela. */
    enum class TipoLog(val rotulo: String, @ColorRes val cor: Int) {
        CICLO_DE_VIDA("CICLO DE VIDA", R.color.log_ciclo_vida),
        TAREFA("TAREFA", R.color.log_tarefa),
        SUCESSO("SUCESSO", R.color.log_sucesso),
        AVISO("AVISO", R.color.log_aviso),
        ERRO("ERRO", R.color.log_erro),
        NOTIFICACAO("NOTIFICAÇÃO", R.color.log_notificacao),
        SISTEMA("SISTEMA", R.color.log_sistema)
    }

    data class LogEntry(
        val id: Long,
        val horario: Long,
        val tipo: TipoLog,
        val mensagem: String,
        val thread: String
    )

    enum class Status { PARADO, EXECUTANDO, PAUSADO }

    /** Fotografia do estado atual do serviço, desenhada no painel da tela. */
    data class EstadoServico(
        val status: Status = Status.PARADO,
        val ciclo: Int = 0,
        val inicioEm: Long = 0L,
        val tarefaAtual: String = "Aguardando início",
        val etapa: Int = 0,
        val totalEtapas: Int = 5,
        val progresso: Int = 0,
        val bateria: String = "—",
        val rede: String = "—",
        val kbEnviados: Double = 0.0,
        val pacotesEnviados: Int = 0,
        val falhas: Int = 0,
        val ultimaCompressao: String = "—",
        val intervaloSegundos: Int = 5,
        val threadAtual: String = "—"
    )

    private val proximoId = AtomicLong(0)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _estado = MutableStateFlow(EstadoServico())
    val estado: StateFlow<EstadoServico> = _estado.asStateFlow()

    /** Quando ligado, o envio ao servidor falha de propósito (demonstração de tratamento de erros). */
    @Volatile var simularFalhaDeRede: Boolean = false

    /** Registra no Logcat E na lista exibida na tela. Seguro para chamar de qualquer thread. */
    fun log(tipo: TipoLog, mensagem: String) {
        val thread = Thread.currentThread().name
        Log.i(TAG, "[${tipo.rotulo}] $mensagem (thread: $thread)")
        val novo = LogEntry(proximoId.getAndIncrement(), System.currentTimeMillis(), tipo, mensagem, thread)
        _logs.update { atual -> (listOf(novo) + atual).take(MAX_LOGS) }
    }

    fun atualizarEstado(transformacao: (EstadoServico) -> EstadoServico) {
        _estado.update(transformacao)
    }

    fun limparLogs() {
        _logs.value = emptyList()
    }
}
