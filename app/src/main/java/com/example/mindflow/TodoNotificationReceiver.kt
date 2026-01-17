package com.example.mindflow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class TodoNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getIntExtra("todo_id", -1)
        val todoTitle = intent.getStringExtra("todo_title") ?: "待办提醒"
        val todoContent = intent.getStringExtra("todo_content") ?: "别忘了你的任务哦"

        showNotification(context, todoId, todoTitle, todoContent)
    }

    private fun showNotification(context: Context, todoId: Int, title: String, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "todo_reminder_channel"

        // 1. 创建通知渠道 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "待办任务提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于显示待办事项的定时提醒"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. 点击通知打开 App
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. 构建通知
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 这里可以使用你自己的 logo 资源 R.drawable.ic_launcher_foreground
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // 4. 发送通知
        notificationManager.notify(todoId, notification)
    }
}