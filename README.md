# TWS Bridge – Universal Earbud Synchronization

**TWS Bridge** is an offline-first Android diagnostic and Bluetooth engineering application built to investigate whether two independent wireless earbuds (one left, one right) from different original pairs—especially generic/white-label earbuds with unknown chipsets—can be identified, inspected, and synchronized to operate together as a stereo TWS pair.

> **Current Status:** Phase 1 (Bluetooth discovery and GATT/SDP inspection) is implemented. Phase 2 now includes heuristic chipset fingerprinting and a one-tap read-only probe for standard Device Information and Battery characteristics. Phase 3 vendor synchronization, Phase 4 firmware inspection, and Phase 5 advanced audio routing remain unimplemented.

---

## 1. Technical Feasibility Research

### Why Two Mismatched TWS Earbuds Do Not Pair Out of the Box
In standard True Wireless Stereo (TWS) earbuds, the left and right buds are **not** two independent Bluetooth speakers that an Android phone connects to separately over Classic A2DP. Instead:

1. **Peer Link Keys & Static Peer Addresses:** During factory calibration, the Left and Right earbuds store each other's Bluetooth Device Address (`BD_ADDR`), a shared 128-bit TWS Link Key / Long-Term Key (LTK), and role assignment (`Master/Primary` vs. `Slave/Secondary`, or dynamic role-swap tokens) inside internal non-volatile flash/EEPROM.
2. **Classic A2DP Relay / Sniffing Topology:** On Classic BR/EDR earbuds (used by the vast majority of generic/knockoff earbuds on JieLi, Bluetrum, older BES/Actions/Airoha parts), the phone connects only to the **Primary** bud. The Secondary bud either receives relayed audio packets over a proprietary 2.4 GHz / Bluetooth link or sniffs the phone-to-primary A2DP ACL stream (e.g., Qualcomm TrueWireless Mirroring, BES IBRT, Airoha MCSync).
3. **Why Different-Pair Buds Fail:** Even if two buds come from the exact same assembly line and PCB revision, Bud L from Pair A expects Bud R's `BD_ADDR` and link key from Pair A, while Bud R from Pair B expects Bud L from Pair B. Unless both buds support **field re-pairing** (via hardware tap sequences or a vendor BLE/SPP command) or **independent dual-stream audio** (via LE Audio or OEM Dual Audio), they will remain two separate mono/single-bud devices.

### Realistic Paths That *Can* Work (Subject to Hardware Support)
1. **Factory Hardware / Case Re-Sync (Highest probability on generic buds):** Many JieLi and Bluetrum clones actually support re-pairing two mismatched buds from the same firmware family using specific multi-tap sequences (e.g., double/triple-tap while flashing in pairing mode) or placing both buds in the same charging case if the case uses power-line UART/pulse signaling.
2. **Vendor BLE / SPP Control Commands (Phase 2–3):** Some SoCs (notably **JieLi RCSP** `0xAE00` and **BES** vendor GATT/SPP) leave a control channel exposed over BLE or Classic SPP. If the earbud firmware still listens to TWS peer-search or factory-reset opcodes, an Android app can send them without root.
3. **Host-Side Dual Streaming / LE Audio (Phase 5):**
   - **Classic Bluetooth A2DP on standard AOSP Android** routes media to **one active A2DP sink** at a time.
   - **OEM Dual Audio** (e.g., Samsung Dual Audio) can stream identical audio to two Classic devices, but does *not* provide true L/R channel separation and has variable inter-bud drift.
   - **Bluetooth LE Audio (Android 13+ API 33+, CIS/CSIS or Auracast BIS):** Can stream separate Left/Right Isochronous Channels if *both* the phone and the earbuds support LE Audio. Generic knockoff earbuds almost never include LE Audio hardware/firmware.

---

## 2. Android Bluetooth API Limitations (Non-Rooted Phones)

| Capability | Standard Android API (Non-Root) | Limitation / Caveat |
|---|---|---|
| **BLE Scanning** | `BluetoothLeScanner.startScan()` | Works on Android 8+. Requires `BLUETOOTH_SCAN` (API 31+) or `ACCESS_FINE_LOCATION` (API 26–30). |
| **Classic BR/EDR Discovery** | `BluetoothAdapter.startDiscovery()` | ~12s inquiry window. High radio contention; must be stopped before `connectGatt()`. |
| **MAC Addresses** | `BluetoothDevice.getAddress()` | Available for discovered devices with runtime scan/connect permissions, though BLE devices often use Resolvable/Static Random Addresses that rotate. |
| **GATT Inspection** | `device.connectGatt(..., TRANSPORT_LE)` | Many cheap earbuds are **Classic-only** and reject GATT connections with **`status = 133` (`GATT_ERROR`)** or timeout (`0x08`). |
| **Classic Profile Probing** | `device.fetchUuidsWithSdp()` | Reveals Classic SDP UUIDs (A2DP `0x110B`, HFP `0x111E`, SPP `0x1101`), *not* internal firmware details. |
| **Raw HCI / Link Keys / Spoofing** | **Not Available** | Non-rooted apps cannot inject raw HCI packets, read/write `BD_ADDR`, or extract Classic Bluetooth link keys from `/data/misc/bluedroid`. |
| **Simultaneous Stereo A2DP** | **Not Available in AOSP** | Standard `AudioManager` / `BluetoothA2dp` routes media to one active Classic A2DP sink at a time. |
| **Arbitrary Firmware Flashing** | **Not Available** | Android Bluetooth APIs cannot erase or flash arbitrary earbud flash memory. OTA updates only work if the earbud explicitly exposes a vendor bootloader/DFU service. |

---

## 3. What Can & Cannot Be Accomplished Without Knowing the Chipset

### What CAN Be Accomplished Without Knowing the Chipset (Phases 1–2)
- Discover whether each earbud advertises **BLE**, **Classic BR/EDR**, or **Dual-mode**.
- Measure **RSSI** signal strength and observe **Bluetooth Class of Device (CoD)** (e.g., `Wearable Headset` vs. `Uncategorized`).
- Capture **Manufacturer Specific Data Company IDs** (e.g., `0x05D6` JieLi, `0x0642` Bluetrum, `0x02B0` BES, `0x03E0` Actions, `0x0094` Airoha)—**strictly treated as unverified hints**, because knockoffs frequently spoof Apple's `0x004C` Company ID for pop-up animations.
- Probe **Classic SDP UUIDs** (including whether **SPP `0x1101`** is open for serial AT/vendor commands).
- Connect to **BLE GATT** (when exposed) and read **Device Information (`0x180A`)**, **Battery (`0x180F`)**, **PnP ID (`0x2A50`)**, and list unknown vendor services (`0xAE00`, `0xFF00`, `0xFFE0`, etc.).

### What CANNOT Be Accomplished Without Chipset Identification
- **Decoding or sending proprietary TWS sync commands:** Every SoC vendor uses different framing, CRC, and opcode tables. Sending blind `WRITE` commands to unknown characteristics can trigger factory locks or corrupt NVRAM.
- **Triggering bootloader / firmware dump / recovery mode:** Chipsets like JieLi (USB/UART/OTA RCSP), Bluetrum (UART/proprietary OTA), BES (BES OTA), Actions (ADFU/OTA), and Airoha (RACE protocol) have incompatible, often encrypted or checksummed protocols—and many sealed retail buds disable flash readback entirely.

---

## 4. Proposed Architecture & Folder Structure

TWS Bridge uses **Clean MVVM** with strict separation between Android Bluetooth/Audio platform bridges, pure-Kotlin diagnostic heuristics, and Jetpack Compose UI:

```text
app/src/main/java/com/example/
├── domain/
│   └── model/
│       ├── BluetoothEnvironmentState.kt   # Adapter, BLE, and permission readiness
│       ├── DiagnosticLogEntry.kt          # Structured diagnostic log model
│       ├── DiscoveredDevice.kt            # Unified Classic/BLE device + heuristic confidence
│       └── GattModels.kt                  # GATT session, service, characteristic & read states
├── data/
│   ├── audio/
│   │   └── OsAudioController.kt           # AudioManager volume mixer, routing & L/R stereo test tones
│   ├── bluetooth/
│   │   ├── BluetoothDiscoveryManager.kt   # Dual-mode BLE/Classic scan, SDP, GATT queue & BT renaming
│   │   ├── BluetoothPermissionHelper.kt   # API 26–30 vs API 31+ runtime permission mapping
│   │   ├── DiagnosticLogger.kt            # Ring-buffered thread-safe logger + text export
│   │   └── EarbudHeuristics.kt            # CoD, UUID, Company ID & byte/UTF-8 decoders
│   └── preferences/
│       └── TwsPreferences.kt              # SharedPreferences persistence for Dev Mode, aliases & L/R roles
└── ui/
    ├── components/
    │   └── SharedBadges.kt                # Transport, status pills & RSSI signal meter
    ├── screens/
    │   ├── OsControlScreen.kt             # Default OS Hub: volume, L/R test tone, routing & device names
    │   ├── ScannerScreen.kt               # Dev Mode: Discovery, filters, permissions & radio state
    │   ├── InspectorScreen.kt             # Dev Mode: GATT service tree, sequential reads & SDP
    │   ├── LogsScreen.kt                  # Dev Mode: Diagnostic console + Android Sharesheet export
    │   └── LabGuideScreen.kt              # Dev Mode: Offline feasibility, API limits & chipset reference
    ├── theme/
    │   ├── Color.kt, Theme.kt, Type.kt    # Dark-first hardware lab palette & local fonts
    └── viewmodel/
        └── TwsBridgeViewModel.kt          # Lifecycle-safe state holder & filter orchestration
```

### Planned Module Expansion (Phases 2–5)
- `data/diagnostic/` *(Phase 2)*: Chipset fingerprinting engine (JieLi RCSP, BES, Bluetrum, Actions, Airoha signatures), A2DP codec status query (`BluetoothCodecStatus`), and Confirmed vs. Heuristic confidence reports.
- `data/sync/` *(Phase 3)*: Safe vendor-protocol probing (e.g., SPP/BLE handshake detection), guided hardware/case reset workflows, and dual-connection state verification.
- `data/firmware/` *(Phase 4)*: Read-only DFU/OTA service detection and explicit-consent safety gates (never blind flashing).
- `data/audio/` *(Phase 5)*: `BluetoothA2dp` / `AudioDeviceInfo` / LE Audio capability evaluator and channel-routing diagnostics.

---

## 5. Phase 1–2 & OS Controls Implementation Summary

- **Minimum Android Version:** Android 8.0 (API 26), targetSdk 36.
- **Offline-First & No Root Required:** Zero network calls or `INTERNET` permission required.
- **Dev Mode Toggle (Persistent):**
  - **Dev Mode OFF (Default OS Mode):** Presents the clean **OS Hub** with real `AudioManager` volume sliders (Media + Mute, Voice Call, Ring & Alerts), an `AudioTrack` L/R stereo channel check (`Left Only`, `Right Only`, `L -> R Sweep`), active audio output hardware routes, OS Bluetooth host renaming (`BluetoothAdapter.setName`), OS device alias renaming (`BluetoothDevice.setAlias` on Android 11+), and custom per-earbud labels & Left/Right tags.
  - **Dev Mode ON:** Unlocks the full Phase 1 engineering tabs (**Scanner**, **Inspector**, **Logs**, and **Lab Guide**) alongside **OS Hub**.
- **Dual-Mode Discovery:** Runs BLE low-latency scanning alongside Classic BR/EDR inquiry (`startDiscovery`) and loads system-bonded devices immediately.
- **Earbud Heuristic Filtering:** Highlights probable earbuds by Bluetooth Class of Device, audio/TWS UUIDs, and naming tokens while surfacing unverified Bluetooth SIG Company IDs separately.
- **BLE GATT Client & Classic SDP Inspector:** Sequential GATT read queue (prevents Android's single-operation GATT collisions), automatic Hex + UTF-8 decoding, clear `status 133` diagnostics for Classic-only earbuds, and `fetchUuidsWithSdp()` probing.
- **Diagnostic Console:** Real-time timestamped logs with category filters and one-tap Sharesheet export for collaborative debugging.\n- **Chipset Fingerprint:** Inspector combines manufacturer company IDs, advertised/SDP/GATT UUIDs, and device-name hints. Confidence is explicitly heuristic and warns that clone devices may spoof identifiers.\n- **Standard Device Information Probe:** One action queues readable Device Information and Battery characteristics through the existing serialized GATT read queue; no write commands are issued.

---

## 6. Build & Installation Instructions

### Prerequisites
- **Android Studio** (Ladybug / Meerkat or newer) with JDK 11+ and Android SDK 36 installed.
- Physical Android phone running **Android 8.0 (API 26)** or higher (recommended for real Bluetooth hardware testing; Android Emulators generally do not pass through physical Bluetooth HCI radios).

### Option A: Android Studio
1. Open the repository root folder in Android Studio.
2. Allow Gradle sync to complete.
3. Connect an Android 8+ device via USB with **USB Debugging** enabled.
4. Select the `app` run configuration and click **Run**.

### Option B: Command Line (Gradle)
> Note: Depending on your environment, use `gradle` (AI Studio container) or `./gradlew` (standard local checkout with Gradle Wrapper).

```bash
# Run unit & Robolectric tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug

# Install onto connected USB device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 7. Known Limitations & Next Steps

### Current Limitations
1. **Classic-only generic earbuds** will appear under Classic/Bonded discovery and support **SDP UUID probing**, but will return `GATT_ERROR (133)` if you attempt a BLE GATT connection—this is expected hardware behavior when the earbud disables BLE GATT.
2. **BLE MAC randomization:** Unpaired BLE devices may rotate addresses across power cycles.
3. **No Phase 2–5 write/sync commands yet:** Phase 1 is strictly **read-only and non-destructive**.

### Next Steps (Phase 2 – Earbud Identification)
1. Test both mismatched generic earbuds in **Phase 1** and export the diagnostic logs + GATT/SDP dumps.
2. Inspect whether either bud exposes:
   - BLE services such as `0xAE00` (JieLi RCSP), `0xFFE0`, `0xFF00`, or `0x180A` Device Information (`Manufacturer`, `Model`, `Firmware Revision`, `PnP ID`).
   - Classic **SPP (`0x1101`)** or vendor RFCOMM channels via `Probe Classic SDP`.
3. Compare the new chipset fingerprint with the returned Manufacturer, Model, Firmware and PnP values.\n4. Capture the two earbuds separately and compare their services, identifiers and device-information values before designing any vendor-specific synchronization workflow.
