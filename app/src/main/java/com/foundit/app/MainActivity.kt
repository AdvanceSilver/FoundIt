package com.foundit.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val scope = MainScope()
    private lateinit var api: SupabaseApi

    private lateinit var profilePhotoPicker: ActivityResultLauncher<String>
    private lateinit var cropActivityLauncher: ActivityResultLauncher<Intent>

    private var selectedProfilePhotoUrl: String? = null
    private var selectedProfileShape: String = "circle"

    private lateinit var authPanel: LinearLayout
    private lateinit var homePanel: LinearLayout
    private lateinit var bottomNav: LinearLayout

    private lateinit var homeSection: LinearLayout
    private lateinit var exploreSection: LinearLayout
    private lateinit var lostDashboard: LinearLayout
    private lateinit var foundDashboard: LinearLayout
    private lateinit var notificationsSection: LinearLayout
    private lateinit var profileSection: LinearLayout

    private lateinit var lostAdapter: ReportAdapter
    private lateinit var foundAdapter: ReportAdapter
    private lateinit var myReportsAdapter: ReportAdapter

    private var allReports: List<Report> = emptyList()
    private var categoryFilter = "All"

    private var currentSection = "home"
    private var dashboardParentSection = "home"

    private var notificationList: List<AppNotification> = emptyList()

    private lateinit var notificationListContainer: LinearLayout
    private lateinit var notificationStatus: TextView
    private lateinit var markAllReadButton: Button
    private lateinit var allNotificationsCount: TextView
    private lateinit var unreadNotificationsCount: TextView
    private lateinit var notificationFilterContainer: LinearLayout

    private var selectedNotificationFilter = "All"

    private val openingNotificationIds = mutableSetOf<String>()
    private val deletingNotificationIds = mutableSetOf<String>()
    private val deletingReportIds = mutableSetOf<String>()
    private val changingReportStatusIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSystemBars()
        setContentView(R.layout.activity_main)

        api = SupabaseApi(this)

        profilePhotoPicker =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    openPhotoEditor(uri)
                }
            }

        cropActivityLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->

                if (result.resultCode == Activity.RESULT_OK) {

                    val path =
                        result.data?.getStringExtra("cropped_photo_path")

                    if (path.isNullOrBlank()) {
                        toast("Cropped photo path is missing.")
                        return@registerForActivityResult
                    }

                    val file = File(path)

                    if (!file.exists()) {
                        toast("Cropped photo could not be found.")
                        return@registerForActivityResult
                    }

                    selectedProfileShape = "circle"
                    uploadSelectedProfilePhoto(Uri.fromFile(file))
                }
            }

        authPanel = findViewById(R.id.authPanel)
        homePanel = findViewById(R.id.homePanel)
        bottomNav = findViewById(R.id.bottomNav)

        homeSection = findViewById(R.id.homeSection)
        exploreSection = findViewById(R.id.exploreSection)
        lostDashboard = findViewById(R.id.lostDashboard)
        foundDashboard = findViewById(R.id.foundDashboard)
        notificationsSection = findViewById(R.id.notificationsSection)
        profileSection = findViewById(R.id.profileSection)

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            authenticate(false)
        }

        findViewById<Button>(R.id.signupButton).setOnClickListener {
            authenticate(true)
        }

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            api.signOut()
            selectedProfilePhotoUrl = null
            selectedProfileShape = "circle"
            updateBottomProfilePhoto(null)
            showSignedOut()
        }

        findViewById<Button>(R.id.reportLostButton).setOnClickListener {
            openReport("lost")
        }

        findViewById<Button>(R.id.reportFoundButton).setOnClickListener {
            openReport("found")
        }

        findViewById<Button>(R.id.openLostDashboardButton).setOnClickListener {
            showDashboard("lost")
        }

        findViewById<Button>(R.id.openFoundDashboardButton).setOnClickListener {
            showDashboard("found")
        }

        findViewById<Button>(R.id.exploreLostButton).setOnClickListener {
            showDashboard("lost")
        }

        findViewById<Button>(R.id.exploreFoundButton).setOnClickListener {
            showDashboard("found")
        }

        findViewById<Button>(R.id.lostBackButton).setOnClickListener {
            returnFromDashboard()
        }

        findViewById<Button>(R.id.foundBackButton).setOnClickListener {
            returnFromDashboard()
        }

        // EXPLORE MAP CARD

        findViewById<LinearLayout>(R.id.openMapCard).setOnClickListener {
            startActivity(
                Intent(
                    this@MainActivity,
                    MapActivity::class.java
                )
            )
        }

        // Meeting Places navigation removed.
        // Map Explorer remains available.

        lostAdapter = ReportAdapter(::showDetails)
        foundAdapter = ReportAdapter(::showDetails)

        myReportsAdapter = ReportAdapter(
            onTap = ::showDetails,
            onDelete = ::confirmDeleteReport,
            onChangeStatus = ::showStatusDialog
        )

        findViewById<RecyclerView>(R.id.lostRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = lostAdapter
            isNestedScrollingEnabled = false
        }

        findViewById<RecyclerView>(R.id.foundRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = foundAdapter
            isNestedScrollingEnabled = false
        }

        findViewById<RecyclerView>(R.id.myReportsRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = myReportsAdapter
            isNestedScrollingEnabled = false
        }

        findViewById<Button>(R.id.saveProfileButton).setOnClickListener {
            saveProfile()
        }

        findViewById<Button>(R.id.chooseProfilePhotoButton).setOnClickListener {
            profilePhotoPicker.launch("image/*")
        }

        findViewById<View>(R.id.editProfilePhotoButton).setOnClickListener {
            profilePhotoPicker.launch("image/*")
        }

        setupFilters()
        setupSearch()
        setupBottomNavigation()
        setupNotificationsScreen()
        setupBackNavigation()

        if (api.isSignedIn()) {
            showSignedIn()
        } else {
            showSignedOut()
        }
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

    // =====================================================
    // BACK NAVIGATION
    // =====================================================

    private fun setupBackNavigation() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    val imm = getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager

                    val focusedView = currentFocus

                    if (focusedView != null && imm.isAcceptingText) {
                        imm.hideSoftInputFromWindow(
                            focusedView.windowToken,
                            0
                        )
                        focusedView.clearFocus()
                        return
                    }

                    when (currentSection) {

                        "lost", "found" -> returnFromDashboard()

                        "explore",
                        "profile",
                        "notifications" -> showSection("home")

                        "home" -> finish()

                        else -> showSection("home")
                    }
                }
            }
        )
    }

    // =====================================================
    // BOTTOM NAVIGATION
    // =====================================================

    private fun setupBottomNavigation() {

        findViewById<TextView>(R.id.navHome).setOnClickListener {
            showSection("home")
        }

        findViewById<TextView>(R.id.navExplore).setOnClickListener {
            showSection("explore")
        }

        findViewById<TextView>(R.id.navReport).setOnClickListener {

            updateBottomNavSelection("report")

            val dialog = AlertDialog.Builder(this)
                .setTitle("Report an item")
                .setItems(
                    arrayOf(
                        "🚨 Report Lost Item",
                        "✅ Report Found Item"
                    )
                ) { _, which ->

                    if (which == 0) {
                        openReport("lost")
                    } else {
                        openReport("found")
                    }
                }
                .create()

            dialog.setOnDismissListener {
                updateBottomNavSelection(currentSection)
            }

            dialog.show()
        }

        findViewById<TextView>(R.id.navChats).setOnClickListener {

            if (api.isSignedIn()) {

                updateBottomNavSelection("chats")

                startActivity(
                    Intent(
                        this,
                        ChatsActivity::class.java
                    )
                )

            } else {
                toast("Please sign in to access your chats.")
            }
        }

        findViewById<TextView>(R.id.navNotifications).setOnClickListener {
            showSection("notifications")
            loadNotifications()
        }

        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            showSection("profile")
            loadProfile()
        }
    }

    // =====================================================
    // BOTTOM NAVIGATION SELECTION DESIGN
    // =====================================================

    private fun updateBottomNavSelection(section: String) {

        val selectedPurple = Color.rgb(190, 158, 255)
        val normalColor = Color.rgb(175, 169, 191)

        val selectedSection = when (section) {
            "lost", "found" -> dashboardParentSection
            else -> section
        }

        val navItems = listOf(
            R.id.navHome to "home",
            R.id.navExplore to "explore",
            R.id.navReport to "report",
            R.id.navChats to "chats",
            R.id.navNotifications to "notifications"
        )

        navItems.forEach { (viewId, itemSection) ->

            val item = findViewById<TextView>(viewId)

            val isSelected = selectedSection == itemSection

            val iconColor = if (isSelected) {
                selectedPurple
            } else {
                normalColor
            }

            item.setTextColor(iconColor)

            item.compoundDrawables.forEach { drawable ->
                drawable?.mutate()?.setTint(iconColor)
            }

            item.background = null
            item.alpha = 1f
        }

        val profileItem = findViewById<LinearLayout>(R.id.navProfile)
        val profileLabel = findViewById<TextView>(R.id.navProfileLabel)

        val profileSelected = selectedSection == "profile"

        profileItem.background = null

        profileLabel.setTextColor(
            if (profileSelected) selectedPurple else normalColor
        )

        val profilePhoto = findViewById<ImageView>(R.id.navProfilePhoto)
        profilePhoto.imageTintList = null

        profileItem.alpha = 1f
    }

    // =====================================================
    // SECTION NAVIGATION
    // =====================================================

    private fun showSection(section: String) {

        currentSection = section

        homeSection.visibility = View.GONE
        exploreSection.visibility = View.GONE
        lostDashboard.visibility = View.GONE
        foundDashboard.visibility = View.GONE
        notificationsSection.visibility = View.GONE
        profileSection.visibility = View.GONE

        when (section) {

            "home" -> homeSection.visibility = View.VISIBLE

            "explore" -> exploreSection.visibility = View.VISIBLE

            "lost" -> lostDashboard.visibility = View.VISIBLE

            "found" -> foundDashboard.visibility = View.VISIBLE

            "notifications" -> notificationsSection.visibility = View.VISIBLE

            "profile" -> profileSection.visibility = View.VISIBLE
        }

        updateBottomNavSelection(section)
    }

    private fun showDashboard(type: String) {

        dashboardParentSection = currentSection

        showSection(type)

        applyFilters(
            findViewById<EditText>(R.id.searchInput).text.toString()
        )
    }

    private fun returnFromDashboard() {
        showSection(dashboardParentSection)
    }

    // =====================================================
    // NOTIFICATIONS SCREEN
    // =====================================================

    private fun setupNotificationsScreen() {

        notificationsSection.removeAllViews()
        notificationsSection.orientation = LinearLayout.VERTICAL

        notificationsSection.setPadding(
            0, dp(10), 0, dp(18)
        )

        val topLabel = TextView(this).apply {
            text = "YOUR ACTIVITY"
            textSize = 10f
            letterSpacing = 0.9f
            setTextColor(Color.rgb(167, 139, 250))
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
            )
        }

        notificationsSection.addView(topLabel, matchWrap())

        val heading = TextView(this).apply {
            text = "Notifications"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(
                "sans-serif",
                Typeface.BOLD
            )
            setPadding(0, dp(4), 0, 0)
        }

        notificationsSection.addView(heading, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Updates about your lost and found reports."
            textSize = 12f
            setTextColor(Color.rgb(160, 155, 175))
            setPadding(0, dp(4), 0, dp(15))
        }

        notificationsSection.addView(subtitle, matchWrap())

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val allCard = createNotificationStatCard("TOTAL", "0")
        allNotificationsCount = allCard.second

        val unreadCard = createNotificationStatCard("UNREAD", "0")
        unreadNotificationsCount = unreadCard.second

        val allParams = LinearLayout.LayoutParams(
            0,
            dp(78),
            1f
        ).apply {
            marginEnd = dp(5)
        }

        val unreadParams = LinearLayout.LayoutParams(
            0,
            dp(78),
            1f
        ).apply {
            marginStart = dp(5)
        }

        statsRow.addView(allCard.first, allParams)
        statsRow.addView(unreadCard.first, unreadParams)

        notificationsSection.addView(statsRow, matchWrap())

        val activityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(28), 0, dp(10))
        }

        val activityTitle = TextView(this).apply {
            text = "Recent activity"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
            )

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        activityRow.addView(activityTitle)

        markAllReadButton = Button(this).apply {
            text = "Mark all read"
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.rgb(215, 198, 255))

            background = roundedBackground(
                Color.rgb(43, 34, 62),
                Color.rgb(103, 78, 145),
                12
            )

            setPadding(dp(9), 0, dp(9), 0)
            minHeight = dp(34)
            minimumHeight = dp(34)
            visibility = View.GONE

            setOnClickListener {
                markAllNotificationsRead()
            }
        }

        activityRow.addView(markAllReadButton)

        notificationsSection.addView(activityRow, matchWrap())

        notificationFilterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        notificationsSection.addView(
            notificationFilterContainer,
            matchWrap()
        )

        setupNotificationFilters()

        notificationStatus = TextView(this).apply {
            text = "Loading notifications..."
            textSize = 13f
            setTextColor(Color.rgb(180, 174, 195))
            gravity = Gravity.CENTER

            setPadding(
                dp(8), dp(32), dp(8), dp(32)
            )
        }

        notificationsSection.addView(
            notificationStatus,
            matchWrap()
        )

        notificationListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        notificationsSection.addView(
            notificationListContainer,
            matchWrap()
        )
    }

    private fun createNotificationStatCard(
        label: String,
        initialValue: String
    ): Pair<LinearLayout, TextView> {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(9), dp(13), dp(9))

            background = roundedBackground(
                Color.rgb(24, 22, 34),
                Color.rgb(57, 49, 75),
                15
            )
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = 10f
            letterSpacing = 0.4f
            setTextColor(Color.rgb(170, 160, 190))
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
            )
            maxLines = 1
        }

        val valueView = TextView(this).apply {
            text = initialValue
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, 0)
        }

        card.addView(labelView, matchWrap())
        card.addView(valueView, matchWrap())

        return Pair(card, valueView)
    }

    private fun setupNotificationFilters() {

        notificationFilterContainer.removeAllViews()

        val filters = listOf("All", "Unread", "Read")

        filters.forEach { filter ->

            val chip = TextView(this).apply {

                text = when (filter) {
                    "All" -> "All"
                    "Unread" -> "●  Unread"
                    else -> "✓  Read"
                }

                textSize = 12f
                gravity = Gravity.CENTER

                setPadding(
                    dp(14), dp(8), dp(14), dp(8)
                )

                background = roundedBackground(
                    if (selectedNotificationFilter == filter) {
                        Color.rgb(100, 72, 151)
                    } else {
                        Color.rgb(28, 26, 38)
                    },
                    if (selectedNotificationFilter == filter) {
                        Color.rgb(167, 139, 250)
                    } else {
                        Color.rgb(65, 59, 78)
                    },
                    30
                )

                setTextColor(
                    if (selectedNotificationFilter == filter) {
                        Color.WHITE
                    } else {
                        Color.rgb(185, 179, 200)
                    }
                )

                setOnClickListener {
                    selectedNotificationFilter = filter
                    setupNotificationFilters()
                    displayNotifications()
                }
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(7)
            }

            notificationFilterContainer.addView(chip, params)
        }
    }

    private fun loadNotifications() {

        notificationStatus.text = "Loading notifications..."
        notificationStatus.visibility = View.VISIBLE
        notificationListContainer.removeAllViews()

        scope.launch {
            try {
                notificationList = api.fetchNotifications()
                displayNotifications()

            } catch (e: Exception) {
                notificationStatus.visibility = View.VISIBLE
                notificationStatus.text =
                    "Could not load notifications.\n${e.message}"

                markAllReadButton.visibility = View.GONE
                allNotificationsCount.text = "—"
                unreadNotificationsCount.text = "—"
            }
        }
    }

    private fun displayNotifications() {

        notificationListContainer.removeAllViews()

        val totalCount = notificationList.size
        val unreadCount = notificationList.count { !it.isRead }

        allNotificationsCount.text = totalCount.toString()
        unreadNotificationsCount.text = unreadCount.toString()

        markAllReadButton.visibility =
            if (unreadCount > 0) View.VISIBLE else View.GONE

        markAllReadButton.isEnabled = true

        val filteredNotifications = when (selectedNotificationFilter) {
            "Unread" -> notificationList.filter { !it.isRead }
            "Read" -> notificationList.filter { it.isRead }
            else -> notificationList
        }

        if (notificationList.isEmpty()) {
            notificationStatus.visibility = View.VISIBLE
            notificationStatus.text =
                "🔔\n\nYou're all caught up!\n\nUpdates about your lost and found reports will appear here."
            return
        }

        if (filteredNotifications.isEmpty()) {
            notificationStatus.visibility = View.VISIBLE

            notificationStatus.text =
                if (selectedNotificationFilter == "Unread") {
                    "✨\n\nNo unread notifications.\n\nYou're up to date!"
                } else {
                    "No read notifications yet."
                }

            return
        }

        notificationStatus.visibility = View.GONE

        filteredNotifications.forEach { notification ->

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL

                setPadding(
                    dp(13), dp(13), dp(13), dp(12)
                )

                background = roundedBackground(
                    if (notification.isRead) {
                        Color.rgb(23, 22, 32)
                    } else {
                        Color.rgb(43, 34, 59)
                    },
                    if (notification.isRead) {
                        Color.rgb(57, 53, 70)
                    } else {
                        Color.rgb(130, 99, 184)
                    },
                    16
                )

                isClickable = true
                isFocusable = true
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val iconCircle = TextView(this).apply {
                text = getNotificationIcon(
                    notification.notificationType
                )

                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(210, 190, 255))

                background = roundedBackground(
                    Color.rgb(65, 48, 91),
                    Color.rgb(100, 77, 137),
                    13
                )

                layoutParams = LinearLayout.LayoutParams(
                    dp(40),
                    dp(40)
                )
            }

            titleRow.addView(iconCircle)

            val titleColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(4), 0)

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val title = TextView(this).apply {
                text = notification.title.ifBlank {
                    "FoundIt update"
                }

                textSize = 15f
                setTextColor(Color.WHITE)

                typeface = Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
                )

                maxLines = 2
                setLineSpacing(dp(1).toFloat(), 1f)
            }

            val category = TextView(this).apply {
                text = getNotificationCategory(
                    notification.notificationType
                )

                textSize = 9f
                letterSpacing = 0.3f
                setTextColor(Color.rgb(176, 158, 215))

                typeface = Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
                )

                setPadding(0, dp(3), 0, 0)
            }

            titleColumn.addView(title)
            titleColumn.addView(category)

            titleRow.addView(titleColumn)

            if (!notification.isRead) {

                val unreadDot = TextView(this).apply {
                    text = "●"
                    textSize = 12f
                    setTextColor(Color.rgb(190, 155, 255))
                    gravity = Gravity.CENTER
                    setPadding(dp(3), 0, dp(4), 0)
                }

                titleRow.addView(unreadDot)
            }

            val deleteButton = TextView(this).apply {
                text = "✕"
                textSize = 16f
                setTextColor(Color.rgb(255, 132, 145))
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(5), dp(2), dp(5))
                contentDescription = "Delete notification"

                setOnClickListener {
                    confirmDeleteNotification(notification)
                }
            }

            titleRow.addView(deleteButton)

            card.addView(titleRow, matchWrap())

            val message = TextView(this).apply {
                text = notification.message
                textSize = 14f
                setTextColor(Color.rgb(215, 211, 225))

                typeface = Typeface.create(
                    "sans-serif",
                    Typeface.NORMAL
                )

                setLineSpacing(dp(3).toFloat(), 1f)

                setPadding(
                    0, dp(11), 0, dp(12)
                )
            }

            card.addView(message, matchWrap())

            val bottomRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val date = TextView(this).apply {
                text = "◷  ${formatNotificationDate(notification.createdAt)}"
                textSize = 10f
                setTextColor(Color.rgb(166, 158, 183))

                typeface = Typeface.create(
                    "sans-serif",
                    Typeface.NORMAL
                )

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            bottomRow.addView(date)

            val tapHint = TextView(this).apply {
                text = if (notification.matchedReportId.isNullOrBlank()) {
                    "View report  →"
                } else {
                    "View match  →"
                }

                textSize = 11f

                typeface = Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
                )

                setTextColor(Color.rgb(190, 165, 255))
            }

            bottomRow.addView(tapHint)

            card.addView(bottomRow, matchWrap())

            card.setOnClickListener {
                openNotification(notification)
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(9)
            }

            notificationListContainer.addView(card, params)
        }
    }

    private fun getNotificationIcon(type: String): String {

        val value = type.lowercase()

        return when {
            value.contains("match") -> "✦"
            value.contains("lost") -> "⌕"
            value.contains("found") -> "✓"
            value.contains("status") -> "↗"
            value.contains("report") -> "▤"
            else -> "●"
        }
    }

    private fun getNotificationCategory(type: String): String {

        val value = type.lowercase()

        return when {
            value.contains("match") -> "POSSIBLE MATCH"
            value.contains("lost") -> "LOST ITEM UPDATE"
            value.contains("found") -> "FOUND ITEM UPDATE"
            value.contains("status") -> "REPORT STATUS"
            value.contains("report") -> "REPORT UPDATE"
            else -> "GENERAL UPDATE"
        }
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), strokeColor)
        }
    }

    // =====================================================
    // DELETE NOTIFICATION
    // =====================================================

    private fun confirmDeleteNotification(
        notification: AppNotification
    ) {

        if (notification.id in deletingNotificationIds) return

        AlertDialog.Builder(this)
            .setTitle("Delete notification?")
            .setMessage(
                "This notification will be permanently removed from your list."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteNotification(notification)
            }
            .show()
    }

    private fun deleteNotification(
        notification: AppNotification
    ) {

        if (!deletingNotificationIds.add(notification.id)) return

        scope.launch {
            try {
                api.deleteNotification(notification.id)

                notificationList = notificationList.filter {
                    it.id != notification.id
                }

                displayNotifications()
                toast("Notification deleted.")

            } catch (e: Exception) {
                toast("Could not delete notification: ${e.message}")

            } finally {
                deletingNotificationIds.remove(notification.id)
            }
        }
    }

    // =====================================================
    // OPEN NOTIFICATION
    // =====================================================

    private fun openNotification(
        notification: AppNotification
    ) {

        val firstReportId = notification.relatedReportId
        val secondReportId = notification.matchedReportId

        if (firstReportId.isNullOrBlank()) {
            toast("No related report is available.")
            return
        }

        if (!openingNotificationIds.add(notification.id)) {
            return
        }

        scope.launch {
            try {

                if (!notification.isRead) {

                    api.markNotificationAsRead(notification.id)

                    notificationList = notificationList.map {
                        if (it.id == notification.id) {
                            it.copy(isRead = true)
                        } else {
                            it
                        }
                    }

                    displayNotifications()
                }

                if (!secondReportId.isNullOrBlank()) {

                    val intent = Intent(
                        this@MainActivity,
                        MatchDetailsActivity::class.java
                    )

                    intent.putExtra("first_report_id", firstReportId)
                    intent.putExtra("second_report_id", secondReportId)
                    intent.putExtra("notification_id", notification.id)

                    startActivity(intent)

                } else {

                    val intent = Intent(
                        this@MainActivity,
                        ItemDetailsActivity::class.java
                    )

                    intent.putExtra("report_id", firstReportId)

                    startActivity(intent)
                }

            } catch (e: Exception) {

                toast("Could not open notification: ${e.message}")

            } finally {

                openingNotificationIds.remove(notification.id)
            }
        }
    }

    // =====================================================
    // MARK ALL AS READ
    // =====================================================

    private fun markAllNotificationsRead() {

        markAllReadButton.isEnabled = false

        scope.launch {
            try {
                api.markAllNotificationsAsRead()

                notificationList = notificationList.map {
                    it.copy(isRead = true)
                }

                displayNotifications()
                toast("All notifications marked as read.")

            } catch (e: Exception) {
                toast("Could not update notifications: ${e.message}")

            } finally {
                markAllReadButton.isEnabled = true
            }
        }
    }

    private fun formatNotificationDate(value: String): String {
        return try {
            value.replace("T", " ")
                .substringBefore(".")
                .removeSuffix("Z")
                .trim()
        } catch (_: Exception) {
            value
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun matchWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // =====================================================
    // FILTERS
    // =====================================================

    private fun setupFilters() {

        val ids = listOf(
            R.id.chipAll,
            R.id.chipDocuments,
            R.id.chipElectronics,
            R.id.chipKeys,
            R.id.chipOthers
        )

        val values = listOf(
            "All",
            "Documents",
            "Electronics",
            "Keys",
            "Others"
        )

        ids.forEachIndexed { index, id ->
            findViewById<Chip>(id).setOnClickListener {
                categoryFilter = values[index]

                applyFilters(
                    findViewById<EditText>(R.id.searchInput).text.toString()
                )
            }
        }
    }

    private fun setupSearch() {

        findViewById<EditText>(R.id.searchInput)
            .addTextChangedListener(object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    applyFilters(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) {}
            })
    }

    // =====================================================
    // AUTHENTICATION
    // =====================================================

    private fun authenticate(signUp: Boolean) {

        val email = findViewById<EditText>(R.id.emailInput)
            .text.toString().trim()

        val password = findViewById<EditText>(R.id.passwordInput)
            .text.toString()

        if (email.isBlank() || password.length < 6) {
            toast("Enter a valid email and a password with at least 6 characters.")
            return
        }

        scope.launch {
            try {
                val message = if (signUp) {
                    api.signUp(email, password)
                } else {
                    api.signIn(email, password)
                }

                toast(message)

                if (api.isSignedIn()) {
                    showSignedIn()
                }

            } catch (e: Exception) {
                toast(e.message ?: "Something went wrong.")
            }
        }
    }

    private fun showSignedIn() {

        authPanel.visibility = View.GONE
        homePanel.visibility = View.VISIBLE
        bottomNav.visibility = View.VISIBLE

        showSection("home")

        loadReports()
        loadProfile()
    }

    private fun showSignedOut() {

        authPanel.visibility = View.VISIBLE
        homePanel.visibility = View.GONE
        bottomNav.visibility = View.GONE

        currentSection = "home"
        dashboardParentSection = "home"

        allReports = emptyList()
        notificationList = emptyList()

        if (::lostAdapter.isInitialized) {
            lostAdapter.submit(emptyList())
            foundAdapter.submit(emptyList())
            myReportsAdapter.submit(emptyList())
        }

        findViewById<TextView>(R.id.profileTotalReports).text = "0"
        findViewById<TextView>(R.id.profileLostReports).text = "0"
        findViewById<TextView>(R.id.profileFoundReports).text = "0"

        findViewById<EditText>(R.id.usernameInput).setText("")
        findViewById<EditText>(R.id.bioInput).setText("")

        displayProfilePhoto(null)

        updateBottomNavSelection("home")
    }

    // =====================================================
    // REPORTS
    // =====================================================

    private fun loadReports() {

        scope.launch {
            try {
                allReports = api.fetchReports()

                applyFilters(
                    findViewById<EditText>(R.id.searchInput).text.toString()
                )

            } catch (e: Exception) {
                toast("Could not load reports: ${e.message}")
            }
        }
    }

    private fun applyFilters(query: String) {

        val filtered = allReports.filter {

            val categoryMatches =
                categoryFilter == "All" ||
                it.category.equals(categoryFilter, true)

            val searchMatches =
                query.isBlank() ||
                it.title.contains(query, true) ||
                it.category.contains(query, true) ||
                it.location.contains(query, true) ||
                it.description.contains(query, true)

            categoryMatches && searchMatches
        }

        lostAdapter.submit(
            filtered.filter { it.reportType.equals("lost", true) }
        )

        foundAdapter.submit(
            filtered.filter { it.reportType.equals("found", true) }
        )
    }

    // =====================================================
    // PROFILE FUNCTIONS
    // =====================================================

    private fun loadProfile() {

        scope.launch {
            try {
                val profile = api.fetchProfile()

                findViewById<EditText>(R.id.usernameInput)
                    .setText(profile.username)

                findViewById<EditText>(R.id.bioInput)
                    .setText(profile.bio)

                selectedProfilePhotoUrl = profile.avatarUrl
                selectedProfileShape = "circle"

                displayProfilePhoto(profile.avatarUrl)
                updateBottomProfilePhoto(profile.avatarUrl)

                findViewById<TextView>(R.id.profileStatus).text =
                    "Profile loaded successfully."

            } catch (e: Exception) {
                findViewById<TextView>(R.id.profileStatus).text =
                    "Could not load profile: ${e.message}"
            }
        }

        loadMyReports()
    }

    private fun displayProfilePhoto(photoUrl: String?) {

        val imageView =
            findViewById<ImageView>(R.id.profileImageView)

        imageView.background = null
        imageView.setPadding(0, 0, 0, 0)

        if (photoUrl.isNullOrBlank()) {
            Glide.with(this).clear(imageView)
            imageView.setImageResource(android.R.drawable.ic_menu_camera)
            return
        }

        Glide.with(this)
            .load(photoUrl)
            .circleCrop()
            .placeholder(android.R.drawable.ic_menu_camera)
            .error(android.R.drawable.ic_menu_camera)
            .into(imageView)
    }

    private fun updateBottomProfilePhoto(photoUrl: String?) {

        val imageView =
            findViewById<ImageView>(R.id.navProfilePhoto)

        imageView.imageTintList = null
        imageView.background = null
        imageView.setPadding(0, 0, 0, 0)
        imageView.visibility = View.VISIBLE

        if (photoUrl.isNullOrBlank()) {
            Glide.with(this).clear(imageView)
            imageView.setImageResource(R.drawable.ic_nav_profile)
            return
        }

        Glide.with(this)
            .load(photoUrl)
            .circleCrop()
            .placeholder(R.drawable.ic_nav_profile)
            .error(R.drawable.ic_nav_profile)
            .into(imageView)
    }

    private fun openPhotoEditor(uri: Uri) {

        val intent = Intent(this, CropActivity::class.java)
        intent.putExtra("photo_uri", uri.toString())

        cropActivityLauncher.launch(intent)
    }

    private fun uploadSelectedProfilePhoto(uri: Uri) {

        val photoButton =
            findViewById<Button>(R.id.chooseProfilePhotoButton)

        val status =
            findViewById<TextView>(R.id.profileStatus)

        photoButton.isEnabled = false
        status.text = "Uploading profile photo..."

        displayProfilePhoto(uri.toString())
        updateBottomProfilePhoto(uri.toString())

        scope.launch {
            try {
                val username = findViewById<EditText>(R.id.usernameInput)
                    .text.toString().trim()

                val bio = findViewById<EditText>(R.id.bioInput)
                    .text.toString().trim()

                val photoUrl = api.uploadProfileImage(uri)

                api.saveProfile(
                    username = username,
                    bio = bio,
                    avatarUrl = photoUrl,
                    avatarShape = "circle"
                )

                selectedProfilePhotoUrl = photoUrl
                selectedProfileShape = "circle"

                displayProfilePhoto(photoUrl)
                updateBottomProfilePhoto(photoUrl)

                status.text =
                    "Profile photo uploaded successfully!"

                toast("Profile photo saved!")

            } catch (e: Exception) {

                status.text =
                    "Photo upload failed: ${e.message}"

                displayProfilePhoto(selectedProfilePhotoUrl)
                updateBottomProfilePhoto(selectedProfilePhotoUrl)

                toast("Could not upload profile photo.")

            } finally {
                photoButton.isEnabled = true
            }
        }
    }

    private fun saveProfile() {

        val username = findViewById<EditText>(R.id.usernameInput)
            .text.toString().trim()

        val bio = findViewById<EditText>(R.id.bioInput)
            .text.toString().trim()

        if (username.isBlank()) {
            toast("Please enter a username.")
            return
        }

        if (username.length > 30) {
            toast("Username must be 30 characters or less.")
            return
        }

        if (bio.length > 200) {
            toast("About Me must be 200 characters or less.")
            return
        }

        val saveButton =
            findViewById<Button>(R.id.saveProfileButton)

        saveButton.isEnabled = false

        scope.launch {
            try {
                api.saveProfile(
                    username,
                    bio,
                    selectedProfilePhotoUrl,
                    "circle"
                )

                findViewById<TextView>(R.id.profileStatus).text =
                    "Profile saved successfully!"

                toast("Profile saved!")

            } catch (e: Exception) {
                findViewById<TextView>(R.id.profileStatus).text =
                    "Could not save profile: ${e.message}"

                toast("Could not save profile.")

            } finally {
                saveButton.isEnabled = true
            }
        }
    }

    // =====================================================
    // LOAD MY REPORTS + PROFILE STATISTICS
    // =====================================================

    private fun loadMyReports() {

        scope.launch {
            try {
                val reports = api.fetchMyReports()

                myReportsAdapter.submit(reports)

                val totalReports = reports.size

                val lostReports = reports.count {
                    it.reportType.equals("lost", true)
                }

                val foundReports = reports.count {
                    it.reportType.equals("found", true)
                }

                findViewById<TextView>(R.id.profileTotalReports).text =
                    totalReports.toString()

                findViewById<TextView>(R.id.profileLostReports).text =
                    lostReports.toString()

                findViewById<TextView>(R.id.profileFoundReports).text =
                    foundReports.toString()

            } catch (e: Exception) {

                findViewById<TextView>(R.id.profileTotalReports).text = "—"
                findViewById<TextView>(R.id.profileLostReports).text = "—"
                findViewById<TextView>(R.id.profileFoundReports).text = "—"

                toast("Could not load your reports: ${e.message}")
            }
        }
    }

    // =====================================================
    // CHANGE REPORT STATUS
    // =====================================================

    private fun showStatusDialog(report: Report) {

        if (report.id in changingReportStatusIds) return

        val statuses = arrayOf(
            "Active",
            "Item Found",
            "Returned to Owner",
            "Closed"
        )

        val statusValues = arrayOf(
            "active",
            "found",
            "returned",
            "closed"
        )

        val currentIndex = statusValues.indexOf(report.status)
            .takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Change Report Status")
            .setSingleChoiceItems(
                statuses,
                currentIndex
            ) { dialog, which ->

                dialog.dismiss()

                updateMyReportStatus(
                    report,
                    statusValues[which]
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMyReportStatus(
        report: Report,
        newStatus: String
    ) {

        if (!changingReportStatusIds.add(report.id)) return

        scope.launch {
            try {

                api.updateReportStatus(
                    report.id,
                    newStatus
                )

                toast("Report status updated successfully!")

                loadMyReports()
                loadReports()

            } catch (e: Exception) {

                toast(
                    "Could not update status: ${e.message}"
                )

            } finally {

                changingReportStatusIds.remove(report.id)
            }
        }
    }

    // =====================================================
    // DELETE MY REPORT
    // =====================================================

    private fun confirmDeleteReport(report: Report) {

        if (report.id in deletingReportIds) return

        AlertDialog.Builder(this)
            .setTitle("Delete report?")
            .setMessage(
                "Are you sure you want to permanently delete \"${report.title}\"?"
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteMyReport(report)
            }
            .show()
    }

    private fun deleteMyReport(report: Report) {

        if (!deletingReportIds.add(report.id)) return

        scope.launch {
            try {
                api.deleteReport(report.id)

                toast("Report deleted successfully.")

                loadMyReports()
                loadReports()

            } catch (e: Exception) {
                toast("Could not delete report: ${e.message}")

            } finally {
                deletingReportIds.remove(report.id)
            }
        }
    }

    // =====================================================
    // OTHER FUNCTIONS
    // =====================================================

    private fun openReport(type: String) {

        startActivity(
            Intent(this, ReportActivity::class.java)
                .putExtra("report_type", type)
        )
    }

    private fun showDetails(report: Report) {

        val intent = Intent(this, ItemDetailsActivity::class.java)

        intent.putExtra("title", report.title)
        intent.putExtra("category", report.category)
        intent.putExtra("description", report.description)
        intent.putExtra("location", report.location)
        intent.putExtra("report_type", report.reportType)
        intent.putExtra("image_url", report.imageUrl)
        intent.putExtra("created_at", report.createdAt)

        startActivity(intent)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // =====================================================
    // ACTIVITY LIFECYCLE
    // =====================================================

    override fun onResume() {
        super.onResume()

        if (
            ::api.isInitialized &&
            api.isSignedIn() &&
            ::lostAdapter.isInitialized
        ) {
            loadReports()
        }

        if (
            ::bottomNav.isInitialized &&
            bottomNav.visibility == View.VISIBLE
        ) {
            updateBottomNavSelection(currentSection)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}