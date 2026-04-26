package com.athenareader.core.di

import android.content.Context
import com.athenareader.core.cache.CoverCache
import com.athenareader.core.cache.TileCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideTileCache(): TileCache = TileCache()

    @Provides
    @Singleton
    fun provideCoverCache(@ApplicationContext context: Context): CoverCache = CoverCache(context)
}