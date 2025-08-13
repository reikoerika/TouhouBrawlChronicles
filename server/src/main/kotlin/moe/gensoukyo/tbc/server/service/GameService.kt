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
            currentPlayerIndex = 0
        )
        
        rooms[roomId] = room
        room
    }
    
    suspend fun joinRoom(roomId: String, playerName: String, spectateOnly: Boolean = false): Triple<GameRoom, Player?, moe.gensoukyo.tbc.shared.model.Spectator?>? = roomMutex.withLock {
        val room = rooms[roomId] ?: return null
        
        // 检查是否存在同名玩家（重连情况）
        val existingPlayer = room.players.find { it.name == playerName }
        if (existingPlayer != null) {
            // 同名玩家重连，返回现有玩家信息
            return Triple(room, existingPlayer, null)
        }
        
        // 检查是否存在同名观战者（观战者重连）
        val existingSpectator = room.spectators.find { it.name == playerName }
        if (existingSpectator != null) {
            // 同名观战者重连
            return Triple(room, null, existingSpectator)
        }
        
        // 如果游戏正在进行中或者指定只观战，则以观战者身份加入
        if (room.gameState == GameState.PLAYING || spectateOnly) {
            val spectatorId = UUID.randomUUID().toString()
            val spectator = moe.gensoukyo.tbc.shared.model.Spectator(
                id = spectatorId,
                name = playerName
            )
            room.spectators.add(spectator)
            return Triple(room, null, spectator)
        }
        
        // 游戏未开始且房间未满，以玩家身份加入
        if (room.players.size >= room.maxPlayers) {
            // 房间已满，以观战者身份加入
            val spectatorId = UUID.randomUUID().toString()
            val spectator = moe.gensoukyo.tbc.shared.model.Spectator(
                id = spectatorId,
                name = playerName
            )
            room.spectators.add(spectator)
            return Triple(room, null, spectator)
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
        
        Triple(room, player, null)
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
    
    fun startGame(roomId: String): GameRoom? {
        val room = rooms[roomId] ?: return null
        if (room.players.size < 2) return null
        
        room.gameState = GameState.PLAYING
        room.currentPlayerIndex = 0
        room.turnCount = 1
        room.gamePhase = GamePhase.DRAW
        
        // 给所有玩家发初始手牌（如果还没有的话）
        room.players.forEach { player ->
            while (player.cards.size < 4) {
                player.cards.add(CardFactory.createRandomCard())
            }
        }
        
        return room
    }
    
    fun nextTurn(roomId: String): GameRoom? {
        val room = rooms[roomId] ?: return null
        if (room.gameState != GameState.PLAYING) return null
        
        when (room.gamePhase) {
            GamePhase.DRAW -> {
                // 摸牌阶段 -> 出牌阶段
                room.gamePhase = GamePhase.PLAY
            }
            GamePhase.PLAY -> {
                // 出牌阶段 -> 弃牌阶段
                room.gamePhase = GamePhase.DISCARD
            }
            GamePhase.DISCARD -> {
                // 弃牌阶段 -> 下一个玩家的摸牌阶段
                room.currentPlayerIndex = (room.currentPlayerIndex + 1) % room.players.size
                room.gamePhase = GamePhase.DRAW
                room.turnCount++
                
                // 清理当前回合的出牌区
                room.currentTurnPlayedCards.clear()
            }
        }
        
        return room
    }
    
    fun adjustPlayerOrder(roomId: String, newOrder: List<String>): GameRoom? {
        val room = rooms[roomId] ?: return null
        if (room.gameState != GameState.WAITING) return null
        
        val reorderedPlayers = mutableListOf<Player>()
        newOrder.forEach { playerId ->
            room.players.find { it.id == playerId }?.let { player ->
                reorderedPlayers.add(player)
            }
        }
        
        if (reorderedPlayers.size == room.players.size) {
            room.players.clear()
            room.players.addAll(reorderedPlayers)
            room.currentPlayerIndex = 0
        }
        
        return room
    }
    
    fun playCard(roomId: String, playerId: String, cardId: String, targetId: String?): PlayedCard? {
        val room = rooms[roomId] ?: return null
        val player = room.players.find { it.id == playerId } ?: return null
        val cardIndex = player.cards.indexOfFirst { it.id == cardId }
        
        if (cardIndex == -1) return null
        if (room.gameState != GameState.PLAYING) return null
        if (room.gamePhase != GamePhase.PLAY) return null
        
        val card = player.cards[cardIndex]
        
        // 创建出牌记录
        val playedCard = PlayedCard(
            card = card,
            playerId = playerId,
            playerName = player.name,
            turnNumber = room.turnCount
        )
        
        // 执行卡牌效果
        when (card.type) {
            CardType.ATTACK -> {
                if (targetId != null) {
                    val target = room.players.find { it.id == targetId }
                    if (target != null) {
                        target.health = max(0, target.health - card.damage)
                    }
                }
            }
            CardType.RECOVERY -> {
                player.health = (player.health + card.healing).coerceAtMost(player.maxHealth)
            }
            CardType.SKILL -> {
                // 技能卡的实现可以根据具体需求来扩展
            }
            CardType.DEFENSE -> {
                // 防御卡的使用逻辑
            }
        }
        
        // 从手牌中移除
        player.cards.removeAt(cardIndex)
        
        // 添加到玩家的出牌记录
        player.playedCards.add(playedCard)
        
        // 添加到当前回合的出牌区
        room.currentTurnPlayedCards.add(playedCard)
        
        return playedCard
    }
}