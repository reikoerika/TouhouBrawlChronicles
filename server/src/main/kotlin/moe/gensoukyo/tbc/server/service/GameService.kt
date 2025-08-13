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
    private val generals = createGenerals()  // 武将库
    private val cardEffectService = CardEffectService()  // 卡牌效果服务
    
    suspend fun createRoom(roomName: String, playerName: String): GameRoom = roomMutex.withLock {
        val roomId = UUID.randomUUID().toString()
        val playerId = UUID.randomUUID().toString()
        
        val player = Player(
            id = playerId,
            name = playerName,
            health = 4,  // 默认4血
            maxHealth = 4,
            identity = Identity.LORD  // 房主默认为主公
        )
        
        val room = GameRoom(
            id = roomId,
            name = roomName,
            players = mutableListOf(player),
            currentPlayerIndex = 0,
            deck = CardFactory.createStandardDeck().toMutableList()  // 标准牌堆
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
            health = 4,  // 默认4血，后续根据武将调整
            maxHealth = 4
        )
        
        room.players.add(player)
        
        Triple(room, player, null)
    }
    
    private fun assignIdentities(players: MutableList<Player>) {
        val playerCount = players.size
        val identities = when (playerCount) {
            5 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.SPY)
            6 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.REBEL, Identity.SPY)
            7 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.REBEL, Identity.SPY)
            8 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.REBEL, Identity.REBEL, Identity.SPY)
            else -> listOf(Identity.LORD, Identity.REBEL)  // 简化版
        }.shuffled()
        
        // 确保第一个玩家是主公
        players[0].identity = Identity.LORD
        for (i in 1 until players.size) {
            players[i].identity = identities[i % identities.size]
        }
    }
    
    private fun dealInitialCards(room: GameRoom) {
        room.players.forEach { player ->
            val cardCount = when (player.identity) {
                Identity.LORD -> 6  // 主公6张
                else -> 4  // 其他4张
            }
            
            repeat(cardCount) {
                if (room.deck.isNotEmpty()) {
                    val card = room.deck.removeAt(0)
                    player.cards.add(card)
                }
            }
        }
    }
    
    private fun createGenerals(): List<General> {
        return listOf(
            General("zhangfei", "张飞", "蜀", 1, listOf(
                Skill("paoxiao", "咆哮", "你可以使用任意数量的杀")
            )),
            General("guanyu", "关羽", "蜀", 0, listOf(
                Skill("wusheng", "武圣", "你可以将红色牌当杀使用")
            )),
            General("zhaoyun", "赵云", "蜀", 0, listOf(
                Skill("longdan", "龙胆", "你可以将杀当闪，闪当杀使用")
            )),
            General("machao", "马超", "蜀", 0, listOf(
                Skill("mashu", "马术", "你与其他角色的距离-1")
            )),
            General("huangyueying", "黄月英", "蜀", 0, listOf(
                Skill("jizhi", "集智", "当你使用锦囊牌时，你可以摸一张牌")
            ))
        )
    }
    
    fun getRoom(roomId: String): GameRoom? = rooms[roomId]
    
    fun drawCard(roomId: String, playerId: String, count: Int = 1): List<Card> {
        val room = rooms[roomId] ?: return emptyList()
        val player = room.players.find { it.id == playerId } ?: return emptyList()
        
        val drawnCards = mutableListOf<Card>()
        repeat(count) {
            if (room.deck.isEmpty() && room.discardPile.isNotEmpty()) {
                // 重新洗牌
                room.deck.addAll(room.discardPile.shuffled())
                room.discardPile.clear()
            }
            
            if (room.deck.isNotEmpty()) {
                val card = room.deck.removeAt(0)
                player.cards.add(card)
                drawnCards.add(card)
            }
        }
        
        return drawnCards
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
        val targets = if (targetId != null) {
            listOfNotNull(room.players.find { it.id == targetId })
        } else {
            emptyList()
        }
        
        // 使用新的卡牌效果系统
        val effectResult = when (card.type) {
            CardType.BASIC -> cardEffectService.handleBasicCard(card, player, targets, room)
            CardType.TRICK -> cardEffectService.handleTrickCard(card, player, targets, room)
            CardType.EQUIPMENT -> cardEffectService.handleEquipmentCard(card, player, room)
        }
        
        if (effectResult.success) {
            player.cards.removeAt(cardIndex)
            if (card.type != CardType.EQUIPMENT) {
                room.discardPile.add(card)
            }
            return true
        }
        
        return false
    }
    
    fun getAllRooms(): List<GameRoom> = rooms.values.toList()
    
    fun startGame(roomId: String): GameRoom? {
        val room = rooms[roomId] ?: return null
        if (room.players.size < 2) return null
        
        // 分配身份
        assignIdentities(room.players)
        
        // 选择武将（简化版 - 随机分配）
        room.players.forEach { player ->
            val general = generals.random()
            player.general = general
            // 调整血量上限
            player.maxHealth = 4 + general.healthBonus
            player.health = player.maxHealth
        }
        
        // 发初始手牌
        dealInitialCards(room)
        
        room.gameState = GameState.PLAYING
        room.currentPlayerIndex = 0
        room.turnCount = 1
        room.gamePhase = GamePhase.DRAW
        
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
    
    fun playCard(roomId: String, playerId: String, cardId: String, targetIds: List<String> = emptyList()): PlayedCard? {
        val room = rooms[roomId] ?: return null
        val player = room.players.find { it.id == playerId } ?: return null
        val cardIndex = player.cards.indexOfFirst { it.id == cardId }
        
        if (cardIndex == -1) return null
        if (room.gameState != GameState.PLAYING) return null
        if (room.gamePhase != GamePhase.PLAY) return null
        
        val card = player.cards[cardIndex]
        val targets = targetIds.mapNotNull { targetId -> 
            room.players.find { it.id == targetId } 
        }
        
        // 处理卡牌效果
        val effectResult = when (card.type) {
            CardType.BASIC -> cardEffectService.handleBasicCard(card, player, targets, room)
            CardType.TRICK -> cardEffectService.handleTrickCard(card, player, targets, room)
            CardType.EQUIPMENT -> cardEffectService.handleEquipmentCard(card, player, room)
        }
        
        if (!effectResult.success) {
            return null
        }
        
        // 从手牌中移除
        player.cards.removeAt(cardIndex)
        
        // 装备牌不进入弃牌堆，其他牌进入弃牌堆
        if (card.type != CardType.EQUIPMENT) {
            room.discardPile.add(card)
        }
        
        // 处理摸牌效果
        if (effectResult.drawCards > 0) {
            if (effectResult.affectAllPlayers) {
                room.players.forEach { p ->
                    drawCard(roomId, p.id, effectResult.drawCards)
                }
            } else {
                drawCard(roomId, playerId, effectResult.drawCards)
            }
        }
        
        // 创建出牌记录
        val playedCard = PlayedCard(
            card = card,
            playerId = playerId,
            playerName = player.name,
            turnNumber = room.turnCount,
            targetIds = targetIds
        )
        
        // 添加到玩家的出牌记录
        player.playedCards.add(playedCard)
        
        // 添加到当前回合的出牌区
        room.currentTurnPlayedCards.add(playedCard)
        
        return playedCard
    }
}