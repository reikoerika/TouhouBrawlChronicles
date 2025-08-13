package moe.gensoukyo.tbc.client.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.gensoukyo.tbc.shared.messages.ClientMessage
import moe.gensoukyo.tbc.shared.messages.ServerMessage
import moe.gensoukyo.tbc.shared.model.GameRoom
import moe.gensoukyo.tbc.shared.model.Player

class GameClient {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    
    private var session: WebSocketSession? = null
    private var currentPlayer: Player? = null
    private var currentRoom: GameRoom? = null
    
    suspend fun start() {
        try {
            client.webSocket(
                host = "localhost",
                port = 8080,
                path = "/game"
            ) {
                session = this
                
                // 启动消息监听
                val messageJob = launch { listenToMessages() }
                
                // 启动用户界面
                showMainMenu()
                
                messageJob.cancel()
            }
        } catch (e: Exception) {
            println("连接服务器失败: ${e.message}")
        } finally {
            client.close()
        }
    }
    
    private suspend fun listenToMessages() {
        try {
            for (frame in session!!.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    
                    try {
                        val message = Json.decodeFromString<ServerMessage>(text)
                        handleServerMessage(message)
                    } catch (e: Exception) {
                        println("处理服务器消息时发生错误: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("监听消息时发生错误: ${e.message}")
        }
    }
    
    private suspend fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.RoomCreated -> {
                currentRoom = message.room
                currentPlayer = message.room.players.first()
                println("\\n房间创建成功!")
                println("房间ID: ${message.room.id}")
                println("房间名称: ${message.room.name}")
                showGameMenu()
            }
            
            is ServerMessage.PlayerJoined -> {
                currentRoom = message.room
                val joinedPlayer = message.room.players.find { it.name == message.player.name }
                if (joinedPlayer != null) {
                    currentPlayer = joinedPlayer
                }
                println("\\n成功加入房间!")
                showGameMenu()
            }
            
            is ServerMessage.GameStateUpdate -> {
                currentRoom = message.room
                println("\\n游戏状态更新")
                showRoomInfo()
            }
            
            is ServerMessage.CardDrawn -> {
                println("\\n抽到卡牌: ${message.card.name} - ${message.card.effect}")
            }
            
            is ServerMessage.HealthUpdated -> {
                val player = currentRoom?.players?.find { it.id == message.playerId }
                if (player != null) {
                    println("\\n${player.name} 的血量变为: ${message.newHealth}")
                }
            }
            
            is ServerMessage.AbundantHarvestStarted -> {
                println("\\n五谷丰登开始！")
                println("可选择的卡牌：")
                message.availableCards.forEachIndexed { index, card ->
                    println("${index + 1}. ${card.name} - ${card.effect}")
                }
                val alivePlayers = message.room.players.filter { it.health > 0 }
                val currentPlayer = alivePlayers[message.currentPlayerIndex]
                println("当前选择玩家：${currentPlayer.name}")
            }
            
            is ServerMessage.AbundantHarvestSelection -> {
                if (currentPlayer?.id == message.playerId) {
                    println("\\n轮到你选择卡牌了！")
                    println("可选择的卡牌：")
                    message.availableCards.forEachIndexed { index, card ->
                        println("${index + 1}. ${card.name} - ${card.effect}")
                    }
                    selectAbundantHarvestCard(message.availableCards)
                } else {
                    println("\\n等待${message.playerName}选择卡牌...")
                }
            }
            
            is ServerMessage.AbundantHarvestCardSelected -> {
                println("\\n${message.playerName}选择了${message.selectedCard.name}")
            }
            
            is ServerMessage.AbundantHarvestCompleted -> {
                println("\\n五谷丰登完成！所有玩家选择结果：")
                message.selections.forEach { (playerId, card) ->
                    val player = message.room.players.find { it.id == playerId }
                    println("${player?.name}: ${card.name}")
                }
            }

            is ServerMessage.Error -> {
                println("\\n错误: ${message.message}")
            }

            is ServerMessage.CardPlayed -> {
                println("\\n${message.playedCard.playerName}出了${message.playedCard.card.name}")
                currentRoom = message.room
            }
            
            is ServerMessage.CardResolved -> {
                println("\\n卡牌结算完成：${message.result.message}")
                currentRoom = message.room
            }
            
            is ServerMessage.CardResponseReceived -> {
                if (message.responseCard != null) {
                    println("\\n${message.playerId}出了${message.responseCard!!.name}进行响应")
                } else {
                    println("\\n${message.playerId}选择${if (message.accepted) "接受" else "拒绝"}响应")
                }
                currentRoom = message.room
            }
            
            is ServerMessage.CardResponseRequired -> {
                if (currentPlayer?.id == message.targetPlayerId) {
                    println("\\n需要响应${message.originalPlayer}的${message.originalCard.name}")
                    handleCardResponse(message.originalCard)
                }
            }
            
            is ServerMessage.CardsDrawn -> {
                if (currentPlayer?.id == message.playerId) {
                    println("\\n你摸了${message.cards.size}张牌")
                    currentPlayer?.cards?.addAll(message.cards)
                }
            }
            
            is ServerMessage.GameStarted -> {
                println("\\n游戏开始！")
                println("房间信息：${message.room.name}")
                println("玩家列表：")
                message.room.players.forEach { player ->
                    println("- ${player.name} (${player.identity?.displayName}) 体力：${player.health}/${player.maxHealth}")
                }
                currentRoom = message.room
            }
            
            is ServerMessage.InitialCardsDealt -> {
                if (currentPlayer?.id == message.playerId) {
                    println("\\n获得初始手牌${message.cards.size}张")
                    currentPlayer?.cards?.clear()
                    currentPlayer?.cards?.addAll(message.cards)
                }
            }
            
            is ServerMessage.PlayerOrderChanged -> {
                println("\\n玩家顺序已调整")
                currentRoom = message.room
            }
            
            is ServerMessage.RoomList -> {
                println("\\n可用房间：")
                if (message.rooms.isEmpty()) {
                    println("暂无房间")
                } else {
                    message.rooms.forEach { room ->
                        println("- ${room.name} (${room.id}) 玩家：${room.players.size}/${room.maxPlayers}")
                    }
                }
            }
            
            is ServerMessage.SpectatorJoined -> {
                println("\\n观战者${message.spectator.name}加入了房间")
                currentRoom = message.room
            }
            
            is ServerMessage.TurnStarted -> {
                val player = currentRoom?.players?.find { it.id == message.playerId }
                println("\\n${player?.name}的回合开始 - ${message.phase}")
                if (currentPlayer?.id == message.playerId) {
                    showGameMenu()
                }
            }

            is ServerMessage.CardExecutionStarted -> {
                println("\\n${message.casterName}使用了${message.cardName}")
                if (message.targetNames.isNotEmpty()) {
                    println("目标：${message.targetNames.joinToString(", ")}")
                }
                currentRoom = message.room
            }
            
            is ServerMessage.ResponseRequired -> {
                if (currentPlayer?.id == message.targetPlayerId) {
                    println("\\n需要响应${message.casterName}的${message.originalCard.name}")
                    when (message.responseType) {
                        "NULLIFICATION" -> {
                            println("是否使用无懈可击？")
                            handleNullificationResponse()
                        }
                        "SPECIAL_SELECTION" -> {
                            println("请选择一个选项：")
                            message.availableOptions.forEachIndexed { index, option ->
                                println("${index + 1}. ${option.name} - ${option.description}")
                            }
                            handleSpecialSelection(message.availableOptions)
                        }
                    }
                }
            }
            
            is ServerMessage.ResponseReceived -> {
                if (message.responseCard != null) {
                    println("\\n${message.playerName}使用了${message.responseCard?.name?:"null"}响应")
                } else {
                    println("\\n${message.playerName}选择了${if (message.accepted) "接受" else "拒绝"}响应")
                }
                currentRoom = message.room
            }
            
            is ServerMessage.CardExecutionCompleted -> {
                println("\\n卡牌执行${if (message.success) "成功" else "失败"}")
                if (message.blocked) {
                    println("被无懈可击阻挡")
                }
                println(message.message)
                currentRoom = message.room
            }
            
            is ServerMessage.SpecialExecutionStarted -> {
                println("\\n${message.cardName}特殊效果开始")
                println("当前玩家：${message.currentPlayerName}")
                currentRoom = message.room
            }
            
            is ServerMessage.NullificationPhaseStarted -> {
                println("\\n无懈可击阶段开始")
                println("${message.casterName}使用了${message.cardName}")
                println("所有玩家可以使用无懈可击响应")
            }
        }
    }
    
    private suspend fun showMainMenu() {
        while (true) {
            println("\\n=== 东方格斗编年史 ===")
            println("1. 创建房间")
            println("2. 加入房间")
            println("3. 退出")
            print("请选择: ")
            
            when (readlnOrNull()) {
                "1" -> createRoom()
                "2" -> joinRoom()
                "3" -> return
                else -> println("无效选择")
            }
        }
    }
    
    private suspend fun createRoom() {
        print("输入房间名称: ")
        val roomName = readlnOrNull() ?: return
        
        print("输入你的名字: ")
        val playerName = readlnOrNull() ?: return
        
        val message = ClientMessage.CreateRoom(roomName, playerName)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private suspend fun joinRoom() {
        print("输入房间ID: ")
        val roomId = readlnOrNull() ?: return
        
        print("输入你的名字: ")
        val playerName = readlnOrNull() ?: return
        
        val message = ClientMessage.JoinRoom(roomId, playerName)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private suspend fun showGameMenu() {
        while (true) {
            println("\\n=== 游戏菜单 ===")
            showRoomInfo()
            println("\\n1. 抽卡")
            println("2. 使用卡牌")
            println("3. 增加血量")
            println("4. 减少血量")
            println("5. 显示手牌")
            println("6. 返回主菜单")
            print("请选择: ")
            
            when (readlnOrNull()) {
                "1" -> drawCard()
                "2" -> useCard()
                "3" -> changeHealth(10)
                "4" -> changeHealth(-10)
                "5" -> showCards()
                "6" -> {
                    currentRoom = null
                    currentPlayer = null
                    return
                }
                else -> println("无效选择")
            }
        }
    }
    
    private fun showRoomInfo() {
        val room = currentRoom ?: return
        println("\\n房间: ${room.name} (${room.id})")
        println("状态: ${room.gameState}")
        println("玩家列表:")
        room.players.forEach { player ->
            val indicator = if (player.id == currentPlayer?.id) " (你)" else ""
            println("  - ${player.name}: ${player.health}/${player.maxHealth} HP${indicator}")
        }
    }
    
    private suspend fun drawCard() {
        val playerId = currentPlayer?.id ?: return
        val message = ClientMessage.DrawCard(playerId)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private suspend fun useCard() {
        val player = currentPlayer ?: return
        
        if (player.cards.isEmpty()) {
            println("你没有卡牌可用")
            return
        }
        
        println("\\n你的手牌:")
        player.cards.forEachIndexed { index, card ->
            println("${index + 1}. ${card.name} - ${card.effect}")
        }
        
        print("选择要使用的卡牌 (输入序号): ")
        val choice = readlnOrNull()?.toIntOrNull()
        
        if (choice == null || choice < 1 || choice > player.cards.size) {
            println("无效选择")
            return
        }
        
        val card = player.cards[choice - 1]
        
        // 如果是攻击卡，需要选择目标
        var targetId: String? = null
        if (card.type == moe.gensoukyo.tbc.shared.model.CardType.BASIC) {
            val room = currentRoom ?: return
            val otherPlayers = room.players.filter { it.id != player.id }
            
            if (otherPlayers.isNotEmpty()) {
                println("选择攻击目标:")
                otherPlayers.forEachIndexed { index, target ->
                    println("${index + 1}. ${target.name}")
                }
                
                print("选择目标 (输入序号): ")
                val targetChoice = readlnOrNull()?.toIntOrNull()
                
                if (targetChoice != null && targetChoice in 1..otherPlayers.size) {
                    targetId = otherPlayers[targetChoice - 1].id
                }
            }
        }
        
        val message = ClientMessage.PlayCard(player.id, card.id, listOfNotNull(targetId))
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private suspend fun changeHealth(amount: Int) {
        val playerId = currentPlayer?.id ?: return
        val message = ClientMessage.HealthChange(playerId, amount)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private fun showCards() {
        val player = currentPlayer ?: return
        
        if (player.cards.isEmpty()) {
            println("\\n你没有卡牌")
            return
        }
        
        println("\\n你的手牌:")
        player.cards.forEachIndexed { index, card ->
            println("${index + 1}. ${card.name} (${card.type}) - ${card.effect}")
            if (card.damage > 0) println("   伤害: ${card.damage}")
            if (card.healing > 0) println("   回复: ${card.healing}")
        }
    }
    
    private suspend fun selectAbundantHarvestCard(availableCards: List<moe.gensoukyo.tbc.shared.model.Card>) {
        print("选择要获得的卡牌 (输入序号): ")
        val choice = readlnOrNull()?.toIntOrNull()
        
        if (choice == null || choice < 1 || choice > availableCards.size) {
            println("无效选择")
            return
        }
        
        val selectedCard = availableCards[choice - 1]
        val playerId = currentPlayer?.id ?: return
        val message = ClientMessage.SelectAbundantHarvestCard(playerId, selectedCard.id)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private suspend fun handleCardResponse(originalCard: moe.gensoukyo.tbc.shared.model.Card) {
        val player = currentPlayer ?: return
        
        println("需要响应${originalCard.name}，你的手牌：")
        player.cards.forEachIndexed { index, card ->
            println("${index + 1}. ${card.name}")
        }
        println("0. 不响应")
        
        print("选择响应卡牌 (输入序号): ")
        val choice = readlnOrNull()?.toIntOrNull()
        
        if (choice == null || choice < 0 || choice > player.cards.size) {
            println("无效选择")
            return
        }
        
        val responseCardId = if (choice > 0) player.cards[choice - 1].id else null
        val message = ClientMessage.RespondToCard(player.id, responseCardId, choice > 0)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private suspend fun playCard() {
        val player = currentPlayer ?: return
        
        if (player.cards.isEmpty()) {
            println("你没有卡牌可用")
            return
        }
        
        println("\\n你的手牌:")
        player.cards.forEachIndexed { index, card ->
            println("${index + 1}. ${card.name} - ${card.effect}")
        }
        
        print("选择要使用的卡牌 (输入序号): ")
        val choice = readlnOrNull()?.toIntOrNull()
        
        if (choice == null || choice < 1 || choice > player.cards.size) {
            println("无效选择")
            return
        }
        
        val card = player.cards[choice - 1]
        
        // 如果是攻击卡，需要选择目标
        val targetIds = mutableListOf<String>()
        if (card.type == moe.gensoukyo.tbc.shared.model.CardType.BASIC) {
            val room = currentRoom ?: return
            val otherPlayers = room.players.filter { it.id != player.id && it.health > 0 }
            
            if (otherPlayers.isNotEmpty()) {
                println("选择攻击目标:")
                otherPlayers.forEachIndexed { index, target ->
                    println("${index + 1}. ${target.name}")
                }
                
                print("选择目标 (输入序号): ")
                val targetChoice = readlnOrNull()?.toIntOrNull()
                
                if (targetChoice != null && targetChoice in 1..otherPlayers.size) {
                    targetIds.add(otherPlayers[targetChoice - 1].id)
                }
            }
        }
        
        val message = ClientMessage.PlayCard(player.id, card.id, targetIds)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private fun showGameStatus() {
        val room = currentRoom ?: return
        
        println("\\n=== 游戏状态 ===")
        println("房间：${room.name}")
        println("回合数：${room.turnCount}")
        println("当前阶段：${room.gamePhase}")
        
        println("\\n玩家状态：")
        room.players.forEach { player ->
            val status = if (player.health <= 0) "[已阵亡]" else ""
            val current = if (room.currentPlayer?.id == player.id) "[当前回合]" else ""
            println("- ${player.name} $status $current")
            println("  身份：${player.identity?.displayName}")
            println("  体力：${player.health}/${player.maxHealth}")
            println("  手牌：${player.cards.size}张")
        }
    }
    
    private suspend fun handleNullificationResponse() {
        val player = currentPlayer ?: return
        
        // 查找无懈可击卡牌
        val wuxieKeji = player.cards.find { it.name == "无懈可击" }
        
        println("\\n选择响应方式:")
        if (wuxieKeji != null) {
            println("1. 使用无懈可击")
            println("2. 不响应")
        } else {
            println("1. 不响应")
        }
        
        print("请选择: ")
        val choice = readlnOrNull()?.toIntOrNull() ?: 1
        
        val responseCardId = if (wuxieKeji != null && choice == 1) wuxieKeji.id else null
        val accept = responseCardId != null
        
        val message = ClientMessage.RespondToCard(player.id, responseCardId, accept)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
    
    private suspend fun handleSpecialSelection(options: List<moe.gensoukyo.tbc.shared.messages.ResponseOption>) {
        if (options.isEmpty()) return
        
        print("请选择 (输入序号): ")
        val choice = readlnOrNull()?.toIntOrNull()
        
        if (choice == null || choice < 1 || choice > options.size) {
            println("无效选择，默认选择第一个选项")
            val selectedOption = options.first()
            val message = ClientMessage.RespondToCard(currentPlayer?.id ?: "", selectedOption.id, true)
            session?.send(Json.encodeToString<ClientMessage>(message))
            return
        }
        
        val selectedOption = options[choice - 1]
        val message = ClientMessage.RespondToCard(currentPlayer?.id ?: "", selectedOption.id, true)
        session?.send(Json.encodeToString<ClientMessage>(message))
    }
}