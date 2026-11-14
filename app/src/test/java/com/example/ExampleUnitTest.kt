package com.example

import com.example.domain.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testConcreteVolumeAndMixCalculation() {
        // 10 columns of 0.25m x 0.25m x 3.0m
        val volumeNet = ConcreteEstimator.calculateNetVolume(
            elementType = StructuralElementType.COLUMN_RECT,
            lengthMeters = 0.25,
            widthMeters = 0.25,
            heightDepthMeters = 3.0,
            count = 10
        )
        assertEquals(1.875, volumeNet, 0.001)

        val estimate = ConcreteEstimator.calculateMaterials(
            netVolumeM3 = volumeNet,
            mixGrade = ConcreteGrade.C25,
            wastagePercent = 5.0,
            steelRatioKgPerM3 = 150.0
        )
        // 1.875 * 1.05 = 1.96875 m3 gross
        assertEquals(1.97, estimate.volumeWithWasteM3, 0.05)
        // C25 has 8 bags of cement per m3 -> ~ 15.75 bags
        assertTrue(estimate.cementBags >= 15.0)
        // Commercial steel estimate
        assertTrue(estimate.estimatedSteelTonnes > 0.25)
    }

    @Test
    fun testRebarWeightFormula() {
        // BS 4449 D16 weight = 16^2 / 162 = 1.578 kg/m
        val d16 = RebarDiameter.D16
        assertEquals(1.578, d16.weightPerMeterKg, 0.01)

        val steelResult = SteelEstimator.calculateDetailedRebar(
            diameter = RebarDiameter.D16,
            numberOfBars = 10,
            barLengthMeters = 12.0,
            wastePercentage = 5.0
        )
        // 10 bars of 12m = 120m * 1.578 * 1.05 = ~198.8 kg
        assertEquals(0.199, steelResult.totalWeightTonnes, 0.02)
    }

    @Test
    fun testSudaneseRedBrickCalculation() {
        // 10m length x 3m height wall with 1 door (2 m2) -> 28 m2 net area
        val result = MasonryEstimator.calculateMasonry(
            wallLengthMeters = 10.0,
            wallHeightMeters = 3.0,
            openingsAreaM2 = 2.0,
            brickType = MasonryEstimator.BrickType.SUDANESE_RED_BRICK,
            wallThickness = MasonryEstimator.WallThickness.HALF_BRICK,
            wastagePercentage = 5.0
        )
        assertEquals(28.0, result.wallAreaM2, 0.01)
        // 28 m2 * 53 bricks * 1.05 = ~1558 bricks
        assertTrue(result.totalBricksCount in 1500..1650)
        assertTrue(result.totalBricksThousand in 1.5..1.7)
    }

    @Test
    fun testDxfParserAndOfflineConversion() {
        val sampleDxf = """
            0
            SECTION
            2
            HEADER
            9
            ${'$'}INSUNITS
            70
            6
            0
            ENDSEC
            0
            SECTION
            2
            ENTITIES
            0
            LWPOLYLINE
            8
            COLUMNS
            70
            1
            10
            0.0
            20
            0.0
            10
            0.3
            20
            0.0
            10
            0.3
            20
            0.5
            10
            0.0
            20
            0.5
            0
            LINE
            8
            WALLS
            10
            0.0
            20
            0.0
            11
            10.0
            21
            0.0
            0
            ENDSEC
            0
            EOF
        """.trimIndent()

        val parsed = DxfQuantityParser.parseDxf(sampleDxf)
        assertEquals(CadDrawingUnit.METERS, parsed.detectedUnit)
        assertEquals(2, parsed.totalEntities)

        val columnsLayer = parsed.layers.first { it.layerName == "COLUMNS" }
        assertEquals(1, columnsLayer.closedPolylineCount)
        assertEquals(0.15, columnsLayer.totalClosedAreaInDrawingUnitsSq, 0.001)

        val wallsLayer = parsed.layers.first { it.layerName == "WALLS" }
        assertEquals(10.0, wallsLayer.totalLengthInDrawingUnits, 0.001)

        val report = BlueprintAiAnalyzer.convertDxfToElementsOffline(
            dxfResult = parsed,
            unitOverride = CadDrawingUnit.METERS,
            defaultHeightM = 3.0,
            defaultSlabThicknessM = 0.15
        )
        assertEquals(2, report.elements.size)
        val colEl = report.elements.first { it.category == ExtractedElementCategory.CONCRETE }
        assertEquals(0.45, colEl.volumeM3, 0.01) // 0.15 m2 * 3.0 m = 0.45 m3

        val wallEl = report.elements.first { it.category == ExtractedElementCategory.MASONRY }
        assertEquals(30.0, wallEl.areaM2, 0.01) // 10.0 m * 3.0 m = 30.0 m2
    }

    @Test
    fun testRebarBreakdownByDiameterD() {
        val result = SteelEstimator.calculateDetailedRebar(
            diameter = RebarDiameter.D16,
            numberOfBars = 8,
            barLengthMeters = 3.5,
            lapLengthMeters = 0.8,
            isIncludeStirrups = true,
            stirrupDiameter = RebarDiameter.D10,
            memberLengthMeters = 3.0,
            stirrupSpacingCm = 15.0,
            memberWidthMeters = 0.25,
            memberDepthMeters = 0.50,
            wastePercentage = 5.0
        )
        assertEquals(2, result.diameterBreakdown.size)
        val mainRow = result.diameterBreakdown[0]
        val stirrupRow = result.diameterBreakdown[1]
        assertEquals(RebarDiameter.D16, mainRow.diameter)
        assertEquals(RebarDiameter.D10, stirrupRow.diameter)
        assertTrue(mainRow.bars12mRounded >= 3)
        assertTrue(stirrupRow.bars12mRounded >= 2)
        assertEquals(8, mainRow.cutBarsCount)
        assertEquals(result.stirrupsCount, stirrupRow.cutBarsCount)

        val (primaryEmp, empOptions) = SteelEstimator.calculateEmpiricalDiameterBreakdown(
            totalKg = 1000.0,
            primaryDiameter = RebarDiameter.D12
        )
        assertEquals(RebarDiameter.D12, primaryEmp.diameter)
        // 1000 / 10.66 = 93.8 -> 94 bars
        assertEquals(94, primaryEmp.bars12mRounded)
        assertEquals(5, empOptions.size)
    }

    @Test
    fun testWorkPlanGenerationAndFlexibleCascadeShift() {
        val (plan, phases) = WorkPlanGenerator.generateManualPlan(
            title = "فيلا سكنية",
            location = "الخرطوم",
            floorsCount = 2,
            areaPerFloorM2 = 180.0,
            template = ManualProjectTemplate.RESIDENTIAL_BUILDING,
            pace = SchedulePace.STANDARD,
            startDateMillis = 1_700_000_000_000L,
            startBlank = false
        )
        assertEquals("فيلا سكنية", plan.title)
        assertTrue(phases.size >= 8)

        val indexed = phases.mapIndexed { idx, p -> p.copy(id = (idx + 1).toLong()) }
        val target = indexed[1]
        val originalNextStart = indexed[2].startDay
        val shifted = WorkPlanGenerator.updatePhaseFlexible(
            allPhasesSorted = indexed,
            updatedPhase = target.copy(durationDays = target.durationDays + 4),
            autoShiftSubsequent = true
        )
        assertEquals(originalNextStart + 4, shifted[2].startDay)

        val fastPhases = WorkPlanGenerator.rescalePlanPace(
            phasesSorted = indexed,
            oldPace = SchedulePace.STANDARD,
            newPace = SchedulePace.FAST
        )
        assertTrue(fastPhases.last().startDay <= indexed.last().startDay)
    }
}
