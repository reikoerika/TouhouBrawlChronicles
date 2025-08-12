package moe.gensoukyo.tbc.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val name: String,
    var health: Int = 100,
    val maxHealth: Int = 100,
    val cards: MutableList<Card> = mutableListOf()
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
    var currentPlayer: String? = null,
    var gameState: GameState = GameState.WAITING,
    val maxPlayers: Int = 8
)

@Serializable
enum class GameState {
    WAITING,    // 等待玩家
    PLAYING,    // 游戏中
    FINISHED    // 游戏结束
}