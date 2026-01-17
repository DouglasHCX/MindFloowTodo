package com.example.mindflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// =======================
// 日程概览 - 丝滑滑动版 (AnimatedContent)
// =======================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ScheduleOverviewScreen(
    todos: List<TodoItem>,
    onBack: () -> Unit,
    onEvent: (TodoEvent) -> Unit
) {
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }

    // 弹窗状态管理
    var showDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // 预处理数据：为了性能，这里我们只计算一次，但在 AnimatedContent 内部会根据 targetYearMonth 重新过滤
    // 注意：AnimatedContent 切换时，我们需要确保数据是响应式的
    val allTodos = todos

    // 1. 日程详情弹窗
    if (showDialog && selectedDate != null) {
        val tasksForDay = allTodos.filter {
            it.dueDate != null &&
                    Instant.ofEpochMilli(it.dueDate).atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate
        }
        val sortedTasks = tasksForDay.sortedWith(compareBy({ it.isDone }, { -it.priority }))

        DayTasksDialog(
            date = selectedDate!!,
            tasks = sortedTasks,
            onDismiss = { showDialog = false },
            onEvent = onEvent
        )
    }

    // 2. 年月选择器弹窗
    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYearMonth = currentYearMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { newYearMonth ->
                currentYearMonth = newYearMonth
                showMonthPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日程概览", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. 头部：月份切换 + 星期表头 (这些是固定的，不需要滑动)
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                ScheduleMonthHeader(
                    yearMonth = currentYearMonth,
                    onPrev = { currentYearMonth = currentYearMonth.minusMonths(1) },
                    onNext = { currentYearMonth = currentYearMonth.plusMonths(1) },
                    onTitleClick = { showMonthPicker = true }
                )

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    val weekDays = listOf("一", "二", "三", "四", "五", "六", "日")
                    weekDays.forEachIndexed { index, dayStr ->
                        val isWeekendHeader = index >= 5
                        Text(
                            text = dayStr,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = if (isWeekendHeader) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 2. 核心：日历网格部分 (使用 AnimatedContent 实现左右滑动)
            AnimatedContent(
                targetState = currentYearMonth,
                transitionSpec = {
                    // 判断方向：如果是下个月，从右边进；如果是上个月，从左边进
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }.using(
                        // 大小变化动画 (虽然这里大小基本固定，但加个 Clip 比较保险)
                        SizeTransform(clip = false)
                    )
                },
                label = "CalendarAnimation"
            ) { targetMonth ->
                // 在这里渲染目标月份的日历
                // 注意：这里必须使用 targetMonth，而不是 currentYearMonth，否则动画会有残影

                CalendarGrid(
                    yearMonth = targetMonth,
                    todos = allTodos,
                    onDayClick = { date ->
                        selectedDate = date
                        showDialog = true
                    }
                )
            }
        }
    }
}

// ↓↓↓ 抽离出来的日历网格组件，方便放在 AnimatedContent 中 ↓↓↓
@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    todos: List<TodoItem>,
    onDayClick: (LocalDate) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value // 1 (Mon) - 7 (Sun)
    val startOffset = firstDayOfWeek - 1

    // 针对当前月份过滤任务
    val tasksByDate = remember(todos, yearMonth) {
        todos.filter { it.dueDate != null }
            .groupBy {
                Instant.ofEpochMilli(it.dueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
            }
    }

    val totalSlots = remember(daysInMonth, startOffset) {
        val list = mutableListOf<Int?>()
        repeat(startOffset) { list.add(null) }
        (1..daysInMonth).forEach { list.add(it) }
        while (list.size % 7 != 0) {
            list.add(null)
        }
        list
    }

    val weeks = totalSlots.chunked(7)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (day != null) {
                            val date = yearMonth.atDay(day)
                            val tasks = tasksByDate[date] ?: emptyList()
                            val isToday = date == LocalDate.now()
                            val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

                            ScheduleDayCell(
                                day = day,
                                isToday = isToday,
                                isWeekend = isWeekend,
                                tasks = tasks,
                                onClick = { onDayClick(date) }
                            )
                        } else {
                            Spacer(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

// ↓↓↓ 以下为原有组件，保持不变 ↓↓↓

@Composable
fun ScheduleMonthHeader(
    yearMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, null)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onTitleClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${yearMonth.year}年 ${yearMonth.monthValue}月",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Select Month",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun MonthYearPickerDialog(
    initialYearMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialYearMonth.year) }
    val months = (1..12).toList()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedYear-- }) {
                        Icon(Icons.Default.ChevronLeft, "Prev Year")
                    }
                    Text(
                        text = "$selectedYear 年",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { selectedYear++ }) {
                        Icon(Icons.Default.ChevronRight, "Next Year")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items(months) { month ->
                        val isSelected = month == initialYearMonth.monthValue && selectedYear == initialYearMonth.year

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    onConfirm(YearMonth.of(selectedYear, month))
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "${month}月",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
fun ScheduleDayCell(
    day: Int,
    isToday: Boolean,
    isWeekend: Boolean,
    tasks: List<TodoItem>,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isWeekend -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        isWeekend -> Color.Transparent
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }

    val dateTextColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        isWeekend -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Text(
            text = day.toString(),
            fontSize = 12.sp,
            fontWeight = if (isToday || isWeekend) FontWeight.Bold else FontWeight.Normal,
            color = dateTextColor,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(2.dp))

        val displayTasks = tasks.sortedWith(compareBy({ it.isDone }, { -it.priority })).take(5)

        displayTasks.forEach { todo ->
            TaskTitleChip(todo)
            Spacer(modifier = Modifier.height(2.dp))
        }

        if (tasks.size > 5) {
            Text(
                text = "...",
                fontSize = 8.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                lineHeight = 8.sp
            )
        }
    }
}

@Composable
fun TaskTitleChip(todo: TodoItem) {
    val baseColor = when (todo.priority) {
        3 -> HighPriorityColor
        2 -> MediumPriorityColor
        else -> LowPriorityColor
    }
    val chipColor = if (todo.isDone) baseColor.copy(alpha = 0.6f) else baseColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(chipColor)
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Text(
            text = todo.title,
            color = Color.White,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            lineHeight = 11.sp,
            textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayTasksDialog(
    date: LocalDate,
    tasks: List<TodoItem>,
    onDismiss: () -> Unit,
    onEvent: (TodoEvent) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 48.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("MMM", Locale.CHINA)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("EEE", Locale.CHINA)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .background(Color.White.copy(0.5f), CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    if (tasks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.EventNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "这一天暂时是空白的",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    "去享受生活吧 ☕️",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "任务清单 (${tasks.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(tasks, key = { it.id }) { todo ->
                                ReadOnlyTaskRow(todo, onEvent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadOnlyTaskRow(todo: TodoItem, onEvent: (TodoEvent) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val priorityColor = when (todo.priority) {
        3 -> HighPriorityColor
        2 -> MediumPriorityColor
        else -> LowPriorityColor
    }

    val timeText = if (todo.isDone && todo.completedAt != null) {
        Instant.ofEpochMilli(todo.completedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        ""
    }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { isExpanded = !isExpanded },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                ),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .background(priorityColor)
                    )

                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = todo.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (todo.isDone) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (todo.isDone) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.DoneAll,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "待办",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (todo.content.isNotBlank()) {
                                Text(
                                    text = todo.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            } else {
                                Text(
                                    text = "无详细描述",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray.copy(alpha = 0.5f),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text(if (todo.isDone) "标记为未完成" else "标记为已完成") },
                onClick = {
                    onEvent(TodoEvent.ToggleDone(todo))
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        if (todo.isDone) Icons.Default.Close else Icons.Default.DoneAll,
                        contentDescription = null
                    )
                }
            )
        }
    }
}