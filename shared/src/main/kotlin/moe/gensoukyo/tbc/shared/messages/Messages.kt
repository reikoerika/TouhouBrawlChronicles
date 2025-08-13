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
    data class UseCard(val playerId: String, val cardId: String, val targetId: String? = null) : ClientMessage()
    
    @Serializable
    data class HealthChange(val playerId: String, val amount: Int) : ClientMessage()
    
    @Serializable
    object GetRoomList : ClientMessage()
    
    @Serializable
    data class EndTurn(val playerId: String) : ClientMessage()
    
    @Serializable
    data class StartGame(val roomId: String) : ClientMessage()
    
    @Serializable
    data class AdjustPlayerOrder(val roomId: String, val newOrder: List<String>) : ClientMessage()
    
    @Serializable
    data class PlayCard(val playerId: String, val cardId: String, val targetIds: List<String> = emptyList()) : ClientMessage()
    
    @Serializable
    data class RespondToCard(val playerId: String, val responseCardId: String?, val accept: Boolean = false) : ClientMessage()
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
}