package moe.gensoukyo.tbc.server.websocket

import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.printStack
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.server.service.GameService
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.card.CardExecutionContext
import moe.gensoukyo.tbc.shared.card.CardExecutionPhase
import moe.gensoukyo.tbc.shared.exceptions.CardExecutionException
import moe.gensoukyo.tbc.shared.messages.ResponseOption
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage.*
import moe.gensoukyo.tbc.shared.utils.logDebug
import moe.gensoukyo.tbc.shared.utils.logError
import moe.gensoukyo.tbc.shared.utils.logInfo
import moe.gensoukyo.tbc.shared.utils.logWarn

fun Route.gameWebSocket(gameService: GameService) {
    // 创建统一连接管理器
    val connectionManager = UnifiedConnectionManager()
    
    logInfo("GameWebSocket initialized with UnifiedConnectionManager")
    
    webSocket("/game") {
        val sessionId = java.util.UUID.randomUUID().toString()
        connectionManager.addConnection(sessionId, this)
        logDebug("New WebSocket connection established: $sessionId")
        
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    
                    try {
                        val message = Json.decodeFromString<ClientMessage>(text)
                        logDebug("Received message: ${message::class.simpleName} from session $sessionId")
                        
                        when (message) {
                            is ClientMessage.CreateRoom -> {
                                handleCreateRoom(message, sessionId, connectionManager, gameService, this)
                            }
                            
                            is ClientMessage.JoinRoom -> {
                                handleJoinRoom(message, sessionId, connectionManager, gameService, this)
                            }
                            
                            is ClientMessage.GetRoomList -> {
                                handleGetRoomList(gameService, this)
                            }
                            
                            is ClientMessage.DrawCard -> {
                                handleDrawCard(message, gameService, this)
                            }
                            
                            is ClientMessage.HealthChange -> {
                                handleHealthChange(message, connectionManager, gameService)
                            }
                            
                            is ClientMessage.PlayCard -> {
                                handlePlayCard(message, connectionManager, gameService)
                            }
                            
                            is ClientMessage.RespondToCard -> {
                                handleRespondToCard(message, connectionManager, gameService)
                            }
                            
                            is ClientMessage.StartGame -> {
                                handleStartGame(message, connectionManager, gameService)
                            }
                            
                            is ClientMessage.EndTurn -> {
                                handleEndTurn(message, connectionManager, gameService)
                            }
                            
                            is ClientMessage.AdjustPlayerOrder -> {
                                handleAdjustPlayerOrder(message, connectionManager, gameService)
                            }
                            
                            is ClientMessage.SelectAbundantHarvestCard -> {
                                handleSelectAbundantHarvestCard(message, connectionManager, gameService)
                            }
                        }
                    } catch (e: Exception) {
                        logError("Error processing message", e)
                        e.printStack()
                        send(Json.encodeToString<ServerMessage>(Error("处理消息时发生错误: ${e.message}")))
                    }
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            logDebug("WebSocket connection closed: $sessionId")
            // 客户端断开连接
        } finally {
            connectionManager.removeConnection(sessionId)
            logInfo("Session cleanup completed for: $sessionId")
        }
    }
}

// ======================== 处理函数 ========================

private suspend fun handleCreateRoom(
    message: ClientMessage.CreateRoom,
    sessionId: String,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService,
    session: WebSocketSession
) {
    logInfo("Creating room: ${message.roomName} for player: ${message.playerName}")
    val room = gameService.createRoom(message.roomName, message.playerName)
    val player = room.players.first()
    connectionManager.registerPlayer(player.id, sessionId)
    logInfo("Room created successfully: ${room.id}, player registered: ${player.id}")
    
    session.send(Json.encodeToString<ServerMessage>(RoomCreated(room)))
}

private suspend fun handleJoinRoom(
    message: ClientMessage.JoinRoom,
    sessionId: String,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService,
    session: WebSocketSession
) {
    logInfo("Player ${message.playerName} attempting to join room: ${message.roomId}")
    val result = gameService.joinRoom(message.roomId, message.playerName, message.spectateOnly)
    if (result != null) {
        val (room, player, spectator) = result
        
        if (player != null) {
            // 玩家加入或重连
            connectionManager.registerPlayer(player.id, sessionId)
            logInfo("Player joined/reconnected: ${player.name} (${player.id})")
            session.send(Json.encodeToString<ServerMessage>(PlayerJoined(player, room)))
        } else if (spectator != null) {
            // 观战者加入或重连
            connectionManager.registerSpectator(spectator.id, sessionId)
            logInfo("Spectator joined/reconnected: ${spectator.name} (${spectator.id})")
            session.send(Json.encodeToString<ServerMessage>(SpectatorJoined(spectator, room)))
        }
        
        // 通知房间内所有人
        connectionManager.broadcastToRoom(room, GameStateUpdate(room))
    } else {
        logWarn("Failed to join room: ${message.roomId}")
        session.send(Json.encodeToString<ServerMessage>(Error("无法加入房间")))
    }
}

private suspend fun handleGetRoomList(
    gameService: GameService,
    session: WebSocketSession
) {
    val rooms = gameService.getAllRooms()
    logDebug("Sending room list: ${rooms.size} rooms")
    session.send(Json.encodeToString<ServerMessage>(RoomList(rooms)))
}

private suspend fun handleDrawCard(
    message: ClientMessage.DrawCard,
    gameService: GameService,
    session: WebSocketSession
) {
    val playerId = message.playerId
    val roomId = findPlayerRoom(gameService, playerId)
    logDebug("Player $playerId drawing card in room $roomId")
    
    if (roomId != null) {
        val cards = gameService.drawCard(roomId, playerId, 1)
        if (cards.isNotEmpty()) {
            logDebug("Cards drawn: ${cards.map { it.name }}")
            session.send(Json.encodeToString<ServerMessage>(CardsDrawn(playerId, cards)))
        }
    }
}

private suspend fun handleHealthChange(
    message: ClientMessage.HealthChange,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService
) {
    val playerId = message.playerId
    val roomId = findPlayerRoom(gameService, playerId)
    logDebug("Health change for player $playerId: ${message.amount}")
    
    if (roomId != null) {
        val newHealth = gameService.updatePlayerHealth(roomId, playerId, message.amount)
        if (newHealth != null) {
            val room = gameService.getRoom(roomId)!!
            logDebug("Player $playerId health updated to $newHealth")
            connectionManager.broadcastToRoom(room, HealthUpdated(playerId, newHealth))
        }
    }
}

private suspend fun handlePlayCard(
    message: ClientMessage.PlayCard,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService
) {
    logInfo("PlayCard request: player=${message.playerId}, card=${message.cardId}, targets=${message.targetIds}")
    
    val roomId = findPlayerRoom(gameService, message.playerId)
    if (roomId == null) {
        logError("Room not found for player ${message.playerId}")
        connectionManager.sendToPlayer(message.playerId, Error("房间不存在"))
        return
    }
    
    // 使用新的Result<T>模式
    gameService.playCard(roomId, message.playerId, message.cardId, message.targetIds)
        .onSuccess { context ->
            logInfo("Card execution started: ${context.id}")
            handleCardExecution(context, roomId, connectionManager, gameService)
        }
        .onFailure { error ->
            val errorMsg = when (error) {
                is CardExecutionException -> "卡牌执行失败: ${error.message}"
                else -> "卡牌执行失败: ${error.message}"
            }
            logError("PlayCard failed: $errorMsg", error)
            connectionManager.sendToPlayer(message.playerId, Error(errorMsg))
        }
}

private suspend fun handleRespondToCard(
    message: ClientMessage.RespondToCard,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService
) {
    logInfo("RespondToCard request: player=${message.playerId}, responseCard=${message.responseCardId}, accept=${message.accept}")
    
    val roomId = findPlayerRoom(gameService, message.playerId)
    if (roomId == null) {
        logError("Room not found for player ${message.playerId}")
        return
    }
    
    val context = gameService.respondToCardNew(roomId, message.playerId, message.responseCardId, message.accept)
    if (context != null) {
        handleCardExecution(context, roomId, connectionManager, gameService)
    } else {
        logError("Failed to process response")
        connectionManager.sendToPlayer(message.playerId, Error("响应失败"))
    }
}

private suspend fun handleCardExecution(
    context: CardExecutionContext,
    roomId: String,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService
) {
    val room = gameService.getRoom(roomId) ?: return
    
    when (context.phase) {
        CardExecutionPhase.NULLIFICATION -> {
            // 处理无懈可击阶段
            val targetPlayerIds = gameService.getAllNullificationTargets(roomId)
            if (targetPlayerIds.isEmpty()) {
                // 没有人可以响应，直接进入下一阶段
                val updatedContext = gameService.executeCardEffect(roomId, context.id)
                updatedContext?.let { handleCardExecution(it, roomId, connectionManager, gameService) }
                return
            }
            
            // 广播无懈可击阶段开始
            connectionManager.broadcastToRoom(room, NullificationPhaseStarted(
                executionId = context.id,
                cardName = context.card.name,
                casterName = context.caster.name,
                targetPlayerIds = targetPlayerIds,
                room = room
            ))
            
            // 向可以响应的玩家发送响应请求
            targetPlayerIds.forEach { playerId ->
                connectionManager.sendToPlayer(playerId, ResponseRequired(
                    executionId = context.id,
                    targetPlayerId = playerId,
                    responseType = "NULLIFICATION",
                    originalCard = context.card,
                    casterName = context.caster.name
                ))
            }
        }
        
        CardExecutionPhase.RESOLUTION -> {
            // 执行卡牌效果
            val updatedContext = gameService.executeCardEffect(roomId, context.id)
            updatedContext?.let { handleCardExecution(it, roomId, connectionManager, gameService) }
        }
        
        CardExecutionPhase.SPECIAL_EXECUTION -> {
            // 处理特殊执行阶段
            val targetPlayerId = gameService.getCurrentResponseTarget(roomId)
            if (targetPlayerId != null) {
                val targetPlayerName = room.players.find { it.id == targetPlayerId }?.name ?: "未知"
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
                
                connectionManager.broadcastToRoom(room, SpecialExecutionStarted(
                    executionId = context.id,
                    cardName = context.card.name,
                    currentPlayerId = targetPlayerId,
                    currentPlayerName = targetPlayerName,
                    availableOptions = availableOptions,
                    room = room
                ))
                
                connectionManager.sendToPlayer(targetPlayerId, ResponseRequired(
                    executionId = context.id,
                    targetPlayerId = targetPlayerId,
                    responseType = "SPECIAL_SELECTION",
                    originalCard = context.card,
                    casterName = context.caster.name,
                    availableOptions = availableOptions
                ))
            }
        }
        
        CardExecutionPhase.COMPLETED -> {
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
            
            // 广播卡牌执行完成
            connectionManager.broadcastToRoom(room, CardExecutionCompleted(
                executionId = context.id,
                success = context.result?.success ?: false,
                blocked = context.isBlocked,
                message = context.result?.message ?: "执行完成",
                room = room
            ))
            
            // 广播游戏状态更新
            connectionManager.broadcastToRoom(room, GameStateUpdate(room))
            
            // 清理执行上下文
            gameService.cleanupCardExecution(roomId, context.id)
        }
        
        else -> {
            // 广播卡牌执行开始
            connectionManager.broadcastToRoom(room, CardExecutionStarted(
                executionId = context.id,
                casterName = context.caster.name,
                cardName = context.card.name,
                targetNames = context.finalTargets.map { it.name },
                room = room
            ))
        }
    }
}

private suspend fun handleStartGame(
    message: ClientMessage.StartGame,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService
) {
    logInfo("Starting game in room: ${message.roomId}")
    val result = gameService.startGame(message.roomId)
    if (result != null) {
        val (room, initialCards) = result
        logInfo("Game started successfully: ${room.players.size} players")
        
        // 广播游戏开始消息
        connectionManager.broadcastToRoom(room, GameStarted(room))
        
        // 向每个玩家发送初始手牌
        initialCards.forEach { (playerId, cards) ->
            logDebug("Dealing ${cards.size} initial cards to player $playerId")
            connectionManager.sendToPlayer(playerId, InitialCardsDealt(playerId, cards))
        }
        
        // 通知第一个玩家开始回合
        room.currentPlayer?.let { currentPlayer ->
            logInfo("Starting turn for player: ${currentPlayer.name}")
            connectionManager.sendToPlayer(currentPlayer.id, TurnStarted(currentPlayer.id, room.gamePhase))
        }
    }
}

private suspend fun handleEndTurn(
    message: ClientMessage.EndTurn,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService
) {
    val playerId = message.playerId
    val roomId = findPlayerRoom(gameService, playerId)
    logDebug("Ending turn for player $playerId in room $roomId")
    
    if (roomId != null) {
        val room = gameService.nextTurn(roomId)
        if (room != null) {
            logDebug("Turn advanced successfully")
            connectionManager.broadcastToRoom(room, GameStateUpdate(room))
            
            // 通知当前玩家开始回合
            room.currentPlayer?.let { currentPlayer ->
                logInfo("Next turn started for player: ${currentPlayer.name}")
                connectionManager.sendToPlayer(currentPlayer.id, TurnStarted(currentPlayer.id, room.gamePhase))
            }
        }
    }
}

private suspend fun handleAdjustPlayerOrder(
    message: ClientMessage.AdjustPlayerOrder,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService
) {
    logInfo("Adjusting player order in room: ${message.roomId}")
    val room = gameService.adjustPlayerOrder(message.roomId, message.newOrder)
    if (room != null) {
        logInfo("Player order adjusted successfully")
        connectionManager.broadcastToRoom(room, PlayerOrderChanged(room))
    }
}

private suspend fun handleSelectAbundantHarvestCard(
    message: ClientMessage.SelectAbundantHarvestCard,
    connectionManager: UnifiedConnectionManager,
    gameService: GameService
) {
    val playerId = message.playerId
    val roomId = findPlayerRoom(gameService, playerId)
    logInfo("Player $playerId selecting abundant harvest card: ${message.selectedCardId}")
    
    if (roomId != null) {
        val selectedCard = gameService.selectAbundantHarvestCard(roomId, playerId, message.selectedCardId)
        if (selectedCard != null) {
            val room = gameService.getRoom(roomId)!!
            val pendingResponse = room.pendingResponse
            logInfo("Player selected card: ${selectedCard.name}")
            
            // 广播选择结果
            connectionManager.broadcastToRoom(room, AbundantHarvestCardSelected(
                playerId = playerId,
                playerName = room.players.find { it.id == playerId }?.name ?: "未知",
                selectedCard = selectedCard,
                remainingCards = pendingResponse?.availableCards ?: emptyList(),
                room = room
            ))
            
            if (pendingResponse == null) {
                // 五谷丰登完成
                logInfo("Abundant harvest completed")
                connectionManager.broadcastToRoom(room, AbundantHarvestCompleted(
                    selections = room.lastAbundantHarvestSelections,
                    room = room
                ))
            } else {
                // 通知下一个玩家选择
                logDebug("Notifying next player to select: ${pendingResponse.targetPlayerId}")
                connectionManager.sendToPlayer(pendingResponse.targetPlayerId, AbundantHarvestSelection(
                    playerId = pendingResponse.targetPlayerId,
                    playerName = room.players.find { it.id == pendingResponse.targetPlayerId }?.name ?: "未知",
                    availableCards = pendingResponse.availableCards,
                    room = room
                ))
            }
        }
    }
}

// ======================== 辅助函数 ========================

private fun findPlayerRoom(gameService: GameService, playerId: String): String? {
    return gameService.getAllRooms().find { room ->
        room.players.any { it.id == playerId }
    }?.id
}