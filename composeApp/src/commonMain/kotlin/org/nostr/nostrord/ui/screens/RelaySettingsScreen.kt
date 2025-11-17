package org.nostr.nostrord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nostr.nostrord.network.NostrRepository
import org.nostr.nostrord.ui.Screen
import org.nostr.nostrord.ui.components.Sidebar

data class RelayInfo(
    val url: String,
    var status: RelayStatus = RelayStatus.DISCONNECTED,
    var groupCount: Int? = null,
    var details: String = "No additional details available."
)

enum class RelayStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    ERROR
}

@Composable
fun RelaySettingsScreen(onNavigate: (Screen) -> Unit) {
    val scope = rememberCoroutineScope()
    
    // Collect state from NostrRepository
    val groups by NostrRepository.groups.collectAsState()
    val connectionState by NostrRepository.connectionState.collectAsState()
    val joinedGroups by NostrRepository.joinedGroups.collectAsState()
    val currentRelay by NostrRepository.currentRelayUrl.collectAsState()
    val pubKey = NostrRepository.getPublicKey()
    
    val connectionStatus = when (connectionState) {
        is NostrRepository.ConnectionState.Disconnected -> "Disconnected"
        is NostrRepository.ConnectionState.Connecting -> "Connecting..."
        is NostrRepository.ConnectionState.Connected -> "Connected"
        is NostrRepository.ConnectionState.Error ->
            "Error: ${(connectionState as NostrRepository.ConnectionState.Error).message}"
    }
    
    var relays by remember {
        mutableStateOf(
            listOf(
                RelayInfo("wss://groups.fiatjaf.com"),
                RelayInfo("wss://relay.groups.nip29.com"),
                RelayInfo("wss://groups.0xchat.com")
            )
        )
    }
    
    var newRelayUrl by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Check relay statuses
    LaunchedEffect(currentRelay) {
        relays = relays.map { relay ->
            relay.copy(
                status = if (relay.url == currentRelay) RelayStatus.CONNECTED else RelayStatus.DISCONNECTED
            )
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        /** ---------- SIDEBAR ---------- **/
        Sidebar(
            onNavigate = onNavigate,
            connectionStatus = connectionStatus,
            pubKey = pubKey,
            joinedGroups = joinedGroups,
            groups = groups
        )

        /** ---------- MAIN CONTENT ---------- **/
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF36393F))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF202225))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onNavigate(Screen.Home) }) {
                    Text("← Back", color = Color(0xFF00AFF4))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "Relay Settings",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(relays) { relay ->
                    RelayCard(
                        relay = relay,
                        isActive = relay.url == currentRelay,
                        onSelectRelay = {
                            scope.launch {
                                NostrRepository.switchRelay(relay.url)
                                relays = relays.map { r ->
                                    r.copy(status = if (r.url == relay.url) RelayStatus.CONNECTED else RelayStatus.DISCONNECTED)
                                }
                            }
                        }
                    )
                }
                
                item {
                    AddRelayCard(onClick = { showAddDialog = true })
                }
            }
        }
    }
    
    // Add Relay Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Relay") },
            text = {
                Column {
                    Text("Enter the WebSocket URL of the relay you want to add:")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newRelayUrl,
                        onValueChange = { newRelayUrl = it },
                        placeholder = { Text("wss://example.com") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newRelayUrl.isNotBlank() && newRelayUrl.startsWith("wss://")) {
                            relays = relays + RelayInfo(newRelayUrl)
                            newRelayUrl = ""
                            showAddDialog = false
                        }
                    },
                    enabled = newRelayUrl.isNotBlank() && newRelayUrl.startsWith("wss://")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false
                    newRelayUrl = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RelayCard(
    relay: RelayInfo,
    isActive: Boolean,
    onSelectRelay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.border(2.dp, Color(0xFF5865F2), RoundedCornerShape(8.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2F3136)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Relay URL
            Text(
                text = relay.url,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Status:",
                    color = Color(0xFF99AAB5),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                val statusColor = when (relay.status) {
                    RelayStatus.CONNECTED -> Color(0xFF3BA55D)
                    RelayStatus.CONNECTING -> Color(0xFFFAA81A)
                    RelayStatus.ERROR -> Color(0xFFED4245)
                    RelayStatus.DISCONNECTED -> Color(0xFF747F8D)
                }
                
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, shape = RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = relay.status.name,
                    color = statusColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Groups
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Groups:",
                    color = Color(0xFF99AAB5),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = relay.groupCount?.toString() ?: "Not Available",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Details
            Text(
                text = relay.details,
                color = Color(0xFF99AAB5),
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Select Button
            Button(
                onClick = onSelectRelay,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFF3BA55D) else Color(0xFF5865F2),
                    disabledContainerColor = Color(0xFF3BA55D)
                ),
                enabled = !isActive
            ) {
                Text(
                    text = if (isActive) "Active Relay" else "Select as Active Relay",
                    color = Color.White
                )            
            }
        }
    }
}

@Composable
private fun AddRelayCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2F3136)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add New Relay",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Easily manage your groups in New Relay by including them in the list.",
                color = Color(0xFF99AAB5),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5865F2)
                )
            ) {
                Text("Add Relay URL")
            }
        }
    }
}
