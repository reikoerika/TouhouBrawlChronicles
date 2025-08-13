package moe.gensoukyo.tbc.server.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.gensoukyo.tbc.shared.card.CardFactory
import moe.gensoukyo.tbc.shared.model.*
import moe.gensoukyo.tbc.server.card.*
import moe.gensoukyo.tbc.shared.card.CardExecutionContext
import moe.gensoukyo.tbc.shared.card.CardExecutionPhase
import moe.gensoukyo.tbc.shared.utils.logDebug
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class GameService {
    private val rooms = ConcurrentHashMap<String, GameRoom>()
    private val roomMutex = Mutex()
    private val generals = createGenerals()  // 武将库
    private val cardEffectService = CardEffectService()  // 卡牌效果服务
    private val cardExecutionEngine = CardExecutionEngine(cardEffectService)  // 卡牌执行引擎
    
    suspend fun createRoom(roomName: String, playerName: String): GameRoom = roomMutex.withLock {
        val roomId = UUID.randomUUID().toString()
        val playerId = UUID.randomUUID().toString()
        
        val player = Player(
            id = playerId,
            name = playerName,
            health = 4,  // 默认4血
            maxHealth = 4
            // 不在这里设置身份，等游戏开始时统一分配
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
            2 -> listOf(Identity.LORD, Identity.REBEL)  // 2人局：1主公 + 1反贼
            3 -> listOf(Identity.LORD, Identity.REBEL, Identity.SPY)  // 3人局：1主公 + 1反贼 + 1内奸
            4 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.REBEL, Identity.SPY)  // 4人局：1主公 + 1忠臣 + 1反贼 + 1内奸
            5 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.SPY)
            6 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.REBEL, Identity.SPY)
            7 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.REBEL, Identity.SPY)
            8 -> listOf(Identity.LORD, Identity.LOYALIST, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.REBEL, Identity.REBEL, Identity.SPY)
            else -> listOf(Identity.LORD, Identity.REBEL)  // 默认：1主公 + 1反贼
        }
        
        // 确保身份分配正确，不打乱顺序避免重复分配主公
        // 第一个玩家固定为主公
        players[0].identity = Identity.LORD
        
        // 其余玩家从身份列表的第2个开始分配（跳过主公）
        val otherIdentities = identities.drop(1).shuffled()  // 除了主公外的其他身份打乱
        for (i in 1 until players.size) {
            if (i - 1 < otherIdentities.size) {
                players[i].identity = otherIdentities[i - 1]
            } else {
                // 如果玩家数超过身份配置，默认分配反贼
                players[i].identity = Identity.REBEL
            }
        }
        
        // 调试输出：打印身份分配结果
        println("身份分配完成 - 玩家数: $playerCount")
        players.forEachIndexed { index, player ->
            println("  玩家 ${index + 1}: ${player.name} -> ${player.identity}")
        }
        
        // 验证是否只有一个主公
        val lordCount = players.count { it.identity == Identity.LORD }
        if (lordCount != 1) {
            println("警告: 发现 $lordCount 个主公，应该只有1个！")
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

    fun getAllRooms(): List<GameRoom> = rooms.values.toList()
    
    // ======================== 新的卡牌执行系统 ========================
    
    /**
     * 新的出牌方法 - 使用卡牌执行引擎
     */
    fun playCard(roomId: String, playerId: String, cardId: String, targetIds: List<String> = emptyList()): CardExecutionContext? {
        val room = rooms[roomId] 
        if (room == null) {
            logDebug("Room not found: $roomId")
            return null
        }
        
        val player = room.players.find { it.id == playerId }
        if (player == null) {
            logDebug("Player not found: $playerId in room $roomId")
            return null
        }
        
        val cardIndex = player.cards.indexOfFirst { it.id == cardId }
        if (cardIndex == -1) {
            logDebug("Card not found: $cardId in player ${player.name}'s hand (${player.cards.size} cards)")
            return null
        }
        
        val card = player.cards[cardIndex]
        logDebug("Playing card: ${card.name} (${card.targetType}) by player ${player.name}")
        
        // 根据卡牌的目标类型自动确定目标
        val targets = when (card.targetType) {
            TargetType.ALL_PLAYERS -> room.players.filter { it.health > 0 }
            TargetType.ALL_OTHERS -> room.players.filter { it.id != playerId && it.health > 0 }
            TargetType.NONE -> emptyList()
            else -> targetIds.mapNotNull { targetId -> 
                room.players.find { it.id == targetId } 
            }
        }

        logDebug("Targets determined: ${targets.map { it.name }} (${targets.size} targets)")
        logDebug("Game state: ${room.gameState}, Game phase: ${room.gamePhase}")
        
        // 验证出牌合法性
        if (!isValidCardPlay(card, player, targets, room)) {
            logDebug("Invalid card play detected")
            return null
        }
        
        // 从手牌中移除卡牌
        player.cards.removeAt(cardIndex)
        
        // 开始执行
        val context = cardExecutionEngine.startExecution(card, player, targets, room)
        
        // 保存执行上下文到房间
        room.activeCardExecution = context
        
        logDebug("Card execution started successfully: ${context.id}")
        return context
    }

    /**
     * 获取当前需要响应的玩家
     */
    fun getCurrentResponseTarget(roomId: String): String? {
        val room = rooms[roomId] ?: return null
        val context = room.activeCardExecution ?: return null
        
        return cardExecutionEngine.getResponseTarget(context.id, room)
    }
    
    /**
     * 获取无懈可击阶段所有可以响应的玩家
     */
    fun getAllNullificationTargets(roomId: String): List<String> {
        val room = rooms[roomId] ?: return emptyList()
        val context = room.activeCardExecution ?: return emptyList()
        
        return cardExecutionEngine.getAllNullificationTargets(context.id, room)
    }

    // ======================== 辅助方法 ========================
    
    private fun isValidCardPlay(card: Card, player: Player, targets: List<Player>, room: GameRoom): Boolean {
        // 基本验证逻辑
        if (room.gameState != GameState.PLAYING) {
            logDebug("Invalid game state: ${room.gameState} (expected PLAYING)")
            return false
        }
        if (room.gamePhase != GamePhase.PLAY) {
            logDebug("Invalid game phase: ${room.gamePhase} (expected PLAY)")
            return false
        }
        
        // 验证目标数量和类型
        val result = when (card.targetType) {
            TargetType.NONE -> {
                val valid = targets.isEmpty()
                if (!valid) logDebug("NONE target type requires empty targets, got ${targets.size}")
                valid
            }
            TargetType.SINGLE -> {
                val valid = targets.size == 1
                if (!valid) logDebug("SINGLE target type requires 1 target, got ${targets.size}")
                valid
            }
            TargetType.MULTIPLE -> {
                val valid = targets.isNotEmpty()
                if (!valid) logDebug("MULTIPLE target type requires at least 1 target, got 0")
                valid
            }
            TargetType.ALL_OTHERS -> {
                val expectedTargets = room.players.filter { it.id != player.id && it.health > 0 }
                val valid = targets.size == expectedTargets.size
                if (!valid) logDebug("ALL_OTHERS target type requires ${expectedTargets.size} targets, got ${targets.size}")
                valid
            }
            TargetType.ALL_PLAYERS -> {
                val expectedTargets = room.players.filter { it.health > 0 }
                val valid = targets.size == expectedTargets.size
                if (!valid) logDebug("ALL_PLAYERS target type requires ${expectedTargets.size} targets, got ${targets.size}")
                valid
            }
        }
        
        logDebug("Card play validation result: $result")
        return result
    }
    
    fun startGame(roomId: String): Pair<GameRoom, List<Pair<String, List<Card>>>>? {
        val room = rooms[roomId] ?: return null
        if (room.players.size < 2) return null
        
        // 确保牌堆已初始化并洗牌
        if (room.deck.isEmpty()) {
            room.deck.addAll(CardFactory.createStandardDeck())
        }
        room.deck.shuffle()
        
        // 第一步：分配身份（确保第一个玩家是主公）
        assignIdentities(room.players)
        
        // 第二步：选择武将（简化版 - 随机分配）
        room.players.forEach { player ->
            val general = generals.random()
            player.general = general
            // 根据武将调整血量上限
            player.maxHealth = 4 + general.healthBonus
            player.health = player.maxHealth
        }
        
        // 第三步：发初始手牌并记录
        val initialCards = mutableListOf<Pair<String, List<Card>>>()
        room.players.forEach { player ->
            val cardCount = when (player.identity) {
                Identity.LORD -> 6  // 主公6张
                else -> 4  // 其他角色4张
            }
            
            // 清空现有手牌
            player.cards.clear()
            val drawnCards = mutableListOf<Card>()
            
            repeat(cardCount) {
                if (room.deck.isNotEmpty()) {
                    val card = room.deck.removeAt(0)
                    player.cards.add(card)
                    drawnCards.add(card)
                } else {
                    // 重新洗牌
                    if (room.discardPile.isNotEmpty()) {
                        room.deck.addAll(room.discardPile.shuffled())
                        room.discardPile.clear()
                        if (room.deck.isNotEmpty()) {
                            val card = room.deck.removeAt(0)
                            player.cards.add(card)
                            drawnCards.add(card)
                        }
                    }
                }
            }
            
            // 记录每个玩家的初始手牌
            initialCards.add(Pair(player.id, drawnCards))
        }
        
        // 设置游戏状态
        room.gameState = GameState.PLAYING
        room.currentPlayerIndex = 0  // 从主公开始
        room.turnCount = 1
        room.gamePhase = GamePhase.PLAY  // 游戏开始后直接进入出牌阶段
        
        return Pair(room, initialCards)
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

    fun selectAbundantHarvestCard(roomId: String, playerId: String, selectedCardId: String): Card? {
        val room = rooms[roomId] ?: return null
        val pendingResponse = room.pendingResponse ?: return null
        val player = room.players.find { it.id == playerId } ?: return null
        
        // 验证是否是五谷丰登且是当前选择玩家
        if (!pendingResponse.isAbundantHarvest) return null
        
        val alivePlayers = room.players.filter { it.health > 0 }
        if (pendingResponse.currentSelectionPlayerIndex >= alivePlayers.size) return null
        
        val currentPlayer = alivePlayers[pendingResponse.currentSelectionPlayerIndex]
        if (currentPlayer.id != playerId) return null
        
        // 验证选择的卡牌是否在可用列表中
        val selectedCardIndex = pendingResponse.availableCards.indexOfFirst { it.id == selectedCardId }
        if (selectedCardIndex == -1) return null
        
        val selectedCard = pendingResponse.availableCards.removeAt(selectedCardIndex)
        
        // 将卡牌添加到玩家手牌
        player.cards.add(selectedCard)
        
        // 记录选择
        pendingResponse.selectedCards[playerId] = selectedCardId
        
        // 移动到下一个玩家
        pendingResponse.currentSelectionPlayerIndex++
        
        // 检查是否所有玩家都已选择
        if (pendingResponse.currentSelectionPlayerIndex >= alivePlayers.size) {
            // 五谷丰登完成，将剩余卡牌（如果有）放入弃牌堆
            room.discardPile.addAll(pendingResponse.availableCards)
            room.discardPile.add(pendingResponse.originalCard) // 五谷丰登本身也进入弃牌堆
            
            // 保存选择记录到房间状态中供WebSocket使用
            room.lastAbundantHarvestSelections = pendingResponse.selectedCards.mapValues { (playerId, cardId) ->
                // 从玩家手牌中找到对应卡牌
                room.players.find { it.id == playerId }?.cards?.find { it.id == cardId }
                    ?: Card("unknown", "未知卡牌", CardType.BASIC, "")
            }
            
            room.pendingResponse = null
        } else {
            // 更新到下一个玩家
            pendingResponse.targetPlayerId = alivePlayers[pendingResponse.currentSelectionPlayerIndex].id
        }
        
        return selectedCard
    }

    // ======================== 新系统相关方法 ========================
    
    /**
     * 新系统的响应处理
     */
    fun respondToCardNew(roomId: String, playerId: String, responseCardId: String?, accept: Boolean): CardExecutionContext? {
        val room = rooms[roomId] ?: return null
        val context = room.activeCardExecution ?: return null
        
        when (context.phase) {
            CardExecutionPhase.NULLIFICATION -> {
                val responseCard = if (accept && responseCardId != null) {
                    room.players.find { it.id == playerId }?.cards?.find { it.id == responseCardId }
                } else {
                    null // 不响应或没有响应卡牌
                }
                return cardExecutionEngine.handleNullificationResponse(context.id, playerId, responseCard, room)
            }
            CardExecutionPhase.SPECIAL_EXECUTION -> {
                responseCardId?.let {
                    return cardExecutionEngine.handleSpecialResponse(context.id, playerId, it, room)
                }
            }
            else -> return null
        }
        
        return null
    }
    
    /**
     * 执行卡牌效果
     */
    fun executeCardEffect(roomId: String, executionId: String): CardExecutionContext? {
        val room = rooms[roomId] ?: return null
        return cardExecutionEngine.executeCardEffect(executionId, room)
    }
    
    /**
     * 清理卡牌执行上下文
     */
    fun cleanupCardExecution(roomId: String, executionId: String) {
        cardExecutionEngine.cleanupExecution(executionId)
        rooms[roomId]?.activeCardExecution = null
    }

}