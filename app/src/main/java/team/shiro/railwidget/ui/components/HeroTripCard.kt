package team.shiro.railwidget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.shiro.railwidget.data.api.LulutongData
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripStage
import team.shiro.railwidget.data.model.TripTimeline
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HeroTripCard(
    trip: Trip,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onRefreshTimetable: (() -> Unit)? = null,
    onUpdateGate: ((String) -> Unit)? = null,
    isIslandRunning: Boolean = false,
    isAutoLaunchActive: Boolean = false,
    autoLeadText: String = "",
    onToggleIsland: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis()
) {
    val stage = remember(trip.departureDate, trip.departureTime, trip.arrivalTime, trip.stops, now / 60_000L) {
        trip.getStage(now)
    }
    val isCompleted = stage == TripStage.COMPLETED
    val isInTransit = stage == TripStage.IN_TRANSIT

    // 历史已结束行程默认折叠表格，避免大表格霸屏；未出行或进行中行程可展开浏览
    var isExpandedTable by remember(trip.orderNo) { mutableStateOf(!isCompleted && trip.stops.size > 2) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpandedTable) 180f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "arrowRotation"
    )
    var showEditGateDialog by remember { mutableStateOf(false) }
    var editingGateText by remember(trip.ticketGate) { mutableStateOf(trip.ticketGate.removePrefix("检票口")) }

    val formattedDate = remember(trip.departureDate) { formatDisplayDate(trip.departureDate) }
    val (boardingIdx, alightingIdx) = remember(trip.stops, trip.departureStation, trip.arrivalStation) {
        val depNorm = TripTimeline.station(trip.departureStation)
        val arrNorm = TripTimeline.station(trip.arrivalStation)
        val bIdx = trip.stops.indexOfFirst { TripTimeline.station(it.stationName).equals(depNorm, ignoreCase = true) }
        val aIdx = if (bIdx >= 0) {
            trip.stops.indices.firstOrNull { i ->
                i > bIdx && TripTimeline.station(trip.stops[i].stationName).equals(arrNorm, ignoreCase = true)
            } ?: trip.stops.indexOfFirst { TripTimeline.station(it.stationName).equals(arrNorm, ignoreCase = true) }
        } else {
            trip.stops.indexOfFirst { TripTimeline.station(it.stationName).equals(arrNorm, ignoreCase = true) }
        }
        Pair(bIdx, aIdx)
    }
    val rideStopSpan = if (boardingIdx >= 0 && alightingIdx > boardingIdx) alightingIdx - boardingIdx else 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 1.dp else 2.dp),
        border = BorderStroke(
            1.dp,
            if (isCompleted) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // 1. 顶部车票票头: 车次徽标 + 车型 + 状态指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeBg = when {
                    isCompleted -> Color(0xFF64748B)
                    isInTransit -> Color(0xFF0F766E)
                    trip.trainCode.startsWith("G") || trip.trainCode.startsWith("D") || trip.trainCode.startsWith("C") -> Color(0xFF0284C7)
                    else -> Color(0xFF2563EB)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = trip.trainCode,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = getTrainCategory(trip.trainCode),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 灵动胶囊状态指示芯片
                if (isIslandRunning || isAutoLaunchActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isIslandRunning) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isIslandRunning) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = if (onToggleIsland != null) Modifier.clickable { onToggleIsland() } else Modifier
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            if (isIslandRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDC2626))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "胶囊运行中",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            } else {
                                LightningBoltIcon(
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                val leadLabel = if (autoLeadText.isNotBlank()) "自动拉起 · $autoLeadText" else "自动拉起就绪"
                                Text(
                                    text = leadLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                val statusText = when {
                    isCompleted -> "已结束"
                    isInTransit -> "运行中"
                    else -> trip.computeStatus(now)
                }
                val (statusBg, statusFg) = when (statusText) {
                    "正在检票" -> Pair(Color(0xFFFEF2F2), Color(0xFFDC2626))
                    "停止检票" -> Pair(Color(0xFFFFF7ED), Color(0xFFEA580C))
                    "运行中" -> Pair(Color(0xFFF0FDF4), Color(0xFF16A34A))
                    "已结束" -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
                    else -> Pair(Color(0xFFEFF6FF), Color(0xFF2563EB))
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusText,
                        color = statusFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // 2. 卡片顶栏第二行：完整出行日期与星期 + 乘车人 + 票种标签
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "乘车人 · ${trip.passengerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (trip.ticketType.isNotBlank() && trip.ticketType != "成人票") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (trip.ticketType.contains("补票")) Color(0xFFFEF3C7) else Color(0xFFEFF6FF)
                    ) {
                        Text(
                            text = trip.ticketType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (trip.ticketType.contains("补票")) Color(0xFFD97706) else Color(0xFF2563EB),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. 主行程发到站与时刻
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 出发站与时间
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    val depDisplay = if (trip.departureTime.isNotBlank() && trip.departureTime != "00:00") trip.departureTime else "--:--"
                    Text(
                        text = depDisplay,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = trip.departureStation,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 中间运行图示与历时 + 乘车区间站数/里程
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val durationText = ((trip.getArrivalTimeMillis() - trip.getDepartureTimeMillis()) / 60000L).let { minutes ->
                        if (trip.arrivalTime.isBlank() || minutes <= 0) "" else "${minutes / 60}时${minutes % 60}分"
                    }
                    if (durationText.isNotBlank()) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isCompleted) Color(0xFF94A3B8) else if (isInTransit) Color(0xFF0F766E) else Color(0xFF0284C7))
                        )
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(2.dp)
                                .background(Color(0xFFCBD5E1))
                        )
                        BulletTrainIcon(
                            modifier = Modifier.size(18.dp),
                            tint = if (isCompleted) Color(0xFF94A3B8) else if (isInTransit) Color(0xFF0F766E) else Color(0xFF0284C7)
                        )
                    }

                    val spanSummary = when {
                        rideStopSpan > 0 && trip.rideMileageKm != null -> "乘车 ${rideStopSpan} 站 · ${trip.rideMileageKm.toInt()}km"
                        rideStopSpan > 0 -> "乘车 ${rideStopSpan} 站"
                        trip.rideMileageKm != null -> "${trip.rideMileageKm.toInt()} km"
                        trip.stops.isNotEmpty() -> "全线 ${trip.stops.size} 站"
                        else -> "区间车次"
                    }
                    Text(
                        text = spanSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // 到达站与时间
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    val arrTime = if (trip.arrivalTime.isNotBlank() && trip.arrivalTime != "00:00") trip.arrivalTime else "--:--"
                    Text(
                        text = arrTime,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = trip.arrivalStation,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2.5 在途行程进度条与当前到站实时横幅
            val inTransitPosition = remember(trip.stops, trip.departureDate, trip.departureTime, trip.arrivalTime, now / 60_000L) {
                trip.currentPosition(now)
            }
            if (isInTransit || inTransitPosition != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val progress = TripTimeline.progress(trip, now)
                val progressPercent = (progress * 100).toInt().coerceIn(0, 100)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F766E).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.22f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LocationPinIcon(
                                modifier = Modifier.size(15.dp),
                                tint = Color(0xFF0F766E)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val detailText = inTransitPosition?.detailLabel(trip, now)
                                ?: "列车在途中运行 · 历时推算中"
                            Text(
                                text = detailText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F766E).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    val badgeText = inTransitPosition?.sourceBadge() ?: "时刻表推算"
                                    Text(
                                        text = "$badgeText · $progressPercent%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F766E),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color(0xFFCBD5E1).copy(alpha = 0.6f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = progress.coerceIn(0.01f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(Color(0xFF0F766E))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. 车票席位与检票口大卡条 (Boarding Pass Strip)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text(
                            text = "车厢座席",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        val seatDisplay = when {
                            trip.carriage.isNotBlank() && trip.seat.isNotBlank() -> "${trip.carriage} ${trip.seat}"
                            trip.carriage.isNotBlank() -> trip.carriage
                            trip.seat.isNotBlank() -> trip.seat
                            else -> "无座席凭证"
                        }
                        Text(
                            text = seatDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(modifier = Modifier.weight(0.9f)) {
                        Text(
                            text = if (trip.ticketType == "列车补票") "席别 · 补票" else "席别",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (trip.ticketType == "列车补票") Color(0xFFD97706) else Color(0xFF94A3B8)
                        )
                        Text(
                            text = trip.seatType,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (trip.price.isNotBlank()) {
                            val priceLabel = if (trip.ticketType == "列车补票") "补差价 ${trip.price}" else trip.price
                            Text(
                                text = priceLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (trip.ticketType == "列车补票") Color(0xFFD97706) else Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 检票口 (支持未出行与历史行程点击编辑或重查)
                    val gateCode = trip.getCleanTicketGate()
                    val hasGate = gateCode.isNotBlank() && gateCode != "暂无"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            hasGate && !isCompleted -> Color(0xFFFEF2F2)
                            hasGate && isCompleted -> Color(0xFFEFF6FF)
                            isCompleted -> Color(0xFFF1F5F9)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = if (!hasGate && !isCompleted) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null,
                        modifier = Modifier.clickable(enabled = onUpdateGate != null) {
                            editingGateText = if (hasGate) gateCode else ""
                            showEditGateDialog = true
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "检票口",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        hasGate && !isCompleted -> Color(0xFFDC2626)
                                        hasGate && isCompleted -> Color(0xFF0284C7)
                                        isCompleted -> Color(0xFF64748B)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (onUpdateGate != null) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "修改检票口",
                                        tint = if (hasGate && !isCompleted) Color(0xFFDC2626).copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            val gateFontSize = when {
                                !hasGate -> 12.sp
                                gateCode.length > 8 -> 12.sp
                                gateCode.length > 5 -> 14.sp
                                else -> 16.sp
                            }
                            Text(
                                text = if (hasGate) gateCode else "点此录入",
                                fontSize = gateFontSize,
                                fontWeight = if (hasGate) FontWeight.Black else FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = when {
                                    hasGate && !isCompleted -> Color(0xFFDC2626)
                                    hasGate && isCompleted -> Color(0xFF0284C7)
                                    isCompleted -> Color(0xFF64748B)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 途径车站与停车时间表格折叠/展开栏
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpandedTable = !isExpandedTable },
                color = if (isExpandedTable) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClockDialIcon(
                        modifier = Modifier.size(16.dp),
                        tint = if (isCompleted) Color(0xFF94A3B8) else Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val tableTitle = when {
                        trip.stops.isNotEmpty() && rideStopSpan > 0 ->
                            "经停时刻表 (全线 ${trip.stops.size} 站 · 乘车 ${rideStopSpan} 站)"
                        trip.stops.isNotEmpty() ->
                            "经停时刻表 (全线 ${trip.stops.size} 站)"
                        else ->
                            "经停时刻表 (区间两站)"
                    }
                    Text(
                        text = tableTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isExpandedTable) "收起" else "展开",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = arrowRotation },
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpandedTable,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 120))
            ) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    // 列车编组与运行资料看板 (2×3 双列网格信息卡)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            // 行 1: 车型编组 + 担当路局
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TrainInfoGridItem(
                                    icon = { BulletTrainIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                                    label = "车型编组",
                                    value = buildString {
                                        append(trip.trainModel.ifBlank { "待查询" })
                                        if (trip.formation.isNotBlank() && trip.formation != trip.trainModel) {
                                            append(" · ${trip.formation}")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val (normBureau, normDepot) = remember(trip.railwayBureau, trip.crewDepot) {
                                    val (b, d) = LulutongData.parseBureauAndDepot(trip.railwayBureau)
                                    val finalB = b.ifBlank { trip.railwayBureau }
                                    val finalD = trip.crewDepot.ifBlank { d }
                                    Pair(finalB, finalD)
                                }
                                TrainInfoGridItem(
                                    icon = { CompassBureauIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                                    label = "担当路局",
                                    value = buildString {
                                        append(normBureau.ifBlank { "待查询" })
                                        if (normDepot.isNotBlank() && normDepot != normBureau) {
                                            append(" · $normDepot")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)

                            // 行 2: 餐车位置 + 停靠站台
                            Row(modifier = Modifier.fillMaxWidth()) {
                                val diningVal = when {
                                    trip.diningCar.isNotBlank() && trip.diningCarNote.isNotBlank() && trip.diningCar != trip.diningCarNote ->
                                        "${trip.diningCar} · ${trip.diningCarNote}"
                                    trip.diningCar.isNotBlank() -> trip.diningCar
                                    trip.diningCarNote.isNotBlank() -> trip.diningCarNote
                                    else -> "无餐车 / 待查"
                                }
                                TrainInfoGridItem(
                                    icon = { DiningCarIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                                    label = "餐车位置",
                                    value = diningVal,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val formattedPlat = trip.platform.trim().let { p ->
                                    when {
                                        p.isBlank() -> ""
                                        p.endsWith("站台") || p.endsWith("台") -> p
                                        else -> "${p}站台"
                                    }
                                }
                                val platformVal = buildString {
                                    if (formattedPlat.isNotBlank()) {
                                        append(formattedPlat)
                                    } else {
                                        val gate = trip.getCleanTicketGate()
                                        if (gate != "暂无" && gate.isNotBlank()) {
                                            append("检票口 $gate")
                                        } else {
                                            append("待公布")
                                        }
                                    }
                                }
                                TrainInfoGridItem(
                                    icon = { PlatformIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                                    label = if (trip.platformReference && formattedPlat.isNotBlank()) "上车站台 (参考)" else "上车站台",
                                    value = platformVal,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), thickness = 0.5.dp)

                            // 行 3: 乘车里程 + 全线里程
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TrainInfoGridItem(
                                    icon = { MileageRouteIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                                    label = "乘车里程",
                                    value = trip.rideMileageKm?.let { "${it.toInt()} km" } ?: "待计算",
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                TrainInfoGridItem(
                                    icon = { RailTrackIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                                    label = "全线里程",
                                    value = trip.totalMileageKm?.let { "${it.toInt()} km" } ?: "待计算",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // 看板底部: 数据来源与更新时间
                            if (trip.dataCheckedAt > 0 || trip.dataSource.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val sourceLabel = remember(trip.dataSource, trip.dataCheckedAt) {
                                        buildString {
                                            if (trip.dataSource.isNotBlank()) append(trip.dataSource)
                                            if (trip.dataCheckedAt > 0) {
                                                val checked = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).apply {
                                                    timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                                                }.format(java.util.Date(trip.dataCheckedAt))
                                                if (isNotEmpty()) append(" · ")
                                                append("查询于 $checked")
                                            }
                                        }
                                    }
                                    Text(
                                        text = sourceLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TimetableTable(
                        trip = trip,
                        boardingIndex = boardingIdx,
                        alightingIndex = alightingIdx,
                        isCompleted = isCompleted,
                        now = now
                    )

                    if (trip.stops.isEmpty() && onRefreshTimetable != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRefreshTimetable,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "联网查询完整沿途经停站与停车时长")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            // 5. 底部操作栏: 12306 详情短链 + 灵动岛 + 刷新时刻 + 归档 + 删除
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trip.detailUrl.isNotBlank()) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.clickable {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(trip.detailUrl)).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    ) {
                        Text(
                            text = "12306 官方凭证 ↗",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }

                if (onToggleIsland != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isIslandRunning) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = if (isIslandRunning) BorderStroke(1.dp, Color(0xFFFCA5A5)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onToggleIsland() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            val islandBtnLabel = when {
                                isIslandRunning -> "关闭灵动岛"
                                isCompleted -> "预览灵动岛"
                                else -> "开启灵动岛"
                            }
                            Text(
                                text = islandBtnLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isIslandRunning) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (isIslandRunning) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDC2626))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (onRefreshTimetable != null) {
                    IconButton(onClick = onRefreshTimetable) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新途经时刻表与列车资料",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (!isCompleted) {
                    IconButton(onClick = onArchive) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = "归档行程",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除行程",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    if (showEditGateDialog && onUpdateGate != null) {
        AlertDialog(
            onDismissRequest = { showEditGateDialog = false },
            title = {
                Text(
                    text = "修改检票口",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "${trip.trainCode} · ${trip.departureStation} → ${trip.arrivalStation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingGateText,
                        onValueChange = { editingGateText = it },
                        label = { Text("检票口编号") },
                        placeholder = { Text("如 17A、17A/B、1A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "快捷输入：",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("A", "B", "A/B", "1A", "1B").forEach { suffix ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    editingGateText = if (editingGateText.isNotBlank() && editingGateText.all { it.isDigit() }) {
                                        editingGateText + suffix
                                    } else {
                                        suffix
                                    }
                                }
                            ) {
                                Text(
                                    text = suffix,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (onRefreshTimetable != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                showEditGateDialog = false
                                onRefreshTimetable()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("从 12306 / 路路通重新拉取")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditGateDialog = false
                        onUpdateGate(editingGateText.trim())
                    }
                ) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGateDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun TrainInfoGridItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 全卡面 6 列式途经时刻与累计里程表格组件 (站序 | 车站 | 到点 | 开点 | 停时 | 里程)
 */
@Composable
fun TimetableTable(
    trip: Trip,
    boardingIndex: Int = -1,
    alightingIndex: Int = -1,
    isCompleted: Boolean,
    now: Long = System.currentTimeMillis()
) {
    val stopList = remember(trip.stops, trip.departureStation, trip.arrivalStation, trip.departureTime, trip.arrivalTime) {
        if (trip.stops.isNotEmpty()) {
            trip.stops
        } else {
            listOf(
                StopInfo(
                    stationNo = "01",
                    stationName = trip.departureStation,
                    arriveTime = "----",
                    startTime = if (trip.departureTime.isNotBlank() && trip.departureTime != "00:00") trip.departureTime else "--:--",
                    stopoverTime = "----"
                ),
                StopInfo(
                    stationNo = "02",
                    stationName = trip.arrivalStation,
                    arriveTime = if (trip.arrivalTime.isNotBlank() && trip.arrivalTime != "00:00") trip.arrivalTime else "--:--",
                    startTime = "----",
                    stopoverTime = "----"
                )
            )
        }
    }
    val hasAnyMileage = remember(stopList) { stopList.any { it.mileageKm != null } }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 表头 (6 列: 站序、车站、到点、开点、停时、里程)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "站序",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.10f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "车站",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(if (hasAnyMileage) 0.28f else 0.34f),
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "到点",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.17f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "开点",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.17f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "停时",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.14f),
                        textAlign = TextAlign.Center
                    )
                    if (hasAnyMileage) {
                        Text(
                            text = "里程",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(0.14f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            val position = trip.currentPosition(now)
            stopList.forEachIndexed { index, stop ->
                val isBoarding = index == boardingIndex
                val isAlighting = index == alightingIndex
                val isHit = position != null && position.index == index
                val isPassed = position != null && index < position.index
                val isKeyStop = isBoarding || isAlighting || isHit
                val isInRideSpan = boardingIndex in 0 until alightingIndex && index in (boardingIndex + 1) until alightingIndex

                val rowBg = when {
                    isHit && !isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    isKeyStop -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isCompleted) 0.14f else 0.20f)
                    isInRideSpan -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isCompleted) 0.05f else 0.08f)
                    index % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                    else -> Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .padding(horizontal = 6.dp, vertical = 6.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 站序
                    val seqColor = when {
                        isPassed -> Color(0xFF94A3B8).copy(alpha = 0.5f)
                        isKeyStop -> Color(0xFF0284C7)
                        else -> Color(0xFF94A3B8)
                    }
                    Text(
                        text = stop.stationNo.padStart(2, '0'),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = seqColor,
                        fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(0.10f),
                        textAlign = TextAlign.Center
                    )

                    // 2. 车站名 + 胶囊徽标 + 站台副行
                    Column(
                        modifier = Modifier.weight(if (hasAnyMileage) 0.28f else 0.34f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val nameColor = when {
                                isPassed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                isKeyStop -> Color(0xFF0284C7)
                                isCompleted -> Color(0xFF64748B)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = stop.stationName,
                                fontSize = 12.5.sp,
                                fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Medium,
                                color = nameColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isHit && !isCompleted) {
                                Spacer(modifier = Modifier.width(3.dp))
                                val badgeText = if (position.atStation) "当前" else "下一站"
                                val badgeBg = if (position.atStation) Color(0xFFECFDF5) else Color(0xFFEFF6FF)
                                val badgeFg = if (position.atStation) Color(0xFF0F766E) else Color(0xFF0284C7)
                                Surface(shape = RoundedCornerShape(4.dp), color = badgeBg) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 8.5.sp,
                                        color = badgeFg,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else if (isBoarding) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEFF6FF)) {
                                    Text(
                                        text = "上车",
                                        fontSize = 8.5.sp,
                                        color = Color(0xFF0284C7),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else if (isAlighting) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFECFDF5)) {
                                    Text(
                                        text = "下车",
                                        fontSize = 8.5.sp,
                                        color = Color(0xFF059669),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        if (stop.platform.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                val platTint = when {
                                    isPassed -> Color(0xFF94A3B8).copy(alpha = 0.5f)
                                    stop.platformReference -> Color(0xFFD97706)
                                    else -> Color(0xFF0284C7)
                                }
                                val platClean = stop.platform.removeSuffix("站台")
                                val platText = if (stop.platformReference) "${platClean}站台(参)" else "${platClean}站台"
                                Text(
                                    text = platText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = platTint,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 3. 到点
                    val isOriginStop = index == 0 || stop.arriveTime == "----" || stop.arriveTime == "--:--" || stop.arriveTime.isBlank()
                    val arriveDisplay = if (isOriginStop) "始发" else stop.arriveTime
                    val timeColor = when {
                        isPassed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        isKeyStop -> Color(0xFF0284C7)
                        isCompleted -> Color(0xFF64748B)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = arriveDisplay,
                        fontSize = 11.5.sp,
                        fontFamily = if (isOriginStop) FontFamily.Default else FontFamily.Monospace,
                        fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOriginStop && !isKeyStop) Color(0xFF94A3B8) else timeColor,
                        modifier = Modifier.weight(0.17f),
                        textAlign = TextAlign.Center
                    )

                    // 4. 开点
                    val isTerminalStop = index == stopList.lastIndex || stop.startTime == "----" || stop.startTime == "--:--" || stop.startTime.isBlank()
                    val startDisplay = if (isTerminalStop) "终到" else stop.startTime
                    Text(
                        text = startDisplay,
                        fontSize = 11.5.sp,
                        fontFamily = if (isTerminalStop) FontFamily.Default else FontFamily.Monospace,
                        fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Normal,
                        color = if (isTerminalStop && !isKeyStop) Color(0xFF94A3B8) else timeColor,
                        modifier = Modifier.weight(0.17f),
                        textAlign = TextAlign.Center
                    )

                    // 5. 停时 (彻底修复 ----分 与冗长文字)
                    val dwellDisplay = formatCleanDwell(stop.stopoverTime, isOriginStop, isTerminalStop)
                    val dwellColor = when {
                        isPassed -> Color(0xFF94A3B8).copy(alpha = 0.45f)
                        isKeyStop && dwellDisplay != "—" -> Color(0xFF0284C7)
                        else -> Color(0xFF94A3B8)
                    }
                    Text(
                        text = dwellDisplay,
                        fontSize = 11.sp,
                        color = dwellColor,
                        fontWeight = if (isKeyStop && dwellDisplay != "—") FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(0.14f),
                        textAlign = TextAlign.Center
                    )

                    // 6. 里程
                    if (hasAnyMileage) {
                        val kmText = stop.mileageKm?.let { "${it.toInt()}" } ?: "—"
                        Text(
                            text = kmText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isKeyStop) Color(0xFF0284C7) else Color(0xFF94A3B8),
                            fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(0.14f),
                            textAlign = TextAlign.End
                        )
                    }
                }

                if (index < stopList.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

private fun formatCleanDwell(raw: String, isOrigin: Boolean, isTerminal: Boolean): String {
    if (isOrigin || isTerminal) return "—"
    val s = raw.trim()
    if (s.isEmpty() || s.contains('-') || s == "始发" || s == "终到") return "—"
    val digits = s.filter { it.isDigit() }.toIntOrNull() ?: return "—"
    if (digits <= 0) return "—"
    return "${digits}分"
}

/**
 * 格式化日期为友好显示（例如 10月29日 周四）
 */
fun formatDisplayDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val date = sdf.parse(dateStr) ?: return dateStr
        val outSdf = SimpleDateFormat("M月d日 E", Locale.CHINA)
        outSdf.format(date)
    } catch (_: Exception) {
        dateStr
    }
}

/**
 * 计算发到区间时长（例如 4时28分）
 */
fun calculateDuration(depTime: String, arrTime: String): String {
    if (depTime.isBlank() || depTime == "00:00" || depTime == "--:--" || arrTime.isBlank() || arrTime == "--:--" || arrTime == "00:00") return ""
    return try {
        val depParts = depTime.split(":")
        val arrParts = arrTime.split(":")
        val depMin = depParts[0].toInt() * 60 + depParts[1].toInt()
        val arrMin = arrParts[0].toInt() * 60 + arrParts[1].toInt()
        var totalMin = arrMin - depMin
        if (totalMin < 0) totalMin += 24 * 60
        val hours = totalMin / 60
        val mins = totalMin % 60
        when {
            hours > 0 && mins > 0 -> "${hours}时${mins}分"
            hours > 0 -> "${hours}小时"
            mins > 0 -> "${mins}分钟"
            else -> ""
        }
    } catch (_: Exception) {
        ""
    }
}

/**
 * 获取车次分类（高铁、动车、城际、快速等）
 */
fun getTrainCategory(trainCode: String): String {
    val prefix = trainCode.firstOrNull()?.uppercaseChar()
    return when (prefix) {
        'G' -> "高铁"
        'D' -> "动车"
        'C' -> "城际"
        'Z' -> "直达"
        'T' -> "特快"
        'K' -> "快速"
        'Y' -> "旅游"
        else -> "列车"
    }
}
