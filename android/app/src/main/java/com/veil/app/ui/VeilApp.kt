package com.veil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.veil.app.security.ProtectionStatus
import com.veil.app.security.LocalProtectionController
import com.veil.app.security.protectedStateStore
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
fun VeilApp() {
    var screen by remember { mutableStateOf(AppScreen.WELCOME) }
    val context = LocalContext.current
    val protectionController = remember(context) { LocalProtectionController(protectedStateStore(context)) }
    val protectionStatus by protectionController.status.collectAsState()
    DisposableEffect(protectionController) {
        onDispose { protectionController.cancel() }
    }

    when (screen) {
        AppScreen.WELCOME -> WelcomeScreen(onContinue = { screen = AppScreen.IDENTITY_NOTICE })
        AppScreen.IDENTITY_NOTICE -> IdentityUnavailableScreen(
            status = protectionStatus,
            onPrepare = protectionController::prepare,
            onContinue = { screen = AppScreen.HOME },
        )
        AppScreen.HOME -> HomeScreen(onNavigate = { screen = it })
        AppScreen.ADD_ID -> AddIdScreen(onBack = { screen = AppScreen.HOME })
        AppScreen.MY_ID -> UnavailableIdScreen(onBack = { screen = AppScreen.HOME })
        AppScreen.SETTINGS -> SettingsScreen(onBack = { screen = AppScreen.HOME })
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
                ProtectionStatus.ERROR -> Text("Secure local storage could not be prepared.")
            }
            Text("Veil identity creation is not implemented yet.")
            PrivacyNotice("Preparing local storage does not create or restore a Veil identity.")
            Button(onClick = onContinue) { Text("Continue to local shell") }
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
private fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { VeilTopBar(title = "Settings", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VeilSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(VeilSpacing.Md),
        ) {
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Text("App lock and screen privacy require later platform validation.")
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
