package com.example.todolist.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {
    // 默认字体倍率为 1.0 (正常大小)
    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    // 更新字体大小的方法
    fun updateFontScale(scale: Float) {
        _fontScale.value = scale
    }
}