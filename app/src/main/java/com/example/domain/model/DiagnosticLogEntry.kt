package com.example.domain.model

enum class LogLevel(val badge: String) {
  INFO("INFO"),
  DISCOVERY("SCAN"),
  GATT("GATT"),
  WARNING("WARN"),
  ERROR("ERR"),
}

enum class LogFilter(val label: String) {
  ALL("All"),
  DISCOVERY("Discovery"),
  GATT("GATT"),
  ISSUES("Warn/Err"),
}

data class DiagnosticLogEntry(
  val id: Long,
  val timestampMs: Long,
  val level: LogLevel,
  val category: String,
  val message: String,
  val details: String? = null,
)
