package moe.gensoukyo.tbc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.CardSubType
import moe.gensoukyo.tbc.shared.model.CardType
import moe.gensoukyo.tbc.shared.model.GameRoom
import moe.gensoukyo.tbc.shared.model.GameState
import moe.gensoukyo.tbc.shared.model.GamePhase
import moe.gensoukyo.tbc.shared.model.Player
import moe.gensoukyo.tbc.shared.model.TargetType
import moe.gensoukyo.tbc.ui.theme.CardBackground
import moe.gensoukyo.tbc.ui.theme.DamageRed
import moe.gensoukyo.tbc.ui.theme.HealthGreen
import moe.gensoukyo.tbc.ui.theme.TouhouBlue
import moe.gensoukyo.tbc.ui.theme.TouhouBrawlChroniclesTheme
import moe.gensoukyo.tbc.ui.theme.TouhouGold
import moe.gensoukyo.tbc.ui.theme.TouhouRed
import moe.gensoukyo.tbc.viewmodel.ConnectionState
import moe.gensoukyo.tbc.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val storedServerUrl by viewModel.storedServerUrl.collectAsState()
    val roomList by viewModel.roomList.collectAsState()
    
    var playerName by remember { mutableStateOf(viewModel.getStoredPlayerName()) }
    var roomName by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf(viewModel.getStoredRoomId()) }
    var serverUrl by remember(storedServerUrl) { mutableStateOf(storedServerUrl) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showRoomList by remember { mutableStateOf(false) }
    
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.DISCONNECTED) {
            viewModel.connectToServer()
        } else if (connectionState == ConnectionState.CONNECTED) {
            // 连接成功后自动获取房间列表
            viewModel.getRoomList()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 连接状态指示器 - 只在非游戏状态或连接异常时显示
        if (uiState.gameRoom == null || connectionState != ConnectionState.CONNECTED) {
            ConnectionStatusBar(
                connectionState = connectionState,
                serverUrl = serverUrl,
                onServerSettings = { showServerDialog = true },
                onReconnect = { viewModel.connectToServer(serverUrl) }
            )
        }
        
        // 错误消息
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DamageRed.copy(alpha = 0.1f))
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = DamageRed
                )
            }
        }
        
        if (uiState.gameRoom == null) {
            // 游戏大厅界面
            GameLobby(
                onCreateRoom = { showCreateDialog = true },
                onJoinRoom = { showJoinDialog = true },
                onShowRoomList = { showRoomList = true }
            )
        } else {
            // 游戏界面 - 添加滚动支持
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GameRoomScreen(
                    gameRoom = uiState.gameRoom!!,
                    currentPlayer = uiState.currentPlayer,
                    isSpectating = uiState.isSpectating,
                    onDrawCard = { viewModel.drawCard() },
                    onAddHealth = { viewModel.addHealth(10) },
                    onReduceHealth = { viewModel.reduceHealth(10) },
                    onPlayCard = { cardId, targetIds -> viewModel.playCard(cardId, targetIds) },
                    onStartGame = { viewModel.startGame(uiState.gameRoom!!.id) },
                    onEndTurn = { playerId -> viewModel.endTurn(playerId) },
                    onAdjustOrder = { newOrder -> viewModel.adjustPlayerOrder(uiState.gameRoom!!.id, newOrder) }
                )
            }
        }
    }
    
    // 创建房间对话框
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("创建房间") },
            text = {
                Column {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it },
                        label = { Text("房间名称") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { playerName = it },
                        label = { Text("玩家名称") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (roomName.isNotBlank() && playerName.isNotBlank()) {
                            viewModel.createRoom(roomName, playerName)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 加入房间对话框
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("加入房间") },
            text = {
                Column {
                    OutlinedTextField(
                        value = roomId,
                        onValueChange = { roomId = it },
                        label = { Text("房间ID") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { playerName = it },
                        label = { Text("玩家名称") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (roomId.isNotBlank() && playerName.isNotBlank()) {
                            viewModel.joinRoom(roomId, playerName)
                            showJoinDialog = false
                        }
                    }
                ) {
                    Text("加入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 服务器设置对话框
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("服务器设置") },
            text = {
                Column {
                    Text(
                        text = "请输入服务器WebSocket地址",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("服务器地址") },
                        placeholder = { Text("ws://192.168.1.100:8080/game") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "示例:\nws://10.0.2.2:8080/game (Android模拟器)\nws://192.168.1.100:8080/game (局域网)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.connectToServer(serverUrl)
                        showServerDialog = false
                    }
                ) {
                    Text("连接")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 房间列表对话框
    if (showRoomList) {
        RoomListDialog(
            rooms = roomList,
            playerName = playerName,
            onJoinRoom = { room ->
                if (playerName.isNotBlank()) {
                    viewModel.joinRoomById(room.id, playerName)
                    showRoomList = false
                }
            },
            onRefresh = { viewModel.getRoomList() },
            onDismiss = { showRoomList = false },
            onPlayerNameChange = { playerName = it }
        )
    }
}

@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    serverUrl: String,
    onServerSettings: () -> Unit,
    onReconnect: () -> Unit = {}
) {
    val (text, color) = when (connectionState) {
        ConnectionState.DISCONNECTED -> "未连接" to Color.Gray
        ConnectionState.CONNECTING -> "连接中..." to Color.Yellow
        ConnectionState.CONNECTED -> "已连接" to HealthGreen
        ConnectionState.ERROR -> "连接错误" to DamageRed
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "服务器状态: $text",
                    color = color,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
            
            IconButton(onClick = onServerSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "服务器设置",
                    tint = color
                )
            }
            
            // 添加重连按钮（仅在错误状态显示）
            if (connectionState == ConnectionState.ERROR) {
                IconButton(onClick = { onReconnect() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新连接",
                        tint = DamageRed
                    )
                }
            }
        }
    }
}

@Composable
fun GameLobby(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onShowRoomList: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "东方格斗编年史",
            style = MaterialTheme.typography.headlineLarge,
            color = TouhouRed,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onCreateRoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TouhouRed)
        ) {
            Text("创建房间", fontSize = 18.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onShowRoomList,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TouhouBlue)
        ) {
            Text("房间列表", fontSize = 18.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onJoinRoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TouhouGold)
        ) {
            Text("加入房间", fontSize = 18.sp)
        }
    }
}

@Composable
fun GameRoomScreen(
    gameRoom: GameRoom,
    currentPlayer: Player?,
    isSpectating: Boolean = false,
    onDrawCard: () -> Unit,
    onAddHealth: () -> Unit,
    onReduceHealth: () -> Unit,
    onPlayCard: (String, List<String>) -> Unit,
    onStartGame: () -> Unit,
    onEndTurn: (String) -> Unit,
    onAdjustOrder: (List<String>) -> Unit
) {
    var showGameInfo by remember { mutableStateOf(false) }
    var showPlayerOrder by remember { mutableStateOf(false) }
    var showPlayedCards by remember { mutableStateOf(false) }
    var showDebugInfo by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 房间信息和游戏状态
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "房间: ${gameRoom.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "房间ID: ${gameRoom.id.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = { showGameInfo = true },
                                colors = ButtonDefaults.buttonColors(containerColor = TouhouBlue),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("信息", fontSize = 11.sp)
                            }
                            
                            Button(
                                onClick = { showPlayedCards = true },
                                colors = ButtonDefaults.buttonColors(containerColor = TouhouRed),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("记录", fontSize = 11.sp)
                            }
                            
                            Button(
                                onClick = { showDebugInfo = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("调试", fontSize = 11.sp)
                            }
                        }
                        
                        if (gameRoom.gameState == GameState.WAITING) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { showPlayerOrder = true },
                                colors = ButtonDefaults.buttonColors(containerColor = TouhouGold),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("调整顺序", fontSize = 11.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 游戏状态信息
                GameStatusInfo(gameRoom = gameRoom)
            }
        }
        
        // 玩家列表
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "玩家列表 (${gameRoom.players.size}/${gameRoom.maxPlayers})" + 
                           if (gameRoom.spectators.isNotEmpty()) " · 观战者 ${gameRoom.spectators.size}人" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier.height(150.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(gameRoom.players) { player ->
                        PlayerCard(
                            player = player,
                            isCurrentPlayer = player.id == currentPlayer?.id
                        )
                    }
                    
                    // 显示观战者
                    if (gameRoom.spectators.isNotEmpty()) {
                        item {
                            Text(
                                text = "观战者:",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(gameRoom.spectators) { spectator ->
                            SpectatorCard(spectator = spectator)
                        }
                    }
                }
            }
        }
        
        // 当前回合出牌区域
        if (gameRoom.gameState == GameState.PLAYING && gameRoom.currentTurnPlayedCards.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "当前回合出牌 (${gameRoom.currentTurnPlayedCards.size}张)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(gameRoom.currentTurnPlayedCards) { playedCard ->
                            PlayedCardDisplay(playedCard = playedCard)
                        }
                    }
                }
            }
        }
        
        // 当前玩家操作区 - 只有非观战模式才显示
        if (!isSpectating) {
            currentPlayer?.let { player ->
                CurrentPlayerActions(
                    player = player,
                    gameRoom = gameRoom,
                    onDrawCard = onDrawCard,
                    onAddHealth = onAddHealth,
                    onReduceHealth = onReduceHealth,
                    onPlayCard = onPlayCard,
                    onStartGame = onStartGame,
                    onEndTurn = onEndTurn
                )
            }
        } else {
            // 观战模式提示
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "👀 观战模式",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TouhouBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "您正在观看游戏进行",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
    
    // 游戏信息对话框
    if (showGameInfo) {
        GameInfoDialog(
            gameRoom = gameRoom,
            onDismiss = { showGameInfo = false }
        )
    }
    
    // 玩家顺序调整对话框
    if (showPlayerOrder) {
        PlayerOrderDialog(
            players = gameRoom.players,
            onReorder = onAdjustOrder,
            onDismiss = { showPlayerOrder = false }
        )
    }
    
    // 历史出牌记录对话框
    if (showPlayedCards) {
        PlayedCardsHistoryDialog(
            players = gameRoom.players,
            onDismiss = { showPlayedCards = false }
        )
    }
    
    // 调试信息对话框
    if (showDebugInfo) {
        DebugInfoDialog(
            gameRoom = gameRoom,
            currentPlayer = currentPlayer,
            onDismiss = { showDebugInfo = false }
        )
    }
}

@Composable
fun PlayerCard(
    player: Player,
    isCurrentPlayer: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentPlayer) TouhouGold.copy(alpha = 0.2f) 
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isCurrentPlayer)
            BorderStroke(2.dp, TouhouGold)
            else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = player.name + if (isCurrentPlayer) " (你)" else "",
                        fontWeight = FontWeight.Medium
                    )
                    
                    // 身份标识
                    player.identity?.let { identity ->
                        val (identityText, identityColor) = when (identity) {
                            moe.gensoukyo.tbc.shared.model.Identity.LORD -> "主" to TouhouGold
                            moe.gensoukyo.tbc.shared.model.Identity.LOYALIST -> "忠" to TouhouBlue
                            moe.gensoukyo.tbc.shared.model.Identity.REBEL -> "反" to DamageRed
                            moe.gensoukyo.tbc.shared.model.Identity.SPY -> "内" to Color.Gray
                        }
                        
                        Card(
                            modifier = Modifier.padding(2.dp),
                            colors = CardDefaults.cardColors(containerColor = identityColor.copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = identityText,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // 武将信息
                player.general?.let { general ->
                    Text(
                        text = "武将: ${general.name} (${general.kingdom})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TouhouBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Text(
                    text = "手牌: ${player.cards.size}张",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                // 装备信息 (简化显示)
                val equipmentCount = listOfNotNull(
                    player.equipment.weapon,
                    player.equipment.armor,
                    player.equipment.defensiveHorse,
                    player.equipment.offensiveHorse
                ).size
                if (equipmentCount > 0) {
                    Text(
                        text = "装备: ${equipmentCount}件",
                        style = MaterialTheme.typography.bodySmall,
                        color = HealthGreen
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${player.health}/${player.maxHealth}",
                    color = if (player.health > player.maxHealth * 0.3) HealthGreen else DamageRed,
                    fontWeight = FontWeight.Bold
                )
                
                LinearProgressIndicator(
                    progress =  player.health.toFloat() / player.maxHealth,
                    modifier = Modifier
                        .width(80.dp)
                        .height(6.dp),
                    color = if (player.health > player.maxHealth * 0.3) HealthGreen else DamageRed,
                )
            }
        }
    }
}

@Composable
fun SpectatorCard(spectator: moe.gensoukyo.tbc.shared.model.Spectator) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Gray.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👀 ${spectator.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "观战中",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun EquipmentCard(
    card: Card?,
    equipmentType: String
) {
    Card(
        modifier = Modifier
            .width(80.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (card != null) {
                when (card.subType) {
                    CardSubType.WEAPON -> DamageRed.copy(alpha = 0.6f)
                    CardSubType.ARMOR -> TouhouBlue.copy(alpha = 0.6f)
                    CardSubType.HORSE -> HealthGreen.copy(alpha = 0.6f)
                    else -> TouhouGold.copy(alpha = 0.6f)
                }
            } else {
                Color.Gray.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (card != null) {
                Text(
                    text = card.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // 显示装备效果
                if (card.range != 1) {
                    Text(
                        text = if (card.range > 0) "+${card.range}" else "${card.range}",
                        color = Color.White,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = equipmentType,
                    color = Color.Gray,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun EquipmentArea(
    player: Player
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "装备区",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 武器
                EquipmentCard(
                    card = player.equipment.weapon,
                    equipmentType = "武器"
                )
                
                // 防具
                EquipmentCard(
                    card = player.equipment.armor,
                    equipmentType = "防具"
                )
                
                // 攻击马
                EquipmentCard(
                    card = player.equipment.offensiveHorse,
                    equipmentType = "攻击马"
                )
                
                // 防御马
                EquipmentCard(
                    card = player.equipment.defensiveHorse,
                    equipmentType = "防御马"
                )
            }
        }
    }
}

@Composable
fun TargetSelector(
    card: Card,
    availableTargets: List<Player>,
    currentPlayer: Player,
    onTargetsSelected: (List<String>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedTargets by remember { mutableStateOf(emptySet<String>()) }
    
    AlertDialog(
        onDismissRequest = onCancel,
        title = { 
            Text("选择目标 - ${card.name}")
        },
        text = {
            Column {
                Text(
                    text = "卡牌效果: ${card.effect}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = when (card.targetType) {
                        TargetType.SINGLE -> "选择一个目标"
                        TargetType.MULTIPLE -> "选择一个或多个目标"
                        TargetType.ALL_OTHERS -> "将对所有其他玩家生效"
                        TargetType.ALL_PLAYERS -> "将对所有玩家生效"
                        TargetType.NONE -> "无需选择目标"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (card.targetType == TargetType.ALL_OTHERS) {
                    // 自动选择所有其他玩家
                    LaunchedEffect(Unit) {
                        selectedTargets = availableTargets.filter { it.id != currentPlayer.id }.map { it.id }.toSet()
                    }
                } else if (card.targetType == TargetType.ALL_PLAYERS) {
                    // 自动选择所有玩家
                    LaunchedEffect(Unit) {
                        selectedTargets = availableTargets.map { it.id }.toSet()
                    }
                }
                
                if (card.targetType == TargetType.SINGLE || card.targetType == TargetType.MULTIPLE) {
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(availableTargets.filter { it.id != currentPlayer.id }) { target ->
                            val isSelected = selectedTargets.contains(target.id)
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTargets = if (card.targetType == TargetType.SINGLE) {
                                            // 单目标，替换选择
                                            setOf(target.id)
                                        } else {
                                            // 多目标，切换选择
                                            if (isSelected) {
                                                selectedTargets - target.id
                                            } else {
                                                selectedTargets + target.id
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) TouhouGold.copy(alpha = 0.3f) 
                                    else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) BorderStroke(2.dp, TouhouGold) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = target.name,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "生命: ${target.health}/${target.maxHealth}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = TouhouGold,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (availableTargets.filter { it.id != currentPlayer.id }.isEmpty()) {
                    Text(
                        text = "没有可用的目标",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    onTargetsSelected(selectedTargets.toList())
                },
                enabled = when (card.targetType) {
                    TargetType.NONE -> true
                    TargetType.SINGLE -> selectedTargets.size == 1
                    TargetType.MULTIPLE -> selectedTargets.isNotEmpty()
                    TargetType.ALL_OTHERS, TargetType.ALL_PLAYERS -> true
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    )
}

@Composable
fun CurrentPlayerActions(
    player: Player,
    gameRoom: GameRoom,
    onDrawCard: () -> Unit,
    onAddHealth: () -> Unit,
    onReduceHealth: () -> Unit,
    onPlayCard: (String, List<String>) -> Unit,
    onStartGame: () -> Unit,
    onEndTurn: (String) -> Unit
) {
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    var showTargetSelector by remember { mutableStateOf(false) }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "操作区",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 根据游戏状态显示不同按钮
            when (gameRoom.gameState) {
                GameState.WAITING -> {
                    Button(
                        onClick = onStartGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TouhouRed)
                    ) {
                        Text("开始游戏", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                GameState.PLAYING -> {
                    // 检查是否是当前玩家
                    val currentPlayer = if (gameRoom.players.isNotEmpty()) {
                        gameRoom.players[gameRoom.currentPlayerIndex]
                    } else null
                    
                    if (currentPlayer?.id == player.id) {
                        // 当前玩家的操作
                        Column {
                            Text(
                                text = "轮到你行动 - ${when(gameRoom.gamePhase) {
                                    GamePhase.DRAW -> "摸牌阶段"
                                    GamePhase.PLAY -> "出牌阶段"
                                    GamePhase.DISCARD -> "弃牌阶段"
                                }}",
                                color = TouhouRed,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 操作按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onDrawCard,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TouhouBlue)
                                ) {
                                    Text("抽卡", fontSize = 16.sp)
                                }
                                
                                Button(
                                    onClick = onAddHealth,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = HealthGreen)
                                ) {
                                    Text("加血", fontSize = 16.sp)
                                }
                                
                                Button(
                                    onClick = onReduceHealth,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DamageRed)
                                ) {
                                    Text("扣血", fontSize = 16.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(
                                onClick = { onEndTurn(player.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TouhouGold)
                            ) {
                                Text("结束回合", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // 等待其他玩家
                        Text(
                            text = "等待 ${currentPlayer?.name ?: "其他玩家"} 行动中...",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                GameState.FINISHED -> {
                    Text(
                        text = "游戏结束",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 装备区
            EquipmentArea(player = player)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 手牌展示
            Text(
                text = "手牌 (${player.cards.size}张)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(player.cards) { card ->
                    GameCard(
                        card = card,
                        onClick = { 
                            when (card.targetType) {
                                TargetType.NONE -> {
                                    // 无目标卡牌直接使用
                                    onPlayCard(card.id, emptyList())
                                }
                                else -> {
                                    // 需要目标选择的卡牌
                                    selectedCard = card
                                    showTargetSelector = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 目标选择对话框
    if (showTargetSelector && selectedCard != null) {
        TargetSelector(
            card = selectedCard!!,
            availableTargets = gameRoom.players,
            currentPlayer = player,
            onTargetsSelected = { targetIds ->
                onPlayCard(selectedCard!!.id, targetIds)
                selectedCard = null
                showTargetSelector = false
            },
            onCancel = {
                selectedCard = null
                showTargetSelector = false
            }
        )
    }
}

@Composable
fun GameCard(
    card: Card,
    onClick: () -> Unit
) {
    val cardColor = when (card.type) {
        CardType.BASIC -> when (card.name) {
            "杀" -> DamageRed.copy(alpha = 0.8f)
            "闪" -> TouhouBlue.copy(alpha = 0.8f) 
            "桃" -> HealthGreen.copy(alpha = 0.8f)
            else -> TouhouGold.copy(alpha = 0.8f)
        }
        CardType.TRICK -> TouhouGold.copy(alpha = 0.8f)
        CardType.EQUIPMENT -> when (card.subType) {
            CardSubType.WEAPON -> DamageRed.copy(alpha = 0.6f)
            CardSubType.ARMOR -> TouhouBlue.copy(alpha = 0.6f)
            CardSubType.HORSE -> HealthGreen.copy(alpha = 0.6f)
            else -> TouhouGold.copy(alpha = 0.6f)
        }
    }
    
    Card(
        modifier = Modifier
            .width(100.dp)
            .height(140.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = card.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (card.damage > 0) {
                    Text(
                        text = "伤害: ${card.damage}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                
                if (card.healing > 0) {
                    Text(
                        text = "治疗: ${card.healing}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                
                // 显示目标类型提示
                when (card.targetType) {
                    TargetType.SINGLE -> {
                        Text(
                            text = "🎯",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    TargetType.MULTIPLE -> {
                        Text(
                            text = "🎯+",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    TargetType.ALL_OTHERS -> {
                        Text(
                            text = "🌟",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    TargetType.NONE -> {
                        // 无特殊标记
                    }
                    TargetType.ALL_PLAYERS -> {
                        Text(
                            text = "💫",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            Text(
                text = card.type.name,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun PlayedCardDisplay(playedCard: moe.gensoukyo.tbc.shared.model.PlayedCard) {
    val cardColor = when (playedCard.card.type) {
        CardType.BASIC -> when (playedCard.card.name) {
            "杀" -> DamageRed.copy(alpha = 0.8f)
            "闪" -> TouhouBlue.copy(alpha = 0.8f) 
            "桃" -> HealthGreen.copy(alpha = 0.8f)
            else -> TouhouGold.copy(alpha = 0.8f)
        }
        CardType.TRICK -> TouhouGold.copy(alpha = 0.8f)
        CardType.EQUIPMENT -> when (playedCard.card.subType) {
            CardSubType.WEAPON -> DamageRed.copy(alpha = 0.6f)
            CardSubType.ARMOR -> TouhouBlue.copy(alpha = 0.6f)
            CardSubType.HORSE -> HealthGreen.copy(alpha = 0.6f)
            else -> TouhouGold.copy(alpha = 0.6f)
        }
    }
    
    Card(
        modifier = Modifier
            .width(100.dp)
            .height(140.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = playedCard.card.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = playedCard.playerName,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (playedCard.card.damage > 0) {
                    Text(
                        text = "伤害: ${playedCard.card.damage}",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
                
                if (playedCard.card.healing > 0) {
                    Text(
                        text = "治疗: ${playedCard.card.healing}",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
            
            Text(
                text = playedCard.card.type.name,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 8.sp
            )
        }
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
fun ConnectionStatusBarPreview() {
    TouhouBrawlChroniclesTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectionStatusBar(
                connectionState = ConnectionState.CONNECTED,
                serverUrl = "ws://10.0.2.2:8080/game",
                onServerSettings = {},
                onReconnect = {}
            )
            ConnectionStatusBar(
                connectionState = ConnectionState.ERROR,
                serverUrl = "ws://192.168.1.100:8080/game",
                onServerSettings = {},
                onReconnect = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameLobbyPreview() {
    TouhouBrawlChroniclesTheme {
        GameLobby(
            onCreateRoom = {},
            onJoinRoom = {},
            onShowRoomList = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PlayerCardPreview() {
    TouhouBrawlChroniclesTheme {
        PlayerCard(
            player = Player(
                id = "1",
                name = "测试玩家",
                health = 80,
                maxHealth = 100,
                cards = mutableListOf()
            ),
            isCurrentPlayer = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GameCardPreview() {
    TouhouBrawlChroniclesTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GameCard(
                card = Card(
                    id = "1",
                    name = "杀",
                    type = CardType.BASIC,
                    effect = "造成1点伤害",
                    damage = 1
                ),
                onClick = {}
            )
            
            GameCard(
                card = Card(
                    id = "2",
                    name = "桃",
                    type = CardType.BASIC,
                    effect = "恢复1点生命",
                    healing = 1
                ),
                onClick = {}
            )
            
            GameCard(
                card = Card(
                    id = "3",
                    name = "闪",
                    type = CardType.BASIC,
                    effect = "抵消攻击"
                ),
                onClick = {}
            )
            
            GameCard(
                card = Card(
                    id = "4",
                    name = "决斗",
                    type = CardType.TRICK,
                    effect = "与目标决斗",
                    damage = 1
                ),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
fun GameRoomScreenPreview() {
    TouhouBrawlChroniclesTheme {
        val sampleCards = listOf(
            Card("1", "杀", CardType.BASIC, "造成1点伤害", 1, 0),
            Card("2", "桃", CardType.BASIC, "恢复1点生命", 0, 1),
            Card("3", "闪", CardType.BASIC, "抵消攻击", 0, 0)
        )
        
        val currentPlayer = Player(
            id = "player1",
            name = "当前玩家",
            health = 85,
            maxHealth = 100,
            cards = sampleCards.toMutableList()
        )
        
        val otherPlayer = Player(
            id = "player2", 
            name = "其他玩家",
            health = 60,
            maxHealth = 100,
            cards = mutableListOf()
        )
        
        val gameRoom = GameRoom(
            id = "room123".repeat(10),
            name = "测试房间",
            players = mutableListOf(currentPlayer, otherPlayer),
            currentPlayerIndex = 0,
            gameState = GameState.PLAYING,
            maxPlayers = 8
        )
        
        GameRoomScreen(
            gameRoom = gameRoom,
            currentPlayer = currentPlayer,
            onDrawCard = {},
            onAddHealth = {},
            onReduceHealth = {},
            onPlayCard = { _, _ -> },
            onStartGame = {},
            onEndTurn = { _ -> },
            onAdjustOrder = { _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ServerSettingsDialogPreview() {
    TouhouBrawlChroniclesTheme {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("服务器设置") },
            text = {
                Column {
                    Text(
                        text = "请输入服务器WebSocket地址",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = "ws://10.0.2.2:8080/game",
                        onValueChange = { },
                        label = { Text("服务器地址") },
                        placeholder = { Text("ws://192.168.1.100:8080/game") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "示例:\nws://10.0.2.2:8080/game (Android模拟器)\nws://192.168.1.100:8080/game (局域网)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { }) {
                    Text("连接")
                }
            },
            dismissButton = {
                TextButton(onClick = { }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun RoomListDialog(
    rooms: List<moe.gensoukyo.tbc.shared.model.GameRoom>,
    playerName: String,
    onJoinRoom: (moe.gensoukyo.tbc.shared.model.GameRoom) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onPlayerNameChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("房间列表 (${rooms.size})")
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = TouhouBlue
                    )
                }
            }
        },
        text = {
            Column {
                // 玩家名输入
                OutlinedTextField(
                    value = playerName,
                    onValueChange = onPlayerNameChange,
                    label = { Text("玩家名称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true
                )
                
                if (rooms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无房间",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rooms) { room ->
                            RoomListItem(
                                room = room,
                                onJoinRoom = { onJoinRoom(room) },
                                canJoin = playerName.isNotBlank() && room.players.size < room.maxPlayers
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun RoomListItem(
    room: moe.gensoukyo.tbc.shared.model.GameRoom,
    onJoinRoom: () -> Unit,
    canJoin: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (room.gameState) {
                moe.gensoukyo.tbc.shared.model.GameState.WAITING -> CardBackground
                moe.gensoukyo.tbc.shared.model.GameState.PLAYING -> TouhouGold.copy(alpha = 0.1f)
                moe.gensoukyo.tbc.shared.model.GameState.FINISHED -> Color.Gray.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "玩家: ${room.players.size}/${room.maxPlayers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    Text(
                        text = when (room.gameState) {
                            moe.gensoukyo.tbc.shared.model.GameState.WAITING -> "等待中"
                            moe.gensoukyo.tbc.shared.model.GameState.PLAYING -> "游戏中"
                            moe.gensoukyo.tbc.shared.model.GameState.FINISHED -> "已结束"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (room.gameState) {
                            moe.gensoukyo.tbc.shared.model.GameState.WAITING -> HealthGreen
                            moe.gensoukyo.tbc.shared.model.GameState.PLAYING -> TouhouGold
                            moe.gensoukyo.tbc.shared.model.GameState.FINISHED -> Color.Gray
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            if (canJoin && room.gameState == moe.gensoukyo.tbc.shared.model.GameState.WAITING) {
                Button(
                    onClick = onJoinRoom,
                    colors = ButtonDefaults.buttonColors(containerColor = TouhouBlue),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("加入", fontSize = 14.sp)
                }
            } else {
                Text(
                    text = when {
                        room.players.size >= room.maxPlayers -> "已满"
                        room.gameState != moe.gensoukyo.tbc.shared.model.GameState.WAITING -> "进行中"
                        else -> "不可用"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun GameStatusInfo(gameRoom: GameRoom) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "游戏状态: ${when(gameRoom.gameState) {
                    GameState.WAITING -> "等待开始"
                    GameState.PLAYING -> "进行中"
                    GameState.FINISHED -> "已结束"
                }}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            if (gameRoom.gameState == GameState.PLAYING) {
                Text(
                    text = "第 ${gameRoom.turnCount} 回合",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                // 添加牌堆信息
                Text(
                    text = "牌堆: ${gameRoom.deck.size}张 | 弃牌: ${gameRoom.discardPile.size}张",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else if (gameRoom.gameState == GameState.WAITING) {
                // 显示身份分配信息
                val lordCount = gameRoom.players.count { it.identity == moe.gensoukyo.tbc.shared.model.Identity.LORD }
                val loyalistCount = gameRoom.players.count { it.identity == moe.gensoukyo.tbc.shared.model.Identity.LOYALIST }
                val rebelCount = gameRoom.players.count { it.identity == moe.gensoukyo.tbc.shared.model.Identity.REBEL }
                val spyCount = gameRoom.players.count { it.identity == moe.gensoukyo.tbc.shared.model.Identity.SPY }
                
                if (lordCount + loyalistCount + rebelCount + spyCount > 0) {
                    Text(
                        text = "身份配置: 主$lordCount 忠$loyalistCount 反$rebelCount 内$spyCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = TouhouBlue
                    )
                }
            }
        }
        
        if (gameRoom.gameState == GameState.PLAYING) {
            Column(horizontalAlignment = Alignment.End) {
                val currentPlayer = if (gameRoom.players.isNotEmpty()) {
                    gameRoom.players[gameRoom.currentPlayerIndex]
                } else null
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当前玩家: ${currentPlayer?.name ?: "未知"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TouhouRed
                    )
                    
                    // 显示当前玩家身份
                    currentPlayer?.identity?.let { identity ->
                        val (identityText, identityColor) = when (identity) {
                            moe.gensoukyo.tbc.shared.model.Identity.LORD -> "主" to TouhouGold
                            moe.gensoukyo.tbc.shared.model.Identity.LOYALIST -> "忠" to TouhouBlue
                            moe.gensoukyo.tbc.shared.model.Identity.REBEL -> "反" to DamageRed
                            moe.gensoukyo.tbc.shared.model.Identity.SPY -> "内" to Color.Gray
                        }
                        
                        Card(
                            modifier = Modifier.padding(2.dp),
                            colors = CardDefaults.cardColors(containerColor = identityColor.copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = identityText,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Text(
                    text = "阶段: ${when(gameRoom.gamePhase) {
                        GamePhase.DRAW -> "摸牌"
                        GamePhase.PLAY -> "出牌"
                        GamePhase.DISCARD -> "弃牌"
                    }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TouhouBlue
                )
                
                // 显示当前玩家血量和武将
                currentPlayer?.let { player ->
                    Text(
                        text = "血量: ${player.health}/${player.maxHealth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (player.health > player.maxHealth * 0.3) HealthGreen else DamageRed
                    )
                    
                    player.general?.let { general ->
                        Text(
                            text = "武将: ${general.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GameInfoDialog(
    gameRoom: GameRoom,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("游戏详细信息") },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = "房间信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("房间名称: ${gameRoom.name}")
                    Text("房间ID: ${gameRoom.id}")
                    Text("最大玩家数: ${gameRoom.maxPlayers}")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "游戏状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text("状态: ${gameRoom.gameState}")
                    if (gameRoom.gameState == GameState.PLAYING) {
                        Text("回合数: ${gameRoom.turnCount}")
                        Text("当前阶段: ${gameRoom.gamePhase}")
                        val currentPlayer = if (gameRoom.players.isNotEmpty()) {
                            gameRoom.players[gameRoom.currentPlayerIndex]
                        } else null
                        Text("当前玩家: ${currentPlayer?.name ?: "未知"}")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "玩家列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                items(gameRoom.players) { player ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val currentPlayer = if (gameRoom.players.isNotEmpty()) {
                                gameRoom.players[gameRoom.currentPlayerIndex]
                            } else null
                            
                            Text(
                                text = "${player.name} ${if (player.id == currentPlayer?.id) "(当前)" else ""}",
                                fontWeight = FontWeight.Medium
                            )
                            Text("生命值: ${player.health}/${player.maxHealth}")
                            Text("手牌数: ${player.cards.size}")
                            Text("已出牌数: ${player.playedCards.size}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun PlayerOrderDialog(
    players: List<Player>,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var playerOrder by remember { mutableStateOf(players.map { it.id }) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整出牌顺序") },
        text = {
            LazyColumn {
                items(playerOrder.size) { index ->
                    val playerId = playerOrder[index]
                    val player = players.find { it.id == playerId }
                    
                    if (player != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}. ${player.name}")
                                
                                Row {
                                    if (index > 0) {
                                        Button(
                                            onClick = {
                                                val newOrder = playerOrder.toMutableList()
                                                val temp = newOrder[index]
                                                newOrder[index] = newOrder[index - 1]
                                                newOrder[index - 1] = temp
                                                playerOrder = newOrder
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = TouhouBlue)
                                        ) {
                                            Text("↑", fontSize = 12.sp)
                                        }
                                    }
                                    
                                    if (index < playerOrder.size - 1) {
                                        Button(
                                            onClick = {
                                                val newOrder = playerOrder.toMutableList()
                                                val temp = newOrder[index]
                                                newOrder[index] = newOrder[index + 1]
                                                newOrder[index + 1] = temp
                                                playerOrder = newOrder
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = TouhouBlue)
                                        ) {
                                            Text("↓", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onReorder(playerOrder)
                    onDismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun PlayedCardsHistoryDialog(
    players: List<Player>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("出牌历史记录") },
        text = {
            LazyColumn {
                items(players) { player ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${player.name} (${player.playedCards.size}张)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            if (player.playedCards.isEmpty()) {
                                Text(
                                    text = "尚未出牌",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(player.playedCards.takeLast(5)) { playedCard ->
                                        Card(
                                            modifier = Modifier
                                                .width(60.dp)
                                                .height(80.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = when (playedCard.card.type) {
                                                    CardType.BASIC -> when (playedCard.card.name) {
                                                        "杀" -> DamageRed.copy(alpha = 0.7f)
                                                        "闪" -> TouhouBlue.copy(alpha = 0.7f) 
                                                        "桃" -> HealthGreen.copy(alpha = 0.7f)
                                                        else -> TouhouGold.copy(alpha = 0.7f)
                                                    }
                                                    CardType.TRICK -> TouhouGold.copy(alpha = 0.7f)
                                                    CardType.EQUIPMENT -> when (playedCard.card.subType) {
                                                        CardSubType.WEAPON -> DamageRed.copy(alpha = 0.5f)
                                                        CardSubType.ARMOR -> TouhouBlue.copy(alpha = 0.5f)
                                                        CardSubType.HORSE -> HealthGreen.copy(alpha = 0.5f)
                                                        else -> TouhouGold.copy(alpha = 0.5f)
                                                    }
                                                }
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = playedCard.card.name,
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = "回合${playedCard.turnNumber}",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 6.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                if (player.playedCards.size > 5) {
                                    Text(
                                        text = "...还有${player.playedCards.size - 5}张",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun DebugInfoDialog(
    gameRoom: GameRoom,
    currentPlayer: Player?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调试信息") },
        text = {
            LazyColumn(
                modifier = Modifier.height(400.dp)
            ) {
                item {
                    // 房间基本信息
                    Text(
                        text = "房间信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TouhouRed,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text("房间ID: ${gameRoom.id}")
                    Text("房间名: ${gameRoom.name}")
                    Text("最大玩家: ${gameRoom.maxPlayers}")
                    Text("当前玩家数: ${gameRoom.players.size}")
                    Text("观战者数: ${gameRoom.spectators.size}")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 游戏状态信息
                    Text(
                        text = "游戏状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TouhouBlue,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text("游戏状态: ${gameRoom.gameState}")
                    Text("游戏阶段: ${gameRoom.gamePhase}")
                    Text("回合数: ${gameRoom.turnCount}")
                    Text("当前玩家索引: ${gameRoom.currentPlayerIndex}")
                    Text("当前玩家: ${gameRoom.currentPlayer?.name ?: "无"}")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 牌堆信息
                    Text(
                        text = "牌堆信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TouhouGold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text("牌堆剩余: ${gameRoom.deck.size}张")
                    Text("弃牌堆: ${gameRoom.discardPile.size}张")
                    Text("当前回合出牌: ${gameRoom.currentTurnPlayedCards.size}张")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 当前玩家信息
                if (currentPlayer != null) {
                    item {
                        Text(
                            text = "当前客户端玩家",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DamageRed,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text("玩家ID: ${currentPlayer.id}")
                        Text("玩家名: ${currentPlayer.name}")
                        Text("身份: ${currentPlayer.identity ?: "未分配"}")
                        Text("武将: ${currentPlayer.general?.name ?: "未选择"}")
                        Text("血量: ${currentPlayer.health}${currentPlayer.maxHealth}")
                        Text("手牌数: ${currentPlayer.cards.size}")
                        Text("已出牌数: ${currentPlayer.playedCards.size}")
                        Text("是否铁索: ${currentPlayer.isChained}")
                        Text("延时锦囊: ${currentPlayer.delayedCards.size}张")
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("装备详情:", fontWeight = FontWeight.Bold)
                        Text("  武器: ${currentPlayer.equipment.weapon?.name ?: "无"}")
                        Text("  防具: ${currentPlayer.equipment.armor?.name ?: "无"}")
                        Text("  攻击马: ${currentPlayer.equipment.offensiveHorse?.name ?: "无"}")
                        Text("  防御马: ${currentPlayer.equipment.defensiveHorse?.name ?: "无"}")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // 所有玩家详细信息
                item {
                    Text(
                        text = "所有玩家详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HealthGreen,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                items(gameRoom.players) { player ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (player.identity) {
                                moe.gensoukyo.tbc.shared.model.Identity.LORD -> TouhouGold.copy(alpha = 0.1f)
                                moe.gensoukyo.tbc.shared.model.Identity.LOYALIST -> TouhouBlue.copy(alpha = 0.1f)
                                moe.gensoukyo.tbc.shared.model.Identity.REBEL -> DamageRed.copy(alpha = 0.1f)
                                moe.gensoukyo.tbc.shared.model.Identity.SPY -> Color.Gray.copy(alpha = 0.1f)
                                null -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = player.name,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                player.identity?.let { identity ->
                                    val identityText = when (identity) {
                                        moe.gensoukyo.tbc.shared.model.Identity.LORD -> "主公"
                                        moe.gensoukyo.tbc.shared.model.Identity.LOYALIST -> "忠臣"
                                        moe.gensoukyo.tbc.shared.model.Identity.REBEL -> "反贼"
                                        moe.gensoukyo.tbc.shared.model.Identity.SPY -> "内奸"
                                    }
                                    Text(
                                        text = identityText,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            
                            Text("ID: ${player.id}", fontSize = 10.sp, color = Color.Gray)
                            player.general?.let { general ->
                                Text("武将: ${general.name} (${general.kingdom}) +${general.healthBonus}血", fontSize = 10.sp)
                            }
                            Text("生命: ${player.health}/${player.maxHealth}", fontSize = 10.sp)
                            Text("手牌: ${player.cards.size}张, 已出牌: ${player.playedCards.size}张", fontSize = 10.sp)
                            
                            val equipments = listOfNotNull(
                                player.equipment.weapon?.name,
                                player.equipment.armor?.name,
                                player.equipment.offensiveHorse?.name,
                                player.equipment.defensiveHorse?.name
                            )
                            if (equipments.isNotEmpty()) {
                                Text("装备: ${equipments.joinToString(", ")}", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
