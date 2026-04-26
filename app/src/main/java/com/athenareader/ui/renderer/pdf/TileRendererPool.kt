package com.athenareader.ui.renderer.pdf

import android.graphics.Bitmap
import com.athenareader.core.cache.TileCache
import com.athenareader.domain.model.Tile
import com.athenareader.domain.renderer.PdfRenderer
import kotlinx.coroutines.*
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TileRendererPool @Inject constructor(
    private val pdfRenderer: PdfRenderer,
    private val tileCache: TileCache
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val requestQueue = PriorityBlockingQueue<TileRequest>()
    private val activeWorkers = mutableListOf<Job>()
    private val requestLock = Any()
    private val pendingCallbacks = mutableMapOf<String, MutableList<(Bitmap) -> Unit>>()
    private val enqueuedKeys = mutableSetOf<String>()
    private val sequence = AtomicLong(0)

    init {
        // Default: 2 workers as per PRD
        repeat(2) {
            activeWorkers.add(scope.launch {
                processRequests()
            })
        }
    }

    fun requestTile(tile: Tile, priority: Int, onComplete: (Bitmap) -> Unit) {
        val key = TileCache.generateKey(tile.pageIndex, tile.x, tile.y, tile.zoom)
        val cached = tileCache.get(key)
        if (cached != null) {
            onComplete(cached)
            return
        }

        synchronized(requestLock) {
            pendingCallbacks.getOrPut(key) { mutableListOf() }.add(onComplete)
            if (enqueuedKeys.add(key)) {
                requestQueue.add(
                    TileRequest(
                        tile = tile,
                        priority = priority,
                        sequence = sequence.incrementAndGet()
                    )
                )
            }
        }
    }

    private suspend fun processRequests() = withContext(Dispatchers.IO) {
        while (isActive) {
            val request = requestQueue.take()
            val key = TileCache.generateKey(request.tile.pageIndex, request.tile.x, request.tile.y, request.tile.zoom)
            val callbacks = synchronized(requestLock) {
                enqueuedKeys.remove(key)
                pendingCallbacks.remove(key)
            } ?: continue

            // Check cache again just in case another worker filled it
            var bitmap = tileCache.get(key)
            if (bitmap == null) {
                bitmap = pdfRenderer.renderTile(request.tile)
                tileCache.put(key, bitmap)
                tileCache.trimIfNeeded()
            }

            withContext(Dispatchers.Main) {
                callbacks.forEach { callback ->
                    callback(bitmap)
                }
            }
        }
    }

    fun clearQueue() {
        requestQueue.clear()
        synchronized(requestLock) {
            enqueuedKeys.clear()
            pendingCallbacks.clear()
        }
        tileCache.clear()
    }

    private data class TileRequest(
        val tile: Tile,
        val priority: Int,
        val sequence: Long
    ) : Comparable<TileRequest> {
        override fun compareTo(other: TileRequest): Int {
            return when {
                this.priority != other.priority -> this.priority.compareTo(other.priority)
                else -> this.sequence.compareTo(other.sequence)
            }
        }
    }
}
