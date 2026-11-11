
package com.foundit.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatActivity : AppCompatActivity() {

    private lateinit var api: SupabaseApi
    private lateinit var adapter: PrivateMessageAdapter

    private lateinit var recyclerMessages: RecyclerView
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: ImageButton

    private lateinit var txtUsername: TextView
    private lateinit var txtItemName: TextView

    private var conversationId = ""
    private var currentUserId = ""
    private var otherUsername = ""
    private var itemName = ""

    private var isLoading = false
    private var isSending = false
    private var isUpdatingMessage = false

    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                loadMessages(silent = true)
                handler.postDelayed(this, 5000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        setContentView(R.layout.activity_match_chat)

        val chatRoot = findViewById<View>(R.id.chatRoot)

        val initialLeft = chatRoot.paddingLeft
        val initialTop = chatRoot.paddingTop
        val initialRight = chatRoot.paddingRight
        val initialBottom = chatRoot.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(chatRoot) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            val keyboard = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            val bottomInset = maxOf(
                systemBars.bottom,
                keyboard.bottom
            )

            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + bottomInset
            )

            insets
        }

        ViewCompat.requestApplyInsets(chatRoot)

        api = SupabaseApi(applicationContext)

        conversationId =
            intent.getStringExtra("conversation_id").orEmpty()

        otherUsername =
            intent.getStringExtra("other_username")
                ?: "FoundIt User"

        itemName =
            intent.getStringExtra("item_name")
                ?: "FoundIt conversation"

        if (conversationId.isBlank()) {
            Toast.makeText(
                this,
                "Conversation not found.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        if (!api.isSignedIn()) {
            Toast.makeText(
                this,
                "Please sign in again.",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        currentUserId = api.currentUserId().orEmpty()

        txtUsername = findViewById(R.id.txtChatUsername)
        txtItemName = findViewById(R.id.txtChatItemName)

        recyclerMessages =
            findViewById(R.id.recyclerChatMessages)

        edtMessage =
            findViewById(R.id.edtChatMessage)

        btnSend =
            findViewById(R.id.btnSendChatMessage)

        txtUsername.text = otherUsername
        txtItemName.text = "Regarding: $itemName"

        adapter = PrivateMessageAdapter(
            currentUserId,
            onEditMessage = { message ->
                showEditMessageDialog(message)
            },
            onDeleteMessage = { message ->
                confirmDeleteMessage(message)
            }
        )

        recyclerMessages.layoutManager =
            LinearLayoutManager(this).apply {
                stackFromEnd = true
            }

        recyclerMessages.adapter = adapter

        edtMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                recyclerMessages.post {
                    scrollToLatestMessage()
                }
            }
        }

        findViewById<View>(R.id.btnChatBack).setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            sendMessage()
        }

        loadMessages()
    }

    // EDIT YOUR OWN MESSAGE

    private fun showEditMessageDialog(message: MatchMessage) {

        if (isUpdatingMessage) return

        val editText = EditText(this).apply {

            setText(message.body)

            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)

            hint = "Enter your message"

            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

            minLines = 3
            maxLines = 7

            setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
            )

            background = roundedBackground(
                Color.rgb(25, 25, 38),
                Color.rgb(110, 88, 150)
            )
        }

        val content = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(20),
                dp(8),
                dp(20),
                dp(4)
            )

            addView(
                editText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit message")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val updatedText =
                        editText.text.toString().trim()

                    if (updatedText.isBlank()) {
                        editText.error =
                            "Message cannot be empty."
                        return@setOnClickListener
                    }

                    if (updatedText.length > 3000) {
                        editText.error =
                            "Maximum 3000 characters."
                        return@setOnClickListener
                    }

                    if (updatedText == message.body) {
                        dialog.dismiss()
                        return@setOnClickListener
                    }

                    dialog.dismiss()

                    updateMessage(
                        message,
                        updatedText
                    )
                }
        }

        dialog.show()
    }

    private fun updateMessage(
        message: MatchMessage,
        updatedText: String
    ) {

        if (isUpdatingMessage) return

        isUpdatingMessage = true

        Thread {

            try {

                runBlocking {
                    api.updateMatchMessage(
                        message.id,
                        conversationId,
                        updatedText
                    )
                }

                runOnUiThread {

                    isUpdatingMessage = false

                    Toast.makeText(
                        this,
                        "Message edited successfully.",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadMessages()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    isUpdatingMessage = false

                    Toast.makeText(
                        this,
                        e.message ?: "Could not edit message.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }

    // DELETE YOUR OWN MESSAGE

    private fun confirmDeleteMessage(message: MatchMessage) {

        if (isUpdatingMessage) return

        AlertDialog.Builder(this)
            .setTitle("Delete message?")
            .setMessage(
                "This message will be deleted from the conversation."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteMessage(message)
            }
            .show()
    }

    private fun deleteMessage(message: MatchMessage) {

        if (isUpdatingMessage) return

        isUpdatingMessage = true

        Thread {

            try {

                runBlocking {
                    api.deleteMatchMessage(
                        message.id,
                        conversationId
                    )
                }

                runOnUiThread {

                    isUpdatingMessage = false

                    Toast.makeText(
                        this,
                        "Message deleted.",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadMessages()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    isUpdatingMessage = false

                    Toast.makeText(
                        this,
                        e.message ?: "Could not delete message.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }

    // MESSAGE BACKGROUND

    private fun roundedBackground(
        fill: Int,
        stroke: Int = Color.TRANSPARENT
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // SCROLL TO LATEST MESSAGE

    private fun scrollToLatestMessage() {

        if (adapter.itemCount > 0) {
            recyclerMessages.scrollToPosition(
                adapter.itemCount - 1
            )
        }
    }

    // LOAD CHAT MESSAGES

    private fun loadMessages(silent: Boolean = false) {

        if (isLoading || conversationId.isBlank()) return

        isLoading = true

        Thread {

            try {

                runBlocking {
                    api.markMatchMessagesRead(conversationId)
                }

                val messages = runBlocking {
                    api.fetchMatchMessages(conversationId)
                }

                runOnUiThread {

                    val shouldScroll =
                        adapter.itemCount == 0 ||
                            messages.size > adapter.itemCount

                    adapter.submitList(messages)

                    if (shouldScroll) {
                        recyclerMessages.post {
                            scrollToLatestMessage()
                        }
                    }

                    isLoading = false
                }

            } catch (e: Exception) {

                runOnUiThread {

                    isLoading = false

                    if (!silent) {
                        Toast.makeText(
                            this,
                            e.message ?: "Could not load messages.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

        }.start()
    }

    // SEND NORMAL CHAT MESSAGE

    private fun sendMessage() {

        if (isSending) return

        val message = edtMessage.text.toString().trim()

        if (message.isBlank()) return

        if (message.length > 3000) {

            Toast.makeText(
                this,
                "Maximum message length is 3000 characters.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        isSending = true
        btnSend.isEnabled = false

        Thread {

            try {

                runBlocking {
                    api.sendMatchMessage(
                        conversationId,
                        message
                    )
                }

                runOnUiThread {

                    edtMessage.setText("")

                    isSending = false
                    btnSend.isEnabled = true
                }

                loadMessages()

            } catch (e: Exception) {

                runOnUiThread {

                    isSending = false
                    btnSend.isEnabled = true

                    Toast.makeText(
                        this,
                        e.message ?: "Message could not be sent.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }

    // LIFECYCLE

    override fun onResume() {
        super.onResume()

        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 5000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // PRIVATE MESSAGE ADAPTER

    private class PrivateMessageAdapter(
        private val currentUserId: String,
        private val onEditMessage: (MatchMessage) -> Unit,
        private val onDeleteMessage: (MatchMessage) -> Unit
    ) : RecyclerView.Adapter<PrivateMessageAdapter.MessageHolder>() {

        private val messages = mutableListOf<MatchMessage>()

        fun submitList(newMessages: List<MatchMessage>) {

            messages.clear()
            messages.addAll(newMessages)

            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: android.view.ViewGroup,
            viewType: Int
        ): MessageHolder {

            val view =
                android.view.LayoutInflater.from(parent.context)
                    .inflate(
                        R.layout.item_match_message,
                        parent,
                        false
                    )

            return MessageHolder(view)
        }

        override fun onBindViewHolder(
            holder: MessageHolder,
            position: Int
        ) {
            holder.bind(messages[position])
        }

        override fun getItemCount(): Int = messages.size

        inner class MessageHolder(itemView: View) :
            RecyclerView.ViewHolder(itemView) {

            private val container: LinearLayout =
                itemView.findViewById(R.id.messageContainer)

            private val sender: TextView =
                itemView.findViewById(R.id.txtMessageSender)

            private val body: TextView =
                itemView.findViewById(R.id.txtMessageBody)

            private val time: TextView =
                itemView.findViewById(R.id.txtMessageTime)

            private val receipt: TextView =
                itemView.findViewById(R.id.txtMessageReceipt)

            private fun dp(value: Float): Float {
                return value *
                    itemView.resources.displayMetrics.density
            }

            fun bind(message: MatchMessage) {

                val isMine =
                    message.senderId == currentUserId

                body.text = message.body

                sender.text = if (isMine) {
                    "You"
                } else {
                    "Other user"
                }

                sender.visibility =
                    if (isMine) View.GONE else View.VISIBLE

                container.background = null

                container.gravity =
                    if (isMine) Gravity.END else Gravity.START

                val bubbleParams =
                    body.layoutParams as LinearLayout.LayoutParams

                bubbleParams.gravity =
                    if (isMine) Gravity.END else Gravity.START

                body.layoutParams = bubbleParams

                val meta = itemView.findViewById<LinearLayout>(
                    R.id.messageMeta
                )

                val metaParams =
                    meta.layoutParams as LinearLayout.LayoutParams

                metaParams.gravity =
                    if (isMine) Gravity.END else Gravity.START

                meta.layoutParams = metaParams

                body.background = GradientDrawable().apply {

                    cornerRadius = dp(19f)

                    setColor(
                        if (isMine) {
                            Color.parseColor("#AA7653B8")
                        } else {
                            Color.parseColor("#14FFFFFF")
                        }
                    )

                    setStroke(
                        dp(1f).toInt(),
                        if (isMine) {
                            Color.parseColor("#667653B8")
                        } else {
                            Color.parseColor("#22FFFFFF")
                        }
                    )
                }

                body.setTextColor(Color.WHITE)

                sender.setTextColor(
                    Color.parseColor("#BDA5FF")
                )

                time.setTextColor(
                    Color.parseColor("#888899")
                )

                time.text = formatMessageTime(message.createdAt)

                // LONG PRESS MENU ONLY FOR YOUR OWN MESSAGES

                body.setOnLongClickListener {

                    if (isMine) {

                        val options = arrayOf(
                            "Edit message",
                            "Delete message"
                        )

                        AlertDialog.Builder(itemView.context)
                            .setItems(options) { _, which ->

                                when (which) {
                                    0 -> onEditMessage(message)
                                    1 -> onDeleteMessage(message)
                                }
                            }
                            .show()
                    }

                    isMine
                }

                // MESSAGE READ RECEIPTS

                if (isMine) {

                    receipt.visibility = View.VISIBLE

                    when {

                        !message.readAt.isNullOrBlank() -> {

                            receipt.text = "✓✓"

                            receipt.setTextColor(
                                Color.parseColor("#36A8FF")
                            )
                        }

                        !message.deliveredAt.isNullOrBlank() -> {

                            receipt.text = "✓✓"

                            receipt.setTextColor(
                                Color.parseColor("#999999")
                            )
                        }

                        else -> {

                            receipt.text = "✓"

                            receipt.setTextColor(
                                Color.parseColor("#999999")
                            )
                        }
                    }

                } else {

                    receipt.visibility = View.GONE
                }
            }

            private fun formatMessageTime(value: String?): String {

                if (value.isNullOrBlank() || value == "null") {
                    return ""
                }

                return try {

                    val patterns = listOf(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                        "yyyy-MM-dd'T'HH:mm:ssXXX",
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        "yyyy-MM-dd'T'HH:mm:ss'Z'"
                    )

                    var date: Date? = null

                    for (pattern in patterns) {

                        try {

                            val parser = SimpleDateFormat(
                                pattern,
                                Locale.US
                            )

                            parser.timeZone =
                                TimeZone.getTimeZone("UTC")

                            date = parser.parse(value)

                            if (date != null) break

                        } catch (_: Exception) {
                            // Try next timestamp format.
                        }
                    }

                    if (date == null) return ""

                    SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(date)

                } catch (_: Exception) {
                    ""
                }
            }
        }
    }
}
