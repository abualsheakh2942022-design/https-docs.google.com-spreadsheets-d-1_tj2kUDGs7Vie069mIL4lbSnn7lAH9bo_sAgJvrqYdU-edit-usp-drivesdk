package com.example.domain

/**
 * Concrete Mix specification based on BS 8110 / BS 5328 and Sudanese field practice.
 */
enum class ConcreteGrade(
    val code: String,
    val arabicName: String,
    val ratioDescription: String,
    val cementBagsPerM3: Double, // 50kg bags
    val sandRatioM3: Double,     // m3 per m3 of concrete (loose)
    val gravelRatioM3: Double,   // m3 gravel/crushed stone per m3 of concrete
    val waterLitersPerM3: Double,// Liters per m3 (W/C ~ 0.5)
    val recommendedUsage: String
) {
    C15(
        code = "C15",
        arabicName = "خرسانة عادية (فرشة نظافة)",
        ratioDescription = "1 : 3 : 6",
        cementBagsPerM3 = 5.0, // 250 kg/m3
        sandRatioM3 = 0.47,
        gravelRatioM3 = 0.94,
        waterLitersPerM3 = 160.0,
        recommendedUsage = "فرشة نظافة تحت القواعد (Blinding) ودكات أرضية غير مسلحة"
    ),
    C20(
        code = "C20",
        arabicName = "خرسانة مسلحة عادية (قواعد وميد)",
        ratioDescription = "1 : 2 : 4",
        cementBagsPerM3 = 7.0, // 350 kg/m3
        sandRatioM3 = 0.44,
        gravelRatioM3 = 0.88,
        waterLitersPerM3 = 180.0,
        recommendedUsage = "القواعد الخرسانية المنفصلة، الميد والشناجات الأرضية، والأسوار"
    ),
    C25(
        code = "C25",
        arabicName = "خرسانة مسلحة قياسية (أعمدة وأسقف)",
        ratioDescription = "1 : 1.5 : 3",
        cementBagsPerM3 = 8.0, // 400 kg/m3
        sandRatioM3 = 0.42,
        gravelRatioM3 = 0.84,
        waterLitersPerM3 = 190.0,
        recommendedUsage = "الأعمدة الحاملة، الأسقف الخرسانية والكمرات حسب الكود البريطاني BS 8110"
    ),
    C30(
        code = "C30",
        arabicName = "خرسانة عالية المقاومة (أبراج وخزانات)",
        ratioDescription = "1 : 1 : 2",
        cementBagsPerM3 = 9.0, // 450 kg/m3
        sandRatioM3 = 0.38,
        gravelRatioM3 = 0.76,
        waterLitersPerM3 = 195.0,
        recommendedUsage = "الخزانات المائية، الأعمدة للأبراج، والجسور ذات الأحمال العالية"
    ),
    CUSTOM(
        code = "CUSTOM",
        arabicName = "خلطة مخصصة",
        ratioDescription = "محدد من قبل المهندس",
        cementBagsPerM3 = 8.0,
        sandRatioM3 = 0.42,
        gravelRatioM3 = 0.84,
        waterLitersPerM3 = 190.0,
        recommendedUsage = "حسب التصميم الإنشائي للمشروع ومواصفات الاستشاري"
    )
}

/**
 * Sudanese commercial transport units for aggregates
 */
object SudaneseCommercialUnits {
    // 1 قلاب صغير (نصف لوري) ~ 4 متر مكعب
    const val SMALL_DUMP_TRUCK_M3 = 4.0
    // 1 قلاب تاتة / لوري كبير ~ 16 متر مكعب
    const val LARGE_TRUCK_TATA_M3 = 16.0
    // 1 لوري سكسويل ~ 11 متر مكعب
    const val SIX_WHEEL_TRUCK_M3 = 11.0
    // 1 طن أسمنت = 20 شوال (وزن الشوال 50 كجم)
    const val BAGS_PER_TON_CEMENT = 20.0
    // 1 برميل ماء = 200 لتر
    const val LITERS_PER_WATER_DRUM = 200.0
    // الطوب الأحمر: ألف طوبة = 1000 طوبة
    const val BRICKS_PER_THOUSAND = 1000.0
}

/**
 * Standard Rebar Diameters according to BS 4449
 */
enum class RebarDiameter(
    val mm: Int,
    val weightPerMeterKg: Double, // d^2 / 162
    val weightPer12mBarKg: Double,
    val barsPerTon: Int,
    val commonUseSudan: String
) {
    D8(8, 0.395, 4.74, 211, "الكانات (Stirrups) وشبك البلاطات الخفيفة"),
    D10(10, 0.617, 7.40, 135, "فرش وغطاء الأسقف والميد الخفيفة"),
    D12(12, 0.888, 10.66, 94, "الأسقف، القواعد، وأسياخ الأعمدة 4-6 أسياخ"),
    D16(16, 1.578, 18.94, 53, "تسليح رئيسي للأعمدة والكمرات والقواعد"),
    D20(20, 2.466, 29.59, 34, "أعمدة المباني متعددة الطوابق والكمرات الرئيسية"),
    D25(25, 3.853, 46.24, 21, "القواعد الكبيرة، اللبشة، والأبراج العالية"),
    D32(32, 6.313, 75.76, 13, "المنشآت الثقيلة والجسور والأساسات العميقة");

    companion object {
        fun fromMm(mm: Int): RebarDiameter = entries.firstOrNull { it.mm == mm } ?: D12
    }
}

/**
 * Structural Elements for Concrete estimation
 */
enum class StructuralElementType(val titleAr: String, val steelRatioKgPerM3: Double) {
    FOOTING_ISOLATED("قواعد منفصلة (Isolated Footings)", 85.0),
    FOOTING_COMBINED("قواعد مشتركة (Combined Footings)", 100.0),
    RAFT_FOUNDATION("لبشة خرسانية (Raft Foundation)", 110.0),
    GROUND_BEAM("ميد وشناجات أرضية (Ground Beams)", 115.0),
    COLUMN_RECT("أعمدة مستطيلة/مربعة (Columns)", 150.0),
    COLUMN_CIRCULAR("أعمدة دائرية (Circular Columns)", 160.0),
    BEAMS("كمرات وجسور سقف (Beams)", 125.0),
    SOLID_SLAB("سقف مصمت (Solid Slab)", 95.0),
    RIBBED_SLAB("سقف هوردي (Ribbed / Hollow Block)", 110.0),
    FLAT_SLAB("سقف فلات سلاب (Flat Slab)", 135.0),
    STAIRS("سلالم ودرج (Stairs)", 120.0),
    SLAB_ON_GRADE("دكة خرسانية وأرضيات (Slab on Grade)", 50.0),
    CUSTOM_VOLUME("حجم خرسانة مباشر", 100.0)
}

/**
 * Rebar quantity summary grouped by standard diameter d (weight + commercial 12m bars count)
 */
data class RebarDiameterBreakdown(
    val diameter: RebarDiameter,
    val weightKg: Double,
    val weightTonnes: Double,
    val bars12mExact: Double,
    val bars12mRounded: Int,
    val cutBarsCount: Int? = null,
    val singleBarLengthM: Double? = null,
    val roleLabelAr: String = ""
)

/**
 * Results of Concrete Quantity calculation
 */
data class ConcreteEstimateResult(
    val volumeNetM3: Double,
    val volumeWithWasteM3: Double,
    val wastagePercentage: Double,
    val cementBags: Double,
    val cementTonnes: Double,
    val sandM3: Double,
    val sandSmallTrucks: Double, // قلابات صغيرة 4م3
    val sandLargeTataTrucks: Double, // لواري تاتة 16م3
    val gravelM3: Double,
    val gravelSmallTrucks: Double,
    val gravelLargeTataTrucks: Double,
    val waterLiters: Double,
    val waterDrums: Double, // براميل 200 لتر
    val estimatedSteelTonnes: Double,
    val estimatedSteelKg: Double,
    val mixGrade: ConcreteGrade,
    val rebarDiameter: RebarDiameter = RebarDiameter.D16,
    val estimatedSteel12mBars: Double = 0.0,
    val estimatedSteel12mBarsRounded: Int = 0,
    val steelDiameterOptions: List<RebarDiameterBreakdown> = emptyList()
)

/**
 * Detailed Steel rebar calculation result
 */
data class SteelDetailedResult(
    val diameter: RebarDiameter,
    val numberOfBars: Int,
    val totalLengthMeters: Double,
    val totalWeightKg: Double,
    val totalWeightTonnes: Double,
    val numberOf12mBars: Double,
    val lapLengthMeters: Double,
    val stirrupsCount: Int = 0,
    val stirrupLengthMeters: Double = 0.0,
    val stirrupsDiameter: RebarDiameter = RebarDiameter.D8,
    val stirrupsWeightKg: Double = 0.0,
    val bindingWireKg: Double = 0.0, // سلك الرباط
    val wastePercentage: Double = 5.0,
    val mainWeightKg: Double = 0.0,
    val mainWeightTonnes: Double = 0.0,
    val main12mBarsRounded: Int = 0,
    val stirrupsWeightTonnes: Double = 0.0,
    val stirrups12mBars: Double = 0.0,
    val stirrups12mBarsRounded: Int = 0,
    val diameterBreakdown: List<RebarDiameterBreakdown> = emptyList()
)

/**
 * Masonry estimation result
 */
data class MasonryResult(
    val wallAreaM2: Double,
    val wallVolumeM3: Double,
    val brickType: String,
    val totalBricksCount: Int,
    val totalBricksThousand: Double, // بالألف طوبة
    val cementBagsForMortar: Double,
    val sandM3ForMortar: Double,
    val sandSmallTrucks: Double,
    val waterDrums: Double,
    val wastePercentage: Double
)

/**
 * Plastering & Finishing result
 */
data class PlasterResult(
    val areaM2: Double,
    val thicknessCm: Double,
    val plasterTypeAr: String,
    val cementBags: Double,
    val sandM3: Double,
    val sandSmallTrucks: Double,
    val waterDrums: Double
)

/**
 * Flooring & Tiles result
 */
data class TilesResult(
    val floorAreaM2: Double,
    val tileLengthCm: Double,
    val tileWidthCm: Double,
    val tilesPerBox: Int,
    val totalTilesCount: Int,
    val totalBoxes: Int,
    val cementBagsForBedding: Double,
    val sandM3ForBedding: Double,
    val tileGroutKg: Double // بودرة الترويبة
)

/**
 * Painting result
 */
data class PaintingResult(
    val surfaceAreaM2: Double,
    val primerLiters: Double,      // سيلر مائي
    val primerBuckets: Double,     // بستلات سيلر (18 لتر)
    val puttyBags20Kg: Double,     // شكائر معجون (20 كجم)
    val paintLiters: Double,       // دهان بلاستيك/حريري
    val paintBuckets18L: Double,   // بستلات دهان
    val coatsCount: Int
)

/**
 * Bill of Quantities item with unit pricing in SDG
 */
data class BoqItem(
    val id: String,
    val categoryAr: String,
    val itemDescriptionAr: String,
    val quantity: Double,
    val unitAr: String,
    val unitPriceSdg: Double
) {
    val totalPriceSdg: Double
        get() = quantity * unitPriceSdg
}
