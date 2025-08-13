package moe.gensoukyo.tbc.shared.messages

import kotlinx.serialization.Serializable

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