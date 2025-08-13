package moe.gensoukyo.tbc.shared.utils

import moe.gensoukyo.tbc.shared.messages.ServerMessage

/**
 * 服务端消息处理器接口
 */
interface ServerMessageHandler {
    suspend fun handleMessage(message: ServerMessage): Result<Unit>
}

/**
 * 抽象的消息处理器基类
 * 提供默认的错误处理和日志记录
 */
abstract class AbstractMessageHandler : ServerMessageHandler {
    
    override suspend fun handleMessage(message: ServerMessage): Result<Unit> {
        return runCatching {
            logDebug("Handling message: ${message::class.simpleName}")
            
            when (message) {
                is ServerMessage.RoomCreated -> handleRoomCreated(message)
                is ServerMessage.PlayerJoined -> handlePlayerJoined(message)
                is ServerMessage.SpectatorJoined -> handleSpectatorJoined(message)
                is ServerMessage.GameStateUpdate -> handleGameStateUpdate(message)
                is ServerMessage.CardDrawn -> handleCardDrawn(message)
                is ServerMessage.CardsDrawn -> handleCardsDrawn(message)
                is ServerMessage.HealthUpdated -> handleHealthUpdated(message)
                is ServerMessage.RoomList -> handleRoomList(message)
                is ServerMessage.TurnStarted -> handleTurnStarted(message)
                is ServerMessage.GameStarted -> handleGameStarted(message)
                is ServerMessage.InitialCardsDealt -> handleInitialCardsDealt(message)
                is ServerMessage.PlayerOrderChanged -> handlePlayerOrderChanged(message)
                is ServerMessage.Error -> handleError(message)
                is ServerMessage.CardPlayed -> handleCardPlayed(message)
                is ServerMessage.CardResponseRequired -> handleCardResponseRequired(message)
                is ServerMessage.CardResponseReceived -> handleCardResponseReceived(message)
                is ServerMessage.CardResolved -> handleCardResolved(message)
                is ServerMessage.AbundantHarvestStarted -> handleAbundantHarvestStarted(message)
                is ServerMessage.AbundantHarvestSelection -> handleAbundantHarvestSelection(message)
                is ServerMessage.AbundantHarvestCardSelected -> handleAbundantHarvestCardSelected(message)
                is ServerMessage.AbundantHarvestCompleted -> handleAbundantHarvestCompleted(message)
                
                // 新系统消息
                is ServerMessage.CardExecutionStarted -> handleCardExecutionStarted(message)
                is ServerMessage.ResponseRequired -> handleResponseRequired(message)
                is ServerMessage.ResponseReceived -> handleResponseReceived(message)
                is ServerMessage.CardExecutionCompleted -> handleCardExecutionCompleted(message)
                is ServerMessage.SpecialExecutionStarted -> handleSpecialExecutionStarted(message)
                is ServerMessage.NullificationPhaseStarted -> handleNullificationPhaseStarted(message)
            }
            
        }.onFailure { error ->
            logError("Failed to handle message ${message::class.simpleName}", error)
        }
    }
    
    // 默认实现，子类可以覆盖需要的方法
    protected open suspend fun handleRoomCreated(message: ServerMessage.RoomCreated) {}
    protected open suspend fun handlePlayerJoined(message: ServerMessage.PlayerJoined) {}
    protected open suspend fun handleSpectatorJoined(message: ServerMessage.SpectatorJoined) {}
    protected open suspend fun handleGameStateUpdate(message: ServerMessage.GameStateUpdate) {}
    protected open suspend fun handleCardDrawn(message: ServerMessage.CardDrawn) {}
    protected open suspend fun handleCardsDrawn(message: ServerMessage.CardsDrawn) {}
    protected open suspend fun handleHealthUpdated(message: ServerMessage.HealthUpdated) {}
    protected open suspend fun handleRoomList(message: ServerMessage.RoomList) {}
    protected open suspend fun handleTurnStarted(message: ServerMessage.TurnStarted) {}
    protected open suspend fun handleGameStarted(message: ServerMessage.GameStarted) {}
    protected open suspend fun handleInitialCardsDealt(message: ServerMessage.InitialCardsDealt) {}
    protected open suspend fun handlePlayerOrderChanged(message: ServerMessage.PlayerOrderChanged) {}
    protected open suspend fun handleError(message: ServerMessage.Error) {
        logError("Server error: ${message.message}")
    }
    protected open suspend fun handleCardPlayed(message: ServerMessage.CardPlayed) {}
    protected open suspend fun handleCardResponseRequired(message: ServerMessage.CardResponseRequired) {}
    protected open suspend fun handleCardResponseReceived(message: ServerMessage.CardResponseReceived) {}
    protected open suspend fun handleCardResolved(message: ServerMessage.CardResolved) {}
    protected open suspend fun handleAbundantHarvestStarted(message: ServerMessage.AbundantHarvestStarted) {}
    protected open suspend fun handleAbundantHarvestSelection(message: ServerMessage.AbundantHarvestSelection) {}
    protected open suspend fun handleAbundantHarvestCardSelected(message: ServerMessage.AbundantHarvestCardSelected) {}
    protected open suspend fun handleAbundantHarvestCompleted(message: ServerMessage.AbundantHarvestCompleted) {}
    
    // 新系统消息处理
    protected open suspend fun handleCardExecutionStarted(message: ServerMessage.CardExecutionStarted) {}
    protected open suspend fun handleResponseRequired(message: ServerMessage.ResponseRequired) {}
    protected open suspend fun handleResponseReceived(message: ServerMessage.ResponseReceived) {}
    protected open suspend fun handleCardExecutionCompleted(message: ServerMessage.CardExecutionCompleted) {}
    protected open suspend fun handleSpecialExecutionStarted(message: ServerMessage.SpecialExecutionStarted) {}
    protected open suspend fun handleNullificationPhaseStarted(message: ServerMessage.NullificationPhaseStarted) {}
}

/**
 * 消息处理器注册表
 */
class MessageHandlerRegistry {
    private val handlers = mutableListOf<ServerMessageHandler>()
    
    fun registerHandler(handler: ServerMessageHandler) {
        handlers.add(handler)
    }
    
    fun unregisterHandler(handler: ServerMessageHandler) {
        handlers.remove(handler)
    }
    
    suspend fun handleMessage(message: ServerMessage): List<Result<Unit>> {
        return handlers.map { handler ->
            handler.handleMessage(message)
        }
    }
    
    fun getHandlerCount(): Int = handlers.size
}