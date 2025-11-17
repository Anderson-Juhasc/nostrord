package org.nostr.nostrord.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import org.nostr.nostrord.nostr.Event
import org.nostr.nostrord.utils.epochMillis

object JoinedGroupsManager {
    private val _joinedGroups = MutableStateFlow<Set<String>>(emptySet())
    val joinedGroups: StateFlow<Set<String>> = _joinedGroups.asStateFlow()

    suspend fun loadJoinedGroups(pubKey: String, client: NostrGroupClient?) {
        if (client == null) {
            println("⚠️ Cannot load kind:10009 - no client")
            return
        }
        
        try {
            val filter = buildJsonObject {
                putJsonArray("kinds") { add(10009) }
                putJsonArray("authors") { add(pubKey) }
                put("limit", 1)
            }
            
            val subId = "joined-groups-${epochMillis()}"
            val message = buildJsonArray {
                add("REQ")
                add(subId)
                add(filter)
            }.toString()
            
            client.send(message)
            println("📥 Requesting kind:10009 joined groups")
        } catch (e: Exception) {
            println("❌ Error loading joined groups: ${e.message}")
        }
    }

    fun parseJoinedGroupsEvent(event: JsonObject) {
        try {
            val tags = event["tags"]?.jsonArray ?: return
            val groups = mutableSetOf<String>()

            tags.forEach { tag ->
                val tagArray = tag.jsonArray
                if (tagArray.size >= 2 && tagArray[0].jsonPrimitive.content == "group") {
                    val groupId = tagArray[1].jsonPrimitive.content
                    groups.add(groupId)
                }
            }

            _joinedGroups.value = groups
            println("✅ Loaded ${groups.size} groups from kind:10009")
        } catch (e: Exception) {
            println("❌ Error parsing joined groups: ${e.message}")
        }
    }

    suspend fun publishJoinedGroups(
        groups: Set<String>,
        keyPair: org.nostr.nostrord.nostr.KeyPair?,
        client: NostrGroupClient?,
        relayUrl: String
    ) {
        if (keyPair == null || client == null) {
            println("⚠️ Cannot publish kind:10009 - not logged in or no client")
            return
        }
        
        try {
            val tags = groups.map { groupId ->
                listOf("group", groupId, relayUrl)
            }

            val event = Event(
                pubkey = keyPair.publicKeyHex,
                createdAt = epochMillis() / 1000,
                kind = 10009,
                tags = tags,
                content = ""
            )

            val signedEvent = event.sign(keyPair)
            
            val message = buildJsonArray {
                add("EVENT")
                add(signedEvent.toJsonObject())
            }.toString()

            client.send(message)
            _joinedGroups.value = groups
            println("📤 Published kind:10009 with ${groups.size} groups")
        } catch (e: Exception) {
            println("❌ Error publishing joined groups: ${e.message}")
        }
    }

    fun clear() {
        _joinedGroups.value = emptySet()
    }
}
