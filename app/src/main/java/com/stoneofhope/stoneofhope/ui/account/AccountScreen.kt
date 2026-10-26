package com.stoneofhope.stoneofhope.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity

/**
 * Developer "Account & Sync" screen for the iOS ↔ Android interop test. Placeholder until the real
 * account UI is designed; English only on purpose.
 */
@Composable
fun AccountScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val unlockedUid by viewModel.unlockedUid.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    var email by rememberSaveable { mutableStateOf(viewModel.demoEmail) }
    var password by rememberSaveable { mutableStateOf(viewModel.demoPassword) }
    var draft by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Account & Sync", style = MaterialTheme.typography.titleLarge)
            }
            if (isSyncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(viewModel.firebaseMode, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        user?.let { "Signed in: ${it.email ?: "(no email)"}" } ?: "Not signed in",
                        modifier = Modifier.testTag("signedInStatus"),
                    )
                    user?.let { Text("uid: ${it.uid}", style = MaterialTheme.typography.bodySmall) }
                    Text(
                        if (user != null && unlockedUid == user?.uid) "Note key: unlocked" else "Note key: locked",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    viewModel.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        item {
            if (user == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, label = { Text("Email") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("Password") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Button(
                        onClick = { viewModel.signIn(email, password) },
                        enabled = viewModel.canSignIn && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("signInButton"),
                    ) { Text("Sign in") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::syncNow, enabled = !isSyncing, modifier = Modifier.testTag("syncButton")) {
                        Text("Sync now")
                    }
                    OutlinedButton(onClick = viewModel::signOut) { Text("Sign out") }
                }
            }
        }

        item {
            HorizontalDivider(Modifier.padding(bottom = 12.dp))
            Text("Notes on this device (${notes.size})", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, label = { Text("New quick note") },
                    modifier = Modifier.weight(1f).testTag("noteInput"),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.addNote(draft); draft = "" },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("addNoteButton"),
                ) { Text("Add") }
            }
        }

        items(notes, key = { it.id }) { note -> NoteRow(note, onDelete = { viewModel.delete(note) }) }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(note.content)
                Text(
                    "${note.noteType.raw} · ${note.createdAt}" + if (note.needsSync) " · not synced" else " · synced",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
