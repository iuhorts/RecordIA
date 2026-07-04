package com.recordia.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recordia.data.Task
import com.recordia.data.TaskRepository
import com.recordia.notification.cancelTaskReminder
import com.recordia.notification.scheduleTaskReminder
import com.recordia.service.ListeningService
import com.recordia.service.TaskExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaskRepository(application)
    private val prefs = application.getSharedPreferences("recordia", Context.MODE_PRIVATE)

    val pendingTasks: StateFlow<List<Task>> = repository.getPendingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<Task>> = repository.getCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _aiProvider = MutableStateFlow(prefs.getString("ai_provider", "openai") ?: "openai")
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    init {
        val savedKey = prefs.getString("api_key", "") ?: ""
        if (savedKey.isNotBlank()) {
            _apiKey.value = savedKey
        }
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
        prefs.edit().putString("api_key", key).apply()
    }

    fun setAiProvider(provider: String) {
        _aiProvider.value = provider
        prefs.edit().putString("ai_provider", provider).apply()
    }

    fun addTask(title: String, description: String, dueDateMillis: Long) {
        viewModelScope.launch {
            try {
                val task = Task(
                    title = title,
                    description = description,
                    dueDateMillis = dueDateMillis,
                    source = "manual"
                )
                val id = repository.insertTask(task)
                val savedTask = task.copy(id = id)
                try {
                    scheduleTaskReminder(getApplication(), savedTask)
                } catch (e: Exception) {
                    android.util.Log.w("TaskViewModel", "Failed to schedule reminder", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Failed to add task", e)
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(task.id, !task.isCompleted)
            if (!task.isCompleted) {
                cancelTaskReminder(getApplication(), task.id)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            cancelTaskReminder(getApplication(), task.id)
        }
    }

    fun processTextInput(text: String) {
        viewModelScope.launch {
            val extractor = TaskExtractor(_apiKey.value, _aiProvider.value, repository)
            extractor.processText(text)
        }
    }

    fun isListening(): StateFlow<Boolean> = ListeningService.isListening
    fun isPaused(): StateFlow<Boolean> = ListeningService.isPaused
}
