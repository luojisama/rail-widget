package team.shiro.railwidget.island

import android.content.Context
import android.os.Handler
import android.os.Looper
import team.shiro.railwidget.data.model.Trip
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 灵动胶囊 / 桌面灵动岛全局悬浮层宿主
 * 作为进程级单例持有 LiveIslandView，支持从后台或广播接收器中提前附着 TYPE_APPLICATION_OVERLAY 悬浮窗，
 * 使 Android 14/15 (targetSdk 35) 以及 MIUI / HyperOS 在满足“拥有可见悬浮窗”的前提下
 * 成功豁免后台启动前台服务的限制。
 */
object IslandOverlayHost {

    @Volatile
    private var islandView: LiveIslandView? = null

    @Volatile
    var isAttached: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 确保悬浮窗已被附着并呈现指定的行程信息。
     * 若未附着且拥有悬浮窗权限，将在主线程安全创建并执行 attach。
     * 捕获一切异常并返回是否成功附着。
     */
    fun ensureAttached(context: Context, trip: Trip): Boolean {
        if (!IslandPermissionHelper.hasOverlayPermission(context)) {
            return false
        }

        fun doAttach(): Boolean {
            return try {
                if (islandView == null) {
                    islandView = LiveIslandView(context.applicationContext)
                }
                val view = islandView ?: return false
                val attached = view.attach()
                if (attached) {
                    isAttached = true
                    view.updateTrip(trip)
                    true
                } else {
                    isAttached = false
                    false
                }
            } catch (e: Exception) {
                android.util.Log.w("IslandOverlayHost", "无法附着灵动岛悬浮层", e)
                isAttached = false
                false
            }
        }

        return if (Looper.myLooper() == Looper.getMainLooper()) {
            doAttach()
        } else {
            val latch = CountDownLatch(1)
            var result = false
            mainHandler.post {
                try {
                    result = doAttach()
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(2, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            result
        }
    }

    /**
     * 更新当前展示的行程卡片数据
     */
    fun update(trip: Trip) {
        val action = Runnable {
            try {
                islandView?.updateTrip(trip)
            } catch (e: Exception) {
                android.util.Log.w("IslandOverlayHost", "更新灵动岛悬浮层失败", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    /**
     * 从桌面窗口层分离并销毁悬浮卡片
     */
    fun detach() {
        val action = Runnable {
            try {
                islandView?.detach()
            } catch (e: Exception) {
                android.util.Log.w("IslandOverlayHost", "分离灵动岛悬浮层失败", e)
            } finally {
                islandView = null
                isAttached = false
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }
}
