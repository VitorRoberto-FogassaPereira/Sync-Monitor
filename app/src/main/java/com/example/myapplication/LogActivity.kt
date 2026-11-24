package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MonitorRepository.TipoLog
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TELA 2 do Guia Prático 4 — tudo o que o serviço faz em segundo plano, em tempo real.
 */
class LogActivity : AppCompatActivity() {

    private val logAdapter = LogAdapter()
    private var filtroAtual: Set<TipoLog>? = null // null = todos

    private lateinit var txtTituloLogs: TextView
    private lateinit var txtLogVazio: TextView
    private lateinit var recyclerLogs: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararTela(R.layout.activity_log, "Log de atividades", "Guia Prático 4 • Passo 2")

        txtTituloLogs = findViewById(R.id.txtTituloLogs)
        txtLogVazio = findViewById(R.id.txtLogVazio)
        recyclerLogs = findViewById(R.id.recyclerLogs)
        recyclerLogs.layoutManager = LinearLayoutManager(this)
        recyclerLogs.adapter = logAdapter

        findViewById<ChipGroup>(R.id.chipsFiltro).setOnCheckedStateChangeListener { _, ids ->
            filtroAtual = when (ids.firstOrNull()) {
                R.id.chipCicloVida -> setOf(TipoLog.CICLO_DE_VIDA)
                R.id.chipTarefas -> setOf(TipoLog.TAREFA, TipoLog.SUCESSO)
                R.id.chipProblemas -> setOf(TipoLog.AVISO, TipoLog.ERRO)
                R.id.chipNotificacoes -> setOf(TipoLog.NOTIFICACAO, TipoLog.SISTEMA)
                else -> null
            }
            exibirLogs(MonitorRepository.logs.value)
        }
        findViewById<Button>(R.id.btnLimpar).setOnClickListener { MonitorRepository.limparLogs() }
        findViewById<Button>(R.id.btnCompartilhar).setOnClickListener { exportarLogs() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MonitorRepository.logs.collect { exibirLogs(it) }
            }
        }
    }

    private fun exibirLogs(todos: List<MonitorRepository.LogEntry>) {
        val filtrados = filtroAtual?.let { f -> todos.filter { it.tipo in f } } ?: todos
        val estavaNoTopo = !recyclerLogs.canScrollVertically(-1)
        logAdapter.submitList(filtrados) {
            if (estavaNoTopo) recyclerLogs.scrollToPosition(0) // acompanha os novos registros
        }
        txtTituloLogs.text = "${filtrados.size} registros"
        txtLogVazio.visibility = if (filtrados.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun exportarLogs() {
        val formato = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val texto = MonitorRepository.logs.value.reversed().joinToString("\n") {
            "${formato.format(Date(it.horario))} [${it.tipo.rotulo}] ${it.mensagem} (${it.thread})"
        }
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Log do Sync Monitor")
            putExtra(Intent.EXTRA_TEXT, texto.ifEmpty { "Sem registros." })
        }
        startActivity(Intent.createChooser(enviar, "Exportar log"))
        MonitorRepository.log(TipoLog.SISTEMA, "Log exportado via Intent.ACTION_SEND")
    }
}
