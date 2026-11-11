
package com.foundit.app

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReportActivity : AppCompatActivity() {

    private val scope = MainScope()

    private lateinit var api: SupabaseApi
    private lateinit var reportScrollView: ScrollView

    private var selectedPhoto: Uri? = null
    private var reportType = "lost"
    private var isPublishing = false

    private var originalBottomPadding = 0
    private var keyboardVisible = false

    // Selected map coordinates
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    private val mapPicker =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {

                val data = result.data ?: return@registerForActivityResult

                val latitude = data.getDoubleExtra(
                    "latitude",
                    Double.NaN
                )

                val longitude = data.getDoubleExtra(
                    "longitude",
                    Double.NaN
                )

                if (
                    latitude.isFinite() &&
                    longitude.isFinite() &&
                    latitude in -90.0..90.0 &&
                    longitude in -180.0..180.0
                ) {

                    selectedLatitude = latitude
                    selectedLongitude = longitude

                    findViewById<TextView>(
                        R.id.mapCoordinateText
                    ).apply {
                        text = String.format(
                            java.util.Locale.US,
                            "✓ Location selected: %.5f, %.5f",
                            latitude,
                            longitude
                        )
                        setTextColor(
                            Color.rgb(85, 217, 149)
                        )
                    }

                    Toast.makeText(
                        this,
                        "Map location selected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val photoPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {

                selectedPhoto = uri

                val preview =
                    findViewById<ImageView>(R.id.photoPreview)

                val removeButton =
                    findViewById<Button>(R.id.removePhotoButton)

                val photoButton =
                    findViewById<Button>(R.id.photoButton)

                preview.setImageURI(uri)
                preview.visibility = View.VISIBLE
                removeButton.visibility = View.VISIBLE

                photoButton.text = "CHANGE PHOTO"

                animateView(preview)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        setContentView(R.layout.activity_report)

        reportScrollView = findViewById(R.id.reportRoot)

        originalBottomPadding = reportScrollView.paddingBottom

        setupKeyboardScrolling()

        api = SupabaseApi(this)

        reportType = (
            intent.getStringExtra("report_type") ?: "lost"
        ).lowercase()

        setupHeading()
        setupCategories()
        setupButtons()
        setupAnimations()
    }

    private fun setupKeyboardScrolling() {

        ViewCompat.setOnApplyWindowInsetsListener(
            reportScrollView
        ) { view, insets ->

            val imeInsets = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            val keyboardIsVisible = insets.isVisible(
                WindowInsetsCompat.Type.ime()
            )

            if (keyboardIsVisible) {

                keyboardVisible = true

                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    imeInsets.bottom + dp(20)
                )

                view.post {
                    if (!isFinishing && !isDestroyed) {
                        moveFocusedFieldAboveKeyboard()
                    }
                }

            } else {

                keyboardVisible = false

                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    originalBottomPadding
                )
            }

            insets
        }

        ViewCompat.requestApplyInsets(reportScrollView)
    }

    private fun moveFocusedFieldAboveKeyboard() {

        if (!keyboardVisible) return

        val focusedView = currentFocus ?: return

        if (focusedView !is EditText) return

        reportScrollView.post {

            if (isFinishing || isDestroyed) return@post

            if (!keyboardVisible || !focusedView.hasFocus()) {
                return@post
            }

            val fieldRect = Rect()

            focusedView.getDrawingRect(fieldRect)

            reportScrollView.offsetDescendantRectToMyCoords(
                focusedView,
                fieldRect
            )

            val visibleBottom =
                reportScrollView.height -
                reportScrollView.paddingBottom

            val extraSpace = dp(30)

            val fieldBottom = fieldRect.bottom

            val requiredScroll =
                reportScrollView.scrollY +
                fieldBottom -
                visibleBottom +
                extraSpace

            if (requiredScroll > reportScrollView.scrollY) {

                reportScrollView.smoothScrollTo(
                    0,
                    requiredScroll
                )
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun setupHeading() {

        val heading =
            findViewById<TextView>(R.id.reportHeading)

        val subtitle =
            findViewById<TextView>(R.id.reportSubtitle)

        if (reportType == "lost") {

            heading.text = "🚨 Report Lost Item"
            heading.setTextColor(Color.rgb(255, 105, 125))

            subtitle.text =
                "Help others identify and return your missing item."

        } else {

            heading.text = "✓ Report Found Item"
            heading.setTextColor(Color.rgb(85, 217, 149))

            subtitle.text =
                "Help reunite a found item with its owner."
        }
    }

    private fun setupCategories() {

        val categories = listOf(
            "Documents",
            "Electronics",
            "Keys",
            "Bags",
            "Clothing",
            "Pets",
            "Others"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        findViewById<Spinner>(R.id.categorySpinner).adapter =
            adapter
    }

    private fun setupButtons() {

        val photoButton =
            findViewById<Button>(R.id.photoButton)

        val removePhotoButton =
            findViewById<Button>(R.id.removePhotoButton)

        val publishButton =
            findViewById<Button>(R.id.publishButton)

        val mapLocationButton =
            findViewById<Button>(R.id.mapLocationButton)

        val accent = if (reportType == "lost") {
            Color.rgb(255, 82, 106)
        } else {
            Color.rgb(85, 217, 149)
        }

        photoButton.background = roundedBackground(
            Color.rgb(30, 27, 43),
            accent,
            20f
        )

        photoButton.backgroundTintList = null
        photoButton.stateListAnimator = null
        photoButton.setTextColor(accent)

        mapLocationButton.background = roundedBackground(
            Color.rgb(30, 27, 43),
            accent,
            20f
        )

        mapLocationButton.backgroundTintList = null
        mapLocationButton.stateListAnimator = null
        mapLocationButton.setTextColor(accent)

        removePhotoButton.background = roundedBackground(
            Color.rgb(45, 25, 32),
            Color.rgb(180, 65, 85),
            18f
        )

        removePhotoButton.backgroundTintList = null
        removePhotoButton.stateListAnimator = null

        removePhotoButton.setTextColor(
            Color.rgb(255, 120, 135)
        )

        publishButton.background = roundedBackground(
            Color.rgb(118, 83, 184),
            Color.rgb(175, 145, 240),
            20f
        )

        publishButton.backgroundTintList = null
        publishButton.stateListAnimator = null
        publishButton.setTextColor(Color.WHITE)

        photoButton.setOnClickListener {
            animateButton(photoButton)
            photoPicker.launch("image/*")
        }

        mapLocationButton.setOnClickListener {

            animateButton(mapLocationButton)

            val intent = Intent(
                this,
                MapActivity::class.java
            )

            intent.putExtra("select_location", true)

            selectedLatitude?.let {
                intent.putExtra("latitude", it)
            }

            selectedLongitude?.let {
                intent.putExtra("longitude", it)
            }

            mapPicker.launch(intent)
        }

        removePhotoButton.setOnClickListener {

            selectedPhoto = null

            val preview =
                findViewById<ImageView>(R.id.photoPreview)

            preview.setImageDrawable(null)
            preview.visibility = View.GONE

            removePhotoButton.visibility = View.GONE
            photoButton.text = "＋  SELECT PHOTO"

            Toast.makeText(
                this,
                "Photo removed",
                Toast.LENGTH_SHORT
            ).show()
        }

        publishButton.setOnClickListener {
            animateButton(publishButton)
            publish()
        }
    }

    private fun setupAnimations() {

        val content =
            findViewById<View>(R.id.reportFormContainer)

        content.alpha = 0f
        content.translationY = 24f

        content.post {
            content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun animateView(view: View) {

        view.alpha = 0f
        view.translationY = 12f

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun animateButton(button: View) {

        button.animate().cancel()

        button.scaleX = 1f
        button.scaleY = 1f

        button.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(80)
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun roundedBackground(
        fillColor: Int,
        borderColor: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius
            setStroke(2, borderColor)
        }
    }

    private fun publish() {

        if (isPublishing) return

        val titleInput =
            findViewById<EditText>(R.id.titleInput)

        val locationInput =
            findViewById<EditText>(R.id.locationInput)

        val descriptionInput =
            findViewById<EditText>(R.id.descriptionInput)

        val title = titleInput.text.toString().trim()
        val location = locationInput.text.toString().trim()
        val description = descriptionInput.text.toString().trim()

        val category = findViewById<Spinner>(
            R.id.categorySpinner
        ).selectedItem.toString()

        if (title.isBlank()) {
            titleInput.error = "Please enter the item name"
            titleInput.requestFocus()
            return
        }

        if (location.isBlank()) {
            locationInput.error = "Please enter a general location"
            locationInput.requestFocus()
            return
        }

        isPublishing = true

        val publishButton =
            findViewById<Button>(R.id.publishButton)

        publishButton.isEnabled = false
        publishButton.text = "PUBLISHING..."
        publishButton.alpha = 0.7f

        scope.launch {

            try {

                val imageUrl = selectedPhoto?.let {
                    api.uploadImage(it)
                }

                api.createReport(
                    title,
                    category,
                    description,
                    location,
                    reportType,
                    imageUrl,
                    selectedLatitude,
                    selectedLongitude
                )

                if (!isFinishing && !isDestroyed) {

                    Toast.makeText(
                        this@ReportActivity,
                        "Your report was published!",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                }

            } catch (e: Exception) {

                isPublishing = false

                if (!isFinishing && !isDestroyed) {

                    publishButton.isEnabled = true
                    publishButton.text = "PUBLISH REPORT"
                    publishButton.alpha = 1f

                    Toast.makeText(
                        this@ReportActivity,
                        "Could not publish: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
