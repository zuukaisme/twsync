package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.bluetooth.ChipsetAssessment
import com.example.data.bluetooth.FingerprintConfidence
import com.example.ui.theme.TwsTheme

@Composable
fun ChipsetFingerprintCard(assessment: ChipsetAssessment) {
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
        text = "Chipset fingerprint",
        style = MaterialTheme.typography.titleSmall,
        color = colors.inkPrimary,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = assessment.family.displayName,
          style = MaterialTheme.typography.titleMedium,
          color = colors.inkPrimary,
        )
        Text(
          text = assessment.confidence.label,
          style = MaterialTheme.typography.labelMedium,
          color = if (assessment.confidence == FingerprintConfidence.HIGH) colors.cyan else colors.inkSecondary,
        )
      }
      if (assessment.evidence.isEmpty()) {
        Text(
          text = "No vendor-specific fingerprint was detected from the currently available advertising and profile data.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.inkSecondary,
        )
      } else {
        assessment.evidence.forEach { evidence ->
          Text(
            text = "• $evidence",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
          )
        }
      }
      Text(
        text = assessment.caveat,
        style = MaterialTheme.typography.bodySmall,
        color = colors.inkSecondary,
      )
    }
  }
}
