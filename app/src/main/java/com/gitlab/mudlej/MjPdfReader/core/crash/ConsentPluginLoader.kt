// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.crash

import org.acra.config.CoreConfiguration
import org.acra.plugins.Plugin
import org.acra.plugins.PluginLoader
import org.acra.plugins.ServicePluginLoader

class ConsentPluginLoader : PluginLoader {

    private val services = ServicePluginLoader()

    override fun <T : Plugin> load(clazz: Class<T>): List<T> {
        return services.load(clazz) + guard(clazz)
    }

    override fun <T : Plugin> loadEnabled(config: CoreConfiguration, clazz: Class<T>): List<T> {
        return services.loadEnabled(config, clazz) + guard(clazz).filter { it.enabled(config) }
    }

    private fun <T : Plugin> guard(clazz: Class<T>): List<T> {
        val guard = CrashConsentGuard()
        return if (clazz.isInstance(guard)) listOf(clazz.cast(guard)) else emptyList()
    }
}
