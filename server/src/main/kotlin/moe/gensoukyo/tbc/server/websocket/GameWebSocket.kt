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
import moe.gensoukyo.tbc.shared.messages.PlayCardMessage
import moe.gensoukyo.tbc.shared.messages.RespondToCardMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage.AbundantHarvestCardSelected
import moe.gensoukyo.tbc.shared.messages.ServerMessage.AbundantHarvestCompleted
import moe.gensoukyo.tbc.shared.messages.ServerMessage.AbundantHarvestSelection
import moe.gensoukyo.tbc.shared.messages.ServerMessage.CardsDrawn
import moe.gensoukyo.tbc.shared.messages.ServerMessage.Error
import moe.gensoukyo.tbc.shared.messages.ServerMessage.GameStarted
import moe.gensoukyo.tbc.shared.messages.ServerMessage.GameStateUpdate
import moe.gensoukyo.tbc.shared.messages.ServerMessage.HealthUpdated
import moe.gensoukyo.tbc.shared.messages.ServerMessage.InitialCardsDealt
import moe.gensoukyo.tbc.shared.messages.ServerMessage.PlayerJoined
import moe.gensoukyo.tbc.shared.messages.ServerMessage.PlayerOrderChanged
import moe.gensoukyo.tbc.shared.messages.ServerMessage.RoomCreated
import moe.gensoukyo.tbc.shared.messages.ServerMessage.RoomList
import moe.gensoukyo.tbc.shared.messages.ServerMessage.SpectatorJoined
import moe.gensoukyo.tbc.shared.messages.ServerMessage.TurnStarted
import moe.gensoukyo.tbc.shared.utils.logDebug
import moe.gensoukyo.tbc.shared.utils.logError
import moe.gensoukyo.tbc.shared.utils.logInfo
import moe.gensoukyo.tbc.shared.utils.logWarn
import java.util.concurrent.ConcurrentHashMap

fun Route.gameWebSocket(gameService: GameService) {
    val connections = ConcurrentHashMap<String, WebSocketSession>()
    val playerSessions = ConcurrentHashMap<String, String>() // playerId to sessionId
    val spectatorSessions = ConcurrentHashMap<String, String>() // spectatorId to sessionId
    
    // 创建统一的CardExecutionHandler
    val cardExecutionHandler = CardExecutionHandler(
        gameService = gameService,
        playerSessions = playerSessions,
        spectatorSessions = spectatorSessions
    )
    
    logInfo("GameWebSocket initialized with unified CardExecutionHandler")
    
    webSocket("/game") {
        val sessionId = java.util.UUID.randomUUID().toString()
        connections[sessionId] = this
        logDebug("New WebSocket connection established: $sessionId")
        
        // 同步连接到CardExecutionHandler
        cardExecutionHandler.syncConnections(connections)
        
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    
                    try {
                        val message = Json.decodeFromString<ClientMessage>(text)
                        logDebug("Received message: ${message::class.simpleName} from session $sessionId")
                        
                        when (message) {
                            is ClientMessage.CreateRoom -> {
                                logInfo("Creating room: ${message.roomName} for player: ${message.playerName}")
                                val room = gameService.createRoom(message.roomName, message.playerName)
                                val player = room.players.first()
                                playerSessions[player.id] = sessionId
                                logInfo("Room created successfully: ${room.id}, player registered: ${player.id}")
                                
                                send(Json.encodeToString<ServerMessage>(RoomCreated(room)))
                            }
                            
                            is ClientMessage.JoinRoom -> {
                                logInfo("Player ${message.playerName} attempting to join room: ${message.roomId}")
                                val result = gameService.joinRoom(message.roomId, message.playerName, message.spectateOnly)
                                if (result != null) {
                                    val (room, player, spectator) = result
                                    
                                    if (player != null) {
                                        // 玩家加入或重连
                                        playerSessions[player.id] = sessionId
                                        logInfo("Player joined/reconnected: ${player.name} (${player.id})")
                                        send(Json.encodeToString<ServerMessage>(PlayerJoined(player, room)))
                                    } else if (spectator != null) {
                                        // 观战者加入或重连
                                        spectatorSessions[spectator.id] = sessionId
                                        logInfo("Spectator joined/reconnected: ${spectator.name} (${spectator.id})")
                                        send(Json.encodeToString<ServerMessage>(SpectatorJoined(spectator, room)))
                                    }
                                    
                                    // 同步连接更新
                                    cardExecutionHandler.syncConnections(connections)
                                    
                                    // 通知房间内所有人
                                    broadcastToRoom(connections, playerSessions, spectatorSessions, room.id, room, gameService) {
                                        GameStateUpdate(room)
                                    }
                                } else {
                                    logWarn("Failed to join room: ${message.roomId}")
                                    send(Json.encodeToString<ServerMessage>(Error("无法加入房间")))
                                }
                            }
                            
                            is ClientMessage.GetRoomList -> {
                                val rooms = gameService.getAllRooms()
                                logDebug("Sending room list: ${rooms.size} rooms")
                                send(Json.encodeToString<ServerMessage>(RoomList(rooms)))
                            }
                            
                            is ClientMessage.DrawCard -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                logDebug("Player $playerId drawing card in room $roomId")
                                
                                if (roomId != null) {
                                    val cards = gameService.drawCard(roomId, playerId, 1)
                                    if (cards.isNotEmpty()) {
                                        logDebug("Cards drawn: ${cards.map { it.name }}")
                                        send(Json.encodeToString<ServerMessage>(CardsDrawn(playerId, cards)))
                                    }
                                }
                            }
                            
                            is ClientMessage.HealthChange -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                logDebug("Health change for player $playerId: ${message.amount}")
                                
                                if (roomId != null) {
                                    val newHealth = gameService.updatePlayerHealth(roomId, playerId, message.amount)
                                    if (newHealth != null) {
                                        logDebug("Player $playerId health updated to $newHealth")
                                        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, gameService.getRoom(roomId)!!, gameService) {
                                            HealthUpdated(playerId, newHealth)
                                        }
                                    }
                                }
                            }
                            
                            // === 统一的出牌处理 ===
                            is ClientMessage.PlayCard -> {
                                logInfo("PlayCard request: player=${message.playerId}, card=${message.cardId}, targets=${message.targetIds}")
                                val result = cardExecutionHandler.handlePlayCard(
                                    PlayCardMessage.fromClientMessage(message),
                                    connections
                                )
                                
                                if (result.isFailure) {
                                    val playerId = message.playerId
                                    val sessionId = playerSessions[playerId]
                                    val session = connections[sessionId]
                                    val errorMsg = "卡牌执行失败: ${result.exceptionOrNull()?.message}"
                                    logError("PlayCard failed: $errorMsg")
                                    session?.send(Json.encodeToString<ServerMessage>(Error(errorMsg)))
                                } else {
                                    logInfo("PlayCard handled successfully by CardExecutionHandler")
                                }
                            }
                            
                            // === 统一的响应处理 ===
                            is ClientMessage.RespondToCard -> {
                                logInfo("RespondToCard request: player=${message.playerId}, responseCard=${message.responseCardId}, accept=${message.accept}")
                                val result = cardExecutionHandler.handleResponse(
                                    RespondToCardMessage.fromClientMessage(message),
                                    connections
                                )
                                
                                if (result.isFailure) {
                                    val playerId = message.playerId
                                    val sessionId = playerSessions[playerId]
                                    val session = connections[sessionId]
                                    val errorMsg = "响应失败: ${result.exceptionOrNull()?.message}"
                                    logError("RespondToCard failed: $errorMsg")
                                    session?.send(Json.encodeToString<ServerMessage>(Error(errorMsg)))
                                } else {
                                    logInfo("RespondToCard handled successfully by CardExecutionHandler")
                                }
                            }
                            
                            is ClientMessage.StartGame -> {
                                logInfo("Starting game in room: ${message.roomId}")
                                val result = gameService.startGame(message.roomId)
                                if (result != null) {
                                    val (room, initialCards) = result
                                    logInfo("Game started successfully: ${room.players.size} players")
                                    
                                    // 同步连接更新
                                    cardExecutionHandler.syncConnections(connections)
                                    
                                    // 首先广播游戏开始消息
                                    broadcastToRoom(connections, playerSessions, spectatorSessions, message.roomId, room, gameService) {
                                        GameStarted(room)
                                    }
                                    
                                    // 然后向每个玩家发送他们的初始手牌
                                    initialCards.forEach { (playerId, cards) ->
                                        val sessionId = playerSessions[playerId]
                                        val session = connections[sessionId]
                                        logDebug("Dealing ${cards.size} initial cards to player $playerId")
                                        session?.send(
                                            Json.encodeToString<ServerMessage>(
                                                InitialCardsDealt(playerId, cards)
                                            )
                                        )
                                    }
                                    
                                    // 最后通知第一个玩家（主公）开始回合
                                    room.currentPlayer?.let { currentPlayer ->
                                        val currentPlayerSessionId = playerSessions[currentPlayer.id]
                                        val currentPlayerSession = connections[currentPlayerSessionId]
                                        logInfo("Starting turn for player: ${currentPlayer.name}")
                                        currentPlayerSession?.send(
                                            Json.encodeToString<ServerMessage>(
                                                TurnStarted(currentPlayer.id, room.gamePhase)
                                            )
                                        )
                                    }
                                }
                            }
                            
                            is ClientMessage.EndTurn -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                logDebug("Ending turn for player $playerId in room $roomId")
                                
                                if (roomId != null) {
                                    val room = gameService.nextTurn(roomId)
                                    if (room != null) {
                                        logDebug("Turn advanced successfully")
                                        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                            GameStateUpdate(room)
                                        }
                                        
                                        // 通知当前玩家开始回合
                                        room.currentPlayer?.let { currentPlayer ->
                                            val currentPlayerSessionId = playerSessions[currentPlayer.id]
                                            val currentPlayerSession = connections[currentPlayerSessionId]
                                            logInfo("Next turn started for player: ${currentPlayer.name}")
                                            currentPlayerSession?.send(
                                                Json.encodeToString<ServerMessage>(
                                                    TurnStarted(currentPlayer.id, room.gamePhase)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            
                            is ClientMessage.AdjustPlayerOrder -> {
                                logInfo("Adjusting player order in room: ${message.roomId}")
                                val room = gameService.adjustPlayerOrder(message.roomId, message.newOrder)
                                if (room != null) {
                                    logInfo("Player order adjusted successfully")
                                    broadcastToRoom(connections, playerSessions, spectatorSessions, message.roomId, room, gameService) {
                                        PlayerOrderChanged(room)
                                    }
                                }
                            }
                            
                            is ClientMessage.SelectAbundantHarvestCard -> {
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
                                        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                            AbundantHarvestCardSelected(
                                                playerId = playerId,
                                                playerName = room.players.find { it.id == playerId }?.name ?: "未知",
                                                selectedCard = selectedCard,
                                                remainingCards = pendingResponse?.availableCards ?: emptyList(),
                                                room = room
                                            )
                                        }
                                        
                                        if (pendingResponse == null) {
                                            // 五谷丰登完成
                                            logInfo("Abundant harvest completed")
                                            broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                                AbundantHarvestCompleted(
                                                    selections = room.lastAbundantHarvestSelections,
                                                    room = room
                                                )
                                            }
                                        } else {
                                            // 通知下一个玩家选择
                                            val nextPlayerSessionId = playerSessions[pendingResponse.targetPlayerId]
                                            val nextPlayerSession = connections[nextPlayerSessionId]
                                            logDebug("Notifying next player to select: ${pendingResponse.targetPlayerId}")
                                            nextPlayerSession?.send(
                                                Json.encodeToString<ServerMessage>(
                                                    AbundantHarvestSelection(
                                                        playerId = pendingResponse.targetPlayerId,
                                                        playerName = room.players.find { it.id == pendingResponse.targetPlayerId }?.name ?: "未知",
                                                        availableCards = pendingResponse.availableCards,
                                                        room = room
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logError("Error processing message", e)
                        e.printStack()
                        send(Json.encodeToString<ServerMessage>(Error("处理消息时发生错误: ${e.message}")))
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logDebug("WebSocket connection closed: $sessionId")
            // 客户端断开连接
        } finally {
            connections.remove(sessionId)
            logDebug("Cleaning up session: $sessionId")
            
            // 清理当前会话的玩家和观战者映射（但保留数据以支持重连）
            val playersToRemove = playerSessions.entries.filter { it.value == sessionId }.map { it.key }
            playersToRemove.forEach { playerId ->
                playerSessions.remove(playerId)
                logDebug("Removed player session mapping: $playerId")
            }
            
            val spectatorsToRemove = spectatorSessions.entries.filter { it.value == sessionId }.map { it.key }
            spectatorsToRemove.forEach { spectatorId ->
                spectatorSessions.remove(spectatorId)
                logDebug("Removed spectator session mapping: $spectatorId")
            }
            
            // 同步连接更新
            cardExecutionHandler.syncConnections(connections)
            logInfo("Session cleanup completed for: $sessionId")
        }
    }
}

private fun findPlayerRoom(gameService: GameService, playerId: String): String? {
    return gameService.getAllRooms().find { room ->
        room.players.any { it.id == playerId }
    }?.id
}

private suspend fun broadcastToRoom(
    connections: ConcurrentHashMap<String, WebSocketSession>,
    playerSessions: ConcurrentHashMap<String, String>,
    spectatorSessions: ConcurrentHashMap<String, String>,
    roomId: String,
    room: moe.gensoukyo.tbc.shared.model.GameRoom,
    gameService: GameService,
    messageFactory: () -> ServerMessage
) {
    val message = Json.encodeToString(messageFactory())
    
    // 广播给玩家
    room.players.forEach { player ->
        val sessionId = playerSessions[player.id]
        if (sessionId != null) {
            val session = connections[sessionId]
            session?.send(message)
        }
    }
    
    // 广播给观战者
    room.spectators.forEach { spectator ->
        val sessionId = spectatorSessions[spectator.id]
        if (sessionId != null) {
            val session = connections[sessionId]
            session?.send(message)
        }
    }
}