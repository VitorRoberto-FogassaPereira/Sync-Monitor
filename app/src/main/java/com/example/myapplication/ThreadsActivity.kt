package com.example.myapplication

import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.MonitorRepository.TipoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TELA 4 do Guia Prático 4 — a "Armadilha Clássica de Performance":
 * um Service (ou Activity) NÃO cria thread sozinho. Trabalho pesado na Main Thread congela a tela.
 */
class ThreadsActivity : AppCompatActivity() {

    private lateinit var txtContador: TextView
    private lateinit var txtResultado: TextView
    private lateinit var miniLog: MiniLog
    private var inicioTela = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararTela(R.layout.activity_threads, "Main Thread vs Corrotinas", "Guia Prático 4 • Passo 4")
        txtContador = findViewById(R.id.txtContadorUi)
        txtResultado = findViewById(R.id.txtResultadoThreads)
        miniLog = MiniLog(findViewById(R.id.txtLogThreads), "THREADS_DEMO")
        inicioTela = SystemClock.elapsedRealtime()

        // "Termômetro": a Main Thread atualiza o contador a cada 100 ms.
        // Se a Main Thread for bloqueada, o contador (e o círculo girando) PARAM.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val s = (SystemClock.elapsedRealtime() - inicioTela) / 1000.0
                    txtContador.text = "%.1f s".format(s)
                    delay(100)
                }
            }
        }

        // ❌ Errado: trabalho pesado direto na Main Thread
        findViewById<Button>(R.id.btnTravarMain).setOnClickListener {
            miniLog.add("❌ Thread.sleep(3000) na thread '${Thread.currentThread().name}'")
            MonitorRepository.log(TipoLog.AVISO, "Demonstração: bloqueando a Main Thread por 3s")
            val inicio = SystemClock.elapsedRealtime()
            Thread.sleep(3000) // simula um processamento pesado
            val duracao = SystemClock.elapsedRealtime() - inicio
            txtResultado.text = "❌ A Main Thread ficou bloqueada por $duracao ms: o contador e o círculo CONGELARAM. Acima de 5 s o Android mostraria um ANR."
            miniLog.add("Main Thread liberada após $duracao ms")
            MonitorRepository.log(TipoLog.ERRO, "Main Thread ficou bloqueada por $duracao ms (risco de ANR)")
        }

        // ✅ Certo: o mesmo trabalho em uma Corrotina no Dispatchers.IO
        findViewById<Button>(R.id.btnUsarIO).setOnClickListener {
            lifecycleScope.launch {
                txtResultado.text = "⏳ Trabalhando em segundo plano... repare que o contador continua andando."
                miniLog.add("✅ launch + withContext(Dispatchers.IO) disparado")
                val inicio = SystemClock.elapsedRealtime()
                val threadUsada = withContext(Dispatchers.IO) {
                    Thread.sleep(3000) // o mesmo processamento pesado, agora fora da Main Thread
                    Thread.currentThread().name
                }
                val duracao = SystemClock.elapsedRealtime() - inicio
                txtResultado.text = "✅ Concluído em $duracao ms na thread '$threadUsada'. A tela continuou fluida o tempo todo."
                miniLog.add("Executado em '$threadUsada'; resultado voltou para a Main Thread")
                MonitorRepository.log(TipoLog.SUCESSO, "Demonstração: 3s de trabalho em $threadUsada sem travar a tela")
            }
        }

        miniLog.add("Tela aberta. Main Thread = '${Thread.currentThread().name}'")
    }
}
