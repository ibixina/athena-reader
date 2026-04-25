package com.athenareader.domain.repository

import com.athenareader.domain.model.ReaderSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<ReaderSettings>

    suspend fun setShowPageNumbers(enabled: Boolean)
    suspend fun setShowPageScrubber(enabled: Boolean)
    suspend fun setKeepScreenOn(enabled: Boolean)
}

