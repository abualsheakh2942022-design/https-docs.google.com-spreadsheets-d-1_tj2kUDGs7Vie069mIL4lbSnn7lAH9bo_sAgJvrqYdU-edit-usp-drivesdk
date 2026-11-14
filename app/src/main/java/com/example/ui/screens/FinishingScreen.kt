package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PlasterFinishingEstimator
import com.example.ui.components.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.CivilQuantitiesViewModel
import com.example.viewmodel.FinishingTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishingScreen(
    viewModel: CivilQuantitiesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.finishingState.collectAsState()
    val prices by viewModel.marketPrices.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "حساب كميات التشطيبات والبياض",
                subtitle = "المحارة، السيراميك، والدهانات والمعجون",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("save_finishing_button")
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
            // Tabs Row
            item {
                TabRow(
                    selectedTabIndex = state.currentTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("finishing_tabs")
                ) {
                    FinishingTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == state.currentTab,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setFinishingTab(tab)
                            },
                            text = {
                                Text(
                                    text = tab.titleAr,
                                    fontWeight = if (tab == state.currentTab) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }

            item {
                AnimatedContent(
                    targetState = state.currentTab,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                    label = "finishingTabTransition"
                ) { currentTab ->
                    when (currentTab) {
                        FinishingTab.PLASTER -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "نوع ومواصفات البياض / المحارة",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                PlasterFinishingEstimator.PlasterType.entries.forEach { pType ->
                                    val isSelected = pType == state.plasterType
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.updatePlaster(
                                                    type = pType,
                                                    thickness = pType.defaultThicknessCm.toString()
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = pType.arName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                            Text(
                                                text = "معدل الأسمنت: ${pType.cementBagsPerM3Sand} شوال لكل م³ رمل",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EngineeringInputField(
                                        value = state.plasterAreaM2,
                                        onValueChange = { viewModel.updatePlaster(area = it) },
                                        label = "مساحة المسطح الكلية",
                                        unit = "م²",
                                        modifier = Modifier.weight(1f),
                                        testTag = "plaster_area_input"
                                    )
                                    EngineeringInputField(
                                        value = state.plasterThicknessCm,
                                        onValueChange = { viewModel.updatePlaster(thickness = it) },
                                        label = "متوسط السمك",
                                        unit = "سم",
                                        modifier = Modifier.weight(1f),
                                        testTag = "plaster_thick_input"
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("1.5", "2.0", "2.5", "3.0").forEach { th ->
                                        SuggestionChip(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.updatePlaster(thickness = th)
                                            },
                                            label = { Text("سمك $th سم", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        val res = state.plasterResult
                        if (res != null) {
                            ResultCard(
                                title = "كميات مواد البياض والمحارة",
                                subtitle = "مسطح: ${res.areaM2} م² بسمك ${res.thicknessCm} سم (${res.plasterTypeAr})"
                            ) {
                                FinishingCostChip(
                                    label = "التكلفة التقديرية للبياض:",
                                    amountSdg = res.areaM2 * prices.plasterPerM2Price
                                )
                                ResultRow(
                                    label = "الأسمنت المطلوب",
                                    value = "${res.cementBags}",
                                    unit = "شوال (50 كجم)",
                                    highlight = true,
                                    commercialHint = "يعادل: ${((res.cementBags / 20.0) * 100).toInt() / 100.0} طن أسمنت"
                                )
                                ResultRow(
                                    label = "الرمل النظيف للمحارة",
                                    value = "${res.sandM3}",
                                    unit = "م³",
                                    commercialHint = "سوقياً: ${res.sandSmallTrucks} قلاب رمل صغير (4م³)"
                                )
                                ResultRow(
                                    label = "مياه الرش والمعالجة",
                                    value = "${res.waterDrums}",
                                    unit = "برميل",
                                    commercialHint = "يجب معالجة البياض بالرش بالماء مرتين يومياً لمدة 3 أيام"
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                FinishingActionRow(
                                    onSave = { showSaveDialog = true },
                                    onSendToBoq = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.syncCalculatorsToBoq(showToast = true)
                                        viewModel.navigateTo(AppScreen.BOQ)
                                    }
                                )
                            }
                        }
                            }
                        }

                FinishingTab.TILES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "بيانات السيراميك والبورسلين",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        listOf("30×30 سم", "30", "30", "11"),
                                        listOf("40×40 سم", "40", "40", "6"),
                                        listOf("60×60 سم", "60", "60", "4"),
                                        listOf("80×80 سم", "80", "80", "2"),
                                        listOf("60×120 سم", "120", "60", "2")
                                    ).forEach { spec ->
                                        SuggestionChip(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.updateTiles(length = spec[1], width = spec[2], box = spec[3])
                                            },
                                            label = { Text(spec[0], fontSize = 11.sp) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                EngineeringInputField(
                                    value = state.tilesAreaM2,
                                    onValueChange = { viewModel.updateTiles(area = it) },
                                    label = "مساحة الأرضيات أو الحوائط",
                                    unit = "م²",
                                    testTag = "tiles_area_input"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EngineeringInputField(
                                        value = state.tileLengthCm,
                                        onValueChange = { viewModel.updateTiles(length = it) },
                                        label = "طول البلاطة",
                                        unit = "سم",
                                        modifier = Modifier.weight(1f),
                                        testTag = "tile_l_input"
                                    )
                                    EngineeringInputField(
                                        value = state.tileWidthCm,
                                        onValueChange = { viewModel.updateTiles(width = it) },
                                        label = "عرض البلاطة",
                                        unit = "سم",
                                        modifier = Modifier.weight(1f),
                                        testTag = "tile_w_input"
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                EngineeringInputField(
                                    value = state.tilesPerBox,
                                    onValueChange = { viewModel.updateTiles(box = it) },
                                    label = "عدد البلاطات في الكرتونة",
                                    unit = "بلاطة/كرتونة",
                                    testTag = "tiles_box_input"
                                )
                            }
                        }

                        val res = state.tilesResult
                        if (res != null) {
                            ResultCard(
                                title = "نتائج كميات السيراميك والمونة",
                                subtitle = "مساحة المسطح: ${res.floorAreaM2} م² (مقاس ${res.tileLengthCm.toInt()}×${res.tileWidthCm.toInt()} سم)"
                            ) {
                                FinishingCostChip(
                                    label = "التكلفة التقديرية للسيراميك:",
                                    amountSdg = res.floorAreaM2 * prices.tilesPerM2Price
                                )
                                ResultRow(
                                    label = "عدد كراتين السيراميك",
                                    value = "${res.totalBoxes}",
                                    unit = "كرتونة",
                                    highlight = true,
                                    commercialHint = "إجمالي عدد البلاطات: ${res.totalTilesCount} بلاطة (شامل 8% هالك غلايق وقص)"
                                )
                                ResultRow(
                                    label = "أسمنت مونة الفرش واللصق",
                                    value = "${res.cementBagsForBedding}",
                                    unit = "شوال (50 كجم)",
                                    commercialHint = "بمعدل شوال لكل 5 م² مسطح"
                                )
                                ResultRow(
                                    label = "رمل مونة الفرش",
                                    value = "${res.sandM3ForBedding}",
                                    unit = "م³",
                                    commercialHint = "رمل نظيف ناعم مفلفل سمك 3-5 سم"
                                )
                                ResultRow(
                                    label = "بودرة الترويبة (Grout)",
                                    value = "${res.tileGroutKg}",
                                    unit = "كجم",
                                    commercialHint = "لملء فواصل وعراميس السيراميك"
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                FinishingActionRow(
                                    onSave = { showSaveDialog = true },
                                    onSendToBoq = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.syncCalculatorsToBoq(showToast = true)
                                        viewModel.navigateTo(AppScreen.BOQ)
                                    }
                                )
                            }
                        }
                            }
                        }

                FinishingTab.PAINT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "بيانات الدهانات والمعجون",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                EngineeringInputField(
                                    value = state.paintAreaM2,
                                    onValueChange = { viewModel.updatePaint(area = it) },
                                    label = "مساحة الجدران والأسقف للدهان",
                                    unit = "م²",
                                    testTag = "paint_area_input"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                EngineeringInputField(
                                    value = state.paintCoatsCount,
                                    onValueChange = { viewModel.updatePaint(coats = it) },
                                    label = "عدد أوجه الدهان النهائي",
                                    unit = "أوجه (وشين أو 3)",
                                    testTag = "paint_coats_input"
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val coatsInt = (state.paintCoatsCount.toIntOrNull() ?: 2).coerceIn(1, 5)
                                QuickStepperRow(
                                    label = "ضبط سريع لعدد الأوجه",
                                    valueText = "$coatsInt",
                                    unit = "أوجه",
                                    onDecrement = {
                                        viewModel.updatePaint(coats = (coatsInt - 1).coerceAtLeast(1).toString())
                                    },
                                    onIncrement = {
                                        viewModel.updatePaint(coats = (coatsInt + 1).coerceAtMost(5).toString())
                                    }
                                )
                            }
                        }

                        val res = state.paintResult
                        if (res != null) {
                            ResultCard(
                                title = "كميات المعجون والدهانات المطلوبة",
                                subtitle = "مساحة المسطح: ${res.surfaceAreaM2} م² (${res.coatsCount} أوجه دهان)"
                            ) {
                                FinishingCostChip(
                                    label = "التكلفة التقديرية للدهان:",
                                    amountSdg = res.surfaceAreaM2 * prices.paintPerM2Price
                                )
                                ResultRow(
                                    label = "سيلر مائي تحضيري (Primer)",
                                    value = "${res.primerBuckets.toInt()}",
                                    unit = "بستلة كبيرة (18 لتر)",
                                    commercialHint = "يعادل: ${res.primerLiters} لتر لسد المسام وتثبيت المحارة"
                                )
                                ResultRow(
                                    label = "معجون سحب داخلي (Putty)",
                                    value = "${res.puttyBags20Kg.toInt()}",
                                    unit = "شيكارة (20 كجم)",
                                    highlight = true,
                                    commercialHint = "تكفي لعمل سكينتين معجون لتنعيم السطح"
                                )
                                ResultRow(
                                    label = "دهان بلاستيك/حريري نهائي",
                                    value = "${res.paintBuckets18L.toInt()}",
                                    unit = "بستلة (18 لتر)",
                                    highlight = true,
                                    commercialHint = "إجمالي الدهان: ${res.paintLiters} لتر لتغطية ${res.coatsCount} أوجه"
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                FinishingActionRow(
                                    onSave = { showSaveDialog = true },
                                    onSendToBoq = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.syncCalculatorsToBoq(showToast = true)
                                        viewModel.navigateTo(AppScreen.BOQ)
                                    }
                                )
                            }
                        }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveProjectDialog(
            category = "تشطيبات",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title, location ->
                viewModel.saveCurrentCalculation(title, location, "تشطيبات") {
                    showSaveDialog = false
                }
            }
        )
    }
}

@Composable
private fun FinishingCostChip(label: String, amountSdg: Double) {
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
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${CivilQuantitiesViewModel.formatSdg(amountSdg)} SDG",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun FinishingActionRow(
    onSave: () -> Unit,
    onSendToBoq: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("حفظ المشروع")
        }
        OutlinedButton(
            onClick = onSendToBoq,
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = Icons.Default.RequestQuote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("للمقايسة BOQ")
        }
    }
}
