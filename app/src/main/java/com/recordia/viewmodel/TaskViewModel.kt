package com.recordia.viewmodel

import android.app.Application
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

    val pendingTasks: StateFlow<List<Task>> = repository.getPendingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<Task>> = repository.getCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun addTask(title: String, description: String, dueDateMillis: Long) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description,
                dueDateMillis = dueDateMillis,
                source = "manual"
            )
            val id = repository.insertTask(task)
            val savedTask = task.copy(id = id)
            scheduleTaskReminder(getApplication(), savedTask)
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
            val extractor = TaskExtractor(_apiKey.value, repository)
            extractor.processText(text)
        }
    }

    fun isListening(): StateFlow<Boolean> = ListeningService.isListening

    fun isPaused(): StateFlow<Boolean> = ListeningService.isPaused
}
