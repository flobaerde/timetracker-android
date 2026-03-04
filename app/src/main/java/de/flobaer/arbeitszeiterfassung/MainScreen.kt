package de.flobaer.arbeitszeiterfassung

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import de.flobaer.arbeitszeiterfassung.data.local.SyncStatus
import de.flobaer.arbeitszeiterfassung.ui.mapIcon
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    paddingValues: PaddingValues,
    viewModel: MainViewModel = hiltViewModel(),
    onEditWorkTime: (Long) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val isInPause by viewModel.isInPause.collectAsState()
    val activePause by viewModel.activePause.collectAsState()
    val workTimes by viewModel.workTimes.collectAsState()
    val locations by viewModel.locations.collectAsState()
    val updateFailed by viewModel.updateFailed.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var editingWorkTime by remember { mutableStateOf<WorkTimeItem?>(null) }
    var deletingWorkTime by remember { mutableStateOf<WorkTimeItem?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLocations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLocations()
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }

    val snackbarHostState = remember { SnackbarHostState() }

    fun Instant.toDate(): Date = Date(this.toEpochMilliseconds())

    var selectedLocation by remember { mutableStateOf<LocationOption?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is MainUiState.Error) {
            snackbarHostState.showSnackbar((uiState as MainUiState.Error).message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isRunning) {
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (updateFailed) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, shape = MaterialTheme.shapes.small)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { dropdownExpanded = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = selectedLocation?.icon
                                if (icon != null) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(selectedLocation?.name ?: "--- bitte Ort wählen ---")
                            }
                        }
                    }
                    DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                        locations.forEach {
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(it.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(it.name)
                                }
                            }, onClick = {
                                selectedLocation = it
                                dropdownExpanded = false
                            })
                        }
                    }
                }
            }
        }


        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (!isRunning) {
                        viewModel.startWorkTime(selectedLocation!!.id)
                    } else {
                        viewModel.stopWorkTime()
                    }
                },
                enabled = (isRunning || selectedLocation != null) && !isInPause
            ) {
                Text(if (isRunning) "Stopp" else "Start")
            }

            if (isRunning) {
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        if (isInPause) {
                            viewModel.stopPause()
                        } else {
                            viewModel.startPause()
                        }
                    }
                ) {
                    Text(if (isInPause) "Fortsetzen" else "Pause")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isRunning) {
            val runningWorkTime = workTimes.firstOrNull { it.endTime == null }
            if (runningWorkTime != null) {
                val startInstant = runningWorkTime.startTime
                val startDate = startInstant.toDate()

                var elapsedTime by remember { mutableLongStateOf(0L) }
                var pauseElapsedTime by remember { mutableLongStateOf(0L) }

                LaunchedEffect(isRunning, isInPause, activePause) {
                    while (isRunning) {
                        if (isInPause && activePause != null) {
                            pauseElapsedTime = (System.currentTimeMillis() - activePause!!.startTime.toEpochMilliseconds()) / 1000
                        } else {
                            elapsedTime = (System.currentTimeMillis() - startInstant.toEpochMilliseconds()) / 1000
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }

                Text(
                    "Start: ${dateFormatter.format(startDate)} ${timeFormatter.format(startDate)}",
                    style = MaterialTheme.typography.titleMedium
                )
                val runningLocationName = locations.find { it.id == runningWorkTime.locationId }?.name ?: "Unbekannt"
                Text("Ort: $runningLocationName", style = MaterialTheme.typography.titleMedium)
                
                if (isInPause) {
                    val pHours = pauseElapsedTime / 3600
                    val pMinutes = (pauseElapsedTime % 3600) / 60
                    Text(
                        "In Pause: ${String.format("%02d:%02d", pHours, pMinutes)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    val hours = elapsedTime / 3600
                    val minutes = (elapsedTime % 3600) / 60
                    Text(
                        "Laufzeit: ${String.format("%02d:%02d", hours, minutes)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Zeiträume:", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        val dayOfWeekFormatter = SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMAN)
        val shortTimeFormatter = SimpleDateFormat("HH:mm", Locale.GERMAN)

        fun formatDuration(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return String.format("%02d:%02d", hours, minutes)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(workTimes, key = { it.id }) { workTime ->
                val location = locations.find { it.id == workTime.locationId }
                val startDate = workTime.startTime.toDate()
                val endDate = workTime.endTime?.toDate()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        // Erste Zeile: Datum links, Sync-Status rechts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayOfWeekFormatter.format(startDate),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = when (workTime.syncStatus) {
                                    SyncStatus.SYNCED -> Icons.Default.CloudDone
                                    else -> Icons.Default.CloudOff
                                },
                                contentDescription = "Sync Status",
                                tint = if (workTime.syncStatus == SyncStatus.SYNCED) Color.Gray else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Zweite Zeile: Ort-Icon, Start-Endzeit links, Kaffeetasse + Pausenanzahl rechts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (location != null) location.icon else Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Ort",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val timeRange = if (endDate != null) {
                                    "${shortTimeFormatter.format(startDate)} - ${shortTimeFormatter.format(endDate)}"
                                } else {
                                    "${shortTimeFormatter.format(startDate)} - laufend"
                                }
                                Text(text = timeRange, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalCafe,
                                    contentDescription = "Pausen",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "${workTime.pauseCount}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Dritte Zeile: Netto (Brutto) links, Bearbeiten/Löschen rechts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val durationText = if (endDate != null) {
                                "${formatDuration(workTime.netDurationMillis)} (${formatDuration(workTime.totalDurationMillis)})"
                            } else {
                                ""
                            }
                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row {
                                IconButton(
                                    onClick = { onEditWorkTime(workTime.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Bearbeiten",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { deletingWorkTime = workTime },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Löschen",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        this@Box.apply {
            androidx.compose.material3.SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (uiState is MainUiState.Loading && workTimes.isEmpty()) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

    editingWorkTime?.let { workTime ->
        EditIntervalDialog(
            workTimeItem = workTime,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
            locations = locations,
            onDismiss = { editingWorkTime = null },
            onSave = { newStart, newEnd, newLocationId ->
                val startInstant = Instant.fromEpochMilliseconds(newStart.time)
                val endInstant = Instant.fromEpochMilliseconds(newEnd.time)
                viewModel.editWorkTime(workTime, newLocationId, startInstant, endInstant)
                editingWorkTime = null
            }
        )
    }

    deletingWorkTime?.let { workTime ->
        AlertDialog(
            onDismissRequest = { deletingWorkTime = null },
            title = { Text("Löschen bestätigen") },
            text = { Text("Möchten Sie diesen Zeitraum wirklich endgültig löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkTime(workTime)
                        deletingWorkTime = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                )
                {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingWorkTime = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}