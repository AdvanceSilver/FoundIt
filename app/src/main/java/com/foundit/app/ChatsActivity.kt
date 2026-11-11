
package com.foundit.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatsActivity : AppCompatActivity() {

    private lateinit var api: SupabaseApi
    private lateinit var adapter: ChatConversationAdapter

    private lateinit var recyclerChats: RecyclerView
    private lateinit var txtChatsEmpty: TextView
    private lateinit var txtChatsRefresh: TextView

    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        api = SupabaseApi(applicationContext)

        recyclerChats = findViewById(R.id.recyclerChats)
        txtChatsEmpty = findViewById(R.id.txtChatsEmpty)
        txtChatsRefresh = findViewById(R.id.txtChatsRefresh)

        adapter = ChatConversationAdapter { chat ->
            openChat(chat)
        }

        recyclerChats.layoutManager = LinearLayoutManager(this)
        recyclerChats.adapter = adapter

        txtChatsRefresh.setOnClickListener {
            loadConversations()
        }

        loadConversations()
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized && !isLoading) {
            loadConversations()
        }
    }

    private fun loadConversations() {

        if (isLoading) return

        if (!api.isSignedIn()) {
            Toast.makeText(
                this,
                "Please sign in to view your chats.",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        isLoading = true

        txtChatsRefresh.isEnabled = false
        txtChatsRefresh.alpha = 0.5f

        Thread {

            try {

                val chatItems = runBlocking {

                    val currentUserId = api.currentUserId()
                        ?: throw Exception("Please sign in again.")

                    val conversations = api.fetchMatchConversations()

                    val result = mutableListOf<ChatInboxItem>()

                    for (conversation in conversations) {

                        val otherUserId =
                            if (conversation.userAId == currentUserId) {
                                conversation.userBId
                            } else {
                                conversation.userAId
                            }

                        val profile = try {
                            api.fetchProfile(otherUserId)
                        } catch (_: Exception) {
                            UserProfile(
                                userId = otherUserId,
                                username = "FoundIt User",
                                bio = "",
                                avatarUrl = null,
                                avatarShape = ""
                            )
                        }

                        val messages = try {
                            api.fetchMatchMessages(conversation.id)
                        } catch (_: Exception) {
                            emptyList()
                        }

                        val reportA = try {
                            api.fetchReportById(conversation.reportAId)
                        } catch (_: Exception) {
                            null
                        }

                        val reportB = try {
                            api.fetchReportById(conversation.reportBId)
                        } catch (_: Exception) {
                            null
                        }

                        val otherUserReport = listOfNotNull(
                            reportA,
                            reportB
                        ).firstOrNull {
                            it.userId == otherUserId
                        }

                        val itemName =
                            otherUserReport?.title
                                ?: reportA?.title
                                ?: reportB?.title
                                ?: "FoundIt conversation"

                        val lastMessage = messages.lastOrNull()

                        result.add(
                            ChatInboxItem(
                                conversationId = conversation.id,
                                otherUserId = otherUserId,
                                username = profile.username.ifBlank {
                                    "FoundIt User"
                                },
                                avatarUrl = profile.avatarUrl,
                                lastMessage = lastMessage?.body
                                    ?: "Start your conversation...",
                                itemName = itemName,
                                lastMessageTime = formatTime(
                                    lastMessage?.createdAt
                                )
                            )
                        )
                    }

                    result
                }

                runOnUiThread {

                    adapter.submitList(chatItems)

                    if (chatItems.isEmpty()) {
                        recyclerChats.visibility = View.GONE
                        txtChatsEmpty.visibility = View.VISIBLE
                    } else {
                        recyclerChats.visibility = View.VISIBLE
                        txtChatsEmpty.visibility = View.GONE
                    }

                    isLoading = false
                    txtChatsRefresh.isEnabled = true
                    txtChatsRefresh.alpha = 1f
                }

            } catch (e: Exception) {

                runOnUiThread {

                    isLoading = false
                    txtChatsRefresh.isEnabled = true
                    txtChatsRefresh.alpha = 1f

                    Toast.makeText(
                        this,
                        e.message ?: "Could not load chats.",
                        Toast.LENGTH_LONG
                    ).show()

                    if (adapter.itemCount == 0) {
                        recyclerChats.visibility = View.GONE
                        txtChatsEmpty.visibility = View.VISIBLE
                    }
                }
            }

        }.start()
    }

    private fun openChat(chat: ChatInboxItem) {

        val intent = android.content.Intent(
            this,
            ChatActivity::class.java
        )

        intent.putExtra(
            "conversation_id",
            chat.conversationId
        )

        intent.putExtra(
            "other_username",
            chat.username
        )

        intent.putExtra(
            "item_name",
            chat.itemName
        )

        startActivity(intent)
    }

    private fun formatTime(value: String?): String {

        if (value.isNullOrBlank() || value == "null") {
            return ""
        }

        return try {

            val inputFormats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
            )

            var date: Date? = null

            for (pattern in inputFormats) {

                try {

                    val parser = SimpleDateFormat(
                        pattern,
                        Locale.US
                    )

                    parser.timeZone = TimeZone.getTimeZone("UTC")

                    date = parser.parse(value)

                    if (date != null) break

                } catch (_: Exception) {
                    // Try the next timestamp format.
                }
            }

            if (date == null) {
                return ""
            }

            SimpleDateFormat(
                "dd MMM, hh:mm a",
                Locale.getDefault()
            ).format(date)

        } catch (_: Exception) {
            ""
        }
    }
}
