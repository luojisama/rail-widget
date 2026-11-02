package team.shiro.railwidget.ui.components

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import team.shiro.railwidget.data.local.TripDatabaseHelper
import team.shiro.railwidget.island.IslandPermissionHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
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
    val exact = remember(revision) { IslandPermissionHelper.canScheduleExactAlarms(context) }
    val notifications = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val battery = remember(revision) { IslandPermissionHelper.isIgnoringBatteryOptimizations(context) }
    val db = TripDatabaseHelper.getInstance(context)

    Column(Modifier.fillMaxWidth()) {
        Text("权限状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "悬浮窗权限：${if (overlay) "已授权" else "未授权"}\n" +
                    "精确闹钟权限：${if (exact) "已授权" else "未授权，定时可能延迟"}\n" +
                    "通知权限：${if (notifications) "已开启" else "未开启"}\n" +
                    "电池优化：${if (battery) "已允许后台无限制" else "受系统省电限制"}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(onClick = { IslandPermissionHelper.openOverlaySettings(context) }) {
                Text("悬浮窗与后台弹出")
            }
            if (!exact) {
                OutlinedButton(onClick = { IslandPermissionHelper.requestExactAlarmPermission(context) }) {
                    Text("允许精确闹钟")
                }
            }
            if (!notifications) {
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && context is Activity) {
                        ActivityCompat.requestPermissions(context, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 20042)
                    } else {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Text("开启通知权限")
                }
            }
            if (!battery) {
                OutlinedButton(onClick = { IslandPermissionHelper.requestIgnoreBatteryOptimizations(context) }) {
                    Text("省电策略设为无限制")
                }
            }
            OutlinedButton(onClick = { IslandPermissionHelper.openMiuiAutostartSettings(context) }) {
                Text("MIUI 自启动管理")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "小米/澎湃系统请开启「自启动」、「后台弹出界面」，并将省电策略设为「无限制」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val alarmStatus = db.getSetting("island_alarm_status")
        val startStatus = db.getSetting("island_start_status")
        val nextAlarm = db.getSetting("island_next_alarm_millis").toLongOrNull()
        val nextAlarmText = if (nextAlarm != null && nextAlarm > 0) {
            "下次待唤起时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(nextAlarm))
        } else null

        val diagnosticLines = listOfNotNull(
            nextAlarmText
        )
        if (diagnosticLines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = diagnosticLines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
