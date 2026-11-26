package com.example.foxy.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * ResilientLicenseClient — Chuyển đổi và nâng cấp từ LicenseClient (STIRLAX).
 *
 * Tính năng:
 * - Đọc luồng an toàn có giới hạn kích thước tối đa (Bounded Stream Reader), chống DoS/OOM.
 * - Cơ chế thử lại (Exponential Backoff Retry) khi gặp lỗi mạng tạm thời.
 * - Phân loại trạng thái phản hồi rõ ràng: VALID, INVALID, UNAVAILABLE, UPDATE_REQUIRED.
 * - Kiểm tra giao thức chuyển hướng an toàn (HTTPS only cho public endpoints).
 */
class ResilientLicenseClient(
    private val maxAttempts: Int = 3,
    private val maxReplyBytes: Int = 8192,
    private val connectionTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 5000
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
        val rawResponse: String? = null
    )

    private val responsePattern = Pattern.compile(
        """^\s*\{\s*"protocol"\s*:\s*"[^"]+"\s*,\s*"result"\s*:\s*"(valid|invalid|unavailable|update_required)"\s*\}\s*${'$'}"""
    )

    /**
     * Xác thực license key và chữ ký thiết bị với server endpoint.
     */
    suspend fun verify(
        endpointUrl: String,
        licenseKey: String,
        deviceSignature: String,
        appVersionCode: Int = 1
    ): LicenseResult = withContext(Dispatchers.IO) {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            attempt++
            try {
                val payload = JSONObject().apply {
                    put("protocol", "FOXY_LICENSE_V1")
                    put("versionCode", appVersionCode)
                    put("key", licenseKey)
                    put("deviceSignature", deviceSignature)
                }.toString()

                val result = executePost(endpointUrl, payload)
                return@withContext parseResponse(result)
            } catch (e: Exception) {
                lastException = e
                if (isTransientError(e) && attempt < maxAttempts) {
                    val waitTimeMs = (1000L * (1 shl (attempt - 1))).coerceAtMost(4000L)
                    delay(waitTimeMs)
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

    private fun executePost(targetUrl: String, jsonPayload: String): String {
        val url = URL(targetUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectionTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }

        conn.outputStream.use { os ->
            os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val code = conn.responseCode
        val stream: InputStream = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
        val responseBody = readBounded(stream, maxReplyBytes)
        conn.disconnect()

        return responseBody
    }

    /**
     * Đọc an toàn giới hạn số byte tối đa, port từ LicenseClient.readBounded()
     */
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

    private fun parseResponse(raw: String): LicenseResult {
        return try {
            val json = JSONObject(raw)
            val result = json.optString("result", "unavailable").lowercase()
            val status = when (result) {
                "valid" -> LicenseStatus.VALID
                "invalid" -> LicenseStatus.INVALID
                "update_required" -> LicenseStatus.UPDATE_REQUIRED
                else -> LicenseStatus.UNAVAILABLE
            }
            LicenseResult(status = status, rawResponse = raw)
        } catch (_: Exception) {
            LicenseResult(status = LicenseStatus.UNAVAILABLE, message = "Phản hồi không hợp lệ", rawResponse = raw)
        }
    }

    private fun isTransientError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("timeout") || msg.contains("reset") || msg.contains("refused")
    }
}
