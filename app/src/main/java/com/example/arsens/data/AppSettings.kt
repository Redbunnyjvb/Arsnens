package com.example.arsens.data

import android.content.Context

/** App-brede instellingen (geen projectdata) — bewaard in SharedPreferences, los van de
 *  projectbestanden zodat ze voor álle projecten gelden. */
class AppSettings(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("arsens_settings", Context.MODE_PRIVATE)

    /** Naam van de gekozen AprilTag-dictionary (enum-naam van TagDictionaryOption). */
    var tagDictionaryName: String?
        get() = prefs.getString(KEY_TAG_DICTIONARY, null)
        set(value) {
            prefs.edit().putString(KEY_TAG_DICTIONARY, value).apply()
        }

    /** Naam van de gekozen pose-modus (enum-naam van TagPoseMode). */
    var tagPoseModeName: String?
        get() = prefs.getString(KEY_TAG_POSE_MODE, null)
        set(value) {
            prefs.edit().putString(KEY_TAG_POSE_MODE, value).apply()
        }

    /** Standaard tagformaat (zwart vierkant) in mm voor nieuwe referentietags. */
    var defaultTagSizeMm: Int
        get() = prefs.getInt(KEY_TAG_SIZE, 100)
        set(value) {
            prefs.edit().putInt(KEY_TAG_SIZE, value).apply()
        }

    /** Eerste tag-ID dat voor SENSOR-tags gereserveerd is; referentietags blijven eronder. */
    var sensorTagStartId: Int
        get() = prefs.getInt(KEY_SENSOR_TAG_START, 100)
        set(value) {
            prefs.edit().putInt(KEY_SENSOR_TAG_START, value).apply()
        }

    /** Formaat (zwart vierkant, mm) van de kleine AprilTags óp de sensoren. */
    var sensorTagSizeMm: Int
        get() = prefs.getInt(KEY_SENSOR_TAG_SIZE, 40)
        set(value) {
            prefs.edit().putInt(KEY_SENSOR_TAG_SIZE, value).apply()
        }

    private companion object {
        const val KEY_TAG_DICTIONARY = "tag_dictionary"
        const val KEY_TAG_POSE_MODE = "tag_pose_mode"
        const val KEY_TAG_SIZE = "tag_size_mm"
        const val KEY_SENSOR_TAG_START = "sensor_tag_start_id"
        const val KEY_SENSOR_TAG_SIZE = "sensor_tag_size_mm"
    }
}
