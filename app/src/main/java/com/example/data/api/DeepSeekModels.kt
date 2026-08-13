package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class DeepSeekRequest(
    @Json(name = "model") val model: String = "deepseek-chat",
    @Json(name = "messages") val messages: List<ChatMessage>,
    @Json(name = "temperature") val temperature: Double = 0.2,
    @Json(name = "max_tokens") val maxTokens: Int = 4096
)

@JsonClass(generateAdapter = true)
data class DeepSeekChoice(
    @Json(name = "index") val index: Int = 0,
    @Json(name = "message") val message: ChatMessage
)

@JsonClass(generateAdapter = true)
data class DeepSeekResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "choices") val choices: List<DeepSeekChoice>? = null,
    @Json(name = "error") val error: DeepSeekError? = null
)

@JsonClass(generateAdapter = true)
data class DeepSeekError(
    @Json(name = "message") val message: String? = null,
    @Json(name = "type") val type: String? = null
)
