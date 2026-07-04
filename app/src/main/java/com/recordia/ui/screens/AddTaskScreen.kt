package com.recordia.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.recordia.viewmodel.TaskViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 3600000) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis
    )

    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Tarea") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título de la tarea") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción (opcional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                maxLines = 3
            )

            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Fecha: ${formatDate(selectedDateMillis)}")
            }

            TextButton(
                onClick = { showTimePicker = true }
            ) {
                Text("Hora: ${formatTime(selectedDateMillis)}")
            }

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.addTask(title, description, selectedDateMillis)
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                enabled = title.isNotBlank()
            ) {
                Text("Guardar Tarea")
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDateMillis = combineDateAndTime(it, selectedDateMillis)
                        }
                        showDatePicker = false
                    }) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancelar")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showTimePicker) {
            DatePickerDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                        cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        cal.set(Calendar.MINUTE, timePickerState.minute)
                        selectedDateMillis = cal.timeInMillis
                        showTimePicker = false
                    }) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("Cancelar")
                    }
                }
            ) {
                TimePicker(state = timePickerState)
            }
        }
    }
}

private fun combineDateAndTime(dateMillis: Long, timeMillis: Long): Long {
    val date = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val time = Calendar.getInstance().apply { timeInMillis = timeMillis }
    date.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
    date.set(Calendar.MINUTE, time.get(Calendar.MINUTE))
    return date.timeInMillis
}

private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
}

private fun formatTime(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", cal.get(Calendar.MINUTE))}"
}
