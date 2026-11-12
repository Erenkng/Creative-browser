package com.oavi.browser.feature.privacy

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R

/** Fingerprint, face or the phone's own PIN, pattern or password. */
object ScreenLock {
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** True when the phone has a screen lock that can be asked for. */
    fun isAvailable(context: Context): Boolean {
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Asks the person to prove it is them. [onResult] receives true on success. A phone
     * with no screen lock cannot ask, so it answers true rather than locking the user out.
     */
    fun authenticate(activity: Activity, title: String, onResult: (Boolean) -> Unit) {
        if (!isAvailable(activity)) {
            onResult(true)
            return
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
    }
}

/** Covers a private page until the phone is unlocked. Nothing of the page shows through. */
@Composable
fun PrivateLockCover(visible: Boolean, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Oavi.colors
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(OaviMotion.fade()),
        exit = fadeOut(OaviMotion.fade()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.lock_title), style = Oavi.type.display, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.lock_body), style = Oavi.type.body, color = colors.muted)
            Spacer(Modifier.height(22.dp))
            OaviButton(stringResource(R.string.lock_unlock), onClick = onUnlock, icon = R.drawable.ic_lock)
        }
    }
}
