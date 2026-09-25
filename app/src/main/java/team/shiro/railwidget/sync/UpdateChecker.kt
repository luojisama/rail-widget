package team.shiro.railwidget.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
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

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val GITHUB_REPO = "luojisama/rail-widget"
    private const val CURRENT_VERSION = "1.0.2"

    // 国内高速 GitHub 加速镜像源列表
    val MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://ghproxy.net/",
        "https://github.moeyy.xyz/"
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
        if (MIRROR_PREFIXES.any { originalUrl.startsWith(it) }) return originalUrl
        return "${MIRROR_PREFIXES[0]}$originalUrl"
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
        downloadUrl: String,
        useMirror: Boolean = true,
        onProgress: (Int) -> Unit
    ): Result<File> {
        val candidateUrls = mutableListOf<String>()
        if (useMirror && downloadUrl.startsWith("http")) {
            for (prefix in MIRROR_PREFIXES) {
                candidateUrls.add("$prefix$downloadUrl")
            }
        }
        candidateUrls.add(downloadUrl)

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

    fun openBrowserDownload(context: Context, url: String, useMirror: Boolean = false) {
        val finalUrl = if (useMirror) getMirrorUrl(url) else url
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
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
