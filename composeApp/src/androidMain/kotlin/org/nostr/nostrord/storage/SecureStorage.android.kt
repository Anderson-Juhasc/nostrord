package org.nostr.nostrord.storage

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@SuppressLint("StaticFieldLeak")
actual object SecureStorage {
    private const val PREFS_NAME = "nostr_secure_prefs"
    private const val PRIVATE_KEY_PREF = "nostr_private_key"
    private const val JOINED_GROUPS_PREFIX = "joined_groups_"
    private const val CURRENT_RELAY_URL = "current_relay_url"
    
    private var appContext: Context? = null
    
    private val prefs: SharedPreferences
        get() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("SecureStorage not initialized. Call initialize(context) first.")
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        println("🔐 SecureStorage initialized")
    }
    
    actual fun savePrivateKey(privateKeyHex: String) {
        prefs.edit().putString(PRIVATE_KEY_PREF, privateKeyHex).apply()
        println("🔐 Private key saved")
    }
    
    actual fun getPrivateKey(): String? {
        return prefs.getString(PRIVATE_KEY_PREF, null)
    }
    
    actual fun hasPrivateKey(): Boolean {
        return prefs.contains(PRIVATE_KEY_PREF)
    }
    
    actual fun clearPrivateKey() {
        prefs.edit().remove(PRIVATE_KEY_PREF).apply()
        println("🗑️ Private key cleared")
    }
    
    actual fun saveCurrentRelayUrl(relayUrl: String) {
        prefs.edit().putString(CURRENT_RELAY_URL, relayUrl).apply()
        println("💾 Saved current relay URL: $relayUrl")
    }
    
    actual fun getCurrentRelayUrl(): String? {
        return prefs.getString(CURRENT_RELAY_URL, null)
    }
    
    actual fun clearCurrentRelayUrl() {
        prefs.edit().remove(CURRENT_RELAY_URL).apply()
        println("🗑️ Cleared current relay URL")
    }
    
    actual fun saveJoinedGroupsForRelay(relayUrl: String, groupIds: Set<String>) {
        val key = JOINED_GROUPS_PREFIX + relayUrl.hashCode()
        val json = Json.encodeToString(groupIds.toList())
        prefs.edit().putString(key, json).apply()
        println("💾 Saved ${groupIds.size} joined groups for relay: $relayUrl")
    }
    
    actual fun getJoinedGroupsForRelay(relayUrl: String): Set<String> {
        val key = JOINED_GROUPS_PREFIX + relayUrl.hashCode()
        val json = prefs.getString(key, null) ?: return emptySet()
        return try {
            Json.decodeFromString<List<String>>(json).toSet()
        } catch (e: Exception) {
            println("❌ Failed to parse joined groups: ${e.message}")
            emptySet()
        }
    }
    
    actual fun clearJoinedGroupsForRelay(relayUrl: String) {
        val key = JOINED_GROUPS_PREFIX + relayUrl.hashCode()
        prefs.edit().remove(key).apply()
        println("🗑️ Cleared joined groups for relay: $relayUrl")
    }
    
    actual fun clearAllJoinedGroups() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(JOINED_GROUPS_PREFIX) }.forEach {
            editor.remove(it)
        }
        editor.apply()
        println("🗑️ Cleared all joined groups")
    }
    
    actual fun clearAll() {
        prefs.edit().clear().apply()
        println("🗑️ All secure storage cleared")
    }
}
