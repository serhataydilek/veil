package com.veil.app.privacy

import android.app.Notification
import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPrivacyTest {
    @Test
    fun permissionStateIsAuthoritativeForApiLevelAndGrant() {
        assertEquals(NotificationPermissionState.ENABLED, NotificationPrivacy.permissionState(33, true))
        assertEquals(NotificationPermissionState.DISABLED, NotificationPrivacy.permissionState(33, false))
        assertEquals(NotificationPermissionState.NOT_REQUIRED, NotificationPrivacy.permissionState(32, false))
    }
    @Test
    fun permissionIsOnlyRuntimeRequiredOnAndroid13AndLater() {
        assertFalse(NotificationPrivacy.permissionIsRuntimeRequired(32))
        assertTrue(NotificationPrivacy.permissionIsRuntimeRequired(33))
    }

    @Test
    fun channelDefaultsAreLowInterruptingAndBadgeDisabled() {
        val spec = NotificationPrivacy.channelSpec()
        assertEquals(NotificationManager.IMPORTANCE_LOW, spec.importance)
        assertFalse(spec.showBadge)
    }

    @Test
    fun futureNotificationsMustBeSecretIndividually() {
        assertEquals(Notification.VISIBILITY_SECRET, NotificationPrivacy.notificationVisibility())
    }

}
