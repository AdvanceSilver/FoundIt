
package com.foundit.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MatchDetailsActivity : AppCompatActivity() {

    private val scope = MainScope()
    private lateinit var api: SupabaseApi

    private lateinit var content: LinearLayout
    private lateinit var loadingText: TextView
    private lateinit var chatButton: Button

    private var notificationId: String? = null
    private var lostItemName = "Possible item match"

    private val backgroundColor = Color.rgb(11, 11, 18)
    private val cardColor = Color.rgb(23, 22, 33)
    private val purple = Color.rgb(167, 139, 250)
    private val mutedText = Color.rgb(165, 159, 181)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        api = SupabaseApi(this)
        notificationId = intent.getStringExtra("notification_id")

        buildScreen()
        loadBothReports()
    }

    private fun buildScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            fitsSystemWindows = true
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }

        // FIXED: Use ViewGroup.LayoutParams, not ScrollView.LayoutParams.
        scrollView.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val bottomArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
            setBackgroundColor(Color.rgb(17, 16, 25))
            elevation = dp(10).toFloat()
        }

        chatButton = Button(this).apply {
            text = "Start Private Chat   →"
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            isEnabled = false
            setTextColor(Color.WHITE)

            background = roundedBackground(
                Color.rgb(112, 77, 170),
                Color.rgb(167, 139, 250),
                16
            )

            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(58)

            setOnClickListener {
                startPrivateChat()
            }
        }

        bottomArea.addView(
            chatButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        )

        root.addView(
            bottomArea,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        buildHeader()
    }

    private fun buildHeader() {

        val backButton = TextView(this).apply {
            text = "‹  Back to Notifications"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(purple)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), dp(8), dp(16))

            setOnClickListener {
                finish()
            }
        }

        content.addView(
            backButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val topLabel = TextView(this).apply {
            text = "FOUND IT  /  MATCH DETAILS"
            textSize = 11f
            letterSpacing = 0f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(purple)
            setPadding(0, dp(5), 0, dp(9))
        }

        content.addView(topLabel)

        val heading = TextView(this).apply {
            text = "Possible Match"
            textSize = 29f
            letterSpacing = 0f
            typeface = Typeface.create(
                "sans-serif",
                Typeface.BOLD
            )
            setTextColor(Color.WHITE)
        }

        content.addView(heading)

        val subtitle = TextView(this).apply {
            text = "We found two reports that may be related."
            textSize = 14f
            letterSpacing = 0f
            setTextColor(mutedText)
            setPadding(0, dp(7), 0, dp(20))
        }

        content.addView(subtitle)

        // MATCH BANNER

        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))

            background = roundedBackground(
                Color.rgb(37, 29, 53),
                Color.rgb(112, 82, 155),
                18
            )
        }

        val matchIcon = TextView(this).apply {
            text = "✦"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(purple)

            background = roundedBackground(
                Color.rgb(63, 47, 86),
                Color.rgb(111, 84, 148),
                14
            )

            layoutParams = LinearLayout.LayoutParams(
                dp(48),
                dp(48)
            )
        }

        banner.addView(matchIcon)

        val bannerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }

        val bannerTitle = TextView(this).apply {
            text = "Potential item match"
            textSize = 15f
            letterSpacing = 0f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val bannerSubtitle = TextView(this).apply {
            text = "Compare both reports below"
            textSize = 12f
            letterSpacing = 0f
            setTextColor(Color.rgb(190, 177, 215))
            setPadding(0, dp(5), 0, 0)
        }

        bannerText.addView(bannerTitle)
        bannerText.addView(bannerSubtitle)

        banner.addView(
            bannerText,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        content.addView(
            banner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(23)
            }
        )

        val comparisonHeading = TextView(this).apply {
            text = "Compare reports"
            textSize = 20f
            letterSpacing = 0f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(15))
        }

        content.addView(comparisonHeading)

        loadingText = TextView(this).apply {
            text = "Loading both reports..."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(mutedText)
            setPadding(dp(8), dp(35), dp(8), dp(35))
        }

        content.addView(
            loadingText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun loadBothReports() {

        val firstId = intent.getStringExtra("first_report_id")
        val secondId = intent.getStringExtra("second_report_id")

        if (firstId.isNullOrBlank() || secondId.isNullOrBlank()) {
            loadingText.text = "Both report IDs are required."
            return
        }

        scope.launch {
            try {

                val firstReport = api.fetchReportById(firstId)
                val secondReport = api.fetchReportById(secondId)

                if (firstReport == null || secondReport == null) {
                    loadingText.text =
                        "One or both reports could not be found."
                    return@launch
                }

                val reports = listOf(firstReport, secondReport)

                val lostReport = reports.firstOrNull {
                    it.reportType.equals("lost", true)
                }

                val foundReport = reports.firstOrNull {
                    it.reportType.equals("found", true)
                }

                if (lostReport == null || foundReport == null) {
                    loadingText.text =
                        "These reports are not a Lost and Found pair."
                    return@launch
                }

                lostItemName = lostReport.title

                loadingText.visibility = View.GONE

                addReportCard(
                    "LOST ITEM",
                    "The item someone is looking for",
                    lostReport,
                    true
                )

                addReportCard(
                    "FOUND ITEM",
                    "The item someone has reported finding",
                    foundReport,
                    false
                )

                addSafetyNote()

                chatButton.isEnabled =
                    !notificationId.isNullOrBlank()

                if (!chatButton.isEnabled) {
                    chatButton.text = "Chat unavailable"
                    chatButton.alpha = 0.6f
                }

            } catch (e: Exception) {

                loadingText.visibility = View.VISIBLE
                loadingText.text =
                    "Could not load reports.\n${e.message}"

                Toast.makeText(
                    this@MatchDetailsActivity,
                    "Error loading reports.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun addReportCard(
        heading: String,
        description: String,
        report: Report,
        isLost: Boolean
    ) {

        val accent = if (isLost) {
            Color.rgb(255, 116, 130)
        } else {
            Color.rgb(89, 218, 151)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(15), dp(14), dp(16))

            background = roundedBackground(
                cardColor,
                if (isLost) {
                    Color.rgb(113, 55, 68)
                } else {
                    Color.rgb(48, 103, 79)
                },
                19
            )
        }

        val headingRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val typeTitle = TextView(this).apply {
            text = if (isLost) "🚨  $heading" else "✓  $heading"
            textSize = 17f
            letterSpacing = 0f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
        }

        val typeSubtitle = TextView(this).apply {
            text = description
            textSize = 12f
            letterSpacing = 0f
            setTextColor(mutedText)
            setPadding(0, dp(5), 0, dp(14))
        }

        headingRow.addView(typeTitle)
        headingRow.addView(typeSubtitle)

        card.addView(headingRow)

        // ITEM IMAGE

        val imageFrame = FrameLayout(this).apply {
            background = roundedBackground(
                Color.rgb(37, 35, 48),
                Color.rgb(66, 61, 81),
                15
            )
            clipToOutline = true
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "$heading photo"
        }

        imageFrame.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (!report.imageUrl.isNullOrBlank()) {

            Glide.with(this)
                .load(report.imageUrl)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(image)

        } else {
            image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            image.setPadding(dp(65), dp(65), dp(65), dp(65))
            image.setImageResource(android.R.drawable.ic_menu_gallery)
            image.setColorFilter(Color.rgb(130, 124, 145))
        }

        card.addView(
            imageFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(205)
            ).apply {
                bottomMargin = dp(14)
            }
        )

        val itemName = TextView(this).apply {
            text = report.title.ifBlank { "Unnamed item" }
            textSize = 21f
            letterSpacing = 0f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        card.addView(itemName)

        addDivider(card)

        addInfo(card, "Category", report.category)
        addInfo(card, "Description", report.description)
        addInfo(card, "Location", report.location)
        addInfo(card, "Reported on", report.createdAt)

        content.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(17)
            }
        )
    }

    private fun addInfo(
        parent: LinearLayout,
        label: String,
        value: String?
    ) {

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(8))
        }

        val title = TextView(this).apply {
            text = label
            textSize = 11f
            letterSpacing = 0f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(purple)
        }

        val detail = TextView(this).apply {
            text = value?.takeIf { it.isNotBlank() } ?: "Not provided"
            textSize = 15f
            letterSpacing = 0f
            setTextColor(Color.rgb(225, 221, 235))
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(5), 0, 0)
        }

        section.addView(title)
        section.addView(detail)

        parent.addView(
            section,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addDivider(parent: LinearLayout) {

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(55, 51, 68))
        }

        parent.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(3)
            }
        )
    }

    private fun addSafetyNote() {

        val note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))

            background = roundedBackground(
                Color.rgb(35, 29, 48),
                Color.rgb(91, 72, 122),
                16
            )
        }

        val title = TextView(this).apply {
            text = "ⓘ  Before you connect"
            textSize = 15f
            letterSpacing = 0f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(purple)
        }

        val message = TextView(this).apply {
            text = "This is a possible match, not a confirmed one. Compare the details and communicate with the other user before deciding whether the item belongs to them."
            textSize = 13f
            letterSpacing = 0f
            setTextColor(Color.rgb(207, 199, 222))
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        }

        note.addView(title)
        note.addView(message)

        content.addView(
            note,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        )
    }

    private fun startPrivateChat() {

        val id = notificationId

        if (id.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Notification information is missing.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        chatButton.isEnabled = false
        chatButton.text = "Opening chat..."

        scope.launch {
            try {

                val conversationId = api.createMatchConversation(id)

                val chatIntent = Intent(
                    this@MatchDetailsActivity,
                    ChatActivity::class.java
                ).apply {
                    putExtra("conversation_id", conversationId)
                    putExtra("other_username", "Possible Match")
                    putExtra("item_name", lostItemName)
                }

                startActivity(chatIntent)

            } catch (e: Exception) {

                Toast.makeText(
                    this@MatchDetailsActivity,
                    "Could not start chat: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                chatButton.isEnabled = true
                chatButton.text = "Start Private Chat   →"
            }
        }
    }

    private fun roundedBackground(
        color: Int,
        strokeColor: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()

            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
