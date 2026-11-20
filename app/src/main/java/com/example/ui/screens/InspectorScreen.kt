package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.bluetooth.ChipsetFingerprint
import com.example.data.bluetooth.EarbudHeuristics
import com.example.domain.model.DiscoveredDevice
import com.example.domain.model.GattCharacteristicInfo
import com.example.domain.model.GattConnectionState
import com.example.domain.model.GattInspectionSession
import com.example.domain.model.GattServiceInfo
import com.example.ui.components.RssiSignalBars
import com.example.ui.components.StatusPill
import com.example.ui.components.TransportBadge
import com.example.ui.theme.TwsTheme

@Composable
fun InspectorScreen(
  selectedDevice: DiscoveredDevice?,
  session: GattInspectionSession?,
  onConnectGatt: () -> Unit,
  onDisconnectGatt: () -> Unit,
  onProbeClassicSdp: () -> Unit,
  onReadCharacteristic: (serviceUuid: String, characteristicUuid: String) -> Unit,
  onReadStandardDeviceInformation: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = TwsTheme.colors
  val expandedServices = remember { mutableStateMapOf<String, Boolean>() }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    if (selectedDevice == null) {
      Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Outlined.DevicesOther,
          contentDescription = null,
          tint = colors.cyan,
          modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = stringResource(R.string.inspector_no_device_title),
          style = MaterialTheme.typography.titleLarge,
          color = colors.inkPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = stringResource(R.string.inspector_no_device_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.inkSecondary,
        )
      }
      return
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        DeviceIdentityCard(
          device = selectedDevice,
          session = session,
          onConnectGatt = onConnectGatt,
          onDisconnectGatt = onDisconnectGatt,
          onProbeClassicSdp = onProbeClassicSdp,
        )
      }

      item {
        DeviceInformationProbeCard(
          isGattReady = session?.connectionState == GattConnectionState.READY,
          onProbe = onReadStandardDeviceInformation,
        )
      }

      item {
        ChipsetFingerprintCard(
          ChipsetFingerprint.assess(
            manufacturerCompanyId = selectedDevice.advertisedCompanyId,
            serviceUuids = selectedDevice.advertisedServiceUuids + selectedDevice.sdpUuids + session?.services.orEmpty().map { it.uuid },
            deviceName = selectedDevice.hardwareName,
          ),
        )
      }

      if (selectedDevice.sdpUuids.isNotEmpty() || selectedDevice.advertisedServiceUuids.isNotEmpty()) {
        item { AdvertisedAndSdpCard(device = selectedDevice) }
      }

      if (
        selectedDevice.rawAdvertisementHex != null ||
        selectedDevice.manufacturerPayloads.isNotEmpty() ||
        selectedDevice.serviceDataPayloads.isNotEmpty() ||
        selectedDevice.txPowerLevel != null ||
        selectedDevice.advertisingFlags != null ||
        selectedDevice.connectable != null
      ) {
        item { AdvertisementDetailsCard(device = selectedDevice) }
      }

      if (session != null && session.services.isNotEmpty()) {
        item {
          Text(
            text = "GATT Services (${session.services.size})",
            style = MaterialTheme.typography.titleMedium,
            color = colors.inkPrimary,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
        items(items = session.services, key = { it.uuid }) { service ->
          val expanded = expandedServices[service.uuid] ?: true
          GattServiceCard(
            service = service,
            expanded = expanded,
            onToggleExpand = { expandedServices[service.uuid] = !expanded },
            onReadCharacteristic = onReadCharacteristic,
          )
        }
      } else if (session != null && session.connectionState == GattConnectionState.READY) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
            border = BorderStroke(1.dp, colors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                "Connected to GATT, but zero services were returned. Some budget audio chipsets expose only Classic A2DP/HFP and disable BLE GATT.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.inkSecondary,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun DeviceIdentityCard(
  device: DiscoveredDevice,
  session: GattInspectionSession?,
  onConnectGatt: () -> Unit,
  onDisconnectGatt: () -> Unit,
  onProbeClassicSdp: () -> Unit,
) {
  val colors = TwsTheme.colors
  val connState = session?.connectionState ?: GattConnectionState.DISCONNECTED
  val isBusy =
    connState == GattConnectionState.CONNECTING ||
      connState == GattConnectionState.DISCOVERING_SERVICES
  val isConnected = connState == GattConnectionState.CONNECTED || connState == GattConnectionState.READY

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.chromeBg),
    border = BorderStroke(1.dp, colors.chromeBorder),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = device.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = colors.chromeOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = device.address,
            style = MaterialTheme.typography.labelMedium,
            color = colors.chromeOnSurface.copy(alpha = 0.7f),
          )
        }
        Surface(
          color = colors.chromeSurface,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, colors.chromeBorder),
        ) {
          Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            RssiSignalBars(rssi = device.rssi)
          }
        }
      }

      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        TransportBadge(device.transportType)
        StatusPill(
          text = device.bondState.label,
          containerColor = colors.chromeSurface,
          contentColor = colors.chromeOnSurface.copy(alpha = 0.85f),
          borderColor = colors.chromeBorder,
        )
        StatusPill(
          text = device.deviceClassLabel,
          containerColor = colors.chromeSurface,
          contentColor = colors.chromeOnSurface.copy(alpha = 0.85f),
          borderColor = colors.chromeBorder,
        )
      }

      val statusColor =
        when (connState) {
          GattConnectionState.READY, GattConnectionState.CONNECTED -> Color(0xFF2DD4BF)
          GattConnectionState.CONNECTING, GattConnectionState.DISCOVERING_SERVICES ->
            Color(0xFF38BDF8)
          GattConnectionState.ERROR -> Color(0xFFF87171)
          GattConnectionState.DISCONNECTED -> colors.chromeOnSurface.copy(alpha = 0.75f)
        }

      Surface(
        color = colors.chromeSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF38BDF8),
              )
            }
            Text(
              text = "GATT Status: ${connState.label}",
              style = MaterialTheme.typography.titleSmall,
              color = statusColor,
            )
          }
          session?.statusMessage?.let { msg ->
            Text(
              text = msg,
              style = MaterialTheme.typography.bodySmall,
              color = colors.chromeOnSurface.copy(alpha = 0.78f),
            )
          }
        }
      }

      if (session?.isClassicOnlyWarning == true) {
        Surface(
          color = Color(0xFF423007).copy(alpha = 0.65f),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = Color(0xFFFBBF24),
              modifier = Modifier.size(18.dp),
            )
            Text(
              text =
                "Classic-only earbuds often reject BLE GATT connections (status 133). Use Probe Classic SDP below to inspect Classic BR/EDR profiles (A2DP, HFP, SPP).",
              style = MaterialTheme.typography.bodySmall,
              color = colors.chromeOnSurface,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (isConnected || isBusy) {
          OutlinedButton(
            onClick = onDisconnectGatt,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFF87171)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
            modifier = Modifier.weight(1f).height(48.dp).testTag("disconnect_gatt_button"),
          ) {
            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.inspector_disconnect_gatt))
          }
        } else {
          Button(
            onClick = onConnectGatt,
            shape = RoundedCornerShape(10.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color(0xFF38BDF8),
                contentColor = Color(0xFF041C2C),
              ),
            modifier = Modifier.weight(1f).height(48.dp).testTag("connect_gatt_button"),
          ) {
            Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.inspector_connect_gatt))
          }
        }

        OutlinedButton(
          onClick = onProbeClassicSdp,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, colors.chromeBorder),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2DD4BF)),
          modifier = Modifier.weight(1f).height(48.dp).testTag("probe_sdp_button"),
        ) {
          Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.inspector_probe_sdp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun AdvertisedAndSdpCard(device: DiscoveredDevice) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border = BorderStroke(1.dp, colors.border),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Discovered Profile & Advertising UUIDs",
        style = MaterialTheme.typography.titleSmall,
        color = colors.inkPrimary,
      )
      if (device.advertisedServiceUuids.isNotEmpty()) {
        Text(
          text = "BLE Advertised Services:",
          style = MaterialTheme.typography.bodySmall,
          color = colors.cyan,
        )
        device.advertisedServiceUuids.forEach { uuid ->
          val label = EarbudHeuristics.resolveUuidLabel(uuid)
          Text(
            text = if (label != null) "• $label ($uuid)" else "• $uuid",
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSecondary,
          )
        }
      }
      if (device.sdpUuids.isNotEmpty()) {
        Text(
          text = "Classic SDP Profiles (Confirmed):",
          style = MaterialTheme.typography.bodySmall,
          color = colors.teal,
        )
        device.sdpUuids.forEach { uuid ->
          val label = EarbudHeuristics.resolveUuidLabel(uuid)
          Text(
            text = if (label != null) "• $label ($uuid)" else "• $uuid",
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSecondary,
          )
        }
      }
    }
  }
}

@Composable
private fun GattServiceCard(
  service: GattServiceInfo,
  expanded: Boolean,
  onToggleExpand: () -> Unit,
  onReadCharacteristic: (String, String) -> Unit,
) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border = BorderStroke(1.dp, colors.border),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("gatt_service_${service.uuid}"),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Surface(
        onClick = onToggleExpand,
        color = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = service.knownName ?: "Vendor / Unknown Service",
              style = MaterialTheme.typography.titleSmall,
              color = if (service.knownName != null) colors.cyan else colors.amber,
            )
            Text(
              text = service.uuid,
              style = MaterialTheme.typography.labelSmall,
              color = colors.inkSecondary,
            )
          }
          Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse service" else "Expand service",
            tint = colors.inkSecondary,
          )
        }
      }

      AnimatedVisibility(visible = expanded) {
        Column(
          modifier = Modifier.padding(top = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (service.characteristics.isEmpty()) {
            Text(
              text = "No characteristics exposed under this service.",
              style = MaterialTheme.typography.bodySmall,
              color = colors.inkMuted,
            )
          } else {
            service.characteristics.forEachIndexed { index, charInfo ->
              if (index > 0) {
                HorizontalDivider(color = colors.border)
              }
              GattCharacteristicRow(
                characteristic = charInfo,
                onRead = { onReadCharacteristic(service.uuid, charInfo.uuid) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GattCharacteristicRow(
  characteristic: GattCharacteristicInfo,
  onRead: () -> Unit,
) {
  val colors = TwsTheme.colors

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = characteristic.knownName ?: "Characteristic",
          style = MaterialTheme.typography.bodyLarge,
          color = colors.inkPrimary,
        )
        Text(
          text = characteristic.uuid,
          style = MaterialTheme.typography.labelSmall,
          color = colors.inkMuted,
        )
      }
      if (characteristic.isReadable) {
        OutlinedButton(
          onClick = onRead,
          enabled = !characteristic.isReading,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, colors.chromeBorder),
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = colors.chromeBg,
              contentColor = Color(0xFF38BDF8),
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          modifier = Modifier.height(48.dp).testTag("read_char_${characteristic.uuid}"),
        ) {
          if (characteristic.isReading) {
            CircularProgressIndicator(
              modifier = Modifier.size(14.dp),
              strokeWidth = 2.dp,
              color = Color(0xFF38BDF8),
            )
          } else {
            Text(
              text = stringResource(R.string.inspector_read_char),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      characteristic.properties.forEach { prop ->
        StatusPill(
          text = prop.badge,
          containerColor = colors.cardElevated,
          contentColor = if (prop.badge == "READ") colors.teal else colors.inkSecondary,
          borderColor = colors.border,
        )
      }
    }

    characteristic.lastReadHex?.let { hex ->
      Surface(
        color = colors.chromeBg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF2DD4BF).copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = "HEX: $hex",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF2DD4BF),
          )
          characteristic.lastReadUtf8?.let { utf8 ->
            Text(
              text = "UTF-8: \"$utf8\"",
              style = MaterialTheme.typography.bodySmall,
              color = colors.chromeOnSurface,
            )
          }
        }
      }
    }

    characteristic.readError?.let { err ->
      Text(
        text = err,
        style = MaterialTheme.typography.bodySmall,
        color = colors.coral,
      )
    }
  }
}
