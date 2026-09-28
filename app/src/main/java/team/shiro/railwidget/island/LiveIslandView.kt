package team.shiro.railwidget.island

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import team.shiro.railwidget.R
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripStage
import team.shiro.railwidget.ui.MainActivity

/**
 * 灵动岛 / 实时胶囊全屏覆盖层视图控制器
 * 兼容 MIUI 14、HyperOS 以及 Android 12~15
 */
class LiveIslandView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var currentTrip: Trip? = null
    private var isExpanded = false
    private var isAttached = false

    private val rootContainer = FrameLayout(context)
    private val islandCard = FrameLayout(context)

    // 折叠态布局 (Compact Island)
    private val compactLayout = LinearLayout(context)
    private val tvCompactTrain = TextView(context)
    private val spacerHole = View(context)
    private val tvCompactGate = TextView(context)
    private val tvCompactTime = TextView(context)

    // 展开态布局 (Expanded Island)
    private val expandedLayout = LinearLayout(context)
    private val tvExpandedTitle = TextView(context)
    private val tvExpandedStatus = TextView(context)
    private val tvExpandedRoute = TextView(context)
    private val tvExpandedTimes = TextView(context)
    private val tvExpandedSeat = TextView(context)
    private val tvExpandedGate = TextView(context)
    private val btnOpenApp = TextView(context)

    private val windowParams: WindowManager.LayoutParams

    init {
        val statusBarHeight = getStatusBarHeight(context)
        val cutoutBounds = getCameraCutoutBounds()

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = if (cutoutBounds != null && cutoutBounds.top > 0) {
                cutoutBounds.top
            } else {
                (statusBarHeight * 0.15f).toInt()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        setupViews(cutoutBounds)
    }

    private fun setupViews(cutoutBounds: Rect?) {
        val dp = context.resources.displayMetrics.density

        // 1. 根容器点击事件（展开态时点击透明背景收起）
        rootContainer.setOnClickListener {
            if (isExpanded) {
                collapse()
            }
        }

        // 2. 灵动岛核心卡片容器
        val islandBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * dp
            setColor(Color.parseColor("#0B0F19")) // 深邃黑曜石
            setStroke((1 * dp).toInt(), Color.parseColor("#334155")) // 细腻微光边框
        }
        islandCard.background = islandBg
        islandCard.elevation = 16 * dp

        islandCard.setOnClickListener {
            if (!isExpanded) {
                expand()
            }
        }

        // ==========================================
        // 3. 构建折叠态布局 (Compact Island)
        // ==========================================
        compactLayout.orientation = LinearLayout.HORIZONTAL
        compactLayout.gravity = Gravity.CENTER_VERTICAL
        compactLayout.setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())

        // 车次图标与名称
        tvCompactTrain.setTextColor(Color.WHITE)
        tvCompactTrain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        tvCompactTrain.setTypeface(null, android.graphics.Typeface.BOLD)
        compactLayout.addView(tvCompactTrain)

        // 摄像头挖孔安全避让垫片（若为居中开孔则撑开对应宽度）
        val screenWidth = context.resources.displayMetrics.widthPixels
        val holeWidth = if (cutoutBounds != null && kotlin.math.abs(cutoutBounds.centerX() - screenWidth / 2) < 50 * dp) {
            cutoutBounds.width().coerceAtLeast((24 * dp).toInt())
        } else {
            (14 * dp).toInt()
        }
        val spacerParams = LinearLayout.LayoutParams(holeWidth, 1)
        spacerHole.layoutParams = spacerParams
        compactLayout.addView(spacerHole)

        // 右侧倒计时/状态指示
        tvCompactTime.setTextColor(Color.parseColor("#94A3B8"))
        tvCompactTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        compactLayout.addView(tvCompactTime)

        val gateSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((4 * dp).toInt(), 1)
        }
        compactLayout.addView(gateSpacer)

        // 右侧醒目检票口胶囊
        val gateBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5 * dp
            setColor(Color.parseColor("#DC2626"))
        }
        tvCompactGate.background = gateBg
        tvCompactGate.setTextColor(Color.WHITE)
        tvCompactGate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        tvCompactGate.setTypeface(null, android.graphics.Typeface.BOLD)
        tvCompactGate.setPadding((6 * dp).toInt(), (1 * dp).toInt(), (6 * dp).toInt(), (1 * dp).toInt())
        compactLayout.addView(tvCompactGate)

        islandCard.addView(compactLayout)

        // ==========================================
        // 4. 构建展开态布局 (Expanded Island)
        // ==========================================
        expandedLayout.orientation = LinearLayout.VERTICAL
        expandedLayout.setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
        expandedLayout.visibility = View.GONE

        // 顶栏：车次 + 状态药丸
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvExpandedTitle.setTextColor(Color.WHITE)
        tvExpandedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        tvExpandedTitle.setTypeface(null, android.graphics.Typeface.BOLD)
        topRow.addView(tvExpandedTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        tvExpandedStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        tvExpandedStatus.setTypeface(null, android.graphics.Typeface.BOLD)
        tvExpandedStatus.setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        val statusBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * dp
            setColor(Color.parseColor("#1E293B"))
        }
        tvExpandedStatus.background = statusBg
        topRow.addView(tvExpandedStatus)

        expandedLayout.addView(topRow)

        // 中间行：始发 ➔ 终到
        tvExpandedRoute.setTextColor(Color.WHITE)
        tvExpandedRoute.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        tvExpandedRoute.setTypeface(null, android.graphics.Typeface.BOLD)
        tvExpandedRoute.setPadding(0, (6 * dp).toInt(), 0, 0)
        expandedLayout.addView(tvExpandedRoute)

        // 时刻行：发车时刻 ➔ 到站时刻
        tvExpandedTimes.setTextColor(Color.parseColor("#94A3B8"))
        tvExpandedTimes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        expandedLayout.addView(tvExpandedTimes)

        // 分隔线
        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()).apply {
                topMargin = (10 * dp).toInt()
                bottomMargin = (10 * dp).toInt()
            }
        }
        expandedLayout.addView(divider)

        // 底栏：席位信息 + 检票口大徽章 + 直达 App 按钮
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val seatCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        tvExpandedSeat.setTextColor(Color.parseColor("#E2E8F0"))
        tvExpandedSeat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        tvExpandedSeat.setTypeface(null, android.graphics.Typeface.BOLD)
        seatCol.addView(tvExpandedSeat)

        tvExpandedGate.setTextColor(Color.parseColor("#EF4444"))
        tvExpandedGate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        tvExpandedGate.setTypeface(null, android.graphics.Typeface.BOLD)
        seatCol.addView(tvExpandedGate)

        bottomRow.addView(seatCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // 直达 App 胶囊按钮
        val btnBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12 * dp
            setColor(Color.parseColor("#0284C7"))
        }
        btnOpenApp.background = btnBg
        btnOpenApp.text = "进入卡片 ↗"
        btnOpenApp.setTextColor(Color.WHITE)
        btnOpenApp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        btnOpenApp.setTypeface(null, android.graphics.Typeface.BOLD)
        btnOpenApp.setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
        btnOpenApp.setOnClickListener {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                currentTrip?.let { putExtra("EXTRA_ORDER_NO", it.orderNo) }
            }
            context.startActivity(intent)
            collapse()
        }
        bottomRow.addView(btnOpenApp)

        expandedLayout.addView(bottomRow)
        islandCard.addView(expandedLayout)

        val cardLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        rootContainer.addView(islandCard, cardLayoutParams)
    }

    /**
     * 更新行程数据并重新渲染
     */
    fun updateTrip(trip: Trip) {
        currentTrip = trip
        val dp = context.resources.displayMetrics.density

        // 1. 更新折叠态
        tvCompactTrain.text = "🚄 ${trip.trainCode}"
        val gate = trip.getCleanTicketGate()
        if (gate.isNotBlank() && gate != "暂无") {
            tvCompactGate.text = gate
            tvCompactGate.visibility = View.VISIBLE
        } else {
            tvCompactGate.visibility = View.GONE
        }

        // 计算发车倒计时或状态文本
        val countdown = getCountdownText(trip)
        tvCompactTime.text = countdown

        // 2. 更新展开态
        val category = when (trip.trainCode.firstOrNull()?.uppercaseChar()) {
            'G' -> "高铁"
            'D' -> "动车"
            'C' -> "城际"
            else -> "列车"
        }
        tvExpandedTitle.text = "${trip.trainCode} · $category"

        val stage = trip.getStage()
        val statusText = when {
            stage == TripStage.COMPLETED -> "已结束"
            stage == TripStage.IN_TRANSIT -> "运行中"
            else -> trip.computeStatus()
        }
        tvExpandedStatus.text = statusText
        val (stColor, stBg) = when (statusText) {
            "正在检票" -> Pair(Color.parseColor("#EF4444"), Color.parseColor("#450A0A"))
            "停止检票" -> Pair(Color.parseColor("#F97316"), Color.parseColor("#431407"))
            "运行中" -> Pair(Color.parseColor("#10B981"), Color.parseColor("#064E3B"))
            else -> Pair(Color.parseColor("#38BDF8"), Color.parseColor("#082F49"))
        }
        tvExpandedStatus.setTextColor(stColor)
        (tvExpandedStatus.background as? GradientDrawable)?.setColor(stBg)

        tvExpandedRoute.text = "${trip.departureStation}  ➔  ${trip.arrivalStation}"
        val depTime = if (trip.departureTime.isNotBlank() && trip.departureTime != "00:00") trip.departureTime else "--:--"
        val arrTime = if (trip.arrivalTime.isNotBlank() && trip.arrivalTime != "00:00") trip.arrivalTime else "--:--"
        tvExpandedTimes.text = "发车 $depTime  ·  到达 $arrTime"

        val seatStr = if (trip.carriage.isNotBlank() || trip.seat.isNotBlank()) {
            val tTag = if (trip.ticketType == "列车补票") " · 补票" else ""
            "${trip.carriage} ${trip.seat} · ${trip.seatType}$tTag"
        } else {
            "席位待出 · ${trip.seatType}"
        }
        tvExpandedSeat.text = seatStr

        val gateDesc = if (gate.isNotBlank() && gate != "暂无") "检票口：$gate" else "检票口：待候车大屏公布"
        tvExpandedGate.text = gateDesc

        if (isAttached) {
            try {
                windowManager.updateViewLayout(rootContainer, windowParams)
            } catch (_: Exception) {}
        }
    }

    /**
     * 平滑展开为大岛
     */
    fun expand() {
        if (isExpanded) return
        isExpanded = true

        val dp = context.resources.displayMetrics.density

        // 窗口尺寸切换为全屏透明容器，以便捕获外部点击收起手势
        windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
        windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        try {
            windowManager.updateViewLayout(rootContainer, windowParams)
        } catch (_: Exception) {}

        compactLayout.visibility = View.GONE
        expandedLayout.visibility = View.VISIBLE

        val cardLp = islandCard.layoutParams as FrameLayout.LayoutParams
        val targetWidth = (340 * dp).toInt()
        val targetRadius = 24 * dp

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener { anim ->
                val progress = anim.animatedFraction
                cardLp.width = (progress * targetWidth).toInt().coerceAtLeast((180 * dp).toInt())
                islandCard.layoutParams = cardLp
                (islandCard.background as? GradientDrawable)?.cornerRadius = 20 * dp + (targetRadius - 20 * dp) * progress
            }
        }
        animator.start()
    }

    /**
     * 平滑收起回小胶囊
     */
    fun collapse() {
        if (!isExpanded) return
        isExpanded = false

        val dp = context.resources.displayMetrics.density

        expandedLayout.visibility = View.GONE
        compactLayout.visibility = View.VISIBLE

        val cardLp = islandCard.layoutParams as FrameLayout.LayoutParams
        cardLp.width = FrameLayout.LayoutParams.WRAP_CONTENT
        islandCard.layoutParams = cardLp
        (islandCard.background as? GradientDrawable)?.cornerRadius = 20 * dp

        // 恢复紧凑型 WindowParams，释放屏幕其他区域的触摸穿透
        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        try {
            windowManager.updateViewLayout(rootContainer, windowParams)
        } catch (_: Exception) {}
    }

    /**
     * 将灵动岛添加到桌面图层
     */
    fun attach() {
        if (isAttached) return
        try {
            windowManager.addView(rootContainer, windowParams)
            isAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 从桌面彻底移除并销毁
     */
    fun detach() {
        if (!isAttached) return
        try {
            windowManager.removeViewImmediate(rootContainer)
            isAttached = false
        } catch (_: Exception) {}
    }

    private fun getCountdownText(trip: Trip): String {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            val time = if (trip.departureTime.isNotBlank() && trip.departureTime != "00:00") trip.departureTime else return "已购"
            val depMillis = format.parse("${trip.departureDate} $time")?.time ?: return "已购"
            val diff = depMillis - System.currentTimeMillis()
            when {
                diff < -2 * 3600 * 1000 -> "已到达"
                diff < 0 -> "运行中"
                diff < 60 * 60 * 1000 -> "${diff / 60000}m开"
                diff < 24 * 3600 * 1000 -> "${diff / 3600000}h开"
                else -> "${diff / 86400000}天"
            }
        } catch (_: Exception) {
            "候车"
        }
    }

    private fun getCameraCutoutBounds(): Rect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val cutout = windowManager.currentWindowMetrics.windowInsets.displayCutout
                cutout?.boundingRectTop?.takeIf { !it.isEmpty }
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            (28 * context.resources.displayMetrics.density).toInt()
        }
    }
}
