package com.youtube.spawnpk.models

import com.google.gson.Gson
import java.io.File

data class Settings(
    var ingameName: String = "",
    var clientSecretsPath: String = "",
    var darkTheme: Boolean = true
) {
    companion object {
        private const val SETTINGS_FILE = "settings.json"
        private val gson = Gson()

        fun load(): Settings {
            val file = File(SETTINGS_FILE)
            return if (file.exists()) {
                gson.fromJson(file.readText(), Settings::class.java)
            } else {
                Settings()
            }
        }
    }

    fun save() {
        File(SETTINGS_FILE).writeText(gson.toJson(this))
    }
} 