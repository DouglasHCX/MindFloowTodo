package com.example.mindflow.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSystemSettings: () -> Unit // 跳转动作由外部传入
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 这里是入口
            ListItem(
                headlineContent = { Text("系统设置") },
                supportingContent = { Text("调整字体大小、通知权限") },
                leadingContent = {
                    Icon(Icons.Default.Settings, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    // 点击时执行跳转
                    onNavigateToSystemSettings()
                }
            )

            HorizontalDivider()

            // 下面可以放其他的设置项，比如“关于我们”
            ListItem(
                headlineContent = { Text("关于应用") },
                supportingContent = { Text("版本 1.0.0") }
            )
        }
    }
}