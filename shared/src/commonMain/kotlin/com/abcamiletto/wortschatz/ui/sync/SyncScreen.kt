package com.abcamiletto.wortschatz.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.abcamiletto.wortschatz.data.ProgressSync
import com.abcamiletto.wortschatz.data.SyncCode
import com.abcamiletto.wortschatz.data.SyncStatus
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Slate
import kotlinx.coroutines.launch

@Composable
fun SyncRoute(sync: ProgressSync, onBack: () -> Unit) {
    val status by sync.status.collectAsState()
    var code by remember { mutableStateOf(sync.code) }
    var entry by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun link() {
        scope.launch {
            invalid = !sync.link(entry)
            if (!invalid) {
                code = sync.code
                entry = ""
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("Sync", style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when (status) {
                    SyncStatus.Synced -> "Synced"
                    SyncStatus.Offline -> "Offline. Your reviews sync when you reconnect."
                    SyncStatus.Waiting -> "Syncing"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Slate,
                modifier = Modifier.testTag("syncStatus"),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink)
                    .clickable {
                        clipboard.setText(AnnotatedString(SyncCode.display(code)))
                        copied = true
                    }
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    SyncCode.display(code),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f).testTag("syncCode"),
                )
                Icon(
                    if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = if (copied) "Copied" else "Copy code",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                "Enter this code on your other devices. Anyone with it can see and change your progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate,
            )
            Text("Have a code?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = entry,
                    onValueChange = {
                        entry = it
                        invalid = false
                    },
                    placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text("Codes have 16 letters and numbers") }) else null,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { link() }),
                    modifier = Modifier.weight(1f).testTag("syncEntry"),
                )
                Button(
                    onClick = ::link,
                    enabled = entry.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(56.dp).testTag("syncLink"),
                ) { Text("Link") }
            }
        }
    }
}
