package com.inkreader.core.cache

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TileCache @Inject constructor() {
    // Keep the cache bounded so we don't overrun the heap on smaller devices.
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 4).coerceIn(MIN_CACHE_KB, MAX_CACHE_KB)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun get(key: String): Bitmap? {
        return cache.get(key)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }

    fun trimIfNeeded(targetUtilization: Float = 0.75f) {
        val maxSize = cache.maxSize()
        val currentSize = cache.size()
        if (currentSize < maxSize * TRIM_THRESHOLD) return

        val targetSize = (maxSize * targetUtilization)
            .toInt()
            .coerceAtLeast(MIN_CACHE_KB)
        cache.trimToSize(targetSize)
    }

    fun trimToHalf() {
        cache.trimToSize((cache.maxSize() / 2).coerceAtLeast(MIN_CACHE_KB))
    }

    companion object {
        private const val MIN_CACHE_KB = 16 * 1024
        private const val MAX_CACHE_KB = 120 * 1024
        private const val TRIM_THRESHOLD = 0.90f

        fun generateKey(pageIndex: Int, x: Int, y: Int, zoom: Float): String {
            return "$pageIndex-$x-$y-$zoom"
        }
    }
}
