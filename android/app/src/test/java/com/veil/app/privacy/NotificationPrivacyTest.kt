package com.veil.app.privacy

import android.app.Notification
import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPrivacyTest {
    @Test
    fun permissionIsOnlyRuntimeRequiredOnAndroid13AndLater() {
        assertFalse(NotificationPrivacy.permissionIsRuntimeRequired(32))
        assertTrue(NotificationPrivacy.permissionIsRuntimeRequired(33))
    }

    @Test
    fun channelDefaultsArePrivateAndLowInterrupting() {
        val spec = NotificationPrivacy.channelSpec()
        assertEquals(NotificationManager.IMPORTANCE_LOW, spec.importance)
        assertEquals(Notification.VISIBILITY_SECRET, spec.lockscreenVisibility)
        assertFalse(spec.showBadge)
    }

}
