package team.shiro.railwidget.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import team.shiro.railwidget.ui.MainActivity

/** Uses platform views so a failure in the normal Compose screen cannot hide its report. */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashDiagnostics.read(this).orEmpty()
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(this).apply {
            text = "检测到启动异常"
            textSize = 22f
            setTextColor(0xFF0284C7.toInt())
        })
        content.addView(TextView(this).apply {
            text = "行程和设置仍保留在手机上。复制下面的错误信息，即可继续排查。"
            textSize = 15f
            setPadding(0, padding, 0, padding)
        })
        content.addView(Button(this).apply {
            text = "复制错误信息"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("铁行卡片崩溃信息", report))
                Toast.makeText(this@CrashReportActivity, "已复制", Toast.LENGTH_SHORT).show()
            }
        })
        content.addView(Button(this).apply {
            text = "保留数据并重试"
            setOnClickListener {
                CrashDiagnostics.clear(this@CrashReportActivity)
                startActivity(Intent(this@CrashReportActivity, MainActivity::class.java))
                finish()
            }
        })
        content.addView(ScrollView(this).apply {
            addView(TextView(this@CrashReportActivity).apply {
                text = report
                textSize = 12f
                setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(content)
    }
}
