package moe.gensoukyo.tbc.shared.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 统一的日志系统
 */
object Logger {
    enum class Level(val value: Int, val tag: String) {
        DEBUG(1, "DEBUG"),
        INFO(2, "INFO"),
        WARN(3, "WARN"),
        ERROR(4, "ERROR")
    }
    
    private var currentLevel: Level = Level.INFO
    private var isEnabled: Boolean = true
    
    fun setLevel(level: Level) {
        currentLevel = level
    }
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
    
    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.DEBUG, tag, message, throwable)
    }
    
    fun info(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.INFO, tag, message, throwable)
    }
    
    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }
    
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }
    
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled || level.value < currentLevel.value) return
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val logMessage = buildString {
            append("$timestamp [${level.tag}] $tag: $message")
            throwable?.let { 
                append("\n")
                append(it.stackTraceToString())
            }
        }
        
        println(logMessage)
    }
}

/**
 * 扩展函数用于简化日志记录
 */
inline fun <reified T> T.logDebug(message: String, throwable: Throwable? = null) {
    Logger.debug(T::class.simpleName ?: "Unknown", message, throwable)
}

inline fun <reified T> T.logInfo(message: String, throwable: Throwable? = null) {
    Logger.info(T::class.simpleName ?: "Unknown", message, throwable)
}

inline fun <reified T> T.logWarn(message: String, throwable: Throwable? = null) {
    Logger.warn(T::class.simpleName ?: "Unknown", message, throwable)
}

inline fun <reified T> T.logError(message: String, throwable: Throwable? = null) {
    Logger.error(T::class.simpleName ?: "Unknown", message, throwable)
}

/**
 * 顶级日志函数，用于在无法获取类上下文的地方使用
 */
fun logDebug(message: String, throwable: Throwable? = null) {
    Logger.debug("Global", message, throwable)
}

fun logInfo(message: String, throwable: Throwable? = null) {
    Logger.info("Global", message, throwable)
}

fun logWarn(message: String, throwable: Throwable? = null) {
    Logger.warn("Global", message, throwable)
}

fun logError(message: String, throwable: Throwable? = null) {
    Logger.error("Global", message, throwable)
}