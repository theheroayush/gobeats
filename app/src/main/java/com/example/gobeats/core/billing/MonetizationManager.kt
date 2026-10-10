package com.example.gobeats.core.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Storage contract for persisting monetization and licensing states.
 */
interface MonetizationStorage {
    fun getString(key: String, defValue: String?): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getLong(key: String, defValue: Long): Long
    fun putLong(key: String, value: Long)
    fun clear()
}

/**
 * Android SharedPreferences-backed persistence for production runtime.
 */
class SharedPreferencesMonetizationStorage(context: Context) : MonetizationStorage {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun getString(key: String, defValue: String?): String? = prefs.getString(key, defValue)

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "gobeats_monetization_prefs"
    }
}

/**
 * In-memory storage for high-speed headless JVM unit testing.
 */
class InMemoryMonetizationStorage : MonetizationStorage {
    private val data = mutableMapOf<String, Any?>()

    override fun getString(key: String, defValue: String?): String? = (data[key] as? String) ?: defValue

    override fun putString(key: String, value: String?) {
        data[key] = value
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue

    override fun putBoolean(key: String, value: Boolean) {
        data[key] = value
    }

    override fun getLong(key: String, defValue: Long): Long = (data[key] as? Long) ?: defValue

    override fun putLong(key: String, value: Long) {
        data[key] = value
    }

    override fun clear() {
        data.clear()
    }
}

/**
 * Enumeration of distinct feature flags within gobeats that can be gated
 * behind future monetization tiers or commercial licenses.
 */
enum class ProFeature(val featureKey: String, val displayName: String) {
    OFFLINE_AI_SUBTITLES("ai_subtitles", "Offline AI Speech Subtitles"),
    STUDIO_DSP_EQ("studio_dsp_eq", "Studio 5-Band DSP Equalizer"),
    AUTO_DEVICE_PROFILES("auto_device_profiles", "Hardware Auto Device EQ & AV Profiles"),
    DIALOGUE_BOOST("dialogue_boost", "Acoustic Dialogue Boost Filter"),
    TURBO_PC_SYNC("turbo_pc_sync", "Local Wi-Fi PC Sync File Transfer"),
    DYNAMIC_VISUALIZER("dynamic_visualizer", "60 FPS Real-time Audio Visualizer"),
    UNLIMITED_SMART_PLAYLISTS("unlimited_playlists", "Dynamic On-Device Smart Playlists"),
    CUSTOM_STUDIO_THEMES("custom_themes", "Custom Mesh Gradient & UI Palettes")
}

/**
 * Monetization tiers supported by the gobeats platform architecture.
 */
enum class MonetizationTier(val tierId: String, val title: String) {
    COMMUNITY_FREE("tier_community_free", "Community Edition"),
    PRO_LIFETIME("tier_pro_lifetime", "gobeats PRO Lifetime License"),
    PRO_SUBSCRIPTION("tier_pro_subscription", "gobeats PRO Subscription")
}

/**
 * Encapsulates the active licensing and monetization state on the user's device.
 *
 * In gobeats v3.2, all users receive [isProUnlocked] = true under [isPromotionalUnlock] = true
 * (free community release), ensuring zero paywall barriers while preserving the developer's
 * complete autonomy to introduce Google Play Billing or paid feature gating in future releases.
 */
data class LicenseStatus(
    val tier: MonetizationTier = MonetizationTier.COMMUNITY_FREE,
    val isProUnlocked: Boolean = true,
    val isPromotionalUnlock: Boolean = true,
    val purchaseToken: String? = null,
    val sku: String? = null,
    val licenseExpiryMs: Long = 0L
)

/**
 * Production-ready, modular monetization and licensing manager for gobeats.
 *
 * Responsibilities:
 * 1. Tracks and persists active licensing tier and feature unlock state.
 * 2. Provides query methods ([isFeatureUnlocked]) for feature gating.
 * 3. Default state for v3.2 grants full access to all features ([isProUnlocked] = true).
 * 4. Provides plug-and-play scaffolding for Google Play In-App Billing (Play Billing Library v7+).
 * 5. Guarantees 100% offline resilience: license checks never fail or block playback due to lack of network.
 * 6. Grants the developer full autonomy to activate monetization and modify feature gating in future updates.
 */
class MonetizationManager(
    context: Context? = null,
    storage: MonetizationStorage? = null
) {

    private val storage: MonetizationStorage = storage
        ?: context?.let { SharedPreferencesMonetizationStorage(it) }
        ?: InMemoryMonetizationStorage()

    private val _licenseStatus = MutableStateFlow(loadPersistedLicense())
    val licenseStatus: StateFlow<LicenseStatus> = _licenseStatus.asStateFlow()

    /**
     * Determines whether a specific [ProFeature] is unlocked and available for use.
     * In v3.2, all features evaluate to true by default under the free community tier.
     */
    fun isFeatureUnlocked(feature: ProFeature): Boolean {
        val current = _licenseStatus.value
        // If the license is active (or promotional unlock is active for current version), all features pass
        if (current.isProUnlocked) {
            return true
        }

        // Check per-feature unlock overrides if granular gating is introduced later
        return storage.getBoolean(KEY_FEATURE_OVERRIDE_PREFIX + feature.featureKey, false)
    }

    /**
     * Updates the local license state, typically called when a Google Play Billing
     * purchase is completed or restored, or when the developer modifies license parameters.
     */
    fun setLicenseState(
        tier: MonetizationTier,
        isProUnlocked: Boolean,
        isPromotionalUnlock: Boolean,
        purchaseToken: String? = null,
        sku: String? = null,
        expiryMs: Long = 0L
    ) {
        val updated = LicenseStatus(
            tier = tier,
            isProUnlocked = isProUnlocked,
            isPromotionalUnlock = isPromotionalUnlock,
            purchaseToken = purchaseToken,
            sku = sku,
            licenseExpiryMs = expiryMs
        )
        persistLicense(updated)
        _licenseStatus.value = updated
    }

    /**
     * Records a successful Google Play purchase token and upgrades the license status.
     * Ready to be hooked directly to Google Play Billing `PurchasesUpdatedListener`.
     */
    fun recordPurchase(sku: String, purchaseToken: String, tier: MonetizationTier) {
        setLicenseState(
            tier = tier,
            isProUnlocked = true,
            isPromotionalUnlock = false,
            purchaseToken = purchaseToken,
            sku = sku,
            expiryMs = 0L // Permanent unless subscription with known expiry
        )
    }

    /**
     * Developer override helper to test and verify future paywalls or feature restrictions.
     */
    fun setDeveloperFeatureGating(enabled: Boolean) {
        val current = _licenseStatus.value
        val updated = current.copy(
            isProUnlocked = !enabled,
            isPromotionalUnlock = !enabled
        )
        persistLicense(updated)
        _licenseStatus.value = updated
    }

    /**
     * Resets licensing to the default community unlock state (v3.2 baseline).
     */
    fun resetToCommunityDefault() {
        storage.clear()
        val defaultState = LicenseStatus(
            tier = MonetizationTier.COMMUNITY_FREE,
            isProUnlocked = true,
            isPromotionalUnlock = true
        )
        _licenseStatus.value = defaultState
    }

    /**
     * Returns the formal developer rights and commercial disclosure statement for the UI.
     */
    fun getMonetizationNotice(): String {
        return "gobeats is currently provided free of charge with all features unlocked. " +
                "The developer reserves complete autonomy to introduce optional PRO features, " +
                "in-app purchases, or subscription licenses in future releases."
    }

    private fun loadPersistedLicense(): LicenseStatus {
        val tierId = storage.getString(KEY_TIER_ID, MonetizationTier.COMMUNITY_FREE.tierId)
            ?: MonetizationTier.COMMUNITY_FREE.tierId
        val tier = MonetizationTier.values().find { it.tierId == tierId } ?: MonetizationTier.COMMUNITY_FREE

        // Default to true for v3.2 community release
        val isUnlocked = storage.getBoolean(KEY_IS_PRO_UNLOCKED, true)
        val isPromo = storage.getBoolean(KEY_IS_PROMO_UNLOCK, true)
        val purchaseToken = storage.getString(KEY_PURCHASE_TOKEN, null)
        val sku = storage.getString(KEY_SKU, null)
        val expiryMs = storage.getLong(KEY_EXPIRY_MS, 0L)

        return LicenseStatus(
            tier = tier,
            isProUnlocked = isUnlocked,
            isPromotionalUnlock = isPromo,
            purchaseToken = purchaseToken,
            sku = sku,
            licenseExpiryMs = expiryMs
        )
    }

    private fun persistLicense(status: LicenseStatus) {
        storage.putString(KEY_TIER_ID, status.tier.tierId)
        storage.putBoolean(KEY_IS_PRO_UNLOCKED, status.isProUnlocked)
        storage.putBoolean(KEY_IS_PROMO_UNLOCK, status.isPromotionalUnlock)
        storage.putString(KEY_PURCHASE_TOKEN, status.purchaseToken)
        storage.putString(KEY_SKU, status.sku)
        storage.putLong(KEY_EXPIRY_MS, status.licenseExpiryMs)
    }

    companion object {
        private const val KEY_TIER_ID = "tier_id"
        private const val KEY_IS_PRO_UNLOCKED = "is_pro_unlocked"
        private const val KEY_IS_PROMO_UNLOCK = "is_promo_unlock"
        private const val KEY_PURCHASE_TOKEN = "purchase_token"
        private const val KEY_SKU = "sku"
        private const val KEY_EXPIRY_MS = "expiry_ms"
        private const val KEY_FEATURE_OVERRIDE_PREFIX = "override_feat_"
    }
}
