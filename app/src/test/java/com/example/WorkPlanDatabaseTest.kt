package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.WorkPlanEntity
import com.example.data.db.WorkPlanPhaseEntity
import com.example.domain.ManualProjectTemplate
import com.example.domain.SchedulePace
import com.example.domain.WorkPlanGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkPlanDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createReadUpdateDeleteWorkPlanAndPhases() = runTest {
        val (plan, phases) = WorkPlanGenerator.generateManualPlan(
            title = "عمارة كافوري",
            location = "بحري، السودان",
            floorsCount = 2,
            areaPerFloorM2 = 200.0,
            template = ManualProjectTemplate.RESIDENTIAL_BUILDING,
            pace = SchedulePace.STANDARD,
            startDateMillis = 1_700_000_000_000L,
            startBlank = false
        )

        val dao = db.workPlanDao()
        val planId = dao.createPlanWithPhases(plan, phases)
        assertTrue(planId > 0L)

        val loaded = dao.getAllPlansWithPhases().first()
        assertEquals(1, loaded.size)
        assertEquals("عمارة كافوري", loaded.first().plan.title)
        assertEquals(phases.size, loaded.first().orderedPhases.size)

        val firstPhase = loaded.first().orderedPhases.first()
        dao.updatePhase(firstPhase.copy(progressPercent = 100, statusName = "COMPLETED"))

        val reloaded = dao.getAllPlansWithPhases().first().first().orderedPhases.first()
        assertEquals(100, reloaded.progressPercent)

        dao.deletePlanById(planId)
        val afterDelete = dao.getAllPlansWithPhases().first()
        assertTrue(afterDelete.isEmpty())
    }
}
