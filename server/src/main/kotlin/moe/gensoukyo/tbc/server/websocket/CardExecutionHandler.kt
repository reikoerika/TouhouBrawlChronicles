package moe.gensoukyo.tbc.server.websocket

import io.ktor.websocket.WebSocketSession
import moe.gensoukyo.tbc.server.service.GameService
import moe.gensoukyo.tbc.server.utils.SafeWebSocketManager
import moe.gensoukyo.tbc.shared.card.CardExecutionContext
import moe.gensoukyo.tbc.shared.card.CardExecutionPhase
import moe.gensoukyo.tbc.shared.messages.*
import moe.gensoukyo.tbc.shared.utils.*

/**
 * 重构后的卡牌执行WebSocket处理器
 * 包含完善的错误处理和调试功能
 */
class CardExecutionHandler(
    private val gameService: GameService,
    private val webSocketManager: SafeWebSocketManager = SafeWebSocketManager(),
    private val playerSessions: Map<String, String> = emptyMap(),
    private val spectatorSessions: Map<String, String> = emptyMap()
) {
    
    init {
        Logger.setLevel(Logger.Level.DEBUG) // 开发阶段启用详细日志
    }
    
    /**
     * 同步连接到WebSocket管理器
     */
    fun syncConnections(connections: Map<String, WebSocketSession>) {
        // 清理旧连接
        webSocketManager.cleanup()
        
        // 添加所有当前活跃连接
        connections.forEach { (sessionId, session) ->
            webSocketManager.addConnection(sessionId, session)
        }
        
        logDebug("Synced ${connections.size} connections to SafeWebSocketManager")
    }
    
    /**
     * 安全地处理出牌消息
     */
    suspend fun handlePlayCard(
        message: PlayCardMessage,
        connections: Map<String, WebSocketSession>
    ): Result<Unit> {
        return runCatching {
            logDebug("Handling play card: player=${message.playerId}, card=${message.cardId}, targets=${message.targetIds}")

            val roomId = findPlayerRoom(message.playerId)
                ?: return Result.failure(IllegalStateException("Player ${message.playerId} not found in any room"))

            logInfo("Starting card execution for player ${message.playerId} in room $roomId")

            val context = gameService.playCard(roomId, message.playerId, message.cardId, message.targetIds)
                ?: return Result.failure(IllegalStateException("Failed to start card execution"))

            // 更新WebSocket连接映射
            updateConnectionMappings(connections, playerSessions, spectatorSessions)

            // 广播卡牌执行开始
            broadcastCardExecutionStarted(context, roomId)

            // 处理下一阶段
            handleNextPhase(context, roomId)
        }.onFailure { error ->
            logError("Failed to handle play card", error)
        }
    }
    
    /**
     * 安全地处理响应消息
     */
    suspend fun handleResponse(
        message: RespondToCardMessage,
        connections: Map<String, WebSocketSession>
    ): Result<Unit> {
        return runCatching {
            logDebug("Handling response: player=${message.playerId}, responseCard=${message.responseCardId}, accept=${message.accept}")
            
            val roomId = findPlayerRoom(message.playerId)
                ?: return Result.failure(IllegalStateException("Player ${message.playerId} not found in any room"))
            
            // 更新WebSocket连接映射
            updateConnectionMappings(connections, playerSessions, spectatorSessions)
            
            val context = gameService.respondToCardNew(roomId, message.playerId, message.responseCardId, message.accept)
                ?: return Result.failure(IllegalStateException("Failed to process response"))
            
            // 广播响应结果
            broadcastResponseReceived(context, roomId, message)
            
            // 处理下一阶段
            handleNextPhase(context, roomId)
        }.onFailure { error ->
            logError("Failed to handle response", error)
        }
    }
    
    /**
     * 处理卡牌执行的下一阶段
     */
    private suspend fun handleNextPhase(context: CardExecutionContext, roomId: String) {
        logDebug("Processing phase: ${context.phase} for execution ${context.id}")
        
        when (context.phase) {
            CardExecutionPhase.NULLIFICATION -> handleNullificationPhase(context, roomId)
            CardExecutionPhase.RESOLUTION -> handleResolutionPhase(context, roomId)
            CardExecutionPhase.SPECIAL_EXECUTION -> handleSpecialExecutionPhase(context, roomId)
            CardExecutionPhase.COMPLETED -> handleCompletedPhase(context, roomId)
            else -> logWarn("Unknown execution phase: ${context.phase}")
        }
    }
    
    /**
     * 处理无懈可击阶段
     */
    private suspend fun handleNullificationPhase(context: CardExecutionContext, roomId: String) {
        val room = gameService.getRoom(roomId)
        if (room == null) {
            logError("Room $roomId not found during nullification phase")
            return
        }
        
        val targetPlayerIds = gameService.getAllNullificationTargets(roomId)
        if (targetPlayerIds.isEmpty()) {
            logWarn("No players can respond to nullification for execution ${context.id}")
            // 直接进入下一阶段
            val updatedContext = gameService.executeCardEffect(roomId, context.id)
            updatedContext?.let { handleNextPhase(it, roomId) }
            return
        }
        
        // 广播无懈可击阶段开始
        broadcastToRoom(roomId) {
            ServerMessage.NullificationPhaseStarted(
                executionId = context.id,
                cardName = context.card.name,
                casterName = context.caster.name,
                targetPlayerIds = targetPlayerIds,
                room = room
            )
        }
        
        // 向所有可以响应的玩家发送无懈可击响应请求
        targetPlayerIds.forEach { playerId ->
            sendMessageToPlayer(playerId, roomId) {
                ServerMessage.ResponseRequired(
                    executionId = context.id,
                    targetPlayerId = playerId,
                    responseType = "NULLIFICATION",
                    originalCard = context.card,
                    casterName = context.caster.name
                )
            }
        }
    }
    
    /**
     * 处理结算阶段
     */
    private suspend fun handleResolutionPhase(context: CardExecutionContext, roomId: String) {
        val updatedContext = gameService.executeCardEffect(roomId, context.id)
        if (updatedContext != null) {
            handleNextPhase(updatedContext, roomId)
        } else {
            logError("Failed to execute card effect for execution ${context.id}")
        }
    }
    
    /**
     * 处理特殊执行阶段
     */
    private suspend fun handleSpecialExecutionPhase(context: CardExecutionContext, roomId: String) {
        val room = gameService.getRoom(roomId)
        if (room == null) {
            logError("Room $roomId not found during special execution phase")
            return
        }
        
        val targetPlayerId = gameService.getCurrentResponseTarget(roomId)
        if (targetPlayerId == null) {
            logWarn("No target player for special execution ${context.id}")
            return
        }
        
        val targetPlayerName = room.players.find { it.id == targetPlayerId }?.name ?: "未知"
        
        // 获取可用选项
        val availableOptions = when (context.card.name) {
            "五谷丰登" -> {
                context.specialData.abundantHarvestCards.map { card ->
                    ResponseOption(
                        id = card.id,
                        name = card.name,
                        description = card.effect
                    )
                }
            }
            else -> emptyList()
        }
        
        // 广播特殊执行开始
        broadcastToRoom(roomId) {
            ServerMessage.SpecialExecutionStarted(
                executionId = context.id,
                cardName = context.card.name,
                currentPlayerId = targetPlayerId,
                currentPlayerName = targetPlayerName,
                availableOptions = availableOptions,
                room = room
            )
        }
        
        // 发送选择请求给当前玩家
        sendMessageToPlayer(targetPlayerId, roomId) {
            ServerMessage.ResponseRequired(
                executionId = context.id,
                targetPlayerId = targetPlayerId,
                responseType = "SPECIAL_SELECTION",
                originalCard = context.card,
                casterName = context.caster.name,
                availableOptions = availableOptions
            )
        }
    }
    
    /**
     * 处理完成阶段
     */
    private suspend fun handleCompletedPhase(context: CardExecutionContext, roomId: String) {
        val room = gameService.getRoom(roomId)
        if (room == null) {
            logError("Room $roomId not found during completion phase")
            return
        }
        
        // 添加使用过的卡牌到出牌区（无论成功或被阻挡）
        val playedCard = moe.gensoukyo.tbc.shared.model.PlayedCard(
            card = context.card,
            playerId = context.caster.id,
            playerName = context.caster.name,
            turnNumber = room.turnCount,
            timestamp = System.currentTimeMillis(),
            targetIds = context.finalTargets.map { it.id }
        )
        room.currentTurnPlayedCards.add(playedCard)
        logDebug("Added card ${context.card.name} to play area for player ${context.caster.name} (blocked: ${context.isBlocked})")
        
        // 广播卡牌执行完成消息
        broadcastToRoom(roomId) {
            ServerMessage.CardExecutionCompleted(
                executionId = context.id,
                success = context.result?.success ?: false,
                blocked = context.isBlocked,
                message = context.result?.message ?: "执行完成",
                room = room
            )
        }
        
        // 广播游戏状态更新以同步手牌变化和出牌区
        broadcastToRoom(roomId) {
            ServerMessage.GameStateUpdate(room)
        }
        
        // 清理执行上下文
        gameService.cleanupCardExecution(roomId, context.id)
    }
    
    // ======================== 辅助方法 ========================
    
    /**
     * 更新WebSocket连接映射
     */
    private fun updateConnectionMappings(
        connections: Map<String, WebSocketSession>,
        playerSessions: Map<String, String>,
        spectatorSessions: Map<String, String>
    ) {
        // 清理现有连接
        webSocketManager.cleanup()
        
        // 添加玩家连接
        playerSessions.forEach { (playerId, sessionId) ->
            connections[sessionId]?.let { session ->
                webSocketManager.addConnection(sessionId, session)
            }
        }
        
        // 添加观战者连接
        spectatorSessions.forEach { (spectatorId, sessionId) ->
            connections[sessionId]?.let { session ->
                webSocketManager.addConnection(sessionId, session)
            }
        }
    }
    
    /**
     * 安全地广播消息到房间
     */
    private suspend fun broadcastToRoom(roomId: String, messageProvider: () -> ServerMessage) {
        val room = gameService.getRoom(roomId)
        if (room == null) {
            logError("Cannot broadcast to room $roomId: room not found")
            return
        }
        
        val allPlayerIds = room.players.map { it.id } + room.spectators.map { it.id }
        val sessionIds = allPlayerIds.mapNotNull { playerId ->
            playerSessions[playerId] ?: spectatorSessions[playerId]
        }
        
        if (sessionIds.isEmpty()) {
            logWarn("No active sessions found for room $roomId - playerSessions: ${playerSessions.keys}, spectatorSessions: ${spectatorSessions.keys}, roomPlayerIds: ${allPlayerIds}")
            return
        }
        
        val message = messageProvider()
        val successCount = webSocketManager.broadcastMessage(sessionIds, message)
        
        logDebug("Broadcast to room $roomId: $successCount/${sessionIds.size} successful")
    }
    
    /**
     * 安全地发送消息给指定玩家
     */
    private suspend fun sendMessageToPlayer(playerId: String, roomId: String, messageProvider: () -> ServerMessage) {
        val sessionId = playerSessions[playerId] ?: spectatorSessions[playerId]
        if (sessionId == null) {
            logWarn("No session found for player $playerId in room $roomId")
            return
        }
        
        val message = messageProvider()
        val success = webSocketManager.sendMessage(sessionId, message)
        
        if (success) {
            logDebug("Message sent to player $playerId: ${message::class.simpleName}")
        } else {
            logError("Failed to send message to player $playerId")
        }
    }
    
    /**
     * 广播卡牌执行开始消息
     */
    private suspend fun broadcastCardExecutionStarted(context: CardExecutionContext, roomId: String) {
        val room = gameService.getRoom(roomId)
        if (room == null) {
            logError("Room $roomId not found when broadcasting card execution started")
            return
        }
        
        broadcastToRoom(roomId) {
            ServerMessage.CardExecutionStarted(
                executionId = context.id,
                casterName = context.caster.name,
                cardName = context.card.name,
                targetNames = context.finalTargets.map { it.name },
                room = room
            )
        }
    }
    
    /**
     * 广播响应接收消息
     */
    private suspend fun broadcastResponseReceived(
        context: CardExecutionContext,
        roomId: String,
        message: RespondToCardMessage
    ) {
        val room = gameService.getRoom(roomId)
        if (room == null) {
            logError("Room $roomId not found when broadcasting response received")
            return
        }
        
        broadcastToRoom(roomId) {
            ServerMessage.ResponseReceived(
                executionId = context.id,
                playerId = message.playerId,
                playerName = room.players.find { it.id == message.playerId }?.name ?: "未知",
                responseCard = context.responses.lastOrNull()?.responseCard,
                accepted = message.accept,
                room = room
            )
        }
    }
    
    /**
     * 查找玩家所在房间
     */
    private fun findPlayerRoom(playerId: String): String? {
        return gameService.getAllRooms().find { room ->
            room.players.any { it.id == playerId }
        }?.id
    }
}