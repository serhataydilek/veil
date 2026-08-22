package com.veil.app.privacy

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidNotificationPrivacyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After
    fun tearDown() {
        manager.deleteNotificationChannel(NotificationPrivacy.CHANNEL_ID)
    }

    @Test
    fun channelDefaultsAreLowImportanceAndBadgeDisabled() {
        NotificationPrivacy.createChannel(context)
        val channel = manager.getNotificationChannel(NotificationPrivacy.CHANNEL_ID)

        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.canShowBadge())
    }
}
