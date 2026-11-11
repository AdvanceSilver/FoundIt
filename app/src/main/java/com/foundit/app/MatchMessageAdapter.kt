package com.foundit.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MatchMessageAdapter(
    private val currentUserId: String
) : RecyclerView.Adapter<MatchMessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<MatchMessage>()

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout =
            view.findViewById(R.id.messageContainer)

        val sender: TextView =
            view.findViewById(R.id.txtMessageSender)

        val body: TextView =
            view.findViewById(R.id.txtMessageBody)

        val time: TextView =
            view.findViewById(R.id.txtMessageTime)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_match_message, parent, false)

        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: MessageViewHolder,
        position: Int
    ) {
        val message = messages[position]
        val isMine = message.senderId == currentUserId

        holder.container.gravity =
            if (isMine) Gravity.END else Gravity.START

        holder.sender.visibility =
            if (isMine) View.GONE else View.VISIBLE

        holder.sender.text = "Other user"

        holder.body.text = message.body

        val bubble = GradientDrawable().apply {
            cornerRadius = 22f
            setColor(
                if (isMine) Color.rgb(124, 77, 255)
                else Color.rgb(37, 37, 54)
            )
        }

        holder.body.background = bubble

        holder.time.text = formatTime(message.createdAt)

        holder.time.gravity =
            if (isMine) Gravity.END else Gravity.START
    }

    override fun getItemCount(): Int = messages.size

    fun submitMessages(newMessages: List<MatchMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: MatchMessage) {
        if (messages.none { it.id == message.id }) {
            messages.add(message)
            notifyItemInserted(messages.size - 1)
        }
    }

    private fun formatTime(value: String?): String {
        if (value.isNullOrBlank()) return ""

        return try {
            val input = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.getDefault()
            )
            input.timeZone = TimeZone.getTimeZone("UTC")

            val date = input.parse(value) ?: return ""

            SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            ).format(date)
        } catch (e: Exception) {
            ""
        }
    }
}