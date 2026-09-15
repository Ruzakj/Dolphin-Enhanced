/**
 * Copyright 2013 Dolphin Emulator Project
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
import android.os.Handler
import org.dolphinemu.dolphinemu.NativeLibrary
import kotlin.math.min

class InputOverlayDrawableButton
    (
    private val defaultStateBitmap: BitmapDrawable,
    private val pressedStateBitmap: BitmapDrawable,
    val buttonId: Int
) {
    var pointerId: Int
        private set
    private var tiltStatus = 0
    private var previousTouchX = 0
    private var previousTouchY = 0
    private var controlPositionX = 0
    private var controlPositionY = 0
    private var handler: Handler? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val innerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        pointerId = -1
        if (buttonId == InputOverlay.sInputHackForRK4) {
            handler = Handler()
        }
    }

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

    fun onDraw(canvas: Canvas?) {
        if (canvas == null) return
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val pressed = pointerId != -1
        val alpha = defaultStateBitmap.alpha.coerceIn(0, 255)
        val minSide = min(b.width(), b.height()).toFloat()
        val isRound = b.width().toFloat() / b.height().toFloat() in 0.78f..1.28f

        fillPaint.color = if (pressed) Color.rgb(66, 112, 148) else Color.rgb(22, 25, 30)
        fillPaint.alpha = (alpha * if (pressed) 0.88f else 0.64f).toInt()
        strokePaint.color = if (pressed) Color.rgb(184, 227, 255) else Color.WHITE
        strokePaint.alpha = (alpha * if (pressed) 0.95f else 0.56f).toInt()
        strokePaint.strokeWidth = (minSide * 0.035f).coerceAtLeast(1.5f)
        innerStrokePaint.color = Color.WHITE
        innerStrokePaint.alpha = (alpha * 0.20f).toInt()
        innerStrokePaint.strokeWidth = (minSide * 0.015f).coerceAtLeast(1f)

        if (isRound) {
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val radius = minSide * 0.46f
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, strokePaint)
            canvas.drawCircle(cx, cy, radius * 0.76f, innerStrokePaint)
        } else {
            val insetX = b.width() * 0.04f
            val insetY = b.height() * 0.08f
            val r = RectF(b.left + insetX, b.top + insetY, b.right - insetX, b.bottom - insetY)
            val radius = minSide * 0.28f
            canvas.drawRoundRect(r, radius, radius, fillPaint)
            canvas.drawRoundRect(r, radius, radius, strokePaint)
        }

        val label = buttonLabel(buttonId)
        if (label.isNotEmpty()) {
            labelPaint.color = Color.WHITE
            labelPaint.alpha = (alpha * if (pressed) 1.0f else 0.90f).toInt()
            labelPaint.textSize = minSide * when {
                label.length >= 5 -> 0.24f
                label.length >= 3 -> 0.29f
                else -> 0.36f
            }
            val fm = labelPaint.fontMetrics
            val y = b.exactCenterY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, b.exactCenterX(), y, labelPaint)
        }
    }

    private fun buttonLabel(id: Int): String = when (id) {
        NativeLibrary.ButtonType.BUTTON_A, NativeLibrary.ButtonType.WIIMOTE_BUTTON_A,
        NativeLibrary.ButtonType.CLASSIC_BUTTON_A -> "A"
        NativeLibrary.ButtonType.BUTTON_B, NativeLibrary.ButtonType.WIIMOTE_BUTTON_B,
        NativeLibrary.ButtonType.CLASSIC_BUTTON_B -> "B"
        NativeLibrary.ButtonType.BUTTON_X, NativeLibrary.ButtonType.CLASSIC_BUTTON_X -> "X"
        NativeLibrary.ButtonType.BUTTON_Y, NativeLibrary.ButtonType.CLASSIC_BUTTON_Y -> "Y"
        NativeLibrary.ButtonType.BUTTON_Z -> "Z"
        NativeLibrary.ButtonType.BUTTON_START -> "START"
        NativeLibrary.ButtonType.TRIGGER_L, NativeLibrary.ButtonType.TRIGGER_L_ANALOG,
        NativeLibrary.ButtonType.CLASSIC_TRIGGER_L -> "L"
        NativeLibrary.ButtonType.TRIGGER_R, NativeLibrary.ButtonType.TRIGGER_R_ANALOG,
        NativeLibrary.ButtonType.CLASSIC_TRIGGER_R -> "R"
        NativeLibrary.ButtonType.WIIMOTE_BUTTON_1 -> "1"
        NativeLibrary.ButtonType.WIIMOTE_BUTTON_2 -> "2"
        NativeLibrary.ButtonType.WIIMOTE_BUTTON_MINUS, NativeLibrary.ButtonType.CLASSIC_BUTTON_MINUS,
        NativeLibrary.ButtonType.GUITAR_BUTTON_MINUS, NativeLibrary.ButtonType.DRUMS_BUTTON_MINUS,
        NativeLibrary.ButtonType.TURNTABLE_BUTTON_MINUS -> "−"
        NativeLibrary.ButtonType.WIIMOTE_BUTTON_PLUS, NativeLibrary.ButtonType.CLASSIC_BUTTON_PLUS,
        NativeLibrary.ButtonType.GUITAR_BUTTON_PLUS, NativeLibrary.ButtonType.DRUMS_BUTTON_PLUS,
        NativeLibrary.ButtonType.TURNTABLE_BUTTON_PLUS -> "+"
        NativeLibrary.ButtonType.WIIMOTE_BUTTON_HOME, NativeLibrary.ButtonType.CLASSIC_BUTTON_HOME,
        NativeLibrary.ButtonType.TURNTABLE_BUTTON_HOME -> "HOME"
        NativeLibrary.ButtonType.WIIMOTE_TILT_TOGGLE -> "TILT"
        NativeLibrary.ButtonType.HOTKEYS_UPRIGHT_TOGGLE -> "ROT"
        NativeLibrary.ButtonType.NUNCHUK_BUTTON_C -> "C"
        NativeLibrary.ButtonType.NUNCHUK_BUTTON_Z -> "Z"
        NativeLibrary.ButtonType.CLASSIC_BUTTON_ZL -> "ZL"
        NativeLibrary.ButtonType.CLASSIC_BUTTON_ZR -> "ZR"
        NativeLibrary.ButtonType.GUITAR_FRET_GREEN, NativeLibrary.ButtonType.DRUMS_PAD_GREEN,
        NativeLibrary.ButtonType.TURNTABLE_BUTTON_GREEN_LEFT, NativeLibrary.ButtonType.TURNTABLE_BUTTON_GREEN_RIGHT -> "G"
        NativeLibrary.ButtonType.GUITAR_FRET_RED, NativeLibrary.ButtonType.DRUMS_PAD_RED,
        NativeLibrary.ButtonType.TURNTABLE_BUTTON_RED_LEFT, NativeLibrary.ButtonType.TURNTABLE_BUTTON_RED_RIGHT -> "R"
        NativeLibrary.ButtonType.GUITAR_FRET_YELLOW, NativeLibrary.ButtonType.DRUMS_PAD_YELLOW -> "Y"
        NativeLibrary.ButtonType.GUITAR_FRET_BLUE, NativeLibrary.ButtonType.DRUMS_PAD_BLUE,
        NativeLibrary.ButtonType.TURNTABLE_BUTTON_BLUE_LEFT, NativeLibrary.ButtonType.TURNTABLE_BUTTON_BLUE_RIGHT -> "B"
        NativeLibrary.ButtonType.GUITAR_FRET_ORANGE, NativeLibrary.ButtonType.DRUMS_PAD_ORANGE -> "O"
        NativeLibrary.ButtonType.GUITAR_STRUM_UP -> "↑"
        NativeLibrary.ButtonType.GUITAR_STRUM_DOWN -> "↓"
        NativeLibrary.ButtonType.DRUMS_PAD_BASS -> "BASS"
        NativeLibrary.ButtonType.TURNTABLE_BUTTON_EUPHORIA -> "FX"
        else -> ""
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        pointerId = id
        if (buttonId == NativeLibrary.ButtonType.WIIMOTE_TILT_TOGGLE) {
            val valueList = floatArrayOf(0.5f, 1.0f, 0.0f)
            val value = valueList[tiltStatus]
            tiltStatus = (tiltStatus + 1) % valueList.size
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_TILT + 1,
                value
            )
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_TILT + 2,
                value
            )
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_TILT + 3,
                0f
            )
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice,
                NativeLibrary.ButtonType.WIIMOTE_TILT + 4,
                0f
            )
        } else {
            NativeLibrary.onGamePadEvent(
                NativeLibrary.TouchScreenDevice,
                buttonId, NativeLibrary.ButtonState.PRESSED
            )
        }
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        pointerId = -1
        if (buttonId != NativeLibrary.ButtonType.WIIMOTE_TILT_TOGGLE) {
            NativeLibrary.onGamePadEvent(
                NativeLibrary.TouchScreenDevice,
                buttonId, NativeLibrary.ButtonState.RELEASED
            )
            if (buttonId == InputOverlay.sInputHackForRK4) {
                handler!!.postDelayed({
                    NativeLibrary.onGamePadMoveEvent(
                        NativeLibrary.TouchScreenDevice,
                        NativeLibrary.ButtonType.WIIMOTE_SHAKE_X + 2,
                        1f
                    )
                    handler!!.postDelayed({
                        NativeLibrary.onGamePadMoveEvent(
                            NativeLibrary.TouchScreenDevice,
                            NativeLibrary.ButtonType.WIIMOTE_SHAKE_X + 2,
                            0f
                        )
                    }, 60)
                }, 120)
            }
        }
    }

    fun setPosition(x: Int, y: Int) {
        controlPositionX = x
        controlPositionY = y
    }

    private val currentStateBitmapDrawable: BitmapDrawable
        get() = if (pointerId != -1) pressedStateBitmap else defaultStateBitmap

    fun setAlpha(value: Int) {
        defaultStateBitmap.alpha = value
        pressedStateBitmap.alpha = value
    }

    var bounds: Rect
        get() = defaultStateBitmap.bounds
        set(bounds) {
            defaultStateBitmap.bounds = bounds
            pressedStateBitmap.bounds = bounds
        }
}
