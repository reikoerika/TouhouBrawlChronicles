package moe.gensoukyo.tbc.shared.messages

import kotlinx.serialization.Serializable
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.GameRoom
import moe.gensoukyo.tbc.shared.model.Player

@Serializable
sealed class ClientMessage {
    @Serializable
    data class JoinRoom(val roomId: String, val playerName: String) : ClientMessage()
    
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
}

@Serializable
sealed class ServerMessage {
    @Serializable
    data class RoomCreated(val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class PlayerJoined(val player: Player, val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class GameStateUpdate(val room: GameRoom) : ServerMessage()
    
    @Serializable
    data class CardDrawn(val playerId: String, val card: Card) : ServerMessage()
    
    @Serializable
    data class HealthUpdated(val playerId: String, val newHealth: Int) : ServerMessage()
    
    @Serializable
    data class RoomList(val rooms: List<GameRoom>) : ServerMessage()
    
    @Serializable
    data class Error(val message: String) : ServerMessage()
}