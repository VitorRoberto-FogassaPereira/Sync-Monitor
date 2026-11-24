// ARQUIVO: SensorActivity.kt — Desafio 1 (Módulo 2): Listeners e Ciclo de Vida de Sensores
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
 * Padrão Observer na prática:
 *  - Subject (Sujeito)   = SensorManager / acelerômetro do aparelho
 *  - Observer (Ouvinte)  = esta Activity, que implementa SensorEventListener
 *
 * A Activity NÃO pergunta ao sensor "mudou?" em um laço (polling). Ela se inscreve
 * e fica adormecida até o sistema chamar onSensorChanged().
 */
class SensorActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "SENSOR_DESAFIO"
        private const val G = SensorManager.GRAVITY_EARTH // 9,80665 m/s²
    }

    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null

    // Frequência de entrega escolhida pelo usuário
    private var delayAtual = SensorManager.SENSOR_DELAY_UI
    private var nomeDelayAtual = "SENSOR_DELAY_UI"

    private var listenerRegistrado = false
    private var simularErroLeak = false

    // Estatísticas
    private var eventosTotais = 0L
    private var eventosNoSegundo = 0
    private var inicioJanelaHz = 0L

    private val logs = ArrayDeque<String>()
    private val formatoHora = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // Views
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararTela(R.layout.activity_sensor, "Desafio 1 — Acelerômetro", "Módulo 2 • Listeners e ciclo de vida")
        vincularViews()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        exibirInfoDoSensor()
        configurarControles()
        registrarLog("onCreate() — tela criada, SensorManager obtido (listener ainda NÃO registrado)")
    }

    // ===================== CICLO DE VIDA: o coração do desafio =====================

    override fun onResume() {
        super.onResume()
        // Registra o observador apenas quando a tela está visível e ativa
        registrarListener("onResume()")
    }

    override fun onPause() {
        super.onPause()
        if (simularErroLeak) {
            // ❌ ERRO PROPOSITAL (demonstração): esquecer de cancelar a inscrição.
            // O sensor continua acordando a CPU mesmo com o app minimizado.
            registrarLog("onPause() — ⚠ listener NÃO cancelado (modo erro). Veja a CPU no Profiler!")
        } else {
            // ✅ Cancela a inscrição imediatamente ao sair da tela (evita consumo de bateria e Memory Leak)
            cancelarListener("onPause()")
        }
    }

    override fun onDestroy() {
        // Rede de segurança: mesmo no modo de erro, nunca deixamos a Activity vazar de verdade
        if (listenerRegistrado) cancelarListener("onDestroy()")
        super.onDestroy()
    }

    private fun registrarListener(origem: String) {
        val sensor = acelerometro ?: return
        if (listenerRegistrado) return
        sensorManager.registerListener(this, sensor, delayAtual)
        listenerRegistrado = true
        inicioJanelaHz = SystemClock.elapsedRealtime()
        eventosNoSegundo = 0
        registrarLog("$origem — registerListener() com $nomeDelayAtual ✅")
        atualizarStatus()
    }

    private fun cancelarListener(origem: String) {
        if (!listenerRegistrado) return
        sensorManager.unregisterListener(this)
        listenerRegistrado = false
        registrarLog("$origem — unregisterListener() 🛑 CPU livre para dormir")
        atualizarStatus()
    }

    // ===================== CALLBACKS DO SensorEventListener =====================

    /** Chamado pelo sistema a cada nova leitura — é aqui que o Observer é "acordado". */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])

        eventosTotais++
        eventosNoSegundo++
        // Log de diagnóstico (filtre por SENSOR_DESAFIO no Logcat) — a cada 50 eventos para não poluir
        if (eventosTotais % 50 == 0L) {
            Log.d(TAG, "evento #$eventosTotais x=%.2f y=%.2f z=%.2f".format(x, y, z))
        }

        txtX.text = "%+.2f".format(x)
        txtY.text = "%+.2f".format(y)
        txtZ.text = "%+.2f".format(z)
        barraX.progress = paraBarra(x)
        barraY.progress = paraBarra(y)
        barraZ.progress = paraBarra(z)

        val magnitude = sqrt(x * x + y * y + z * z)
        txtMagnitude.text = "%.2f m/s²  (%.2f g)".format(magnitude, magnitude / G)
        txtOrientacao.text = estimarOrientacao(x, y, z, magnitude)
        txtEventos.text = eventosTotais.toString()

        val agora = SystemClock.elapsedRealtime()
        if (agora - inicioJanelaHz >= 1000) {
            txtHz.text = "$eventosNoSegundo Hz"
            eventosNoSegundo = 0
            inicioJanelaHz = agora
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        val nivel = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "ALTA"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MÉDIA"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "BAIXA"
            else -> "NÃO CONFIÁVEL"
        }
        registrarLog("onAccuracyChanged() — precisão do sensor: $nivel")
    }

    // ===================== Auxiliares =====================

    /** Converte -2g..+2g (≈ -19,6..+19,6 m/s²) para 0..100 na barra (50 = zero). */
    private fun paraBarra(valor: Float): Int =
        (50 + valor / (2 * G) * 50).toInt().coerceIn(0, 100)

    private fun estimarOrientacao(x: Float, y: Float, z: Float, magnitude: Float): String = when {
        magnitude > G * 1.3f -> "📳 Movimento brusco / sacudindo!"
        abs(z) > abs(x) && abs(z) > abs(y) -> if (z > 0) "📱 Deitado, tela para cima" else "📱 Deitado, tela para baixo"
        abs(y) > abs(x) -> if (y > 0) "📱 Em pé (retrato)" else "📱 De cabeça para baixo"
        else -> if (x > 0) "📱 Paisagem (girado à esquerda)" else "📱 Paisagem (girado à direita)"
    }

    private fun exibirInfoDoSensor() {
        val s = acelerometro
        txtInfoSensor.text = if (s == null) {
            registrarLog("❌ Este aparelho não possui acelerômetro")
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

    private fun configurarControles() {
        findViewById<ChipGroup>(R.id.chipsDelay).setOnCheckedStateChangeListener { _, ids ->
            val (delay, nome) = when (ids.firstOrNull()) {
                R.id.chipNormal -> SensorManager.SENSOR_DELAY_NORMAL to "SENSOR_DELAY_NORMAL"
                R.id.chipGame -> SensorManager.SENSOR_DELAY_GAME to "SENSOR_DELAY_GAME"
                R.id.chipFastest -> SensorManager.SENSOR_DELAY_FASTEST to "SENSOR_DELAY_FASTEST"
                else -> SensorManager.SENSOR_DELAY_UI to "SENSOR_DELAY_UI"
            }
            delayAtual = delay
            nomeDelayAtual = nome
            registrarLog("Frequência alterada para $nome — re-registrando o listener")
            if (listenerRegistrado) {
                cancelarListener("troca de frequência")
                registrarListener("troca de frequência")
            }
        }

        findViewById<MaterialSwitch>(R.id.switchLeak).setOnCheckedChangeListener { _, ligado ->
            simularErroLeak = ligado
            registrarLog(
                if (ligado) "⚠ Modo ERRO ligado: o onPause() NÃO vai cancelar o listener"
                else "✅ Modo correto: o onPause() cancela o listener"
            )
        }

        findViewById<MaterialButton>(R.id.btnZerar).setOnClickListener {
            eventosTotais = 0
            txtEventos.text = "0"
            registrarLog("Contador de eventos zerado")
        }

    }

    private fun atualizarStatus() {
        if (listenerRegistrado) {
            txtStatusListener.text = "● LISTENER REGISTRADO — recebendo eventos"
            txtStatusListener.setTextColor(ContextCompat.getColor(this, R.color.status_executando))
        } else {
            txtStatusListener.text = "● LISTENER CANCELADO — CPU ociosa"
            txtStatusListener.setTextColor(ContextCompat.getColor(this, R.color.status_parado))
            txtHz.text = "0 Hz"
        }
    }

    private fun registrarLog(mensagem: String) {
        Log.i(TAG, mensagem)
        logs.addFirst("${formatoHora.format(Date())}  $mensagem")
        while (logs.size > 40) logs.removeLast()
        if (::txtLog.isInitialized) txtLog.text = logs.joinToString("\n")
    }

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
