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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val GITHUB_REPO = "luojisama/rail-widget"
    private const val CURRENT_VERSION = "1.0.0"

    fun checkUpdate(): Result<UpdateInfo> {
        return try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "RailWidget-Android")
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
        onProgress: (Int) -> Unit
    ): Result<File> {
        return try {
            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(Exception("下载安装包失败: ${response.code}"))
                val body = response.body ?: return Result.failure(Exception("下载内容为空"))

                val totalBytes = body.contentLength()
                val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                val apkFile = File(destDir, "rail-widget-latest.apk")

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

                // Launch installer
                installApk(context, apkFile)
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
