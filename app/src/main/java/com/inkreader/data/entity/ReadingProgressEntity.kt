package com.inkreader.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReadingProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "document_id")
    val documentId: Long,
    
    @ColumnInfo(name = "page_index")
    val pageIndex: Int = 0,
    
    @ColumnInfo(name = "scroll_x")
    val scrollX: Int = 0,
    
    @ColumnInfo(name = "scroll_y")
    val scrollY: Int = 0,
    
    val zoom: Float = 1.0f,
    
    @ColumnInfo(name = "chapter_id")
    val chapterId: String? = null
)
