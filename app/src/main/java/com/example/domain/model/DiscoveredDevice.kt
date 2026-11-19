package com.example.domain.model

enum class TransportType(val label: String) {
  BLE("BLE"),
  CLASSIC("Classic"),
  DUAL("Dual"),
  UNKNOWN("Unknown"),
}

enum class BondState(val label: String) {
  NONE("Unpaired"),
  BONDING("Pairing..."),
  BONDED("Bonded"),
}

enum class ScanMode(val label: String) {
  DUAL("BLE + Classic"),
  BLE_ONLY("BLE Only"),
  CLASSIC_ONLY("Classic Only"),
}

enum class DeviceFilter(val label: String) {
  EARBUDS("Likely Earbuds"),
  ALL("All Devices"),
  BONDED("Bonded"),
  BLE("BLE"),
  CLASSIC("Classic"),
}

enum class EarbudRole(val label: String, val shortBadge: String) {
  NONE("Unassigned", "—"),
  LEFT("Left Earbud", "L"),
  RIGHT("Right Earbud", "R"),
}

data class EarbudClassification(
  val isLikelyEarbud: Boolean,
  val confidenceLabel: String?,
  val reason: String?,
)

data class DiscoveredDevice(
  val address: String,
  val name: String?,
  val systemAlias: String? = null,
  val customAlias: String? = null,
  val assignedRole: EarbudRole = EarbudRole.NONE,
  val rssi: Int?,
  val transportType: TransportType,
  val majorClass: Int?,
  val deviceClass: Int?,
  val deviceClassLabel: String,
  val bondState: BondState,
  val isLikelyEarbud: Boolean,
  val earbudConfidenceLabel: String?,
  val earbudReason: String?,
  val advertisedCompanyId: Int?,
  val advertisedCompanyHint: String?,
  val advertisedServiceUuids: List<String>,
  val sdpUuids: List<String> = emptyList(),
  val isBondedSnapshot: Boolean = false,
  val lastSeenMs: Long,
) {
  val displayName: String
    get() =
      customAlias?.takeIf { it.isNotBlank() }
        ?: systemAlias?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Unnamed Device"

  val hardwareName: String?
    get() = name?.takeIf { it.isNotBlank() }

  val signalStrengthLabel: String
    get() = rssi?.let { "$it dBm" } ?: "N/A"
}
