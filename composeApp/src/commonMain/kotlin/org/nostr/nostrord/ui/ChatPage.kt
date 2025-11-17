package org.nostr.nostrord.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment

@Composable
fun ChatPage() {
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf("Hello 👋", "Welcome to Nostr Chat!") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Group Chat", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        // Message list
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            messages.forEach { msg ->
                Text(msg, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
            }
        }

        // Input
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .height(48.dp)
            )
            Button(onClick = {
                if (message.isNotBlank()) {
                    messages.add(message)
                    message = ""
                }
            }) {
                Text("Send")
            }
        }
    }
}
