package moe.gensoukyo.tbc.server.card

import moe.gensoukyo.tbc.shared.model.*
import moe.gensoukyo.tbc.server.service.CardEffectService
import moe.gensoukyo.tbc.shared.card.CardExecutionContext
import moe.gensoukyo.tbc.shared.card.CardExecutionPhase
import moe.gensoukyo.tbc.shared.card.CardExecutionResult
import moe.gensoukyo.tbc.shared.card.CardResponse
import moe.gensoukyo.tbc.shared.card.CardResponseType
import java.util.*

/**
 * 卡牌执行引擎
 * 负责管理卡牌从出牌到结算完成的整个流程
 */
class CardExecutionEngine(
    private val cardEffectService: CardEffectService
) {
    private val activeExecutions = mutableMapOf<String, CardExecutionContext>()
    
    /**
     * 开始执行一张卡牌
     */
    fun startExecution(
        card: Card,
        caster: Player,
        targets: List<Player>,
        room: GameRoom
    ): CardExecutionContext {
        val executionId = UUID.randomUUID().toString()
        val context = CardExecutionContext(
            id = executionId,
            card = card,
            caster = caster,
            originalTargets = targets,
            finalTargets = targets.toList()
        )
        
        activeExecutions[executionId] = context
        
        // 确定下一个阶段
        context.phase = when {
            isInstantEffect(card) -> CardExecutionPhase.RESOLUTION
            needsNullificationCheck(card, targets) -> CardExecutionPhase.NULLIFICATION
            else -> CardExecutionPhase.RESOLUTION
        }
        
        return context
    }
    
    /**
     * 处理无懈可击响应
     */
    fun handleNullificationResponse(
        executionId: String,
        responderId: String,
        responseCard: Card?,
        room: GameRoom
    ): CardExecutionContext? {
        val context = activeExecutions[executionId] ?: return null
        
        if (context.phase != CardExecutionPhase.NULLIFICATION) return null
        
        val response = CardResponse(
            playerId = responderId,
            playerName = room.players.find { it.id == responderId }?.name ?: "未知",
            responseCard = responseCard,
            responseType = CardResponseType.NULLIFICATION
        )
        
        context.responses.add(response)
        
        // 如果有无懈可击响应，卡牌被阻挡
        if (responseCard?.name == "无懈可击") {
            context.isBlocked = true
            context.phase = CardExecutionPhase.COMPLETED
            context.result = CardExecutionResult(
                success = false,
                message = "${context.card.name}被无懈可击阻挡"
            )
        } else {
            // 检查是否所有玩家都已响应
            val alivePlayers = room.players.filter { it.health > 0 }
            val respondedPlayers = context.responses.map { it.playerId }.toSet()
            
            if (alivePlayers.all { it.id in respondedPlayers }) {
                // 所有玩家都已响应，进入结算阶段
                context.phase = CardExecutionPhase.RESOLUTION
            }
            // 如果还有玩家未响应，保持在NULLIFICATION阶段
        }
        
        return context
    }
    
    /**
     * 执行卡牌效果
     */
    fun executeCardEffect(
        executionId: String,
        room: GameRoom
    ): CardExecutionContext? {
        val context = activeExecutions[executionId] ?: return null
        
        if (context.phase != CardExecutionPhase.RESOLUTION) return null
        
        // 如果被阻挡，直接完成
        if (context.isBlocked) {
            context.phase = CardExecutionPhase.COMPLETED
            return context
        }
        
        val effectResult = when (context.card.type) {
            CardType.BASIC -> cardEffectService.handleBasicCard(
                context.card, 
                context.caster, 
                context.finalTargets, 
                room
            )
            CardType.TRICK -> cardEffectService.handleTrickCard(
                context.card, 
                context.caster, 
                context.finalTargets, 
                room
            )
            CardType.EQUIPMENT -> cardEffectService.handleEquipmentCard(
                context.card, 
                context.caster, 
                room
            )
        }
        
        // 检查是否需要特殊处理
        if (effectResult.requiresSpecialHandling) {
            context.phase = CardExecutionPhase.SPECIAL_EXECUTION
            setupSpecialExecution(context, room)
        } else {
            context.phase = CardExecutionPhase.COMPLETED
            context.result = CardExecutionResult(
                success = effectResult.success,
                message = effectResult.message,
                cardsDrawn = effectResult.drawCards
            )
        }
        
        return context
    }
    
    /**
     * 处理特殊执行（如五谷丰登选牌）
     */
    fun handleSpecialResponse(
        executionId: String,
        responderId: String,
        selectedOption: String,
        room: GameRoom
    ): CardExecutionContext? {
        val context = activeExecutions[executionId] ?: return null
        
        if (context.phase != CardExecutionPhase.SPECIAL_EXECUTION) return null
        
        when (context.card.name) {
            "五谷丰登" -> handleAbundantHarvestSelection(context, responderId, selectedOption, room)
            // 其他特殊卡牌处理...
        }
        
        return context
    }
    
    /**
     * 获取需要响应的玩家
     */
    fun getResponseTarget(executionId: String, room: GameRoom): String? {
        val context = activeExecutions[executionId] ?: return null
        
        return when (context.phase) {
            CardExecutionPhase.NULLIFICATION -> {
                // 无懈可击阶段：返回null表示所有玩家都可以同时响应
                null
            }
            CardExecutionPhase.SPECIAL_EXECUTION -> {
                // 根据特殊卡牌类型返回需要响应的玩家
                getSpecialResponseTarget(context)
            }
            else -> null
        }
    }
    
    /**
     * 获取无懈可击阶段所有可以响应的玩家
     */
    fun getAllNullificationTargets(executionId: String, room: GameRoom): List<String> {
        val context = activeExecutions[executionId] ?: return emptyList()
        
        return if (context.phase == CardExecutionPhase.NULLIFICATION) {
            val alivePlayers = room.players.filter { it.health > 0 }
            val respondedPlayers = context.responses.map { it.playerId }.toSet()
            alivePlayers.filter { it.id !in respondedPlayers }.map { it.id }
        } else {
            emptyList()
        }
    }
    
    /**
     * 清理已完成的执行
     */
    fun cleanupExecution(executionId: String) {
        activeExecutions.remove(executionId)
    }
    
    /**
     * 获取执行上下文
     */
    fun getExecution(executionId: String): CardExecutionContext? {
        return activeExecutions[executionId]
    }
    
    // ======================== 私有辅助方法 ========================
    
    private fun isInstantEffect(card: Card): Boolean {
        // 装备牌、桃等立即生效的卡牌
        return when (card.type) {
            CardType.EQUIPMENT -> true
            CardType.BASIC -> card.name in listOf("桃")
            else -> false
        }
    }
    
    private fun needsNullificationCheck(card: Card, targets: List<Player>): Boolean {
        // 所有锦囊牌都需要无懈可击检查，不论是否有目标
        return card.type == CardType.TRICK
    }
    
    private fun setupSpecialExecution(context: CardExecutionContext, room: GameRoom) {
        when (context.card.name) {
            "五谷丰登" -> setupAbundantHarvest(context, room)
            // 其他特殊卡牌设置...
        }
    }
    
    private fun setupAbundantHarvest(context: CardExecutionContext, room: GameRoom) {
        val alivePlayers = room.players.filter { it.health > 0 }
        val availableCards = mutableListOf<Card>()
        
        // 从牌堆抽取卡牌
        repeat(alivePlayers.size) {
            if (room.deck.isEmpty() && room.discardPile.isNotEmpty()) {
                room.deck.addAll(room.discardPile.shuffled())
                room.discardPile.clear()
            }
            if (room.deck.isNotEmpty()) {
                availableCards.add(room.deck.removeAt(0))
            }
        }
        
        context.specialData = context.specialData.copy(
            abundantHarvestCards = availableCards,
            abundantHarvestPlayerOrder = alivePlayers.map { it.id },
            abundantHarvestCurrentIndex = 0
        )
    }
    
    private fun handleAbundantHarvestSelection(
        context: CardExecutionContext,
        playerId: String,
        selectedCardId: String,
        room: GameRoom
    ) {
        val availableCards = context.specialData.abundantHarvestCards.toMutableList()
        val currentIndex = context.specialData.abundantHarvestCurrentIndex
        val playerOrder = context.specialData.abundantHarvestPlayerOrder
        
        // 验证是否是当前玩家
        if (currentIndex >= playerOrder.size || playerOrder[currentIndex] != playerId) return
        
        // 选择卡牌
        val selectedCard = availableCards.find { it.id == selectedCardId } ?: return
        availableCards.remove(selectedCard)
        
        // 添加到玩家手牌
        val player = room.players.find { it.id == playerId }
        player?.cards?.add(selectedCard)
        
        // 更新索引
        val nextIndex = currentIndex + 1
        context.specialData = context.specialData.copy(
            abundantHarvestCards = availableCards,
            abundantHarvestCurrentIndex = nextIndex
        )
        
        // 检查是否完成
        if (nextIndex >= playerOrder.size) {
            // 五谷丰登完成
            room.discardPile.addAll(availableCards)  // 剩余卡牌进入弃牌堆
            context.phase = CardExecutionPhase.COMPLETED
            context.result = CardExecutionResult(
                success = true,
                message = "五谷丰登完成，所有玩家都已选择卡牌"
            )
        }
    }
    
    private fun getSpecialResponseTarget(context: CardExecutionContext): String? {
        return when (context.card.name) {
            "五谷丰登" -> {
                val currentIndex = context.specialData.abundantHarvestCurrentIndex
                val playerOrder = context.specialData.abundantHarvestPlayerOrder
                if (currentIndex < playerOrder.size) playerOrder[currentIndex] else null
            }
            else -> null
        }
    }
}