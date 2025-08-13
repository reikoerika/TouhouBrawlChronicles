package moe.gensoukyo.tbc.server.websocket

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.server.card.*
import moe.gensoukyo.tbc.server.service.GameService
import moe.gensoukyo.tbc.shared.messages.*
import moe.gensoukyo.tbc.shared.model.Card

/**
 * 新的卡牌执行WebSocket处理器
 * 使用简化的消息流程，符合三国杀标准
 */
class CardExecutionHandler(
    private val gameService: GameService
) {
    
    /**
     * 处理出牌消息
     */
    suspend fun handlePlayCard(
        message: PlayCardMessage,
        connections: Map<String, WebSocketSession>,
        playerSessions: Map<String, String>,
        spectatorSessions: Map<String, String>
    ) {
        val roomId = findPlayerRoom(message.playerId) ?: return
        
        // 执行出牌
        val context = gameService.playCardNew(roomId, message.playerId, message.cardId, message.targetIds)
            ?: return
        
        val room = gameService.getRoom(roomId) ?: return
        
        // 广播卡牌执行开始
        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId) {
            CardExecutionStartedMessage(
                executionId = context.id,
                casterName = context.caster.name,
                cardName = context.card.name,
                targetNames = context.finalTargets.map { it.name },
                room = room
            )
        }
        
        // 处理下一阶段
        handleNextPhase(context, roomId, connections, playerSessions, spectatorSessions)
    }
    
    /**
     * 处理响应消息
     */
    suspend fun handleResponse(
        message: RespondToCardMessage,
        connections: Map<String, WebSocketSession>,
        playerSessions: Map<String, String>,
        spectatorSessions: Map<String, String>
    ) {
        val roomId = findPlayerRoom(message.playerId) ?: return
        
        // 处理响应
        val context = gameService.respondToCardNew(roomId, message.playerId, message.responseCardId, message.accept)
            ?: return
        
        val room = gameService.getRoom(roomId) ?: return
        
        // 广播响应结果
        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId) {
            ResponseReceivedMessage(
                executionId = context.id,
                playerId = message.playerId,
                playerName = room.players.find { it.id == message.playerId }?.name ?: "未知",
                responseCard = context.responses.lastOrNull()?.responseCard,
                accepted = message.accept,
                room = room
            )
        }
        
        // 如果执行完成，发送完成消息
        if (context.isCompleted) {
            broadcastToRoom(connections, playerSessions, spectatorSessions, roomId) {
                CardExecutionCompletedMessage(
                    executionId = context.id,
                    success = context.result?.success ?: false,
                    blocked = context.isBlocked,
                    message = context.result?.message ?: "执行完成",
                    room = room
                )
            }
        } else {
            // 继续下一阶段
            handleNextPhase(context, roomId, connections, playerSessions, spectatorSessions)
        }
    }
    
    /**
     * 处理下一执行阶段
     */
    private suspend fun handleNextPhase(
        context: CardExecutionContext,
        roomId: String,
        connections: Map<String, WebSocketSession>,
        playerSessions: Map<String, String>,
        spectatorSessions: Map<String, String>
    ) {
        when (context.phase) {
            CardExecutionPhase.NULLIFICATION -> {
                handleNullificationPhase(context, roomId, connections, playerSessions, spectatorSessions)
            }
            CardExecutionPhase.RESOLUTION -> {
                handleResolutionPhase(context, roomId, connections, playerSessions, spectatorSessions)
            }
            CardExecutionPhase.SPECIAL_EXECUTION -> {
                handleSpecialExecutionPhase(context, roomId, connections, playerSessions, spectatorSessions)
            }
            CardExecutionPhase.COMPLETED -> {
                val room = gameService.getRoom(roomId) ?: return
                broadcastToRoom(connections, playerSessions, spectatorSessions, roomId) {
                    CardExecutionCompletedMessage(
                        executionId = context.id,
                        success = context.result?.success ?: false,
                        blocked = context.isBlocked,
                        message = context.result?.message ?: "执行完成",
                        room = room
                    )
                }
            }
            else -> {}
        }
    }
    
    /**
     * 处理无懈可击阶段
     */
    private suspend fun handleNullificationPhase(
        context: CardExecutionContext,
        roomId: String,
        connections: Map<String, WebSocketSession>,
        playerSessions: Map<String, String>,
        spectatorSessions: Map<String, String>
    ) {
        val targetPlayerId = gameService.getCurrentResponseTarget(roomId) ?: return
        val targetSession = connections[playerSessions[targetPlayerId]] ?: return
        
        // 发送无懈可击响应请求
        targetSession.send(
            Json.encodeToString(
                ResponseRequiredMessage(
                    executionId = context.id,
                    targetPlayerId = targetPlayerId,
                    responseType = "NULLIFICATION",
                    originalCard = context.card,
                    casterName = context.caster.name
                )
            )
        )
    }
    
    /**
     * 处理结算阶段
     */
    private suspend fun handleResolutionPhase(
        context: CardExecutionContext,
        roomId: String,
        connections: Map<String, WebSocketSession>,
        playerSessions: Map<String, String>,
        spectatorSessions: Map<String, String>
    ) {
        // 继续执行卡牌效果
        val updatedContext = gameService.continueCardExecution(roomId) ?: return
        
        // 处理执行后的状态
        handleNextPhase(updatedContext, roomId, connections, playerSessions, spectatorSessions)
    }
    
    /**
     * 处理特殊执行阶段（如五谷丰登）
     */
    private suspend fun handleSpecialExecutionPhase(
        context: CardExecutionContext,
        roomId: String,
        connections: Map<String, WebSocketSession>,
        playerSessions: Map<String, String>,
        spectatorSessions: Map<String, String>
    ) {
        val room = gameService.getRoom(roomId) ?: return
        val targetPlayerId = gameService.getCurrentResponseTarget(roomId) ?: return
        val targetPlayerName = room.players.find { it.id == targetPlayerId }?.name ?: "未知"
        
        // 获取可用选项
        val availableOptions = when (context.card.name) {
            "五谷丰登" -> {
                val availableCards = context.specialData["availableCards"] as? List<Card> ?: emptyList()
                availableCards.map { card ->
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
        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId) {
            SpecialExecutionStartedMessage(
                executionId = context.id,
                cardName = context.card.name,
                currentPlayerId = targetPlayerId,
                currentPlayerName = targetPlayerName,
                availableOptions = availableOptions,
                room = room
            )
        }
        
        // 发送选择请求给当前玩家
        val targetSession = connections[playerSessions[targetPlayerId]] ?: return
        targetSession.send(
            Json.encodeToString(
                ResponseRequiredMessage(
                    executionId = context.id,
                    targetPlayerId = targetPlayerId,
                    responseType = "SPECIAL_SELECTION",
                    originalCard = context.card,
                    casterName = context.caster.name,
                    availableOptions = availableOptions
                )
            )
        )
    }
    
    // ======================== 辅助方法 ========================
    
    private fun findPlayerRoom(playerId: String): String? {
        return gameService.getAllRooms().find { room ->
            room.players.any { it.id == playerId }
        }?.id
    }
    
    private suspend fun broadcastToRoom(
        connections: Map<String, WebSocketSession>,
        playerSessions: Map<String, String>,
        spectatorSessions: Map<String, String>,
        roomId: String,
        messageProvider: () -> Any
    ) {
        val room = gameService.getRoom(roomId) ?: return
        val message = Json.encodeToString(messageProvider())
        
        // 发送给所有房间内的玩家
        room.players.forEach { player ->
            val sessionId = playerSessions[player.id]
            sessionId?.let { connections[it]?.send(message) }
        }
        
        // 发送给所有观战者
        room.spectators.forEach { spectator ->
            val sessionId = spectatorSessions[spectator.id]
            sessionId?.let { connections[it]?.send(message) }
        }
    }
}