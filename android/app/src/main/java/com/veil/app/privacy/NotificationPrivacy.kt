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

    fun permissionState(apiLevel: Int, granted: Boolean): NotificationPermissionState = when {
        !permissionIsRuntimeRequired(apiLevel) -> NotificationPermissionState.NOT_REQUIRED
        granted -> NotificationPermissionState.ENABLED
        else -> NotificationPermissionState.DISABLED
    }

    fun channelSpec(): NotificationChannelSpec = NotificationChannelSpec(
        id = CHANNEL_ID,
        importance = NotificationManager.IMPORTANCE_LOW,
        showBadge = false,
    )

    /** Future delivery must apply this to each notification; channels cannot enforce it. */
    fun notificationVisibility(): Int = Notification.VISIBILITY_SECRET

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val spec = channelSpec()
        val channel = NotificationChannel(spec.id, "Veil activity", spec.importance).apply {
            setShowBadge(spec.showBadge)
            description = "Privacy-preserving Veil activity alerts"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

internal enum class NotificationPermissionState {
    ENABLED,
    DISABLED,
    NOT_REQUIRED,
}

internal data class NotificationChannelSpec(
    val id: String,
    val importance: Int,
    val showBadge: Boolean,
)
