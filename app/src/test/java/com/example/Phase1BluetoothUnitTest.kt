package com.example

import android.bluetooth.BluetoothClass
import com.example.data.bluetooth.DiagnosticLogger
import com.example.data.bluetooth.EarbudHeuristics
import com.example.domain.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1BluetoothUnitTest {

  @Test
  fun `classifies audio wearable headset class as high match earbud`() {
    val result =
      EarbudHeuristics.classifyEarbudCandidate(
        name = "Generic-BT",
        majorClass = BluetoothClass.Device.Major.AUDIO_VIDEO,
        deviceClass = BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        advertisedUuids = emptyList(),
        sdpUuids = emptyList(),
      )
    assertTrue(result.isLikelyEarbud)
    assertEquals("High Match", result.confidenceLabel)
  }

  @Test
  fun `classifies unknown non-audio BLE beacon as non-earbud`() {
    val result =
      EarbudHeuristics.classifyEarbudCandidate(
        name = "SmartThermometer",
        majorClass = BluetoothClass.Device.Major.UNCATEGORIZED,
        deviceClass = null,
        advertisedUuids = listOf("00001809-0000-1000-8000-00805f9b34fb"),
        sdpUuids = emptyList(),
      )
    assertFalse(result.isLikelyEarbud)
    assertNull(result.confidenceLabel)
  }

  @Test
  fun `classifies JieLi vendor UUID and TWS name as high match`() {
    val result =
      EarbudHeuristics.classifyEarbudCandidate(
        name = "TWS-M10",
        majorClass = null,
        deviceClass = null,
        advertisedUuids = listOf("0000ae00-0000-1000-8000-00805f9b34fb"),
        sdpUuids = emptyList(),
      )
    assertTrue(result.isLikelyEarbud)
    assertEquals("High Match", result.confidenceLabel)
  }

  @Test
  fun `formats bytes to hex and printable UTF-8 safely`() {
    val bytes = byteArrayOf(0x4A, 0x4C, 0x2D, 0x31, 0x30)
    assertEquals("4A 4C 2D 31 30", EarbudHeuristics.formatBytesToHex(bytes))
    assertEquals("JL-10", EarbudHeuristics.formatBytesToPrintableUtf8(bytes))

    val binary = byteArrayOf(0x01, 0x02, 0xFF.toByte(), 0x00)
    assertNull(EarbudHeuristics.formatBytesToPrintableUtf8(binary))
  }

  @Test
  fun `resolves standard GATT and JieLi UUID labels`() {
    assertEquals(
      "Device Information",
      EarbudHeuristics.resolveUuidLabel("0000180a-0000-1000-8000-00805f9b34fb"),
    )
    assertNotNull(EarbudHeuristics.resolveUuidLabel("0000ae00-0000-1000-8000-00805f9b34fb"))
  }

  @Test
  fun `diagnostic logger respects ring buffer limit and exports plain text`() {
    val logger = DiagnosticLogger(maxEntries = 3)
    logger.log(LogLevel.INFO, "TEST", "Event 1")
    logger.log(LogLevel.DISCOVERY, "TEST", "Event 2")
    logger.log(LogLevel.GATT, "TEST", "Event 3")
    logger.log(LogLevel.WARNING, "TEST", "Event 4", "extra detail")

    val entries = logger.entries.value
    assertEquals(3, entries.size)
    assertEquals("Event 2", entries.first().message)
    assertEquals("Event 4", entries.last().message)

    val exported = logger.exportPlainText()
    assertTrue(exported.contains("Event 4"))
    assertTrue(exported.contains("extra detail"))
  }
}
