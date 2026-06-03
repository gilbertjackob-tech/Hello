package com.glassbox.hello.status.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object StoryExportRenderer {
    private const val OUT_W = 1080
    private const val OUT_H = 1920

    fun render(context: Context, draft: StoryDraft): ByteArray {
        val bytes = draft.sourceBytes ?: draft.sourceUri?.let { readUri(context, it) }
            ?: throw IllegalArgumentException("No story media selected")
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Could not decode story media")
        val output = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawBackground(canvas, draft.selectedEffect)
        drawSource(canvas, source, draft.selectedEffect)
        val faces = estimateFaces()
        drawEffectOverlay(canvas, draft.selectedEffect, faces)
        drawSticker(canvas, draft.sticker)
        drawCaption(canvas, draft)
        return ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, 94, stream)
            stream.toByteArray()
        }
    }

    private fun readUri(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not read selected story media")
    }

    private fun drawBackground(canvas: Canvas, effect: StoryEffect) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (effect.backgroundAware) {
            val colors = when (effect.id) {
                "sunset" -> intArrayOf(Color.rgb(255, 138, 61), Color.rgb(92, 38, 128))
                "mint_bg" -> intArrayOf(Color.rgb(10, 148, 136), Color.rgb(205, 237, 242))
                else -> intArrayOf(Color.rgb(8, 12, 18), Color.rgb(0, 212, 170))
            }
            paint.shader = LinearGradient(0f, 0f, 0f, OUT_H.toFloat(), colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, OUT_W.toFloat(), OUT_H.toFloat(), paint)
        } else {
            canvas.drawColor(Color.BLACK)
        }
    }

    private fun drawSource(canvas: Canvas, source: Bitmap, effect: StoryEffect) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(ColorMatrix(colorMatrixFor(effect.id)))
            alpha = if (effect.backgroundAware) 218 else 255
        }
        val src = centerCropRect(source.width, source.height, OUT_W, OUT_H)
        canvas.drawBitmap(source, src, Rect(0, 0, OUT_W, OUT_H), paint)
    }

    private fun drawEffectOverlay(canvas: Canvas, effect: StoryEffect, faces: List<DetectedFace>) {
        if (!effect.faceAware) return
        val targets = faces.ifEmpty {
            listOf(DetectedFace(OUT_W / 2f, OUT_H * 0.42f, OUT_W * 0.34f, OUT_H * 0.24f))
        }
        targets.forEach { face ->
            when (effect.id) {
                "face_glow" -> drawFaceGlow(canvas, face)
                "neon_mask" -> drawNeonMask(canvas, face)
                "game_pop" -> drawGamePop(canvas, face)
            }
        }
    }

    private fun drawFaceGlow(canvas: Canvas, face: DetectedFace) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            color = Color.argb(210, 255, 241, 118)
        }
        canvas.drawOval(face.rect.insetBy(-34f), paint)
        paint.strokeWidth = 7f
        paint.color = Color.argb(220, 255, 255, 255)
        canvas.drawCircle(face.cx - face.w * 0.18f, face.cy - face.h * 0.05f, face.w * 0.05f, paint)
        canvas.drawCircle(face.cx + face.w * 0.18f, face.cy - face.h * 0.05f, face.w * 0.05f, paint)
    }

    private fun drawNeonMask(canvas: Canvas, face: DetectedFace) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = Color.argb(230, 155, 124, 255)
        }
        canvas.drawRoundRect(face.rect.insetBy(-18f), 80f, 80f, paint)
        paint.color = Color.argb(230, 0, 212, 170)
        canvas.drawLine(face.cx - face.w * 0.36f, face.cy, face.cx + face.w * 0.36f, face.cy, paint)
    }

    private fun drawGamePop(canvas: Canvas, face: DetectedFace) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(220, 56, 189, 248)
        }
        canvas.drawCircle(face.cx - face.w * 0.32f, face.cy - face.h * 0.5f, 42f, paint)
        paint.color = Color.argb(220, 255, 65, 108)
        canvas.drawCircle(face.cx + face.w * 0.32f, face.cy - face.h * 0.5f, 42f, paint)
        paint.color = Color.WHITE
        paint.textSize = 58f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("HELLO", face.cx, face.cy - face.h * 0.58f, paint)
    }

    private fun drawSticker(canvas: Canvas, sticker: String?) {
        if (sticker.isNullOrBlank()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 132f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(sticker, OUT_W * 0.78f, OUT_H * 0.22f, paint)
    }

    private fun drawCaption(canvas: Canvas, draft: StoryDraft) {
        val caption = draft.caption.trim()
        if (caption.isBlank()) return
        val textWidth = (OUT_W * 0.78f).roundToInt()
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = draft.textColor
            textSize = 76f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
        val layout = StaticLayout.Builder
            .obtain(caption, 0, caption.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val anchorX = OUT_W / 2f + draft.textOffsetX.coerceIn(-0.38f, 0.38f) * OUT_W * 0.42f
        val anchorY = OUT_H / 2f + draft.textOffsetY.coerceIn(-0.42f, 0.42f) * OUT_H * 0.42f
        val left = (anchorX - textWidth / 2f).coerceIn(48f, OUT_W - textWidth - 48f)
        val top = (anchorY - layout.height / 2f).coerceIn(72f, OUT_H - layout.height - 120f)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(132, 10, 18, 28) }
        canvas.drawRoundRect(RectF(left - 28f, top - 24f, left + textWidth + 28f, top + layout.height + 24f), 32f, 32f, boxPaint)
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun estimateFaces(): List<DetectedFace> {
        return listOf(DetectedFace(OUT_W / 2f, OUT_H * 0.42f, OUT_W * 0.34f, OUT_H * 0.24f))
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

    private fun colorMatrixFor(id: String): FloatArray = when (id) {
        "vivid", "aesthetic", "game_pop" -> floatArrayOf(
            1.18f, -0.04f, -0.04f, 0f, 8f,
            -0.03f, 1.15f, -0.03f, 0f, 6f,
            -0.02f, -0.02f, 1.18f, 0f, 7f,
            0f, 0f, 0f, 1f, 0f
        )
        "film", "sunset" -> floatArrayOf(
            1.12f, 0f, 0f, 0f, 10f,
            0f, 1.02f, 0f, 0f, 4f,
            0f, 0f, 0.92f, 0f, -4f,
            0f, 0f, 0f, 1f, 0f
        )
        "noir", "neon_mask" -> floatArrayOf(
            0.45f, 0.68f, 0.14f, 0f, -12f,
            0.45f, 0.68f, 0.14f, 0f, -12f,
            0.45f, 0.68f, 0.14f, 0f, -12f,
            0f, 0f, 0f, 1f, 0f
        )
        else -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }
}

private data class DetectedFace(val cx: Float, val cy: Float, val w: Float, val h: Float) {
    val rect: RectF get() = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
}

private fun RectF.insetBy(value: Float): RectF = RectF(left - value, top - value, right + value, bottom + value)
