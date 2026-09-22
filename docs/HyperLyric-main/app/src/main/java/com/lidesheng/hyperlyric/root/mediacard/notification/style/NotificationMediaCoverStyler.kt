package com.lidesheng.hyperlyric.root.mediacard.notification.style

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView

internal object NotificationMediaCoverStyler {
    private val circleOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    fun applyCircle(albumView: View, albumImage: ImageView) {
        if (albumView.visibility != View.VISIBLE) albumView.visibility = View.VISIBLE
        albumView.outlineProvider = circleOutlineProvider
        albumView.clipToOutline = false
        albumImage.outlineProvider = circleOutlineProvider
        albumImage.clipToOutline = true
        albumView.invalidateOutline()
        albumImage.invalidateOutline()
    }
}
