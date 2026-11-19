package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.StatusPill
import com.example.ui.theme.TwsTheme

@Composable
fun LabGuideScreen(modifier: Modifier = Modifier) {
  val colors = TwsTheme.colors

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.lab_guide_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.inkPrimary,
          )
          Text(
            text = stringResource(R.string.lab_guide_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
          )
        }
      }

      item {
        GuideSectionCard(
          badge = "FEASIBILITY",
          badgeBg = colors.cyanContainer,
          badgeFg = colors.cyan,
          title = "Why Mismatched TWS Earbuds Reject Each Other",
          points =
            listOf(
              "Factory TWS buds store a static peer BD_ADDR and 128-bit Link Key / LTK inside internal flash/EEPROM.",
              "Two buds from different boxes usually share the same radio hardware, but hold different peer addresses or mismatched firmware build hashes.",
              "Re-pairing them requires either a chipset-supported TWS re-sync gesture/command (e.g., vendor BLE/SPP control packet or multi-tap factory reset) or Android-side Dual Audio / LE Audio routing.",
            ),
        )
      }

      item {
        GuideSectionCard(
          badge = "ANDROID API LIMITS",
          badgeBg = colors.amberContainer,
          badgeFg = colors.amber,
          title = "What Non-Root Android Apps Can and Cannot Do",
          points =
            listOf(
              "CAN: Scan BLE + Classic BR/EDR, read RSSI, query bonded devices, probe Classic SDP UUIDs, inspect BLE GATT services/characteristics, and evaluate BluetoothA2dp codec/LE Audio capabilities.",
              "CANNOT: Send raw HCI commands, override Classic A2DP single-sink routing on stock AOSP, spoof earbud BD_ADDR, or erase/flash arbitrary SoC firmware over standard Bluetooth APIs.",
              "DUAL STREAMING: Simultaneous stereo to two independent A2DP earbuds is not natively exposed by standard Android Audio APIs without OEM Dual Audio or LE Audio (Android 13+ CIS/BIS).",
            ),
        )
      }

      item {
        GuideSectionCard(
          badge = "UNKNOWN CHIPSET",
          badgeBg = colors.coralContainer,
          badgeFg = colors.coral,
          title = "Without Knowing the SoC Architecture",
          points =
            listOf(
              "Phase 1 can safely discover Classic/BLE presence, Company IDs (note: knockoffs frequently spoof Apple 0x004C), and open GATT services.",
              "Without chipset identification, proprietary TWS sync opcodes and OTA/recovery protocols remain unknown—sending blind write commands risks soft-bricking.",
              "Always distinguish confirmed GATT Device Info strings from heuristic advertising guesses.",
            ),
        )
      }

      item {
        GuideSectionCard(
          badge = "CHIPSET MATRIX",
          badgeBg = colors.tealContainer,
          badgeFg = colors.teal,
          title = "Common Generic Earbud SoCs (Phase 2 Target)",
          points =
            listOf(
              "Zhuhai JieLi (AC69xx / AD69xx): Extremely common in budget clones. Often exposes BLE RCSP service (0xAE00/0xAE01/0xAE02); some variants support touch/button TWS re-pairing.",
              "Bluetrum (AB56xx / AB53xx): Widely used in low-cost buds. Usually Classic-only BR/EDR; BLE GATT may be absent or minimal; TWS pairing often relies on case/tap factory sequences.",
              "Bestechnic (BES2300/2500/2600): Mid-tier TWS SoC. Supports vendor BLE/SPP OTA & TWS IBRT sync commands when vendor UUIDs are enabled.",
              "Actions (ATS30xx) & Airoha (AB15xx): Feature-rich audio SoCs with documented vendor GATT/SPP protocols in OEM SDKs, rarely left unlocked in sealed retail firmware.",
            ),
        )
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun GuideSectionCard(
  badge: String,
  badgeBg: Color,
  badgeFg: Color,
  title: String,
  points: List<String>,
) {
  val colors = TwsTheme.colors

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border = BorderStroke(1.dp, colors.border),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      StatusPill(text = badge, containerColor = badgeBg, contentColor = badgeFg)
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colors.inkPrimary,
      )
      points.forEach { point ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(text = "•", style = MaterialTheme.typography.bodyMedium, color = badgeFg)
          Text(
            text = point,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSecondary,
          )
        }
      }
    }
  }
}
