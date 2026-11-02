package team.shiro.railwidget.island

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object IslandPermissionHelper {

    /**
     * 检查是否已授予应用悬浮窗（上层绘制）权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * 检查是否已具有精确闹钟调度权限 (Android 12+ / API 31+)
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(AlarmManager::class.java)
            am?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    /**
     * 引导用户前往精确闹钟授权页
     */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
    }

    /**
     * 检查是否已加入电池优化白名单（无限制模式）
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * 请求将应用加入电池优化白名单（免除低电耗与待机杀后台限制）
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(listIntent)
                } catch (_: Exception) {
                    openAppSettings(context)
                }
            }
        }
    }

    /**
     * 针对不同厂商（尤其是 MIUI 14 / HyperOS 与原生 Android）引导用户前往悬浮窗授权页
     */
    fun openOverlaySettings(context: Context) {
        val isXiaomi = Build.MANUFACTURER.contains("Xiaomi", ignoreCase = true) ||
                Build.BRAND.contains("Redmi", ignoreCase = true) ||
                Build.BRAND.contains("POCO", ignoreCase = true)

        if (isXiaomi) {
            // 尝试打开 MIUI / HyperOS 专属应用权限管理页（直达“显示悬浮窗”与“后台弹出界面”）
            try {
                val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    putExtra("extra_pkgname", context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(miuiIntent)
                return
            } catch (_: Exception) {
                try {
                    val miuiFallback = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                        putExtra("extra_pkgname", context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(miuiFallback)
                    return
                } catch (_: Exception) {}
            }
        }

        // 标准 Android 悬浮窗授权页
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * 针对 MIUI / HyperOS 打开自启动管理页，若失败回退至应用详情设置页
     */
    fun openMiuiAutostartSettings(context: Context) {
        try {
            val intent = Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * 打开系统应用详情设置页
     */
    fun openAppSettings(context: Context) {
        try {
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetails)
        } catch (_: Exception) {}
    }
}
