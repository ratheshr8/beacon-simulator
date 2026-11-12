package com.ratheshr8.beaconsimulator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ratheshr8.beaconsimulator.ble.AdvertiseConfig
import com.ratheshr8.beaconsimulator.ble.AdvertiseResult
import com.ratheshr8.beaconsimulator.ble.BeaconAdvertiser
import com.ratheshr8.beaconsimulator.ble.BeaconScanner
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.ble.ScanFilterConfig
import com.ratheshr8.beaconsimulator.ble.ScannedBeacon
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppMode { TRANSMITTER, RECEIVER }

data class UiState(
    val mode: AppMode = AppMode.TRANSMITTER,
    val uuidText: String = DEFAULT_UUID,
    val majorText: String = "1",
    val minorText: String = "1",
    val txPowerText: String = "-59",
    val includeEddystone: Boolean = false,
    val isAdvertising: Boolean = false,
    val advertisingSupported: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    // Receiver
    val filterUuidText: String = "",
    val filterMajorText: String = "",
    val filterMinorText: String = "",
    val isScanning: Boolean = false,
    val scannedBeacons: List<ScannedBeacon> = emptyList(),
)

private const val DEFAULT_UUID = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"

class BeaconViewModel(application: Application) : AndroidViewModel(application) {
    private val advertiser = BeaconAdvertiser(application)
    private val scanner = BeaconScanner(application)

    private val _state = MutableStateFlow(
        UiState(
            advertisingSupported = advertiser.isAdvertisingSupported(),
            bluetoothEnabled = advertiser.isBluetoothEnabled(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        scanner.setListener { beacons ->
            _state.update { it.copy(scannedBeacons = beacons) }
        }
    }

    fun refreshHardwareState() {
        _state.update {
            it.copy(
                advertisingSupported = advertiser.isAdvertisingSupported(),
                bluetoothEnabled = advertiser.isBluetoothEnabled() || scanner.isBluetoothEnabled(),
            )
        }
    }

    fun setMode(mode: AppMode) {
        if (mode == AppMode.TRANSMITTER && _state.value.isScanning) {
            stopScan()
        }
        if (mode == AppMode.RECEIVER && _state.value.isAdvertising) {
            stopAdvertising()
        }
        _state.update { it.copy(mode = mode, statusMessage = null, isError = false) }
    }

    fun updateUuid(value: String) = _state.update { it.copy(uuidText = value) }
    fun updateMajor(value: String) = _state.update { it.copy(majorText = value) }
    fun updateMinor(value: String) = _state.update { it.copy(minorText = value) }
    fun updateTxPower(value: String) = _state.update { it.copy(txPowerText = value) }
    fun updateIncludeEddystone(value: Boolean) = _state.update { it.copy(includeEddystone = value) }

    fun updateFilterUuid(value: String) {
        _state.update { it.copy(filterUuidText = value) }
        applyScanFilter()
    }

    fun updateFilterMajor(value: String) {
        _state.update { it.copy(filterMajorText = value) }
        applyScanFilter()
    }

    fun updateFilterMinor(value: String) {
        _state.update { it.copy(filterMinorText = value) }
        applyScanFilter()
    }

    fun toggleAdvertising() {
        if (_state.value.isAdvertising) stopAdvertising() else startAdvertising()
    }

    fun toggleScanning() {
        if (_state.value.isScanning) stopScan() else startScan()
    }

    fun clearScanResults() {
        scanner.clear()
        setStatus("Scan list cleared", error = false)
    }

    private fun startAdvertising() {
        refreshHardwareState()
        val current = _state.value
        val uuid = IBeaconPacket.parseUuidOrNull(current.uuidText)
        if (uuid == null) {
            setStatus("UUID must be a valid 8-4-4-4-12 UUID", error = true)
            return
        }
        val major = current.majorText.toIntOrNull()
        val minor = current.minorText.toIntOrNull()
        val txPower = current.txPowerText.toIntOrNull()
        if (major == null || major !in 0..65535) {
            setStatus("Major must be an integer 0–65535", error = true)
            return
        }
        if (minor == null || minor !in 0..65535) {
            setStatus("Minor must be an integer 0–65535", error = true)
            return
        }
        if (txPower == null || txPower !in -128..127) {
            setStatus("TX power must be an integer -128–127 (typical: -59)", error = true)
            return
        }

        setStatus("Starting advertiser…", error = false)
        advertiser.start(
            AdvertiseConfig(
                uuid = uuid,
                major = major,
                minor = minor,
                txPower = txPower,
                includeEddystone = current.includeEddystone,
            )
        ) { result ->
            viewModelScope.launch {
                when (result) {
                    is AdvertiseResult.Started -> {
                        _state.update { it.copy(isAdvertising = true) }
                        setStatus(
                            "Advertising iBeacon" +
                                if (current.includeEddystone) " + Eddystone-UID" else "",
                            error = false,
                        )
                    }
                    is AdvertiseResult.Failed -> {
                        _state.update { it.copy(isAdvertising = false) }
                        setStatus(result.message, error = true)
                    }
                    is AdvertiseResult.Stopped -> {
                        _state.update { it.copy(isAdvertising = false) }
                    }
                }
            }
        }
    }

    private fun stopAdvertising() {
        advertiser.stop {
            _state.update { it.copy(isAdvertising = false) }
            setStatus("Advertising stopped", error = false)
        }
    }

    private fun startScan() {
        refreshHardwareState()
        applyScanFilter()
        scanner.start { error ->
            viewModelScope.launch {
                _state.update { it.copy(isScanning = false) }
                setStatus(error, error = true)
            }
        }
        _state.update { it.copy(isScanning = true) }
        setStatus("Scanning for iBeacons…", error = false)
    }

    private fun stopScan() {
        scanner.stop()
        _state.update { it.copy(isScanning = false) }
        setStatus("Scan stopped", error = false)
    }

    private fun applyScanFilter() {
        val s = _state.value
        val uuid = s.filterUuidText.trim().takeIf { it.isNotEmpty() }?.let {
            IBeaconPacket.parseUuidOrNull(it)
        }
        val major = s.filterMajorText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val minor = s.filterMinorText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        scanner.setFilter(ScanFilterConfig(uuid = uuid, major = major, minor = minor))
    }

    private fun setStatus(message: String, error: Boolean) {
        _state.update { it.copy(statusMessage = message, isError = error) }
    }

    override fun onCleared() {
        advertiser.stop()
        scanner.stop()
        scanner.setListener(null)
        super.onCleared()
    }

    companion object {
        val defaultUuid: UUID = UUID.fromString(DEFAULT_UUID)
    }
}
