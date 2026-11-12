package com.ratheshr8.beaconsimulator.ble

import android.content.Context
import java.util.UUID
import org.json.JSONObject

/**
 * Persists user-assigned display names for beacons.
 * Primary key is uuid|major|minor (stable across random MACs).
 */
class DeviceNameStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAll(): Map<String, String> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.getString(key))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveAll(names: Map<String, String>) {
        val obj = JSONObject()
        names.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    companion object {
        private const val PREFS = "beacon_device_names"
        private const val KEY = "names_json"

        fun keyFor(uuid: UUID, major: Int, minor: Int): String =
            "${uuid.toString().uppercase()}|$major|$minor"

        fun keyForAddress(address: String): String = "addr:${address.uppercase()}"
    }
}
