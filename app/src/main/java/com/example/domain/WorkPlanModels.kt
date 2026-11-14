package com.example.domain

import com.example.data.db.ProjectEntity
import com.example.data.db.WorkPlanEntity
import com.example.data.db.WorkPlanPhaseEntity
import com.example.data.db.WorkPlanWithPhases
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class WorkPlanSourceType(val arLabel: String) {
    CALCULATED_CURRENT("من الحسابات الحالية"),
    SAVED_PROJECT("من مشروع محفوظ"),
    SPEC_DOCUMENT("من كراسة مواصفات"),
    MANUAL_ENTRY("مشروع مدخل يدوياً");

    companion object {
        fun fromName(name: String): WorkPlanSourceType =
            entries.firstOrNull { it.name == name } ?: MANUAL_ENTRY
    }
}

enum class PhaseStatus(val arLabel: String) {
    NOT_STARTED("لم يبدأ"),
    IN_PROGRESS("قيد التنفيذ"),
    COMPLETED("مكتمل"),
    DELAYED("متأخر / متابعة");

    companion object {
        fun fromName(name: String): PhaseStatus =
            entries.firstOrNull { it.name == name } ?: NOT_STARTED
    }

    fun nextCycle(): PhaseStatus = when (this) {
        NOT_STARTED -> IN_PROGRESS
        IN_PROGRESS -> COMPLETED
        COMPLETED -> NOT_STARTED
        DELAYED -> IN_PROGRESS
    }
}

enum class PhaseCategory(val arLabel: String, val colorHex: Long) {
    PREPARATION("تجهيز وحفر", 0xFF475569),
    SUBSTRUCTURE("الأساسات والقواعد", 0xFF0D9488),
    SUPERSTRUCTURE("الهيكل والخرسانات", 0xFF1A365D),
    MASONRY("المباني والطوب", 0xFFC2410C),
    MEP("الكهرباء والسباكة", 0xFF0284C7),
    FINISHING("التشطيبات والتسليم", 0xFF7C3AED);

    companion object {
        fun fromName(name: String): PhaseCategory =
            entries.firstOrNull { it.name == name } ?: SUPERSTRUCTURE
    }
}

enum class SchedulePace(val arLabel: String, val factor: Double, val descAr: String) {
    FAST("سريع (ضغط عمل)", 0.75, "تقليص المدد ~25% بمضاعفة الأطقم والشدات مع الحفاظ على الحد الأدنى لفك الشدات"),
    STANDARD("قياسي (BS 8110)", 1.0, "توازن مثالي يراعي المعالجة 7 أيام وفك الشدات 14-21 يوماً"),
    RELAXED("مرن / مريح", 1.25, "هامش زمني +25% لظروف التوريد والطقس الحار بالسودان");

    companion object {
        fun fromName(name: String): SchedulePace =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class ManualProjectTemplate(val arLabel: String, val descAr: String) {
    RESIDENTIAL_BUILDING("مبنى خرساني متكامل (هيكل + مباني + تشطيبات)", "مناسب للمنازل والعمائر السكنية في السودان"),
    STRUCTURAL_SHELL_ONLY("أعمال هيكل خرساني فقط (عظم)", "حفر وأساسات وأعمدة وأسقف فقط"),
    FINISHING_RENOVATION("أعمال مباني وتشطيبات (محارة + بلاط + دهان)", "للمباني الجاهزة هيكلياً أو الترميم"),
    BOUNDARY_WALL_ANNEX("سور خارجي / ملحق أرضي", "قواعد شريطية وأعمدة ومباني طوب أحمر")
}

data class WorkPlanSummary(
    val totalDurationDays: Int,
    val totalWeeksRounded: Double,
    val weightedProgressPercent: Int,
    val completedPhasesCount: Int,
    val inProgressPhasesCount: Int,
    val pendingPhasesCount: Int,
    val expectedFinishDateMillis: Long
)

object WorkPlanGenerator {

    private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun computeSummary(plan: WorkPlanEntity, phases: List<WorkPlanPhaseEntity>): WorkPlanSummary {
        if (phases.isEmpty()) {
            return WorkPlanSummary(
                totalDurationDays = 0,
                totalWeeksRounded = 0.0,
                weightedProgressPercent = 0,
                completedPhasesCount = 0,
                inProgressPhasesCount = 0,
                pendingPhasesCount = 0,
                expectedFinishDateMillis = plan.startDateMillis
            )
        }
        val minStart = phases.minOf { it.startDay }.coerceAtLeast(0)
        val maxEnd = phases.maxOf { it.startDay + it.durationDays.coerceAtLeast(1) }
        val spanDays = (maxEnd - minStart).coerceAtLeast(1)
        val totalEffortDays = phases.sumOf { it.durationDays.coerceAtLeast(1) }.coerceAtLeast(1)
        val weightedProgress = phases.sumOf {
            it.durationDays.coerceAtLeast(1) * it.progressPercent.coerceIn(0, 100)
        } / totalEffortDays

        val completed = phases.count { PhaseStatus.fromName(it.statusName) == PhaseStatus.COMPLETED || it.progressPercent >= 100 }
        val inProgress = phases.count {
            val s = PhaseStatus.fromName(it.statusName)
            s == PhaseStatus.IN_PROGRESS || s == PhaseStatus.DELAYED
        }
        val pending = phases.size - completed - inProgress

        return WorkPlanSummary(
            totalDurationDays = spanDays,
            totalWeeksRounded = ((spanDays / 7.0) * 10.0).roundToInt() / 10.0,
            weightedProgressPercent = weightedProgress.coerceIn(0, 100),
            completedPhasesCount = completed,
            inProgressPhasesCount = inProgress,
            pendingPhasesCount = pending.coerceAtLeast(0),
            expectedFinishDateMillis = plan.startDateMillis + maxEnd * ONE_DAY_MILLIS
        )
    }

    fun formatDateArabic(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        return fmt.format(Date(millis))
    }

    fun dayOffsetToDate(startDateMillis: Long, dayOffset: Int): String {
        return formatDateArabic(startDateMillis + dayOffset.coerceAtLeast(0) * ONE_DAY_MILLIS)
    }

    /**
     * Generates a flexible schedule directly from current app calculations (Concrete, Steel, Masonry, Finishing, or Blueprint).
     */
    fun generateFromCurrentCalculations(
        title: String,
        location: String,
        pace: SchedulePace,
        startDateMillis: Long,
        concreteResult: ConcreteEstimateResult?,
        concreteElement: StructuralElementType,
        steelTonnes: Double,
        steelDiameterMm: Int,
        steelBars12m: Int,
        masonryResult: MasonryResult?,
        plasterResult: PlasterResult?,
        tilesResult: TilesResult?,
        paintResult: PaintingResult?,
        blueprintConcreteM3: Double = 0.0,
        blueprintSteelTonnes: Double = 0.0,
        blueprintBricksCount: Int = 0,
        blueprintFinishAreaM2: Double = 0.0
    ): Pair<WorkPlanEntity, List<WorkPlanPhaseEntity>> {
        val phases = mutableListOf<WorkPlanPhaseEntity>()
        var cursorDay = 0
        var order = 0

        val effectiveConcreteM3 = if (blueprintConcreteM3 > 0.0) blueprintConcreteM3 else (concreteResult?.volumeWithWasteM3 ?: 0.0)
        val effectiveSteelTonnes = if (blueprintSteelTonnes > 0.0) blueprintSteelTonnes else steelTonnes
        val effectiveBricks = if (blueprintBricksCount > 0) blueprintBricksCount else (masonryResult?.totalBricksCount ?: 0)
        val effectivePlasterM2 = if (blueprintFinishAreaM2 > 0.0) blueprintFinishAreaM2 else (plasterResult?.areaM2 ?: 0.0)
        val effectiveTilesM2 = tilesResult?.floorAreaM2 ?: 0.0
        val effectivePaintM2 = paintResult?.surfaceAreaM2 ?: 0.0

        // 1. Site mobilization & layout
        val mobDays = scaleDays(2, pace, minDays = 1)
        phases.add(
            phase(
                order = order++,
                title = "تجهيز الموقع، التخطيط (الخنزيرة) وتوريد المواد",
                category = PhaseCategory.PREPARATION,
                startDay = cursorDay,
                durationDays = mobDays,
                quantityHint = if (effectiveConcreteM3 > 0) "توريد مبدئي لـ $effectiveConcreteM3 م³ خرسانة" else "تجهيز الموقع والمخزن والمياه",
                resourcesNote = "تحديد المحاور والمناسيب وتأمين براميل المياه وتشوين الرمل والكنكري النظيف"
            )
        )
        cursorDay += mobDays

        // 2. Concrete & Steel works if present
        if (effectiveConcreteM3 > 0.0) {
            val isSubstructure = concreteElement in listOf(
                StructuralElementType.FOOTING_ISOLATED,
                StructuralElementType.FOOTING_COMBINED,
                StructuralElementType.RAFT_FOUNDATION,
                StructuralElementType.GROUND_BEAM
            )
            if (isSubstructure || blueprintConcreteM3 > 0.0) {
                val excDays = scaleDays(max(2, ceil(effectiveConcreteM3 / 12.0).toInt()), pace, minDays = 2)
                phases.add(
                    phase(
                        order = order++,
                        title = "الحفر، الدمك، وفرشة النظافة (C15)",
                        category = PhaseCategory.SUBSTRUCTURE,
                        startDay = cursorDay,
                        durationDays = excDays,
                        quantityHint = "خرسانة نظافة وأساسات (~${round1(effectiveConcreteM3 * 0.25)} م³)",
                        resourcesNote = "فحص تربة التأسيس ورش ومراعاة العزل البيتوميني في السودان"
                    )
                )
                cursorDay += excDays
            }

            val formworkDays = scaleDays(max(3, ceil(effectiveConcreteM3 / 8.0).toInt()), pace, minDays = 2)
            val rebarInfo = if (effectiveSteelTonnes > 0.0) {
                "حديد: ${round2(effectiveSteelTonnes)} طن (~$steelBars12m سيخة Ø$steelDiameterMm مم بطول 12م)"
            } else {
                "حديد ${concreteResult?.estimatedSteelTonnes ?: 0.0} طن"
            }
            phases.add(
                phase(
                    order = order++,
                    title = if (blueprintConcreteM3 > 0.0) "نجارة وحدادة عناصر الهيكل والخرسانات" else "نجارة وحدادة ${concreteElement.titleAr.substringBefore(" (")}",
                    category = if (isSubstructure) PhaseCategory.SUBSTRUCTURE else PhaseCategory.SUPERSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = formworkDays,
                    quantityHint = rebarInfo,
                    resourcesNote = "مراجعة الغطاء الخرساني (BS 8110: 25-50 مم)، التراكب (50d)، وتثبيت البسكويت"
                )
            )
            cursorDay += max(2, formworkDays - 1) // slight overlap before pour

            val pourDays = scaleDays(max(1, ceil(effectiveConcreteM3 / 22.0).toInt()), pace, minDays = 1)
            phases.add(
                phase(
                    order = order++,
                    title = "صب الخرسانة (${concreteResult?.mixGrade?.code ?: "C25"}) والدمك بالهزاز",
                    category = if (isSubstructure) PhaseCategory.SUBSTRUCTURE else PhaseCategory.SUPERSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = pourDays,
                    quantityHint = "${concreteResult?.cementBags ?: round1(effectiveConcreteM3 * 8)} شوال أسمنت | $effectiveConcreteM3 م³",
                    resourcesNote = "الصب في الأوقات المعتدلة صباحاً/مساءً بالسودان وأخذ مكعبات الفحص"
                )
            )
            cursorDay += pourDays

            // BS 8110 curing & striking period
            val isSlabOrBeam = concreteElement in listOf(
                StructuralElementType.SOLID_SLAB,
                StructuralElementType.RIBBED_SLAB,
                StructuralElementType.FLAT_SLAB,
                StructuralElementType.BEAMS,
                StructuralElementType.STAIRS
            ) || blueprintConcreteM3 > 0.0
            val curingDays = if (isSlabOrBeam) {
                if (pace == SchedulePace.FAST) 14 else 16
            } else {
                7
            }
            phases.add(
                phase(
                    order = order++,
                    title = if (isSlabOrBeam) "المعالجة بالماء وفك الشدات (BS 8110)" else "المعالجة المستمرة بالماء (Curing 7 أيام)",
                    category = if (isSubstructure) PhaseCategory.SUBSTRUCTURE else PhaseCategory.SUPERSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = curingDays,
                    quantityHint = "${concreteResult?.waterDrums ?: round1(effectiveConcreteM3 * 0.9)} برميل ماء معالجة",
                    resourcesNote = if (isSlabOrBeam) "BS 8110: فك الجوانب بعد 24-48 ساعة، وإبقاء الدعائم تحت الأسقف 14-21 يوماً" else "الرش بالماء مرتين يومياً وتغطية الخرسانة بالخيش الرطب لمدة 7 أيام"
                )
            )
            // Masonry can overlap after initial 7 days curing!
            cursorDay += if (isSlabOrBeam) 7 else 5
        } else if (effectiveSteelTonnes > 0.0) {
            val rebarDays = scaleDays(max(2, ceil(effectiveSteelTonnes * 1.8).toInt()), pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "تقطيع وتكريب وتجميع حديد التسليح",
                    category = PhaseCategory.SUPERSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = rebarDays,
                    quantityHint = "${round2(effectiveSteelTonnes)} طن | $steelBars12m سيخة (12م) Ø$steelDiameterMm مم",
                    resourcesNote = "تجهيز الكانات وسلك الرباط والتأكد من أطوال التراكب والركوب"
                )
            )
            cursorDay += rebarDays
        }

        // 3. Masonry works
        if (effectiveBricks > 0) {
            val masonryDays = scaleDays(max(3, ceil(effectiveBricks / 1100.0).toInt()), pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "أعمال المباني والطوب والأعتاب",
                    category = PhaseCategory.MASONRY,
                    startDay = cursorDay,
                    durationDays = masonryDays,
                    quantityHint = "$effectiveBricks طوبة (${round2(effectiveBricks / 1000.0)} ألف) | ${masonryResult?.cementBagsForMortar ?: round1(effectiveBricks * 0.0038)} شوال مونة",
                    resourcesNote = "غمر الطوب الأحمر بالماء قبل البناء وضبط الشاقولية والزوايا وصب الأعتاب"
                )
            )
            cursorDay += max(2, masonryDays - 1)
        }

        // 4. First-fix MEP (always helpful when doing masonry -> plaster)
        if (effectiveBricks > 0 || effectivePlasterM2 > 0.0) {
            val mepDays = scaleDays(3, pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "تمديدات الكهرباء والسباكة التأسيسية (First Fix)",
                    category = PhaseCategory.MEP,
                    startDay = cursorDay,
                    durationDays = mepDays,
                    quantityHint = "تكسير وتركيب مواسير الكهرباء والسباكة",
                    resourcesNote = "اختبار ضغط مواسير التغذية قبل تغطيتها بالبياض"
                )
            )
            cursorDay += mepDays
        }

        // 5. Plastering
        if (effectivePlasterM2 > 0.0) {
            val plasterDays = scaleDays(max(3, ceil(effectivePlasterM2 / 35.0).toInt()), pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "الطرطشة المسمارية والبياض (المحارة)",
                    category = PhaseCategory.FINISHING,
                    startDay = cursorDay,
                    durationDays = plasterDays,
                    quantityHint = "${round1(effectivePlasterM2)} م² | ${plasterResult?.cementBags ?: round1(effectivePlasterM2 * 0.16)} شوال أسمنت",
                    resourcesNote = "عمل البؤج والأوتار ورش الطرطشة 3 أيام قبل التخشين"
                )
            )
            cursorDay += max(2, plasterDays - 1)
        }

        // 6. Tiling
        if (effectiveTilesM2 > 0.0) {
            val tileDays = scaleDays(max(2, ceil(effectiveTilesM2 / 22.0).toInt()), pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "أعمال الأرضيات والسيراميك والترويبة",
                    category = PhaseCategory.FINISHING,
                    startDay = cursorDay,
                    durationDays = tileDays,
                    quantityHint = "${round1(effectiveTilesM2)} م² (${tilesResult?.totalBoxes ?: 0} كرتونة)",
                    resourcesNote = "ضبط الميول للحمامات والشرفات ومونة التركيب والروبة"
                )
            )
            cursorDay += tileDays
        }

        // 7. Painting & Handover
        if (effectivePaintM2 > 0.0 || effectivePlasterM2 > 0.0) {
            val area = if (effectivePaintM2 > 0.0) effectivePaintM2 else effectivePlasterM2
            val paintDays = scaleDays(max(2, ceil(area / 65.0).toInt()), pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "الدهانات (سيلر + معجون + وجهين) والتسليم النهائي",
                    category = PhaseCategory.FINISHING,
                    startDay = cursorDay,
                    durationDays = paintDays,
                    quantityHint = "${round1(area)} م² (${paintResult?.paintBuckets18L?.toInt() ?: ceil(area / 63.0).toInt()} بستلة دهان)",
                    resourcesNote = "صنفرة المعجون والنظافة العامة واستلام الأعمال"
                )
            )
        }

        val plan = WorkPlanEntity(
            title = title.ifBlank { "خطة عمل الكميات المحسوبة" },
            location = location.ifBlank { "الخرطوم، السودان" },
            sourceType = WorkPlanSourceType.CALCULATED_CURRENT.name,
            paceName = pace.name,
            startDateMillis = startDateMillis,
            notes = "خطة عمل مولدة تلقائياً من كميات الحسابات الحالية مع مراعاة اشتراطات BS 8110."
        )
        return plan to phases
    }

    /**
     * Generates a flexible schedule from a saved ProjectEntity in Room.
     */
    fun generateFromSavedProject(
        project: ProjectEntity,
        pace: SchedulePace,
        startDateMillis: Long = System.currentTimeMillis()
    ): Pair<WorkPlanEntity, List<WorkPlanPhaseEntity>> {
        val text = "${project.summaryText}\n${project.detailedDetails}"
        val extractedConcreteM3 = extractNumberBefore(text, listOf("م³", "م3")) ?: 0.0
        val extractedSteelTonnes = extractNumberBefore(text, listOf("طن حديد", "طن تسليح", "طن")) ?: 0.0
        val extractedBricksThousand = extractNumberBefore(text, listOf("ألف طوبة", "ألف")) ?: 0.0
        val extractedAreaM2 = extractNumberBefore(text, listOf("م²", "م2")) ?: 0.0

        val phases = mutableListOf<WorkPlanPhaseEntity>()
        var cursorDay = 0
        var order = 0

        val mobDays = scaleDays(2, pace, minDays = 1)
        phases.add(
            phase(
                order = order++,
                title = "تجهيز الموقع ومراجعة المخططات (${project.category})",
                category = PhaseCategory.PREPARATION,
                startDay = cursorDay,
                durationDays = mobDays,
                quantityHint = project.summaryText.take(70),
                resourcesNote = "اعتماد العينات ومراجعة الأسعار والتوريدات في ${project.clientOrLocation}"
            )
        )
        cursorDay += mobDays

        when {
            project.category.contains("مقايسة") || project.category.contains("مخطط") -> {
                val vol = if (extractedConcreteM3 > 0) extractedConcreteM3 else 35.0
                val excDays = scaleDays(max(3, ceil(vol / 10.0).toInt()), pace, 2)
                phases.add(phase(order++, "أعمال الحفر وفرشة النظافة (C15)", PhaseCategory.SUBSTRUCTURE, cursorDay, excDays, "حسب بنود المقايسة (${round1(vol * 0.25)} م³)", "فحص قاع الحفر والدمك"))
                cursorDay += excDays

                val fndDays = scaleDays(max(5, ceil(vol / 7.0).toInt()), pace, 3)
                phases.add(phase(order++, "تنفيذ الأساسات والقواعد والميد", PhaseCategory.SUBSTRUCTURE, cursorDay, fndDays, "خرسانة وتسليح الأساسات", "تغطية خرسانية 50 مم وعزل بيتومين"))
                cursorDay += fndDays

                val supDays = scaleDays(max(7, ceil(vol / 5.0).toInt()), pace, 4)
                phases.add(phase(order++, "الأعمدة والكمرات والأسقف (BS 8110)", PhaseCategory.SUPERSTRUCTURE, cursorDay, supDays, "خرسانة ${round1(vol)} م³", "صب في الأوقات المعتدلة وفك الشدات 14-21 يوماً"))
                cursorDay += (supDays - 3).coerceAtLeast(3)

                val bricksCount = if (extractedBricksThousand > 0) (extractedBricksThousand * 1000).toInt() else 12000
                val masDays = scaleDays(max(5, ceil(bricksCount / 1400.0).toInt()), pace, 3)
                phases.add(phase(order++, "أعمال المباني والطوب والأعتاب", PhaseCategory.MASONRY, cursorDay, masDays, "$bricksCount طوبة", "رش الطوب بالماء وضبط الأوتار"))
                cursorDay += (masDays - 2).coerceAtLeast(2)

                val mepDays = scaleDays(4, pace, 2)
                phases.add(phase(order++, "التمديدات التأسيسية (كهرباء وسباكة)", PhaseCategory.MEP, cursorDay, mepDays, "تأسيس الشبكات", "فحص الضغط قبل البياض"))
                cursorDay += mepDays

                val finArea = if (extractedAreaM2 > 0) extractedAreaM2 else 180.0
                val finDays = scaleDays(max(6, ceil(finArea / 25.0).toInt()), pace, 4)
                phases.add(phase(order++, "البياض، السيراميك، والدهانات", PhaseCategory.FINISHING, cursorDay, finDays, "${round1(finArea)} م² تشطيبات", "بؤج وأوتار ثم سيراميك ومعجون ودهان"))
            }
            project.category.contains("خرسانة") -> {
                val vol = if (extractedConcreteM3 > 0) extractedConcreteM3 else 15.0
                val prepDays = scaleDays(max(3, ceil(vol / 6.0).toInt()), pace, 2)
                phases.add(phase(order++, "النجارة المسلحة وتجهيز الشدات", PhaseCategory.SUPERSTRUCTURE, cursorDay, prepDays, "${round1(vol)} م³ خرسانة", "مراجعة الرأسية والدعائم والشيرب"))
                cursorDay += (prepDays - 1).coerceAtLeast(1)

                val steelDays = scaleDays(max(2, ceil(vol / 8.0).toInt()), pace, 2)
                phases.add(phase(order++, "تشكيل وتركيب حديد التسليح والكانات", PhaseCategory.SUPERSTRUCTURE, cursorDay, steelDays, "مطابق للمخطط الإنشائي", "فحص التراكب (50d) وتركيب البسكويت"))
                cursorDay += steelDays

                val pourDays = scaleDays(max(1, ceil(vol / 20.0).toInt()), pace, 1)
                phases.add(phase(order++, "صب الخرسانة والدمك بالهزاز", PhaseCategory.SUPERSTRUCTURE, cursorDay, pourDays, "${round1(vol)} م³ صبة", "تجنب ذروة الحر في السودان"))
                cursorDay += pourDays

                phases.add(phase(order++, "المعالجة بالماء وفك الشدات (BS 8110)", PhaseCategory.SUPERSTRUCTURE, cursorDay, if (pace == SchedulePace.FAST) 14 else 16, "معالجة 7 أيام + فك 14 يوم", "رش مستمر بالماء يومياً"))
            }
            project.category.contains("تسليح") -> {
                val tons = if (extractedSteelTonnes > 0) extractedSteelTonnes else 3.0
                val cutDays = scaleDays(max(2, ceil(tons * 1.5).toInt()), pace, 2)
                phases.add(phase(order++, "توريد وفرد وتقطيع الأسياخ (12م)", PhaseCategory.SUPERSTRUCTURE, cursorDay, cutDays, "${round2(tons)} طن حديد", "تخزين بعيداً عن الرطوبة المباشرة"))
                cursorDay += (cutDays - 1).coerceAtLeast(1)

                val fixDays = scaleDays(max(2, ceil(tons * 1.5).toInt()), pace, 2)
                phases.add(phase(order++, "تكريب الكانات والتركيب والرباط", PhaseCategory.SUPERSTRUCTURE, cursorDay, fixDays, "تجهيز وربط الأسياخ", "اعتماد الاستشاري قبل إغلاق النجارة"))
            }
            project.category.contains("مباني") -> {
                val bricks = if (extractedBricksThousand > 0) (extractedBricksThousand * 1000).toInt() else 5000
                val soakDays = scaleDays(1, pace, 1)
                phases.add(phase(order++, "تشوين الطوب وغمره بالماء وتحديد القد", PhaseCategory.MASONRY, cursorDay, soakDays, "$bricks طوبة", "ضمان نظافة الطوب الأحمر"))
                cursorDay += soakDays

                val buildDays = scaleDays(max(3, ceil(bricks / 1100.0).toInt()), pace, 2)
                phases.add(phase(order++, "بناء الحوائط وتركيب الحلوق والأعتاب", PhaseCategory.MASONRY, cursorDay, buildDays, "$bricks طوبة + مونة", "الحد الأقصى للبناء اليومي 1.5م ارتفاعاً"))
                cursorDay += buildDays

                phases.add(phase(order++, "رش المباني بالماء والكحلة", PhaseCategory.MASONRY, cursorDay, 3, "معالجة المونة 3 أيام", "رش صباحاً ومساءً"))
            }
            else -> {
                val area = if (extractedAreaM2 > 0) extractedAreaM2 else 150.0
                val prepDays = scaleDays(2, pace, 1)
                phases.add(phase(order++, "تجهيز الأسطح والطرطشة والبؤج", PhaseCategory.FINISHING, cursorDay, prepDays, "${round1(area)} م²", "تنظيف الحوائط ورشها بالماء"))
                cursorDay += prepDays

                val workDays = scaleDays(max(4, ceil(area / 30.0).toInt()), pace, 3)
                phases.add(phase(order++, "تنفيذ أعمال البياض / البلاط والدهان", PhaseCategory.FINISHING, cursorDay, workDays, "${round1(area)} م² تشطيب", "ضبط الاستقامة والميول ونعومة المعجون"))
                cursorDay += workDays

                phases.add(phase(order++, "الفحص والنظافة والتسليم", PhaseCategory.FINISHING, cursorDay, 2, "تسليم نهائي", "استلام هندسي كامل"))
            }
        }

        val plan = WorkPlanEntity(
            title = "خطة: ${project.title}",
            location = project.clientOrLocation,
            sourceType = WorkPlanSourceType.SAVED_PROJECT.name,
            linkedProjectId = project.id,
            paceName = pace.name,
            startDateMillis = startDateMillis,
            notes = "خطة زمنية مرتبطة بالمشروع المحفوظ (${project.category})."
        )
        return plan to phases
    }

    /**
     * Generates a customizable plan from manual project entry (template or blank start).
     */
    fun generateManualPlan(
        title: String,
        location: String,
        floorsCount: Int,
        areaPerFloorM2: Double,
        template: ManualProjectTemplate,
        pace: SchedulePace,
        startDateMillis: Long,
        startBlank: Boolean
    ): Pair<WorkPlanEntity, List<WorkPlanPhaseEntity>> {
        val safeFloors = floorsCount.coerceIn(1, 12)
        val safeArea = areaPerFloorM2.coerceAtLeast(30.0)
        val scaleFactor = (safeArea / 150.0).coerceIn(0.6, 3.5)
        val phases = mutableListOf<WorkPlanPhaseEntity>()
        var cursorDay = 0
        var order = 0

        if (startBlank) {
            phases.add(
                phase(
                    order = 0,
                    title = "المرحلة الأولى: تجهيز الموقع وبدء الأعمال",
                    category = PhaseCategory.PREPARATION,
                    startDay = 0,
                    durationDays = 3,
                    quantityHint = "أدخل الكميات أو الموارد المطلوبة",
                    resourcesNote = "يمكنك تعديل الاسم والمدة أو إضافة مراحل جديدة بحرية"
                )
            )
            phases.add(
                phase(
                    order = 1,
                    title = "المرحلة الثانية: التنفيذ الرئيسي",
                    category = PhaseCategory.SUPERSTRUCTURE,
                    startDay = 3,
                    durationDays = 7,
                    quantityHint = "مساحة تقريبية ${round1(safeArea * safeFloors)} م²",
                    resourcesNote = "اضبط تاريخ البداية أو فعّل الإزاحة التلقائية"
                )
            )
        } else {
            when (template) {
                ManualProjectTemplate.RESIDENTIAL_BUILDING,
                ManualProjectTemplate.STRUCTURAL_SHELL_ONLY -> {
                    val mobDays = scaleDays(3, pace, 2)
                    phases.add(phase(order++, "تخطيط الموقع والحفر والردم", PhaseCategory.PREPARATION, cursorDay, mobDays, "مسطح ${round1(safeArea)} م²", "خنزيرة ومناسيب وحفر للأساسات"))
                    cursorDay += mobDays

                    val fndDays = scaleDays((8 * scaleFactor).roundToInt().coerceAtLeast(5), pace, 4)
                    phases.add(phase(order++, "فرشة النظافة والقواعد والميد الأرضية", PhaseCategory.SUBSTRUCTURE, cursorDay, fndDays, "خرسانة C20 + عزل بيتوميني", "معالجة بالماء 7 أيام بعد الصب"))
                    cursorDay += fndDays

                    for (floor in 1..safeFloors) {
                        val floorName = when (floor) {
                            1 -> "الطابق الأرضي"
                            2 -> "الطابق الأول"
                            else -> "الطابق رقم $floor"
                        }
                        val colDays = scaleDays((5 * scaleFactor).roundToInt().coerceAtLeast(3), pace, 3)
                        phases.add(phase(order++, "أعمدة $floorName (نجارة + حدادة + صب)", PhaseCategory.SUPERSTRUCTURE, cursorDay, colDays, "رتبة C25 | تغطية 30 مم", "تأكد من الشاقولية والرش بالماء"))
                        cursorDay += colDays

                        val slabDays = scaleDays((7 * scaleFactor).roundToInt().coerceAtLeast(5), pace, 4)
                        phases.add(phase(order++, "نجارة وحدادة وصب سقف $floorName", PhaseCategory.SUPERSTRUCTURE, cursorDay, slabDays, "سقف ${round1(safeArea)} م²", "مراجعة التراكب وسمك البلاطة قبل الصب"))
                        cursorDay += slabDays

                        val cureDays = if (pace == SchedulePace.FAST) 14 else 16
                        phases.add(phase(order++, "معالجة وفك شدة سقف $floorName (BS 8110)", PhaseCategory.SUPERSTRUCTURE, cursorDay, cureDays, "مدة أمان الدعائم 14-21 يوماً", "عدم التحميل المبكر على البلاطة"))
                        // Next floor columns can start 5 days after slab pour while props remain!
                        cursorDay += 5
                    }

                    if (template == ManualProjectTemplate.RESIDENTIAL_BUILDING) {
                        cursorDay += 6
                        val masDays = scaleDays((6 * safeFloors * scaleFactor).roundToInt().coerceAtLeast(5), pace, 4)
                        phases.add(phase(order++, "أعمال المباني لجميع الطوابق", PhaseCategory.MASONRY, cursorDay, masDays, "$safeFloors طوابق", "رش الطوب وضبط الأعتاب"))
                        cursorDay += (masDays - 3).coerceAtLeast(3)

                        val mepDays = scaleDays((5 * safeFloors).coerceAtLeast(4), pace, 3)
                        phases.add(phase(order++, "تأسيس السباكة والكهرباء والتكييف", PhaseCategory.MEP, cursorDay, mepDays, "تمديدات واختبار ضغط", "قبل البدء بالمحارة"))
                        cursorDay += mepDays

                        val finDays = scaleDays((12 * safeFloors * scaleFactor).roundToInt().coerceAtLeast(8), pace, 6)
                        phases.add(phase(order++, "البياض والسيراميك والدهانات والتسليم", PhaseCategory.FINISHING, cursorDay, finDays, "${round1(safeArea * safeFloors)} م²", "تشطيب متكامل وتنظيف وتسليم"))
                    }
                }
                ManualProjectTemplate.FINISHING_RENOVATION -> {
                    val totalArea = safeArea * safeFloors
                    val f = (totalArea / 150.0).coerceIn(0.6, 4.0)
                    val mepDays = scaleDays((4 * f).roundToInt().coerceAtLeast(3), pace, 2)
                    phases.add(phase(order++, "مراجعة وتعديل التمديدات الكهربائية والصحية", PhaseCategory.MEP, cursorDay, mepDays, "${round1(totalArea)} م²", "فحص التسريب والعزل المائي للحمامات"))
                    cursorDay += mepDays

                    val plasterDays = scaleDays((7 * f).roundToInt().coerceAtLeast(4), pace, 3)
                    phases.add(phase(order++, "الطرطشة والبؤج والبياض (المحارة)", PhaseCategory.FINISHING, cursorDay, plasterDays, "سمك 2 سم داخلي / 2.5 سم خارجي", "المعالجة بالرش 3 أيام"))
                    cursorDay += (plasterDays - 2).coerceAtLeast(2)

                    val tileDays = scaleDays((6 * f).roundToInt().coerceAtLeast(3), pace, 2)
                    phases.add(phase(order++, "تركيب السيراميك والبورسلين والرخام", PhaseCategory.FINISHING, cursorDay, tileDays, "ضبط الميول والترويبة", "حماية البلاط قبل الدهان"))
                    cursorDay += tileDays

                    val paintDays = scaleDays((6 * f).roundToInt().coerceAtLeast(3), pace, 2)
                    phases.add(phase(order++, "السيلر والمعجون والدهانات والتسليم", PhaseCategory.FINISHING, cursorDay, paintDays, "وجهين دهان نهائي", "التسليم النهائي للمالك"))
                }
                ManualProjectTemplate.BOUNDARY_WALL_ANNEX -> {
                    val excDays = scaleDays(3, pace, 2)
                    phases.add(phase(order++, "التخطيط وحفر الشريطية وفرشة النظافة", PhaseCategory.SUBSTRUCTURE, cursorDay, excDays, "عمق تأسيس آمن", "رش التربة ومعالجة الأرضة"))
                    cursorDay += excDays

                    val colDays = scaleDays(5, pace, 3)
                    phases.add(phase(order++, "صب القواعد والميد والأعمدة", PhaseCategory.SUPERSTRUCTURE, cursorDay, colDays, "فواصل تمدد كل 15-20م للأسوار", "معالجة 7 أيام"))
                    cursorDay += colDays

                    val wallDays = scaleDays(6, pace, 3)
                    phases.add(phase(order++, "بناء الطوب الأحمر وتقفيل السور/الملحق", PhaseCategory.MASONRY, cursorDay, wallDays, "طوبة كاملة 20 سم أو نصف طوبة", "كحلة وطرطشة وحماية"))
                    cursorDay += wallDays

                    val finDays = scaleDays(5, pace, 3)
                    phases.add(phase(order++, "البياض الخارجي المقاوم والدهان", PhaseCategory.FINISHING, cursorDay, finDays, "دهان خارجي مقاوم للشمس", "تسليم نهائي"))
                }
            }
        }

        val plan = WorkPlanEntity(
            title = title.ifBlank { template.arLabel.substringBefore(" (") },
            location = location.ifBlank { "الخرطوم، السودان" },
            sourceType = WorkPlanSourceType.MANUAL_ENTRY.name,
            paceName = pace.name,
            startDateMillis = startDateMillis,
            notes = "خطة مدخلة يدوياً ($safeFloors طوابق، مسطح ${round1(safeArea)} م²)."
        )
        return plan to phases
    }

    /**
     * Generates a flexible schedule directly from an analyzed specification booklet (كراسة مواصفات).
     */
    fun generateFromSpecDocument(
        title: String,
        location: String,
        pace: SchedulePace,
        startDateMillis: Long,
        report: SpecDocumentAnalysisReport,
        concreteGrossM3: Double,
        steelTonnes: Double,
        steelByDiameter: List<RebarDiameterBreakdown>,
        bricksCount: Int,
        finishingAreaM2: Double,
        excavationM3: Double,
        waterproofingAreaM2: Double
    ): Pair<WorkPlanEntity, List<WorkPlanPhaseEntity>> {
        val phases = mutableListOf<WorkPlanPhaseEntity>()
        var cursorDay = 0
        var order = 0
        val tech = report.technicalSummary

        val mobDays = scaleDays(2, pace, minDays = 1)
        phases.add(
            phase(
                order = order++,
                title = "مراجعة كراسة المواصفات واعتماد المواد والتخطيط",
                category = PhaseCategory.PREPARATION,
                startDay = cursorDay,
                durationDays = mobDays,
                quantityHint = tech.cementTypeAr.take(65),
                resourcesNote = "اعتماد عينات ${tech.concreteMixSpecsAr.take(60)} و${tech.steelGradeAndDiametersAr.take(55)}"
            )
        )
        cursorDay += mobDays

        if (excavationM3 > 0.0 || concreteGrossM3 > 0.0) {
            val volRef = if (excavationM3 > 0.0) excavationM3 else concreteGrossM3 * 0.4
            val excDays = scaleDays(max(2, ceil(volRef / 18.0).toInt()), pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "أعمال الحفر، الدمك، وفرشة النظافة والتربة",
                    category = PhaseCategory.SUBSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = excDays,
                    quantityHint = if (excavationM3 > 0.0) "حفر وردم ${round1(excavationM3)} م³" else "تأسيس وفرشة نظافة C15",
                    resourcesNote = "التأكد من منسوب التأسيس ومقاومة الكبريتات في الأساسات"
                )
            )
            cursorDay += excDays
        }

        if (waterproofingAreaM2 > 0.0) {
            val wpDays = scaleDays(max(2, ceil(waterproofingAreaM2 / 60.0).toInt()), pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "أعمال العزل المائي والحماية (وفق الكراسة)",
                    category = PhaseCategory.SUBSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = wpDays,
                    quantityHint = "${round1(waterproofingAreaM2)} م² عزل",
                    resourcesNote = "اختبار العزل وحماية الأساسات أو الأسطح حسب بنود الكراسة"
                )
            )
            cursorDay += max(1, wpDays - 1)
        }

        if (concreteGrossM3 > 0.0 || steelTonnes > 0.0) {
            val formworkDays = scaleDays(max(3, ceil(maxOf(concreteGrossM3 / 8.0, steelTonnes * 1.5)).toInt()), pace, minDays = 2)
            val rebarSummary = if (steelByDiameter.isNotEmpty()) {
                steelByDiameter.joinToString(" + ") { "Ø${it.diameter.mm}: ${it.bars12mRounded} سيخة" }
            } else {
                "${round2(steelTonnes)} طن حديد"
            }
            phases.add(
                phase(
                    order = order++,
                    title = "الشدات الخشبية وتقطيع وربط التسليح (BS 8110)",
                    category = PhaseCategory.SUPERSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = formworkDays,
                    quantityHint = rebarSummary,
                    resourcesNote = "الغطاء الخرساني المطلوب: ${tech.concreteCoverSpecAr.take(65)}"
                )
            )
            cursorDay += max(2, formworkDays - 1)

            val pourDays = scaleDays(max(1, ceil(concreteGrossM3.coerceAtLeast(10.0) / 22.0).toInt()), pace, minDays = 1)
            phases.add(
                phase(
                    order = order++,
                    title = "صب الخرسانات (${report.recommendedMixGrade.code}) وأخذ مكعبات الفحص",
                    category = PhaseCategory.SUPERSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = pourDays,
                    quantityHint = "${round1(concreteGrossM3)} م³ خرسانة",
                    resourcesNote = tech.concreteMixSpecsAr.take(90)
                )
            )
            cursorDay += pourDays

            val curingDays = if (pace == SchedulePace.FAST) 14 else 16
            phases.add(
                phase(
                    order = order++,
                    title = "المعالجة بالماء وفك الشدات (BS 8110)",
                    category = PhaseCategory.SUPERSTRUCTURE,
                    startDay = cursorDay,
                    durationDays = curingDays,
                    quantityHint = "رش يومي 7 أيام + فك 14-21 يوماً",
                    resourcesNote = "تغطية الصبة بالخيش الرطب في أجواء السودان الحارة"
                )
            )
            cursorDay += 7
        }

        if (bricksCount > 0) {
            val masDays = scaleDays(max(3, ceil(bricksCount / 1150.0).toInt()), pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "أعمال المباني والطوب والمونة",
                    category = PhaseCategory.MASONRY,
                    startDay = cursorDay,
                    durationDays = masDays,
                    quantityHint = "$bricksCount طوبة (${round2(bricksCount / 1000.0)} ألف)",
                    resourcesNote = tech.masonryAndMortarSpecsAr.take(90)
                )
            )
            cursorDay += max(2, masDays - 1)
        }

        if (finishingAreaM2 > 0.0 || bricksCount > 0) {
            val mepDays = scaleDays(3, pace, minDays = 2)
            phases.add(
                phase(
                    order = order++,
                    title = "التمديدات التأسيسية (كهرباء وسباكة) واختبار الضغط",
                    category = PhaseCategory.MEP,
                    startDay = cursorDay,
                    durationDays = mepDays,
                    quantityHint = "أعمال التأسيس قبل البياض",
                    resourcesNote = "التأكد من مسارات التمديدات والاختبار قبل التغطية"
                )
            )
            cursorDay += mepDays
        }

        if (finishingAreaM2 > 0.0) {
            val finDays = scaleDays(max(4, ceil(finishingAreaM2 / 30.0).toInt()), pace, minDays = 3)
            phases.add(
                phase(
                    order = order++,
                    title = "أعمال البياض والسيراميك والدهانات حسب الكراسة",
                    category = PhaseCategory.FINISHING,
                    startDay = cursorDay,
                    durationDays = finDays,
                    quantityHint = "${round1(finishingAreaM2)} م² تشطيبات",
                    resourcesNote = tech.finishingSpecsAr.take(90)
                )
            )
        }

        val plan = WorkPlanEntity(
            title = title.ifBlank { tech.projectTitle.ifBlank { "خطة عمل كراسة المواصفات" } },
            location = location.ifBlank { "السودان" },
            sourceType = WorkPlanSourceType.SPEC_DOCUMENT.name,
            paceName = pace.name,
            startDateMillis = startDateMillis,
            notes = "مولدة من كراسة المواصفات (${report.recommendedMixGrade.code}) — ${tech.cementTypeAr.take(50)}"
        )
        return plan to phases
    }

    /**
     * Flexibly updates a phase and optionally cascades start-day shifts to subsequent phases.
     */
    fun updatePhaseFlexible(
        allPhasesSorted: List<WorkPlanPhaseEntity>,
        updatedPhase: WorkPlanPhaseEntity,
        autoShiftSubsequent: Boolean
    ): List<WorkPlanPhaseEntity> {
        val oldPhase = allPhasesSorted.firstOrNull { it.id == updatedPhase.id } ?: return allPhasesSorted
        val oldEnd = oldPhase.startDay + oldPhase.durationDays
        val newEnd = updatedPhase.startDay + updatedPhase.durationDays
        val endDelta = newEnd - oldEnd

        return allPhasesSorted.map { item ->
            when {
                item.id == updatedPhase.id -> updatedPhase
                autoShiftSubsequent && item.sortOrder > updatedPhase.sortOrder && endDelta != 0 -> {
                    item.copy(startDay = (item.startDay + endDelta).coerceAtLeast(0))
                }
                else -> item
            }
        }
    }

    /**
     * Rescales a plan cleanly when switching between FAST / STANDARD / RELAXED pace.
     */
    fun rescalePlanPace(
        phasesSorted: List<WorkPlanPhaseEntity>,
        oldPace: SchedulePace,
        newPace: SchedulePace
    ): List<WorkPlanPhaseEntity> {
        if (oldPace == newPace || phasesSorted.isEmpty()) return phasesSorted
        val ratio = newPace.factor / oldPace.factor
        var prevNewStart = 0
        var prevOldStart = 0
        val result = mutableListOf<WorkPlanPhaseEntity>()

        phasesSorted.forEachIndexed { idx, p ->
            val oldStart = p.startDay
            val newStart = if (idx == 0) oldStart else {
                val gap = ((oldStart - prevOldStart) * ratio).roundToInt()
                (prevNewStart + gap).coerceAtLeast(0)
            }
            // Respect BS 8110 curing/striking minimum for curing phases
            val isCuring = p.title.contains("فك شد") || p.title.contains("المعالجة") || p.title.contains("BS 8110")
            val minDur = if (isCuring) 7 else 1
            val newDur = (p.durationDays * ratio).roundToInt().coerceAtLeast(minDur)
            result.add(p.copy(startDay = newStart, durationDays = newDur))
            prevOldStart = oldStart
            prevNewStart = newStart
        }
        return result
    }

    private fun phase(
        order: Int,
        title: String,
        category: PhaseCategory,
        startDay: Int,
        durationDays: Int,
        quantityHint: String,
        resourcesNote: String
    ) = WorkPlanPhaseEntity(
        planId = 0,
        sortOrder = order,
        title = title,
        categoryName = category.name,
        startDay = startDay.coerceAtLeast(0),
        durationDays = durationDays.coerceAtLeast(1),
        progressPercent = 0,
        statusName = PhaseStatus.NOT_STARTED.name,
        quantityHint = quantityHint,
        resourcesNote = resourcesNote
    )

    private fun scaleDays(baseDays: Int, pace: SchedulePace, minDays: Int): Int {
        return (baseDays * pace.factor).roundToInt().coerceAtLeast(minDays)
    }

    private fun extractNumberBefore(text: String, units: List<String>): Double? {
        for (unit in units) {
            val escaped = Regex.escape(unit)
            val regex = Regex("""(\d+(?:\.\d+)?)\s*$escaped""")
            val match = regex.find(text)
            val value = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            if (value != null && value > 0.0) return value
        }
        return null
    }

    private fun round1(v: Double) = (v * 10.0).roundToInt() / 10.0
    private fun round2(v: Double) = (v * 100.0).roundToInt() / 100.0
}
