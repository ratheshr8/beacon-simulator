# Beacon Simulator

Android app that simulates both a **BLE beacon transmitter** and a **BLE receiver/scanner** so you can test beacon-aware apps without physical beacon hardware.

**Package:** `com.ratheshr8.beaconsimulator`  
**Min SDK:** 26 (Android 8.0) · **Target / Compile SDK:** 34  
**UI:** Kotlin + Jetpack Compose (Material 3)

### Download APK (no build required)

Get the prebuilt debug APK from GitHub Releases:

**https://github.com/ratheshr8/beacon-simulator/releases/latest**

Latest asset: `beacon-simulator-*-debug.apk` (see release notes for the exact filename)  
Install: open the file on your phone, or `adb install -r <apk-file>`  
(Allow install from unknown sources / “Install unknown apps” if Android asks.)

---

## Modes

### 1. Transmitter (Beacon)

Advertises an **iBeacon** packet using Android BLE advertising APIs (`BluetoothLeAdvertiser`):

| Field | Description |
| --- | --- |
| Proximity UUID | 128-bit UUID (same format your app already uses) |
| Major / Minor | 0–65535 |
| Measured TX power | Calibrated RSSI at 1 m (typical −59) |
| Eddystone-UID (optional) | Extra UID frame in the scan response (derived from UUID/major/minor) |

Tap **Start advertising** to begin, **Stop advertising** to end.

### 2. Receiver (Scanner)

Scans for nearby BLE advertisements and parses Apple company ID (`0x004C`) iBeacon frames. The list shows:

- UUID, major, minor  
- RSSI  
- Rough estimated distance / proximity  
- Advertiser MAC address  

Optional filters: UUID, major, minor.

### Forward scanned beacons to an external URL

In **Receiver** mode you can POST live scan results to any HTTP(S) webhook:

1. Enter a **Forward URL** (e.g. `https://your-server.com/hooks/beacons` or a local `http://192.168.x.x:3000/...`).
2. Enable **Forward to URL** (posts about every 2 seconds while scanning), and/or tap **Send now**.
3. Your server receives JSON like:

```json
{
  "source": "beacon-simulator",
  "event": "beacon_scan",
  "timestampMs": 1731400000000,
  "beacons": [
    {
      "uuid": "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
      "major": 1,
      "minor": 1,
      "rssi": -61,
      "txPower": -59,
      "distanceMeters": 1.25,
      "proximity": "Near",
      "deviceAddress": "AA:BB:CC:DD:EE:FF",
      "lastSeenEpochMs": 1731400000123
    }
  ]
}
```

Use this to feed your existing backend, n8n/Zapier webhook, or another app without pairing to the BLE radio directly.

### Floor plan & beacon locations

Receiver → **Floor plan** tab:

1. **New blank** or **From photo** — create a plan and set its real-world width in meters.
2. **Place beacon** — tap the map where a physical (or simulated) beacon sits, then enter UUID / major / minor (or fill from a live scan).
3. **Locate** — start scanning. Detected placed beacons highlight with RSSI and range rings; with **2+** beacons in range the app estimates **your** position on the plan (RSSI multilateration — approximate indoors).

Placed beacon coordinates are the “exact” map locations you configure. Live locate overlays detection/range; it does not invent beacon coordinates from RSSI alone.

---

## Build & install

### Prerequisites

- Android Studio Ladybug / Koala+ (or JDK 17 + Android SDK 34)
- A physical Android phone with Bluetooth LE (emulators do **not** support BLE advertising/scanning usefully)

### From Android Studio

1. `git clone https://github.com/ratheshr8/beacon-simulator.git`
2. Open the project in Android Studio and let Gradle sync.
3. Connect a device → **Run** `app`.

### From the command line

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch, grant **Nearby devices / Bluetooth** and **Location** permissions when prompted.

**If Receiver shows “Scanning…” but lists nothing:**
1. Turn **Location** ON in Android system settings (required for BLE results on most phones).
2. Use **two phones** — a phone usually cannot see its own advertisements.
3. Watch the counters (`ads heard` / `BLE devices`). If ads stay at 0, permissions/Location are the issue; if ads rise but iBeacons stay 0, try Transmitter **Compatible company ID**.
4. Reinstall after updating so the app no longer declares `BLUETOOTH_SCAN` with `neverForLocation` (that flag emptied scans on many devices).

---

## How to use both modes

### Quick self-test (two phones)

1. Phone A → **Transmitter**: set UUID / major / minor → **Start advertising**.
2. Phone B → **Receiver**: optionally paste the same UUID filter → **Start scan**.
3. Confirm Phone B lists the beacon with updating RSSI.

### Single-phone workflow

Use **Transmitter** on this device and point your production beacon-aware app (on a second device, or the same device if your stack supports it) at matching UUID/major/minor.  
Many stacks cannot scan advertisements originating from the same radio; prefer two devices.

---

## Point your existing app at this simulator

1. Open **Transmitter** mode.
2. Set **UUID**, **Major**, and **Minor** to the exact values your app already monitors (or change your app’s monitored region to match the simulator defaults).
3. Start advertising.
4. In your existing app, start ranging/monitoring as usual.

**Default proximity UUID:** `E2C56DB5-DFFB-48D2-B060-D0F5A71096E0`  
**Default major / minor:** `1` / `1`  
**Default measured power:** `-59`

Your scanner must read **manufacturer-specific data** for company ID `0x004C` with the standard iBeacon layout (`0x02 0x15` + UUID + major + minor + TX). Libraries such as Android Beacon Library, custom `ScanRecord` parsers, and many cross-platform plugins do this.

---

## Permissions

| Android version | Permissions requested |
| --- | --- |
| 12+ (API 31+) | `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION` |
| 8–11 | `ACCESS_FINE_LOCATION` (+ legacy Bluetooth permissions declared in the manifest) |

Location is still requested because some OEMs and older APIs gate BLE scan results behind it, even when scan is marked `neverForLocation`.

---

## Known Android BLE advertising limitations

Android is **not** an iBeacon platform the way iOS is. Practical caveats:

1. **Hardware support varies**  
   Advertising requires `BluetoothAdapter.isMultipleAdvertisementSupported() == true`. Many low-end / older phones return `false`. The app surfaces this in Transmitter mode.

2. **OEM / firmware filtering**  
   Some manufacturers restrict or strip Apple company ID (`0x004C`) manufacturer data. Advertising may report success while receivers never see a valid iBeacon frame—or `startAdvertising` may fail with `ADVERTISE_FAILED_FEATURE_UNSUPPORTED` / data errors.

3. **iOS Core Location usually will not see Android-emulated iBeacons**  
   iOS region monitoring expects Apple’s iBeacon ecosystem. An Android phone advertising manufacturer data is **not** a reliable stand-in for a physical iBeacon when the client is iOS. Use an Android scanner (this app’s Receiver mode, or your Android app) for validation.

4. **Same-device loopback is unreliable**  
   A phone generally cannot reliably discover its own advertisements. Use two devices or a USB BLE sniffer.

5. **Payload size**  
   BLE advertise PDUs are small. Enabling Eddystone-UID uses the scan response. If start fails with “data too large”, disable Eddystone.

6. **Distance is approximate**  
   RSSI→meters uses a common path-loss heuristic. Indoors, treat proximity bands (Immediate / Near / Far) as guidance only.

7. **Background limits**  
   This app is built for foreground testing. Android aggressively limits background scan/advertise; keep the screen on while testing.

**Best practical approach used here:** advertise a standards-shaped iBeacon manufacturer payload via `BluetoothLeAdvertiser`, optionally add Eddystone-UID service data in the scan response, and verify with the in-app Receiver or any scanner that parses manufacturer data.

---

## Project layout

```
app/src/main/java/com/ratheshr8/beaconsimulator/
  MainActivity.kt              # Compose host + permission flow
  BeaconViewModel.kt           # Mode state, start/stop wiring
  ble/
    IBeaconPacket.kt           # Build/parse iBeacon frames
    EddystonePacket.kt         # Optional Eddystone-UID
    BeaconAdvertiser.kt        # BLE advertise
    BeaconScanner.kt           # BLE scan + filter
    DistanceEstimator.kt       # RSSI → rough meters
  permissions/BlePermissions.kt
  ui/…                         # Material 3 screens
```

---

## License

MIT — use freely for development and testing.
