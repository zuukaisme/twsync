package com.example

import com.example.data.bluetooth.ChipsetFamily
import com.example.data.bluetooth.ChipsetFingerprint
import com.example.data.bluetooth.FingerprintConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipsetFingerprintTest {

  @Test
  fun jieliServiceUuidProducesPossibleFingerprint() {
    val result = ChipsetFingerprint.assess(
      manufacturerCompanyId = null,
      serviceUuids = listOf("0000AE00-0000-1000-8000-00805F9B34FB"),
      deviceName = "Unknown Bud",
    )

    assertEquals(ChipsetFamily.JIELI, result.family)
    assertEquals(FingerprintConfidence.POSSIBLE, result.confidence)
    assertTrue(result.evidence.isNotEmpty())
  }

  @Test
  fun independentJieliSignalsRaiseFingerprintConfidence() {
    val result = ChipsetFingerprint.assess(
      manufacturerCompanyId = 0x05D6,
      serviceUuids = listOf("0000ae00-0000-1000-8000-00805f9b34fb"),
      deviceName = "Generic TWS",
    )

    assertEquals(ChipsetFamily.JIELI, result.family)
    assertEquals(FingerprintConfidence.HIGH, result.confidence)
    assertEquals(2, result.evidence.size)
  }

  @Test
  fun genericVendorUuidDoesNotIdentifyChipset() {
    val result = ChipsetFingerprint.assess(
      manufacturerCompanyId = null,
      serviceUuids = listOf("0000FFE0-0000-1000-8000-00805F9B34FB"),
      deviceName = "Unknown Audio",
    )

    assertEquals(ChipsetFamily.UNKNOWN, result.family)
    assertEquals(FingerprintConfidence.NONE, result.confidence)
    assertTrue(result.evidence.single().contains("does not identify a chipset"))
  }

  @Test
  fun conflictingVendorSignalsDoNotSelectAChipset() {
    val result = ChipsetFingerprint.assess(
      manufacturerCompanyId = 0x05D6,
      serviceUuids = emptyList(),
      deviceName = "Airoha Audio",
    )

    assertEquals(ChipsetFamily.UNKNOWN, result.family)
    assertEquals(FingerprintConfidence.NONE, result.confidence)
    assertTrue(result.caveat.contains("Conflicting"))
  }

  @Test
  fun unknownDeviceProducesNoChipsetClaim() {
    val result = ChipsetFingerprint.assess(
      manufacturerCompanyId = null,
      serviceUuids = emptyList(),
      deviceName = "Nearby Device",
    )

    assertEquals(ChipsetFamily.UNKNOWN, result.family)
    assertEquals(FingerprintConfidence.NONE, result.confidence)
    assertTrue(result.evidence.isEmpty())
  }
}
