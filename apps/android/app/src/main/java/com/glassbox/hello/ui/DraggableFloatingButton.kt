package com.glassbox.hello.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Draggable floating button that snaps to the nearest horizontal edge and persists position.
 */
class DraggableFloatingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val dragThreshold = ViewConfiguration.get(context).scaledTouchSlop
    private val snapInterpolator = DecelerateInterpolator()
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialViewX = 0f
    private var initialViewY = 0f
    private var dragging = false
    private var onSnapListener: ((Float, Float) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { restorePosition() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = false
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialViewX = x
                initialViewY = y
                animate().scaleX(DRAG_SCALE).scaleY(DRAG_SCALE).setDuration(SCALE_DURATION_MILLIS).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!dragging && (abs(dx) > dragThreshold || abs(dy) > dragThreshold)) {
                    dragging = true
                }
                if (dragging) {
                    x = (initialViewX + dx).coerceIn(0f, maxX())
                    y = (initialViewY + dy).coerceIn(0f, maxY())
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                animate().scaleX(1f).scaleY(1f).setDuration(SCALE_DURATION_MILLIS).start()
                return if (dragging) {
                    snapToNearestEdge()
                    true
                } else {
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                animate().scaleX(1f).scaleY(1f).setDuration(SCALE_DURATION_MILLIS).start()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Registers a listener invoked after snap animation finishes.
     */
    fun setOnSnapListener(listener: (Float, Float) -> Unit) {
        onSnapListener = listener
    }

    /**
     * Persists the current button position.
     */
    fun savePosition() {
        preferences.edit()
            .putFloat(KEY_X, x)
            .putFloat(KEY_Y, y)
            .apply()
    }

    private fun restorePosition() {
        val storedX = preferences.getFloat(KEY_X, Float.NaN)
        val storedY = preferences.getFloat(KEY_Y, Float.NaN)
        if (storedX.isNaN() || storedY.isNaN()) {
            x = maxX()
            y = maxY() * DEFAULT_VERTICAL_FRACTION
        } else {
            x = storedX.coerceIn(0f, maxX())
            y = storedY.coerceIn(0f, maxY())
        }
    }

    private fun snapToNearestEdge() {
        val targetX = if (x + width / 2f < parentWidth() / 2f) 0f else maxX()
        animate()
            .x(targetX)
            .setDuration(SNAP_DURATION_MILLIS)
            .setInterpolator(snapInterpolator)
            .withEndAction {
                dragging = false
                savePosition()
                onSnapListener?.invoke(x, y)
            }
            .start()
    }

    private fun parentWidth(): Float = ((parent as? android.view.View)?.width ?: resources.displayMetrics.widthPixels).toFloat()

    private fun parentHeight(): Float = ((parent as? android.view.View)?.height ?: resources.displayMetrics.heightPixels).toFloat()

    private fun maxX(): Float = (parentWidth() - width).coerceAtLeast(0f)

    private fun maxY(): Float = (parentHeight() - height).coerceAtLeast(0f)

    companion object {
        private const val PREFERENCES_NAME: String = "browser_fab_position"
        private const val KEY_X: String = "x"
        private const val KEY_Y: String = "y"
        private const val SNAP_DURATION_MILLIS: Long = 240L
        private const val SCALE_DURATION_MILLIS: Long = 120L
        private const val DRAG_SCALE: Float = 1.05f
        private const val DEFAULT_VERTICAL_FRACTION: Float = 0.78f
    }
}
