package com.example.mindflow.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mindflow.TodoEvent
import com.example.mindflow.TodoState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    state: TodoState,
    onBackClick: () -> Unit,
    onEvent: (TodoEvent) -> Unit,
    onExportData: () -> Unit
) {
    val context = LocalContext.current

    // 1. 头像选择器
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flag)
            onEvent(TodoEvent.UpdateAvatar(uri.toString()))
        }
    }

    // 2. 数据恢复选择器
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onEvent(TodoEvent.RestoreData(uri))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- 1. 个人设置 ---
            SettingsGroupTitle("个人个性化")

            ListItem(
                headlineContent = { Text("更换头像") },
                supportingContent = { Text("点击选择一张你喜欢的图片") },
                leadingContent = {
                    if (state.avatarUri != null) {
                        AsyncImage(
                            model = state.avatarUri,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Face, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable {
                    avatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )

            HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))

            // --- 2. 显示设置 ---
            SettingsGroupTitle("显示与外观")

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FormatSize, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("字体大小", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${String.format("%.1f", state.appFontScale)}x", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = state.appFontScale,
                    onValueChange = { onEvent(TodoEvent.UpdateFontScale(it)) },
                    valueRange = 0.8f..1.3f,
                    steps = 4,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "预览：字体大小测试 Preview Text",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))

            // --- 3. 权限与隐私 ---
            SettingsGroupTitle("权限管理")

            SettingsItem(
                icon = Icons.Outlined.Notifications,
                title = "通知权限",
                subtitle = "前往系统设置开启/关闭提醒",
                onClick = { openAppNotificationSettings(context) }
            )

            HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))

            // --- 4. 数据安全 ---
            SettingsGroupTitle("数据安全")

            SettingsItem(
                icon = Icons.Outlined.CloudDownload,
                title = "备份数据",
                subtitle = "导出 JSON 文件到本地",
                onClick = onExportData
            )

            SettingsItem(
                icon = Icons.Outlined.CloudUpload,
                title = "恢复数据",
                subtitle = "从 JSON 文件导入数据",
                onClick = { restoreLauncher.launch(arrayOf("application/json")) }
            )
        }
    }
}

@Composable
fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = Color.Gray) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

fun openAppNotificationSettings(context: Context) {
    val intent = Intent().apply {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            else -> {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }
    context.startActivity(intent)
}