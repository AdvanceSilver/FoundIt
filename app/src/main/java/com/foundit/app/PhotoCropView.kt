
package com.foundit.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class PhotoCropView(context: Context) : View(context) {

    private var sourceBitmap: Bitmap? = null
    private val imageMatrix = Matrix()

    private val imagePaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val cropRect = RectF()

    private var lastX = 0f
    private var lastY = 0f
    private var initialized = false

    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {

                    imageMatrix.postScale(
                        detector.scaleFactor,
                        detector.scaleFactor,
                        detector.focusX,
                        detector.focusY
                    )

                    clampImage()
                    invalidate()
                    return true
                }
            }
        )

    fun setImage(bitmap: Bitmap) {
        sourceBitmap = bitmap
        initialized = false

        if (width > 0 && height > 0) {
            setupImage()
        }

        invalidate()
    }

    private fun setupImage() {

        val bitmap = sourceBitmap ?: return

        val size = min(width, height).toFloat()

        if (size <= 0f) return

        val left = (width - size) / 2f
        val top = (height - size) / 2f

        cropRect.set(
            left,
            top,
            left + size,
            top + size
        )

        val scale = max(
            size / bitmap.width,
            size / bitmap.height
        )

        val x = cropRect.centerX() - bitmap.width * scale / 2f
        val y = cropRect.centerY() - bitmap.height * scale / 2f

        imageMatrix.reset()
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(x, y)

        initialized = true
        clampImage()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupImage()
    }

    private fun clampImage() {

        val bitmap = sourceBitmap ?: return

        val imageBounds = RectF(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat()
        )

        imageMatrix.mapRect(imageBounds)

        var dx = 0f
        var dy = 0f

        if (imageBounds.left > cropRect.left) {
            dx = cropRect.left - imageBounds.left
        } else if (imageBounds.right < cropRect.right) {
            dx = cropRect.right - imageBounds.right
        }

        if (imageBounds.top > cropRect.top) {
            dy = cropRect.top - imageBounds.top
        } else if (imageBounds.bottom < cropRect.bottom) {
            dy = cropRect.bottom - imageBounds.bottom
        }

        imageMatrix.postTranslate(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(20, 20, 25))

        val bitmap = sourceBitmap ?: return

        if (!initialized) return

        canvas.save()

        canvas.clipRect(cropRect)

        canvas.drawBitmap(
            bitmap,
            imageMatrix,
            imagePaint
        )

        canvas.restore()

        canvas.drawRect(cropRect, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {

                if (!scaleDetector.isInProgress &&
                    event.pointerCount == 1
                ) {

                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    imageMatrix.postTranslate(dx, dy)

                    clampImage()
                    invalidate()
                }

                lastX = event.x
                lastY = event.y

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                performClick()
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun getCroppedBitmap(): Bitmap? {

        if (!initialized || cropRect.width() <= 0f) {
            return null
        }

        val bitmap = sourceBitmap ?: return null

        val size = cropRect.width().toInt()

        if (size <= 0) return null

        val result = Bitmap.createBitmap(
            size,
            size,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)

        canvas.drawColor(Color.BLACK)

        canvas.save()

        canvas.translate(
            -cropRect.left,
            -cropRect.top
        )

        canvas.drawBitmap(
            bitmap,
            imageMatrix,
            imagePaint
        )

        canvas.restore()

        return result
    }
}
