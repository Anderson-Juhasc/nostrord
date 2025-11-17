package org.nostr.nostrord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.nostr.nostrord.network.NostrRepository
import org.nostr.nostrord.network.NostrGroupClient
import org.nostr.nostrord.network.UserMetadata
import org.nostr.nostrord.ui.components.AppButton
import org.nostr.nostrord.ui.components.GroupSidebar
import org.nostr.nostrord.utils.*

sealed class ChatItem {
    data class DateSeparator(val date: String) : ChatItem()
    data class SystemEvent(
        val pubkey: String,
        val action: String,
        val createdAt: Long,
        val id: String
    ) : ChatItem()
    data class Message(val message: NostrGroupClient.NostrMessage) : ChatItem()
}

@Composable
private fun AvatarPlaceholder(displayName: String, pubkey: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(generateColorFromString(pubkey)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayName.take(2).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GroupScreen(
    groupId: String,
    groupName: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    var selectedChannel by remember { mutableStateOf("general") }
    
    val allMessages by NostrRepository.messages.collectAsState()
    val allGroupMessages = allMessages[groupId] ?: emptyList()
    
    // Filter messages by channel
    val messages = remember(allGroupMessages, selectedChannel) {
        if (selectedChannel == "general") {
            // General: messages WITHOUT channel tag
            allGroupMessages.filter { message ->
                !message.tags.any { it.size >= 2 && it[0] == "channel" }
            }
        } else {
            // Other channels: messages WITH matching channel tag
            allGroupMessages.filter { message ->
                message.tags.any { it.size >= 2 && it[0] == "channel" && it[1] == selectedChannel }
            }
        }
    }
    
    val connectionState by NostrRepository.connectionState.collectAsState()
    val joinedGroups by NostrRepository.joinedGroups.collectAsState()
    val userMetadata by NostrRepository.userMetadata.collectAsState()
    
    var messageInput by remember { mutableStateOf("") }
    var showLeaveDialog by remember { mutableStateOf(false) }
    val isJoined = joinedGroups.contains(groupId)
    
    val connectionStatus = when (connectionState) {
        is NostrRepository.ConnectionState.Disconnected -> "Disconnected"
        is NostrRepository.ConnectionState.Connecting -> "Connecting..."
        is NostrRepository.ConnectionState.Connected -> "Connected"
        is NostrRepository.ConnectionState.Error -> "Error"
    }

    val chatItems = remember(messages) {
        buildChatItems(messages)
    }

    // Initial load when screen opens
    LaunchedEffect(groupId) {
        scope.launch {
            NostrRepository.requestGroupMessages(groupId, selectedChannel)
        }
    }

    // Reload when channel changes
    LaunchedEffect(selectedChannel) {
        scope.launch {
            NostrRepository.requestGroupMessages(groupId, selectedChannel)
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Group") },
            text = { Text("Are you sure you want to leave ${groupName ?: "this group"}? You can rejoin later if you change your mind.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            NostrRepository.leaveGroup(groupId)
                            showLeaveDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFED4245)
                    )
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        GroupSidebar(
            groupName = groupName,
            selectedId = selectedChannel,
            onSelect = { channel ->
                selectedChannel = channel
            }
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
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
                TextButton(onClick = onBack) {
                    Text("← Back", color = Color(0xFF00AFF4))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#$selectedChannel",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$connectionStatus • ${messages.size} messages",
                        color = Color(0xFF99AAB5),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (!isJoined) {
                    Button(
                        onClick = {
                            scope.launch {
                                NostrRepository.joinGroup(groupId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5865F2)
                        )
                    ) {
                        Text("Join Group")
                    }
                } else {
                    Button(
                        onClick = { showLeaveDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFED4245)
                        )
                    ) {
                        Text("Leave Group")
                    }
                }
            }

            val listState = rememberLazyListState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (chatItems.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "No messages yet",
                            color = Color(0xFF99AAB5),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (isJoined) "Be the first to send a message!" else "Join the group to participate!",
                            color = Color(0xFF72767D),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(chatItems, key = { item ->
                            when (item) {
                                is ChatItem.DateSeparator -> "date_${item.date}"
                                is ChatItem.SystemEvent -> "system_${item.id}"
                                is ChatItem.Message -> "msg_${item.message.id}"
                            }
                        }) { item ->
                            when (item) {
                                is ChatItem.DateSeparator -> DateSeparatorItem(item.date)
                                is ChatItem.SystemEvent -> SystemEventItem(item, userMetadata[item.pubkey])
                                is ChatItem.Message -> MessageItem(item.message, userMetadata[item.message.pubkey])
                            }
                        }
                    }

                    LaunchedEffect(chatItems.size) {
                        if (chatItems.isNotEmpty()) {
                            listState.scrollToItem(chatItems.lastIndex)
                        }
                    }
                }
            }

            if (!isJoined) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF40444B))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Join the group to send messages",
                            color = Color(0xFF72767D),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    NostrRepository.joinGroup(groupId)
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF5865F2)
                            )
                        ) {
                            Text("Join Now")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF40444B))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        placeholder = { Text("Message #$selectedChannel") },
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Enter &&
                                    event.isShiftPressed
                                ) {
                                    if (messageInput.isNotBlank()) {
                                        scope.launch {
                                            NostrRepository.sendMessage(groupId, messageInput, selectedChannel)
                                            messageInput = ""
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF383A40),
                            unfocusedContainerColor = Color(0xFF383A40),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = Color(0xFF72767D),
                            unfocusedPlaceholderColor = Color(0xFF72767D),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    AppButton(
                        text = "Send",
                        enabled = messageInput.isNotBlank(),
                        onClick = {
                            scope.launch {
                                NostrRepository.sendMessage(groupId, messageInput, selectedChannel)
                                messageInput = ""
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DateSeparatorItem(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF40444B),
            thickness = 1.dp
        )
        Surface(
            color = Color(0xFF36393F),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = date,
                color = Color(0xFF72767D),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SystemEventItem(
    event: ChatItem.SystemEvent,
    metadata: UserMetadata? = null
) {
    val displayName = metadata?.displayName ?: metadata?.name ?: event.pubkey.take(8)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            color = Color(0xFF99AAB5),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = event.action,
            color = Color(0xFF72767D),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatTimestamp(event.createdAt),
            color = Color(0xFF72767D),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MessageItem(
    message: NostrGroupClient.NostrMessage,
    metadata: UserMetadata? = null
) {
    val displayName = metadata?.displayName ?: metadata?.name ?: message.pubkey.take(8)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        AvatarPlaceholder(displayName, message.pubkey)
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(message.createdAt),
                    color = Color(0xFF72767D),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SelectionContainer {
                Text(
                    text = message.content,
                    color = Color(0xFFDCDDDE),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun buildChatItems(messages: List<NostrGroupClient.NostrMessage>): List<ChatItem> {
    val items = mutableListOf<ChatItem>()
    var lastDate: String? = null
    
    messages.sortedBy { it.createdAt }.forEach { message ->
        val messageDate = getDateLabel(message.createdAt)
        
        if (messageDate != lastDate) {
            items.add(ChatItem.DateSeparator(messageDate))
            lastDate = messageDate
        }
        
        when (message.kind) {
            9 -> items.add(ChatItem.Message(message))
            9021 -> items.add(ChatItem.SystemEvent(
                pubkey = message.pubkey,
                action = "Joined",
                createdAt = message.createdAt,
                id = message.id
            ))
            9022 -> items.add(ChatItem.SystemEvent(
                pubkey = message.pubkey,
                action = "Left",
                createdAt = message.createdAt,
                id = message.id
            ))
        }
    }
    
    return items
}

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
