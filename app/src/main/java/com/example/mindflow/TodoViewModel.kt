package com.example.mindflow

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

val StandardCategories = listOf("工作", "学习", "生活", "健身")
const val CATEGORY_IDEA = "灵感"
const val CATEGORY_ALL = "全部"

enum class StatsType { COMPLETED, INCOMPLETE }
enum class CalendarMode { MONTH, YEAR }
enum class StatsSubScreen { MENU, CENTER, WEEKLY_REPORT, SCHEDULE_OVERVIEW }
enum class InspirationFilter { ALL, PENDING, HATCHED }

data class DayTaskStatus(
    val hasIncomplete: Boolean = false,
    val hasCompleted: Boolean = false
)

data class TodoState(
    val todos: List<TodoItem> = emptyList(),
    val displayedTodos: List<TodoItem> = emptyList(),
    val inspirationTodos: List<TodoItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isAddingTodo: Boolean = false,
    val editingTodo: TodoItem? = null,
    val inspirationFilter: InspirationFilter = InspirationFilter.ALL,
    val randomIdea: TodoItem? = null,
    val currentScreen: AppScreen = AppScreen.Todo,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val statsType: StatsType? = null,
    val statsCategory: String = CATEGORY_ALL,
    val statsCompletedTodos: List<TodoItem> = emptyList(),
    val statsIncompleteTodos: List<TodoItem> = emptyList(),
    val currentYearMonth: YearMonth = YearMonth.now(),
    val isCalendarExpanded: Boolean = false,
    val calendarMode: CalendarMode = CalendarMode.MONTH,
    val statsSubScreen: StatsSubScreen = StatsSubScreen.MENU,
    val statsDateRange: Pair<Long?, Long?> = null to null,
    val weeklyReport: WeeklyReportData? = null,
    val reportStartDate: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val appFontScale: Float = 1.0f,
    val avatarUri: String? = null,
    val isFirstLaunch: Boolean = true
)

class TodoViewModel(application: Application, private val dao: TodoDao) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("mindflow_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(TodoState())
    val state: StateFlow<TodoState> = _state.asStateFlow()

    val dayStatusMap: StateFlow<Map<LocalDate, DayTaskStatus>> = dao.getAllTodos()
        .map { todos ->
            todos.filter { it.dueDate != null && it.category != CATEGORY_IDEA }
                .groupBy { Instant.ofEpochMilli(it.dueDate!!).atZone(ZoneId.systemDefault()).toLocalDate() }
                .mapValues { (_, dayTodos) -> DayTaskStatus(hasIncomplete = dayTodos.any { !it.isDone }, hasCompleted = dayTodos.any { it.isDone }) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        val savedScale = prefs.getFloat("app_font_scale", 1.0f)
        val savedAvatar = prefs.getString("user_avatar_uri", null)
        val isFirst = prefs.getBoolean("is_first_launch", true)

        _state.update { it.copy(appFontScale = savedScale, avatarUri = savedAvatar, isFirstLaunch = isFirst) }

        viewModelScope.launch {
            dao.getAllTodos().collect { allTodos ->
                _state.update { currentState ->
                    val daily = filterDailyTodos(allTodos, currentState.selectedDate, currentState.searchQuery, currentState.isSearchActive)
                    val ideas = filterInspirations(allTodos, currentState.searchQuery, currentState.isSearchActive, currentState.inspirationFilter)
                    val completedList = filterStatsTodos(allTodos, currentState.statsCategory, true, currentState.statsDateRange)
                    val incompleteList = filterStatsTodos(allTodos, currentState.statsCategory, false, currentState.statsDateRange)
                    currentState.copy(todos = allTodos, displayedTodos = daily, inspirationTodos = ideas, statsCompletedTodos = completedList, statsIncompleteTodos = incompleteList)
                }
            }
        }
    }

    fun setScreen(screen: AppScreen) { _state.update { it.copy(currentScreen = screen, statsSubScreen = if (screen == AppScreen.Stats) StatsSubScreen.MENU else it.statsSubScreen) } }

    fun onEvent(event: TodoEvent) {
        when (event) {
            is TodoEvent.CompleteOnboarding -> {
                prefs.edit().putBoolean("is_first_launch", false).apply()
                _state.update { it.copy(isFirstLaunch = false) }
            }
            is TodoEvent.UpdateFontScale -> {
                prefs.edit().putFloat("app_font_scale", event.scale).apply()
                _state.update { it.copy(appFontScale = event.scale) }
            }
            is TodoEvent.UpdateAvatar -> {
                prefs.edit().putString("user_avatar_uri", event.uri).apply()
                _state.update { it.copy(avatarUri = event.uri) }
            }
            is TodoEvent.RestoreData -> {
                viewModelScope.launch {
                    try {
                        context.contentResolver.openInputStream(event.uri)?.use { inputStream ->
                            val jsonString = inputStream.bufferedReader().use { it.readText() }
                            val jsonObject = org.json.JSONObject(jsonString)
                            val jsonArray = jsonObject.getJSONArray("data")

                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.getJSONObject(i)
                                val todo = TodoItem(
                                    id = item.optInt("id"),
                                    title = item.getString("title"),
                                    content = item.optString("content"),
                                    priority = item.optInt("priority", 2),
                                    isDone = item.optBoolean("isDone", false),
                                    dueDate = item.optLong("dueDate").takeIf { it > 0 },
                                    category = item.optString("category", "其他"),
                                    isIncubated = item.optBoolean("isIncubated", false),
                                    isFromInspiration = item.optBoolean("isFromInspiration", false),
                                    audioPath = item.optString("audioPath", null)
                                )
                                dao.insertTodo(todo)
                            }
                        }
                        Toast.makeText(context, "数据恢复成功！", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "恢复失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            is TodoEvent.ShowDialog -> _state.update { it.copy(isAddingTodo = true, editingTodo = null) }
            is TodoEvent.HideDialog -> _state.update { it.copy(isAddingTodo = false, editingTodo = null) }
            is TodoEvent.EditTodo -> _state.update { it.copy(isAddingTodo = true, editingTodo = event.todo) }
            is TodoEvent.SelectDate -> _state.update { val newMonth = YearMonth.from(event.date); it.copy(selectedDate = event.date, currentYearMonth = newMonth, displayedTodos = filterDailyTodos(it.todos, event.date, it.searchQuery, it.isSearchActive)) }
            is TodoEvent.SaveTodo -> {
                val newTodo = TodoItem(
                    id = event.id ?: 0,
                    title = event.title,
                    content = event.content,
                    priority = event.priority,
                    dueDate = event.dueDate,
                    reminderTime = event.reminderTime,
                    category = event.category,
                    imageUris = _state.value.editingTodo?.imageUris ?: emptyList(),
                    isDone = _state.value.editingTodo?.isDone ?: false,
                    completedAt = _state.value.editingTodo?.completedAt,
                    isIncubated = _state.value.editingTodo?.isIncubated ?: false,
                    isFromInspiration = _state.value.editingTodo?.isFromInspiration ?: false,
                    audioPath = event.audioPath ?: _state.value.editingTodo?.audioPath
                )
                viewModelScope.launch {
                    if (event.id != null) {
                        dao.updateTodo(newTodo)
                        NotificationScheduler.cancel(context, newTodo)
                        if (newTodo.reminderTime != null) NotificationScheduler.schedule(context, newTodo)
                    } else {
                        val newId = dao.insertTodo(newTodo)
                        if (event.reminderTime != null) { val todoWithId = newTodo.copy(id = newId.toInt()); NotificationScheduler.schedule(context, todoWithId) }
                    }
                }
                _state.update { it.copy(isAddingTodo = false, editingTodo = null) }
            }
            is TodoEvent.IncubateTodo -> {
                viewModelScope.launch {
                    val updatedInspiration = event.todo.copy(isIncubated = true)
                    dao.updateTodo(updatedInspiration)
                    val newTodo = TodoItem(title = event.todo.title, content = event.todo.content, category = "其他", dueDate = System.currentTimeMillis(), priority = 2, imageUris = event.todo.imageUris, isFromInspiration = true, audioPath = event.todo.audioPath)
                    dao.insertTodo(newTodo)
                }
                _state.update { it.copy(randomIdea = null) }
            }
            is TodoEvent.ToggleDone -> { val isNowDone = !event.todo.isDone; val completedTime = if (isNowDone) System.currentTimeMillis() else null; viewModelScope.launch { dao.updateTodo(event.todo.copy(isDone = isNowDone, completedAt = completedTime)) } }
            is TodoEvent.DeleteTodo -> { viewModelScope.launch { dao.deleteTodo(event.todo); NotificationScheduler.cancel(context, event.todo); event.todo.audioPath?.let { path -> try { java.io.File(path).delete() } catch (e: Exception) { e.printStackTrace() } }; event.todo.imageUris.forEach { uriStr -> try { context.contentResolver.delete(android.net.Uri.parse(uriStr), null, null) } catch (e: Exception) { e.printStackTrace() } } } }
            is TodoEvent.UndoDelete -> { viewModelScope.launch { val newId = dao.insertTodo(event.todo); if (event.todo.reminderTime != null && event.todo.reminderTime > System.currentTimeMillis()) { val todoWithId = event.todo.copy(id = newId.toInt()); NotificationScheduler.schedule(context, todoWithId) } } }
            is TodoEvent.UpdateSearchQuery -> _state.update { it.copy(searchQuery = event.query, displayedTodos = filterDailyTodos(it.todos, it.selectedDate, event.query, it.isSearchActive), inspirationTodos = filterInspirations(it.todos, event.query, it.isSearchActive, it.inspirationFilter)) }
            is TodoEvent.ToggleSearchMode -> _state.update { val newMode = !it.isSearchActive; val newQuery = if (newMode) it.searchQuery else ""; it.copy(isSearchActive = newMode, searchQuery = newQuery, displayedTodos = filterDailyTodos(it.todos, it.selectedDate, newQuery, newMode), inspirationTodos = filterInspirations(it.todos, newQuery, newMode, it.inspirationFilter)) }
            is TodoEvent.EnterStatsDetail -> _state.update { it.copy(statsType = event.type, statsCategory = CATEGORY_ALL, statsDateRange = null to null, statsCompletedTodos = filterStatsTodos(it.todos, CATEGORY_ALL, true, null to null), statsIncompleteTodos = filterStatsTodos(it.todos, CATEGORY_ALL, false, null to null)) }
            is TodoEvent.ExitStatsDetail -> _state.update { it.copy(statsType = null) }
            is TodoEvent.SelectStatsCategory -> _state.update { it.copy(statsCategory = event.category, statsCompletedTodos = filterStatsTodos(it.todos, event.category, true, it.statsDateRange), statsIncompleteTodos = filterStatsTodos(it.todos, event.category, false, it.statsDateRange)) }

            // ★★★ 核心修改：使用 ImageUtils 压缩图片 ★★★
            is TodoEvent.UpdateTodoImage -> viewModelScope.launch {
                // 1. 压缩图片并获取新路径
                val compressedPath = ImageUtils.compressAndSaveImage(context, android.net.Uri.parse(event.imageUri))

                // 2. 如果压缩成功，存入数据库；否则存原图（兜底）
                val finalPath = compressedPath ?: event.imageUri

                val list = event.todo.imageUris.toMutableList()
                list.add(finalPath)
                dao.updateTodo(event.todo.copy(imageUris = list))
            }

            is TodoEvent.DeleteTodoImage -> viewModelScope.launch {
                val list = event.todo.imageUris.toMutableList()
                list.remove(event.imageUri)

                // 尝试删除本地文件
                try {
                    val file = java.io.File(event.imageUri)
                    if (file.exists()) file.delete()
                } catch (e: Exception) { e.printStackTrace() }

                dao.updateTodo(event.todo.copy(imageUris = list))
            }

            is TodoEvent.ToggleCalendarExpand -> _state.update { it.copy(isCalendarExpanded = !it.isCalendarExpanded) }
            is TodoEvent.ToggleCalendarMode -> _state.update { val newMode = if (it.calendarMode == CalendarMode.MONTH) CalendarMode.YEAR else CalendarMode.MONTH; it.copy(calendarMode = newMode) }
            is TodoEvent.ChangeCurrentMonth -> _state.update { if (it.currentYearMonth != event.yearMonth) it.copy(currentYearMonth = event.yearMonth) else it }
            is TodoEvent.NavigateStatsSubScreen -> _state.update { it.copy(statsSubScreen = event.subScreen) }
            is TodoEvent.UpdateStatsDateRange -> _state.update { val newRange = event.startMillis to event.endMillis; it.copy(statsDateRange = newRange, statsCompletedTodos = filterStatsTodos(it.todos, it.statsCategory, true, newRange), statsIncompleteTodos = filterStatsTodos(it.todos, it.statsCategory, false, newRange)) }
            is TodoEvent.ChangeReportWeek -> { _state.update { val newDate = it.reportStartDate.plusWeeks(event.offset.toLong()); val endOfWeek = newDate.plusDays(6); val report = WeeklyAnalysisEngine.analyze(it.todos, newDate, endOfWeek); it.copy(reportStartDate = newDate, weeklyReport = report) } }
            is TodoEvent.GenerateWeeklyReport -> { _state.update { val startOfWeek = it.reportStartDate; val endOfWeek = startOfWeek.plusDays(6); val report = WeeklyAnalysisEngine.analyze(it.todos, startOfWeek, endOfWeek); it.copy(weeklyReport = report) } }
            is TodoEvent.SetReportWeek -> { _state.update { val newStartDate = event.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); val endOfWeek = newStartDate.plusDays(6); val report = WeeklyAnalysisEngine.analyze(it.todos, newStartDate, endOfWeek); it.copy(reportStartDate = newStartDate, weeklyReport = report) } }
            is TodoEvent.SelectInspirationFilter -> { _state.update { it.copy(inspirationFilter = event.filter, inspirationTodos = filterInspirations(it.todos, it.searchQuery, it.isSearchActive, event.filter)) } }
            is TodoEvent.ShowRandomIdea -> { val candidates = _state.value.todos.filter { it.category == CATEGORY_IDEA && !it.isIncubated }; if (candidates.isNotEmpty()) { _state.update { it.copy(randomIdea = candidates.random()) } } }
            is TodoEvent.DismissRandomIdea -> _state.update { it.copy(randomIdea = null) }
        }
    }

    private fun filterDailyTodos(todos: List<TodoItem>, date: LocalDate, query: String, isSearchActive: Boolean): List<TodoItem> {
        val nonIdeas = todos.filter { it.category != CATEGORY_IDEA }
        if (isSearchActive && query.isNotBlank()) return nonIdeas.filter { it.title.contains(query, true) || it.content.contains(query, true) || it.category.contains(query, true) }
        return nonIdeas.filter { if (it.dueDate == null) false else Instant.ofEpochMilli(it.dueDate).atZone(ZoneId.systemDefault()).toLocalDate().isEqual(date) }
    }

    private fun filterInspirations(todos: List<TodoItem>, query: String, isSearchActive: Boolean, filter: InspirationFilter): List<TodoItem> {
        var ideas = todos.filter { it.category == CATEGORY_IDEA }
        if (isSearchActive && query.isNotBlank()) { ideas = ideas.filter { it.title.contains(query, true) || it.content.contains(query, true) } }
        ideas = when (filter) { InspirationFilter.ALL -> ideas; InspirationFilter.PENDING -> ideas.filter { !it.isIncubated }; InspirationFilter.HATCHED -> ideas.filter { it.isIncubated } }
        return ideas.sortedWith(compareBy({ it.isIncubated }, { -it.id }))
    }

    private fun filterStatsTodos(todos: List<TodoItem>, category: String, isDone: Boolean, dateRange: Pair<Long?, Long?>): List<TodoItem> {
        val (start, end) = dateRange
        return todos.filter { item ->
            val statusMatch = item.isDone == isDone
            val categoryMatch = if (category == CATEGORY_ALL) item.category != CATEGORY_IDEA else if (category == "其他") item.category !in StandardCategories && item.category != CATEGORY_IDEA else item.category == category
            val dateMatch = if (start != null && end != null) item.dueDate != null && item.dueDate >= start && item.dueDate <= end else true
            statusMatch && categoryMatch && dateMatch
        }.sortedByDescending { it.dueDate ?: 0L }
    }

    fun generateBackupJson(): String {
        val allTodos = state.value.todos
        val jsonArray = org.json.JSONArray()
        allTodos.forEach { todo ->
            val jsonObject = org.json.JSONObject()
            jsonObject.put("id", todo.id)
            jsonObject.put("title", todo.title)
            jsonObject.put("content", todo.content)
            jsonObject.put("isDone", todo.isDone)
            jsonObject.put("priority", todo.priority)
            jsonObject.put("category", todo.category)
            jsonObject.put("dueDate", todo.dueDate ?: 0L)
            jsonObject.put("isIncubated", todo.isIncubated)
            jsonObject.put("isFromInspiration", todo.isFromInspiration)
            jsonObject.put("audioPath", todo.audioPath)
            jsonArray.put(jsonObject)
        }
        val finalJson = org.json.JSONObject()
        finalJson.put("version", "1.0")
        finalJson.put("exportTime", System.currentTimeMillis())
        finalJson.put("device", android.os.Build.MODEL)
        finalJson.put("data", jsonArray)
        return finalJson.toString(4)
    }
}