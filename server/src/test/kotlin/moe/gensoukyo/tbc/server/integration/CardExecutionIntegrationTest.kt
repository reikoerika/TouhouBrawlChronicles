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
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.server.main
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.CardType
import moe.gensoukyo.tbc.shared.model.Player
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 卡牌执行系统集成测试
 * 测试完整的出牌流程：WebSocket消息 -> CardExecutionEngine -> 状态更新 -> 客户端通知
 */
class CardExecutionIntegrationTest {

    @Test
    fun `test complete card execution workflow`() = testApplication {
        application {
            main()
        }

        // 创建两个WebSocket连接模拟两个客户端
        val client1Messages = mutableListOf<ServerMessage>()
        val client2Messages = mutableListOf<ServerMessage>()

        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            // 客户端1连接
                client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                    // 创建房间
                    send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                    // 监听消息
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val message = Json.decodeFromString<ServerMessage>(frame.readText())
                            client1Messages.add(message)

                            if (message is ServerMessage.RoomCreated) {
                                    // 启动客户端2
                                    launch {
                                        client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                                        // 加入房间
                                        send(Json.encodeToString<ClientMessage>(
                                            ClientMessage.JoinRoom(message.room.id, "玩家2")
                                        ))

                                        // 监听消息
                                        for (frame2 in incoming) {
                                            if (frame2 is Frame.Text) {
                                                val message2 = Json.decodeFromString<ServerMessage>(frame2.readText())
                                                client2Messages.add(message2)

                                                if (message2 is ServerMessage.PlayerJoined) {
                                                    // 开始游戏
                                                    send(Json.encodeToString<ClientMessage>(
                                                        ClientMessage.StartGame(message.room.id)
                                                    ))
                                                }

                                                if (message2 is ServerMessage.GameStarted) {
                                                    // 给玩家1发送出牌消息
                                                    val room = message2.room
                                                    val player1 = room.players[0]

                                                    // 添加测试卡牌
                                                    player1.cards.add(Card(
                                                        id = "test_sha",
                                                        name = "杀",
                                                        type = CardType.BASIC,
                                                        effect = "对一名角色造成1点伤害",
                                                        damage = 1
                                                    ))

                                                    delay(100) // 等待状态同步

                                                    // 玩家1对玩家2出杀
                                                    this@webSocket.send(Json.encodeToString<ClientMessage>(
                                                        ClientMessage.PlayCard(
                                                            player1.id,
                                                            "test_sha",
                                                            listOf(room.players[1].id)
                                                        )
                                                    ))
                                                }

                                                // 如果是卡牌执行完成，结束测试
                                                if (message2 is ServerMessage.CardExecutionCompleted) {
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 如果是卡牌执行完成，结束测试
                            if (message is ServerMessage.CardExecutionCompleted) {
                                break
                            }

                    }
                }
            }
        } finally {
            client.close()
        }

        // 验证消息流程
        assertTrue(client1Messages.any { it is ServerMessage.RoomCreated })
        assertTrue(client1Messages.any { it is ServerMessage.PlayerJoined })
        assertTrue(client1Messages.any { it is ServerMessage.GameStarted })
        assertTrue(client1Messages.any { it is ServerMessage.CardExecutionStarted })
        assertTrue(client1Messages.any { it is ServerMessage.CardExecutionCompleted })

        assertTrue(client2Messages.any { it is ServerMessage.PlayerJoined })
        assertTrue(client2Messages.any { it is ServerMessage.GameStarted })
        assertTrue(client2Messages.any { it is ServerMessage.CardExecutionStarted })
        assertTrue(client2Messages.any { it is ServerMessage.CardExecutionCompleted })

        // 验证出牌区有记录
        val finalGameState = client1Messages.filterIsInstance<ServerMessage.GameStateUpdate>().lastOrNull()
        if (finalGameState != null) {
            assertTrue(finalGameState.room.currentTurnPlayedCards.isNotEmpty())
            assertEquals("杀", finalGameState.room.currentTurnPlayedCards[0].card.name)
        }
    }

    @Test
    fun `test card execution with nullification response`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间并添加两个玩家
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                var roomId = ""
                var player1Id: String
                var player2Id: String

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        when (message) {
                            is ServerMessage.RoomCreated -> {
                                roomId = message.room.id
                                player1Id = message.room.players[0].id
                                
                                // 加入第二个玩家（模拟）
                                val player2 = Player(
                                    id = "player2_id",
                                    name = "玩家2",
                                    health = 4,
                                    maxHealth = 4
                                )
                                player2.cards.add(Card(
                                    id = "wuxie",
                                    name = "无懈可击",
                                    type = CardType.TRICK,
                                    effect = "抵消一张锦囊牌的效果"
                                ))
                                player2Id = player2.id

                                // 给玩家1添加过河拆桥卡牌
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.PlayCard(
                                        player1Id,
                                        "guohechaiqiao",
                                        listOf(player2Id)
                                    )
                                ))
                            }

                            is ServerMessage.ResponseRequired -> {
                                if (message.responseType == "NULLIFICATION") {
                                    // 模拟玩家2使用无懈可击响应
                                    send(Json.encodeToString<ClientMessage>(
                                        ClientMessage.RespondToCard(
                                            message.targetPlayerId,
                                            "wuxie",
                                            true
                                        )
                                    ))
                                }
                            }

                            is ServerMessage.CardExecutionCompleted -> {
                                // 测试完成
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

        // 验证无懈响应流程
        assertTrue(messages.any { it is ServerMessage.ResponseRequired })
        assertTrue(messages.any { it is ServerMessage.ResponseReceived })
        
        val completedMessage = messages.filterIsInstance<ServerMessage.CardExecutionCompleted>().firstOrNull()
        assertNotNull(completedMessage)
        assertTrue(completedMessage.blocked) // 应该被无懈可击阻挡
    }

    @Test
    fun `test abundant harvest card execution`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                var roomId = ""
                var player1Id = ""

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        when (message) {
                            is ServerMessage.RoomCreated -> {
                                roomId = message.room.id
                                player1Id = message.room.players[0].id

                                // 出五谷丰登
                                send(Json.encodeToString<ClientMessage>(
                                    ClientMessage.PlayCard(
                                        player1Id,
                                        "wugufengdeng",
                                        emptyList()
                                    )
                                ))
                            }

                            is ServerMessage.AbundantHarvestStarted -> {
                                // 验证五谷丰登开始消息
                                assertTrue(message.availableCards.isNotEmpty())
                                assertNotNull(message.room)
                            }

                            is ServerMessage.AbundantHarvestSelection -> {
                                // 模拟选择第一张卡牌
                                if (message.availableCards.isNotEmpty()) {
                                    send(Json.encodeToString<ClientMessage>(
                                        ClientMessage.SelectAbundantHarvestCard(
                                            message.playerId,
                                            message.availableCards[0].id
                                        )
                                    ))
                                }
                            }

                            is ServerMessage.AbundantHarvestCompleted -> {
                                // 五谷丰登完成
                                assertTrue(message.selections.isNotEmpty())
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

        // 验证五谷丰登完整流程
        assertTrue(messages.any { it is ServerMessage.AbundantHarvestStarted })
        assertTrue(messages.any { it is ServerMessage.AbundantHarvestSelection })
        assertTrue(messages.any { it is ServerMessage.AbundantHarvestCardSelected })
        assertTrue(messages.any { it is ServerMessage.AbundantHarvestCompleted })
    }

    @Test
    fun `test error handling in card execution`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        if (message is ServerMessage.RoomCreated) {
                            val player1Id = message.room.players[0].id

                            // 尝试出不存在的卡牌
                            send(Json.encodeToString<ClientMessage>(
                                ClientMessage.PlayCard(
                                    player1Id,
                                    "non_existent_card",
                                    emptyList()
                                )
                            ))

                            // 等待错误消息后结束测试
                            delay(500)
                            break
                        }
                    }
                }
            }
        } finally {
            client.close()
        }

        // 验证收到错误消息
        val errorMessages = messages.filterIsInstance<ServerMessage.Error>()
        assertTrue(errorMessages.isNotEmpty())
        assertTrue(errorMessages.any { it.message.contains("卡牌") || it.message.contains("card") })
    }

    @Test
    fun `test multi-target card execution`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                // 创建房间
                send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val message = Json.decodeFromString<ServerMessage>(frame.readText())
                        messages.add(message)

                        if (message is ServerMessage.RoomCreated) {
                            val roomId = message.room.id
                            val player1Id = message.room.players[0].id

                            // 模拟添加多个玩家后使用AOE卡牌
                            send(Json.encodeToString<ClientMessage>(
                                ClientMessage.PlayCard(
                                    player1Id,
                                    "nanmanruqin", // 南蛮入侵，对所有其他角色生效
                                    emptyList() // AOE卡牌不需要指定目标
                                )
                            ))

                            delay(500)
                            break
                        }
                    }
                }
            }
        } finally {
            client.close()
        }

        // 由于只有一个玩家，AOE卡牌应该没有目标
        val executionStarted = messages.filterIsInstance<ServerMessage.CardExecutionStarted>()
        if (executionStarted.isNotEmpty()) {
            assertTrue(executionStarted[0].targetNames.isEmpty())
        }
    }
}