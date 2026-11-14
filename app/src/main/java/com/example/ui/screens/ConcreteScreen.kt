package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ConcreteGrade
import com.example.domain.StructuralElementType
import com.example.ui.components.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.CivilQuantitiesViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcreteScreen(
    viewModel: CivilQuantitiesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.concreteState.collectAsState()
    val prices by viewModel.marketPrices.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showSaveDialog by remember { mutableStateOf(false) }

    val diagL = if (state.elementType == StructuralElementType.COLUMN_CIRCULAR) {
        state.circularDiameterM.toDoubleOrNull() ?: 0.30
    } else {
        state.lengthM.toDoubleOrNull() ?: 4.0
    }
    val diagW = if (state.elementType == StructuralElementType.COLUMN_CIRCULAR) {
        state.circularDiameterM.toDoubleOrNull() ?: 0.30
    } else {
        state.widthM.toDoubleOrNull() ?: 0.25
    }
    val diagH = when (state.elementType) {
        StructuralElementType.SOLID_SLAB,
        StructuralElementType.FLAT_SLAB,
        StructuralElementType.RIBBED_SLAB,
        StructuralElementType.SLAB_ON_GRADE -> state.slabThicknessM.toDoubleOrNull() ?: 0.15
        else -> state.depthHeightM.toDoubleOrNull() ?: 3.0
    }
    val currentCount = (state.count.toIntOrNull() ?: 1).coerceAtLeast(1)

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "حساب كميات الخرسانة والخلطات",
                subtitle = "BS 8110 / BS 5328 والمواصفات السودانية",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("save_concrete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "حفظ في مشروع",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Structural Element Selection
            item {
                SectionHeader(
                    title = "1. نوع العنصر الإنشائي",
                    icon = Icons.Default.Category
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(StructuralElementType.entries) { element ->
                        val isSelected = element == state.elementType
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateConcreteElementType(element)
                            },
                            label = {
                                Text(
                                    text = element.titleAr.substringBefore(" ("),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("element_chip_${element.name}")
                        )
                    }
                }
            }

            // Live Interactive Blueprint Canvas Preview
            item {
                LiveConcreteDiagramCard(
                    elementType = state.elementType,
                    lengthM = diagL,
                    widthM = diagW,
                    heightM = diagH,
                    count = currentCount,
                    netVolumeM3 = state.result?.volumeNetM3 ?: 0.0
                )
            }

            // 2. Element Dimensions Input
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring()),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "أبعاد ${state.elementType.titleAr}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick field dimension presets tailored per structural element
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            when (state.elementType) {
                                StructuralElementType.COLUMN_RECT -> {
                                    listOf(
                                        Triple("25×40×3م", "0.40", "0.25"),
                                        Triple("25×50×3م", "0.50", "0.25"),
                                        Triple("30×60×3.2م", "0.60", "0.30")
                                    ).forEach { (lbl, l, w) ->
                                        SuggestionChip(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.updateConcreteDimensions(length = l, width = w)
                                            },
                                            label = { Text(lbl, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                StructuralElementType.SOLID_SLAB,
                                StructuralElementType.FLAT_SLAB,
                                StructuralElementType.RIBBED_SLAB,
                                StructuralElementType.SLAB_ON_GRADE -> {
                                    listOf(
                                        Triple("10×12م (15سم)", "12.0", "10.0"),
                                        Triple("12×15م (15سم)", "15.0", "12.0"),
                                        Triple("15×20م (18سم)", "20.0", "15.0")
                                    ).forEachIndexed { idx, (lbl, l, w) ->
                                        SuggestionChip(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.updateConcreteDimensions(
                                                    length = l,
                                                    width = w,
                                                    slabThickness = if (idx == 2) "0.18" else "0.15"
                                                )
                                            },
                                            label = { Text(lbl, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                StructuralElementType.FOOTING_ISOLATED -> {
                                    listOf(
                                        Triple("1.5×1.5×0.5م", "1.5", "1.5"),
                                        Triple("2.0×2.0×0.6م", "2.0", "2.0"),
                                        Triple("2.5×2.5×0.7م", "2.5", "2.5")
                                    ).forEachIndexed { idx, (lbl, l, w) ->
                                        SuggestionChip(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.updateConcreteDimensions(
                                                    length = l,
                                                    width = w,
                                                    depthHeight = when (idx) {
                                                        0 -> "0.5"
                                                        1 -> "0.6"
                                                        else -> "0.7"
                                                    }
                                                )
                                            },
                                            label = { Text(lbl, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                StructuralElementType.BEAMS,
                                StructuralElementType.GROUND_BEAM -> {
                                    listOf(
                                        Triple("4.0×0.25×0.5م", "4.0", "0.25"),
                                        Triple("5.0×0.25×0.6م", "5.0", "0.25"),
                                        Triple("6.0×0.30×0.7م", "6.0", "0.30")
                                    ).forEachIndexed { idx, (lbl, l, w) ->
                                        SuggestionChip(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.updateConcreteDimensions(
                                                    length = l,
                                                    width = w,
                                                    depthHeight = when (idx) {
                                                        0 -> "0.5"
                                                        1 -> "0.6"
                                                        else -> "0.7"
                                                    }
                                                )
                                            },
                                            label = { Text(lbl, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (state.elementType) {
                            StructuralElementType.COLUMN_CIRCULAR -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EngineeringInputField(
                                        value = state.circularDiameterM,
                                        onValueChange = { viewModel.updateConcreteDimensions(circularDiameter = it) },
                                        label = "قطر العمود",
                                        unit = "متر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "col_diameter_input"
                                    )
                                    EngineeringInputField(
                                        value = state.depthHeightM,
                                        onValueChange = { viewModel.updateConcreteDimensions(depthHeight = it) },
                                        label = "ارتفاع العمود",
                                        unit = "متر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "col_height_input"
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                EngineeringInputField(
                                    value = state.count,
                                    onValueChange = { viewModel.updateConcreteDimensions(count = it) },
                                    label = "عدد الأعمدة المتماثلة",
                                    unit = "عمود",
                                    testTag = "col_count_input"
                                )
                            }
                            StructuralElementType.SOLID_SLAB,
                            StructuralElementType.FLAT_SLAB,
                            StructuralElementType.RIBBED_SLAB,
                            StructuralElementType.SLAB_ON_GRADE -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EngineeringInputField(
                                        value = state.lengthM,
                                        onValueChange = { viewModel.updateConcreteDimensions(length = it) },
                                        label = "طول البلاطة / السقف",
                                        unit = "متر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "slab_len_input"
                                    )
                                    EngineeringInputField(
                                        value = state.widthM,
                                        onValueChange = { viewModel.updateConcreteDimensions(width = it) },
                                        label = "عرض البلاطة",
                                        unit = "متر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "slab_width_input"
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EngineeringInputField(
                                        value = state.slabThicknessM,
                                        onValueChange = { viewModel.updateConcreteDimensions(slabThickness = it) },
                                        label = "سمك البلاطة",
                                        unit = "متر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "slab_thick_input"
                                    )
                                    EngineeringInputField(
                                        value = state.count,
                                        onValueChange = { viewModel.updateConcreteDimensions(count = it) },
                                        label = "عدد الطوابق / البلاطات",
                                        unit = "سقف",
                                        modifier = Modifier.weight(1f),
                                        testTag = "slab_count_input"
                                    )
                                }
                            }
                            StructuralElementType.CUSTOM_VOLUME -> {
                                EngineeringInputField(
                                    value = state.directVolumeM3,
                                    onValueChange = { viewModel.updateConcreteDimensions(directVolume = it) },
                                    label = "الحجم المباشر المطلوب",
                                    unit = "م³",
                                    testTag = "direct_vol_input"
                                )
                            }
                            else -> {
                                // Default Rectangular elements (Footings, Columns, Beams, etc.)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EngineeringInputField(
                                        value = state.lengthM,
                                        onValueChange = { viewModel.updateConcreteDimensions(length = it) },
                                        label = "الطول",
                                        unit = "متر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "elem_len_input"
                                    )
                                    EngineeringInputField(
                                        value = state.widthM,
                                        onValueChange = { viewModel.updateConcreteDimensions(width = it) },
                                        label = "العرض",
                                        unit = "متر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "elem_width_input"
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EngineeringInputField(
                                        value = state.depthHeightM,
                                        onValueChange = { viewModel.updateConcreteDimensions(depthHeight = it) },
                                        label = "الارتفاع / العمق",
                                        unit = "متر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "elem_depth_input"
                                    )
                                    EngineeringInputField(
                                        value = state.count,
                                        onValueChange = { viewModel.updateConcreteDimensions(count = it) },
                                        label = "العدد",
                                        unit = "عنصر",
                                        modifier = Modifier.weight(1f),
                                        testTag = "elem_count_input"
                                    )
                                }
                            }
                        }

                        if (state.elementType != StructuralElementType.CUSTOM_VOLUME) {
                            Spacer(modifier = Modifier.height(10.dp))
                            QuickStepperRow(
                                label = "تعديل سريع للعدد",
                                valueText = "$currentCount",
                                unit = "عنصر",
                                onDecrement = {
                                    val next = (currentCount - 1).coerceAtLeast(1)
                                    viewModel.updateConcreteDimensions(count = next.toString())
                                },
                                onIncrement = {
                                    viewModel.updateConcreteDimensions(count = (currentCount + 1).toString())
                                }
                            )
                        }
                    }
                }
            }

            // 3. Concrete Mix Grade (BS 8110)
            item {
                SectionHeader(
                    title = "2. رتبة الخلطة الخرسانية (الكود البريطاني)",
                    icon = Icons.Default.Science,
                    badgeText = state.mixGrade.ratioDescription
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConcreteGrade.entries.forEach { grade ->
                        val isSelected = grade == state.mixGrade
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mix_grade_${grade.code}"),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateConcreteMix(grade)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.updateConcreteMix(grade)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${grade.code} - ${grade.arabicName}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = grade.ratioDescription,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = grade.recommendedUsage,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Wastage & Steel Ratio & Preferred Rebar Diameter d
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring()),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EngineeringInputField(
                                value = state.wastagePercent,
                                onValueChange = { viewModel.updateConcreteWastage(it) },
                                label = "نسبة الهالك والتشوين",
                                unit = "%",
                                modifier = Modifier.weight(1f),
                                testTag = "wastage_input"
                            )
                            EngineeringInputField(
                                value = state.steelRatioKgPerM3,
                                onValueChange = { viewModel.updateConcreteSteelRatio(it) },
                                label = "معدل التسليح التقديري",
                                unit = "كجم/م³",
                                modifier = Modifier.weight(1f),
                                testTag = "steel_ratio_input"
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        EngineeringSliderControl(
                            label = "ضبط سريع لنسبة الهالك",
                            value = state.wastagePercent.toFloatOrNull() ?: 5f,
                            valueRange = 0f..15f,
                            unit = "%",
                            presets = listOf(0f, 5f, 7f, 10f),
                            stepDecimals = 0,
                            onValueChange = { viewModel.updateConcreteWastage(it.roundToInt().toString()) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineeringSliderControl(
                            label = "ضبط سريع لمعدل التسليح",
                            value = state.steelRatioKgPerM3.toFloatOrNull() ?: 140f,
                            valueRange = 50f..220f,
                            unit = "كجم/م³",
                            presets = listOf(85f, 110f, 140f, 160f),
                            stepDecimals = 0,
                            onValueChange = { viewModel.updateConcreteSteelRatio(it.roundToInt().toString()) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "قطر السيخ المستخدم لحساب العدد (د):",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(com.example.domain.RebarDiameter.entries) { d ->
                                val selected = d == state.rebarDiameter
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.updateConcreteRebarDiameter(d)
                                    },
                                    label = {
                                        Text(
                                            text = "D${d.mm} (${d.mm} مم)",
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    modifier = Modifier.testTag("concrete_rebar_d_${d.mm}")
                                )
                            }
                        }
                        Text(
                            text = "المعدل السوداني الشائع: القواعد (85-100)، الأعمدة (140-180)، الأسقف (95-130) كجم/م³",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // 5. Results Section
            item {
                val res = state.result
                if (res != null) {
                    val estSdg = (res.cementTonnes * prices.cementTonPrice) +
                        (res.sandLargeTataTrucks * prices.sandTataTruckPrice) +
                        (res.gravelLargeTataTrucks * prices.gravelTataTruckPrice) +
                        (res.estimatedSteelTonnes * prices.steelTonPrice) +
                        (res.waterDrums * prices.waterDrumPrice)

                    ResultCard(
                        title = "نتائج كميات المواد المطلوبة",
                        subtitle = "حجم الصبة: ${res.volumeWithWasteM3} م³ (صافي: ${res.volumeNetM3} م³ + ${res.wastagePercentage}% هالك)"
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "التكلفة الفورية المقدرة:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${CivilQuantitiesViewModel.formatSdg(estSdg)} SDG",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        // Cement
                        ResultRow(
                            label = "الأسمنت البورتلاندي",
                            value = "${res.cementBags}",
                            unit = "شوال (50 كجم)",
                            highlight = true,
                            commercialHint = "يعادل: ${res.cementTonnes} طن أسمنت (${res.mixGrade.cementBagsPerM3} شوال للمتر المكعب)"
                        )

                        // Sand
                        ResultRow(
                            label = "الرمل الخرساني النظيف",
                            value = "${res.sandM3}",
                            unit = "م³",
                            commercialHint = "سوقياً: ${res.sandSmallTrucks} قلاب صغير (4م³) أو ${res.sandLargeTataTrucks} لوري تاتة (16م³)"
                        )

                        // Gravel / الكنكري
                        ResultRow(
                            label = "الكنكري (الحصى / السن المكسر)",
                            value = "${res.gravelM3}",
                            unit = "م³",
                            commercialHint = "سوقياً: ${res.gravelSmallTrucks} قلاب صغير (4م³) أو ${res.gravelLargeTataTrucks} لوري تاتة (16م³)"
                        )

                        // Water
                        ResultRow(
                            label = "مياه الخلط والمعالجة (Curing)",
                            value = "${res.waterLiters}",
                            unit = "لتر",
                            commercialHint = "سوقياً: ${res.waterDrums} برميل ماء (برميل سعة 200 لتر)"
                        )

                        // Steel by chosen diameter d
                        ResultRow(
                            label = "السيخ (قطر د = ${res.rebarDiameter.mm} مم)",
                            value = "${res.estimatedSteelTonnes}",
                            unit = "طن",
                            highlight = true,
                            commercialHint = "الوزن: ${res.estimatedSteelKg} كجم | العدد: ${res.estimatedSteel12mBarsRounded} سيخة (12م) — دقيق ${res.estimatedSteel12mBars} سيخة"
                        )

                        if (res.steelDiameterOptions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            RebarBreakdownCard(
                                title = "توزيع السيخ حسب القطر (د) — الوزن والعدد",
                                subtitle = "مكافئ نفس الوزن (${res.estimatedSteelKg} كجم) للأقطار التجارية 12 متر",
                                items = res.steelDiameterOptions
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val text = """
                                        تقرير كميات الخرسانة:
                                        العنصر: ${state.elementType.titleAr}
                                        رتبة الخلطة: ${res.mixGrade.code} (${res.mixGrade.ratioDescription})
                                        الحجم الإجمالي: ${res.volumeWithWasteM3} م³
                                        - الأسمنت: ${res.cementBags} شوال (${res.cementTonnes} طن)
                                        - الرمل: ${res.sandM3} م³ (~ ${res.sandLargeTataTrucks} لوري تاتة)
                                        - الكنكري: ${res.gravelM3} م³ (~ ${res.gravelLargeTataTrucks} لوري تاتة)
                                        - المياه: ${res.waterDrums} برميل
                                        - حديد التسليح (قطر ${res.rebarDiameter.mm} مم): ${res.estimatedSteelKg} كجم (${res.estimatedSteelTonnes} طن) | العدد: ${res.estimatedSteel12mBarsRounded} سيخة (12م)
                                    """.trimIndent()
                                    shareText(context, text, "مشاركة كميات الخرسانة")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_concrete_button")
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة")
                            }

                            FilledTonalButton(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_dialog_trigger_button")
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ المشروع")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.syncCalculatorsToBoq(showToast = true)
                                viewModel.navigateTo(AppScreen.BOQ)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.RequestQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحديث وإرسال الكميات إلى المقايسة (BOQ)")
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveProjectDialog(
            category = "خرسانة",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title, location ->
                viewModel.saveCurrentCalculation(title, location, "خرسانة") {
                    showSaveDialog = false
                }
            }
        )
    }
}
