package com.junkfood.seal.util

import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.R
import com.junkfood.seal.util.PreferenceUtil.getString
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

private const val TAG = "TelegramUtil"
private const val BASE_URL = "https://api.telegram.org"
private const val MAX_BOT_API_SIZE_BYTES = 50L * 1024 * 1024

object TelegramUtil {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()

    suspend fun uploadFile(
        filePath: String,
        title: String,
        notificationId: Int,
        onProgress: (percent: Int) -> Unit = {},
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val token = TELEGRAM_BOT_TOKEN.getString()
            val chatId = TELEGRAM_CHAT_ID.getString()

            if (token.isBlank() || chatId.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        context.getString(R.string.telegram_not_configured)
                    )
                )
            }

            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("File not found: $filePath")
                )
            }

            if (file.length() > MAX_BOT_API_SIZE_BYTES) {
                return@withContext Result.failure(
                    IllegalStateException(context.getString(R.string.telegram_file_too_large))
                )
            }

            runCatching {
                val ext = file.extension.lowercase()
                val mediaType = guessMimeType(ext).toMediaTypeOrNull()
                val endpoint = guessEndpoint(ext)
                val fieldName = guessFieldName(ext)

                val requestBody =
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", chatId)
                        .addFormDataPart(
                            fieldName,
                            file.name,
                            file.asRequestBody(mediaType),
                        )
                        .build()

                val request =
                    Request.Builder()
                        .url("$BASE_URL/bot$token/$endpoint")
                        .post(requestBody)
                        .build()

                Log.d(TAG, "Uploading ${file.name} via $endpoint to chat $chatId")
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    throw Exception(
                        context.getString(R.string.telegram_api_error, response.code, body)
                    )
                }
                Log.d(TAG, "Upload successful for ${file.name}")
                Unit
            }
        }

    suspend fun testConnection(token: String, chatId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val getMeRequest =
                    Request.Builder().url("$BASE_URL/bot$token/getMe").build()
                val getMeResponse = client.newCall(getMeRequest).execute()
                if (!getMeResponse.isSuccessful) {
                    throw Exception(context.getString(R.string.telegram_invalid_token))
                }

                val msgBody =
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", chatId)
                        .addFormDataPart(
                            "text",
                            context.getString(R.string.telegram_test_message),
                        )
                        .build()
                val msgRequest =
                    Request.Builder()
                        .url("$BASE_URL/bot$token/sendMessage")
                        .post(msgBody)
                        .build()
                val msgResponse = client.newCall(msgRequest).execute()
                if (!msgResponse.isSuccessful) {
                    val body = msgResponse.body?.string() ?: ""
                    throw Exception(context.getString(R.string.telegram_invalid_chat, body))
                }
            }
        }

    private fun guessMimeType(ext: String): String =
        when (ext) {
            "mp4", "mkv", "webm", "mov", "avi" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }

    private fun guessEndpoint(ext: String): String =
        when (ext) {
            "mp4", "mov" -> "sendVideo"
            "mp3", "m4a", "opus", "flac", "wav", "ogg" -> "sendAudio"
            else -> "sendDocument"
        }

    private fun guessFieldName(ext: String): String =
        when (ext) {
            "mp4", "mov" -> "video"
            "mp3", "m4a", "opus", "flac", "wav", "ogg" -> "audio"
            else -> "document"
        }
}
