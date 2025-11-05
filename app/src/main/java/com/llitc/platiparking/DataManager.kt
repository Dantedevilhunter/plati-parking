package com.llitc.platiparking

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("settings")

class DataManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val REGISTRATION_KEY = stringPreferencesKey("vehicle_registration")
        val CONFIGS_KEY = stringPreferencesKey("parking_configs")
    }

    val registrationNumberFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[REGISTRATION_KEY] ?: ""
    }

    suspend fun saveRegistration(number: String) {
        dataStore.edit { settings ->
            settings[REGISTRATION_KEY] = number
        }
    }

    val parkingConfigsFlow: Flow<List<ParkingConfig>> = dataStore.data.map { preferences ->
        val jsonString = preferences[CONFIGS_KEY]
        if (jsonString.isNullOrEmpty()) {
            emptyList()
        } else {
            Json.decodeFromString<List<ParkingConfig>>(jsonString)
        }
    }

    suspend fun saveParkingConfigs(configs: List<ParkingConfig>) {
        val jsonString = Json.encodeToString(configs)
        dataStore.edit { settings ->
            settings[CONFIGS_KEY] = jsonString
        }
    }
}