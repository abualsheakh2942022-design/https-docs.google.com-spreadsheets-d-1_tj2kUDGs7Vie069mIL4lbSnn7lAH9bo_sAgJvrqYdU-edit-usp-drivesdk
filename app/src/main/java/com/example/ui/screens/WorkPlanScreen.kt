package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ProjectEntity
import com.example.data.db.WorkPlanPhaseEntity
import com.example.data.db.WorkPlanWithPhases
import com.example.domain.*
import com.example.ui.components.EngineeringInputField
import com.example.ui.components.EngineeringTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.shareText
import com.example.ui.theme.NavyLight
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.CivilQuantitiesViewModel
import kotlin.math.roundToInt

private enum class CreatePlanMode {
    CALCULATED,
    SAVED_PROJECT,
    MANUAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPlanScreen(
    viewModel: CivilQuantitiesViewModel,
    onBack: () -> Unit
) {
    val plans by viewModel.workPlans.collectAsState()
    val selectedPlanId by viewModel.selectedWorkPlanId.collectAsState()
    val savedProjects by viewModel.savedProjects.collectAsState(initial = emptyList())
    val context = LocalContext.current

    val activePlanWithPhases = remember(plans, selectedPlanId) {
        plans.firstOrNull { it.plan.id == selectedPlanId } ?: plans.firstOrNull()
    }

    var autoShiftSubsequent by remember { mutableStateOf(true) }
    var createDialogMode by remember { mutableStateOf<CreatePlanMode?>(null) }
    var showAddPhaseDialog by remember { mutableStateOf(false) }
    var phaseToEdit by remember { mutableStateOf<WorkPlanPhaseEntity?>(null) }
    var showEditPlanDialog by remember { mutableStateOf(false) }
    var planToDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            EngineeringTopBar(
                title = "خطة العمل والخارطة الزمنية",
                subtitle = "جدول مرن للمشاريع المحسوبة والمدخلة يدوياً (BS 8110)",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    if (activePlanWithPhases != null) {
                        IconButton(
                            onClick = {
                                val text = buildWorkPlanShareReport(activePlanWithPhases)
                                shareText(context, text, activePlanWithPhases.plan.title)
                            },
                            modifier = Modifier.testTag("share_work_plan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة الخطة",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { createDialogMode = CreatePlanMode.CALCULATED },
                        modifier = Modifier.testTag("new_work_plan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddChart,
                            contentDescription = "خطة عمل جديدة",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (activePlanWithPhases != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddPhaseDialog = true },
                    icon = { Icon(Icons.Default.AddTask, contentDescription = null) },
                    text = { Text("إضافة مرحلة") },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("add_phase_fab")
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quick Create Actions Strip
            item {
                SectionHeader(
                    title = "إنشاء خطة عمل وخارطة زمنية",
                    icon = Icons.Default.Timeline,
                    badgeText = "${plans.size} خطة"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CreateSourceChipButton(
                        title = "من الحسابات",
                        icon = Icons.Default.Calculate,
                        accent = NavyPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_plan_from_calc_btn"),
                        onClick = { createDialogMode = CreatePlanMode.CALCULATED }
                    )
                    CreateSourceChipButton(
                        title = "من مشروع محفوظ",
                        icon = Icons.Default.FolderSpecial,
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_plan_from_saved_btn"),
                        onClick = { createDialogMode = CreatePlanMode.SAVED_PROJECT }
                    )
                    CreateSourceChipButton(
                        title = "إدخال يدوي",
                        icon = Icons.Default.EditCalendar,
                        accent = SuccessGreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_plan_manual_btn"),
                        onClick = { createDialogMode = CreatePlanMode.MANUAL }
                    )
                }
            }

            // Existing Plans Selector
            if (plans.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(plans, key = { it.plan.id }) { item ->
                            val isSelected = item.plan.id == activePlanWithPhases?.plan?.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectWorkPlan(item.plan.id) },
                                label = {
                                    Text(
                                        text = item.plan.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (WorkPlanSourceType.fromName(item.plan.sourceType)) {
                                            WorkPlanSourceType.CALCULATED_CURRENT -> Icons.Default.Calculate
                                            WorkPlanSourceType.SAVED_PROJECT -> Icons.Default.Bookmark
                                            WorkPlanSourceType.SPEC_DOCUMENT -> Icons.Default.Gavel
                                            WorkPlanSourceType.MANUAL_ENTRY -> Icons.Default.EditNote
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("select_plan_chip_${item.plan.id}")
                            )
                        }
                    }
                }
            }

            if (activePlanWithPhases == null) {
                item {
                    EmptyWorkPlanCard(
                        onCreateFromCalc = { createDialogMode = CreatePlanMode.CALCULATED },
                        onCreateFromSaved = { createDialogMode = CreatePlanMode.SAVED_PROJECT },
                        onCreateManual = { createDialogMode = CreatePlanMode.MANUAL }
                    )
                }
            } else {
                val plan = activePlanWithPhases.plan
                val orderedPhases = activePlanWithPhases.orderedPhases
                val summary = WorkPlanGenerator.computeSummary(plan, orderedPhases)
                val pace = SchedulePace.fromName(plan.paceName)
                val sourceType = WorkPlanSourceType.fromName(plan.sourceType)

                // Plan Overview Hero Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("work_plan_overview_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(listOf(NavyPrimary, NavyLight))
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = sourceType.arLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { showEditPlanDialog = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل بيانات الخطة",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { planToDeleteId = plan.id },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "حذف الخطة",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = plan.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "${plan.location} • البدء: ${WorkPlanGenerator.formatDateArabic(plan.startDateMillis)} | الانتهاء المتوقع: ${WorkPlanGenerator.formatDateArabic(summary.expectedFinishDateMillis)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PlanMetricPill(
                                    label = "المدة الكلية",
                                    value = "${summary.totalDurationDays} يوم (${summary.totalWeeksRounded} أسبوع)",
                                    modifier = Modifier.weight(1f)
                                )
                                PlanMetricPill(
                                    label = "الإنجاز الكلي",
                                    value = "${summary.weightedProgressPercent}% (${summary.completedPhasesCount}/${orderedPhases.size})",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { summary.weightedProgressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = Color.White.copy(alpha = 0.25f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "مرونة الجدول — وتيرة التنفيذ:",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.85f))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(SchedulePace.entries) { p ->
                                    val selected = p == pace
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.changeWorkPlanPace(activePlanWithPhases, p) },
                                        label = {
                                            Text(
                                                text = p.arLabel,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color.White.copy(alpha = 0.12f),
                                            labelColor = Color.White,
                                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                            selectedLabelColor = Color.White
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.3f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Auto-shift flexibility control
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SyncAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "إزاحة المراحل التالية تلقائياً",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "عند تقديم أو تأخير أي مرحلة أو تغيير مدتها، يتم ترحيل المراحل اللاحقة تلقائياً",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Switch(
                                checked = autoShiftSubsequent,
                                onCheckedChange = { autoShiftSubsequent = it },
                                modifier = Modifier.testTag("auto_shift_switch")
                            )
                        }
                    }
                }

                // Interactive Gantt Chart Timeline
                item {
                    SectionHeader(
                        title = "الخارطة الزمنية التفاعلية (Gantt)",
                        icon = Icons.Default.ViewTimeline,
                        badgeText = "اضغط على أي شريط للتعديل"
                    )
                    FlexibleGanttTimelineCard(
                        planStartMillis = plan.startDateMillis,
                        totalDurationDays = summary.totalDurationDays.coerceAtLeast(7),
                        phases = orderedPhases,
                        onPhaseTap = { phaseToEdit = it }
                    )
                }

                // Phases Checklist Header
                item {
                    SectionHeader(
                        title = "مراحل التنفيذ والتفاصيل (${orderedPhases.size})",
                        icon = Icons.Default.Checklist,
                        badgeText = "${summary.completedPhasesCount} مكتمل"
                    )
                }

                if (orderedPhases.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("لا توجد مراحل بعد في هذه الخطة.", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(onClick = { showAddPhaseDialog = true }) {
                                    Text("إضافة أول مرحلة")
                                }
                            }
                        }
                    }
                } else {
                    items(orderedPhases, key = { it.id }) { phase ->
                        val index = orderedPhases.indexOf(phase)
                        PhaseFlexibleCard(
                            phase = phase,
                            index = index,
                            totalCount = orderedPhases.size,
                            planStartMillis = plan.startDateMillis,
                            onCycleStatus = { viewModel.cycleWorkPlanPhaseStatus(phase) },
                            onShiftDays = { delta ->
                                viewModel.shiftWorkPlanPhase(activePlanWithPhases, phase, delta, autoShiftSubsequent)
                            },
                            onMoveUp = { viewModel.moveWorkPlanPhaseOrder(activePlanWithPhases, phase.id, moveUp = true) },
                            onMoveDown = { viewModel.moveWorkPlanPhaseOrder(activePlanWithPhases, phase.id, moveUp = false) },
                            onEdit = { phaseToEdit = phase },
                            onDelete = { viewModel.deleteWorkPlanPhase(phase.id) }
                        )
                    }
                }
            }
        }
    }

    // Create Work Plan Dialog (Calculated / Saved / Manual)
    if (createDialogMode != null) {
        CreateWorkPlanDialog(
            initialMode = createDialogMode!!,
            savedProjects = savedProjects,
            onDismiss = { createDialogMode = null },
            onCreateFromCalculated = { title, location, pace, startMillis ->
                viewModel.createWorkPlanFromCurrentCalculations(title, location, pace, startMillis) {
                    createDialogMode = null
                }
            },
            onCreateFromSavedProject = { project, pace ->
                viewModel.createOrOpenWorkPlanForSavedProject(project, pace, navigateToSchedule = false)
                createDialogMode = null
            },
            onCreateManual = { title, location, floors, area, template, pace, startMillis, startBlank ->
                viewModel.createManualWorkPlan(
                    title = title,
                    location = location,
                    floorsCount = floors,
                    areaPerFloorM2 = area,
                    template = template,
                    pace = pace,
                    startDateMillis = startMillis,
                    startBlank = startBlank
                ) {
                    createDialogMode = null
                }
            }
        )
    }

    // Add Phase Dialog
    if (showAddPhaseDialog && activePlanWithPhases != null) {
        val suggestedStart = activePlanWithPhases.orderedPhases.maxOfOrNull { it.startDay + it.durationDays } ?: 0
        PhaseEditorDialog(
            initialPhase = null,
            defaultStartDay = suggestedStart,
            autoShiftEnabled = autoShiftSubsequent,
            onDismiss = { showAddPhaseDialog = false },
            onConfirm = { title, cat, startDay, duration, progress, status, qtyHint, resNote, _ ->
                viewModel.addWorkPlanPhase(
                    planWithPhases = activePlanWithPhases,
                    title = title,
                    category = cat,
                    startDay = startDay,
                    durationDays = duration,
                    quantityHint = qtyHint,
                    resourcesNote = resNote
                )
                showAddPhaseDialog = false
            }
        )
    }

    // Edit Phase Dialog
    if (phaseToEdit != null && activePlanWithPhases != null) {
        val editing = phaseToEdit!!
        PhaseEditorDialog(
            initialPhase = editing,
            defaultStartDay = editing.startDay,
            autoShiftEnabled = autoShiftSubsequent,
            onDismiss = { phaseToEdit = null },
            onConfirm = { title, cat, startDay, duration, progress, status, qtyHint, resNote, shiftFollowing ->
                viewModel.updateWorkPlanPhase(
                    planWithPhases = activePlanWithPhases,
                    updatedPhase = editing.copy(
                        title = title,
                        categoryName = cat.name,
                        startDay = startDay,
                        durationDays = duration,
                        progressPercent = progress,
                        statusName = status.name,
                        quantityHint = qtyHint,
                        resourcesNote = resNote
                    ),
                    autoShiftSubsequent = shiftFollowing
                )
                phaseToEdit = null
            }
        )
    }

    // Edit Plan Metadata Dialog
    if (showEditPlanDialog && activePlanWithPhases != null) {
        val plan = activePlanWithPhases.plan
        var titleStr by remember { mutableStateOf(plan.title) }
        var locStr by remember { mutableStateOf(plan.location) }
        var notesStr by remember { mutableStateOf(plan.notes) }
        var startDayShift by remember { mutableStateOf(0) }

        AlertDialog(
            onDismissRequest = { showEditPlanDialog = false },
            title = { Text("تعديل بيانات الخطة الزمنية", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleStr,
                        onValueChange = { titleStr = it },
                        label = { Text("اسم المشروع / الخطة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = locStr,
                        onValueChange = { locStr = it },
                        label = { Text("الموقع") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("ملاحظات عامة") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "تاريخ البدء: ${WorkPlanGenerator.formatDateArabic(plan.startDateMillis + startDayShift * 86_400_000L)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row {
                            TextButton(onClick = { startDayShift -= 1 }) { Text("-1 يوم") }
                            TextButton(onClick = { startDayShift += 1 }) { Text("+1 يوم") }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateWorkPlanMeta(
                            plan = plan,
                            title = titleStr,
                            location = locStr,
                            startDateMillis = plan.startDateMillis + startDayShift * 86_400_000L,
                            notes = notesStr
                        )
                        showEditPlanDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPlanDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Delete Plan Confirmation
    if (planToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { planToDeleteId = null },
            title = { Text("حذف خطة العمل") },
            text = { Text("هل تريد حذف هذه الخطة الزمنية بالكامل؟ لن يؤثر ذلك على مشاريعك المحفوظة الأخرى.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkPlan(planToDeleteId!!)
                        planToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDeleteId = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun CreateSourceChipButton(
    title: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .heightIn(min = 66.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlanMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyWorkPlanCard(
    onCreateFromCalc: () -> Unit,
    onCreateFromSaved: () -> Unit,
    onCreateManual: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EventNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ابدأ ببناء خطتك الزمنية المرنة",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "حوّل نتائج الحسابات أو المشاريع المحفوظة إلى خارطة جانت زمنية قابلة للتعديل، أو أدخل مشروعاً جديداً يدوياً بمراحله ومدده.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCreateFromCalc,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("توليد خطة من الحسابات الحالية")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCreateFromSaved,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("مشروع محفوظ", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onCreateManual,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("إدخال يدوي", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FlexibleGanttTimelineCard(
    planStartMillis: Long,
    totalDurationDays: Int,
    phases: List<WorkPlanPhaseEntity>,
    onPhaseTap: (WorkPlanPhaseEntity) -> Unit
) {
    val dayWidthDp = 22.dp
    val labelColWidth = 126.dp
    val totalDaysToDisplay = (totalDurationDays + 2).coerceAtLeast(14)
    val timelineWidth = dayWidthDp * totalDaysToDisplay
    val horizontalScrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gantt_chart_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Fixed phase column header
                Box(
                    modifier = Modifier
                        .width(labelColWidth)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 8.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "المرحلة / النشاط",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Scrollable ruler & bars
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    // Week / Day Ruler Header
                    Row(
                        modifier = Modifier
                            .width(timelineWidth)
                            .height(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var day = 0
                        while (day < totalDaysToDisplay) {
                            val blockDays = minOf(7, totalDaysToDisplay - day)
                            val weekNum = (day / 7) + 1
                            Box(
                                modifier = Modifier
                                    .width(dayWidthDp * blockDays)
                                    .fillMaxHeight()
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "أسبوع $weekNum (${WorkPlanGenerator.dayOffsetToDate(planStartMillis, day).substring(5)})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1
                                )
                            }
                            day += 7
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Rows
            phases.forEachIndexed { idx, phase ->
                val cat = PhaseCategory.fromName(phase.categoryName)
                val status = PhaseStatus.fromName(phase.statusName)
                val barColor = if (status == PhaseStatus.COMPLETED) SuccessGreen else Color(cat.colorHex)
                val offsetDp = dayWidthDp * phase.startDay.coerceAtLeast(0)
                val barWidthDp = (dayWidthDp * phase.durationDays.coerceAtLeast(1)).coerceAtLeast(28.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable { onPhaseTap(phase) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .width(labelColWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(barColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${idx + 1}. ${phase.title}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(timelineWidth)
                                .fillMaxHeight()
                        ) {
                            // Subtle vertical week dividers
                            var d = 0
                            while (d < totalDaysToDisplay) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = dayWidthDp * d)
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                                )
                                d += 7
                            }

                            // Gantt Bar
                            Box(
                                modifier = Modifier
                                    .offset(x = offsetDp)
                                    .width(barWidthDp)
                                    .height(26.dp)
                                    .align(Alignment.CenterStart)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(barColor.copy(alpha = 0.25f))
                                    .clickable { onPhaseTap(phase) }
                            ) {
                                // Progress fill
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((phase.progressPercent.coerceIn(0, 100) / 100f))
                                        .background(barColor)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${phase.durationDays}ي",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (phase.progressPercent >= 45) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    if (phase.progressPercent > 0) {
                                        Text(
                                            text = "${phase.progressPercent}%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.sp,
                                                color = if (phase.progressPercent >= 80) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (idx < phases.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
private fun PhaseFlexibleCard(
    phase: WorkPlanPhaseEntity,
    index: Int,
    totalCount: Int,
    planStartMillis: Long,
    onCycleStatus: () -> Unit,
    onShiftDays: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = PhaseCategory.fromName(phase.categoryName)
    val status = PhaseStatus.fromName(phase.statusName)
    val catColor = Color(category.colorHex)
    val statusColor = when (status) {
        PhaseStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
        PhaseStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        PhaseStatus.COMPLETED -> SuccessGreen
        PhaseStatus.DELAYED -> SafetyAmber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase_card_${phase.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, catColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = catColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${index + 1}. ${category.arLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = catColor
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable(onClick = onCycleStatus)
                ) {
                    Text(
                        text = "${status.arLabel} (${phase.progressPercent}%)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = phase.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "اليوم ${phase.startDay + 1} ← ${phase.startDay + phase.durationDays} (المدة: ${phase.durationDays} يوم) | ${WorkPlanGenerator.dayOffsetToDate(planStartMillis, phase.startDay)} → ${WorkPlanGenerator.dayOffsetToDate(planStartMillis, phase.startDay + phase.durationDays)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )

            if (phase.quantityHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "الكميات: ${phase.quantityHint}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (phase.resourcesNote.isNotBlank()) {
                Text(
                    text = phase.resourcesNote,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { phase.progressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Day Shift
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onShiftDays(-1) },
                        enabled = phase.startDay > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("تقديم -1ي", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { onShiftDays(1) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("تأجيل +1ي", fontSize = 11.sp)
                    }
                }

                // Reorder + Edit + Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "أعلى", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = index < totalCount - 1, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "أسفل", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "تعديل المرحلة",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "حذف المرحلة",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateWorkPlanDialog(
    initialMode: CreatePlanMode,
    savedProjects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onCreateFromCalculated: (title: String, location: String, pace: SchedulePace, startMillis: Long) -> Unit,
    onCreateFromSavedProject: (project: ProjectEntity, pace: SchedulePace) -> Unit,
    onCreateManual: (
        title: String,
        location: String,
        floors: Int,
        areaM2: Double,
        template: ManualProjectTemplate,
        pace: SchedulePace,
        startMillis: Long,
        startBlank: Boolean
    ) -> Unit
) {
    var mode by remember { mutableStateOf(initialMode) }
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("الخرطوم، السودان") }
    var pace by remember { mutableStateOf(SchedulePace.STANDARD) }
    var floorsStr by remember { mutableStateOf("2") }
    var areaStr by remember { mutableStateOf("180") }
    var template by remember { mutableStateOf(ManualProjectTemplate.RESIDENTIAL_BUILDING) }
    var startBlank by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء خطة عمل وخارطة زمنية", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = mode.ordinal) {
                    Tab(
                        selected = mode == CreatePlanMode.CALCULATED,
                        onClick = { mode = CreatePlanMode.CALCULATED },
                        text = { Text("حسابات", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = mode == CreatePlanMode.SAVED_PROJECT,
                        onClick = { mode = CreatePlanMode.SAVED_PROJECT },
                        text = { Text("محفوظة", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = mode == CreatePlanMode.MANUAL,
                        onClick = { mode = CreatePlanMode.MANUAL },
                        text = { Text("يدوي", fontSize = 11.sp) }
                    )
                }

                when (mode) {
                    CreatePlanMode.CALCULATED -> {
                        Text(
                            text = "سيتم قراءة كميات الخرسانة والتسليح والمباني والتشطيبات (أو المخطط المحلل) الحالية وحساب المدد تلقائياً.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("اسم المشروع") },
                            placeholder = { Text("مثلاً: فيلا المنشية - هيكل وتجهيز") },
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
                        PaceSelectorRow(selected = pace, onSelect = { pace = it })
                    }
                    CreatePlanMode.SAVED_PROJECT -> {
                        PaceSelectorRow(selected = pace, onSelect = { pace = it })
                        if (savedProjects.isEmpty()) {
                            Text(
                                text = "لا توجد مشاريع محفوظة حالياً. احفظ حساباتك من أي شاشة أولاً أو اختر «حسابات» أو «يدوي».",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(savedProjects, key = { it.id }) { proj ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onCreateFromSavedProject(proj, pace) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(proj.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = "${proj.category} • ${proj.clientOrLocation}",
                                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CreatePlanMode.MANUAL -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("اسم المشروع المدخل") },
                            placeholder = { Text("مثلاً: عمارة 3 طوابق - أمدرمان") },
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EngineeringInputField(
                                value = floorsStr,
                                onValueChange = { floorsStr = it },
                                label = "عدد الطوابق",
                                unit = "طابق",
                                modifier = Modifier.weight(1f)
                            )
                            EngineeringInputField(
                                value = areaStr,
                                onValueChange = { areaStr = it },
                                label = "مساحة الطابق",
                                unit = "م²",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("بدء بجدول فارغ لإدخال المراحل يدوياً", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = startBlank, onCheckedChange = { startBlank = it })
                        }
                        if (!startBlank) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(ManualProjectTemplate.entries) { tpl ->
                                    FilterChip(
                                        selected = tpl == template,
                                        onClick = { template = tpl },
                                        label = { Text(tpl.arLabel.substringBefore(" ("), fontSize = 11.sp) }
                                    )
                                }
                            }
                            PaceSelectorRow(selected = pace, onSelect = { pace = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mode != CreatePlanMode.SAVED_PROJECT) {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        when (mode) {
                            CreatePlanMode.CALCULATED -> onCreateFromCalculated(title, location, pace, now)
                            CreatePlanMode.MANUAL -> onCreateManual(
                                title,
                                location,
                                floorsStr.toIntOrNull() ?: 2,
                                areaStr.toDoubleOrNull() ?: 180.0,
                                template,
                                pace,
                                now,
                                startBlank
                            )
                            else -> Unit
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_work_plan")
                ) {
                    Text("إنشاء الخطة")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun PaceSelectorRow(
    selected: SchedulePace,
    onSelect: (SchedulePace) -> Unit
) {
    Column {
        Text("وتيرة العمل:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SchedulePace.entries) { p ->
                FilterChip(
                    selected = p == selected,
                    onClick = { onSelect(p) },
                    label = { Text(p.arLabel, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun PhaseEditorDialog(
    initialPhase: WorkPlanPhaseEntity?,
    defaultStartDay: Int,
    autoShiftEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        category: PhaseCategory,
        startDay: Int,
        durationDays: Int,
        progress: Int,
        status: PhaseStatus,
        quantityHint: String,
        resourcesNote: String,
        autoShiftFollowing: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialPhase?.title ?: "") }
    var category by remember { mutableStateOf(initialPhase?.let { PhaseCategory.fromName(it.categoryName) } ?: PhaseCategory.SUPERSTRUCTURE) }
    var startDayStr by remember { mutableStateOf(((initialPhase?.startDay ?: defaultStartDay) + 1).toString()) }
    var durationStr by remember { mutableStateOf((initialPhase?.durationDays ?: 5).toString()) }
    var progressFloat by remember { mutableStateOf((initialPhase?.progressPercent ?: 0).toFloat()) }
    var status by remember { mutableStateOf(initialPhase?.let { PhaseStatus.fromName(it.statusName) } ?: PhaseStatus.NOT_STARTED) }
    var quantityHint by remember { mutableStateOf(initialPhase?.quantityHint ?: "") }
    var resourcesNote by remember { mutableStateOf(initialPhase?.resourcesNote ?: "") }
    var shiftFollowing by remember { mutableStateOf(autoShiftEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPhase == null) "إضافة مرحلة تنفيذية جديدة" else "تعديل المرحلة",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("اسم المرحلة / النشاط") },
                        placeholder = { Text("مثلاً: صب سقف الطابق الأول") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("التصنيف:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PhaseCategory.entries) { cat ->
                            FilterChip(
                                selected = cat == category,
                                onClick = { category = cat },
                                label = { Text(cat.arLabel, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EngineeringInputField(
                            value = startDayStr,
                            onValueChange = { startDayStr = it },
                            label = "يوم البداية",
                            unit = "اليوم",
                            modifier = Modifier.weight(1f)
                        )
                        EngineeringInputField(
                            value = durationStr,
                            onValueChange = { durationStr = it },
                            label = "المدة",
                            unit = "يوم",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (initialPhase != null) {
                    item {
                        Text(
                            text = "نسبة الإنجاز: ${progressFloat.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = progressFloat,
                            onValueChange = {
                                progressFloat = it
                                status = when {
                                    it.roundToInt() >= 100 -> PhaseStatus.COMPLETED
                                    it.roundToInt() > 0 && status == PhaseStatus.NOT_STARTED -> PhaseStatus.IN_PROGRESS
                                    it.roundToInt() == 0 && status == PhaseStatus.COMPLETED -> PhaseStatus.NOT_STARTED
                                    else -> status
                                }
                            },
                            valueRange = 0f..100f
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(PhaseStatus.entries) { st ->
                                FilterChip(
                                    selected = st == status,
                                    onClick = {
                                        status = st
                                        if (st == PhaseStatus.COMPLETED) progressFloat = 100f
                                        else if (st == PhaseStatus.NOT_STARTED) progressFloat = 0f
                                    },
                                    label = { Text(st.arLabel, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إزاحة المراحل التالية عند تعديل المدة/البداية", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = shiftFollowing, onCheckedChange = { shiftFollowing = it })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = quantityHint,
                        onValueChange = { quantityHint = it },
                        label = { Text("ملخص الكميات (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = resourcesNote,
                        onValueChange = { resourcesNote = it },
                        label = { Text("ملاحظات فنية / طاقم العمل (اختياري)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val zeroBasedStart = ((startDayStr.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
                    val safeDuration = (durationStr.toIntOrNull() ?: 3).coerceAtLeast(1)
                    onConfirm(
                        title.ifBlank { "مرحلة تنفيذية" },
                        category,
                        zeroBasedStart,
                        safeDuration,
                        progressFloat.roundToInt().coerceIn(0, 100),
                        status,
                        quantityHint,
                        resourcesNote,
                        shiftFollowing
                    )
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

private fun buildWorkPlanShareReport(planWithPhases: WorkPlanWithPhases): String {
    val plan = planWithPhases.plan
    val phases = planWithPhases.orderedPhases
    val summary = WorkPlanGenerator.computeSummary(plan, phases)
    return buildString {
        appendLine("📅 خطة العمل والجدول الزمني: ${plan.title}")
        appendLine("الموقع: ${plan.location}")
        appendLine("تاريخ البدء: ${WorkPlanGenerator.formatDateArabic(plan.startDateMillis)}")
        appendLine("الانتهاء المتوقع: ${WorkPlanGenerator.formatDateArabic(summary.expectedFinishDateMillis)}")
        appendLine("المدة الكلية: ${summary.totalDurationDays} يوم (${summary.totalWeeksRounded} أسبوع) | الإنجاز: ${summary.weightedProgressPercent}%")
        appendLine()
        appendLine("--- مراحل التنفيذ ---")
        phases.forEachIndexed { i, p ->
            val st = PhaseStatus.fromName(p.statusName).arLabel
            appendLine("${i + 1}. ${p.title} [$st - ${p.progressPercent}%]")
            appendLine("   اليوم ${p.startDay + 1} إلى ${p.startDay + p.durationDays} (${p.durationDays} يوم)")
            if (p.quantityHint.isNotBlank()) appendLine("   الكميات: ${p.quantityHint}")
            if (p.resourcesNote.isNotBlank()) appendLine("   ملاحظة: ${p.resourcesNote}")
        }
    }
}
