package de.flobaer.arbeitszeiterfassung

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

data class LocationOption(val id: Int, val name: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditIntervalDialog(
    workTimeItem: WorkTimeItem,
    dateFormatter: SimpleDateFormat,
    timeFormatter: SimpleDateFormat,
    locations: List<LocationOption>,
    onDismiss: () -> Unit,
    onSave: (Date, Date, Int) -> Unit
) {
    val initialStartMillis = remember { workTimeItem.startTime.toEpochMilliseconds() }
    val initialEndMillis = remember { workTimeItem.endTime?.toEpochMilliseconds() ?: System.currentTimeMillis() }

    var updatedStartMillis by remember { mutableLongStateOf(initialStartMillis) }
    var updatedEndMillis by remember { mutableLongStateOf(initialEndMillis) }

    val locationIds = remember(locations) { locations.map { it.id } }
    val isLocationInvalid = workTimeItem.locationId !in locationIds

    var updatedLocation by remember { mutableStateOf(locations.find { it.id == workTimeItem.locationId }) }

    var showDatePickerFor by remember { mutableStateOf<String?>(null) }
    var showTimePickerFor by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    if (showDatePickerFor != null) {
        val isStartTime = showDatePickerFor == "start"
        val dateToEdit = if (isStartTime) updatedStartMillis else updatedEndMillis
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateToEdit)

        DatePickerDialog(
            onDismissRequest = { showDatePickerFor = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { newDateMillis ->
                            val oldCal = Calendar.getInstance().apply { timeInMillis = dateToEdit }
                            val newCal = Calendar.getInstance().apply { timeInMillis = newDateMillis }

                            oldCal.set(Calendar.YEAR, newCal.get(Calendar.YEAR))
                            oldCal.set(Calendar.MONTH, newCal.get(Calendar.MONTH))
                            oldCal.set(Calendar.DAY_OF_MONTH, newCal.get(Calendar.DAY_OF_MONTH))

                            if (isStartTime) {
                                updatedStartMillis = oldCal.timeInMillis
                            } else {
                                updatedEndMillis = oldCal.timeInMillis
                            }
                        }
                        showDatePickerFor = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerFor = null }) {
                    Text("Abbrechen")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePickerFor != null) {
        val isStartTime = showTimePickerFor == "start"
        val timeToEdit = if (isStartTime) updatedStartMillis else updatedEndMillis
        val cal = Calendar.getInstance().apply { timeInMillis = timeToEdit }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )

        TimePickerDialog(
            title = if (isStartTime) "Startzeit auswählen" else "Endzeit auswählen",
            onDismiss = { showTimePickerFor = null },
            onConfirm = {
                val newCal = Calendar.getInstance().apply { timeInMillis = timeToEdit }
                newCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                newCal.set(Calendar.MINUTE, timePickerState.minute)
                newCal.set(Calendar.SECOND, 0)

                if (isStartTime) {
                    updatedStartMillis = newCal.timeInMillis
                } else {
                    updatedEndMillis = newCal.timeInMillis
                }
                showTimePickerFor = null
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zeitraum bearbeiten") },
        text = {
            Column {
                Text("Startzeit", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${dateFormatter.format(Date(updatedStartMillis))} ${timeFormatter.format(Date(updatedStartMillis))}",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showDatePickerFor = "start" }) { Text("Datum") }
                    TextButton(onClick = { showTimePickerFor = "start" }) { Text("Zeit") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Endzeit", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${dateFormatter.format(Date(updatedEndMillis))} ${timeFormatter.format(Date(updatedEndMillis))}",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showDatePickerFor = "end" }) { Text("Datum") }
                    TextButton(onClick = { showTimePickerFor = "end" }) { Text("Zeit") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ort", style = MaterialTheme.typography.labelLarge)
                Box {
                    val locationRowColor = if (isLocationInvalid) MaterialTheme.colorScheme.errorContainer else Color.Transparent
                    TextButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.background(locationRowColor)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = updatedLocation?.icon
                            if (icon != null) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(updatedLocation?.name ?: "Ort #${workTimeItem.locationId}")
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
                                updatedLocation = it
                                dropdownExpanded = false
                            })
                        }
                    }
                }
                if (isLocationInvalid) {
                    Text("Ort ist nicht mehr gültig. Bitte neuen Ort wählen.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (updatedStartMillis >= updatedEndMillis) {
                    error = "Startzeit muss vor Endzeit liegen."
                } else if (updatedLocation == null) {
                    error = "Bitte einen Ort auswählen."
                } else {
                    error = null
                    onSave(Date(updatedStartMillis), Date(updatedEndMillis), updatedLocation!!.id)
                }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun TimePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                content()
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
            }
        }
    }
}