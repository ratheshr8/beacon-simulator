package com.ratheshr8.beaconsimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ratheshr8.beaconsimulator.ui.theme.Danger
import com.ratheshr8.beaconsimulator.ui.theme.OceanDeep
import com.ratheshr8.beaconsimulator.ui.theme.SignalAmber
import com.ratheshr8.beaconsimulator.ui.theme.SignalTeal
import androidx.compose.ui.Modifier as ComposeModifierAlias

@Composable
fun StatusBanner(message: String, isError: Boolean) {
    val bg = if (isError) {
        Danger.copy(alpha = 0.12f)
    } else {
        SignalTeal.copy(alpha = 0.12f)
    }
    val fg = if (isError) Danger else SignalTeal
    Text(
        text = message,
        modifier = ComposeModifierAlias
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(12.dp),
        color = fg,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun HardwareBanner(
    bluetoothEnabled: Boolean,
    advertisingSupported: Boolean,
) {
    val ok = bluetoothEnabled && advertisingSupported
    val bg = if (ok) OceanDeep.copy(alpha = 0.08f) else SignalAmber.copy(alpha = 0.15f)
    val message = when {
        !bluetoothEnabled -> "Bluetooth is off. Enable Bluetooth to advertise or scan."
        !advertisingSupported ->
            "This device reports no BLE peripheral advertising support. " +
                "Receiver mode may still work; Transmitter mode likely will not."
        else -> "Bluetooth on · BLE advertising supported on this device."
    }
    Text(
        text = message,
        modifier = ComposeModifierAlias
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun BeaconListItem(
    displayName: String,
    uuid: String,
    major: Int,
    minor: Int,
    rssi: Int,
    txPower: Int,
    distance: String,
    proximity: String,
    address: String,
    onRename: (() -> Unit)? = null,
) {
    Surface(
        modifier = ComposeModifierAlias.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = ComposeModifierAlias.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = ComposeModifierAlias.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = ComposeModifierAlias.weight(1f),
                )
                if (onRename != null) {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename device")
                    }
                }
                Text("$rssi dBm", style = MaterialTheme.typography.titleMedium)
            }
            Text(uuid, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = ComposeModifierAlias.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Major $major · Minor $minor", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "TX $txPower",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "$proximity · ~$distance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
