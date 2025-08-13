package moe.gensoukyo.tbc.server.service

import moe.gensoukyo.tbc.shared.model.*

/**
 * 卡牌效果处理服务
 */
class CardEffectService {
    
    /**
     * 处理基本牌效果
     */
    fun handleBasicCard(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        return when (card.name) {
            "杀" -> handleSha(card, caster, targets, room)
            "闪" -> handleShan(card, caster, targets, room)
            "桃" -> handleTao(card, caster, targets, room)
            else -> CardEffectResult(false, "未知的基本牌: ${card.name}")
        }
    }
    
    /**
     * 处理锦囊牌效果
     */
    fun handleTrickCard(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        return when (card.name) {
            "决斗" -> handleDuel(card, caster, targets, room)
            "火攻" -> handleFireAttack(card, caster, targets, room)
            "无中生有" -> handleSomethingForNothing(card, caster, targets, room)
            "无懈可击" -> handleNegate(card, caster, targets, room)
            "铁索连环" -> handleIronChain(card, caster, targets, room)
            "过河拆桥" -> handleDismantling(card, caster, targets, room)
            "五谷丰登" -> handleAbundantHarvest(card, caster, targets, room)
            "桃园结义" -> handlePeachGarden(card, caster, targets, room)
            "借刀杀人" -> handleBorrowKnife(card, caster, targets, room)
            "顺手牵羊" -> handleSwindle(card, caster, targets, room)
            "南蛮入侵" -> handleBarbarianInvasion(card, caster, targets, room)
            "万箭齐发" -> handleArrowBarrage(card, caster, targets, room)
            else -> CardEffectResult(false, "未知的锦囊牌: ${card.name}")
        }
    }
    
    /**
     * 处理装备牌
     */
    fun handleEquipmentCard(card: Card, caster: Player, room: GameRoom): CardEffectResult {
        return when (card.subType) {
            CardSubType.WEAPON -> {
                caster.equipment.weapon?.let { room.discardPile.add(it) }
                caster.equipment.weapon = card
                CardEffectResult(true, "${caster.name} 装备了 ${card.name}")
            }
            CardSubType.ARMOR -> {
                caster.equipment.armor?.let { room.discardPile.add(it) }
                caster.equipment.armor = card
                CardEffectResult(true, "${caster.name} 装备了 ${card.name}")
            }
            CardSubType.HORSE -> {
                if (card.range > 0) {
                    // 防御马
                    caster.equipment.defensiveHorse?.let { room.discardPile.add(it) }
                    caster.equipment.defensiveHorse = card
                } else {
                    // 攻击马
                    caster.equipment.offensiveHorse?.let { room.discardPile.add(it) }
                    caster.equipment.offensiveHorse = card
                }
                CardEffectResult(true, "${caster.name} 装备了 ${card.name}")
            }
            else -> CardEffectResult(false, "未知的装备类型")
        }
    }
    
    // 基本牌效果实现
    private fun handleSha(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        if (targets.isEmpty()) return CardEffectResult(false, "杀需要指定目标")
        val target = targets.first()
        
        // 简化版：直接造成1点伤害
        target.health = maxOf(0, target.health - 1)
        
        return CardEffectResult(true, "${caster.name} 对 ${target.name} 使用杀，造成1点伤害")
    }
    
    private fun handleShan(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        // 闪通常在响应阶段使用，这里只是占位
        return CardEffectResult(true, "${caster.name} 使用了闪")
    }
    
    private fun handleTao(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        val target = if (targets.isNotEmpty()) targets.first() else caster
        
        if (target.health < target.maxHealth) {
            target.health = minOf(target.maxHealth, target.health + 1)
            return CardEffectResult(true, "${caster.name} 对 ${target.name} 使用桃，恢复1点体力")
        }
        
        return CardEffectResult(false, "${target.name} 体力已满")
    }
    
    // 锦囊牌效果实现
    private fun handleDuel(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        if (targets.isEmpty()) return CardEffectResult(false, "决斗需要指定目标")
        val target = targets.first()
        
        // 简化版：直接对目标造成1点伤害
        target.health = maxOf(0, target.health - 1)
        
        return CardEffectResult(true, "${caster.name} 对 ${target.name} 使用决斗")
    }
    
    private fun handleFireAttack(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        if (targets.isEmpty()) return CardEffectResult(false, "火攻需要指定目标")
        val target = targets.first()
        
        // 简化版：直接造成1点火焰伤害
        target.health = maxOf(0, target.health - 1)
        
        return CardEffectResult(true, "${caster.name} 对 ${target.name} 使用火攻")
    }
    
    private fun handleSomethingForNothing(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        // 摸两张牌的逻辑需要在GameService中实现
        return CardEffectResult(true, "${caster.name} 使用无中生有，摸两张牌", drawCards = 2)
    }
    
    private fun handleNegate(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        return CardEffectResult(true, "${caster.name} 使用无懈可击")
    }
    
    private fun handleIronChain(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        if (targets.isEmpty()) {
            // 无目标使用：摸一张牌
            return CardEffectResult(true, "${caster.name} 使用铁索连环，摸一张牌", drawCards = 1)
        }
        
        // 改变目标的铁索状态
        targets.forEach { target ->
            target.isChained = !target.isChained
        }
        
        return CardEffectResult(true, "${caster.name} 使用铁索连环")
    }
    
    private fun handleDismantling(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        if (targets.isEmpty()) return CardEffectResult(false, "过河拆桥需要指定目标")
        val target = targets.first()
        
        // 简化版：随机弃置一张手牌
        if (target.cards.isNotEmpty()) {
            val discardedCard = target.cards.removeAt((0 until target.cards.size).random())
            room.discardPile.add(discardedCard)
            return CardEffectResult(true, "${caster.name} 对 ${target.name} 使用过河拆桥，弃置了 ${discardedCard.name}")
        }
        
        return CardEffectResult(false, "${target.name} 没有手牌可弃置")
    }
    
    private fun handleAbundantHarvest(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        // 所有玩家各摸一张牌
        return CardEffectResult(true, "${caster.name} 使用五谷丰登，所有玩家各摸一张牌", drawCards = 1, affectAllPlayers = true)
    }
    
    private fun handlePeachGarden(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        // 所有玩家回复1点体力
        room.players.forEach { player ->
            if (player.health < player.maxHealth) {
                player.health = minOf(player.maxHealth, player.health + 1)
            }
        }
        
        return CardEffectResult(true, "${caster.name} 使用桃园结义，所有玩家回复1点体力")
    }
    
    private fun handleBorrowKnife(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        if (targets.isEmpty()) return CardEffectResult(false, "借刀杀人需要指定目标")
        
        return CardEffectResult(true, "${caster.name} 使用借刀杀人")
    }
    
    private fun handleSwindle(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        if (targets.isEmpty()) return CardEffectResult(false, "顺手牵羊需要指定目标")
        val target = targets.first()
        
        // 简化版：随机获得一张手牌
        if (target.cards.isNotEmpty()) {
            val stolenCard = target.cards.removeAt((0 until target.cards.size).random())
            caster.cards.add(stolenCard)
            return CardEffectResult(true, "${caster.name} 对 ${target.name} 使用顺手牵羊，获得了一张牌")
        }
        
        return CardEffectResult(false, "${target.name} 没有手牌")
    }
    
    private fun handleBarbarianInvasion(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        // 所有其他玩家需要出杀，否则受到1点伤害
        room.players.filter { it.id != caster.id }.forEach { player ->
            // 简化版：直接造成1点伤害
            player.health = maxOf(0, player.health - 1)
        }
        
        return CardEffectResult(true, "${caster.name} 使用南蛮入侵")
    }
    
    private fun handleArrowBarrage(card: Card, caster: Player, targets: List<Player>, room: GameRoom): CardEffectResult {
        // 所有其他玩家需要出闪，否则受到1点伤害
        room.players.filter { it.id != caster.id }.forEach { player ->
            // 简化版：直接造成1点伤害
            player.health = maxOf(0, player.health - 1)
        }
        
        return CardEffectResult(true, "${caster.name} 使用万箭齐发")
    }
}

/**
 * 卡牌效果结果
 */
data class CardEffectResult(
    val success: Boolean,
    val message: String,
    val drawCards: Int = 0,  // 需要摸牌数量
    val affectAllPlayers: Boolean = false  // 是否影响所有玩家
)