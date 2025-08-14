package moe.gensoukyo.tbc.server.websocket

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.model.GameRoom
import moe.gensoukyo.tbc.shared.utils.logDebug
import moe.gensoukyo.tbc.shared.utils.logError
import moe.gensoukyo.tbc.shared.utils.logWarn
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一连接管理器
 * 集中管理所有WebSocket连接和会话映射
 */
class UnifiedConnectionManager {
    // WebSocket连接管理
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    
    // 会话映射管理
    private val playerSessions = ConcurrentHashMap<String, String>() // playerId -> sessionId
    private val spectatorSessions = ConcurrentHashMap<String, String>() // spectatorId -> sessionId
    private val sessionToPlayerMap = ConcurrentHashMap<String, String>() // sessionId -> playerId/spectatorId
    
    /**
     * 添加WebSocket连接
     */
    fun addConnection(sessionId: String, session: WebSocketSession) {
        connections[sessionId] = session
        logDebug("Added connection: $sessionId (total: ${connections.size})")
    }
    
    /**
     * 移除WebSocket连接并清理相关映射
     */
    fun removeConnection(sessionId: String) {
        connections.remove(sessionId)
        
        // 清理相关的玩家/观战者映射
        val playerId = sessionToPlayerMap.remove(sessionId)
        if (playerId != null) {
            playerSessions.remove(playerId)
            spectatorSessions.remove(playerId)
            logDebug("Removed connection and session mappings: sessionId=$sessionId, playerId=$playerId")
        }
        
        logDebug("Removed connection: $sessionId (remaining: ${connections.size})")
    }
    
    /**
     * 注册玩家会话
     */
    fun registerPlayer(playerId: String, sessionId: String) {
        // 清理该玩家的旧会话（如果存在）
        val oldSessionId = playerSessions[playerId]
        if (oldSessionId != null) {
            sessionToPlayerMap.remove(oldSessionId)
        }
        
        // 注册新会话
        playerSessions[playerId] = sessionId
        sessionToPlayerMap[sessionId] = playerId
        logDebug("Registered player: $playerId -> $sessionId")
    }
    
    /**
     * 注册观战者会话
     */
    fun registerSpectator(spectatorId: String, sessionId: String) {
        // 清理该观战者的旧会话（如果存在）
        val oldSessionId = spectatorSessions[spectatorId]
        if (oldSessionId != null) {
            sessionToPlayerMap.remove(oldSessionId)
        }
        
        // 注册新会话
        spectatorSessions[spectatorId] = sessionId
        sessionToPlayerMap[sessionId] = spectatorId
        logDebug("Registered spectator: $spectatorId -> $sessionId")
    }
    
    /**
     * 获取玩家会话ID
     */
    fun getPlayerSessionId(playerId: String): String? {
        return playerSessions[playerId]
    }
    
    /**
     * 获取观战者会话ID
     */
    fun getSpectatorSessionId(spectatorId: String): String? {
        return spectatorSessions[spectatorId]
    }
    
    /**
     * 获取WebSocket连接
     */
    fun getConnection(sessionId: String): WebSocketSession? {
        return connections[sessionId]
    }
    
    /**
     * 向单个玩家发送消息
     */
    suspend fun sendToPlayer(playerId: String, message: ServerMessage): Boolean {
        val sessionId = playerSessions[playerId] ?: spectatorSessions[playerId]
        if (sessionId == null) {
            logWarn("No session found for player: $playerId")
            return false
        }
        
        return sendToSession(sessionId, message)
    }
    
    /**
     * 向单个会话发送消息
     */
    suspend fun sendToSession(sessionId: String, message: ServerMessage): Boolean {
        val session = connections[sessionId]
        if (session == null) {
            logWarn("No connection found for session: $sessionId")
            return false
        }
        
        return try {
            val jsonMessage = Json.encodeToString<ServerMessage>(message)
            session.send(jsonMessage)
            logDebug("Message sent to session $sessionId: ${message::class.simpleName}")
            true
        } catch (e: ClosedSendChannelException) {
            logWarn("Connection closed for session $sessionId, removing...")
            removeConnection(sessionId)
            false
        } catch (e: Exception) {
            logError("Failed to send message to session $sessionId: ${e.message}", e)
            false
        }
    }
    
    /**
     * 向房间中的所有人广播消息
     */
    suspend fun broadcastToRoom(room: GameRoom, message: ServerMessage): Int {
        val allUserIds = room.players.map { it.id } + room.spectators.map { it.id }
        val sessionIds = allUserIds.mapNotNull { userId ->
            playerSessions[userId] ?: spectatorSessions[userId]
        }
        
        if (sessionIds.isEmpty()) {
            logWarn("No active sessions found for room ${room.id} - players: ${room.players.map { it.id }}, spectators: ${room.spectators.map { it.id }}")
            return 0
        }
        
        return broadcastToSessions(sessionIds, message)
    }
    
    /**
     * 向多个会话广播消息
     */
    suspend fun broadcastToSessions(sessionIds: List<String>, message: ServerMessage): Int {
        var successCount = 0
        val jsonMessage = Json.encodeToString<ServerMessage>(message)
        
        sessionIds.forEach { sessionId ->
            val session = connections[sessionId]
            if (session != null) {
                try {
                    session.send(jsonMessage)
                    successCount++
                } catch (e: ClosedSendChannelException) {
                    logWarn("Connection closed for session $sessionId, removing...")
                    removeConnection(sessionId)
                } catch (e: Exception) {
                    logError("Failed to send message to session $sessionId: ${e.message}", e)
                }
            } else {
                logWarn("No connection found for session: $sessionId")
            }
        }
        
        logDebug("Broadcast to ${sessionIds.size} sessions: $successCount successful")
        return successCount
    }
    
    /**
     * 获取连接统计信息
     */
    fun getStats(): ConnectionStats {
        return ConnectionStats(
            totalConnections = connections.size,
            totalPlayers = playerSessions.size,
            totalSpectators = spectatorSessions.size,
            activeSessions = sessionToPlayerMap.size
        )
    }
    
    /**
     * 清理所有连接和映射
     */
    fun cleanup() {
        connections.clear()
        playerSessions.clear()
        spectatorSessions.clear()
        sessionToPlayerMap.clear()
        logDebug("All connections and mappings cleaned up")
    }
}

/**
 * 连接统计信息
 */
data class ConnectionStats(
    val totalConnections: Int,
    val totalPlayers: Int,
    val totalSpectators: Int,
    val activeSessions: Int
)