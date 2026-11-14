package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.BlueprintAnalysisStatus
import com.example.viewmodel.CivilQuantitiesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueprintAnalyzerScreen(
    viewModel: CivilQuantitiesViewModel,
    onBack: () -> Unit,
    onNavigateToBoq: () -> Unit
) {
    val state by viewModel.blueprintState.collectAsState()
    val context = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var elementToEdit by remember { mutableStateOf<ExtractedBlueprintElement?>(null) }

    // Zero-permission Photo Picker for blueprint images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onBlueprintImagesSelected(uris)
        }
    }

    // Zero-permission SAF Document Picker for PDF, AutoCAD DXF, CSV/TXT Schedules
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onEngineeringDocumentSelected(uri)
        }
    }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "حساب الكميات من المخططات والملفات",
                subtitle = "صور الخرط • PDF • AutoCAD DXF • جداول ETABS/Revit",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    if (state.report != null) {
                        IconButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.testTag("save_blueprint_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "حفظ المخطط",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Supported Formats Banner & Pickers
            item {
                SectionHeader(
                    title = "1. اختيار المخطط أو الملف الهندسي",
                    icon = Icons.Default.UploadFile,
                    badgeText = "استيراد مباشر"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Images Picker
                    Card(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(124.dp)
                            .testTag("pick_blueprint_images_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "اختيار صور المخطط",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "صور الخرط والمخططات",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "JPG / PNG (حتى 4 لوحات)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    // Files Picker (PDF, DXF, CSV/TXT)
                    Card(
                        onClick = {
                            documentPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "text/*",
                                    "application/dxf",
                                    "image/vnd.dxf",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(124.dp)
                            .testTag("pick_cad_file_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "استيراد ملف هندسي",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "ملفات البرامج و PDF",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                            Text(
                                text = "AutoCAD DXF • PDF • CSV",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                // Supported engineering formats hint
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "• ملفات أوتوكاد (DXF): تُقرأ الطبقات والأطوال والمساحات المغلقة والبلوكات مباشرة في الجهاز.\n• مخططات PDF والصور: تُقرأ الجداول الإنشائية والمساقط تلقائياً مع مراعاة BS 8110.\n• جداول Excel/ETABS/Revit: صدّر الجدول بصيغة CSV أو TXT لحصره فوراً.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // 2. Preview of Selected File / Blueprint
            if (state.selectedFileKind != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.selectedFileKind?.arLabel ?: "",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = state.selectedFileNames.joinToString("، "),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 2
                                    )
                                }
                                if (state.selectedFileKind == EngineeringFileReader.FileKind.PDF && state.pdfPageCount > 1) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.selectPdfPage(state.currentPdfPageIndex - 1) },
                                            enabled = state.currentPdfPageIndex > 0
                                        ) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = "الصفحة السابقة")
                                        }
                                        Text(
                                            text = "${state.currentPdfPageIndex + 1} / ${state.pdfPageCount}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        IconButton(
                                            onClick = { viewModel.selectPdfPage(state.currentPdfPageIndex + 1) },
                                            enabled = state.currentPdfPageIndex < state.pdfPageCount - 1
                                        ) {
                                            Icon(Icons.Default.ChevronLeft, contentDescription = "الصفحة التالية")
                                        }
                                    }
                                }
                            }

                            // Bitmap Preview for Images & PDF
                            if (state.previewBitmaps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(state.previewBitmaps) { bmp ->
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "معاينة المخطط",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .height(210.dp)
                                                .widthIn(min = 240.dp, max = 340.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }

                            // DXF Layer Preview & Unit Selector
                            val dxf = state.dxfParseResult
                            if (dxf != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "وحدة الرسم في ملف AutoCAD DXF:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf(CadDrawingUnit.MILLIMETERS, CadDrawingUnit.CENTIMETERS, CadDrawingUnit.METERS)) { unit ->
                                        FilterChip(
                                            selected = state.dxfUnit == unit,
                                            onClick = { viewModel.updateDxfUnit(unit) },
                                            label = { Text(unit.arName) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = dxf.parseNotes,
                                    style = MaterialTheme.typography.bodySmall.copy(color = BlueprintTeal, fontWeight = FontWeight.Medium)
                                )
                            }

                            // CSV / TXT Schedule Preview
                            val schedText = state.scheduleTextContent
                            if (!schedText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = schedText.lines().take(6).joinToString("\n"),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(8.dp),
                                        maxLines = 6
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Site Defaults & Analysis Trigger
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "إعدادات المخطط والمواصفات المطبقة",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EngineeringInputField(
                                value = state.defaultHeightM,
                                onValueChange = { viewModel.updateBlueprintDefaults(heightM = it) },
                                label = "ارتفاع الدور/العمود",
                                unit = "متر",
                                modifier = Modifier.weight(1f),
                                testTag = "bp_default_height"
                            )
                            EngineeringInputField(
                                value = state.defaultSlabThicknessM,
                                onValueChange = { viewModel.updateBlueprintDefaults(slabThicknessM = it) },
                                label = "سمك البلاطة",
                                unit = "متر",
                                modifier = Modifier.weight(1f),
                                testTag = "bp_default_slab"
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EngineeringInputField(
                                value = state.wastagePercent,
                                onValueChange = { viewModel.updateBlueprintDefaults(wastagePercent = it) },
                                label = "نسبة الهالك",
                                unit = "%",
                                modifier = Modifier.weight(1f)
                            )
                            EngineeringInputField(
                                value = state.steelRateKgPerM3,
                                onValueChange = { viewModel.updateBlueprintDefaults(steelRateKgPerM3 = it) },
                                label = "معدل الحديد التقديري",
                                unit = "كجم/م³",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "رتبة الخرسانة (BS 8110):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            items(listOf(ConcreteGrade.C15, ConcreteGrade.C20, ConcreteGrade.C25, ConcreteGrade.C30)) { grade ->
                                FilterChip(
                                    selected = state.defaultMixGrade == grade,
                                    onClick = { viewModel.updateBlueprintDefaults(mixGrade = grade) },
                                    label = { Text("${grade.code} (${grade.ratioDescription})") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "قطر السيخ الافتراضي للتسليح (د):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            items(RebarDiameter.entries) { d ->
                                FilterChip(
                                    selected = state.defaultSteelDiameter == d,
                                    onClick = { viewModel.updateBlueprintDefaults(steelDiameter = d) },
                                    label = { Text("D${d.mm} (${d.mm} مم)") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.userContextNote,
                            onValueChange = { viewModel.updateBlueprintDefaults(userContextNote = it) },
                            label = { Text("ملاحظة إضافية للمهندس (اختياري)") },
                            placeholder = { Text("مثلاً: احسب فقط القواعد والأعمدة، أو المقياس 1:100") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val isLoading = state.analysisStatus is BlueprintAnalysisStatus.Loading
                        Button(
                            onClick = { viewModel.runBlueprintAiAnalysis() },
                            enabled = !isLoading && state.selectedFileKind != null &&
                                state.selectedFileKind != EngineeringFileReader.FileKind.UNSUPPORTED,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("analyze_blueprint_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text((state.analysisStatus as BlueprintAnalysisStatus.Loading).statusMessage)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.selectedFileKind == EngineeringFileReader.FileKind.DXF)
                                        "تصنيف ذكي لطبقات المخطط (AI)"
                                    else
                                        "قراءة المخطط واستخراج الكميات",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 4. Error Banner if any
            if (state.analysisStatus is BlueprintAnalysisStatus.Error) {
                val errMsg = (state.analysisStatus as BlueprintAnalysisStatus.Error).message
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "خطأ",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }
                    }
                }
            }

            // 5. Extracted Elements & Sudanese Market Aggregated Totals
            val report = state.report
            if (report != null) {
                item {
                    SectionHeader(
                        title = "2. العناصر المستخرجة (${report.elements.size})",
                        icon = Icons.Default.Checklist,
                        badgeText = report.drawingScale
                    )
                    Text(
                        text = "${report.drawingTitle} — ${report.sourceSummary}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                items(report.elements, key = { it.id }) { el ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("extracted_el_${el.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (el.isIncluded) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (el.isIncluded) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = el.isIncluded,
                                    onCheckedChange = { viewModel.toggleBlueprintElement(el.id) }
                                )
                                Surface(
                                    color = when (el.category) {
                                        ExtractedElementCategory.CONCRETE -> BlueprintTeal.copy(alpha = 0.15f)
                                        ExtractedElementCategory.STEEL -> SafetyAmber.copy(alpha = 0.15f)
                                        ExtractedElementCategory.MASONRY -> Color(0xFFC2410C).copy(alpha = 0.15f)
                                        ExtractedElementCategory.FINISHING -> Color(0xFF7C3AED).copy(alpha = 0.15f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = el.category.arLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = el.nameAr,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { elementToEdit = el },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "تعديل الأبعاد",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteBlueprintElement(el.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "حذف",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "العدد: ${el.count} | الأبعاد: ${el.lengthM} × ${el.widthM} × ${el.heightOrThicknessM} م",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                val metricText = when (el.category) {
                                    ExtractedElementCategory.CONCRETE -> "${el.volumeM3} م³"
                                    ExtractedElementCategory.STEEL -> {
                                        val dia = if (el.diameterMm > 0) RebarDiameter.fromMm(el.diameterMm) else state.defaultSteelDiameter
                                        val breakdown = SteelEstimator.breakdownFromWeightKg(el.weightKg, dia)
                                        "${el.weightKg} كجم | ${breakdown.bars12mRounded} سيخة (Ø${dia.mm})"
                                    }
                                    ExtractedElementCategory.MASONRY,
                                    ExtractedElementCategory.FINISHING -> "${el.areaM2} م²"
                                }
                                Text(
                                    text = metricText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            if (el.notesAr.isNotBlank()) {
                                Text(
                                    text = el.notesAr,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 6. Aggregated Sudanese Market Quantities Card
                item {
                    val totals = state.aggregatedTotals
                    ResultCard(
                        title = "إجمالي المواد المطلوبة للسوق السوداني",
                        subtitle = "خلطة ${state.defaultMixGrade.code} (${state.defaultMixGrade.ratioDescription}) | شاملة الهالك ${state.wastagePercent}%"
                    ) {
                        if (totals.totalConcreteGrossM3 > 0.0) {
                            ResultRow(
                                label = "إجمالي الخرسانة",
                                value = "${totals.totalConcreteGrossM3}",
                                unit = "م³",
                                highlight = true,
                                commercialHint = "الحجم الصافي من اللوحة: ${totals.totalConcreteNetM3} م³"
                            )
                            ResultRow(
                                label = "الأسمنت البورتلاندي",
                                value = "${totals.cementBags50Kg}",
                                unit = "شوال (50 كجم)",
                                highlight = true,
                                commercialHint = "يعادل: ${totals.cementTonnes} طن أسمنت"
                            )
                            ResultRow(
                                label = "الرمل الخرساني",
                                value = "${totals.sandM3}",
                                unit = "م³",
                                commercialHint = "يعادل ~ ${totals.sandTataTrucks} لوري تاتة (16م³)"
                            )
                            ResultRow(
                                label = "الكنكري (السن المكسر)",
                                value = "${totals.gravelM3}",
                                unit = "م³",
                                commercialHint = "يعادل ~ ${totals.gravelTataTrucks} لوري تاتة (16م³)"
                            )
                            ResultRow(
                                label = "مياه الخلط والمعالجة",
                                value = "${totals.waterDrums}",
                                unit = "برميل (200 لتر)"
                            )
                        }

                        if (totals.estimatedTotalSteelTonnes > 0.0) {
                            ResultRow(
                                label = "إجمالي حديد التسليح",
                                value = "${totals.estimatedTotalSteelTonnes}",
                                unit = "طن",
                                highlight = true,
                                commercialHint = "الوزن الكلي: ${totals.estimatedTotalSteelKg} كجم (شامل الهالك)"
                            )
                            if (totals.steelByDiameter.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                RebarBreakdownCard(
                                    title = "تفصيل السيخ حسب القطر (د) — الوزن والعدد",
                                    items = totals.steelByDiameter
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        if (totals.masonryAreaM2 > 0.0) {
                            ResultRow(
                                label = "الطوب (${state.defaultBrickType.arName.substringBefore(" (")})",
                                value = "${totals.totalBricksCount}",
                                unit = "طوبة",
                                highlight = true,
                                commercialHint = "سوقياً: ${totals.totalBricksThousand} ألف طوبة | مونة: ${totals.mortarCementBags} شوال أسمنت + ${totals.mortarSandM3} م³ رمل"
                            )
                        }

                        if (totals.finishingAreaM2 > 0.0) {
                            ResultRow(
                                label = "مسطحات التشطيبات والبياض",
                                value = "${totals.finishingAreaM2}",
                                unit = "م²"
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.pushBlueprintTotalsToBoq()
                                onNavigateToBoq()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("push_blueprint_boq_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Icon(Icons.Default.RequestQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ترحيل الكميات لجدول التسعير (BOQ)", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حفظ المشروع")
                            }
                            OutlinedButton(
                                onClick = {
                                    val shareBody = buildString {
                                        appendLine("تقرير كميات المخطط الهندسي: ${report.drawingTitle}")
                                        appendLine("الخلطة: ${state.defaultMixGrade.code}")
                                        if (totals.totalConcreteGrossM3 > 0) {
                                            appendLine("• الخرسانة: ${totals.totalConcreteGrossM3} م³")
                                            appendLine("• الأسمنت: ${totals.cementBags50Kg} شوال (${totals.cementTonnes} طن)")
                                            appendLine("• الرمل: ${totals.sandM3} م³ | الكنكري: ${totals.gravelM3} م³")
                                        }
                                        if (totals.estimatedTotalSteelTonnes > 0) {
                                            appendLine("• حديد التسليح: ${totals.estimatedTotalSteelTonnes} طن (${totals.estimatedTotalSteelKg} كجم)")
                                            totals.steelByDiameter.forEach { dRow ->
                                                appendLine("  - قطر ${dRow.diameter.mm} مم: ${dRow.weightKg} كجم | ${dRow.bars12mRounded} سيخة (12م)")
                                            }
                                        }
                                        if (totals.totalBricksCount > 0) {
                                            appendLine("• الطوب: ${totals.totalBricksCount} طوبة (${totals.totalBricksThousand} ألف)")
                                        }
                                    }
                                    shareText(context, shareBody, "كميات المخطط")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة")
                            }
                        }
                    }
                }

                // 7. Engineering Notes from Blueprint
                if (report.engineeringNotesAr.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "ملاحظات هندسية (BS 8110 والسودان):",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                report.engineeringNotesAr.forEach { note ->
                                    Text(
                                        text = "• $note",
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Element Dimensions Dialog
    if (elementToEdit != null) {
        val el = elementToEdit!!
        var cntStr by remember { mutableStateOf(el.count.toString()) }
        var lenStr by remember { mutableStateOf(el.lengthM.toString()) }
        var widStr by remember { mutableStateOf(el.widthM.toString()) }
        var hgtStr by remember { mutableStateOf(el.heightOrThicknessM.toString()) }

        AlertDialog(
            onDismissRequest = { elementToEdit = null },
            title = { Text("تعديل: ${el.nameAr}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngineeringInputField(
                        value = cntStr,
                        onValueChange = { cntStr = it },
                        label = "العدد",
                        unit = "عنصر"
                    )
                    EngineeringInputField(
                        value = lenStr,
                        onValueChange = { lenStr = it },
                        label = "الطول",
                        unit = "متر"
                    )
                    EngineeringInputField(
                        value = widStr,
                        onValueChange = { widStr = it },
                        label = "العرض",
                        unit = "متر"
                    )
                    EngineeringInputField(
                        value = hgtStr,
                        onValueChange = { hgtStr = it },
                        label = "الارتفاع / السمك",
                        unit = "متر"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBlueprintElementValues(
                            elementId = el.id,
                            count = cntStr.toIntOrNull() ?: el.count,
                            lengthM = lenStr.toDoubleOrNull() ?: el.lengthM,
                            widthM = widStr.toDoubleOrNull() ?: el.widthM,
                            heightOrThicknessM = hgtStr.toDoubleOrNull() ?: el.heightOrThicknessM
                        )
                        elementToEdit = null
                    }
                ) {
                    Text("تحديث")
                }
            },
            dismissButton = {
                TextButton(onClick = { elementToEdit = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showSaveDialog) {
        SaveProjectDialog(
            category = "مخطط هندسي",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title, location ->
                viewModel.saveBlueprintProject(title, location) {
                    showSaveDialog = false
                }
            }
        )
    }
}
