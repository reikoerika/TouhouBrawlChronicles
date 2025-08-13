package moe.gensoukyo.tbc.shared.utils

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 安全的序列化工具类
 */
object SafeJson {
    val jsonInstance = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }
    
    /**
     * 安全地序列化对象
     */
    inline fun <reified T> encodeToString(value: T): Result<String> {
        return try {
            val result = jsonInstance.encodeToString(value)
            Logger.debug("SafeJson", "Encoded ${T::class.simpleName}: ${result.take(200)}${if(result.length > 200) "..." else ""}")
            Result.success(result)
        } catch (e: SerializationException) {
            val error = "Failed to serialize ${T::class.simpleName}: ${e.message}"
            Logger.error("SafeJson", error, e)
            Result.failure(SerializationError(error, e))
        } catch (e: Exception) {
            val error = "Unexpected error during serialization of ${T::class.simpleName}: ${e.message}"
            Logger.error("SafeJson", error, e)
            Result.failure(SerializationError(error, e))
        }
    }
    
    /**
     * 安全地反序列化对象
     */
    inline fun <reified T> decodeFromString(string: String): Result<T> {
        return try {
            Logger.debug("SafeJson", "Decoding ${T::class.simpleName}: ${string.take(200)}${if(string.length > 200) "..." else ""}")
            val result = jsonInstance.decodeFromString<T>(string)
            Result.success(result)
        } catch (e: SerializationException) {
            val error = "Failed to deserialize ${T::class.simpleName}: ${e.message}"
            Logger.error("SafeJson", error, e)
            Result.failure(SerializationError(error, e))
        } catch (e: Exception) {
            val error = "Unexpected error during deserialization of ${T::class.simpleName}: ${e.message}"
            Logger.error("SafeJson", error, e)
            Result.failure(SerializationError(error, e))
        }
    }
    
    /**
     * 安全地从JsonElement反序列化
     */
    inline fun <reified T> decodeFromJsonElement(element: JsonElement): Result<T> {
        return try {
            val result = jsonInstance.decodeFromJsonElement<T>(element)
            Logger.debug("SafeJson", "Decoded ${T::class.simpleName} from JsonElement")
            Result.success(result)
        } catch (e: SerializationException) {
            val error = "Failed to deserialize ${T::class.simpleName} from JsonElement: ${e.message}"
            Logger.error("SafeJson", error, e)
            Result.failure(SerializationError(error, e))
        } catch (e: Exception) {
            val error = "Unexpected error during JsonElement deserialization of ${T::class.simpleName}: ${e.message}"
            Logger.error("SafeJson", error, e)
            Result.failure(SerializationError(error, e))
        }
    }
}

/**
 * 序列化错误类
 */
class SerializationError(message: String, cause: Throwable? = null) : Exception(message, cause)