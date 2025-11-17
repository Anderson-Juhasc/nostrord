package org.nostr.nostrord.storage

import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

actual object SecureStorage {
    private const val PRIVATE_KEY_PREF = "nostr_private_key"
    private const val JOINED_GROUPS_PREFIX = "joined_groups_"
    private const val CURRENT_RELAY_URL = "current_relay_url"
    
    actual fun savePrivateKey(privateKeyHex: String) {
        localStorage.setItem(PRIVATE_KEY_PREF, privateKeyHex)
        println("🔐 Private key saved to localStorage")
    }
    
    actual fun getPrivateKey(): String? {
        return localStorage.getItem(PRIVATE_KEY_PREF)
    }
    
    actual fun hasPrivateKey(): Boolean {
        return localStorage.getItem(PRIVATE_KEY_PREF) != null
    }
    
    actual fun clearPrivateKey() {
        localStorage.removeItem(PRIVATE_KEY_PREF)
        println("🗑️ Private key cleared")
    }
    
    actual fun saveCurrentRelayUrl(relayUrl: String) {
        localStorage.setItem(CURRENT_RELAY_URL, relayUrl)
        println("💾 Saved current relay URL: $relayUrl")
    }
    
    actual fun getCurrentRelayUrl(): String? {
        return localStorage.getItem(CURRENT_RELAY_URL)
    }
    
    actual fun clearCurrentRelayUrl() {
        localStorage.removeItem(CURRENT_RELAY_URL)
        println("🗑️ Cleared current relay URL")
    }
    
    actual fun saveJoinedGroupsForRelay(relayUrl: String, groupIds: Set<String>) {
        val key = JOINED_GROUPS_PREFIX + relayUrl.hashCode()
        val json = Json.encodeToString(groupIds.toList())
        localStorage.setItem(key, json)
        println("💾 Saved ${groupIds.size} joined groups for relay: $relayUrl")
    }
    
    actual fun getJoinedGroupsForRelay(relayUrl: String): Set<String> {
        val key = JOINED_GROUPS_PREFIX + relayUrl.hashCode()
        val json = localStorage.getItem(key) ?: return emptySet()
        return try {
            Json.decodeFromString<List<String>>(json).toSet()
        } catch (e: Exception) {
            println("❌ Failed to parse joined groups for relay: ${e.message}")
            emptySet()
        }
    }
    
    actual fun clearJoinedGroupsForRelay(relayUrl: String) {
        val key = JOINED_GROUPS_PREFIX + relayUrl.hashCode()
        localStorage.removeItem(key)
        println("🗑️ Cleared joined groups for relay: $relayUrl")
    }
    
    actual fun clearAllJoinedGroups() {
        try {
            val keysToRemove = mutableListOf<String>()
            for (i in 0 until localStorage.length) {
                val key = localStorage.key(i)
                if (key != null && key.startsWith(JOINED_GROUPS_PREFIX)) {
                    keysToRemove.add(key)
                }
            }
            keysToRemove.forEach { localStorage.removeItem(it) }
            println("🗑️ Cleared all relay-specific joined groups")
        } catch (e: Exception) {
            println("❌ Failed to clear all joined groups: ${e.message}")
        }
    }
    
    actual fun clearAll() {
        localStorage.clear()
        println("🗑️ All secure storage cleared")
    }
}
