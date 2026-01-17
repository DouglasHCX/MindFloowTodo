package com.example.mindflow

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// =======================
// 日历专用组件库 (样式回退 + 红绿点 + 交互优化)
// =======================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MindFlowCalendar(
    currentYearMonth: YearMonth,
    selectedDate: LocalDate,
    isExpanded: Boolean,
    mode: CalendarMode,
    dayStatusMap: Map<LocalDate, DayTaskStatus>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit,
    onToggleExpand: () -> Unit,
    onToggleMode: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .animateContentSize()
    ) {
        CalendarHeader(
            currentYearMonth = currentYearMonth,
            mode = mode,
            onHeaderClick = onToggleMode,
            onToggleExpand = onToggleExpand,
            isExpanded = isExpanded
        )

        if (mode == CalendarMode.YEAR) {
            YearCalendarView(
                currentYear = currentYearMonth.year,
                // 点击月份：切换月份，切回月视图，如果收起则展开
                onMonthSelected = { month ->
                    onMonthChanged(YearMonth.of(currentYearMonth.year, month))
                    onToggleMode()
                    if (!isExpanded) onToggleExpand()
                },
                onYearChanged = { year ->
                    onMonthChanged(YearMonth.of(year, currentYearMonth.month))
                }
            )
        } else {
            ExpandableCalendarContainer(
                currentYearMonth = currentYearMonth,
                selectedDate = selectedDate,
                isExpanded = isExpanded,
                dayStatusMap = dayStatusMap,
                onDateSelected = onDateSelected,
                onMonthChanged = onMonthChanged,
                onToggleExpand = onToggleExpand,
                onPrevWeek = onPrevWeek,
                onNextWeek = onNextWeek
            )
        }
    }
}

@Composable
fun CalendarHeader(
    currentYearMonth: YearMonth,
    mode: CalendarMode,
    onHeaderClick: () -> Unit,
    onToggleExpand: () -> Unit,
    isExpanded: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val title = if (mode == CalendarMode.YEAR) "${currentYearMonth.year}" else "${currentYearMonth.year}.${currentYearMonth.monthValue.toString().padStart(2, '0')}"
        Surface(shape = RoundedCornerShape(16.dp), color = if (mode == CalendarMode.YEAR) SelectedDateColor else Color.Transparent, modifier = Modifier.clickable { onHeaderClick() }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (mode == CalendarMode.YEAR) Color.White else MaterialTheme.colorScheme.onBackground)
                if (mode == CalendarMode.MONTH) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand", modifier = Modifier.size(20.dp).clickable { onToggleExpand() })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandableCalendarContainer(
    currentYearMonth: YearMonth,
    selectedDate: LocalDate,
    isExpanded: Boolean,
    dayStatusMap: Map<LocalDate, DayTaskStatus>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit,
    onToggleExpand: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    Column {
        if (isExpanded) {
            // 月视图：选中后自动收起
            MonthCalendarView(
                currentYearMonth = currentYearMonth,
                selectedDate = selectedDate,
                dayStatusMap = dayStatusMap,
                onDateSelected = { date ->
                    onDateSelected(date)
                    onToggleExpand()
                },
                onMonthChanged = onMonthChanged
            )
        } else {
            // 周视图
            WeekCalendarView(
                selectedDate = selectedDate,
                dayStatusMap = dayStatusMap,
                onDateSelected = onDateSelected,
                onPrevWeek = onPrevWeek,
                onNextWeek = onNextWeek
            )
        }

        // 底部把手
        Box(modifier = Modifier.fillMaxWidth().height(24.dp).clickable { onToggleExpand() }, contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.LightGray.copy(alpha = 0.5f)))
        }
    }
}

@Composable
fun WeekCalendarView(
    selectedDate: LocalDate,
    dayStatusMap: Map<LocalDate, DayTaskStatus>,
    onDateSelected: (LocalDate) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val dayOfWeek = selectedDate.dayOfWeek.value
    val monday = selectedDate.minusDays((dayOfWeek - 1).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevWeek, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronLeft, "Prev", tint = Color.Gray) }
        Spacer(modifier = Modifier.width(4.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { date ->
                val isSelected = date.isEqual(selectedDate)
                val isToday = date.isEqual(LocalDate.now())

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(85.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SelectedDateColor else if (isToday) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surface)
                        .clickable { onDateSelected(date) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (date.dayOfMonth == 1) "${date.monthValue}月" else formatWeek(date), fontSize = 10.sp, color = if (isSelected) Color.White.copy(0.7f) else Color.Gray)
                    Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)

                    // ↓↓↓ 恢复红绿点逻辑 ↓↓↓
                    val status = dayStatusMap[date]
                    if (status != null && (status.hasIncomplete || status.hasCompleted)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (status.hasIncomplete) Box(modifier = Modifier.size(4.dp).background(Color.Red, CircleShape))
                            if (status.hasCompleted) Box(modifier = Modifier.size(4.dp).background(Color(0xFF4CAF50), CircleShape))
                        }
                    } else if (isToday) {
                        Text("今", fontSize = 10.sp, color = if (isSelected) Color.White.copy(0.7f) else Color.Gray)
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onNextWeek, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ChevronRight, "Next", tint = Color.Gray) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendarView(
    currentYearMonth: YearMonth,
    selectedDate: LocalDate,
    dayStatusMap: Map<LocalDate, DayTaskStatus>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit
) {
    val baseIndex = Int.MAX_VALUE / 2
    val initialPageOffset = remember(currentYearMonth) { ChronoUnit.MONTHS.between(YearMonth.now(), currentYearMonth).toInt() }
    val pagerState = rememberPagerState(initialPage = baseIndex + initialPageOffset, pageCount = { Int.MAX_VALUE })

    LaunchedEffect(pagerState.currentPage) {
        val diff = pagerState.currentPage - baseIndex
        val newMonth = YearMonth.now().plusMonths(diff.toLong())
        if (newMonth != currentYearMonth) onMonthChanged(newMonth)
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold) } }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val diff = page - baseIndex
            val monthToShow = YearMonth.now().plusMonths(diff.toLong())
            MonthGrid(monthToShow, selectedDate, dayStatusMap, onDateSelected)
        }
    }
}

@Composable
fun MonthGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    dayStatusMap: Map<LocalDate, DayTaskStatus>,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value - 1
    val days = mutableListOf<LocalDate?>()
    repeat(firstDayOfWeek) { days.add(null) }
    for (i in 1..daysInMonth) { days.add(yearMonth.atDay(i)) }
    val rows = days.chunked(7)
    Column { rows.forEach { week -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceAround) { val paddedWeek = week.toMutableList(); while(paddedWeek.size < 7) paddedWeek.add(null); paddedWeek.forEach { date -> DayCell(date, date == selectedDate, dayStatusMap[date], onDateSelected) } } } }
}

@Composable
fun DayCell(
    date: LocalDate?,
    isSelected: Boolean,
    status: DayTaskStatus?,
    onDateSelected: (LocalDate) -> Unit
) {
    if (date == null) { Spacer(modifier = Modifier.size(40.dp)) } else {
        val isToday = date == LocalDate.now()
        // 样式恢复：CircleShape
        Column(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isSelected) SelectedDateColor else Color.Transparent).clickable { onDateSelected(date) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else if (isToday) PrimaryColor else MaterialTheme.colorScheme.onSurface)

            // ↓↓↓ 恢复红绿点逻辑 ↓↓↓
            if (status != null && (status.hasIncomplete || status.hasCompleted)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (status.hasIncomplete) Box(modifier = Modifier.size(4.dp).background(Color.Red, CircleShape))
                    if (status.hasCompleted) Box(modifier = Modifier.size(4.dp).background(Color(0xFF4CAF50), CircleShape))
                }
            } else if (isToday && !isSelected) {
                Box(modifier = Modifier.size(4.dp).background(PrimaryColor, CircleShape))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YearCalendarView(currentYear: Int, onMonthSelected: (Int) -> Unit, onYearChanged: (Int) -> Unit) {
    val baseIndex = Int.MAX_VALUE / 2
    val initialPageOffset = remember(currentYear) { currentYear - LocalDate.now().year }
    val pagerState = rememberPagerState(initialPage = baseIndex + initialPageOffset, pageCount = { Int.MAX_VALUE })
    LaunchedEffect(pagerState.currentPage) { val diff = pagerState.currentPage - baseIndex; val newYear = LocalDate.now().year + diff; if (newYear != currentYear) onYearChanged(newYear) }
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(400.dp)) { page ->
        val yearToShow = LocalDate.now().year + (page - baseIndex)
        LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val months = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")
            items(months.indices.toList()) { index -> Box(modifier = Modifier.height(60.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).clickable { onMonthSelected(index + 1) }, contentAlignment = Alignment.Center) { Text(text = months[index], fontSize = 16.sp, fontWeight = FontWeight.Medium) } }
        }
    }
}