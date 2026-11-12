package com.ratheshr8.beaconsimulator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratheshr8.beaconsimulator.UiState
import com.ratheshr8.beaconsimulator.ble.BeaconIdentity
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.qr.QrCodes
import com.ratheshr8.beaconsimulator.ui.components.HardwareBanner
import com.ratheshr8.beaconsimulator.ui.components.StatusBanner
import androidx.compose.ui.Modifier as ComposeModifierAlias

@Composable
fun TransmitterScreen(
    state: UiState,
    onUuidChange: (String) -> Unit,
    onMajorChange: (String) -> Unit,
    onMinorChange: (String) -> Unit,
    onTxPowerChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onEddystoneChange: (Boolean) -> Unit,
    onCompatCompanyIdChange: (Boolean) -> Unit,
    onToggleAdvertise: () -> Unit,
) {
    val identity = remember(
        state.uuidText,
        state.majorText,
        state.minorText,
        state.txPowerText,
        state.transmitterLabel,
        state.useCompatCompanyId,
    ) {
        val uuid = IBeaconPacket.parseUuidOrNull(state.uuidText)
        val major = state.majorText.toIntOrNull()
        val minor = state.minorText.toIntOrNull()
        val tx = state.txPowerText.toIntOrNull() ?: -59
        if (uuid != null && major != null && minor != null) {
            BeaconIdentity(
                uuid = uuid,
                major = major,
                minor = minor,
                label = state.transmitterLabel.ifBlank { "Transmitter" },
                companyId = if (state.useCompatCompanyId) {
                    IBeaconPacket.COMPAT_COMPANY_ID
                } else {
                    IBeaconPacket.APPLE_COMPANY_ID
                },
                txPower = tx,
            )
        } else {
            null
        }
    }
    val qrBitmap = remember(identity?.toQrPayload()) {
        identity?.let { QrCodes.encodeBitmap(it.toQrPayload()) }
    }

    Column(
        modifier = ComposeModifierAlias
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Beacon Transmitter", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Advertise as an iBeacon. Show the QR on this screen so a Receiver can pair instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HardwareBanner(
            bluetoothEnabled = state.bluetoothEnabled,
            advertisingSupported = state.advertisingSupported,
        )

        OutlinedTextField(
            value = state.transmitterLabel,
            onValueChange = onLabelChange,
            modifier = ComposeModifierAlias.fillMaxWidth(),
            label = { Text("Device name (shown on Receiver)") },
            singleLine = true,
            enabled = !state.isAdvertising,
        )
        OutlinedTextField(
            value = state.uuidText,
            onValueChange = onUuidChange,
            modifier = ComposeModifierAlias.fillMaxWidth(),
            label = { Text("Proximity UUID") },
            singleLine = true,
            enabled = !state.isAdvertising,
        )
        Row(
            modifier = ComposeModifierAlias.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.majorText,
                onValueChange = onMajorChange,
                modifier = ComposeModifierAlias.weight(1f),
                label = { Text("Major") },
                singleLine = true,
                enabled = !state.isAdvertising,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.minorText,
                onValueChange = onMinorChange,
                modifier = ComposeModifierAlias.weight(1f),
                label = { Text("Minor") },
                singleLine = true,
                enabled = !state.isAdvertising,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            value = state.txPowerText,
            onValueChange = onTxPowerChange,
            modifier = ComposeModifierAlias.fillMaxWidth(),
            label = { Text("Measured TX power at 1 m (dBm)") },
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
            Text("Also advertise Eddystone-UID in scan response")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.useCompatCompanyId,
                onCheckedChange = onCompatCompanyIdChange,
                enabled = !state.isAdvertising,
            )
            Text("Compatible company ID (if Apple 0x004C is filtered)")
        }

        Button(
            onClick = onToggleAdvertise,
            modifier = ComposeModifierAlias.fillMaxWidth(),
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
            Spacer(modifier = ComposeModifierAlias.padding(horizontal = 6.dp))
            Text(if (state.isAdvertising) "Stop advertising" else "Start advertising")
        }

        state.statusMessage?.let { StatusBanner(it, state.isError) }

        Text("Pairing QR code", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "On the other phone: Receiver → Scan QR. This locks the list to this transmitter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Transmitter pairing QR",
                modifier = ComposeModifierAlias
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .size(240.dp)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        } else {
            StatusBanner("Fix UUID / major / minor to generate a QR code", isError = true)
        }

        Spacer(modifier = ComposeModifierAlias.height(16.dp))
    }
}
