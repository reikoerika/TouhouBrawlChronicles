# 新的出牌架构设计

## 概述

重构了整个出牌系统，使其符合三国杀的标准流程和规则。新架构采用状态机模式，清晰地分离了各个执行阶段。

## 核心组件

### 1. CardExecutionEngine
卡牌执行引擎，负责管理卡牌从出牌到结算完成的整个流程。

### 2. CardExecutionContext
卡牌执行上下文，包含一张卡牌的完整执行信息。

### 3. CardExecutionPhase
执行阶段枚举：
- TARGETING: 目标选择阶段
- NULLIFICATION: 无懈可击响应阶段  
- RESOLUTION: 卡牌结算阶段
- SPECIAL_EXECUTION: 特殊执行阶段（如五谷丰登选牌）
- COMPLETED: 执行完成

## 执行流程

### 标准锦囊牌流程（如五谷丰登）
```
1. 玩家出牌 (PlayCardNew) 
   ↓
2. 目标确认 → 创建执行上下文
   ↓  
3. 无懈可击响应阶段 (NULLIFICATION)
   ↓
4. 如果被无懈可击 → 执行完成
   如果没有被无懈可击 → 进入结算阶段
   ↓
5. 卡牌效果结算 (RESOLUTION)
   ↓
6. 如果需要特殊处理 → 特殊执行阶段 (SPECIAL_EXECUTION)
   如果不需要 → 执行完成
   ↓
7. 特殊执行完成 → 执行完成 (COMPLETED)
```

### 基本牌流程（如杀、闪）
```
1. 玩家出牌 (PlayCardNew)
   ↓
2. 直接进入结算阶段 (RESOLUTION)
   ↓
3. 执行完成 (COMPLETED)
```

## 新的API接口

### GameService 新方法
- `playCardNew()`: 新的出牌方法
- `respondToCardNew()`: 新的响应方法
- `continueCardExecution()`: 继续执行卡牌效果
- `getCurrentResponseTarget()`: 获取当前需要响应的玩家
- `getAvailableResponseOptions()`: 获取可用响应选项

### 新的消息类型
- `CardExecutionStarted`: 卡牌执行开始
- `ResponseRequired`: 需要响应
- `ResponseReceived`: 响应已接收
- `CardExecutionCompleted`: 卡牌执行完成
- `SpecialExecutionStarted`: 特殊执行开始

## 五谷丰登示例流程

1. **玩家A出五谷丰登**
   ```kotlin
   // 客户端发送
   PlayCardNew(playerId = "A", cardId = "五谷丰登", targetIds = listOf("B", "C", "D"))
   ```

2. **服务器广播执行开始**
   ```kotlin
   CardExecutionStarted(
       executionId = "exec_123",
       casterName = "玩家A", 
       cardName = "五谷丰登",
       targetNames = listOf("玩家B", "玩家C", "玩家D"),
       room = room
   )
   ```

3. **无懈可击响应阶段**
   ```kotlin
   // 发送给玩家B
   ResponseRequired(
       executionId = "exec_123",
       targetPlayerId = "B",
       responseType = "NULLIFICATION",
       originalCard = 五谷丰登卡牌,
       casterName = "玩家A"
   )
   ```

4. **玩家B选择不响应**
   ```kotlin
   // 客户端发送
   RespondToCardNew(playerId = "B", accept = false)
   ```

5. **进入特殊执行阶段**
   ```kotlin
   SpecialExecutionStarted(
       executionId = "exec_123",
       cardName = "五谷丰登",
       currentPlayerId = "A",
       currentPlayerName = "玩家A",
       availableOptions = listOf(
           ResponseOption("card1", "杀", "对目标造成1点伤害"),
           ResponseOption("card2", "闪", "抵消一次杀的效果")
       ),
       room = room
   )
   ```

6. **玩家依次选择卡牌直到完成**

## 优势

1. **清晰的状态管理**: 每个执行阶段都有明确的状态
2. **符合三国杀规则**: 严格按照无懈可击 → 效果执行的流程
3. **易于扩展**: 新卡牌只需要在对应阶段添加逻辑
4. **统一的消息流**: 所有卡牌使用相同的消息架构
5. **简化的WebSocket处理**: 减少特殊情况判断

## 兼容性

新架构与旧系统并存，可以逐步迁移：
- 旧系统继续使用 `PlayCard` 和相关旧消息
- 新系统使用 `PlayCardNew` 和新消息类型
- 客户端可以根据需要选择使用哪套系统

## 下一步

1. 更新客户端以支持新的消息类型
2. 逐步迁移现有卡牌到新系统
3. 添加更多特殊卡牌的支持（决斗、借刀杀人等）
4. 完善错误处理和超时机制