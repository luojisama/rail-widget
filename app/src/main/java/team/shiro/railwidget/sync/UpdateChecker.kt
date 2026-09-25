package team.shiro.railwidget.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val hasUpdate: Boolean
)

data class MirrorNode(
    val name: String,
    val prefix: String
)

data class SpeedTestResult(
    val nodeName: String,
    val pingMs: Long,
    val fullUrl: String
)

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // 测速专用的轻量客户端，超短超时
    private val speedTestClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private const val GITHUB_REPO = "luojisama/rail-widget"
    private const val CURRENT_VERSION = "1.0.6"

    // 常用多线加速镜像节点
    val MIRROR_NODES = listOf(
        MirrorNode("ghfast 节点 (国内多线)", "https://ghfast.top/"),
        MirrorNode("ghproxy 节点 (高速镜像)", "https://ghproxy.net/"),
        MirrorNode("moeyy 节点 (香港/亚太)", "https://github.moeyy.xyz/"),
        MirrorNode("gh-proxy 节点 (备用容灾)", "https://gh-proxy.com/"),
        MirrorNode("GitHub 官方原链 (直连)", "")
    )

    /**
     * 将 Release Notes 的 Markdown 标记清洗转换为清晰自然的纯文本格式
     */
    fun formatReleaseNotes(raw: String): String {
        if (raw.isBlank()) return "暂无更新说明"
        val lines = raw.lines()
        val cleaned = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                cleaned.append("\n")
                continue
            }

            val formattedLine = when {
                trimmed.startsWith("####") -> {
                    "\n【" + trimmed.removePrefix("####").trim() + "】"
                }
                trimmed.startsWith("###") -> {
                    "\n■ " + trimmed.removePrefix("###").trim()
                }
                trimmed.startsWith("##") -> {
                    "\n■ " + trimmed.removePrefix("##").trim()
                }
                trimmed.startsWith("#") -> {
                    "\n■ " + trimmed.removePrefix("#").trim()
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    "  • " + trimmed.substring(2).trim()
                }
                trimmed.matches(Regex("^[0-9]+\\..*")) -> {
                    "  " + trimmed
                }
                else -> trimmed
            }
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")

            cleaned.append(formattedLine).append("\n")
        }

        return cleaned.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun getMirrorUrl(originalUrl: String): String {
        if (originalUrl.isBlank()) return originalUrl
        val candidate = MIRROR_NODES.firstOrNull { it.prefix.isNotBlank() }
        val prefix = candidate?.prefix ?: "https://ghfast.top/"
        if (MIRROR_NODES.any { it.prefix.isNotBlank() && originalUrl.startsWith(it.prefix) }) return originalUrl
        return "$prefix$originalUrl"
    }

    /**
     * 并发对所有候选线路进行 HTTP HEAD 探测，选出响应时间最短且可用的最快线路
     */
    suspend fun selectFastestMirror(originalDownloadUrl: String): SpeedTestResult = withContext(Dispatchers.IO) {
        if (originalDownloadUrl.isBlank()) {
            return@withContext SpeedTestResult("默认线路", 0, originalDownloadUrl)
        }

        val testJobs = MIRROR_NODES.map { node ->
            async {
                val fullUrl = if (node.prefix.isBlank()) originalDownloadUrl else "${node.prefix}$originalDownloadUrl"
                val ping = pingNode(fullUrl)
                SpeedTestResult(node.name, ping, fullUrl)
            }
        }

        val results = testJobs.awaitAll()

        // 优先选取有效响应（< 2500ms）且延迟最低的节点
        val available = results.filter { it.pingMs < 2500 }
        val best = available.minByOrNull { it.pingMs }
            ?: results.minByOrNull { it.pingMs }
            ?: SpeedTestResult("ghfast 节点 (国内多线)", 110, "${MIRROR_NODES[0].prefix}$originalDownloadUrl")

        best
    }

    private fun pingNode(url: String): Long {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "RailCard-SpeedTest")
                .build()

            speedTestClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in 200..399) {
                    System.currentTimeMillis() - start
                } else {
                    Long.MAX_VALUE
                }
            }
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    fun checkUpdate(): Result<UpdateInfo> {
        return try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "RailCard-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("检查更新失败: HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return Result.failure(Exception("空响应"))
                val json = JSONObject(body)

                val tagName = json.optString("tag_name").removePrefix("v").trim()
                val releaseNotes = json.optString("body")
                val htmlUrl = json.optString("html_url")

                var apkDownloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name")
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(tagName, CURRENT_VERSION)
                Result.success(
                    UpdateInfo(
                        latestVersion = tagName,
                        releaseNotes = releaseNotes,
                        downloadUrl = apkDownloadUrl,
                        htmlUrl = htmlUrl,
                        hasUpdate = hasUpdate
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadAndInstall(
        context: Context,
        originalDownloadUrl: String,
        preferredUrl: String? = null,
        onProgress: (Int) -> Unit
    ): Result<File> {
        val candidateUrls = mutableListOf<String>()
        if (!preferredUrl.isNullExempt()) {
            candidateUrls.add(preferredUrl!!)
        }

        // 加入所有备用镜像
        for (node in MIRROR_NODES) {
            val fullUrl = if (node.prefix.isBlank()) originalDownloadUrl else "${node.prefix}$originalDownloadUrl"
            if (!candidateUrls.contains(fullUrl)) {
                candidateUrls.add(fullUrl)
            }
        }

        var lastError: Exception? = null

        for (candidate in candidateUrls) {
            try {
                val res = downloadFromUrl(context, candidate, onProgress)
                if (res.isSuccess) {
                    val file = res.getOrThrow()
                    installApk(context, file)
                    return Result.success(file)
                } else {
                    lastError = res.exceptionOrNull() as? Exception
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        return Result.failure(lastError ?: Exception("所有线路下载均失败，请尝试网页下载"))
    }

    private fun String?.isNullExempt(): Boolean = this == null || this.isBlank()

    private fun downloadFromUrl(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): Result<File> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RailCard-Android-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
                val body = response.body ?: return Result.failure(Exception("下载内容为空"))

                val totalBytes = body.contentLength()
                val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                val apkFile = File(destDir, "RailCard-latest.apk")

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes: Long = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }
                Result.success(apkFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }

    fun openBrowserDownload(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)

        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
