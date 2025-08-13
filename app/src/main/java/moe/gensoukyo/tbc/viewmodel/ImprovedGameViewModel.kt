package moe.gensoukyo.tbc.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import moe.gensoukyo.tbc.data.PreferencesManager
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.model.*
import moe.gensoukyo.tbc.shared.utils.*

// 为Android客户端创建简化的连接管理器
class AndroidWebSocketManager(
    private val client: HttpClient,
    private val serverUrl: String
) {
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
    
    data class ConnectionInfo(
        val state: ConnectionState,
        val messagesSent: Long = 0,
        val messagesReceived: Long = 0,
        val reconnectAttempts: Long = 0,
        val lastError: String? = null
    )
    
    private val _connectionState = MutableStateFlow(ConnectionInfo(ConnectionState.DISCONNECTED))
    val connectionState: StateFlow<ConnectionInfo> = _connectionState.asStateFlow()
    
    private val _incomingMessages = MutableStateFlow<ServerMessage?>(null)
    val incomingMessages: StateFlow<ServerMessage?> = _incomingMessages.asStateFlow()
    
    suspend fun connect(): Boolean {
        _connectionState.value = _connectionState.value.copy(state = ConnectionState.CONNECTING)
        // 简化实现 - 实际项目中需要真正的WebSocket连接
        _connectionState.value = _connectionState.value.copy(state = ConnectionState.CONNECTED)
        return true
    }
    
    suspend fun disconnect() {
        _connectionState.value = _connectionState.value.copy(state = ConnectionState.DISCONNECTED)
    }
    
    suspend fun sendMessage(message: ClientMessage): Boolean {
        val current = _connectionState.value
        _connectionState.value = current.copy(messagesSent = current.messagesSent + 1)
        return true
    }
    
    fun getConnectionStats(): ConnectionInfo {
        return _connectionState.value
    }
}

/**
 * 重构后的GameViewModel
 * 使用新的连接管理和消息处理系统
 */
class ImprovedGameViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager.getInstance(application)
    
    private val client = HttpClient(OkHttp) {
        install(WebSockets)
        install(ContentNegotiation) {
            json()
        }
    }
    
    // WebSocket连接管理器
    private val webSocketManager = AndroidWebSocketManager(
        client = client,
        serverUrl = "ws://192.168.1.100:8080/game" // 可配置的服务器地址
    )
    
    // 消息处理器
    private val messageHandler = GameMessageHandler()
    
    data class GameUiState(
        val isConnected: Boolean = false,
        val connectionInfo: AndroidWebSocketManager.ConnectionInfo = 
            AndroidWebSocketManager.ConnectionInfo(AndroidWebSocketManager.ConnectionState.DISCONNECTED),
        val gameRoom: GameRoom? = null,
        val currentPlayer: Player? = null,
        val availableRooms: List<GameRoom> = emptyList(),
        val needsResponse: Boolean = false,
        val responseType: ResponseType? = null,
        val errorMessage: String? = null,
        val isLoading: Boolean = false,
        val debugInfo: String = ""
    )
    
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    
    init {
        Logger.setLevel(Logger.Level.DEBUG)
        startObservingConnection()
        startObservingMessages()
    }
    
    /**
     * 开始观察连接状态
     */
    private fun startObservingConnection() {
        viewModelScope.launch {
            webSocketManager.connectionState.collect { connectionInfo ->
                logInfo("Connection state changed: ${connectionInfo.state}")
                
                _uiState.value = _uiState.value.copy(
                    isConnected = connectionInfo.state == AndroidWebSocketManager.ConnectionState.CONNECTED,
                    connectionInfo = connectionInfo,
                    errorMessage = connectionInfo.lastError
                )
                
                // 更新调试信息
                updateDebugInfo()
            }
        }
    }
    
    /**
     * 开始观察收到的消息
     */
    private fun startObservingMessages() {
        viewModelScope.launch {
            webSocketManager.incomingMessages
                .filterNotNull()
                .collect { message ->
                    logDebug("Processing incoming message: ${message::class.simpleName}")
                    messageHandler.handleMessage(message)
                }
        }
    }
    
    /**
     * 连接到服务器
     */
    fun connect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val success = webSocketManager.connect()
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = if (success) null else "连接失败"
            )
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        viewModelScope.launch {
            webSocketManager.disconnect()
        }
    }
    
    /**
     * 创建房间
     */
    fun createRoom(roomName: String, playerName: String) {
        sendMessage(ClientMessage.CreateRoom(roomName, playerName))
    }
    
    /**
     * 加入房间
     */
    fun joinRoom(roomId: String, playerName: String, spectateOnly: Boolean = false) {
        sendMessage(ClientMessage.JoinRoom(roomId, playerName, spectateOnly))
    }
    
    /**
     * 获取房间列表
     */
    fun getRoomList() {
        sendMessage(ClientMessage.GetRoomList)
    }
    
    /**
     * 出牌（使用新系统）
     */
    fun playCard(cardId: String, targetIds: List<String> = emptyList()) {
        val currentPlayer = _uiState.value.currentPlayer
        if (currentPlayer == null) {
            logWarn("Cannot play card: no current player")
            return
        }
        
        sendMessage(ClientMessage.PlayCard(currentPlayer.id, cardId, targetIds))
    }
    
    /**
     * 响应卡牌（使用新系统）
     */
    fun respondToCard(responseCardId: String?, accept: Boolean = false) {
        val currentPlayer = _uiState.value.currentPlayer
        if (currentPlayer == null) {
            logWarn("Cannot respond: no current player")
            return
        }
        
        sendMessage(ClientMessage.RespondToCard(currentPlayer.id, responseCardId, accept))
    }
    
    /**
     * 安全地发送消息
     */
    private fun sendMessage(message: ClientMessage) {
        viewModelScope.launch {
            val success = webSocketManager.sendMessage(message)
            if (!success) {
                logWarn("Failed to send message: ${message::class.simpleName}")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "消息发送失败，请检查网络连接"
                )
            }
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * 更新调试信息
     */
    private fun updateDebugInfo() {
        val connectionInfo = webSocketManager.getConnectionStats()
        val debugInfo = buildString {
            append("Connection: ${connectionInfo.state}\n")
            append("Messages Sent: ${connectionInfo.messagesSent}\n")
            append("Messages Received: ${connectionInfo.messagesReceived}\n")
            append("Reconnect Attempts: ${connectionInfo.reconnectAttempts}\n")
            connectionInfo.lastError?.let { append("Last Error: $it\n") }
        }
        
        _uiState.value = _uiState.value.copy(debugInfo = debugInfo)
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            webSocketManager.disconnect()
            client.close()
        }
    }
    
    /**
     * 游戏消息处理器
     */
    private inner class GameMessageHandler : AbstractMessageHandler() {
        
        override suspend fun handleRoomCreated(message: ServerMessage.RoomCreated) {
            logInfo("Room created: ${message.room.name}")
            _uiState.value = _uiState.value.copy(
                gameRoom = message.room,
                currentPlayer = message.room.players.firstOrNull()
            )
        }
        
        override suspend fun handlePlayerJoined(message: ServerMessage.PlayerJoined) {
            logInfo("Player joined: ${message.player.name}")
            _uiState.value = _uiState.value.copy(
                gameRoom = message.room,
                currentPlayer = if (_uiState.value.currentPlayer?.id == message.player.id) 
                    message.player else _uiState.value.currentPlayer
            )
        }
        
        override suspend fun handleGameStateUpdate(message: ServerMessage.GameStateUpdate) {
            _uiState.value = _uiState.value.copy(gameRoom = message.room)
        }
        
        override suspend fun handleRoomList(message: ServerMessage.RoomList) {
            _uiState.value = _uiState.value.copy(availableRooms = message.rooms)
        }
        
        override suspend fun handleError(message: ServerMessage.Error) {
            logError("Server error: ${message.message}")
            _uiState.value = _uiState.value.copy(errorMessage = message.message)
        }
        
        override suspend fun handleResponseRequired(message: ServerMessage.ResponseRequired) {
            val currentPlayer = _uiState.value.currentPlayer
            if (currentPlayer?.id == message.targetPlayerId) {
                logInfo("Response required: ${message.responseType}")
                
                val responseType = when (message.responseType) {
                    "NULLIFICATION" -> ResponseType.NULLIFICATION
                    "SPECIAL_SELECTION" -> ResponseType.ABUNDANT_HARVEST // 或其他特殊类型
                    else -> ResponseType.OPTIONAL
                }
                
                _uiState.value = _uiState.value.copy(
                    needsResponse = true,
                    responseType = responseType,
                    errorMessage = "需要响应: ${message.originalCard.name}"
                )
            }
        }
        
        override suspend fun handleCardExecutionCompleted(message: ServerMessage.CardExecutionCompleted) {
            logInfo("Card execution completed: ${message.message}")
            
            _uiState.value = _uiState.value.copy(
                gameRoom = message.room,
                needsResponse = false,
                responseType = null,
                errorMessage = if (message.success) null else message.message
            )
        }
        
        override suspend fun handleNullificationPhaseStarted(message: ServerMessage.NullificationPhaseStarted) {
            logInfo("Nullification phase started for: ${message.cardName}")
            _uiState.value = _uiState.value.copy(
                gameRoom = message.room,
                errorMessage = "${message.casterName}使用了${message.cardName}，所有玩家可以使用无懈可击响应"
            )
        }
        
        override suspend fun handleSpecialExecutionStarted(message: ServerMessage.SpecialExecutionStarted) {
            logInfo("Special execution started: ${message.cardName}")
            _uiState.value = _uiState.value.copy(
                gameRoom = message.room,
                errorMessage = "${message.cardName}特殊效果开始，当前玩家：${message.currentPlayerName}"
            )
        }
        
        // 可以继续添加其他消息处理方法...
    }
}