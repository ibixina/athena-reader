package com.athenareader.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    
    @ColumnInfo(name = "file_path")
    val filePath: String,
    
    val hash: String,
    
    val format: String,
    
    @ColumnInfo(name = "last_opened")
    val lastOpened: Long
)
