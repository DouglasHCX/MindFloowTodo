package com.example.mindflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle // ★ 新增引用
import java.util.Locale          // ★ 新增引用

@Composable
fun MeScreen(
    state: TodoState,
    onExportData: () -> Unit,
    onEvent: (TodoEvent) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // 1. 顶部个人资料
        ProfileHeader(avatarUri = state.avatarUri)

        Spacer(modifier = Modifier.height(24.dp))

        // 2. 贡献度热力图
        Text("贡献概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        ContributionHeatmap(todos = state.todos)

        Spacer(modifier = Modifier.height(24.dp))

        // 3. 设置入口
        SettingsEntryCard(onClick = onNavigateToSettings)

        Spacer(modifier = Modifier.height(48.dp))

        // 底部版本号
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "MindFlow v2.5.0 \n Built for Creators",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun SettingsEntryCard(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("系统设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("头像、字体、备份与恢复", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun ProfileHeader(avatarUri: String?) {
    val currentHour = LocalTime.now().hour
    val greeting = when (currentHour) {
        in 5..11 -> "早上好 ☀️"
        in 12..13 -> "中午好 🍚"
        in 14..18 -> "下午好 ☕"
        in 19..23 -> "晚上好 🌙"
        else -> "深夜了，注意休息 💤"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = "保持专注，保持灵感", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

// ★★★ 核心修改：英文标签版热力图 ★★★
@Composable
fun ContributionHeatmap(todos: List<TodoItem>) {
    val today = LocalDate.now()
    val weeksToShow = 15 // 显示多少周
    val currentWeekMonday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val startOfGrid = currentWeekMonday.minusWeeks((weeksToShow - 1).toLong())

    val completedMap = remember(todos) {
        todos.filter { it.isDone && it.category != CATEGORY_IDEA }.groupingBy { todo ->
            val timestamp = todo.completedAt ?: todo.dueDate
            if (timestamp != null) Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.MIN
        }.eachCount()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. 顶部月份标签行
            // 左侧留出星期标签的宽度 (32dp + 4dp 间距 = 36dp)
            // ★ 修改：宽度增加以适配英文
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(36.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (weekIndex in 0 until weeksToShow) {
                        val currentWeekStart = startOfGrid.plusWeeks(weekIndex.toLong())
                        val showMonth = weekIndex == 0 || currentWeekStart.dayOfMonth <= 7

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (showMonth) {
                                // ★ 修改：使用英文月份缩写 (Jan, Feb...)
                                Text(
                                    text = currentWeekStart.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // 2. 左侧星期标签 (Y轴)
                // ★ 修改：宽度改为 32dp 以容纳 "Mon"
                Column(
                    modifier = Modifier.width(32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (day in 1..7) {
                        Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.CenterStart) {
                            // ★ 修改：只显示 Mon, Thu, Sun
                            if (day == 1 || day == 4 || day == 7) {
                                Text(
                                    text = when(day) { 1 -> "Mon"; 4 -> "Thu"; 7 -> "Sun"; else -> "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 3. 右侧热力图网格
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (weekIndex in 0 until weeksToShow) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (dayOfWeek in 1..7) {
                                val currentWeekStart = startOfGrid.plusWeeks(weekIndex.toLong())
                                val cellDate = currentWeekStart.plusDays(dayOfWeek.toLong() - 1)
                                val count = completedMap[cellDate] ?: 0
                                val isFuture = cellDate.isAfter(today)
                                val baseColor = getHeatmapColor(count)

                                val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                val displayColor = if (isFuture) Color.Transparent else if (count == 0) emptyColor else baseColor

                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(displayColor)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 底部图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ★ 修改：Less / More
                Text("Less", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))

                Box(modifier = Modifier.padding(horizontal = 2.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)))
                listOf(2, 4, 6, 8).forEach { level ->
                    Box(modifier = Modifier.padding(horizontal = 2.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(getHeatmapColor(level)))
                }

                Spacer(modifier = Modifier.width(4.dp))
                Text("More", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun getHeatmapColor(count: Int): Color {
    return when {
        count == 0 -> Color.Transparent
        count <= 2 -> Color(0xFF9BE9A8)
        count <= 4 -> Color(0xFF40C463)
        count <= 6 -> Color(0xFF30A14E)
        else -> Color(0xFF216E39)
    }
}