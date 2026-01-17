package com.example.mindflow

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 桌面小组件逻辑
class DashboardWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 遍历每一个放在桌面的小组件进行更新
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    // 接收到数据更新广播时触发
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.mindflow.ACTION_WIDGET_UPDATE") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, DashboardWidget::class.java))
            onUpdate(context, appWidgetManager, ids)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val today = LocalDate.now()
                val zone = ZoneId.systemDefault()

                // 1. 获取数据 (现在可以使用 getAllTodosSync 了)
                val allTodos = db.todoDao().getAllTodosSync()

                // 2. 过滤今天的任务
                val todayTodos = allTodos.filter {
                    val date = if (it.dueDate != null) Instant.ofEpochMilli(it.dueDate).atZone(zone).toLocalDate() else null
                    date == today && it.category != "灵感"
                }

                val total = todayTodos.size
                val done = todayTodos.count { it.isDone }
                val remaining = total - done
                val progress = if (total > 0) (done.toFloat() / total * 100).toInt() else 0

                // 3. 切换回主线程更新 UI (RemoteViews 内部处理了线程，但为了安全)
                val views = RemoteViews(context.packageName, R.layout.widget_dashboard)

                views.setTextViewText(R.id.widget_count_text, remaining.toString())
                views.setTextViewText(R.id.widget_date_text, LocalDate.now().format(DateTimeFormatter.ofPattern("MM月dd日 · EEE", Locale.CHINA)))

                // 进度条处理
                views.setProgressBar(R.id.widget_progress_bar, 100, progress, false)

                // 点击事件
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                views.setOnClickPendingIntent(R.id.widget_count_text, pendingIntent)
                views.setOnClickPendingIntent(R.id.header_layout, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        // 辅助方法：发送更新广播
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(context, DashboardWidget::class.java)
            intent.action = "com.example.mindflow.ACTION_WIDGET_UPDATE"
            context.sendBroadcast(intent)
        }
    }
}