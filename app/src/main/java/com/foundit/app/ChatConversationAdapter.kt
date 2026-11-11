package com.foundit.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class ChatInboxItem(
    val conversationId: String,
    val otherUserId: String,
    val username: String,
    val avatarUrl: String? = null,
    val lastMessage: String = "Start your conversation...",
    val itemName: String = "FoundIt conversation",
    val lastMessageTime: String = ""
)

class ChatConversationAdapter(
    private val onChatClick: (ChatInboxItem) -> Unit
) : RecyclerView.Adapter<ChatConversationAdapter.ChatViewHolder>() {

    private val conversations = mutableListOf<ChatInboxItem>()

    fun submitList(items: List<ChatInboxItem>) {
        conversations.clear()
        conversations.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChatViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_conversation, parent, false)

        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ChatViewHolder,
        position: Int
    ) {
        holder.bind(conversations[position])
    }

    override fun getItemCount(): Int = conversations.size

    inner class ChatViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val avatar: ImageView =
            itemView.findViewById(R.id.imgChatAvatar)

        private val initial: TextView =
            itemView.findViewById(R.id.txtChatInitial)

        private val username: TextView =
            itemView.findViewById(R.id.txtConversationUsername)

        private val message: TextView =
            itemView.findViewById(R.id.txtConversationMessage)

        private val itemName: TextView =
            itemView.findViewById(R.id.txtConversationItem)

        private val time: TextView =
            itemView.findViewById(R.id.txtConversationTime)

        fun bind(chat: ChatInboxItem) {

            username.text = chat.username
            message.text = chat.lastMessage
            itemName.text = chat.itemName
            time.text = chat.lastMessageTime

            val firstLetter = chat.username
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString() ?: "U"

            initial.text = firstLetter

            if (!chat.avatarUrl.isNullOrBlank()) {

                avatar.visibility = View.VISIBLE
                initial.visibility = View.GONE

                Glide.with(itemView.context)
                    .load(chat.avatarUrl)
                    .placeholder(R.drawable.bg_glass)
                    .error(R.drawable.bg_glass)
                    .circleCrop()
                    .into(avatar)

            } else {

                Glide.with(itemView.context).clear(avatar)
                avatar.visibility = View.GONE
                initial.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                onChatClick(chat)
            }
        }
    }
}