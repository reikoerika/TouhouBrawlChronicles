package moe.gensoukyo.tbc.client.utils

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.utils.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 客户端WebSocket连接管理器
 * 提供自动重连、错误处理和消息队列功能
 */
class ClientWebSocketManager(
    private val client: HttpClient,
    private val serverUrl: String
) {
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED
    }
    
    data class ConnectionInfo(
        val state: ConnectionState,
        val lastError: String? = null,
        val reconnectAttempts: Int = 0,
        val messagesSent: Long = 0,
        val messagesReceived: Long = 0
    )
    
    private val _connectionState = MutableStateFlow(ConnectionInfo(ConnectionState.DISCONNECTED))
    val connectionState: StateFlow<ConnectionInfo> = _connectionState.asStateFlow()
    
    private val _incomingMessages = MutableStateFlow<ServerMessage?>(null)
    val incomingMessages: StateFlow<ServerMessage?> = _incomingMessages.asStateFlow()
    
    private var currentSession: WebSocketSession? = null
    private val isRunning = AtomicBoolean(false)
    private val messageQueue = mutableListOf<ClientMessage>()
    private var reconnectJob: Job? = null
    
    // 重连配置
    private val maxReconnectAttempts = 5
    private val reconnectDelay = 3.seconds
    private val maxReconnectDelay = 30.seconds
    
    /**
     * 启动连接
     */
    suspend fun connect(): Boolean {
        if (isRunning.get()) {
            logWarn("WebSocket is already running")
            return currentSession?.isActive == true
        }
        
        isRunning.set(true)
        return attemptConnection()
    }
    
    /**
     * 断开连接
     */
    suspend fun disconnect() {
        logInfo("Disconnecting WebSocket...")
        
        isRunning.set(false)
        reconnectJob?.cancel()
        
        currentSession?.let { session ->
            runCatching { session.close() }
        }
        currentSession = null
        
        updateConnectionState(ConnectionState.DISCONNECTED)
    }
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(message: ClientMessage): Boolean {
        val session = currentSession
        
        if (session == null || !session.isActive) {
            logWarn("Cannot send message: WebSocket not connected")
            // 将消息添加到队列，等待重连后发送
            synchronized(messageQueue) {
                messageQueue.add(message)
            }
            triggerReconnectIfNeeded()
            return false
        }
        
        return try {
            logDebug("Sending message: ${message::class.simpleName}")
            
            val jsonResult = SafeJson.encodeToString(message)
            if (jsonResult.isFailure) {
                logError("Failed to serialize message", jsonResult.exceptionOrNull())
                return false
            }
            
            session.send(jsonResult.getOrThrow())
            
            val currentState = _connectionState.value
            _connectionState.value = currentState.copy(
                messagesSent = currentState.messagesSent + 1
            )
            
            logDebug("Message sent successfully")
            true
        } catch (e: Exception) {
            logError("Failed to send message", e)
            triggerReconnectIfNeeded()
            false
        }
    }
    
    /**
     * 尝试连接到服务器
     */
    private suspend fun attemptConnection(): Boolean {
        updateConnectionState(ConnectionState.CONNECTING)
        
        return try {
            logInfo("Attempting to connect to $serverUrl")
            
            client.webSocket(serverUrl) {
                logInfo("WebSocket connected successfully")
                currentSession = this
                updateConnectionState(ConnectionState.CONNECTED)
                
                // 发送排队的消息
                flushMessageQueue()
                
                // 开始接收消息
                handleIncomingMessages()
            }
            
            true
        } catch (e: Exception) {
            logError("Failed to connect to WebSocket", e)
            updateConnectionState(
                ConnectionState.FAILED,
                error = "Connection failed: ${e.message}"
            )
            
            if (isRunning.get()) {
                scheduleReconnect()
            }
            
            false
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private suspend fun handleIncomingMessages() {
        try {
            currentSession?.let { session ->
                for (frame in session.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            logDebug("Received message: ${text.take(100)}${if(text.length > 100) "..." else ""}")
                            
                            val result = SafeJson.decodeFromString<ServerMessage>(text)
                            if (result.isSuccess) {
                                val message = result.getOrThrow()
                                _incomingMessages.value = message
                                
                                val currentState = _connectionState.value
                                _connectionState.value = currentState.copy(
                                    messagesReceived = currentState.messagesReceived + 1
                                )
                            } else {
                                logError("Failed to deserialize message", result.exceptionOrNull())
                            }
                        }
                        is Frame.Close -> {
                            logInfo("WebSocket connection closed by server")
                            break
                        }
                        else -> {
                            logDebug("Received non-text frame: ${frame.frameType}")
                        }
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logInfo("WebSocket receive channel closed")
        } catch (e: Exception) {
            logError("Error handling incoming messages", e)
        } finally {
            currentSession = null
            if (isRunning.get()) {
                logInfo("Connection lost, scheduling reconnect...")
                scheduleReconnect()
            }
        }
    }
    
    /**
     * 调度重连
     */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            val currentState = _connectionState.value
            val attempts = currentState.reconnectAttempts
            
            if (attempts >= maxReconnectAttempts) {
                logError("Max reconnect attempts reached, giving up")
                updateConnectionState(
                    ConnectionState.FAILED,
                    error = "Max reconnect attempts ($maxReconnectAttempts) reached"
                )
                return@launch
            }
            
            updateConnectionState(ConnectionState.RECONNECTING, reconnectAttempts = attempts + 1)
            
            // 计算延迟时间（指数退避）
            val delay = minOf(
                reconnectDelay * (1 shl attempts), // 指数退避：3s, 6s, 12s, 24s...
                maxReconnectDelay
            )
            
            logInfo("Scheduling reconnect in $delay (attempt ${attempts + 1}/$maxReconnectAttempts)")
            delay(delay)
            
            if (isRunning.get()) {
                attemptConnection()
            }
        }
    }
    
    /**
     * 触发重连（如果需要）
     */
    private fun triggerReconnectIfNeeded() {
        val currentState = _connectionState.value
        if (currentState.state == ConnectionState.CONNECTED || 
            currentState.state == ConnectionState.CONNECTING ||
            currentState.state == ConnectionState.RECONNECTING) {
            return
        }
        
        if (isRunning.get()) {
            scheduleReconnect()
        }
    }
    
    /**
     * 发送排队的消息
     */
    private fun flushMessageQueue() {
        synchronized(messageQueue) {
            val messagesToSend = messageQueue.toList()
            messageQueue.clear()
            
            messagesToSend.forEach { message ->
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessage(message)
                }
            }
            
            if (messagesToSend.isNotEmpty()) {
                logInfo("Sent ${messagesToSend.size} queued messages")
            }
        }
    }
    
    /**
     * 更新连接状态
     */
    private fun updateConnectionState(
        state: ConnectionState,
        error: String? = null,
        reconnectAttempts: Int = 0
    ) {
        val currentState = _connectionState.value
        _connectionState.value = currentState.copy(
            state = state,
            lastError = error ?: currentState.lastError,
            reconnectAttempts = if (state == ConnectionState.CONNECTED) 0 else 
                if (reconnectAttempts > 0) reconnectAttempts else currentState.reconnectAttempts
        )
        
        logInfo("Connection state changed to: $state ${error?.let { "($it)" } ?: ""}")
    }
    
    /**
     * 获取连接统计信息
     */
    fun getConnectionStats(): ConnectionInfo {
        return _connectionState.value
    }
    
    /**
     * 检查连接是否活跃
     */
    fun isConnected(): Boolean {
        return currentSession?.isActive == true && _connectionState.value.state == ConnectionState.CONNECTED
    }
}