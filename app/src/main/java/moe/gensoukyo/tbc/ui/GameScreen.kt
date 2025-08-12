package moe.gensoukyo.tbc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import moe.gensoukyo.tbc.shared.model.Card
import moe.gensoukyo.tbc.shared.model.CardType
import moe.gensoukyo.tbc.shared.model.Player
import moe.gensoukyo.tbc.shared.model.GameRoom
import moe.gensoukyo.tbc.shared.model.GameState
import moe.gensoukyo.tbc.ui.theme.*
import moe.gensoukyo.tbc.viewmodel.ConnectionState
import moe.gensoukyo.tbc.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    var playerName by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("ws://10.0.2.2:8080/game") }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.DISCONNECTED) {
            viewModel.connectToServer(serverUrl)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 连接状态指示器
        ConnectionStatusBar(
            connectionState = connectionState,
            serverUrl = serverUrl,
            onServerSettings = { showServerDialog = true }
        )
        
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
                onJoinRoom = { showJoinDialog = true }
            )
        } else {
            // 游戏界面
            GameRoomScreen(
                gameRoom = uiState.gameRoom!!,
                currentPlayer = uiState.currentPlayer,
                onDrawCard = { viewModel.drawCard() },
                onAddHealth = { viewModel.addHealth(10) },
                onReduceHealth = { viewModel.reduceHealth(10) },
                onUseCard = { cardId, targetId -> viewModel.useCard(cardId, targetId) }
            )
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
}

@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    serverUrl: String,
    onServerSettings: () -> Unit
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
        }
    }
}

@Composable
fun GameLobby(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit
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
            onClick = onJoinRoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TouhouBlue)
        ) {
            Text("加入房间", fontSize = 18.sp)
        }
    }
}

@Composable
fun GameRoomScreen(
    gameRoom: moe.gensoukyo.tbc.shared.model.GameRoom,
    currentPlayer: Player?,
    onDrawCard: () -> Unit,
    onAddHealth: () -> Unit,
    onReduceHealth: () -> Unit,
    onUseCard: (String, String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 房间信息
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "房间: ${gameRoom.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "房间ID: ${gameRoom.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "游戏状态: ${gameRoom.gameState}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // 玩家列表
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "玩家列表 (${gameRoom.players.size}/${gameRoom.maxPlayers})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier.height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(gameRoom.players) { player ->
                        PlayerCard(
                            player = player,
                            isCurrentPlayer = player.id == currentPlayer?.id
                        )
                    }
                }
            }
        }
        
        // 当前玩家操作区
        currentPlayer?.let { player ->
            CurrentPlayerActions(
                player = player,
                onDrawCard = onDrawCard,
                onAddHealth = onAddHealth,
                onReduceHealth = onReduceHealth,
                onUseCard = onUseCard
            )
        }
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
                Text(
                    text = player.name + if (isCurrentPlayer) " (你)" else "",
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "手牌: ${player.cards.size}张",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
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
fun CurrentPlayerActions(
    player: Player,
    onDrawCard: () -> Unit,
    onAddHealth: () -> Unit,
    onReduceHealth: () -> Unit,
    onUseCard: (String, String?) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "操作区",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDrawCard,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TouhouBlue)
                ) {
                    Text("抽卡")
                }
                
                Button(
                    onClick = onAddHealth,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = HealthGreen)
                ) {
                    Text("加血")
                }
                
                Button(
                    onClick = onReduceHealth,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DamageRed)
                ) {
                    Text("扣血")
                }
            }
            
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
                        onClick = { onUseCard(card.id, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun GameCard(
    card: Card,
    onClick: () -> Unit
) {
    val cardColor = when (card.type) {
        CardType.ATTACK -> DamageRed.copy(alpha = 0.8f)
        CardType.DEFENSE -> TouhouBlue.copy(alpha = 0.8f)
        CardType.RECOVERY -> HealthGreen.copy(alpha = 0.8f)
        CardType.SKILL -> TouhouGold.copy(alpha = 0.8f)
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
            }
            
            Text(
                text = card.type.name,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
fun ConnectionStatusBarPreview() {
    TouhouBrawlChroniclesTheme {
        ConnectionStatusBar(
            connectionState = ConnectionState.CONNECTED,
            serverUrl = "ws://10.0.2.2:8080/game",
            onServerSettings = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GameLobbyPreview() {
    TouhouBrawlChroniclesTheme {
        GameLobby(
            onCreateRoom = {},
            onJoinRoom = {}
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
                    type = CardType.ATTACK,
                    effect = "造成1点伤害",
                    damage = 1
                ),
                onClick = {}
            )
            
            GameCard(
                card = Card(
                    id = "2",
                    name = "桃",
                    type = CardType.RECOVERY,
                    effect = "恢复1点生命",
                    healing = 1
                ),
                onClick = {}
            )
            
            GameCard(
                card = Card(
                    id = "3",
                    name = "闪",
                    type = CardType.DEFENSE,
                    effect = "抵消攻击",
                    damage = 0
                ),
                onClick = {}
            )
            
            GameCard(
                card = Card(
                    id = "4",
                    name = "决斗",
                    type = CardType.SKILL,
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
            Card("1", "杀", CardType.ATTACK, "造成1点伤害", 1, 0),
            Card("2", "桃", CardType.RECOVERY, "恢复1点生命", 0, 1),
            Card("3", "闪", CardType.DEFENSE, "抵消攻击", 0, 0)
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
            id = "room123",
            name = "测试房间",
            players = mutableListOf(currentPlayer, otherPlayer),
            currentPlayer = "player1",
            gameState = GameState.PLAYING,
            maxPlayers = 8
        )
        
        GameRoomScreen(
            gameRoom = gameRoom,
            currentPlayer = currentPlayer,
            onDrawCard = {},
            onAddHealth = {},
            onReduceHealth = {},
            onUseCard = { _, _ -> }
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