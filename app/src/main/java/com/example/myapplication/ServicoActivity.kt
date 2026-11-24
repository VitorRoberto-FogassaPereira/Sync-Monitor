package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.MonitorRepository.Status
import com.example.myapplication.MonitorRepository.TipoLog
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TELA 1 do Guia Prático 4 — controla e acompanha o SincronizacaoService.
 */
class ServicoActivity : AppCompatActivity() {

    // Solicita a permissão de notificações em tempo de execução (Android 13+)
    private val pedirPermissaoNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
            MonitorRepository.log(
                if (concedida) TipoLog.SUCESSO else TipoLog.AVISO,
                if (concedida) "Permissão POST_NOTIFICATIONS concedida pelo usuário"
                else "Permissão de notificação NEGADA — o serviço roda, mas a notificação fica invisível"
            )
        }

    private lateinit var txtStatus: TextView
    private lateinit var txtTempoAtivo: TextView
    private lateinit var txtCiclo: TextView
    private lateinit var txtTarefa: TextView
    private lateinit var txtThread: TextView
    private lateinit var progresso: LinearProgressIndicator
    private lateinit var txtBateria: TextView
    private lateinit var txtRede: TextView
    private lateinit var txtPacotes: TextView
    private lateinit var txtKb: TextView
    private lateinit var txtCompressao: TextView
    private lateinit var txtFalhas: TextView
    private lateinit var txtIntervalo: TextView
    private lateinit var txtUltimoLog: TextView
    private lateinit var sliderIntervalo: Slider
    private lateinit var btnIniciarServico: Button
    private lateinit var btnPausar: Button
    private lateinit var btnPararServico: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararTela(R.layout.activity_servico, "Foreground Service", "Guia Prático 4 • Passo 1")
        vincularViews()
        configurarControles()
        observarServico()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Mostram que o Service sobrevive quando a tela sai de cena
    override fun onStart() {
        super.onStart()
        MonitorRepository.log(TipoLog.SISTEMA, "ServicoActivity.onStart() — tela visível")
    }

    override fun onStop() {
        super.onStop()
        val ativo = MonitorRepository.estado.value.status != Status.PARADO
        MonitorRepository.log(
            TipoLog.SISTEMA,
            "ServicoActivity.onStop() — tela fora de cena" + if (ativo) "; o Foreground Service continua trabalhando" else ""
        )
    }

    private fun vincularViews() {
        txtStatus = findViewById(R.id.txtStatus)
        txtTempoAtivo = findViewById(R.id.txtTempoAtivo)
        txtCiclo = findViewById(R.id.txtCiclo)
        txtTarefa = findViewById(R.id.txtTarefa)
        txtThread = findViewById(R.id.txtThread)
        progresso = findViewById(R.id.progresso)
        txtBateria = findViewById(R.id.txtBateria)
        txtRede = findViewById(R.id.txtRede)
        txtPacotes = findViewById(R.id.txtPacotes)
        txtKb = findViewById(R.id.txtKb)
        txtCompressao = findViewById(R.id.txtCompressao)
        txtFalhas = findViewById(R.id.txtFalhas)
        txtIntervalo = findViewById(R.id.txtIntervalo)
        txtUltimoLog = findViewById(R.id.txtUltimoLog)
        sliderIntervalo = findViewById(R.id.sliderIntervalo)
        btnIniciarServico = findViewById(R.id.btnIniciarServico)
        btnPausar = findViewById(R.id.btnPausar)
        btnPararServico = findViewById(R.id.btnPararServico)
    }

    private fun configurarControles() {
        // ACIONAMENTO DO SERVIÇO (Guia Prático 4)
        val intentServico = Intent(this, SincronizacaoService::class.java)

        sliderIntervalo.value = MonitorRepository.estado.value.intervaloSegundos.toFloat().coerceIn(2f, 10f)
        txtIntervalo.text = "Intervalo entre ciclos: ${sliderIntervalo.value.toInt()}s"
        sliderIntervalo.addOnChangeListener { _, valor, doUsuario ->
            txtIntervalo.text = "Intervalo entre ciclos: ${valor.toInt()}s"
            if (doUsuario && MonitorRepository.estado.value.status != Status.PARADO) {
                startService(
                    Intent(this, SincronizacaoService::class.java)
                        .setAction(SincronizacaoService.ACTION_ALTERAR_INTERVALO)
                        .putExtra(SincronizacaoService.EXTRA_INTERVALO, valor.toInt())
                )
            }
        }

        btnIniciarServico.setOnClickListener {
            intentServico.action = SincronizacaoService.ACTION_INICIAR
            intentServico.putExtra(SincronizacaoService.EXTRA_INTERVALO, sliderIntervalo.value.toInt())
            MonitorRepository.log(TipoLog.SISTEMA, "Usuário tocou em Iniciar → ContextCompat.startForegroundService()")
            // ContextCompat escolhe automaticamente startForegroundService (Android 8+) ou startService
            ContextCompat.startForegroundService(this, intentServico)
        }

        btnPausar.setOnClickListener {
            val acao = if (MonitorRepository.estado.value.status == Status.PAUSADO)
                SincronizacaoService.ACTION_RETOMAR else SincronizacaoService.ACTION_PAUSAR
            startService(Intent(this, SincronizacaoService::class.java).setAction(acao))
        }

        btnPararServico.setOnClickListener {
            MonitorRepository.log(TipoLog.SISTEMA, "Usuário tocou em Parar → stopService()")
            stopService(intentServico)
        }

        findViewById<MaterialSwitch>(R.id.switchFalhaRede).apply {
            isChecked = MonitorRepository.simularFalhaDeRede
            setOnCheckedChangeListener { _, ligado ->
                MonitorRepository.simularFalhaDeRede = ligado
                MonitorRepository.log(
                    if (ligado) TipoLog.AVISO else TipoLog.SUCESSO,
                    if (ligado) "Modo falha de rede LIGADO — os envios vão falhar para demonstrar o backoff exponencial"
                    else "Modo falha de rede DESLIGADO — envios voltam ao normal"
                )
            }
        }

        findViewById<Button>(R.id.btnVerLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    /** Observa os StateFlows do repositório somente enquanto a tela está visível (STARTED). */
    private fun observarServico() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { MonitorRepository.estado.collect { renderizarEstado(it) } }
                launch {
                    MonitorRepository.logs.collect { lista ->
                        val ultimo = lista.firstOrNull()
                        if (ultimo == null) {
                            txtUltimoLog.text = "Nenhuma atividade ainda."
                        } else {
                            txtUltimoLog.text = "[${ultimo.tipo.rotulo}] ${ultimo.mensagem}"
                            txtUltimoLog.setTextColor(ContextCompat.getColor(this@ServicoActivity, ultimo.tipo.cor))
                        }
                    }
                }
                launch {
                    while (true) { // atualiza o relógio de tempo ativo a cada segundo
                        atualizarTempoAtivo(MonitorRepository.estado.value)
                        delay(1000)
                    }
                }
            }
        }
    }

    private fun renderizarEstado(e: MonitorRepository.EstadoServico) {
        val (texto, cor) = when (e.status) {
            Status.EXECUTANDO -> "● EXECUTANDO" to R.color.status_executando
            Status.PAUSADO -> "● PAUSADO" to R.color.status_pausado
            Status.PARADO -> "● PARADO" to R.color.status_parado
        }
        txtStatus.text = texto
        txtStatus.setTextColor(ContextCompat.getColor(this, cor))
        txtCiclo.text = "Ciclo #${e.ciclo} • Etapa ${e.etapa}/${e.totalEtapas}"
        txtTarefa.text = e.tarefaAtual
        progresso.setProgressCompat(e.progresso, true)
        txtThread.text = "Thread: ${e.threadAtual}"
        txtBateria.text = e.bateria
        txtRede.text = e.rede
        txtPacotes.text = e.pacotesEnviados.toString()
        txtKb.text = "%.2f KB".format(e.kbEnviados)
        txtCompressao.text = e.ultimaCompressao
        txtFalhas.text = e.falhas.toString()

        btnIniciarServico.isEnabled = e.status == Status.PARADO
        btnPausar.isEnabled = e.status != Status.PARADO
        btnPararServico.isEnabled = e.status != Status.PARADO
        btnPausar.text = if (e.status == Status.PAUSADO) "Retomar" else "Pausar"
        atualizarTempoAtivo(e)
    }

    private fun atualizarTempoAtivo(e: MonitorRepository.EstadoServico) {
        val segundos = if (e.status == Status.PARADO || e.inicioEm == 0L) 0
        else (System.currentTimeMillis() - e.inicioEm) / 1000
        txtTempoAtivo.text = "%02d:%02d:%02d".format(segundos / 3600, (segundos % 3600) / 60, segundos % 60)
    }
}
