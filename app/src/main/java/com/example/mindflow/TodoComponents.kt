package com.example.mindflow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 1. 颜色定义
val HighPriorityColor = Color(0xFFFF6F61)
val MediumPriorityColor = Color(0xFFFFD166)
val LowPriorityColor = Color(0xFF118AB2)
val DoneColor = Color(0xFFE0E0E0)
val SelectedDateColor = Color(0xFF333333)
val PrimaryColor = Color(0xFF6200EE)
val IdeaColor = Color(0xFFFFF59D)

val MorandiColors = listOf(
    Color(0xFFFFF7D6), Color(0xFFE2F0CB), Color(0xFFD4E6F1),
    Color(0xFFFADBD8), Color(0xFFFDEBD0), Color(0xFFE8DAEF)
)

// 2. 通用辅助函数
fun formatWeek(date: LocalDate): String { return date.format(DateTimeFormatter.ofPattern("EEE", Locale.CHINA)) }
fun formatDate(date: LocalDate): String { return date.format(DateTimeFormatter.ofPattern("MM月dd日", Locale.CHINA)) }
fun formatRangeDate(millis: Long): String { return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MM/dd")) }
fun localDateToTimestamp(date: LocalDate): Long { return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
fun formatTime(millis: Long): String { return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }

// 3. 简单音频播放器组件
@Composable
fun SimpleAudioPlayer(audioPath: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.height(40.dp).fillMaxWidth().clickable {
            if (isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                try {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(audioPath)
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                    isPlaying = true
                    mediaPlayer.setOnCompletionListener {
                        isPlaying = false
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "播放失败，文件可能已丢失", Toast.LENGTH_SHORT).show()
                    isPlaying = false
                }
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = "Play/Stop",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPlaying) "正在播放录音..." else "点击播放语音备注",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// 4. 业务组件

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoCard(todo: TodoItem, onEvent: (TodoEvent) -> Unit, onLongClick: (TodoItem) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    val cardColor = if (todo.isDone) DoneColor else when (todo.priority) { 3 -> HighPriorityColor; 2 -> MediumPriorityColor; else -> LowPriorityColor }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEvent(TodoEvent.ToggleDone(todo)) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick(todo) }),
        colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = todo.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textDecoration = if (todo.isDone) TextDecoration.LineThrough else null, color = Color.White)
                    if (todo.isFromInspiration) { Spacer(modifier = Modifier.width(6.dp)); Icon(Icons.Outlined.Lightbulb, contentDescription = "From Inspiration", tint = Color.Yellow.copy(alpha = 0.9f), modifier = Modifier.size(16.dp)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 图片图标
                    if (todo.imageUris.isNotEmpty()) {
                        Icon(imageVector = if (todo.imageUris.size > 1) Icons.Outlined.PhotoLibrary else Icons.Outlined.Image, contentDescription = "Images", tint = Color.White, modifier = Modifier.size(16.dp))
                        if (todo.imageUris.size > 1) { Spacer(modifier = Modifier.width(2.dp)); Text(text = "${todo.imageUris.size}", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // ↓↓↓ 核心修改：如果有录音，只显示小图标，不显示播放器 ↓↓↓
                    if (todo.audioPath != null) {
                        Icon(Icons.Default.Mic, contentDescription = "Has Audio", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // ↑↑↑ 结束 ↑↑↑

                    if (todo.reminderTime != null) { Icon(Icons.Default.Notifications, contentDescription = "Reminder", tint = Color.White.copy(0.9f), modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(2.dp)); Text(text = formatTime(todo.reminderTime), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f), fontSize = 12.sp); Spacer(modifier = Modifier.width(8.dp)) }
                    if (todo.dueDate != null) { Text(text = formatDate(Instant.ofEpochMilli(todo.dueDate).atZone(ZoneId.systemDefault()).toLocalDate()), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f), fontSize = 12.sp); Spacer(modifier = Modifier.width(8.dp)) }
                    Box { IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White) }; DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { DropdownMenuItem(text = { Text("编辑") }, onClick = { onEvent(TodoEvent.EditTodo(todo)); showMenu = false }, leadingIcon = { Icon(Icons.Default.Edit, null) }); DropdownMenuItem(text = { Text("删除") }, onClick = { onEvent(TodoEvent.DeleteTodo(todo)); showMenu = false }, leadingIcon = { Icon(Icons.Default.Delete, null) }) } }
                }
            }
            if (todo.content.isNotBlank()) { Spacer(modifier = Modifier.height(8.dp)); Text(text = todo.content, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, color = Color.White.copy(0.9f)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InspirationGridCard(todo: TodoItem, onEvent: (TodoEvent) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val backgroundColor = MorandiColors[todo.id % MorandiColors.size]
    val cardColor = if (todo.isIncubated) backgroundColor.copy(alpha = 0.5f) else backgroundColor
    val contentColor = if (todo.isIncubated) Color.Black.copy(0.4f) else Color.Black.copy(0.8f)
    val isShortAndPunchy = todo.content.isBlank() || (todo.content.length < 20 && todo.title.length < 10 && todo.audioPath == null)

    Card(
        modifier = Modifier.fillMaxWidth().padding(4.dp).combinedClickable(onClick = { onEvent(TodoEvent.EditTodo(todo)) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showMenu = true }),
        colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(if (todo.isIncubated) 0.dp else 2.dp),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(text = todo.title, style = if (isShortAndPunchy) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = contentColor, modifier = Modifier.weight(1f), textDecoration = if (todo.isIncubated) TextDecoration.LineThrough else null, lineHeight = if (isShortAndPunchy) 32.sp else 24.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ↓↓↓ 核心修改：灵感卡片也只显示小图标 ↓↓↓
                    if (todo.audioPath != null) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (todo.imageUris.isNotEmpty()) {
                        Icon(Icons.Outlined.Image, contentDescription = null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (todo.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = todo.content, style = if (isShortAndPunchy) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = if (isShortAndPunchy) 0.9f else 0.7f), lineHeight = if (isShortAndPunchy) 28.sp else 20.sp, maxLines = 10, overflow = TextOverflow.Ellipsis, fontWeight = if (isShortAndPunchy) FontWeight.Medium else FontWeight.Normal)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (todo.isIncubated) { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color.Gray); Spacer(modifier = Modifier.width(4.dp)); Text("已孵化", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                    else { Icon(Icons.Default.PushPin, null, modifier = Modifier.size(12.dp), tint = contentColor.copy(alpha = 0.3f)); Spacer(modifier = Modifier.width(4.dp)); Text(text = "灵感", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.5f)) }
                }
                if (!todo.isIncubated) { Surface(color = Color.White.copy(0.6f), shape = CircleShape, modifier = Modifier.size(32.dp).clickable { onEvent(TodoEvent.IncubateTodo(todo)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); Toast.makeText(context, "✨ 灵感已孵化！", Toast.LENGTH_SHORT).show() }) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AutoAwesome, contentDescription = "Incubate", modifier = Modifier.size(18.dp), tint = PrimaryColor) } } }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { DropdownMenuItem(text = { Text("编辑") }, onClick = { onEvent(TodoEvent.EditTodo(todo)); showMenu = false }, leadingIcon = { Icon(Icons.Default.Edit, null) }); DropdownMenuItem(text = { Text("删除") }, onClick = { onEvent(TodoEvent.DeleteTodo(todo)); showMenu = false }, leadingIcon = { Icon(Icons.Default.Delete, null) }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RandomIdeaDialog(todo: TodoItem, onDismiss: () -> Unit, onEvent: (TodoEvent) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val backgroundColor = MorandiColors[todo.id % MorandiColors.size]

    Dialog(onDismissRequest = onDismiss) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(300.dp).background(backgroundColor.copy(alpha = 0.5f), CircleShape).padding(20.dp))
            Card(modifier = Modifier.fillMaxWidth().height(400.dp).padding(16.dp), colors = CardDefaults.cardColors(containerColor = backgroundColor), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(12.dp)) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✨ 灵感重现", style = MaterialTheme.typography.labelLarge, color = Color.Black.copy(0.4f), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(text = todo.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.Black.copy(0.8f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        // 这里保留播放器，因为这是一个详情查看的场景
                        if (todo.audioPath != null) { SimpleAudioPlayer(todo.audioPath); Spacer(modifier = Modifier.height(8.dp)) }
                        if (todo.content.isNotBlank()) { Text(text = todo.content, style = MaterialTheme.typography.bodyLarge, color = Color.Black.copy(0.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onEvent(TodoEvent.ShowRandomIdea) }, border = BorderStroke(1.dp, Color.Black.copy(0.2f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black.copy(0.6f))) { Text("下一个") }
                        Button(onClick = { onEvent(TodoEvent.IncubateTodo(todo)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); Toast.makeText(context, "灵感已孵化！", Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(0.8f))) { Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("立即孵化") }
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-12).dp).background(Color.White, CircleShape).border(1.dp, Color.LightGray, CircleShape)) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
        }
    }
}

@Composable
fun InspirationCard(todo: TodoItem) { Card(colors = CardDefaults.cardColors(containerColor = IdeaColor), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(16.dp)) { Text(text = todo.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black); if (todo.content.isNotBlank()) { Spacer(modifier = Modifier.height(8.dp)); Text(text = todo.content, style = MaterialTheme.typography.bodyMedium, color = Color.Black.copy(0.8f)) } } } }

@Composable
fun ContainerTag(text: String) { Box(modifier = Modifier.background(Color.White.copy(0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(text, fontSize = 10.sp, color = Color.White) } }

@Composable
fun EmptyStateView(isSearch: Boolean, text: String = "今天任务全部搞定！\n奖励自己一朵小红花 🌸") { Column(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp).background(if (isSearch) Color(0xFFF5F5F5) else Color(0xFFF3E5F5), CircleShape)) { Icon(imageVector = if (isSearch) Icons.Default.SearchOff else Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(60.dp), tint = if (isSearch) Color.Gray else Color(0xFFAB47BC)) }; Spacer(modifier = Modifier.height(24.dp)); Text(text = if (isSearch) "哎呀，没找到相关任务..." else text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 24.sp) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToActionContainer(todo: TodoItem, onDelete: () -> Unit, onCamera: () -> Unit, content: @Composable () -> Unit) { val haptic = LocalHapticFeedback.current; val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value -> when (value) { SwipeToDismissBoxValue.EndToStart -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDelete(); true }; SwipeToDismissBoxValue.StartToEnd -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onCamera(); false }; else -> false } }); SwipeToDismissBox(state = dismissState, backgroundContent = { val direction = dismissState.dismissDirection; val color = when (direction) { SwipeToDismissBoxValue.EndToStart -> Color.Red; SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50); else -> Color.Transparent }; val alignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart; val icon = if (direction == SwipeToDismissBoxValue.EndToStart) Icons.Outlined.Delete else Icons.Outlined.CameraAlt; Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp), contentAlignment = alignment) { Icon(imageVector = icon, contentDescription = null, tint = Color.White) } }, content = { content() }) }

// ↓↓↓ 核心修改：PhotoSessionDialog (查看详情弹窗) ↓↓↓
// 在这里增加音频播放器
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoSessionDialog(todo: TodoItem, onDismiss: () -> Unit, onAddPhoto: () -> Unit, onDeletePhoto: (String) -> Unit, onImageClick: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column {
                // 顶部：图片展示区
                Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color.Black)) {
                    if (todo.imageUris.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { todo.imageUris.size })
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            val currentUri = todo.imageUris[page]
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(model = currentUri, contentDescription = "Image", modifier = Modifier.fillMaxSize().clickable { onImageClick(currentUri) }, contentScale = ContentScale.Fit)
                                IconButton(onClick = { onDeletePhoto(currentUri) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(0.4f), CircleShape).size(36.dp)) { Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(20.dp)) }
                            }
                        }
                        if (todo.imageUris.size > 1) { Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("${pagerState.currentPage + 1} / ${todo.imageUris.size}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) } }
                    } else {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.AddAPhoto, null, tint = Color.Gray, modifier = Modifier.size(48.dp)); Spacer(modifier = Modifier.height(8.dp)); Text("点击下方按钮拍照", color = Color.Gray) }
                    }
                }

                // 底部：内容详情区
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(todo.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("包含 ${todo.imageUris.size} 张照片", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    // ★★★ 新增：在这里显示音频播放器 ★★★
                    if (todo.audioPath != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // 播放器标题
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("语音备注", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // 播放器组件
                        SimpleAudioPlayer(todo.audioPath)
                    }
                    // ↑↑↑ 结束 ↑↑↑

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onDismiss) { Text("关闭") }
                        Button(onClick = onAddPhoto, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            val btnIcon = if (todo.imageUris.isEmpty()) Icons.Outlined.AddAPhoto else Icons.Outlined.CameraAlt
                            val btnText = if (todo.imageUris.isEmpty()) "拍摄照片" else "继续拍照"
                            Icon(btnIcon, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(btnText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(imageUri: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(model = imageUri, contentDescription = "Full Screen", modifier = Modifier.fillMaxSize().clickable { onDismiss() }, contentScale = ContentScale.Fit)
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp).background(Color.Black.copy(0.5f), CircleShape)) { Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDateRangePicker(
    initialStart: Long?,
    initialEnd: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit
) {
    val datePickerState = rememberDateRangePickerState(initialSelectedStartDateMillis = initialStart, initialSelectedEndDateMillis = initialEnd)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { onConfirm(datePickerState.selectedStartDateMillis, datePickerState.selectedEndDateMillis); onDismiss() }) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) { DateRangePicker(state = datePickerState, modifier = Modifier.weight(1f)) }
}

@Composable
fun PieChart(progress: Float, modifier: Modifier = Modifier) { val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress"); Canvas(modifier = modifier) { drawArc(color = Color(0xFFEEEEEE), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 30f, cap = StrokeCap.Round)); drawArc(color = PrimaryColor, startAngle = -90f, sweepAngle = animatedProgress * 360f, useCenter = false, style = Stroke(width = 30f, cap = StrokeCap.Round)) } }

@Composable
fun PriorityChip(label: String, value: Int, current: Int, color: Color, onClick: () -> Unit) {
    val isSelected = value == current
    val backgroundColor = if (isSelected) color else color.copy(alpha = 0.2f)
    val textColor = if (isSelected) Color.White else Color.Black.copy(alpha = 0.7f)
    val borderColor = if (isSelected) Color.Transparent else color.copy(alpha = 0.5f)
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(backgroundColor).border(width = if(isSelected) 0.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = textColor) }
}

// ↓↓↓ AddTodoDialog (增加录音逻辑) ↓↓↓
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodoDialog(
    defaultDate: LocalDate,
    defaultCategory: String = "其他",
    todoToEdit: TodoItem? = null,
    onEvent: (TodoEvent) -> Unit
) {
    var title by remember { mutableStateOf(todoToEdit?.title ?: "") }
    var content by remember { mutableStateOf(todoToEdit?.content ?: "") }
    var priority by remember { mutableIntStateOf(todoToEdit?.priority ?: 2) }
    var selectedDate by remember { mutableStateOf<Long?>(todoToEdit?.dueDate ?: localDateToTimestamp(defaultDate)) }
    var category by remember { mutableStateOf(todoToEdit?.category ?: defaultCategory) }
    var audioPath by remember { mutableStateOf(todoToEdit?.audioPath) }

    var selectedTime by remember {
        mutableStateOf(
            if (todoToEdit?.reminderTime != null) {
                val time = Instant.ofEpochMilli(todoToEdit.reminderTime).atZone(ZoneId.systemDefault()).toLocalTime()
                time.hour to time.minute
            } else null
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isIdeaMode = category == "灵感"
    val isEditMode = todoToEdit != null

    // 录音相关状态
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }

    // 录音权限检查
    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) Toast.makeText(context, "需要录音权限", Toast.LENGTH_SHORT).show()
    }

    // 开始录音
    fun startRecording() {
        val file = File(context.filesDir, "audio_${System.currentTimeMillis()}.m4a")
        val newRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = newRecorder
        isRecording = true
        audioPath = file.absolutePath
    }

    // 停止录音
    fun stopRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recorder = null
        isRecording = false
    }

    DisposableEffect(Unit) {
        onDispose { stopRecording() }
    }

    if (showDatePicker) { val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate); DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { selectedDate = datePickerState.selectedDateMillis; showDatePicker = false }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }) { DatePicker(state = datePickerState) } }
    if (showTimePicker) { val initialHour = selectedTime?.first ?: 12; val initialMinute = selectedTime?.second ?: 0; val timePickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true); LaunchedEffect(timePickerState.hour, timePickerState.minute) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }; AlertDialog(onDismissRequest = { showTimePicker = false }, confirmButton = { TextButton(onClick = { selectedTime = timePickerState.hour to timePickerState.minute; showTimePicker = false }) { Text("确定") } }, dismissButton = { TextButton(onClick = { selectedTime = null; showTimePicker = false }) { Text("清除") } }, text = { TimePicker(state = timePickerState) }) }

    AlertDialog(
        onDismissRequest = { onEvent(TodoEvent.HideDialog) },
        title = { Column { Text(text = if (isEditMode) "编辑任务" else if (isIdeaMode) "记录灵感" else "添加任务", fontWeight = FontWeight.Bold); if (!isIdeaMode) { Spacer(modifier = Modifier.height(12.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Surface(onClick = { showDatePicker = true }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.Center) { Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(modifier = Modifier.width(4.dp)); Text(text = if (selectedDate != null) formatDate(Instant.ofEpochMilli(selectedDate!!).atZone(ZoneId.systemDefault()).toLocalDate()) else "日期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) } }; Surface(onClick = { showTimePicker = true }, shape = RoundedCornerShape(8.dp), color = if (selectedTime != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.Center) { Icon(Icons.Default.Notifications, null, modifier = Modifier.size(14.dp), tint = if(selectedTime != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.width(4.dp)); Text(text = if (selectedTime != null) String.format("%02d:%02d", selectedTime!!.first, selectedTime!!.second) else "提醒", style = MaterialTheme.typography.labelMedium, color = if(selectedTime != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) } } } } } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))

                // 录音区域
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (audioPath != null && !isRecording) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("语音已录制", modifier = Modifier.weight(1f))
                                IconButton(onClick = { audioPath = null }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isRecording) {
                                    stopRecording()
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    startRecording()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isRecording) "停止录音" else "按住录音 (点击开始)")
                        }
                    }
                }

                // 语音转文字输入 (可选，保留作为辅助)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("内容 (可选)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, shape = RoundedCornerShape(12.dp))
                    // 也可以选择移除这个转文字功能，既然已经有了纯录音
                }

                if (!isIdeaMode) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Text("优先级", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.width(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PriorityChip("低", 1, priority, LowPriorityColor) { priority = 1 }; PriorityChip("中", 2, priority, MediumPriorityColor) { priority = 2 }; PriorityChip("高", 3, priority, HighPriorityColor) { priority = 3 } } }; Column { Text("分类", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("工作", "学习", "生活", "健身").forEach { cat -> FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) }) } }; if (category !in listOf("工作", "学习", "生活", "健身", "灵感")) { Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("自定义分类") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)) } } } else { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lightbulb, null, tint = IdeaColor, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("将自动归档至“灵感”分类", style = MaterialTheme.typography.bodySmall, color = Color.Gray) } }
            }
        },
        confirmButton = { Button(onClick = { if (title.isNotBlank()) { val finalReminderTime = if (selectedDate != null && selectedTime != null) { val date = Instant.ofEpochMilli(selectedDate!!).atZone(ZoneId.systemDefault()).toLocalDate(); LocalDateTime.of(date, LocalTime.of(selectedTime!!.first, selectedTime!!.second)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() } else null; if (finalReminderTime != null) { val timeStr = Instant.ofEpochMilli(finalReminderTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm")); Toast.makeText(context, "已设置提醒：$timeStr", Toast.LENGTH_SHORT).show() }; onEvent(TodoEvent.SaveTodo(todoToEdit?.id, title, content, priority, selectedDate, finalReminderTime, category, audioPath)) } }) { Text(if (isEditMode) "更新" else "保存") } },
        dismissButton = { TextButton(onClick = { onEvent(TodoEvent.HideDialog) }) { Text("取消") } }
    )
}