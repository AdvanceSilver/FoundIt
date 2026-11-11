
package com.foundit.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {

    private lateinit var cropView: PhotoCropView
    private lateinit var root: LinearLayout

    private var loadedBitmap: Bitmap? = null

    private val purple = Color.rgb(167, 139, 250)
    private val backgroundColor = Color.rgb(11, 11, 18)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        createLayout()

        val uriString = intent.getStringExtra("photo_uri")

        if (uriString.isNullOrBlank()) {
            showError("No photo was selected.")
            return
        }

        try {

            val uri = Uri.parse(uriString)

            loadedBitmap = loadBitmap(uri)

            if (loadedBitmap == null) {
                showError("Could not open this photo. Please select another photo.")
                return
            }

            cropView.post {
                loadedBitmap?.let {
                    cropView.setImage(it)
                }
            }

        } catch (e: Exception) {

            showError(
                "Photo loading failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    private fun createLayout() {

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                dp(16) + systemBars.left,
                dp(12) + systemBars.top,
                dp(16) + systemBars.right,
                dp(12) + systemBars.bottom
            )

            insets
        }

        val title = TextView(this).apply {
            text = "Adjust Your Photo"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val instructions = TextView(this).apply {
            text = "Drag to move your photo. Pinch with two fingers to zoom."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(12))
        }

        cropView = PhotoCropView(this).apply {
            setBackgroundColor(Color.rgb(20, 20, 25))
            minimumHeight = dp(220)
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(65, 65, 75))
            setOnClickListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        val saveButton = Button(this).apply {
            text = "Use Photo"
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(purple)
            setOnClickListener {
                saveCroppedPhoto()
            }
        }

        val buttonParams = LinearLayout.LayoutParams(
            0,
            dp(50),
            1f
        ).apply {
            marginStart = dp(5)
            marginEnd = dp(5)
        }

        buttonRow.addView(cancelButton, buttonParams)
        buttonRow.addView(saveButton, buttonParams)

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            instructions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            cropView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(8)
            }
        )

        setContentView(root)

        ViewCompat.requestApplyInsets(root)
    }

    private fun loadBitmap(uri: Uri): Bitmap? {

        return if (Build.VERSION.SDK_INT >= 28) {

            val source = ImageDecoder.createSource(
                contentResolver,
                uri
            )

            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

        } else {

            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        }
    }

    private fun saveCroppedPhoto() {

        val croppedBitmap = cropView.getCroppedBitmap()

        if (croppedBitmap == null) {
            Toast.makeText(
                this,
                "Please wait for your photo to load.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {

            val file = File(
                cacheDir,
                "profile_${System.currentTimeMillis()}.jpg"
            )

            FileOutputStream(file).use { output ->

                croppedBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    output
                )
            }

            val resultIntent = android.content.Intent().apply {
                putExtra("cropped_photo_path", file.absolutePath)
            }

            setResult(Activity.RESULT_OK, resultIntent)

            croppedBitmap.recycle()

            finish()

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Could not save photo: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showError(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()

        finish()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadedBitmap?.recycle()
        loadedBitmap = null
    }
}
