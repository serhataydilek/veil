package com.veil.app.lock

import androidx.test.platform.app.InstrumentationRegistry
import com.veil.app.security.AesGcmProtectedBlobCipher
import com.veil.app.security.AndroidAtomicProtectedStateFile
import com.veil.app.security.AndroidLocalProtectionKeyStore
import com.veil.app.security.AuthenticationResult
import com.veil.app.security.FakeAppAuthenticator
import com.veil.app.security.ProtectedStateStore
import com.veil.app.security.ProtectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidAppLockLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var keys: AndroidLocalProtectionKeyStore
    private lateinit var file: AndroidAtomicProtectedStateFile
    private lateinit var store: ProtectedStateStore

    @Before
    fun setUp() {
        keys = AndroidLocalProtectionKeyStore(TEST_ALIAS)
        file = AndroidAtomicProtectedStateFile(context, TEST_FILE_NAME)
        store = ProtectedStateStore(keys, file, AesGcmProtectedBlobCipher())
        assertTrue(store.purge().complete)
        assertEquals(ProtectionStatus.READY, store.provision())
        assertTrue(store.writeAppLockEnabled(true))
    }

    @After
    fun tearDown() {
        assertTrue(store.purge().complete)
    }

    @Test
    fun enabledPreferenceStartsLocked() {
        val controller = controller()

        assertEquals(AppLockSessionState.LOCKED, controller.state.value.session)
        assertNotEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
        controller.cancel()
    }

    @Test
    fun testAuthenticatorUnlocksWithoutProductionFallback() {
        val controller = controller()
        controller.onProcessForeground()
        val authenticator = FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS)

        controller.requestUnlock(authenticator)

        assertEquals(1, authenticator.authenticateCalls)
        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
        controller.cancel()
    }

    @Test
    fun briefBackgroundKeepsUnlockedWithinGrace() {
        val clock = FakeMonotonicClock()
        val controller = controller(clock)
        controller.onProcessForeground()
        controller.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))
        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)

        controller.onProcessBackground()
        clock.now += AppLockGracePolicy.DEFAULT_GRACE_MILLIS - 1
        controller.onProcessForeground()

        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
        controller.cancel()
    }

    @Test
    fun backgroundRelocksAfterGraceExpires() {
        val clock = FakeMonotonicClock()
        val controller = controller(clock)
        controller.onProcessForeground()
        controller.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))
        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)

        controller.onProcessBackground()
        clock.now += AppLockGracePolicy.DEFAULT_GRACE_MILLIS + 1
        controller.onProcessForeground()

        assertEquals(AppLockSessionState.LOCKED, controller.state.value.session)
        controller.cancel()
    }

    private fun controller(clock: MonotonicClock = SystemMonotonicClock): AppPrivacyController = AppPrivacyController(
        store,
        monotonicClock = clock,
        workerDispatcher = Dispatchers.Unconfined,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private class FakeMonotonicClock(var now: Long = 1_000L) : MonotonicClock {
        override fun nowMillis(): Long = now
    }

    private companion object {
        const val TEST_ALIAS = "veil.test.app-lock-lifecycle.v1"
        const val TEST_FILE_NAME = "veil-test-app-lock-lifecycle.v1"
    }
}
