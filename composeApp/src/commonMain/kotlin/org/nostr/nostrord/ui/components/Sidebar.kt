package org.nostr.nostrord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nostr.nostrord.network.GroupMetadata
import org.nostr.nostrord.network.NostrRepository
import org.nostr.nostrord.ui.Screen
import kotlin.math.abs

@Composable
fun Sidebar(
    onNavigate: (Screen) -> Unit,
    connectionStatus: String,
    pubKey: String?,
    joinedGroups: Set<String>,
    groups: List<GroupMetadata>
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .widthIn(min = 80.dp, max = 250.dp)
            .fillMaxHeight()
            .background(Color(0xFF2F3136))
            .padding(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Nostrord",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            IconButton(onClick = { onNavigate(Screen.BackupPrivateKey) }) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = "Backup Private Key",
                    tint = Color.White
                )
            }
        }

        // Connection info
        Text(
            connectionStatus,
            color = Color(0xFF99AAB5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Logged-in pubkey
        if (pubKey != null) {
            // Logged-in pubkey display
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(Color(0xFF2F3136), shape = RoundedCornerShape(8.dp))
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = "Logged in as: ${pubKey.take(8)}…",
                    color = Color(0xFF99AAB5),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        NostrRepository.logout()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED4245))
            ) {
                Text("Logout", color = Color.White)
            }
        } else {
            // Show login only if not logged in
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onNavigate(Screen.NostrLogin) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2))
            ) {
                Text("Login with Nostr", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        /** --- Sidebar Groups List --- **/
        Text(
            "Joined Groups",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp)
        )

        if (joinedGroups.isEmpty()) {
            Text(
                "No joined groups yet",
                color = Color(0xFF99AAB5),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            val joinedList = joinedGroups.toList()
            LazyColumn {
                items(joinedList.size) { index ->
                    val groupId = joinedList[index]
                    val group = groups.find { it.id == groupId }
                    val groupName = group?.name ?: groupId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0xFF36393F), RoundedCornerShape(8.dp))
                            .clickable { onNavigate(Screen.Group(groupId, group?.name)) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colored circle avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(generateColorFromString(groupId)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = groupName.take(1).uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Group name
                        Text(
                            text = groupName,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
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
