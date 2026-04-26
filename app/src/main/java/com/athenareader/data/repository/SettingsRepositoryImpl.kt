package com.athenareader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import com.athenareader.domain.model.ReaderSettings
import com.athenareader.domain.repository.SettingsRepository
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
            keepScreenOn = preferences[KEEP_SCREEN_ON] ?: true,
            penColor = preferences[PEN_COLOR] ?: android.graphics.Color.BLACK,
            penWidth = preferences[PEN_WIDTH] ?: 3f,
            highlighterColor = preferences[HIGHLIGHTER_COLOR] ?: android.graphics.Color.parseColor("#FF8F00"),
            highlighterWidth = preferences[HIGHLIGHTER_WIDTH] ?: 12f
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

    override suspend fun setPenColor(color: Int) {
        dataStore.edit { it[PEN_COLOR] = color }
    }

    override suspend fun setPenWidth(width: Float) {
        dataStore.edit { it[PEN_WIDTH] = width }
    }

    override suspend fun setHighlighterColor(color: Int) {
        dataStore.edit { it[HIGHLIGHTER_COLOR] = color }
    }

    override suspend fun setHighlighterWidth(width: Float) {
        dataStore.edit { it[HIGHLIGHTER_WIDTH] = width }
    }

    private companion object {
        val SHOW_PAGE_NUMBERS = booleanPreferencesKey("show_page_numbers")
        val SHOW_PAGE_SCRUBBER = booleanPreferencesKey("show_page_scrubber")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PEN_COLOR = intPreferencesKey("pen_color")
        val PEN_WIDTH = floatPreferencesKey("pen_width")
        val HIGHLIGHTER_COLOR = intPreferencesKey("highlighter_color")
        val HIGHLIGHTER_WIDTH = floatPreferencesKey("highlighter_width")
    }
}

