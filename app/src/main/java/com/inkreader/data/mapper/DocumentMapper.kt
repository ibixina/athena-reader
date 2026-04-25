package com.inkreader.data.mapper

import com.inkreader.data.entity.DocumentEntity
import com.inkreader.domain.model.Document
import com.inkreader.domain.model.DocumentFormat

fun DocumentEntity.toDomain(): Document {
    return Document(
        id = id,
        name = name,
        filePath = filePath,
        hash = hash,
        format = DocumentFormat.valueOf(format),
        lastOpened = lastOpened
    )
}

fun Document.toEntity(): DocumentEntity {
    return DocumentEntity(
        id = id,
        name = name,
        filePath = filePath,
        hash = hash,
        format = format.name,
        lastOpened = lastOpened
    )
}
