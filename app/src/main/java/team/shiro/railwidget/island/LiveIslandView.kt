package team.shiro.railwidget.island

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripStage
import team.shiro.railwidget.ui.MainActivity

/**
 * 原生黑曜石灵动岛 / 实时胶囊悬浮层控制器
 * 完整包裹前摄物理挖孔，自屏幕顶端（摄像头区域）自然向下延展
 * 兼容 MIUI 14、HyperOS 以及 Android 12~15
 */
class LiveIslandView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var currentTrip: Trip? = null
    private var isExpanded = false
    private var isAttached = false

    private val rootContainer = FrameLayout(context)
    private val islandCard = FrameLayout(context)

    // 折叠态布局 (Compact Island: 包裹前摄并左右翼展)
    private val compactLayout = LinearLayout(context)
    private val tvCompactTrain = TextView(context)
    private val spacerCameraHole = View(context)
    private val tvCompactTime = TextView(context)
    private val tvCompactGate = TextView(context)

    // 展开态布局 (Expanded Island: 登车牌大卡)
    private val expandedLayout = LinearLayout(context)
    private val tvExpandedTitle = TextView(context)
    private val tvExpandedStatus = TextView(context)
    private val btnCloseExpanded = TextView(context)
    private val tvExpandedRoute = TextView(context)
    private val tvExpandedTimes = TextView(context)
    private val tvExpandedSeat = TextView(context)
    private val tvExpandedGate = TextView(context)
    private val btnOpenApp = TextView(context)

    private val windowParams: WindowManager.LayoutParams
    private val statusBarHeight: Int

    init {
        val dp = context.resources.displayMetrics.density
        statusBarHeight = getStatusBarHeight(context)

        // 顶层贴顶包裹摄像头：y = 0，允许延伸至硬件打孔区
        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        setupViews()
    }

    private fun setupViews() {
        val dp = context.resources.displayMetrics.density
        val capsuleHeight = (statusBarHeight + (6 * dp)).toInt() // 从屏幕顶边缘延伸至状态栏下沿略微探出

        // 1. 根容器透明背景（展开态时点击蒙层收起）
        rootContainer.setOnClickListener {
            if (isExpanded) {
                collapse()
            }
        }

        // 2. 灵动岛核心卡片容器（纯黑背景与物理摄像头一体化）
        val islandBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                12 * dp, 12 * dp, // top-left
                12 * dp, 12 * dp, // top-right
                18 * dp, 18 * dp, // bottom-right
                18 * dp, 18 * dp  // bottom-left
            )
            setColor(Color.BLACK) // 纯黑以达到前摄完全隐形效果
            setStroke((1 * dp).toInt(), Color.parseColor("#262626")) // 微光质感边框
        }
        islandCard.background = islandBg
        islandCard.elevation = 16 * dp

        // 核心触控分发：ACTION_DOWN 拦截消费以确保独占手势，ACTION_UP 触发展开
        val touchListener = View.OnTouchListener { v, event ->
            if (!isExpanded) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(60).start()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start()
                        expand()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start()
                        true
                    }
                    else -> true
                }
            } else {
                false
            }
        }
        islandCard.setOnTouchListener(touchListener)
        compactLayout.setOnTouchListener(touchListener)

        // ==========================================
        // 3. 构建折叠态布局 (Compact Island: 包裹前摄并左右翼展)
        // ==========================================
        compactLayout.orientation = LinearLayout.HORIZONTAL
        compactLayout.gravity = Gravity.CENTER_VERTICAL
        compactLayout.setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), (2 * dp).toInt())

        // 左翼：车次徽章
        tvCompactTrain.setTextColor(Color.WHITE)
        tvCompactTrain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        tvCompactTrain.setTypeface(null, android.graphics.Typeface.BOLD)
        compactLayout.addView(tvCompactTrain)

        // 中间：摄像头物理开孔避让留白（正好使物理镜头落在黑色无字安全区）
        val cameraHoleParams = LinearLayout.LayoutParams((22 * dp).toInt(), 1)
        spacerCameraHole.layoutParams = cameraHoleParams
        compactLayout.addView(spacerCameraHole)

        // 右翼：倒计时 / 发车时刻指示
        tvCompactTime.setTextColor(Color.parseColor("#94A3B8"))
        tvCompactTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        compactLayout.addView(tvCompactTime)

        // 右翼：精简检票口药丸
        val gateBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5 * dp
            setColor(Color.parseColor("#DC2626"))
        }
        tvCompactGate.background = gateBg
        tvCompactGate.setTextColor(Color.WHITE)
        tvCompactGate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        tvCompactGate.setTypeface(null, android.graphics.Typeface.BOLD)
        tvCompactGate.setPadding((5 * dp).toInt(), (1 * dp).toInt(), (5 * dp).toInt(), (1 * dp).toInt())
        val gateParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = (6 * dp).toInt()
        }
        tvCompactGate.layoutParams = gateParams
        compactLayout.addView(tvCompactGate)

        val compactLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            capsuleHeight
        ).apply {
            gravity = Gravity.CENTER
        }
        islandCard.addView(compactLayout, compactLp)

        // ==========================================
        // 4. 构建展开态布局 (Expanded Island: 登车牌大卡)
        // ==========================================
        expandedLayout.orientation = LinearLayout.VERTICAL
        expandedLayout.visibility = View.GONE
        expandedLayout.setPadding(
            (18 * dp).toInt(),
            (statusBarHeight + (8 * dp)).toInt(), // 预留顶部前摄区域高度，内容自前摄下方排布
            (18 * dp).toInt(),
            (16 * dp).toInt()
        )

        // 头部行：车次 + 状态药丸 + 关闭按钮
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvExpandedTitle.setTextColor(Color.WHITE)
        tvExpandedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        tvExpandedTitle.setTypeface(null, android.graphics.Typeface.BOLD)
        headerRow.addView(tvExpandedTitle)

        val headerSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        headerRow.addView(headerSpacer)

        val statusBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6 * dp
        }
        tvExpandedStatus.background = statusBg
        tvExpandedStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        tvExpandedStatus.setTypeface(null, android.graphics.Typeface.BOLD)
        tvExpandedStatus.setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        headerRow.addView(tvExpandedStatus)

        // 展开态右上角收起图标 ✕
        btnCloseExpanded.text = " ✕ "
        btnCloseExpanded.setTextColor(Color.parseColor("#94A3B8"))
        btnCloseExpanded.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        btnCloseExpanded.setPadding((10 * dp).toInt(), (2 * dp).toInt(), 0, (2 * dp).toInt())
        btnCloseExpanded.setOnClickListener { collapse() }
        headerRow.addView(btnCloseExpanded)

        expandedLayout.addView(headerRow)

        // 行程发到站
        tvExpandedRoute.setTextColor(Color.parseColor("#F8FAFC"))
        tvExpandedRoute.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        tvExpandedRoute.setTypeface(null, android.graphics.Typeface.BOLD)
        tvExpandedRoute.setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        expandedLayout.addView(tvExpandedRoute)

        // 发到时刻
        tvExpandedTimes.setTextColor(Color.parseColor("#94A3B8"))
        tvExpandedTimes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        tvExpandedTimes.setPadding(0, 0, 0, (10 * dp).toInt())
        expandedLayout.addView(tvExpandedTimes)

        // 分割线
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()).apply {
                bottomMargin = (10 * dp).toInt()
            }
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        expandedLayout.addView(divider)

        // 席位与车厢
        tvExpandedSeat.setTextColor(Color.parseColor("#CBD5E1"))
        tvExpandedSeat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        expandedLayout.addView(tvExpandedSeat)

        // 底部操作区：检票口 + 进入铁行卡片按钮
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (14 * dp).toInt(), 0, 0)
        }

        tvExpandedGate.setTextColor(Color.parseColor("#EF4444"))
        tvExpandedGate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        tvExpandedGate.setTypeface(null, android.graphics.Typeface.BOLD)
        bottomRow.addView(tvExpandedGate)

        val bottomSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        bottomRow.addView(bottomSpacer)

        val btnBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * dp
            setColor(Color.parseColor("#1E293B"))
            setStroke((1 * dp).toInt(), Color.parseColor("#334155"))
        }
        btnOpenApp.background = btnBg
        btnOpenApp.text = "进入铁行卡片 ↗"
        btnOpenApp.setTextColor(Color.parseColor("#38BDF8"))
        btnOpenApp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        btnOpenApp.setTypeface(null, android.graphics.Typeface.BOLD)
        btnOpenApp.setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
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
            capsuleHeight
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

        // 1. 更新折叠态
        tvCompactTrain.text = "🚄 ${trip.trainCode}"
        val gate = trip.getCleanTicketGate()
        val shortGate = getShortGate(gate)
        if (shortGate.isNotBlank()) {
            tvCompactGate.text = shortGate
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
     * 平滑自前摄区域向下喷涌展开
     */
    fun expand() {
        if (isExpanded) return
        isExpanded = true

        val dp = context.resources.displayMetrics.density

        // 窗口尺寸切换为全屏透明容器，以便捕获外部点击收起手势
        windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
        windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowParams.y = 0

        val cardLp = islandCard.layoutParams as FrameLayout.LayoutParams
        cardLp.height = FrameLayout.LayoutParams.WRAP_CONTENT
        islandCard.layoutParams = cardLp

        try {
            windowManager.updateViewLayout(rootContainer, windowParams)
        } catch (_: Exception) {}

        compactLayout.visibility = View.GONE
        expandedLayout.visibility = View.VISIBLE

        val targetWidth = (340 * dp).toInt()
        val targetRadius = 24 * dp

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.05f)
            addUpdateListener { anim ->
                val progress = anim.animatedFraction
                cardLp.width = (progress * targetWidth).toInt().coerceAtLeast((180 * dp).toInt())
                islandCard.layoutParams = cardLp
                (islandCard.background as? GradientDrawable)?.cornerRadius = 16 * dp + (targetRadius - 16 * dp) * progress
            }
        }
        animator.start()
    }

    /**
     * 平滑收起回顶部包裹前摄的折叠胶囊
     */
    fun collapse() {
        if (!isExpanded) return
        isExpanded = false

        val dp = context.resources.displayMetrics.density
        val capsuleHeight = (statusBarHeight + (6 * dp)).toInt()

        expandedLayout.visibility = View.GONE
        compactLayout.visibility = View.VISIBLE

        val cardLp = islandCard.layoutParams as FrameLayout.LayoutParams
        cardLp.width = FrameLayout.LayoutParams.WRAP_CONTENT
        cardLp.height = capsuleHeight
        islandCard.layoutParams = cardLp

        (islandCard.background as? GradientDrawable)?.apply {
            cornerRadii = floatArrayOf(
                12 * dp, 12 * dp,
                12 * dp, 12 * dp,
                18 * dp, 18 * dp,
                18 * dp, 18 * dp
            )
        }

        // 恢复紧凑型 WindowParams，贴顶 y=0 包裹摄像头，允许外部触摸穿透
        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowParams.y = 0

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

    private fun getShortGate(gate: String): String {
        val clean = gate.removePrefix("检票口").trim()
        if (clean.isBlank() || clean == "暂无") return ""
        val slashIndex = clean.lastIndexOf('/')
        if (slashIndex >= 0 && slashIndex < clean.length - 1) {
            val after = clean.substring(slashIndex + 1).trim()
            if (after.any { it.isDigit() }) return after
        }
        // 如果包含站名，提取后面的数字+字母部分，如 "呈贡昆明南站16A" -> "16A"
        val regex = Regex("([0-9]{1,2}[A-Za-z](?:/[A-Za-z])?|[0-9]{1,2}号?)")
        val match = regex.find(clean)
        if (match != null) {
            return match.value
        }
        return if (clean.length > 6) clean.takeLast(4) else clean
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

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            (28 * context.resources.displayMetrics.density).toInt()
        }
    }
}
