package com.ratheshr8.beaconsimulator.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import java.util.UUID

sealed class AdvertiseResult {
    data object Started : AdvertiseResult()
    data class Failed(val message: String, val errorCode: Int? = null) : AdvertiseResult()
    data object Stopped : AdvertiseResult()
}

data class AdvertiseConfig(
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val txPower: Int,
    val includeEddystone: Boolean = false,
    /**
     * BLE company ID for manufacturer data. Apple iBeacon uses 0x004C; some OEMs
     * strip that ID when advertising — use [IBeaconPacket.COMPAT_COMPANY_ID] then.
     */
    val companyId: Int = IBeaconPacket.APPLE_COMPANY_ID,
)

/**
 * Starts BLE advertising with an iBeacon manufacturer payload.
 *
 * Practical notes:
 * - Requires [BluetoothAdapter.isMultipleAdvertisementSupported] on most devices.
 * - Some OEMs silently drop or reject Apple company ID (0x004C) manufacturer data.
 * - Even when advertising succeeds, another phone may need a custom scanner that
 *   reads manufacturer data (standard iOS Core Location will not see Android-emulated
 *   iBeacons in many cases).
 */
class BeaconAdvertiser(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var activeCallback: AdvertiseCallback? = null
    private var advertising = false

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun isAdvertisingSupported(): Boolean =
        adapter?.isMultipleAdvertisementSupported == true

    fun isAdvertising(): Boolean = advertising

    @SuppressLint("MissingPermission")
    fun start(config: AdvertiseConfig, onResult: (AdvertiseResult) -> Unit) {
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            onResult(AdvertiseResult.Failed("Bluetooth is off or unavailable"))
            return
        }
        if (!bt.isMultipleAdvertisementSupported) {
            onResult(
                AdvertiseResult.Failed(
                    "This device does not support BLE peripheral advertising " +
                        "(isMultipleAdvertisementSupported=false)"
                )
            )
            return
        }

        stopInternal()

        val leAdvertiser = bt.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            onResult(AdvertiseResult.Failed("BluetoothLeAdvertiser is null on this device"))
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val iBeaconData = IBeaconPacket.buildManufacturerData(
            uuid = config.uuid,
            major = config.major,
            minor = config.minor,
            txPower = config.txPower,
        )

        val advertiseDataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(config.companyId, iBeaconData)

        // Scan response can carry Eddystone-UID without overcrowding the primary PDU.
        val scanResponse = if (config.includeEddystone) {
            AdvertiseData.Builder()
                .addServiceUuid(EddystonePacket.SERVICE_UUID)
                .addServiceData(
                    EddystonePacket.SERVICE_UUID,
                    EddystonePacket.buildUidServiceData(
                        proximityUuid = config.uuid,
                        major = config.major,
                        minor = config.minor,
                        txPower = config.txPower,
                    )
                )
                .build()
        } else {
            null
        }

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                advertising = true
                onResult(AdvertiseResult.Started)
            }

            override fun onStartFailure(errorCode: Int) {
                advertising = false
                activeCallback = null
                onResult(
                    AdvertiseResult.Failed(
                        message = failureMessage(errorCode),
                        errorCode = errorCode,
                    )
                )
            }
        }

        activeCallback = callback
        advertiser = leAdvertiser
        try {
            if (scanResponse != null) {
                leAdvertiser.startAdvertising(settings, advertiseDataBuilder.build(), scanResponse, callback)
            } else {
                leAdvertiser.startAdvertising(settings, advertiseDataBuilder.build(), callback)
            }
        } catch (security: SecurityException) {
            advertising = false
            activeCallback = null
            onResult(AdvertiseResult.Failed("Missing BLUETOOTH_ADVERTISE permission: ${security.message}"))
        } catch (ex: Exception) {
            advertising = false
            activeCallback = null
            onResult(AdvertiseResult.Failed("Failed to start advertising: ${ex.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(onResult: ((AdvertiseResult) -> Unit)? = null) {
        stopInternal()
        onResult?.invoke(AdvertiseResult.Stopped)
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal() {
        val callback = activeCallback
        val adv = advertiser
        if (callback != null && adv != null) {
            runCatching { adv.stopAdvertising(callback) }
        }
        activeCallback = null
        advertiser = null
        advertising = false
    }

    private fun failureMessage(errorCode: Int): String = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ->
            "Advertise data too large (try disabling Eddystone)"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
            "Too many advertisers already running"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED ->
            "Advertising already started"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR ->
            "Internal Bluetooth stack error"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
            "Advertising feature unsupported on this hardware/firmware"
        else -> "Advertising failed (errorCode=$errorCode)"
    }
}
