package com.example.mindflow

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// =======================
// 1. 待办列表界面 (TodoScreenContent)
// =======================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreenContent(state: TodoState, dayStatusMap: Map<LocalDate, DayTaskStatus>, onEvent: (TodoEvent) -> Unit, onOpenSession: (TodoItem) -> Unit) {
    val activeTodos = state.displayedTodos.filter { !it.isDone }
    val doneTodos = state.displayedTodos.filter { it.isDone }
    val totalCount = state.displayedTodos.size
    val doneCount = doneTodos.size
    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.isSearchActive) {
            SearchBar(query = state.searchQuery, onQueryChange = { onEvent(TodoEvent.UpdateSearchQuery(it)) }, onSearch = { }, active = false, onActiveChange = { }, placeholder = { Text("搜索任务...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { onEvent(TodoEvent.ToggleSearchMode) }) { Icon(Icons.Default.Close, "Close") } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {}
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp, bottom = 12.dp, end = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CXMindFlow 待办", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
                IconButton(onClick = { onEvent(TodoEvent.ToggleSearchMode) }) { Icon(Icons.Default.Search, "Search") }
            }
            MindFlowCalendar(
                currentYearMonth = state.currentYearMonth, selectedDate = state.selectedDate, isExpanded = state.isCalendarExpanded, mode = state.calendarMode, dayStatusMap = dayStatusMap,
                onDateSelected = { onEvent(TodoEvent.SelectDate(it)) }, onMonthChanged = { onEvent(TodoEvent.ChangeCurrentMonth(it)) }, onToggleExpand = { onEvent(TodoEvent.ToggleCalendarExpand) }, onToggleMode = { onEvent(TodoEvent.ToggleCalendarMode) },
                onPrevWeek = { onEvent(TodoEvent.SelectDate(state.selectedDate.minusWeeks(1))) }, onNextWeek = { onEvent(TodoEvent.SelectDate(state.selectedDate.plusWeeks(1))) }
            )
        }

        val showProgressCard = !state.isSearchActive && totalCount > 0
        AnimatedVisibility(visible = showProgressCard, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            DailyProgressCard(date = state.selectedDate, total = totalCount, done = doneCount, progress = progress)
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.displayedTodos.isEmpty()) { item { EmptyStateView(isSearch = state.isSearchActive) } } else {
                items(activeTodos, key = { it.id }) { todo -> Box(modifier = Modifier.animateItemPlacement()) { SwipeToActionContainer(todo = todo, onDelete = { onEvent(TodoEvent.DeleteTodo(todo)) }, onCamera = { onOpenSession(todo) }) { TodoCard(todo = todo, onEvent = onEvent, onLongClick = { onOpenSession(todo) }) } } }
                if (doneTodos.isNotEmpty()) {
                    item { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp).animateItemPlacement()) { Text("已完成", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(8.dp)); HorizontalDivider(modifier = Modifier.weight(1f)) } }
                    items(doneTodos, key = { "done_${it.id}" }) { todo -> Box(modifier = Modifier.animateItemPlacement()) { SwipeToActionContainer(todo = todo, onDelete = { onEvent(TodoEvent.DeleteTodo(todo)) }, onCamera = { onOpenSession(todo) }) { TodoCard(todo = todo, onEvent = onEvent, onLongClick = { onOpenSession(todo) }) } } }
                }
            }
        }
    }
}

@Composable
fun DailyProgressCard(date: LocalDate, total: Int, done: Int, progress: Float) {
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing), label = "DailyProgress")
    val dateText = date.format(DateTimeFormatter.ofPattern("MM月dd日", Locale.CHINA))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$dateText 完成度", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "$done / $total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = if (progress == 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

// =======================
// 2. 灵感界面 (InspirationScreenContent)
// =======================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InspirationScreenContent(state: TodoState, onEvent: (TodoEvent) -> Unit) {
    val context = LocalContext.current

    if (state.randomIdea != null) {
        RandomIdeaDialog(
            todo = state.randomIdea,
            onDismiss = { onEvent(TodoEvent.DismissRandomIdea) },
            onEvent = onEvent
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.isSearchActive) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { onEvent(TodoEvent.UpdateSearchQuery(it)) },
                onSearch = { },
                active = false,
                onActiveChange = { },
                placeholder = { Text("搜索灵感...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = { onEvent(TodoEvent.ToggleSearchMode) }) { Icon(Icons.Default.Close, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp, bottom = 4.dp, end = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("灵感墙", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)

                Row {
                    IconButton(onClick = {
                        onEvent(TodoEvent.ShowRandomIdea)
                        if (state.todos.none { it.category == CATEGORY_IDEA && !it.isIncubated }) {
                            android.widget.Toast.makeText(context, "没有可漫游的灵感哦", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Outlined.Shuffle, "Random", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onEvent(TodoEvent.ToggleSearchMode) }) { Icon(Icons.Default.Search, "Search") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = state.inspirationFilter == InspirationFilter.ALL, onClick = { onEvent(TodoEvent.SelectInspirationFilter(InspirationFilter.ALL)) }, label = { Text("全部") })
            FilterChip(selected = state.inspirationFilter == InspirationFilter.PENDING, onClick = { onEvent(TodoEvent.SelectInspirationFilter(InspirationFilter.PENDING)) }, label = { Text("待孵化") })
            FilterChip(selected = state.inspirationFilter == InspirationFilter.HATCHED, onClick = { onEvent(TodoEvent.SelectInspirationFilter(InspirationFilter.HATCHED)) }, label = { Text("已孵化") })
        }

        if (state.inspirationTodos.isEmpty()) {
            EmptyStateView(isSearch = state.isSearchActive, text = "暂无灵感\n快捕捉你的想法！💡")
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.inspirationTodos, key = { it.id }) { todo ->
                    InspirationGridCard(todo = todo, onEvent = onEvent)
                }
            }
        }
    }
}

// =======================
// 3. 统计界面 (StatsScreenContent)
// =======================
@Composable
fun StatsScreenContent(state: TodoState, dayStatusMap: Map<LocalDate, DayTaskStatus>, onEvent: (TodoEvent) -> Unit) {
    when (state.statsSubScreen) {
        StatsSubScreen.MENU -> StatsMenuScreen(onEvent)
        StatsSubScreen.CENTER -> if (state.statsType != null) StatsDetailView(state, onEvent) else StatsOverview(state, dayStatusMap, onEvent)
        StatsSubScreen.WEEKLY_REPORT -> WeeklyReportScreen(state, onEvent)
        StatsSubScreen.SCHEDULE_OVERVIEW -> ScheduleOverviewScreen(
            todos = state.todos,
            onBack = { onEvent(TodoEvent.NavigateStatsSubScreen(StatsSubScreen.MENU)) },
            onEvent = onEvent
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsMenuScreen(onEvent: (TodoEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Text("统计与复盘", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 24.dp))
        Card(modifier = Modifier.fillMaxWidth().height(140.dp).clickable { onEvent(TodoEvent.NavigateStatsSubScreen(StatsSubScreen.CENTER)) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) { Column(modifier = Modifier.align(Alignment.BottomStart)) { Icon(Icons.Default.PieChart, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(modifier = Modifier.height(8.dp)); Text("统计中心", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer); Text("查看数据图表与完成率", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)) } }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth().height(140.dp).clickable { onEvent(TodoEvent.NavigateStatsSubScreen(StatsSubScreen.WEEKLY_REPORT)) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) { Column(modifier = Modifier.align(Alignment.BottomStart)) { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer); Spacer(modifier = Modifier.height(8.dp)); Text("周报复盘", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer); Text("智能分析本周表现 (New)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)) } }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth().height(140.dp).clickable { onEvent(TodoEvent.NavigateStatsSubScreen(StatsSubScreen.SCHEDULE_OVERVIEW)) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) { Column(modifier = Modifier.align(Alignment.BottomStart)) { Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(modifier = Modifier.height(8.dp)); Text("日程概览", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer); Text("全月任务分布墙", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)) } }
        }
    }
}

@Composable
fun KeywordDialog(keyword: KeywordItem, onDismiss: () -> Unit) { Dialog(onDismissRequest = onDismiss) { Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(keyword.tag, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.height(16.dp)); Text(keyword.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center); Spacer(modifier = Modifier.height(24.dp)); Button(onClick = onDismiss) { Text("这就去发朋友圈") } } } } }

@Composable
fun WeekSelectionDialog(currentStartDate: LocalDate, todos: List<TodoItem>, onDismiss: () -> Unit, onWeekSelected: (LocalDate) -> Unit) {
    val weekOptions = remember { val options = mutableListOf<Triple<LocalDate, Int, Int>>(); var current = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); val weekFields = WeekFields.of(Locale.CHINA); repeat(52) { val weekNum = current.get(weekFields.weekOfWeekBasedYear()); val year = current.get(weekFields.weekBasedYear()); options.add(Triple(current, year, weekNum)); current = current.minusWeeks(1) }; options }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().height(550.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column {
                Text("选择历史周报", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp))
                LazyColumn(modifier = Modifier.weight(1f)) { items(weekOptions) { (startDate, year, weekNum) -> val endDate = startDate.plusDays(6); val isSelected = startDate == currentStartDate; val report = remember(startDate, todos) { WeeklyAnalysisEngine.analyze(todos, startDate, endDate) }; val weekTitle = report.title; val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent; val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    Row(modifier = Modifier.fillMaxWidth().clickable { onWeekSelected(startDate); onDismiss() }.background(backgroundColor).padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("${year}年 第${weekNum}周", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = contentColor); Column(horizontalAlignment = Alignment.End) { Text("${startDate.format(DateTimeFormatter.ofPattern("MM.dd"))} - ${endDate.format(DateTimeFormatter.ofPattern("MM.dd"))}", style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.6f)); Spacer(modifier = Modifier.height(2.dp)); Text(weekTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }; Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp) } }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(16.dp)) { Text("关闭") }
            }
        }
    }
}

@Composable
fun WeeklyReportScreen(state: TodoState, onEvent: (TodoEvent) -> Unit) {
    LaunchedEffect(Unit) { onEvent(TodoEvent.GenerateWeeklyReport) }
    var selectedKeyword by remember { mutableStateOf<KeywordItem?>(null) }
    var showWeekDialog by remember { mutableStateOf(false) }
    if (selectedKeyword != null) { KeywordDialog(keyword = selectedKeyword!!, onDismiss = { selectedKeyword = null }) }
    if (showWeekDialog) { WeekSelectionDialog(currentStartDate = state.reportStartDate, todos = state.todos, onDismiss = { showWeekDialog = false }, onWeekSelected = { date -> onEvent(TodoEvent.SetReportWeek(date)) }) }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val weekFields = WeekFields.of(Locale.CHINA); val weekNumber = state.reportStartDate.get(weekFields.weekOfWeekBasedYear()); val year = state.reportStartDate.year; val dateRangeText = "${state.reportStartDate.format(DateTimeFormatter.ofPattern("MM.dd"))} - ${state.reportStartDate.plusDays(6).format(DateTimeFormatter.ofPattern("MM.dd"))}"
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { IconButton(onClick = { onEvent(TodoEvent.NavigateStatsSubScreen(StatsSubScreen.MENU)) }) { Icon(Icons.Default.ArrowBack, "Back") }; Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).clickable { showWeekDialog = true }.padding(4.dp)) { IconButton(onClick = { onEvent(TodoEvent.ChangeReportWeek(-1)) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(20.dp)) }; Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) { Text("${year}年 第${weekNumber}周 ($dateRangeText)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(4.dp)); Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { onEvent(TodoEvent.ChangeReportWeek(1)) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp)) } }; Spacer(modifier = Modifier.width(48.dp)) }
        val report = state.weeklyReport
        if (report == null) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item { Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(24.dp)) { Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("本周称号", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(0.8f)); Spacer(modifier = Modifier.height(8.dp)); Text(report.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Color.White); Spacer(modifier = Modifier.height(16.dp)); Text("总分: ${report.score.totalScore}", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold) } }; Spacer(modifier = Modifier.height(24.dp)) }
                item { Text("五维能力模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(16.dp)); Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { RadarChart(scores = listOf(report.score.execution / 100f, report.score.focus / 100f, report.score.balance / 100f, report.score.resilience / 100f, report.score.activity / 100f), labels = listOf("执行", "专注", "平衡", "攻坚", "活跃")) }; Spacer(modifier = Modifier.height(24.dp)) }
                item { Text("本周关键词 (点击查看)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { report.keywords.forEach { keyword -> SuggestionChip(onClick = { selectedKeyword = keyword }, label = { Text(keyword.tag) }) } }; Spacer(modifier = Modifier.height(24.dp)) }
                item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) { Column(modifier = Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)); Text("智能总结", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; Spacer(modifier = Modifier.height(8.dp)); Text(report.summary, style = MaterialTheme.typography.bodyMedium, lineHeight = 24.sp) } }; Spacer(modifier = Modifier.height(48.dp)) }
            }
        }
    }
}

@Composable
fun RadarChart(scores: List<Float>, labels: List<String>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.onBackground
    Canvas(modifier = Modifier.size(250.dp)) {
        val center = Offset(size.width / 2, size.height / 2); val radius = size.width / 2 - 20.dp.toPx(); val angleStep = 360f / 5
        for (i in 1..3) { val r = radius * i / 3; val path = Path(); for (j in 0 until 5) { val angle = Math.toRadians((j * angleStep - 90).toDouble()); val x = center.x + r * cos(angle).toFloat(); val y = center.y + r * sin(angle).toFloat(); if (j == 0) path.moveTo(x, y) else path.lineTo(x, y) }; path.close(); drawPath(path, color = surfaceColor.copy(alpha = 0.2f), style = Stroke(width = 1.dp.toPx())) }
        val dataPath = Path()
        scores.forEachIndexed { index, score -> val angle = Math.toRadians((index * angleStep - 90).toDouble()); val r = radius * score; val x = center.x + r * cos(angle).toFloat(); val y = center.y + r * sin(angle).toFloat(); if (index == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y); drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = Offset(x, y)) }
        dataPath.close(); drawPath(dataPath, color = primaryColor.copy(alpha = 0.3f), style = Fill); drawPath(dataPath, color = primaryColor, style = Stroke(width = 2.dp.toPx()))
        labels.forEachIndexed { index, label -> val angle = Math.toRadians((index * angleStep - 90).toDouble()); val r = radius + 20.dp.toPx(); val x = center.x + r * cos(angle).toFloat(); val y = center.y + r * sin(angle).toFloat(); drawContext.canvas.nativeCanvas.apply { val paint = android.graphics.Paint().apply { color = surfaceColor.toArgb(); textSize = 12.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER }; drawText(label, x, y, paint) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailView(state: TodoState, onEvent: (TodoEvent) -> Unit) {
    val displayCategories = listOf(CATEGORY_ALL) + StandardCategories + "其他"
    val isCompletedView = state.statsType == StatsType.COMPLETED
    val title = if (isCompletedView) "已完成任务归档" else "待办积压清单"
    val listData = if (isCompletedView) state.statsCompletedTodos else state.statsIncompleteTodos
    var showDateRangePicker by remember { mutableStateOf(false) }
    val rangeText = if (state.statsDateRange.first != null && state.statsDateRange.second != null) { "${formatRangeDate(state.statsDateRange.first!!)} - ${formatRangeDate(state.statsDateRange.second!!)}" } else { "所有时间" }

    if (showDateRangePicker) {
        StatsDateRangePicker(initialStart = state.statsDateRange.first, initialEnd = state.statsDateRange.second, onDismiss = { showDateRangePicker = false }, onConfirm = { start, end -> onEvent(TodoEvent.UpdateStatsDateRange(start, end)) })
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onEvent(TodoEvent.ExitStatsDetail) }) { Icon(Icons.Default.ArrowBack, "Back") }; Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)) }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { showDateRangePicker = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.width(8.dp)); Text("时间筛选: $rangeText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.weight(1f)); if (state.statsDateRange.first != null) { Icon(Icons.Default.Close, null, modifier = Modifier.clickable { onEvent(TodoEvent.UpdateStatsDateRange(null, null)) }, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(displayCategories) { category -> FilterChip(selected = state.statsCategory == category, onClick = { onEvent(TodoEvent.SelectStatsCategory(category)) }, label = { Text(category) }, leadingIcon = if (state.statsCategory == category) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null) } }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (listData.isEmpty()) { item { EmptyStateView(isSearch = false, text = if (isCompletedView) "暂无记录" else "暂无积压") } } else {
                items(listData, key = { it.id }) { todo -> TodoCard(todo = todo, onEvent = onEvent, onLongClick = { }) }
            }
        }
    }
}

@Composable
fun WorkloadTrendCard(todos: List<TodoItem>) {
    val today = LocalDate.now()
    val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val weekDates = (0..6).map { startOfWeek.plusDays(it.toLong()) }

    val dataPoints = weekDates.map { date ->
        todos.count { todo ->
            if (!todo.isDone || todo.category == CATEGORY_IDEA) return@count false
            val timestamp = todo.dueDate ?: todo.completedAt
            if (timestamp != null) {
                val recordDate = Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                recordDate == date
            } else {
                false
            }
        }
    }

    val labels = weekDates.map { it.format(DateTimeFormatter.ofPattern("MM.dd")) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本周工作量趋势 (已完成)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            LineChart(data = dataPoints, modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 4.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { label -> Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.widthIn(min = 20.dp)) }
            }
        }
    }
}

@Composable
fun LineChart(data: List<Int>, modifier: Modifier = Modifier, lineColor: Color = MaterialTheme.colorScheme.primary) {
    val maxValue = (data.maxOrNull() ?: 0).coerceAtLeast(5)
    var animationPlayed by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(targetValue = if (animationPlayed) 1f else 0f, animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing), label = "GraphAnimation")
    LaunchedEffect(Unit) { animationPlayed = true }

    Canvas(modifier = modifier) {
        val width = size.width; val height = size.height; val chartHeight = height; val spacing = if (data.size > 1) width / (data.size - 1) else width
        drawLine(color = Color.LightGray.copy(alpha = 0.5f), start = Offset(0f, chartHeight), end = Offset(width, chartHeight), strokeWidth = 1.dp.toPx())
        val path = Path(); val fillPath = Path()
        data.forEachIndexed { index, value -> val x = index * spacing; val y = chartHeight - (value.toFloat() / maxValue * chartHeight) * animProgress; if (index == 0) { path.moveTo(x, y); fillPath.moveTo(x, chartHeight); fillPath.lineTo(x, y) } else { path.lineTo(x, y); fillPath.lineTo(x, y) }; drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y)) }
        fillPath.lineTo(width, chartHeight); fillPath.close()
        drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(lineColor.copy(alpha = 0.3f), Color.Transparent), startY = 0f, endY = chartHeight))
        drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StatsOverview(state: TodoState, dayStatusMap: Map<LocalDate, DayTaskStatus>, onEvent: (TodoEvent) -> Unit) {
    var isAllTimeMode by remember { mutableStateOf(true) }
    val rawList = if (isAllTimeMode) state.todos else state.displayedTodos
    val targetTodos = remember(rawList) { rawList.filter { it.category != CATEGORY_IDEA } }
    val totalTasks = targetTodos.size; val completedTasks = targetTodos.count { it.isDone }; val incompleteTasks = totalTasks - completedTasks; val completionRate = if (totalTasks > 0) (completedTasks.toFloat() / totalTasks) * 100 else 0f
    val categoryStats = targetTodos.groupBy { if (it.category in StandardCategories) it.category else "其他" }.mapValues { it.value.size }
    val displayCategories = StandardCategories + "其他"; val pagerState = rememberPagerState(pageCount = { 2 })

    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        item { Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onEvent(TodoEvent.NavigateStatsSubScreen(StatsSubScreen.MENU)) }) { Icon(Icons.Default.ArrowBack, "Back") }; Text("统计中心", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = !isAllTimeMode, onClick = { isAllTimeMode = false }, label = { Text("按日期") }); FilterChip(selected = isAllTimeMode, onClick = { isAllTimeMode = true }, label = { Text("累计") }) } } }
        if (!isAllTimeMode) { item { MindFlowCalendar(currentYearMonth = state.currentYearMonth, selectedDate = state.selectedDate, isExpanded = state.isCalendarExpanded, mode = state.calendarMode, dayStatusMap = dayStatusMap, onDateSelected = { onEvent(TodoEvent.SelectDate(it)) }, onMonthChanged = { onEvent(TodoEvent.ChangeCurrentMonth(it)) }, onToggleExpand = { onEvent(TodoEvent.ToggleCalendarExpand) }, onToggleMode = { onEvent(TodoEvent.ToggleCalendarMode) }, onPrevWeek = { onEvent(TodoEvent.SelectDate(state.selectedDate.minusWeeks(1))) }, onNextWeek = { onEvent(TodoEvent.SelectDate(state.selectedDate.plusWeeks(1))) }); Spacer(modifier = Modifier.height(16.dp)) } }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                    if (page == 0) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(250.dp).clickable { onEvent(TodoEvent.EnterStatsDetail(StatsType.COMPLETED)) }) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text(if (isAllTimeMode) "累计完成率" else "${state.selectedDate.dayOfMonth}日 完成率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null, tint = Color.Gray) }
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(contentAlignment = Alignment.Center) { PieChart(progress = completionRate / 100f, modifier = Modifier.size(150.dp)); Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(String.format("%.1f%%", completionRate), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text("已完成", style = MaterialTheme.typography.labelSmall, color = Color.Gray) } }
                                Spacer(modifier = Modifier.height(8.dp)); Text("完成 $completedTasks / 总计 $totalTasks", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(250.dp).clickable { onEvent(TodoEvent.EnterStatsDetail(StatsType.INCOMPLETE)) }) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("待办积压", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)); Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFD32F2F)) }
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(contentAlignment = Alignment.Center) { Canvas(modifier = Modifier.size(150.dp)) { drawArc(color = Color.White, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 30f, cap = StrokeCap.Round)); val progress = if (totalTasks > 0) incompleteTasks.toFloat() / totalTasks else 0f; drawArc(color = Color(0xFFEF5350), startAngle = -90f, sweepAngle = progress * 360f, useCenter = false, style = Stroke(width = 30f, cap = StrokeCap.Round)) }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$incompleteTasks", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)); Text("剩余任务", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F)) } }
                                Spacer(modifier = Modifier.height(8.dp)); Text("加油！消灭它们！", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) { repeat(2) { iteration -> val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.LightGray; Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(8.dp)) } }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { WorkloadTrendCard(todos = state.todos); Spacer(modifier = Modifier.height(16.dp)) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("任务分类分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(16.dp))
                    displayCategories.forEach { category -> val count = categoryStats[category] ?: 0; val percentage = if (totalTasks > 0) count.toFloat() / totalTasks else 0f; Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(category, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall); Spacer(modifier = Modifier.width(8.dp)); Box(modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { Box(modifier = Modifier.fillMaxWidth(percentage).fillMaxHeight().background(when(category) { "工作" -> Color(0xFFFF6F61); "学习" -> Color(0xFFFFD166); "生活" -> Color(0xFF118AB2); "健身" -> Color(0xFF66BB6A); else -> Color.Gray })) }; Spacer(modifier = Modifier.width(8.dp)); Text("$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) } }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}