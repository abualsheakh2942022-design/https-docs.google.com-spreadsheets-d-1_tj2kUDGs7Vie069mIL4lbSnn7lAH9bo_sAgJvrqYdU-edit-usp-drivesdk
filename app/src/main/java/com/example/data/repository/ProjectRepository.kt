package com.example.data.repository

import com.example.data.db.ProjectDao
import com.example.data.db.ProjectEntity
import com.example.data.db.WorkPlanDao
import com.example.data.db.WorkPlanEntity
import com.example.data.db.WorkPlanPhaseEntity
import com.example.data.db.WorkPlanWithPhases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val workPlanDao: WorkPlanDao? = null
) {

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    val allWorkPlans: Flow<List<WorkPlanWithPhases>> =
        workPlanDao?.getAllPlansWithPhases() ?: flowOf(emptyList())

    suspend fun getProjectById(id: Long): ProjectEntity? {
        return projectDao.getProjectById(id)
    }

    suspend fun saveProject(project: ProjectEntity): Long {
        return projectDao.insertProject(project)
    }

    suspend fun deleteProject(project: ProjectEntity) {
        projectDao.deleteProject(project)
    }

    suspend fun deleteById(id: Long) {
        projectDao.deleteById(id)
    }

    suspend fun getPlanByLinkedProjectId(projectId: Long): WorkPlanWithPhases? {
        return workPlanDao?.getPlanByLinkedProjectId(projectId)
    }

    suspend fun createWorkPlan(plan: WorkPlanEntity, phases: List<WorkPlanPhaseEntity>): Long {
        return workPlanDao?.createPlanWithPhases(plan, phases) ?: 0L
    }

    suspend fun updateWorkPlan(plan: WorkPlanEntity) {
        workPlanDao?.updatePlan(plan)
    }

    suspend fun deleteWorkPlan(planId: Long) {
        workPlanDao?.deletePlanById(planId)
    }

    suspend fun addWorkPlanPhase(phase: WorkPlanPhaseEntity): Long {
        return workPlanDao?.insertPhase(phase) ?: 0L
    }

    suspend fun updateWorkPlanPhase(phase: WorkPlanPhaseEntity) {
        workPlanDao?.updatePhase(phase)
    }

    suspend fun updateWorkPlanPhases(phases: List<WorkPlanPhaseEntity>) {
        if (phases.isNotEmpty()) {
            workPlanDao?.updatePhases(phases)
        }
    }

    suspend fun deleteWorkPlanPhase(phaseId: Long) {
        workPlanDao?.deletePhaseById(phaseId)
    }
}
