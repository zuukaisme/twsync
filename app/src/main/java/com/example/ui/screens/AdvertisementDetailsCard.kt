package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.DiscoveredDevice
import com.example.ui.theme.TwsTheme

@Composable
fun AdvertisementDetailsCard(device: DiscoveredDevice) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Text(
        text = "BLE Advertisement Details",
        style = MaterialTheme.typography.titleSmall,
        color = colors.inkPrimary,
      )

      device.connectable?.let {
        AdvertisementValue(label = "Connectable", value = if (it) "Yes" else "No")
      }

      device.advertisingFlags?.let {
        AdvertisementValue(label = "Advertising flags", value = "0x%02X".format(it))
      }

      device.txPowerLevel?.let {
        AdvertisementValue(label = "Advertised TX power", value = "$it dBm")
      }

      if (device.manufacturerPayloads.isNotEmpty()) {
        Text(
          text = "Manufacturer-specific data",
          style = MaterialTheme.typography.labelLarge,
          color = colors.cyan,
        )
        device.manufacturerPayloads.forEach {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
          )
        }
      }

      if (device.serviceDataPayloads.isNotEmpty()) {
        Text(
          text = "Service data",
          style = MaterialTheme.typography.labelLarge,
          color = colors.cyan,
        )
        device.serviceDataPayloads.forEach {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
          )
        }
      }

      device.rawAdvertisementHex?.let {
        Text(
          text = "Raw advertisement payload",
          style = MaterialTheme.typography.labelLarge,
          color = colors.cyan,
        )
        Text(
          text = it,
          style = MaterialTheme.typography.bodySmall,
          color = colors.inkSecondary,
        )
      }
    }
  }
}

@Composable
private fun AdvertisementValue(label: String, value: String) {
  val colors = TwsTheme.colors
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = colors.inkMuted,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = colors.inkPrimary,
    )
  }
}
