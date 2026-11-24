package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.MonitorRepository.Status
import kotlinx.coroutines.launch

/**
 * TELA INICIAL — menu de navegação.
 * Cada cartão abre uma tela que demonstra UM conceito da apostila.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararTela(
            R.layout.activity_main,
            titulo = "Sync Monitor",
            subtitulo = "Código de Alta Performance: Mobile",
            mostrarVoltar = false
        )

        abrirAoTocar(R.id.cardStatusHome, ServicoActivity::class.java)
        abrirAoTocar(R.id.cardSensor, SensorActivity::class.java)
        abrirAoTocar(R.id.cardServico, ServicoActivity::class.java)
        abrirAoTocar(R.id.cardLogs, LogActivity::class.java)
        abrirAoTocar(R.id.cardNotificacoes, NotificacaoActivity::class.java)
        abrirAoTocar(R.id.cardThreads, ThreadsActivity::class.java)
        abrirAoTocar(R.id.cardBateria, BateriaActivity::class.java)

        observarStatusDoServico()
    }

    private fun abrirAoTocar(idCartao: Int, tela: Class<*>) {
        findViewById<View>(idCartao).setOnClickListener { startActivity(Intent(this, tela)) }
    }

    /** Mostra no topo do menu se o serviço está rodando — útil ao voltar de outras telas. */
    private fun observarStatusDoServico() {
        val txt = findViewById<TextView>(R.id.txtStatusServicoHome)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MonitorRepository.estado.collect { e ->
                    val (texto, cor) = when (e.status) {
                        Status.EXECUTANDO -> "● EXECUTANDO — ciclo #${e.ciclo}" to R.color.status_executando
                        Status.PAUSADO -> "● PAUSADO — ciclo #${e.ciclo}" to R.color.status_pausado
                        Status.PARADO -> "● PARADO" to R.color.status_parado
                    }
                    txt.text = texto
                    txt.setTextColor(ContextCompat.getColor(this@MainActivity, cor))
                }
            }
        }
    }
}
