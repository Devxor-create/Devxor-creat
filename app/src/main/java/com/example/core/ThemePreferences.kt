package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class AppThemeMode(
    val storageKey: String,
    val label: String,
    val description: String
) {
    LIGHT(
        storageKey = "light",
        label = "Light",
        description = "Bright daylight palette for well-lit environments"
    ),
    DARK(
        storageKey = "dark",
        label = "Dark",
        description = "Midnight orbital palette for low-light comfort"
    ),
    SYSTEM(
        storageKey = "system",
        label = "System",
        description = "Automatically matches your Android system theme"
    );

    companion object {
        fun fromStorageKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}

private val Context.themePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spacex_theme_preferences"
)

class ThemePreferencesManager(context: Context) {
    private val appContext = context.applicationContext
    private val themeModeKey = stringPreferencesKey("app_theme_mode")

    val themeModeFlow: Flow<AppThemeMode> = appContext.themePreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppThemeMode.fromStorageKey(preferences[themeModeKey])
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        appContext.themePreferencesDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.storageKey
        }
    }
}
