package moe.gensoukyo.tbc.shared.messages

import kotlinx.serialization.Serializable

/**
 * 卡牌执行引擎内部使用的消息类型
 * 这些消息用于CardExecutionHandler内部处理，不直接用于客户端通信
 */

// ======================== 内部处理消息 ========================

@Serializable
data class PlayCardMessage(
    val playerId: String,
    val cardId: String,
    val targetIds: List<String> = emptyList()
) {
    companion object {
        /**
         * 从统一的PlayCard客户端消息创建内部处理消息
         */
        fun fromClientMessage(playCard: ClientMessage.PlayCard): PlayCardMessage {
            return PlayCardMessage(
                playerId = playCard.playerId,
                cardId = playCard.cardId,
                targetIds = playCard.targetIds
            )
        }
    }
}

@Serializable
data class RespondToCardMessage(
    val playerId: String,
    val responseCardId: String? = null,
    val accept: Boolean = false
) {
    companion object {
        /**
         * 从统一的RespondToCard客户端消息创建内部处理消息
         */
        fun fromClientMessage(respondToCard: ClientMessage.RespondToCard): RespondToCardMessage {
            return RespondToCardMessage(
                playerId = respondToCard.playerId,
                responseCardId = respondToCard.responseCardId,
                accept = respondToCard.accept
            )
        }
    }
}