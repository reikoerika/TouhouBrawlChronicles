package moe.gensoukyo.tbc.server.service

import kotlinx.coroutines.runBlocking
import moe.gensoukyo.tbc.shared.exceptions.CardExecutionException
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.CardType
import moe.gensoukyo.tbc.shared.model.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * GameService单元测试
 * 测试统一重构后的出牌架构
 */
class GameServiceTest {
    private val gameService = GameService()

    @Test
    fun `test createRoom should create room with player`() = runBlocking {
        val roomName = "测试房间"
        val playerName = "测试玩家"
        
        val room = gameService.createRoom(roomName, playerName)
        
        assertEquals(roomName, room.name)
        assertEquals(1, room.players.size)
        assertEquals(playerName, room.players[0].name)
        assertEquals(GameState.WAITING, room.gameState)
    }

    @Test
    fun `test joinRoom should add player to existing room`() = runBlocking {
        val room = gameService.createRoom("房间1", "玩家1")
        val roomId = room.id
        
        val result = gameService.joinRoom(roomId, "玩家2", false)
        
        assertNotNull(result)
        val (updatedRoom, newPlayer, _) = result
        assertEquals(2, updatedRoom.players.size)
        assertEquals("玩家2", newPlayer!!.name)
    }

    @Test
    fun `test playCard with valid parameters should return success`() = runBlocking {
        // 创建房间并开始游戏
        val room = gameService.createRoom("测试房间", "玩家1")
        gameService.joinRoom(room.id, "玩家2", false)
        val gameStartResult = gameService.startGame(room.id)
        assertNotNull(gameStartResult)
        
        val updatedRoom = gameStartResult.first
        val player1 = updatedRoom.players[0]
        
        // 给玩家添加一张测试卡牌
        player1.cards.add(Card(
            id = "test_card_1",
            name = "杀",
            type = CardType.BASIC,
            effect = "对一名角色造成1点伤害",
            damage = 1
        ))
        
        val result = gameService.playCard(room.id, player1.id, "test_card_1", listOf(updatedRoom.players[1].id))
        
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        assertEquals("杀", context.card.name)
        assertEquals(player1.id, context.caster.id)
    }

    @Test
    fun `test playCard with invalid room should return failure`() {
        val result = gameService.playCard("invalid_room", "player1", "card1", emptyList())
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CardExecutionException.RoomNotFoundException)
    }

    @Test
    fun `test playCard with invalid player should return failure`() = runBlocking {
        val room = gameService.createRoom("测试房间", "玩家1")
        
        val result = gameService.playCard(room.id, "invalid_player", "card1", emptyList())
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CardExecutionException.PlayerNotFoundException)
    }

    @Test
    fun `test playCard with invalid card should return failure`() = runBlocking {
        // 创建房间并添加第二个玩家
        val room = gameService.createRoom("测试房间", "玩家1")
        gameService.joinRoom(room.id, "玩家2", false)
        
        // 开始游戏以设置正确的游戏状态和阶段
        val gameStartResult = gameService.startGame(room.id)
        assertNotNull(gameStartResult)
        
        val updatedRoom = gameStartResult.first
        val player = updatedRoom.players[0]
        
        val result = gameService.playCard(updatedRoom.id, player.id, "invalid_card", emptyList())
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CardExecutionException.CardNotFoundException)
    }

    @Test
    fun `test playCard when not player turn should return failure`() = runBlocking {
        // 创建多玩家房间并开始游戏
        val room = gameService.createRoom("测试房间", "玩家1")
        gameService.joinRoom(room.id, "玩家2", false)
        val gameStartResult = gameService.startGame(room.id)
        assertNotNull(gameStartResult)
        
        val updatedRoom = gameStartResult.first
        val player2 = updatedRoom.players[1] // 非当前回合玩家
        
        // 给玩家2添加卡牌
        player2.cards.add(Card(
            id = "test_card_2",
            name = "杀",
            type = CardType.BASIC,
            effect = "对一名角色造成1点伤害",
            damage = 1
        ))
        
        // 玩家2尝试在非自己回合时出牌
        val result = gameService.playCard(room.id, player2.id, "test_card_2", emptyList())
        
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CardExecutionException.InvalidGameStateException)
    }

    @Test
    fun `test startGame should initialize cards and set game state`() = runBlocking {
        val room = gameService.createRoom("测试房间", "玩家1")
        gameService.joinRoom(room.id, "玩家2", false)
        
        val result = gameService.startGame(room.id)
        
        assertNotNull(result)
        val (updatedRoom, initialCards) = result
        assertEquals(GameState.PLAYING, updatedRoom.gameState)
        assertNotNull(updatedRoom.currentPlayer)
        assertEquals(2, initialCards.size) // 两个玩家的初始手牌
    }

    @Test
    fun `test getAllRooms should return all created rooms`() = runBlocking {
        gameService.createRoom("房间1", "玩家1")
        gameService.createRoom("房间2", "玩家2")
        
        val rooms = gameService.getAllRooms()
        
        assertEquals(2, rooms.size)
        assertTrue(rooms.any { it.name == "房间1" })
        assertTrue(rooms.any { it.name == "房间2" })
    }

    @Test
    fun `test drawCard should add card to player hand`() = runBlocking {
        val room = gameService.createRoom("测试房间", "玩家1")
        val player = room.players[0]
        val initialCardCount = player.cards.size
        
        val cards = gameService.drawCard(room.id, player.id, 1)
        
        assertEquals(1, cards.size)
        assertEquals(initialCardCount + 1, player.cards.size)
    }

    @Test
    fun `test updatePlayerHealth should modify player health correctly`() = runBlocking {
        val room = gameService.createRoom("测试房间", "玩家1")
        val player = room.players[0]
        val initialHealth = player.health
        
        val newHealth = gameService.updatePlayerHealth(room.id, player.id, -1)
        
        assertNotNull(newHealth)
        assertEquals(initialHealth - 1, newHealth)
        assertEquals(newHealth, player.health)
    }
}