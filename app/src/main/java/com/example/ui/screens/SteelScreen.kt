package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import com.example.domain.RebarDiameter
import com.example.ui.components.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.CivilQuantitiesViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteelScreen(
    viewModel: CivilQuantitiesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.steelState.collectAsState()
    val prices by viewModel.marketPrices.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "حساب كميات حديد التسليح",
                subtitle = "BS 4449 والأسياخ التجارية 12 متر في السودان",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("save_steel_button")
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
            // Mode Switch (Detailed vs Empirical)
            item {
                TabRow(
                    selectedTabIndex = if (state.isDetailedMode) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("steel_mode_tabs")
                ) {
                    Tab(
                        selected = state.isDetailedMode,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleSteelMode(true)
                        },
                        text = {
                            Text(
                                "حساب تفصيلي (بالقطر والعدد والكانات)",
                                fontWeight = if (state.isDetailedMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = !state.isDetailedMode,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleSteelMode(false)
                        },
                        text = {
                            Text(
                                "تقدير سريع بالنسبة (كجم/م³)",
                                fontWeight = if (!state.isDetailedMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            item {
                AnimatedContent(
                    targetState = state.isDetailedMode,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    },
                    label = "steelModeTransition"
                ) { isDetailed ->
                    if (isDetailed) {
                        val barsInt = (state.numberOfBars.toIntOrNull() ?: 8).coerceAtLeast(1)
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Live Section Diagram
                            LiveSteelSectionDiagramCard(
                                diameter = state.diameter,
                                numberOfBars = barsInt,
                                memberWidthM = state.memberWidthM.toDoubleOrNull() ?: 0.25,
                                memberDepthM = state.memberDepthM.toDoubleOrNull() ?: 0.50,
                                includeStirrups = state.isIncludeStirrups,
                                stirrupDiameter = state.stirrupDiameter,
                                stirrupsCount = state.detailedResult?.stirrupsCount ?: 0
                            )

                            // Diameter Selector
                            Column {
                                SectionHeader(
                                    title = "1. اختيار قطر حديد التسليح القياسي",
                                    icon = Icons.Default.FiberManualRecord,
                                    badgeText = "وزن المتر = ${state.diameter.weightPerMeterKg} كجم"
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(RebarDiameter.entries) { rebar ->
                                        val isSelected = rebar == state.diameter
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.updateSteelDetailed(diameter = rebar)
                                            },
                                            label = {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = "${rebar.mm} مم",
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    Text(
                                                        text = "${rebar.weightPer12mBarKg} كجم/سيخة",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                                                    )
                                                }
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.testTag("rebar_chip_${rebar.mm}")
                                        )
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = "استخدام قطر ${state.diameter.mm} مم الشائع: ${state.diameter.commonUseSudan} (الطن يحتوي على ~ ${state.diameter.barsPerTon} سيخة بطول 12م)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            // Main Rebar Parameters Input
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
                                        text = "بيانات حديد التسليح الرئيسي",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    QuickStepperRow(
                                        label = "تعديل سريع لعدد الأسياخ",
                                        valueText = "$barsInt",
                                        unit = "سيخة Ø${state.diameter.mm}",
                                        onDecrement = {
                                            viewModel.updateSteelDetailed(numberOfBars = (barsInt - 1).coerceAtLeast(1).toString())
                                        },
                                        onIncrement = {
                                            viewModel.updateSteelDetailed(numberOfBars = (barsInt + 1).toString())
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        EngineeringInputField(
                                            value = state.numberOfBars,
                                            onValueChange = { viewModel.updateSteelDetailed(numberOfBars = it) },
                                            label = "عدد الأسياخ",
                                            unit = "سيخة",
                                            modifier = Modifier.weight(1f),
                                            testTag = "num_bars_input"
                                        )
                                        EngineeringInputField(
                                            value = state.barLengthM,
                                            onValueChange = { viewModel.updateSteelDetailed(barLengthM = it) },
                                            label = "طول السيخ الصافي",
                                            unit = "متر",
                                            modifier = Modifier.weight(1f),
                                            testTag = "bar_length_input"
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        EngineeringInputField(
                                            value = state.lapLengthM,
                                            onValueChange = { viewModel.updateSteelDetailed(lapLengthM = it) },
                                            label = "طول التراكب / الوصلة (Lap)",
                                            unit = "متر",
                                            modifier = Modifier.weight(1f),
                                            testTag = "lap_length_input"
                                        )
                                        EngineeringInputField(
                                            value = state.wastePercentage,
                                            onValueChange = { viewModel.updateSteelDetailed(wastePercentage = it) },
                                            label = "هالك التقطيع",
                                            unit = "%",
                                            modifier = Modifier.weight(1f),
                                            testTag = "steel_waste_input"
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    EngineeringSliderControl(
                                        label = "ضبط سريع لهالك التقطيع",
                                        value = state.wastePercentage.toFloatOrNull() ?: 5f,
                                        valueRange = 0f..15f,
                                        unit = "%",
                                        presets = listOf(0f, 5f, 7f, 10f),
                                        stepDecimals = 0,
                                        onValueChange = { viewModel.updateSteelDetailed(wastePercentage = it.roundToInt().toString()) }
                                    )
                                    Text(
                                        text = "ملاحظة BS 8110: طول التراكب الموصى به = 45 إلى 50 مرة قطر السيخ (مثلاً 50 × ${state.diameter.mm} مم = ${(state.diameter.mm * 50) / 1000.0} متر)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            // Stirrups Section (الكانات)
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
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "حساب الكانات (Stirrups / Ties)",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            Text(
                                                text = "حساب محيط الكانة والأقفال (10d) وعدد الكانات وسلك الرباط",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                        Switch(
                                            checked = state.isIncludeStirrups,
                                            onCheckedChange = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.updateSteelDetailed(isIncludeStirrups = it)
                                            },
                                            modifier = Modifier.testTag("stirrups_switch")
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = state.isIncludeStirrups,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                            Text(
                                                text = "قطر سيخ الكانات (د):",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(listOf(RebarDiameter.D8, RebarDiameter.D10, RebarDiameter.D12)) { d ->
                                                    val isSel = d == state.stirrupDiameter
                                                    FilterChip(
                                                        selected = isSel,
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            viewModel.updateSteelDetailed(stirrupDiameter = d)
                                                        },
                                                        label = {
                                                            Text(
                                                                text = "D${d.mm} (${d.mm} مم)",
                                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        },
                                                        modifier = Modifier.testTag("stirrup_diameter_${d.mm}")
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                EngineeringInputField(
                                                    value = state.memberLengthM,
                                                    onValueChange = { viewModel.updateSteelDetailed(memberLengthM = it) },
                                                    label = "طول العمود / الكمرة",
                                                    unit = "متر",
                                                    modifier = Modifier.weight(1f),
                                                    testTag = "member_len_input"
                                                )
                                                EngineeringInputField(
                                                    value = state.stirrupSpacingCm,
                                                    onValueChange = { viewModel.updateSteelDetailed(stirrupSpacingCm = it) },
                                                    label = "مسافة التوزيع (Spacing)",
                                                    unit = "سم",
                                                    modifier = Modifier.weight(1f),
                                                    testTag = "stirrup_spacing_input"
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(top = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                listOf("10", "12.5", "15", "20", "25").forEach { sp ->
                                                    SuggestionChip(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            viewModel.updateSteelDetailed(stirrupSpacingCm = sp)
                                                        },
                                                        label = { Text("كل $sp سم", fontSize = 11.sp) }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                EngineeringInputField(
                                                    value = state.memberWidthM,
                                                    onValueChange = { viewModel.updateSteelDetailed(memberWidthM = it) },
                                                    label = "عرض القطاع الخرساني",
                                                    unit = "متر",
                                                    modifier = Modifier.weight(1f),
                                                    testTag = "member_w_input"
                                                )
                                                EngineeringInputField(
                                                    value = state.memberDepthM,
                                                    onValueChange = { viewModel.updateSteelDetailed(memberDepthM = it) },
                                                    label = "عمق / ارتفاع القطاع",
                                                    unit = "متر",
                                                    modifier = Modifier.weight(1f),
                                                    testTag = "member_d_input"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Results Card Detailed
                            val res = state.detailedResult
                            if (res != null) {
                                val steelCostSdg = res.totalWeightTonnes * prices.steelTonPrice
                                ResultCard(
                                    title = "نتائج كميات حديد التسليح",
                                    subtitle = "تفصيل السيخ بالوزن والعدد حسب القطر (د) — سيخ تجاري 12م"
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
                                                text = "التكلفة التقديرية للحديد:",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "${CivilQuantitiesViewModel.formatSdg(steelCostSdg)} SDG",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }

                                    ResultRow(
                                        label = "الوزن الإجمالي الكلي (شامل الهالك)",
                                        value = "${res.totalWeightTonnes}",
                                        unit = "طن",
                                        highlight = true,
                                        commercialHint = "يعادل: ${res.totalWeightKg} كجم (بالهالك ${res.wastePercentage}%)"
                                    )

                                    ResultRow(
                                        label = "السيخ الرئيسي (قطر د = ${res.diameter.mm} مم)",
                                        value = "${res.main12mBarsRounded}",
                                        unit = "سيخة 12م (${res.mainWeightKg} كجم)",
                                        highlight = true,
                                        commercialHint = "دقيق: ${res.numberOf12mBars} سيخة | ${res.numberOfBars} سيخة مقصوصة | أطوال: ${res.totalLengthMeters} م"
                                    )

                                    if (state.isIncludeStirrups && res.stirrupsCount > 0) {
                                        ResultRow(
                                            label = "الكانات (قطر د = ${res.stirrupsDiameter.mm} مم)",
                                            value = "${res.stirrupsCount}",
                                            unit = "كانة (${res.stirrupsWeightKg} كجم)",
                                            commercialHint = "تحتاج: ${res.stirrups12mBarsRounded} سيخة 12م (دقيق ${res.stirrups12mBars}) | محيط الكانة: ${res.stirrupLengthMeters} م"
                                        )
                                    }

                                    ResultRow(
                                        label = "سلك الرباط المقدر (Binding Wire)",
                                        value = "${res.bindingWireKg}",
                                        unit = "كجم",
                                        commercialHint = "بمعدل ~ 7 كجم لكل طن حديد تسليح"
                                    )

                                    if (res.diameterBreakdown.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        RebarBreakdownCard(
                                            title = "ملخص السيخ حسب القطر (د)",
                                            items = res.diameterBreakdown
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val breakdownText = res.diameterBreakdown.joinToString("\n") { row ->
                                                    "- ${row.roleLabelAr} Ø${row.diameter.mm} مم: الوزن ${row.weightKg} كجم (${row.weightTonnes} طن) | العدد ${row.bars12mRounded} سيخة (12م)"
                                                }
                                                val text = """
                                                    تقرير حديد التسليح حسب القطر (د):
                                                    الوزن الإجمالي: ${res.totalWeightTonnes} طن (${res.totalWeightKg} كجم)
                                                    $breakdownText
                                                    ${if (state.isIncludeStirrups) "عدد الكانات: ${res.stirrupsCount} كانة" else ""}
                                                    سلك الرباط: ${res.bindingWireKg} كجم
                                                """.trimIndent()
                                                shareText(context, text, "مشاركة كميات حديد التسليح")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("مشاركة")
                                        }

                                        FilledTonalButton(
                                            onClick = { showSaveDialog = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("حفظ")
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
                    } else {
                        // Empirical Mode
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "التقدير التقريبي لحديد التسليح بالمتر المكعب خرسانة",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    EngineeringInputField(
                                        value = state.empiricalConcreteVolumeM3,
                                        onValueChange = { viewModel.updateEmpiricalSteel(it, state.empiricalSteelRatioKgPerM3) },
                                        label = "حجم الخرسانة المسلحة الإجمالي",
                                        unit = "م³",
                                        testTag = "emp_vol_input"
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    EngineeringInputField(
                                        value = state.empiricalSteelRatioKgPerM3,
                                        onValueChange = { viewModel.updateEmpiricalSteel(state.empiricalConcreteVolumeM3, it) },
                                        label = "معدل التسليح التقديري",
                                        unit = "كجم/م³",
                                        testTag = "emp_ratio_input"
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            "قواعد 90" to "90",
                                            "أسقف 110" to "110",
                                            "كمرات 125" to "125",
                                            "أعمدة 160" to "160"
                                        ).forEach { (label, rate) ->
                                            SuggestionChip(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    viewModel.updateEmpiricalSteel(state.empiricalConcreteVolumeM3, rate)
                                                },
                                                label = { Text(label, fontSize = 11.sp) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "اختيار قطر السيخ لحساب العدد (د):",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(RebarDiameter.entries) { d ->
                                            val selected = d == state.empiricalDiameter
                                            FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    viewModel.updateEmpiricalSteelDiameter(d)
                                                },
                                                label = {
                                                    Text(
                                                        text = "D${d.mm} (${d.mm} مم)",
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                modifier = Modifier.testTag("emp_rebar_d_${d.mm}")
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "معدلات تقريبية شائعة:\n• القواعد: 80 - 100 كجم/م³\n• الأعمدة: 140 - 180 كجم/م³\n• الكمرات والجسور: 110 - 140 كجم/م³\n• الأسقف المصمتة: 90 - 120 كجم/م³\n• الأسقف الفلات: 120 - 150 كجم/م³",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 18.sp
                                            ),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }

                            val primaryEmp = state.empiricalPrimaryBreakdown
                            val empCostSdg = state.empiricalTotalTonnes * prices.steelTonPrice
                            ResultCard(
                                title = "الكميات التقديرية لحديد التسليح",
                                subtitle = "لحجم صبة ${state.empiricalConcreteVolumeM3} م³ | قطر د = ${state.empiricalDiameter.mm} مم"
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
                                            text = "التكلفة التقديرية للحديد:",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "${CivilQuantitiesViewModel.formatSdg(empCostSdg)} SDG",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }

                                ResultRow(
                                    label = "إجمالي وزن حديد التسليح",
                                    value = "${state.empiricalTotalTonnes}",
                                    unit = "طن",
                                    highlight = true,
                                    commercialHint = "يعادل: ${state.empiricalTotalKg} كجم (شامل 5% هالك)"
                                )

                                if (primaryEmp != null) {
                                    ResultRow(
                                        label = "عدد الأسياخ (قطر د = ${primaryEmp.diameter.mm} مم)",
                                        value = "${primaryEmp.bars12mRounded}",
                                        unit = "سيخة (12م)",
                                        highlight = true,
                                        commercialHint = "دقيق: ${primaryEmp.bars12mExact} سيخة × ${primaryEmp.diameter.weightPer12mBarKg} كجم/سيخة"
                                    )
                                }

                                if (state.empiricalDiameterOptions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    RebarBreakdownCard(
                                        title = "توزيع السيخ حسب القطر (د) — الوزن والعدد",
                                        subtitle = "مكافئ نفس الوزن (${state.empiricalTotalKg} كجم) لجميع الأقطار القياسية",
                                        items = state.empiricalDiameterOptions
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val text = buildString {
                                                appendLine("تقدير حديد التسليح (النسبة القياسية):")
                                                appendLine("حجم الخرسانة: ${state.empiricalConcreteVolumeM3} م³")
                                                appendLine("الوزن الإجمالي: ${state.empiricalTotalKg} كجم (${state.empiricalTotalTonnes} طن)")
                                                if (primaryEmp != null) {
                                                    appendLine("القطر المختار Ø${primaryEmp.diameter.mm} مم: ${primaryEmp.bars12mRounded} سيخة (12م)")
                                                }
                                            }
                                            shareText(context, text, "مشاركة تقدير حديد التسليح")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مشاركة")
                                    }

                                    FilledTonalButton(
                                        onClick = { showSaveDialog = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حفظ")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Reference Rebar Table
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "جدول أوزان وأسياخ حديد التسليح (BS 4449)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        RebarDiameter.entries.forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "قطر ${r.mm} مم (D${r.mm})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${r.weightPerMeterKg} كجم/م | ${r.weightPer12mBarKg} كجم/سيخة | ${r.barsPerTon} سيخة/طن",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveProjectDialog(
            category = "تسليح",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title, location ->
                viewModel.saveCurrentCalculation(title, location, "تسليح") {
                    showSaveDialog = false
                }
            }
        )
    }
}
