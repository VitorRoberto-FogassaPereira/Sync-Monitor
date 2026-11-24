package com.example.myapplication

import android.util.Log
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pequeno registro exibido no rodapé de cada tela de demonstração (também vai para o Logcat). */
class MiniLog(private val alvo: TextView, private val tag: String, private val maximo: Int = 30) {
    private val linhas = ArrayDeque<String>()
    private val formato = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun add(mensagem: String) {
        Log.i(tag, mensagem)
        linhas.addFirst("${formato.format(Date())}  $mensagem")
        while (linhas.size > maximo) linhas.removeLast()
        alvo.text = linhas.joinToString("\n")
    }
}
