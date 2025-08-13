# 模块架构调整说明

## 架构原则

### Shared模块职责
- **仅包含真正共享的代码**
- 消息定义 (`ClientMessage`, `ServerMessage`)
- 数据模型 (`GameRoom`, `Player`, `Card`等)
- 通用工具类 (`Logger`, `SafeJson`)
- 接口定义 (`MessageHandlers`)

### Client模块职责  
- **客户端特定的实现**
- CLI客户端连接管理
- 客户端WebSocket管理器
- 客户端特有的工具类

### Server模块职责
- **服务端特定的实现** 
- 服务端WebSocket管理器
- 游戏逻辑处理
- 服务端特有的工具类

### Android App模块职责
- **Android特定的实现**
- Android UI组件
- Android特有的连接管理
- 平台相关的工具类

## 调整后的文件分布

### Shared模块 (`shared/`)
```
src/main/kotlin/moe/gensoukyo/tbc/shared/
├── messages/          # 消息定义
├── model/             # 数据模型  
├── card/              # 卡牌执行上下文
└── utils/
    ├── Logger.kt      # 通用日志系统
    ├── SafeJson.kt    # 安全序列化工具
    └── MessageHandlers.kt  # 消息处理接口
```

### Client模块 (`client/`)
```
src/main/kotlin/moe/gensoukyo/tbc/client/
├── service/           # 客户端服务
└── utils/
    └── ClientWebSocketManager.kt  # CLI客户端连接管理
```

### Server模块 (`server/`)
```
src/main/kotlin/moe/gensoukyo/tbc/server/
├── websocket/         # WebSocket处理器
├── service/           # 游戏服务
└── utils/
    └── SafeWebSocketManager.kt    # 服务端连接管理
```

### Android App模块 (`app/`)
```
src/main/java/moe/gensoukyo/tbc/
├── viewmodel/         # Android ViewModels
├── ui/                # Android UI组件
└── utils/             # Android特有工具
    └── AndroidWebSocketManager.kt  # Android连接管理
```

## 重构要点

1. **避免平台特定代码混入Shared模块**
   - WebSocket管理器分别在各自平台实现
   - UI相关逻辑仅在对应平台模块

2. **保持接口一致性**
   - 各平台的连接管理器实现相同的核心接口
   - 消息处理模式保持统一

3. **依赖方向正确**
   - Client/Server/App模块依赖Shared
   - Shared模块不依赖任何平台特定代码

4. **代码复用最大化**
   - 通用逻辑在Shared中实现
   - 平台差异通过接口抽象

这种架构确保了：
- ✅ 代码职责清晰
- ✅ 平台特定功能隔离
- ✅ 最大化代码复用
- ✅ 便于维护和扩展