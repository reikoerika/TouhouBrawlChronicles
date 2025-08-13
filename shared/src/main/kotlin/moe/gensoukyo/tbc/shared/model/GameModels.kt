package moe.gensoukyo.tbc.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val name: String,
    var health: Int = 100,
    val maxHealth: Int = 100,
    val cards: MutableList<Card> = mutableListOf(),
    val playedCards: MutableList<PlayedCard> = mutableListOf()  // 已出的牌记录
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
    val healing: Int = 0
)

@Serializable
data class PlayedCard(
    val card: Card,
    val playerId: String,
    val playerName: String,
    val turnNumber: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class CardType {
    ATTACK,    // 攻击牌
    DEFENSE,   // 防御牌
    SKILL,     // 技能牌
    RECOVERY   // 恢复牌
}

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
    val currentTurnPlayedCards: MutableList<PlayedCard> = mutableListOf()  // 当前回合出牌区
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