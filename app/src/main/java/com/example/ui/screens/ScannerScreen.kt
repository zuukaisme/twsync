package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BluetoothEnvironmentState
import com.example.domain.model.BondState
import com.example.domain.model.DeviceFilter
import com.example.domain.model.DiscoveredDevice
import com.example.domain.model.ScanMode
import com.example.ui.components.RssiSignalBars
import com.example.ui.components.StatusPill
import com.example.ui.components.TransportBadge
import com.example.ui.theme.TwsTheme

@Composable
fun ScannerScreen(
  environmentState: BluetoothEnvironmentState,
  isScanning: Boolean,
  scanMode: ScanMode,
  deviceFilter: DeviceFilter,
  searchQuery: String,
  devices: List<DiscoveredDevice>,
  totalDiscoveredCount: Int,
  onRequestPermissions: () -> Unit,
  onRequestEnableBluetooth: () -> Unit,
  onScanModeSelected: (ScanMode) -> Unit,
  onToggleScan: () -> Unit,
  onFilterSelected: (DeviceFilter) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  onInspectDevice: (DiscoveredDevice) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = TwsTheme.colors

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        ScannerHeaderCard(
          environmentState = environmentState,
          isScanning = isScanning,
          scanMode = scanMode,
          totalDiscoveredCount = totalDiscoveredCount,
          onScanModeSelected = onScanModeSelected,
          onToggleScan = onToggleScan,
        )
      }

      if (!environmentState.isBluetoothSupported) {
        item {
          StatusBannerCard(
            icon = Icons.Default.BluetoothDisabled,
            title = stringResource(R.string.bluetooth_unsupported_title),
            body = stringResource(R.string.bluetooth_unsupported_body),
            accentColor = colors.coral,
            containerColor = colors.coralContainer,
          )
        }
      } else {
        if (environmentState.missingPermissions.isNotEmpty()) {
          item {
            PermissionPromptCard(
              requiresLegacyLocation = environmentState.requiresLegacyLocationPermission,
              onGrantPermissions = onRequestPermissions,
            )
          }
        }
        if (!environmentState.isBluetoothEnabled) {
          item {
            StatusBannerCard(
              icon = Icons.Default.BluetoothDisabled,
              title = stringResource(R.string.bluetooth_disabled_title),
              body = stringResource(R.string.bluetooth_disabled_body),
              accentColor = colors.amber,
              containerColor = colors.amberContainer,
              actionLabel = stringResource(R.string.bluetooth_enable_button),
              actionTag = "enable_bluetooth_button",
              onAction = onRequestEnableBluetooth,
            )
          }
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth().testTag("device_search_input"),
            placeholder = {
              Text(
                text = "Filter by name, MAC address, or SIG company ID...",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
              )
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search devices",
                tint = colors.inkSecondary,
              )
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(
                  onClick = { onSearchQueryChanged("") },
                  modifier = Modifier.testTag("clear_search_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = colors.inkSecondary,
                  )
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.cyan,
                unfocusedBorderColor = colors.border,
                focusedContainerColor = colors.cardSurface,
                unfocusedContainerColor = colors.cardSurface,
              ),
          )

          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            DeviceFilter.entries.forEach { filter ->
              val selected = deviceFilter == filter
              FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                shape = RoundedCornerShape(10.dp),
                label = { Text(filter.label, style = MaterialTheme.typography.labelLarge) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.chromeBg,
                    selectedLabelColor = Color(0xFF38BDF8),
                    containerColor = colors.cardSurface,
                    labelColor = colors.inkSecondary,
                  ),
                border =
                  FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = colors.border,
                    selectedBorderColor = colors.cyan.copy(alpha = 0.6f),
                  ),
                modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
              )
            }
          }
        }
      }

      if (devices.isEmpty()) {
        item { EmptyDiscoveryCard() }
      } else {
        items(items = devices, key = { it.address }) { device ->
          DiscoveredDeviceCard(device = device, onInspect = { onInspectDevice(device) })
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun ScannerHeaderCard(
  environmentState: BluetoothEnvironmentState,
  isScanning: Boolean,
  scanMode: ScanMode,
  totalDiscoveredCount: Int,
  onScanModeSelected: (ScanMode) -> Unit,
  onToggleScan: () -> Unit,
) {
  val colors = TwsTheme.colors
  val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
  val pulseAlpha by
    infiniteTransition.animateFloat(
      initialValue = 1f,
      targetValue = 0.35f,
      animationSpec = infiniteRepeatable(animation = tween(750), repeatMode = RepeatMode.Reverse),
      label = "pulse_alpha",
    )

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.chromeBg),
    border =
      BorderStroke(
        1.dp,
        if (isScanning) Color(0xFF38BDF8).copy(alpha = 0.65f) else colors.chromeBorder,
      ),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.scanner_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.chromeOnSurface,
          )
          Text(
            text = stringResource(R.string.scanner_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colors.chromeOnSurface.copy(alpha = 0.7f),
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier =
              Modifier.size(10.dp)
                .clip(CircleShape)
                .alpha(if (isScanning) pulseAlpha else 1f)
                .background(
                  when {
                    isScanning -> Color(0xFF38BDF8)
                    environmentState.isReadyToScan -> Color(0xFF2DD4BF)
                    else -> Color(0xFFFBBF24)
                  },
                ),
          )
          Text(
            text =
              when {
                isScanning -> "Scanning ($totalDiscoveredCount)"
                else -> "$totalDiscoveredCount seen"
              },
            style = MaterialTheme.typography.labelMedium,
            color =
              if (isScanning) Color(0xFF38BDF8) else colors.chromeOnSurface.copy(alpha = 0.78f),
          )
        }
      }

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ScanMode.entries.forEachIndexed { index, mode ->
          SegmentedButton(
            selected = scanMode == mode,
            onClick = { onScanModeSelected(mode) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = ScanMode.entries.size),
            colors =
              SegmentedButtonDefaults.colors(
                activeContainerColor = Color(0xFF0C354D),
                activeContentColor = Color(0xFF38BDF8),
                inactiveContainerColor = colors.chromeSurface,
                inactiveContentColor = colors.chromeOnSurface.copy(alpha = 0.72f),
                activeBorderColor = Color(0xFF38BDF8).copy(alpha = 0.6f),
                inactiveBorderColor = colors.chromeBorder,
              ),
            modifier = Modifier.testTag("scan_mode_${mode.name.lowercase()}"),
          ) {
            Text(
              text = mode.label,
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Button(
        onClick = onToggleScan,
        enabled = environmentState.isReadyToScan || isScanning,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (isScanning) Color(0xFF4A1616) else Color(0xFF38BDF8),
            contentColor = if (isScanning) Color(0xFFF87171) else Color(0xFF041C2C),
            disabledContainerColor = colors.chromeSurface,
            disabledContentColor = colors.chromeOnSurface.copy(alpha = 0.4f),
          ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("toggle_scan_button"),
      ) {
        Icon(
          imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.BluetoothSearching,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text =
            if (isScanning) stringResource(R.string.scan_stop)
            else stringResource(R.string.scan_start),
          style = MaterialTheme.typography.titleSmall,
        )
      }
    }
  }
}

@Composable
private fun PermissionPromptCard(
  requiresLegacyLocation: Boolean,
  onGrantPermissions: () -> Unit,
) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.amberContainer),
    border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = colors.amber,
        )
        Text(
          text = stringResource(R.string.permission_required_title),
          style = MaterialTheme.typography.titleMedium,
          color = colors.amber,
        )
      }
      Text(
        text =
          if (requiresLegacyLocation) stringResource(R.string.permission_required_body_legacy)
          else stringResource(R.string.permission_required_body_api31),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.inkPrimary,
      )
      Button(
        onClick = onGrantPermissions,
        shape = RoundedCornerShape(10.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.chromeBg,
            contentColor = colors.chromeOnSurface,
          ),
        modifier = Modifier.height(48.dp).testTag("grant_permissions_button"),
      ) {
        Text(text = stringResource(R.string.permission_grant_button))
      }
    }
  }
}

@Composable
private fun StatusBannerCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  body: String,
  accentColor: Color,
  containerColor: Color,
  actionLabel: String? = null,
  actionTag: String? = null,
  onAction: (() -> Unit)? = null,
) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = accentColor)
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = accentColor)
      }
      Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.inkPrimary)
      if (actionLabel != null && onAction != null) {
        OutlinedButton(
          onClick = onAction,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, accentColor),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
          modifier =
            Modifier.height(48.dp).then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier),
        ) {
          Text(text = actionLabel)
        }
      }
    }
  }
}

@Composable
private fun DiscoveredDeviceCard(
  device: DiscoveredDevice,
  onInspect: () -> Unit,
) {
  val colors = TwsTheme.colors

  Card(
    onClick = onInspect,
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border =
      BorderStroke(
        1.dp,
        if (device.isLikelyEarbud) colors.teal.copy(alpha = 0.5f) else colors.border,
      ),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("device_card_${device.address}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = if (device.isLikelyEarbud) colors.chromeBg else colors.cardElevated,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(40.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector =
                  if (device.isLikelyEarbud) Icons.Default.Headphones else Icons.Default.Bluetooth,
                contentDescription = if (device.isLikelyEarbud) "Likely earbud" else "Bluetooth device",
                tint = if (device.isLikelyEarbud) Color(0xFF2DD4BF) else colors.inkSecondary,
                modifier = Modifier.size(22.dp),
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = device.displayName,
              style = MaterialTheme.typography.titleMedium,
              color = colors.inkPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = device.address,
              style = MaterialTheme.typography.labelMedium,
              color = colors.inkSecondary,
            )
          }
        }
        RssiSignalBars(rssi = device.rssi)
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        TransportBadge(device.transportType)
        if (device.bondState != BondState.NONE) {
          StatusPill(
            text = device.bondState.label.uppercase(),
            containerColor = colors.tealContainer,
            contentColor = colors.teal,
          )
        }
        if (device.isLikelyEarbud && device.earbudConfidenceLabel != null) {
          StatusPill(
            text = device.earbudConfidenceLabel.uppercase(),
            containerColor = colors.tealContainer,
            contentColor = colors.teal,
          )
        }
        StatusPill(
          text = device.deviceClassLabel,
          containerColor = colors.cardElevated,
          contentColor = colors.inkSecondary,
          borderColor = colors.border,
        )
      }

      AnimatedVisibility(
        visible = device.earbudReason != null || device.advertisedCompanyHint != null,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          device.earbudReason?.let { reason ->
            Text(
              text = "Heuristic: $reason",
              style = MaterialTheme.typography.bodySmall,
              color = colors.teal,
            )
          }
          device.advertisedCompanyHint?.let { hint ->
            Text(
              text = "Advertised SIG CoID (unverified): $hint",
              style = MaterialTheme.typography.labelSmall,
              color = colors.amber,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (device.advertisedServiceUuids.isNotEmpty()) {
              "${device.advertisedServiceUuids.size} advertised UUID(s)"
            } else {
              "Tap to open Inspector"
            },
          style = MaterialTheme.typography.bodySmall,
          color = colors.inkMuted,
        )
        Text(
          text = "INSPECT ->",
          style = MaterialTheme.typography.labelMedium,
          color = colors.cyan,
        )
      }
    }
  }
}

@Composable
private fun EmptyDiscoveryCard() {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border = BorderStroke(1.dp, colors.border),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Radar,
        contentDescription = null,
        tint = colors.cyan,
        modifier = Modifier.size(40.dp),
      )
      Text(
        text = stringResource(R.string.empty_devices_title),
        style = MaterialTheme.typography.titleMedium,
        color = colors.inkPrimary,
      )
      Text(
        text = stringResource(R.string.empty_devices_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.inkSecondary,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          tint = colors.inkMuted,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "Note: Some generic earbuds only expose Classic BR/EDR and omit BLE GATT.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.inkMuted,
        )
      }
    }
  }
}
