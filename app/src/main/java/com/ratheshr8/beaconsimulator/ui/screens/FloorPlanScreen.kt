package com.ratheshr8.beaconsimulator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratheshr8.beaconsimulator.UiState
import com.ratheshr8.beaconsimulator.ble.DistanceEstimator
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.floorplan.FloorPlanTool
import com.ratheshr8.beaconsimulator.ui.components.FloorPlanCanvas
import com.ratheshr8.beaconsimulator.ui.components.StatusBanner
import androidx.compose.ui.Modifier as ComposeModifierAlias

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorPlanScreen(
    state: UiState,
    onCreateBlankPlan: (name: String, widthMeters: Float) -> Unit,
    onImportPlanImage: (uri: Uri, name: String, widthMeters: Float) -> Unit,
    onSelectPlan: (String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onWidthMetersChange: (String) -> Unit,
    onToolChange: (FloorPlanTool) -> Unit,
    onMapTap: (Float, Float) -> Unit,
    onConfirmPlaceBeacon: (label: String, uuid: String, major: Int, minor: Int) -> Unit,
    onCancelPlace: () -> Unit,
    onDeleteBeacon: (String) -> Unit,
    onToggleScan: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var createModeImport by remember { mutableStateOf(false) }
    var planName by remember { mutableStateOf("Office floor") }
    var widthText by remember { mutableStateOf(state.floorPlanWidthText) }
    var planMenuExpanded by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportPlanImage(
                uri,
                planName.ifBlank { "Imported plan" },
                widthText.toFloatOrNull() ?: 20f,
            )
        }
    }

    val selected = state.floorPlans.firstOrNull { it.id == state.selectedFloorPlanId }
    val statuses = state.locatedBeaconStatuses

    Column(
        modifier = ComposeModifierAlias
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = ComposeModifierAlias.height(8.dp))
        Text("Floor plan", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Create a plan, place each beacon at its exact map position, then use Locate " +
                "while scanning to see range rings and your estimated position.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = ComposeModifierAlias.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    createModeImport = false
                    showCreateDialog = true
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = ComposeModifierAlias.padding(horizontal = 4.dp))
                Text("New blank")
            }
            OutlinedButton(
                onClick = {
                    createModeImport = true
                    showCreateDialog = true
                },
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = ComposeModifierAlias.padding(horizontal = 4.dp))
                Text("From photo")
            }
        }

        if (state.floorPlans.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = planMenuExpanded,
                onExpandedChange = { planMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = selected?.name ?: "Select floor plan",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Active plan") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(planMenuExpanded) },
                    modifier = ComposeModifierAlias
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = planMenuExpanded,
                    onDismissRequest = { planMenuExpanded = false },
                ) {
                    state.floorPlans.forEach { plan ->
                        DropdownMenuItem(
                            text = { Text("${plan.name} (${plan.beacons.size} beacons)") },
                            onClick = {
                                onSelectPlan(plan.id)
                                planMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        selected?.let { plan ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.floorPlanWidthText,
                    onValueChange = onWidthMetersChange,
                    label = { Text("Plan width (meters)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = ComposeModifierAlias.weight(1f),
                )
                IconButton(onClick = { onDeletePlan(plan.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete plan")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.floorPlanTool == FloorPlanTool.VIEW,
                    onClick = { onToolChange(FloorPlanTool.VIEW) },
                    label = { Text("View") },
                )
                FilterChip(
                    selected = state.floorPlanTool == FloorPlanTool.PLACE,
                    onClick = { onToolChange(FloorPlanTool.PLACE) },
                    label = { Text("Place beacon") },
                )
                FilterChip(
                    selected = state.floorPlanTool == FloorPlanTool.LOCATE,
                    onClick = { onToolChange(FloorPlanTool.LOCATE) },
                    label = { Text("Locate") },
                )
            }

            Text(
                text = when (state.floorPlanTool) {
                    FloorPlanTool.PLACE ->
                        "Tap the map to drop a beacon at its exact floor position."
                    FloorPlanTool.LOCATE ->
                        "Start scan to highlight detected beacons, draw range rings, and estimate your position (2–3+ placed beacons in range)."
                    FloorPlanTool.VIEW ->
                        "Placed beacons are shown at their configured map coordinates."
                },
                style = MaterialTheme.typography.bodySmall,
            )

            if (state.floorPlanTool == FloorPlanTool.LOCATE) {
                Button(
                    onClick = onToggleScan,
                    modifier = ComposeModifierAlias.fillMaxWidth(),
                    enabled = state.bluetoothEnabled,
                ) {
                    Text(if (state.isScanning) "Stop scan" else "Start scan to locate")
                }
            }

            FloorPlanCanvas(
                imagePath = plan.imagePath,
                planWidthMeters = plan.widthMeters,
                beaconStatuses = statuses,
                userLocation = if (state.floorPlanTool == FloorPlanTool.LOCATE) {
                    state.userLocationEstimate?.point
                } else {
                    null
                },
                onTapNormalized = { x, y ->
                    if (state.floorPlanTool == FloorPlanTool.PLACE) onMapTap(x, y)
                },
            )

            if (state.floorPlanTool == FloorPlanTool.LOCATE) {
                state.userLocationEstimate?.let { est ->
                    StatusBanner(
                        message = "Estimated position from ${est.usedBeaconCount} beacon(s) · " +
                            "confidence ${(est.confidence * 100).toInt()}%",
                        isError = false,
                    )
                } ?: if (state.isScanning) {
                    StatusBanner(
                        message = "Need at least 2 detected placed beacons for a position estimate.",
                        isError = false,
                    )
                } else {
                    Unit
                }
            }

            Text("Beacons on this plan", style = MaterialTheme.typography.titleMedium)
            if (plan.beacons.isEmpty()) {
                Text(
                    "None yet — choose Place beacon and tap the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                statuses.forEach { status ->
                    Row(
                        modifier = ComposeModifierAlias
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = ComposeModifierAlias.weight(1f)) {
                            Text(status.placed.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${IBeaconPacket.formatUuid(status.placed.uuid)} · " +
                                    "M${status.placed.major}/m${status.placed.minor}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                if (status.detected) {
                                    "Detected · ${status.rssi} dBm · " +
                                        DistanceEstimator.formatDistance(status.distanceMeters)
                                } else {
                                    "Not detected · map (${"%.0f".format(status.placed.x * 100)}%, " +
                                        "${"%.0f".format(status.placed.y * 100)}%)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status.detected) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { onDeleteBeacon(status.placed.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove beacon")
                        }
                    }
                }
            }
        }

        if (selected == null) {
            StatusBanner(
                message = "Create a blank plan or import a floor photo to begin.",
                isError = false,
            )
        }

        Spacer(modifier = ComposeModifierAlias.height(24.dp))
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(if (createModeImport) "Import floor photo" else "New blank floor plan")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = planName,
                        onValueChange = { planName = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it },
                        label = { Text("Real width (meters)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val meters = widthText.toFloatOrNull() ?: 20f
                        if (createModeImport) {
                            showCreateDialog = false
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        } else {
                            onCreateBlankPlan(planName.ifBlank { "Floor plan" }, meters)
                            showCreateDialog = false
                        }
                    },
                ) {
                    Text(if (createModeImport) "Choose photo" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }

    state.pendingPlace?.let { pending ->
        PlaceBeaconDialog(
            defaultUuid = state.uuidText,
            defaultMajor = state.majorText,
            defaultMinor = state.minorText,
            scannedOptions = state.scannedBeacons.map {
                Triple(IBeaconPacket.formatUuid(it.uuid), it.major, it.minor)
            }.distinct(),
            mapX = pending.first,
            mapY = pending.second,
            onDismiss = onCancelPlace,
            onConfirm = onConfirmPlaceBeacon,
        )
    }
}

@Composable
private fun PlaceBeaconDialog(
    defaultUuid: String,
    defaultMajor: String,
    defaultMinor: String,
    scannedOptions: List<Triple<String, Int, Int>>,
    mapX: Float,
    mapY: Float,
    onDismiss: () -> Unit,
    onConfirm: (label: String, uuid: String, major: Int, minor: Int) -> Unit,
) {
    var label by remember { mutableStateOf("Beacon ${scannedOptions.size + 1}") }
    var uuid by remember { mutableStateOf(defaultUuid) }
    var major by remember { mutableStateOf(defaultMajor) }
    var minor by remember { mutableStateOf(defaultMinor) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Place beacon here") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Map position: ${"%.0f".format(mapX * 100)}%, ${"%.0f".format(mapY * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uuid,
                    onValueChange = { uuid = it },
                    label = { Text("UUID") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = major,
                        onValueChange = { major = it },
                        label = { Text("Major") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = ComposeModifierAlias.weight(1f),
                    )
                    OutlinedTextField(
                        value = minor,
                        onValueChange = { minor = it },
                        label = { Text("Minor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = ComposeModifierAlias.weight(1f),
                    )
                }
                if (scannedOptions.isNotEmpty()) {
                    Text("Fill from scanned:", style = MaterialTheme.typography.labelLarge)
                    scannedOptions.take(5).forEach { (u, maj, minV) ->
                        TextButton(
                            onClick = {
                                uuid = u
                                major = maj.toString()
                                minor = minV.toString()
                                label = "M$maj/m$minV"
                            },
                        ) {
                            Text("$u · $maj/$minV", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedUuid = IBeaconPacket.parseUuidOrNull(uuid)
                    val maj = major.toIntOrNull()
                    val minV = minor.toIntOrNull()
                    when {
                        parsedUuid == null -> error = "Invalid UUID"
                        maj == null || maj !in 0..65535 -> error = "Major must be 0–65535"
                        minV == null || minV !in 0..65535 -> error = "Minor must be 0–65535"
                        else -> onConfirm(label.ifBlank { "Beacon" }, uuid.trim(), maj, minV)
                    }
                },
            ) { Text("Place") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
