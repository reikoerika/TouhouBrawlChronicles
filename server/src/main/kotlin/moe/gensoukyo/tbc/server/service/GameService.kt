package moe.gensoukyo.tbc.server.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.gensoukyo.tbc.shared.card.CardFactory
import moe.gensoukyo.tbc.shared.model.*
import moe.gensoukyo.tbc.server.card.*
import moe.gensoukyo.tbc.shared.card.CardExecutionContext
import moe.gensoukyo.tbc.shared.card.CardExecutionPhase
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
    
    // ======================== 新的卡牌执行系统 ========================
    
    /**
     * 新的出牌方法 - 使用卡牌执行引擎
     */
    fun playCardNew(roomId: String, playerId: String, cardId: String, targetIds: List<String> = emptyList()): CardExecutionContext? {
        val room = rooms[roomId] ?: return null
        val player = room.players.find { it.id == playerId } ?: return null
        val cardIndex = player.cards.indexOfFirst { it.id == cardId }
        if (cardIndex == -1) return null
        
        val card = player.cards[cardIndex]
        val targets = targetIds.mapNotNull { targetId -> 
            room.players.find { it.id == targetId } 
        }
        
        // 验证出牌合法性
        if (!isValidCardPlay(card, player, targets, room)) {
            return null
        }
        
        // 从手牌中移除卡牌
        player.cards.removeAt(cardIndex)
        
        // 开始执行
        val context = cardExecutionEngine.startExecution(card, player, targets, room)
        
        // 保存执行上下文到房间
        room.activeCardExecution = context
        
        return context
    }
    
    /**
     * 处理卡牌响应
     */
    fun respondToCardNew(roomId: String, playerId: String, responseCardId: String?, accept: Boolean): CardExecutionContext? {
        val room = rooms[roomId] ?: return null
        val context = room.activeCardExecution ?: return null
        
        val respondingPlayer = room.players.find { it.id == playerId } ?: return null
        var responseCard: Card? = null
        
        // 处理响应卡牌
        if (responseCardId != null && accept) {
            val cardIndex = respondingPlayer.cards.indexOfFirst { it.id == responseCardId }
            if (cardIndex != -1) {
                responseCard = respondingPlayer.cards.removeAt(cardIndex)
                room.discardPile.add(responseCard)
            }
        }
        
        // 根据当前阶段处理响应
        val updatedContext = when (context.phase) {
            CardExecutionPhase.NULLIFICATION -> {
                cardExecutionEngine.handleNullificationResponse(context.id, playerId, responseCard)
            }
            CardExecutionPhase.SPECIAL_EXECUTION -> {
                responseCardId?.let { 
                    cardExecutionEngine.handleSpecialResponse(context.id, playerId, it, room)
                }
            }
            else -> null
        }
        
        // 如果执行完成，清理状态
        if (updatedContext?.isCompleted == true) {
            room.activeCardExecution = null
            cardExecutionEngine.cleanupExecution(context.id)
            
            // 将原始卡牌加入弃牌堆
            if (context.card.type != CardType.EQUIPMENT) {
                room.discardPile.add(context.card)
            }
        }
        
        return updatedContext
    }
    
    /**
     * 继续执行卡牌效果（用于无懈可击响应完成后）
     */
    fun continueCardExecution(roomId: String): CardExecutionContext? {
        val room = rooms[roomId] ?: return null
        val context = room.activeCardExecution ?: return null
        
        val updatedContext = when (context.phase) {
            CardExecutionPhase.RESOLUTION -> {
                cardExecutionEngine.executeCardEffect(context.id, room)
            }
            else -> context
        }
        
        // 如果执行完成，清理状态
        if (updatedContext?.isCompleted == true) {
            room.activeCardExecution = null
            cardExecutionEngine.cleanupExecution(context.id)
            
            // 将原始卡牌加入弃牌堆
            if (context.card.type != CardType.EQUIPMENT) {
                room.discardPile.add(context.card)
            }
        }
        
        return updatedContext
    }
    
    /**
     * 获取当前需要响应的玩家
     */
    fun getCurrentResponseTarget(roomId: String): String? {
        val room = rooms[roomId] ?: return null
        val context = room.activeCardExecution ?: return null
        
        return cardExecutionEngine.getResponseTarget(context.id)
    }
    
    /**
     * 获取可用的响应选项（如五谷丰登的卡牌列表）
     */
    fun getAvailableResponseOptions(roomId: String): List<String> {
        val room = rooms[roomId] ?: return emptyList()
        val context = room.activeCardExecution ?: return emptyList()
        
        return when (context.card.name) {
            "五谷丰登" -> {
                context.specialData.abundantHarvestCards.map { it.id }
            }
            else -> emptyList()
        }
    }
    
    // ======================== 辅助方法 ========================
    
    private fun isValidCardPlay(card: Card, player: Player, targets: List<Player>, room: GameRoom): Boolean {
        // 基本验证逻辑
        if (room.gameState != GameState.PLAYING) return false
        if (room.gamePhase != GamePhase.PLAY) return false
        
        // 验证目标数量和类型
        return when (card.targetType) {
            TargetType.NONE -> targets.isEmpty()
            TargetType.SINGLE -> targets.size == 1
            TargetType.MULTIPLE -> targets.isNotEmpty()
            TargetType.ALL_OTHERS -> {
                val expectedTargets = room.players.filter { it.id != player.id && it.health > 0 }
                targets.size == expectedTargets.size
            }
            TargetType.ALL_PLAYERS -> {
                val expectedTargets = room.players.filter { it.health > 0 }
                targets.size == expectedTargets.size
            }
        }
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
        room.gamePhase = GamePhase.DRAW
        
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
    
    fun playCard(roomId: String, playerId: String, cardId: String, targetIds: List<String> = emptyList()): Pair<PlayedCard?, PendingResponse?>? {
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
        
        // 检查是否需要响应
        val needsResponse = checkIfNeedsResponse(card, targets)
        
        if (needsResponse.isNotEmpty()) {
            // 需要响应，创建待处理响应
            val target = needsResponse.first()
            val responseType = determineResponseType(card)
            
            val pendingResponse = PendingResponse(
                targetPlayerId = target.id,
                originalCard = card,
                originalPlayerId = playerId,
                responseType = responseType,
                isDuel = card.name == "决斗",
                duelCurrentPlayer = if (card.name == "决斗") target.id else null,
                duelInitiator = if (card.name == "决斗") playerId else null,
                duelTarget = if (card.name == "决斗") target.id else null,
                isAbundantHarvest = card.name == "五谷丰登"
            )
            
            room.pendingResponse = pendingResponse
            
            // 从手牌移除但暂不进入弃牌堆，等响应完成后处理
            player.cards.removeAt(cardIndex)
            
            // 创建出牌记录
            val playedCard = PlayedCard(
                card = card,
                playerId = playerId,
                playerName = player.name,
                turnNumber = room.turnCount,
                targetIds = targetIds
            )
            
            return Pair(playedCard, pendingResponse)
        } else {
            // 不需要响应，直接处理效果
            return handleCardDirectly(room, player, cardIndex, card, targets, targetIds)
        }
    }
    
    private fun checkIfNeedsResponse(card: Card, targets: List<Player>): List<Player> {
        return when (card.name) {
            "杀" -> targets // 杀需要目标响应闪
            "决斗" -> targets // 决斗是基本牌，需要目标响应杀或闪
            else -> when (card.type) {
                CardType.TRICK -> targets // 锦囊牌（包括五谷丰登）可能需要无懈可击
                else -> emptyList()
            }
        }
    }
    
    private fun determineResponseType(card: Card): ResponseType {
        return when (card.name) {
            "杀" -> ResponseType.DODGE  // 杀只能用闪响应
            "决斗" -> ResponseType.DUEL_KILL  // 决斗需要用杀响应
            else -> when (card.type) {
                CardType.TRICK -> ResponseType.NULLIFICATION  // 锦囊牌用无懈可击响应
                else -> ResponseType.OPTIONAL
            }
        }
    }
    
    private fun handleCardDirectly(
        room: GameRoom, 
        player: Player, 
        cardIndex: Int, 
        card: Card, 
        targets: List<Player>, 
        targetIds: List<String>
    ): Pair<PlayedCard?, PendingResponse?> {
        // 处理卡牌效果
        val effectResult = when (card.type) {
            CardType.BASIC -> cardEffectService.handleBasicCard(card, player, targets, room)
            CardType.TRICK -> cardEffectService.handleTrickCard(card, player, targets, room)
            CardType.EQUIPMENT -> cardEffectService.handleEquipmentCard(card, player, room)
        }
        
        if (!effectResult.success) {
            return Pair(null, null)
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
                    drawCard(room.id, p.id, effectResult.drawCards)
                }
            } else {
                drawCard(room.id, player.id, effectResult.drawCards)
            }
        }
        
        // 创建出牌记录
        val playedCard = PlayedCard(
            card = card,
            playerId = player.id,
            playerName = player.name,
            turnNumber = room.turnCount,
            targetIds = targetIds
        )
        
        // 添加到玩家的出牌记录
        player.playedCards.add(playedCard)
        
        // 添加到当前回合的出牌区
        room.currentTurnPlayedCards.add(playedCard)
        
        return Pair(playedCard, null)
    }
    
    private fun handleAbundantHarvest(room: GameRoom, player: Player, cardIndex: Int, card: Card): Pair<PlayedCard?, PendingResponse?> {
        // 从手牌中移除五谷丰登
        player.cards.removeAt(cardIndex)
        
        // 从牌堆顶抽取等同于存活玩家数量的牌
        val alivePlayers = room.players.filter { it.health > 0 }
        val availableCards = mutableListOf<Card>()
        
        repeat(alivePlayers.size) {
            if (room.deck.isEmpty() && room.discardPile.isNotEmpty()) {
                // 重新洗牌
                room.deck.addAll(room.discardPile.shuffled())
                room.discardPile.clear()
            }
            
            if (room.deck.isNotEmpty()) {
                availableCards.add(room.deck.removeAt(0))
            }
        }
        
        // 创建五谷丰登的PendingResponse
        val pendingResponse = PendingResponse(
            targetPlayerId = alivePlayers.first().id,
            originalCard = card,
            originalPlayerId = player.id,
            responseType = ResponseType.ABUNDANT_HARVEST,
            isAbundantHarvest = true,
            availableCards = availableCards,
            currentSelectionPlayerIndex = 0
        )
        
        room.pendingResponse = pendingResponse
        
        // 创建出牌记录
        val playedCard = PlayedCard(
            card = card,
            playerId = player.id,
            playerName = player.name,
            turnNumber = room.turnCount,
            targetIds = alivePlayers.map { it.id }
        )
        
        return Pair(playedCard, pendingResponse)
    }
    
    fun respondToCard(roomId: String, playerId: String, responseCardId: String?, accept: Boolean): CardResolutionResult? {
        val room = rooms[roomId] ?: return null
        val pendingResponse = room.pendingResponse ?: return null
        val respondingPlayer = room.players.find { it.id == playerId } ?: return null
        
        // 验证是否是正确的响应者
        if (playerId != pendingResponse.targetPlayerId && (!pendingResponse.isDuel || playerId != pendingResponse.duelCurrentPlayer)) {
            return null
        }
        
        var responseCard: Card? = null
        if (responseCardId != null) {
            val cardIndex = respondingPlayer.cards.indexOfFirst { it.id == responseCardId }
            if (cardIndex != -1) {
                responseCard = respondingPlayer.cards[cardIndex]
                // 验证响应卡牌是否有效
                if (isValidResponse(responseCard, pendingResponse.responseType)) {
                    respondingPlayer.cards.removeAt(cardIndex)
                    room.discardPile.add(responseCard)
                }
            }
        }
        
        // 记录响应
        val cardResponse = CardResponse(
            playerId = playerId,
            playerName = respondingPlayer.name,
            responseCard = responseCard,
            accepted = accept && responseCard != null
        )
        
        pendingResponse.responses.add(cardResponse)
        
        // 处理决斗的特殊逻辑
        if (pendingResponse.isDuel) {
            return handleDuelResponse(room, pendingResponse, cardResponse)
        } else {
            // 普通卡牌响应，解析最终结果
            val result = resolveCardEffect(room, pendingResponse, cardResponse)
            
            // 清除待处理响应
            room.pendingResponse = null
            
            // 将原始卡牌加入弃牌堆（如果不是装备）
            if (pendingResponse.originalCard.type != CardType.EQUIPMENT) {
                room.discardPile.add(pendingResponse.originalCard)
            }
            
            return result
        }
    }
    
    private fun handleDuelResponse(room: GameRoom, pendingResponse: PendingResponse, response: CardResponse): CardResolutionResult? {
        val duelInitiator = room.players.find { it.id == pendingResponse.duelInitiator!! }
        val duelTarget = room.players.find { it.id == pendingResponse.duelTarget!! }
        
        if (duelInitiator == null || duelTarget == null) {
            return CardResolutionResult(success = false, message = "决斗参与者不存在")
        }
        
        if (response.accepted && response.responseCard?.name == "杀") {
            // 成功出杀，增加计数并切换到另一方
            pendingResponse.duelKillCount++
            
            // 切换当前出杀的玩家
            pendingResponse.duelCurrentPlayer = if (pendingResponse.duelCurrentPlayer == duelTarget.id) {
                pendingResponse.duelInitiator
            } else {
                pendingResponse.duelTarget
            }
            
            // 更新targetPlayerId为当前需要出杀的玩家
            pendingResponse.targetPlayerId = pendingResponse.duelCurrentPlayer!!
            
            // 返回继续决斗的结果
            val currentPlayerName = room.players.find { it.id == pendingResponse.duelCurrentPlayer }?.name ?: "未知"
            return CardResolutionResult(
                success = true,
                blocked = false,
                message = "${response.playerName}出了杀，轮到${currentPlayerName}出杀"
            )
        } else {
            // 没有出杀或拒绝出杀，决斗结束
            pendingResponse.duelFinished = true
            
            // 不出杀的一方受到1点伤害
            val loserPlayer = room.players.find { it.id == response.playerId }!!
            loserPlayer.health = maxOf(0, loserPlayer.health - 1)
            
            val winnerPlayer = room.players.find {
                it.id != response.playerId && (it.id == pendingResponse.duelInitiator || it.id == pendingResponse.duelTarget) 
            }!!
            
            // 清除待处理响应
            room.pendingResponse = null
            
            // 将决斗卡牌加入弃牌堆
            room.discardPile.add(pendingResponse.originalCard)
            
            // 检查玩家是否死亡
            val gameResult = if (loserPlayer.health <= 0) {
                "决斗结束，${loserPlayer.name}没有出杀，受到1点伤害并死亡"
            } else {
                "决斗结束，${loserPlayer.name}没有出杀，受到1点伤害"
            }
            
            return CardResolutionResult(
                success = true,
                blocked = false,
                damage = 1,
                message = gameResult
            )
        }
    }
    
    private fun isValidResponse(responseCard: Card, responseType: ResponseType): Boolean {
        return when (responseType) {
            ResponseType.DODGE -> responseCard.name == "闪"
            ResponseType.NULLIFICATION -> responseCard.name == "无懈可击"
            ResponseType.DUEL_KILL -> responseCard.name == "杀"
            ResponseType.ABUNDANT_HARVEST -> true // 五谷丰登选择任何可用卡牌都有效
            ResponseType.OPTIONAL -> true
        }
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
    
    private fun startAbundantHarvestSelection(room: GameRoom, originalPlayer: Player) {
        val originalCard = Card("五谷丰登", "所有玩家依次选择获得一张牌", CardType.TRICK, "五谷丰登")
        
        // 从牌堆顶抽取等同于存活玩家数量的牌
        val alivePlayers = room.players.filter { it.health > 0 }
        val availableCards = mutableListOf<Card>()
        
        repeat(alivePlayers.size) {
            if (room.deck.isEmpty() && room.discardPile.isNotEmpty()) {
                // 重新洗牌
                room.deck.addAll(room.discardPile.shuffled())
                room.discardPile.clear()
            }
            
            if (room.deck.isNotEmpty()) {
                availableCards.add(room.deck.removeAt(0))
            }
        }
        
        // 创建五谷丰登的PendingResponse
        val pendingResponse = PendingResponse(
            targetPlayerId = alivePlayers.first().id,
            originalCard = originalCard,
            originalPlayerId = originalPlayer.id,
            responseType = ResponseType.ABUNDANT_HARVEST,
            isAbundantHarvest = true,
            availableCards = availableCards,
            currentSelectionPlayerIndex = 0
        )
        
        room.pendingResponse = pendingResponse
    }
    
    private fun resolveCardEffect(room: GameRoom, pendingResponse: PendingResponse, response: CardResponse): CardResolutionResult {
        val originalCard = pendingResponse.originalCard
        val targets = listOfNotNull(room.players.find { it.id == pendingResponse.targetPlayerId })
        val originalPlayer = room.players.find { it.id == pendingResponse.originalPlayerId }
        
        return when {
            response.accepted && response.responseCard != null -> {
                // 响应成功，卡牌效果被阻挡
                CardResolutionResult(
                    success = false,
                    blocked = true,
                    message = "${response.playerName}使用${response.responseCard!!.name}成功阻挡了${originalCard.name}"
                )
            }
            else -> {
                // 没有响应或响应失败，执行原始效果
                if (originalPlayer != null) {
                    val effectResult = when (originalCard.type) {
                        CardType.BASIC -> cardEffectService.handleBasicCard(originalCard, originalPlayer, targets, room)
                        CardType.TRICK -> cardEffectService.handleTrickCard(originalCard, originalPlayer, targets, room)
                        CardType.EQUIPMENT -> cardEffectService.handleEquipmentCard(originalCard, originalPlayer, room)
                    }
                    
                    // 检查是否是五谷丰登且需要特殊处理
                    if (effectResult.requiresSpecialHandling && originalCard.name == "五谷丰登") {
                        // 启动五谷丰登选牌流程
                        startAbundantHarvestSelection(room, originalPlayer)
                        return CardResolutionResult(
                            success = true,
                            blocked = false,
                            message = "${originalCard.name}开始，玩家依次选择卡牌"
                        )
                    }
                    
                    CardResolutionResult(
                        success = effectResult.success,
                        blocked = false,
                        damage = if (originalCard.damage > 0) originalCard.damage else 0,
                        healing = if (originalCard.healing > 0) originalCard.healing else 0,
                        message = "${originalCard.name}生效"
                    )
                } else {
                    CardResolutionResult(success = false, message = "原始玩家不存在")
                }
            }
        }
    }
}