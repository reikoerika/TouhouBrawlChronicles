package moe.gensoukyo.tbc.server.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.time.Instant

fun Route.healthRoutes() {
    get("/health") {
        val healthStatus = HealthStatus(
            status = "UP",
            timestamp = Instant.now().toString(),
            service = "touhou-brawl-chronicles-server",
            version = "1.0.0",
            uptime = System.currentTimeMillis(),
            checks = mapOf(
                "websocket" to "UP",
                "game_service" to "UP"
            )
        )
        
        call.respond(HttpStatusCode.OK, healthStatus)
    }
    
    get("/") {
        call.respondText(
            contentType = ContentType.Text.Html,
            text = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>东方格斗编年史 - 服务器</title>
                    <meta charset="UTF-8">
                    <style>
                        body { 
                            font-family: Arial, sans-serif; 
                            margin: 40px; 
                            background: linear-gradient(135deg, #ff6b6b, #4ecdc4);
                            color: white;
                        }
                        .container { 
                            background: rgba(0,0,0,0.7); 
                            padding: 30px; 
                            border-radius: 10px;
                            max-width: 600px;
                            margin: 0 auto;
                        }
                        h1 { color: #FFD700; }
                        .status { color: #4CAF50; font-weight: bold; }
                        .endpoint { 
                            background: rgba(255,255,255,0.1); 
                            padding: 10px; 
                            margin: 10px 0; 
                            border-radius: 5px;
                        }
                        code { background: rgba(0,0,0,0.5); padding: 2px 6px; border-radius: 3px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🎮 东方格斗编年史服务器</h1>
                        <p class="status">✅ 服务器运行正常</p>
                        
                        <h3>API端点:</h3>
                        <div class="endpoint">
                            <strong>健康检查:</strong> <code>GET /health</code>
                        </div>
                        <div class="endpoint">
                            <strong>WebSocket游戏:</strong> <code>ws://localhost:8080/game</code>
                        </div>
                        
                        <h3>连接信息:</h3>
                        <p>🌐 HTTP服务: <code>http://localhost:8080</code></p>
                        <p>🔌 WebSocket: <code>ws://localhost:8080/game</code></p>
                        <p>⏰ 启动时间: ${Instant.now()}</p>
                        
                        <h3>客户端配置:</h3>
                        <p>Android模拟器: <code>ws://10.0.2.2:8080/game</code></p>
                        <p>局域网设备: <code>ws://[服务器IP]:8080/game</code></p>
                    </div>
                </body>
                </html>
            """.trimIndent()
        )
    }
}

@Serializable
data class HealthStatus(
    val status: String,
    val timestamp: String,
    val service: String,
    val version: String,
    val uptime: Long,
    val checks: Map<String, String>
)