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

    var showTagDistances: Boolean
        get() = prefs.getBoolean("show_tag_distances", false)
        set(value) { prefs.edit().putBoolean("show_tag_distances", value).apply() }

    /** Standaard tagformaat (zwart vierkant) in mm voor nieuwe referentietags. */
    var defaultTagSizeMm: Int
        get() = prefs.getInt(KEY_TAG_SIZE, 100)
        set(value) {
            prefs.edit().putInt(KEY_TAG_SIZE, value).apply()
        }

    /** Eerste tag-ID dat voor SENSOR-tags gereserveerd is; referentietags blijven eronder. */
    var sensorTagStartId: Int
        get() = prefs.getInt(KEY_SENSOR_TAG_START, 200)
        set(value) {
            prefs.edit().putInt(KEY_SENSOR_TAG_START, value).apply()
        }

    /** Koppelt bij het plaatsen van een sensor automatisch de sensor-tag (ID ≥ [sensorTagStartId])
     *  die onder de cursor staat, en leidt het sensornummer ervan af. Uit = handmatig plaatsen zonder
     *  tag-koppeling (oud gedrag). */
    var autoLinkSensorTagOnPlace: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LINK_SENSOR_TAG, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_LINK_SENSOR_TAG, value).apply()
        }

    /** Formaat (zwart vierkant, mm) van de kleine AprilTags óp de sensoren. */
    var sensorTagSizeMm: Int
        get() = prefs.getInt(KEY_SENSOR_TAG_SIZE, 40)
        set(value) {
            prefs.edit().putInt(KEY_SENSOR_TAG_SIZE, value).apply()
        }

    /** Corrigeer een sensorpositie automatisch zodra de bijbehorende tag weer stabiel in beeld komt
     *  (straal-replay). Wijzigt opgeslagen meetdata, dus opt-in: standaard uit. */
    var autoCorrectSensorDrift: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CORRECT_DRIFT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_CORRECT_DRIFT, value).apply()
        }

    private companion object {
        const val KEY_TAG_DICTIONARY = "tag_dictionary"
        const val KEY_TAG_POSE_MODE = "tag_pose_mode"
        const val KEY_TAG_SIZE = "tag_size_mm"
        const val KEY_SENSOR_TAG_START = "sensor_tag_start_id"
        const val KEY_SENSOR_TAG_SIZE = "sensor_tag_size_mm"
        const val KEY_AUTO_CORRECT_DRIFT = "auto_correct_sensor_drift"
        const val KEY_AUTO_LINK_SENSOR_TAG = "auto_link_sensor_tag_on_place"
    }
}
