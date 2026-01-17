package com.example.mindflow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast

object NotificationScheduler {

    fun schedule(context: Context, todo: TodoItem) {
        if (todo.reminderTime == null) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // ★★★ 核心修改：移除 5 分钟提前量，改为准时提醒 ★★★
        val triggerTime = todo.reminderTime

        // 如果触发时间已经过去了，就不设置了
        if (triggerTime < System.currentTimeMillis()) return

        val intent = Intent(context, TodoNotificationReceiver::class.java).apply {
            putExtra("todo_id", todo.id)
            putExtra("todo_title", todo.title)
            // ★★★ 修改：移除“(5分钟后开始)”后缀，直接显示原始内容 ★★★
            putExtra("todo_content", todo.content)
        }

        // 使用 todo.id 作为 requestCode，确保每个任务的 PendingIntent 是唯一的
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todo.id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 设置精确闹钟
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    // 如果没有精确闹钟权限，使用非精确的
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("Scheduler", "Notification scheduled for: $triggerTime")
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context, "无法设置提醒，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }

    // 取消提醒
    fun cancel(context: Context, todo: TodoItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TodoNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            todo.id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}