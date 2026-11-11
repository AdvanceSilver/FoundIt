
package com.foundit.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ItemDetailsActivity : AppCompatActivity() {

    private val scope = MainScope()
    private lateinit var api: SupabaseApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.parseColor("#0B0B12")
        window.navigationBarColor = Color.parseColor("#0B0B12")

        setContentView(R.layout.activity_item_details)

        // Handle phone status bar and navigation bar spacing.
        val root = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                0,
                systemBars.top,
                0,
                systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(root)

        api = SupabaseApi(this)

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        val reportId = intent.getStringExtra("report_id")

        if (!reportId.isNullOrBlank()) {
            loadReport(reportId)
        } else {
            showReport(
                title = intent.getStringExtra("title").orEmpty(),
                category = intent.getStringExtra("category").orEmpty(),
                description = intent.getStringExtra("description").orEmpty(),
                location = intent.getStringExtra("location").orEmpty(),
                reportType = intent.getStringExtra("report_type").orEmpty(),
                imageUrl = intent.getStringExtra("image_url"),
                createdAt = intent.getStringExtra("created_at").orEmpty()
            )
        }
    }

    private fun loadReport(reportId: String) {
        findViewById<TextView>(R.id.detailTitle).text = "Loading report..."

        scope.launch {
            try {
                val report = api.fetchReportById(reportId)

                if (report == null) {
                    Toast.makeText(
                        this@ItemDetailsActivity,
                        "This report is no longer available.",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                    return@launch
                }

                showReport(
                    title = report.title,
                    category = report.category,
                    description = report.description,
                    location = report.location,
                    reportType = report.reportType,
                    imageUrl = report.imageUrl,
                    createdAt = report.createdAt
                )

            } catch (e: Exception) {
                Toast.makeText(
                    this@ItemDetailsActivity,
                    "Could not load report: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
        }
    }

    private fun showReport(
        title: String,
        category: String,
        description: String,
        location: String,
        reportType: String,
        imageUrl: String?,
        createdAt: String
    ) {
        findViewById<TextView>(R.id.detailTitle).text = title
        findViewById<TextView>(R.id.detailCategory).text = category

        findViewById<TextView>(R.id.detailDescription).text =
            description.ifBlank { "No description provided." }

        findViewById<TextView>(R.id.detailLocation).text = location

        findViewById<TextView>(R.id.detailDate).text =
            createdAt.take(10).ifBlank { "Not available" }

        val status = findViewById<TextView>(R.id.detailStatus)
        val isLost = reportType.equals("lost", true)

        status.text = if (isLost) {
            "🚨 LOST ITEM"
        } else {
            "✅ FOUND ITEM"
        }

        status.setTextColor(
            getColor(if (isLost) R.color.lost else R.color.found)
        )

        val image = findViewById<ImageView>(R.id.detailImage)

        if (!imageUrl.isNullOrBlank() && imageUrl != "null") {
            image.visibility = View.VISIBLE

            Glide.with(this)
                .load(imageUrl)
                .fitCenter()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(image)
        } else {
            image.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
