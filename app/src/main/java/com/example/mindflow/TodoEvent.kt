package com.example.mindflow

import java.time.LocalDate
import java.time.YearMonth

sealed class TodoEvent {
    // --- 基础增删改查 ---
    object ShowDialog : TodoEvent()
    object HideDialog : TodoEvent()
    data class SaveTodo(
        val id: Int? = null,
        val title: String,
        val content: String,
        val priority: Int,
        val dueDate: Long?,
        val reminderTime: Long?,
        val category: String,
        val audioPath: String?
    ) : TodoEvent()

    data class EditTodo(val todo: TodoItem) : TodoEvent()
    data class DeleteTodo(val todo: TodoItem) : TodoEvent()
    data class UndoDelete(val todo: TodoItem) : TodoEvent()
    data class ToggleDone(val todo: TodoItem) : TodoEvent()
    data class IncubateTodo(val todo: TodoItem) : TodoEvent()

    // --- 搜索 ---
    object ToggleSearchMode : TodoEvent()
    data class UpdateSearchQuery(val query: String) : TodoEvent()

    // --- 日历与日期选择 ---
    data class SelectDate(val date: LocalDate) : TodoEvent()
    data class ChangeCurrentMonth(val yearMonth: YearMonth) : TodoEvent()
    object ToggleCalendarExpand : TodoEvent()
    object ToggleCalendarMode : TodoEvent()

    // --- 图片/附件 ---
    data class UpdateTodoImage(val todo: TodoItem, val imageUri: String) : TodoEvent()
    data class DeleteTodoImage(val todo: TodoItem, val imageUri: String) : TodoEvent()

    // --- 灵感模式 ---
    object ShowRandomIdea : TodoEvent()
    object DismissRandomIdea : TodoEvent()
    data class SelectInspirationFilter(val filter: InspirationFilter) : TodoEvent()

    // --- 统计与周报 ---
    data class NavigateStatsSubScreen(val subScreen: StatsSubScreen) : TodoEvent()
    object GenerateWeeklyReport : TodoEvent()
    data class SetReportWeek(val date: LocalDate) : TodoEvent()
    data class ChangeReportWeek(val offset: Int) : TodoEvent()

    // --- 统计详情 ---
    data class EnterStatsDetail(val type: StatsType) : TodoEvent()
    object ExitStatsDetail : TodoEvent()
    data class UpdateStatsDateRange(val startMillis: Long?, val endMillis: Long?) : TodoEvent()
    data class SelectStatsCategory(val category: String) : TodoEvent()

    // --- 设置相关 ---
    data class UpdateFontScale(val scale: Float) : TodoEvent()
    data class UpdateAvatar(val uri: String) : TodoEvent()
    data class RestoreData(val uri: android.net.Uri) : TodoEvent()

    // ★★★ 新增：完成新手引导 ★★★
    object CompleteOnboarding : TodoEvent()
}