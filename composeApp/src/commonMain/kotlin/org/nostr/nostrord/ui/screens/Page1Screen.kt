package org.nostr.nostrord.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import org.nostr.nostrord.ui.Screen

@Composable
fun Page1Screen(onNavigate: (Screen) -> Unit) {
    Column {
        Text("Page 1")
        Button(onClick = { onNavigate(Screen.Home) }) {
            Text("Back to Home")
        }
    }
}
