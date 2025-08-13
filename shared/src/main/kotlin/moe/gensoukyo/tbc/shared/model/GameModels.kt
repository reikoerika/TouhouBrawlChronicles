package moe.gensoukyo.tbc.shared.model

import kotlinx.serialization.Serializable
import moe.gensoukyo.tbc.shared.card.CardExecutionContext

@Serializable
data class Player(
    val id: String,
    val name: String,
    var health: Int = 100,
    var maxHealth: Int = 100,  // 改为var以支持动态调整
    val cards: MutableList<Card> = mutableListOf(),
    val playedCards: MutableList<PlayedCard> = mutableListOf(),  // 已出的牌记录
    var general: General? = null,  // 武将
    var identity: Identity? = null,  // 身份
    val equipment: Equipment = Equipment(),  // 装备
    var isChained: Boolean = false,  // 铁索连环状态
    var delayedCards: MutableList<DelayedCard> = mutableListOf()  // 延时锦囊
)

@Serializable
data class Spectator(
    val id: String,
    val name: String,
    val joinTime: Long = System.currentTimeMillis() // 观战加入时间
)

@Serializable
data class Card(
    val id: String,
    val name: String,
    val type: CardType,
    val effect: String,
    val damage: Int = 0,
    val healing: Int = 0,
    val suit: Suit = Suit.SPADE,  // 花色
    val number: Int = 1,  // 点数
    val subType: CardSubType = CardSubType.BASIC,  // 子类型
    val targetType: TargetType = TargetType.SINGLE,  // 目标类型
    val range: Int = 1  // 攻击距离（装备用）
)

@Serializable
data class PlayedCard(
    val card: Card,
    val playerId: String,
    val playerName: String,
    val turnNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val targetIds: List<String> = emptyList()  // 目标玩家ID列表
)

@Serializable
enum class CardType {
    BASIC,      // 基本牌（杀、闪、桃）
    TRICK,      // 锦囊牌
    EQUIPMENT   // 装备牌
}

@Serializable 
enum class CardSubType {
    BASIC,          // 基本牌
    INSTANT_TRICK,  // 普通锦囊
    DELAYED_TRICK,  // 延时锦囊
    WEAPON,         // 武器
    ARMOR,          // 防具
    HORSE           // 马
}

@Serializable
enum class Suit {
    SPADE,    // 黑桃
    HEART,    // 红心
    CLUB,     // 梅花
    DIAMOND   // 方块
}

@Serializable
enum class TargetType {
    NONE,        // 无目标
    SINGLE,      // 单个目标
    MULTIPLE,    // 多个目标
    ALL_OTHERS,  // 所有其他人
    ALL_PLAYERS  // 所有人
}

@Serializable
enum class Identity {
    LORD,        // 主公
    LOYALIST,    // 忠臣
    REBEL,       // 反贼
    SPY          // 内奸
}

@Serializable
data class General(
    val id: String,
    val name: String,
    val kingdom: String,        // 势力
    val healthBonus: Int = 0,   // 血量加成
    val skills: List<Skill> = emptyList()  // 技能
)

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val type: SkillType = SkillType.ACTIVE
)

@Serializable
enum class SkillType {
    ACTIVE,    // 主动技能
    PASSIVE,   // 被动技能
    TRIGGER    // 触发技能
}

@Serializable
data class Equipment(
    var weapon: Card? = null,    // 武器
    var armor: Card? = null,     // 防具
    var defensiveHorse: Card? = null,  // 防御马
    var offensiveHorse: Card? = null   // 攻击马
) {
    val attackRange: Int
        get() = weapon?.range ?: 1
        
    val hasDefensiveHorse: Boolean
        get() = defensiveHorse != null
        
    val hasOffensiveHorse: Boolean  
        get() = offensiveHorse != null
}

@Serializable
data class DelayedCard(
    val card: Card,
    val targetPlayerId: String,
    val turnsRemaining: Int = 1
)

@Serializable
data class GameRoom(
    val id: String,
    val name: String,
    val players: MutableList<Player> = mutableListOf(),
    val spectators: MutableList<Spectator> = mutableListOf(), // 观战者列表
    var currentPlayerIndex: Int = 0,
    var gameState: GameState = GameState.WAITING,
    val maxPlayers: Int = 8,
    var turnCount: Int = 0,
    var gamePhase: GamePhase = GamePhase.DRAW,
    val currentTurnPlayedCards: MutableList<PlayedCard> = mutableListOf(),  // 当前回合出牌区
    val deck: MutableList<Card> = mutableListOf(),  // 牌堆
    val discardPile: MutableList<Card> = mutableListOf(),  // 弃牌堆
    var pendingResponse: PendingResponse? = null,  // 待处理的卡牌响应（旧系统）
    var lastAbundantHarvestSelections: Map<String, Card> = emptyMap(),  // 上次五谷丰登的选择记录
    @kotlinx.serialization.Transient
    var activeCardExecution: CardExecutionContext? = null  // 当前卡牌执行上下文（新系统）
) {
    val currentPlayer: Player?
        get() = if (players.isNotEmpty() && currentPlayerIndex < players.size) 
            players[currentPlayerIndex] else null
}

@Serializable
enum class GameState {
    WAITING,    // 等待玩家
    PLAYING,    // 游戏中
    FINISHED    // 游戏结束
}

@Serializable
enum class GamePhase {
    DRAW,       // 摸牌阶段
    PLAY,       // 出牌阶段
    DISCARD     // 弃牌阶段
}

@Serializable
enum class ResponseType {
    DODGE,          // 闪避（对杀的响应）
    NULLIFICATION,  // 无懈可击（对锦囊的响应）
    DUEL_KILL,      // 决斗中出杀（决斗特有）
    ABUNDANT_HARVEST, // 五谷丰登选择卡牌
    OPTIONAL        // 可选响应
}

@Serializable
data class CardResponse(
    val playerId: String,
    val playerName: String,
    val responseCard: Card?,
    val accepted: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class CardResolutionResult(
    val success: Boolean,
    val blocked: Boolean = false,
    val damage: Int = 0,
    val healing: Int = 0,
    val message: String = ""
)

@Serializable
data class PendingResponse(
    var targetPlayerId: String,
    val originalCard: Card,
    val originalPlayerId: String,
    val responseType: ResponseType,
    val responses: MutableList<CardResponse> = mutableListOf(),
    val timeoutMs: Long = 15000,
    val startTime: Long = System.currentTimeMillis(),
    // 决斗专用字段
    var isDuel: Boolean = false,
    var duelCurrentPlayer: String? = null,  // 当前需要出杀的玩家
    var duelKillCount: Int = 0,  // 已经出的杀的数量
    var duelInitiator: String? = null,  // 决斗发起者
    var duelTarget: String? = null,  // 决斗目标
    var duelFinished: Boolean = false,  // 决斗是否结束
    // 五谷丰登专用字段
    var isAbundantHarvest: Boolean = false,  // 是否是五谷丰登
    var availableCards: MutableList<Card> = mutableListOf(),  // 可选择的卡牌
    var currentSelectionPlayerIndex: Int = 0,  // 当前选择玩家的索引
    var selectedCards: MutableMap<String, String> = mutableMapOf()  // 玩家ID到卡牌ID的映射
)