package com.recordia.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.recordia.service.ListeningService
import com.recordia.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentApiKey by viewModel.apiKey.collectAsState()
    val currentProvider by viewModel.aiProvider.collectAsState()
    val isListening by viewModel.isListening().collectAsState()
    val isPaused by viewModel.isPaused().collectAsState()

    var apiKey by remember { mutableStateOf(currentApiKey) }
    var showKey by remember { mutableStateOf(false) }

    val hasMicPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
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
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Proveedor de IA",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Gemini (Google) es gratis. OpenAI necesita crédito.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Column(Modifier.selectableGroup()) {
                        listOf("openai" to "OpenAI (de pago)", "gemini" to "Gemini API (gratis)").forEach { (value, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = currentProvider == value,
                                        onClick = { viewModel.setAiProvider(value) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentProvider == value,
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (currentProvider == "gemini") "API Key de Gemini" else "API Key de OpenAI",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (currentProvider == "gemini")
                            "Obtén tu key gratis en https://aistudio.google.com/apikey"
                        else
                            "Obtén tu key en https://platform.openai.com/api-keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        visualTransformation = if (showKey)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        singleLine = true
                    )

                    Button(
                        onClick = { viewModel.setApiKey(apiKey) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar API Key")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Escucha Continua",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isListening) "Activada" else "Desactivada",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isListening,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (apiKey.isBlank()) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Configura tu API Key de Gemini primero",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        return@Switch
                                    }
                                    if (hasMicPermission) {
                                        val intent = Intent(context, ListeningService::class.java).apply {
                                            putExtra("api_key", apiKey)
                                            putExtra("ai_provider", currentProvider)
                                        }
                                        ContextCompat.startForegroundService(context, intent)
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Permiso de micrófono no concedido",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    val intent = Intent(context, ListeningService::class.java).apply {
                                        action = ListeningService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                }
                            }
                        )
                    }

                    if (isListening && isPaused) {
                        Button(
                            onClick = {
                                val intent = Intent(context, ListeningService::class.java).apply {
                                    action = ListeningService.ACTION_RESUME
                                    putExtra("api_key", apiKey)
                                    putExtra("ai_provider", currentProvider)
                                }
                                context.startService(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reanudar Escucha")
                        }
                    }

                    if (!hasMicPermission) {
                        Text(
                            text = "Permiso de micrófono no concedido",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            Text(
                                text = "Recomendación: Desactivar optimización de batería para mejor funcionamiento",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Entrada de Texto",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Escribe una frase y RecordIA extraerá la tarea automáticamente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    var textInput by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Ej: 'Comprar leche mañana a las 10am'") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        maxLines = 3
                    )

                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.processTextInput(textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = textInput.isNotBlank() && apiKey.isNotBlank()
                    ) {
                        Text("Extraer Tarea")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "RecordIA v1.0.0 - Tu secretario virtual inteligente",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
