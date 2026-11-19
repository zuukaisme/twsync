package com.example.data.bluetooth

import java.util.Locale

enum class ChipsetFamily(val displayName: String) {
  JIELI("JieLi"),
  BESTECHNIC("Bestechnic / BES"),
  ACTIONS("Actions Technology"),
  BLUETRUM("Bluetrum"),
  AIROHA("Airoha"),
  UNKNOWN("Unknown vendor"),
}

enum class FingerprintConfidence(val label: String) {
  HIGH("Multiple matching signals"),
  POSSIBLE("One vendor-associated signal"),
  NONE("No useful chipset signature"),
}

data class ChipsetAssessment(
  val family: ChipsetFamily,
  val confidence: FingerprintConfidence,
  val evidence: List<String>,
  val caveat: String,
)

object ChipsetFingerprint {
  private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

  fun assess(
    manufacturerCompanyId: Int?,
    serviceUuids: List<String>,
    deviceName: String?,
  ): ChipsetAssessment {
    val normalizedUuids = serviceUuids.map { it.lowercase(Locale.ROOT) }
    val name = deviceName.orEmpty().lowercase(Locale.ROOT)
    val evidenceByFamily = linkedMapOf<ChipsetFamily, MutableList<String>>()

    fun add(family: ChipsetFamily, signal: String) {
      evidenceByFamily.getOrPut(family) { mutableListOf() }.add(signal)
    }

    when (manufacturerCompanyId) {
      0x05D6 -> add(ChipsetFamily.JIELI, "Manufacturer data company ID 0x05D6 is associated with Zhuhai Jieli")
      0x02B0 -> add(ChipsetFamily.BESTECHNIC, "Manufacturer data company ID 0x02B0 is associated with Bestechnic")
      0x03E0 -> add(ChipsetFamily.ACTIONS, "Manufacturer data company ID 0x03E0 is associated with Actions Technology")
      0x0642 -> add(ChipsetFamily.BLUETRUM, "Manufacturer data company ID 0x0642 is associated with Bluetrum")
      0x0094 -> add(ChipsetFamily.AIROHA, "Manufacturer data company ID 0x0094 is associated with Airoha")
    }

    val shortUuids = normalizedUuids.mapNotNull(::shortUuid)
    if ("ae00" in shortUuids || "ae01" in shortUuids || "ae02" in shortUuids) {
      val matches = shortUuids.filter { it in setOf("ae00", "ae01", "ae02") }
      add(ChipsetFamily.JIELI, "JieLi RCSP-associated service UUID detected (" + matches.joinToString() + ")")
    }

    val familyNameTokens = mapOf(
      ChipsetFamily.JIELI to listOf("jieli", "jl-"),
      ChipsetFamily.BESTECHNIC to listOf("bestechnic", "bes"),
      ChipsetFamily.ACTIONS to listOf("actions"),
      ChipsetFamily.BLUETRUM to listOf("bluetrum"),
      ChipsetFamily.AIROHA to listOf("airoha"),
    )
    familyNameTokens.forEach { (family, tokens) ->
      if (tokens.any(name::contains)) add(family, "Device name contains a vendor-associated token")
    }

    val winner = evidenceByFamily.entries.maxByOrNull { it.value.size }
    if (winner == null) {
      val genericVendorServices = shortUuids.filter { it in setOf("ff00", "ffe0", "ffe1") }
      return ChipsetAssessment(
        family = ChipsetFamily.UNKNOWN,
        confidence = FingerprintConfidence.NONE,
        evidence = if (genericVendorServices.isEmpty()) emptyList() else listOf("Generic vendor UART/service UUID detected (" + genericVendorServices.joinToString() + "); it does not identify a chipset"),
        caveat = "No reliable chipset signature was found. A missing signature does not mean the earbuds lack a vendor chipset.",
      )
    }

    val confidence = if (winner.value.size >= 2) FingerprintConfidence.HIGH else FingerprintConfidence.POSSIBLE
    return ChipsetAssessment(
      family = winner.key,
      confidence = confidence,
      evidence = winner.value.toList(),
      caveat = "This is a heuristic fingerprint, not proof of the physical SoC. Advertising data, names and company IDs can be spoofed or reused by clone firmware. Confirm with device-information reads and repeatable captures.",
    )
  }

  private fun shortUuid(uuid: String): String? {
    val normalized = uuid.lowercase(Locale.ROOT)
    return when {
      normalized.length == 4 -> normalized
      normalized.length == 8 && normalized.startsWith("0000") -> normalized.substring(4)
      normalized.length == 36 && normalized.endsWith(BASE_UUID_SUFFIX) -> normalized.substring(4, 8)
      else -> null
    }
  }
}
