package com.ratheshr8.beaconsimulator.floorplan

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class FloorPlanStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val imagesDir = File(appContext.filesDir, "floor_plans").also { it.mkdirs() }

    fun loadAll(): List<FloorPlan> {
        val raw = prefs.getString(KEY_PLANS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(parsePlan(array.getJSONObject(i)))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAll(plans: List<FloorPlan>) {
        val array = JSONArray()
        plans.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_PLANS, array.toString()).apply()
    }

    fun importImage(uri: Uri): String? {
        return runCatching {
            val dest = File(imagesDir, "plan_${UUID.randomUUID()}.jpg")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        }.getOrNull()
    }

    fun deleteImage(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun toJson(plan: FloorPlan): JSONObject {
        val beacons = JSONArray()
        plan.beacons.forEach { b ->
            beacons.put(
                JSONObject()
                    .put("id", b.id)
                    .put("label", b.label)
                    .put("uuid", b.uuid.toString())
                    .put("major", b.major)
                    .put("minor", b.minor)
                    .put("x", b.x.toDouble())
                    .put("y", b.y.toDouble()),
            )
        }
        return JSONObject()
            .put("id", plan.id)
            .put("name", plan.name)
            .put("imagePath", plan.imagePath)
            .put("widthMeters", plan.widthMeters.toDouble())
            .put("beacons", beacons)
    }

    private fun parsePlan(obj: JSONObject): FloorPlan {
        val beaconsJson = obj.optJSONArray("beacons") ?: JSONArray()
        val beacons = buildList {
            for (i in 0 until beaconsJson.length()) {
                val b = beaconsJson.getJSONObject(i)
                add(
                    PlacedBeacon(
                        id = b.getString("id"),
                        label = b.optString("label", "Beacon"),
                        uuid = UUID.fromString(b.getString("uuid")),
                        major = b.getInt("major"),
                        minor = b.getInt("minor"),
                        x = b.getDouble("x").toFloat(),
                        y = b.getDouble("y").toFloat(),
                    ),
                )
            }
        }
        return FloorPlan(
            id = obj.getString("id"),
            name = obj.getString("name"),
            imagePath = obj.optString("imagePath", "")
                .takeIf { it.isNotBlank() && it != "null" },
            widthMeters = obj.optDouble("widthMeters", 20.0).toFloat(),
            beacons = beacons,
        )
    }

    companion object {
        private const val PREFS = "floor_plans"
        private const val KEY_PLANS = "plans_json"
    }
}
