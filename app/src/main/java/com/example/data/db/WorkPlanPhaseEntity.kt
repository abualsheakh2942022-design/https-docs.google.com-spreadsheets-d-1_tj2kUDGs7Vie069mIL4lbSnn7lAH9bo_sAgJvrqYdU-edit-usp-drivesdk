package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "work_plan_phases",
    foreignKeys = [
        ForeignKey(
            entity = WorkPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId")]
)
data class WorkPlanPhaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: Long,
    val sortOrder: Int,
    val title: String,
    val categoryName: String,
    val startDay: Int,
    val durationDays: Int,
    val progressPercent: Int = 0,
    val statusName: String = "NOT_STARTED",
    val quantityHint: String = "",
    val resourcesNote: String = ""
)
