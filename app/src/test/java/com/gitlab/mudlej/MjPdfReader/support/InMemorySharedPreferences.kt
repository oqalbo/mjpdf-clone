// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.support

import android.content.SharedPreferences

class InMemorySharedPreferences(initialValues: Map<String, Any?> = emptyMap()) : SharedPreferences {
    private val values = initialValues.filterValues { it != null }.mapValues { copyValue(it.value) }.toMutableMap()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    fun snapshot(): Map<String, Any?> = values.mapValues { copyValue(it.value) }
    override fun getAll(): MutableMap<String, *> = snapshot().toMutableMap()
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun getString(key: String, defValue: String?): String? = values[key]?.let { it as String } ?: defValue
    override fun getInt(key: String, defValue: Int): Int = values[key]?.let { it as Int } ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key]?.let { it as Long } ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key]?.let { it as Float } ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key]?.let { it as Boolean } ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = values[key] ?: return defValues
        return (value as Set<*>).map { it as String }.toMutableSet()
    }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }
    override fun edit(): SharedPreferences.Editor = Editor()

    private fun copyValue(value: Any?): Any? = if (value is Set<*>) value.toMutableSet() else value

    private inner class Editor : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var cleared = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor = apply { changes[key] = value }
        override fun putString(key: String, value: String?): SharedPreferences.Editor = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = put(key, values?.toSet())
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)
        override fun remove(key: String): SharedPreferences.Editor = put(key, null)
        override fun clear(): SharedPreferences.Editor = apply { cleared = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            val notifyClear = cleared
            if (cleared) values.clear()
            val changed = changes.filter { (key, value) ->
                if (value == null) values.containsKey(key) else values[key] != value
            }.keys.toList()
            changes.filterValues { it == null }.keys.forEach(values::remove)
            changes.filterValues { it != null }.forEach { (key, value) -> values[key] = copyValue(value) }
            changes.clear()
            cleared = false
            val registered = listeners.toList()
            if (notifyClear) registered.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, null) }
            changed.forEach { key ->
                registered.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) }
            }
        }
    }
}
