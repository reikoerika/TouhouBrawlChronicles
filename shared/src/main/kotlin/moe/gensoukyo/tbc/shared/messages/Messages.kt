package moe.gensoukyo.tbc.shared.messages

import kotlinx.serialization.Serializable
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.CardResolutionResult
import moe.gensoukyo.tbc.shared.model.CardResponse
import moe.gensoukyo.tbc.shared.model.GameRoom
import moe.gensoukyo.tbc.shared.model.Player
import moe.gensoukyo.tbc.shared.model.PlayedCard
import moe.gensoukyo.tbc.shared.model.ResponseType
import moe.gensoukyo.tbc.shared.model.Spectator

@Serializable
sealed class ClientMessage {
    @Serializable
    data class JoinRoom(val roomId: String, val playerName: String, val spectateOnly: Boolean = false) : ClientMessage()
    
    @Serializable
    data class CreateRoom(val roomName: String, val playerName: String) : ClientMessage()
    
    @Serializable
    data class DrawCard(val playerId: String) : ClientMessage()
    
    @Serializable
    object GetRoomList : ClientMessage()
    
    @Serializable
    data class EndTurn(val playerId: String) : ClientMessage()
    
    @Serializable
    data class StartGame(val roomId: String) : ClientMessage()
    
    @Serializable
    data class AdjustPlayerOrder(val roomId: String, val newOrder: List<String>) : ClientMessage()
    
    // 统一的出牌消息 - 支持多目标
    @Serializable
    data class PlayCard(val playerId: String, val cardId: String, val targetIds: List<String> = emptyList()) : ClientMessage()
    
    // 统一的响应消息  
    @Serializable
    data class RespondToCard(val playerId: String, val responseCardId: String? = null, val accept: Boolean = false) : ClientMessage()
    
    @Serializable
    data class SelectAbundantHarvestCard(val playerId: String, val selectedCardId: String) : ClientMessage()
    
    @Serializable
    data class HealthChange(val playerId: String, val amount: Int) : ClientMessage()
}

@Serializable
sealed class ServerMessage {
    @Serializable
    data class RoomCreated(val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class PlayerJoined(val player: Player, val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class SpectatorJoined(val spectator: Spectator, val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class GameStateUpdate(val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class CardDrawn(val playerId: String, val card: Card) : ServerMessage()
    
    @Serializable
    data class CardsDrawn(val playerId: String, val cards: List<Card>) : ServerMessage()
    
    @Serializable
    data class HealthUpdated(val playerId: String, val newHealth: Int) : ServerMessage()
    
    @Serializable
    data class RoomList(val rooms: List<GameRoom>) : ServerMessage()
    
    @Serializable
    data class TurnStarted(val playerId: String, val phase: moe.gensoukyo.tbc.shared.model.GamePhase) : ServerMessage()
    
    @Serializable
    data class GameStarted(val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class InitialCardsDealt(val playerId: String, val cards: List<Card>) : ServerMessage()
    
    @Serializable
    data class PlayerOrderChanged(val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class Error(val message: String) : ServerMessage()
    
    @Serializable
    data class CardPlayed(val playedCard: PlayedCard, val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class CardResponseRequired(
        val targetPlayerId: String,
        val originalCard: Card,
        val originalPlayer: String,
        val responseType: ResponseType,
        val timeoutMs: Long = 15000
    ) : ServerMessage()
    
    @Serializable
    data class CardResponseReceived(
        val playerId: String, 
        val responseCard: Card?, 
        val accepted: Boolean,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class CardResolved(
        val originalCard: Card,
        val responses: List<CardResponse>,
        val result: CardResolutionResult,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class AbundantHarvestStarted(
        val availableCards: List<Card>,
        val currentPlayerIndex: Int,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class AbundantHarvestSelection(
        val playerId: String,
        val playerName: String,
        val availableCards: List<Card>,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class AbundantHarvestCardSelected(
        val playerId: String,
        val playerName: String,
        val selectedCard: Card,
        val remainingCards: List<Card>,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class AbundantHarvestCompleted(
        val selections: Map<String, Card>,
        val room: GameRoom
    ) : ServerMessage()
    
    // 新的卡牌执行系统消息
    @Serializable
    data class CardExecutionStarted(
        val executionId: String,
        val casterName: String,
        val cardName: String,
        val targetNames: List<String>,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class ResponseRequired(
        val executionId: String,
        val targetPlayerId: String,
        val responseType: String,
        val originalCard: Card,
        val casterName: String,
        val timeoutMs: Long = 15000,
        val availableOptions: List<ResponseOption> = emptyList()
    ) : ServerMessage()
    
    @Serializable
    data class ResponseReceived(
        val executionId: String,
        val playerId: String,
        val playerName: String,
        val responseCard: Card?,
        val accepted: Boolean,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class CardExecutionCompleted(
        val executionId: String,
        val success: Boolean,
        val blocked: Boolean,
        val message: String,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class SpecialExecutionStarted(
        val executionId: String,
        val cardName: String,
        val currentPlayerId: String,
        val currentPlayerName: String,
        val availableOptions: List<ResponseOption>,
        val room: GameRoom
    ) : ServerMessage()
    
    @Serializable
    data class NullificationPhaseStarted(
        val executionId: String,
        val cardName: String,
        val casterName: String,
        val targetPlayerIds: List<String>,
        val room: GameRoom
    ) : ServerMessage()
}

@Serializable
data class ResponseOption(
    val id: String,
    val name: String,
    val description: String
)