package org.nostr.nostrord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nostr.nostrord.network.NostrRepository
import org.nostr.nostrord.ui.Screen
import org.nostr.nostrord.ui.components.Sidebar
import kotlin.math.abs

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    val scope = rememberCoroutineScope()

    val groups by NostrRepository.groups.collectAsState()
    val connectionState by NostrRepository.connectionState.collectAsState()
    val currentRelayUrl by NostrRepository.currentRelayUrl.collectAsState()
    val joinedGroups by NostrRepository.joinedGroups.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(groups, searchQuery) {
        if (searchQuery.isBlank()) groups
        else groups.filter {
            it.name?.contains(searchQuery, ignoreCase = true) == true ||
                    it.id.contains(searchQuery, ignoreCase = true)
        }
    }

    val connectionStatus = when (connectionState) {
        is NostrRepository.ConnectionState.Disconnected -> "Disconnected"
        is NostrRepository.ConnectionState.Connecting -> "Connecting..."
        is NostrRepository.ConnectionState.Connected -> "Connected"
        is NostrRepository.ConnectionState.Error ->
            "Error: ${(connectionState as NostrRepository.ConnectionState.Error).message}"
    }

    val pubKey = NostrRepository.getPublicKey()

    LaunchedEffect(Unit) {
        scope.launch {
            NostrRepository.connect()
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
            // Top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF202225))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Relay: $currentRelayUrl",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )

                    IconButton(onClick = { onNavigate(Screen.RelaySettings) }) {
                        Text(
                            "⚙️",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // Content - Header section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            ) {
                Text("Explore", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Groups found: ${filteredGroups.size}", color = Color(0xFF99AAB5))

                Spacer(modifier = Modifier.height(16.dp))

                /** --- Search Bar --- **/
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search groups...", color = Color(0xFF99AAB5)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Scrollable Grid
            if (filteredGroups.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "No groups found",
                        color = Color(0xFF99AAB5),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredGroups) { group ->
                        Column(
                            modifier = Modifier
                                .background(Color(0xFF2F3136), RoundedCornerShape(8.dp))
                                .clickable {
                                    onNavigate(Screen.Group(group.id, group.name))
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Group avatar placeholder
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(generateColorFromString(group.id)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (group.name ?: group.id).take(1).uppercase(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = group.name ?: group.id,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            group.about?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = it,
                                    color = Color(0xFF99AAB5),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper function to generate consistent colors from strings
private fun generateColorFromString(str: String): Color {
    val hash = str.hashCode()
    val colors = listOf(
        Color(0xFF5865F2),
        Color(0xFF57F287),
        Color(0xFFFEE75C),
        Color(0xFFEB459E),
        Color(0xFFED4245),
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFF95E1D3)
    )
    return colors[abs(hash) % colors.size]
}
