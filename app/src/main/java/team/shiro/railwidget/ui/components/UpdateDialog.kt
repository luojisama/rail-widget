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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.shiro.railwidget.sync.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    onDismiss: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Text(
                text = "发现新版本 v${updateInfo.latestVersion}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = "更新日志：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在下载更新包: $downloadProgress%")
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(onClick = onDownloadAndInstall) {
                    Text("应用内在线更新")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row {
                    OutlinedButton(onClick = onOpenBrowser) {
                        Text("网页下载")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("稍后再说")
                    }
                }
            }
        }
    )
}
