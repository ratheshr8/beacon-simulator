package com.ratheshr8.beaconsimulator

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ratheshr8.beaconsimulator.ble.AdvertiseConfig
import com.ratheshr8.beaconsimulator.ble.AdvertiseResult
import com.ratheshr8.beaconsimulator.ble.BeaconAdvertiser
import com.ratheshr8.beaconsimulator.ble.BeaconIdentity
import com.ratheshr8.beaconsimulator.ble.BeaconScanner
import com.ratheshr8.beaconsimulator.ble.DeviceNameStore
import com.ratheshr8.beaconsimulator.ble.IBeaconPacket
import com.ratheshr8.beaconsimulator.ble.NearbyBleDevice
import com.ratheshr8.beaconsimulator.ble.ScanFilterConfig
import com.ratheshr8.beaconsimulator.ble.ScanStats
import com.ratheshr8.beaconsimulator.ble.ScannedBeacon
import com.ratheshr8.beaconsimulator.floorplan.FloorPlan
import com.ratheshr8.beaconsimulator.floorplan.FloorPlanStore
import com.ratheshr8.beaconsimulator.floorplan.FloorPlanTool
import com.ratheshr8.beaconsimulator.floorplan.LocatedBeaconStatus
import com.ratheshr8.beaconsimulator.floorplan.PlacedBeacon
import com.ratheshr8.beaconsimulator.floorplan.Point2d
import com.ratheshr8.beaconsimulator.floorplan.ReceiverTab
import com.ratheshr8.beaconsimulator.floorplan.Trilateration
import com.ratheshr8.beaconsimulator.floorplan.UserLocationEstimate
import kotlin.math.sqrt
import com.ratheshr8.beaconsimulator.net.BeaconForwarder
import com.ratheshr8.beaconsimulator.net.ForwardResult
import com.ratheshr8.beaconsimulator.permissions.BlePermissions
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
    val useCompatCompanyId: Boolean = true,
    val transmitterLabel: String = "Transmitter 1",
    val isAdvertising: Boolean = false,
    val advertisingSupported: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    // Receiver scan
    val filterUuidText: String = "",
    val filterMajorText: String = "",
    val filterMinorText: String = "",
    val isScanning: Boolean = false,
    val scannedBeacons: List<ScannedBeacon> = emptyList(),
    val nearbyBleDevices: List<NearbyBleDevice> = emptyList(),
    val scanStats: ScanStats = ScanStats(),
    val showAllBleDevices: Boolean = false,
    val filtersExpanded: Boolean = false,
    val deviceNames: Map<String, String> = emptyMap(),
    val renameTargetKey: String? = null,
    val renameDraft: String = "",
    val pairedIdentity: BeaconIdentity? = null,
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
    val liveMoveTrackedBeacon: Boolean = true,
    val receiverPin: Point2d? = null,
)

private const val DEFAULT_UUID = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"
private const val FORWARD_INTERVAL_MS = 2_000L

class BeaconViewModel(application: Application) : AndroidViewModel(application) {
    private val advertiser = BeaconAdvertiser(application)
    private val scanner = BeaconScanner(application)
    private val forwarder = BeaconForwarder()
    private val floorPlanStore = FloorPlanStore(application)
    private val deviceNameStore = DeviceNameStore(application)
    private var forwardJob: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            advertisingSupported = advertiser.isAdvertisingSupported(),
            bluetoothEnabled = advertiser.isBluetoothEnabled(),
            locationEnabled = BlePermissions.isLocationEnabled(application),
            deviceNames = deviceNameStore.loadAll(),
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

        scanner.setListener { beacons, nearby, stats ->
            _state.update {
                it.copy(
                    scannedBeacons = beacons,
                    nearbyBleDevices = nearby,
                    scanStats = stats,
                )
            }
            recomputeFloorPlanOverlay()
        }
    }

    fun refreshHardwareState() {
        _state.update {
            it.copy(
                advertisingSupported = advertiser.isAdvertisingSupported(),
                bluetoothEnabled = advertiser.isBluetoothEnabled() || scanner.isBluetoothEnabled(),
                locationEnabled = BlePermissions.isLocationEnabled(getApplication()),
            )
        }
    }

    fun updateShowAllBleDevices(show: Boolean) {
        _state.update { it.copy(showAllBleDevices = show) }
    }

    fun updateFiltersExpanded(expanded: Boolean) {
        _state.update { it.copy(filtersExpanded = expanded) }
    }

    fun beginRenameBeacon(uuid: UUID, major: Int, minor: Int, currentName: String) {
        val key = DeviceNameStore.keyFor(uuid, major, minor)
        _state.update {
            it.copy(
                renameTargetKey = key,
                renameDraft = currentName,
            )
        }
    }

    fun updateRenameDraft(value: String) {
        _state.update { it.copy(renameDraft = value) }
    }

    fun cancelRenameBeacon() {
        _state.update { it.copy(renameTargetKey = null, renameDraft = "") }
    }

    fun confirmRenameBeacon() {
        val key = _state.value.renameTargetKey ?: return
        val name = _state.value.renameDraft.trim()
        val updated = _state.value.deviceNames.toMutableMap()
        if (name.isEmpty()) {
            updated.remove(key)
        } else {
            updated[key] = name
        }
        deviceNameStore.saveAll(updated)
        _state.update {
            it.copy(
                deviceNames = updated,
                renameTargetKey = null,
                renameDraft = "",
            )
        }
    }

    fun displayNameFor(beacon: ScannedBeacon): String {
        val key = DeviceNameStore.keyFor(beacon.uuid, beacon.major, beacon.minor)
        return _state.value.deviceNames[key]
            ?: "Beacon M${beacon.major}/m${beacon.minor}"
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
    fun updateUseCompatCompanyId(value: Boolean) = _state.update { it.copy(useCompatCompanyId = value) }
    fun updateTransmitterLabel(value: String) = _state.update { it.copy(transmitterLabel = value) }

    fun currentTransmitterIdentity(): BeaconIdentity? {
        val s = _state.value
        val uuid = IBeaconPacket.parseUuidOrNull(s.uuidText) ?: return null
        val major = s.majorText.toIntOrNull() ?: return null
        val minor = s.minorText.toIntOrNull() ?: return null
        val tx = s.txPowerText.toIntOrNull() ?: -59
        return BeaconIdentity(
            uuid = uuid,
            major = major,
            minor = minor,
            label = s.transmitterLabel.ifBlank { "Transmitter" },
            companyId = if (s.useCompatCompanyId) {
                IBeaconPacket.COMPAT_COMPANY_ID
            } else {
                IBeaconPacket.APPLE_COMPANY_ID
            },
            txPower = tx,
        )
    }

    fun pairFromQr(raw: String): Boolean {
        val identity = BeaconIdentity.parseQrPayload(raw) ?: run {
            setStatus("Invalid beacon QR code", error = true)
            return false
        }
        val names = _state.value.deviceNames.toMutableMap()
        names[identity.stableKey()] = identity.label
        deviceNameStore.saveAll(names)
        _state.update {
            it.copy(
                pairedIdentity = identity,
                deviceNames = names,
                filterUuidText = identity.uuid.toString().uppercase(),
                filterMajorText = identity.major.toString(),
                filterMinorText = identity.minor.toString(),
                useCompatCompanyId = identity.companyId == IBeaconPacket.COMPAT_COMPANY_ID,
            )
        }
        applyScanFilter()
        ensurePairedBeaconOnFloorPlan(identity)
        setStatus("Paired with “${identity.label}” — list locked to this beacon", error = false)
        return true
    }

    fun clearPairing() {
        _state.update {
            it.copy(
                pairedIdentity = null,
                filterUuidText = "",
                filterMajorText = "",
                filterMinorText = "",
            )
        }
        applyScanFilter()
        setStatus("Cleared QR pairing / filters", error = false)
    }

    fun updateLiveMoveTrackedBeacon(enabled: Boolean) {
        _state.update { it.copy(liveMoveTrackedBeacon = enabled) }
    }

    fun setReceiverPin(x: Float, y: Float) {
        _state.update { it.copy(receiverPin = Point2d(x, y)) }
        setStatus("Receiver pin set on floor plan", error = false)
        recomputeFloorPlanOverlay()
    }

    fun movePlacedBeacon(beaconId: String, x: Float, y: Float) {
        updateSelectedPlan { plan ->
            plan.copy(
                beacons = plan.beacons.map {
                    if (it.id == beaconId) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it
                },
            )
        }
        recomputeFloorPlanOverlay()
    }

    private fun ensurePairedBeaconOnFloorPlan(identity: BeaconIdentity) {
        val planId = _state.value.selectedFloorPlanId ?: return
        val plan = _state.value.floorPlans.firstOrNull { it.id == planId } ?: return
        val exists = plan.beacons.any {
            it.uuid == identity.uuid && it.major == identity.major && it.minor == identity.minor
        }
        if (exists) return
        val placed = PlacedBeacon(
            label = identity.label,
            uuid = identity.uuid,
            major = identity.major,
            minor = identity.minor,
            x = 0.5f,
            y = 0.5f,
        )
        updateSelectedPlan { it.copy(beacons = it.beacons + placed) }
        setStatus("Added “${identity.label}” to floor plan center — drag in Place mode to position", error = false)
    }

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
        var state = _state.value
        var plan = state.floorPlans.firstOrNull { it.id == state.selectedFloorPlanId }
        if (plan == null) {
            _state.update {
                it.copy(locatedBeaconStatuses = emptyList(), userLocationEstimate = null)
            }
            return
        }

        // Live-move paired/tracked transmitter on the map from measured distance.
        if (state.liveMoveTrackedBeacon && state.floorPlanTool == FloorPlanTool.LOCATE) {
            val moved = maybeLiveMoveTrackedBeacon(plan, state)
            if (moved != null) {
                plan = moved
                state = _state.value
            }
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

    /**
     * Move the paired beacon marker along the ray from the receiver pin (or You)
     * so map distance matches the latest RSSI estimate — reflects transmitter motion.
     */
    private fun maybeLiveMoveTrackedBeacon(plan: FloorPlan, state: UiState): FloorPlan? {
        val paired = state.pairedIdentity ?: return null
        val scan = state.scannedBeacons.firstOrNull {
            it.uuid == paired.uuid && it.major == paired.major && it.minor == paired.minor
        } ?: return null
        val meters = scan.estimatedDistanceMeters ?: return null
        val origin = state.receiverPin ?: state.userLocationEstimate?.point ?: Point2d(0.5f, 0.5f)
        val target = plan.beacons.firstOrNull {
            it.uuid == paired.uuid && it.major == paired.major && it.minor == paired.minor
        } ?: return null

        val dx = target.x - origin.x
        val dy = target.y - origin.y
        val currentNorm = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val desiredNorm = Trilateration.metersToNormalized(meters, plan.widthMeters)
        val scale = desiredNorm / currentNorm
        val newX = (origin.x + dx * scale).coerceIn(0.02f, 0.98f)
        val newY = (origin.y + dy * scale).coerceIn(0.02f, 0.98f)
        if (kotlin.math.abs(newX - target.x) < 0.004f && kotlin.math.abs(newY - target.y) < 0.004f) {
            return null
        }

        val updatedBeacons = plan.beacons.map {
            if (it.id == target.id) it.copy(x = newX, y = newY) else it
        }
        val updatedPlan = plan.copy(beacons = updatedBeacons)
        val plans = state.floorPlans.map { if (it.id == plan.id) updatedPlan else it }
        // Persist lightly so restarts keep last known tracked spot
        floorPlanStore.saveAll(plans)
        _state.update { it.copy(floorPlans = plans) }
        return updatedPlan
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
                companyId = if (current.useCompatCompanyId) {
                    IBeaconPacket.COMPAT_COMPANY_ID
                } else {
                    IBeaconPacket.APPLE_COMPANY_ID
                },
            )
        ) { result ->
            viewModelScope.launch {
                when (result) {
                    is AdvertiseResult.Started -> {
                        _state.update { it.copy(isAdvertising = true) }
                        val company = if (current.useCompatCompanyId) {
                            "compat 0x0BAF"
                        } else {
                            "Apple 0x004C"
                        }
                        setStatus(
                            "Advertising iBeacon ($company)" +
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
        if (!_state.value.locationEnabled) {
            setStatus(
                "Location is OFF. Android will return empty BLE scans — turn on Location in system settings, then retry.",
                error = true,
            )
            return
        }
        applyScanFilter()
        scanner.start { error ->
            viewModelScope.launch {
                _state.update { it.copy(isScanning = false) }
                stopForwardLoop()
                setStatus(error, error = true)
            }
        }
        _state.update { it.copy(isScanning = true) }
        setStatus(
            "Scanning… Use a second phone in Transmitter mode (same-phone advertise→scan usually shows nothing).",
            error = false,
        )
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
