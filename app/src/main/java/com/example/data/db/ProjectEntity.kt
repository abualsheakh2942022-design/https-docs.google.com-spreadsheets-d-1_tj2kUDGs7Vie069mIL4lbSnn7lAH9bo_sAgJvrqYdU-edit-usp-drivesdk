package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val clientOrLocation: String,
    val category: String, // "خرسانة", "تسليح", "مباني", "تشطيبات", "مقايسة شاملة"
    val summaryText: String,
    val detailedDetails: String,
    val totalCostSdg: Double = 0.0,
    val createdAtTimestamp: Long = System.currentTimeMillis()
)
