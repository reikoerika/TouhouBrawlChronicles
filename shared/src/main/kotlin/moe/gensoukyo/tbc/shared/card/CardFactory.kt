package moe.gensoukyo.tbc.shared.card

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import moe.gensoukyo.tbc.shared.model.*
import java.util.UUID

object CardFactory {
    private val basicCards = listOf(
        CardTemplate("杀", CardType.BASIC, CardSubType.BASIC, "对目标造成1点伤害", damage = 1, targetType = TargetType.SINGLE),
        CardTemplate("闪", CardType.BASIC, CardSubType.BASIC, "抵消一次攻击"),
        CardTemplate("桃", CardType.BASIC, CardSubType.BASIC, "恢复1点生命值", healing = 1, targetType = TargetType.NONE)
    )
    
    private val trickCards = listOf(
        // 普通锦囊
        CardTemplate("决斗", CardType.TRICK, CardSubType.INSTANT_TRICK, "与目标决斗，双方轮流出杀", targetType = TargetType.SINGLE),
        CardTemplate("火攻", CardType.TRICK, CardSubType.INSTANT_TRICK, "展示手牌，对方弃相同花色牌，否则受到1点火焰伤害", targetType = TargetType.SINGLE),
        CardTemplate("无中生有", CardType.TRICK, CardSubType.INSTANT_TRICK, "摸两张牌", targetType = TargetType.NONE),
        CardTemplate("无懈可击", CardType.TRICK, CardSubType.INSTANT_TRICK, "抵消一个锦囊牌的效果", targetType = TargetType.NONE),
        CardTemplate("铁索连环", CardType.TRICK, CardSubType.INSTANT_TRICK, "将目标横置或重置，或摸一张牌", targetType = TargetType.MULTIPLE),
        CardTemplate("过河拆桥", CardType.TRICK, CardSubType.INSTANT_TRICK, "弃置目标一张牌", targetType = TargetType.SINGLE),
        CardTemplate("五谷丰登", CardType.TRICK, CardSubType.INSTANT_TRICK, "所有玩家依次选择获得一张牌", targetType = TargetType.ALL_PLAYERS),
        CardTemplate("桃园结义", CardType.TRICK, CardSubType.INSTANT_TRICK, "所有玩家回复1点体力", targetType = TargetType.ALL_PLAYERS),
        CardTemplate("借刀杀人", CardType.TRICK, CardSubType.INSTANT_TRICK, "令目标对其攻击范围内的角色使用一张杀", targetType = TargetType.SINGLE),
        CardTemplate("顺手牵羊", CardType.TRICK, CardSubType.INSTANT_TRICK, "获得目标一张牌", targetType = TargetType.SINGLE),
        CardTemplate("南蛮入侵", CardType.TRICK, CardSubType.INSTANT_TRICK, "所有其他玩家需出杀，否则受到1点伤害", targetType = TargetType.ALL_OTHERS),
        CardTemplate("万箭齐发", CardType.TRICK, CardSubType.INSTANT_TRICK, "所有其他玩家需出闪，否则受到1点伤害", targetType = TargetType.ALL_OTHERS),
        
        // 延时锦囊
        CardTemplate("乐不思蜀", CardType.TRICK, CardSubType.DELAYED_TRICK, "判定，若不为红桃则跳过出牌阶段", targetType = TargetType.SINGLE),
        CardTemplate("闪电", CardType.TRICK, CardSubType.DELAYED_TRICK, "判定，若为黑桃2-9则受到3点雷电伤害", targetType = TargetType.SINGLE)
    )
    
    private val equipmentCards = listOf(
        // 武器
        CardTemplate("青釭剑", CardType.EQUIPMENT, CardSubType.WEAPON, "攻击范围2，你的杀无视目标防具", range = 2),
        CardTemplate("雌雄双股剑", CardType.EQUIPMENT, CardSubType.WEAPON, "攻击范围2，对异性目标使用杀时可令其选择弃两张牌或受伤害", range = 2),
        CardTemplate("青龙偃月刀", CardType.EQUIPMENT, CardSubType.WEAPON, "攻击范围3，杀被闪避后可再使用一张杀", range = 3),
        CardTemplate("丈八蛇矛", CardType.EQUIPMENT, CardSubType.WEAPON, "攻击范围3，可将两张手牌当杀使用", range = 3),
        CardTemplate("方天画戟", CardType.EQUIPMENT, CardSubType.WEAPON, "攻击范围4，手牌数小于等于2时，杀可指定至多3个目标", range = 4),
        CardTemplate("贯石斧", CardType.EQUIPMENT, CardSubType.WEAPON, "攻击范围3，杀被闪避后可弃两张牌令杀依然造成伤害", range = 3),
        CardTemplate("麒麟弓", CardType.EQUIPMENT, CardSubType.WEAPON, "攻击范围5，杀造成伤害后可弃置其一张坐骑牌", range = 5),
        CardTemplate("诸葛连弩", CardType.EQUIPMENT, CardSubType.WEAPON, "攻击范围1，出牌阶段可使用任意张杀", range = 1),
        
        // 防具
        CardTemplate("八卦阵", CardType.EQUIPMENT, CardSubType.ARMOR, "受到伤害时可判定，红色则视为使用闪"),
        CardTemplate("仁王盾", CardType.EQUIPMENT, CardSubType.ARMOR, "黑色杀对你无效"),
        CardTemplate("白银狮子", CardType.EQUIPMENT, CardSubType.ARMOR, "受到超过1点伤害时，防止多余伤害"),
        
        // 坐骑
        CardTemplate("赤兔马", CardType.EQUIPMENT, CardSubType.HORSE, "攻击距离+1", range = -1),
        CardTemplate("的卢", CardType.EQUIPMENT, CardSubType.HORSE, "攻击距离+1", range = -1),
        CardTemplate("爪黄飞电", CardType.EQUIPMENT, CardSubType.HORSE, "攻击距离+1", range = -1),
        CardTemplate("绝影", CardType.EQUIPMENT, CardSubType.HORSE, "其他角色与你的距离+1", range = 1),
        CardTemplate("紫骍", CardType.EQUIPMENT, CardSubType.HORSE, "其他角色与你的距离+1", range = 1)
    )
    
    private val allCards = basicCards + trickCards + equipmentCards
    
    fun createRandomCard(): Card {
        val template = allCards.random()
        return createFromTemplate(template)
    }
    
    fun createCard(name: String): Card? {
        val template = allCards.find { it.name == name } ?: return null
        return createFromTemplate(template)
    }
    
    fun createBasicCard(): Card {
        val template = basicCards.random()
        return createFromTemplate(template)
    }
    
    fun createTrickCard(): Card {
        val template = trickCards.random()
        return createFromTemplate(template)
    }
    
    fun createEquipmentCard(): Card {
        val template = equipmentCards.random()
        return createFromTemplate(template)
    }
    
    private fun createFromTemplate(template: CardTemplate): Card {
        return Card(
            id = UUID.randomUUID().toString(),
            name = template.name,
            type = template.type,
            effect = template.effect,
            damage = template.damage,
            healing = template.healing,
            suit = Suit.values().random(),
            number = (1..13).random(),
            subType = template.subType,
            targetType = template.targetType,
            range = template.range
        )
    }
    
    /**
     * 创建完整的108张标准牌堆
     */
    fun createStandardDeck(): List<Card> {
        val deck = mutableListOf<Card>()
        
        // 基本牌分布
        repeat(30) { deck.add(createFromTemplate(basicCards.find { it.name == "杀" }!!)) }
        repeat(24) { deck.add(createFromTemplate(basicCards.find { it.name == "闪" }!!)) }
        repeat(8) { deck.add(createFromTemplate(basicCards.find { it.name == "桃" }!!)) }
        
        // 锦囊牌
        repeat(3) { deck.add(createFromTemplate(trickCards.find { it.name == "决斗" }!!)) }
        repeat(3) { deck.add(createFromTemplate(trickCards.find { it.name == "火攻" }!!)) }
        repeat(4) { deck.add(createFromTemplate(trickCards.find { it.name == "无中生有" }!!)) }
        repeat(3) { deck.add(createFromTemplate(trickCards.find { it.name == "无懈可击" }!!)) }
        repeat(3) { deck.add(createFromTemplate(trickCards.find { it.name == "铁索连环" }!!)) }
        repeat(6) { deck.add(createFromTemplate(trickCards.find { it.name == "过河拆桥" }!!)) }
        repeat(2) { deck.add(createFromTemplate(trickCards.find { it.name == "五谷丰登" }!!)) }
        repeat(1) { deck.add(createFromTemplate(trickCards.find { it.name == "桃园结义" }!!)) }
        repeat(2) { deck.add(createFromTemplate(trickCards.find { it.name == "借刀杀人" }!!)) }
        repeat(5) { deck.add(createFromTemplate(trickCards.find { it.name == "顺手牵羊" }!!)) }
        repeat(3) { deck.add(createFromTemplate(trickCards.find { it.name == "南蛮入侵" }!!)) }
        repeat(1) { deck.add(createFromTemplate(trickCards.find { it.name == "万箭齐发" }!!)) }
        repeat(3) { deck.add(createFromTemplate(trickCards.find { it.name == "乐不思蜀" }!!)) }
        repeat(2) { deck.add(createFromTemplate(trickCards.find { it.name == "闪电" }!!)) }
        
        // 装备牌 - 简化版
        equipmentCards.take(6).forEach { template ->
            deck.add(createFromTemplate(template))
        }
        
        return deck.shuffled()
    }
    
    fun getAllCardNames(): List<String> {
        return allCards.map { it.name }
    }
}

private data class CardTemplate(
    val name: String,
    val type: CardType,
    val subType: CardSubType,
    val effect: String,
    val damage: Int = 0,
    val healing: Int = 0,
    val targetType: TargetType = TargetType.NONE,
    val range: Int = 1
)

/**
 * 卡牌执行状态枚举
 */
@Serializable
enum class CardExecutionPhase {
    TARGETING,           // 目标选择阶段
    NULLIFICATION,       // 无懈可击响应阶段
    RESOLUTION,          // 卡牌结算阶段
    SPECIAL_EXECUTION,   // 特殊执行阶段（如五谷丰登选牌）
    COMPLETED            // 执行完成
}

/**
 * 卡牌执行上下文
 * 包含一张卡牌从出牌到结算完成的完整信息
 */
@Serializable
data class CardExecutionContext(
    val id: String,                                    // 执行ID
    val card: Card,                                    // 出的卡牌
    val caster: Player,                               // 出牌者
    val originalTargets: List<Player>,                // 原始目标列表
    val finalTargets: List<Player>,                   // 最终目标列表（可能被无懈可击改变）
    var phase: CardExecutionPhase = CardExecutionPhase.TARGETING,
    val responses: MutableList<CardResponse> = mutableListOf(),  // 响应记录
    var isBlocked: Boolean = false,                   // 是否被无懈可击阻挡
    var result: CardExecutionResult? = null,          // 执行结果
    var specialData: Map<String, Any> = emptyMap()    // 特殊数据（如五谷丰登的可选卡牌）
) {
    val isCompleted: Boolean
        get() = phase == CardExecutionPhase.COMPLETED

    val needsNullificationResponse: Boolean
        get() = phase == CardExecutionPhase.NULLIFICATION && !isBlocked
}

/**
 * 卡牌响应
 */
@Serializable
data class CardResponse(
    val playerId: String,
    val playerName: String,
    val responseCard: Card?,
    val responseType: CardResponseType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 响应类型
 */
@Serializable
enum class CardResponseType {
    NULLIFICATION,       // 无懈可击
    DODGE,              // 闪避
    COUNTER_ATTACK,     // 反击（如决斗中的杀）
    SPECIAL_SELECTION   // 特殊选择（如五谷丰登选牌）
}

/**
 * 卡牌执行结果
 */
@Serializable
data class CardExecutionResult(
    val success: Boolean,
    val message: String,
    val damage: Int = 0,
    val healing: Int = 0,
    val cardsDrawn: Int = 0,
    val effectsApplied: List<String> = emptyList()
)

/**
 * 响应请求
 */
@Serializable
data class ResponseRequest(
    val targetPlayerId: String,
    val executionId: String,
    val responseType: CardResponseType,
    val originalCard: Card,
    val casterName: String,
    val timeoutMs: Long = 15000,
    val availableOptions: List<String> = emptyList()  // 可用选项（如五谷丰登的卡牌列表）
)