package com.athenareader.data.mapper

import com.athenareader.data.entity.ReadingProgressEntity
import com.athenareader.domain.model.ReadingProgress

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

