package moe.gensoukyo.tbc.shared.messages

import kotlinx.serialization.Serializable
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.GameRoom

/**
 * 新的卡牌执行相关消息
 */

// ======================== 客户端消息 ========================

@Serializable
data class PlayCardMessage(
    val playerId: String,
    val cardId: String,
    val targetIds: List<String> = emptyList()
)

@Serializable
data class RespondToCardMessage(
    val playerId: String,
    val responseCardId: String? = null,
    val accept: Boolean = false
)

// ======================== 服务端消息 ========================

@Serializable
data class CardExecutionStartedMessage(
    val executionId: String,
    val casterName: String,
    val cardName: String,
    val targetNames: List<String>,
    val room: GameRoom
)

@Serializable
data class ResponseRequiredMessage(
    val executionId: String,
    val targetPlayerId: String,
    val responseType: String,  // NULLIFICATION, SPECIAL_SELECTION, etc.
    val originalCard: Card,
    val casterName: String,
    val timeoutMs: Long = 15000,
    val availableOptions: List<ResponseOption> = emptyList()
)

@Serializable
data class ResponseOption(
    val id: String,
    val name: String,
    val description: String
)

@Serializable
data class ResponseReceivedMessage(
    val executionId: String,
    val playerId: String,
    val playerName: String,
    val responseCard: Card?,
    val accepted: Boolean,
    val room: GameRoom
)

@Serializable
data class CardExecutionCompletedMessage(
    val executionId: String,
    val success: Boolean,
    val blocked: Boolean,
    val message: String,
    val room: GameRoom
)

@Serializable
data class SpecialExecutionStartedMessage(
    val executionId: String,
    val cardName: String,
    val currentPlayerId: String,
    val currentPlayerName: String,
    val availableOptions: List<ResponseOption>,
    val room: GameRoom
)