package io.nekohasekai.sagernet.route

import io.nekohasekai.sagernet.database.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRulePolicyTest {

    @Test
    fun systemRuleSeeds_areFixedBypassRules() {
        val seeds = RouteRulePolicy.systemRuleSeeds()

        assertEquals(3, seeds.size)
        assertEquals(
            listOf(
                RouteRulePolicy.SYSTEM_RULE_RU_DOMAIN_ID,
                RouteRulePolicy.SYSTEM_RULE_RU_IP_ID,
                RouteRulePolicy.SYSTEM_RULE_RU_TLD_ID,
            ),
            seeds.map { it.stableId }
        )
        assertTrue(seeds.all { it.outbound == -1L })
    }

    @Test
    fun runtimeRules_followAnchorLayoutAndCustomSlots() {
        val beforeAdsRule = RuleEntity(
            id = 1L,
            name = "before-ads",
            userOrder = 10_000L,
            enabled = true,
            domains = "before-ads.example",
            outbound = 0L,
        )
        val adsRule = RuleEntity(
            id = 10L,
            name = "ads",
            userOrder = 20_000L,
            enabled = true,
            domains = "geosite:category-ads-all",
            outbound = -2L,
        )
        val betweenAdsAndRuRule = RuleEntity(
            id = 2L,
            name = "between-ads-ru",
            userOrder = 30_000L,
            enabled = true,
            domains = "between-ads-ru.example",
            outbound = 0L,
        )
        val betweenRuAndQuicRule = RuleEntity(
            id = 3L,
            name = "between-ru-quic",
            userOrder = 40_000L,
            enabled = true,
            domains = "between-ru-quic.example",
            outbound = 0L,
        )
        val quicRule = RuleEntity(
            id = 20L,
            name = "quic",
            userOrder = 50_000L,
            enabled = true,
            port = "443",
            network = "udp",
            outbound = -2L,
        )
        val customRule = RuleEntity(
            id = 99L,
            name = "user-rule",
            userOrder = 60_000L,
            enabled = true,
            domains = "example.com",
            outbound = 0L,
        )

        val runtimeRules = RouteRulePolicy.runtimeRules(
            listOf(
                quicRule,
                customRule,
                adsRule,
                beforeAdsRule,
                betweenAdsAndRuRule,
                betweenRuAndQuicRule,
            )
        )

        assertEquals(9, runtimeRules.size)
        assertEquals(1L, runtimeRules[0].stableId)
        assertEquals(10L, runtimeRules[1].stableId)
        assertEquals(2L, runtimeRules[2].stableId)
        assertEquals(
            listOf(
                RouteRulePolicy.SYSTEM_RULE_RU_DOMAIN_ID,
                RouteRulePolicy.SYSTEM_RULE_RU_IP_ID,
                RouteRulePolicy.SYSTEM_RULE_RU_TLD_ID,
            ),
            runtimeRules.subList(3, 6).map { it.stableId }
        )
        assertEquals(3L, runtimeRules[6].stableId)
        assertEquals(20L, runtimeRules[7].stableId)
        assertEquals(99L, runtimeRules.last().stableId)
    }

    @Test
    fun defaultUserRuleSeeds_containOnlyEditableDefaults() {
        val seeds = RouteRulePolicy.defaultUserRuleSeeds()

        assertEquals(2, seeds.size)
        assertEquals(listOf("default-block-ads", "default-block-quic"), seeds.map { it.name })
        assertTrue(seeds.all { it.outbound == -2L })
    }

    @Test
    fun normalizeUserRules_movesLegacyAndEditedAnchorsIntoCustomBands() {
        val legacyCustom = RuleEntity(id = 1L, userOrder = 1L, enabled = true, domains = "legacy")
        val editedAdsAnchor = RuleEntity(
            id = 2L,
            userOrder = 20_000L,
            enabled = true,
            domains = "edited-ads-anchor"
        )
        val editedQuicAnchor = RuleEntity(
            id = 3L,
            userOrder = 50_000L,
            enabled = true,
            domains = "edited-quic-anchor"
        )

        val normalized = RouteRulePolicy.normalizeUserRules(
            listOf(legacyCustom, editedAdsAnchor, editedQuicAnchor)
        )

        assertEquals(listOf(30_000L, 40_000L, 60_000L), normalized.map { it.userOrder })
    }

    @Test
    fun prepareNewRule_placesCustomRulesAfterQuicByDefault() {
        val existingRules = listOf(
            RuleEntity(
                id = 10L,
                userOrder = 20_000L,
                enabled = true,
                domains = "geosite:category-ads-all",
                outbound = -2L,
            ),
            RuleEntity(
                id = 20L,
                userOrder = 50_000L,
                enabled = true,
                port = "443",
                network = "udp",
                outbound = -2L,
            ),
            RuleEntity(
                id = 30L,
                userOrder = 60_000L,
                enabled = true,
                domains = "example.com",
                outbound = 0L,
            ),
        )
        val newCustom = RuleEntity(enabled = true, domains = "later.example", outbound = 0L)

        RouteRulePolicy.prepareNewRule(newCustom, existingRules)

        assertEquals(60_001L, newCustom.userOrder)
    }
}
