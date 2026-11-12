package com.ratheshr8.beaconsimulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratheshr8.beaconsimulator.UiState
import com.ratheshr8.beaconsimulator.ui.components.HardwareBanner
import com.ratheshr8.beaconsimulator.ui.components.StatusBanner

@Composable
fun TransmitterScreen(
    state: UiState,
    onUuidChange: (String) -> Unit,
    onMajorChange: (String) -> Unit,
    onMinorChange: (String) -> Unit,
    onTxPowerChange: (String) -> Unit,
    onEddystoneChange: (Boolean) -> Unit,
    onToggleAdvertise: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Beacon Transmitter",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Advertise as an iBeacon using Android BLE advertising. " +
                "Point your existing beacon-aware app at the same UUID / major / minor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HardwareBanner(
            bluetoothEnabled = state.bluetoothEnabled,
            advertisingSupported = state.advertisingSupported,
        )

        OutlinedTextField(
            value = state.uuidText,
            onValueChange = onUuidChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Proximity UUID") },
            singleLine = true,
            enabled = !state.isAdvertising,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.majorText,
                onValueChange = onMajorChange,
                modifier = Modifier.weight(1f),
                label = { Text("Major") },
                singleLine = true,
                enabled = !state.isAdvertising,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.minorText,
                onValueChange = onMinorChange,
                modifier = Modifier.weight(1f),
                label = { Text("Minor") },
                singleLine = true,
                enabled = !state.isAdvertising,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        OutlinedTextField(
            value = state.txPowerText,
            onValueChange = onTxPowerChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Measured TX power at 1 m (dBm)") },
            supportingText = { Text("Typical iBeacon value: −59") },
            singleLine = true,
            enabled = !state.isAdvertising,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.includeEddystone,
                onCheckedChange = onEddystoneChange,
                enabled = !state.isAdvertising,
            )
            Text(
                text = "Also advertise Eddystone-UID in scan response",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = onToggleAdvertise,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.bluetoothEnabled,
        ) {
            Icon(
                imageVector = if (state.isAdvertising) {
                    Icons.Default.Stop
                } else {
                    Icons.AutoMirrored.Filled.BluetoothSearching
                },
                contentDescription = null,
            )
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Text(if (state.isAdvertising) "Stop advertising" else "Start advertising")
        }

        state.statusMessage?.let { StatusBanner(it, state.isError) }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tip: Use Receiver mode on a second phone to verify the payload, " +
                "or configure your production app with the UUID above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
