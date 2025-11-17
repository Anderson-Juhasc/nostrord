package org.nostr.nostrord.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

expect object SecureStorage {
    fun savePrivateKey(privateKeyHex: String)
    fun getPrivateKey(): String?
    fun hasPrivateKey(): Boolean
    fun clearPrivateKey()
    
    fun saveCurrentRelayUrl(relayUrl: String)
    fun getCurrentRelayUrl(): String?
    fun clearCurrentRelayUrl()
    
    fun saveJoinedGroupsForRelay(relayUrl: String, groupIds: Set<String>)
    fun getJoinedGroupsForRelay(relayUrl: String): Set<String>
    fun clearJoinedGroupsForRelay(relayUrl: String)
    fun clearAllJoinedGroups()
    
    fun clearAll()
}

// Legacy support functions can stay in common
suspend fun SecureStorage.saveJoinedGroups(groups: Set<String>) {
    saveJoinedGroupsForRelay("legacy", groups)
}

suspend fun SecureStorage.getJoinedGroups(): Set<String> {
    return getJoinedGroupsForRelay("legacy")
}

suspend fun SecureStorage.clearJoinedGroups() {
    clearJoinedGroupsForRelay("legacy")
}
