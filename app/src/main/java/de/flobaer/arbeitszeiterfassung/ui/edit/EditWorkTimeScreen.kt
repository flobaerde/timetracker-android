package de.flobaer.arbeitszeiterfassung.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.flobaer.arbeitszeiterfassung.data.local.PauseEntity
import kotlinx.datetime.Instant
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkTimeScreen(
    workTimeId: Long,
    onBack: () -> Unit,
    viewModel: EditWorkTimeViewModel = hiltViewModel()
) {
    LaunchedEffect(workTimeId) {
        viewModel.loadWorkTime(workTimeId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val locations by viewModel.locations.collectAsState()

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.GERMAN) }
    val dateFormatter = remember { SimpleDateFormat("EEE, d. MMMM", Locale.GERMAN) }

    var showDatePickerFor by remember { mutableStateOf<String?>(null) }
    var showTimePickerFor by remember { mutableStateOf<String?>(null) }
    var deletingPause by remember { mutableStateOf<PauseEntity?>(null) }

    val dateWidth: Dp = 160.dp
    val timeWidth: Dp = 84.dp

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle Errors via Snackbar
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is EditUiState.Success && state.error != null) {
            snackbarHostState.showSnackbar(state.error)
            viewModel.dismissError()
        }
    }

    // Handle successful save
    LaunchedEffect(uiState) {
        if (uiState is EditUiState.Saved) {
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Zeitraum bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (uiState is EditUiState.Success) {
                        TextButton(onClick = { viewModel.saveChanges() }) {
                            Text("Speichern")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is EditUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is EditUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(state.message)
                }
            }
            is EditUiState.Success -> {
                val start = state.editedStartTime
                val end = state.editedEndTime

                val grossMillis = remember(start, end) {
                    val referenceEnd = end ?: kotlinx.datetime.Clock.System.now()
                    (referenceEnd.toEpochMilliseconds() - start.toEpochMilliseconds()).coerceAtLeast(0)
                }
                val pauseMillis = remember(state.pauses) {
                    val now = kotlinx.datetime.Clock.System.now()
                    state.pauses.sumOf { p ->
                        val s = p.startTime.toEpochMilliseconds()
                        val e = (p.endTime ?: now).toEpochMilliseconds()
                        (e - s).coerceAtLeast(0)
                    }
                }
                val netMillis = (grossMillis - pauseMillis).coerceAtLeast(0)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCell("Brutto", formatDuration(grossMillis), modifier = Modifier.weight(1f))
                            StatCell("Netto", formatDuration(netMillis), modifier = Modifier.weight(1f))
                            StatCell("Pausen", formatDuration(pauseMillis), modifier = Modifier.weight(1f))
                        }
                    }
                    item { HorizontalDivider() }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ort", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                            var expanded by remember { mutableStateOf(false) }
                            val selected = locations.find { it.id == state.editedLocationId }

                            Box {
                                TextButton(onClick = { expanded = true }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val icon = selected?.icon
                                        if (icon != null) {
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(selected?.name ?: "--- bitte Ort wählen ---")
                                    }
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    locations.forEach { loc ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(loc.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(loc.name)
                                                }
                                            },
                                            onClick = {
                                                viewModel.updateLocation(loc.id)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { HorizontalDivider() }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Start", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                            DateTimeCell(
                                label = dateFormatter.format(Date(start.toEpochMilliseconds())),
                                width = dateWidth,
                                onClick = { showDatePickerFor = "start" }
                            )
                            DateTimeCell(
                                label = timeFormatter.format(Date(start.toEpochMilliseconds())),
                                width = timeWidth,
                                onClick = { showTimePickerFor = "start" }
                            )
                        }
                    }
                    item {
                        val isRunning = state.workTime.endTime == null
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ende", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                            DateTimeCell(
                                label = end?.let { dateFormatter.format(Date(it.toEpochMilliseconds())) } ?: "offen",
                                width = dateWidth,
                                onClick = { if (!isRunning) showDatePickerFor = "end" }
                            )
                            DateTimeCell(
                                label = end?.let { timeFormatter.format(Date(it.toEpochMilliseconds())) } ?: "offen",
                                width = timeWidth,
                                onClick = { if (!isRunning) showTimePickerFor = "end" }
                            )
                        }
                    }
                    item { HorizontalDivider() }
                    item {
                        Text("Pausen", style = MaterialTheme.typography.titleMedium)
                    }
                    items(state.pauses.size) { index ->
                        val pause = state.pauses[index]
                        val isEditing = state.editingPauseId == pause.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            if (!isEditing) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.beginEditPause(pause.id) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val pStartText = timeFormatter.format(Date(pause.startTime.toEpochMilliseconds()))
                                            val pEndText = pause.endTime?.let { timeFormatter.format(Date(it.toEpochMilliseconds())) } ?: "offen"
                                            Text(
                                                text = "$pStartText - $pEndText",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { deletingPause = pause }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Pause löschen",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            } else {
                                // Edit view for pause
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Start", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                                    val pStart = state.editingPauseStart ?: pause.startTime
                                    DateTimeCell(
                                        label = dateFormatter.format(Date(pStart.toEpochMilliseconds())),
                                        width = dateWidth,
                                        onClick = { showDatePickerFor = "p_start" }
                                    )
                                    DateTimeCell(
                                        label = timeFormatter.format(Date(pStart.toEpochMilliseconds())),
                                        width = timeWidth,
                                        onClick = { showTimePickerFor = "p_start" }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ende", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                                    val pEnd = state.editingPauseEnd ?: pause.endTime
                                    DateTimeCell(
                                        label = pEnd?.let { dateFormatter.format(Date(it.toEpochMilliseconds())) } ?: "offen",
                                        width = dateWidth,
                                        onClick = { showDatePickerFor = "p_end" }
                                    )
                                    DateTimeCell(
                                        label = pEnd?.let { timeFormatter.format(Date(it.toEpochMilliseconds())) } ?: "offen",
                                        width = timeWidth,
                                        onClick = { showTimePickerFor = "p_end" }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.saveEditingPause() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Speichern")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.cancelEditPause() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Abbrechen")
                                    }
                                }
                            }
                        }
                    }

                    // Button "Neue Pause anlegen" or Edit-Dialog for new pause
                    item {
                        if (state.isAddingNewPause) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Start", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                                    val pStart = state.editingPauseStart!!
                                    DateTimeCell(
                                        label = dateFormatter.format(Date(pStart.toEpochMilliseconds())),
                                        width = dateWidth,
                                        onClick = { showDatePickerFor = "p_start" }
                                    )
                                    DateTimeCell(
                                        label = timeFormatter.format(Date(pStart.toEpochMilliseconds())),
                                        width = timeWidth,
                                        onClick = { showTimePickerFor = "p_start" }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ende", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                                    val pEnd = state.editingPauseEnd!!
                                    DateTimeCell(
                                        label = dateFormatter.format(Date(pEnd.toEpochMilliseconds())),
                                        width = dateWidth,
                                        onClick = { showDatePickerFor = "p_end" }
                                    )
                                    DateTimeCell(
                                        label = timeFormatter.format(Date(pEnd.toEpochMilliseconds())),
                                        width = timeWidth,
                                        onClick = { showTimePickerFor = "p_end" }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.saveEditingPause() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Speichern")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.cancelEditPause() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Abbrechen")
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { viewModel.startAddingPause() },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                    Text(
                                        text = "Neue Pause anlegen",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    }
                }

                // Dialogs
                val currentForDate = when (showDatePickerFor) {
                    "start" -> start
                    "end" -> end
                    "p_start" -> state.editingPauseStart ?: state.pauses.find { it.id == state.editingPauseId }?.startTime
                    "p_end" -> state.editingPauseEnd
                        ?: (if (state.isAddingNewPause) null else state.pauses.find { it.id == state.editingPauseId }?.endTime)
                        ?: state.editingPauseStart
                        ?: state.pauses.find { it.id == state.editingPauseId }?.startTime
                    else -> null
                }
                if (showDatePickerFor != null && currentForDate != null) {
                    val initialMillis = currentForDate.toEpochMilliseconds()
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                    DatePickerDialog(
                        onDismissRequest = { showDatePickerFor = null },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { selectedMillis ->
                                    val oldCal = Calendar.getInstance(Locale.GERMAN).apply { timeInMillis = initialMillis }
                                    val newCal = Calendar.getInstance(Locale.GERMAN).apply { timeInMillis = selectedMillis }
                                    oldCal.set(Calendar.YEAR, newCal.get(Calendar.YEAR))
                                    oldCal.set(Calendar.MONTH, newCal.get(Calendar.MONTH))
                                    oldCal.set(Calendar.DAY_OF_MONTH, newCal.get(Calendar.DAY_OF_MONTH))
                                    val newInstant = Instant.fromEpochMilliseconds(oldCal.timeInMillis)
                                    when (showDatePickerFor) {
                                        "start" -> viewModel.updateStartTime(newInstant)
                                        "end" -> viewModel.updateEndTime(newInstant)
                                        "p_start" -> viewModel.updateEditingPauseStart(newInstant)
                                        "p_end" -> viewModel.updateEditingPauseEnd(newInstant)
                                    }
                                }
                                showDatePickerFor = null
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showDatePickerFor = null }) { Text("Abbrechen") } }
                    ) { DatePicker(state = datePickerState) }
                }

                val currentForTime = when (showTimePickerFor) {
                    "start" -> start
                    "end" -> end
                    "p_start" -> state.editingPauseStart ?: state.pauses.find { it.id == state.editingPauseId }?.startTime
                    "p_end" -> state.editingPauseEnd
                        ?: (if (state.isAddingNewPause) null else state.pauses.find { it.id == state.editingPauseId }?.endTime)
                        ?: state.editingPauseStart
                        ?: state.pauses.find { it.id == state.editingPauseId }?.startTime
                    else -> null
                }
                if (showTimePickerFor != null && currentForTime != null) {
                    val initialMillis = currentForTime.toEpochMilliseconds()
                    val cal = Calendar.getInstance(Locale.GERMAN).apply { timeInMillis = initialMillis }
                    val timePickerState = rememberTimePickerState(
                        initialHour = cal.get(Calendar.HOUR_OF_DAY),
                        initialMinute = cal.get(Calendar.MINUTE),
                        is24Hour = true
                    )
                    de.flobaer.arbeitszeiterfassung.TimePickerDialog(
                        title = when (showTimePickerFor) {
                            "start" -> "Startzeit auswählen"
                            "end" -> "Endzeit auswählen"
                            "p_start" -> "Pausenstart auswählen"
                            else -> "Pausenende auswählen"
                        },
                        onDismiss = { showTimePickerFor = null },
                        onConfirm = {
                            val newCal = Calendar.getInstance(Locale.GERMAN).apply { timeInMillis = initialMillis }
                            newCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            newCal.set(Calendar.MINUTE, timePickerState.minute)
                            newCal.set(Calendar.SECOND, 0)
                            val newInstant = Instant.fromEpochMilliseconds(newCal.timeInMillis)
                            when (showTimePickerFor) {
                                "start" -> viewModel.updateStartTime(newInstant)
                                "end" -> viewModel.updateEndTime(newInstant)
                                "p_start" -> viewModel.updateEditingPauseStart(newInstant)
                                "p_end" -> viewModel.updateEditingPauseEnd(newInstant)
                            }
                            showTimePickerFor = null
                        }
                    ) { TimePicker(state = timePickerState) }
                }

                deletingPause?.let { pause ->
                    AlertDialog(
                        onDismissRequest = { deletingPause = null },
                        title = { Text("Pause löschen") },
                        text = { Text("Möchten Sie diese Pause wirklich löschen?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deletePause(pause.id)
                                    deletingPause = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Löschen")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { deletingPause = null }) {
                                Text("Abbrechen")
                            }
                        }
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun StatCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DateTimeCell(label: String, width: Dp, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(width).heightIn(min = 48.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = null
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return String.format(Locale.GERMAN, "%d:%02d", hours, minutes)
}
