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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.audio.AudioChannelTest
import com.example.data.audio.OsAudioState
import com.example.domain.model.BluetoothEnvironmentState
import com.example.domain.model.BondState
import com.example.domain.model.DiscoveredDevice
import com.example.domain.model.EarbudRole
import com.example.ui.components.StatusPill
import com.example.ui.components.TransportBadge
import com.example.ui.theme.TwsTheme
import kotlin.math.roundToInt

@Composable
fun OsControlScreen(
  environmentState: BluetoothEnvironmentState,
  localAdapterName: String?,
  audioState: OsAudioState,
  devices: List<DiscoveredDevice>,
  bannerMessage: String?,
  devModeEnabled: Boolean,
  onDismissBanner: () -> Unit,
  onRequestPermissions: () -> Unit,
  onRequestEnableBluetooth: () -> Unit,
  onOpenSystemBluetoothSettings: () -> Unit,
  onRenameLocalAdapter: (String) -> Unit,
  onSetMediaVolume: (Int) -> Unit,
  onToggleMediaMute: () -> Unit,
  onSetCallVolume: (Int) -> Unit,
  onSetRingVolume: (Int) -> Unit,
  onPlayChannelTest: (AudioChannelTest) -> Unit,
  onRenameDevice: (address: String, alias: String) -> Unit,
  onAssignEarbudRole: (address: String, role: EarbudRole) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = TwsTheme.colors
  var editingOsName by remember { mutableStateOf(false) }
  var editingDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        AnimatedVisibility(visible = bannerMessage != null) {
          bannerMessage?.let { msg ->
            Surface(
              color = colors.chromeBg,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.55f)),
              modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.chromeOnSurface,
                  modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissBanner) {
                  Text("OK", color = colors.cyan)
                }
              }
            }
          }
        }
      }

      item {
        OsIdentityCard(
          environmentState = environmentState,
          localAdapterName = localAdapterName,
          audioState = audioState,
          onRequestPermissions = onRequestPermissions,
          onRequestEnableBluetooth = onRequestEnableBluetooth,
          onOpenSystemBluetoothSettings = onOpenSystemBluetoothSettings,
          onEditOsName = { editingOsName = true },
        )
      }

      item {
        VolumeMixerCard(
          audioState = audioState,
          onSetMediaVolume = onSetMediaVolume,
          onToggleMediaMute = onToggleMediaMute,
          onSetCallVolume = onSetCallVolume,
          onSetRingVolume = onSetRingVolume,
        )
      }

      item {
        StereoCheckCard(
          activeTest = audioState.activeChannelTest,
          onPlayChannelTest = onPlayChannelTest,
        )
      }

      item {
        ActiveOutputRoutesCard(audioState = audioState)
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Paired & Audio Devices",
              style = MaterialTheme.typography.titleMedium,
              color = colors.inkPrimary,
            )
            Text(
              text = "Rename devices and tag Left / Right earbuds",
              style = MaterialTheme.typography.bodySmall,
              color = colors.inkSecondary,
            )
          }
          StatusPill(
            text = "${devices.size}",
            containerColor = colors.chromeBg,
            contentColor = colors.chromeOnSurface,
            borderColor = colors.chromeBorder,
          )
        }
      }

      if (devices.isEmpty()) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
            border = BorderStroke(1.dp, colors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "No Paired Earbuds Loaded",
                style = MaterialTheme.typography.titleSmall,
                color = colors.inkPrimary,
              )
              Text(
                text =
                  if (environmentState.missingPermissions.isNotEmpty()) {
                    "Grant Nearby Devices permission above to view and rename your paired Bluetooth earbuds."
                  } else {
                    "Pair an earbud in Android Bluetooth settings, or enable Dev Mode to run a raw BLE/Classic scan."
                  },
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
              )
            }
          }
        }
      } else {
        items(items = devices, key = { it.address }) { device ->
          OsDeviceCard(
            device = device,
            onRenameClick = { editingDevice = device },
            onAssignRole = { role -> onAssignEarbudRole(device.address, role) },
          )
        }
      }

      if (!devModeEnabled) {
        item {
          Surface(
            color = colors.cardSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.DeveloperMode,
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text =
                  "Raw BLE/Classic Scanner, GATT Inspector, Event Logs, and Feasibility Guide are tucked inside Dev Mode (top-right toggle).",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
              )
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }

  if (editingOsName) {
    RenameTextDialog(
      title = "Rename OS Bluetooth Name",
      subtitle = "Updates this device's visible Bluetooth adapter name.",
      initialValue = localAdapterName.orEmpty(),
      placeholder = "e.g., TWS Bridge OS",
      onDismiss = { editingOsName = false },
      onConfirm = { newName ->
        editingOsName = false
        onRenameLocalAdapter(newName)
      },
    )
  }

  editingDevice?.let { target ->
    RenameTextDialog(
      title = "Rename Bluetooth Device",
      subtitle =
        "Updates the OS Bluetooth alias (Android 11+) and TWS Bridge display label for ${target.address}.",
      initialValue = target.displayName,
      placeholder = "e.g., Left Bud (Pair A)",
      onDismiss = { editingDevice = null },
      onConfirm = { newAlias ->
        editingDevice = null
        onRenameDevice(target.address, newAlias)
      },
    )
  }
}

@Composable
private fun OsIdentityCard(
  environmentState: BluetoothEnvironmentState,
  localAdapterName: String?,
  audioState: OsAudioState,
  onRequestPermissions: () -> Unit,
  onRequestEnableBluetooth: () -> Unit,
  onOpenSystemBluetoothSettings: () -> Unit,
  onEditOsName: () -> Unit,
) {
  val colors = TwsTheme.colors
  val btRoute = audioState.outputRoutes.firstOrNull { it.isBluetooth }

  // High-contrast dark hero control panel on light background (and deep obsidian on dark mode)
  Card(
    colors = CardDefaults.cardColors(containerColor = colors.chromeBg),
    border = BorderStroke(1.dp, colors.chromeBorder),
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
            text = "OS Device Name",
            style = MaterialTheme.typography.labelMedium,
            color = colors.chromeOnSurface.copy(alpha = 0.68f),
          )
          Text(
            text = localAdapterName?.takeIf { it.isNotBlank() } ?: "Android Bluetooth Host",
            style = MaterialTheme.typography.titleLarge,
            color = colors.chromeOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        FilledTonalButton(
          onClick = onEditOsName,
          enabled = environmentState.hasConnectPermission && environmentState.isBluetoothEnabled,
          shape = RoundedCornerShape(10.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = colors.chromeSurface,
              contentColor = Color(0xFF38BDF8),
              disabledContainerColor = colors.chromeSurface.copy(alpha = 0.45f),
              disabledContentColor = colors.chromeOnSurface.copy(alpha = 0.4f),
            ),
          modifier = Modifier.height(48.dp).testTag("rename_os_name_button"),
        ) {
          Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Rename")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatusPill(
          text =
            when {
              !environmentState.isBluetoothSupported -> "NO BT RADIO"
              environmentState.isBluetoothEnabled -> "BLUETOOTH ON"
              else -> "BLUETOOTH OFF"
            },
          containerColor = colors.chromeSurface,
          contentColor =
            if (environmentState.isBluetoothEnabled) Color(0xFF2DD4BF) else Color(0xFFFBBF24),
          borderColor = colors.chromeBorder,
        )
        StatusPill(
          text =
            if (btRoute != null) "OUTPUT: ${btRoute.productName}" else "OUTPUT: PHONE SPEAKER",
          containerColor = colors.chromeSurface,
          contentColor = Color(0xFF38BDF8),
          borderColor = colors.chromeBorder,
        )
        StatusPill(
          text = "RINGER: ${audioState.ringerModeLabel.uppercase()}",
          containerColor = colors.chromeSurface,
          contentColor = colors.chromeOnSurface.copy(alpha = 0.85f),
          borderColor = colors.chromeBorder,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (environmentState.missingPermissions.isNotEmpty()) {
          Button(
            onClick = onRequestPermissions,
            shape = RoundedCornerShape(10.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFBBF24),
                contentColor = Color(0xFF0B1120),
              ),
            modifier = Modifier.weight(1f).height(48.dp).testTag("os_grant_permissions_button"),
          ) {
            Text("Grant BT Permission")
          }
        } else if (environmentState.isBluetoothSupported && !environmentState.isBluetoothEnabled) {
          Button(
            onClick = onRequestEnableBluetooth,
            shape = RoundedCornerShape(10.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color(0xFF38BDF8),
                contentColor = Color(0xFF041C2C),
              ),
            modifier = Modifier.weight(1f).height(48.dp).testTag("os_enable_bt_button"),
          ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Turn On Bluetooth")
          }
        }

        OutlinedButton(
          onClick = onOpenSystemBluetoothSettings,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, colors.chromeBorder),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.chromeOnSurface),
          modifier = Modifier.weight(1f).height(48.dp).testTag("open_bt_settings_button"),
        ) {
          Icon(
            Icons.Default.SettingsBluetooth,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("System Pairings", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun VolumeMixerCard(
  audioState: OsAudioState,
  onSetMediaVolume: (Int) -> Unit,
  onToggleMediaMute: () -> Unit,
  onSetCallVolume: (Int) -> Unit,
  onSetRingVolume: (Int) -> Unit,
) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border = BorderStroke(1.dp, colors.border),
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
        Text(
          text = "OS Volume Mixer",
          style = MaterialTheme.typography.titleMedium,
          color = colors.inkPrimary,
        )
        Surface(
          onClick = onToggleMediaMute,
          color = colors.chromeBg,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, colors.chromeBorder),
          modifier = Modifier.testTag("toggle_media_mute_button"),
        ) {
          Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector =
                if (audioState.isMediaMuted) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = if (audioState.isMediaMuted) "Unmute media" else "Mute media",
              tint = if (audioState.isMediaMuted) Color(0xFFFBBF24) else Color(0xFF38BDF8),
            )
          }
        }
      }

      VolumeSliderRow(
        label = "Media",
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        value = audioState.mediaVolume,
        max = audioState.mediaMaxVolume,
        accent = colors.cyan,
        testTag = "media_volume_slider",
        onValueCommit = onSetMediaVolume,
      )

      HorizontalDivider(color = colors.border)

      VolumeSliderRow(
        label = "Voice Call",
        icon = Icons.Default.Call,
        value = audioState.callVolume,
        max = audioState.callMaxVolume,
        accent = colors.teal,
        testTag = "call_volume_slider",
        onValueCommit = onSetCallVolume,
      )

      HorizontalDivider(color = colors.border)

      VolumeSliderRow(
        label = "Ring & Alerts",
        icon = Icons.Default.NotificationsActive,
        value = audioState.ringVolume,
        max = audioState.ringMaxVolume,
        accent = colors.amber,
        testTag = "ring_volume_slider",
        onValueCommit = onSetRingVolume,
      )
    }
  }
}

@Composable
private fun VolumeSliderRow(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  value: Int,
  max: Int,
  accent: Color,
  testTag: String,
  onValueCommit: (Int) -> Unit,
) {
  val colors = TwsTheme.colors
  var sliderPos by remember(value) { mutableFloatStateOf(value.toFloat()) }
  val pct = ((sliderPos / max.coerceAtLeast(1)) * 100).roundToInt()

  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = colors.inkPrimary)
      }
      Text(
        text = "$pct% (${sliderPos.roundToInt()}/$max)",
        style = MaterialTheme.typography.labelMedium,
        color = colors.inkSecondary,
      )
    }
    Slider(
      value = sliderPos,
      onValueChange = { next ->
        sliderPos = next
        onValueCommit(next.roundToInt())
      },
      valueRange = 0f..max.toFloat().coerceAtLeast(1f),
      steps = (max - 1).coerceAtLeast(0),
      colors =
        SliderDefaults.colors(
          thumbColor = colors.chromeBg,
          activeTrackColor = accent,
          inactiveTrackColor = colors.border,
        ),
      modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
  }
}

@Composable
private fun StereoCheckCard(
  activeTest: AudioChannelTest?,
  onPlayChannelTest: (AudioChannelTest) -> Unit,
) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border = BorderStroke(1.dp, colors.border),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = colors.teal)
        Column {
          Text(
            text = "Stereo Channel Check",
            style = MaterialTheme.typography.titleMedium,
            color = colors.inkPrimary,
          )
          Text(
            text = "Verify Left / Right separation on active earbuds",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AudioChannelTest.entries.forEach { test ->
          val isPlaying = activeTest == test
          OutlinedButton(
            onClick = { onPlayChannelTest(test) },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (isPlaying) colors.teal else colors.chromeBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = if (isPlaying) colors.tealContainer else colors.chromeBg,
                contentColor = if (isPlaying) colors.teal else colors.chromeOnSurface,
              ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("audio_test_${test.name.lowercase()}"),
          ) {
            if (isPlaying) {
              Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
              text = test.label,
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ActiveOutputRoutesCard(audioState: OsAudioState) {
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
        text = "Active Audio Output Hardware",
        style = MaterialTheme.typography.titleSmall,
        color = colors.inkPrimary,
      )
      audioState.outputRoutes.forEach { route ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = if (route.isBluetooth) Icons.Default.Headphones else Icons.Default.Speaker,
              contentDescription = null,
              tint = if (route.isBluetooth) colors.teal else colors.inkSecondary,
              modifier = Modifier.size(18.dp),
            )
            Column {
              Text(
                text = route.productName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = route.typeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
              )
            }
          }
          StatusPill(
            text = route.channelCounts,
            containerColor = colors.cardElevated,
            contentColor = if (route.isBluetooth) colors.teal else colors.inkSecondary,
            borderColor = colors.border,
          )
        }
      }
    }
  }
}

@Composable
private fun OsDeviceCard(
  device: DiscoveredDevice,
  onRenameClick: () -> Unit,
  onAssignRole: (EarbudRole) -> Unit,
) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border =
      BorderStroke(
        1.dp,
        if (device.assignedRole != EarbudRole.NONE) colors.teal.copy(alpha = 0.6f) else colors.border,
      ),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("os_device_card_${device.address}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = device.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = colors.inkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          val hwSuffix =
            device.hardwareName?.takeIf { it != device.displayName }?.let { " • HW: $it" }.orEmpty()
          Text(
            text = "${device.address}$hwSuffix",
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkSecondary,
          )
        }
        IconButton(
          onClick = onRenameClick,
          modifier = Modifier.testTag("rename_device_button_${device.address}"),
        ) {
          Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Rename ${device.displayName}",
            tint = colors.cyan,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TransportBadge(device.transportType)
          if (device.bondState == BondState.BONDED) {
            StatusPill(
              text = "PAIRED",
              containerColor = colors.tealContainer,
              contentColor = colors.teal,
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          EarbudRole.entries.forEach { role ->
            val selected = device.assignedRole == role
            OutlinedButton(
              onClick = { onAssignRole(role) },
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, if (selected) colors.teal else colors.border),
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor = if (selected) colors.chromeBg else colors.cardElevated,
                  contentColor = if (selected) Color(0xFF2DD4BF) else colors.inkSecondary,
                ),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
              modifier = Modifier.height(48.dp).testTag("role_${role.name.lowercase()}_${device.address}"),
            ) {
              Text(text = role.shortBadge, style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RenameTextDialog(
  title: String,
  subtitle: String,
  initialValue: String,
  placeholder: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  val colors = TwsTheme.colors
  var text by remember(initialValue) { mutableStateOf(initialValue) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.cardSurface,
    titleContentColor = colors.inkPrimary,
    textContentColor = colors.inkSecondary,
    title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          singleLine = true,
          placeholder = { Text(placeholder, color = colors.inkMuted) },
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = colors.cyan,
              unfocusedBorderColor = colors.border,
              focusedContainerColor = colors.cardElevated,
              unfocusedContainerColor = colors.cardElevated,
            ),
          modifier = Modifier.fillMaxWidth().testTag("rename_dialog_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(text) },
        shape = RoundedCornerShape(10.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.chromeBg,
            contentColor = colors.chromeOnSurface,
          ),
        modifier = Modifier.testTag("rename_dialog_confirm"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = colors.inkSecondary)
      }
    },
  )
}
