package com.example.domain

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt

object ConcreteEstimator {

    fun calculateNetVolume(
        elementType: StructuralElementType,
        lengthMeters: Double,
        widthMeters: Double,
        heightDepthMeters: Double,
        count: Int,
        slabThicknessMeters: Double = 0.15,
        circularDiameterMeters: Double = 0.30,
        stairsRiserCount: Int = 10,
        stairsRiserHeightM: Double = 0.15,
        stairsTreadLengthM: Double = 0.30,
        stairsWaistThicknessM: Double = 0.15,
        landingLengthM: Double = 1.2,
        landingWidthM: Double = 2.4,
        landingThicknessM: Double = 0.15,
        customDirectVolumeM3: Double = 0.0
    ): Double {
        val qty = count.coerceAtLeast(1)
        return when (elementType) {
            StructuralElementType.FOOTING_ISOLATED,
            StructuralElementType.FOOTING_COMBINED,
            StructuralElementType.RAFT_FOUNDATION -> {
                lengthMeters * widthMeters * heightDepthMeters * qty
            }
            StructuralElementType.GROUND_BEAM,
            StructuralElementType.BEAMS -> {
                lengthMeters * widthMeters * heightDepthMeters * qty
            }
            StructuralElementType.COLUMN_RECT -> {
                lengthMeters * widthMeters * heightDepthMeters * qty
            }
            StructuralElementType.COLUMN_CIRCULAR -> {
                val radius = circularDiameterMeters / 2.0
                (PI * radius * radius) * heightDepthMeters * qty
            }
            StructuralElementType.SOLID_SLAB,
            StructuralElementType.FLAT_SLAB,
            StructuralElementType.SLAB_ON_GRADE -> {
                lengthMeters * widthMeters * slabThicknessMeters * qty
            }
            StructuralElementType.RIBBED_SLAB -> {
                // Ribbed slab approximate volume: Topping (5-7cm) + concrete ribs (usually 60% of solid volume)
                val totalArea = lengthMeters * widthMeters
                val equivalentThickness = (slabThicknessMeters * 0.65).coerceAtLeast(0.10)
                totalArea * equivalentThickness * qty
            }
            StructuralElementType.STAIRS -> {
                // Stairs volume = Flight waist slab + Steps volume + Landing
                val flightSlopeLength = kotlin.math.sqrt(
                    (stairsRiserCount * stairsTreadLengthM).let { it * it } +
                    (stairsRiserCount * stairsRiserHeightM).let { it * it }
                )
                val waistVolume = flightSlopeLength * widthMeters * stairsWaistThicknessM
                val stepsVolume = 0.5 * stairsTreadLengthM * stairsRiserHeightM * widthMeters * stairsRiserCount
                val landingVolume = landingLengthM * landingWidthM * landingThicknessM
                (waistVolume + stepsVolume + landingVolume) * qty
            }
            StructuralElementType.CUSTOM_VOLUME -> {
                customDirectVolumeM3 * qty
            }
        }
    }

    fun calculateMaterials(
        netVolumeM3: Double,
        mixGrade: ConcreteGrade,
        wastagePercent: Double = 5.0,
        customCementBagsPerM3: Double = 8.0,
        customSandRatio: Double = 0.42,
        customGravelRatio: Double = 0.84,
        steelRatioKgPerM3: Double = 100.0,
        rebarDiameter: RebarDiameter = RebarDiameter.D16
    ): ConcreteEstimateResult {
        val wasteFactor = 1.0 + (wastagePercent / 100.0)
        val grossVolumeM3 = netVolumeM3 * wasteFactor

        val bagsPerM3 = if (mixGrade == ConcreteGrade.CUSTOM) customCementBagsPerM3 else mixGrade.cementBagsPerM3
        val sandPerM3 = if (mixGrade == ConcreteGrade.CUSTOM) customSandRatio else mixGrade.sandRatioM3
        val gravelPerM3 = if (mixGrade == ConcreteGrade.CUSTOM) customGravelRatio else mixGrade.gravelRatioM3
        val waterPerM3 = mixGrade.waterLitersPerM3

        val totalCementBags = grossVolumeM3 * bagsPerM3
        val totalCementTonnes = totalCementBags / SudaneseCommercialUnits.BAGS_PER_TON_CEMENT

        // In Sudanese practice, sand and gravel loose volume factors
        val totalSandM3 = grossVolumeM3 * sandPerM3
        val sandSmallTrucks = totalSandM3 / SudaneseCommercialUnits.SMALL_DUMP_TRUCK_M3
        val sandLargeTataTrucks = totalSandM3 / SudaneseCommercialUnits.LARGE_TRUCK_TATA_M3

        val totalGravelM3 = grossVolumeM3 * gravelPerM3
        val gravelSmallTrucks = totalGravelM3 / SudaneseCommercialUnits.SMALL_DUMP_TRUCK_M3
        val gravelLargeTataTrucks = totalGravelM3 / SudaneseCommercialUnits.LARGE_TRUCK_TATA_M3

        val totalWaterLiters = grossVolumeM3 * waterPerM3
        val waterDrums = totalWaterLiters / SudaneseCommercialUnits.LITERS_PER_WATER_DRUM

        val estimatedSteelKg = grossVolumeM3 * steelRatioKgPerM3
        val estimatedSteelTonnes = estimatedSteelKg / 1000.0
        val primaryBreakdown = SteelEstimator.breakdownFromWeightKg(
            weightKg = estimatedSteelKg,
            diameter = rebarDiameter,
            roleLabelAr = "التسليح الرئيسي المقترح"
        )
        val options = listOf(
            RebarDiameter.D10,
            RebarDiameter.D12,
            RebarDiameter.D16,
            RebarDiameter.D20,
            RebarDiameter.D25
        ).map { d ->
            SteelEstimator.breakdownFromWeightKg(
                weightKg = estimatedSteelKg,
                diameter = d,
                roleLabelAr = "مكافئ قطر ${d.mm} مم"
            )
        }

        return ConcreteEstimateResult(
            volumeNetM3 = round2(netVolumeM3),
            volumeWithWasteM3 = round2(grossVolumeM3),
            wastagePercentage = wastagePercent,
            cementBags = round1(totalCementBags),
            cementTonnes = round2(totalCementTonnes),
            sandM3 = round2(totalSandM3),
            sandSmallTrucks = round2(sandSmallTrucks),
            sandLargeTataTrucks = round2(sandLargeTataTrucks),
            gravelM3 = round2(totalGravelM3),
            gravelSmallTrucks = round2(gravelSmallTrucks),
            gravelLargeTataTrucks = round2(gravelLargeTataTrucks),
            waterLiters = round1(totalWaterLiters),
            waterDrums = round1(waterDrums),
            estimatedSteelTonnes = round3(estimatedSteelTonnes),
            estimatedSteelKg = round1(estimatedSteelKg),
            mixGrade = mixGrade,
            rebarDiameter = rebarDiameter,
            estimatedSteel12mBars = primaryBreakdown.bars12mExact,
            estimatedSteel12mBarsRounded = primaryBreakdown.bars12mRounded,
            steelDiameterOptions = options
        )
    }

    private fun round1(v: Double) = (v * 10.0).roundToInt() / 10.0
    private fun round2(v: Double) = (v * 100.0).roundToInt() / 100.0
    private fun round3(v: Double) = (v * 1000.0).roundToInt() / 1000.0
}

object SteelEstimator {

    fun breakdownFromWeightKg(
        weightKg: Double,
        diameter: RebarDiameter,
        cutBarsCount: Int? = null,
        singleBarLengthM: Double? = null,
        roleLabelAr: String = ""
    ): RebarDiameterBreakdown {
        val safeWeight = weightKg.coerceAtLeast(0.0)
        val bars12m = if (diameter.weightPer12mBarKg > 0.0) safeWeight / diameter.weightPer12mBarKg else 0.0
        val roundedBars = if (bars12m > 0.0) ceil(bars12m).toInt() else 0
        return RebarDiameterBreakdown(
            diameter = diameter,
            weightKg = (safeWeight * 10.0).roundToInt() / 10.0,
            weightTonnes = ((safeWeight / 1000.0) * 1000.0).roundToInt() / 1000.0,
            bars12mExact = (bars12m * 10.0).roundToInt() / 10.0,
            bars12mRounded = roundedBars,
            cutBarsCount = cutBarsCount,
            singleBarLengthM = singleBarLengthM?.let { (it * 100.0).roundToInt() / 100.0 },
            roleLabelAr = roleLabelAr
        )
    }

    /**
     * Calculates detailed steel rebar weight and count of 12m commercial bars per diameter d
     */
    fun calculateDetailedRebar(
        diameter: RebarDiameter,
        numberOfBars: Int,
        barLengthMeters: Double,
        lapLengthMeters: Double = 0.0,
        isIncludeStirrups: Boolean = false,
        stirrupDiameter: RebarDiameter = RebarDiameter.D8,
        memberLengthMeters: Double = 0.0,
        stirrupSpacingCm: Double = 20.0,
        memberWidthMeters: Double = 0.25,
        memberDepthMeters: Double = 0.50,
        concreteCoverCm: Double = 2.5,
        wastePercentage: Double = 5.0
    ): SteelDetailedResult {
        val count = numberOfBars.coerceAtLeast(1)
        val lengthPerBarWithLap = barLengthMeters + lapLengthMeters
        val totalMainLength = count * lengthPerBarWithLap
        val mainWeightKg = totalMainLength * diameter.weightPerMeterKg

        var stirrupsCount = 0
        var stirrupTotalLengthM = 0.0
        var stirrupsWeightKg = 0.0
        var singleStirrupPerimeter = 0.0

        if (isIncludeStirrups && stirrupSpacingCm > 0 && memberLengthMeters > 0) {
            val spacingM = stirrupSpacingCm / 100.0
            stirrupsCount = ceil(memberLengthMeters / spacingM).toInt() + 1

            // Stirrup perimeter deducting cover on both sides:
            val coverM = concreteCoverCm / 100.0
            val effWidth = (memberWidthMeters - 2 * coverM).coerceAtLeast(0.05)
            val effDepth = (memberDepthMeters - 2 * coverM).coerceAtLeast(0.05)
            // Hook length BS 8110: 2 hooks of 10d or 12d ~ 2 * 0.10m
            val hooksLength = 2 * (10 * (stirrupDiameter.mm / 1000.0))
            singleStirrupPerimeter = 2 * (effWidth + effDepth) + hooksLength
            stirrupTotalLengthM = stirrupsCount * singleStirrupPerimeter
            stirrupsWeightKg = stirrupTotalLengthM * stirrupDiameter.weightPerMeterKg
        }

        val wasteFactor = 1.0 + wastePercentage / 100.0
        val grossMainKg = mainWeightKg * wasteFactor
        val grossStirrupsKg = stirrupsWeightKg * wasteFactor
        val totalCombinedKg = grossMainKg + grossStirrupsKg
        val totalCombinedTonnes = totalCombinedKg / 1000.0

        // In commercial market: 1 bar = 12 meters
        val main12mBars = (totalMainLength * wasteFactor) / 12.0
        val main12mRounded = if (main12mBars > 0.0) ceil(main12mBars).toInt() else 0

        val stirrups12mBars = if (isIncludeStirrups && stirrupTotalLengthM > 0.0) {
            (stirrupTotalLengthM * wasteFactor) / 12.0
        } else 0.0
        val stirrups12mRounded = if (stirrups12mBars > 0.0) ceil(stirrups12mBars).toInt() else 0

        // Binding wire (سلك الرباط): approximately 7 kg per tonne of rebar
        val bindingWireKg = (totalCombinedTonnes * 7.0).coerceAtLeast(0.5)

        val breakdownList = mutableListOf<RebarDiameterBreakdown>()
        breakdownList.add(
            RebarDiameterBreakdown(
                diameter = diameter,
                weightKg = (grossMainKg * 10.0).roundToInt() / 10.0,
                weightTonnes = ((grossMainKg / 1000.0) * 1000.0).roundToInt() / 1000.0,
                bars12mExact = (main12mBars * 10.0).roundToInt() / 10.0,
                bars12mRounded = main12mRounded,
                cutBarsCount = count,
                singleBarLengthM = (lengthPerBarWithLap * 100.0).roundToInt() / 100.0,
                roleLabelAr = "تسليح رئيسي"
            )
        )
        if (isIncludeStirrups && stirrupsCount > 0 && grossStirrupsKg > 0.0) {
            breakdownList.add(
                RebarDiameterBreakdown(
                    diameter = stirrupDiameter,
                    weightKg = (grossStirrupsKg * 10.0).roundToInt() / 10.0,
                    weightTonnes = ((grossStirrupsKg / 1000.0) * 1000.0).roundToInt() / 1000.0,
                    bars12mExact = (stirrups12mBars * 10.0).roundToInt() / 10.0,
                    bars12mRounded = stirrups12mRounded,
                    cutBarsCount = stirrupsCount,
                    singleBarLengthM = (singleStirrupPerimeter * 100.0).roundToInt() / 100.0,
                    roleLabelAr = "كانات (Stirrups)"
                )
            )
        }

        return SteelDetailedResult(
            diameter = diameter,
            numberOfBars = count,
            totalLengthMeters = (totalMainLength * 100.0).roundToInt() / 100.0,
            totalWeightKg = (totalCombinedKg * 10.0).roundToInt() / 10.0,
            totalWeightTonnes = (totalCombinedTonnes * 1000.0).roundToInt() / 1000.0,
            numberOf12mBars = (main12mBars * 10.0).roundToInt() / 10.0,
            lapLengthMeters = lapLengthMeters,
            stirrupsCount = stirrupsCount,
            stirrupLengthMeters = (singleStirrupPerimeter * 100.0).roundToInt() / 100.0,
            stirrupsDiameter = stirrupDiameter,
            stirrupsWeightKg = (grossStirrupsKg * 10.0).roundToInt() / 10.0,
            bindingWireKg = (bindingWireKg * 10.0).roundToInt() / 10.0,
            wastePercentage = wastePercentage,
            mainWeightKg = (grossMainKg * 10.0).roundToInt() / 10.0,
            mainWeightTonnes = ((grossMainKg / 1000.0) * 1000.0).roundToInt() / 1000.0,
            main12mBarsRounded = main12mRounded,
            stirrupsWeightTonnes = ((grossStirrupsKg / 1000.0) * 1000.0).roundToInt() / 1000.0,
            stirrups12mBars = (stirrups12mBars * 10.0).roundToInt() / 10.0,
            stirrups12mBarsRounded = stirrups12mRounded,
            diameterBreakdown = breakdownList
        )
    }

    /**
     * Rebar calculation by element empirical ratio (e.g. Columns 150 kg/m3)
     */
    fun calculateByEmpiricalRatio(
        concreteVolumeM3: Double,
        kgPerM3: Double,
        wastePercentage: Double = 5.0
    ): Pair<Double, Double> {
        val totalKg = concreteVolumeM3 * kgPerM3 * (1.0 + wastePercentage / 100.0)
        val totalTonnes = totalKg / 1000.0
        return Pair((totalKg * 10.0).roundToInt() / 10.0, (totalTonnes * 1000.0).roundToInt() / 1000.0)
    }

    fun calculateEmpiricalDiameterBreakdown(
        totalKg: Double,
        primaryDiameter: RebarDiameter
    ): Pair<RebarDiameterBreakdown, List<RebarDiameterBreakdown>> {
        val primary = breakdownFromWeightKg(
            weightKg = totalKg,
            diameter = primaryDiameter,
            roleLabelAr = "القطر المختار"
        )
        val all = listOf(
            RebarDiameter.D10,
            RebarDiameter.D12,
            RebarDiameter.D16,
            RebarDiameter.D20,
            RebarDiameter.D25
        ).map { d ->
            breakdownFromWeightKg(
                weightKg = totalKg,
                diameter = d,
                roleLabelAr = if (d == primaryDiameter) "القطر المختار" else "بديل مكافئ"
            )
        }
        return primary to all
    }
}

object MasonryEstimator {

    enum class BrickType(val arName: String, val lengthCm: Double, val widthCm: Double, val heightCm: Double) {
        SUDANESE_RED_BRICK("طوب أحمر بلدي سوداني (20×10×7 سم)", 20.0, 10.0, 7.0),
        SUDANESE_RED_BRICK_SMALL("طوب أحمر كمائن (19×9×6 سم)", 19.0, 9.0, 6.0),
        HOLLOW_CONCRETE_BLOCK_20("بلك خرساني مفرغ (40×20×20 سم)", 40.0, 20.0, 20.0),
        HOLLOW_CONCRETE_BLOCK_15("بلك خرساني مفرغ (40×20×15 سم)", 40.0, 15.0, 20.0),
        HOLLOW_CONCRETE_BLOCK_10("بلك قواطع (40×10×20 سم)", 40.0, 10.0, 20.0),
        SOLID_CEMENT_BRICK("طوب أسمنتي مصمت (20×10×6 سم)", 20.0, 10.0, 6.0)
    }

    enum class WallThickness(val arName: String, val thicknessM: Double) {
        HALF_BRICK("نصف طوبة (قاطع 10 سم)", 0.10),
        ONE_BRICK("طوبة كاملة (جدار حامل 20 سم)", 0.20),
        ONE_AND_HALF("طوبة ونصف (جدار سميك 30 سم)", 0.30),
        BLOCK_20("بلك 20 سم", 0.20),
        BLOCK_15("بلك 15 سم", 0.15)
    }

    fun calculateMasonry(
        wallLengthMeters: Double,
        wallHeightMeters: Double,
        openingsAreaM2: Double = 0.0, // Doors and windows
        brickType: BrickType = BrickType.SUDANESE_RED_BRICK,
        wallThickness: WallThickness = WallThickness.HALF_BRICK,
        wastagePercentage: Double = 5.0
    ): MasonryResult {
        val rawArea = (wallLengthMeters * wallHeightMeters - openingsAreaM2).coerceAtLeast(0.0)
        val volumeM3 = rawArea * wallThickness.thicknessM

        // Bricks count per m2 or m3
        val baseBricksCount = when (brickType) {
            BrickType.SUDANESE_RED_BRICK,
            BrickType.SUDANESE_RED_BRICK_SMALL -> {
                if (wallThickness == WallThickness.HALF_BRICK) {
                    // ~52 to 55 bricks per m2 for 10cm wall with mortar
                    rawArea * 53.0
                } else if (wallThickness == WallThickness.ONE_BRICK) {
                    // ~470 bricks per m3 or ~95-105 per m2
                    volumeM3 * 475.0
                } else {
                    volumeM3 * 480.0
                }
            }
            BrickType.HOLLOW_CONCRETE_BLOCK_20,
            BrickType.HOLLOW_CONCRETE_BLOCK_15,
            BrickType.HOLLOW_CONCRETE_BLOCK_10 -> {
                // 40cm x 20cm block -> 12.5 blocks per m2
                rawArea * 12.5
            }
            BrickType.SOLID_CEMENT_BRICK -> {
                if (wallThickness == WallThickness.HALF_BRICK) rawArea * 55.0 else volumeM3 * 500.0
            }
        }

        val totalBricksWithWaste = (baseBricksCount * (1.0 + wastagePercentage / 100.0)).roundToInt()
        val totalThousand = totalBricksWithWaste / 1000.0

        // Mortar calculation:
        // In Sudanese practice: 1000 red bricks require ~3.5 to 4.0 bags of cement (50kg) and 0.45 m3 sand
        val cementBags: Double
        val sandM3: Double

        if (brickType == BrickType.HOLLOW_CONCRETE_BLOCK_20 ||
            brickType == BrickType.HOLLOW_CONCRETE_BLOCK_15 ||
            brickType == BrickType.HOLLOW_CONCRETE_BLOCK_10
        ) {
            // For blocks: 100 blocks need ~1.5 bags cement and 0.15 m3 sand
            cementBags = (totalBricksWithWaste / 100.0) * 1.5
            sandM3 = (totalBricksWithWaste / 100.0) * 0.15
        } else {
            // For red bricks:
            cementBags = totalThousand * 3.8
            sandM3 = totalThousand * 0.45
        }

        val sandTrucks = sandM3 / SudaneseCommercialUnits.SMALL_DUMP_TRUCK_M3
        // Water: ~1 drum per 2000 bricks or 5 bags
        val waterDrums = (cementBags / 6.0).coerceAtLeast(0.5)

        return MasonryResult(
            wallAreaM2 = (rawArea * 100.0).roundToInt() / 100.0,
            wallVolumeM3 = (volumeM3 * 100.0).roundToInt() / 100.0,
            brickType = brickType.arName,
            totalBricksCount = totalBricksWithWaste,
            totalBricksThousand = (totalThousand * 100.0).roundToInt() / 100.0,
            cementBagsForMortar = (cementBags * 10.0).roundToInt() / 10.0,
            sandM3ForMortar = (sandM3 * 100.0).roundToInt() / 100.0,
            sandSmallTrucks = (sandTrucks * 10.0).roundToInt() / 10.0,
            waterDrums = (waterDrums * 10.0).roundToInt() / 10.0,
            wastePercentage = wastagePercentage
        )
    }
}

object PlasterFinishingEstimator {

    enum class PlasterType(val arName: String, val cementBagsPerM3Sand: Double, val defaultThicknessCm: Double) {
        INTERNAL_PLASTER("بياض داخلي تخشين (1.5 - 2 سم)", 6.5, 2.0),
        EXTERNAL_PLASTER("بياض خارجي مقاوم للطقس (2.5 سم)", 7.5, 2.5),
        SPATTERDASH("طرطشة مسمارية تحضيرية (0.5 سم)", 9.0, 0.5),
        CEILING_PLASTER("بياض أسقف (1.5 سم)", 7.0, 1.5)
    }

    fun calculatePlaster(
        areaM2: Double,
        thicknessCm: Double,
        plasterType: PlasterType = PlasterType.INTERNAL_PLASTER,
        wastagePercentage: Double = 10.0
    ): PlasterResult {
        val volumeM3 = areaM2 * (thicknessCm / 100.0) * (1.0 + wastagePercentage / 100.0)
        // Sand loose volume is approx equal to mortar volume + 10%
        val sandM3 = volumeM3 * 1.05
        val cementBags = sandM3 * plasterType.cementBagsPerM3Sand
        val sandTrucks = sandM3 / SudaneseCommercialUnits.SMALL_DUMP_TRUCK_M3
        val waterDrums = (cementBags / 5.0).coerceAtLeast(0.5)

        return PlasterResult(
            areaM2 = (areaM2 * 10.0).roundToInt() / 10.0,
            thicknessCm = thicknessCm,
            plasterTypeAr = plasterType.arName,
            cementBags = (cementBags * 10.0).roundToInt() / 10.0,
            sandM3 = (sandM3 * 100.0).roundToInt() / 100.0,
            sandSmallTrucks = (sandTrucks * 10.0).roundToInt() / 10.0,
            waterDrums = (waterDrums * 10.0).roundToInt() / 10.0
        )
    }

    fun calculateTiles(
        areaM2: Double,
        tileLengthCm: Double = 60.0,
        tileWidthCm: Double = 60.0,
        tilesPerBox: Int = 4,
        wastagePercentage: Double = 8.0
    ): TilesResult {
        val grossArea = areaM2 * (1.0 + wastagePercentage / 100.0)
        val singleTileAreaM2 = (tileLengthCm / 100.0) * (tileWidthCm / 100.0)
        val totalTiles = ceil(grossArea / singleTileAreaM2).toInt()
        val totalBoxes = ceil(totalTiles.toDouble() / tilesPerBox).toInt()

        // Bedding mortar: 1 bag of cement per 5 m2 + sand 0.05 m3 per m2
        val cementBags = grossArea / 5.0
        val sandM3 = grossArea * 0.05
        // Grout: approx 0.35 kg per m2
        val groutKg = grossArea * 0.35

        return TilesResult(
            floorAreaM2 = (areaM2 * 10.0).roundToInt() / 10.0,
            tileLengthCm = tileLengthCm,
            tileWidthCm = tileWidthCm,
            tilesPerBox = tilesPerBox,
            totalTilesCount = totalTiles,
            totalBoxes = totalBoxes,
            cementBagsForBedding = (cementBags * 10.0).roundToInt() / 10.0,
            sandM3ForBedding = (sandM3 * 100.0).roundToInt() / 100.0,
            tileGroutKg = (groutKg * 10.0).roundToInt() / 10.0
        )
    }

    fun calculatePainting(
        surfaceAreaM2: Double,
        coatsCount: Int = 2
    ): PaintingResult {
        // Primer / Sealer: 1 Liter covers ~10-12 m2 (1 bucket 18L covers ~180 m2)
        val primerLiters = surfaceAreaM2 / 10.0
        val primerBuckets = ceil(primerLiters / 18.0)

        // Putty (معجون): 2 coats, 1 bag of 20kg covers ~15-18 m2 for 2 coats
        val puttyBags = ceil(surfaceAreaM2 / 16.0)

        // Emulsion / Plastic paint: 1 Liter covers ~7 m2 per coat
        val paintLiters = (surfaceAreaM2 * coatsCount) / 7.0
        val paintBuckets18L = ceil(paintLiters / 18.0)

        return PaintingResult(
            surfaceAreaM2 = (surfaceAreaM2 * 10.0).roundToInt() / 10.0,
            primerLiters = (primerLiters * 10.0).roundToInt() / 10.0,
            primerBuckets = primerBuckets,
            puttyBags20Kg = puttyBags,
            paintLiters = (paintLiters * 10.0).roundToInt() / 10.0,
            paintBuckets18L = paintBuckets18L,
            coatsCount = coatsCount
        )
    }
}
