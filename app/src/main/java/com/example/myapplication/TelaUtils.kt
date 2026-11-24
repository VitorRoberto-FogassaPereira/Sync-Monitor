package com.example.myapplication

import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

/**
 * Configuração comum a TODAS as telas do app:
 *  - layout edge-to-edge respeitando barra de status / navegação
 *  - barra superior (toolbar) com título, subtítulo e seta de voltar
 *
 * Todo layout de tela deve ter a raiz com id "raiz" e uma MaterialToolbar com id "toolbar".
 */
fun AppCompatActivity.prepararTela(
    @LayoutRes layout: Int,
    titulo: String,
    subtitulo: String? = null,
    mostrarVoltar: Boolean = true
) {
    enableEdgeToEdge()
    setContentView(layout)
    val raiz = findViewById<View>(R.id.raiz)
    ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
        insets
    }
    val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
    toolbar.title = titulo
    toolbar.subtitle = subtitulo
    if (mostrarVoltar) {
        toolbar.setNavigationIcon(R.drawable.ic_voltar)
        toolbar.setNavigationContentDescription("Voltar")
        toolbar.setNavigationOnClickListener { finish() }
    }
}
