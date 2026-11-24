package com.example.myapplication

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.example.myapplication.MonitorRepository.TipoLog

/**
 * TELA 3 do Guia Prático 4 — permissão POST_NOTIFICATIONS, canais e anatomia da notificação.
 */
class NotificacaoActivity : AppCompatActivity() {

    companion object {
        private const val ID_NOTIFICACAO_TESTE = 2001
    }

    private lateinit var miniLog: MiniLog
    private lateinit var txtPermissao: TextView
    private lateinit var txtHabilitadas: TextView
    private lateinit var txtVersao: TextView
    private lateinit var txtCanais: TextView

    private val pedirPermissao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
            miniLog.add(if (concedida) "✅ Usuário CONCEDEU a permissão" else "⛔ Usuário NEGOU a permissão")
            MonitorRepository.log(
                if (concedida) TipoLog.SUCESSO else TipoLog.AVISO,
                "Permissão POST_NOTIFICATIONS ${if (concedida) "concedida" else "negada"} (tela Notificações)"
            )
            atualizarSituacao()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararTela(R.layout.activity_notificacao, "Notificações e permissões", "Guia Prático 4 • Passo 3")
        miniLog = MiniLog(findViewById(R.id.txtLogNotificacao), "NOTIFICACOES_DEMO")
        txtPermissao = findViewById(R.id.txtPermissaoStatus)
        txtHabilitadas = findViewById(R.id.txtNotifHabilitadas)
        txtVersao = findViewById(R.id.txtVersaoAndroid)
        txtCanais = findViewById(R.id.txtCanais)

        Notificacoes.criarCanais(this)
        miniLog.add("Canais de notificação garantidos com createNotificationChannels()")

        findViewById<Button>(R.id.btnSolicitarPermissao).setOnClickListener { solicitarPermissao() }
        findViewById<Button>(R.id.btnConfigNotificacoes).setOnClickListener { abrirConfiguracoes() }
        findViewById<Button>(R.id.btnNotificacaoTeste).setOnClickListener { enviarNotificacaoTeste() }
    }

    // onResume: atualiza ao voltar da tela de Configurações do Android
    override fun onResume() {
        super.onResume()
        atualizarSituacao()
    }

    private fun permissaoConcedida(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun atualizarSituacao() {
        txtPermissao.text = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "Não exigida (Android < 13)"
            permissaoConcedida() -> "✅ Concedida"
            else -> "⛔ Não concedida"
        }
        txtHabilitadas.text = if (NotificationManagerCompat.from(this).areNotificationsEnabled()) "✅ Ativadas" else "⛔ Bloqueadas"
        txtVersao.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        txtCanais.text = descreverCanais()
    }

    private fun descreverCanais(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "Canais não existem antes do Android 8.0 (API 26)."
        val canais = getSystemService(NotificationManager::class.java).notificationChannels
        if (canais.isEmpty()) return "Nenhum canal criado."
        return canais.joinToString("\n\n") { c ->
            val importancia = when (c.importance) {
                NotificationManager.IMPORTANCE_NONE -> "BLOQUEADO pelo usuário"
                NotificationManager.IMPORTANCE_MIN -> "MIN (silencioso, recolhido)"
                NotificationManager.IMPORTANCE_LOW -> "LOW (sem som)"
                NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT (com som)"
                NotificationManager.IMPORTANCE_HIGH -> "HIGH (som + pop-up)"
                else -> c.importance.toString()
            }
            "• ${c.name}\n   id: ${c.id}\n   importância: $importancia"
        }
    }

    private fun solicitarPermissao() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                miniLog.add("Android < 13: a permissão é concedida automaticamente na instalação")
            permissaoConcedida() -> miniLog.add("A permissão já está concedida")
            else -> {
                miniLog.add("Chamando RequestPermission(POST_NOTIFICATIONS)...")
                pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun abrirConfiguracoes() {
        miniLog.add("Abrindo as configurações de notificação do app")
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        runCatching { startActivity(intent) }
    }

    private fun enviarNotificacaoTeste() {
        if (!permissaoConcedida()) {
            miniLog.add("⛔ Sem permissão: a notificação não apareceria. Solicitando...")
            solicitarPermissao()
            return
        }
        val abrirEstaTela = TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(Intent(this, NotificacaoActivity::class.java))
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(this, Notificacoes.CANAL_ALERTAS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Notificação de teste 🔔")
            .setContentText("Canal: Alertas de sincronização (IMPORTANCE_DEFAULT)")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Esta notificação foi publicada no canal de alertas.\nToque para voltar à tela de Notificações."
                )
            )
            .setContentIntent(abrirEstaTela)
            .setAutoCancel(true) // some ao tocar
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_NOTIFICACAO_TESTE, n)
        miniLog.add("✅ Notificação publicada (id $ID_NOTIFICACAO_TESTE). Abra a gaveta de notificações.")
        MonitorRepository.log(TipoLog.NOTIFICACAO, "Notificação de teste enviada no canal de alertas")
    }
}
