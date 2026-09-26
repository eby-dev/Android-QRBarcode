package com.ahmadabuhasan.qrbarcode.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

// Dims the camera preview around a square frame so users know where to aim.
// Purely visual: ML Kit scans the whole image, not just the frame.
class ViewfinderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val cornerLength = 28 * density

    private val maskPaint = Paint().apply { color = Color.argb(0x80, 0, 0, 0) }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
    }

    private val frame = RectF()
    private val mask = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val corners = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(w, h) * 0.7f
        val left = (w - size) / 2
        // Sit slightly above centre, leaving room for the flash button below.
        val top = (h - size) / 2 - h * 0.05f
        frame.set(left, top, left + size, top + size)

        mask.reset()
        mask.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        mask.addRect(frame, Path.Direction.CW)

        corners.reset()
        with(frame) {
            corners.moveTo(left, top + cornerLength); corners.lineTo(left, top); corners.lineTo(left + cornerLength, top)
            corners.moveTo(right - cornerLength, top); corners.lineTo(right, top); corners.lineTo(right, top + cornerLength)
            corners.moveTo(right, bottom - cornerLength); corners.lineTo(right, bottom); corners.lineTo(right - cornerLength, bottom)
            corners.moveTo(left + cornerLength, bottom); corners.lineTo(left, bottom); corners.lineTo(left, bottom - cornerLength)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(mask, maskPaint)
        canvas.drawPath(corners, cornerPaint)
    }
}
