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
                                    val playedCard = gameService.playCard(roomId, playerId, message.cardId, message.targetIds)
                                    if (playedCard != null) {
                                        val room = gameService.getRoom(roomId)!!
                                        broadcastToRoom(connections, playerSessions, spectatorSessions, roomId, room, gameService) {
                                            ServerMessage.CardPlayed(playedCard, room)
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