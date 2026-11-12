package com.ratheshr8.beaconsimulator.ui.screens

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
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratheshr8.beaconsimulator.UiState
import com.ratheshr8.beaconsimulator.ble.DistanceEstimator
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.ui.components.BeaconListItem
import com.ratheshr8.beaconsimulator.ui.components.StatusBanner

@Composable
fun ReceiverScreen(
    state: UiState,
    onFilterUuidChange: (String) -> Unit,
    onFilterMajorChange: (String) -> Unit,
    onFilterMinorChange: (String) -> Unit,
    onToggleScan: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = Modifier.padding(top = 16.dp))
            Text(
                text = "Receiver / Scanner",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Scan for nearby iBeacon advertisements. Filters are optional.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.filterUuidText,
                onValueChange = onFilterUuidChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter UUID (optional)") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.filterMajorText,
                    onValueChange = onFilterMajorChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Major") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = state.filterMinorText,
                    onValueChange = onFilterMinorChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Minor") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onToggleScan,
                    modifier = Modifier.weight(1f),
                    enabled = state.bluetoothEnabled,
                ) {
                    Icon(
                        imageVector = if (state.isScanning) Icons.Default.Stop else Icons.Default.Radar,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                    Text(if (state.isScanning) "Stop scan" else "Start scan")
                }
                OutlinedButton(onClick = onClear) {
                    Icon(Icons.Default.ClearAll, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Clear")
                }
            }

            state.statusMessage?.let { StatusBanner(it, state.isError) }

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
                modifier = Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
