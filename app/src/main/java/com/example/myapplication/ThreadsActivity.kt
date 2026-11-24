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
 * TELA 4 do Guia Prático 4 — demonstra a “Armadilha Clássica de Performance”.
 *
 * Uma Activity ou um Service não cria uma thread separada automaticamente.
 * Por padrão, o código executado por uma Activity roda na Main Thread, que também
 * é responsável por atualizar a interface. Se ela ficar ocupada com uma tarefa
 * demorada, a tela deixa de responder até que o trabalho termine.
 */
class ThreadsActivity : AppCompatActivity() {

    // Referência ao TextView que mostra o tempo decorrido desde que a tela foi aberta.
    private lateinit var txtContador: TextView

    // Referência ao TextView que apresenta o resultado de cada demonstração.
    private lateinit var txtResultado: TextView

    // Componente auxiliar que exibe mensagens de log na própria tela.
    private lateinit var miniLog: MiniLog

    // Guarda o instante em que a tela foi iniciada, usando um relógio monotônico.
    private var inicioTela = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prepara a tela e define o título e a identificação desta etapa do guia.
        prepararTela(
            R.layout.activity_threads,
            "Main Thread vs Corrotinas",
            "Guia Prático 4 • Passo 4"
        )

        // Localiza os elementos da interface definidos no layout.
        txtContador = findViewById(R.id.txtContadorUi)
        txtResultado = findViewById(R.id.txtResultadoThreads)

        // Cria o log visual, associado ao TextView indicado no layout.
        miniLog = MiniLog(findViewById(R.id.txtLogThreads), "THREADS_DEMO")

        // Registra o horário inicial para calcular o tempo exibido pelo contador.
        inicioTela = SystemClock.elapsedRealtime()

        // “Termômetro” da Main Thread: atualiza o contador a cada 100 milissegundos.
        // A atualização da interface acontece na Main Thread.
        // Se ela for bloqueada, este contador e as animações da tela também param.
        lifecycleScope.launch {
            // Executa o bloco enquanto a Activity estiver no estado STARTED ou superior.
            // O bloco é suspenso automaticamente quando a tela deixa de estar visível.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    // Calcula quantos segundos se passaram desde a abertura da tela.
                    val s = (SystemClock.elapsedRealtime() - inicioTela) / 1000.0

                    // Atualiza o texto do contador. A formatação mantém uma casa decimal.
                    txtContador.text = "%.1f s".format(s)

                    // Suspende esta corrotina por 100 ms antes da próxima atualização.
                    // delay não bloqueia a thread enquanto aguarda.
                    delay(100)
                }
            }
        }

        // ❌ Demonstração incorreta: o trabalho demorado roda na Main Thread.
        // O listener de clique é chamado na Main Thread; por isso, Thread.sleep
        // impede temporariamente a interface de processar atualizações e toques.
        findViewById<Button>(R.id.btnTravarMain).setOnClickListener {
            // Registra no log visual qual thread está executando o listener.
            miniLog.add(
                "❌ Thread.sleep(3000) na thread '${Thread.currentThread().name}'"
            )

            // Registra no repositório que a demonstração de bloqueio começou.
            MonitorRepository.log(
                TipoLog.AVISO,
                "Demonstração: bloqueando a Main Thread por 3s"
            )

            // Marca o início para medir por quanto tempo a thread ficará bloqueada.
            val inicio = SystemClock.elapsedRealtime()

            // Simula um processamento demorado bloqueando a Main Thread por 3 segundos.
            // Durante essa espera, a interface não consegue atualizar o contador.
            Thread.sleep(3000)

            // Calcula o tempo total decorrido durante a espera.
            val duracao = SystemClock.elapsedRealtime() - inicio

            // Informa que a tela congelou durante o bloqueio.
            // Um bloqueio longo pode levar o Android a exibir um aviso de ANR.
            txtResultado.text =
                "❌ A Main Thread ficou bloqueada por $duracao ms: " +
                "o contador e o círculo CONGELARAM. " +
                "Acima de 5 s o Android mostraria um ANR."

            // Registra que a Main Thread voltou a executar após a espera.
            miniLog.add("Main Thread liberada após $duracao ms")

            // Registra o bloqueio como erro/demonstração de risco de ANR.
            MonitorRepository.log(
                TipoLog.ERRO,
                "Main Thread ficou bloqueada por $duracao ms (risco de ANR)"
            )
        }

        // ✅ Demonstração recomendada: executa o trabalho em Dispatchers.IO.
        // A interface continua disponível enquanto a tarefa aguarda ou processa dados.
        findViewById<Button>(R.id.btnUsarIO).setOnClickListener {
            // Inicia uma corrotina vinculada ao ciclo de vida desta Activity.
            lifecycleScope.launch {
                // Atualiza a interface antes de iniciar o trabalho em segundo plano.
                txtResultado.text =
                    "⏳ Trabalhando em segundo plano... " +
                    "repare que o contador continua andando."

                // Registra que o trabalho em IO foi iniciado.
                miniLog.add("✅ launch + withContext(Dispatchers.IO) disparado")

                // Marca o instante inicial para medir a duração total da operação.
                val inicio = SystemClock.elapsedRealtime()

                // Muda a execução para uma thread apropriada a operações de entrada e saída.
                // Ao sair do bloco, a corrotina retorna ao contexto anterior
                // (a Main Thread, neste caso) para atualizar a interface.
                val threadUsada = withContext(Dispatchers.IO) {
                    // Simula o mesmo trabalho demorado, agora fora da Main Thread.
                    Thread.sleep(3000)

                    // Guarda o nome da thread usada para mostrá-lo no resultado.
                    Thread.currentThread().name
                }

                // Calcula a duração total desde o início até a conclusão do trabalho.
                val duracao = SystemClock.elapsedRealtime() - inicio

                // Este trecho volta a executar na Main Thread e pode atualizar a interface.
                txtResultado.text =
                    "✅ Concluído em $duracao ms na thread '$threadUsada'. " +
                    "A tela continuou fluida o tempo todo."

                // Registra em qual thread ocorreu o trabalho e que o resultado voltou à Main Thread.
                miniLog.add(
                    "Executado em '$threadUsada'; resultado voltou para a Main Thread"
                )

                // Registra no repositório que o trabalho em segundo plano terminou com sucesso.
                MonitorRepository.log(
                    TipoLog.SUCESSO,
                    "Demonstração: 3s de trabalho em $threadUsada sem travar a tela"
                )
            }
        }

        // Registra a abertura da tela e o nome da thread que está executando este código.
        miniLog.add("Tela aberta. Main Thread = '${Thread.currentThread().name}'")
    }
}