package team.shiro.railwidget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.shiro.railwidget.data.model.StopInfo
import team.shiro.railwidget.data.model.Trip
import team.shiro.railwidget.data.model.TripStage
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HeroTripCard(
    trip: Trip,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onRefreshTimetable: (() -> Unit)? = null,
    onUpdateGate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stage = trip.getStage()
    val isCompleted = stage == TripStage.COMPLETED
    val isInTransit = stage == TripStage.IN_TRANSIT
    // 历史已结束行程默认折叠表格，避免大表格霸屏；未出行或进行中行程可展开浏览
    var isExpandedTable by remember(trip.orderNo) { mutableStateOf(!isCompleted && trip.stops.size > 2) }
    var showEditGateDialog by remember { mutableStateOf(false) }
    var editingGateText by remember(trip.ticketGate) { mutableStateOf(trip.ticketGate.removePrefix("检票口")) }

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

            // 1. 顶部车票票头: 车次徽标 + 车型 + 日期与星期 + 乘车人 + 状态指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 车次大徽标
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

                Spacer(modifier = Modifier.width(10.dp))

                // 乘车日期 (含星期) 与 乘车人
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDisplayDate(trip.departureDate),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "乘车人 · ${trip.passengerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 状态指示药丸
                val statusText = when {
                    isCompleted -> "已结束"
                    isInTransit -> "运行中"
                    else -> trip.computeStatus()
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. 主行程发到站与时刻 (大号粗体时刻 + 矢量运行路线历时)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 出发站与时间
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = trip.departureTime,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
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

                // 中间运行图示与历时
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val durationText = calculateDuration(trip.departureTime, trip.arrivalTime)
                    if (durationText.isNotBlank()) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }

                    // 轨迹指示器
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
                                .width(56.dp)
                                .height(2.dp)
                                .background(Color(0xFFCBD5E1))
                        )
                        Icon(
                            imageVector = Icons.Default.DirectionsTransit,
                            contentDescription = null,
                            tint = if (isCompleted) Color(0xFF94A3B8) else if (isInTransit) Color(0xFF0F766E) else Color(0xFF0284C7),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    val stopsCount = if (trip.stops.isNotEmpty()) "经停 ${trip.stops.size} 站" else "区间车次"
                    Text(
                        text = stopsCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                // 到达站与时间
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    val arrTime = if (trip.arrivalTime.isNotBlank()) trip.arrivalTime else "--:--"
                    Text(
                        text = arrTime,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
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
                    // 车厢与座位
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

                    // 席别
                    Column(modifier = Modifier.weight(0.9f)) {
                        Text(
                            text = "席别",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = trip.seatType,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 检票口 (醒目高亮，支持点击快速修改或从大屏重查)
                    val gateCode = trip.getCleanTicketGate()
                    val hasGate = gateCode.isNotBlank() && gateCode != "暂无"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isCompleted -> Color(0xFFF1F5F9)
                            hasGate -> Color(0xFFFEF2F2)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = if (!hasGate && !isCompleted) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null,
                        modifier = Modifier.clickable(enabled = onUpdateGate != null && !isCompleted) {
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
                                    color = if (isCompleted) Color(0xFF64748B) else if (hasGate) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (onUpdateGate != null && !isCompleted) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "修改检票口",
                                        tint = if (hasGate) Color(0xFFDC2626).copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (hasGate) gateCode else "点此录入",
                                fontSize = if (hasGate) 16.sp else 12.sp,
                                fontWeight = if (hasGate) FontWeight.Black else FontWeight.Bold,
                                color = if (isCompleted) Color(0xFF64748B) else if (hasGate) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 【核心需求】途径车站与停车时间表格折叠/展开栏
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
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (isCompleted) Color(0xFF94A3B8) else Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val tableTitle = if (trip.stops.isNotEmpty()) {
                        "沿途经停站与停车时间表 (全线共 ${trip.stops.size} 站)"
                    } else {
                        "沿途经停站时刻表 (区间两站)"
                    }
                    Text(
                        text = tableTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCompleted) Color(0xFF64748B) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (isExpandedTable) "收起" else "展开",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (isExpandedTable) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            AnimatedVisibility(visible = isExpandedTable) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    TimetableTable(
                        trip = trip,
                        isCompleted = isCompleted
                    )

                    // 若尚未联网补齐多站时刻表，提供一键补全按钮
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

            // 5. 底部操作栏: 12306 详情短链 + 刷新时刻 + 归档 + 删除
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

                Spacer(modifier = Modifier.weight(1f))

                if (onRefreshTimetable != null) {
                    IconButton(onClick = onRefreshTimetable) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新途经时刻表",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (!isCompleted) {
                    IconButton(onClick = onArchive) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = "归档行程",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除行程",
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
                        text = "${trip.trainCode} · ${trip.departureStation} ➔ ${trip.arrivalStation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingGateText,
                        onValueChange = { editingGateText = it },
                        label = { Text("检票口编号") },
                        placeholder = { Text("如 17A、17A/B、A5") },
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
                            Text("从 12306 官方重新拉取检票口")
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

/**
 * 全卡面规整途经时刻表格组件 (Table)
 */
@Composable
fun TimetableTable(
    trip: Trip,
    isCompleted: Boolean
) {
    // 边框容器
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 表头 (5 列: 站序、车站、到达、发车、停留)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "站序",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.12f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "车站",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.32f),
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "到达",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.20f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "发车",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.20f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "停留",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(0.16f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // 表体数据行
            val stopList = if (trip.stops.isNotEmpty()) {
                trip.stops
            } else {
                // 兜底基于出发站与到达站构建标准两站行
                listOf(
                    StopInfo(
                        stationNo = "01",
                        stationName = trip.departureStation,
                        arriveTime = "----",
                        startTime = trip.departureTime,
                        stopoverTime = "始发"
                    ),
                    StopInfo(
                        stationNo = "02",
                        stationName = trip.arrivalStation,
                        arriveTime = trip.arrivalTime.ifBlank { "--:--" },
                        startTime = "----",
                        stopoverTime = "终到"
                    )
                )
            }

            stopList.forEachIndexed { index, stop ->
                val isBoarding = stop.stationName.startsWith(trip.departureStation) || trip.departureStation.startsWith(stop.stationName)
                val isAlighting = stop.stationName.startsWith(trip.arrivalStation) || trip.arrivalStation.startsWith(stop.stationName)
                val isKeyStop = isBoarding || isAlighting

                val rowBg = when {
                    isKeyStop && !isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                    index % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    else -> Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 站序
                    Text(
                        text = stop.stationNo.padStart(2, '0'),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isKeyStop && !isCompleted) Color(0xFF0284C7) else Color(0xFF94A3B8),
                        fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(0.12f),
                        textAlign = TextAlign.Center
                    )

                    // 车站名 (出发/到达标注 [上车] / [下车] 胶囊)
                    Row(
                        modifier = Modifier.weight(0.32f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stop.stationName,
                            fontSize = 13.sp,
                            fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isKeyStop && !isCompleted -> Color(0xFF0284C7)
                                isCompleted -> Color(0xFF64748B)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isBoarding && !isCompleted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEFF6FF)
                            ) {
                                Text(
                                    text = "上车",
                                    fontSize = 9.sp,
                                    color = Color(0xFF0284C7),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        } else if (isAlighting && !isCompleted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFECFDF5)
                            ) {
                                Text(
                                    text = "下车",
                                    fontSize = 9.sp,
                                    color = Color(0xFF059669),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // 到站时间
                    val arriveDisplay = when {
                        stop.arriveTime == "----" || stop.arriveTime.isBlank() -> "始发"
                        else -> stop.arriveTime
                    }
                    Text(
                        text = arriveDisplay,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Normal,
                        color = if (isKeyStop && !isCompleted) Color(0xFF0284C7) else if (isCompleted) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.20f),
                        textAlign = TextAlign.Center
                    )

                    // 发车时间
                    val startDisplay = when {
                        stop.startTime == "----" || stop.startTime.isBlank() -> "终到"
                        else -> stop.startTime
                    }
                    Text(
                        text = startDisplay,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isKeyStop) FontWeight.Bold else FontWeight.Normal,
                        color = if (isKeyStop && !isCompleted) Color(0xFF0284C7) else if (isCompleted) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.20f),
                        textAlign = TextAlign.Center
                    )

                    // 停留时长
                    val dwellDisplay = when {
                        stop.stopoverTime == "始发" || stop.stopoverTime == "终到" -> "--"
                        stop.stopoverTime.isNotBlank() -> {
                            if (stop.stopoverTime.endsWith("分") || stop.stopoverTime.endsWith("分钟")) {
                                "停${stop.stopoverTime}"
                            } else {
                                "${stop.stopoverTime}分"
                            }
                        }
                        else -> "--"
                    }
                    Text(
                        text = dwellDisplay,
                        fontSize = 11.sp,
                        color = if (isKeyStop && !isCompleted) Color(0xFF0284C7) else Color(0xFF94A3B8),
                        fontWeight = if (isKeyStop) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(0.16f),
                        textAlign = TextAlign.Center
                    )
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

/**
 * 格式化日期为友好显示（例如 2026年9月25日 周五）
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
    if (depTime.isBlank() || arrTime.isBlank()) return ""
    return try {
        val depParts = depTime.split(":")
        val arrParts = arrTime.split(":")
        val depMin = depParts[0].toInt() * 60 + depParts[1].toInt()
        val arrMin = arrParts[0].toInt() * 60 + arrParts[1].toInt()
        var totalMin = arrMin - depMin
        if (totalMin < 0) totalMin += 24 * 60 // 跨日
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
