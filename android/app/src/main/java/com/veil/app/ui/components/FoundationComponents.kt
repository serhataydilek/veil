package com.veil.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeilTopBar(title: String, onBack: (() -> Unit)? = null, actions: List<Pair<String, () -> Unit>> = emptyList()) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) TextButton(onClick = onBack) { Text("Back") }
        },
        actions = {
            actions.forEach { (label, action) -> TextButton(onClick = action) { Text(label) } }
        },
    )
}

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body)
        Button(onClick = onPrimary) { Text(primaryLabel) }
        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
fun PrivacyNotice(text: String) {
    Card {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LocalStatusBanner(text: String) {
    Card {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
