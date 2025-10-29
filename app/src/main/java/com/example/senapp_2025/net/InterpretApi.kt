package com.senapp.net

import com.senapp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log

@Serializable
data class TokenDto(
    @SerialName("signId") val signId: String,
    @SerialName("tStart") val tStart: Long = 0,
    @SerialName("tEnd")   val tEnd: Long = 0,
    @SerialName("conf")   val conf: Float = 1f
)

@Serializable
data class InterpretRequestDto(
    @SerialName("tokens") val tokens: List<TokenDto>
)

@Serializable
data class InterpretResponseDto(
    val text: String,
    val alt: List<String> = emptyList(),
    val confidence: Float = 0f
)

object InterpretApi {
    /** Por defecto toma lo definido en build.gradle.kts (INTERPRET_BASE_URL) */
    var BASE_URL: String = BuildConfig.INTERPRET_BASE_URL

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            )
        }
        install(Logging) { level = LogLevel.INFO }
        expectSuccess = false
    }

    suspend fun interpret(tokens: List<TokenDto>): InterpretResponseDto {
        Log.i("InterpretApi", "POST -> $BASE_URL/interpret")
        val req = InterpretRequestDto(tokens)
        return client.post("$BASE_URL/interpret") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    /** Ping simple al GET "/" para verificar conectividad */
    suspend fun ping(): Boolean {
        return try {
            Log.i("InterpretApi", "GET -> $BASE_URL/")
            val res = client.get("$BASE_URL/")
            Log.i("InterpretApi", "Ping status=${res.status.value}")
            res.status.value in 200..299
        } catch (e: Exception) {
            Log.w("InterpretApi", "Ping failed: ${e.message}")
            false
        }
    }
}
