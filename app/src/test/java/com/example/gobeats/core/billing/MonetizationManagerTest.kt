package com.example.gobeats.core.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MonetizationManagerTest {

    private lateinit var storage: InMemoryMonetizationStorage
    private lateinit var manager: MonetizationManager

    @Before
    fun setup() {
        storage = InMemoryMonetizationStorage()
        manager = MonetizationManager(storage = storage)
    }

    @Test
    fun `default state in v3_2 grants full pro access under promotional community tier`() {
        val status = manager.licenseStatus.value

        assertEquals(MonetizationTier.COMMUNITY_FREE, status.tier)
        assertTrue(status.isProUnlocked)
        assertTrue(status.isPromotionalUnlock)

        // All features must evaluate to unlocked
        ProFeature.values().forEach { feature ->
            assertTrue("Feature ${feature.name} must be unlocked by default", manager.isFeatureUnlocked(feature))
        }
    }

    @Test
    fun `recordPurchase upgrades status and records SKU and purchase token`() {
        manager.recordPurchase(
            sku = "gobeats_lifetime_pro",
            purchaseToken = "token_xyz_98765",
            tier = MonetizationTier.PRO_LIFETIME
        )

        val updated = manager.licenseStatus.value
        assertEquals(MonetizationTier.PRO_LIFETIME, updated.tier)
        assertTrue(updated.isProUnlocked)
        assertFalse(updated.isPromotionalUnlock)
        assertEquals("gobeats_lifetime_pro", updated.sku)
        assertEquals("token_xyz_98765", updated.purchaseToken)

        // Features remain fully unlocked
        assertTrue(manager.isFeatureUnlocked(ProFeature.OFFLINE_AI_SUBTITLES))
        assertTrue(manager.isFeatureUnlocked(ProFeature.STUDIO_DSP_EQ))
    }

    @Test
    fun `developer feature gating override allows testing paywalled state`() {
        // Developer enables feature gating (simulating future paywall)
        manager.setDeveloperFeatureGating(true)

        val status = manager.licenseStatus.value
        assertFalse(status.isProUnlocked)
        assertFalse(status.isPromotionalUnlock)

        // Features without explicit override should now return false
        assertFalse(manager.isFeatureUnlocked(ProFeature.OFFLINE_AI_SUBTITLES))
        assertFalse(manager.isFeatureUnlocked(ProFeature.TURBO_PC_SYNC))

        // Reset back to community default
        manager.resetToCommunityDefault()
        assertTrue(manager.licenseStatus.value.isProUnlocked)
        assertTrue(manager.isFeatureUnlocked(ProFeature.OFFLINE_AI_SUBTITLES))
    }

    @Test
    fun `getMonetizationNotice provides legal transparency notice`() {
        val notice = manager.getMonetizationNotice()

        assertTrue(notice.contains("free of charge"))
        assertTrue(notice.contains("reserves complete autonomy"))
    }

    @Test
    fun `state persists across MonetizationManager instances via storage`() {
        manager.recordPurchase(
            sku = "gobeats_subscription_annual",
            purchaseToken = "sub_token_112233",
            tier = MonetizationTier.PRO_SUBSCRIPTION
        )

        // Create new instance with the same storage
        val newInstance = MonetizationManager(storage = storage)
        val loadedStatus = newInstance.licenseStatus.value

        assertEquals(MonetizationTier.PRO_SUBSCRIPTION, loadedStatus.tier)
        assertTrue(loadedStatus.isProUnlocked)
        assertEquals("gobeats_subscription_annual", loadedStatus.sku)
        assertEquals("sub_token_112233", loadedStatus.purchaseToken)
    }
}
