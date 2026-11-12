package com.oavi.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.widget.FrameLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * The surface a page is drawn on. Shows whichever tab it is told to and reports the finger
 * movements the island uses to tuck itself away while reading.
 */
class EngineView(context: Context) : FrameLayout(context) {
    private val geckoView = GeckoView(context)
    private var shown: GeckoSession? = null
    private var lastY = 0f

    /** Vertical finger movement in pixels since the last report; negative while scrolling down a page. */
    var onScrollGesture: ((Float) -> Unit)? = null

    /** A finger landed on the page. */
    var onTouchStart: (() -> Unit)? = null

    init {
        addView(geckoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Colour shown until a page has painted, so a new tab never flashes white in dark mode. */
    fun setCoverColor(color: Int) {
        geckoView.coverUntilFirstPaint(color)
    }

    /** Displays the page of [tabId], or nothing when that tab has no page loaded yet. */
    fun show(controller: TabsController, tabId: String?) {
        val session = controller.geckoFor(tabId)
        if (shown === session) return
        if (shown != null) geckoView.releaseSession()
        shown = session
        if (session != null) geckoView.setSession(session)
    }

    /** Detaches from the engine. Call when the view is going away for good. */
    fun release() {
        if (shown != null) geckoView.releaseSession()
        shown = null
    }

    /** Takes a picture of the visible page. [onResult] receives null when nothing could be captured. */
    fun capture(onResult: (Bitmap?) -> Unit) {
        if (shown == null || width == 0 || height == 0) {
            onResult(null)
            return
        }
        try {
            geckoView.capturePixels().accept(
                { bitmap -> onResult(bitmap) },
                { onResult(null) },
            )
        } catch (_: RuntimeException) {
            onResult(null)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.y
                onTouchStart?.invoke()
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                val delta = event.y - lastY
                lastY = event.y
                if (delta != 0f) onScrollGesture?.invoke(delta)
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
