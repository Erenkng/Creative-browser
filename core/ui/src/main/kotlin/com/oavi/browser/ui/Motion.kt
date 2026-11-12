package com.oavi.browser.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * How things move. Everything that changes place, size or shape rides a spring, so motion
 * can be interrupted mid-way and always settles; only colour and opacity use timed fades.
 */
object OaviMotion {
    /** Position, size and shape: quick, with a soft landing. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 420f)

    /** Small acknowledgements under the finger: a visible, friendly bounce. */
    fun <T> lively(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.58f, stiffness = 560f)

    /** Large surfaces such as sheets and full screens: no overshoot. */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 260f)

    /** Colour and opacity. */
    fun <T> fade(): FiniteAnimationSpec<T> = tween(durationMillis = 160)
}

/** Touch feedback, named for what happened rather than how it feels. */
class Haptics internal constructor(private val view: View) {
    /** A light tick: a value changed, a threshold was crossed mid-gesture. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** A gesture went far enough that releasing now will act. */
    fun threshold() = perform(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)

    /** Something the user asked for was done. */
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)

    /** Something could not be done. */
    fun reject() = perform(HapticFeedbackConstants.REJECT)

    /** A long press took hold. */
    fun hold() = perform(HapticFeedbackConstants.LONG_PRESS)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
