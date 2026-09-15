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
import android.graphics.drawable.BitmapDrawable
import org.dolphinemu.dolphinemu.NativeLibrary
import kotlin.math.min

class InputOverlayDrawableJoystick
    (
    bitmapBounds: BitmapDrawable, bitmapOuter: BitmapDrawable,
    innerDefault: BitmapDrawable, innerPressed: BitmapDrawable,
    rectOuter: Rect, rectInner: Rect, joystick: Int
) {
    private val axisIDs: IntArray = intArrayOf(0, 0, 0, 0)
    private val axises: FloatArray = floatArrayOf(0f, 0f)
    private val factors: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)

    var pointerId = -1
        private set
    var buttonId = 0
        private set
    private var controlPositionX = 0
    private var controlPositionY = 0
    private var previousTouchX = 0
    private var previousTouchY = 0
    private var alpha = 0
    private val virtBounds: Rect
    private val origBounds: Rect
    private val outerBitmap: BitmapDrawable
    private val defaultInnerBitmap: BitmapDrawable
    private val pressedInnerBitmap: BitmapDrawable
    private val boundsBoxBitmap: BitmapDrawable

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var bounds: Rect
        get() = outerBitmap.bounds
        set(bounds) {
            outerBitmap.bounds = bounds
        }

    init {
        setAxisIDs(joystick)

        outerBitmap = bitmapOuter
        defaultInnerBitmap = innerDefault
        pressedInnerBitmap = innerPressed
        boundsBoxBitmap = bitmapBounds

        bounds = rectOuter
        defaultInnerBitmap.bounds = rectInner
        pressedInnerBitmap.bounds = rectInner
        virtBounds = outerBitmap.copyBounds()
        origBounds = outerBitmap.copyBounds()
        boundsBoxBitmap.alpha = 0
        boundsBoxBitmap.bounds = virtBounds
        updateInnerBounds()
    }

    fun onDraw(canvas: Canvas?) {
        if (canvas == null) return
        val pad = if (pointerId != -1 && boundsBoxBitmap.alpha > 0) boundsBoxBitmap.bounds else outerBitmap.bounds
        if (pad.width() <= 0 || pad.height() <= 0) return

        val a = alpha.coerceIn(0, 255)
        val size = min(pad.width(), pad.height()).toFloat()
        val cx = pad.exactCenterX()
        val cy = pad.exactCenterY()
        val outerRadius = size * 0.46f

        basePaint.color = Color.rgb(18, 21, 26)
        basePaint.alpha = (a * 0.60f).toInt()
        guidePaint.color = Color.WHITE
        guidePaint.alpha = (a * 0.48f).toInt()
        guidePaint.strokeWidth = (size * 0.022f).coerceAtLeast(1.5f)
        canvas.drawCircle(cx, cy, outerRadius, basePaint)
        canvas.drawCircle(cx, cy, outerRadius, guidePaint)
        guidePaint.alpha = (a * 0.20f).toInt()
        canvas.drawCircle(cx, cy, outerRadius * 0.69f, guidePaint)
        canvas.drawCircle(cx, cy, outerRadius * 0.36f, guidePaint)

        val inner = currentBitmapDrawable.bounds
        val knobCx = inner.exactCenterX()
        val knobCy = inner.exactCenterY()
        val knobRadius = (min(inner.width(), inner.height()).toFloat() * 0.46f)
            .coerceAtMost(outerRadius * 0.46f)
            .coerceAtLeast(size * 0.13f)

        knobPaint.color = if (pointerId != -1) Color.rgb(62, 108, 145) else Color.rgb(76, 80, 89)
        knobPaint.alpha = (a * if (pointerId != -1) 0.94f else 0.82f).toInt()
        knobStrokePaint.color = if (pointerId != -1) Color.rgb(187, 228, 255) else Color.WHITE
        knobStrokePaint.alpha = (a * if (pointerId != -1) 0.95f else 0.62f).toInt()
        knobStrokePaint.strokeWidth = (size * 0.025f).coerceAtLeast(1.5f)
        canvas.drawCircle(knobCx, knobCy, knobRadius, knobPaint)
        canvas.drawCircle(knobCx, knobCy, knobRadius, knobStrokePaint)

        highlightPaint.color = Color.WHITE
        highlightPaint.alpha = (a * 0.18f).toInt()
        canvas.drawCircle(
            knobCx - knobRadius * 0.22f,
            knobCy - knobRadius * 0.24f,
            knobRadius * 0.32f,
            highlightPaint
        )
    }

    fun onPointerDown(id: Int, x: Float, y: Float) {
        val reCenter = InputOverlay.sJoystickRelative
        outerBitmap.alpha = 0
        boundsBoxBitmap.alpha = alpha
        if (reCenter) {
            virtBounds.offset(x.toInt() - virtBounds.centerX(), y.toInt() - virtBounds.centerY())
        }
        boundsBoxBitmap.bounds = virtBounds
        pointerId = id
        setJoystickState(x, y)
    }

    fun onPointerMove(id: Int, x: Float, y: Float) {
        setJoystickState(x, y)
    }

    fun onPointerUp(id: Int, x: Float, y: Float) {
        outerBitmap.alpha = alpha
        boundsBoxBitmap.alpha = 0
        virtBounds.set(origBounds)
        bounds = origBounds
        pointerId = -1
        setJoystickState(x, y)
    }

    private fun setAxisIDs(joystick: Int) {
        if (joystick != 0) {
            buttonId = joystick
            factors[0] = 1f
            factors[1] = 1f
            factors[2] = 1f
            factors[3] = 1f
            axisIDs[0] = joystick + 1
            axisIDs[1] = joystick + 2
            axisIDs[2] = joystick + 3
            axisIDs[3] = joystick + 4
            return
        }

        when (InputOverlay.sJoyStickSetting) {
            InputOverlay.JOYSTICK_EMULATE_IR -> {
                buttonId = 0
                factors[0] = 0.8f
                factors[1] = 0.8f
                factors[2] = 0.4f
                factors[3] = 0.4f
                axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1
                axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2
                axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3
                axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4
            }

            InputOverlay.JOYSTICK_EMULATE_WII_SWING -> {
                buttonId = 0
                factors[0] = -0.8f
                factors[1] = -0.8f
                factors[2] = -0.8f
                factors[3] = -0.8f
                axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SWING + 1
                axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SWING + 2
                axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SWING + 3
                axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SWING + 4
            }

            InputOverlay.JOYSTICK_EMULATE_WII_TILT -> {
                buttonId = 0
                if (InputOverlay.sControllerType == InputOverlay.CONTROLLER_WIINUNCHUK) {
                    factors[0] = 0.8f
                    factors[1] = 0.8f
                    factors[2] = 0.8f
                    factors[3] = 0.8f
                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4
                } else {
                    factors[0] = -0.8f
                    factors[1] = -0.8f
                    factors[2] = 0.8f
                    factors[3] = 0.8f
                    axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4
                    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3
                    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1
                    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2
                }
            }

            InputOverlay.JOYSTICK_EMULATE_WII_SHAKE -> {
                buttonId = 0
                axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X
                axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X
                axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y
                axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SWING -> {
                buttonId = 0
                factors[0] = -0.8f
                factors[1] = -0.8f
                factors[2] = -0.8f
                factors[3] = -0.8f
                axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SWING + 1
                axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SWING + 2
                axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SWING + 3
                axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SWING + 4
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_TILT -> {
                buttonId = 0
                factors[0] = 0.8f
                factors[1] = 0.8f
                factors[2] = 0.8f
                factors[3] = 0.8f
                axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_TILT + 1
                axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_TILT + 2
                axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_TILT + 3
                axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_TILT + 4
            }

            InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SHAKE -> {
                buttonId = 0
                axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X
                axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X
                axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Y
                axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Z
            }
        }
    }

    private fun setJoystickState(touchXInput: Float, touchYInput: Float) {
        var touchX = touchXInput
        var touchY = touchYInput
        if (pointerId != -1) {
            var maxY = virtBounds.bottom.toFloat()
            var maxX = virtBounds.right.toFloat()
            touchX -= virtBounds.centerX().toFloat()
            maxX -= virtBounds.centerX().toFloat()
            touchY -= virtBounds.centerY().toFloat()
            maxY -= virtBounds.centerY().toFloat()
            val axisX = touchX / maxX
            val axisY = touchY / maxY
            axises[0] = axisY
            axises[1] = axisX
        } else {
            axises[1] = 0.0f
            axises[0] = axises[1]
        }

        updateInnerBounds()
        val values = axisValues

        if (buttonId != 0) {
            values[1] = min(values[1].toDouble(), 1.0).toFloat()
            values[0] = min(values[0].toDouble(), 0.0).toFloat()
            values[3] = min(values[3].toDouble(), 1.0).toFloat()
            values[2] = min(values[2].toDouble(), 0.0).toFloat()
        } else if (InputOverlay.sJoyStickSetting == InputOverlay.JOYSTICK_EMULATE_WII_SHAKE ||
            InputOverlay.sJoyStickSetting == InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SHAKE
        ) {
            values[0] = -values[1]
            values[1] = -values[1]
            values[3] = -values[3]
            handleShakeEvent(values)
            return
        }

        for (i in 0..3) {
            NativeLibrary.onGamePadMoveEvent(
                NativeLibrary.TouchScreenDevice, axisIDs[i], factors[i] * values[i]
            )
        }
    }

    private fun handleShakeEvent(values: FloatArray) {
        for (i in values.indices) {
            if (values[i] > 0.15f) {
                if (InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.PRESSED) {
                    InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.PRESSED
                    NativeLibrary.onGamePadEvent(
                        NativeLibrary.TouchScreenDevice, axisIDs[i],
                        NativeLibrary.ButtonState.PRESSED
                    )
                }
            } else if (InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.RELEASED) {
                InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.RELEASED
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TouchScreenDevice, axisIDs[i],
                    NativeLibrary.ButtonState.RELEASED
                )
            }
        }
    }

    fun onConfigureBegin(x: Int, y: Int) {
        previousTouchX = x
        previousTouchY = y
    }

    fun onConfigureMove(x: Int, y: Int) {
        val deltaX = x - previousTouchX
        val deltaY = y - previousTouchY
        val bounds: Rect = bounds
        controlPositionX += deltaX
        controlPositionY += deltaY
        this.bounds = Rect(
            controlPositionX, controlPositionY,
            controlPositionX + bounds.width(),
            controlPositionY + bounds.height()
        )
        virtBounds.set(
            controlPositionX, controlPositionY,
            controlPositionX + virtBounds.width(),
            controlPositionY + virtBounds.height()
        )
        updateInnerBounds()
        origBounds.set(
            controlPositionX, controlPositionY,
            controlPositionX + origBounds.width(),
            controlPositionY + origBounds.height()
        )
        previousTouchX = x
        previousTouchY = y
    }

    private val axisValues: FloatArray
        get() = floatArrayOf(axises[0], axises[0], axises[1], axises[1])

    private fun updateInnerBounds() {
        var x = virtBounds.centerX() + (axises[1] * (virtBounds.width() / 2)).toInt()
        var y = virtBounds.centerY() + (axises[0] * (virtBounds.height() / 2)).toInt()

        if (x > virtBounds.centerX() + (virtBounds.width() / 2)) x = virtBounds.centerX() + (virtBounds.width() / 2)
        if (x < virtBounds.centerX() - (virtBounds.width() / 2)) x = virtBounds.centerX() - (virtBounds.width() / 2)
        if (y > virtBounds.centerY() + (virtBounds.height() / 2)) y = virtBounds.centerY() + (virtBounds.height() / 2)
        if (y < virtBounds.centerY() - (virtBounds.height() / 2)) y = virtBounds.centerY() - (virtBounds.height() / 2)

        val width = pressedInnerBitmap.bounds.width() / 2
        val height = pressedInnerBitmap.bounds.height() / 2
        defaultInnerBitmap.setBounds(x - width, y - height, x + width, y + height)
        pressedInnerBitmap.bounds = defaultInnerBitmap.bounds
    }

    fun setPosition(x: Int, y: Int) {
        controlPositionX = x
        controlPositionY = y
    }

    private val currentBitmapDrawable: BitmapDrawable
        get() = if (pointerId != -1) pressedInnerBitmap else defaultInnerBitmap

    fun setAlpha(value: Int) {
        alpha = value
        defaultInnerBitmap.alpha = value
        pressedInnerBitmap.alpha = value

        if (pointerId == -1) {
            outerBitmap.alpha = value
            boundsBoxBitmap.alpha = 0
        } else {
            outerBitmap.alpha = 0
            boundsBoxBitmap.alpha = value
        }
    }
}
