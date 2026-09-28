package team.shiro.railwidget.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 原生 Canvas 自绘的高铁动车流线型车头图标 (中国复兴号/和谐号风格)
 * 纯几何矢量绘制，彻底告别系统 emoji 渲染不一致与粗糙感
 */
@Composable
fun BulletTrainIcon(
    modifier: Modifier = Modifier.size(18.dp),
    tint: Color = Color.Unspecified
) {
    val drawColor = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. 车身流线型轮廓 (从左后方平直延伸，到右前方前突流线型子弹鼻锥)
        val bodyPath = Path().apply {
            moveTo(w * 0.08f, h * 0.72f) // 左下底盘
            lineTo(w * 0.65f, h * 0.72f) // 底盘向前延伸
            // 子弹头下颌弧线
            cubicTo(
                w * 0.78f, h * 0.72f,
                w * 0.94f, h * 0.62f,
                w * 0.98f, h * 0.50f  // 鼻锥尖端
            )
            // 子弹头上风挡弧线
            cubicTo(
                w * 0.92f, h * 0.36f,
                w * 0.76f, h * 0.28f,
                w * 0.60f, h * 0.28f  // 驾驶室顶部
            )
            lineTo(w * 0.08f, h * 0.28f) // 车顶向后平直
            close()
        }
        drawPath(path = bodyPath, color = drawColor, style = Fill)

        // 2. 驾驶室风挡车窗 (斜向前方的锐利黑色/反白狭长车窗)
        val windowPath = Path().apply {
            moveTo(w * 0.62f, h * 0.35f)
            lineTo(w * 0.78f, h * 0.38f)
            cubicTo(w * 0.84f, h * 0.42f, w * 0.86f, h * 0.46f, w * 0.83f, h * 0.49f)
            lineTo(w * 0.62f, h * 0.49f)
            close()
        }
        drawPath(path = windowPath, color = Color.White.copy(alpha = 0.85f), style = Fill)

        // 3. 车侧客室车窗 (两个精致的小长方形)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.16f, h * 0.38f),
            size = Size(w * 0.18f, h * 0.14f),
            cornerRadius = CornerRadius(h * 0.03f, h * 0.03f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.38f, h * 0.38f),
            size = Size(w * 0.18f, h * 0.14f),
            cornerRadius = CornerRadius(h * 0.03f, h * 0.03f)
        )

        // 4. 底部动感轨道线条 (2条小线段，突出飞驰感)
        drawLine(
            color = drawColor.copy(alpha = 0.6f),
            start = Offset(w * 0.05f, h * 0.84f),
            end = Offset(w * 0.75f, h * 0.84f),
            strokeWidth = h * 0.08f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 原生 Canvas 自绘的现代利落闪电矢量图标 (用于自动拉起与快速同步)
 * 告别系统 emoji ⚡ 的不可控显示
 */
@Composable
fun LightningBoltIcon(
    modifier: Modifier = Modifier.size(14.dp),
    tint: Color = Color.Unspecified
) {
    val drawColor = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val boltPath = Path().apply {
            moveTo(w * 0.58f, h * 0.06f) // 顶部偏右起点
            lineTo(w * 0.22f, h * 0.52f) // 斜向左下
            lineTo(w * 0.48f, h * 0.52f) // 水平向右折返拐点
            lineTo(w * 0.38f, h * 0.94f) // 底部尖端
            lineTo(w * 0.78f, h * 0.42f) // 斜向右上
            lineTo(w * 0.52f, h * 0.42f) // 水平向左折返拐点
            close()
        }
        drawPath(path = boltPath, color = drawColor, style = Fill)
    }
}

/**
 * 原生 Canvas 自绘的极简精巧时钟图标 (用于途经时刻表展开栏)
 * 纯矢量表盘与指针，告别 🕒 / ⏰
 */
@Composable
fun ClockDialIcon(
    modifier: Modifier = Modifier.size(15.dp),
    tint: Color = Color.Unspecified
) {
    val drawColor = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val strokeW = r * 0.16f

        // 外圆表盘
        drawCircle(
            color = drawColor,
            radius = r - strokeW / 2f,
            center = center,
            style = Stroke(width = strokeW)
        )

        // 时针 (指向 10点)
        drawLine(
            color = drawColor,
            start = center,
            end = Offset(center.x - r * 0.32f, center.y - r * 0.32f),
            strokeWidth = strokeW * 1.1f,
            cap = StrokeCap.Round
        )

        // 分针 (指向 2点)
        drawLine(
            color = drawColor,
            start = center,
            end = Offset(center.x + r * 0.45f, center.y - r * 0.35f),
            strokeWidth = strokeW * 0.85f,
            cap = StrokeCap.Round
        )

        // 中心圆点
        drawCircle(
            color = drawColor,
            radius = strokeW * 0.7f,
            center = center
        )
    }
}

/**
 * 原生 Canvas 自绘的平行铁轨透视延伸图标 (用于在途中状态及空卡片)
 * 替代系统 emoji 🛤️
 */
@Composable
fun RailTrackIcon(
    modifier: Modifier = Modifier.size(40.dp),
    tint: Color = Color.Unspecified
) {
    val drawColor = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 枕木 (从远到近 4 根，透视变宽)
        val tieYList = listOf(h * 0.30f, h * 0.50f, h * 0.70f, h * 0.90f)
        tieYList.forEachIndexed { i, y ->
            val factor = 0.35f + i * 0.18f
            val halfW = w * factor * 0.45f
            drawLine(
                color = drawColor.copy(alpha = 0.4f + i * 0.15f),
                start = Offset(w * 0.5f - halfW, y),
                end = Offset(w * 0.5f + halfW, y),
                strokeWidth = h * 0.06f,
                cap = StrokeCap.Round
            )
        }

        // 两条透视钢轨 (向上交汇于远方)
        drawLine(
            color = drawColor,
            start = Offset(w * 0.20f, h * 0.95f),
            end = Offset(w * 0.38f, h * 0.20f),
            strokeWidth = h * 0.08f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = drawColor,
            start = Offset(w * 0.80f, h * 0.95f),
            end = Offset(w * 0.62f, h * 0.20f),
            strokeWidth = h * 0.08f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 原生 Canvas 自绘的带有折角与齿孔的铁路车票凭证图标
 * 替代系统 emoji 📋
 */
@Composable
fun TicketDocIcon(
    modifier: Modifier = Modifier.size(40.dp),
    tint: Color = Color.Unspecified
) {
    val drawColor = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 车票卡片轮廓 (带圆角与两端微小凹槽)
        val cardPath = Path().apply {
            moveTo(w * 0.15f, h * 0.18f)
            lineTo(w * 0.85f, h * 0.18f)
            lineTo(w * 0.85f, h * 0.44f)
            // 右侧凹槽
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w * 0.77f, h * 0.44f, w * 0.93f, h * 0.56f),
                startAngleDegrees = -90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(w * 0.85f, h * 0.82f)
            lineTo(w * 0.15f, h * 0.82f)
            lineTo(w * 0.15f, h * 0.56f)
            // 左侧凹槽
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w * 0.07f, h * 0.44f, w * 0.23f, h * 0.56f),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            close()
        }

        drawPath(
            path = cardPath,
            color = drawColor.copy(alpha = 0.12f),
            style = Fill
        )
        drawPath(
            path = cardPath,
            color = drawColor,
            style = Stroke(width = h * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 内部横线条 (模拟票面信息)
        drawLine(
            color = drawColor,
            start = Offset(w * 0.28f, h * 0.34f),
            end = Offset(w * 0.72f, h * 0.34f),
            strokeWidth = h * 0.045f,
            cap = StrokeCap.Round
        )
        // 虚线分割线
        val dashCount = 5
        val segW = (w * 0.54f) / (dashCount * 2 - 1)
        for (i in 0 until dashCount) {
            val sx = w * 0.23f + i * 2 * segW
            drawLine(
                color = drawColor.copy(alpha = 0.5f),
                start = Offset(sx, h * 0.50f),
                end = Offset(sx + segW, h * 0.50f),
                strokeWidth = h * 0.035f,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = drawColor.copy(alpha = 0.7f),
            start = Offset(w * 0.28f, h * 0.66f),
            end = Offset(w * 0.58f, h * 0.66f),
            strokeWidth = h * 0.045f,
            cap = StrokeCap.Round
        )
    }
}
