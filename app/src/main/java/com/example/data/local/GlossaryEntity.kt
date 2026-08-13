package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medical_glossary")
data class GlossaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val termEn: String,
    val termVn: String,
    val category: String, // Cardiology, Oncology, Pharmacology, Neurology, Diagnostics, Anatomy, General
    val definitionVn: String = "",
    val isCustom: Boolean = false
)
