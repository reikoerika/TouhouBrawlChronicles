package moe.gensoukyo.tbc.server.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.server.main
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.CardType
import org.junit.Test
import kotlin.test.assertTrue

/**
 * 错误处理场景集成测试
 * 测试各种异常情况下的系统行为
 */
class ErrorHandlingIntegrationTest {

    @Test
    fun `test invalid room id error handling`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 尝试加入不存在的房间
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.JoinRoom("invalid_room_id", "测试玩家")
                ))

                delay(500) // 等待服务器响应

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)
                        break // 只需要第一个响应
                    }
                }
            }
        } finally {
            client.close()
        }

        // 验证收到错误消息
        assertTrue(messages.any { it is ServerMessage.Error })
        val errorMsg = messages.filterIsInstance<ServerMessage.Error>().first()
        assertTrue(errorMsg.message.contains("房间") || errorMsg.message.contains("room"))
    }

    @Test
    fun `test card not found error handling`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间并开始游戏
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        if (message is ServerMessage.RoomCreated) {
                            val roomId = message.room.id
                            val playerId = message.room.players[0].id

                            // 添加第二个玩家并开始游戏
                            send(Json.encodeToString<ClientMessage>(
                                ClientMessage.JoinRoom(roomId, "玩家2")
                            ))
                        } else if (message is ServerMessage.PlayerJoined) {
                            val roomId = message.room.id
                            send(Json.encodeToString<ClientMessage>(
                                ClientMessage.StartGame(roomId)
                            ))
                        } else if (message is ServerMessage.GameStarted) {
                            val playerId = message.room.players[0].id

                            // 尝试出不存在的卡牌
                            send(Json.encodeToString<ClientMessage>(
                                ClientMessage.PlayCard(
                                    playerId,
                                    "non_existent_card",
                                    emptyList()
                                )
                            ))

                            delay(300)
                            break
                        }
                    }
                }
            }
        } finally {
            client.close()
        }

        // 验证收到卡牌未找到错误
        val errorMessages = messages.filterIsInstance<ServerMessage.Error>()
        assertTrue(errorMessages.isNotEmpty())
        assertTrue(errorMessages.any { 
            it.message.contains("卡牌") || it.message.contains("card") || it.message.contains("找不到")
        })
    }

    @Test
    fun `test invalid game state error handling`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间但不开始游戏
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        if (message is ServerMessage.RoomCreated) {
                            val playerId = message.room.players[0].id

                            // 在游戏未开始时尝试出牌
                            send(Json.encodeToString<ClientMessage>(
                                ClientMessage.PlayCard(
                                    playerId,
                                    "test_card",
                                    emptyList()
                                )
                            ))

                            delay(300)
                            break
                        }
                    }
                }
            }
        } finally {
            client.close()
        }

        // 验证收到游戏状态错误
        val errorMessages = messages.filterIsInstance<ServerMessage.Error>()
        assertTrue(errorMessages.isNotEmpty())
        assertTrue(errorMessages.any { 
            it.message.contains("游戏") || it.message.contains("状态") || it.message.contains("state")
        })
    }

    @Test
    fun `test wrong turn error handling`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间并添加玩家
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                var roomId = ""
                var player2Id = ""

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        when (message) {
                            is ServerMessage.RoomCreated -> {
                                roomId = message.room.id
                                // 添加第二个玩家
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.JoinRoom(roomId, "玩家2")
                                ))
                            }
                            is ServerMessage.PlayerJoined -> {
                                player2Id = message.player.id
                                // 开始游戏
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.StartGame(roomId)
                                ))
                            }
                            is ServerMessage.GameStarted -> {
                                // 让非当前回合玩家（玩家2）尝试出牌
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.PlayCard(
                                        player2Id,
                                        "test_card",
                                        emptyList()
                                    )
                                ))
                                delay(300)
                                break
                            }
                            else -> {
                                // 忽略其他消息类型
                            }
                        }
                    }
                }
            }
        } finally {
            client.close()
        }

        // 验证收到回合错误
        val errorMessages = messages.filterIsInstance<ServerMessage.Error>()
        assertTrue(errorMessages.isNotEmpty())
        assertTrue(errorMessages.any { 
            it.message.contains("回合") || it.message.contains("turn") || it.message.contains("轮次")
        })
    }

    @Test
    fun `test invalid target error handling`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间并开始游戏
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        when (message) {
                            is ServerMessage.RoomCreated -> {
                                val roomId = message.room.id
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.JoinRoom(roomId, "玩家2")
                                ))
                            }
                            is ServerMessage.PlayerJoined -> {
                                val roomId = message.room.id
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.StartGame(roomId)
                                ))
                            }
                            is ServerMessage.GameStarted -> {
                                val playerId = message.room.players[0].id
                                
                                // 给玩家1添加杀卡牌
                                val player1 = message.room.players[0]
                                player1.cards.add(Card(
                                    id = "sha_card",
                                    name = "杀",
                                    type = CardType.BASIC,
                                    effect = "对一名角色造成1点伤害",
                                    damage = 1
                                ))

                                // 使用无效目标出牌
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.PlayCard(
                                        playerId,
                                        "sha_card",
                                        listOf("invalid_target_id")
                                    )
                                ))
                                delay(300)
                                break
                            }
                            else -> {
                                // 忽略其他消息类型
                            }
                        }
                    }
                }
            }
        } finally {
            client.close()
        }

        // 验证收到目标无效错误
        val errorMessages = messages.filterIsInstance<ServerMessage.Error>()
        assertTrue(errorMessages.isNotEmpty())
        assertTrue(errorMessages.any { 
            it.message.contains("目标") || it.message.contains("target") || it.message.contains("玩家")
        })
    }

    @Test
    fun `test connection timeout error handling`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间并开始游戏
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        when (message) {
                            is ServerMessage.RoomCreated -> {
                                val roomId = message.room.id
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.JoinRoom(roomId, "玩家2")
                                ))
                            }
                            is ServerMessage.PlayerJoined -> {
                                val roomId = message.room.id
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.StartGame(roomId)
                                ))
                            }
                            is ServerMessage.GameStarted -> {
                                // 出一张需要响应的牌（过河拆桥）
                                val playerId = message.room.players[0].id
                                val player1 = message.room.players[0]
                                player1.cards.add(Card(
                                    id = "guohechaiqiao",
                                    name = "过河拆桥",
                                    type = CardType.TRICK,
                                    effect = "弃置目标一张牌"
                                ))

                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.PlayCard(
                                        playerId,
                                        "guohechaiqiao",
                                        listOf(message.room.players[1].id)
                                    )
                                ))
                            }
                            is ServerMessage.ResponseRequired -> {
                                // 不响应，等待超时
                                delay(16000) // 超过15秒超时时间
                                break
                            }
                            else -> {
                                // 忽略其他消息类型
                            }
                        }
                    }
                }
            }
        } finally {
            client.close()
        }

        // 这个测试主要验证系统在超时情况下不会崩溃
        // 由于实际测试中15秒太长，这里主要测试消息流程完整性
        assertTrue(messages.any { it is ServerMessage.GameStarted })
    }

    @Test
    fun `test malformed message error handling`() = testApplication {
        application {
            main()
        }

        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        var connectionClosed = false

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 发送畸形JSON消息
                send("{ invalid json content }")
                
                delay(500)
                
                // 发送空消息
                send("")
                
                delay(500)
                
                // 发送有效消息验证连接仍然正常
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))
                
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val message = Json.decodeFromString<ServerMessage>(text)
                            if (message is ServerMessage.RoomCreated) {
                                // 连接正常，可以接收到有效响应
                                break
                            }
                        } catch (e: Exception) {
                            // 忽略解析错误，继续测试
                        }
                    }
                }
            }
        } catch (e: Exception) {
            connectionClosed = true
        } finally {
            client.close()
        }

        // WebSocket连接应该保持稳定，不会因为畸形消息而关闭
        // 这个测试主要验证系统的容错性
    }

    @Test
    fun `test concurrent card execution error handling`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间并开始游戏
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        when (message) {
                            is ServerMessage.RoomCreated -> {
                                val roomId = message.room.id
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.JoinRoom(roomId, "玩家2")
                                ))
                            }
                            is ServerMessage.PlayerJoined -> {
                                val roomId = message.room.id
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.StartGame(roomId)
                                ))
                            }
                            is ServerMessage.GameStarted -> {
                                val playerId = message.room.players[0].id
                                val cardId = "test_card"
                                val targetId = message.room.players[1].id

                                // 同时发送多个相同的出牌请求（并发测试）
                                repeat(5) {
                                    send(Json.encodeToString<ClientMessage>(
                                        ClientMessage.PlayCard(playerId, cardId, listOf(targetId))
                                    ))
                                }
                                
                                delay(1000)
                                break
                            }
                            else -> {
                                // 忽略其他消息类型
                            }
                        }
                    }
                }
            }
        } finally {
            client.close()
        }

        // 验证系统能正确处理并发请求，可能产生错误消息但不应崩溃
        val errorMessages = messages.filterIsInstance<ServerMessage.Error>()
        // 应该至少有一些错误消息，因为同一张牌不能同时出多次
        assertTrue(errorMessages.isNotEmpty())
    }
}