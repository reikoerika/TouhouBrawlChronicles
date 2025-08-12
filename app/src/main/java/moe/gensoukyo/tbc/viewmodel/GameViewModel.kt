package moe.gensoukyo.tbc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.GameRoom
import moe.gensoukyo.tbc.shared.model.Player

class GameViewModel : ViewModel() {
    private val client = HttpClient(Android) {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }
    
    private var webSocketSession: WebSocketSession? = null
    
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    fun connectToServer(serverUrl: String = "ws://10.0.2.2:8080/game") {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                webSocketSession = client.webSocketSession {
                    url(serverUrl)
                }
                
                _connectionState.value = ConnectionState.CONNECTED
                
                // 监听服务器消息
                webSocketSession?.let { session ->
                    for (frame in session.incoming) {
                        if (frame is Frame.Text) {
                            val message = Json.decodeFromString<ServerMessage>(frame.readText())
                            handleServerMessage(message)
                        }
                    }
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                _uiState.value = _uiState.value.copy(errorMessage = "连接失败: ${e.message}")
            }
        }
    }
    
    fun createRoom(roomName: String, playerName: String) {
        sendMessage(ClientMessage.CreateRoom(roomName, playerName))
    }
    
    fun joinRoom(roomId: String, playerName: String) {
        sendMessage(ClientMessage.JoinRoom(roomId, playerName))
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
    val errorMessage: String? = null
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}