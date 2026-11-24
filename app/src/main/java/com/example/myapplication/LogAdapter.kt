package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MonitorRepository.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lista de logs exibida na tela. ListAdapter + DiffUtil só redesenham os itens que mudaram. */
class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(Diff) {

    private val formatoHora = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val barraCor: View = view.findViewById(R.id.barraCor)
        val txtTipo: TextView = view.findViewById(R.id.txtTipo)
        val txtHora: TextView = view.findViewById(R.id.txtHora)
        val txtMensagem: TextView = view.findViewById(R.id.txtMensagem)
        val txtThread: TextView = view.findViewById(R.id.txtThreadLog)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder =
        LogViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false))

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = getItem(position)
        val cor = ContextCompat.getColor(holder.itemView.context, item.tipo.cor)
        holder.barraCor.setBackgroundColor(cor)
        holder.txtTipo.text = item.tipo.rotulo
        holder.txtTipo.setTextColor(cor)
        holder.txtHora.text = formatoHora.format(Date(item.horario))
        holder.txtMensagem.text = item.mensagem
        holder.txtThread.text = "thread: ${item.thread}"
    }

    private object Diff : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(a: LogEntry, b: LogEntry) = a.id == b.id
        override fun areContentsTheSame(a: LogEntry, b: LogEntry) = a == b
    }
}
