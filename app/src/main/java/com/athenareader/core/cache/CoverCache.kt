package com.athenareader.core.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheSize = MAX_COVERS
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val diskCacheDir = File(context.cacheDir, "covers").apply {
        if (!exists()) mkdirs()
    }

    fun put(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
        saveToDisk(key, bitmap)
    }

    fun get(key: String): Bitmap? {
        // Try memory cache first
        memoryCache.get(key)?.let { return it }

        // Try disk cache
        return getFromDisk(key)?.also {
            memoryCache.put(key, it)
        }
    }

    fun getFromMemory(key: String): Bitmap? {
        return memoryCache.get(key)
    }

    private fun saveToDisk(key: String, bitmap: Bitmap) {
        val file = File(diskCacheDir, hashKey(key))
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFromDisk(key: String): Bitmap? {
        val file = File(diskCacheDir, hashKey(key))
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }

    private fun hashKey(key: String): String {
        return try {
            MessageDigest.getInstance("MD5")
                .digest(key.toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            key.hashCode().toString()
        }
    }

    fun remove(key: String) {
        memoryCache.remove(key)
        File(diskCacheDir, hashKey(key)).delete()
    }

    fun clear() {
        memoryCache.evictAll()
        diskCacheDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val MAX_COVERS = 50
    }
}