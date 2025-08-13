package moe.gensoukyo.tbc.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.data.PreferencesManager
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.model.GameRoom
import moe.gensoukyo.tbc.shared.model.Player
import moe.gensoukyo.tbc.shared.model.Spectator

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager.getInstance(application)
    
    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 20_000
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    private var webSocketSession: WebSocketSession? = null
    
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _roomList = MutableStateFlow<List<GameRoom>>(emptyList())
    val roomList: StateFlow<List<GameRoom>> = _roomList.asStateFlow()
    
    // 暴露存储的服务器地址
    val storedServerUrl: StateFlow<String> = preferencesManager.serverUrl
    
    fun connectToServer(serverUrl: String? = null) {
        val urlToUse = serverUrl ?: preferencesManager.getServerUrl()
        
        // 保存服务器地址
        if (serverUrl != null) {
            preferencesManager.saveServerUrl(serverUrl)
        }
        
        viewModelScope.launch {
            try {
                // 先关闭现有连接
                webSocketSession?.close()
                
                _connectionState.value = ConnectionState.CONNECTING
                _uiState.value = _uiState.value.copy(errorMessage = null)
                
                webSocketSession = client.webSocketSession(urlString = urlToUse)
                
                _connectionState.value = ConnectionState.CONNECTED
                
                // 监听服务器消息
                webSocketSession?.let { session ->
                    try {
                        for (frame in session.incoming) {
                            if (frame is Frame.Text) {
                                val message = Json.decodeFromString<ServerMessage>(frame.readText())
                                handleServerMessage(message)
                            }
                        }
                    } catch (e: Exception) {
                        _connectionState.value = ConnectionState.ERROR
                        _uiState.value = _uiState.value.copy(errorMessage = "连接中断: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                _uiState.value = _uiState.value.copy(errorMessage = "连接失败: ${e.message}")
            }
        }
    }
    
    fun createRoom(roomName: String, playerName: String) {
        // 保存玩家名称
        preferencesManager.savePlayerName(playerName)
        sendMessage(ClientMessage.CreateRoom(roomName, playerName))
    }
    
    fun joinRoom(roomId: String, playerName: String) {
        // 保存玩家名称和房间ID
        preferencesManager.savePlayerName(playerName)
        preferencesManager.saveLastRoomId(roomId)
        sendMessage(ClientMessage.JoinRoom(roomId, playerName, spectateOnly = false))
    }
    
    fun spectateRoom(roomId: String, playerName: String) {
        // 以观战者身份加入房间
        preferencesManager.savePlayerName(playerName)
        preferencesManager.saveLastRoomId(roomId)
        sendMessage(ClientMessage.JoinRoom(roomId, playerName, spectateOnly = true))
    }
    
    fun drawCard() {
        _uiState.value.currentPlayer?.let { player ->
            sendMessage(ClientMessage.DrawCard(player.id))
        }
    }
    
    fun addHealth(amount: Int) {
        _uiState.value.currentPlayer?.let { player ->
            sendMessage(ClientMessage.HealthChange(player.id, amount))
        }
    }
    
    fun reduceHealth(amount: Int) {
        _uiState.value.currentPlayer?.let { player ->
            sendMessage(ClientMessage.HealthChange(player.id, -amount))
        }
    }
    
    fun useCard(cardId: String, targetId: String? = null) {
        _uiState.value.currentPlayer?.let { player ->
            sendMessage(ClientMessage.UseCard(player.id, cardId, targetId))
        }
    }
    
    fun getStoredPlayerName(): String = preferencesManager.getPlayerName()
    fun getStoredRoomId(): String = preferencesManager.getLastRoomId()
    
    fun getRoomList() {
        sendMessage(ClientMessage.GetRoomList)
    }
    
    fun joinRoomById(roomId: String, playerName: String) {
        joinRoom(roomId, playerName)
    }
    
    fun startGame(roomId: String) {
        sendMessage(ClientMessage.StartGame(roomId))
    }
    
    fun endTurn(playerId: String) {
        sendMessage(ClientMessage.EndTurn(playerId))
    }
    
    fun adjustPlayerOrder(roomId: String, newOrder: List<String>) {
        sendMessage(ClientMessage.AdjustPlayerOrder(roomId, newOrder))
    }
    
    fun playCard(cardId: String, targetIds: List<String> = emptyList()) {
        _uiState.value.currentPlayer?.let { player ->
            sendMessage(ClientMessage.PlayCard(player.id, cardId, targetIds))
        }
    }
    
    private fun sendMessage(message: ClientMessage) {
        viewModelScope.launch {
            try {
                webSocketSession?.send(Json.encodeToString(message))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "发送消息失败: ${e.message}")
            }
        }
    }
    
    private fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.RoomCreated -> {
                _uiState.value = _uiState.value.copy(
                    gameRoom = message.room,
                    currentPlayer = message.room.players.first(),
                    errorMessage = null
                )
            }
            
            is ServerMessage.PlayerJoined -> {
                _uiState.value = _uiState.value.copy(
                    gameRoom = message.room,
                    currentPlayer = message.player,
                    currentSpectator = null,
                    isSpectating = false,
                    errorMessage = null
                )
            }
            
            is ServerMessage.SpectatorJoined -> {
                _uiState.value = _uiState.value.copy(
                    gameRoom = message.room,
                    currentPlayer = null,
                    currentSpectator = message.spectator,
                    isSpectating = true,
                    errorMessage = null
                )
            }
            
            is ServerMessage.GameStateUpdate -> {
                _uiState.value = _uiState.value.copy(
                    gameRoom = message.room,
                    errorMessage = null
                )
            }
            
            is ServerMessage.CardDrawn -> {
                val currentState = _uiState.value
                val updatedPlayer = currentState.currentPlayer?.copy(
                    cards = currentState.currentPlayer.cards.toMutableList().apply { 
                        add(message.card) 
                    }
                )
                _uiState.value = currentState.copy(
                    currentPlayer = updatedPlayer,
                    errorMessage = null
                )
            }
            
            is ServerMessage.CardsDrawn -> {
                val currentState = _uiState.value
                val updatedPlayer = currentState.currentPlayer?.copy(
                    cards = currentState.currentPlayer.cards.toMutableList().apply { 
                        addAll(message.cards)
                    }
                )
                
                if (updatedPlayer != null) {
                    _uiState.value = currentState.copy(
                        currentPlayer = updatedPlayer,
                        errorMessage = "${updatedPlayer.name} 摸了${message.cards.size}张牌"
                    )
                }
            }
            
            is ServerMessage.HealthUpdated -> {
                val currentState = _uiState.value
                val updatedRoom = currentState.gameRoom?.let { room ->
                    room.copy(
                        players = room.players.map { player ->
                            if (player.id == message.playerId) {
                                player.copy(health = message.newHealth)
                            } else {
                                player
                            }
                        }.toMutableList()
                    )
                }
                _uiState.value = currentState.copy(
                    gameRoom = updatedRoom,
                    errorMessage = null
                )
            }
            
            is ServerMessage.RoomList -> {
                _roomList.value = message.rooms
            }
            
            is ServerMessage.GameStarted -> {
                _uiState.value = _uiState.value.copy(
                    gameRoom = message.room,
                    errorMessage = null
                )
            }
            
            is ServerMessage.TurnStarted -> {
                _uiState.value = _uiState.value.copy(errorMessage = "轮到 ${message.playerId} 行动")
            }
            
            is ServerMessage.PlayerOrderChanged -> {
                _uiState.value = _uiState.value.copy(
                    gameRoom = message.room,
                    errorMessage = null
                )
            }
            
            is ServerMessage.CardPlayed -> {
                val currentState = _uiState.value
                val updatedRoom = message.room
                
                // 更新当前玩家信息（如果是当前玩家出的牌）
                val updatedCurrentPlayer = if (currentState.currentPlayer?.id == message.playedCard.playerId) {
                    updatedRoom.players.find { it.id == currentState.currentPlayer.id }
                } else {
                    currentState.currentPlayer
                }
                
                _uiState.value = currentState.copy(
                    gameRoom = updatedRoom,
                    currentPlayer = updatedCurrentPlayer,
                    errorMessage = "${message.playedCard.playerName} 出了 ${message.playedCard.card.name}"
                )
            }
            
            is ServerMessage.Error -> {
                _uiState.value = _uiState.value.copy(errorMessage = message.message)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            webSocketSession?.close()
            client.close()
        }
    }
}

data class GameUiState(
    val gameRoom: GameRoom? = null,
    val currentPlayer: Player? = null,
    val currentSpectator: Spectator? = null,
    val isSpectating: Boolean = false,
    val errorMessage: String? = null
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}