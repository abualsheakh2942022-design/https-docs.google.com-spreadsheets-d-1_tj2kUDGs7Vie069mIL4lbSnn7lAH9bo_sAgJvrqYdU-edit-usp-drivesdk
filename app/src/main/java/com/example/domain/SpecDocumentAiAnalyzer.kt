package com.example.domain

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "SpecDocumentAiAnalyzer"

enum class SpecItemCategory(val arLabel: String) {
    EXCAVATION("حفر وردم وتأسيس"),
    CONCRETE("أعمال الخرسانات"),
    STEEL("حديد التسليح"),
    MASONRY("أعمال المباني والطوب"),
    FINISHING("البياض والتشطيبات"),
    WATERPROOFING("أعمال العزل والحماية");

    companion object {
        fun fromCode(raw: String): SpecItemCategory {
            return when (raw.uppercase().trim()) {
                "EXCAVATION" -> EXCAVATION
                "CONCRETE" -> CONCRETE
                "STEEL" -> STEEL
                "MASONRY" -> MASONRY
                "FINISHING" -> FINISHING
                "WATERPROOFING" -> WATERPROOFING
                else -> CONCRETE
            }
        }
    }
}

data class SpecTechnicalSummary(
    val projectTitle: String,
    val clientOrConsultant: String,
    val concreteMixSpecsAr: String,
    val cementTypeAr: String,
    val steelGradeAndDiametersAr: String,
    val concreteCoverSpecAr: String,
    val masonryAndMortarSpecsAr: String,
    val finishingSpecsAr: String,
    val bs8110ComplianceNotesAr: List<String>
)

data class SpecWorkItem(
    val id: String = UUID.randomUUID().toString(),
    val itemCode: String,
    val category: SpecItemCategory,
    val nameAr: String,
    val specRequirementAr: String,
    val unitAr: String,
    val count: Int,
    val lengthM: Double,
    val widthM: Double,
    val heightOrThicknessM: Double,
    val diameterMm: Int = 0,
    val explicitQuantityFromDoc: Double = 0.0,
    val volumeM3: Double = 0.0,
    val areaM2: Double = 0.0,
    val weightKg: Double = 0.0,
    val notesAr: String = "",
    val isIncluded: Boolean = true
)

data class SpecDocumentAnalysisReport(
    val technicalSummary: SpecTechnicalSummary,
    val recommendedMixGrade: ConcreteGrade,
    val defaultRebarDiameter: RebarDiameter,
    val items: List<SpecWorkItem>,
    val engineeringNotesAr: List<String>,
    val sourceSummary: String
)

/**
 * Analyzes civil engineering specification booklets (كراسات الشروط والمواصفات),
 * Word (.docx), Excel (.xlsx), multi-page PDFs, CSV/TXT BOQs, or pasted contract clauses
 * using Firebase AI Logic (Gemini) structured JSON output aligned with BS 8110 & Sudanese SSMO.
 */
object SpecDocumentAiAnalyzer {

    private val specSchema = Schema.obj(
        properties = mapOf(
            "projectTitle" to Schema.string(description = "Arabic project or specification booklet title"),
            "clientOrConsultant" to Schema.string(description = "Client, owner, or consulting office name if mentioned, else غير محدد"),
            "recommendedMixGrade" to Schema.enumeration(
                values = listOf("C15", "C20", "C25", "C30"),
                description = "Primary BS 8110 concrete grade required by the specification"
            ),
            "defaultRebarDiameterMm" to Schema.integer(description = "Primary main rebar diameter in mm (8, 10, 12, 16, 20, 25, or 32)"),
            "concreteMixSpecsAr" to Schema.string(description = "Summary of specified concrete grades, cement content kg/m3, or volumetric mix ratios in Arabic"),
            "cementTypeAr" to Schema.string(description = "Specified cement type (e.g. OPC عادي، SRC مقاوم للأملاح للأساسات) in Arabic"),
            "steelGradeAndDiametersAr" to Schema.string(description = "Specified rebar yield grade (BS 4449 G460/G500) and diameters (d) mentioned in Arabic"),
            "concreteCoverSpecAr" to Schema.string(description = "Specified concrete cover in mm for footings, columns, beams, slabs in Arabic"),
            "masonryAndMortarSpecsAr" to Schema.string(description = "Specified brick/block type, compressive strength, and mortar mix ratio in Arabic"),
            "finishingSpecsAr" to Schema.string(description = "Specified plaster thickness/ratio, tiles, paint coats, and waterproofing in Arabic"),
            "bs8110ComplianceNotesAr" to Schema.array(
                items = Schema.string(),
                description = "Technical verification notes comparing booklet clauses against BS 8110 and Sudanese SSMO standards"
            ),
            "extractedItems" to Schema.array(
                description = "All quantified work items or derivable items from the specification booklet and BOQ tables",
                items = Schema.obj(
                    properties = mapOf(
                        "itemCode" to Schema.string(description = "Clause/BOQ item number e.g. 2.1.3 or عام"),
                        "category" to Schema.enumeration(
                            values = listOf("EXCAVATION", "CONCRETE", "STEEL", "MASONRY", "FINISHING", "WATERPROOFING"),
                            description = "Engineering work category"
                        ),
                        "nameAr" to Schema.string(description = "Clear Arabic item description"),
                        "specRequirementAr" to Schema.string(description = "Technical specification extracted from the booklet for this item"),
                        "unitAr" to Schema.string(description = "Unit in Arabic e.g. م³, طن, كجم, م², م.ط, عدد"),
                        "count" to Schema.integer(description = "Count of identical elements or bars (min 1)"),
                        "lengthM" to Schema.double(description = "Length in meters if stated or derivable, else 0.0"),
                        "widthM" to Schema.double(description = "Width in meters if stated or derivable, else 0.0"),
                        "heightOrThicknessM" to Schema.double(description = "Height/depth/thickness in meters if stated, else 0.0"),
                        "diameterMm" to Schema.integer(description = "Rebar diameter d in mm for STEEL items (8,10,12,16,20,25,32), else 0"),
                        "explicitQuantityFromDoc" to Schema.double(description = "Explicit numeric quantity stated in the spec/BOQ table in unitAr, else 0.0"),
                        "volumeM3" to Schema.double(description = "Total net volume in m3 (for CONCRETE or EXCAVATION items)"),
                        "areaM2" to Schema.double(description = "Total net area in m2 (for MASONRY, FINISHING, WATERPROOFING)"),
                        "weightKg" to Schema.double(description = "Total rebar weight in kilograms (convert tonnes * 1000 for STEEL items)"),
                        "notesAr" to Schema.string(description = "Assumptions, missing dimension notes, or BS 8110 / Sudan site remarks")
                    ),
                    optionalProperties = listOf("notesAr", "diameterMm", "explicitQuantityFromDoc", "specRequirementAr")
                )
            ),
            "engineeringNotesAr" to Schema.array(
                items = Schema.string(),
                description = "General engineering takeoff summary & missing information alerts in Arabic"
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = specSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun analyzeSpecificationText(
        documentName: String,
        specText: String,
        defaultHeightM: Double,
        defaultSlabThicknessM: Double,
        defaultSteelRateKgPerM3: Double,
        userContextNote: String
    ): SpecDocumentAnalysisReport {
        val cleaned = specText.trim()
        if (cleaned.length < 15) {
            throw IllegalArgumentException("نص كراسة المواصفات قصير جداً أو فارغ. يرجى تحميل ملف أو لصق بنود الكراسة.")
        }

        val prompt = buildSpecPromptHeader(
            documentName = documentName,
            defaultHeightM = defaultHeightM,
            defaultSlabThicknessM = defaultSlabThicknessM,
            defaultSteelRateKgPerM3 = defaultSteelRateKgPerM3,
            userContextNote = userContextNote
        ) + "\n\n--- محتوى كراسة المواصفات / المستند ---\n${cleaned.take(28_000)}"

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("لم يرجع الذكاء الاصطناعي بيانات من المستند.")
            parseReportJson(jsonText, "تحليل كراسة المواصفات النصية: $documentName")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Spec text analysis timed out", e)
            throw IllegalStateException("انتهت مهلة تحليل كراسة المواصفات (60 ثانية). جرّب تقليص حجم النص أو القسم المطلوب.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Spec text analysis failed", e)
            throw IllegalStateException("تعذر تحليل كراسة المواصفات: ${e.localizedMessage ?: "خطأ غير متوقع"}")
        }
    }

    suspend fun analyzeSpecificationImages(
        documentName: String,
        pageBitmaps: List<Bitmap>,
        defaultHeightM: Double,
        defaultSlabThicknessM: Double,
        defaultSteelRateKgPerM3: Double,
        userContextNote: String
    ): SpecDocumentAnalysisReport {
        if (pageBitmaps.isEmpty()) {
            throw IllegalArgumentException("لا توجد صفحات صور للتحليل.")
        }

        val prompt = buildSpecPromptHeader(
            documentName = documentName,
            defaultHeightM = defaultHeightM,
            defaultSlabThicknessM = defaultSlabThicknessM,
            defaultSteelRateKgPerM3 = defaultSteelRateKgPerM3,
            userContextNote = userContextNote
        ) + "\n\nاقرأ جميع الصفحات المصورة المرفقة لكراسة المواصفات واستخرج منها الشروط الفنية وبنود الكميات بدقة."

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(
                    content {
                        pageBitmaps.take(6).forEach { bmp ->
                            image(bmp)
                        }
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("لم يرجع النموذج أي بيانات من صفحات المستند.")
            parseReportJson(jsonText, "تحليل صفحات مصورة ($documentName - ${pageBitmaps.take(6).size} صفحة)")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Spec image analysis timed out", e)
            throw IllegalStateException("انتهت مهلة تحليل صفحات الكراسة (60 ثانية). جرّب تقليل عدد الصفحات في الدفعة الواحدة.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Spec image analysis failed", e)
            throw IllegalStateException("تعذر قراءة وتحليل صفحات الكراسة: ${e.localizedMessage ?: "خطأ غير متوقع"}")
        }
    }

    private fun buildSpecPromptHeader(
        documentName: String,
        defaultHeightM: Double,
        defaultSlabThicknessM: Double,
        defaultSteelRateKgPerM3: Double,
        userContextNote: String
    ): String = buildString {
        appendLine("أنت مهندس مدني ومثمن كميات (Quantity Surveyor) خبير في قراءة كراسات الشروط والمواصفات الفنية وجداول الكميات (BOQ) وفق الكود البريطاني (BS 8110 / BS 4449 / BS 5328) واشتراطات السوق والمواصفات السودانية (SSMO).")
        appendLine("اسم المستند: $documentName")
        appendLine("القيم الافتراضية الميدانية عند نقص الأبعاد الصريحة:")
        appendLine("- ارتفاع الدور/الحوائط/الأعمدة الافتراضي: $defaultHeightM متر")
        appendLine("- سمك البلاطة الافتراضي: $defaultSlabThicknessM متر")
        appendLine("- نسبة التسليح الاسترشادية عند عدم ذكر وزن الحديد صراحة: $defaultSteelRateKgPerM3 كجم/م³")
        if (userContextNote.isNotBlank()) {
            appendLine("توجيهات إضافية من المهندس: $userContextNote")
        }
        appendLine()
        appendLine("المطلوب بدقة:")
        appendLine("1. استخلاص المواصفات الفنية الحاكمة من الكراسة: رتب الخرسانة (C15/C20/C25/C30)، نوع الأسمنت (OPC عادي أو SRC مقاوم للأملاح للأساسات)، رتبة الحديد وأقطار السيخ d المحددة، الغطاء الخرساني (مم)، مواصفات الطوب والمونة، ومواصفات التشطيب والعزل.")
        appendLine("2. استخراج كافة بنود الأعمال القابلة للحصر في extractedItems:")
        appendLine("   - إذا وردت كمية صريحة في الكراسة/المقايسة ضعها في explicitQuantityFromDoc واملأ volumeM3 أو areaM2 أو weightKg بما يكافئها.")
        appendLine("   - إذا وردت أبعاد (عدد × طول × عرض × ارتفاع/سمك) في النص فاحسب الحجم/المساحة بدقة بوحدة المتر.")
        appendLine("   - إذا ورد حديد التسليح بالطن (مثلاً 4.5 طن) فحوّله إلى كجم في weightKg (4500 كجم) وحدد diameterMm.")
        appendLine("   - إذا كانت الكراسة مواصفات عامة بدون أبعاد مشروع محددة، استخلص بنود الأعمال الرئيسية المذكورة فيها بقيم قياسية مبدئية واذكر في notesAr أن البند يحتاج إدخال أبعاد المشروع أو تعديلها.")
        appendLine("3. قدّم ملاحظات مطابقة الكراسة مع الكود البريطاني BS 8110 وظروف الصب والمعالجة والتربة في السودان.")
    }

    /**
     * Parses structured Gemini JSON into strongly-typed SpecDocumentAnalysisReport.
     * Exposed internally/publicly for deterministic unit testing.
     */
    fun parseReportJson(jsonString: String, sourceSummary: String): SpecDocumentAnalysisReport {
        val root = JSONObject(jsonString)

        val mixStr = root.optString("recommendedMixGrade", "C25")
        val mixGrade = ConcreteGrade.entries.firstOrNull { it.code == mixStr } ?: ConcreteGrade.C25

        val defaultDiaMm = root.optInt("defaultRebarDiameterMm", 16)
        val defaultDia = RebarDiameter.fromMm(
            if (defaultDiaMm in listOf(8, 10, 12, 16, 20, 25, 32)) defaultDiaMm else 16
        )

        val complianceArr = root.optJSONArray("bs8110ComplianceNotesAr")
        val complianceNotes = mutableListOf<String>()
        if (complianceArr != null) {
            for (i in 0 until complianceArr.length()) {
                val item = complianceArr.optString(i)
                if (item.isNotBlank()) complianceNotes.add(item)
            }
        }

        val summary = SpecTechnicalSummary(
            projectTitle = root.optString("projectTitle", "كراسة مواصفات ومستندات مشروع"),
            clientOrConsultant = root.optString("clientOrConsultant", "غير محدد"),
            concreteMixSpecsAr = root.optString("concreteMixSpecsAr", "حسب الكود البريطاني BS 8110 (${mixGrade.code})"),
            cementTypeAr = root.optString("cementTypeAr", "أسمنت بورتلاندي عادي OPC (ومقاوم للأملاح SRC تحت الأرض عند الحاجة)"),
            steelGradeAndDiametersAr = root.optString("steelGradeAndDiametersAr", "حديد عالي المقاومة BS 4449 (أقطار رئيسية Ø${defaultDia.mm} مم)"),
            concreteCoverSpecAr = root.optString("concreteCoverSpecAr", "أساسات 50مم، أعمدة 30-40مم، أسقف 25مم"),
            masonryAndMortarSpecsAr = root.optString("masonryAndMortarSpecsAr", "طوب أحمر/بلك بمونة أسمنت ورمل 1:4"),
            finishingSpecsAr = root.optString("finishingSpecsAr", "بياض مونة 1:4 بسمك 2سم، سيراميك ودهان"),
            bs8110ComplianceNotesAr = complianceNotes
        )

        val itemsArr = root.optJSONArray("extractedItems")
        val items = mutableListOf<SpecWorkItem>()
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.optJSONObject(i) ?: continue
                val category = SpecItemCategory.fromCode(obj.optString("category", "CONCRETE"))
                val unitAr = obj.optString("unitAr", defaultUnitFor(category))
                val count = obj.optInt("count", 1).coerceAtLeast(1)
                val len = obj.optDouble("lengthM", 0.0).coerceAtLeast(0.0)
                val wid = obj.optDouble("widthM", 0.0).coerceAtLeast(0.0)
                val hgt = obj.optDouble("heightOrThicknessM", 0.0).coerceAtLeast(0.0)
                val dia = obj.optInt("diameterMm", 0)
                val explicitQty = obj.optDouble("explicitQuantityFromDoc", 0.0).coerceAtLeast(0.0)
                var vol = obj.optDouble("volumeM3", 0.0).coerceAtLeast(0.0)
                var area = obj.optDouble("areaM2", 0.0).coerceAtLeast(0.0)
                var weight = obj.optDouble("weightKg", 0.0).coerceAtLeast(0.0)

                // Reconcile explicit quantities and geometric derivations cleanly
                when (category) {
                    SpecItemCategory.CONCRETE, SpecItemCategory.EXCAVATION -> {
                        if (vol <= 0.0 && explicitQty > 0.0) vol = explicitQty
                        if (vol <= 0.0 && len > 0.0 && wid > 0.0 && hgt > 0.0) {
                            vol = round2(count * len * wid * hgt)
                        }
                    }
                    SpecItemCategory.MASONRY, SpecItemCategory.FINISHING, SpecItemCategory.WATERPROOFING -> {
                        if (area <= 0.0 && explicitQty > 0.0) area = explicitQty
                        if (area <= 0.0 && len > 0.0) {
                            val secondDim = if (hgt > 0.0) hgt else if (wid > 0.0) wid else 1.0
                            area = round2(count * len * secondDim)
                        }
                    }
                    SpecItemCategory.STEEL -> {
                        if (weight <= 0.0 && explicitQty > 0.0) {
                            weight = if (unitAr.contains("طن")) round1(explicitQty * 1000.0) else round1(explicitQty)
                        }
                        if (weight <= 0.0 && len > 0.0) {
                            val effectiveDia = if (dia in listOf(8, 10, 12, 16, 20, 25, 32)) dia else defaultDia.mm
                            weight = round1(count * len * RebarDiameter.fromMm(effectiveDia).weightPerMeterKg)
                        }
                    }
                }

                items.add(
                    SpecWorkItem(
                        itemCode = obj.optString("itemCode", "${i + 1}").ifBlank { "${i + 1}" },
                        category = category,
                        nameAr = obj.optString("nameAr", "بند مواصفات ${i + 1}"),
                        specRequirementAr = obj.optString("specRequirementAr", ""),
                        unitAr = unitAr,
                        count = count,
                        lengthM = round2(len),
                        widthM = round2(wid),
                        heightOrThicknessM = round2(hgt),
                        diameterMm = dia,
                        explicitQuantityFromDoc = round2(explicitQty),
                        volumeM3 = round2(vol),
                        areaM2 = round2(area),
                        weightKg = round1(weight),
                        notesAr = obj.optString("notesAr", "")
                    )
                )
            }
        }

        val notesArr = root.optJSONArray("engineeringNotesAr")
        val notes = mutableListOf<String>()
        if (notesArr != null) {
            for (i in 0 until notesArr.length()) {
                val n = notesArr.optString(i)
                if (n.isNotBlank()) notes.add(n)
            }
        }

        return SpecDocumentAnalysisReport(
            technicalSummary = summary,
            recommendedMixGrade = mixGrade,
            defaultRebarDiameter = defaultDia,
            items = items,
            engineeringNotesAr = notes,
            sourceSummary = sourceSummary
        )
    }

    private fun defaultUnitFor(category: SpecItemCategory): String = when (category) {
        SpecItemCategory.EXCAVATION, SpecItemCategory.CONCRETE -> "م³"
        SpecItemCategory.STEEL -> "كجم"
        SpecItemCategory.MASONRY, SpecItemCategory.FINISHING, SpecItemCategory.WATERPROOFING -> "م²"
    }

    private fun round1(v: Double) = (v * 10.0).roundToInt() / 10.0
    private fun round2(v: Double) = (v * 100.0).roundToInt() / 100.0
}
