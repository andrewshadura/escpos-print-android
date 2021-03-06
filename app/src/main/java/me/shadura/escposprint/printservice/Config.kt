package me.shadura.escposprint.printservice

import android.content.SharedPreferences
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import me.shadura.escposprint.L
import me.shadura.escpos.PrinterModel

import me.shadura.escposprint.detect.PrinterRec

@Serializable
class Config {
    var configuredPrinters = mutableMapOf<String, PrinterRec>()

    @UseExperimental(ImplicitReflectionSerializer::class)
    fun write(prefs: SharedPreferences) {
        with (prefs.edit()) {
            putString(PREF_CONFIG, Json.stringify(this@Config))
            apply()
        }
        L.i("wrote json: %s".format(prefs.getString(PREF_CONFIG, "")))
    }

    companion object {
        const val SHARED_PREFS_PRINTERS = "printers"

        private const val PREF_CONFIG = "config"

        @UseExperimental(ImplicitReflectionSerializer::class)
        fun read(prefs: SharedPreferences) : Config {
            val config = prefs.getString(PREF_CONFIG, "") ?: ""
            L.i("read json: %s".format(config))
            return if (config.isNotBlank()) {
                Json.nonstrict.parse(config)
            } else {
                Config()
            }
        }
    }
}
