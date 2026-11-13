package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wmsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wms_preferences")

class WmsPreferencesRepository(private val context: Context) {

    val selectedFactoryId: Flow<String?> = context.wmsDataStore.data.map { prefs ->
        prefs[KEY_SELECTED_FACTORY_ID]
    }

    val appLanguage: Flow<AppLanguage> = context.wmsDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[KEY_APP_LANGUAGE])
    }

    val themeMode: Flow<ThemeMode> = context.wmsDataStore.data.map { prefs ->
        ThemeMode.fromCode(prefs[KEY_THEME_MODE])
    }

    val recentScans: Flow<List<String>> = context.wmsDataStore.data.map { prefs ->
        prefs[KEY_RECENT_SCANS]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_RECENT_SCANS)
            ?: emptyList()
    }

    suspend fun setSelectedFactoryId(factoryId: String) {
        context.wmsDataStore.edit { prefs ->
            prefs[KEY_SELECTED_FACTORY_ID] = factoryId
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.wmsDataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = language.code
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.wmsDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.code
        }
    }

    suspend fun addRecentScan(partNumber: String) {
        val cleaned = partNumber.trim()
        if (cleaned.isEmpty()) return
        context.wmsDataStore.edit { prefs ->
            val current = prefs[KEY_RECENT_SCANS]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val updated = (listOf(cleaned) + current.filterNot { it == cleaned })
                .take(MAX_RECENT_SCANS)
            prefs[KEY_RECENT_SCANS] = updated.joinToString(",")
        }
    }

    private companion object {
        const val MAX_RECENT_SCANS = 5
        val KEY_SELECTED_FACTORY_ID = stringPreferencesKey("selected_factory_id")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_RECENT_SCANS = stringPreferencesKey("recent_scans")
    }
}
