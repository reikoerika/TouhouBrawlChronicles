package moe.gensoukyo.tbc.server.utils

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.isActive
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.utils.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 安全的WebSocket会话管理器
 */
class SafeWebSocketManager {
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    private val connectionMetadata = ConcurrentHashMap<String, ConnectionInfo>()
    private val messageCounter = AtomicLong(0)
    
    data class ConnectionInfo(
        val sessionId: String,
        val createdAt: Long = System.currentTimeMillis(),
        var lastActivity: Long = System.currentTimeMillis(),
        var messagesSent: Long = 0,
        var messagesReceived: Long = 0,
        var errors: Long = 0
    )
    
    /**
     * 添加连接
     */
    fun addConnection(sessionId: String, session: WebSocketSession) {
        connections[sessionId] = session
        connectionMetadata[sessionId] = ConnectionInfo(sessionId)
        logInfo("Added connection: $sessionId")
    }
    
    /**
     * 移除连接
     */
    fun removeConnection(sessionId: String) {
        connections.remove(sessionId)
        connectionMetadata.remove(sessionId)
        logInfo("Removed connection: $sessionId")
    }
    
    /**
     * 获取连接
     */
    fun getConnection(sessionId: String): WebSocketSession? {
        return connections[sessionId]
    }
    
    /**
     * 安全地发送消息到指定连接
     */
    suspend fun sendMessage(sessionId: String, message: ServerMessage): Boolean {
        val session = connections[sessionId]
        if (session == null) {
            logWarn("Attempted to send message to non-existent session: $sessionId")
            return false
        }
        
        return sendMessageToSession(session, message, sessionId)
    }
    
    /**
     * 安全地发送消息到指定会话
     */
    suspend fun sendMessageToSession(
        session: WebSocketSession, 
        message: ServerMessage, 
        sessionId: String? = null
    ): Boolean {
        return try {
            val messageId = messageCounter.incrementAndGet()
            logDebug("Sending message #$messageId to session $sessionId: ${message::class.simpleName}")
            
            val jsonResult = SafeJson.encodeToString(message)
            if (jsonResult.isFailure) {
                logError("Failed to serialize message for session $sessionId", jsonResult.exceptionOrNull())
                updateConnectionError(sessionId)
                return false
            }
            
            session.send(jsonResult.getOrThrow())
            updateConnectionActivity(sessionId, sent = true)
            
            logDebug("Successfully sent message #$messageId to session $sessionId")
            true
        } catch (e: ClosedSendChannelException) {
            logWarn("Attempted to send to closed session $sessionId")
            sessionId?.let { removeConnection(it) }
            false
        } catch (e: Exception) {
            logError("Failed to send message to session $sessionId", e)
            updateConnectionError(sessionId)
            false
        }
    }
    
    /**
     * 广播消息到多个连接
     */
    suspend fun broadcastMessage(sessionIds: List<String>, message: ServerMessage): Int {
        var successCount = 0
        val messageId = messageCounter.incrementAndGet()
        
        logDebug("Broadcasting message #$messageId (${message::class.simpleName}) to ${sessionIds.size} sessions")
        
        sessionIds.forEach { sessionId ->
            if (sendMessage(sessionId, message)) {
                successCount++
            }
        }
        
        logInfo("Broadcast message #$messageId completed: $successCount/${sessionIds.size} successful")
        return successCount
    }
    
    /**
     * 获取所有活跃的连接ID
     */
    fun getActiveConnections(): Set<String> {
        return connections.keys.toSet()
    }
    
    /**
     * 获取连接统计信息
     */
    fun getConnectionInfo(sessionId: String): ConnectionInfo? {
        return connectionMetadata[sessionId]
    }
    
    /**
     * 获取所有连接的统计信息
     */
    fun getAllConnectionInfo(): Map<String, ConnectionInfo> {
        return connectionMetadata.toMap()
    }
    
    /**
     * 清理死连接
     */
    fun cleanup() {
        val toRemove = mutableListOf<String>()
        
        connections.forEach { (sessionId, session) ->
            if (session.isActive.not()) {
                toRemove.add(sessionId)
            }
        }
        
        toRemove.forEach { removeConnection(it) }
        
        if (toRemove.isNotEmpty()) {
            logInfo("Cleaned up ${toRemove.size} inactive connections")
        }
    }
    
    private fun updateConnectionActivity(sessionId: String?, sent: Boolean = false, received: Boolean = false) {
        sessionId?.let { id ->
            connectionMetadata[id]?.let { info ->
                info.lastActivity = System.currentTimeMillis()
                if (sent) info.messagesSent++
                if (received) info.messagesReceived++
            }
        }
    }
    
    private fun updateConnectionError(sessionId: String?) {
        sessionId?.let { id ->
            connectionMetadata[id]?.let { info ->
                info.errors++
                info.lastActivity = System.currentTimeMillis()
            }
        }
    }
}