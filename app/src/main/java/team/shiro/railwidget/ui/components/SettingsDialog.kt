package team.shiro.railwidget.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    onDismiss: () -> Unit,
    onSaveAndSync: (
        mode: Int, // 0 for CloudMail, 1 for IMAP
        cloudUrl: String,
        cloudUser: String,
        cloudPass: String,
        imapHost: String,
        imapPort: String,
        imapUser: String,
        imapPass: String,
        imapSsl: Boolean
    ) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0: Cloud Mail, 1: IMAP

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
        title = { Text("邮箱同步设置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text("Cloud Mail 自建") }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text("通用 IMAP") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (mode == 0) {
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
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveAndSync(
                        mode,
                        cloudUrl,
                        cloudUser,
                        cloudPass,
                        imapHost,
                        imapPort,
                        imapUser,
                        imapPass,
                        imapSsl
                    )
                }
            ) {
                Text("保存并同步")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
