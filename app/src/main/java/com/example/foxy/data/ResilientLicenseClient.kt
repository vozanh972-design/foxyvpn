package com.example.foxy.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client xác thực key trực tiếp qua API server Lunex (`https://lunex.io.vn/api/check.php`).
 * App không tự quyết định key hợp lệ — mọi kết quả đều phụ thuộc phản hồi từ server.
 */
class ResilientLicenseClient(
    private val maxAttempts: Int = 2,
    private val maxReplyBytes: Int = 16384,
    private val connectionTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 8000
) {

    enum class LicenseStatus {
        VALID,
        INVALID,
        UNAVAILABLE,
        UPDATE_REQUIRED
    }

    data class LicenseResult(
        val status: LicenseStatus,
        val message: String? = null,
        val key: String = "",
        val type: String = "free",
        val expiresAt: String = "",
        val deviceId: String = "",
        val rawResponse: String? = null
    )

    companion object {
        const val GET_KEY_URL = "https://lunex.io.vn/api/mykey.php"
        const val CHECK_KEY_URL = "https://lunex.io.vn/api/check.php"
    }

    /**
     * Gửi `key` và `device_id` lên `check.php`.
     * Chỉ trả về `VALID` khi server phản hồi HTTP 200 kèm `"status":"success"` và `"result":"OK"`.
     */
    suspend fun verify(
        endpointUrl: String = CHECK_KEY_URL,
        licenseKey: String,
        deviceSignature: String,
    ): LicenseResult = withContext(Dispatchers.IO) {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            attempt++
            try {
                val payload = JSONObject().apply {
                    put("key", licenseKey.trim())
                    put("device_id", deviceSignature)
                }.toString()

                val (httpCode, body) = executePost(endpointUrl, payload)
                return@withContext parseResponse(httpCode, body, licenseKey.trim())
            } catch (e: Exception) {
                lastException = e
                if (isTransientError(e) && attempt < maxAttempts) {
                    delay(800L)
                } else {
                    break
                }
            }
        }

        LicenseResult(
            status = LicenseStatus.UNAVAILABLE,
            message = lastException?.localizedMessage ?: "Không thể kết nối đến máy chủ xác thực"
        )
    }

    private fun executePost(targetUrl: String, jsonPayload: String): Pair<Int, String> {
        val url = URL(targetUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectionTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FoxyVPN-Android/1.0")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }

        conn.outputStream.use { os ->
            os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val code = conn.responseCode
        val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
        val responseBody = if (stream != null) readBounded(stream, maxReplyBytes) else ""
        conn.disconnect()

        return code to responseBody
    }

    private fun readBounded(stream: InputStream, maxBytes: Int): String {
        val buffer = ByteArray(1024)
        val out = ByteArrayOutputStream()
        var totalRead = 0
        var bytesRead: Int

        while (stream.read(buffer).also { bytesRead = it } != -1) {
            totalRead += bytesRead
            if (totalRead > maxBytes) {
                out.write(buffer, 0, bytesRead - (totalRead - maxBytes))
                break
            }
            out.write(buffer, 0, bytesRead)
        }

        return out.toString("UTF-8")
    }

    private fun parseResponse(httpCode: Int, raw: String, fallbackKey: String): LicenseResult {
        return try {
            val json = JSONObject(raw)
            val statusStr = json.optString("status", "").lowercase()
            val resultStr = json.optString("result", "")
            val message = json.optString("message", "").ifBlank { null }

            if (httpCode == 200 && statusStr == "success" && resultStr.equals("OK", ignoreCase = true)) {
                LicenseResult(
                    status = LicenseStatus.VALID,
                    message = message ?: "OK",
                    key = json.optString("key", fallbackKey).ifBlank { fallbackKey },
                    type = json.optString("type", "free").ifBlank { "free" },
                    expiresAt = json.optString("expires_at", "").ifBlank { "" },
                    deviceId = json.optString("device_id", ""),
                    rawResponse = raw,
                )
            } else {
                LicenseResult(
                    status = LicenseStatus.INVALID,
                    message = message ?: "Key không hợp lệ (mã $httpCode)",
                    rawResponse = raw,
                )
            }
        } catch (_: Exception) {
            LicenseResult(
                status = LicenseStatus.UNAVAILABLE,
                message = "Phản hồi từ máy chủ không hợp lệ (HTTP $httpCode)",
                rawResponse = raw,
            )
        }
    }

    private fun isTransientError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("timeout") || msg.contains("reset") || msg.contains("refused")
    }
}
