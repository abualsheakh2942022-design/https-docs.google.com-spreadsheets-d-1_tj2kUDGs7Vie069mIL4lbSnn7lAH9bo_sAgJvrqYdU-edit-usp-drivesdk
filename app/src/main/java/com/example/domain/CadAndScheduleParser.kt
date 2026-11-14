package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Drawing units supported by AutoCAD DXF ($INSUNITS)
 */
enum class CadDrawingUnit(val code: Int, val arName: String, val toMetersFactor: Double) {
    MILLIMETERS(4, "ملليمتر (mm)", 0.001),
    CENTIMETERS(5, "سنتيمتر (cm)", 0.01),
    METERS(6, "متر (m)", 1.0),
    INCHES(1, "بوصة (in)", 0.0254),
    FEET(2, "قدم (ft)", 0.3048);

    companion object {
        fun fromInsUnitsCode(code: Int): CadDrawingUnit {
            return entries.firstOrNull { it.code == code } ?: METERS
        }
    }
}

data class DxfLayerSummary(
    val layerName: String,
    val lineCount: Int = 0,
    val polylineCount: Int = 0,
    val closedPolylineCount: Int = 0,
    val circleCount: Int = 0,
    val blockInsertCount: Int = 0,
    val totalLengthInDrawingUnits: Double = 0.0,
    val totalClosedAreaInDrawingUnitsSq: Double = 0.0,
    val blockNames: Map<String, Int> = emptyMap(),
    val textsInLayer: List<String> = emptyList()
)

data class DxfParseResult(
    val detectedUnit: CadDrawingUnit,
    val totalEntities: Int,
    val layers: List<DxfLayerSummary>,
    val allAnnotations: List<String>,
    val parseNotes: String
)

/**
 * Pure-Kotlin AutoCAD ASCII DXF Parser.
 * Extracts geometry (LINE, LWPOLYLINE, POLYLINE, CIRCLE, ARC, INSERT, TEXT, MTEXT)
 * grouped by CAD Layer and converts to real-world lengths and areas.
 */
object DxfQuantityParser {

    private data class MutableLayerAcc(
        var lineCount: Int = 0,
        var polylineCount: Int = 0,
        var closedPolylineCount: Int = 0,
        var circleCount: Int = 0,
        var blockInsertCount: Int = 0,
        var totalLength: Double = 0.0,
        var totalClosedArea: Double = 0.0,
        val blockNames: MutableMap<String, Int> = mutableMapOf(),
        val texts: MutableList<String> = mutableListOf()
    )

    fun parseDxf(dxfContent: String): DxfParseResult {
        val lines = dxfContent.lineSequence().map { it.trimEnd('\r') }.toList()
        var insUnitsCode = 6 // default meters
        var inHeader = false
        var inEntities = false
        var currentHeaderVar = ""

        val layersMap = linkedMapOf<String, MutableLayerAcc>()
        val allAnnotations = mutableListOf<String>()
        var totalEntities = 0

        fun layerAcc(name: String): MutableLayerAcc {
            val clean = name.ifBlank { "0" }
            return layersMap.getOrPut(clean) { MutableLayerAcc() }
        }

        // Parse pair-by-pair (code, value)
        var i = 0
        var currentSection = ""

        // Active entity state
        var entityType = ""
        var entityLayer = "0"
        var x1 = 0.0; var y1 = 0.0; var x2 = 0.0; var y2 = 0.0
        var radius = 0.0
        var polyClosed = false
        var polyVertices = mutableListOf<Pair<Double, Double>>()
        var pendingX: Double? = null
        var blockName = ""
        var textValue = ""
        var inPolylineOldStyle = false
        var inVertex = false

        fun flushEntity() {
            if (entityType.isBlank()) return
            val acc = layerAcc(entityLayer)
            when (entityType) {
                "LINE" -> {
                    totalEntities++
                    acc.lineCount++
                    acc.totalLength += hypot(x2 - x1, y2 - y1)
                }
                "LWPOLYLINE" -> {
                    totalEntities++
                    acc.polylineCount++
                    val len = computePolylineLength(polyVertices, polyClosed)
                    acc.totalLength += len
                    if (polyClosed && polyVertices.size >= 3) {
                        acc.closedPolylineCount++
                        acc.totalClosedArea += computePolygonArea(polyVertices)
                    }
                }
                "POLYLINE" -> {
                    totalEntities++
                    acc.polylineCount++
                    val len = computePolylineLength(polyVertices, polyClosed)
                    acc.totalLength += len
                    if (polyClosed && polyVertices.size >= 3) {
                        acc.closedPolylineCount++
                        acc.totalClosedArea += computePolygonArea(polyVertices)
                    }
                }
                "CIRCLE" -> {
                    totalEntities++
                    acc.circleCount++
                    if (radius > 0) {
                        acc.totalLength += 2.0 * PI * radius
                        acc.totalClosedArea += PI * radius * radius
                    }
                }
                "ARC" -> {
                    totalEntities++
                    if (radius > 0) {
                        // Approximate arc length if angles not fully tracked, or track 1/4 circle fallback
                        acc.totalLength += 1.57 * radius
                    }
                }
                "INSERT" -> {
                    totalEntities++
                    acc.blockInsertCount++
                    if (blockName.isNotBlank()) {
                        acc.blockNames[blockName] = (acc.blockNames[blockName] ?: 0) + 1
                    }
                }
                "TEXT", "MTEXT" -> {
                    val cleanedText = cleanMtextFormatting(textValue)
                    if (cleanedText.isNotBlank()) {
                        if (acc.texts.size < 40) acc.texts.add(cleanedText)
                        if (allAnnotations.size < 120) allAnnotations.add("[$entityLayer] $cleanedText")
                    }
                }
            }
            entityType = ""
            entityLayer = "0"
            x1 = 0.0; y1 = 0.0; x2 = 0.0; y2 = 0.0
            radius = 0.0
            polyClosed = false
            polyVertices = mutableListOf()
            pendingX = null
            blockName = ""
            textValue = ""
        }

        while (i + 1 < lines.size) {
            val codeStr = lines[i].trim()
            val valueStr = lines[i + 1].trim()
            i += 2

            val code = codeStr.toIntOrNull() ?: continue

            if (code == 0 && valueStr == "SECTION") {
                if (i + 1 < lines.size && lines[i].trim() == "2") {
                    currentSection = lines[i + 1].trim().uppercase()
                    inHeader = currentSection == "HEADER"
                    inEntities = currentSection == "ENTITIES"
                    i += 2
                }
                continue
            }

            if (code == 0 && valueStr == "ENDSEC") {
                if (inEntities) {
                    if (inPolylineOldStyle) {
                        inPolylineOldStyle = false
                        inVertex = false
                    }
                    flushEntity()
                }
                inHeader = false
                inEntities = false
                currentSection = ""
                continue
            }

            if (inHeader) {
                if (code == 9) {
                    currentHeaderVar = valueStr
                } else if (currentHeaderVar == "\$INSUNITS" && code == 70) {
                    insUnitsCode = valueStr.toIntOrNull() ?: 6
                }
                continue
            }

            if (inEntities) {
                if (code == 0) {
                    if (valueStr == "VERTEX" && inPolylineOldStyle) {
                        inVertex = true
                        pendingX = null
                        continue
                    }
                    if (valueStr == "SEQEND" && inPolylineOldStyle) {
                        inPolylineOldStyle = false
                        inVertex = false
                        flushEntity()
                        continue
                    }
                    if (!inPolylineOldStyle) {
                        flushEntity()
                        entityType = valueStr.uppercase()
                        if (entityType == "POLYLINE") {
                            inPolylineOldStyle = true
                            inVertex = false
                        }
                    } else {
                        // Unexpected entity inside polyline -> flush polyline first
                        inPolylineOldStyle = false
                        inVertex = false
                        flushEntity()
                        entityType = valueStr.uppercase()
                    }
                    continue
                }

                if (inVertex) {
                    when (code) {
                        10 -> pendingX = valueStr.toDoubleOrNull()
                        20 -> {
                            val y = valueStr.toDoubleOrNull()
                            val px = pendingX
                            if (px != null && y != null) {
                                polyVertices.add(px to y)
                                pendingX = null
                            }
                        }
                    }
                    continue
                }

                when (code) {
                    8 -> entityLayer = valueStr
                    2 -> if (entityType == "INSERT") blockName = valueStr
                    1, 3 -> if (entityType == "TEXT" || entityType == "MTEXT") {
                        textValue += valueStr
                    }
                    10 -> {
                        val v = valueStr.toDoubleOrNull() ?: 0.0
                        if (entityType == "LWPOLYLINE") {
                            pendingX = v
                        } else {
                            x1 = v
                        }
                    }
                    20 -> {
                        val v = valueStr.toDoubleOrNull() ?: 0.0
                        if (entityType == "LWPOLYLINE") {
                            val px = pendingX
                            if (px != null) {
                                polyVertices.add(px to v)
                                pendingX = null
                            }
                        } else {
                            y1 = v
                        }
                    }
                    11 -> x2 = valueStr.toDoubleOrNull() ?: 0.0
                    21 -> y2 = valueStr.toDoubleOrNull() ?: 0.0
                    40 -> radius = valueStr.toDoubleOrNull() ?: 0.0
                    70 -> {
                        if (entityType == "LWPOLYLINE" || entityType == "POLYLINE") {
                            val flags = valueStr.toIntOrNull() ?: 0
                            polyClosed = (flags and 1) == 1
                        }
                    }
                }
            }
        }

        flushEntity()

        val detectedUnit = CadDrawingUnit.fromInsUnitsCode(insUnitsCode)
        val layerSummaries = layersMap.entries.map { (name, acc) ->
            DxfLayerSummary(
                layerName = name,
                lineCount = acc.lineCount,
                polylineCount = acc.polylineCount,
                closedPolylineCount = acc.closedPolylineCount,
                circleCount = acc.circleCount,
                blockInsertCount = acc.blockInsertCount,
                totalLengthInDrawingUnits = acc.totalLength,
                totalClosedAreaInDrawingUnitsSq = acc.totalClosedArea,
                blockNames = acc.blockNames.toMap(),
                textsInLayer = acc.texts.toList()
            )
        }.sortedByDescending {
            it.lineCount + it.polylineCount + it.blockInsertCount + it.circleCount
        }

        return DxfParseResult(
            detectedUnit = detectedUnit,
            totalEntities = totalEntities,
            layers = layerSummaries,
            allAnnotations = allAnnotations,
            parseNotes = "تم استخراج $totalEntities عنصر رسم عبر ${layerSummaries.size} طبقة (وحدة الرسم المكتشفة: ${detectedUnit.arName})"
        )
    }

    private fun computePolylineLength(vertices: List<Pair<Double, Double>>, closed: Boolean): Double {
        if (vertices.size < 2) return 0.0
        var sum = 0.0
        for (idx in 0 until vertices.size - 1) {
            val a = vertices[idx]
            val b = vertices[idx + 1]
            sum += hypot(b.first - a.first, b.second - a.second)
        }
        if (closed && vertices.size >= 3) {
            val first = vertices.first()
            val last = vertices.last()
            sum += hypot(first.first - last.first, first.second - last.second)
        }
        return sum
    }

    private fun computePolygonArea(vertices: List<Pair<Double, Double>>): Double {
        if (vertices.size < 3) return 0.0
        var area = 0.0
        for (i in vertices.indices) {
            val j = (i + 1) % vertices.size
            area += vertices[i].first * vertices[j].second
            area -= vertices[j].first * vertices[i].second
        }
        return abs(area) / 2.0
    }

    private fun cleanMtextFormatting(raw: String): String {
        return raw
            .replace(Regex("""\\[pP~]"""), " ")
            .replace(Regex("""\\[fFhHwWcCqQaAtT][^;]*;"""), "")
            .replace("{", "")
            .replace("}", "")
            .trim()
    }
}

/**
 * Helper to inspect file type and render PDF blueprint pages safely to scaled Bitmaps.
 */
object EngineeringFileReader {

    enum class FileKind(val arLabel: String) {
        IMAGE("صورة مخطط / لوحة"),
        PDF("مستند / مخطط PDF"),
        DXF("ملف AutoCAD DXF"),
        DOCX("مستند Word (.docx)"),
        XLSX("جدول Excel (.xlsx)"),
        SCHEDULE_TEXT("جدول أو مستند نصي CSV / TXT"),
        UNSUPPORTED("صيغة غير مدعومة")
    }

    fun detectFileKind(fileName: String, mimeType: String?): FileKind {
        val lower = fileName.lowercase()
        val mime = mimeType?.lowercase() ?: ""
        return when {
            lower.endsWith(".dxf") || mime.contains("dxf") -> FileKind.DXF
            lower.endsWith(".pdf") || mime == "application/pdf" -> FileKind.PDF
            lower.endsWith(".docx") ||
                mime.contains("wordprocessingml.document") -> FileKind.DOCX
            lower.endsWith(".xlsx") ||
                mime.contains("spreadsheetml.sheet") -> FileKind.XLSX
            lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt") ||
                mime.startsWith("text/") || mime.contains("csv") -> FileKind.SCHEDULE_TEXT
            mime.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") -> FileKind.IMAGE
            else -> FileKind.UNSUPPORTED
        }
    }

    fun readTextFromUri(context: Context, uri: Uri, maxBytes: Int = 8_000_000): String {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val readBytes = stream.readBytes()
            val capped = if (readBytes.size > maxBytes) readBytes.copyOf(maxBytes) else readBytes
            return OfficeDocumentParser.decodeTextBytes(capped)
        }
        throw IllegalStateException("تعذر فتح الملف المختار.")
    }

    fun readDocxFromUri(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            return OfficeDocumentParser.extractTextFromDocx(stream)
        }
        throw IllegalStateException("تعذر فتح ملف Word (.docx) المختار.")
    }

    fun readXlsxFromUri(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            return OfficeDocumentParser.extractTextFromXlsx(stream)
        }
        throw IllegalStateException("تعذر فتح ملف Excel (.xlsx) المختار.")
    }

    fun getPdfPageCount(context: Context, uri: Uri): Int {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return 0
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                return renderer.pageCount
            }
        }
    }

    fun renderPdfPageToBitmap(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        maxDimensionPx: Int = 1800
    ): Bitmap {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("تعذر فتح ملف PDF.")
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) throw IllegalStateException("ملف PDF فارغ.")
                val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safeIndex).use { page ->
                    val scale = minOf(
                        maxDimensionPx.toFloat() / page.width.coerceAtLeast(1),
                        maxDimensionPx.toFloat() / page.height.coerceAtLeast(1),
                        2.5f
                    )
                    val targetW = (page.width * scale).toInt().coerceAtLeast(400)
                    val targetH = (page.height * scale).toInt().coerceAtLeast(400)
                    val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    fun renderPdfPagesRange(
        context: Context,
        uri: Uri,
        startPageIndexZeroBased: Int,
        maxPages: Int = 6,
        maxDimensionPx: Int = 1400
    ): List<Bitmap> {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("تعذر فتح ملف PDF.")
        val bitmaps = mutableListOf<Bitmap>()
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val total = renderer.pageCount
                if (total == 0) throw IllegalStateException("ملف PDF فارغ.")
                val safeStart = startPageIndexZeroBased.coerceIn(0, total - 1)
                val safeEndExclusive = (safeStart + maxPages.coerceIn(1, 6)).coerceAtMost(total)
                for (idx in safeStart until safeEndExclusive) {
                    renderer.openPage(idx).use { page ->
                        val scale = minOf(
                            maxDimensionPx.toFloat() / page.width.coerceAtLeast(1),
                            maxDimensionPx.toFloat() / page.height.coerceAtLeast(1),
                            2.0f
                        )
                        val targetW = (page.width * scale).toInt().coerceAtLeast(400)
                        val targetH = (page.height * scale).toInt().coerceAtLeast(400)
                        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    }
                }
            }
        }
        return bitmaps
    }

    fun loadScaledBitmap(context: Context, uri: Uri, maxDimensionPx: Int = 1800): Bitmap {
        val optionsBounds = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, optionsBounds)
        }
        val origW = optionsBounds.outWidth
        val origH = optionsBounds.outHeight
        var sampleSize = 1
        while (origW / sampleSize > maxDimensionPx * 2 || origH / sampleSize > maxDimensionPx * 2) {
            sampleSize *= 2
        }
        val decodeOpts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: throw IllegalStateException("تعذر قراءة الصورة المختارة.")

        val maxEdge = maxOf(raw.width, raw.height)
        return if (maxEdge > maxDimensionPx) {
            val scale = maxDimensionPx.toFloat() / maxEdge
            Bitmap.createScaledBitmap(
                raw,
                (raw.width * scale).toInt().coerceAtLeast(1),
                (raw.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            raw
        }
    }
}
