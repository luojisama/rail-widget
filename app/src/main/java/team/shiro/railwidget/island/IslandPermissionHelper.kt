package team.shiro.railwidget.island

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object IslandPermissionHelper {

    /**
     * 检查是否已授予应用悬浮窗（上层绘制）权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
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
                // 若被定制版安全中心拦截，尝试二级页面
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

        // 标准 Android 悬浮窗授权页 (Android 12/13/14/15 通用)
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // 极端情况跳转至应用详情页
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetails)
        }
    }
}
