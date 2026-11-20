package com.glasschat.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.glasschat.ui.theme.Appearance
import kotlin.math.abs

/**
 * A light tick and a click sound on every tap anywhere in the app
 * (Chat settings → Taps). Watches the raw touches, so it works for every
 * button, row and menu without each one opting in. Scrolls, drags and long
 * presses don't count as taps.
 */
class TapFeedback(context: Context) {
    private val ctx = context.applicationContext
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var moved = false

    fun onTouch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                downAt = ev.eventTime
                moved = false
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop) moved = true
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> moved = true
            MotionEvent.ACTION_UP ->
                if (!moved && ev.eventTime - downAt < ViewConfiguration.getLongPressTimeout()) tap()
        }
    }

    private fun tap() {
        val s = Appearance.style.value
        if (s.tapHaptic) runCatching {
            vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
        if (s.tapSound) runCatching {
            // Plays even when the phone's own touch sounds are off; follows the phone's volume.
            audio?.playSoundEffect(AudioManager.FX_KEY_CLICK, -1f)
        }
    }
}
