package org.nostr.nostrord.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.nostr.nostrord.nostr.KeyPair
import org.nostr.nostrord.nostr.Event
import org.nostr.nostrord.storage.SecureStorage
import org.nostr.nostrord.utils.epochMillis

object NostrRepository {
    private var client: NostrGroupClient? = null
    private var metadataClient: NostrGroupClient? = null
    private var isConnecting = false

    private var keyPair: KeyPair? = null
    
    private val metadataRelays = listOf(
        "wss://relay.damus.io",
        //"wss://relay.nostr.band",
        //"wss://nos.lol"
    )
    private var currentMetadataRelayIndex = 0
    
    private val _currentRelayUrl = MutableStateFlow("wss://groups.fiatjaf.com")
    val currentRelayUrl: StateFlow<String> = _currentRelayUrl.asStateFlow()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _groups = MutableStateFlow<List<GroupMetadata>>(emptyList())
    val groups: StateFlow<List<GroupMetadata>> = _groups.asStateFlow()
    
    private val _messages = MutableStateFlow<Map<String, List<NostrGroupClient.NostrMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<NostrGroupClient.NostrMessage>>> = _messages.asStateFlow()
    
    private val _joinedGroups = MutableStateFlow<Set<String>>(emptySet())
    val joinedGroups: StateFlow<Set<String>> = _joinedGroups.asStateFlow()
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userMetadata = MutableStateFlow<Map<String, UserMetadata>>(emptyMap())
    val userMetadata: StateFlow<Map<String, UserMetadata>> = _userMetadata.asStateFlow()

    private var kind10009SubId: String? = null
    private var kind10009Received = false
    private var eoseReceived = false
    
    // Store groups from ALL relays (for kind:10009 merging)
    private val allRelayGroups = mutableMapOf<String, MutableSet<String>>() // relayUrl -> Set<groupId>
    
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    suspend fun initialize() {
        val savedRelayUrl = SecureStorage.getCurrentRelayUrl()
        if (savedRelayUrl != null) {
            _currentRelayUrl.value = savedRelayUrl
            println("✅ Loaded saved relay URL: $savedRelayUrl")
        }
        
        val savedPrivateKey = SecureStorage.getPrivateKey()
        if (savedPrivateKey != null) {
            try {
                keyPair = KeyPair.fromPrivateKeyHex(savedPrivateKey)
                _isLoggedIn.value = true
                _joinedGroups.value = SecureStorage.getJoinedGroupsForRelay(_currentRelayUrl.value)
                println("✅ Loaded saved credentials and ${_joinedGroups.value.size} joined groups for relay")
                connect()
                connectToMetadataRelay()
            } catch (e: Exception) {
                println("❌ Failed to load saved credentials: ${e.message}")
                SecureStorage.clearPrivateKey()
            }
        }
    }
    
    // NEW: Load kind:10009 from Nostr
    private suspend fun loadJoinedGroupsFromNostr() {
        val pubKey = keyPair?.publicKeyHex ?: return
        val currentClient = metadataClient ?: run {
            println("⚠️ No metadata client available")
            return
        }
        
        try {
            // Reset flags
            kind10009Received = false
            eoseReceived = false
            
            val filter = buildJsonObject {
                putJsonArray("kinds") { add(10009) }
                putJsonArray("authors") { add(pubKey) }
                put("limit", 1)
            }
            
            val subId = "joined-groups-${epochMillis()}"
            kind10009SubId = subId

            val message = buildJsonArray {
                add("REQ")
                add(subId)
                add(filter)
            }.toString()
            
            currentClient.send(message)
            println("📥 Requesting kind:10009 for relay: ${_currentRelayUrl.value}")
            println("   SubId: $subId")
            println("   PubKey: ${pubKey.take(16)}...")
            
            // Wait for EOSE or timeout (5 seconds)
            var waitTime = 0
            while (!eoseReceived && waitTime < 5000) {
                kotlinx.coroutines.delay(500)
                waitTime += 500
            }
            
            // Close the subscription
            val closeMsg = buildJsonArray {
                add("CLOSE")
                add(subId)
            }.toString()
            currentClient.send(closeMsg)
            println("🔒 Closed subscription: $subId")
            
            if (!kind10009Received) {
                println("⚠️ No kind:10009 event found on relay")
                // If we have local joined groups, publish them
                val localGroups = SecureStorage.getJoinedGroupsForRelay(_currentRelayUrl.value)
                if (localGroups.isNotEmpty()) {
                    println("📤 Publishing local joined groups (${localGroups.size} groups) as kind:10009")
                    _joinedGroups.value = localGroups
                    // Initialize allRelayGroups with current relay's local groups
                    allRelayGroups[_currentRelayUrl.value] = localGroups.toMutableSet()
                    publishJoinedGroupsList()
                } else {
                    println("ℹ️ No local joined groups to publish")
                }
            } else {
                println("✅ Successfully loaded kind:10009 with ${_joinedGroups.value.size} groups for current relay")
            }
        } catch (e: Exception) {
            println("❌ Failed to load joined groups: ${e.message}")
        }
    }

    // NEW: Publish kind:10009 to Nostr
    private suspend fun publishJoinedGroupsList() {
        val currentKeyPair = keyPair ?: run {
            println("⚠️ Cannot publish kind:10009 - not logged in")
            return
        }
        val currentClient = metadataClient ?: run {
            println("⚠️ Cannot publish kind:10009 - metadata client not connected")
            return
        }
        
        try {
            // Update allRelayGroups with current relay's groups
            val currentRelayGroups = _joinedGroups.value
            allRelayGroups[_currentRelayUrl.value] = currentRelayGroups.toMutableSet()
            
            // Build tags from ALL relays (merge all groups)
            val tags = mutableListOf<List<String>>()
            allRelayGroups.forEach { (relayUrl, groupIds) ->
                groupIds.forEach { groupId ->
                    tags.add(listOf("group", groupId, relayUrl))
                }
            }
            
            println("🔄 Merging groups from ${allRelayGroups.size} relay(s):")
            allRelayGroups.forEach { (relay, groups) ->
                println("   • $relay: ${groups.size} group(s)")
            }
            
            val event = Event(
                pubkey = currentKeyPair.publicKeyHex,
                createdAt = epochMillis() / 1000,
                kind = 10009,
                tags = tags,
                content = ""
            )
            
            val signedEvent = event.sign(currentKeyPair)
            
            val message = buildJsonArray {
                add("EVENT")
                add(signedEvent.toJsonObject())
            }.toString()
            
            currentClient.send(message)
            val totalGroups = tags.size
            println("📤 Published kind:10009 with $totalGroups total group(s) across ${allRelayGroups.size} relay(s)")
            println("   Current relay (${_currentRelayUrl.value}): ${currentRelayGroups.size} group(s)")
        } catch (e: Exception) {
            println("❌ Failed to publish joined groups: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private suspend fun connectToMetadataRelay() {
        try {
            val relayUrl = metadataRelays[currentMetadataRelayIndex]
            println("🔗 Connecting to metadata relay: $relayUrl")
            
            val newMetadataClient = NostrGroupClient(relayUrl)
            metadataClient = newMetadataClient
            
            newMetadataClient.connect { msg ->
                handleMetadataMessage(msg, newMetadataClient)
            }
            
            newMetadataClient.waitForConnection()
            println("✅ Connected to metadata relay: $relayUrl")

            // Wait for connection to stabilize
            kotlinx.coroutines.delay(1000)
            println("🔄 Loading kind:10009 joined groups...")

            loadJoinedGroupsFromNostr()
        } catch (e: Exception) {
            println("❌ Failed to connect to metadata relay: ${e.message}")
            if (currentMetadataRelayIndex < metadataRelays.size - 1) {
                currentMetadataRelayIndex++
                connectToMetadataRelay()
            }
        }
    } 

    private fun handleMetadataMessage(msg: String, client: NostrGroupClient) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            val arr = json.parseToJsonElement(msg).jsonArray
            
            // Handle EOSE for kind:10009 subscription
            if (arr.size >= 2 && arr[0].jsonPrimitive.content == "EOSE") {
                val subId = arr[1].jsonPrimitive.content
                if (subId == kind10009SubId) {
                    eoseReceived = true
                    println("✅ EOSE received for kind:10009 subscription")
                }
                return
            }
            
            if (arr.size >= 3 && arr[0].jsonPrimitive.content == "EVENT") {
                val event = arr[2].jsonObject
                val kind = event["kind"]?.jsonPrimitive?.int
                
                if (kind == 10009) {
                    kind10009Received = true
                    println("🎯 Received kind:10009 event")
                    val tags = event["tags"]?.jsonArray ?: return
                    
                    // Clear and rebuild allRelayGroups from kind:10009
                    allRelayGroups.clear()
                    val currentRelayGroups = mutableSetOf<String>()
                    
                    tags.forEach { tag ->
                        val tagArray = tag.jsonArray
                        if (tagArray.size >= 2 && tagArray[0].jsonPrimitive.content == "group") {
                            val groupId = tagArray[1].jsonPrimitive.content
                            val relayUrl = tagArray.getOrNull(2)?.jsonPrimitive?.content
                            
                            // Store in allRelayGroups map
                            if (relayUrl != null) {
                                allRelayGroups.getOrPut(relayUrl) { mutableSetOf() }.add(groupId)
                                
                                // Filter only groups from current relay for UI
                                if (relayUrl == _currentRelayUrl.value) {
                                    currentRelayGroups.add(groupId)
                                    println("  ✅ $groupId (${_currentRelayUrl.value})")
                                } else {
                                    println("  📝 $groupId ($relayUrl) - stored for merging")
                                }
                            } else {
                                // No relay specified, include in current relay
                                currentRelayGroups.add(groupId)
                                allRelayGroups.getOrPut(_currentRelayUrl.value) { mutableSetOf() }.add(groupId)
                                println("  ✅ $groupId (no relay specified, using current)")
                            }
                        }
                    }
                    
                    _joinedGroups.value = currentRelayGroups
                    SecureStorage.saveJoinedGroupsForRelay(_currentRelayUrl.value, currentRelayGroups)
                    println("💾 Saved ${currentRelayGroups.size} groups for current relay")
                    println("📊 Total groups across all relays: ${allRelayGroups.values.sumOf { it.size }}")
                    println("📊 Relays in kind:10009: ${allRelayGroups.keys.joinToString(", ")}")
                    return
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error parsing metadata message: ${e.message}")
        }
        
        val userMetadata = client.parseUserMetadata(msg)
        if (userMetadata != null) {
            val (pubkey, metadata) = userMetadata
            _userMetadata.value = _userMetadata.value + (pubkey to metadata)
            println("✅ Loaded metadata for ${metadata.name ?: metadata.displayName ?: pubkey.take(8)}")
        }
    }

    suspend fun connect() {
        connect(_currentRelayUrl.value)
    }
    
    private suspend fun connect(relayUrl: String) {
        if (client != null || isConnecting) {
            println("⚠️ Already connected or connecting")
            return
        }
        
        isConnecting = true
        _connectionState.value = ConnectionState.Connecting
        
        try {
            val newClient = NostrGroupClient(relayUrl)
            client = newClient
            
            newClient.connect { msg ->
                println("📩 Received: $msg")
                handleMessage(msg, newClient)
            }
            
            newClient.waitForConnection()
            _connectionState.value = ConnectionState.Connected
            println("✅ Repository connected to $relayUrl")
            
            keyPair?.let { kp ->
                newClient.sendAuth(kp.privateKeyHex)
            }
            newClient.requestGroups()
            
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            println("❌ Connection failed: ${e.message}")
            client = null
        } finally {
            isConnecting = false
        }
    }

    fun getPublicKey(): String? = keyPair?.publicKeyHex

    fun getPrivateKey(): String? = keyPair?.privateKeyHex

    suspend fun loginSuspend(privKey: String, pubKey: String) {
        keyPair = KeyPair.fromPrivateKeyHex(privKey)
        SecureStorage.savePrivateKey(privKey)
        _isLoggedIn.value = true
        connect()
        connectToMetadataRelay()
    }
    
    suspend fun logout() {
        disconnect()
        metadataClient?.disconnect()
        metadataClient = null
        SecureStorage.clearPrivateKey()
        SecureStorage.clearAllJoinedGroups()
        keyPair = null
        _isLoggedIn.value = false
        _joinedGroups.value = emptySet()
        allRelayGroups.clear()
        println("👋 Logged out")
    }

    suspend fun switchRelay(newRelayUrl: String) {
        println("🔄 Switching to relay: $newRelayUrl")
        
        // Disconnect from current relay
        disconnect()
        
        // Update relay URL
        _currentRelayUrl.value = newRelayUrl
        SecureStorage.saveCurrentRelayUrl(newRelayUrl)
        
        // Load local groups first (fallback)
        _joinedGroups.value = SecureStorage.getJoinedGroupsForRelay(newRelayUrl)
        println("📂 Loaded ${_joinedGroups.value.size} local joined groups")
        
        // Connect to new relay
        connect(newRelayUrl)
        
        // Reset kind:10009 flags
        kind10009Received = false
        eoseReceived = false
        
        // Ensure metadata client is connected
        val currentMetadataClient = metadataClient
        if (currentMetadataClient == null) {
            println("🔄 Metadata client not connected, connecting...")
            connectToMetadataRelay()
            kotlinx.coroutines.delay(2000)
        } else {
            kotlinx.coroutines.delay(1000)
        }
        
        // Reload kind:10009 for new relay
        println("🔄 Loading kind:10009 for new relay...")
        loadJoinedGroupsFromNostr()
    }

    suspend fun requestUserMetadata(pubkeys: Set<String>) {
        val currentMetadataClient = metadataClient
        if (currentMetadataClient == null) {
            println("⚠️ Metadata client not connected, connecting now...")
            connectToMetadataRelay()
            metadataClient?.let {
                it.requestMetadata(pubkeys.toList())
            }
            return
        }
        
        println("📥 Requesting metadata for ${pubkeys.size} users: ${pubkeys.map { it.take(8) }}")
        currentMetadataClient.requestMetadata(pubkeys.toList())
    }
    
    private fun handleMessage(msg: String, client: NostrGroupClient) {
        val groupMetadata = client.parseGroupMetadata(msg)
        if (groupMetadata != null && groupMetadata.name != null) {
            _groups.value = (_groups.value + groupMetadata).distinctBy { it.id }
            return
        }
        
        val userMetadata = client.parseUserMetadata(msg)
        if (userMetadata != null) {
            val (pubkey, metadata) = userMetadata
            _userMetadata.value = _userMetadata.value + (pubkey to metadata)
            println("✅ Loaded metadata from group relay for ${metadata.name ?: metadata.displayName ?: pubkey.take(8)}")
            return
        }
        
        val message = client.parseMessage(msg)
        if (message != null && (message.kind == 9 || message.kind == 9021 || message.kind == 9022)) {
            val groupId = extractGroupIdFromMessage(msg)
            if (groupId != null) {
                val currentMessages = _messages.value[groupId] ?: emptyList()
                _messages.value = _messages.value + (groupId to (currentMessages + message).distinctBy { it.id }.sortedBy { it.createdAt })
                
                if (!_userMetadata.value.containsKey(message.pubkey)) {
                    println("🔍 Requesting metadata for new user: ${message.pubkey.take(8)}")
                    CoroutineScope(Dispatchers.Default).launch {
                        requestUserMetadata(setOf(message.pubkey))
                    }
                }
                
                val eventType = when (message.kind) {
                    9 -> "message"
                    9021 -> "join"
                    9022 -> "leave"
                    else -> "event"
                }
                println("✅ Added $eventType to group $groupId from ${message.pubkey.take(8)}")
            }
        }
    } 

    private fun extractGroupIdFromMessage(msg: String): String? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val arr = json.parseToJsonElement(msg).jsonArray
            if (arr.size < 3) return null
            val event = arr[2].jsonObject
            val tags = event["tags"]?.jsonArray ?: return null
            
            tags.firstOrNull { tag ->
                val tagArray = tag.jsonArray
                tagArray.size >= 2 && tagArray[0].jsonPrimitive.content == "h"
            }?.jsonArray?.get(1)?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun joinGroup(groupId: String) {
        val currentClient = client ?: run {
            println("⚠️ Cannot join group - not connected")
            return
        }
        
        val currentKeyPair = keyPair ?: run {
            println("⚠️ Cannot join group - not logged in")
            return
        }
        
        try {
            val event = Event(
                pubkey = currentKeyPair.publicKeyHex,
                createdAt = epochMillis() / 1000,
                kind = 9021,
                tags = listOf(
                    listOf("h", groupId)
                ),
                content = "/join"
            )

            println(event)
            
            val signedEvent = event.sign(currentKeyPair)
            
            val message = buildJsonArray {
                add("EVENT")
                add(signedEvent.toJsonObject())
            }.toString()
            
            currentClient.send(message)
            
            _joinedGroups.value = _joinedGroups.value + groupId
            SecureStorage.saveJoinedGroupsForRelay(_currentRelayUrl.value, _joinedGroups.value)
            
            // NEW: Publish kind:10009
            publishJoinedGroupsList()
            
            println("✅ Joined group $groupId on relay ${_currentRelayUrl.value}")
            
            requestGroupMessages(groupId)
            
        } catch (e: Exception) {
            println("❌ Failed to join group: ${e.message}")
            e.printStackTrace()
        }
    }
    
    suspend fun leaveGroup(groupId: String, reason: String? = null) {
        val currentClient = client ?: run {
            println("⚠️ Cannot leave group - not connected")
            return
        }
        
        val currentKeyPair = keyPair ?: run {
            println("⚠️ Cannot leave group - not logged in")
            return
        }
        
        try {
            val event = Event(
                pubkey = currentKeyPair.publicKeyHex,
                createdAt = epochMillis() / 1000,
                kind = 9022,
                tags = listOf(
                    listOf("h", groupId)
                ),
                content = reason.orEmpty()
            )

            println(event)
            
            val signedEvent = event.sign(currentKeyPair)
            
            val message = buildJsonArray {
                add("EVENT")
                add(signedEvent.toJsonObject())
            }.toString()
            
            currentClient.send(message)
            
            _joinedGroups.value = _joinedGroups.value - groupId
            SecureStorage.saveJoinedGroupsForRelay(_currentRelayUrl.value, _joinedGroups.value)
            
            // NEW: Publish kind:10009
            publishJoinedGroupsList()
            
            _messages.value = _messages.value - groupId
            
            println("✅ Left group $groupId on relay ${_currentRelayUrl.value}")
            
        } catch (e: Exception) {
            println("❌ Failed to leave group: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun isGroupJoined(groupId: String): Boolean {
        return _joinedGroups.value.contains(groupId)
    }
    
    suspend fun requestGroupMessages(groupId: String, channel: String? = null) {
        val currentClient = client
        if (currentClient == null) {
            println("⚠️ Not connected, connecting first...")
            connect()
            return requestGroupMessages(groupId, channel)
        }
        
        currentClient.requestGroupMessages(groupId, channel)
    }

suspend fun sendMessage(groupId: String, content: String, channel: String? = null) {
    val currentClient = client ?: run {
        println("⚠️ Cannot send message - not connected")
        return
    }
    
    val currentKeyPair = keyPair ?: run {
        println("⚠️ Cannot send message - not logged in")
        return
    }
    
    try {
        val tags = mutableListOf(listOf("h", groupId))
        // Only add channel tag if it's NOT "general"
        if (channel != null && channel != "general") {
            tags.add(listOf("channel", channel))
        }
        
        val event = Event(
            pubkey = currentKeyPair.publicKeyHex,
            createdAt = epochMillis() / 1000,
            kind = 9,
            tags = tags,
            content = content
        )
        
        val signedEvent = event.sign(currentKeyPair)
        
        val eventJson = signedEvent.toJsonObject()
        val message = buildJsonArray {
            add("EVENT")
            add(eventJson)
        }.toString()
        
        currentClient.send(message)
        println("📤 Sent message to group $groupId${if (channel != null && channel != "general") " in channel $channel" else " (general)"}: $content")
        
    } catch (e: Exception) {
        println("❌ Failed to send message: ${e.message}")
        e.printStackTrace()
    }
}

    fun getMessagesForGroup(groupId: String): List<NostrGroupClient.NostrMessage> {
        return _messages.value[groupId] ?: emptyList()
    }
    
    suspend fun disconnect() {
        client?.disconnect()
        client = null
        _connectionState.value = ConnectionState.Disconnected
        _groups.value = emptyList()
        _messages.value = emptyMap()
        isConnecting = false
    }
}
