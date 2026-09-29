package com.example.gobeats.core.audio

import kotlin.math.ln
import kotlin.math.roundToInt

data class AutoEqProfile(
    val id: String,
    val brand: String,
    val model: String,
    val category: String, // "Over-Ear", "TWS", "IEM", "Studio"
    val preampDb: Float,
    val gainsDb: List<Float>, // Gains at [60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz]
    val recommendedBassBoost: Int = 0,
    val recommendedVirtualizer: Int = 0
) {
    val fullName: String
        get() = "$brand $model"
}

object AutoEqCatalog {

    val REFERENCE_FREQS_HZ = listOf(60, 230, 910, 3600, 14000)

    val CATEGORIES = listOf("All", "TWS", "Over-Ear", "IEM", "Studio")

    val PROFILES: List<AutoEqProfile> = listOf(
        // Sony
        AutoEqProfile(
            id = "sony_wh1000xm5",
            brand = "Sony",
            model = "WH-1000XM5",
            category = "Over-Ear",
            preampDb = -3.2f,
            gainsDb = listOf(-3.5f, -4.2f, 1.4f, 2.8f, 1.9f),
            recommendedBassBoost = 80,
            recommendedVirtualizer = 220
        ),
        AutoEqProfile(
            id = "sony_wh1000xm4",
            brand = "Sony",
            model = "WH-1000XM4",
            category = "Over-Ear",
            preampDb = -3.8f,
            gainsDb = listOf(-4.1f, -3.6f, 1.8f, 3.4f, 2.5f),
            recommendedBassBoost = 60,
            recommendedVirtualizer = 250
        ),
        AutoEqProfile(
            id = "sony_wf1000xm5",
            brand = "Sony",
            model = "WF-1000XM5",
            category = "TWS",
            preampDb = -2.4f,
            gainsDb = listOf(-1.8f, -2.1f, 0.9f, 2.2f, 2.8f),
            recommendedBassBoost = 100,
            recommendedVirtualizer = 180
        ),
        // Apple
        AutoEqProfile(
            id = "apple_airpods_pro_2",
            brand = "Apple",
            model = "AirPods Pro (2nd Gen)",
            category = "TWS",
            preampDb = -1.5f,
            gainsDb = listOf(0.8f, -0.9f, 0.4f, 1.6f, 2.1f),
            recommendedBassBoost = 120,
            recommendedVirtualizer = 150
        ),
        AutoEqProfile(
            id = "apple_airpods_max",
            brand = "Apple",
            model = "AirPods Max",
            category = "Over-Ear",
            preampDb = -2.2f,
            gainsDb = listOf(-1.2f, 0.5f, 1.8f, 2.6f, 1.4f),
            recommendedBassBoost = 90,
            recommendedVirtualizer = 200
        ),
        AutoEqProfile(
            id = "apple_airpods_3",
            brand = "Apple",
            model = "AirPods (3rd Gen)",
            category = "TWS",
            preampDb = -4.0f,
            gainsDb = listOf(4.2f, -1.8f, -0.5f, 1.9f, 2.4f),
            recommendedBassBoost = 250,
            recommendedVirtualizer = 160
        ),
        // Sennheiser
        AutoEqProfile(
            id = "sennheiser_hd650",
            brand = "Sennheiser",
            model = "HD 650 / HD 6XX",
            category = "Studio",
            preampDb = -4.5f,
            gainsDb = listOf(4.8f, -1.4f, 0.2f, 1.5f, 2.9f),
            recommendedBassBoost = 180,
            recommendedVirtualizer = 100
        ),
        AutoEqProfile(
            id = "sennheiser_hd600",
            brand = "Sennheiser",
            model = "HD 600",
            category = "Studio",
            preampDb = -4.8f,
            gainsDb = listOf(5.2f, -0.8f, -0.3f, 0.9f, 2.1f),
            recommendedBassBoost = 200,
            recommendedVirtualizer = 90
        ),
        AutoEqProfile(
            id = "sennheiser_momentum_4",
            brand = "Sennheiser",
            model = "Momentum 4 Wireless",
            category = "Over-Ear",
            preampDb = -3.1f,
            gainsDb = listOf(-3.8f, -1.6f, 1.2f, 2.3f, 1.7f),
            recommendedBassBoost = 70,
            recommendedVirtualizer = 210
        ),
        AutoEqProfile(
            id = "sennheiser_ie200",
            brand = "Sennheiser",
            model = "IE 200",
            category = "IEM",
            preampDb = -2.0f,
            gainsDb = listOf(1.4f, -0.6f, 1.1f, 0.8f, -1.5f),
            recommendedBassBoost = 110,
            recommendedVirtualizer = 140
        ),
        // Bose
        AutoEqProfile(
            id = "bose_qc_ultra",
            brand = "Bose",
            model = "QuietComfort Ultra",
            category = "Over-Ear",
            preampDb = -2.9f,
            gainsDb = listOf(-2.8f, -2.2f, 1.5f, 2.1f, 1.8f),
            recommendedBassBoost = 90,
            recommendedVirtualizer = 200
        ),
        AutoEqProfile(
            id = "bose_qc45",
            brand = "Bose",
            model = "QuietComfort 45",
            category = "Over-Ear",
            preampDb = -3.0f,
            gainsDb = listOf(-1.9f, -1.4f, 1.1f, 0.8f, -2.4f),
            recommendedBassBoost = 120,
            recommendedVirtualizer = 180
        ),
        // Beyerdynamic
        AutoEqProfile(
            id = "beyer_dt770_pro_80",
            brand = "Beyerdynamic",
            model = "DT 770 Pro (80 Ohm)",
            category = "Studio",
            preampDb = -3.6f,
            gainsDb = listOf(-0.8f, 1.9f, 0.6f, 1.4f, -4.2f),
            recommendedBassBoost = 100,
            recommendedVirtualizer = 140
        ),
        AutoEqProfile(
            id = "beyer_dt990_pro",
            brand = "Beyerdynamic",
            model = "DT 990 Pro (250 Ohm)",
            category = "Studio",
            preampDb = -4.2f,
            gainsDb = listOf(3.1f, -2.2f, 0.9f, 1.2f, -5.1f),
            recommendedBassBoost = 150,
            recommendedVirtualizer = 120
        ),
        // Audio-Technica
        AutoEqProfile(
            id = "ath_m50x",
            brand = "Audio-Technica",
            model = "ATH-M50x",
            category = "Studio",
            preampDb = -3.3f,
            gainsDb = listOf(-1.6f, -2.4f, 1.9f, 1.4f, -1.8f),
            recommendedBassBoost = 100,
            recommendedVirtualizer = 180
        ),
        // Moondrop & IEMs
        AutoEqProfile(
            id = "moondrop_blessing_3",
            brand = "Moondrop",
            model = "Blessing 3",
            category = "IEM",
            preampDb = -2.1f,
            gainsDb = listOf(2.4f, 0.5f, -0.4f, -1.2f, 0.8f),
            recommendedBassBoost = 140,
            recommendedVirtualizer = 150
        ),
        AutoEqProfile(
            id = "moondrop_aria",
            brand = "Moondrop",
            model = "Aria (2021)",
            category = "IEM",
            preampDb = -1.9f,
            gainsDb = listOf(-0.9f, -1.1f, 0.8f, 1.2f, 1.6f),
            recommendedBassBoost = 100,
            recommendedVirtualizer = 160
        ),
        AutoEqProfile(
            id = "moondrop_chu_2",
            brand = "Moondrop",
            model = "Chu II",
            category = "IEM",
            preampDb = -2.0f,
            gainsDb = listOf(-1.4f, -0.8f, 0.6f, 0.9f, 1.4f),
            recommendedBassBoost = 90,
            recommendedVirtualizer = 150
        ),
        AutoEqProfile(
            id = "truthear_hexa",
            brand = "Truthear",
            model = "HEXA",
            category = "IEM",
            preampDb = -2.2f,
            gainsDb = listOf(2.1f, 0.6f, -0.3f, 0.4f, 1.1f),
            recommendedBassBoost = 130,
            recommendedVirtualizer = 140
        ),
        AutoEqProfile(
            id = "kz_zsn_pro_x",
            brand = "KZ",
            model = "ZSN Pro X",
            category = "IEM",
            preampDb = -3.5f,
            gainsDb = listOf(-2.6f, -1.8f, 2.1f, -3.4f, -2.8f),
            recommendedBassBoost = 60,
            recommendedVirtualizer = 180
        ),
        AutoEqProfile(
            id = "tangzu_waner",
            brand = "Tangzu",
            model = "Wan'er S.G",
            category = "IEM",
            preampDb = -2.0f,
            gainsDb = listOf(-1.2f, -0.9f, 0.5f, 1.1f, 1.8f),
            recommendedBassBoost = 100,
            recommendedVirtualizer = 150
        ),
        // Samsung & Nothing & OnePlus
        AutoEqProfile(
            id = "samsung_buds2_pro",
            brand = "Samsung",
            model = "Galaxy Buds2 Pro",
            category = "TWS",
            preampDb = -1.8f,
            gainsDb = listOf(-0.6f, 0.4f, 0.8f, -1.4f, 1.2f),
            recommendedBassBoost = 110,
            recommendedVirtualizer = 160
        ),
        AutoEqProfile(
            id = "samsung_buds_fe",
            brand = "Samsung",
            model = "Galaxy Buds FE",
            category = "TWS",
            preampDb = -2.3f,
            gainsDb = listOf(-2.1f, -0.5f, 1.2f, 0.6f, 1.5f),
            recommendedBassBoost = 90,
            recommendedVirtualizer = 150
        ),
        AutoEqProfile(
            id = "nothing_ear_2024",
            brand = "Nothing",
            model = "Ear (2024)",
            category = "TWS",
            preampDb = -2.4f,
            gainsDb = listOf(-1.8f, -1.2f, 1.4f, 1.1f, 1.9f),
            recommendedBassBoost = 100,
            recommendedVirtualizer = 170
        ),
        AutoEqProfile(
            id = "oneplus_buds_pro_2",
            brand = "OnePlus",
            model = "Buds Pro 2",
            category = "TWS",
            preampDb = -3.1f,
            gainsDb = listOf(-3.4f, -2.1f, 1.6f, 1.9f, 1.4f),
            recommendedBassBoost = 70,
            recommendedVirtualizer = 190
        ),
        AutoEqProfile(
            id = "jbl_tune_770nc",
            brand = "JBL",
            model = "Tune 770NC",
            category = "Over-Ear",
            preampDb = -2.7f,
            gainsDb = listOf(-2.2f, -1.1f, 0.9f, 1.8f, 1.5f),
            recommendedBassBoost = 90,
            recommendedVirtualizer = 180
        ),
        AutoEqProfile(
            id = "shure_se215",
            brand = "Shure",
            model = "SE215",
            category = "IEM",
            preampDb = -3.8f,
            gainsDb = listOf(-2.4f, -2.9f, 0.8f, 3.8f, 4.2f),
            recommendedBassBoost = 80,
            recommendedVirtualizer = 160
        )
    )

    fun findById(id: String?): AutoEqProfile? {
        if (id.isNullOrBlank()) return null
        return PROFILES.find { it.id == id }
    }

    fun searchProfiles(query: String, category: String? = null): List<AutoEqProfile> {
        val trimmed = query.trim().lowercase()
        return PROFILES.filter { profile ->
            val categoryMatch = category == null || category == "All" ||
                profile.category.equals(category, ignoreCase = true)
            val queryMatch = trimmed.isEmpty() ||
                profile.brand.lowercase().contains(trimmed) ||
                profile.model.lowercase().contains(trimmed) ||
                profile.fullName.lowercase().contains(trimmed) ||
                profile.category.lowercase().contains(trimmed)
            categoryMatch && queryMatch
        }
    }

    /**
     * Interpolates the target EQ gain in millibels (1 dB = 100 mB) for any hardware EQ band center frequency
     * using logarithmic frequency interpolation across REFERENCE_FREQS_HZ.
     */
    fun interpolateGainMb(profile: AutoEqProfile, targetFreqHz: Int): Int {
        val freqs = REFERENCE_FREQS_HZ
        val gains = profile.gainsDb
        if (freqs.isEmpty() || gains.isEmpty()) return 0

        val clampedFreq = targetFreqHz.coerceIn(20, 20000)
        if (clampedFreq <= freqs.first()) {
            return (gains.first() * 100f).roundToInt()
        }
        if (clampedFreq >= freqs.last()) {
            return (gains.last() * 100f).roundToInt()
        }

        for (i in 0 until freqs.size - 1) {
            val f0 = freqs[i]
            val f1 = freqs[i + 1]
            if (clampedFreq in f0..f1) {
                val logF = ln(clampedFreq.toDouble())
                val logF0 = ln(f0.toDouble())
                val logF1 = ln(f1.toDouble())
                val t = if (logF1 > logF0) ((logF - logF0) / (logF1 - logF0)).toFloat() else 0f
                val g0 = gains.getOrElse(i) { 0f }
                val g1 = gains.getOrElse(i + 1) { g0 }
                val interpolatedDb = g0 + t * (g1 - g0)
                return (interpolatedDb * 100f).roundToInt()
            }
        }
        return 0
    }
}
