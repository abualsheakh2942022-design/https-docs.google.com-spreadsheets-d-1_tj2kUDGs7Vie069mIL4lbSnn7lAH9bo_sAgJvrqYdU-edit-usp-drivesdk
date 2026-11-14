package com.example.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class WorkPlanWithPhases(
    @Embedded val plan: WorkPlanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "planId"
    )
    val phases: List<WorkPlanPhaseEntity>
) {
    val orderedPhases: List<WorkPlanPhaseEntity>
        get() = phases.sortedWith(compareBy<WorkPlanPhaseEntity> { it.sortOrder }.thenBy { it.startDay }.thenBy { it.id })
}

@Dao
interface WorkPlanDao {

    @Transaction
    @Query("SELECT * FROM work_plans ORDER BY createdAtTimestamp DESC")
    fun getAllPlansWithPhases(): Flow<List<WorkPlanWithPhases>>

    @Transaction
    @Query("SELECT * FROM work_plans WHERE linkedProjectId = :projectId LIMIT 1")
    suspend fun getPlanByLinkedProjectId(projectId: Long): WorkPlanWithPhases?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: WorkPlanEntity)

    @Query("DELETE FROM work_plans WHERE id = :planId")
    suspend fun deletePlanById(planId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhase(phase: WorkPlanPhaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhases(phases: List<WorkPlanPhaseEntity>)

    @Update
    suspend fun updatePhase(phase: WorkPlanPhaseEntity)

    @Update
    suspend fun updatePhases(phases: List<WorkPlanPhaseEntity>)

    @Query("DELETE FROM work_plan_phases WHERE id = :phaseId")
    suspend fun deletePhaseById(phaseId: Long)

    @Transaction
    suspend fun createPlanWithPhases(
        plan: WorkPlanEntity,
        phases: List<WorkPlanPhaseEntity>
    ): Long {
        val newPlanId = insertPlan(plan)
        if (phases.isNotEmpty()) {
            insertPhases(phases.map { it.copy(planId = newPlanId) })
        }
        return newPlanId
    }
}
