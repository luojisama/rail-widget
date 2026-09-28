package team.shiro.railwidget.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    initialCloudUrl: String,
    initialCloudUser: String,
    initialCloudPass: String,
    initialImapHost: String,
    initialImapPort: String,
    initialImapUser: String,
    initialImapPass: String,
    initialImapSsl: Boolean,
    initialIslandAutoLaunch: Boolean = true,
    initialIslandLeadMinutes: Long = 60L,
    initialIslandTransferHandover: Boolean = true,
    initialIslandAutoClose: Boolean = true,
    onDismiss: () -> Unit,
    onSaveSettings: (
        mode: Int, // 0 for CloudMail, 1 for IMAP
        cloudUrl: String,
        cloudUser: String,
        cloudPass: String,
        imapHost: String,
        imapPort: String,
        imapUser: String,
        imapPass: String,
        imapSsl: Boolean,
        islandAutoLaunch: Boolean,
        islandLeadMinutes: Long,
        islandTransferHandover: Boolean,
        islandAutoClose: Boolean
    ) -> Unit
) {
    var selectedCategoryTab by remember { mutableIntStateOf(0) } // 0: 灵动岛自动化, 1: 邮箱同步

    // 灵动岛自动化配置状态
    var islandAutoLaunch by remember { mutableStateOf(initialIslandAutoLaunch) }
    var islandLeadMinutes by remember { mutableLongStateOf(initialIslandLeadMinutes) }
    var islandTransferHandover by remember { mutableStateOf(initialIslandTransferHandover) }
    var islandAutoClose by remember { mutableStateOf(initialIslandAutoClose) }

    // 邮箱配置状态
    var mailMode by remember { mutableIntStateOf(0) } // 0: Cloud Mail, 1: IMAP
    var cloudUrl by remember { mutableStateOf(initialCloudUrl) }
    var cloudUser by remember { mutableStateOf(initialCloudUser) }
    var cloudPass by remember { mutableStateOf(initialCloudPass) }

    var imapHost by remember { mutableStateOf(initialImapHost) }
    var imapPort by remember { mutableStateOf(initialImapPort) }
    var imapUser by remember { mutableStateOf(initialImapUser) }
    var imapPass by remember { mutableStateOf(initialImapPass) }
    var imapSsl by remember { mutableStateOf(initialImapSsl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("应用设置", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PrimaryTabRow(
                    selectedTabIndex = selectedCategoryTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedCategoryTab == 0,
                        onClick = { selectedCategoryTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LightningBoltIcon(
                                    modifier = Modifier.size(13.dp),
                                    tint = if (selectedCategoryTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("灵动岛自动化")
                            }
                        }
                    )
                    Tab(
                        selected = selectedCategoryTab == 1,
                        onClick = { selectedCategoryTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selectedCategoryTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("邮箱同步")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedCategoryTab == 0) {
                    // TAB 0: 灵动岛自动化
                    // 1. 自动拉起总开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "发车前自动拉起灵动岛",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "临近发车时在屏幕顶端弹出实时胶囊",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = islandAutoLaunch,
                            onCheckedChange = { islandAutoLaunch = it }
                        )
                    }

                    if (islandAutoLaunch) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "提前拉起时间：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val leadOptions = listOf(
                            Pair("15分钟", 15L),
                            Pair("30分钟", 30L),
                            Pair("1小时 (推荐)", 60L),
                            Pair("2小时", 120L),
                            Pair("当天0点", 0L)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            leadOptions.forEach { (label, minutes) ->
                                FilterChip(
                                    selected = islandLeadMinutes == minutes,
                                    onClick = { islandLeadMinutes = minutes },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // 2. 中转换乘自动识别与接力
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "智能中转换乘接力",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "第一程到站后不关闭，平滑接力下一程车次并更新检票口",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = islandTransferHandover,
                            onCheckedChange = { islandTransferHandover = it }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // 3. 到站后自动关闭
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "到站后自动安全关闭",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "单程到站后留出 15 分钟出站缓冲，随后自动安全退出",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = islandAutoClose,
                            onCheckedChange = { islandAutoClose = it }
                        )
                    }

                } else {
                    // TAB 1: 邮箱同步
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = mailMode == 0,
                            onClick = { mailMode = 0 },
                            label = { Text("Cloud Mail 自建") }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = mailMode == 1,
                            onClick = { mailMode = 1 },
                            label = { Text("通用 IMAP") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (mailMode == 0) {
                        Text("针对 Cloudflare Workers Serverless 邮箱：")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cloudUrl,
                            onValueChange = { cloudUrl = it },
                            label = { Text("服务地址 (URL)") },
                            placeholder = { Text("https://mail.example.com") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cloudUser,
                            onValueChange = { cloudUser = it },
                            label = { Text("邮箱账号") },
                            placeholder = { Text("user@example.com") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cloudPass,
                            onValueChange = { cloudPass = it },
                            label = { Text("密码") },
                            placeholder = { Text("输入邮箱密码") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("针对 QQ、163、Gmail 等标准协议邮箱：")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imapHost,
                            onValueChange = { imapHost = it },
                            label = { Text("IMAP 服务器") },
                            placeholder = { Text("imap.qq.com") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imapPort,
                            onValueChange = { imapPort = it },
                            label = { Text("端口") },
                            placeholder = { Text("993") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imapUser,
                            onValueChange = { imapUser = it },
                            label = { Text("用户名 / 邮箱地址") },
                            placeholder = { Text("user@qq.com") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imapPass,
                            onValueChange = { imapPass = it },
                            label = { Text("授权码 / 密码") },
                            placeholder = { Text("邮箱授权码") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("启用 SSL/TLS", modifier = Modifier.weight(1f))
                            Switch(checked = imapSsl, onCheckedChange = { imapSsl = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(
                        mailMode,
                        cloudUrl,
                        cloudUser,
                        cloudPass,
                        imapHost,
                        imapPort,
                        imapUser,
                        imapPass,
                        imapSsl,
                        islandAutoLaunch,
                        islandLeadMinutes,
                        islandTransferHandover,
                        islandAutoClose
                    )
                }
            ) {
                Text("保存设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
