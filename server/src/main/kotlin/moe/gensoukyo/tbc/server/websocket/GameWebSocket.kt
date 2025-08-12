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
                                val result = gameService.joinRoom(message.roomId, message.playerName)
                                if (result != null) {
                                    val (room, player) = result
                                    playerSessions[player.id] = sessionId
                                    
                                    // 通知加入成功
                                    send(Json.encodeToString<ServerMessage>(ServerMessage.PlayerJoined(player, room)))
                                    
                                    // 通知房间内其他玩家
                                    broadcastToRoom(connections, playerSessions, room.id, room, gameService) {
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
                                    val card = gameService.drawCard(roomId, playerId)
                                    if (card != null) {
                                        send(Json.encodeToString<ServerMessage>(ServerMessage.CardDrawn(playerId, card)))
                                    }
                                }
                            }
                            
                            is ClientMessage.HealthChange -> {
                                val playerId = message.playerId
                                val roomId = findPlayerRoom(gameService, playerId)
                                
                                if (roomId != null) {
                                    val newHealth = gameService.updatePlayerHealth(roomId, playerId, message.amount)
                                    if (newHealth != null) {
                                        broadcastToRoom(connections, playerSessions, roomId, gameService.getRoom(roomId)!!, gameService) {
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
                                        broadcastToRoom(connections, playerSessions, roomId, room, gameService) {
                                            ServerMessage.GameStateUpdate(room)
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
            // 清理玩家会话映射
            playerSessions.entries.removeIf { it.value == sessionId }
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
    roomId: String,
    room: moe.gensoukyo.tbc.shared.model.GameRoom,
    gameService: GameService,
    messageFactory: () -> ServerMessage
) {
    val message = Json.encodeToString(messageFactory())
    
    room.players.forEach { player ->
        val sessionId = playerSessions[player.id]
        if (sessionId != null) {
            val session = connections[sessionId]
            session?.send(message)
        }
    }
}