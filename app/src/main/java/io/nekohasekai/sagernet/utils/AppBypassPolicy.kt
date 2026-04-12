package io.nekohasekai.sagernet.utils

import android.content.Context
import io.nekohasekai.sagernet.ktx.app
import moe.matsuri.nb4a.utils.NGUtil

object AppBypassPolicy {

    const val BYPASS_ASSET = "bypass_ru_packagename.txt"
    const val BOOTSTRAP_VERSION = 1L

    fun parsePackageNames(content: String): LinkedHashSet<String> {
        val packages = LinkedHashSet<String>()
        content.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .forEach { packages.add(it) }
        return packages
    }

    fun readPackageNames(context: Context = app): LinkedHashSet<String> = try {
        parsePackageNames(NGUtil.readTextFromAssets(context, BYPASS_ASSET))
    } catch (_: Exception) {
        linkedSetOf()
    }

    fun matchingInstalledPackages(
        installedPackages: Set<String>,
        bypassPackages: Iterable<String>,
    ): List<String> {
        return bypassPackages.filterTo(ArrayList()) { installedPackages.contains(it) }
    }

    fun shouldBootstrapDefaults(
        bootstrapVersion: Long?,
        proxyApps: Boolean?,
        bypass: Boolean?,
        individual: String?,
        firstInstallTime: Long,
        lastUpdateTime: Long,
    ): Boolean {
        return bootstrapVersion == null &&
            proxyApps == null &&
            bypass == null &&
            individual == null &&
            firstInstallTime == lastUpdateTime
    }
}
