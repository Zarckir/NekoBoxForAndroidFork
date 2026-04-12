package io.nekohasekai.sagernet.route

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.ktx.app

data class RuntimeRouteRule(
    val stableId: Long? = null,
    val name: String,
    val config: String = "",
    val enabled: Boolean = true,
    val domains: String = "",
    val ip: String = "",
    val port: String = "",
    val sourcePort: String = "",
    val network: String = "",
    val source: String = "",
    val protocol: String = "",
    val outbound: Long = 0L,
    val packages: Set<String> = emptySet(),
)

data class SystemRouteRuleItem(
    val stableId: Long,
    val name: String,
    val summary: String,
    val outboundLabel: String,
)

enum class CustomRuleSlot(val baseOrder: Long) {
    BEFORE_ADS(10_000L),
    BETWEEN_ADS_AND_RU(30_000L),
    BETWEEN_RU_AND_QUIC(40_000L),
    AFTER_QUIC(60_000L),
}

data class CustomInsertion(
    val slot: CustomRuleSlot,
    val index: Int,
)

data class OrderedUserRules(
    val customBeforeAds: List<RuleEntity>,
    val adsRules: List<RuleEntity>,
    val customBetweenAdsAndRu: List<RuleEntity>,
    val customBetweenRuAndQuic: List<RuleEntity>,
    val quicRules: List<RuleEntity>,
    val customAfterQuic: List<RuleEntity>,
) {
    fun inPersistenceOrder(): List<RuleEntity> {
        return customBeforeAds +
            adsRules +
            customBetweenAdsAndRu +
            customBetweenRuAndQuic +
            quicRules +
            customAfterQuic
    }
}

object RouteRulePolicy {

    const val SYSTEM_RULE_RU_DOMAIN_ID = -1001L
    const val SYSTEM_RULE_RU_IP_ID = -1002L
    const val SYSTEM_RULE_RU_TLD_ID = -1003L

    private const val RUSSIA_LABEL = "Russia"
    private const val RU_TLD_LIST = "ru,рф,su,xn--p1ai"
    private const val ORDER_BAND_SIZE = 10_000L
    private const val ADS_ANCHOR_BASE_ORDER = 20_000L
    private const val QUIC_ANCHOR_BASE_ORDER = 50_000L

    internal fun systemRuleSeeds(): List<RuntimeRouteRule> = listOf(
        RuntimeRouteRule(
            stableId = SYSTEM_RULE_RU_DOMAIN_ID,
            name = "system-ru-domain",
            domains = "geosite:category-ru",
            outbound = -1L
        ),
        RuntimeRouteRule(
            stableId = SYSTEM_RULE_RU_IP_ID,
            name = "system-ru-ip",
            ip = "geoip:ru",
            outbound = -1L
        ),
        RuntimeRouteRule(
            stableId = SYSTEM_RULE_RU_TLD_ID,
            name = "system-ru-tld",
            domains = RU_TLD_LIST,
            outbound = -1L
        )
    )

    internal fun defaultUserRuleSeeds(): List<RuntimeRouteRule> = listOf(
        RuntimeRouteRule(
            name = "default-block-ads",
            domains = "geosite:category-ads-all",
            outbound = -2L
        ),
        RuntimeRouteRule(
            name = "default-block-quic",
            port = "443",
            network = "udp",
            outbound = -2L
        ),
    )

    fun runtimeRules(userRules: List<RuleEntity>): List<RuntimeRouteRule> {
        val orderedUsers = orderedUserRules(userRules.filter { it.enabled })
        return buildList {
            addAll(orderedUsers.customBeforeAds.map { it.toRuntimeRouteRule() })
            addAll(orderedUsers.adsRules.map { it.toRuntimeRouteRule() })
            addAll(orderedUsers.customBetweenAdsAndRu.map { it.toRuntimeRouteRule() })
            addAll(systemRuleSeeds())
            addAll(orderedUsers.customBetweenRuAndQuic.map { it.toRuntimeRouteRule() })
            addAll(orderedUsers.quicRules.map { it.toRuntimeRouteRule() })
            addAll(orderedUsers.customAfterQuic.map { it.toRuntimeRouteRule() })
        }
    }

    fun createDefaultUserRules(): List<RuleEntity> {
        val defaults = defaultUserRuleSeeds().map { seed ->
            RuleEntity(
                name = localizedDefaultName(seed.name),
                config = seed.config,
                enabled = true,
                domains = seed.domains,
                ip = seed.ip,
                port = seed.port,
                sourcePort = seed.sourcePort,
                network = seed.network,
                source = seed.source,
                protocol = seed.protocol,
                outbound = seed.outbound,
                packages = seed.packages,
            )
        }
        return normalizeUserRules(defaults)
    }

    fun systemRouteItems(): List<SystemRouteRuleItem> {
        return systemRuleSeeds().map { rule ->
            SystemRouteRuleItem(
                stableId = rule.stableId!!,
                name = localizedSystemName(rule.stableId),
                summary = rule.mkSummary(),
                outboundLabel = displayOutbound(rule.outbound),
            )
        }
    }

    fun isPinnedUserRule(rule: RuleEntity): Boolean {
        return isAdsRule(rule) || isQuicRule(rule)
    }

    fun prepareNewRule(rule: RuleEntity, existingRules: List<RuleEntity>) {
        rule.userOrder = when {
            isAdsRule(rule) -> nextOrder(existingRules, ADS_ANCHOR_BASE_ORDER)
            isQuicRule(rule) -> nextOrder(existingRules, QUIC_ANCHOR_BASE_ORDER)
            else -> nextOrder(existingRules, CustomRuleSlot.AFTER_QUIC.baseOrder)
        }
    }

    fun normalizeUserRules(userRules: List<RuleEntity>): List<RuleEntity> {
        return rebuildUserRules(orderedUserRules(userRules))
    }

    fun resolveCustomInsertion(
        ordered: OrderedUserRules,
        insertionPosition: Int,
        systemRuleCount: Int = systemRuleSeeds().size,
    ): CustomInsertion {
        val safePosition = insertionPosition.coerceAtLeast(1)
        val beforeAdsBoundary = 1 + ordered.customBeforeAds.size
        val betweenAdsAndRuBoundary =
            beforeAdsBoundary + ordered.adsRules.size + ordered.customBetweenAdsAndRu.size
        val betweenRuAndQuicBoundary =
            betweenAdsAndRuBoundary + systemRuleCount + ordered.customBetweenRuAndQuic.size
        val afterQuicBoundary = betweenRuAndQuicBoundary + ordered.quicRules.size

        return when {
            safePosition <= beforeAdsBoundary -> CustomInsertion(
                CustomRuleSlot.BEFORE_ADS,
                (safePosition - 1).coerceIn(0, ordered.customBeforeAds.size)
            )

            safePosition <= betweenAdsAndRuBoundary -> CustomInsertion(
                CustomRuleSlot.BETWEEN_ADS_AND_RU,
                (safePosition - beforeAdsBoundary - ordered.adsRules.size).coerceIn(
                    0,
                    ordered.customBetweenAdsAndRu.size
                )
            )

            safePosition <= betweenRuAndQuicBoundary -> CustomInsertion(
                CustomRuleSlot.BETWEEN_RU_AND_QUIC,
                (safePosition - betweenAdsAndRuBoundary - systemRuleCount).coerceIn(
                    0,
                    ordered.customBetweenRuAndQuic.size
                )
            )

            else -> CustomInsertion(
                CustomRuleSlot.AFTER_QUIC,
                (safePosition - afterQuicBoundary).coerceIn(0, ordered.customAfterQuic.size)
            )
        }
    }

    fun insertCustomRule(
        ordered: OrderedUserRules,
        rule: RuleEntity,
        insertion: CustomInsertion,
    ): List<RuleEntity> {
        val customBeforeAds = ordered.customBeforeAds.toMutableList()
        val customBetweenAdsAndRu = ordered.customBetweenAdsAndRu.toMutableList()
        val customBetweenRuAndQuic = ordered.customBetweenRuAndQuic.toMutableList()
        val customAfterQuic = ordered.customAfterQuic.toMutableList()
        when (insertion.slot) {
            CustomRuleSlot.BEFORE_ADS -> customBeforeAds.add(
                insertion.index.coerceIn(0, customBeforeAds.size),
                rule
            )

            CustomRuleSlot.BETWEEN_ADS_AND_RU -> customBetweenAdsAndRu.add(
                insertion.index.coerceIn(0, customBetweenAdsAndRu.size),
                rule
            )

            CustomRuleSlot.BETWEEN_RU_AND_QUIC -> customBetweenRuAndQuic.add(
                insertion.index.coerceIn(0, customBetweenRuAndQuic.size),
                rule
            )

            CustomRuleSlot.AFTER_QUIC -> customAfterQuic.add(
                insertion.index.coerceIn(0, customAfterQuic.size),
                rule
            )
        }
        return rebuildUserRules(
            OrderedUserRules(
                customBeforeAds = customBeforeAds,
                adsRules = ordered.adsRules,
                customBetweenAdsAndRu = customBetweenAdsAndRu,
                customBetweenRuAndQuic = customBetweenRuAndQuic,
                quicRules = ordered.quicRules,
                customAfterQuic = customAfterQuic,
            )
        )
    }

    fun orderedUserRules(userRules: List<RuleEntity>): OrderedUserRules {
        val customBeforeAds = ArrayList<RuleEntity>()
        val adsRules = ArrayList<RuleEntity>()
        val customBetweenAdsAndRu = ArrayList<RuleEntity>()
        val customBetweenRuAndQuic = ArrayList<RuleEntity>()
        val quicRules = ArrayList<RuleEntity>()
        val customAfterQuic = ArrayList<RuleEntity>()

        userRules.sortedWith(compareBy<RuleEntity> { it.userOrder }.thenBy { it.id }).forEach { rule ->
            when {
                isAdsRule(rule) -> adsRules += rule
                isQuicRule(rule) -> quicRules += rule
                else -> when (customSlot(rule)) {
                    CustomRuleSlot.BEFORE_ADS -> customBeforeAds += rule
                    CustomRuleSlot.BETWEEN_ADS_AND_RU -> customBetweenAdsAndRu += rule
                    CustomRuleSlot.BETWEEN_RU_AND_QUIC -> customBetweenRuAndQuic += rule
                    CustomRuleSlot.AFTER_QUIC -> customAfterQuic += rule
                }
            }
        }

        return OrderedUserRules(
            customBeforeAds = customBeforeAds,
            adsRules = adsRules,
            customBetweenAdsAndRu = customBetweenAdsAndRu,
            customBetweenRuAndQuic = customBetweenRuAndQuic,
            quicRules = quicRules,
            customAfterQuic = customAfterQuic,
        )
    }

    private fun rebuildUserRules(ordered: OrderedUserRules): List<RuleEntity> {
        assignUserOrder(ordered.customBeforeAds, CustomRuleSlot.BEFORE_ADS.baseOrder)
        assignUserOrder(ordered.adsRules, ADS_ANCHOR_BASE_ORDER)
        assignUserOrder(ordered.customBetweenAdsAndRu, CustomRuleSlot.BETWEEN_ADS_AND_RU.baseOrder)
        assignUserOrder(
            ordered.customBetweenRuAndQuic,
            CustomRuleSlot.BETWEEN_RU_AND_QUIC.baseOrder
        )
        assignUserOrder(ordered.quicRules, QUIC_ANCHOR_BASE_ORDER)
        assignUserOrder(ordered.customAfterQuic, CustomRuleSlot.AFTER_QUIC.baseOrder)
        return ordered.inPersistenceOrder()
    }

    private fun assignUserOrder(rules: List<RuleEntity>, baseOrder: Long) {
        rules.forEachIndexed { index, rule ->
            rule.userOrder = baseOrder + index
        }
    }

    private fun nextOrder(existingRules: List<RuleEntity>, baseOrder: Long): Long {
        return existingRules
            .asSequence()
            .map { it.userOrder }
            .filter { it.inBand(baseOrder) }
            .maxOrNull()
            ?.plus(1)
            ?: baseOrder
    }

    private fun customSlot(rule: RuleEntity): CustomRuleSlot {
        return when {
            rule.userOrder.inBand(CustomRuleSlot.BEFORE_ADS.baseOrder) -> CustomRuleSlot.BEFORE_ADS
            rule.userOrder.inBand(CustomRuleSlot.BETWEEN_ADS_AND_RU.baseOrder) -> {
                CustomRuleSlot.BETWEEN_ADS_AND_RU
            }

            rule.userOrder.inBand(CustomRuleSlot.BETWEEN_RU_AND_QUIC.baseOrder) -> {
                CustomRuleSlot.BETWEEN_RU_AND_QUIC
            }

            rule.userOrder.inBand(CustomRuleSlot.AFTER_QUIC.baseOrder) -> CustomRuleSlot.AFTER_QUIC
            rule.userOrder.inBand(ADS_ANCHOR_BASE_ORDER) -> CustomRuleSlot.BETWEEN_ADS_AND_RU
            rule.userOrder.inBand(QUIC_ANCHOR_BASE_ORDER) -> CustomRuleSlot.BETWEEN_RU_AND_QUIC
            else -> CustomRuleSlot.AFTER_QUIC
        }
    }

    private fun localizedSystemName(stableId: Long): String {
        return when (stableId) {
            SYSTEM_RULE_RU_DOMAIN_ID -> app.getString(R.string.route_bypass_domain, RUSSIA_LABEL)
            SYSTEM_RULE_RU_IP_ID -> app.getString(R.string.route_bypass_ip, RUSSIA_LABEL)
            SYSTEM_RULE_RU_TLD_ID -> "ru"
            else -> error("Unknown system route rule: $stableId")
        }
    }

    private fun localizedDefaultName(debugName: String): String {
        return when (debugName) {
            "default-block-quic" -> app.getString(R.string.route_opt_block_quic)
            "default-block-ads" -> app.getString(R.string.route_opt_block_ads)
            else -> debugName
        }
    }

    private fun isAdsRule(rule: RuleEntity): Boolean {
        return rule.config.isBlank() &&
            rule.domains == "geosite:category-ads-all" &&
            rule.ip.isBlank() &&
            rule.port.isBlank() &&
            rule.sourcePort.isBlank() &&
            rule.network.isBlank() &&
            rule.source.isBlank() &&
            rule.protocol.isBlank() &&
            rule.outbound == -2L &&
            rule.packages.isEmpty()
    }

    private fun isQuicRule(rule: RuleEntity): Boolean {
        return rule.config.isBlank() &&
            rule.domains.isBlank() &&
            rule.ip.isBlank() &&
            rule.port == "443" &&
            rule.sourcePort.isBlank() &&
            rule.network == "udp" &&
            rule.source.isBlank() &&
            rule.protocol.isBlank() &&
            rule.outbound == -2L &&
            rule.packages.isEmpty()
    }

    private fun Long.inBand(baseOrder: Long): Boolean {
        return this in baseOrder until (baseOrder + ORDER_BAND_SIZE)
    }
}

private fun RuleEntity.toRuntimeRouteRule(): RuntimeRouteRule {
    return RuntimeRouteRule(
        stableId = id,
        name = displayName(),
        config = config,
        enabled = enabled,
        domains = domains,
        ip = ip,
        port = port,
        sourcePort = sourcePort,
        network = network,
        source = source,
        protocol = protocol,
        outbound = outbound,
        packages = packages,
    )
}

fun RuntimeRouteRule.mkSummary(): String {
    var summary = ""
    if (config.isNotBlank()) summary += "[config]\n"
    if (domains.isNotBlank()) summary += "$domains\n"
    if (ip.isNotBlank()) summary += "$ip\n"
    if (source.isNotBlank()) summary += "src ip: $source\n"
    if (sourcePort.isNotBlank()) summary += "src port: $sourcePort\n"
    if (port.isNotBlank()) summary += "dst port: $port\n"
    if (network.isNotBlank()) summary += "network: $network\n"
    if (protocol.isNotBlank()) summary += "protocol: $protocol\n"
    if (packages.isNotEmpty()) {
        summary += app.getString(R.string.apps_message, packages.size) + "\n"
    }
    val lines = summary.trim().split("\n")
    return if (lines.size > 3) {
        lines.subList(0, 3).joinToString("\n", postfix = "\n...")
    } else {
        summary.trim()
    }
}

fun displayOutbound(outbound: Long): String {
    return when (outbound) {
        0L -> app.getString(R.string.route_proxy)
        -1L -> app.getString(R.string.route_bypass)
        -2L -> app.getString(R.string.route_block)
        else -> ProfileManager.getProfile(outbound)?.displayName()
            ?: app.getString(R.string.error_title)
    }
}
