package moe.gensoukyo.tbc.server.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.gensoukyo.tbc.shared.card.CardFactory
import moe.gensoukyo.tbc.shared.model.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class GameService {
    private val rooms = ConcurrentHashMap<String, GameRoom>()
    private val roomMutex = Mutex()
    
    suspend fun createRoom(roomName: String, playerName: String): GameRoom = roomMutex.withLock {
        val roomId = UUID.randomUUID().toString()
        val playerId = UUID.randomUUID().toString()
        
        val player = Player(
            id = playerId,
            name = playerName,
            health = 100,
            maxHealth = 100
        )
        
        // 给玩家发4张初始卡牌
        repeat(4) {
            player.cards.add(CardFactory.createRandomCard())
        }
        
        val room = GameRoom(
            id = roomId,
            name = roomName,
            players = mutableListOf(player),
            currentPlayer = playerId
        )
        
        rooms[roomId] = room
        room
    }
    
    suspend fun joinRoom(roomId: String, playerName: String): Pair<GameRoom, Player>? = roomMutex.withLock {
        val room = rooms[roomId] ?: return null
        
        if (room.players.size >= room.maxPlayers) {
            return null
        }
        
        val playerId = UUID.randomUUID().toString()
        val player = Player(
            id = playerId,
            name = playerName,
            health = 100,
            maxHealth = 100
        )
        
        // 给玩家发4张初始卡牌
        repeat(4) {
            player.cards.add(CardFactory.createRandomCard())
        }
        
        room.players.add(player)
        
        // 如果房间满了，开始游戏
        if (room.players.size >= 2) {
            room.gameState = GameState.PLAYING
        }
        
        Pair(room, player)
    }
    
    fun getRoom(roomId: String): GameRoom? = rooms[roomId]
    
    fun drawCard(roomId: String, playerId: String): Card? {
        val room = rooms[roomId] ?: return null
        val player = room.players.find { it.id == playerId } ?: return null
        
        val newCard = CardFactory.createRandomCard()
        player.cards.add(newCard)
        return newCard
    }
    
    fun updatePlayerHealth(roomId: String, playerId: String, amount: Int): Int? {
        val room = rooms[roomId] ?: return null
        val player = room.players.find { it.id == playerId } ?: return null
        
        player.health = max(0, player.health + amount)
        
        // 检查玩家是否死亡
        if (player.health <= 0) {
            // 游戏结束逻辑可以在这里添加
            room.gameState = GameState.FINISHED
        }
        
        return player.health
    }
    
    fun useCard(roomId: String, playerId: String, cardId: String, targetId: String?): Boolean {
        val room = rooms[roomId] ?: return false
        val player = room.players.find { it.id == playerId } ?: return false
        val cardIndex = player.cards.indexOfFirst { it.id == cardId }
        
        if (cardIndex == -1) return false
        
        val card = player.cards[cardIndex]
        
        when (card.type) {
            CardType.ATTACK -> {
                if (targetId != null) {
                    val target = room.players.find { it.id == targetId }
                    if (target != null) {
                        target.health = max(0, target.health - card.damage)
                        player.cards.removeAt(cardIndex)
                        return true
                    }
                }
            }
            CardType.RECOVERY -> {
                player.health = (player.health + card.healing).coerceAtMost(player.maxHealth)
                player.cards.removeAt(cardIndex)
                return true
            }
            CardType.SKILL -> {
                // 技能卡的实现可以根据具体需求来扩展
                player.cards.removeAt(cardIndex)
                return true
            }
            CardType.DEFENSE -> {
                // 防御卡的使用逻辑
                player.cards.removeAt(cardIndex)
                return true
            }
        }
        
        return false
    }
    
    fun getAllRooms(): List<GameRoom> = rooms.values.toList()
}