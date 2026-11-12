package com.ratheshr8.beaconsimulator.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.ratheshr8.beaconsimulator.UiState
import com.ratheshr8.beaconsimulator.ble.DeviceNameStore
import com.ratheshr8.beaconsimulator.ble.DistanceEstimator
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.ble.ScannedBeacon
import com.ratheshr8.beaconsimulator.floorplan.FloorPlanTool
import com.ratheshr8.beaconsimulator.floorplan.ReceiverTab
import com.ratheshr8.beaconsimulator.permissions.BlePermissions
import com.ratheshr8.beaconsimulator.ui.components.BeaconListItem
import com.ratheshr8.beaconsimulator.ui.components.StatusBanner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier as ComposeModifierAlias

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverScreen(
    state: UiState,
    onReceiverTabChange: (ReceiverTab) -> Unit,
    onFilterUuidChange: (String) -> Unit,
    onFilterMajorChange: (String) -> Unit,
    onFilterMinorChange: (String) -> Unit,
    onWebhookUrlChange: (String) -> Unit,
    onForwardEnabledChange: (Boolean) -> Unit,
    onSendNow: () -> Unit,
    onToggleScan: () -> Unit,
    onClear: () -> Unit,
    onShowAllBleChange: (Boolean) -> Unit,
    onFiltersExpandedChange: (Boolean) -> Unit,
    onBeginRename: (ScannedBeacon, String) -> Unit,
    onRenameDraftChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onPairFromQr: (String) -> Unit,
    onClearPairing: () -> Unit,
    onLiveMoveChange: (Boolean) -> Unit,
    onSetReceiverPin: (Float, Float) -> Unit,
    onBeaconDrag: (String, Float, Float) -> Unit,
    onReceiverSyncChange: (Boolean) -> Unit,
    onReceiverNameChange: (String) -> Unit,
    onCreateBlankPlan: (String, Float) -> Unit,
    onImportPlanImage: (Uri, String, Float) -> Unit,
    onSelectPlan: (String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onWidthMetersChange: (String) -> Unit,
    onToolChange: (FloorPlanTool) -> Unit,
    onMapTap: (Float, Float) -> Unit,
    onConfirmPlaceBeacon: (String, String, Int, Int) -> Unit,
    onCancelPlace: () -> Unit,
    onDeleteBeacon: (String) -> Unit,
) {
    Column(modifier = ComposeModifierAlias.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = ComposeModifierAlias
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SegmentedButton(
                selected = state.receiverTab == ReceiverTab.SCAN,
                onClick = { onReceiverTabChange(ReceiverTab.SCAN) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Scan") }
            SegmentedButton(
                selected = state.receiverTab == ReceiverTab.FLOOR_PLAN,
                onClick = { onReceiverTabChange(ReceiverTab.FLOOR_PLAN) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Floor plan") }
        }

        when (state.receiverTab) {
            ReceiverTab.SCAN -> ScanTab(
                state = state,
                onFilterUuidChange = onFilterUuidChange,
                onFilterMajorChange = onFilterMajorChange,
                onFilterMinorChange = onFilterMinorChange,
                onWebhookUrlChange = onWebhookUrlChange,
                onForwardEnabledChange = onForwardEnabledChange,
                onSendNow = onSendNow,
                onToggleScan = onToggleScan,
                onClear = onClear,
                onShowAllBleChange = onShowAllBleChange,
                onFiltersExpandedChange = onFiltersExpandedChange,
                onBeginRename = onBeginRename,
                onRenameDraftChange = onRenameDraftChange,
                onConfirmRename = onConfirmRename,
                onCancelRename = onCancelRename,
                onPairFromQr = onPairFromQr,
                onClearPairing = onClearPairing,
            )
            ReceiverTab.FLOOR_PLAN -> FloorPlanScreen(
                state = state,
                onCreateBlankPlan = onCreateBlankPlan,
                onImportPlanImage = onImportPlanImage,
                onSelectPlan = onSelectPlan,
                onDeletePlan = onDeletePlan,
                onWidthMetersChange = onWidthMetersChange,
                onToolChange = onToolChange,
                onMapTap = onMapTap,
                onConfirmPlaceBeacon = onConfirmPlaceBeacon,
                onCancelPlace = onCancelPlace,
                onDeleteBeacon = onDeleteBeacon,
                onToggleScan = onToggleScan,
                onLiveMoveChange = onLiveMoveChange,
                onSetReceiverPin = onSetReceiverPin,
                onBeaconDrag = onBeaconDrag,
                onReceiverSyncChange = onReceiverSyncChange,
                onReceiverNameChange = onReceiverNameChange,
            )
        }
    }
}

@Composable
private fun ScanTab(
    state: UiState,
    onFilterUuidChange: (String) -> Unit,
    onFilterMajorChange: (String) -> Unit,
    onFilterMinorChange: (String) -> Unit,
    onWebhookUrlChange: (String) -> Unit,
    onForwardEnabledChange: (Boolean) -> Unit,
    onSendNow: () -> Unit,
    onToggleScan: () -> Unit,
    onClear: () -> Unit,
    onShowAllBleChange: (Boolean) -> Unit,
    onFiltersExpandedChange: (Boolean) -> Unit,
    onBeginRename: (ScannedBeacon, String) -> Unit,
    onRenameDraftChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onPairFromQr: (String) -> Unit,
    onClearPairing: () -> Unit,
) {
    val context = LocalContext.current
    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        onPairFromQr(contents)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            qrLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Scan QR on the Transmitter phone")
                    .setBeepEnabled(false),
            )
        }
    }

    Column(
        modifier = ComposeModifierAlias
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = ComposeModifierAlias.padding(top = 8.dp))
        Text("Receiver / Scanner", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "iBeacons: ${state.scanStats.iBeaconCount} · " +
                "BLE: ${state.scanStats.uniqueDevicesSeen} · " +
                "ads: ${state.scanStats.rawAdvertisementCount}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = ComposeModifierAlias
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onToggleScan,
                modifier = ComposeModifierAlias.weight(1f),
                enabled = state.bluetoothEnabled,
            ) {
                Icon(
                    imageVector = if (state.isScanning) Icons.Default.Stop else Icons.Default.Radar,
                    contentDescription = null,
                )
                Spacer(modifier = ComposeModifierAlias.padding(horizontal = 6.dp))
                Text(if (state.isScanning) "Stop" else "Scan")
            }
            OutlinedButton(
                onClick = {
                    if (BlePermissions.hasCamera(context)) {
                        qrLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Scan QR on the Transmitter phone")
                                .setBeepEnabled(false),
                        )
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                },
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = ComposeModifierAlias.padding(horizontal = 4.dp))
                Text("QR")
            }
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Default.ClearAll, contentDescription = null)
            }
        }

        state.pairedIdentity?.let { paired ->
            StatusBanner(
                message = "Paired: ${paired.label} · M${paired.major}/m${paired.minor} — list locked",
                isError = false,
            )
            TextButton(onClick = onClearPairing) { Text("Clear pairing") }
        }

        OutlinedButton(
            onClick = { onFiltersExpandedChange(!state.filtersExpanded) },
            modifier = ComposeModifierAlias.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (state.filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
            Spacer(modifier = ComposeModifierAlias.padding(horizontal = 4.dp))
            Text(if (state.filtersExpanded) "Hide filters & webhook" else "Show filters & webhook")
        }

        AnimatedVisibility(visible = state.filtersExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = ComposeModifierAlias.padding(top = 8.dp, bottom = 4.dp),
            ) {
                OutlinedTextField(
                    value = state.filterUuidText,
                    onValueChange = onFilterUuidChange,
                    modifier = ComposeModifierAlias.fillMaxWidth(),
                    label = { Text("Filter UUID (optional)") },
                    singleLine = true,
                )
                Row(
                    modifier = ComposeModifierAlias.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.filterMajorText,
                        onValueChange = onFilterMajorChange,
                        modifier = ComposeModifierAlias.weight(1f),
                        label = { Text("Major") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = state.filterMinorText,
                        onValueChange = onFilterMinorChange,
                        modifier = ComposeModifierAlias.weight(1f),
                        label = { Text("Minor") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = state.webhookUrl,
                    onValueChange = onWebhookUrlChange,
                    modifier = ComposeModifierAlias.fillMaxWidth(),
                    label = { Text("Forward URL (webhook)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.forwardEnabled,
                            onCheckedChange = onForwardEnabledChange,
                        )
                        Text("Forward to URL", style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(
                        onClick = onSendNow,
                        enabled = state.webhookUrl.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = ComposeModifierAlias.padding(horizontal = 4.dp))
                        Text("Send now")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.showAllBleDevices,
                        onCheckedChange = onShowAllBleChange,
                    )
                    Text(
                        "Show nearby BLE devices (debug)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (!state.locationEnabled) {
            StatusBanner(
                message = "Location is OFF. Turn on Location in Android settings or BLE scans stay empty.",
                isError = true,
            )
        }
        state.statusMessage?.takeIf { state.isError }?.let { StatusBanner(it, true) }
        state.lastForwardStatus?.let { StatusBanner(it, state.lastForwardWasError) }

        Text(
            text = "Detected devices — tap ✎ to rename",
            style = MaterialTheme.typography.titleMedium,
            modifier = ComposeModifierAlias.padding(top = 8.dp, bottom = 4.dp),
        )

        LazyColumn(
            modifier = ComposeModifierAlias
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.scannedBeacons.isEmpty()) {
                item {
                    Text(
                        text = when {
                            !state.isScanning ->
                                "No beacons yet. Start a scan with a second phone in Transmitter mode."
                            state.scanStats.rawAdvertisementCount == 0 ->
                                "Scan is running but no BLE advertisements yet."
                            else ->
                                "Hearing BLE traffic, but no iBeacon frames yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = state.scannedBeacons,
                    key = { "ib-${it.deviceAddress}-${it.uuid}-${it.major}-${it.minor}" },
                ) { beacon ->
                    val key = DeviceNameStore.keyFor(beacon.uuid, beacon.major, beacon.minor)
                    val name = state.deviceNames[key]
                        ?: "Beacon M${beacon.major}/m${beacon.minor}"
                    BeaconListItem(
                        displayName = name,
                        uuid = IBeaconPacket.formatUuid(beacon.uuid),
                        major = beacon.major,
                        minor = beacon.minor,
                        rssi = beacon.rssi,
                        txPower = beacon.txPower,
                        distance = DistanceEstimator.formatDistance(beacon.estimatedDistanceMeters),
                        proximity = DistanceEstimator.proximityLabel(beacon.estimatedDistanceMeters),
                        address = beacon.deviceAddress,
                        onRename = { onBeginRename(beacon, name) },
                    )
                }
            }

            if (state.showAllBleDevices && state.nearbyBleDevices.isNotEmpty()) {
                item {
                    Text(
                        "Nearby BLE advertisements",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = ComposeModifierAlias.padding(top = 8.dp),
                    )
                }
                items(
                    items = state.nearbyBleDevices,
                    key = { "ble-${it.deviceAddress}" },
                ) { device ->
                    val companies = if (device.manufacturerCompanyIds.isEmpty()) {
                        "no mfr data"
                    } else {
                        device.manufacturerCompanyIds.joinToString { "0x%04X".format(it) }
                    }
                    StatusBanner(
                        message = "${device.name ?: "unnamed"} · ${device.deviceAddress}\n" +
                            "${device.rssi} dBm · $companies",
                        isError = false,
                    )
                }
            }
        }
    }

    if (state.renameTargetKey != null) {
        AlertDialog(
            onDismissRequest = onCancelRename,
            title = { Text("Rename device") },
            text = {
                OutlinedTextField(
                    value = state.renameDraft,
                    onValueChange = onRenameDraftChange,
                    label = { Text("Device name") },
                    singleLine = true,
                    modifier = ComposeModifierAlias.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRename) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = onCancelRename) { Text("Cancel") }
            },
        )
    }
}
