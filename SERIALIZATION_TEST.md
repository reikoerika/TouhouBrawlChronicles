# 序列化测试验证

## 问题解决

原问题：`Map<String, Any>`不能在Kotlin序列化中使用，因为`Any`类型无法确定序列化策略。

## 解决方案

创建了类型安全的`SpecialExecutionData`数据类，替代`Map<String, Any>`：

```kotlin
@Serializable
data class SpecialExecutionData(
    // 五谷丰登相关数据
    val abundantHarvestCards: List<Card> = emptyList(),
    val abundantHarvestPlayerOrder: List<String> = emptyList(),
    val abundantHarvestCurrentIndex: Int = 0,
    
    // 决斗相关数据
    val duelKillCount: Int = 0,
    val duelInitiator: String = "",
    val duelTarget: String = "",
    
    // 扩展字段
    val customStringData: Map<String, String> = emptyMap(),
    val customIntData: Map<String, Int> = emptyMap(),
    val customBooleanData: Map<String, Boolean> = emptyMap()
)
```

## 优势

1. **类型安全**：编译时就能检查类型错误
2. **可序列化**：完全支持Kotlin序列化
3. **结构清晰**：每种特殊卡牌都有明确的数据字段
4. **易于扩展**：可以轻松添加新的特殊卡牌数据
5. **性能更好**：避免了类型转换和运行时检查

## 使用示例

### 五谷丰登数据操作

```kotlin
// 设置五谷丰登数据
context.specialData = context.specialData.copy(
    abundantHarvestCards = availableCards,
    abundantHarvestPlayerOrder = alivePlayers.map { it.id },
    abundantHarvestCurrentIndex = 0
)

// 读取五谷丰登数据
val availableCards = context.specialData.abundantHarvestCards
val currentIndex = context.specialData.abundantHarvestCurrentIndex
val playerOrder = context.specialData.abundantHarvestPlayerOrder
```

### 决斗数据操作

```kotlin
// 设置决斗数据
context.specialData = context.specialData.copy(
    duelKillCount = 2,
    duelInitiator = "player1",
    duelTarget = "player2"
)
```

## 迁移完成

所有使用`Map<String, Any>`的地方都已经更新为使用新的`SpecialExecutionData`：

- ✅ CardExecutionEngine.setupAbundantHarvest()
- ✅ CardExecutionEngine.handleAbundantHarvestSelection()
- ✅ CardExecutionEngine.getSpecialResponseTarget()
- ✅ GameService.getAvailableResponseOptions()
- ✅ CardExecutionHandler.handleSpecialExecutionPhase()

新架构现在完全支持序列化，可以安全地在网络上传输和存储。