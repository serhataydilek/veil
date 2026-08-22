package com.veil.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardPrivacyTest {
    @Test
    fun sensitiveCopyIsExplicitAndSchedulesBoundedClear() {
        val port = FakeClipboardPort()
        ClipboardPrivacy(port).copySensitive("private value")

        assertTrue(port.current?.token?.isNotBlank() == true)
        assertEquals("private value", port.current?.text)
        assertEquals(ClipboardPrivacy.CLEAR_DELAY_MILLIS, port.delayMillis)
    }

    @Test
    fun delayedClearDoesNotRemoveReplacedClipboardContent() {
        val port = FakeClipboardPort()
        ClipboardPrivacy(port).copySensitive("private value")
        port.current = OwnedClipboardClip("other-owner", "user replacement")

        port.runDelayedClear()

        assertEquals("user replacement", port.current?.text)
    }

    @Test
    fun delayedClearRemovesOnlyMatchingVeilOwnedContent() {
        val port = FakeClipboardPort()
        ClipboardPrivacy(port).copySensitive("private value")

        port.runDelayedClear()

        assertNull(port.current)
    }

    private class FakeClipboardPort : ClipboardPort {
        var current: OwnedClipboardClip? = null
        var scheduledToken: String? = null
        var delayMillis: Long? = null

        override fun set(clip: OwnedClipboardClip) {
            current = clip
        }

        override fun clearAfter(token: String, delayMillis: Long) {
            scheduledToken = token
            this.delayMillis = delayMillis
        }

        fun runDelayedClear() {
            if (current?.token == scheduledToken) current = null
        }
    }
}
