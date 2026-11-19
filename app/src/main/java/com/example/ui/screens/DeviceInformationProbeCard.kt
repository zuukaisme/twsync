package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TwsTheme

@Composable
fun DeviceInformationProbeCard(
  isGattReady: Boolean,
  onProbe: () -> Unit,
) {
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
        text = "Standard device information",
        style = MaterialTheme.typography.titleSmall,
        color = colors.inkPrimary,
      )
      Text(
        text = "Queue reads for Manufacturer, Model, Serial, Firmware, Hardware, Software, PnP ID and Battery Level when the earbuds expose those standard GATT characteristics.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.inkSecondary,
      )
      Button(
        onClick = onProbe,
        enabled = isGattReady,
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF38BDF8),
          contentColor = Color(0xFF041C2C),
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (isGattReady) "Read available device information" else "Connect to GATT first")
      }
      Text(
        text = "Read-only. Missing characteristics are skipped; no vendor commands or firmware writes are sent.",
        style = MaterialTheme.typography.labelSmall,
        color = colors.inkSecondary,
      )
    }
  }
}
