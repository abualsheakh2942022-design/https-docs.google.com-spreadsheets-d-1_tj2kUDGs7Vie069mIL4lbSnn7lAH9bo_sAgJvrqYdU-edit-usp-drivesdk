package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_plans")
data class WorkPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val location: String,
    val sourceType: String, // WorkPlanSourceType name
    val linkedProjectId: Long? = null,
    val paceName: String = "STANDARD",
    val startDateMillis: Long = System.currentTimeMillis(),
    val notes: String = "",
    val createdAtTimestamp: Long = System.currentTimeMillis()
)
