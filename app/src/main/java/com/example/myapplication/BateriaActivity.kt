package com.example.myapplication

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * TELA 5 do Guia Prático 4 — restrições de segundo plano e economia de bateria (Módulo 4.1).
 */
class BateriaActivity : AppCompatActivity() {

    private lateinit var miniLog: MiniLog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararTela(R.layout.activity_bateria, "Bateria e Doze Mode", "Guia Prático 4 • Passo 5")
        miniLog = MiniLog(findViewById(R.id.txtLogBateria), "BATERIA_DEMO")

        findViewById<Button>(R.id.btnAtualizarBateria).setOnClickListener { lerSituacao() }
        findViewById<Button>(R.id.btnConfigBateria).setOnClickListener {
            miniLog.add("Abrindo configurações de otimização de bateria")
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    // Lê de novo ao voltar das Configurações
    override fun onResume() {
        super.onResume()
        lerSituacao()
    }

    private fun lerSituacao() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = getSystemService(PowerManager::class.java)
        val nivel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val carregando = bm.isCharging
        val economia = pm.isPowerSaveMode
        val doze = pm.isDeviceIdleMode
        val isento = pm.isIgnoringBatteryOptimizations(packageName)

        texto(R.id.txtNivelBateria, "$nivel%")
        texto(R.id.txtCarregando, if (carregando) "Sim" else "Não")
        texto(R.id.txtEconomia, if (economia) "LIGADA" else "Desligada")
        texto(R.id.txtDoze, if (doze) "Sim" else "Não")
        texto(R.id.txtOtimizacao, if (isento) "Desativada (isento)" else "Ativa")
        texto(R.id.txtBucket, lerBucket())

        miniLog.add(
            "Bateria $nivel%${if (carregando) " ⚡" else ""} • economia ${if (economia) "ON" else "off"} • " +
                "Doze ${if (doze) "sim" else "não"} • otimização ${if (isento) "desativada" else "ativa"} • bucket ${lerBucket()}"
        )
    }

    /** App Standby Bucket (Android 9+): quanto mais "raro" o bucket, mais restrito o app fica. */
    private fun lerBucket(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "Indisponível (< Android 9)"
        val usm = getSystemService(UsageStatsManager::class.java)
        return when (usm.appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE (em uso)"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
            45 -> "RESTRICTED"
            else -> usm.appStandbyBucket.toString()
        }
    }

    private fun texto(id: Int, valor: String) {
        findViewById<TextView>(id).text = valor
    }
}
