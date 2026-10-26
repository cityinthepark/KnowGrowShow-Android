package com.stoneofhope.stoneofhope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun HomePlaceholderScreen(onOpenAccount: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Stone of Hope",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Phase 1 — home placeholder",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedButton(
            onClick = onOpenAccount,
            modifier = Modifier
                .padding(top = 24.dp)
                .testTag("openAccount")
        ) {
            Text("Account & Sync")
        }
    }
}
