package team.shiro.railwidget.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.shiro.railwidget.sync.SpeedTestResult
import team.shiro.railwidget.sync.UpdateChecker
import team.shiro.railwidget.sync.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    onDismiss: () -> Unit,
    onDownloadAndInstall: (preferredUrl: String) -> Unit,
    onOpenBrowser: (url: String) -> Unit
) {
    var speedTestResult by remember { mutableStateOf<SpeedTestResult?>(null) }
    var isTestingSpeed by remember { mutableStateOf(true) }

    val formattedNotes = remember(updateInfo.releaseNotes) {
        UpdateChecker.formatReleaseNotes(updateInfo.releaseNotes)
    }

    // 自动并发测速，选择响应最快线路
    LaunchedEffect(updateInfo.downloadUrl) {
        isTestingSpeed = true
        val result = UpdateChecker.selectFastestMirror(updateInfo.downloadUrl)
        speedTestResult = result
        isTestingSpeed = false
    }

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "发现新版本 v${updateInfo.latestVersion}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "更新内容：",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // 清洗为纯文本自然段落排版展示，彻底告别 Markdown 标记杂乱
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formattedNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 自动测速线路卡片
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTestingSpeed) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    } else {
                        Color(0xFFE8F5E9)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTestingSpeed) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "正在测速多条下载线路...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "并发探测国内镜像与原链延迟",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                val nodeName = speedTestResult?.nodeName ?: "默认极速线路"
                                val pingText = speedTestResult?.let { if (it.pingMs < 2500) "${it.pingMs}ms" else "可用" } ?: ""
                                Text(
                                    text = "已自动优选最快线路：$nodeName",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    text = "网络往返延迟 $pingText · 支持自动故障转移",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF388E3C)
                                )
                            }
                        }
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (downloadProgress > 0) "正在高速下载更新安装包: $downloadProgress%" else "正在连接测速最优线路，请稍候...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (downloadProgress > 0) downloadProgress / 100f else 0.1f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(onClick = {
                    val targetUrl = speedTestResult?.fullUrl ?: updateInfo.downloadUrl
                    onDownloadAndInstall(targetUrl)
                }) {
                    Text("立即升级")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row {
                    OutlinedButton(onClick = {
                        val targetUrl = speedTestResult?.fullUrl ?: updateInfo.downloadUrl
                        onOpenBrowser(targetUrl)
                    }) {
                        Text("网页下载")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = onDismiss) {
                        Text("稍后")
                    }
                }
            }
        }
    )
}
