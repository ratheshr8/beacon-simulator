package com.ratheshr8.beaconsimulator

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ratheshr8.beaconsimulator.ble.AdvertiseConfig
import com.ratheshr8.beaconsimulator.ble.AdvertiseResult
import com.ratheshr8.beaconsimulator.ble.BeaconAdvertiser
import com.ratheshr8.beaconsimulator.ble.BeaconScanner
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.ble.ScanFilterConfig
import com.ratheshr8.beaconsimulator.ble.ScannedBeacon
import com.ratheshr8.beaconsimulator.floorplan.FloorPlan
import com.ratheshr8.beaconsimulator.floorplan.FloorPlanStore
import com.ratheshr8.beaconsimulator.floorplan.FloorPlanTool
import com.ratheshr8.beaconsimulator.floorplan.LocatedBeaconStatus
import com.ratheshr8.beaconsimulator.floorplan.PlacedBeacon
import com.ratheshr8.beaconsimulator.floorplan.ReceiverTab
import com.ratheshr8.beaconsimulator.floorplan.Trilateration
import com.ratheshr8.beaconsimulator.floorplan.UserLocationEstimate
import com.ratheshr8.beaconsimulator.net.BeaconForwarder
import com.ratheshr8.beaconsimulator.net.ForwardResult
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    // Receiver scan
    val filterUuidText: String = "",
    val filterMajorText: String = "",
    val filterMinorText: String = "",
    val isScanning: Boolean = false,
    val scannedBeacons: List<ScannedBeacon> = emptyList(),
    val webhookUrl: String = "",
    val forwardEnabled: Boolean = false,
    val lastForwardStatus: String? = null,
    val lastForwardWasError: Boolean = false,
    // Receiver floor plan
    val receiverTab: ReceiverTab = ReceiverTab.SCAN,
    val floorPlans: List<FloorPlan> = emptyList(),
    val selectedFloorPlanId: String? = null,
    val floorPlanTool: FloorPlanTool = FloorPlanTool.VIEW,
    val floorPlanWidthText: String = "20",
    val pendingPlace: Pair<Float, Float>? = null,
    val locatedBeaconStatuses: List<LocatedBeaconStatus> = emptyList(),
    val userLocationEstimate: UserLocationEstimate? = null,
)

private const val DEFAULT_UUID = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"
private const val FORWARD_INTERVAL_MS = 2_000L

class BeaconViewModel(application: Application) : AndroidViewModel(application) {
    private val advertiser = BeaconAdvertiser(application)
    private val scanner = BeaconScanner(application)
    private val forwarder = BeaconForwarder()
    private val floorPlanStore = FloorPlanStore(application)
    private var forwardJob: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            advertisingSupported = advertiser.isAdvertisingSupported(),
            bluetoothEnabled = advertiser.isBluetoothEnabled(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val plans = floorPlanStore.loadAll()
        _state.update {
            it.copy(
                floorPlans = plans,
                selectedFloorPlanId = plans.firstOrNull()?.id,
                floorPlanWidthText = plans.firstOrNull()?.widthMeters?.toString() ?: "20",
            )
        }
        recomputeFloorPlanOverlay()

        scanner.setListener { beacons ->
            _state.update { it.copy(scannedBeacons = beacons) }
            recomputeFloorPlanOverlay()
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

    fun setReceiverTab(tab: ReceiverTab) {
        _state.update { it.copy(receiverTab = tab) }
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

    fun updateWebhookUrl(value: String) = _state.update { it.copy(webhookUrl = value) }

    fun updateForwardEnabled(enabled: Boolean) {
        _state.update { it.copy(forwardEnabled = enabled) }
        if (enabled && _state.value.isScanning) {
            startForwardLoop()
        } else if (!enabled) {
            stopForwardLoop()
        }
    }

    fun toggleAdvertising() {
        if (_state.value.isAdvertising) stopAdvertising() else startAdvertising()
    }

    fun toggleScanning() {
        if (_state.value.isScanning) stopScan() else startScan()
    }

    fun clearScanResults() {
        scanner.clear()
        recomputeFloorPlanOverlay()
        setStatus("Scan list cleared", error = false)
    }

    fun sendNow() {
        viewModelScope.launch { postSnapshot(force = true) }
    }

    // region Floor plan

    fun createBlankFloorPlan(name: String, widthMeters: Float) {
        val plan = FloorPlan(name = name, widthMeters = widthMeters.coerceAtLeast(1f))
        persistPlans(_state.value.floorPlans + plan, selectId = plan.id)
        setStatus("Created floor plan “${plan.name}”", error = false)
    }

    fun importFloorPlanImage(uri: Uri, name: String, widthMeters: Float) {
        val path = floorPlanStore.importImage(uri)
        if (path == null) {
            setStatus("Could not import floor plan image", error = true)
            return
        }
        val plan = FloorPlan(
            name = name,
            imagePath = path,
            widthMeters = widthMeters.coerceAtLeast(1f),
        )
        persistPlans(_state.value.floorPlans + plan, selectId = plan.id)
        setStatus("Imported floor plan “${plan.name}”", error = false)
    }

    fun selectFloorPlan(id: String) {
        val plan = _state.value.floorPlans.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                selectedFloorPlanId = id,
                floorPlanWidthText = plan.widthMeters.toString(),
                pendingPlace = null,
            )
        }
        recomputeFloorPlanOverlay()
    }

    fun deleteFloorPlan(id: String) {
        val current = _state.value.floorPlans
        val doomed = current.firstOrNull { it.id == id }
        floorPlanStore.deleteImage(doomed?.imagePath)
        val remaining = current.filterNot { it.id == id }
        persistPlans(remaining, selectId = remaining.firstOrNull()?.id)
        setStatus("Deleted floor plan", error = false)
    }

    fun updateFloorPlanWidthText(value: String) {
        _state.update { it.copy(floorPlanWidthText = value) }
        val meters = value.toFloatOrNull() ?: return
        updateSelectedPlan { it.copy(widthMeters = meters.coerceAtLeast(1f)) }
        recomputeFloorPlanOverlay()
    }

    fun setFloorPlanTool(tool: FloorPlanTool) {
        _state.update { it.copy(floorPlanTool = tool, pendingPlace = null) }
        recomputeFloorPlanOverlay()
    }

    fun onFloorPlanTap(x: Float, y: Float) {
        if (_state.value.floorPlanTool != FloorPlanTool.PLACE) return
        if (_state.value.selectedFloorPlanId == null) {
            setStatus("Create or select a floor plan first", error = true)
            return
        }
        _state.update { it.copy(pendingPlace = x to y) }
    }

    fun cancelPlaceBeacon() {
        _state.update { it.copy(pendingPlace = null) }
    }

    fun confirmPlaceBeacon(label: String, uuidText: String, major: Int, minor: Int) {
        val pending = _state.value.pendingPlace ?: return
        val uuid = IBeaconPacket.parseUuidOrNull(uuidText) ?: return
        val placed = PlacedBeacon(
            label = label,
            uuid = uuid,
            major = major,
            minor = minor,
            x = pending.first,
            y = pending.second,
        )
        updateSelectedPlan { plan -> plan.copy(beacons = plan.beacons + placed) }
        _state.update { it.copy(pendingPlace = null) }
        recomputeFloorPlanOverlay()
        setStatus("Placed “${placed.label}” on floor plan", error = false)
    }

    fun deletePlacedBeacon(beaconId: String) {
        updateSelectedPlan { plan ->
            plan.copy(beacons = plan.beacons.filterNot { it.id == beaconId })
        }
        recomputeFloorPlanOverlay()
    }

    private fun updateSelectedPlan(transform: (FloorPlan) -> FloorPlan) {
        val id = _state.value.selectedFloorPlanId ?: return
        val updated = _state.value.floorPlans.map { if (it.id == id) transform(it) else it }
        persistPlans(updated, selectId = id)
    }

    private fun persistPlans(plans: List<FloorPlan>, selectId: String?) {
        floorPlanStore.saveAll(plans)
        val selected = plans.firstOrNull { it.id == selectId } ?: plans.firstOrNull()
        _state.update {
            it.copy(
                floorPlans = plans,
                selectedFloorPlanId = selected?.id,
                floorPlanWidthText = selected?.widthMeters?.toString() ?: it.floorPlanWidthText,
            )
        }
        recomputeFloorPlanOverlay()
    }

    private fun recomputeFloorPlanOverlay() {
        val state = _state.value
        val plan = state.floorPlans.firstOrNull { it.id == state.selectedFloorPlanId }
        if (plan == null) {
            _state.update {
                it.copy(locatedBeaconStatuses = emptyList(), userLocationEstimate = null)
            }
            return
        }

        val statuses = plan.beacons.map { placed ->
            val match = state.scannedBeacons.firstOrNull {
                it.uuid == placed.uuid && it.major == placed.major && it.minor == placed.minor
            }
            LocatedBeaconStatus(
                placed = placed,
                detected = match != null,
                rssi = match?.rssi,
                distanceMeters = match?.estimatedDistanceMeters,
            )
        }

        val anchors = statuses.mapNotNull { status ->
            val meters = status.distanceMeters ?: return@mapNotNull null
            Trilateration.Anchor(
                x = status.placed.x,
                y = status.placed.y,
                radius = Trilateration.metersToNormalized(meters, plan.widthMeters),
            )
        }

        val estimate = if (anchors.size >= 2) {
            Trilateration.estimate(anchors)?.let { point ->
                UserLocationEstimate(
                    point = point,
                    confidence = (anchors.size / 3f).coerceAtMost(1f),
                    usedBeaconCount = anchors.size,
                )
            }
        } else {
            null
        }

        _state.update {
            it.copy(
                locatedBeaconStatuses = statuses,
                userLocationEstimate = estimate,
            )
        }
    }

    // endregion

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
                stopForwardLoop()
                setStatus(error, error = true)
            }
        }
        _state.update { it.copy(isScanning = true) }
        setStatus("Scanning for iBeacons…", error = false)
        if (_state.value.forwardEnabled) {
            startForwardLoop()
        }
    }

    private fun stopScan() {
        scanner.stop()
        stopForwardLoop()
        _state.update { it.copy(isScanning = false) }
        setStatus("Scan stopped", error = false)
    }

    private fun startForwardLoop() {
        if (forwardJob?.isActive == true) return
        forwardJob = viewModelScope.launch {
            while (isActive) {
                postSnapshot(force = false)
                delay(FORWARD_INTERVAL_MS)
            }
        }
    }

    private fun stopForwardLoop() {
        forwardJob?.cancel()
        forwardJob = null
    }

    private suspend fun postSnapshot(force: Boolean) {
        val current = _state.value
        if (!force && !current.forwardEnabled) return
        val url = current.webhookUrl.trim()
        if (url.isEmpty()) {
            if (force || current.forwardEnabled) {
                _state.update {
                    it.copy(
                        lastForwardStatus = "Set a webhook URL to forward beacon data",
                        lastForwardWasError = true,
                    )
                }
            }
            return
        }
        when (val result = forwarder.postBeacons(url, current.scannedBeacons)) {
            is ForwardResult.Success -> {
                _state.update {
                    it.copy(
                        lastForwardStatus =
                            "Forwarded ${result.beaconCount} beacon(s) → HTTP ${result.httpCode}",
                        lastForwardWasError = false,
                    )
                }
            }
            is ForwardResult.Failure -> {
                _state.update {
                    it.copy(
                        lastForwardStatus = "Forward failed: ${result.message}",
                        lastForwardWasError = true,
                    )
                }
            }
        }
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
        stopForwardLoop()
        advertiser.stop()
        scanner.stop()
        scanner.setListener(null)
        super.onCleared()
    }

    companion object {
        val defaultUuid: UUID = UUID.fromString(DEFAULT_UUID)
    }
}
