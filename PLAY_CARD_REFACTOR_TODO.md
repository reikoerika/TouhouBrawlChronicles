# 出牌架构重构 TODO 列表

## 🚀 阶段一：消息层统一 (优先级：高)

### 1.1 消息类型合并
- [ ] **合并ClientMessage出牌消息**
  - [ ] 移除 `UseCard` 消息类型
  - [ ] 移除 `PlayCardNew` 消息类型  
  - [ ] 统一使用 `PlayCard(playerId, cardId, targetIds)`
  - [ ] 更新序列化注解和测试

- [ ] **合并响应消息**
  - [ ] 移除 `RespondToCardNew` 消息类型
  - [ ] 统一使用 `RespondToCard(playerId, responseCardId?, accept)`
  - [ ] 确保向后兼容性

- [ ] **清理CardExecutionMessages.kt**
  - [ ] 评估 `PlayCardMessage` 是否需要保留
  - [ ] 移除重复的消息定义
  - [ ] 更新导入引用

### 1.2 消息迁移策略
- [ ] **创建消息兼容层**
  - [ ] 实现旧消息到新消息的转换器
  - [ ] 添加版本控制支持
  - [ ] 创建渐进式迁移机制

## 🔧 阶段二：服务层重构 (优先级：高)

### 2.1 GameService API统一
- [ ] **移除重复的出牌方法**
  - [ ] 删除 `useCard(roomId, playerId, cardId, targetId)` 
  - [ ] 删除 `playCardNew(roomId, playerId, cardId, targetIds)`
  - [ ] 保留并重构 `playCard(roomId, playerId, cardId, targetIds)`

- [ ] **标准化返回值类型**
  - [ ] 统一使用 `CardExecutionResult` 类型
  - [ ] 移除 `Pair<PlayedCard?, PendingResponse?>?` 返回值
  - [ ] 实现 Result<T> 错误处理模式

- [ ] **整合执行引擎**
  - [ ] 确保所有卡牌都通过 `CardExecutionEngine` 执行
  - [ ] 移除特殊卡牌的硬编码逻辑
  - [ ] 统一五谷丰登的处理流程

### 2.2 错误处理统一
- [ ] **标准化异常处理**
  - [ ] 创建统一的 `CardExecutionException` 体系
  - [ ] 实现详细的错误日志记录
  - [ ] 添加错误恢复机制

## 🌐 阶段三：WebSocket层整合 (优先级：高)

### 3.1 GameWebSocket重构
- [ ] **移除多套出牌处理逻辑**
  - [ ] 删除 `is ClientMessage.UseCard` 分支
  - [ ] 删除 `is ClientMessage.PlayCardNew` 分支
  - [ ] 简化 `is ClientMessage.PlayCard` 分支逻辑

- [ ] **整合CardExecutionHandler**
  - [ ] 将 `CardExecutionHandler` 功能合并到 `GameWebSocket`
  - [ ] 移除独立的handler实例创建
  - [ ] 统一错误处理流程

### 3.2 连接管理统一
- [ ] **解决会话映射问题**
  - [ ] 修复 `GameService.getPlayerSession()` 总是返回null的问题
  - [ ] 统一 `playerSessions` 和 `spectatorSessions` 的管理
  - [ ] 移除 `CardExecutionHandler` 中的独立 `SafeWebSocketManager`

- [ ] **创建统一连接管理器**
  - [ ] 实现 `UnifiedConnectionManager` 类
  - [ ] 集中管理所有WebSocket连接
  - [ ] 提供统一的广播和单播API

## 📱 阶段四：客户端适配 (优先级：中)

### 4.1 Android ViewModel统一
- [ ] **合并ViewModel类**
  - [ ] 决定保留 `GameViewModel` 还是 `ImprovedGameViewModel`
  - [ ] 移除重复的出牌方法
  - [ ] 统一状态管理模式

- [ ] **更新UI层调用**
  - [ ] 修改 `GameScreen.kt` 中的方法调用
  - [ ] 确保UI响应逻辑一致
  - [ ] 更新错误显示机制

### 4.2 CLI客户端适配
- [ ] **重构GameClient出牌方法**
  - [ ] 移除 `useCard()` 和重复的 `playCard()` 方法
  - [ ] 统一用户输入处理
  - [ ] 更新命令行界面

## 🧪 阶段五：测试和清理 (优先级：中)

### 5.1 测试覆盖
- [ ] **单元测试更新**
  - [ ] 更新 `GameService` 测试用例
  - [ ] 添加统一出牌流程测试
  - [ ] 测试错误处理场景

- [ ] **集成测试**
  - [ ] 测试所有卡牌类型的完整流程
  - [ ] 验证五谷丰登功能正常
  - [ ] 测试多人响应场景

### 5.2 性能测试
- [ ] **基准测试**
  - [ ] 建立出牌性能基准
  - [ ] 测试并发连接处理
  - [ ] 监控内存使用情况

### 5.3 代码清理
- [ ] **移除废弃代码**
  - [ ] 删除未使用的消息类型
  - [ ] 清理导入语句
  - [ ] 移除注释掉的代码

- [ ] **文档更新**
  - [ ] 更新API文档
  - [ ] 修正代码注释
  - [ ] 更新架构图

## 🔄 持续优化 (优先级：低)

### 6.1 性能优化
- [ ] **消息序列化优化**
  - [ ] 评估消息大小优化
  - [ ] 实现消息压缩
  - [ ] 优化频繁操作的性能

### 6.2 功能扩展准备
- [ ] **新卡牌支持框架**
  - [ ] 设计插件化卡牌系统
  - [ ] 实现卡牌热加载机制
  - [ ] 创建卡牌开发工具

## ⚠️ 关键风险缓解

### 风险监控
- [ ] **功能回归测试**
  - [ ] 创建自动化测试套件
  - [ ] 实现回滚机制
  - [ ] 建立监控告警

- [ ] **兼容性保证**
  - [ ] 维护API版本控制
  - [ ] 实现平滑升级路径
  - [ ] 提供迁移指南

## 📊 成功验收标准

### 代码质量指标
- [ ] 出牌相关代码减少 ≥ 40%
- [ ] 消息类型数量减少 ≥ 50%
- [ ] 单元测试覆盖率 ≥ 90%

### 功能验收
- [ ] 所有现有卡牌功能正常
- [ ] 五谷丰登完整流程无问题
- [ ] 会话丢失问题完全解决

### 性能验收  
- [ ] 出牌响应时间 ≤ 100ms
- [ ] 支持并发连接 ≥ 100
- [ ] 内存使用优化 ≥ 20%

---

## 📋 任务分配建议

**第1周：** 阶段一和阶段二 (消息层+服务层)
**第2周：** 阶段三 (WebSocket层整合)  
**第3周：** 阶段四和阶段五 (客户端+测试)
**第4周：** 持续优化和文档完善

**总预计时间：** 3-4周
**核心开发人员：** 2-3人
**测试人员：** 1人