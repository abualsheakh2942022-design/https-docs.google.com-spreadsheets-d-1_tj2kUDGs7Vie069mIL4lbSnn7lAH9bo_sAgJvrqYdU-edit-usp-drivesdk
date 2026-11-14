package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun SpecDocumentScreen(
    viewModel: CivilQuantitiesViewModel,
    onBack: () -> Unit,
    onNavigateToBoq: () -> Unit,
    onNavigateToWorkPlan: () -> Unit
) {
    val state by viewModel.specState.collectAsState()
    val context = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var showWorkPlanDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<SpecWorkItem?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onSpecImagesSelected(uris)
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onSpecDocumentSelected(uri)
        }
    }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "حساب الكميات من كراسات المواصفات",
                subtitle = "Word (.docx) • Excel (.xlsx) • PDF • نصوص العقود",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    if (state.report != null) {
                        IconButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.testTag("save_spec_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "حفظ كراسة المواصفات",
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
            // 1. Source Selection & Preview
            item {
                SectionHeader(
                    title = "1. مصدر كراسة المواصفات والمستندات",
                    icon = Icons.Default.MenuBook,
                    badgeText = "BS 8110 • SSMO"
                )
                SpecSourceInputSection(
                    state = state,
                    onSelectMode = viewModel::setSpecSourceMode,
                    onPickDocument = {
                        docPickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/*",
                                "*/*"
                            )
                        )
                    },
                    onPickImages = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPastedTextChanged = viewModel::updateSpecPastedText,
                    onChangePdfRange = viewModel::updateSpecPdfRange
                )
            }

            // 2. Site Defaults & Mix Parameters
            item {
                SectionHeader(
                    title = "2. الإعدادات الميدانية المرجعية",
                    icon = Icons.Default.Tune
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EngineeringInputField(
                                value = state.defaultHeightM,
                                onValueChange = { viewModel.updateSpecDefaults(heightM = it) },
                                label = "ارتفاع الدور الافتراضي",
                                unit = "م",
                                modifier = Modifier.weight(1f),
                                testTag = "spec_default_height_input"
                            )
                            EngineeringInputField(
                                value = state.defaultSlabThicknessM,
                                onValueChange = { viewModel.updateSpecDefaults(slabThicknessM = it) },
                                label = "سمك البلاطة",
                                unit = "م",
                                modifier = Modifier.weight(1f),
                                testTag = "spec_default_slab_input"
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EngineeringInputField(
                                value = state.wastagePercent,
                                onValueChange = { viewModel.updateSpecDefaults(wastagePercent = it) },
                                label = "نسبة الهالك",
                                unit = "%",
                                modifier = Modifier.weight(1f),
                                testTag = "spec_wastage_input"
                            )
                            EngineeringInputField(
                                value = state.steelRateKgPerM3,
                                onValueChange = { viewModel.updateSpecDefaults(steelRateKgPerM3 = it) },
                                label = "نسبة التسليح التقديرية",
                                unit = "كجم/م³",
                                modifier = Modifier.weight(1f),
                                testTag = "spec_steel_rate_input"
                            )
                        }

                        Text(
                            text = "رتبة الخرسانة الافتراضية (BS 8110):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(ConcreteGrade.C15, ConcreteGrade.C20, ConcreteGrade.C25, ConcreteGrade.C30).forEach { grade ->
                                FilterChip(
                                    selected = state.defaultMixGrade == grade,
                                    onClick = { viewModel.updateSpecDefaults(mixGrade = grade) },
                                    label = { Text("${grade.code} (${grade.ratioDescription})") }
                                )
                            }
                        }

                        Text(
                            text = "القطر الرئيسي الافتراضي للسيخ (د):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                RebarDiameter.D10,
                                RebarDiameter.D12,
                                RebarDiameter.D16,
                                RebarDiameter.D20,
                                RebarDiameter.D25
                            ).forEach { dia ->
                                FilterChip(
                                    selected = state.defaultSteelDiameter == dia,
                                    onClick = { viewModel.updateSpecDefaults(steelDiameter = dia) },
                                    label = { Text("${dia.mm} مم") }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.userContextNote,
                            onValueChange = { viewModel.updateSpecDefaults(userContextNote = it) },
                            label = { Text("ملاحظات أو توجيهات للتحليل (اختياري)") },
                            placeholder = { Text("مثلاً: التركيز على بنود الأساسات والدور الأرضي فقط") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spec_context_note_input")
                        )
                    }
                }
            }

            // 3. Analyze CTA & Status
            item {
                val isLoading = state.analysisStatus is BlueprintAnalysisStatus.Loading
                Button(
                    onClick = { viewModel.runSpecDocumentAnalysis() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("analyze_spec_document_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text((state.analysisStatus as BlueprintAnalysisStatus.Loading).statusMessage)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تحليل الكراسة واستخراج المواصفات والكميات",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (state.analysisStatus is BlueprintAnalysisStatus.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (state.analysisStatus as BlueprintAnalysisStatus.Error).message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }
                    }
                }
            }

            // 4. Report Results
            val report = state.report
            if (report != null) {
                item {
                    SectionHeader(
                        title = "3. المواصفات الفنية الحاكمة المستخلصة",
                        icon = Icons.Default.FactCheck,
                        badgeText = report.recommendedMixGrade.code
                    )
                    SpecTechnicalSummaryCard(summary = report.technicalSummary)
                }

                if (report.engineeringNotesAr.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "تنبيهات الحصر والبنود الناقصة:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                                report.engineeringNotesAr.forEach { note ->
                                    Text(
                                        text = "• $note",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "4. بنود الكميات المستخرجة من الكراسة (${report.items.size})",
                        icon = Icons.Default.Checklist
                    )
                }

                items(report.items, key = { it.id }) { specItem ->
                    SpecWorkItemCard(
                        item = specItem,
                        onToggle = { viewModel.toggleSpecItem(specItem.id) },
                        onEdit = { itemToEdit = specItem },
                        onDelete = { viewModel.deleteSpecItem(specItem.id) }
                    )
                }

                // 5. Sudanese Market Aggregated Totals + Rebar Breakdown by diameter d
                item {
                    val t = state.aggregatedTotals
                    SectionHeader(
                        title = "5. ملخص التوريدات والكميات للسوق السوداني",
                        icon = Icons.Default.LocalShipping
                    )
                    ResultCard(
                        title = "إجمالي المواد المحسوبة من الكراسة",
                        subtitle = "تشمل نسبة الهالك (${state.wastagePercent}%) ورتبة (${state.defaultMixGrade.code})"
                    ) {
                        if (t.excavationM3 > 0.0) {
                            ResultRow("أعمال الحفر والردم", "${t.excavationM3}", "م³")
                        }
                        if (t.totalConcreteGrossM3 > 0.0) {
                            ResultRow(
                                "الخرسانة الكلية (بالهالك)",
                                "${t.totalConcreteGrossM3}",
                                "م³",
                                highlight = true
                            )
                            ResultRow(
                                "الأسمنت المطلوب",
                                "${t.cementBags50Kg}",
                                "شوال (50كجم)",
                                commercialHint = "يعادل ${t.cementTonnes} طن (${report.technicalSummary.cementTypeAr.take(35)})"
                            )
                            ResultRow(
                                "الرمل النظيف",
                                "${t.sandM3}",
                                "م³",
                                commercialHint = "${t.sandTataTrucks} لوري تاتة (16م³)"
                            )
                            ResultRow(
                                "الكنكري / الحصى",
                                "${t.gravelM3}",
                                "م³",
                                commercialHint = "${t.gravelTataTrucks} لوري تاتة (16م³)"
                            )
                            ResultRow("مياه الخلط والمعالجة", "${t.waterDrums}", "برميل (200 لتر)")
                        }
                        if (t.estimatedTotalSteelTonnes > 0.0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ResultRow(
                                "إجمالي حديد التسليح",
                                "${t.estimatedTotalSteelTonnes}",
                                "طن",
                                highlight = true,
                                commercialHint = "الوزن الكلي: ${t.estimatedTotalSteelKg} كجم"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            RebarBreakdownCard(
                                title = "تفصيل السيخ بالوزن والعدد حسب القطر (د)",
                                subtitle = "طول السيخة التجارية = 12 متر (BS 4449)",
                                items = t.steelByDiameter
                            )
                        }
                        if (t.totalBricksCount > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ResultRow(
                                "أعمال الطوب (${state.defaultBrickType.arName})",
                                "${t.totalBricksThousand}",
                                "ألف طوبة",
                                commercialHint = "الإجمالي: ${t.totalBricksCount} طوبة | مونة: ${t.mortarCementBags} شوال أسمنت"
                            )
                        }
                        if (t.finishingAreaM2 > 0.0) {
                            ResultRow("أعمال البياض والتشطيبات", "${t.finishingAreaM2}", "م²")
                        }
                        if (t.waterproofingAreaM2 > 0.0) {
                            ResultRow("أعمال العزل والحماية", "${t.waterproofingAreaM2}", "م²")
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.pushSpecTotalsToBoq()
                                    onNavigateToBoq()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("spec_push_to_boq_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Icon(Icons.Default.RequestQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ترحيل للمقايسة BOQ", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { showWorkPlanDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("spec_create_work_plan_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueprintTeal)
                            ) {
                                Icon(Icons.Default.ViewTimeline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("خطة العمل الزمنية", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("spec_save_project_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ المشروع", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val shareBody = buildSpecShareReport(report, state.aggregatedTotals)
                                    shareText(context, shareBody, "ملخص كميات كراسة المواصفات")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("spec_share_report_btn")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة التقرير", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    itemToEdit?.let { editing ->
        EditSpecItemDialog(
            item = editing,
            onDismiss = { itemToEdit = null },
            onConfirm = { count, len, wid, hgt, explicitQty, dia ->
                viewModel.updateSpecItemValues(
                    itemId = editing.id,
                    count = count,
                    lengthM = len,
                    widthM = wid,
                    heightOrThicknessM = hgt,
                    explicitQuantity = explicitQty,
                    diameterMm = dia
                )
                itemToEdit = null
            }
        )
    }

    if (showSaveDialog) {
        SaveProjectDialog(
            category = "كراسة مواصفات",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title, location ->
                viewModel.saveSpecProject(title, location) {
                    showSaveDialog = false
                }
            }
        )
    }

    if (showWorkPlanDialog) {
        SpecWorkPlanQuickDialog(
            defaultTitle = state.report?.technicalSummary?.projectTitle ?: "مشروع كراسة المواصفات",
            onDismiss = { showWorkPlanDialog = false },
            onConfirm = { title, location, pace ->
                viewModel.createWorkPlanFromSpecDocument(
                    title = title,
                    location = location,
                    pace = pace
                ) {
                    showWorkPlanDialog = false
                    onNavigateToWorkPlan()
                }
            }
        )
    }
}

private fun buildSpecShareReport(
    report: SpecDocumentAnalysisReport,
    totals: com.example.viewmodel.SpecAggregatedTotals
): String = buildString {
    appendLine("تقرير كميات كراسة المواصفات: ${report.technicalSummary.projectTitle}")
    appendLine("مواصفات الخرسانة: ${report.technicalSummary.concreteMixSpecsAr}")
    appendLine("نوع الأسمنت: ${report.technicalSummary.cementTypeAr}")
    appendLine("الحديد والأقطار: ${report.technicalSummary.steelGradeAndDiametersAr}")
    appendLine("-----------------------------------")
    if (totals.excavationM3 > 0) appendLine("• الحفر والردم: ${totals.excavationM3} م³")
    if (totals.totalConcreteGrossM3 > 0) {
        appendLine("• الخرسانة: ${totals.totalConcreteGrossM3} م³")
        appendLine("• الأسمنت: ${totals.cementBags50Kg} شوال (${totals.cementTonnes} طن)")
        appendLine("• الرمل: ${totals.sandM3} م³ (${totals.sandTataTrucks} تاتة)")
        appendLine("• الكنكري: ${totals.gravelM3} م³ (${totals.gravelTataTrucks} تاتة)")
    }
    if (totals.estimatedTotalSteelTonnes > 0) {
        appendLine("• إجمالي الحديد: ${totals.estimatedTotalSteelTonnes} طن (${totals.estimatedTotalSteelKg} كجم)")
        totals.steelByDiameter.forEach { d ->
            appendLine("  - قطر ${d.diameter.mm} مم: ${d.weightKg} كجم | ${d.bars12mRounded} سيخة 12م")
        }
    }
    if (totals.totalBricksCount > 0) {
        appendLine("• الطوب: ${totals.totalBricksCount} طوبة (${totals.totalBricksThousand} ألف)")
    }
    if (totals.finishingAreaM2 > 0) appendLine("• التشطيبات: ${totals.finishingAreaM2} م²")
    if (totals.waterproofingAreaM2 > 0) appendLine("• العزل: ${totals.waterproofingAreaM2} م²")
}
