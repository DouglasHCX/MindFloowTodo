package com.example.mindflow

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import com.example.mindflow.settings.SettingsScreen
import com.example.mindflow.settings.SystemSettingsScreen

enum class AppScreen {
    Todo, Inspiration, Stats, Me, Settings, SystemSettings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(applicationContext)
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TodoViewModel(this@MainActivity.application, db.todoDao()) as T
            }
        })[TodoViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(this, "未开启通知权限，将无法收到任务提醒", Toast.LENGTH_LONG).show()
                }
            }
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val state by viewModel.state.collectAsState()
            val currentDensity = LocalDensity.current
            val customDensity = Density(currentDensity.density, state.appFontScale)

            CompositionLocalProvider(LocalDensity provides customDensity) {
                var showSplash by remember { mutableStateOf(true) }
                MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                    if (showSplash) {
                        SplashScreen(onTimeout = { showSplash = false })
                    } else {
                        if (state.isFirstLaunch) {
                            OnboardingScreen(onFinish = { viewModel.onEvent(TodoEvent.CompleteOnboarding) })
                        } else {
                            MainScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(true) {
        launch { scale.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 800, easing = { OvershootInterpolator(2f).getInterpolation(it) })) }
        launch { alpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 800)) }
        delay(2000); onTimeout()
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(100.dp).scale(scale.value), shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) { Icon(Icons.Default.Edit, "Logo", tint = Color.White, modifier = Modifier.padding(20.dp)) }
            Spacer(modifier = Modifier.height(16.dp))
            Text("MindFlow", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha.value))
            Text("捕捉每一个灵感", style = MaterialTheme.typography.bodyMedium, color = Color.Gray.copy(alpha = alpha.value))
        }
    }
}

@Composable
fun MainScreen(viewModel: TodoViewModel) {
    val state by viewModel.state.collectAsState()
    val dayStatusMap by viewModel.dayStatusMap.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var sessionTodoId by remember { mutableStateOf<Int?>(null) }
    // 确保 activeSessionTodo 能够实时获取最新的 todo 状态（包括刚添加的图片）
    val activeSessionTodo = remember(sessionTodoId, state.todos) { state.todos.find { it.id == sessionTodoId } }

    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val currentToast = remember { mutableStateOf<Toast?>(null) }

    // 备份导出 Launcher
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val jsonString = viewModel.generateBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                Toast.makeText(context, "备份成功！文件已保存", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "备份失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val interceptedOnEvent: (TodoEvent) -> Unit = { event ->
        if (event is TodoEvent.ToggleDone) {
            currentToast.value?.cancel()
            val message = if (!event.todo.isDone) EncouragementManager.getRandomMessage() else EncouragementManager.getRandomComfortMessage()
            val newToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            newToast.show()
            currentToast.value = newToast
        }
        handleEventWithSnackbar(event, viewModel, scope, snackbarHostState)
    }

    fun createImageUri(): Uri {
        val directory = File(context.filesDir, "images")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "todo_img_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    // ★★★ 1. 修复：添加 galleryLauncher 定义 ★★★
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && activeSessionTodo != null) {
            scope.launch {
                // 使用 ImageUtils 压缩并保存图片到私有目录
                val savedPath = ImageUtils.compressAndSaveImage(context, uri)
                if (savedPath != null) {
                    viewModel.onEvent(TodoEvent.UpdateTodoImage(activeSessionTodo!!, savedPath))
                    Toast.makeText(context, "图片已添加 🖼️", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "图片处理失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ★★★ 2. 修复：相机拍照后也进行压缩 ★★★
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && activeSessionTodo != null && tempImageUri != null) {
            scope.launch {
                // 1. 尝试压缩拍摄的图片
                val compressedPath = ImageUtils.compressAndSaveImage(context, tempImageUri!!)

                if (compressedPath != null) {
                    // 2. 如果压缩成功，删除原始的大图以节省空间
                    try {
                        val pathSegments = tempImageUri!!.pathSegments
                        val filename = pathSegments.last()
                        val originalFile = File(File(context.filesDir, "images"), filename)
                        if (originalFile.exists()) {
                            originalFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 3. 保存压缩后的图片路径
                    viewModel.onEvent(TodoEvent.UpdateTodoImage(activeSessionTodo!!, compressedPath))
                    Toast.makeText(context, "照片已保存 (已压缩) 📸", Toast.LENGTH_SHORT).show()
                } else {
                    // 4. 如果压缩失败，则回退使用原始图片
                    viewModel.onEvent(TodoEvent.UpdateTodoImage(activeSessionTodo!!, tempImageUri.toString()))
                    Toast.makeText(context, "照片已保存 📸", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.currentScreen != AppScreen.SystemSettings && state.currentScreen != AppScreen.Settings) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                    NavigationBarItem(icon = { Icon(Icons.Default.List, "Todo") }, label = { Text("待办") }, selected = state.currentScreen == AppScreen.Todo, onClick = { viewModel.setScreen(AppScreen.Todo) })
                    NavigationBarItem(icon = { Icon(Icons.Outlined.Lightbulb, "Idea") }, label = { Text("灵感") }, selected = state.currentScreen == AppScreen.Inspiration, onClick = { viewModel.setScreen(AppScreen.Inspiration) })
                    NavigationBarItem(icon = { Icon(Icons.Default.DateRange, "Stats") }, label = { Text("统计") }, selected = state.currentScreen == AppScreen.Stats, onClick = { viewModel.setScreen(AppScreen.Stats) })
                    NavigationBarItem(icon = { Icon(Icons.Default.Person, "Me") }, label = { Text("我的") }, selected = state.currentScreen == AppScreen.Me, onClick = { viewModel.setScreen(AppScreen.Me) })
                }
            }
        },
        floatingActionButton = {
            val showAddButton = (state.currentScreen == AppScreen.Todo || state.currentScreen == AppScreen.Inspiration)
            val isToday = state.selectedDate == LocalDate.now()
            val showBackToToday = showAddButton && state.currentScreen == AppScreen.Todo && !isToday

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AnimatedVisibility(visible = showBackToToday, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    FloatingActionButton(onClick = { viewModel.onEvent(TodoEvent.SelectDate(LocalDate.now())) }, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                        Icon(Icons.Filled.WbSunny, contentDescription = "Back to Today")
                    }
                }
                if (showAddButton) {
                    FloatingActionButton(onClick = { viewModel.onEvent(TodoEvent.ShowDialog) }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, "Add") }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (state.currentScreen) {
                AppScreen.Todo -> TodoScreenContent(
                    state = state,
                    dayStatusMap = dayStatusMap,
                    onEvent = interceptedOnEvent,
                    onOpenSession = { todo -> sessionTodoId = todo.id }
                )
                AppScreen.Inspiration -> InspirationScreenContent(state = state, onEvent = interceptedOnEvent)
                AppScreen.Stats -> StatsScreenContent(
                    state = state,
                    dayStatusMap = dayStatusMap,
                    onEvent = viewModel::onEvent
                )
                AppScreen.Me -> MeScreen(
                    state = state,
                    onExportData = { exportLauncher.launch("mindflow_backup_${System.currentTimeMillis()}.json") },
                    onEvent = viewModel::onEvent,
                    onNavigateToSettings = { viewModel.setScreen(AppScreen.SystemSettings) }
                )
                AppScreen.Settings -> SettingsScreen(
                    onNavigateToSystemSettings = { viewModel.setScreen(AppScreen.SystemSettings) }
                )
                AppScreen.SystemSettings -> SystemSettingsScreen(
                    state = state,
                    onBackClick = { viewModel.setScreen(AppScreen.Me) },
                    onEvent = viewModel::onEvent,
                    onExportData = { exportLauncher.launch("mindflow_backup_${System.currentTimeMillis()}.json") }
                )
            }
        }

        if (state.isAddingTodo) {
            AddTodoDialog(
                defaultDate = state.selectedDate,
                defaultCategory = if (state.currentScreen == AppScreen.Inspiration) CATEGORY_IDEA else "其他",
                todoToEdit = state.editingTodo,
                onEvent = viewModel::onEvent
            )
        }

        if (activeSessionTodo != null) {
            // ★★★ 3. 使用 PhotoSessionDialog 并传入 galleryLauncher ★★★
            PhotoSessionDialog(
                todo = activeSessionTodo!!,
                onDismiss = { sessionTodoId = null },
                onAddPhoto = { tempImageUri = createImageUri(); cameraLauncher.launch(tempImageUri!!) },
                onGalleryClick = {
                    // 启动系统照片选择器 (仅显示图片)
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onDeletePhoto = { uri -> viewModel.onEvent(TodoEvent.DeleteTodoImage(activeSessionTodo!!, uri)); Toast.makeText(context, "照片已删除", Toast.LENGTH_SHORT).show() },
                onImageClick = { uri -> fullScreenImageUri = uri }
            )
        }

        if (fullScreenImageUri != null) {
            FullScreenImageDialog(imageUri = fullScreenImageUri!!, onDismiss = { fullScreenImageUri = null })
        }
    }
}

fun handleEventWithSnackbar(event: TodoEvent, viewModel: TodoViewModel, scope: kotlinx.coroutines.CoroutineScope, snackbarHostState: SnackbarHostState) {
    if (event is TodoEvent.DeleteTodo) {
        viewModel.onEvent(event); scope.launch { if (snackbarHostState.showSnackbar("已删除", "撤销", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) { viewModel.onEvent(TodoEvent.UndoDelete(event.todo)) } }
    } else {
        viewModel.onEvent(event)
    }
}