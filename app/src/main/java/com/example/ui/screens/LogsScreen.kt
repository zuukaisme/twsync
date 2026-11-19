package com.example.ui.screens

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DiagnosticLogEntry
import com.example.domain.model.LogFilter
import com.example.domain.model.LogLevel
import com.example.ui.components.StatusPill
import com.example.ui.theme.TwsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
  logs: List<DiagnosticLogEntry>,
  logFilter: LogFilter,
  onFilterSelected: (LogFilter) -> Unit,
  onShareLogs: () -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = TwsTheme.colors
  val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).padding(horizontal = 16.dp)) {
      Spacer(modifier = Modifier.height(14.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = stringResource(R.string.logs_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.inkPrimary,
          )
          Text(
            text = "${logs.size} event(s) recorded",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onShareLogs,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, colors.chromeBorder),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = colors.chromeBg,
                contentColor = Color(0xFF38BDF8),
              ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(48.dp).testTag("share_logs_button"),
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.logs_share))
          }
          OutlinedButton(
            onClick = onClearLogs,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, colors.border),
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = colors.cardSurface,
                contentColor = colors.inkSecondary,
              ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(48.dp).testTag("clear_logs_button"),
          ) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.logs_clear))
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LogFilter.entries.forEach { filter ->
          val selected = logFilter == filter
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
            modifier = Modifier.testTag("log_filter_${filter.name.lowercase()}"),
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      if (logs.isEmpty()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
          border = BorderStroke(1.dp, colors.border),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Terminal,
              contentDescription = null,
              tint = colors.inkMuted,
              modifier = Modifier.size(36.dp),
            )
            Text(
              text = stringResource(R.string.logs_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = colors.inkSecondary,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(items = logs, key = { it.id }) { entry ->
            LogRowCard(entry = entry, timeFormatter = timeFormatter)
          }
        }
      }
    }
  }
}

@Composable
private fun LogRowCard(entry: DiagnosticLogEntry, timeFormatter: SimpleDateFormat) {
  val colors = TwsTheme.colors
  val (bg, fg) =
    when (entry.level) {
      LogLevel.DISCOVERY -> colors.cyanContainer to colors.cyan
      LogLevel.GATT -> colors.tealContainer to colors.teal
      LogLevel.WARNING -> colors.amberContainer to colors.amber
      LogLevel.ERROR -> colors.coralContainer to colors.coral
      LogLevel.INFO -> colors.cardElevated to colors.inkSecondary
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
    border = BorderStroke(1.dp, colors.border),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StatusPill(text = entry.level.badge, containerColor = bg, contentColor = fg)
          Text(
            text = entry.category,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
          )
        }
        Text(
          text = timeFormatter.format(Date(entry.timestampMs)),
          style = MaterialTheme.typography.labelSmall,
          color = colors.inkMuted,
        )
      }
      Text(
        text = entry.message,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.inkPrimary,
      )
      entry.details?.takeIf { it.isNotBlank() }?.let { details ->
        Text(
          text = details,
          style = MaterialTheme.typography.labelSmall,
          color = colors.inkSecondary,
        )
      }
    }
  }
}
