# 东方格斗编年史 - 出牌架构分析与重构

## 一、现有出牌架构分析

### 1.1 多重架构并存问题

目前项目中存在**三套出牌系统**同时运行，造成混乱和维护困难：

#### 1.1.1 旧基础出牌系统 (UseCard)
```kotlin
// 客户端消息
ClientMessage.UseCard(playerId, cardId, targetId)

// 服务端处理
GameService.useCard(roomId, playerId, cardId, targetId): Boolean
```
**特点：**
- 单目标设计，不支持多目标
- 简单的布尔返回值
- 缺乏响应处理机制

#### 1.1.2 五谷丰登专用系统 (PlayCard)
```kotlin
// 客户端消息
ClientMessage.PlayCard(playerId, cardId, targetIds)

// 服务端处理
GameService.playCard(roomId, playerId, cardId, targetIds): Pair<PlayedCard?, PendingResponse?>?
```
**特点：**
- 支持多目标
- 返回复杂的响应对象
- 专门为五谷丰登设计
- 在GameWebSocket.kt中有大量特殊处理逻辑

#### 1.1.3 新卡牌执行引擎 (PlayCardNew)
```kotlin
// 客户端消息
ClientMessage.PlayCardNew(playerId, cardId, targetIds)

// 服务端处理  
GameService.playCardNew(roomId, playerId, cardId, targetIds): CardExecutionContext?
```
**特点：**
- 基于状态机的CardExecutionEngine
- 标准化的执行阶段 (TARGETING → NULLIFICATION → RESOLUTION → SPECIAL_EXECUTION → COMPLETED)
- 通过CardExecutionHandler处理WebSocket通信
- 支持复杂的卡牌流程

### 1.2 架构问题分析

#### 1.2.1 重复代码和逻辑分散
```kotlin
// GameWebSocket.kt 中有三个不同的处理分支：
is ClientMessage.UseCard -> { /* 旧系统处理 */ }
is ClientMessage.PlayCard -> { /* 五谷丰登特殊处理 */ }  
is ClientMessage.PlayCardNew -> { /* 新系统处理 */ }
```

#### 1.2.2 客户端API混乱
```kotlin
// GameViewModel.kt - 同时存在多个出牌方法
fun useCard(cardId: String, targetId: String? = null)           // 旧系统
fun playCard(cardId: String, targetIds: List<String> = emptyList()) // 发送PlayCardNew消息

// ImprovedGameViewModel.kt 
fun playCard(cardId: String, targetIds: List<String> = emptyList()) // 新系统
```

#### 1.2.3 会话管理混乱
- `CardExecutionHandler` 创建独立的 `SafeWebSocketManager`
- `GameWebSocket.kt` 维护自己的连接映射 (`playerSessions`, `spectatorSessions`)
- 导致"No active sessions found"的会话丢失问题

#### 1.2.4 消息类型冗余
```kotlin
// 三套不同的消息类型：
data class UseCard(...)                    // 旧系统
data class PlayCard(...)                   // 五谷丰登系统  
data class PlayCardNew(...)                // 新系统

// 对应的响应消息：
data class RespondToCard(...)              // 旧系统
data class RespondToCardNew(...)           // 新系统
```

### 1.3 各层级架构现状

#### 1.3.1 共享层 (Shared)
```
shared/messages/
├── Messages.kt                    # 主要客户端/服务端消息
├── CardExecutionMessages.kt       # 新系统专用消息
└── [其他消息文件]

shared/model/
├── Card.kt                       # 卡牌模型
├── CardExecutionContext.kt       # 新系统执行上下文
└── [其他模型]
```

#### 1.3.2 服务端层 (Server)
```
server/service/
└── GameService.kt                # 包含三套出牌方法

server/websocket/
├── GameWebSocket.kt              # 主要WebSocket处理，包含三套出牌逻辑
└── CardExecutionHandler.kt       # 新系统专用处理器

server/engine/
└── CardExecutionEngine.kt        # 新系统卡牌执行引擎
```

#### 1.3.3 客户端层 
```
# Android App
app/viewmodel/
├── GameViewModel.kt              # 旧系统ViewModel
└── ImprovedGameViewModel.kt      # 新系统ViewModel

# CLI Client  
client/service/
└── GameClient.kt                 # 包含多套出牌方法
```

## 二、统一架构设计

### 2.1 设计原则

#### 2.1.1 单一出牌入口
- 统一使用 `PlayCard` 消息类型
- 移除 `UseCard` 和 `PlayCardNew` 
- 所有卡牌通过同一套流程处理

#### 2.1.2 标准化执行流程
```
出牌请求 → 目标验证 → 无懈可击阶段 → 卡牌结算 → 特殊执行 → 完成
```

#### 2.1.3 统一会话管理
- 单一的WebSocket连接管理器
- 统一的玩家会话映射
- 集中的消息广播机制

#### 2.1.4 分层职责清晰
```
表现层 (UI) → 业务层 (GameService) → 执行层 (CardExecutionEngine) → 传输层 (WebSocket)
```

### 2.2 统一架构设计

#### 2.2.1 消息层重构
```kotlin
// 统一的出牌消息 (移除New后缀)
@Serializable
data class PlayCard(
    val playerId: String, 
    val cardId: String, 
    val targetIds: List<String> = emptyList()
) : ClientMessage()

// 统一的响应消息
@Serializable
data class RespondToCard(
    val playerId: String, 
    val responseCardId: String? = null, 
    val accept: Boolean = false
) : ClientMessage()

// 移除的消息类型：
// - UseCard ❌
// - PlayCardNew ❌  
// - RespondToCardNew ❌
```

#### 2.2.2 服务层重构
```kotlin
class GameService {
    // 统一的出牌接口
    suspend fun playCard(
        roomId: String, 
        playerId: String, 
        cardId: String, 
        targetIds: List<String> = emptyList()
    ): CardExecutionResult
    
    // 统一的响应接口
    suspend fun respondToCard(
        roomId: String,
        playerId: String, 
        responseCardId: String? = null,
        accept: Boolean = false
    ): ResponseResult
    
    // 移除的方法：
    // - useCard() ❌
    // - playCardNew() ❌
    // - playCard() (旧版本) ❌
}
```

#### 2.2.3 WebSocket层重构
```kotlin
class UnifiedGameWebSocket {
    private val connectionManager = UnifiedConnectionManager()
    private val cardExecutionEngine = CardExecutionEngine()
    
    suspend fun handleMessage(message: ClientMessage) {
        when (message) {
            is ClientMessage.PlayCard -> handlePlayCard(message)
            is ClientMessage.RespondToCard -> handleRespondToCard(message) 
            // 移除其他出牌相关分支
        }
    }
}
```

#### 2.2.4 连接管理重构
```kotlin
class UnifiedConnectionManager {
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    private val playerSessions = ConcurrentHashMap<String, String>()
    private val spectatorSessions = ConcurrentHashMap<String, String>()
    
    // 统一的会话管理
    fun addPlayerSession(playerId: String, sessionId: String, session: WebSocketSession)
    fun removeSession(sessionId: String)
    fun broadcastToRoom(roomId: String, message: ServerMessage)
    fun sendToPlayer(playerId: String, message: ServerMessage)
}
```

#### 2.2.5 客户端层重构
```kotlin
// 统一的ViewModel接口
interface GameViewModel {
    fun playCard(cardId: String, targetIds: List<String> = emptyList())
    fun respondToCard(responseCardId: String? = null, accept: Boolean = false)
    
    // 移除的方法：
    // - useCard() ❌
    // - playCardNew() ❌
}
```

### 2.3 卡牌执行流程标准化

#### 2.3.1 统一执行阶段
```kotlin
enum class CardExecutionPhase {
    TARGETING,          // 目标确认
    NULLIFICATION,      // 无懈可击响应  
    RESOLUTION,         // 卡牌结算
    SPECIAL_EXECUTION,  // 特殊执行（如五谷丰登选牌）
    COMPLETED          // 执行完成
}
```

#### 2.3.2 标准消息流
```kotlin
// 1. 执行开始
CardExecutionStarted(executionId, casterName, cardName, targetNames, room)

// 2. 需要响应 (无懈可击/特殊选择)
ResponseRequired(executionId, targetPlayerId, responseType, originalCard, casterName)

// 3. 响应接收
ResponseReceived(executionId, playerId, responseCard, accepted)

// 4. 特殊执行开始 (如五谷丰登)
SpecialExecutionStarted(executionId, cardName, currentPlayerId, availableOptions, room)

// 5. 执行完成
CardExecutionCompleted(executionId, success, message, room)
```

## 三、重构实施计划

### 3.1 阶段一：消息层统一 (1-2天)
- [ ] 合并 `UseCard`, `PlayCard`, `PlayCardNew` 为统一的 `PlayCard`
- [ ] 合并响应消息为统一的 `RespondToCard`
- [ ] 更新所有消息序列化测试
- [ ] 创建消息迁移兼容层

### 3.2 阶段二：服务层重构 (2-3天)  
- [ ] 重构 `GameService` 移除重复的出牌方法
- [ ] 确保所有卡牌都通过 `CardExecutionEngine` 执行
- [ ] 统一错误处理和返回值类型
- [ ] 更新单元测试

### 3.3 阶段三：WebSocket层整合 (2-3天)
- [ ] 重构 `GameWebSocket.kt` 移除多套出牌处理逻辑
- [ ] 整合 `CardExecutionHandler` 到主WebSocket处理器
- [ ] 统一连接和会话管理
- [ ] 解决会话丢失问题

### 3.4 阶段四：客户端适配 (1-2天)
- [ ] 统一Android ViewModel接口
- [ ] 更新CLI客户端调用
- [ ] 移除废弃的客户端方法
- [ ] 更新UI层调用

### 3.5 阶段五：测试和清理 (2-3天)
- [ ] 集成测试所有卡牌类型
- [ ] 性能测试和优化
- [ ] 移除废弃代码和文件
- [ ] 更新文档

## 四、架构优势

### 4.1 简化维护
- 单一出牌入口，减少50%的相关代码
- 统一的错误处理和日志记录
- 一致的测试策略

### 4.2 提升可靠性
- 消除会话管理混乱
- 标准化的执行流程
- 更好的错误恢复机制

### 4.3 便于扩展
- 新卡牌只需在CardExecutionEngine中添加逻辑
- 统一的UI适配接口
- 标准化的网络协议

### 4.4 性能提升
- 减少消息类型判断开销
- 统一的连接池管理
- 更高效的广播机制

## 五、风险评估

### 5.1 兼容性风险
**风险：** 破坏现有客户端功能
**缓解：** 
- 分阶段迁移，保留兼容层
- 充分的集成测试
- 渐进式部署策略

### 5.2 数据一致性风险  
**风险：** 重构过程中状态不一致
**缓解：**
- 原子性操作
- 事务性状态更新
- 回滚机制

### 5.3 性能风险
**风险：** 重构可能影响性能
**缓解：**
- 性能基准测试
- 分步优化
- 监控关键指标

## 六、成功指标

### 6.1 代码质量指标
- 出牌相关代码减少 >= 40%
- 消息类型数量减少 >= 50%  
- 单元测试覆盖率 >= 90%

### 6.2 功能指标
- 所有现有卡牌功能正常
- 新卡牌添加时间减少 >= 60%
- 会话丢失问题解决率 100%

### 6.3 性能指标
- 出牌响应时间 <= 100ms
- 内存使用量减少 >= 20%
- 并发连接支持 >= 1000

这个统一架构将显著简化项目结构，提高代码质量，并为未来的功能扩展提供坚实的基础。