package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM medical_glossary ORDER BY termEn ASC")
    fun getAllTerms(): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM medical_glossary WHERE category = :category ORDER BY termEn ASC")
    fun getTermsByCategory(category: String): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM medical_glossary WHERE termEn LIKE '%' || :query || '%' OR termVn LIKE '%' || :query || '%' ORDER BY termEn ASC")
    fun searchTerms(query: String): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM medical_glossary WHERE LOWER(termEn) = LOWER(:termEn) LIMIT 1")
    suspend fun findTermEn(termEn: String): GlossaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerms(terms: List<GlossaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerm(term: GlossaryEntity): Long

    @Query("DELETE FROM medical_glossary WHERE id = :id")
    suspend fun deleteTerm(id: Long)

    @Query("SELECT COUNT(*) FROM medical_glossary")
    suspend fun getCount(): Int
}
