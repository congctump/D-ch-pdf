package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translated_documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val title: String,
    val totalPages: Int,
    val translatedPages: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val modelUsed: String = "DeepSeek V3",
    val status: String = "COMPLETED", // COMPLETED, IN_PROGRESS, FAILED
    val isSample: Boolean = false
)

@Entity(tableName = "page_blocks")
data class PageBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val blockIndex: Int,
    val originalText: String,
    val translatedText: String,
    val blockType: String = "PARAGRAPH" // HEADER, PARAGRAPH, BULLET, TABLE_ROW, DIAGNOSTIC
)
