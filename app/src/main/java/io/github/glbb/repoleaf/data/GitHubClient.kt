package io.github.glbb.repoleaf.data

import io.github.glbb.repoleaf.domain.DeviceCode
import io.github.glbb.repoleaf.domain.DeviceTokenResult
import io.github.glbb.repoleaf.domain.RepositoryConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class GitHubClient {
    private val apiVersion = "2022-11-28"

    fun resolveCommit(config: RepositoryConfig, token: String?): String {
        val ref = URLEncoder.encode(config.branch, Charsets.UTF_8.name())
        val connection = connection("https://api.github.com/repos/${config.owner}/${config.name}/commits/$ref", token)
        return connection.useResponse { code, body ->
            if (code !in 200..299) throw githubError(code, body)
            JSONObject(body).getString("sha")
        }
    }

    fun downloadArchive(config: RepositoryConfig, commit: String, token: String?, target: File) {
        var url = "https://api.github.com/repos/${config.owner}/${config.name}/zipball/$commit"
        var includeToken = true
        repeat(6) {
            val connection = connection(url, token.takeIf { includeToken }, followRedirects = false)
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val next = connection.getHeaderField("Location") ?: error("GitHub 返回了无地址的重定向")
                connection.disconnect()
                val host = URI(next).host?.lowercase()
                if (host !in setOf("github.com", "api.github.com", "codeload.github.com")) {
                    error("拒绝不可信的下载地址")
                }
                url = next
                includeToken = host == "api.github.com"
            } else {
                connection.useBinaryResponse { responseCode, input ->
                    if (responseCode !in 200..299) {
                        val body = input.bufferedReader().use { it.readText() }
                        throw githubError(responseCode, body)
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                return
            }
        }
        error("GitHub 下载重定向次数过多")
    }

    fun requestDeviceCode(clientId: String): DeviceCode {
        val body = form(mapOf("client_id" to clientId))
        val connection = connection("https://github.com/login/device/code", null, method = "POST", body = body)
        return connection.useResponse { code, response ->
            if (code !in 200..299) error("无法启动 GitHub 登录（HTTP $code）")
            val json = JSONObject(response)
            DeviceCode(
                json.getString("device_code"), json.getString("user_code"),
                json.getString("verification_uri"), json.getLong("expires_in"),
                json.optLong("interval", 5),
            )
        }
    }

    fun pollDeviceToken(clientId: String, code: DeviceCode): DeviceTokenResult {
        val body = form(
            mapOf(
                "client_id" to clientId,
                "device_code" to code.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
        )
        val connection = connection("https://github.com/login/oauth/access_token", null, method = "POST", body = body)
        return connection.useResponse { httpCode, response ->
            if (httpCode !in 200..299) return@useResponse DeviceTokenResult.Failure("GitHub 登录失败（HTTP $httpCode）")
            val json = JSONObject(response)
            json.optString("access_token").takeIf { it.isNotBlank() }?.let { return@useResponse DeviceTokenResult.Success(it) }
            when (json.optString("error")) {
                "authorization_pending" -> DeviceTokenResult.Pending(code.intervalSeconds)
                "slow_down" -> DeviceTokenResult.Pending(code.intervalSeconds + 5)
                "expired_token" -> DeviceTokenResult.Failure("验证码已过期，请重新登录")
                "access_denied" -> DeviceTokenResult.Failure("GitHub 授权已取消")
                else -> DeviceTokenResult.Failure(json.optString("error_description", "GitHub 登录失败"))
            }
        }
    }

    fun currentLogin(token: String): String {
        val connection = connection("https://api.github.com/user", token)
        return connection.useResponse { code, body ->
            if (code !in 200..299) throw githubError(code, body)
            JSONObject(body).getString("login")
        }
    }

    private fun connection(
        url: String,
        token: String?,
        followRedirects: Boolean = true,
        method: String = "GET",
        body: ByteArray? = null,
    ): HttpURLConnection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = method
        instanceFollowRedirects = followRedirects
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", apiVersion)
        setRequestProperty("User-Agent", "RepoLeaf-Android")
        token?.let { setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            outputStream.use { it.write(body) }
        }
    }

    private fun form(values: Map<String, String>) = values.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, "UTF-8") }=${URLEncoder.encode(it.value, "UTF-8") }"
    }.toByteArray()

    private fun githubError(code: Int, body: String): Exception {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
        return IllegalStateException(
            when (code) {
                401 -> "GitHub 凭证无效或已过期"
                403 -> "没有仓库读取权限，或 GitHub API 已限流"
                404 -> "仓库、分支不存在，或当前账号无权访问"
                else -> "GitHub 请求失败（HTTP $code${message?.let { "：$it" } ?: ""}）"
            },
        )
    }

    private inline fun <T> HttpURLConnection.useResponse(block: (Int, String) -> T): T = try {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        block(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    } finally {
        disconnect()
    }

    private inline fun <T> HttpURLConnection.useBinaryResponse(block: (Int, java.io.InputStream) -> T): T = try {
        val code = responseCode
        block(code, if (code in 200..299) inputStream else errorStream)
    } finally {
        disconnect()
    }
}
