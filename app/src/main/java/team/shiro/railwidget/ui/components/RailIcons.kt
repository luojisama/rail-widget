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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

/** One square 24-unit drawing grid, centered even in non-square layout slots. */
@Composable
private fun RailIconCanvas(modifier: Modifier, drawIcon: DrawScope.() -> Unit) {
    Canvas(modifier = modifier) {
        val side = size.minDimension
        withTransform({
            translate((size.width - side) / 2f, (size.height - side) / 2f)
            scale(side / 24f, side / 24f, pivot = Offset.Zero)
        }) { drawIcon() }
    }
}

@Composable
fun BulletTrainIcon(modifier: Modifier = Modifier.size(18.dp), tint: Color = Color.Unspecified) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    RailIconCanvas(modifier) {
        val body = Path().apply {
            moveTo(2f, 15.5f); lineTo(15.5f, 15.5f)
            cubicTo(18.5f, 15.5f, 21f, 13.5f, 22f, 11f)
            cubicTo(20.5f, 8f, 17.5f, 6f, 14.5f, 6f)
            lineTo(2f, 6f); close()
        }
        drawPath(body, color)
        val window = Path().apply {
            moveTo(15f, 7.75f); lineTo(18.75f, 8.5f)
            cubicTo(20f, 9.5f, 20.5f, 10.5f, 19.75f, 11.5f)
            lineTo(15f, 11.5f); close()
        }
        val glass = Color.White.copy(alpha = 0.85f)
        drawPath(window, glass)
        drawRoundRect(glass, Offset(4f, 8.25f), Size(4f, 3.25f), CornerRadius(0.5f))
        drawRoundRect(glass, Offset(9.5f, 8.25f), Size(4f, 3.25f), CornerRadius(0.5f))
        drawLine(color.copy(alpha = 0.6f), Offset(2.5f, 18f), Offset(17.5f, 18f), 1.75f, StrokeCap.Round)
    }
}

@Composable
fun LightningBoltIcon(modifier: Modifier = Modifier.size(14.dp), tint: Color = Color.Unspecified) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    RailIconCanvas(modifier) {
        drawPath(Path().apply {
            moveTo(14f, 2f); lineTo(5f, 13f); lineTo(11.5f, 13f)
            lineTo(10f, 22f); lineTo(19f, 10f); lineTo(12.5f, 10f); close()
        }, color)
    }
}

@Composable
fun ClockDialIcon(modifier: Modifier = Modifier.size(15.dp), tint: Color = Color.Unspecified) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    RailIconCanvas(modifier) {
        val center = Offset(12f, 12f)
        drawCircle(color, 9.125f, center, style = Stroke(1.75f))
        drawLine(color, center, Offset(8.5f, 8.5f), 1.75f, StrokeCap.Round)
        drawLine(color, center, Offset(16.5f, 8.5f), 1.75f, StrokeCap.Round)
        drawCircle(color, 1f, center)
    }
}

@Composable
fun RailTrackIcon(modifier: Modifier = Modifier.size(40.dp), tint: Color = Color.Unspecified) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    RailIconCanvas(modifier) {
        listOf(8f, 12f, 16f, 20f).forEachIndexed { index, y ->
            val halfWidth = 3.5f + index * 1.5f
            drawLine(color.copy(alpha = 0.45f + index * 0.15f),
                Offset(12f - halfWidth, y), Offset(12f + halfWidth, y), 1.75f, StrokeCap.Round)
        }
        drawLine(color, Offset(4.5f, 21f), Offset(9f, 3f), 1.75f, StrokeCap.Round)
        drawLine(color, Offset(19.5f, 21f), Offset(15f, 3f), 1.75f, StrokeCap.Round)
    }
}

@Composable
fun TicketDocIcon(modifier: Modifier = Modifier.size(40.dp), tint: Color = Color.Unspecified) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    RailIconCanvas(modifier) {
        val outline = Path().apply {
            moveTo(5f, 4f); lineTo(19f, 4f)
            quadraticTo(21f, 4f, 21f, 6f); lineTo(21f, 10f)
            cubicTo(18.5f, 10f, 18.5f, 14f, 21f, 14f); lineTo(21f, 18f)
            quadraticTo(21f, 20f, 19f, 20f); lineTo(5f, 20f)
            quadraticTo(3f, 20f, 3f, 18f); lineTo(3f, 14f)
            cubicTo(5.5f, 14f, 5.5f, 10f, 3f, 10f); lineTo(3f, 6f)
            quadraticTo(3f, 4f, 5f, 4f); close()
        }
        drawPath(outline, color.copy(alpha = 0.12f))
        drawPath(outline, color, style = Stroke(1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color, Offset(7f, 8f), Offset(17f, 8f), 1.5f, StrokeCap.Round)
        repeat(4) { index ->
            val start = 7f + index * 2.75f
            drawLine(color.copy(alpha = 0.5f), Offset(start, 12f), Offset(start + 1.5f, 12f), 1.25f, StrokeCap.Round)
        }
        drawLine(color.copy(alpha = 0.7f), Offset(7f, 16f), Offset(13.5f, 16f), 1.5f, StrokeCap.Round)
    }
}

