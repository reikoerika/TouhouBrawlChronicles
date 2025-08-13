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
    
    fun respondToCard(responseCardId: String?, accept: Boolean = false) {
        _uiState.value.currentPlayer?.let { player ->
            // 如果接受响应且有响应卡牌，立即从本地手牌中移除
            if (accept && responseCardId != null) {
                val cardIndex = player.cards.indexOfFirst { it.id == responseCardId }
                if (cardIndex != -1) {
                    val updatedCards = player.cards.toMutableList().apply {
                        removeAt(cardIndex)
                    }
                    val updatedPlayer = player.copy(cards = updatedCards)
                    
                    // 更新本地状态
                    _uiState.value = _uiState.value.copy(
                        currentPlayer = updatedPlayer
                    )
                }
            }
            
            // 发送响应消息到服务器
            sendMessage(ClientMessage.RespondToCard(player.id, responseCardId, accept))
        }
    }
    
    fun selectAbundantHarvestCard(selectedCardId: String) {
        _uiState.value.currentPlayer?.let { player ->
            sendMessage(ClientMessage.SelectAbundantHarvestCard(player.id, selectedCardId))
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
                    errorMessage = "游戏开始！开始分发初始手牌..."
                )
            }
            
            is ServerMessage.InitialCardsDealt -> {
                val currentState = _uiState.value
                if (currentState.currentPlayer?.id == message.playerId) {
                    // 更新当前玩家的手牌
                    val updatedPlayer = currentState.currentPlayer.copy(
                        cards = message.cards.toMutableList()
                    )
                    val cardCountMessage = when (updatedPlayer.identity) {
                        moe.gensoukyo.tbc.shared.model.Identity.LORD -> "主公获得初始手牌 ${message.cards.size} 张！"
                        else -> "获得初始手牌 ${message.cards.size} 张！"
                    }
                    _uiState.value = currentState.copy(
                        currentPlayer = updatedPlayer,
                        errorMessage = cardCountMessage
                    )
                }
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
            
            is ServerMessage.CardResponseRequired -> {
                val currentState = _uiState.value
                if (currentState.currentPlayer?.id == message.targetPlayerId) {
                    // 当前玩家需要响应
                    val responseMessage = when (message.responseType) {
                        moe.gensoukyo.tbc.shared.model.ResponseType.DODGE -> "需要出闪响应${message.originalPlayer}的${message.originalCard.name}"
                        moe.gensoukyo.tbc.shared.model.ResponseType.NULLIFICATION -> "是否使用无懈可击响应${message.originalCard.name}"
                        moe.gensoukyo.tbc.shared.model.ResponseType.DUEL_KILL -> {
                            val killCount = currentState.gameRoom?.pendingResponse?.duelKillCount ?: 0
                            "决斗第${killCount + 1}回合：需要出杀响应${message.originalPlayer}"
                        }
                        moe.gensoukyo.tbc.shared.model.ResponseType.ABUNDANT_HARVEST -> "请选择一张卡牌"
                        moe.gensoukyo.tbc.shared.model.ResponseType.OPTIONAL -> "是否响应${message.originalCard.name}"
                    }
                    
                    _uiState.value = currentState.copy(
                        needsResponse = true,
                        errorMessage = responseMessage,
                        responseTimeoutMs = message.timeoutMs,
                        responseType = message.responseType
                    )
                } else {
                    // 其他玩家收到响应通知
                    _uiState.value = currentState.copy(
                        errorMessage = "等待玩家响应${message.originalCard.name}..."
                    )
                }
            }
            
            is ServerMessage.CardResponseReceived -> {
                val currentState = _uiState.value
                
                // 更新当前玩家信息（从服务器返回的房间状态中获取）
                val updatedCurrentPlayer = message.room.players.find { it.id == currentState.currentPlayer?.id }
                
                // 如果这是决斗中间步骤，保持响应状态（等待下一轮）
                if (message.room.pendingResponse != null && !message.room.pendingResponse!!.duelFinished) {
                    _uiState.value = currentState.copy(
                        gameRoom = message.room,
                        currentPlayer = updatedCurrentPlayer ?: currentState.currentPlayer,
                        needsResponse = currentState.currentPlayer?.id == message.room.pendingResponse!!.duelCurrentPlayer,
                        responseType = if (currentState.currentPlayer?.id == message.room.pendingResponse!!.duelCurrentPlayer) {
                            message.room.pendingResponse!!.responseType
                        } else null,
                        errorMessage = if (message.accepted) {
                            "玩家${message.room.players.find { it.id == message.playerId }?.name}出了杀"
                        } else {
                            "玩家${message.room.players.find { it.id == message.playerId }?.name}选择不出杀"
                        }
                    )
                } else {
                    // 普通响应或决斗已结束
                    _uiState.value = currentState.copy(
                        gameRoom = message.room,
                        currentPlayer = updatedCurrentPlayer ?: currentState.currentPlayer,
                        needsResponse = false,
                        responseType = null,
                        errorMessage = if (message.accepted) {
                            "玩家${message.room.players.find { it.id == message.playerId }?.name}成功响应"
                        } else {
                            "玩家${message.room.players.find { it.id == message.playerId }?.name}选择不响应"
                        }
                    )
                }
            }
            
            is ServerMessage.CardResolved -> {
                val currentState = _uiState.value
                
                // 更新当前玩家信息（从服务器返回的房间状态中获取）
                val updatedCurrentPlayer = message.room.players.find { it.id == currentState.currentPlayer?.id }
                
                _uiState.value = currentState.copy(
                    gameRoom = message.room,
                    currentPlayer = updatedCurrentPlayer ?: currentState.currentPlayer,
                    needsResponse = false,
                    responseType = null,
                    errorMessage = message.result.message
                )
            }
            
            is ServerMessage.AbundantHarvestStarted -> {
                val currentState = _uiState.value
                _uiState.value = currentState.copy(
                    gameRoom = message.room,
                    isAbundantHarvestActive = true,
                    abundantHarvestAvailableCards = message.availableCards,
                    abundantHarvestCurrentPlayerIndex = message.currentPlayerIndex,
                    errorMessage = "五谷丰登开始！等待玩家选择卡牌..."
                )
            }
            
            is ServerMessage.AbundantHarvestSelection -> {
                val currentState = _uiState.value
                if (currentState.currentPlayer?.id == message.playerId) {
                    // 当前玩家需要选择卡牌
                    _uiState.value = currentState.copy(
                        gameRoom = message.room,
                        abundantHarvestAvailableCards = message.availableCards,
                        needsResponse = true,
                        responseType = moe.gensoukyo.tbc.shared.model.ResponseType.ABUNDANT_HARVEST,
                        errorMessage = "轮到你选择卡牌了！"
                    )
                } else {
                    // 其他玩家等待
                    _uiState.value = currentState.copy(
                        gameRoom = message.room,
                        abundantHarvestAvailableCards = message.availableCards,
                        errorMessage = "等待${message.playerName}选择卡牌..."
                    )
                }
            }
            
            is ServerMessage.AbundantHarvestCardSelected -> {
                val currentState = _uiState.value
                
                // 如果是当前玩家选择了卡牌，需要将卡牌添加到手牌中
                val updatedCurrentPlayer = if (currentState.currentPlayer?.id == message.playerId) {
                    val updatedCards = currentState.currentPlayer.cards.toMutableList().apply {
                        add(message.selectedCard)
                    }
                    currentState.currentPlayer.copy(cards = updatedCards)
                } else {
                    currentState.currentPlayer
                }
                
                _uiState.value = currentState.copy(
                    gameRoom = message.room,
                    currentPlayer = updatedCurrentPlayer,
                    abundantHarvestAvailableCards = message.remainingCards,
                    needsResponse = false,
                    responseType = null,
                    errorMessage = "${message.playerName}选择了${message.selectedCard.name}"
                )
            }
            
            is ServerMessage.AbundantHarvestCompleted -> {
                val currentState = _uiState.value
                
                // 更新当前玩家信息（从服务器返回的房间状态中获取）
                val updatedCurrentPlayer = message.room.players.find { it.id == currentState.currentPlayer?.id }
                
                _uiState.value = currentState.copy(
                    gameRoom = message.room,
                    currentPlayer = updatedCurrentPlayer ?: currentState.currentPlayer,
                    isAbundantHarvestActive = false,
                    abundantHarvestAvailableCards = emptyList(),
                    abundantHarvestCurrentPlayerIndex = 0,
                    needsResponse = false,
                    responseType = null,
                    errorMessage = "五谷丰登完成！所有玩家都已选择卡牌"
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
    val errorMessage: String? = null,
    val pendingResponse: moe.gensoukyo.tbc.shared.model.PendingResponse? = null,
    val needsResponse: Boolean = false,
    val responseTimeoutMs: Long = 15000,
    val responseType: moe.gensoukyo.tbc.shared.model.ResponseType? = null,
    // 五谷丰登相关状态
    val abundantHarvestAvailableCards: List<moe.gensoukyo.tbc.shared.model.Card> = emptyList(),
    val isAbundantHarvestActive: Boolean = false,
    val abundantHarvestCurrentPlayerIndex: Int = 0
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}