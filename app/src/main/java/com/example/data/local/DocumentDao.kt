package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM translated_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM translated_documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM page_blocks WHERE documentId = :documentId ORDER BY pageIndex ASC, blockIndex ASC")
    fun getPageBlocks(documentId: Long): Flow<List<PageBlockEntity>>

    @Query("SELECT * FROM page_blocks WHERE documentId = :documentId ORDER BY pageIndex ASC, blockIndex ASC")
    suspend fun getPageBlocksList(documentId: Long): List<PageBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageBlocks(blocks: List<PageBlockEntity>)

    @Query("UPDATE translated_documents SET translatedPages = :translatedPages, status = :status WHERE id = :id")
    suspend fun updateDocumentProgress(id: Long, translatedPages: Int, status: String)

    @Query("DELETE FROM translated_documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("DELETE FROM page_blocks WHERE documentId = :documentId")
    suspend fun deletePageBlocks(documentId: Long)

    @Transaction
    suspend fun deleteDocumentWithBlocks(documentId: Long) {
        deletePageBlocks(documentId)
        deleteDocument(documentId)
    }
}
