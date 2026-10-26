package team.shiro.railwidget.diagnostics

import android.content.Context
import android.os.Build
import team.shiro.railwidget.BuildConfig
import java.io.File

/** Private, local crash reports. Nothing is uploaded or sent automatically. */
object CrashDiagnostics {
    private const val REPORT = "last-startup-crash.txt"
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(context, thread.name, error)
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun record(context: Context, thread: String, error: Throwable) {
        runCatching {
            val report = buildString {
                appendLine("铁行卡片 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Thread: ${thread.take(80)}")
                var cause: Throwable? = error
                repeat(5) {
                    val current = cause ?: return@repeat
                    appendLine("${current.javaClass.name}: ${current.message.orEmpty().take(1000)}")
                    current.stackTrace.take(45).forEach { appendLine("    at $it") }
                    cause = current.cause?.takeUnless { it === current }
                    if (cause != null) appendLine("Caused by:")
                }
            }
            File(context.filesDir, REPORT).writeText(report.take(32_000))
        }
    }

    fun read(context: Context): String? = runCatching {
        File(context.filesDir, REPORT).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        File(context.filesDir, REPORT).delete()
    }
}
