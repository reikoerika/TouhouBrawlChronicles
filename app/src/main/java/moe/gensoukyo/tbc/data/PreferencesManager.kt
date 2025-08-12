package moe.gensoukyo.tbc.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "touhou_brawl_chronicles_prefs",
        Context.MODE_PRIVATE
    )
    
    private val _serverUrl = MutableStateFlow(getServerUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    
    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "ws://10.0.2.2:8080/game"
        
        private var INSTANCE: PreferencesManager? = null
        
        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
    
    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        _serverUrl.value = url
    }
    
    fun getPlayerName(): String {
        return prefs.getString("player_name", "") ?: ""
    }
    
    fun savePlayerName(name: String) {
        prefs.edit().putString("player_name", name).apply()
    }
    
    fun getLastRoomId(): String {
        return prefs.getString("last_room_id", "") ?: ""
    }
    
    fun saveLastRoomId(roomId: String) {
        prefs.edit().putString("last_room_id", roomId).apply()
    }
}