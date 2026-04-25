package com.inkreader.data.mapper

import com.inkreader.data.entity.ReadingProgressEntity
import com.inkreader.domain.model.ReadingProgress

fun ReadingProgressEntity.toDomain(): ReadingProgress {
    return ReadingProgress(
        documentId = documentId,
        pageIndex = pageIndex,
        scrollX = scrollX,
        scrollY = scrollY,
        zoom = zoom,
        chapterId = chapterId
    )
}

fun ReadingProgress.toEntity(): ReadingProgressEntity {
    return ReadingProgressEntity(
        documentId = documentId,
        pageIndex = pageIndex,
        scrollX = scrollX,
        scrollY = scrollY,
        zoom = zoom,
        chapterId = chapterId
    )
}

