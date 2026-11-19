package com.example.data.bluetooth

import android.bluetooth.BluetoothClass
import com.example.domain.model.EarbudClassification
import java.util.Locale

object EarbudHeuristics {

  private val earbudDeviceClasses =
    setOf(
      BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
      BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
      BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
      BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
      BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
    )

  private val audioProfileUuidPrefixes =
    setOf(
      "0000110b", // A2DP Audio Sink
      "0000110d", // Advanced Audio Distribution
      "0000110e", // AVRCP
      "0000111e", // Handsfree
      "00001108", // Headset
      "0000184e", // LE Audio ASCS
      "00001850", // LE Audio PACS
      "00001853", // LE Audio CSIS (Coordinated Set)
      "00001855", // LE Audio TMAS
      "0000fe2c", // Google Fast Pair
      "0000ae00", // JieLi RCSP Vendor Service (Common in generic TWS)
    )

  private val highSignalNameTokens =
    listOf(
      "tws",
      "earbud",
      "buds",
      "airpods",
      "pods",
      "headset",
      "headphone",
      "earphone",
      "a6s",
      "e7s",
      "f9",
      "m10",
      "i12",
      "i13",
      "lenovo",
      "qcy",
      "haylou",
      "soundcore",
      "jlab",
      "edifier",
    )

  private val weakChannelSuffixes = listOf("_l", "_r", "-l", "-r", " left", " right", " (l)", " (r)")

  // Common Bluetooth SIG Company Identifiers encountered in earbuds (heuristic only: knockoffs may spoof IDs like Apple 0x004C)
  private val companyIds =
    mapOf(
      0x000A to "Qualcomm Technologies International (CSR)",
      0x000F to "Broadcom Corporation",
      0x0046 to "MediaTek, Inc.",
      0x004C to "Apple, Inc. (often spoofed by clone TWS popups)",
      0x005D to "Realtek Semiconductor",
      0x0075 to "Samsung Electronics",
      0x0094 to "Airoha Technology Corp.",
      0x00E0 to "Google LLC",
      0x02B0 to "Bestechnic (BES) / Hengxuan",
      0x03E0 to "Actions Technology (炬芯)",
      0x05D6 to "Zhuhai Jieli Technology (杰理)",
      0x0642 to "Bluetrum Technology (中科蓝讯)",
      0x079A to "Guangdong Oppo Mobile",
      0x089A to "Xiaomi Communications",
    )

  private val knownUuidLabels =
    mapOf(
      // Standard GATT Services
      "00001800-0000-1000-8000-00805f9b34fb" to "Generic Access (GAP)",
      "00001801-0000-1000-8000-00805f9b34fb" to "Generic Attribute (GATT)",
      "0000180a-0000-1000-8000-00805f9b34fb" to "Device Information",
      "0000180f-0000-1000-8000-00805f9b34fb" to "Battery Service",
      "00001812-0000-1000-8000-00805f9b34fb" to "Human Interface Device (HID)",
      "0000184e-0000-1000-8000-00805f9b34fb" to "Audio Stream Control (ASCS / LE Audio)",
      "00001850-0000-1000-8000-00805f9b34fb" to "Published Audio Capabilities (PACS)",
      "00001853-0000-1000-8000-00805f9b34fb" to "Coordinated Set Identification (CSIS)",
      "00001855-0000-1000-8000-00805f9b34fb" to "Telephony & Media Audio (TMAS)",
      "0000fe2c-0000-1000-8000-00805f9b34fb" to "Google Fast Pair Service",
      // Vendor / Chipset-associated UUIDs (observed in field; verify before assuming chipset)
      "0000ae00-0000-1000-8000-00805f9b34fb" to "JieLi RCSP Control Service (Vendor)",
      "0000ae01-0000-1000-8000-00805f9b34fb" to "JieLi RCSP Write Channel",
      "0000ae02-0000-1000-8000-00805f9b34fb" to "JieLi RCSP Notify Channel",
      "0000fee0-0000-1000-8000-00805f9b34fb" to "Vendor Telemetry / Anhui Huami",
      "0000ff00-0000-1000-8000-00805f9b34fb" to "Generic Vendor Service (0xFF00)",
      "0000ffe0-0000-1000-8000-00805f9b34fb" to "Generic UART / Serial Bridge (0xFFE0)",
      // Standard GATT Characteristics
      "00002a00-0000-1000-8000-00805f9b34fb" to "Device Name",
      "00002a01-0000-1000-8000-00805f9b34fb" to "Appearance",
      "00002a04-0000-1000-8000-00805f9b34fb" to "Peripheral Preferred Connection Params",
      "00002a05-0000-1000-8000-00805f9b34fb" to "Service Changed",
      "00002a19-0000-1000-8000-00805f9b34fb" to "Battery Level",
      "00002a23-0000-1000-8000-00805f9b34fb" to "System ID",
      "00002a24-0000-1000-8000-00805f9b34fb" to "Model Number String",
      "00002a25-0000-1000-8000-00805f9b34fb" to "Serial Number String",
      "00002a26-0000-1000-8000-00805f9b34fb" to "Firmware Revision String",
      "00002a27-0000-1000-8000-00805f9b34fb" to "Hardware Revision String",
      "00002a28-0000-1000-8000-00805f9b34fb" to "Software Revision String",
      "00002a29-0000-1000-8000-00805f9b34fb" to "Manufacturer Name String",
      "00002a50-0000-1000-8000-00805f9b34fb" to "PnP ID (Vendor/Product ID)",
      // Classic SDP Profile UUIDs
      "0000110b-0000-1000-8000-00805f9b34fb" to "A2DP Audio Sink",
      "0000110c-0000-1000-8000-00805f9b34fb" to "AVRCP Remote Control Target",
      "0000110e-0000-1000-8000-00805f9b34fb" to "AVRCP Remote Control",
      "0000111e-0000-1000-8000-00805f9b34fb" to "Handsfree Profile (HFP)",
      "00001108-0000-1000-8000-00805f9b34fb" to "Headset Profile (HSP)",
      "00001101-0000-1000-8000-00805f9b34fb" to "Serial Port Profile (SPP)",
    )

  fun resolveDeviceClassLabel(majorClass: Int?, deviceClass: Int?): String {
    if (deviceClass == null && majorClass == null) return "Unspecified Class"
    return when (deviceClass) {
      BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Wearable Headset"
      BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
      BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Handsfree Audio"
      BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> "Portable Audio"
      BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "HiFi Audio"
      BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Loudspeaker"
      BluetoothClass.Device.PHONE_SMART -> "Smartphone"
      BluetoothClass.Device.COMPUTER_LAPTOP -> "Laptop"
      else ->
        when (majorClass) {
          BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video Device"
          BluetoothClass.Device.Major.PHONE -> "Phone"
          BluetoothClass.Device.Major.COMPUTER -> "Computer"
          BluetoothClass.Device.Major.WEARABLE -> "Wearable"
          BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
          BluetoothClass.Device.Major.UNCATEGORIZED -> "Uncategorized (BLE)"
          else -> "Class 0x${(deviceClass ?: majorClass ?: 0).toString(16)}"
        }
    }
  }

  fun classifyEarbudCandidate(
    name: String?,
    majorClass: Int?,
    deviceClass: Int?,
    advertisedUuids: List<String>,
    sdpUuids: List<String>,
  ): EarbudClassification {
    val reasons = mutableListOf<String>()
    var score = 0

    if (deviceClass != null && deviceClass in earbudDeviceClasses) {
      score += 4
      reasons += "Audio headset/headphone CoD"
    } else if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
      score += 2
      reasons += "Audio/Video major class"
    }

    val allUuidsLower = (advertisedUuids + sdpUuids).map { it.lowercase(Locale.ROOT) }
    val matchingUuid =
      allUuidsLower.firstOrNull { uuid -> audioProfileUuidPrefixes.any { uuid.startsWith(it) } }
    if (matchingUuid != null) {
      score += 3
      reasons += "Audio/TWS service UUID"
    }

    val normalizedName = name?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (normalizedName.isNotEmpty()) {
      if (highSignalNameTokens.any { normalizedName.contains(it) }) {
        score += 2
        reasons += "Name matches earbud pattern"
      }
      if (weakChannelSuffixes.any { normalizedName.endsWith(it) }) {
        score += 2
        reasons += "L/R channel suffix"
      }
    }

    return when {
      score >= 4 ->
        EarbudClassification(
          isLikelyEarbud = true,
          confidenceLabel = "High Match",
          reason = reasons.joinToString(" + "),
        )
      score >= 2 ->
        EarbudClassification(
          isLikelyEarbud = true,
          confidenceLabel = "Possible Audio",
          reason = reasons.joinToString(" + "),
        )
      else -> EarbudClassification(isLikelyEarbud = false, confidenceLabel = null, reason = null)
    }
  }

  fun resolveCompanyHint(companyId: Int?): String? {
    if (companyId == null) return null
    val known = companyIds[companyId]
    val hex = "0x%04X".format(companyId)
    return if (known != null) "$known ($hex)" else "Bluetooth SIG $hex"
  }

  fun resolveUuidLabel(uuid: String): String? {
    val normalized = uuid.lowercase(Locale.ROOT)
    knownUuidLabels[normalized]?.let {
      return it
    }
    if (normalized.endsWith("-0000-1000-8000-00805f9b34fb") && normalized.length == 36) {
      val shortHex = normalized.substring(4, 8).uppercase(Locale.ROOT)
      return "SIG UUID 0x$shortHex"
    }
    return null
  }

  fun formatBytesToHex(bytes: ByteArray?): String {
    if (bytes == null || bytes.isEmpty()) return "<empty>"
    return bytes.joinToString(" ") { "%02X".format(it) }
  }

  fun formatBytesToPrintableUtf8(bytes: ByteArray?): String? {
    if (bytes == null || bytes.isEmpty()) return null
    val printableCount = bytes.count { b -> val c = b.toInt() and 0xFF; c in 32..126 }
    if (printableCount * 2 < bytes.size) return null
    return bytes
      .map { b ->
        val c = b.toInt() and 0xFF
        if (c in 32..126) c.toChar() else '.'
      }
      .joinToString("")
  }
}
