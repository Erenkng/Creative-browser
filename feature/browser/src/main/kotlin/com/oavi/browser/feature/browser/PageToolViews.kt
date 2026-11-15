package com.oavi.browser.feature.browser

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.oavi.browser.ui.ButtonKind
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviButton
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.OaviSheet
import com.oavi.browser.ui.R
import com.oavi.browser.ui.glass

/**
 * The small player shown while a page is read aloud: what is being read, how far along,
 * pause or continue, and stop.
 */
@Composable
fun ReadAloudBar(
    visible: Boolean,
    preparing: Boolean,
    paused: Boolean,
    title: String,
    progress: Float,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Oavi.colors
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(OaviMotion.spatial()) { it } + fadeIn(OaviMotion.fade()),
        exit = slideOutVertically(OaviMotion.gentle()) { it } + fadeOut(OaviMotion.fade()),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(26.dp), elevation = 16.dp)
                .drawBehind {
                    drawRect(colors.accentSoft, size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
                }
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_speak), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (preparing) R.string.read_aloud_preparing else R.string.read_aloud_now),
                    style = Oavi.type.caption,
                    color = colors.muted,
                )
                if (title.isNotBlank()) {
                    Text(title, style = Oavi.type.label, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!preparing) {
                OaviIconButton(
                    icon = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                    contentDescription = stringResource(if (paused) R.string.cd_play else R.string.cd_pause),
                    onClick = onPauseOrResume,
                    modifier = Modifier.size(44.dp),
                )
            }
            OaviIconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.cd_stop_reading),
                onClick = onStop,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

/** A sheet with the page's address as a QR code, for opening it on another device. */
@Composable
fun QrSheet(
    url: String?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Oavi.colors
    val code = remember(url) { url?.let { qrBitmap(it) } }
    OaviSheet(visible = url != null, onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.qr_title), style = Oavi.type.title, color = colors.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(236.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (code != null) {
                    Image(
                        bitmap = code.asImageBitmap(),
                        contentDescription = stringResource(R.string.menu_qr),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(208.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = url.orEmpty(),
                style = Oavi.type.caption,
                color = colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OaviButton(stringResource(R.string.search_copy_link), onClick = { url?.let(onCopy) }, kind = ButtonKind.TONAL, icon = R.drawable.ic_copy)
                OaviButton(stringResource(R.string.search_share), onClick = { url?.let(onShare) }, icon = R.drawable.ic_share)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Draws [text] as a QR code, one pixel per module; scaled up without smoothing when shown. */
private fun qrBitmap(text: String): Bitmap? = try {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
    val size = matrix.width
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF111014.toInt() else 0xFFFFFFFF.toInt() }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
} catch (_: WriterException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
