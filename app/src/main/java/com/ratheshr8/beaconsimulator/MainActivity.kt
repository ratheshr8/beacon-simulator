package com.ratheshr8.beaconsimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ratheshr8.beaconsimulator.permissions.BlePermissions
import com.ratheshr8.beaconsimulator.ui.components.StatusBanner
import com.ratheshr8.beaconsimulator.ui.screens.ReceiverScreen
import com.ratheshr8.beaconsimulator.ui.screens.TransmitterScreen
import com.ratheshr8.beaconsimulator.ui.theme.BeaconSimulatorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BeaconViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeaconSimulatorTheme {
                BeaconSimulatorApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHardwareState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeaconSimulatorApp(viewModel: BeaconViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var permissionsGranted by remember {
        mutableStateOf(BlePermissions.hasAll(viewModel.getApplication()))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
        permissionMessage = if (permissionsGranted) {
            null
        } else {
            "Bluetooth and location permissions are required for advertise/scan."
        }
        viewModel.refreshHardwareState()
    }

    LaunchedEffect(Unit) {
        val missing = BlePermissions.missingPermissions(viewModel.getApplication())
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            permissionsGranted = true
        }
        viewModel.refreshHardwareState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beacon Simulator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                SegmentedButton(
                    selected = state.mode == AppMode.TRANSMITTER,
                    onClick = { viewModel.setMode(AppMode.TRANSMITTER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Transmitter")
                }
                SegmentedButton(
                    selected = state.mode == AppMode.RECEIVER,
                    onClick = { viewModel.setMode(AppMode.RECEIVER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Receiver")
                }
            }

            if (!permissionsGranted) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    StatusBanner(
                        message = permissionMessage
                            ?: "Grant Bluetooth and location permissions to continue.",
                        isError = true,
                    )
                }
            }

            when (state.mode) {
                AppMode.TRANSMITTER -> TransmitterScreen(
                    state = state,
                    onUuidChange = viewModel::updateUuid,
                    onMajorChange = viewModel::updateMajor,
                    onMinorChange = viewModel::updateMinor,
                    onTxPowerChange = viewModel::updateTxPower,
                    onEddystoneChange = viewModel::updateIncludeEddystone,
                    onToggleAdvertise = {
                        if (!BlePermissions.hasAll(viewModel.getApplication())) {
                            permissionLauncher.launch(BlePermissions.requiredPermissions())
                        } else {
                            viewModel.toggleAdvertising()
                        }
                    },
                )
                AppMode.RECEIVER -> ReceiverScreen(
                    state = state,
                    onReceiverTabChange = viewModel::setReceiverTab,
                    onFilterUuidChange = viewModel::updateFilterUuid,
                    onFilterMajorChange = viewModel::updateFilterMajor,
                    onFilterMinorChange = viewModel::updateFilterMinor,
                    onWebhookUrlChange = viewModel::updateWebhookUrl,
                    onForwardEnabledChange = viewModel::updateForwardEnabled,
                    onSendNow = viewModel::sendNow,
                    onToggleScan = {
                        if (!BlePermissions.hasAll(viewModel.getApplication())) {
                            permissionLauncher.launch(BlePermissions.requiredPermissions())
                        } else {
                            viewModel.toggleScanning()
                        }
                    },
                    onClear = viewModel::clearScanResults,
                    onCreateBlankPlan = viewModel::createBlankFloorPlan,
                    onImportPlanImage = viewModel::importFloorPlanImage,
                    onSelectPlan = viewModel::selectFloorPlan,
                    onDeletePlan = viewModel::deleteFloorPlan,
                    onWidthMetersChange = viewModel::updateFloorPlanWidthText,
                    onToolChange = viewModel::setFloorPlanTool,
                    onMapTap = viewModel::onFloorPlanTap,
                    onConfirmPlaceBeacon = viewModel::confirmPlaceBeacon,
                    onCancelPlace = viewModel::cancelPlaceBeacon,
                    onDeleteBeacon = viewModel::deletePlacedBeacon,
                )
            }
        }
    }
}
