package com.recordia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.recordia.data.Task
import com.recordia.ui.components.TaskItem
import com.recordia.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskViewModel,
    onAddTask: () -> Unit,
    onSettings: () -> Unit,
    onVoiceInput: () -> Unit
) {
    val pendingTasks by viewModel.pendingTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    val isListening by viewModel.isListening().collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RecordIA") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onVoiceInput) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Entrada de voz",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir tarea")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isListening) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎤 Escuchando...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (pendingTasks.isEmpty() && completedTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No hay tareas aún",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Añade una tarea o activa la escucha continua",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            if (pendingTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Pendientes",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(pendingTasks, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Completadas",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(completedTasks, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }
}
