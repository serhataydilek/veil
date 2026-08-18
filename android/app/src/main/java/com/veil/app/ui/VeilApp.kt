package com.veil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.veil.app.lock.AppLockError
import com.veil.app.lock.AppLockSessionState
import com.veil.app.lock.AppPrivacyController
import com.veil.app.lock.AppPrivacyViewState
import com.veil.app.lock.userMessage
import com.veil.app.security.AppAuthenticator
import com.veil.app.security.AuthenticatorAvailability
import com.veil.app.security.ProtectionStatus
import com.veil.app.ui.components.EmptyState
import com.veil.app.ui.components.LocalStatusBanner
import com.veil.app.ui.components.PrivacyNotice
import com.veil.app.ui.components.VeilTopBar
import com.veil.app.ui.theme.VeilSpacing

private enum class AppScreen {
    WELCOME,
    IDENTITY_NOTICE,
    HOME,
    ADD_ID,
    MY_ID,
    SETTINGS,
}

@Composable
internal fun VeilApp(
    controller: AppPrivacyController,
    authenticator: AppAuthenticator,
) {
    val state by controller.state.collectAsState()
    var screen by remember { mutableStateOf(AppScreen.WELCOME) }
    LaunchedEffect(state.session, state.appLockEnabled) {
        if (state.session == AppLockSessionState.UNLOCKED && state.appLockEnabled) {
            screen = AppScreen.HOME
        }
    }

    when {
        state.session == AppLockSessionState.EVALUATING ||
            state.protectionStatus == ProtectionStatus.CHECKING -> WaitingScreen()
        state.session == AppLockSessionState.UNAVAILABLE ||
            state.protectionStatus == ProtectionStatus.KEY_UNAVAILABLE ||
            state.protectionStatus == ProtectionStatus.CORRUPT_OR_UNREADABLE ||
            state.protectionStatus == ProtectionStatus.MIGRATION_FAILED -> IdentityUnavailableScreen(
            status = state.protectionStatus,
            onPrepare = if (state.protectionStatus == ProtectionStatus.MIGRATION_FAILED) {
                controller::load
            } else {
                {}
            },
            onContinue = {},
            allowContinue = false,
        )
        state.session == AppLockSessionState.LOCKED ||
            state.session == AppLockSessionState.AUTHENTICATING -> LockedScreen(
            authenticating = state.session == AppLockSessionState.AUTHENTICATING,
            error = state.error,
            onUnlock = { controller.requestUnlock(authenticator) },
        )
        else -> when (screen) {
            AppScreen.WELCOME -> WelcomeScreen(onContinue = { screen = AppScreen.IDENTITY_NOTICE })
            AppScreen.IDENTITY_NOTICE -> IdentityUnavailableScreen(
                status = state.protectionStatus,
                onPrepare = controller::prepare,
                onContinue = { screen = AppScreen.HOME },
                allowContinue = true,
            )
            AppScreen.HOME -> HomeScreen(onNavigate = { screen = it })
            AppScreen.ADD_ID -> AddIdScreen(onBack = { screen = AppScreen.HOME })
            AppScreen.MY_ID -> UnavailableIdScreen(onBack = { screen = AppScreen.HOME })
            AppScreen.SETTINGS -> SettingsScreen(
                state = state,
                onBack = { screen = AppScreen.HOME },
                onAppLockChange = { enabled -> controller.setAppLockEnabled(enabled, authenticator) },
            )
        }
    }
}

@Composable
private fun WaitingScreen() {
    Scaffold { padding ->
        CenteredContent(padding) {
            Text("Veil", style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun LockedScreen(
    authenticating: Boolean,
    error: AppLockError?,
    onUnlock: () -> Unit,
) {
    Scaffold { padding ->
        CenteredContent(padding) {
            Text("Veil", style = MaterialTheme.typography.displaySmall)
            Text("Veil is locked.")
            Button(onClick = onUnlock, enabled = !authenticating) { Text("Unlock") }
            error?.userMessage()?.let { Text(it) }
        }
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Scaffold { padding ->
        CenteredContent(padding) {
            Text("Veil", style = MaterialTheme.typography.displaySmall)
            Text("Private conversations. Nothing else.", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onContinue) { Text("Continue") }
        }
    }
}

@Composable
private fun IdentityUnavailableScreen(
    status: ProtectionStatus,
    onPrepare: () -> Unit,
    onContinue: () -> Unit,
    allowContinue: Boolean,
) {
    Scaffold { padding ->
        CenteredContent(padding) {
            Text("Secure local storage", style = MaterialTheme.typography.headlineSmall)
            when (status) {
                ProtectionStatus.NOT_PROVISIONED, ProtectionStatus.PURGED -> {
                    Text("Secure local storage is not prepared.")
                    Button(onClick = onPrepare) { Text("Prepare secure storage") }
                }
                ProtectionStatus.CHECKING -> Text("Checking secure local storage.")
                ProtectionStatus.PROVISIONING, ProtectionStatus.PURGING -> Text("Preparing secure local storage.")
                ProtectionStatus.READY -> Text("Secure local storage ready.")
                ProtectionStatus.KEY_UNAVAILABLE -> Text(
                    "Protected local state is unavailable. It has not been reset.",
                )
                ProtectionStatus.CORRUPT_OR_UNREADABLE -> Text(
                    "Protected local state is unreadable. It has not been reset.",
                )
                ProtectionStatus.MIGRATION_FAILED -> {
                    Text("Protected local state could not be updated. Existing data has not been changed.")
                    Button(onClick = onPrepare) { Text("Try again") }
                }
                ProtectionStatus.ERROR -> Text("Secure local storage could not be prepared.")
            }
            Text("Veil identity creation is not implemented yet.")
            PrivacyNotice("Preparing local storage does not create or restore a Veil identity.")
            if (allowContinue) {
                Button(onClick = onContinue) { Text("Continue to local shell") }
            }
        }
    }
}

@Composable
private fun HomeScreen(onNavigate: (AppScreen) -> Unit) {
    Scaffold(
        topBar = {
            VeilTopBar(
                title = "Veil",
                actions = listOf(
                    "Add ID" to { onNavigate(AppScreen.ADD_ID) },
                    "My ID" to { onNavigate(AppScreen.MY_ID) },
                    "Settings" to { onNavigate(AppScreen.SETTINGS) },
                ),
            )
        },
    ) { padding ->
        EmptyState(
            modifier = Modifier.padding(padding),
            title = "No conversations",
            body = "Exchange IDs with someone you know to connect.",
            primaryLabel = "Add ID",
            onPrimary = { onNavigate(AppScreen.ADD_ID) },
            secondaryLabel = "Show my ID",
            onSecondary = { onNavigate(AppScreen.MY_ID) },
        )
    }
}

@Composable
private fun AddIdScreen(onBack: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { VeilTopBar(title = "Add an ID", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VeilSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(VeilSpacing.Md),
        ) {
            Text("Both people need to add each other’s current IDs before a conversation can begin.")
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    saved = false
                    error = null
                },
                label = { Text("Contact ID") },
                supportingText = { Text(error ?: "Local input only. Veil does not query an ID.") },
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (value.isBlank()) error = "Enter an ID to save it locally." else saved = true
                },
            ) { Text("Save ID") }
            if (saved) {
                LocalStatusBanner("ID saved")
            }
        }
    }
}

@Composable
private fun UnavailableIdScreen(onBack: () -> Unit) {
    Scaffold(topBar = { VeilTopBar(title = "My ID", onBack = onBack) }) { padding ->
        CenteredContent(padding) {
            Text("Contact IDs are unavailable", style = MaterialTheme.typography.headlineSmall)
            Text("Veil does not generate a contact ID until the reviewed capability subsystem exists.")
            PrivacyNotice("No temporary or production contact ID has been generated.")
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AppPrivacyViewState,
    onBack: () -> Unit,
    onAppLockChange: (Boolean) -> Unit,
) {
    val lockToggleEnabled = state.protectionStatus == ProtectionStatus.READY &&
        state.appLockPreferenceKnown &&
        !state.preferenceChangeInProgress &&
        (
            state.authenticatorAvailability == AuthenticatorAvailability.AVAILABLE ||
                state.appLockEnabled
            )
    Scaffold(topBar = { VeilTopBar(title = "Settings", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VeilSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(VeilSpacing.Md),
        ) {
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = VeilSpacing.Md)) {
                    Text("App Lock")
                    Text(
                        "Require device authentication to open Veil.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = state.appLockEnabled,
                    onCheckedChange = onAppLockChange,
                    enabled = lockToggleEnabled,
                )
            }
            if (state.authenticatorAvailability == AuthenticatorAvailability.NOT_CONFIGURED) {
                Text("A device screen lock must be configured before App Lock can be used.")
            }
            state.error?.userMessage()?.let { Text(it) }
            Text(
                "Screen privacy is always on. Android can block some screenshots, recordings, and recent-app previews. It cannot prevent every capture path.",
            )
            Text("Notifications are not configured in this local-only foundation.")
            Text("Identity", style = MaterialTheme.typography.titleMedium)
            Text("Identity creation requires the secure core. Veil does not provide identity recovery.")
        }
    }
}

@Composable
private fun CenteredContent(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(VeilSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(VeilSpacing.Md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
