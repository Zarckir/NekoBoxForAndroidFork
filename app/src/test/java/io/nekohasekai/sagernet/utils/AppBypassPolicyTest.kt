package io.nekohasekai.sagernet.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBypassPolicyTest {

    @Test
    fun parsePackageNames_trimsCommentsAndDuplicates() {
        val packages = AppBypassPolicy.parsePackageNames(
            """
            ru.example.first
            # comment
              ru.example.second  
            ru.example.first
            """.trimIndent()
        )

        assertEquals(
            linkedSetOf("ru.example.first", "ru.example.second"),
            packages
        )
    }

    @Test
    fun matchingInstalledPackages_preservesAssetOrder() {
        val matchingPackages = AppBypassPolicy.matchingInstalledPackages(
            installedPackages = setOf("ru.third", "ru.second"),
            bypassPackages = listOf("ru.first", "ru.second", "ru.third"),
        )

        assertEquals(listOf("ru.second", "ru.third"), matchingPackages)
    }

    @Test
    fun shouldBootstrapDefaults_requiresFreshInstallAndMissingState() {
        assertTrue(
            AppBypassPolicy.shouldBootstrapDefaults(
                bootstrapVersion = null,
                proxyApps = null,
                bypass = null,
                individual = null,
                firstInstallTime = 10L,
                lastUpdateTime = 10L,
            )
        )
        assertFalse(
            AppBypassPolicy.shouldBootstrapDefaults(
                bootstrapVersion = null,
                proxyApps = true,
                bypass = null,
                individual = null,
                firstInstallTime = 10L,
                lastUpdateTime = 10L,
            )
        )
        assertFalse(
            AppBypassPolicy.shouldBootstrapDefaults(
                bootstrapVersion = null,
                proxyApps = null,
                bypass = null,
                individual = null,
                firstInstallTime = 10L,
                lastUpdateTime = 11L,
            )
        )
        assertFalse(
            AppBypassPolicy.shouldBootstrapDefaults(
                bootstrapVersion = 1L,
                proxyApps = null,
                bypass = null,
                individual = null,
                firstInstallTime = 10L,
                lastUpdateTime = 10L,
            )
        )
    }
}
