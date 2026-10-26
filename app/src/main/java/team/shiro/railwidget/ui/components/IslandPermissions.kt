package team.shiro.railwidget.ui.components

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.island.IslandPermissionHelper

@Composable
fun IslandPermissions() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val overlay = remember(revision) { IslandPermissionHelper.hasOverlayPermission(context) }
    val exact = remember(revision) { Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val notifications = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val db = TripDatabaseHelper.getInstance(context)
    fun open(intent: Intent) { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Column(Modifier.fillMaxWidth()) {
        Text("自动唤起权限", style = MaterialTheme.typography.titleSmall)
        Text("精确闹钟：${if (exact) "已授权" else "未授权，定时可能延迟"}\n悬浮窗：${if (overlay) "已授权" else "未授权"}\n通知：${if (notifications) "已开启" else "未开启"}",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }, enabled = !exact) { Text("允许精确闹钟") }
        TextButton(onClick = { IslandPermissionHelper.openOverlaySettings(context) }) { Text("悬浮窗与后台弹出权限") }
        TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33 && !notifications && context is Activity) {
                ActivityCompat.requestPermissions(context, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 20042)
            } else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("通知权限") }
        Text("MIUI 14：请允许自启动、后台弹出界面，并将省电策略设为无限制。系统强行停止应用后，需要重新打开应用恢复调度。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            val intent = Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            try { context.startActivity(intent) } catch (_: Exception) {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }) { Text("自启动与省电设置") }
        val diagnostic = listOf(db.getSetting("island_alarm_status"), db.getSetting("island_start_status")).filter { it.isNotBlank() }.joinToString("\n")
        if (diagnostic.isNotBlank()) Text(diagnostic, style = MaterialTheme.typography.bodySmall)
    }
}
