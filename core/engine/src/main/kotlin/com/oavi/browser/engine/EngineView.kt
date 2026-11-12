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

    private var coverMax = -1
    private var coverRetracted = -1

    /**
     * Tells the engine that something floats over the bottom of the page: up to [maxPx] pixels,
     * [coverPx] of them right now. The engine then keeps the page's own bottom bars (cookie
     * notices, chat boxes, "Accept" buttons) above the island instead of underneath it, and
     * moves them down as the island shrinks. This is the engine's dynamic-toolbar mechanism;
     * the island plays the toolbar.
     */
    fun setBottomCover(maxPx: Int, coverPx: Int) {
        val max = maxPx.coerceAtLeast(0)
        if (max != coverMax) {
            coverMax = max
            geckoView.setDynamicToolbarMaxHeight(max)
        }
        // The engine assumes the "toolbar" is fully shown and takes a negative clipping for
        // how far it has moved out of the way (measured on the emulator: a positive value
        // lifts the page's bottom bars instead of lowering them).
        val retracted = (max - coverPx).coerceIn(0, max)
        if (retracted != coverRetracted) {
            coverRetracted = retracted
            geckoView.setVerticalClipping(-retracted)
        }
    }

    private var coverColor: Int? = null

    /**
     * Colour shown over a page that has not painted yet, so a new tab never flashes white in
     * dark mode. It only applies to pages shown from now on: covering a page that has already
     * painted would hide it until its next paint.
     */
    fun setCoverColor(color: Int) {
        coverColor = color
    }

    /** Displays the page of [tabId], or nothing when that tab has no page loaded yet. */
    fun show(controller: TabsController, tabId: String?) {
        val session = controller.geckoFor(tabId)
        if (shown === session) return
        if (shown != null) geckoView.releaseSession()
        shown = session
        if (session != null) {
            val color = coverColor
            if (color != null && controller.needsCover(tabId)) geckoView.coverUntilFirstPaint(color)
            geckoView.setSession(session)
        }
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
