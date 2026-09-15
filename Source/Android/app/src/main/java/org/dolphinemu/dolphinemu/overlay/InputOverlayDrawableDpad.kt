/**
 * Copyright 2016 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */
package org.dolphinemu.dolphinemu.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import org.dolphinemu.dolphinemu.NativeLibrary
import kotlin.math.min

class InputOverlayDrawableDpad
    (
    private val defaultStateBitmap: BitmapDrawable,
    private val pressedOneDirectionStateBitmap: BitmapDrawable,
    private val pressedTwoDirectionsStateBitmap: BitmapDrawable,
    buttonUp: Int,
    buttonDown: Int,
    buttonLeft: Int,
    buttonRight: Int
) {
    private val buttonIds = IntArray(4)
    private val pressStates = BooleanArray(4)
    var pointerId: Int
        private set
    private var previousTouchX = 0
    private var previousTouchY = 0
    private var controlPositionX = 0
    private var controlPositionY = 0

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        pointerId = -1
        buttonIds[0] = buttonUp
        buttonIds[1] = buttonDown
        buttonIds[2] = buttonLeft
        buttonIds[3] = buttonRight
    }

    fun onDraw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val alpha = defaultStateBitmap.alpha.coerceIn(0, 255)
        val size = min(b.width(), b.height()).toFloat()
        val arm = size * 0.34f
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val half = size * 0.48f
        val corner = arm * 0.24f

        basePaint.color = Color.rgb(22, 25, 30)
        basePaint.alpha = (alpha * 0.66f).toInt()
        pressedPaint.color = Color.rgb(66, 112, 148)
        pressedPaint.alpha = (alpha * 0.90f).toInt()
        strokePaint.color = Color.WHITE
        strokePaint.alpha = (alpha * 0.58f).toInt()
        strokePaint.strokeWidth = (size * 0.022f).coerceAtLeast(1.5f)

        val vertical = RectF(cx - arm / 2f, cy - half, cx + arm / 2f, cy + half)
        val horizontal = RectF(cx - half, cy - arm / 2f, cx + half, cy + arm / 2f)
        canvas.drawRoundRect(vertical, corner, corner, basePaint)
        canvas.drawRoundRect(horizontal, corner, corner, basePaint)
        canvas.drawRoundRect(vertical, corner, corner, strokePaint)
        canvas.drawRoundRect(horizontal, corner, corner, strokePaint)

        if (pressStates[0]) canvas.drawRoundRect(RectF(cx - arm / 2f, cy - half, cx + arm / 2f, cy), corner, corner, pressedPaint)
        if (pressStates[1]) canvas.drawRoundRect(RectF(cx - arm / 2f, cy, cx + arm / 2f, cy + half), corner, corner, pressedPaint)
        if (pressStates[2]) canvas.drawRoundRect(RectF(cx - half, cy - arm / 2f, cx, cy + arm / 2f), corner, corner, pressedPaint)
        if (pressStates[3]) canvas.drawRoundRect(RectF(cx, cy - arm / 2f, cx + half, cy + arm / 2f), corner, corner, pressedPaint)

        arrowPaint.color = Color.WHITE
        arrowPaint.alpha = (alpha * 0.86f).toInt()
        arrowPaint.textSize = size * 0.15f
        val fm = arrowPaint.fontMetrics
        val baselineAdjust = -(fm.ascent + fm.descent) / 2f
        canvas.drawText("▲", cx, cy - size * 0.29f + baselineAdjust, arrowPaint)
        canvas.drawText("▼", cx, cy + size * 0.29f + baselineAdjust, arrowPaint)
        canvas.drawText("◀", cx - size * 0.29f, cy + baselineAdjust, arrowPaint)
        canvas.drawText("▶", cx + size * 0.29f, cy + baselineAdjust, arrowPaint)
    }

    fun getButtonId(direction: Int): Int = buttonIds[direction]

    fun onConfigureBegin(x: Int, y: Int) {
        previousTouchX = x
        previousTouchY = y
    }

    fun onConfigureMove(x: Int, y: Int) {
        val bounds = bounds
        controlPositionX += x - previousTouchX
        controlPositionY += y - previousTouchY
        this.bounds = Rect(
            controlPositionX, controlPositionY,
            controlPositionX + bounds.width(), controlPositionY + bounds.height()
        )
        previousTouchX = x
        previousTouchY = y
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        pointerId = id
        setDpadState(x.toInt(), y.toInt())
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
        setDpadState(x.toInt(), y.toInt())
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        pointerId = -1
        setDpadState(x.toInt(), y.toInt())
    }

    fun setPosition(x: Int, y: Int) {
        controlPositionX = x
        controlPositionY = y
    }

    var bounds: Rect
        get() = defaultStateBitmap.bounds
        set(bounds) {
            defaultStateBitmap.bounds = bounds
            pressedOneDirectionStateBitmap.bounds = bounds
            pressedTwoDirectionsStateBitmap.bounds = bounds
        }

    fun setAlpha(value: Int) {
        defaultStateBitmap.alpha = value
        pressedOneDirectionStateBitmap.alpha = value
        pressedTwoDirectionsStateBitmap.alpha = value
    }

    private fun setDpadState(pointerX: Int, pointerY: Int) {
        val pressed = booleanArrayOf(false, false, false, false)
        if (pointerId != -1) {
            val bounds = bounds
            if (bounds.top + (bounds.height() / 3) > pointerY) pressed[0] = true
            else if (bounds.bottom - (bounds.height() / 3) < pointerY) pressed[1] = true
            if (bounds.left + (bounds.width() / 3) > pointerX) pressed[2] = true
            else if (bounds.right - (bounds.width() / 3) < pointerX) pressed[3] = true
        }

        for (i in pressed.indices) {
            if (pressed[i] != pressStates[i]) {
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TouchScreenDevice,
                    buttonIds[i],
                    if (pressed[i]) NativeLibrary.ButtonState.PRESSED else NativeLibrary.ButtonState.RELEASED
                )
            }
        }

        for (i in pressStates.indices) pressStates[i] = pressed[i]
    }
}
