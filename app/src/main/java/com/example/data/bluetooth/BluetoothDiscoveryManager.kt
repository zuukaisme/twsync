package com.example.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.domain.model.BluetoothEnvironmentState
import com.example.domain.model.BondState
import com.example.domain.model.CharacteristicProperty
import com.example.domain.model.DiscoveredDevice
import com.example.domain.model.GattCharacteristicInfo
import com.example.domain.model.GattConnectionState
import com.example.domain.model.GattInspectionSession
import com.example.domain.model.GattServiceInfo
import com.example.domain.model.LogLevel
import com.example.domain.model.ScanMode
import com.example.domain.model.TransportType
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BluetoothDiscoveryManager(
  private val context: Context,
  private val logger: DiagnosticLogger,
) {
  private val appContext = context.applicationContext
  private val bluetoothManager: BluetoothManager? =
    appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  private val deviceMap = ConcurrentHashMap<String, DiscoveredDevice>()
  private val rawDeviceMap = ConcurrentHashMap<String, BluetoothDevice>()

  private val _environmentState = MutableStateFlow(readEnvironmentState())
  val environmentState: StateFlow<BluetoothEnvironmentState> = _environmentState.asStateFlow()

  private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _isBleScanning = MutableStateFlow(false)
  private val _isClassicScanning = MutableStateFlow(false)

  private val _inspectionSession = MutableStateFlow<GattInspectionSession?>(null)
  val inspectionSession: StateFlow<GattInspectionSession?> = _inspectionSession.asStateFlow()

  private val _localAdapterName = MutableStateFlow<String?>(null)
  val localAdapterName: StateFlow<String?> = _localAdapterName.asStateFlow()

  private var activeGatt: BluetoothGatt? = null
  private val pendingReadQueue = ArrayDeque<BluetoothGattCharacteristic>()
  private var isGattOperationInFlight = false
  private var receiverRegistered = false

  private val leScanCallback =
    object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        handleBleScanResult(result)
      }

      override fun onBatchScanResults(results: MutableList<ScanResult>) {
        results.forEach { handleBleScanResult(it) }
      }

      override fun onScanFailed(errorCode: Int) {
        _isBleScanning.value = false
        updateCombinedScanningFlag()
        logger.log(
          LogLevel.ERROR,
          "BLE_SCAN",
          "BLE scan failed with error code $errorCode",
          explainScanError(errorCode),
        )
      }
    }

  private val classicReceiver =
    object : BroadcastReceiver() {
      @SuppressLint("MissingPermission")
      override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
          BluetoothAdapter.ACTION_STATE_CHANGED -> {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            refreshEnvironment()
            logger.log(
              LogLevel.INFO,
              "ADAPTER",
              "Bluetooth adapter state changed: ${adapterStateName(state)}",
            )
            if (state == BluetoothAdapter.STATE_OFF) {
              stopScan()
            } else if (state == BluetoothAdapter.STATE_ON) {
              loadBondedDevices()
            }
          }

          BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
            _isClassicScanning.value = true
            updateCombinedScanningFlag()
            logger.log(LogLevel.DISCOVERY, "CLASSIC", "Classic BR/EDR discovery started")
          }

          BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
            _isClassicScanning.value = false
            updateCombinedScanningFlag()
            logger.log(LogLevel.DISCOVERY, "CLASSIC", "Classic BR/EDR discovery finished")
          }

          BluetoothDevice.ACTION_FOUND -> {
            val device =
              IntentCompat.getParcelableExtra(
                intent,
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java,
              ) ?: return
            val rssiExtra =
              intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).let {
                if (it == Short.MIN_VALUE) null else it.toInt()
              }
            val deviceName =
              if (BluetoothPermissionHelper.hasConnectPermission(appContext)) {
                safeGetDeviceName(device)
              } else {
                null
              }
            upsertDiscoveredDevice(
              device = device,
              overrideName = deviceName,
              rssi = rssiExtra,
              advertisedUuids = emptyList(),
              companyId = null,
              fromClassicDiscovery = true,
              isBondedSnapshot = false,
            )
          }

          BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
            val device =
              IntentCompat.getParcelableExtra(
                intent,
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java,
              ) ?: return
            val bondState =
              intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            logger.log(
              LogLevel.INFO,
              "BOND",
              "Bond state changed for ${device.address}: ${mapBondState(bondState).label}",
            )
            upsertDiscoveredDevice(
              device = device,
              overrideName = safeGetDeviceName(device),
              rssi = null,
              advertisedUuids = emptyList(),
              companyId = null,
              fromClassicDiscovery = false,
              isBondedSnapshot = bondState == BluetoothDevice.BOND_BONDED,
            )
          }

          BluetoothDevice.ACTION_UUID -> {
            val device =
              IntentCompat.getParcelableExtra(
                intent,
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java,
              ) ?: return
            val parcelables =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(
                  BluetoothDevice.EXTRA_UUID,
                  ParcelUuid::class.java,
                )
              } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
              }
            val uuids =
              parcelables
                ?.mapNotNull { (it as? ParcelUuid)?.uuid?.toString() }
                ?.distinct()
                .orEmpty()
            logger.log(
              LogLevel.DISCOVERY,
              "SDP",
              "SDP UUID probe returned ${uuids.size} UUID(s) for ${device.address}",
              uuids.joinToString(", ") { u -> EarbudHeuristics.resolveUuidLabel(u) ?: u },
            )
            updateDeviceSdpUuids(device.address, uuids)
          }
        }
      }
    }

  private val gattCallback =
    object : BluetoothGattCallback() {
      @SuppressLint("MissingPermission")
      override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val address = gatt.device?.address ?: return
        logger.log(
          LogLevel.GATT,
          "GATT_STATE",
          "Connection change for $address: status=$status (${explainGattStatus(status)}), newState=${profileStateName(newState)}",
        )

        if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
          isGattOperationInFlight = false
          pendingReadQueue.clear()
          val current = deviceMap[address]
          val classicWarning = current?.transportType == TransportType.CLASSIC
          _inspectionSession.update { session ->
            if (session?.deviceAddress == address) {
              session.copy(
                connectionState = GattConnectionState.ERROR,
                statusMessage =
                  "Disconnected with GATT status $status (${explainGattStatus(status)})." +
                    if (classicWarning) {
                      " Device appears Classic-only (BR/EDR) and may not expose a BLE GATT server."
                    } else {
                      ""
                    },
                isClassicOnlyWarning = classicWarning,
              )
            } else {
              session
            }
          }
          safeCloseGatt(gatt)
          return
        }

        when (newState) {
          BluetoothProfile.STATE_CONNECTED -> {
            _inspectionSession.update { session ->
              if (session?.deviceAddress == address) {
                session.copy(
                  connectionState = GattConnectionState.DISCOVERING_SERVICES,
                  statusMessage = "Connected to GATT server. Discovering services...",
                )
              } else {
                session
              }
            }
            try {
              val started = gatt.discoverServices()
              if (!started) {
                logger.log(
                  LogLevel.ERROR,
                  "GATT",
                  "gatt.discoverServices() returned false for $address",
                )
              }
            } catch (se: SecurityException) {
              logger.log(LogLevel.ERROR, "GATT", "SecurityException discovering services", se.message)
            }
          }

          BluetoothProfile.STATE_DISCONNECTED -> {
            isGattOperationInFlight = false
            pendingReadQueue.clear()
            _inspectionSession.update { session ->
              if (session?.deviceAddress == address) {
                session.copy(
                  connectionState = GattConnectionState.DISCONNECTED,
                  statusMessage = "Disconnected from GATT server.",
                )
              } else {
                session
              }
            }
            safeCloseGatt(gatt)
          }
        }
      }

      override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        val address = gatt.device?.address ?: return
        if (status != BluetoothGatt.GATT_SUCCESS) {
          logger.log(
            LogLevel.ERROR,
            "GATT",
            "Service discovery failed for $address with status=$status",
          )
          _inspectionSession.update { session ->
            if (session?.deviceAddress == address) {
              session.copy(
                connectionState = GattConnectionState.ERROR,
                statusMessage = "Service discovery failed (status $status).",
              )
            } else {
              session
            }
          }
          return
        }

        val mappedServices =
          gatt.services.map { service ->
            val svcUuid = service.uuid.toString()
            val characteristics =
              service.characteristics.map { char ->
                val charUuid = char.uuid.toString()
                GattCharacteristicInfo(
                  serviceUuid = svcUuid,
                  uuid = charUuid,
                  knownName = EarbudHeuristics.resolveUuidLabel(charUuid),
                  properties = decodeCharacteristicProperties(char.properties),
                  descriptors = char.descriptors.map { it.uuid.toString() },
                )
              }
            GattServiceInfo(
              uuid = svcUuid,
              knownName = EarbudHeuristics.resolveUuidLabel(svcUuid),
              isPrimary =
                service.type == android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY,
              characteristics = characteristics,
            )
          }

        logger.log(
          LogLevel.GATT,
          "GATT_DISCOVERY",
          "Discovered ${mappedServices.size} service(s) on $address",
          mappedServices.joinToString("; ") { it.knownName ?: it.uuid },
        )

        _inspectionSession.update { session ->
          if (session?.deviceAddress == address) {
            session.copy(
              connectionState = GattConnectionState.READY,
              statusMessage =
                if (mappedServices.isEmpty()) {
                  "Connected, but device exposed 0 GATT services."
                } else {
                  "Discovered ${mappedServices.size} service(s). Tap Read on readable characteristics."
                },
              services = mappedServices,
            )
          } else {
            session
          }
        }
      }

      @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
      override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int,
      ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
          handleCharacteristicReadResult(gatt, characteristic, characteristic.value, status)
        }
      }

      override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
      ) {
        handleCharacteristicReadResult(gatt, characteristic, value, status)
      }
    }

  init {
    registerSystemReceiver()
    refreshEnvironment()
    loadBondedDevices()
  }

  private fun registerSystemReceiver() {
    if (receiverRegistered) return
    val filter =
      IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        addAction(BluetoothDevice.ACTION_UUID)
      }
    ContextCompat.registerReceiver(appContext, classicReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    receiverRegistered = true
  }

  fun refreshEnvironment() {
    val state = readEnvironmentState()
    _environmentState.value = state
    _localAdapterName.value = readLocalAdapterName()
    if (state.hasConnectPermission && state.isBluetoothEnabled) {
      loadBondedDevices()
    }
  }

  @SuppressLint("MissingPermission")
  private fun readLocalAdapterName(): String? {
    val adapter = bluetoothAdapter ?: return null
    if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) return null
    return try {
      adapter.name
    } catch (_: SecurityException) {
      null
    }
  }

  @SuppressLint("MissingPermission")
  fun renameLocalAdapter(newName: String): Boolean {
    val trimmed = newName.trim()
    if (trimmed.isEmpty()) return false
    val adapter = bluetoothAdapter ?: return false
    if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
      logger.log(LogLevel.WARNING, "OS_BT", "Missing BLUETOOTH_CONNECT permission to rename adapter")
      return false
    }
    return try {
      val updated = adapter.setName(trimmed)
      if (updated) {
        _localAdapterName.value = trimmed
        logger.log(LogLevel.INFO, "OS_BT", "Renamed local Bluetooth adapter to \"$trimmed\"")
      } else {
        logger.log(
          LogLevel.WARNING,
          "OS_BT",
          "BluetoothAdapter.setName returned false (Bluetooth may be off or restricted by OEM)",
        )
      }
      updated
    } catch (se: SecurityException) {
      logger.log(LogLevel.ERROR, "OS_BT", "Permission denied renaming Bluetooth adapter", se.message)
      false
    }
  }

  @SuppressLint("MissingPermission")
  fun renameRemoteDevice(address: String, newAlias: String): Boolean {
    val trimmed = newAlias.trim()
    val device = rawDeviceMap[address]
    var osAliasUpdated = false
    if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
      BluetoothPermissionHelper.hasConnectPermission(appContext)
    ) {
      try {
        val statusCode = device.setAlias(trimmed.ifEmpty { null })
        osAliasUpdated = statusCode == 0
        logger.log(
          if (osAliasUpdated) LogLevel.INFO else LogLevel.WARNING,
          "OS_BT",
          "BluetoothDevice.setAlias($address -> \"$trimmed\") returned status=$statusCode",
        )
      } catch (se: SecurityException) {
        logger.log(LogLevel.WARNING, "OS_BT", "Permission denied setting OS device alias", se.message)
      }
    }
    deviceMap[address]?.let { current ->
      deviceMap[address] =
        current.copy(
          systemAlias = if (osAliasUpdated) trimmed.ifEmpty { null } else current.systemAlias,
        )
      emitSortedDevices()
    }
    return osAliasUpdated
  }

  private fun readEnvironmentState(): BluetoothEnvironmentState {
    val pm = appContext.packageManager
    val bleSupported = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val btSupported = bluetoothAdapter != null
    val enabled =
      try {
        bluetoothAdapter?.isEnabled == true
      } catch (_: SecurityException) {
        false
      }
    return BluetoothEnvironmentState(
      isBluetoothSupported = btSupported,
      isBleSupported = bleSupported,
      isBluetoothEnabled = enabled,
      hasScanPermission = BluetoothPermissionHelper.hasScanPermission(appContext),
      hasConnectPermission = BluetoothPermissionHelper.hasConnectPermission(appContext),
      missingPermissions = BluetoothPermissionHelper.missingPermissions(appContext),
      requiresLegacyLocationPermission =
        BluetoothPermissionHelper.requiresLegacyLocationPermission(),
      androidVersionLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )
  }

  @SuppressLint("MissingPermission")
  fun loadBondedDevices() {
    val adapter = bluetoothAdapter ?: return
    if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) return
    try {
      val bonded = adapter.bondedDevices.orEmpty()
      bonded.forEach { device ->
        upsertDiscoveredDevice(
          device = device,
          overrideName = safeGetDeviceName(device),
          rssi = null,
          advertisedUuids = emptyList(),
          companyId = null,
          fromClassicDiscovery = false,
          isBondedSnapshot = true,
        )
      }
      if (bonded.isNotEmpty()) {
        logger.log(
          LogLevel.INFO,
          "BONDED",
          "Loaded ${bonded.size} system-bonded Bluetooth device(s)",
        )
      }
    } catch (se: SecurityException) {
      logger.log(LogLevel.WARNING, "BONDED", "Unable to read bonded devices", se.message)
    }
  }

  @SuppressLint("MissingPermission")
  fun startScan(mode: ScanMode) {
    refreshEnvironment()
    val env = _environmentState.value
    if (!env.isBluetoothSupported) {
      logger.log(LogLevel.ERROR, "SCAN", "Bluetooth adapter is not supported on this device")
      return
    }
    if (!env.isBluetoothEnabled) {
      logger.log(LogLevel.WARNING, "SCAN", "Cannot scan while Bluetooth radio is off")
      return
    }
    if (!env.hasScanPermission || !env.hasConnectPermission) {
      logger.log(
        LogLevel.WARNING,
        "PERMISSIONS",
        "Missing required permissions: ${env.missingPermissions.joinToString()}",
      )
      return
    }

    stopScan()
    loadBondedDevices()

    val adapter = bluetoothAdapter ?: return

    if (mode == ScanMode.DUAL || mode == ScanMode.BLE_ONLY) {
      val scanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
      if (scanner != null && env.isBleSupported) {
        try {
          val settings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
          scanner.startScan(null, settings, leScanCallback)
          _isBleScanning.value = true
          logger.log(LogLevel.DISCOVERY, "BLE_SCAN", "Started low-latency BLE scan")
        } catch (se: SecurityException) {
          logger.log(LogLevel.ERROR, "BLE_SCAN", "Permission denied starting BLE scan", se.message)
        } catch (e: IllegalStateException) {
          logger.log(LogLevel.ERROR, "BLE_SCAN", "BLE scanner unavailable", e.message)
        }
      } else {
        logger.log(LogLevel.WARNING, "BLE_SCAN", "BLE scanner unavailable on this adapter")
      }
    }

    if (mode == ScanMode.DUAL || mode == ScanMode.CLASSIC_ONLY) {
      try {
        if (adapter.isDiscovering) {
          adapter.cancelDiscovery()
        }
        val started = adapter.startDiscovery()
        if (started) {
          _isClassicScanning.value = true
          logger.log(
            LogLevel.DISCOVERY,
            "CLASSIC",
            "Requested Classic BR/EDR inquiry (approx. 12s window)",
          )
        } else {
          logger.log(
            LogLevel.WARNING,
            "CLASSIC",
            "BluetoothAdapter.startDiscovery() returned false",
          )
        }
      } catch (se: SecurityException) {
        logger.log(
          LogLevel.ERROR,
          "CLASSIC",
          "Permission denied starting Classic discovery",
          se.message,
        )
      }
    }

    updateCombinedScanningFlag()
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    val adapter = bluetoothAdapter ?: return
    if (_isBleScanning.value && BluetoothPermissionHelper.hasScanPermission(appContext)) {
      try {
        adapter.bluetoothLeScanner?.stopScan(leScanCallback)
        logger.log(LogLevel.DISCOVERY, "BLE_SCAN", "Stopped BLE scan")
      } catch (_: Exception) {
      }
    }
    _isBleScanning.value = false

    if (BluetoothPermissionHelper.hasScanPermission(appContext)) {
      try {
        if (adapter.isDiscovering) {
          adapter.cancelDiscovery()
          logger.log(LogLevel.DISCOVERY, "CLASSIC", "Cancelled Classic discovery")
        }
      } catch (_: Exception) {
      }
    }
    _isClassicScanning.value = false
    updateCombinedScanningFlag()
  }

  fun selectDeviceForInspection(address: String) {
    val existing = deviceMap[address] ?: return
    val currentSession = _inspectionSession.value
    if (currentSession != null && currentSession.deviceAddress != address) {
      disconnectGatt()
    }
    if (_inspectionSession.value?.deviceAddress != address) {
      _inspectionSession.value =
        GattInspectionSession(
          deviceAddress = existing.address,
          deviceName = existing.displayName,
          connectionState = GattConnectionState.DISCONNECTED,
          statusMessage =
            if (existing.transportType == TransportType.CLASSIC) {
              "Classic BR/EDR device detected. You can attempt GATT or probe Classic SDP UUIDs."
            } else {
              "Ready to open a BLE GATT client connection."
            },
          isClassicOnlyWarning = existing.transportType == TransportType.CLASSIC,
        )
    }
  }

  @SuppressLint("MissingPermission")
  fun connectGatt(address: String) {
    if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
      logger.log(LogLevel.WARNING, "GATT", "Missing BLUETOOTH_CONNECT permission")
      return
    }
    val device = rawDeviceMap[address] ?: run {
      logger.log(LogLevel.ERROR, "GATT", "BluetoothDevice handle not found for $address")
      return
    }

    // Stop active discovery before connecting to prevent radio contention (common cause of status 133)
    if (_isScanning.value) {
      stopScan()
    }
    disconnectGatt()

    val knownDevice = deviceMap[address]
    _inspectionSession.value =
      GattInspectionSession(
        deviceAddress = address,
        deviceName = knownDevice?.displayName ?: address,
        connectionState = GattConnectionState.CONNECTING,
        statusMessage = "Opening GATT client (TRANSPORT_LE)...",
        isClassicOnlyWarning = knownDevice?.transportType == TransportType.CLASSIC,
      )

    logger.log(
      LogLevel.GATT,
      "GATT",
      "Connecting GATT to ${knownDevice?.displayName ?: address} ($address) using TRANSPORT_LE",
    )

    try {
      activeGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    } catch (se: SecurityException) {
      logger.log(LogLevel.ERROR, "GATT", "SecurityException in connectGatt", se.message)
      _inspectionSession.update {
        it?.copy(
          connectionState = GattConnectionState.ERROR,
          statusMessage = "Permission denied while connecting GATT.",
        )
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun disconnectGatt() {
    pendingReadQueue.clear()
    isGattOperationInFlight = false
    val gatt = activeGatt ?: return
    activeGatt = null
    val address = gatt.device?.address
    try {
      gatt.disconnect()
      gatt.close()
      if (address != null) {
        logger.log(LogLevel.GATT, "GATT", "Closed GATT connection for $address")
      }
    } catch (_: Exception) {
    }
    _inspectionSession.update { session ->
      session?.copy(
        connectionState = GattConnectionState.DISCONNECTED,
        statusMessage = "Disconnected.",
      )
    }
  }

  @SuppressLint("MissingPermission")
  fun probeClassicSdp(address: String) {
    if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
      logger.log(LogLevel.WARNING, "SDP", "Missing BLUETOOTH_CONNECT permission")
      return
    }
    val device = rawDeviceMap[address] ?: return
    try {
      val cached =
        device.uuids?.mapNotNull { it?.uuid?.toString() }?.distinct().orEmpty()
      if (cached.isNotEmpty()) {
        updateDeviceSdpUuids(address, cached)
      }
      val triggered = device.fetchUuidsWithSdp()
      logger.log(
        LogLevel.DISCOVERY,
        "SDP",
        "Triggered Classic SDP UUID fetch for $address (started=$triggered, cached=${cached.size})",
      )
    } catch (se: SecurityException) {
      logger.log(LogLevel.ERROR, "SDP", "SecurityException probing SDP UUIDs", se.message)
    }
  }

  fun readStandardDeviceInformation() {
    val gatt = activeGatt
    if (gatt == null) {
      logger.log(LogLevel.WARNING, "DEVICE_INFO", "Connect to a GATT device before probing standard device information")
      return
    }

    val probes = listOf(
      "0000180a-0000-1000-8000-00805f9b34fb" to listOf(
        "00002a29-0000-1000-8000-00805f9b34fb",
        "00002a24-0000-1000-8000-00805f9b34fb",
        "00002a25-0000-1000-8000-00805f9b34fb",
        "00002a26-0000-1000-8000-00805f9b34fb",
        "00002a27-0000-1000-8000-00805f9b34fb",
        "00002a28-0000-1000-8000-00805f9b34fb",
        "00002a50-0000-1000-8000-00805f9b34fb",
      ),
      "0000180f-0000-1000-8000-00805f9b34fb" to listOf(
        "00002a19-0000-1000-8000-00805f9b34fb",
      ),
    )

    var queued = 0
    probes.forEach { (serviceUuid, characteristicUuids) ->
      val service = try {
        gatt.getService(UUID.fromString(serviceUuid))
      } catch (_: IllegalArgumentException) {
        null
      }
      characteristicUuids.forEach { characteristicUuid ->
        val characteristic = service?.getCharacteristic(UUID.fromString(characteristicUuid))
        if (characteristic != null &&
          (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
        ) {
          readCharacteristic(serviceUuid, characteristicUuid)
          queued++
        }
      }
    }

    logger.log(
      if (queued > 0) LogLevel.INFO else LogLevel.WARNING,
      "DEVICE_INFO",
      if (queued > 0) "Queued $queued standard Device Information / Battery reads" else "No readable standard Device Information or Battery characteristics were found",
      "Reads are optional; many earbuds expose neither standard service.",
    )
  }

  fun readCharacteristic(serviceUuid: String, characteristicUuid: String) {
    val gatt = activeGatt ?: run {
      logger.log(LogLevel.WARNING, "GATT_READ", "No active GATT connection")
      return
    }
    val service = gatt.getService(UUID.fromString(serviceUuid)) ?: return
    val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid)) ?: return
    if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
      logger.log(LogLevel.WARNING, "GATT_READ", "Characteristic $characteristicUuid is not readable")
      return
    }

    updateCharacteristicState(serviceUuid, characteristicUuid) {
      it.copy(isReading = true, readError = null)
    }
    pendingReadQueue.addLast(characteristic)
    drainGattReadQueue(gatt)
  }

  @SuppressLint("MissingPermission")
  private fun drainGattReadQueue(gatt: BluetoothGatt) {
    if (isGattOperationInFlight) return
    val next = pendingReadQueue.pollFirst() ?: return
    if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) return
    isGattOperationInFlight = true
    try {
      val ok = gatt.readCharacteristic(next)
      if (!ok) {
        isGattOperationInFlight = false
        val svcUuid = next.service?.uuid?.toString().orEmpty()
        val charUuid = next.uuid.toString()
        logger.log(
          LogLevel.WARNING,
          "GATT_READ",
          "readCharacteristic returned false for $charUuid",
        )
        updateCharacteristicState(svcUuid, charUuid) {
          it.copy(isReading = false, readError = "Read rejected by Android stack")
        }
        drainGattReadQueue(gatt)
      }
    } catch (se: SecurityException) {
      isGattOperationInFlight = false
      logger.log(LogLevel.ERROR, "GATT_READ", "SecurityException reading characteristic", se.message)
    }
  }

  private fun handleCharacteristicReadResult(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray?,
    status: Int,
  ) {
    isGattOperationInFlight = false
    val svcUuid = characteristic.service?.uuid?.toString().orEmpty()
    val charUuid = characteristic.uuid.toString()
    val hex = EarbudHeuristics.formatBytesToHex(value)
    val utf8 = EarbudHeuristics.formatBytesToPrintableUtf8(value)

    if (status == BluetoothGatt.GATT_SUCCESS) {
      logger.log(
        LogLevel.GATT,
        "GATT_READ",
        "Read ${EarbudHeuristics.resolveUuidLabel(charUuid) ?: charUuid}: $hex",
        utf8?.let { "UTF-8: \"$it\"" },
      )
      updateCharacteristicState(svcUuid, charUuid) {
        it.copy(
          isReading = false,
          readError = null,
          lastReadHex = hex,
          lastReadUtf8 = utf8,
          lastReadTimestampMs = System.currentTimeMillis(),
        )
      }
    } else {
      logger.log(
        LogLevel.WARNING,
        "GATT_READ",
        "Failed reading $charUuid (status=$status: ${explainGattStatus(status)})",
      )
      updateCharacteristicState(svcUuid, charUuid) {
        it.copy(
          isReading = false,
          readError = "GATT error $status (${explainGattStatus(status)})",
        )
      }
    }

    drainGattReadQueue(gatt)
  }

  private fun updateCharacteristicState(
    serviceUuid: String,
    characteristicUuid: String,
    transform: (GattCharacteristicInfo) -> GattCharacteristicInfo,
  ) {
    _inspectionSession.update { session ->
      session?.copy(
        services =
          session.services.map { service ->
            if (service.uuid == serviceUuid) {
              service.copy(
                characteristics =
                  service.characteristics.map { char ->
                    if (char.uuid == characteristicUuid) transform(char) else char
                  },
              )
            } else {
              service
            }
          },
      )
    }
  }

  @SuppressLint("MissingPermission")
  private fun handleBleScanResult(result: ScanResult) {
    val device = result.device ?: return
    val record = result.scanRecord
    val advertisedName =
      record?.deviceName ?: if (BluetoothPermissionHelper.hasConnectPermission(appContext)) {
        safeGetDeviceName(device)
      } else {
        null
      }
    val uuids = record?.serviceUuids?.mapNotNull { it?.uuid?.toString() }.orEmpty()
    var companyId: Int? = null
    val manufacturerPayloads = mutableListOf<String>()
    val mfgData = record?.manufacturerSpecificData
    if (mfgData != null && mfgData.size() > 0) {
      for (index in 0 until mfgData.size()) {
        val id = mfgData.keyAt(index)
        val payload = mfgData.valueAt(index)
        if (companyId == null) companyId = id
        manufacturerPayloads += "Company ID 0x%04X: %s".format(id, formatAdvertisementBytes(payload))
      }
    }
    val serviceData = record?.serviceData.orEmpty().map { (uuid, payload) ->
      "${uuid.uuid}: ${formatAdvertisementBytes(payload)}"
    }
    upsertDiscoveredDevice(
      device = device,
      overrideName = advertisedName,
      rssi = result.rssi,
      advertisedUuids = uuids,
      companyId = companyId,
      fromClassicDiscovery = false,
      isBondedSnapshot = false,
      rawAdvertisementHex = record?.bytes?.let(::formatAdvertisementBytes),
      manufacturerPayloads = manufacturerPayloads,
      serviceDataPayloads = serviceData,
      txPowerLevel = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
      advertisingFlags = record?.advertiseFlags?.takeIf { it >= 0 },
      connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else null,
    )
  }

  @SuppressLint("MissingPermission")
  private fun upsertDiscoveredDevice(
    device: BluetoothDevice,
    overrideName: String?,
    rssi: Int?,
    advertisedUuids: List<String>,
    companyId: Int?,
    fromClassicDiscovery: Boolean,
    isBondedSnapshot: Boolean,
    rawAdvertisementHex: String? = null,
    manufacturerPayloads: List<String> = emptyList(),
    serviceDataPayloads: List<String> = emptyList(),
    txPowerLevel: Int? = null,
    advertisingFlags: Int? = null,
    connectable: Boolean? = null,
  ) {
    val address = device.address ?: return
    rawDeviceMap[address] = device
    val previous = deviceMap[address]

    val btClass =
      try {
        device.bluetoothClass
      } catch (_: SecurityException) {
        null
      }
    val majorClass = btClass?.majorDeviceClass ?: previous?.majorClass
    val deviceClass = btClass?.deviceClass ?: previous?.deviceClass

    val rawType =
      try {
        device.type
      } catch (_: SecurityException) {
        BluetoothDevice.DEVICE_TYPE_UNKNOWN
      }

    val mappedType =
      when {
        rawType == BluetoothDevice.DEVICE_TYPE_DUAL -> TransportType.DUAL
        rawType == BluetoothDevice.DEVICE_TYPE_LE ->
          if (previous?.transportType == TransportType.CLASSIC || fromClassicDiscovery) {
            TransportType.DUAL
          } else {
            TransportType.BLE
          }
        rawType == BluetoothDevice.DEVICE_TYPE_CLASSIC ->
          if (previous?.transportType == TransportType.BLE) {
            TransportType.DUAL
          } else {
            TransportType.CLASSIC
          }
        fromClassicDiscovery ->
          if (previous?.transportType == TransportType.BLE) TransportType.DUAL else TransportType.CLASSIC
        previous != null -> previous.transportType
        else -> TransportType.BLE
      }

    val bondState =
      try {
        mapBondState(device.bondState)
      } catch (_: SecurityException) {
        previous?.bondState ?: BondState.NONE
      }

    val mergedName = overrideName?.takeIf { it.isNotBlank() } ?: previous?.name
    val mergedUuids = ((previous?.advertisedServiceUuids.orEmpty()) + advertisedUuids).distinct()
    val mergedSdpUuids = previous?.sdpUuids.orEmpty()
    val mergedCompanyId = companyId ?: previous?.advertisedCompanyId

    val classification =
      EarbudHeuristics.classifyEarbudCandidate(
        name = mergedName,
        majorClass = majorClass,
        deviceClass = deviceClass,
        advertisedUuids = mergedUuids,
        sdpUuids = mergedSdpUuids,
      )

    val systemAlias =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        BluetoothPermissionHelper.hasConnectPermission(appContext)
      ) {
        safeGetDeviceAlias(device)
      } else {
        previous?.systemAlias
      }

    val entry =
      DiscoveredDevice(
        address = address,
        name = mergedName,
        systemAlias = systemAlias ?: previous?.systemAlias,
        rssi = rssi ?: previous?.rssi,
        transportType = mappedType,
        majorClass = majorClass,
        deviceClass = deviceClass,
        deviceClassLabel = EarbudHeuristics.resolveDeviceClassLabel(majorClass, deviceClass),
        bondState = bondState,
        isLikelyEarbud = classification.isLikelyEarbud,
        earbudConfidenceLabel = classification.confidenceLabel,
        earbudReason = classification.reason,
        advertisedCompanyId = mergedCompanyId,
        advertisedCompanyHint = EarbudHeuristics.resolveCompanyHint(mergedCompanyId),
        advertisedServiceUuids = mergedUuids,
        sdpUuids = mergedSdpUuids,
        rawAdvertisementHex = rawAdvertisementHex ?: previous?.rawAdvertisementHex,
        manufacturerPayloads = (previous?.manufacturerPayloads.orEmpty() + manufacturerPayloads).distinct(),
        serviceDataPayloads = (previous?.serviceDataPayloads.orEmpty() + serviceDataPayloads).distinct(),
        txPowerLevel = txPowerLevel ?: previous?.txPowerLevel,
        advertisingFlags = advertisingFlags ?: previous?.advertisingFlags,
        connectable = connectable ?: previous?.connectable,
        isBondedSnapshot = isBondedSnapshot || (previous?.isBondedSnapshot == true),
        lastSeenMs = System.currentTimeMillis(),
      )

    val isNew = previous == null
    deviceMap[address] = entry
    emitSortedDevices()

    if (isNew && !isBondedSnapshot) {
      logger.log(
        LogLevel.DISCOVERY,
        mappedType.label.uppercase(),
        "Found ${entry.displayName} ($address) • RSSI=${entry.signalStrengthLabel}",
        buildString {
          append("Class=${entry.deviceClassLabel}")
          if (entry.isLikelyEarbud) append(" • Match=${entry.earbudConfidenceLabel} (${entry.earbudReason})")
          if (entry.advertisedCompanyHint != null) append(" • CoID=${entry.advertisedCompanyHint}")
        },
      )
    }
  }

  private fun updateDeviceSdpUuids(address: String, sdpUuids: List<String>) {
    val previous = deviceMap[address] ?: return
    val mergedSdp = (previous.sdpUuids + sdpUuids).distinct()
    val classification =
      EarbudHeuristics.classifyEarbudCandidate(
        name = previous.name,
        majorClass = previous.majorClass,
        deviceClass = previous.deviceClass,
        advertisedUuids = previous.advertisedServiceUuids,
        sdpUuids = mergedSdp,
      )
    deviceMap[address] =
      previous.copy(
        sdpUuids = mergedSdp,
        isLikelyEarbud = classification.isLikelyEarbud,
        earbudConfidenceLabel = classification.confidenceLabel,
        earbudReason = classification.reason,
      )
    emitSortedDevices()
  }

  private fun formatAdvertisementBytes(bytes: ByteArray): String =
    bytes.joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

  private fun emitSortedDevices() {
    _devices.value =
      deviceMap.values.sortedWith(
        compareByDescending<DiscoveredDevice> { it.isLikelyEarbud }
          .thenByDescending { it.bondState == BondState.BONDED }
          .thenByDescending { it.rssi ?: Int.MIN_VALUE }
          .thenBy { it.displayName },
      )
  }

  private fun updateCombinedScanningFlag() {
    _isScanning.value = _isBleScanning.value || _isClassicScanning.value
  }

  @SuppressLint("MissingPermission")
  private fun safeGetDeviceName(device: BluetoothDevice): String? {
    return try {
      device.name
    } catch (_: SecurityException) {
      null
    }
  }

  @SuppressLint("MissingPermission")
  private fun safeGetDeviceAlias(device: BluetoothDevice): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        device.alias
      } catch (_: SecurityException) {
        null
      }
    } else {
      null
    }
  }

  @SuppressLint("MissingPermission")
  private fun safeCloseGatt(gatt: BluetoothGatt) {
    try {
      gatt.close()
    } catch (_: Exception) {
    }
    if (activeGatt == gatt) {
      activeGatt = null
    }
  }

  private fun mapBondState(state: Int): BondState {
    return when (state) {
      BluetoothDevice.BOND_BONDED -> BondState.BONDED
      BluetoothDevice.BOND_BONDING -> BondState.BONDING
      else -> BondState.NONE
    }
  }

  private fun decodeCharacteristicProperties(props: Int): List<CharacteristicProperty> {
    val list = mutableListOf<CharacteristicProperty>()
    if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) list += CharacteristicProperty.READ
    if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) list += CharacteristicProperty.WRITE
    if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
      list += CharacteristicProperty.WRITE_NO_RESPONSE
    }
    if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) list += CharacteristicProperty.NOTIFY
    if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) list += CharacteristicProperty.INDICATE
    if ((props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) {
      list += CharacteristicProperty.SIGNED_WRITE
    }
    if ((props and BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0) {
      list += CharacteristicProperty.BROADCAST
    }
    return list
  }

  private fun explainGattStatus(status: Int): String {
    return when (status) {
      BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
      0x08 -> "Connection Timeout (0x08)"
      0x13 -> "Remote User Terminated (0x13)"
      0x16 -> "Local Host Terminated (0x16)"
      0x85, 133 -> "GATT_ERROR 133 (Common on Classic-only devices or radio timeout)"
      0x101 -> "GATT_FAILURE (257)"
      else -> "Status $status"
    }
  }

  private fun explainScanError(errorCode: Int): String {
    return when (errorCode) {
      ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
      ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
      ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth stack error"
      ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning unsupported"
      else -> "Error code $errorCode"
    }
  }

  private fun adapterStateName(state: Int): String {
    return when (state) {
      BluetoothAdapter.STATE_OFF -> "OFF"
      BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
      BluetoothAdapter.STATE_ON -> "ON"
      BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
      else -> "UNKNOWN ($state)"
    }
  }

  private fun profileStateName(state: Int): String {
    return when (state) {
      BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
      BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
      BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
      BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
      else -> "UNKNOWN ($state)"
    }
  }

  fun release() {
    stopScan()
    disconnectGatt()
    if (receiverRegistered) {
      try {
        appContext.unregisterReceiver(classicReceiver)
      } catch (_: Exception) {
      }
      receiverRegistered = false
    }
  }
}
