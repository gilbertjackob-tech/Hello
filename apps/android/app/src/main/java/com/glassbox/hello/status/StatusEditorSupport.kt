package com.glassbox.hello.status

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

enum class StatusImageLook(val label: String) {
    Natural("Natural"),
    Vivid("Vivid"),
    Warm("Warm"),
    Cool("Cool"),
    Mono("Mono"),
    Noir("Noir")
}

enum class StatusTextBoxStyle(val label: String) {
    Glass("Glass"),
    Solid("Solid"),
    Clean("Clean")
}

fun statusLookMatrix(look: StatusImageLook): FloatArray = when (look) {
    StatusImageLook.Natural -> floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    StatusImageLook.Vivid -> floatArrayOf(
        1.18f, -0.04f, -0.04f, 0f, 8f,
        -0.03f, 1.15f, -0.03f, 0f, 6f,
        -0.02f, -0.02f, 1.18f, 0f, 7f,
        0f, 0f, 0f, 1f, 0f
    )
    StatusImageLook.Warm -> floatArrayOf(
        1.12f, 0f, 0f, 0f, 10f,
        0f, 1.02f, 0f, 0f, 4f,
        0f, 0f, 0.92f, 0f, -4f,
        0f, 0f, 0f, 1f, 0f
    )
    StatusImageLook.Cool -> floatArrayOf(
        0.94f, 0f, 0.05f, 0f, -2f,
        0f, 1.02f, 0.02f, 0f, 0f,
        0.02f, 0f, 1.12f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    )
    StatusImageLook.Mono -> floatArrayOf(
        0.33f, 0.59f, 0.11f, 0f, 0f,
        0.33f, 0.59f, 0.11f, 0f, 0f,
        0.33f, 0.59f, 0.11f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    StatusImageLook.Noir -> floatArrayOf(
        0.45f, 0.68f, 0.14f, 0f, -12f,
        0.45f, 0.68f, 0.14f, 0f, -12f,
        0.45f, 0.68f, 0.14f, 0f, -12f,
        0f, 0f, 0f, 1f, 0f
    )
}

fun renderEditedStatusImage(
    sourceBytes: ByteArray,
    caption: String,
    look: StatusImageLook,
    textColor: Int,
    font: StatusFont,
    boxStyle: StatusTextBoxStyle,
    offsetX: Float,
    offsetY: Float
): ByteArray {
    val source = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
        ?: throw IllegalArgumentException("Could not decode status image")
    val output = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(statusLookMatrix(look)))
        isFilterBitmap = true
    }

    val srcRect = centerCropRect(source.width, source.height, output.width, output.height)
    val dstRect = Rect(0, 0, output.width, output.height)
    canvas.drawBitmap(source, srcRect, dstRect, paint)

    if (caption.isNotBlank()) {
        drawCaptionOverlay(
            canvas = canvas,
            width = output.width,
            height = output.height,
            caption = caption,
            textColor = textColor,
            font = font,
            boxStyle = boxStyle,
            offsetX = offsetX,
            offsetY = offsetY
        )
    }

    return ByteArrayOutputStream().use { stream ->
        output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        stream.toByteArray()
    }
}

private fun centerCropRect(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Rect {
    val srcRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
    val dstRatio = targetWidth.toFloat() / targetHeight.toFloat()
    return if (srcRatio > dstRatio) {
        val croppedWidth = (sourceHeight * dstRatio).roundToInt()
        val left = ((sourceWidth - croppedWidth) / 2f).roundToInt()
        Rect(left, 0, left + croppedWidth, sourceHeight)
    } else {
        val croppedHeight = (sourceWidth / dstRatio).roundToInt()
        val top = ((sourceHeight - croppedHeight) / 2f).roundToInt()
        Rect(0, top, sourceWidth, top + croppedHeight)
    }
}

private fun drawCaptionOverlay(
    canvas: Canvas,
    width: Int,
    height: Int,
    caption: String,
    textColor: Int,
    font: StatusFont,
    boxStyle: StatusTextBoxStyle,
    offsetX: Float,
    offsetY: Float
) {
    val textWidth = (width * 0.76f).roundToInt()
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 82f
        textAlign = Paint.Align.CENTER
        typeface = when (font) {
            StatusFont.Normal -> Typeface.SANS_SERIF
            StatusFont.Bold -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            StatusFont.Serif -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            StatusFont.Monospace -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    val layout = StaticLayout.Builder
        .obtain(caption, 0, caption.length, textPaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .build()

    val anchorX = width / 2f + offsetX.coerceIn(-0.38f, 0.38f) * width * 0.42f
    val anchorY = height / 2f + offsetY.coerceIn(-0.42f, 0.42f) * height * 0.42f
    val left = (anchorX - textWidth / 2f).coerceIn(48f, width - textWidth - 48f)
    val top = (anchorY - layout.height / 2f).coerceIn(72f, height - layout.height - 120f)

    if (boxStyle != StatusTextBoxStyle.Clean) {
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (boxStyle) {
                StatusTextBoxStyle.Glass -> Color.argb(132, 10, 18, 28)
                StatusTextBoxStyle.Solid -> Color.argb(196, 8, 12, 18)
                StatusTextBoxStyle.Clean -> Color.TRANSPARENT
            }
        }
        val boxRect = RectF(left - 28f, top - 24f, left + textWidth + 28f, top + layout.height + 24f)
        canvas.drawRoundRect(boxRect, 32f, 32f, boxPaint)
    }

    canvas.save()
    canvas.translate(left, top)
    layout.draw(canvas)
    canvas.restore()
}
