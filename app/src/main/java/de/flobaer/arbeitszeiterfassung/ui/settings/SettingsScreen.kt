package de.flobaer.arbeitszeiterfassung.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val apiUrlFromDataStore by viewModel.apiUrl.collectAsState()
    var apiUrl by remember(apiUrlFromDataStore) { mutableStateOf(apiUrlFromDataStore) }
    val apiStatus by viewModel.apiStatus.collectAsState()
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    val nextSyncMillis by viewModel.nextPeriodicSyncTimeMillis.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshNextPeriodicSync()
        viewModel.showSaveConfirmation.collect { 
            coroutineScope.launch {
                snackbarHostState.showSnackbar("API URL gespeichert")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = { 
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") 
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { viewModel.saveApiUrl(apiUrl) }) {
                Text("Speichern")
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (loggedInUser == null) {
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Benutzername") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { viewModel.login(username, password) }) {
                    Text("Anmelden")
                }
            } else {
                val user = loggedInUser!!
                val fullName = listOfNotNull(user.firstName, user.lastName).joinToString(" ")
                Text("Angemeldet als: $fullName (${user.username})")
                Button(onClick = { viewModel.logout() }) {
                    Text("Abmelden")
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.checkApiStatus() },
                    enabled = apiStatus != ApiStatus.Loading
                ) {
                    Text("Verbindung testen")
                }
                when (apiStatus) {
                    is ApiStatus.Success -> Icon(Icons.Default.CheckCircle, contentDescription = "Erfolg", tint = Color.Green)
                    is ApiStatus.Warning -> Icon(Icons.Default.Warning, contentDescription = "Warnung", tint = Color(0xFFFFA500)) // Orange
                    is ApiStatus.Error -> Icon(Icons.Default.Error, contentDescription = "Fehler", tint = Color.Red)
                    is ApiStatus.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else -> {}
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Sync‑Abschnitt
            val nextSyncText = remember(nextSyncMillis) {
                nextSyncMillis?.let { millis ->
                    val df = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    df.format(java.util.Date(millis))
                } ?: "Unbekannt"
            }
            Text(text = "Nächster periodischer Sync: $nextSyncText")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.triggerSyncNow() }) {
                    Text("Jetzt synchronisieren")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Version: ${de.flobaer.arbeitszeiterfassung.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
            )
        }
    }
}