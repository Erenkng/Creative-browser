package com.oavi.browser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

/** Where site icons come from. Provided once at the top of the app. */
class SiteIcons(
    val revision: StateFlow<Int>,
    val load: suspend (host: String) -> Bitmap?,
)

val LocalSiteIcons = staticCompositionLocalOf<SiteIcons?> { null }

/**
 * A site's own icon on a light tile, or its first letter while no icon is known. The tile is
 * light in both themes because most icons are drawn for white backgrounds.
 */
@Composable
fun SiteIcon(host: String, modifier: Modifier = Modifier, size: Dp = 40.dp, label: String = host) {
    val icons = LocalSiteIcons.current
    val revision = icons?.revision?.collectAsState()?.value ?: 0
    val icon by produceState<ImageBitmap?>(null, host, revision) {
        value = if (icons == null || host.isBlank()) null else icons.load(host)?.asImageBitmap()
    }
    val image = icon
    if (image == null) {
        LetterTile(label.ifBlank { host }, modifier, size)
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(if (Oavi.colors.isDark) Color(0xFFEDEBF1) else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.16f),
        )
    }
}
