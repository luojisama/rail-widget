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
 * 自动读取手机前置摄像头物理位置（DisplayCutout），居中贴合包裹，独占触控杜绝触发通知栏
 * 兼容 MIUI 14、HyperOS 以及 Android 12~15
 */
class LiveIslandView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var currentTrip: Trip? = null
    private var isExpanded = false
    private var isAttached = false

    private val rootContainer = FrameLayout(context)

    // 核心拦截卡片容器：独占消费一切手势，彻底杜绝下拉通知栏
    private val islandCard = IslandCardView(context)

    // 折叠态布局 (Compact Island: 上层包裹前摄，下层下沉延伸展示核心消息)
    private val compactLayout = LinearLayout(context)
    // 1. 上层打孔避让行 (通知栏水平高度：极简车次徽标 + 前摄避让 + 车型次要标识)
    private val compactTopRow = LinearLayout(context)
    private val tvCompactTrain = TextView(context)
    private val spacerCameraHole = View(context)
    private val tvCompactCategory = TextView(context)

    // 2. 下层核心动态消息行 (完全延展于状态栏下方安全区：区间与倒计时/状态 + 检票口药丸)
    private val compactBottomRow = LinearLayout(context)
    private val tvCompactMessage = TextView(context)
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

    private var capsuleTopY: Int = 0
    private var capsuleHeight: Int = 0

    init {
        val dp = context.resources.displayMetrics.density
        statusBarHeight = getStatusBarHeight(context)

        // 默认高度设为精致小巧的 46dp（上层紧凑包裹前摄，下层下沉延展，两行文字紧密精致）
        capsuleTopY = (statusBarHeight * 0.08f).toInt().coerceAtLeast((2 * dp).toInt())
        capsuleHeight = (46 * dp).toInt()

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
            y = capsuleTopY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        setupViews()
    }

    private fun setupViews() {
        val dp = context.resources.displayMetrics.density

        // 1. 根容器透明背景（展开态时点击蒙层收起）
        rootContainer.setOnClickListener {
            if (isExpanded) {
                collapse()
            }
        }

        // 2. 自动探测并读取前摄硬件挖孔位置 (DisplayCutout)
        rootContainer.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.post {
                    val cutout = detectCameraCutout(v)
                    applyCutoutGeometry(cutout)
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            rootContainer.setOnApplyWindowInsetsListener { v, insets ->
                val cutout = insets.displayCutout?.boundingRectTop
                if (cutout != null && !cutout.isEmpty) {
                    applyCutoutGeometry(cutout)
                }
                insets
            }
        }

        // 3. 灵动岛核心卡片容器（纯黑背景与物理摄像头一体化，圆润饱满的小巧跑道水滴胶囊）
        val islandBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = capsuleHeight / 2f
            setColor(Color.BLACK) // 纯黑以使前摄镜头完美融为一体
            // 0.8dp 精细微光反光边缘，消除黑框生硬感，在浅色/深色壁纸下通透精致
            setStroke((0.8f * dp).toInt().coerceAtLeast(1), Color.parseColor("#33FFFFFF"))
        }
        islandCard.background = islandBg
        // 折叠态设为 0dp elevation，杜绝悬浮窗口边界硬切阴影导致的外层方形灰框
        islandCard.elevation = 0f

        // ==========================================
        // 4. 构建折叠态布局 (Compact Island: 上层包裹前摄，下层下沉延伸展示核心消息)
        // ==========================================
        compactLayout.orientation = LinearLayout.VERTICAL
        compactLayout.gravity = Gravity.CENTER_HORIZONTAL
        // 紧凑内边距：左右 12dp，上下仅 3.5dp/4.5dp，杜绝臃肿厚重
        compactLayout.setPadding((12 * dp).toInt(), (3.5f * dp).toInt(), (12 * dp).toInt(), (4.5f * dp).toInt())

        // 4.1 顶部行 (前摄包裹行：左翼车次 + 中置绝对安全避让 + 右翼车型，两翼 weight=1f 确保中置避让 100% 居中)
        compactTopRow.orientation = LinearLayout.HORIZONTAL
        compactTopRow.gravity = Gravity.CENTER_VERTICAL
        val topRowLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        compactTopRow.layoutParams = topRowLp

        // 左翼容器 (weight = 1f，内容靠右停靠在前摄左边安全区)
        val leftWing = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvCompactTrain.setTextColor(Color.WHITE)
        tvCompactTrain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        tvCompactTrain.setTypeface(null, android.graphics.Typeface.BOLD)
        tvCompactTrain.includeFontPadding = false
        val trainLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            rightMargin = (2 * dp).toInt()
        }
        tvCompactTrain.layoutParams = trainLp
        leftWing.addView(tvCompactTrain)
        compactTopRow.addView(leftWing)

        // 中间：摄像头物理开孔避让留白（通过两翼等宽权重，绝对锁定在卡片几何正中心）
        val cameraHoleParams = LinearLayout.LayoutParams((18 * dp).toInt(), 1)
        spacerCameraHole.layoutParams = cameraHoleParams
        compactTopRow.addView(spacerCameraHole)

        // 右翼容器 (weight = 1f，内容靠左停靠在前摄右边安全区)
        val rightWing = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvCompactCategory.setTextColor(Color.parseColor("#94A3B8"))
        tvCompactCategory.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        tvCompactCategory.includeFontPadding = false
        val catLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = (2 * dp).toInt()
        }
        tvCompactCategory.layoutParams = catLp
        rightWing.addView(tvCompactCategory)
        compactTopRow.addView(rightWing)

        compactLayout.addView(compactTopRow)

        // 4.2 底部行 (状态栏下方下沉延展区：发到站与倒计时/状态 + 精简检票口药丸)
        compactBottomRow.orientation = LinearLayout.HORIZONTAL
        compactBottomRow.gravity = Gravity.CENTER_VERTICAL
        val bottomRowLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (1.5f * dp).toInt() // 极小行距，紧凑精致
        }
        compactBottomRow.layoutParams = bottomRowLp

        // 核心动态消息：发到站 + 倒计时/运行状态
        tvCompactMessage.setTextColor(Color.parseColor("#F1F5F9"))
        tvCompactMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        tvCompactMessage.includeFontPadding = false
        tvCompactMessage.maxLines = 1
        tvCompactMessage.ellipsize = android.text.TextUtils.TruncateAt.END
        compactBottomRow.addView(tvCompactMessage)

        // 精简检票口药丸 (圆润饱满朱红微胶囊)
        val gateBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 3.5f * dp
            setColor(Color.parseColor("#E11D48"))
        }
        tvCompactGate.background = gateBg
        tvCompactGate.setTextColor(Color.WHITE)
        tvCompactGate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
        tvCompactGate.setTypeface(null, android.graphics.Typeface.BOLD)
        tvCompactGate.includeFontPadding = false
        tvCompactGate.setPadding((4 * dp).toInt(), (1 * dp).toInt(), (4 * dp).toInt(), (1 * dp).toInt())
        val gateParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = (4 * dp).toInt()
        }
        tvCompactGate.layoutParams = gateParams
        compactBottomRow.addView(tvCompactGate)

        compactLayout.addView(compactBottomRow)

        val compactLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        islandCard.addView(compactLayout, compactLp)

        // ==========================================
        // 5. 构建展开态布局 (Expanded Island: 登车牌大卡)
        // ==========================================
        expandedLayout.orientation = LinearLayout.VERTICAL
        expandedLayout.visibility = View.GONE
        expandedLayout.setPadding(
            (18 * dp).toInt(),
            (statusBarHeight + (8 * dp)).toInt(), // 预留前摄区域高度，内容从前摄下方展开
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
     * 自动探测并读取前摄硬件挖孔位置 (DisplayCutout)
     */
    private fun detectCameraCutout(view: View): Rect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val insetsCutout = view.rootWindowInsets?.displayCutout?.boundingRectTop
            if (insetsCutout != null && !insetsCutout.isEmpty) return insetsCutout
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val displayCutout = context.display?.cutout?.boundingRectTop
                if (displayCutout != null && !displayCutout.isEmpty) return displayCutout
                val wmCutout = windowManager.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
                if (wmCutout != null && !wmCutout.isEmpty) return wmCutout
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 根据前摄物理坐标自适应调整胶囊垂直位置、高度与中心留白
     */
    private fun applyCutoutGeometry(cutout: Rect?) {
        val dp = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels

        if (cutout != null && !cutout.isEmpty && kotlin.math.abs(cutout.centerX() - screenWidth / 2) < 60 * dp) {
            // 居中打孔屏：自动读到精确前摄坐标
            val holeWidth = cutout.width().coerceAtLeast((14 * dp).toInt())
            val holeHeight = cutout.height().coerceAtLeast((14 * dp).toInt())

            // 胶囊垂直 Y 轴：从前摄顶部往上留 2dp 贴合包裹（刚好比通知栏文字稍高一点，绝不顶死屏幕顶端）
            capsuleTopY = (cutout.top - (2 * dp).toInt()).coerceAtLeast(0)
            // 高度：前摄包裹行 + 下方紧凑下沉延展行，46dp 极其精致，告别厚重
            val minBaseHeight = (46 * dp).toInt()
            val minCutoutHeight = holeHeight + (26 * dp).toInt()
            capsuleHeight = kotlin.math.max(minBaseHeight, minCutoutHeight)

            // 中间留白：孔径 + 6dp 紧凑避让，文字紧密依附前摄两侧
            val spacerWidth = holeWidth + (6 * dp).toInt()
            spacerCameraHole.layoutParams = LinearLayout.LayoutParams(spacerWidth, 1)
        } else {
            // 兜底（左打孔或无挖孔机型）：高度设为 46dp，距离顶部约 2dp
            capsuleTopY = (statusBarHeight * 0.08f).toInt().coerceAtLeast((2 * dp).toInt())
            capsuleHeight = (46 * dp).toInt()
            spacerCameraHole.layoutParams = LinearLayout.LayoutParams((18 * dp).toInt(), 1)
        }

        val cornerRadius = capsuleHeight / 2f
        (islandCard.background as? GradientDrawable)?.cornerRadius = cornerRadius
        islandCard.elevation = 0f

        if (!isExpanded) {
            windowParams.y = capsuleTopY
            val cardLp = islandCard.layoutParams as? FrameLayout.LayoutParams
            if (cardLp != null) {
                cardLp.height = capsuleHeight
                islandCard.layoutParams = cardLp
            }
            if (isAttached) {
                try {
                    windowManager.updateViewLayout(rootContainer, windowParams)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * 更新行程数据并重新渲染
     */
    fun updateTrip(trip: Trip) {
        currentTrip = trip

        // 1. 更新顶部打孔行（次要基础标识：车次 + 车型，在通知栏区域极简呈现）
        tvCompactTrain.text = "🚄 ${trip.trainCode}"
        tvCompactCategory.text = getTrainCategory(trip.trainCode)

        // 2. 更新底部下沉消息行（向下扩出展示核心消息：发到站与状态/倒计时 + 检票口）
        val countdown = getCountdownText(trip)
        val routeDesc = "${trip.departureStation} ➔ ${trip.arrivalStation}"
        tvCompactMessage.text = "$routeDesc · $countdown"

        val gate = trip.getCleanTicketGate()
        val shortGate = getShortGate(gate)
        if (shortGate.isNotBlank()) {
            tvCompactGate.text = shortGate
            tvCompactGate.visibility = View.VISIBLE
        } else {
            tvCompactGate.visibility = View.GONE
        }

        // 3. 更新展开态
        val category = getTrainCategory(trip.trainCode)
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
        cardLp.topMargin = capsuleTopY
        cardLp.height = FrameLayout.LayoutParams.WRAP_CONTENT
        islandCard.layoutParams = cardLp

        try {
            windowManager.updateViewLayout(rootContainer, windowParams)
        } catch (_: Exception) {}

        compactLayout.visibility = View.GONE
        expandedLayout.visibility = View.VISIBLE
        expandedLayout.alpha = 0f

        // 展开态大卡启用阴影（全屏窗口不会截断阴影边缘）
        islandCard.elevation = 16 * dp

        val targetWidth = (340 * dp).toInt()
        val targetRadius = 24 * dp
        val initialRadius = capsuleHeight / 2f

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.05f)
            addUpdateListener { anim ->
                val progress = anim.animatedFraction
                cardLp.width = (progress * targetWidth).toInt().coerceAtLeast((180 * dp).toInt())
                islandCard.layoutParams = cardLp
                (islandCard.background as? GradientDrawable)?.cornerRadius = initialRadius + (targetRadius - initialRadius) * progress
                // 阶梯式淡入：前 20% 专注于黑曜石药丸弹簧张开，后 80% 平滑淡入内容，消除文字折行挤压
                expandedLayout.alpha = if (progress < 0.2f) 0f else ((progress - 0.2f) / 0.8f).coerceIn(0f, 1f)
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

        // 收起回折叠态：关闭 elevation，消除外层方形阴影截断黑框
        islandCard.elevation = 0f

        expandedLayout.visibility = View.GONE
        compactLayout.visibility = View.VISIBLE
        compactLayout.alpha = 0f
        compactLayout.animate().alpha(1f).setDuration(160).start()

        val cardLp = islandCard.layoutParams as FrameLayout.LayoutParams
        cardLp.width = FrameLayout.LayoutParams.WRAP_CONTENT
        cardLp.topMargin = 0
        cardLp.height = capsuleHeight
        islandCard.layoutParams = cardLp

        (islandCard.background as? GradientDrawable)?.cornerRadius = capsuleHeight / 2f

        // 恢复紧凑型 WindowParams，恢复前摄精确 Y 坐标，允许外部触摸穿透
        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowParams.y = capsuleTopY

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

    private fun getTrainCategory(trainCode: String): String {
        return when (trainCode.firstOrNull()?.uppercaseChar()) {
            'G' -> "高铁"
            'D' -> "动车"
            'C' -> "城际"
            'Z' -> "直快"
            'T' -> "特快"
            'K' -> "快速"
            'Y' -> "旅游"
            'S' -> "市域"
            else -> "列车"
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

    /**
     * 专属事件拦截容器类
     * 强制在 onInterceptTouchEvent 与 onTouchEvent 拦截并消费一切触控事件，
     * 彻底隔绝 MIUI / Android 系统的 StatusBarManager 下拉手势！
     */
    private inner class IslandCardView(context: Context) : FrameLayout(context) {

        private var startX = 0f
        private var startY = 0f

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            // 折叠态下由卡片强制拦截所有触摸事件，绝不让底层系统捕获到下拉手势
            return if (!isExpanded) true else super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isExpanded) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        parent?.requestDisallowInterceptTouchEvent(true)
                        animate().scaleX(0.96f).scaleY(0.96f).setDuration(60).start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start()
                        expand()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        animate().scaleX(1.0f).scaleY(1.0f).setDuration(60).start()
                        return true
                    }
                }
                return true
            }
            return super.onTouchEvent(event)
        }
    }
}
