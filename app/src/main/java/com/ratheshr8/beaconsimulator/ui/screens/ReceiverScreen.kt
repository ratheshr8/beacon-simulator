package com.ratheshr8.beaconsimulator.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratheshr8.beaconsimulator.UiState
import com.ratheshr8.beaconsimulator.ble.DistanceEstimator
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.floorplan.FloorPlanTool
import com.ratheshr8.beaconsimulator.floorplan.ReceiverTab
import com.ratheshr8.beaconsimulator.ui.components.BeaconListItem
import com.ratheshr8.beaconsimulator.ui.components.StatusBanner
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
) {
    Column(
        modifier = ComposeModifierAlias
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = ComposeModifierAlias.padding(top = 12.dp))
            Text("Receiver / Scanner", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Scan nearby iBeacons and optionally POST them to an external URL.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                placeholder = { Text("https://example.com/hooks/beacons") },
                supportingText = { Text("POSTs JSON every ~2s while scanning when enabled") },
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

            Row(
                modifier = ComposeModifierAlias.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    Text(if (state.isScanning) "Stop scan" else "Start scan")
                }
                OutlinedButton(onClick = onClear) {
                    Icon(Icons.Default.ClearAll, contentDescription = null)
                    Spacer(modifier = ComposeModifierAlias.padding(horizontal = 4.dp))
                    Text("Clear")
                }
            }

            state.statusMessage?.let { StatusBanner(it, state.isError) }
            state.lastForwardStatus?.let { StatusBanner(it, state.lastForwardWasError) }

            Text(
                text = "${state.scannedBeacons.size} beacon(s)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.scannedBeacons.isEmpty()) {
            Text(
                text = if (state.isScanning) {
                    "Listening for Apple company ID (0x004C) iBeacon frames…"
                } else {
                    "No beacons yet. Start a scan, or open Transmitter mode on another device."
                },
                modifier = ComposeModifierAlias.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = ComposeModifierAlias.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = state.scannedBeacons,
                    key = { "${it.deviceAddress}-${it.uuid}-${it.major}-${it.minor}" },
                ) { beacon ->
                    BeaconListItem(
                        uuid = IBeaconPacket.formatUuid(beacon.uuid),
                        major = beacon.major,
                        minor = beacon.minor,
                        rssi = beacon.rssi,
                        txPower = beacon.txPower,
                        distance = DistanceEstimator.formatDistance(beacon.estimatedDistanceMeters),
                        proximity = DistanceEstimator.proximityLabel(beacon.estimatedDistanceMeters),
                        address = beacon.deviceAddress,
                    )
                }
            }
        }
    }
}
