
package com.foundit.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class UserProfile(
    val userId: String,
    val username: String,
    val bio: String,
    val avatarUrl: String? = null,
    val avatarShape: String = ""
)

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val notificationType: String,
    val relatedReportId: String?,
    val matchedReportId: String?,
    val isRead: Boolean,
    val createdAt: String
)

class SupabaseApi(context: Context) {

    private val session = SessionStore(context.applicationContext)
    private val resolver = context.applicationContext.contentResolver

    // CONNECTION

    private suspend fun connection(
        path: String,
        method: String,
        token: String? = session.accessToken
    ): HttpURLConnection {

        if (token != null) {
            refreshSessionIfNeeded()
        }

        val currentToken = if (token != null) {
            session.accessToken
        } else {
            null
        }

        val url = URL(SupabaseConfig.URL.trimEnd('/') + path)

        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 20000

            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty(
                "Authorization",
                "Bearer ${currentToken ?: SupabaseConfig.ANON_KEY}"
            )
            setRequestProperty("Content-Type", "application/json")
        }
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val code = conn.responseCode

        val stream = if (code in 200..299) {
            conn.inputStream
        } else {
            conn.errorStream
        }

        val text = stream?.bufferedReader()
            ?.use(BufferedReader::readText)
            .orEmpty()

        if (code !in 200..299) {
            val message = try {
                val json = JSONObject(text)
                json.optString(
                    "msg",
                    json.optString(
                        "message",
                        json.optString("error_description", text)
                    )
                )
            } catch (_: Exception) {
                text
            }

            throw Exception(
                message.ifBlank { "Request failed ($code)" }
            )
        }

        return text
    }

    // JWT EXPIRY CHECK

    private fun getTokenExpiry(token: String): Long? {
        return try {
            val parts = token.split(".")

            if (parts.size < 2) return null

            val payload = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val json = JSONObject(String(payload, Charsets.UTF_8))

            json.optLong("exp", 0L)
                .takeIf { it > 0L }
                ?.times(1000L)

        } catch (_: Exception) {
            null
        }
    }

    // AUTOMATIC SESSION REFRESH

    private suspend fun refreshSessionIfNeeded() {

        val accessToken = session.accessToken
            ?: throw Exception("Please sign in again.")

        val expiry = getTokenExpiry(accessToken)

        if (expiry != null &&
            System.currentTimeMillis() < expiry - 60000L
        ) {
            return
        }

        val refreshToken = session.refreshToken

        if (refreshToken.isNullOrBlank()) {
            session.clear()
            throw Exception("Your session expired. Please sign in again.")
        }

        try {
            val c = connection(
                "/auth/v1/token?grant_type=refresh_token",
                "POST",
                null
            )

            c.doOutput = true

            c.outputStream.use {
                it.write(
                    JSONObject()
                        .put("refresh_token", refreshToken)
                        .toString()
                        .toByteArray()
                )
            }

            val result = JSONObject(readResponse(c))

            val newAccessToken = result.optString("access_token")
            val newRefreshToken = result.optString("refresh_token")

            val user = result.optJSONObject("user")
            val newUserId = user?.optString("id")
                ?.takeIf { !it.isNullOrBlank() && it != "null" }
                ?: session.userId

            if (newAccessToken.isBlank() ||
                newRefreshToken.isBlank() ||
                newUserId.isNullOrBlank()
            ) {
                throw Exception("Invalid refresh response.")
            }

            saveSession(
                newAccessToken,
                newRefreshToken,
                newUserId
            )

        } catch (e: Exception) {
            if (e.message?.contains("refresh token", true) == true ||
                e.message?.contains("invalid", true) == true
            ) {
                session.clear()
            }

            throw Exception(
                "Could not refresh your session. Please sign in again. ${e.message}"
            )
        }
    }

    // SAVE SESSION

    private fun saveSession(
        token: String,
        refreshToken: String,
        uid: String
    ) {
        session.accessToken = token
        session.refreshToken = refreshToken
        session.userId = uid
    }

    fun isSignedIn(): Boolean {
        return !session.accessToken.isNullOrBlank() &&
                !session.userId.isNullOrBlank()
    }

    fun currentUserId(): String? {
        return session.userId
    }

    fun signOut() {
        session.clear()
    }

    // AUTHENTICATION

    suspend fun signUp(email: String, password: String): String =
        withContext(Dispatchers.IO) {

            val c = connection("/auth/v1/signup", "POST", null)
            c.doOutput = true

            c.outputStream.use {
                it.write(
                    JSONObject()
                        .put("email", email)
                        .put("password", password)
                        .toString()
                        .toByteArray()
                )
            }

            val result = JSONObject(readResponse(c))
            val token = result.optString("access_token")
            val refresh = result.optString("refresh_token")
            val user = result.optJSONObject("user") ?: result

            if (token.isNotBlank() && refresh.isNotBlank()) {
                saveSession(token, refresh, user.optString("id"))
            }

            if (token.isBlank()) {
                "Account created. Check your email to confirm, then sign in."
            } else {
                "Account created and signed in."
            }
        }

    suspend fun signIn(email: String, password: String): String =
        withContext(Dispatchers.IO) {

            val c = connection(
                "/auth/v1/token?grant_type=password",
                "POST",
                null
            )

            c.doOutput = true

            c.outputStream.use {
                it.write(
                    JSONObject()
                        .put("email", email)
                        .put("password", password)
                        .toString()
                        .toByteArray()
                )
            }

            val result = JSONObject(readResponse(c))

            val token = result.getString("access_token")
            val refresh = result.optString("refresh_token")
            val uid = result.getJSONObject("user").getString("id")

            if (refresh.isBlank()) {
                throw Exception("Could not obtain a refresh token. Please try again.")
            }

            saveSession(token, refresh, uid)

            "Signed in successfully."
        }

    // PROFILE FUNCTIONS

    suspend fun fetchProfile(): UserProfile =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val c = connection(
                "/rest/v1/profiles?select=user_id,username,bio,avatar_url,avatar_shape&user_id=eq.$uid",
                "GET"
            )

            val array = JSONArray(readResponse(c))

            if (array.length() == 0) {
                UserProfile(uid, "", "", null, "")
            } else {
                val profile = array.getJSONObject(0)

                UserProfile(
                    userId = profile.optString("user_id"),
                    username = profile.optString("username"),
                    bio = profile.optString("bio"),
                    avatarUrl = profile.optString("avatar_url")
                        .takeIf { it.isNotBlank() && it != "null" },
                    avatarShape = profile.optString("avatar_shape")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: ""
                )
            }
        }

    suspend fun fetchProfile(userId: String): UserProfile =
        withContext(Dispatchers.IO) {

            val safeUid = Uri.encode(userId)

            val c = connection(
                "/rest/v1/profiles?select=user_id,username,bio,avatar_url,avatar_shape&user_id=eq.$safeUid",
                "GET"
            )

            val array = JSONArray(readResponse(c))

            if (array.length() == 0) {
                UserProfile(userId, "FoundIt User", "", null, "")
            } else {
                val profile = array.getJSONObject(0)

                UserProfile(
                    userId = profile.optString("user_id"),
                    username = profile.optString("username")
                        .ifBlank { "FoundIt User" },
                    bio = profile.optString("bio"),
                    avatarUrl = profile.optString("avatar_url")
                        .takeIf { it.isNotBlank() && it != "null" },
                    avatarShape = profile.optString("avatar_shape")
                        .takeIf { it.isNotBlank() && it != "null" } ?: ""
                )
            }
        }

    suspend fun saveProfile(
        username: String,
        bio: String,
        avatarUrl: String? = null,
        avatarShape: String? = null
    ) = withContext(Dispatchers.IO) {

        val uid = session.userId
            ?: throw Exception("Please sign in again.")

        val body = JSONObject()
            .put("user_id", uid)
            .put("username", username)
            .put("bio", bio)

        if (avatarUrl != null) body.put("avatar_url", avatarUrl)
        if (avatarShape != null) body.put("avatar_shape", avatarShape)

        val c = connection(
            "/rest/v1/profiles?on_conflict=user_id",
            "POST"
        )

        c.setRequestProperty(
            "Prefer",
            "resolution=merge-duplicates,return=representation"
        )

        c.doOutput = true

        c.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        readResponse(c)
    }

    // REPORT FUNCTIONS

    suspend fun fetchReports(): List<Report> =
        withContext(Dispatchers.IO) {

            val c = connection(
                "/rest/v1/reports?select=*&order=created_at.desc",
                "GET"
            )

            parseReports(readResponse(c))
        }

    suspend fun fetchMyReports(): List<Report> =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val c = connection(
                "/rest/v1/reports?select=*&user_id=eq.$uid&order=created_at.desc",
                "GET"
            )

            parseReports(readResponse(c))
        }

    suspend fun fetchReportById(reportId: String): Report? =
        withContext(Dispatchers.IO) {

            val safeId = Uri.encode(reportId)

            val c = connection(
                "/rest/v1/reports?select=*&id=eq.$safeId",
                "GET"
            )

            parseReports(readResponse(c)).firstOrNull()
        }

    // UPDATE REPORT STATUS

    suspend fun updateReportStatus(
        reportId: String,
        newStatus: String
    ) = withContext(Dispatchers.IO) {

        val uid = session.userId
            ?: throw Exception("Please sign in again.")

        val allowedStatuses = setOf(
            "active",
            "found",
            "returned",
            "closed"
        )

        if (newStatus !in allowedStatuses) {
            throw Exception("Invalid report status.")
        }

        val safeId = Uri.encode(reportId)
        val safeUid = Uri.encode(uid)

        val dateFormat = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val updatedAt = dateFormat.format(Date())

        val body = JSONObject()
            .put("status", newStatus)
            .put("status_updated_at", updatedAt)

        val c = connection(
            "/rest/v1/reports?id=eq.$safeId&user_id=eq.$safeUid",
            "PATCH"
        )

        c.setRequestProperty("Prefer", "return=representation")
        c.doOutput = true

        c.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        val result = JSONArray(readResponse(c))

        if (result.length() == 0) {
            throw Exception(
                "Report not found or you do not have permission to update it."
            )
        }
    }

    // PARSE REPORTS

    private fun parseReports(response: String): List<Report> {
        val array = JSONArray(response)

        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)

            val latitude = o.optDouble(
                "latitude",
                Double.NaN
            ).takeIf { it.isFinite() }

            val longitude = o.optDouble(
                "longitude",
                Double.NaN
            ).takeIf { it.isFinite() }

            Report(
                id = o.getString("id"),
                userId = o.getString("user_id"),
                title = o.optString("title"),
                category = o.optString("category"),
                description = o.optString("description"),
                location = o.optString("location"),
                reportType = o.optString("report_type"),
                imageUrl = o.optString("image_url").takeIf {
                    it.isNotBlank() && it != "null"
                },
                createdAt = o.optString("created_at"),

                status = o.optString("status").takeIf {
                    it in setOf("active", "found", "returned", "closed")
                } ?: "active",

                statusUpdatedAt = o.optString("status_updated_at").takeIf {
                    it.isNotBlank() && it != "null"
                },

                latitude = latitude,
                longitude = longitude
            )
        }
    }

    // NOTIFICATION FUNCTIONS

    suspend fun fetchNotifications(): List<AppNotification> =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val c = connection(
                "/rest/v1/notifications?select=*&user_id=eq.$uid&order=created_at.desc",
                "GET"
            )

            val array = JSONArray(readResponse(c))

            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)

                AppNotification(
                    id = item.getString("id"),
                    title = item.optString("title"),
                    message = item.optString("message"),
                    notificationType = item.optString("notification_type"),
                    relatedReportId = item.optString("related_report_id")
                        .takeIf { it.isNotBlank() && it != "null" },
                    matchedReportId = item.optString("matched_report_id")
                        .takeIf { it.isNotBlank() && it != "null" },
                    isRead = item.optBoolean("is_read", false),
                    createdAt = item.optString("created_at")
                )
            }
        }

    suspend fun markNotificationAsRead(notificationId: String) =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val safeId = Uri.encode(notificationId)

            val c = connection(
                "/rest/v1/notifications?id=eq.$safeId&user_id=eq.$uid",
                "PATCH"
            )

            c.setRequestProperty("Prefer", "return=minimal")
            c.doOutput = true

            c.outputStream.use {
                it.write(
                    JSONObject()
                        .put("is_read", true)
                        .toString()
                        .toByteArray()
                )
            }

            readResponse(c)
        }

    suspend fun markAllNotificationsAsRead() =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val c = connection(
                "/rest/v1/notifications?user_id=eq.$uid&is_read=eq.false",
                "PATCH"
            )

            c.setRequestProperty("Prefer", "return=minimal")
            c.doOutput = true

            c.outputStream.use {
                it.write(
                    JSONObject()
                        .put("is_read", true)
                        .toString()
                        .toByteArray()
                )
            }

            readResponse(c)
        }

    suspend fun deleteNotification(notificationId: String) =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val safeId = Uri.encode(notificationId)
            val safeUid = Uri.encode(uid)

            val c = connection(
                "/rest/v1/notifications?id=eq.$safeId&user_id=eq.$safeUid",
                "DELETE"
            )

            c.setRequestProperty("Prefer", "return=minimal")

            readResponse(c)
        }

    // PRIVATE CHAT FUNCTIONS

    suspend fun createMatchConversation(
        notificationId: String
    ): String = withContext(Dispatchers.IO) {

        if (!isSignedIn()) {
            throw Exception("Please sign in again.")
        }

        val safeNotificationId = UUID.fromString(notificationId).toString()

        val c = connection(
            "/rest/v1/rpc/create_match_conversation",
            "POST"
        )

        c.doOutput = true

        c.outputStream.use {
            it.write(
                JSONObject()
                    .put("p_notification_id", safeNotificationId)
                    .toString()
                    .toByteArray()
            )
        }

        val response = readResponse(c).trim()
        val conversationId = response.removeSurrounding("\"")

        if (conversationId.isBlank() ||
            conversationId == "null"
        ) {
            throw Exception("Could not create the conversation.")
        }

        conversationId
    }

    suspend fun fetchMatchConversations(): List<MatchConversation> =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val safeUid = Uri.encode(uid)

            val c = connection(
                "/rest/v1/match_conversations?select=*&or=(user_a_id.eq.$safeUid,user_b_id.eq.$safeUid)&order=created_at.desc",
                "GET"
            )

            val array = JSONArray(readResponse(c))

            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)

                MatchConversation(
                    id = item.getString("id"),
                    reportAId = item.getString("report_a_id"),
                    reportBId = item.getString("report_b_id"),
                    userAId = item.getString("user_a_id"),
                    userBId = item.getString("user_b_id"),
                    createdAt = item.optString("created_at")
                        .takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }

    // LOAD MESSAGES INCLUDING DELIVERY AND READ STATUS

    suspend fun fetchMatchMessages(
        conversationId: String
    ): List<MatchMessage> = withContext(Dispatchers.IO) {

        if (!isSignedIn()) {
            throw Exception("Please sign in again.")
        }

        val safeConversationId =
            Uri.encode(UUID.fromString(conversationId).toString())

        val c = connection(
            "/rest/v1/match_messages?select=*&conversation_id=eq.$safeConversationId&order=created_at.asc",
            "GET"
        )

        val array = JSONArray(readResponse(c))

        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)

            MatchMessage(
                id = item.getString("id"),
                conversationId = item.getString("conversation_id"),
                senderId = item.getString("sender_id"),
                body = item.getString("body"),
                createdAt = item.optString("created_at")
                    .takeIf { it.isNotBlank() && it != "null" },
                deliveredAt = item.optString("delivered_at")
                    .takeIf { it.isNotBlank() && it != "null" },
                readAt = item.optString("read_at")
                    .takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    // MARK INCOMING MESSAGES AS DELIVERED AND READ

    suspend fun markMatchMessagesRead(
        conversationId: String
    ) = withContext(Dispatchers.IO) {

        if (!isSignedIn()) {
            throw Exception("Please sign in again.")
        }

        val safeConversationId =
            UUID.fromString(conversationId).toString()

        val c = connection(
            "/rest/v1/rpc/mark_match_messages_read",
            "POST"
        )

        c.doOutput = true

        c.outputStream.use {
            it.write(
                JSONObject()
                    .put("p_conversation_id", safeConversationId)
                    .toString()
                    .toByteArray()
            )
        }

        readResponse(c)
    }

    // SEND A MESSAGE

    suspend fun sendMatchMessage(
        conversationId: String,
        message: String
    ): MatchMessage = withContext(Dispatchers.IO) {

        val uid = session.userId
            ?: throw Exception("Please sign in again.")

        val cleanMessage = message.trim()

        if (cleanMessage.isBlank()) {
            throw Exception("Message cannot be empty.")
        }

        if (cleanMessage.length > 3000) {
            throw Exception("Message is too long. Maximum 3000 characters.")
        }

        val safeConversationId =
            UUID.fromString(conversationId).toString()

        val body = JSONObject()
            .put("conversation_id", safeConversationId)
            .put("sender_id", uid)
            .put("body", cleanMessage)

        val c = connection(
            "/rest/v1/match_messages",
            "POST"
        )

        c.setRequestProperty(
            "Prefer",
            "return=representation"
        )

        c.doOutput = true

        c.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        val result = JSONArray(readResponse(c))

        if (result.length() == 0) {
            throw Exception("Message could not be sent.")
        }

        val item = result.getJSONObject(0)

        MatchMessage(
            id = item.getString("id"),
            conversationId = item.getString("conversation_id"),
            senderId = item.getString("sender_id"),
            body = item.getString("body"),
            createdAt = item.optString("created_at")
                .takeIf { it.isNotBlank() && it != "null" },
            deliveredAt = item.optString("delivered_at")
                .takeIf { it.isNotBlank() && it != "null" },
            readAt = item.optString("read_at")
                .takeIf { it.isNotBlank() && it != "null" }
        )
    }

    // EDIT YOUR OWN MESSAGE

    suspend fun updateMatchMessage(
        messageId: String,
        conversationId: String,
        newBody: String
    ) = withContext(Dispatchers.IO) {

        val uid = session.userId
            ?: throw Exception("Please sign in again.")

        val cleanMessage = newBody.trim()

        if (cleanMessage.isBlank()) {
            throw Exception("Message cannot be empty.")
        }

        if (cleanMessage.length > 3000) {
            throw Exception("Message is too long. Maximum 3000 characters.")
        }

        val safeMessageId =
            Uri.encode(UUID.fromString(messageId).toString())

        val safeConversationId =
            Uri.encode(UUID.fromString(conversationId).toString())

        val safeUid = Uri.encode(uid)

        val body = JSONObject()
            .put("body", cleanMessage)

        val c = connection(
            "/rest/v1/match_messages?id=eq.$safeMessageId&conversation_id=eq.$safeConversationId&sender_id=eq.$safeUid",
            "PATCH"
        )

        c.setRequestProperty(
            "Prefer",
            "return=representation"
        )

        c.doOutput = true

        c.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        val result = JSONArray(readResponse(c))

        if (result.length() == 0) {
            throw Exception(
                "Message could not be edited. It may not belong to you."
            )
        }
    }

    // DELETE YOUR OWN MESSAGE

    suspend fun deleteMatchMessage(
        messageId: String,
        conversationId: String
    ) = withContext(Dispatchers.IO) {

        val uid = session.userId
            ?: throw Exception("Please sign in again.")

        val safeMessageId =
            Uri.encode(UUID.fromString(messageId).toString())

        val safeConversationId =
            Uri.encode(UUID.fromString(conversationId).toString())

        val safeUid = Uri.encode(uid)

        val c = connection(
            "/rest/v1/match_messages?id=eq.$safeMessageId&conversation_id=eq.$safeConversationId&sender_id=eq.$safeUid",
            "DELETE"
        )

        c.setRequestProperty(
            "Prefer",
            "return=representation"
        )

        val result = JSONArray(readResponse(c))

        if (result.length() == 0) {
            throw Exception(
                "Message could not be deleted. It may not belong to you."
            )
        }
    }

    // DELETE REPORT PHOTO FROM STORAGE

    private suspend fun deleteReportImageFromStorage(
        imageUrl: String,
        uid: String
    ) = withContext(Dispatchers.IO) {

        val imageUri = Uri.parse(imageUrl)
        val baseUri = Uri.parse(SupabaseConfig.URL)

        if (!imageUri.host.equals(baseUri.host, ignoreCase = true)) {
            return@withContext
        }

        val marker =
            "/storage/v1/object/public/${SupabaseConfig.BUCKET}/"

        val imagePath = imageUri.encodedPath
            ?.substringAfter(marker, "")
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext

        val decodedPath = Uri.decode(imagePath)

        if (!decodedPath.startsWith("$uid/") ||
            decodedPath.startsWith("$uid/profiles/")
        ) {
            return@withContext
        }

        val c = connection(
            "/storage/v1/object/${SupabaseConfig.BUCKET}",
            "DELETE"
        )

        c.doOutput = true

        c.outputStream.use {
            it.write(
                JSONObject()
                    .put("prefixes", JSONArray().put(decodedPath))
                    .toString()
                    .toByteArray()
            )
        }

        readResponse(c)
    }

    // DELETE MY REPORT AND ITS PHOTO

    suspend fun deleteReport(reportId: String) =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val safeId = Uri.encode(reportId)
            val safeUid = Uri.encode(uid)

            val fetchConnection = connection(
                "/rest/v1/reports?select=image_url&id=eq.$safeId&user_id=eq.$safeUid",
                "GET"
            )

            val reports = JSONArray(readResponse(fetchConnection))

            if (reports.length() == 0) {
                throw Exception("Report not found or you cannot delete it.")
            }

            val imageUrl = reports.getJSONObject(0)
                .optString("image_url")
                .takeIf { it.isNotBlank() && it != "null" }

            if (imageUrl != null) {
                deleteReportImageFromStorage(imageUrl, uid)
            }

            val c = connection(
                "/rest/v1/reports?id=eq.$safeId&user_id=eq.$safeUid",
                "DELETE"
            )

            c.setRequestProperty("Prefer", "return=minimal")

            readResponse(c)
        }

    // REPORT IMAGE UPLOAD

    suspend fun uploadImage(uri: Uri): String =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val mime = resolver.getType(uri) ?: "image/jpeg"

            if (!mime.startsWith("image/")) {
                throw Exception("Please choose an image.")
            }

            val extension = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }

            val objectPath = "$uid/${UUID.randomUUID()}.$extension"

            val c = connection(
                "/storage/v1/object/${SupabaseConfig.BUCKET}/$objectPath",
                "POST"
            )

            c.setRequestProperty("Content-Type", mime)
            c.setRequestProperty("x-upsert", "false")
            c.doOutput = true

            resolver.openInputStream(uri).use { input ->
                if (input == null) {
                    throw Exception("Cannot read selected photo.")
                }

                c.outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            readResponse(c)

            "${SupabaseConfig.URL.trimEnd('/')}/storage/v1/object/public/${SupabaseConfig.BUCKET}/$objectPath"
        }

    // PROFILE IMAGE UPLOAD

    suspend fun uploadProfileImage(uri: Uri): String =
        withContext(Dispatchers.IO) {

            val uid = session.userId
                ?: throw Exception("Please sign in again.")

            val mime = resolver.getType(uri) ?: "image/jpeg"

            if (!mime.startsWith("image/")) {
                throw Exception("Please choose a profile photo.")
            }

            val extension = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }

            val objectPath =
                "$uid/profiles/${UUID.randomUUID()}.$extension"

            val c = connection(
                "/storage/v1/object/${SupabaseConfig.BUCKET}/$objectPath",
                "POST"
            )

            c.setRequestProperty("Content-Type", mime)
            c.setRequestProperty("x-upsert", "false")
            c.doOutput = true

            resolver.openInputStream(uri).use { input ->
                if (input == null) {
                    throw Exception("Cannot read selected profile photo.")
                }

                c.outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            readResponse(c)

            "${SupabaseConfig.URL.trimEnd('/')}/storage/v1/object/public/${SupabaseConfig.BUCKET}/$objectPath"
        }

    // CREATE REPORT WITH OPTIONAL MAP COORDINATES

    suspend fun createReport(
        title: String,
        category: String,
        description: String,
        location: String,
        reportType: String,
        imageUrl: String?,
        latitude: Double? = null,
        longitude: Double? = null
    ) = withContext(Dispatchers.IO) {

        val uid = session.userId
            ?: throw Exception("Please sign in again.")

        val body = JSONObject()
            .put("user_id", uid)
            .put("title", title)
            .put("category", category)
            .put("description", description)
            .put("location", location)
            .put("report_type", reportType)

        if (imageUrl != null) {
            body.put("image_url", imageUrl)
        }

        if (
            latitude != null &&
            longitude != null &&
            latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
        ) {
            body.put("latitude", latitude)
            body.put("longitude", longitude)
        }

        val c = connection("/rest/v1/reports", "POST")

        c.setRequestProperty("Prefer", "return=representation")
        c.doOutput = true

        c.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        readResponse(c)
    }
}
