package moe.gensoukyo.tbc.shared.exceptions

import kotlinx.serialization.Serializable

/**
 * 卡牌执行异常基类
 */
@Serializable
sealed class CardExecutionException(
    override val message: String,
    val errorCode: String,
    val errorData: Map<String, String> = emptyMap()
) : Exception(message) {
    
    /**
     * 房间相关错误
     */
    @Serializable
    data class RoomNotFoundException(
        val roomId: String
    ) : CardExecutionException(
        message = "Room not found: $roomId",
        errorCode = "ROOM_NOT_FOUND",
        errorData = mapOf("roomId" to roomId)
    )
    
    /**
     * 玩家相关错误
     */
    @Serializable
    data class PlayerNotFoundException(
        val playerId: String,
        val roomId: String
    ) : CardExecutionException(
        message = "Player $playerId not found in room $roomId",
        errorCode = "PLAYER_NOT_FOUND",
        errorData = mapOf("playerId" to playerId, "roomId" to roomId)
    )
    
    /**
     * 卡牌相关错误
     */
    @Serializable
    data class CardNotFoundException(
        val cardId: String,
        val playerId: String
    ) : CardExecutionException(
        message = "Card $cardId not found in player $playerId's hand",
        errorCode = "CARD_NOT_FOUND",
        errorData = mapOf("cardId" to cardId, "playerId" to playerId)
    )
    
    /**
     * 游戏状态错误
     */
    @Serializable
    data class InvalidGameStateException(
        val currentState: String,
        val expectedState: String,
        val operation: String
    ) : CardExecutionException(
        message = "Invalid game state for $operation: $currentState (expected: $expectedState)",
        errorCode = "INVALID_GAME_STATE",
        errorData = mapOf(
            "currentState" to currentState,
            "expectedState" to expectedState,
            "operation" to operation
        )
    )
    
    /**
     * 卡牌使用规则违反错误
     */
    @Serializable
    data class InvalidCardPlayException(
        val cardName: String,
        val reason: String,
        val violation: String
    ) : CardExecutionException(
        message = "Cannot play $cardName: $reason",
        errorCode = "INVALID_CARD_PLAY",
        errorData = mapOf(
            "cardName" to cardName,
            "reason" to reason,
            "violation" to violation
        )
    )
    
    /**
     * 目标选择错误
     */
    @Serializable
    data class InvalidTargetException(
        val cardName: String,
        val targetType: String,
        val providedTargets: Int,
        val expectedTargets: String
    ) : CardExecutionException(
        message = "Invalid targets for $cardName: provided $providedTargets targets, expected $expectedTargets",
        errorCode = "INVALID_TARGET",
        errorData = mapOf(
            "cardName" to cardName,
            "targetType" to targetType,
            "providedTargets" to providedTargets.toString(),
            "expectedTargets" to expectedTargets
        )
    )
    
    /**
     * 执行引擎内部错误
     */
    @Serializable
    data class ExecutionEngineException(
        val executionId: String,
        val phase: String,
        val details: String
    ) : CardExecutionException(
        message = "Card execution failed in phase $phase: $details",
        errorCode = "EXECUTION_ENGINE_ERROR",
        errorData = mapOf(
            "executionId" to executionId,
            "phase" to phase,
            "details" to details
        )
    )
}

/**
 * 错误恢复策略
 */
enum class ErrorRecoveryStrategy {
    NONE,           // 不恢复，直接返回错误
    RETRY,          // 重试操作
    FALLBACK,       // 使用备用逻辑
    CLEANUP         // 清理状态并继续
}

/**
 * 错误处理结果
 */
@Serializable
data class ErrorHandlingResult(
    val success: Boolean,
    val originalError: String,
    val recoveryStrategy: ErrorRecoveryStrategy,
    val recoveryMessage: String = "",
    val shouldRetry: Boolean = false
)