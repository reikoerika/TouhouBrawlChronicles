package moe.gensoukyo.tbc.server.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
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
import moe.gensoukyo.tbc.shared.model.GameRoom
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 全卡牌类型执行流程测试
 * 测试BASIC、TRICK、EQUIPMENT等所有卡牌类型的完整执行流程
 */
class AllCardTypesIntegrationTest {

    @Test
    fun `test basic card execution - sha (杀)`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupTwoPlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                val player2 = testContext.room.players[1]
                
                // 给玩家1添加杀卡牌
                player1.cards.add(Card(
                    id = "sha_001",
                    name = "杀",
                    type = CardType.BASIC,
                    effect = "对一名角色造成1点伤害",
                    damage = 1
                ))

                val initialHealth = player2.health

                // 出杀
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "sha_001", listOf(player2.id))
                ))

                // 等待执行完成
                waitForMessage<ServerMessage.CardExecutionCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证杀的执行流程
        assertTrue(messages.any { it is ServerMessage.CardExecutionStarted })
        assertTrue(messages.any { it is ServerMessage.CardExecutionCompleted })
        
        val executionStarted = messages.filterIsInstance<ServerMessage.CardExecutionStarted>().first()
        assertEquals("杀", executionStarted.cardName)
        assertTrue(executionStarted.targetNames.contains("玩家2"))
    }

    @Test
    fun `test basic card execution - shan (闪)`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupTwoPlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                val player2 = testContext.room.players[1]
                
                // 给玩家1添加杀，玩家2添加闪
                player1.cards.add(Card(
                    id = "sha_001",
                    name = "杀",
                    type = CardType.BASIC,
                    effect = "对一名角色造成1点伤害",
                    damage = 1
                ))
                player2.cards.add(Card(
                    id = "shan_001",
                    name = "闪",
                    type = CardType.BASIC,
                    effect = "抵消一次杀的伤害"
                ))

                // 玩家1出杀
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "sha_001", listOf(player2.id))
                ))

                // 等待响应请求
                val responseRequired = waitForMessage<ServerMessage.ResponseRequired>(this, messages)
                
                // 玩家2用闪响应
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.RespondToCard(player2.id, "shan_001", true)
                ))

                // 等待执行完成
                waitForMessage<ServerMessage.CardExecutionCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证闪的响应流程
        assertTrue(messages.any { it is ServerMessage.ResponseRequired })
        assertTrue(messages.any { it is ServerMessage.ResponseReceived })
        
        val executionCompleted = messages.filterIsInstance<ServerMessage.CardExecutionCompleted>().first()
        assertTrue(executionCompleted.blocked) // 被闪阻挡
    }

    @Test
    fun `test basic card execution - tao (桃)`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupTwoPlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                
                // 先减血
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.HealthChange(player1.id, -1)
                ))
                
                delay(200)

                // 给玩家1添加桃卡牌
                player1.cards.add(Card(
                    id = "tao_001",
                    name = "桃",
                    type = CardType.BASIC,
                    effect = "回复1点体力",
                    healing = 1
                ))

                // 使用桃
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "tao_001", emptyList())
                ))

                // 等待执行完成
                waitForMessage<ServerMessage.CardExecutionCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证桃的治疗效果
        assertTrue(messages.any { it is ServerMessage.HealthUpdated })
        assertTrue(messages.any { it is ServerMessage.CardExecutionCompleted })
        
        val executionCompleted = messages.filterIsInstance<ServerMessage.CardExecutionCompleted>().first()
        assertTrue(executionCompleted.success) // 桃应该成功执行
    }

    @Test
    fun `test trick card execution - guohechaiqiao (过河拆桥)`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupTwoPlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                val player2 = testContext.room.players[1]
                
                // 给玩家2一些手牌
                player2.cards.add(Card(id = "dummy_card", name = "测试卡", type = CardType.BASIC, effect = "测试"))
                
                // 给玩家1添加过河拆桥
                player1.cards.add(Card(
                    id = "ghcq_001",
                    name = "过河拆桥",
                    type = CardType.TRICK,
                    effect = "弃置目标一张牌"
                ))

                // 出过河拆桥
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "ghcq_001", listOf(player2.id))
                ))

                // 等待执行完成
                waitForMessage<ServerMessage.CardExecutionCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证锦囊牌的执行流程
        assertTrue(messages.any { it is ServerMessage.CardExecutionStarted })
        assertTrue(messages.any { it is ServerMessage.CardExecutionCompleted })
        
        val executionStarted = messages.filterIsInstance<ServerMessage.CardExecutionStarted>().first()
        assertEquals("过河拆桥", executionStarted.cardName)
    }

    @Test
    fun `test trick card execution - wuxiekeji (无懈可击)`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupTwoPlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                val player2 = testContext.room.players[1]
                
                // 给玩家1添加过河拆桥，玩家2添加无懈可击
                player1.cards.add(Card(
                    id = "ghcq_001",
                    name = "过河拆桥",
                    type = CardType.TRICK,
                    effect = "弃置目标一张牌"
                ))
                player2.cards.add(Card(
                    id = "wuxie_001",
                    name = "无懈可击",
                    type = CardType.TRICK,
                    effect = "抵消一张锦囊牌的效果"
                ))

                // 出过河拆桥
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "ghcq_001", listOf(player2.id))
                ))

                // 等待无懈可击阶段
                val nullificationPhase = waitForMessage<ServerMessage.NullificationPhaseStarted>(this, messages)
                
                // 玩家2使用无懈可击
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.RespondToCard(player2.id, "wuxie_001", true)
                ))

                // 等待执行完成
                waitForMessage<ServerMessage.CardExecutionCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证无懈可击的阻挡效果
        assertTrue(messages.any { it is ServerMessage.NullificationPhaseStarted })
        assertTrue(messages.any { it is ServerMessage.ResponseReceived })
        
        val executionCompleted = messages.filterIsInstance<ServerMessage.CardExecutionCompleted>().first()
        assertTrue(executionCompleted.blocked) // 被无懈可击阻挡
    }

    @Test
    fun `test aoe trick card execution - nanmanruqin (南蛮入侵)`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupThreePlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                
                // 给玩家1添加南蛮入侵
                player1.cards.add(Card(
                    id = "nanman_001",
                    name = "南蛮入侵",
                    type = CardType.TRICK,
                    effect = "所有其他角色需要打出一张杀，否则受到1点伤害"
                ))

                // 出南蛮入侵
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "nanman_001", emptyList())
                ))

                // 等待执行完成
                waitForMessage<ServerMessage.CardExecutionCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证AOE锦囊牌的执行
        assertTrue(messages.any { it is ServerMessage.CardExecutionStarted })
        
        val executionStarted = messages.filterIsInstance<ServerMessage.CardExecutionStarted>().first()
        assertEquals("南蛮入侵", executionStarted.cardName)
        // AOE卡牌目标为所有其他玩家
        assertEquals(2, executionStarted.targetNames.size)
    }

    @Test
    fun `test equipment card execution - weapon`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupTwoPlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                
                // 给玩家1添加武器装备
                player1.cards.add(Card(
                    id = "zhanmadao",
                    name = "斩马刀",
                    type = CardType.EQUIPMENT,
                    effect = "攻击距离+1",
                    damage = 1
                ))

                // 装备武器
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "zhanmadao", emptyList())
                ))

                // 等待执行完成
                waitForMessage<ServerMessage.CardExecutionCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证装备卡的执行
        assertTrue(messages.any { it is ServerMessage.CardExecutionStarted })
        assertTrue(messages.any { it is ServerMessage.CardExecutionCompleted })
        
        val executionStarted = messages.filterIsInstance<ServerMessage.CardExecutionStarted>().first()
        assertEquals("斩马刀", executionStarted.cardName)
        assertTrue(executionStarted.targetNames.isEmpty()) // 装备卡无需目标
    }

    @Test
    fun `test equipment card execution - armor`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupTwoPlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                
                // 给玩家1添加防具装备
                player1.cards.add(Card(
                    id = "bagua",
                    name = "八卦阵",
                    type = CardType.EQUIPMENT,
                    effect = "当你需要使用或打出闪时，你可以进行判定，若结果为红色，视为你使用或打出了一张闪"
                ))

                // 装备防具
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "bagua", emptyList())
                ))

                // 等待执行完成
                waitForMessage<ServerMessage.CardExecutionCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证防具装备的执行
        assertTrue(messages.any { it is ServerMessage.CardExecutionCompleted })
        
        val executionCompleted = messages.filterIsInstance<ServerMessage.CardExecutionCompleted>().first()
        assertTrue(executionCompleted.success) // 装备应该成功
    }

    @Test
    fun `test special card execution - wugufengdeng (五谷丰登)`() = testApplication {
        application {
            main()
        }

        val messages = mutableListOf<ServerMessage>()
        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        try {
            client.webSocket(host = "127.0.0.1", port = 8080, path = "/game") {
                val testContext = setupThreePlayerGame(this, messages)
                
                val player1 = testContext.room.players[0]
                
                // 给玩家1添加五谷丰登
                player1.cards.add(Card(
                    id = "wugu_001",
                    name = "五谷丰登",
                    type = CardType.TRICK,
                    effect = "所有角色依次选择获得其中一张牌"
                ))

                // 出五谷丰登
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.PlayCard(player1.id, "wugu_001", emptyList())
                ))

                // 等待五谷丰登开始
                val abundantHarvestStarted = waitForMessage<ServerMessage.AbundantHarvestStarted>(this, messages)
                
                // 验证有可选择的卡牌
                assertTrue(abundantHarvestStarted.availableCards.isNotEmpty())
                
                // 模拟选择流程
                val firstSelection = waitForMessage<ServerMessage.AbundantHarvestSelection>(this, messages)
                
                // 选择第一张卡牌
                send(Json.encodeToString<ClientMessage>(
                    ClientMessage.SelectAbundantHarvestCard(
                        firstSelection.playerId,
                        firstSelection.availableCards.first().id
                    )
                ))

                // 等待五谷丰登完成
                waitForMessage<ServerMessage.AbundantHarvestCompleted>(this, messages)
            }
        } finally {
            client.close()
        }

        // 验证五谷丰登的特殊执行流程
        assertTrue(messages.any { it is ServerMessage.AbundantHarvestStarted })
        assertTrue(messages.any { it is ServerMessage.AbundantHarvestSelection })
        assertTrue(messages.any { it is ServerMessage.AbundantHarvestCardSelected })
        assertTrue(messages.any { it is ServerMessage.AbundantHarvestCompleted })
    }

    // 辅助函数
    data class TestGameContext(
        val room: GameRoom,
        val messages: List<ServerMessage>
    )

    private suspend fun setupTwoPlayerGame(
        session: WebSocketSession,
        messages: MutableList<ServerMessage>
    ): TestGameContext {
        // 创建房间
        session.send(Json.encodeToString<ClientMessage>(ClientMessage.CreateRoom("测试房间", "玩家1")))
        val roomCreated = waitForMessage<ServerMessage.RoomCreated>(session, messages)
        
        // 添加第二个玩家
        session.send(Json.encodeToString<ClientMessage>(
            ClientMessage.JoinRoom(roomCreated.room.id, "玩家2")
        ))
        val playerJoined = waitForMessage<ServerMessage.PlayerJoined>(session, messages)
        
        // 开始游戏
        session.send(Json.encodeToString<ClientMessage>(
            ClientMessage.StartGame(roomCreated.room.id)
        ))
        val gameStarted = waitForMessage<ServerMessage.GameStarted>(session, messages)
        
        return TestGameContext(gameStarted.room, messages)
    }

    private suspend fun setupThreePlayerGame(
        session: WebSocketSession,
        messages: MutableList<ServerMessage>
    ): TestGameContext {
        val twoPlayerContext = setupTwoPlayerGame(session, messages)
        
        // 添加第三个玩家
        session.send(Json.encodeToString<ClientMessage>(
            ClientMessage.JoinRoom(twoPlayerContext.room.id, "玩家3")
        ))
        val player3Joined = waitForMessage<ServerMessage.PlayerJoined>(session, messages)
        
        return TestGameContext(player3Joined.room, messages)
    }

    private suspend inline fun <reified T : ServerMessage> waitForMessage(
        session: WebSocketSession,
        messages: MutableList<ServerMessage>,
        timeoutMs: Long = 5000
    ): T {
        val startTime = System.currentTimeMillis()
        
        // 先检查已有消息
        messages.filterIsInstance<T>().lastOrNull()?.let { return it }
        
        // 等待新消息
        for (frame in session.incoming) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw RuntimeException("等待消息超时: ${T::class.simpleName}")
            }
            
            if (frame is Frame.Text) {
                try {
                    val message = Json.decodeFromString<ServerMessage>(frame.readText())
                    messages.add(message)
                    
                    if (message is T) {
                        return message
                    }
                } catch (e: Exception) {
                    // 忽略解析错误，继续等待
                }
            }
        }
        
        throw RuntimeException("未收到期望的消息: ${T::class.simpleName}")
    }
}