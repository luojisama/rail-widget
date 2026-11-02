package team.shiro.railwidget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MarkdownReleaseNotes(
                        markdown = updateInfo.releaseNotes,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isTestingSpeed) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    } else {
                        Color(0xFFE8F5E9)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTestingSpeed) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在检测下载线路…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val nodeName = speedTestResult?.nodeName ?: "默认线路"
                            val pingText = speedTestResult?.let { if (it.pingMs < 2500) " (${it.pingMs}ms)" else "" }.orEmpty()
                            Text(
                                text = "下载线路：$nodeName$pingText",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (downloadProgress > 0) "正在下载更新：$downloadProgress%" else "正在连接下载服务器…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (downloadProgress > 0) downloadProgress / 100f else 0.05f },
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
                    Text("立即更新")
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
                        Text("浏览器下载")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            }
        }
    )
}

internal sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class BulletItem(val indent: Int, val text: String) : MdBlock()
    data class NumberedItem(val number: String, val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data object Divider : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

internal fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    if (raw.isBlank()) return listOf(MdBlock.Paragraph("暂无更新说明"))
    val blocks = mutableListOf<MdBlock>()
    for (line in raw.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        when {
            trimmed.matches(Regex("^[-*_]{3,}$")) -> blocks += MdBlock.Divider
            trimmed.startsWith("####") -> blocks += MdBlock.Heading(4, trimmed.removePrefix("####").trim())
            trimmed.startsWith("###") -> blocks += MdBlock.Heading(3, trimmed.removePrefix("###").trim())
            trimmed.startsWith("##") -> blocks += MdBlock.Heading(2, trimmed.removePrefix("##").trim())
            trimmed.startsWith("#") -> blocks += MdBlock.Heading(1, trimmed.removePrefix("#").trim())
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                val leadingSpaces = line.takeWhile { it == ' ' || it == '\t' }.length
                val indentLevel = (leadingSpaces / 2).coerceIn(0, 2)
                blocks += MdBlock.BulletItem(indentLevel, trimmed.substring(2).trim())
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val dotIdx = trimmed.indexOf('.')
                val num = trimmed.substring(0, dotIdx + 1)
                val content = trimmed.substring(dotIdx + 1).trim()
                blocks += MdBlock.NumberedItem(num, content)
            }
            trimmed.startsWith(">") -> blocks += MdBlock.Quote(trimmed.removePrefix(">").trim())
            else -> blocks += MdBlock.Paragraph(trimmed)
        }
    }
    return blocks
}

internal fun parseInlineMarkdown(
    text: String,
    boldColor: Color,
    codeColor: Color,
    codeBg: Color
): AnnotatedString = buildAnnotatedString {
    val tokenRegex = Regex("""(\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\)|\*([^*]+)\*)""")
    var lastIndex = 0
    for (match in tokenRegex.findAll(text)) {
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        val boldText = match.groups[2]?.value
        val codeText = match.groups[3]?.value
        val linkLabel = match.groups[4]?.value
        val italicText = match.groups[6]?.value
        when {
            boldText != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                append(boldText)
            }
            codeText != null -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = codeColor,
                    background = codeBg,
                    fontSize = 11.5.sp
                )
            ) {
                append(" $codeText ")
            }
            linkLabel != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = codeColor)) {
                append(linkLabel)
            }
            italicText != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(italicText)
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

@Composable
fun MarkdownReleaseNotes(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (block.level <= 3) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(if (block.level <= 2) 15.dp else 13.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = parseInlineMarkdown(block.text, onSurface, primary, codeBg),
                            fontSize = when (block.level) {
                                1 -> 15.sp
                                2 -> 14.sp
                                else -> 13.sp
                            },
                            fontWeight = FontWeight.Bold,
                            color = onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MdBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indent * 12).dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (block.indent == 0) "•" else "◦",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                            lineHeight = 19.sp,
                            modifier = Modifier.width(12.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, onSurface, primary, codeBg),
                            fontSize = 12.5.sp,
                            color = onSurfaceVariant,
                            lineHeight = 19.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.number,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                            lineHeight = 19.sp,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, onSurface, primary, codeBg),
                            fontSize = 12.5.sp,
                            color = onSurfaceVariant,
                            lineHeight = 19.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MdBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(16.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, onSurface, primary, codeBg),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            lineHeight = 18.sp
                        )
                    }
                }
                is MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, onSurface, primary, codeBg),
                        fontSize = 12.5.sp,
                        color = onSurfaceVariant,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
