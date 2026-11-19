package com.example.domain.model

enum class GattConnectionState(val label: String) {
  DISCONNECTED("Disconnected"),
  CONNECTING("Connecting..."),
  CONNECTED("Connected"),
  DISCOVERING_SERVICES("Discovering Services..."),
  READY("Services Discovered"),
  ERROR("Connection Error"),
}

enum class CharacteristicProperty(val badge: String) {
  READ("READ"),
  WRITE("WRITE"),
  WRITE_NO_RESPONSE("WRITE_NR"),
  NOTIFY("NOTIFY"),
  INDICATE("INDICATE"),
  SIGNED_WRITE("SIGNED"),
  BROADCAST("BCAST"),
}

data class GattCharacteristicInfo(
  val serviceUuid: String,
  val uuid: String,
  val knownName: String?,
  val properties: List<CharacteristicProperty>,
  val descriptors: List<String>,
  val lastReadHex: String? = null,
  val lastReadUtf8: String? = null,
  val lastReadTimestampMs: Long? = null,
  val isReading: Boolean = false,
  val readError: String? = null,
) {
  val isReadable: Boolean
    get() = CharacteristicProperty.READ in properties
}

data class GattServiceInfo(
  val uuid: String,
  val knownName: String?,
  val isPrimary: Boolean,
  val characteristics: List<GattCharacteristicInfo>,
)

data class GattInspectionSession(
  val deviceAddress: String,
  val deviceName: String,
  val connectionState: GattConnectionState = GattConnectionState.DISCONNECTED,
  val statusMessage: String? = null,
  val services: List<GattServiceInfo> = emptyList(),
  val isClassicOnlyWarning: Boolean = false,
)
