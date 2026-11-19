package com.example.data.bluetooth

import com.example.domain.model.DiagnosticLogEntry
import com.example.domain.model.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DiagnosticLogger(private val maxEntries: Int = 500) {
  private val idGenerator = AtomicLong(0L)
  private val _entries = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
  val entries: StateFlow<List<DiagnosticLogEntry>> = _entries.asStateFlow()

  fun log(level: LogLevel, category: String, message: String, details: String? = null) {
    val entry =
      DiagnosticLogEntry(
        id = idGenerator.incrementAndGet(),
        timestampMs = System.currentTimeMillis(),
        level = level,
        category = category,
        message = message,
        details = details,
      )
    _entries.update { current ->
      val combined = current + entry
      if (combined.size > maxEntries) combined.takeLast(maxEntries) else combined
    }
  }

  fun clear() {
    _entries.value = emptyList()
  }

  fun exportPlainText(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    val snapshot = _entries.value
    if (snapshot.isEmpty()) return "TWS Bridge Diagnostic Log (empty)"
    return buildString {
      appendLine("=== TWS Bridge Diagnostic Log ===")
      appendLine("Entries: ${snapshot.size}")
      appendLine()
      snapshot.forEach { entry ->
        val ts = formatter.format(Date(entry.timestampMs))
        appendLine("[$ts] [${entry.level.badge}] [${entry.category}] ${entry.message}")
        if (!entry.details.isNullOrBlank()) {
          appendLine("    ${entry.details}")
        }
      }
    }
  }
}
