package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.BoqItem
import com.example.ui.components.*
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.CivilQuantitiesViewModel
import com.example.viewmodel.MarketPricesSdg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoqScreen(
    viewModel: CivilQuantitiesViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.boqItems.collectAsState()
    val prices by viewModel.marketPrices.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showPriceEditor by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BoqItem?>(null) }
    var selectedCategory by remember { mutableStateOf("الكل") }

    val categories = remember(items) { listOf("الكل") + items.map { it.categoryAr }.distinct() }
    val filteredItems = remember(items, selectedCategory) {
        if (selectedCategory == "الكل") items else items.filter { it.categoryAr == selectedCategory }
    }
    val totalCost = items.sumOf { it.totalPriceSdg }
    val filteredCost = filteredItems.sumOf { it.totalPriceSdg }
    val animatedTotalCost by animateFloatAsState(
        targetValue = totalCost.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "animatedBoqTotal"
    )

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "جدول الكميات والتسعير (BOQ)",
                subtitle = "تقدير التكاليف بالجنيه السوداني (SDG)",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = { showPriceEditor = true },
                        modifier = Modifier.testTag("edit_prices_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PriceChange,
                            contentDescription = "أسعار السوق",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("save_boq_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "حفظ المقايسة",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddItemDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("add_boq_item_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة بند مخصص")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Total Cost Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("total_cost_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "إجمالي التكلفة التقديرية للمشروع",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = CivilQuantitiesViewModel.formatSdg(animatedTotalCost.toDouble()),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "جنيه سوداني (SDG)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.syncCalculatorsToBoq(showToast = true)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحديث من الحاسبات", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = { showPriceEditor = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("أسعار السوق", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Export, Share & Work Plan Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val builder = java.lang.StringBuilder()
                                builder.append("📊 مقايسة وجدول كميات هندسي (BOQ)\n")
                                builder.append("وفق الكود البريطاني والمواصفات السودانية\n\n")
                                items.forEachIndexed { i, itm ->
                                    builder.append("${i + 1}. [${itm.categoryAr}] ${itm.itemDescriptionAr}\n")
                                    builder.append("   الكمية: ${itm.quantity} ${itm.unitAr} × ${CivilQuantitiesViewModel.formatSdg(itm.unitPriceSdg)} = ${CivilQuantitiesViewModel.formatSdg(itm.totalPriceSdg)} SDG\n")
                                }
                                builder.append("\n💰 الإجمالي الكلي: ${CivilQuantitiesViewModel.formatSdg(totalCost)} جنيه سوداني")
                                shareText(context, builder.toString(), "مشاركة مقايسة الكميات")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة التقرير")
                        }

                        FilledTonalButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ المقايسة")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.createWorkPlanFromCurrentCalculations(
                                title = "خطة عمل المقايسة الحالية",
                                location = "السودان",
                                pace = com.example.domain.SchedulePace.STANDARD,
                                startDateMillis = System.currentTimeMillis()
                            ) {
                                viewModel.navigateTo(com.example.viewmodel.AppScreen.WORK_PLAN)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("boq_create_work_plan_btn")
                    ) {
                        Icon(imageVector = Icons.Default.ViewTimeline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إنشاء خطة عمل وخارطة زمنية من المقايسة")
                    }
                }
            }

            // BOQ Items Header & Category Filter Chips
            item {
                SectionHeader(
                    title = "بنود الكميات والأسعار",
                    icon = Icons.Default.ListAlt,
                    badgeText = if (selectedCategory == "الكل") "${items.size} بند"
                    else "${filteredItems.size} بند (${CivilQuantitiesViewModel.formatSdg(filteredCost)} SDG)"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSel = category == selectedCategory
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedCategory = category
                            },
                            label = {
                                Text(
                                    text = category,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }

            // BOQ Item Cards
            if (filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد بنود حالياً في هذا التصنيف. قم بإضافة بنود يدوية أو تحديث المقايسة من الحاسبات.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(spring())
                            .clip(RoundedCornerShape(12.dp))
                            .tactileClickable { editingItem = item }
                            .testTag("boq_item_${item.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = item.categoryAr,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { editingItem = item },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل البند",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.deleteBoqItem(item.id)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "حذف البند",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.itemDescriptionAr,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val step = if (item.quantity >= 10.0) 1.0 else 0.5
                                    FilledTonalIconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.adjustBoqItemQuantity(item.id, -step)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "إنقاص الكمية", modifier = Modifier.size(15.dp))
                                    }
                                    Text(
                                        text = "${item.quantity} ${item.unitAr} × ${CivilQuantitiesViewModel.formatSdg(item.unitPriceSdg)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    FilledTonalIconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.adjustBoqItemQuantity(item.id, step)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "زيادة الكمية", modifier = Modifier.size(15.dp))
                                    }
                                }
                                Text(
                                    text = "${CivilQuantitiesViewModel.formatSdg(item.totalPriceSdg)} SDG",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Market Prices Editor BottomSheet / Dialog
    if (showPriceEditor) {
        var cementPrice by remember { mutableStateOf(prices.cementTonPrice.toInt().toString()) }
        var steelPrice by remember { mutableStateOf(prices.steelTonPrice.toInt().toString()) }
        var sandTataPrice by remember { mutableStateOf(prices.sandTataTruckPrice.toInt().toString()) }
        var sandSmallPrice by remember { mutableStateOf(prices.sandSmallTruckPrice.toInt().toString()) }
        var gravelTataPrice by remember { mutableStateOf(prices.gravelTataTruckPrice.toInt().toString()) }
        var redBricksPrice by remember { mutableStateOf(prices.redBricksPerThousand.toInt().toString()) }
        var waterDrumPrice by remember { mutableStateOf(prices.waterDrumPrice.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { showPriceEditor = false },
            title = {
                Text("تحديث أسعار مواد البناء بالسودان (SDG)", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        EngineeringInputField(
                            value = cementPrice,
                            onValueChange = { cementPrice = it },
                            label = "سعر طن الأسمنت (20 شوال)",
                            unit = "SDG"
                        )
                    }
                    item {
                        EngineeringInputField(
                            value = steelPrice,
                            onValueChange = { steelPrice = it },
                            label = "سعر طن حديد التسليح",
                            unit = "SDG"
                        )
                    }
                    item {
                        EngineeringInputField(
                            value = sandTataPrice,
                            onValueChange = { sandTataPrice = it },
                            label = "سعر لوري رمل تاتة (16م³)",
                            unit = "SDG"
                        )
                    }
                    item {
                        EngineeringInputField(
                            value = sandSmallPrice,
                            onValueChange = { sandSmallPrice = it },
                            label = "سعر قلاب رمل صغير (4م³)",
                            unit = "SDG"
                        )
                    }
                    item {
                        EngineeringInputField(
                            value = gravelTataPrice,
                            onValueChange = { gravelTataPrice = it },
                            label = "سعر لوري كنكري تاتة (16م³)",
                            unit = "SDG"
                        )
                    }
                    item {
                        EngineeringInputField(
                            value = redBricksPrice,
                            onValueChange = { redBricksPrice = it },
                            label = "سعر ألف طوبة أحمر بلدي",
                            unit = "SDG"
                        )
                    }
                    item {
                        EngineeringInputField(
                            value = waterDrumPrice,
                            onValueChange = { waterDrumPrice = it },
                            label = "سعر برميل الماء (200 لتر)",
                            unit = "SDG"
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = MarketPricesSdg(
                            cementTonPrice = cementPrice.toDoubleOrNull() ?: prices.cementTonPrice,
                            steelTonPrice = steelPrice.toDoubleOrNull() ?: prices.steelTonPrice,
                            sandTataTruckPrice = sandTataPrice.toDoubleOrNull() ?: prices.sandTataTruckPrice,
                            sandSmallTruckPrice = sandSmallPrice.toDoubleOrNull() ?: prices.sandSmallTruckPrice,
                            gravelTataTruckPrice = gravelTataPrice.toDoubleOrNull() ?: prices.gravelTataTruckPrice,
                            redBricksPerThousand = redBricksPrice.toDoubleOrNull() ?: prices.redBricksPerThousand,
                            waterDrumPrice = waterDrumPrice.toDoubleOrNull() ?: prices.waterDrumPrice
                        )
                        viewModel.updateMarketPrices(updated)
                        showPriceEditor = false
                    }
                ) {
                    Text("حفظ الأسعار")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPriceEditor = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Add Custom Item Dialog
    if (showAddItemDialog) {
        var cat by remember { mutableStateOf("عام") }
        var desc by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("1.0") }
        var unit by remember { mutableStateOf("مقطوعية") }
        var price by remember { mutableStateOf("100000") }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("إضافة بند مخصص للمقايسة", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("وصف البند") },
                        placeholder = { Text("مثلاً: حفر قواعد، عزل رطوبة، مصنعية نجارة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cat,
                        onValueChange = { cat = it },
                        label = { Text("القسم / التصنيف") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EngineeringInputField(
                            value = qty,
                            onValueChange = { qty = it },
                            label = "الكمية",
                            unit = "",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("الوحدة") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    EngineeringInputField(
                        value = price,
                        onValueChange = { price = it },
                        label = "سعر الوحدة",
                        unit = "SDG"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (desc.isNotBlank()) {
                            viewModel.addCustomBoqItem(
                                category = cat.ifBlank { "عام" },
                                desc = desc,
                                qty = qty.toDoubleOrNull() ?: 1.0,
                                unit = unit.ifBlank { "وحدة" },
                                price = price.toDoubleOrNull() ?: 0.0
                            )
                        }
                        showAddItemDialog = false
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    editingItem?.let { current ->
        var editDesc by remember(current.id) { mutableStateOf(current.itemDescriptionAr) }
        var editQty by remember(current.id) { mutableStateOf(current.quantity.toString()) }
        var editPrice by remember(current.id) { mutableStateOf(current.unitPriceSdg.toLong().toString()) }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("تعديل البند (${current.categoryAr})", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("وصف البند") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    EngineeringInputField(
                        value = editQty,
                        onValueChange = { editQty = it },
                        label = "الكمية",
                        unit = current.unitAr
                    )
                    EngineeringInputField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = "سعر الوحدة",
                        unit = "SDG"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBoqItem(
                            id = current.id,
                            desc = editDesc,
                            qty = editQty.toDoubleOrNull() ?: current.quantity,
                            unitPrice = editPrice.toDoubleOrNull() ?: current.unitPriceSdg
                        )
                        editingItem = null
                    }
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showSaveDialog) {
        SaveProjectDialog(
            category = "مقايسة شاملة",
            onDismiss = { showSaveDialog = false },
            onConfirm = { title, location ->
                viewModel.saveCurrentCalculation(title, location, "مقايسة شاملة") {
                    showSaveDialog = false
                }
            }
        )
    }
}
