package moe.gensoukyo.tbc.server.websocket

import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.server.service.GameService
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.CardType
import java.util.concurrent.ConcurrentHashMap

fun Route.gameWebSocket(gameService: GameService) {
    val connections = ConcurrentHashMap<String, WebSocketSession>()
    val playerSessions = ConcurrentHashMap<String, String>() // playerId to sessionId
    val spectatorSessions = ConcurrentHashMap<String, String>() // spectatorId to sessionId
    
    webSocket("/game") {
        val sessionId = java.util.UUID.randomUUID().toString()
        connections[sessionId] = this
        
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    
                    try {
                        val message = Json.decodeFromString<ClientMessage>(text)
                        
                        when (message) {
                            is ClientMessage.CreateRoom -> {
                                val room = gameService.createRoom(message.roomName, message.playerName)
                                val player = room.players.first()
                                playerSessions[player.id] = sessionId
                                
                                send(Json.encodeToString<ServerMessage>(ServerMessage.RoomCreated(room)))
                            }
                            
                            is ClientMessage.JoinRoom -> {
                                val result = gameService.joinRoom(message.roomId, message.playerName, message.spectateOnly)
                                if (result != null) {
                                    val (room, player, spectator) = result
                                    
                                    if (player != null) {
                                        // 玩家加入或重连
                                        playerSessions[player.id] = sessionId
                                        send(Json.encodeToString<ServerMessage>(ServerMessage.PlayerJoined(player, room)))
                                    } else if (spectator != null) {
                                        // 观战者加入或重连
                                        spectatorSessions[spectator.id] = sessionId
                                        send(Json.encodeToString<ServerMessage>(ServerMessage.SpectatorJoined(spectator, room)))
                                    }
                                    
                                    // 通知房间内所有人
                                    broadcastToRoom(connections, playerSessions, spectatorSessions, room.id, room, gameService) {
                                        ServerMessage.GameStateUpdate(room)
                                    }
                                } else {
                                    send(Json.encodeToString<ServerMessage>(ServerMessage.Error("无法加入房间")))
                                }
                            }
                            
                            is ClientMessage.GetRoomList -> {
                                val rooms = gameService.getAllRooms()
                                send(Json.encodeToString<ServerMessage>(ServerMessage.RoomList(rooms)))
                            }
                            
                            is ClientMessage.DrawCard -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                
                                if (roomId != null) {
                                    val cards = gameService.drawCard(roomId, playerId, 1)
                                    if (cards.isNotEmpty()) {
                                        send(Json.encodeToString<ServerMessage>(ServerMessage.CardsDrawn(playerId, cards)))
                                    }
                                }
                            }
                            
                            is ClientMessage.HealthChange -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                
                                if (roomId != null) {
                                    val newHealth = gameService.updatePlayerHealth(roomId, playerId, message.amount)
                                    if (newHealth != null) {
                                        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, gameService.getRoom(roomId)!!, gameService) {
                                            ServerMessage.HealthUpdated(playerId, newHealth)
                                        }
                                    }
                                }
                            }
                            
                            is ClientMessage.UseCard -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                
                                if (roomId != null) {
                                    val success = gameService.useCard(roomId, playerId, message.cardId, message.targetId)
                                    if (success) {
                                        val room = gameService.getRoom(roomId)!!
                                        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                            ServerMessage.GameStateUpdate(room)
                                        }
                                    }
                                }
                            }
                            
                            is ClientMessage.StartGame -> {
                                val result = gameService.startGame(message.roomId)
                                if (result != null) {
                                    val (room, initialCards) = result
                                    
                                    // 首先广播游戏开始消息
                                    broadcastToRoom(connections, playerSessions, spectatorSessions, message.roomId, room, gameService) {
                                        ServerMessage.GameStarted(room)
                                    }
                                    
                                    // 然后向每个玩家发送他们的初始手牌
                                    initialCards.forEach { (playerId, cards) ->
                                        val sessionId = playerSessions[playerId]
                                        val session = connections[sessionId]
                                        session?.send(
                                            Json.encodeToString<ServerMessage>(
                                                ServerMessage.InitialCardsDealt(playerId, cards)
                                            )
                                        )
                                    }
                                    
                                    // 最后通知第一个玩家（主公）开始回合
                                    room.currentPlayer?.let { currentPlayer ->
                                        val currentPlayerSessionId = playerSessions[currentPlayer.id]
                                        val currentPlayerSession = connections[currentPlayerSessionId]
                                        currentPlayerSession?.send(
                                            Json.encodeToString<ServerMessage>(
                                                ServerMessage.TurnStarted(currentPlayer.id, room.gamePhase)
                                            )
                                        )
                                    }
                                }
                            }
                            
                            is ClientMessage.EndTurn -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                
                                if (roomId != null) {
                                    val room = gameService.nextTurn(roomId)
                                    if (room != null) {
                                        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                            ServerMessage.GameStateUpdate(room)
                                        }
                                        
                                        // 通知当前玩家开始回合
                                        room.currentPlayer?.let { currentPlayer ->
                                            val currentPlayerSessionId = playerSessions[currentPlayer.id]
                                            val currentPlayerSession = connections[currentPlayerSessionId]
                                            currentPlayerSession?.send(
                                                Json.encodeToString<ServerMessage>(
                                                    ServerMessage.TurnStarted(currentPlayer.id, room.gamePhase)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            
                            is ClientMessage.AdjustPlayerOrder -> {
                                val room = gameService.adjustPlayerOrder(message.roomId, message.newOrder)
                                if (room != null) {
                                    broadcastToRoom(connections, playerSessions, spectatorSessions, message.roomId, room, gameService) {
                                        ServerMessage.PlayerOrderChanged(room)
                                    }
                                }
                            }
                            
                            is ClientMessage.PlayCard -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                
                                if (roomId != null) {
                                    val result = gameService.playCard(roomId, playerId, message.cardId, message.targetIds)
                                    if (result != null) {
                                        val (playedCard, pendingResponse) = result
                                        val room = gameService.getRoom(roomId)!!
                                        
                                        if (pendingResponse != null) {
                                            if (pendingResponse.isAbundantHarvest) {
                                                // 五谷丰登特殊处理
                                                if (playedCard != null) {
                                                    broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                                        ServerMessage.CardPlayed(playedCard, room)
                                                    }
                                                }
                                                
                                                // 广播五谷丰登开始
                                                broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                                    ServerMessage.AbundantHarvestStarted(
                                                        availableCards = pendingResponse.availableCards,
                                                        currentPlayerIndex = pendingResponse.currentSelectionPlayerIndex,
                                                        room = room
                                                    )
                                                }
                                                
                                                // 通知第一个玩家选择
                                                val firstPlayerSessionId = playerSessions[pendingResponse.targetPlayerId]
                                                val firstPlayerSession = connections[firstPlayerSessionId]
                                                firstPlayerSession?.send(
                                                    Json.encodeToString<ServerMessage>(
                                                        ServerMessage.AbundantHarvestSelection(
                                                            playerId = pendingResponse.targetPlayerId,
                                                            playerName = room.players.find { it.id == pendingResponse.targetPlayerId }?.name ?: "未知",
                                                            availableCards = pendingResponse.availableCards,
                                                            room = room
                                                        )
                                                    )
                                                )
                                            } else {
                                                // 其他需要响应的卡牌处理
                                                if (playedCard != null) {
                                                    broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                                        ServerMessage.CardPlayed(playedCard, room)
                                                    }
                                                }
                                                
                                                // 发送响应请求
                                                val targetSessionId = playerSessions[pendingResponse.targetPlayerId]
                                                val targetSession = connections[targetSessionId]
                                                targetSession?.send(
                                                    Json.encodeToString<ServerMessage>(
                                                        ServerMessage.CardResponseRequired(
                                                            targetPlayerId = pendingResponse.targetPlayerId,
                                                            originalCard = pendingResponse.originalCard,
                                                            originalPlayer = room.players.find { it.id == pendingResponse.originalPlayerId }?.name ?: "未知",
                                                            responseType = pendingResponse.responseType
                                                        )
                                                    )
                                                )
                                            }
                                        } else if (playedCard != null) {
                                            // 不需要响应，直接广播结果
                                            broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                                ServerMessage.CardPlayed(playedCard, room)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            is ClientMessage.SelectAbundantHarvestCard -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                
                                if (roomId != null) {
                                    val selectedCard = gameService.selectAbundantHarvestCard(roomId, playerId, message.selectedCardId)
                                    if (selectedCard != null) {
                                        val room = gameService.getRoom(roomId)!!
                                        val pendingResponse = room.pendingResponse
                                        // 广播选择结果
                                        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                            ServerMessage.AbundantHarvestCardSelected(
                                                playerId = playerId,
                                                playerName = room.players.find { it.id == playerId }?.name ?: "未知",
                                                selectedCard = selectedCard,
                                                remainingCards = pendingResponse?.availableCards ?: emptyList(),
                                                room = room
                                            )
                                        }
                                        
                                        if (pendingResponse == null) {
                                            // 五谷丰登完成
                                            broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                                ServerMessage.AbundantHarvestCompleted(
                                                    selections = room.lastAbundantHarvestSelections,
                                                    room = room
                                                )
                                            }
                                        } else {
                                            // 通知下一个玩家选择
                                            val nextPlayerSessionId = playerSessions[pendingResponse.targetPlayerId]
                                            val nextPlayerSession = connections[nextPlayerSessionId]
                                            nextPlayerSession?.send(
                                                Json.encodeToString<ServerMessage>(
                                                    ServerMessage.AbundantHarvestSelection(
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
                            
                            is ClientMessage.RespondToCard -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                
                                if (roomId != null) {
                                    val result = gameService.respondToCard(roomId, playerId, message.responseCardId, message.accept)
                                    if (result != null) {
                                        val room = gameService.getRoom(roomId)!!
                                        
                                        // 检查是否还有待处理的响应（决斗继续）
                                        val stillPending = room.pendingResponse != null && !room.pendingResponse!!.duelFinished
                                        
                                        if (stillPending) {
                                            // 决斗继续，向下一个玩家发送响应请求
                                            val pendingResponse = room.pendingResponse!!
                                            val nextPlayerSessionId = playerSessions[pendingResponse.duelCurrentPlayer!!]
                                            val nextPlayerSession = connections[nextPlayerSessionId]
                                            
                                            nextPlayerSession?.send(
                                                Json.encodeToString<ServerMessage>(
                                                    ServerMessage.CardResponseRequired(
                                                        targetPlayerId = pendingResponse.duelCurrentPlayer!!,
                                                        originalCard = pendingResponse.originalCard,
                                                        originalPlayer = room.players.find { it.id == pendingResponse.duelInitiator }?.name ?: "未知",
                                                        responseType = pendingResponse.responseType,
                                                        timeoutMs = pendingResponse.timeoutMs
                                                    )
                                                )
                                            )
                                            
                                            // 广播中间结果给所有人
                                            broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                                ServerMessage.CardResponseReceived(
                                                    playerId = playerId,
                                                    responseCard = room.players.find { it.id == playerId }?.let { player ->
                                                        message.responseCardId?.let { cardId ->
                                                            null // 简化处理
                                                        }
                                                    },
                                                    accepted = message.accept,
                                                    room = room
                                                )
                                            }
                                        } else {
                                            // 决斗结束或其他响应完成，广播最终结果
                                            broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                                ServerMessage.CardResolved(
                                                    originalCard = result.let {
                                                        Card("", "决斗", CardType.TRICK, "")
                                                    },
                                                    responses = emptyList(),
                                                    result = result,
                                                    room = room
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        send(Json.encodeToString<ServerMessage>(ServerMessage.Error("处理消息时发生错误: ${e.message}")))
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            // 客户端断开连接
        } finally {
            connections.remove(sessionId)
            // 清理当前会话的玩家和观战者映射（但保留数据以支持重连）
            val playersToRemove = playerSessions.entries.filter { it.value == sessionId }.map { it.key }
            playersToRemove.forEach { playerId ->
                playerSessions.remove(playerId)
            }
            
            val spectatorsToRemove = spectatorSessions.entries.filter { it.value == sessionId }.map { it.key }
            spectatorsToRemove.forEach { spectatorId ->
                spectatorSessions.remove(spectatorId)
            }
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