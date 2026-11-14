package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.*
import com.example.ui.theme.*
import com.example.viewmodel.SpecDocumentUiState
import com.example.viewmodel.SpecSourceMode

@Composable
fun SpecSourceInputSection(
    state: SpecDocumentUiState,
    onSelectMode: (SpecSourceMode) -> Unit,
    onPickDocument: () -> Unit,
    onPickImages: () -> Unit,
    onPastedTextChanged: (String) -> Unit,
    onChangePdfRange: (startPage: Int, count: Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Mode selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpecSourceMode.entries.forEach { mode ->
                val selected = state.sourceMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onSelectMode(mode) },
                    label = {
                        Text(
                            text = mode.arLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (mode) {
                                SpecSourceMode.DOCUMENT -> Icons.Default.Description
                                SpecSourceMode.IMAGES -> Icons.Default.AddPhotoAlternate
                                SpecSourceMode.PASTED_TEXT -> Icons.Default.EditNote
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("spec_mode_${mode.name.lowercase()}")
                )
            }
        }

        when (state.sourceMode) {
            SpecSourceMode.DOCUMENT -> {
                Card(
                    onClick = onPickDocument,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_spec_document_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "استيراد كراسة مواصفات",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "استيراد ملف كراسة المواصفات أو المقايسة",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "يدعم: Word (.docx) • Excel (.xlsx) • PDF متعدد الصفحات • CSV / TXT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // Selected document preview
                if (state.selectedFileName.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.selectedFileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = state.selectedFileKind?.arLabel ?: "",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.secondary)
                                    )
                                }
                            }

                            // PDF multi-page range selector
                            if (state.selectedFileKind == EngineeringFileReader.FileKind.PDF && state.pdfPageCount > 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "إجمالي صفحات الكراسة: ${state.pdfPageCount} صفحة (يُحلل حتى 6 صفحات في الدفعة)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val prev = (state.pdfStartPage - state.pdfPagesToAnalyze).coerceAtLeast(1)
                                            onChangePdfRange(prev, state.pdfPagesToAnalyze)
                                        },
                                        enabled = state.pdfStartPage > 1,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("الصفحات السابقة", fontSize = 11.sp)
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        val endPage = minOf(state.pdfPageCount, state.pdfStartPage + state.pdfPagesToAnalyze - 1)
                                        Text(
                                            text = "ص ${state.pdfStartPage} - $endPage",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val next = (state.pdfStartPage + state.pdfPagesToAnalyze)
                                                .coerceAtMost(state.pdfPageCount)
                                            onChangePdfRange(next, state.pdfPagesToAnalyze)
                                        },
                                        enabled = state.pdfStartPage + state.pdfPagesToAnalyze - 1 < state.pdfPageCount,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("الصفحات التالية", fontSize = 11.sp)
                                    }
                                }
                            }

                            if (state.previewBitmaps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(state.previewBitmaps) { idx, bmp ->
                                        Box(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(155.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White)
                                        ) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "صفحة ${idx + 1}",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = "ص ${state.pdfStartPage + idx}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.extractedDocumentText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "معاينة النص المستخرج (${state.extractedDocumentText.length} حرف):",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = state.extractedDocumentText.take(360) +
                                                if (state.extractedDocumentText.length > 360) "\n..." else "",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            ),
                                            maxLines = 6
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SpecSourceMode.IMAGES -> {
                Card(
                    onClick = onPickImages,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_spec_images_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "صور صفحات كراسة المواصفات",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "اختيار صور صفحات الكراسة أو المقايسة",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "حتى 6 صفحات مصورة (JPG / PNG) من المعرض مباشرة",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                if (state.previewBitmaps.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.previewBitmaps) { idx, bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "صورة صفحة ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(145.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }
            }

            SpecSourceMode.PASTED_TEXT -> {
                OutlinedTextField(
                    value = state.pastedSpecText,
                    onValueChange = onPastedTextChanged,
                    label = { Text("الصق أو اكتب بنود كراسة المواصفات والمقايسة هنا") },
                    placeholder = {
                        Text(
                            "مثال: خرسانة مسلحة للقواعد رتبة C25 بأسمنت مقاوم للأملاح 400 كجم/م³ عدد 12 قاعدة بأبعاد 1.5×1.5×0.5م وتسليح Ø16 وزن 1.8 طن، وبناء طوب أحمر مساحة 140 م²..."
                        )
                    },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spec_pasted_text_input")
                )
                Text(
                    text = "عدد الأحرف المدخلة: ${state.pastedSpecText.length} حرف (يدعم العربية والإنجليزية والجداول المنسوخة)",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}

@Composable
fun SpecTechnicalSummaryCard(summary: SpecTechnicalSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spec_technical_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BlueprintTeal.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = BlueprintTeal,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.projectTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (summary.clientOrConsultant.isNotBlank() && summary.clientOrConsultant != "غير محدد") {
                        Text(
                            text = "الجهة / الاستشاري: ${summary.clientOrConsultant}",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            SpecClauseRow("مواصفات الخرسانة:", summary.concreteMixSpecsAr)
            SpecClauseRow("نوع الأسمنت:", summary.cementTypeAr)
            SpecClauseRow("الحديد والأقطار (د):", summary.steelGradeAndDiametersAr)
            SpecClauseRow("الغطاء الخرساني:", summary.concreteCoverSpecAr)
            SpecClauseRow("المباني والمونة:", summary.masonryAndMortarSpecsAr)
            SpecClauseRow("التشطيبات والعزل:", summary.finishingSpecsAr)

            if (summary.bs8110ComplianceNotesAr.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "مطابقة الكود البريطاني BS 8110 والمواصفات السودانية:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        summary.bs8110ComplianceNotesAr.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecClauseRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
        )
    }
}

@Composable
fun SpecWorkItemCard(
    item: SpecWorkItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val catColor = when (item.category) {
        SpecItemCategory.EXCAVATION -> Color(0xFF475569)
        SpecItemCategory.CONCRETE -> BlueprintTeal
        SpecItemCategory.STEEL -> SafetyAmber
        SpecItemCategory.MASONRY -> Color(0xFFC2410C)
        SpecItemCategory.FINISHING -> Color(0xFF7C3AED)
        SpecItemCategory.WATERPROOFING -> NavyPrimary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spec_item_card_${item.itemCode}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isIncluded) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            1.dp,
            if (item.isIncluded) catColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.isIncluded,
                    onCheckedChange = { onToggle() }
                )
                Surface(
                    color = catColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${item.itemCode} • ${item.category.arLabel}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = catColor,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل البند",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف البند",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = item.nameAr,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            if (item.specRequirementAr.isNotBlank()) {
                Text(
                    text = item.specRequirementAr,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            val qtySummary = when (item.category) {
                SpecItemCategory.CONCRETE, SpecItemCategory.EXCAVATION -> {
                    val v = if (item.volumeM3 > 0.0) item.volumeM3 else item.explicitQuantityFromDoc
                    "الكمية: $v م³"
                }
                SpecItemCategory.STEEL -> {
                    val dia = if (item.diameterMm > 0) item.diameterMm else 16
                    val barW = RebarDiameter.fromMm(dia).weightPer12mBarKg
                    val barsCount = if (item.weightKg > 0 && barW > 0) kotlin.math.ceil(item.weightKg / barW).toInt() else 0
                    "د = $dia مم | الوزن: ${item.weightKg} كجم | العدد: $barsCount سيخة 12م"
                }
                SpecItemCategory.MASONRY, SpecItemCategory.FINISHING, SpecItemCategory.WATERPROOFING -> {
                    val a = if (item.areaM2 > 0.0) item.areaM2 else item.explicitQuantityFromDoc
                    "المساحة: $a م²"
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = qtySummary + if (item.lengthM > 0 || item.widthM > 0) {
                        "  (${item.count} × ${item.lengthM} × ${item.widthM} × ${item.heightOrThicknessM} م)"
                    } else "",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            if (item.notesAr.isNotBlank()) {
                Text(
                    text = "ملاحظة: ${item.notesAr}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun EditSpecItemDialog(
    item: SpecWorkItem,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, len: Double, wid: Double, hgt: Double, explicitQty: Double, diameterMm: Int) -> Unit
) {
    var countStr by remember { mutableStateOf(item.count.toString()) }
    var lenStr by remember { mutableStateOf(item.lengthM.toString()) }
    var widStr by remember { mutableStateOf(item.widthM.toString()) }
    var hgtStr by remember { mutableStateOf(item.heightOrThicknessM.toString()) }
    val initialQty = when (item.category) {
        SpecItemCategory.CONCRETE, SpecItemCategory.EXCAVATION -> if (item.volumeM3 > 0) item.volumeM3 else item.explicitQuantityFromDoc
        SpecItemCategory.STEEL -> item.weightKg
        else -> if (item.areaM2 > 0) item.areaM2 else item.explicitQuantityFromDoc
    }
    var qtyStr by remember { mutableStateOf(initialQty.toString()) }
    var selectedDia by remember { mutableStateOf(if (item.diameterMm in listOf(8, 10, 12, 16, 20, 25, 32)) item.diameterMm else 16) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تعديل: ${item.nameAr}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qtyStr,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) qtyStr = it },
                    label = {
                        Text(
                            when (item.category) {
                                SpecItemCategory.STEEL -> "الكمية المباشرة (كجم)"
                                SpecItemCategory.CONCRETE, SpecItemCategory.EXCAVATION -> "الكمية المباشرة (م³)"
                                else -> "الكمية المباشرة (م²)"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "أو تعديل الأبعاد التفصيلية (تُحسب تلقائياً إذا > 0):",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countStr,
                        onValueChange = { if (it.all { c -> c.isDigit() }) countStr = it },
                        label = { Text("العدد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lenStr,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) lenStr = it },
                        label = { Text("الطول (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widStr,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) widStr = it },
                        label = { Text("العرض (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hgtStr,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) hgtStr = it },
                        label = { Text("الارتفاع/السمك (م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (item.category == SpecItemCategory.STEEL) {
                    Text(
                        text = "قطر السيخ (د):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RebarDiameter.entries.forEach { d ->
                            FilterChip(
                                selected = selectedDia == d.mm,
                                onClick = { selectedDia = d.mm },
                                label = { Text("${d.mm}مم") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        countStr.toIntOrNull() ?: 1,
                        lenStr.toDoubleOrNull() ?: 0.0,
                        widStr.toDoubleOrNull() ?: 0.0,
                        hgtStr.toDoubleOrNull() ?: 0.0,
                        qtyStr.toDoubleOrNull() ?: 0.0,
                        selectedDia
                    )
                }
            ) {
                Text("تحديث")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun SpecWorkPlanQuickDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, location: String, pace: SchedulePace) -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var location by remember { mutableStateOf("الخرطوم، السودان") }
    var selectedPace by remember { mutableStateOf(SchedulePace.STANDARD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إنشاء خطة عمل من كراسة المواصفات",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم المشروع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("الموقع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "وتيرة التنفيذ:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                SchedulePace.entries.forEach { pace ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPace == pace,
                            onClick = { selectedPace = pace }
                        )
                        Column {
                            Text(
                                text = pace.arLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = pace.descAr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, location, selectedPace) }) {
                Text("توليد الجدول الزمني")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
