package moe.gensoukyo.tbc.shared.card

import kotlinx.serialization.Serializable
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.Player

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
 * 特殊数据容器 - 类型安全的序列化容器
 */
@Serializable
data class SpecialExecutionData(
    // 五谷丰登相关数据
    val abundantHarvestCards: List<Card> = emptyList(),
    val abundantHarvestPlayerOrder: List<String> = emptyList(),
    val abundantHarvestCurrentIndex: Int = 0,
    
    // 决斗相关数据
    val duelKillCount: Int = 0,
    val duelInitiator: String = "",
    val duelTarget: String = "",
    
    // 其他特殊卡牌数据可以继续添加...
    val customStringData: Map<String, String> = emptyMap(),
    val customIntData: Map<String, Int> = emptyMap(),
    val customBooleanData: Map<String, Boolean> = emptyMap()
)

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
    var specialData: SpecialExecutionData = SpecialExecutionData()  // 特殊数据
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
    val availableOptions: List<String> = emptyList()  // 可用选项ID列表
)