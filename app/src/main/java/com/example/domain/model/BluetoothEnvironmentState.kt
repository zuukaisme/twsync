package com.example.domain.model

data class BluetoothEnvironmentState(
  val isBluetoothSupported: Boolean = true,
  val isBleSupported: Boolean = true,
  val isBluetoothEnabled: Boolean = false,
  val hasScanPermission: Boolean = false,
  val hasConnectPermission: Boolean = false,
  val missingPermissions: List<String> = emptyList(),
  val requiresLegacyLocationPermission: Boolean = false,
  val androidVersionLabel: String = "",
) {
  val isReadyToScan: Boolean
    get() = isBluetoothSupported && isBluetoothEnabled && hasScanPermission && hasConnectPermission
}
