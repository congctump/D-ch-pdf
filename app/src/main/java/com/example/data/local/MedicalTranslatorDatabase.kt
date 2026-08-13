package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, PageBlockEntity::class, GlossaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MedicalTranslatorDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun glossaryDao(): GlossaryDao

    companion object {
        @Volatile
        private var INSTANCE: MedicalTranslatorDatabase? = null

        fun getDatabase(context: Context): MedicalTranslatorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicalTranslatorDatabase::class.java,
                    "medical_translator_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
