package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.MasonryEstimator
import com.example.ui.components.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.CivilQuantitiesViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasonryScreen(
    viewModel: CivilQuantitiesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.masonryState.collectAsState()
    val prices by viewModel.marketPrices.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "حساب كميات أعمال المباني والطوب",
                subtitle = "الطوب الأحمر البلدي السوداني والبلك الأسمنتي",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("save_masonry_button")
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
            // Live Wall Elevation Preview
            item {
                LiveMasonryWallDiagramCard(
                    wallLengthM = state.wallLengthM.toDoubleOrNull() ?: 12.0,
                    wallHeightM = state.wallHeightM.toDoubleOrNull() ?: 3.0,
                    openingsAreaM2 = state.openingsAreaM2.toDoubleOrNull() ?: 4.5,
                    netAreaM2 = state.result?.wallAreaM2 ?: 0.0
                )
            }

            // 1. Brick Type Selection
            item {
                SectionHeader(
                    title = "1. نوع الطوب أو البلك المستخدم",
                    icon = Icons.Default.ViewAgenda
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MasonryEstimator.BrickType.entries.take(4).forEach { type ->
                        val isSelected = type == state.brickType
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("brick_type_${type.name}"),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateMasonry(brickType = type)
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
                                        viewModel.updateMasonry(brickType = type)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = type.arName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = "المقاس: ${type.lengthCm} × ${type.widthCm} × ${type.heightCm} سم",
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

            // 2. Wall Thickness Selection
            item {
                SectionHeader(
                    title = "2. سمك وطبيعة الجدار",
                    icon = Icons.Default.LineWeight
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val halfSelected = state.wallThickness == MasonryEstimator.WallThickness.HALF_BRICK
                    val oneSelected = state.wallThickness == MasonryEstimator.WallThickness.ONE_BRICK

                    FilterChip(
                        selected = halfSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.updateMasonry(wallThickness = MasonryEstimator.WallThickness.HALF_BRICK)
                        },
                        label = { Text("نصف طوبة (10 سم)") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = oneSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.updateMasonry(wallThickness = MasonryEstimator.WallThickness.ONE_BRICK)
                        },
                        label = { Text("طوبة كاملة (20 سم)") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // 3. Wall Dimensions Input
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
                            text = "أبعاد الحائط والفتحات",
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
                                value = state.wallLengthM,
                                onValueChange = { viewModel.updateMasonry(length = it) },
                                label = "طول الجدار الكلي",
                                unit = "متر",
                                modifier = Modifier.weight(1f),
                                testTag = "wall_len_input"
                            )
                            EngineeringInputField(
                                value = state.wallHeightM,
                                onValueChange = { viewModel.updateMasonry(height = it) },
                                label = "ارتفاع الجدار",
                                unit = "متر",
                                modifier = Modifier.weight(1f),
                                testTag = "wall_height_input"
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("2.8", "3.0", "3.2", "3.5", "4.0").forEach { h ->
                                SuggestionChip(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.updateMasonry(height = h)
                                    },
                                    label = { Text("ارتفاع $h م", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EngineeringInputField(
                                value = state.openingsAreaM2,
                                onValueChange = { viewModel.updateMasonry(openings = it) },
                                label = "مساحة الفتحات (أبواب وشبابيك)",
                                unit = "م²",
                                modifier = Modifier.weight(1f),
                                testTag = "openings_input"
                            )
                            EngineeringInputField(
                                value = state.wastePercentage,
                                onValueChange = { viewModel.updateMasonry(waste = it) },
                                label = "هالك الكسر والتشوين",
                                unit = "%",
                                modifier = Modifier.weight(1f),
                                testTag = "masonry_waste_input"
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        EngineeringSliderControl(
                            label = "ضبط سريع لهالك الكسر",
                            value = state.wastePercentage.toFloatOrNull() ?: 7f,
                            valueRange = 0f..15f,
                            unit = "%",
                            presets = listOf(5f, 7f, 10f, 12f),
                            stepDecimals = 0,
                            onValueChange = { viewModel.updateMasonry(waste = it.roundToInt().toString()) }
                        )
                    }
                }
            }

            // 4. Results Card
            item {
                val res = state.result
                if (res != null) {
                    val estCostSdg = (res.totalBricksThousand * prices.redBricksPerThousand) +
                        ((res.cementBagsForMortar / 20.0) * prices.cementTonPrice) +
                        (res.sandSmallTrucks * prices.sandSmallTruckPrice)

                    ResultCard(
                        title = "نتائج كميات المباني والمونة",
                        subtitle = "مساحة الجدار الصافية: ${res.wallAreaM2} م² | الحجم: ${res.wallVolumeM3} م³"
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
                                    text = "التكلفة التقديرية للمباني:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${CivilQuantitiesViewModel.formatSdg(estCostSdg)} SDG",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        ResultRow(
                            label = "عدد الطوب المطلوب (شامل الهالك)",
                            value = "${res.totalBricksCount}",
                            unit = "طوبة",
                            highlight = true,
                            commercialHint = "سوقياً في السودان: ${res.totalBricksThousand} ألف طوبة (بالألف طوبة)"
                        )

                        ResultRow(
                            label = "الأسمنت لمونة البناء",
                            value = "${res.cementBagsForMortar}",
                            unit = "شوال (50 كجم)",
                            highlight = true,
                            commercialHint = "يعادل: ${((res.cementBagsForMortar / 20.0) * 100).toInt() / 100.0} طن أسمنت"
                        )

                        ResultRow(
                            label = "الرمل لمونة البناء",
                            value = "${res.sandM3ForMortar}",
                            unit = "م³",
                            commercialHint = "سوقياً: ${res.sandSmallTrucks} قلاب رمل صغير (4م³)"
                        )

                        ResultRow(
                            label = "مياه خلط المونة ورش الطوب",
                            value = "${res.waterDrums}",
                            unit = "برميل",
                            commercialHint = "يجب رش الطوب الأحمر جيداً بالماء قبل البناء لمنع امتصاص ماء المونة"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val text = """
                                        تقرير كميات أعمال المباني:
                                        نوع الطوب: ${res.brickType}
                                        المساحة الصافية: ${res.wallAreaM2} م²
                                        - إجمالي الطوب: ${res.totalBricksCount} طوبة (${res.totalBricksThousand} ألف طوبة)
                                        - أسمنت المونة: ${res.cementBagsForMortar} شوال
                                        - رمل المونة: ${res.sandM3ForMortar} م³ (~ ${res.sandSmallTrucks} قلاب صغير)
                                        - مياه الرش والخلط: ${res.waterDrums} برميل
                                    """.trimIndent()
                                    shareText(context, text, "مشاركة كميات المباني")
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

            // Technical Note
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "إرشادات هندسية للمباني في السودان (SSMO):",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. غمر الطوب الأحمر بالماء: يجب رش الطوب قبل البناء بساعة لتشبيعه حتى لا يمتص ماء خلط المونة الإسمنتية.\n2. سمك عراميس المونة: 10 إلى 15 مم.\n3. الربط الهندسي: عدم استمرار الحلول الرأسية واستخدام كانات ربط مع الأعمدة الخرسانية كل 3 أو 4 مداميك.\n4. المعالجة: رش الجدران بالماء لمدة 3 أيام متتالية صباحاً ومساءً.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 19.sp
                            )
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveProjectDialog(
            category = "مباني",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title, location ->
                viewModel.saveCurrentCalculation(title, location, "مباني") {
                    showSaveDialog = false
                }
            }
        )
    }
}
