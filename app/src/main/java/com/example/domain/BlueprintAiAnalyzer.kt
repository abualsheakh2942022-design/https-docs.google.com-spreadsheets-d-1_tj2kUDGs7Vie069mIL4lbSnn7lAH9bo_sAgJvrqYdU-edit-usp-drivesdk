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

private const val TAG = "BlueprintAiAnalyzer"

enum class ExtractedElementCategory(val arLabel: String) {
    CONCRETE("خرسانة"),
    STEEL("حديد تسليح"),
    MASONRY("مباني وطوب"),
    FINISHING("تشطيبات")
}

data class ExtractedBlueprintElement(
    val id: String = UUID.randomUUID().toString(),
    val category: ExtractedElementCategory,
    val nameAr: String,
    val structuralType: String,
    val count: Int,
    val lengthM: Double,
    val widthM: Double,
    val heightOrThicknessM: Double,
    val diameterMm: Int = 0,
    val volumeM3: Double = 0.0,
    val areaM2: Double = 0.0,
    val weightKg: Double = 0.0,
    val notesAr: String = "",
    val isIncluded: Boolean = true
)

data class BlueprintAnalysisReport(
    val drawingTitle: String,
    val drawingScale: String,
    val recommendedMixGrade: ConcreteGrade,
    val elements: List<ExtractedBlueprintElement>,
    val engineeringNotesAr: List<String>,
    val sourceSummary: String
)

/**
 * Combines multimodal Gemini AI (via Firebase AI Logic) and deterministic CAD layer heuristics
 * to extract engineering quantities from blueprints, PDFs, AutoCAD DXF, and schedule files.
 */
object BlueprintAiAnalyzer {

    private val blueprintSchema = Schema.obj(
        properties = mapOf(
            "drawingTitle" to Schema.string(description = "Title or subject of the engineering drawing or schedule in Arabic"),
            "drawingScale" to Schema.string(description = "Detected scale e.g. 1:100, 1:50, or غير محدد"),
            "recommendedMixGrade" to Schema.enumeration(
                values = listOf("C15", "C20", "C25", "C30"),
                description = "Recommended BS 8110 concrete grade based on elements"
            ),
            "extractedElements" to Schema.array(
                description = "List of structural or architectural elements extracted for quantity takeoff",
                items = Schema.obj(
                    properties = mapOf(
                        "category" to Schema.enumeration(
                            values = listOf("CONCRETE", "STEEL", "MASONRY", "FINISHING"),
                            description = "Quantity category"
                        ),
                        "nameAr" to Schema.string(description = "Clear Arabic engineering name e.g. قاعدة منفصلة F1 or أعمدة C1 30x60"),
                        "structuralType" to Schema.string(description = "Element type e.g. FOOTING, COLUMN, BEAM, SLAB, WALL, REBAR, PLASTER, TILES"),
                        "count" to Schema.integer(description = "Number of identical elements or bars"),
                        "lengthM" to Schema.double(description = "Length in meters"),
                        "widthM" to Schema.double(description = "Width in meters"),
                        "heightOrThicknessM" to Schema.double(description = "Height, depth, or slab thickness in meters"),
                        "diameterMm" to Schema.integer(description = "Rebar diameter in mm if STEEL, otherwise 0"),
                        "volumeM3" to Schema.double(description = "Total net volume in cubic meters for all count"),
                        "areaM2" to Schema.double(description = "Total net area in square meters (walls, slabs, finishing)"),
                        "weightKg" to Schema.double(description = "Total rebar weight in kg if STEEL or estimated"),
                        "notesAr" to Schema.string(description = "Specific BS 8110 or Sudanese construction note for this element")
                    ),
                    optionalProperties = listOf("notesAr", "diameterMm")
                )
            ),
            "engineeringNotesAr" to Schema.array(
                items = Schema.string(),
                description = "List of concise Arabic engineering observations per BS 8110 and Sudanese SSMO practice"
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = blueprintSchema
                temperature = 0.2f
            }
        )
    }

    /**
     * Analyzes one or more blueprint/PDF page bitmaps using multimodal Gemini AI.
     */
    suspend fun analyzeBlueprintImages(
        bitmaps: List<Bitmap>,
        userContextNote: String,
        defaultHeightM: Double,
        defaultSlabThicknessM: Double
    ): BlueprintAnalysisReport {
        if (bitmaps.isEmpty()) {
            throw IllegalArgumentException("لا توجد صور أو صفحات مخطط للتحليل.")
        }

        val promptText = buildString {
            appendLine("أنت مهندس مدني خبير في حساب الكميات الإنشائية والمعمارية وفق الكود البريطاني (BS 8110 / BS 4449) والمواصفات والوحدات الهندسية في السودان.")
            appendLine("قم بقراءة المخطط/الخرطة المرفقة بدقة واستخراج جميع العناصر الإنشائية والمعمارية الظاهرة أو الجداول الهندسية (جداول القواعد، الأعمدة، الكمرات، الأسقف، الحوائط، أو جداول التسليح).")
            appendLine("القيم الافتراضية للموقع إذا لم يظهر الارتفاع/السمك في المسقط الأفقي:")
            appendLine("- ارتفاع الدور/الأعمدة/الجدران الافتراضي: $defaultHeightM متر")
            appendLine("- سمك البلاطة الافتراضي: $defaultSlabThicknessM متر")
            if (userContextNote.isNotBlank()) {
                appendLine("ملاحظة المهندس الإضافية: $userContextNote")
            }
            appendLine("التعليمات:")
            appendLine("1. حوّل جميع الأبعاد الظاهرة (سواء بالملم أو السم) إلى المتر في الحقول lengthM و widthM و heightOrThicknessM.")
            appendLine("2. احسب volumeM3 = count * lengthM * widthM * heightOrThicknessM للعناصر الخرسانية.")
            appendLine("3. احسب areaM2 للجدران (الطول * الارتفاع) أو للأسقف والتشطيبات.")
            appendLine("4. إذا وُجد جدول تسليح أو أقطار سيخ (مثل Ø16 أو T12)، أضف بنود STEEL مع diameterMm و weightKg.")
            appendLine("5. قدّم توصيات مختصرة بالعربية تراعي الكود البريطاني BS 8110 وظروف السودان.")
        }

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(
                    content {
                        bitmaps.take(4).forEach { bmp ->
                            image(bmp)
                        }
                        text(promptText)
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("لم يرجع النموذج أي بيانات من المخطط.")
            parseReportJson(jsonText, "تحليل المخطط بالذكاء الاصطناعي (${bitmaps.size} لوحة)")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Blueprint AI analysis timed out", e)
            throw IllegalStateException("انتهت مهلة تحليل المخطط (60 ثانية). تأكد من سرعة الإنترنت أو جرّب تقليل عدد الصفحات.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Blueprint AI analysis failed", e)
            throw IllegalStateException("تعذر تحليل المخطط عبر الذكاء الاصطناعي: ${e.localizedMessage ?: "خطأ غير متوقع"}")
        }
    }

    /**
     * Uses Gemini AI to interpret AutoCAD DXF layer summary & text annotations or CSV/TXT schedules.
     */
    suspend fun analyzeEngineeringTextData(
        fileName: String,
        extractedSummaryText: String,
        defaultHeightM: Double,
        defaultSlabThicknessM: Double,
        userContextNote: String
    ): BlueprintAnalysisReport {
        val promptText = buildString {
            appendLine("أنت مهندس مدني خبير في قراءة ملفات البرامج الهندسية (AutoCAD DXF, ETABS, Revit Schedules, Excel CSV) وحساب الكميات وفق الكود البريطاني BS 8110 والمواصفات السودانية.")
            appendLine("اسم الملف: $fileName")
            appendLine("القيم الافتراضية للأبعاد المفقودة: ارتفاع الدور/العمود/الجدار = $defaultHeightM م، سمك البلاطة = $defaultSlabThicknessM م.")
            if (userContextNote.isNotBlank()) {
                appendLine("ملاحظات المهندس: $userContextNote")
            }
            appendLine("البيانات المستخرجة من الملف الهندسي:")
            appendLine(extractedSummaryText.take(16000))
            appendLine()
            appendLine("المطلوب: صنّف الطبقات/البنود إلى عناصر كميات دقيقة (CONCRETE, STEEL, MASONRY, FINISHING) واحسب الأبعاد بالمتر والحجوم (volumeM3) والمساحات (areaM2) والأوزان (weightKg).")
        }

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(promptText)
            }
            val jsonText = response.text ?: throw IllegalStateException("لم يرجع النموذج أي نتائج للملف.")
            parseReportJson(jsonText, "تحليل ذكي لملف: $fileName")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Engineering text AI analysis timed out", e)
            throw IllegalStateException("انتهت مهلة تحليل الملف (60 ثانية). حاول مرة أخرى.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Engineering text AI analysis failed", e)
            throw IllegalStateException("تعذر تحليل الملف بالذكاء الاصطناعي: ${e.localizedMessage ?: "خطأ غير متوقع"}")
        }
    }

    /**
     * Deterministic, offline-ready conversion of AutoCAD DXF layer geometry into quantities
     * using layer naming conventions (COLUMNS, FOOTINGS, BEAMS, SLABS, WALLS, etc.).
     */
    fun convertDxfToElementsOffline(
        dxfResult: DxfParseResult,
        unitOverride: CadDrawingUnit,
        defaultHeightM: Double,
        defaultSlabThicknessM: Double,
        defaultWallThicknessM: Double = 0.20
    ): BlueprintAnalysisReport {
        val factor = unitOverride.toMetersFactor
        val areaFactor = factor * factor
        val elements = mutableListOf<ExtractedBlueprintElement>()

        dxfResult.layers.forEach { layer ->
            val upper = layer.layerName.uppercase()
            val lenM = round2(layer.totalLengthInDrawingUnits * factor)
            val areaM2 = round2(layer.totalClosedAreaInDrawingUnitsSq * areaFactor)
            val entityCount = layer.lineCount + layer.polylineCount + layer.circleCount + layer.blockInsertCount
            if (entityCount == 0 && lenM <= 0.0 && areaM2 <= 0.0) return@forEach

            val isColumn = upper.contains("COL") || upper.contains("AC-") || upper.contains("عمود") || upper.contains("اعمد")
            val isFooting = upper.contains("FOOT") || upper.contains("FND") || upper.contains("FOUND") || upper.contains("قاعد")
            val isBeam = upper.contains("BEAM") || upper.contains("BM") || upper.contains("TIE") || upper.contains("كمر") || upper.contains("ميد")
            val isSlab = upper.contains("SLAB") || upper.contains("FLOOR") || upper.contains("ROOF") || upper.contains("سقف") || upper.contains("بلاط")
            val isSteel = upper.contains("REBAR") || upper.contains("STEEL") || upper.contains("RFT") || upper.contains("حديد") || upper.contains("تسليح")
            val isWall = upper.contains("WALL") || upper.contains("BRICK") || upper.contains("MASON") || upper.contains("ARCH") || upper.contains("جدار") || upper.contains("حائط") || upper.contains("طوب")

            val textsSample = layer.textsInLayer.take(3).joinToString("، ")

            when {
                isSteel -> {
                    // Assume default D16 if not specified in text
                    val diameter = detectDiameterFromTexts(layer.textsInLayer) ?: 16
                    val weightPerM = RebarDiameter.fromMm(diameter).weightPerMeterKg
                    val weightKg = round1(lenM * weightPerM)
                    elements.add(
                        ExtractedBlueprintElement(
                            category = ExtractedElementCategory.STEEL,
                            nameAr = "تسليح طبقة (${layer.layerName})",
                            structuralType = "REBAR",
                            count = (layer.lineCount + layer.polylineCount).coerceAtLeast(1),
                            lengthM = lenM,
                            widthM = 0.0,
                            heightOrThicknessM = 0.0,
                            diameterMm = diameter,
                            weightKg = weightKg,
                            notesAr = if (textsSample.isNotBlank()) "نصوص الطبقة: $textsSample" else "حساب مباشر من أطوال الخطوط في طبقة التسليح"
                        )
                    )
                }
                isColumn -> {
                    val colCount = maxOf(layer.closedPolylineCount + layer.circleCount + layer.blockInsertCount, 1)
                    val totalArea = if (areaM2 > 0.0) areaM2 else colCount * 0.12 // 0.30x0.40 fallback
                    val vol = round2(totalArea * defaultHeightM)
                    elements.add(
                        ExtractedBlueprintElement(
                            category = ExtractedElementCategory.CONCRETE,
                            nameAr = "أعمدة - طبقة (${layer.layerName})",
                            structuralType = "COLUMN",
                            count = colCount,
                            lengthM = round2(totalArea / colCount),
                            widthM = 1.0,
                            heightOrThicknessM = defaultHeightM,
                            volumeM3 = vol,
                            areaM2 = totalArea,
                            notesAr = "محسوبة من $colCount مضلع مغلق/بلوك × ارتفاع $defaultHeightM م" +
                                if (textsSample.isNotBlank()) " ($textsSample)" else ""
                        )
                    )
                }
                isFooting -> {
                    val fCount = maxOf(layer.closedPolylineCount + layer.blockInsertCount, 1)
                    val totalArea = if (areaM2 > 0.0) areaM2 else round2(lenM * 0.6)
                    val depth = 0.50
                    val vol = round2(totalArea * depth)
                    elements.add(
                        ExtractedBlueprintElement(
                            category = ExtractedElementCategory.CONCRETE,
                            nameAr = "قواعد/أساسات - طبقة (${layer.layerName})",
                            structuralType = "FOOTING",
                            count = fCount,
                            lengthM = round2(totalArea),
                            widthM = 1.0,
                            heightOrThicknessM = depth,
                            volumeM3 = vol,
                            areaM2 = totalArea,
                            notesAr = "عمق قاعدة افتراضي 0.50 م (قابل للتعديل حسب اللوحة)"
                        )
                    )
                }
                isSlab -> {
                    val totalArea = if (areaM2 > 0.0) areaM2 else round2((lenM / 4.0) * (lenM / 4.0))
                    val vol = round2(totalArea * defaultSlabThicknessM)
                    elements.add(
                        ExtractedBlueprintElement(
                            category = ExtractedElementCategory.CONCRETE,
                            nameAr = "بلاطة/سقف - طبقة (${layer.layerName})",
                            structuralType = "SLAB",
                            count = layer.closedPolylineCount.coerceAtLeast(1),
                            lengthM = round2(totalArea),
                            widthM = 1.0,
                            heightOrThicknessM = defaultSlabThicknessM,
                            volumeM3 = vol,
                            areaM2 = totalArea,
                            notesAr = "مساحة مغلقة $totalArea م² × سمك $defaultSlabThicknessM م"
                        )
                    )
                }
                isBeam -> {
                    val beamDepth = 0.50
                    val beamWidth = 0.25
                    val vol = round2(lenM * beamWidth * beamDepth)
                    elements.add(
                        ExtractedBlueprintElement(
                            category = ExtractedElementCategory.CONCRETE,
                            nameAr = "كمرات/ميد - طبقة (${layer.layerName})",
                            structuralType = "BEAM",
                            count = (layer.lineCount + layer.polylineCount).coerceAtLeast(1),
                            lengthM = lenM,
                            widthM = beamWidth,
                            heightOrThicknessM = beamDepth,
                            volumeM3 = vol,
                            notesAr = "إجمالي أطوال $lenM م × قطاع 25×50 سم (قابل للتعديل)"
                        )
                    )
                }
                isWall -> {
                    // Note: architectural double-line walls have 2 lines per wall, or single centerlines.
                    val wallArea = round2(lenM * defaultHeightM)
                    elements.add(
                        ExtractedBlueprintElement(
                            category = ExtractedElementCategory.MASONRY,
                            nameAr = "حوائط ومباني - طبقة (${layer.layerName})",
                            structuralType = "WALL",
                            count = 1,
                            lengthM = lenM,
                            widthM = defaultWallThicknessM,
                            heightOrThicknessM = defaultHeightM,
                            areaM2 = wallArea,
                            volumeM3 = round2(wallArea * defaultWallThicknessM),
                            notesAr = "أطوال خطوط الطبقة $lenM م × ارتفاع $defaultHeightM م (تحقق من الخطوط المزدوجة)"
                        )
                    )
                }
                else -> {
                    // Generic geometric layer: expose area if closed polygons exist, otherwise length
                    if (areaM2 > 0.0) {
                        elements.add(
                            ExtractedBlueprintElement(
                                category = ExtractedElementCategory.FINISHING,
                                nameAr = "مسطح طبقة (${layer.layerName})",
                                structuralType = "AREA",
                                count = layer.closedPolylineCount.coerceAtLeast(1),
                                lengthM = lenM,
                                widthM = 1.0,
                                heightOrThicknessM = 0.02,
                                areaM2 = areaM2,
                                notesAr = "مساحة مغلقة مستخرجة مباشرة من أوتوكاد ($areaM2 م²)"
                            )
                        )
                    } else if (lenM > 0.0) {
                        elements.add(
                            ExtractedBlueprintElement(
                                category = ExtractedElementCategory.MASONRY,
                                nameAr = "أطوال طبقة (${layer.layerName})",
                                structuralType = "LINE_WORK",
                                count = entityCount.coerceAtLeast(1),
                                lengthM = lenM,
                                widthM = defaultWallThicknessM,
                                heightOrThicknessM = defaultHeightM,
                                areaM2 = round2(lenM * defaultHeightM),
                                notesAr = "أطوال مستخرجة من الطبقة: $lenM م (محيط أو جدران)"
                            )
                        )
                    }
                }
            }
        }

        val notes = mutableListOf(
            "تمت قراءة ملف DXF مباشرة بدون إنترنت بوحدة: ${unitOverride.arName}.",
            "يمكنك الضغط على «تحليل ذكي AI» لقراءة نصوص المخطط وتصنيف الطبقات بدقة أعلى، أو تعديل أبعاد أي بند مباشرة."
        )
        if (dxfResult.allAnnotations.isNotEmpty()) {
            notes.add("تم رصد ${dxfResult.allAnnotations.size} نص/ملحوظة داخل اللوحة.")
        }

        return BlueprintAnalysisReport(
            drawingTitle = "مخطط CAD (${dxfResult.layers.size} طبقة)",
            drawingScale = "1:1 (${unitOverride.arName})",
            recommendedMixGrade = ConcreteGrade.C25,
            elements = elements,
            engineeringNotesAr = notes,
            sourceSummary = dxfResult.parseNotes
        )
    }

    private fun detectDiameterFromTexts(texts: List<String>): Int? {
        val regex = Regex("""(?:Ø|T|D|Φ|diameter\s*)(\d{1,2})""", RegexOption.IGNORE_CASE)
        for (t in texts) {
            val match = regex.find(t)
            if (match != null) {
                val d = match.groupValues[1].toIntOrNull()
                if (d in listOf(8, 10, 12, 16, 20, 25, 32)) return d
            }
        }
        return null
    }

    private fun parseReportJson(jsonString: String, sourceSummary: String): BlueprintAnalysisReport {
        val root = JSONObject(jsonString)
        val title = root.optString("drawingTitle", "مخطط هندسي")
        val scale = root.optString("drawingScale", "غير محدد")
        val mixStr = root.optString("recommendedMixGrade", "C25")
        val mixGrade = ConcreteGrade.entries.firstOrNull { it.code == mixStr } ?: ConcreteGrade.C25

        val elementsArr = root.optJSONArray("extractedElements")
        val elements = mutableListOf<ExtractedBlueprintElement>()
        if (elementsArr != null) {
            for (i in 0 until elementsArr.length()) {
                val obj = elementsArr.optJSONObject(i) ?: continue
                val catStr = obj.optString("category", "CONCRETE")
                val category = when (catStr) {
                    "STEEL" -> ExtractedElementCategory.STEEL
                    "MASONRY" -> ExtractedElementCategory.MASONRY
                    "FINISHING" -> ExtractedElementCategory.FINISHING
                    else -> ExtractedElementCategory.CONCRETE
                }
                val count = obj.optInt("count", 1).coerceAtLeast(1)
                val len = obj.optDouble("lengthM", 0.0).coerceAtLeast(0.0)
                val wid = obj.optDouble("widthM", 0.0).coerceAtLeast(0.0)
                val hgt = obj.optDouble("heightOrThicknessM", 0.0).coerceAtLeast(0.0)
                val dia = obj.optInt("diameterMm", 0)
                var vol = obj.optDouble("volumeM3", 0.0)
                var area = obj.optDouble("areaM2", 0.0)
                var weight = obj.optDouble("weightKg", 0.0)

                if (category == ExtractedElementCategory.CONCRETE && vol <= 0.0 && len > 0 && wid > 0 && hgt > 0) {
                    vol = round2(count * len * wid * hgt)
                }
                if ((category == ExtractedElementCategory.MASONRY || category == ExtractedElementCategory.FINISHING) &&
                    area <= 0.0 && len > 0
                ) {
                    area = round2(count * len * (if (hgt > 0) hgt else if (wid > 0) wid else 1.0))
                }
                if (category == ExtractedElementCategory.STEEL && weight <= 0.0 && len > 0 && dia > 0) {
                    weight = round1(count * len * RebarDiameter.fromMm(dia).weightPerMeterKg)
                }

                elements.add(
                    ExtractedBlueprintElement(
                        category = category,
                        nameAr = obj.optString("nameAr", "عنصر هندسي ${i + 1}"),
                        structuralType = obj.optString("structuralType", "ELEMENT"),
                        count = count,
                        lengthM = round2(len),
                        widthM = round2(wid),
                        heightOrThicknessM = round2(hgt),
                        diameterMm = dia,
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

        return BlueprintAnalysisReport(
            drawingTitle = title,
            drawingScale = scale,
            recommendedMixGrade = mixGrade,
            elements = elements,
            engineeringNotesAr = notes,
            sourceSummary = sourceSummary
        )
    }

    private fun round1(v: Double) = (v * 10.0).roundToInt() / 10.0
    private fun round2(v: Double) = (v * 100.0).roundToInt() / 100.0
}
