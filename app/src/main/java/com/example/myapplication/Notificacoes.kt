package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Canais de notificação usados pelo app — compartilhados pelo Service e pela tela de Notificações. */
object Notificacoes {

    // Canal 1: notificação fixa do serviço (importância baixa = sem som)
    const val CANAL_SERVICO = "canal_sincronizacao_ativa"
    // Canal 2: alertas pontuais (bateria baixa, resumo ao encerrar, teste)
    const val CANAL_ALERTAS = "canal_alertas_sincronizacao"

    /** Canais são obrigatórios a partir do Android 8.0 (API 26). Criar de novo um canal existente não tem efeito. */
    fun criarCanais(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canalServico = NotificationChannel(
            CANAL_SERVICO, "Sincronização em andamento", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Notificação fixa que mostra o progresso da sincronização" }

        val canalAlertas = NotificationChannel(
            CANAL_ALERTAS, "Alertas de sincronização", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Bateria baixa e resumo ao encerrar" }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(canalServico, canalAlertas))
    }
}
