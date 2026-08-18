package com.veil.app.lock

import com.veil.app.security.AesGcmProtectedBlobCipher
import com.veil.app.security.AuthenticationResult
import com.veil.app.security.AuthenticatorAvailability
import com.veil.app.security.ExistingKeyResult
import com.veil.app.security.FakeAppAuthenticator
import com.veil.app.security.ProtectedLocalPayloadCodec
import com.veil.app.security.ProtectedStateFormat
import com.veil.app.security.ProtectionFixture
import com.veil.app.security.ProtectionStatus
import com.veil.app.security.protectionFixture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrivacyControllerTest {
    @Test
    fun appLockDefaultsDisabledAfterPhase1bMigration() {
        val fixture = protectionFixture()
        writeLegacySentinel(fixture)
        val controller = controller(fixture)

        assertEquals(ProtectionStatus.READY, controller.state.value.protectionStatus)
        assertFalse(controller.state.value.appLockEnabled)
        assertTrue(controller.state.value.appLockPreferenceKnown)
        assertEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
        assertEquals(false, fixture.store.load().payload?.fromLegacy)
    }

    @Test
    fun enablingRequiresAuthSuccess() {
        val fixture = readyFixture()
        val controller = controller(fixture)
        controller.onProcessForeground()
        val authenticator = FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS)

        controller.setAppLockEnabled(true, authenticator)

        assertEquals(1, authenticator.authenticateCalls)
        assertTrue(controller.state.value.appLockEnabled)
        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
        assertEquals(true, fixture.store.load().payload?.appLockEnabled)
    }

    @Test
    fun cancelledAuthDoesNotEnable() {
        val fixture = readyFixture()
        val controller = controller(fixture)
        val authenticator = FakeAppAuthenticator(nextResult = AuthenticationResult.CANCELLED)

        controller.setAppLockEnabled(true, authenticator)

        assertFalse(controller.state.value.appLockEnabled)
        assertEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
        assertEquals(false, fixture.store.load().payload?.appLockEnabled)
    }

    @Test
    fun failedAuthDoesNotEnable() {
        val fixture = readyFixture()
        val controller = controller(fixture)
        val authenticator = FakeAppAuthenticator(nextResult = AuthenticationResult.ERROR)

        controller.setAppLockEnabled(true, authenticator)

        assertFalse(controller.state.value.appLockEnabled)
        assertEquals(AppLockError.AUTH_FAILED, controller.state.value.error)
        assertEquals(false, fixture.store.load().payload?.appLockEnabled)
    }

    @Test
    fun persistenceFailureDoesNotEnable() {
        val fixture = readyFixture()
        val controller = controller(fixture)
        fixture.file.failWrites = true
        val authenticator = FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS)

        controller.setAppLockEnabled(true, authenticator)

        assertFalse(controller.state.value.appLockEnabled)
        assertEquals(AppLockError.PROTECTED_STATE_UNAVAILABLE, controller.state.value.error)
        assertEquals(false, fixture.store.load().payload?.appLockEnabled)
    }

    @Test
    fun disablingRequiresAuthSuccess() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        controller.onProcessForeground()
        val authenticator = FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS)

        controller.setAppLockEnabled(false, authenticator)

        assertFalse(controller.state.value.appLockEnabled)
        assertEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
        assertEquals(false, fixture.store.load().payload?.appLockEnabled)
    }

    @Test
    fun cancelledDisableKeepsEnabled() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        val authenticator = FakeAppAuthenticator(nextResult = AuthenticationResult.CANCELLED)

        controller.setAppLockEnabled(false, authenticator)

        assertTrue(controller.state.value.appLockEnabled)
        assertEquals(true, fixture.store.load().payload?.appLockEnabled)
        assertNotEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
    }

    @Test
    fun coldStartWithEnabledPreferenceIsLocked() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))

        val controller = controller(fixture)
        controller.onProcessForeground()

        assertEquals(AppLockSessionState.LOCKED, controller.state.value.session)
        assertTrue(controller.state.value.appLockEnabled)
    }

    @Test
    fun coldStartWithDisabledPreferenceDoesNotRequireLock() {
        val fixture = readyFixture()

        val controller = controller(fixture)
        controller.onProcessForeground()

        assertEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
        assertFalse(controller.state.value.appLockEnabled)
    }

    @Test
    fun successfulAuthUnlocksSession() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        controller.onProcessForeground()
        val authenticator = FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS)

        controller.requestUnlock(authenticator)

        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
    }

    @Test
    fun realBackgroundRelocksWhenEnabled() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        controller.onProcessForeground()
        controller.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))

        controller.onProcessBackground()

        assertEquals(AppLockSessionState.LOCKED, controller.state.value.session)
    }

    @Test
    fun configurationLikeLifecycleDoesNotLockIfProcessDidNotBackground() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        controller.onProcessForeground()
        controller.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))

        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
    }

    @Test
    fun authFlowBackgroundDoesNotRaceLockBeforeResult() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        val authenticator = FakeAppAuthenticator(
            nextResult = AuthenticationResult.SUCCESS,
            completeImmediately = false,
        )
        controller.requestUnlock(authenticator)

        controller.onProcessBackground()
        assertEquals(AppLockSessionState.AUTHENTICATING, controller.state.value.session)

        authenticator.complete(AuthenticationResult.SUCCESS)
        controller.onProcessBackground()
        assertEquals(AppLockSessionState.AUTHENTICATING, controller.state.value.session)
        assertNotEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)

        controller.onProcessForeground()
        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
    }

    @Test
    fun processRecreationNeverRestoresUnlocked() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val first = controller(fixture)
        first.onProcessForeground()
        first.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))
        assertEquals(AppLockSessionState.UNLOCKED, first.state.value.session)
        first.cancel()

        val recreated = controller(fixture)

        assertEquals(AppLockSessionState.LOCKED, recreated.state.value.session)
        assertNotEquals(AppLockSessionState.UNLOCKED, recreated.state.value.session)
    }

    @Test
    fun corruptProtectedStateCannotBypassLock() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        fixture.file.contents = byteArrayOf(1, 2, 3)

        val controller = controller(fixture)

        assertEquals(ProtectionStatus.CORRUPT_OR_UNREADABLE, controller.state.value.protectionStatus)
        assertEquals(AppLockSessionState.UNAVAILABLE, controller.state.value.session)
        assertFalse(controller.state.value.appLockPreferenceKnown)
        assertFalse(controller.state.value.appLockEnabled)
        assertNotEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
        assertNotEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
    }

    @Test
    fun missingLocalProtectionKeyCannotBypassLock() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        fixture.keys.makeMissing()

        val controller = controller(fixture)

        assertEquals(ProtectionStatus.KEY_UNAVAILABLE, controller.state.value.protectionStatus)
        assertEquals(AppLockSessionState.UNAVAILABLE, controller.state.value.session)
        assertFalse(controller.state.value.appLockPreferenceKnown)
        assertFalse(controller.state.value.appLockEnabled)
        assertNotEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
    }

    @Test
    fun failedMigrationPreservesPreviousValidStateForController() {
        val fixture = protectionFixture()
        writeLegacySentinel(fixture)
        val previous = fixture.file.contents!!.copyOf()
        fixture.file.failWrites = true

        val controller = controller(fixture)

        assertEquals(ProtectionStatus.MIGRATION_FAILED, controller.state.value.protectionStatus)
        assertEquals(AppLockSessionState.UNAVAILABLE, controller.state.value.session)
        assertFalse(controller.state.value.appLockPreferenceKnown)
        assertFalse(controller.state.value.appLockEnabled)
        assertEquals(AppLockError.STATE_UPDATE_FAILED, controller.state.value.error)
        assertNotEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
        assertEquals(previous.toList(), fixture.file.contents?.toList())
        assertEquals(true, fixture.store.load().payload?.fromLegacy)
        assertEquals(ProtectionStatus.READY, fixture.store.load().status)

        fixture.file.failWrites = false
        controller.load()

        assertEquals(ProtectionStatus.READY, controller.state.value.protectionStatus)
        assertTrue(controller.state.value.appLockPreferenceKnown)
        assertFalse(controller.state.value.appLockEnabled)
        assertEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
        assertEquals(false, fixture.store.load().payload?.fromLegacy)
    }

    @Test
    fun notConfiguredAvailabilityDoesNotEnable() {
        val fixture = readyFixture()
        val controller = controller(fixture)
        val authenticator = FakeAppAuthenticator(
            availabilityValue = AuthenticatorAvailability.NOT_CONFIGURED,
        )

        controller.setAppLockEnabled(true, authenticator)

        assertEquals(0, authenticator.authenticateCalls)
        assertFalse(controller.state.value.appLockEnabled)
        assertEquals(AppLockError.AUTH_NOT_CONFIGURED, controller.state.value.error)
    }

    @Test
    fun enableAppLockWhileBackgroundedDuringPersistEndsLocked() {
        val fixture = readyFixture()
        lateinit var session: AppLockSessionState
        var enabled = false
        runWithPausedWrite(fixture, afterWrite = {
            session = it.state.value.session
            enabled = it.state.value.appLockEnabled
        }) { controller, awaitWrite, releaseWrite ->
            controller.onProcessForeground()
            val authenticator = FakeAppAuthenticator(completeImmediately = false)
            controller.setAppLockEnabled(true, authenticator)
            authenticator.complete(AuthenticationResult.SUCCESS)
            awaitWrite()
            controller.onProcessBackground()
            releaseWrite()
        }
        assertTrue(enabled)
        assertEquals(AppLockSessionState.LOCKED, session)
        assertEquals(true, fixture.store.load().payload?.appLockEnabled)
    }

    @Test
    fun disableAppLockPersistFailureWhileBackgroundedEndsLocked() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        fixture.file.failWrites = true
        lateinit var session: AppLockSessionState
        var enabled = false
        runWithPausedWrite(fixture, afterWrite = {
            session = it.state.value.session
            enabled = it.state.value.appLockEnabled
        }) { controller, awaitWrite, releaseWrite ->
            controller.onProcessForeground()
            controller.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))
            assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
            val authenticator = FakeAppAuthenticator(completeImmediately = false)
            controller.setAppLockEnabled(false, authenticator)
            authenticator.complete(AuthenticationResult.SUCCESS)
            awaitWrite()
            controller.onProcessBackground()
            releaseWrite()
        }
        assertTrue(enabled)
        assertEquals(AppLockSessionState.LOCKED, session)
        assertEquals(true, fixture.store.load().payload?.appLockEnabled)
    }

    @Test
    fun enableAppLockWhileForegroundEndsUnlocked() {
        val fixture = readyFixture()
        val controller = controller(fixture)
        controller.onProcessForeground()

        controller.setAppLockEnabled(true, FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))

        assertTrue(controller.state.value.appLockEnabled)
        assertEquals(AppLockSessionState.UNLOCKED, controller.state.value.session)
    }

    @Test
    fun disableAppLockWhileForegroundClearsLock() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        controller.onProcessForeground()
        controller.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))

        controller.setAppLockEnabled(false, FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))

        assertFalse(controller.state.value.appLockEnabled)
        assertEquals(AppLockSessionState.LOCK_NOT_REQUIRED, controller.state.value.session)
    }

    @Test
    fun cancelledDisableWhileBackgroundedEndsLocked() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        controller.onProcessForeground()
        controller.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))
        val authenticator = FakeAppAuthenticator(completeImmediately = false)

        controller.setAppLockEnabled(false, authenticator)
        controller.onProcessBackground()
        authenticator.complete(AuthenticationResult.CANCELLED)

        assertTrue(controller.state.value.appLockEnabled)
        assertEquals(AppLockSessionState.LOCKED, controller.state.value.session)
        assertEquals(true, fixture.store.load().payload?.appLockEnabled)
    }

    @Test
    fun failedAuthWhileBackgroundedWithLockEnabledEndsLocked() {
        val fixture = readyFixture()
        assertTrue(fixture.store.writeAppLockEnabled(true))
        val controller = controller(fixture)
        controller.onProcessForeground()
        controller.requestUnlock(FakeAppAuthenticator(nextResult = AuthenticationResult.SUCCESS))
        val authenticator = FakeAppAuthenticator(completeImmediately = false)

        controller.setAppLockEnabled(false, authenticator)
        controller.onProcessBackground()
        authenticator.complete(AuthenticationResult.ERROR)

        assertTrue(controller.state.value.appLockEnabled)
        assertEquals(AppLockSessionState.LOCKED, controller.state.value.session)
    }

    private fun runWithPausedWrite(
        fixture: ProtectionFixture,
        afterWrite: (AppPrivacyController) -> Unit = {},
        body: (AppPrivacyController, () -> Unit, () -> Unit) -> Unit,
    ) {
        val enteredWrite = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        fixture.file.onWrite = {
            enteredWrite.countDown()
            assertTrue(releaseWrite.await(3, TimeUnit.SECONDS))
        }
        val executor = Executors.newSingleThreadExecutor()
        val loaded = CountDownLatch(1)
        fixture.file.onRead = { loaded.countDown() }
        try {
            val created = AppPrivacyController(
                fixture.store,
                workerDispatcher = executor.asCoroutineDispatcher(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )
            assertTrue(loaded.await(3, TimeUnit.SECONDS))
            runBlocking {
                created.state.first { it.session != AppLockSessionState.EVALUATING && it.protectionStatus != ProtectionStatus.CHECKING }
            }
            fixture.file.onRead = null
            body(
                created,
                { assertTrue(enteredWrite.await(3, TimeUnit.SECONDS)) },
                { releaseWrite.countDown() },
            )
            executor.shutdown()
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS))
            afterWrite(created)
            created.cancel()
        } finally {
            fixture.file.onRead = null
            releaseWrite.countDown()
            executor.shutdownNow()
        }
    }

    private fun readyFixture(): ProtectionFixture = protectionFixture().also {
        assertEquals(ProtectionStatus.READY, it.store.provision())
    }

    private fun writeLegacySentinel(fixture: ProtectionFixture) {
        val key = (fixture.keys.provisioningKey() as ExistingKeyResult.Available).key
        val blob = AesGcmProtectedBlobCipher().encrypt(key, ProtectedLocalPayloadCodec.LEGACY_SENTINEL)
        fixture.file.contents = ProtectedStateFormat.encode(blob)
    }

    private fun controller(fixture: ProtectionFixture): AppPrivacyController {
        val created = AppPrivacyController(
            fixture.store,
            workerDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        return created
    }
}
