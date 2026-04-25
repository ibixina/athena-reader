package com.inkreader.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "document_id", index = true)
    val documentId: Long,

    @ColumnInfo(name = "page_index")
    val pageIndex: Int,

    val tool: String, // FINE_PEN, HIGHLIGHTER, ERASER

    val color: Int,

    @ColumnInfo(name = "stroke_width")
    val strokeWidth: Float,

    val opacity: Float,

    // Raw binary: 6 floats per point (x, y, pressure, tiltX, tiltY, timestamp-as-float)
    @ColumnInfo(name = "vector_data", typeAffinity = ColumnInfo.BLOB)
    val vectorData: ByteArray,

    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnnotationEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
