package com.oavi.browser.engine

import com.oavi.browser.model.UrlInput
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/**
 * Decides what a site may do. Quiet, harmless capabilities are granted, notifications are
 * declined, and anything that reveals the person (location, camera, microphone) is asked.
 */
internal class Permissions(
    private val tabId: String,
    private val post: (PageRequest) -> Unit,
    private val finish: (PageRequest) -> Unit,
) : PermissionDelegate {

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: PermissionDelegate.Callback,
    ) {
        val wanted = permissions?.toList().orEmpty()
        if (wanted.isEmpty()) {
            callback.grant()
            return
        }
        lateinit var request: PageRequest
        request = PageRequest.AndroidPermissions(tabId, wanted) { granted ->
            finish(request)
            if (granted) callback.grant() else callback.reject()
        }
        post(request)
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: ContentPermission,
    ): GeckoResult<Int>? = when (perm.permission) {
        PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
        PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
        PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS,
        -> GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)

        PermissionDelegate.PERMISSION_GEOLOCATION -> {
            val result = GeckoResult<Int>()
            lateinit var request: PageRequest
            request = PageRequest.Confirm(tabId, ConfirmKind.LOCATION, "", UrlInput.displayHost(perm.uri)) { agreed ->
                finish(request)
                result.complete(if (agreed) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
            }
            post(request)
            result
        }

        else -> GeckoResult.fromValue(ContentPermission.VALUE_DENY)
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<PermissionDelegate.MediaSource>?,
        audio: Array<PermissionDelegate.MediaSource>?,
        callback: PermissionDelegate.MediaCallback,
    ) {
        val camera = video?.firstOrNull()
        val microphone = audio?.firstOrNull()
        val kind = when {
            camera != null && microphone != null -> ConfirmKind.CAMERA_AND_MICROPHONE
            camera != null -> ConfirmKind.CAMERA
            microphone != null -> ConfirmKind.MICROPHONE
            else -> {
                callback.reject()
                return
            }
        }
        lateinit var request: PageRequest
        request = PageRequest.Confirm(tabId, kind, "", UrlInput.displayHost(uri)) { agreed ->
            finish(request)
            if (agreed) callback.grant(camera, microphone) else callback.reject()
        }
        post(request)
    }
}
