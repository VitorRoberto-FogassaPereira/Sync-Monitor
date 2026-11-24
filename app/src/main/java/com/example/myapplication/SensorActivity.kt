
package com.example.myapplication

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Activity responsável pelo monitoramento do acelerômetro.
 *
 * Implementa SensorEventListener para receber notificações do sistema
 * sempre que ocorrer uma nova leitura ou alteração na precisão do sensor.
 *
 * Padrão Observer:
 * - Subject (Sujeito): SensorManager / sensor do dispositivo.
 * - Observer (Observador): SensorActivity.
 *
 * A Activity não consulta o sensor continuamente (polling).
 * Ela registra um listener e aguarda os eventos enviados pelo sistema.
 */
class SensorActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "SENSOR_DESAFIO"
        private const val G = SensorManager.GRAVITY_EARTH
    }

    // ==================== GERENCIAMENTO DO SENSOR ====================

    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null

    // Armazena a frequência de atualização selecionada pelo usuário.
    private var delayAtual = SensorManager.SENSOR_DELAY_UI
    private var nomeDelayAtual = "SENSOR_DELAY_UI"

    // Controla o estado do registro do listener e a simulação de erro.
    private var listenerRegistrado = false
    private var simularErroLeak = false

    // ==================== ESTATÍSTICAS DO SENSOR ====================

    private var eventosTotais = 0L
    private var eventosNoSegundo = 0
    private var inicioJanelaHz = 0L

    // Mantém os últimos registros de execução exibidos na interface.
    private val logs = ArrayDeque<String>()
    private val formatoHora = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // ==================== COMPONENTES DA INTERFACE ====================

    private lateinit var txtStatusListener: TextView
    private lateinit var txtInfoSensor: TextView
    private lateinit var txtX: TextView
    private lateinit var txtY: TextView
    private lateinit var txtZ: TextView
    private lateinit var barraX: LinearProgressIndicator
    private lateinit var barraY: LinearProgressIndicator
    private lateinit var barraZ: LinearProgressIndicator
    private lateinit var txtMagnitude: TextView
    private lateinit var txtOrientacao: TextView
    private lateinit var txtEventos: TextView
    private lateinit var txtHz: TextView
    private lateinit var txtLog: TextView

    // ==================== INICIALIZAÇÃO DA ACTIVITY ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prepararTela(
            R.layout.activity_sensor,
            "Desafio 1 — Acelerômetro",
            "Módulo 2 • Listeners e ciclo de vida"
        )

        vincularViews()

        // Obtém o serviço responsável pelo gerenciamento dos sensores.
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Tenta obter o acelerômetro disponível no dispositivo.
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        exibirInfoDoSensor()
        configurarControles()

        registrarLog(
            "onCreate() — tela criada, SensorManager obtido " +
            "(listener ainda NÃO registrado)"
        )
    }

    // ==================== CICLO DE VIDA DO LISTENER ====================

    /**
     * Chamado quando a Activity volta a ficar visível e ativa.
     * Registra o listener para começar a receber os eventos do acelerômetro.
     */
    override fun onResume() {
        super.onResume()
        registrarListener("onResume()")
    }

    /**
     * Chamado quando a Activity perde o primeiro plano.
     *
     * O comportamento correto é cancelar o registro do listener,
     * evitando processamento desnecessário e consumo de bateria.
     *
     * A simulação de erro permite demonstrar o que acontece quando
     * o listener não é cancelado nesse momento.
     */
    override fun onPause() {
        super.onPause()

        if (simularErroLeak) {
            // Simulação proposital: mantém o listener registrado.
            registrarLog(
                "onPause() — listener NÃO cancelado (modo erro). " +
                "Observe o comportamento no Profiler!"
            )
        } else {
            cancelarListener("onPause()")
        }
    }

    /**
     * Chamado quando a Activity está sendo destruída.
     * Garante que o listener seja cancelado, mesmo durante a simulação de erro.
     */
    override fun onDestroy() {
        if (listenerRegistrado) {
            cancelarListener("onDestroy()")
        }

        super.onDestroy()
    }

    // ==================== REGISTRO E CANCELAMENTO ====================

    /**
     * Registra esta Activity como observadora do acelerômetro.
     *
     * A verificação evita registros duplicados.
     * Após o registro, inicia a contagem de eventos para calcular a frequência.
     */
    private fun registrarListener(origem: String) {
        val sensor = acelerometro ?: return

        if (listenerRegistrado) return

        sensorManager.registerListener(this, sensor, delayAtual)
        listenerRegistrado = true

        inicioJanelaHz = SystemClock.elapsedRealtime()
        eventosNoSegundo = 0

        registrarLog("$origem — registerListener() com $nomeDelayAtual")
        atualizarStatus()
    }

    /**
     * Cancela o registro desta Activity como observadora do sensor.
     *
     * O unregisterListener() interrompe o recebimento de eventos
     * e evita manter o sensor ativo desnecessariamente.
     */
    private fun cancelarListener(origem: String) {
        if (!listenerRegistrado) return

        sensorManager.unregisterListener(this)
        listenerRegistrado = false

        registrarLog("$origem — unregisterListener() executado")
        atualizarStatus()
    }

    // ==================== EVENTOS DO SENSOR ====================

    /**
     * Recebe as leituras enviadas pelo acelerômetro.
     *
     * Os valores representam a aceleração nos três eixos:
     * X: movimento lateral.
     * Y: movimento vertical.
     * Z: movimento de profundidade.
     *
     * A partir dessas informações, a interface é atualizada
     * e são calculadas a magnitude e a orientação do dispositivo.
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val (x, y, z) = Triple(
            event.values[0],
            event.values[1],
            event.values[2]
        )

        // Atualiza os contadores de eventos recebidos.
        eventosTotais++
        eventosNoSegundo++

        // Registra informações no Logcat a cada 50 eventos.
        if (eventosTotais % 50 == 0L) {
            Log.d(
                TAG,
                "evento #$eventosTotais x=%.2f y=%.2f z=%.2f".format(x, y, z)
            )
        }

        // Atualiza os valores individuais dos três eixos na interface.
        txtX.text = "%+.2f".format(x)
        txtY.text = "%+.2f".format(y)
        txtZ.text = "%+.2f".format(z)

        // Converte os valores de aceleração para as barras de progresso.
        barraX.progress = paraBarra(x)
        barraY.progress = paraBarra(y)
        barraZ.progress = paraBarra(z)

        // Calcula a magnitude total da aceleração nos três eixos.
        val magnitude = sqrt(x * x + y * y + z * z)

        txtMagnitude.text =
            "%.2f m/s²  (%.2f g)".format(magnitude, magnitude / G)

        // Estima a orientação do aparelho com base nos valores recebidos.
        txtOrientacao.text = estimarOrientacao(x, y, z, magnitude)

        // Exibe a quantidade total de eventos recebidos.
        txtEventos.text = eventosTotais.toString()

        // Calcula a frequência aproximada de eventos por segundo.
        val agora = SystemClock.elapsedRealtime()

        if (agora - inicioJanelaHz >= 1000) {
            txtHz.text = "$eventosNoSegundo Hz"

            eventosNoSegundo = 0
            inicioJanelaHz = agora
        }
    }

    /**
     * Recebe alterações no nível de precisão do sensor.
     *
     * Os possíveis níveis são alta, média, baixa ou não confiável.
     */
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        val nivel = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "ALTA"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MÉDIA"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "BAIXA"
            else -> "NÃO CONFIÁVEL"
        }

        registrarLog("onAccuracyChanged() — precisão do sensor: $nivel")
    }

    // ==================== PROCESSAMENTO DOS DADOS ====================

    /**
     * Converte os valores de aceleração para a escala de 0 a 100.
     *
     * Considera uma faixa de -2g a +2g:
     * - 0: valor mínimo.
     * - 50: aceleração próxima de zero.
     * - 100: valor máximo.
     */
    private fun paraBarra(valor: Float): Int =
        (50 + valor / (2 * G) * 50).toInt().coerceIn(0, 100)

    /**
     * Estima a orientação do dispositivo utilizando os valores
     * de aceleração dos eixos X, Y e Z.
     *
     * Também identifica movimentos bruscos a partir da magnitude.
     */
    private fun estimarOrientacao(
        x: Float,
        y: Float,
        z: Float,
        magnitude: Float
    ): String = when {
        magnitude > G * 1.3f ->
            "📳 Movimento brusco / sacudindo!"

        abs(z) > abs(x) && abs(z) > abs(y) ->
            if (z > 0) "📱 Deitado, tela para cima"
            else "📱 Deitado, tela para baixo"

        abs(y) > abs(x) ->
            if (y > 0) "📱 Em pé (retrato)"
            else "📱 De cabeça para baixo"

        else ->
            if (x > 0) "📱 Paisagem (girado à esquerda)"
            else "📱 Paisagem (girado à direita)"
    }

    /**
     * Obtém e apresenta as informações técnicas do acelerômetro.
     *
     * Caso o dispositivo não possua esse sensor, apresenta
     * uma mensagem informando sua indisponibilidade.
     */
    private fun exibirInfoDoSensor() {
        val s = acelerometro

        txtInfoSensor.text = if (s == null) {
            registrarLog("Acelerômetro não disponível neste aparelho")
            "Acelerômetro não disponível neste aparelho."
        } else {
            """
            Nome: ${s.name}
            Fabricante: ${s.vendor}
            Alcance máximo: ±%.1f m/s²
            Resolução: %.4f m/s²
            Consumo de energia: %.2f mA
            Atraso mínimo: ${s.minDelay} µs (até ${if (s.minDelay > 0) 1_000_000 / s.minDelay else 0} Hz)
            """.trimIndent().format(s.maximumRange, s.resolution, s.power)
        }
    }

    // ==================== CONFIGURAÇÃO DOS CONTROLES ====================

    /**
     * Configura os controles responsáveis pela interação do usuário.
     *
     * Permite alterar a frequência de leitura, ativar a simulação
     * de erro no ciclo de vida e zerar o contador de eventos.
     */
    private fun configurarControles() {

        // Seleciona o intervalo de atualização do acelerômetro.
        findViewById<ChipGroup>(R.id.chipsDelay)
            .setOnCheckedStateChangeListener { _, ids ->

                val (delay, nome) = when (ids.firstOrNull()) {
                    R.id.chipNormal ->
                        SensorManager.SENSOR_DELAY_NORMAL to "SENSOR_DELAY_NORMAL"

                    R.id.chipGame ->
                        SensorManager.SENSOR_DELAY_GAME to "SENSOR_DELAY_GAME"

                    R.id.chipFastest ->
                        SensorManager.SENSOR_DELAY_FASTEST to "SENSOR_DELAY_FASTEST"

                    else ->
                        SensorManager.SENSOR_DELAY_UI to "SENSOR_DELAY_UI"
                }

                delayAtual = delay
                nomeDelayAtual = nome

                registrarLog("Frequência alterada para $nome — re-registrando o listener")

                // Reconfigura o listener para aplicar a nova frequência.
                if (listenerRegistrado) {
                    cancelarListener("troca de frequência")
                    registrarListener("troca de frequência")
                }
            }

        // Ativa ou desativa a simulação de erro no cancelamento do listener.
        findViewById<MaterialSwitch>(R.id.switchLeak)
            .setOnCheckedChangeListener { _, ligado ->

                simularErroLeak = ligado

                registrarLog(
                    if (ligado) {
                        "Modo ERRO ativado: onPause() não cancelará o listener"
                    } else {
                        "Modo correto ativado: onPause() cancelará o listener"
                    }
                )
            }

        // Zera o contador de eventos exibido na interface.
        findViewById<MaterialButton>(R.id.btnZerar)
            .setOnClickListener {
                eventosTotais = 0
                txtEventos.text = "0"

                registrarLog("Contador de eventos zerado")
            }
    }

    // ==================== ATUALIZAÇÃO DA INTERFACE ====================

    /**
     * Atualiza o indicador visual de acordo com o estado do listener.
     *
     * Quando registrado, informa que os eventos estão sendo recebidos.
     * Quando cancelado, informa que o monitoramento foi interrompido.
     */
    private fun atualizarStatus() {
        if (listenerRegistrado) {
            txtStatusListener.text = "● LISTENER REGISTRADO — recebendo eventos"

            txtStatusListener.setTextColor(
                ContextCompat.getColor(this, R.color.status_executando)
            )
        } else {
            txtStatusListener.text = "● LISTENER CANCELADO — CPU ociosa"

            txtStatusListener.setTextColor(
                ContextCompat.getColor(this, R.color.status_parado)
            )

            txtHz.text = "0 Hz"
        }
    }

    /**
     * Registra mensagens de diagnóstico no Logcat e na interface.
     *
     * Mantém apenas os 40 registros mais recentes para limitar
     * a quantidade de informações exibidas na tela.
     */
    private fun registrarLog(mensagem: String) {
        Log.i(TAG, mensagem)

        logs.addFirst("${formatoHora.format(Date())}  $mensagem")

        while (logs.size > 40) {
            logs.removeLast()
        }

        if (::txtLog.isInitialized) {
            txtLog.text = logs.joinToString("\n")
        }
    }

    // ==================== VINCULAÇÃO DOS COMPONENTES ====================

    /**
     * Associa as propriedades da Activity aos componentes definidos
     * no arquivo de layout activity_sensor.xml.
     */
    private fun vincularViews() {
        txtStatusListener = findViewById(R.id.txtStatusListener)
        txtInfoSensor = findViewById(R.id.txtInfoSensor)
        txtX = findViewById(R.id.txtX)
        txtY = findViewById(R.id.txtY)
        txtZ = findViewById(R.id.txtZ)
        barraX = findViewById(R.id.barraX)
        barraY = findViewById(R.id.barraY)
        barraZ = findViewById(R.id.barraZ)
        txtMagnitude = findViewById(R.id.txtMagnitude)
        txtOrientacao = findViewById(R.id.txtOrientacao)
        txtEventos = findViewById(R.id.txtEventos)
        txtHz = findViewById(R.id.txtHz)
        txtLog = findViewById(R.id.txtLogSensor)
    }
}
