package com.recordia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.recordia.service.ListeningService
import com.recordia.ui.screens.AddTaskScreen
import com.recordia.ui.screens.SettingsScreen
import com.recordia.ui.screens.TaskListScreen
import com.recordia.ui.theme.RecordIATheme
import com.recordia.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecordIATheme {
                RecordIAApp()
            }
        }
    }
}

@Composable
fun RecordIAApp() {
    val navController = rememberNavController()
    val viewModel: TaskViewModel = viewModel()
    var voiceResult by remember { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let { results ->
            if (results.isNotEmpty()) {
                viewModel.processTextInput(results[0])
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            val context = navController.context
            val intent = Intent(context, ListeningService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    NavHost(navController = navController, startDestination = "task_list") {
        composable("task_list") {
            TaskListScreen(
                viewModel = viewModel,
                onAddTask = { navController.navigate("add_task") },
                onSettings = { navController.navigate("settings") },
                onVoiceInput = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para crear una tarea...")
                    }
                    try {
                        voiceLauncher.launch(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            navController.context,
                            "Reconocimiento de voz no disponible",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        composable("add_task") {
            AddTaskScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
