package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.AudioChannelTest
import com.example.data.audio.OsAudioController
import com.example.data.audio.OsAudioState
import com.example.data.bluetooth.BluetoothDiscoveryManager
import com.example.data.bluetooth.DiagnosticLogger
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.TwsPreferences
import com.example.domain.model.BluetoothEnvironmentState
import com.example.domain.model.BondState
import com.example.domain.model.DeviceFilter
import com.example.domain.model.DiagnosticLogEntry
import com.example.domain.model.DiscoveredDevice
import com.example.domain.model.EarbudRole
import com.example.domain.model.GattInspectionSession
import com.example.domain.model.LogFilter
import com.example.domain.model.LogLevel
import com.example.domain.model.ScanMode
import com.example.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TwsBridgeViewModel(application: Application) : AndroidViewModel(application) {
  private val logger = DiagnosticLogger()
  private val preferences = TwsPreferences(application)
  private val discoveryManager = BluetoothDiscoveryManager(application, logger)
  private val audioController = OsAudioController(application, logger)

  val devModeEnabled: StateFlow<Boolean> = preferences.devModeEnabled
  val themeMode: StateFlow<ThemeMode> = preferences.themeMode
  val environmentState: StateFlow<BluetoothEnvironmentState> = discoveryManager.environmentState
  val localAdapterName: StateFlow<String?> = discoveryManager.localAdapterName
  val audioState: StateFlow<OsAudioState> = audioController.audioState
  val isScanning: StateFlow<Boolean> = discoveryManager.isScanning
  val inspectionSession: StateFlow<GattInspectionSession?> = discoveryManager.inspectionSession

  private val _scanMode = MutableStateFlow(ScanMode.DUAL)
  val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

  private val _deviceFilter = MutableStateFlow(DeviceFilter.ALL)
  val deviceFilter: StateFlow<DeviceFilter> = _deviceFilter.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedDeviceAddress = MutableStateFlow<String?>(null)
  val selectedDeviceAddress: StateFlow<String?> = _selectedDeviceAddress.asStateFlow()

  private val _logFilter = MutableStateFlow(LogFilter.ALL)
  val logFilter: StateFlow<LogFilter> = _logFilter.asStateFlow()

  private val _uiBannerMessage = MutableStateFlow<String?>(null)
  val uiBannerMessage: StateFlow<String?> = _uiBannerMessage.asStateFlow()

  val allDevices: StateFlow<List<DiscoveredDevice>> =
    combine(
        discoveryManager.devices,
        preferences.customAliases,
        preferences.earbudRoles,
      ) { devices, aliases, roles ->
        devices.map { device ->
          device.copy(
            customAlias = aliases[device.address],
            assignedRole = roles[device.address] ?: EarbudRole.NONE,
          )
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val allDevicesCount: StateFlow<Int> =
    allDevices
      .map { it.size }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  val bondedOrAudioDevices: StateFlow<List<DiscoveredDevice>> =
    allDevices
      .map { devices ->
        devices.filter {
          it.bondState == BondState.BONDED ||
            it.assignedRole != EarbudRole.NONE ||
            it.isLikelyEarbud
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val filteredDevices: StateFlow<List<DiscoveredDevice>> =
    combine(allDevices, _deviceFilter, _searchQuery) { devices, filter, query ->
        val normalizedQuery = query.trim().lowercase()
        devices.filter { device ->
          val matchesFilter =
            when (filter) {
              DeviceFilter.EARBUDS -> device.isLikelyEarbud
              DeviceFilter.ALL -> true
              DeviceFilter.BONDED -> device.bondState == BondState.BONDED
              DeviceFilter.BLE ->
                device.transportType == TransportType.BLE ||
                  device.transportType == TransportType.DUAL
              DeviceFilter.CLASSIC ->
                device.transportType == TransportType.CLASSIC ||
                  device.transportType == TransportType.DUAL
            }
          val matchesQuery =
            normalizedQuery.isEmpty() ||
              device.displayName.lowercase().contains(normalizedQuery) ||
              (device.hardwareName?.lowercase()?.contains(normalizedQuery) == true) ||
              device.address.lowercase().contains(normalizedQuery) ||
              (device.advertisedCompanyHint?.lowercase()?.contains(normalizedQuery) == true)
          matchesFilter && matchesQuery
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val selectedDevice: StateFlow<DiscoveredDevice?> =
    combine(allDevices, _selectedDeviceAddress) { devices, address ->
        if (address == null) null else devices.firstOrNull { it.address == address }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val filteredLogs: StateFlow<List<DiagnosticLogEntry>> =
    combine(logger.entries, _logFilter) { entries, filter ->
        when (filter) {
          LogFilter.ALL -> entries
          LogFilter.DISCOVERY -> entries.filter { it.level == LogLevel.DISCOVERY }
          LogFilter.GATT -> entries.filter { it.level == LogLevel.GATT }
          LogFilter.ISSUES ->
            entries.filter { it.level == LogLevel.WARNING || it.level == LogLevel.ERROR }
        }.asReversed()
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  init {
    val env = environmentState.value
    logger.log(
      LogLevel.INFO,
      "INIT",
      "TWS Bridge initialized on ${env.androidVersionLabel} (DevMode=${devModeEnabled.value})",
      "BT Supported=${env.isBluetoothSupported}, BLE Supported=${env.isBleSupported}, BT Enabled=${env.isBluetoothEnabled}",
    )
  }

  fun setDevModeEnabled(enabled: Boolean) {
    preferences.setDevModeEnabled(enabled)
    logger.log(
      LogLevel.INFO,
      "MODE",
      if (enabled) "Developer Mode enabled" else "Developer Mode disabled (OS Hub view)",
    )
    if (!enabled && isScanning.value) {
      discoveryManager.stopScan()
    }
  }

  fun toggleThemeMode(currentlyDark: Boolean) {
    val next = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
    preferences.setThemeMode(next)
  }

  fun refreshEnvironment() {
    discoveryManager.refreshEnvironment()
    audioController.refreshAudioState()
  }

  fun setMediaVolume(level: Int) = audioController.setMediaVolume(level)

  fun toggleMediaMute() = audioController.toggleMediaMute()

  fun setCallVolume(level: Int) = audioController.setCallVolume(level)

  fun setRingVolume(level: Int) = audioController.setRingVolume(level)

  fun playChannelTest(test: AudioChannelTest) = audioController.playChannelTest(test)

  fun renameLocalOsBluetoothName(newName: String) {
    val ok = discoveryManager.renameLocalAdapter(newName)
    _uiBannerMessage.value =
      if (ok) {
        "OS Bluetooth name updated to \"${newName.trim()}\""
      } else {
        "Could not change OS Bluetooth name (check Bluetooth is ON & permission granted)."
      }
  }

  fun renameDevice(address: String, newAlias: String) {
    preferences.setCustomAlias(address, newAlias)
    val osUpdated = discoveryManager.renameRemoteDevice(address, newAlias)
    val label = newAlias.trim()
    _uiBannerMessage.value =
      when {
        label.isEmpty() -> "Cleared custom device name"
        osUpdated -> "Renamed device in OS Bluetooth & TWS Bridge to \"$label\""
        else -> "Saved TWS Bridge label \"$label\" (OS alias requires Android 11+ & bonded device)"
      }
  }

  fun assignEarbudRole(address: String, role: EarbudRole) {
    preferences.setEarbudRole(address, role)
    logger.log(LogLevel.INFO, "OS_DEVICES", "Assigned $address role -> ${role.label}")
  }

  fun dismissBanner() {
    _uiBannerMessage.value = null
  }

  fun setScanMode(mode: ScanMode) {
    _scanMode.value = mode
  }

  fun setDeviceFilter(filter: DeviceFilter) {
    _deviceFilter.value = filter
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setLogFilter(filter: LogFilter) {
    _logFilter.value = filter
  }

  fun toggleScan() {
    if (isScanning.value) {
      discoveryManager.stopScan()
    } else {
      discoveryManager.startScan(_scanMode.value)
    }
  }

  fun selectDevice(address: String) {
    _selectedDeviceAddress.value = address
    discoveryManager.selectDeviceForInspection(address)
  }

  fun connectSelectedGatt() {
    val address = _selectedDeviceAddress.value ?: return
    discoveryManager.connectGatt(address)
  }

  fun disconnectGatt() {
    discoveryManager.disconnectGatt()
  }

  fun probeSelectedSdp() {
    val address = _selectedDeviceAddress.value ?: return
    discoveryManager.probeClassicSdp(address)
  }

  fun readCharacteristic(serviceUuid: String, characteristicUuid: String) {
    discoveryManager.readCharacteristic(serviceUuid, characteristicUuid)
  }

  fun clearLogs() {
    logger.clear()
    logger.log(LogLevel.INFO, "LOGS", "Diagnostic log buffer cleared")
  }

  fun exportLogsPlainText(): String = logger.exportPlainText()

  fun logPermissionOutcome(grantedMap: Map<String, Boolean>) {
    refreshEnvironment()
    val denied = grantedMap.filterValues { !it }.keys
    if (denied.isEmpty()) {
      logger.log(LogLevel.INFO, "PERMISSIONS", "All required Bluetooth permissions granted")
      discoveryManager.loadBondedDevices()
    } else {
      logger.log(
        LogLevel.WARNING,
        "PERMISSIONS",
        "Denied permissions: ${denied.joinToString()}",
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    audioController.release()
    discoveryManager.release()
  }
}
