package com.veil.app.privacy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Foundation only: Veil currently has no message delivery and never requests
 * notification permission at launch. Future delivery code must opt in here.
 */
internal object NotificationPrivacy {
    const val CHANNEL_ID = "veil.private-events"
    const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

    fun permissionIsRuntimeRequired(apiLevel: Int): Boolean = apiLevel >= 33

    fun channelSpec(): NotificationChannelSpec = NotificationChannelSpec(
        id = CHANNEL_ID,
        importance = NotificationManager.IMPORTANCE_LOW,
        lockscreenVisibility = Notification.VISIBILITY_SECRET,
        showBadge = false,
    )

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val spec = channelSpec()
        val channel = NotificationChannel(spec.id, "Veil activity", spec.importance).apply {
            lockscreenVisibility = spec.lockscreenVisibility
            setShowBadge(spec.showBadge)
            description = "Privacy-preserving Veil activity alerts"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

internal data class NotificationChannelSpec(
    val id: String,
    val importance: Int,
    val lockscreenVisibility: Int,
    val showBadge: Boolean,
)
