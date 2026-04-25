package com.inkreader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.inkreader.domain.model.ReaderSettings
import com.inkreader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val settings: Flow<ReaderSettings> = dataStore.data.map { preferences ->
        ReaderSettings(
            showPageNumbers = preferences[SHOW_PAGE_NUMBERS] ?: true,
            showPageScrubber = preferences[SHOW_PAGE_SCRUBBER] ?: true,
            keepScreenOn = preferences[KEEP_SCREEN_ON] ?: true
        )
    }

    override suspend fun setShowPageNumbers(enabled: Boolean) {
        dataStore.edit { it[SHOW_PAGE_NUMBERS] = enabled }
    }

    override suspend fun setShowPageScrubber(enabled: Boolean) {
        dataStore.edit { it[SHOW_PAGE_SCRUBBER] = enabled }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    private companion object {
        val SHOW_PAGE_NUMBERS = booleanPreferencesKey("show_page_numbers")
        val SHOW_PAGE_SCRUBBER = booleanPreferencesKey("show_page_scrubber")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }
}

