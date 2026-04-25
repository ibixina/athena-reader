package com.inkreader.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "document_id", index = true)
    val documentId: Long,

    @ColumnInfo(name = "page_index")
    val pageIndex: Int,

    val color: Int,

    @ColumnInfo(name = "extracted_text")
    val extractedText: String = "",

    @ColumnInfo(name = "range_data")
    val rangeData: String = "",

    @ColumnInfo(name = "user_note")
    val userNote: String = "",

    val tags: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
