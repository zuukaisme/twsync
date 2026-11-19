package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.TransportType
import com.example.ui.theme.TwsTheme

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  borderColor: Color = contentColor.copy(alpha = 0.35f),
  modifier: Modifier = Modifier,
) {
  Surface(
    color = containerColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(6.dp),
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun TransportBadge(transportType: TransportType, modifier: Modifier = Modifier) {
  val colors = TwsTheme.colors
  val (bg, fg) =
    when (transportType) {
      TransportType.BLE -> colors.cyanContainer to colors.cyan
      TransportType.DUAL -> colors.tealContainer to colors.teal
      TransportType.CLASSIC -> colors.amberContainer to colors.amber
      TransportType.UNKNOWN -> colors.cardElevated to colors.inkMuted
    }
  StatusPill(
    text = transportType.label.uppercase(),
    containerColor = bg,
    contentColor = fg,
    modifier = modifier,
  )
}

@Composable
fun RssiSignalBars(rssi: Int?, modifier: Modifier = Modifier) {
  val colors = TwsTheme.colors
  val bars =
    when {
      rssi == null -> 0
      rssi >= -55 -> 4
      rssi >= -70 -> 3
      rssi >= -85 -> 2
      else -> 1
    }
  val activeColor =
    when (bars) {
      4, 3 -> colors.teal
      2 -> colors.cyan
      1 -> colors.amber
      else -> colors.inkMuted
    }
  Row(
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.Bottom,
    modifier = modifier,
  ) {
    for (index in 1..4) {
      val barHeight = (5 + index * 3).dp
      Box(
        modifier =
          Modifier.width(3.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(1.dp))
            .background(if (index <= bars) activeColor else colors.border),
      )
    }
    Text(
      text = rssi?.let { "$it dBm" } ?: "—",
      style = MaterialTheme.typography.labelMedium,
      color = if (rssi != null) colors.inkPrimary else colors.inkMuted,
      modifier = Modifier.padding(start = 6.dp),
    )
  }
}
