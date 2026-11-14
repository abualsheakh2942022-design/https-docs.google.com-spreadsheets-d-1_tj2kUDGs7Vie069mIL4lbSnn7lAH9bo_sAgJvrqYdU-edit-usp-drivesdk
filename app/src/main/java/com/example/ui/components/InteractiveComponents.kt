package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.RebarDiameter
import com.example.domain.StructuralElementType
import com.example.ui.theme.*
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive press-scale modifier with tactile spring bounce and optional haptic click.
 */
@Composable
fun Modifier.tactileClickable(
    enableHaptic: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tactileScale"
    )
    val haptic = LocalHapticFeedback.current
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = {
                if (enableHaptic) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        )
}

/**
 * Smooth slider control with unit badge and quick preset chips for fast field adjustments.
 */
@Composable
fun EngineeringSliderControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    presets: List<Float> = emptyList(),
    stepDecimals: Int = 1,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val displayText = if (stepDecimals == 0) {
        "${clamped.roundToInt()}"
    } else {
        "${(clamped * 10f).roundToInt() / 10f}"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$displayText $unit",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Slider(
            value = clamped,
            onValueChange = { raw ->
                val rounded = if (stepDecimals == 0) raw.roundToInt().toFloat() else (raw * 10f).roundToInt() / 10f
                onValueChange(rounded)
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSel = kotlin.math.abs(clamped - preset) < 0.05f
                    val presetLabel = if (stepDecimals == 0) "${preset.roundToInt()} $unit" else "$preset $unit"
                    SuggestionChip(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onValueChange(preset)
                        },
                        label = {
                            Text(
                                text = presetLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Compact +/- integer or decimal stepper row for fast quantity adjustments.
 */
@Composable
fun QuickStepperRow(
    label: String,
    valueText: String,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDecrement()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "إنقاص $label", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "$valueText $unit",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onIncrement()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "زيادة $label", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Live interactive blueprint Canvas preview for Concrete structural elements.
 */
@Composable
fun LiveConcreteDiagramCard(
    elementType: StructuralElementType,
    lengthM: Double,
    widthM: Double,
    heightM: Double,
    count: Int,
    netVolumeM3: Double,
    modifier: Modifier = Modifier
) {
    val animL by animateFloatAsState(lengthM.toFloat().coerceAtLeast(0.1f), spring(), label = "cL")
    val animW by animateFloatAsState(widthM.toFloat().coerceAtLeast(0.1f), spring(), label = "cW")
    val animH by animateFloatAsState(heightM.toFloat().coerceAtLeast(0.05f), spring(), label = "cH")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring()),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyDark),
        border = BorderStroke(1.dp, BlueprintTeal.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A192F))
                    .padding(10.dp)
            ) {
                val gridColor = Color.White.copy(alpha = 0.08f)
                val step = size.width / 4f
                for (i in 1..3) {
                    drawLine(gridColor, Offset(step * i, 0f), Offset(step * i, size.height), 1f)
                    drawLine(gridColor, Offset(0f, step * i), Offset(size.width, step * i), 1f)
                }

                val stroke = Color(0xFF38BDF8)
                val fill = Color(0xFF38BDF8).copy(alpha = 0.18f)
                val rebarColor = SafetyAmberLight

                when (elementType) {
                    StructuralElementType.COLUMN_CIRCULAR -> {
                        val r = min(size.width, size.height) * 0.36f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(fill, radius = r, center = center)
                        drawCircle(stroke, radius = r, center = center, style = Stroke(width = 3f))
                        drawCircle(rebarColor, radius = r * 0.72f, center = center, style = Stroke(width = 2f))
                        for (k in 0 until 6) {
                            val angle = Math.toRadians((k * 60).toDouble())
                            val bx = center.x + (r * 0.72f * kotlin.math.cos(angle)).toFloat()
                            val by = center.y + (r * 0.72f * kotlin.math.sin(angle)).toFloat()
                            drawCircle(rebarColor, radius = 3.5f, center = Offset(bx, by))
                        }
                    }
                    StructuralElementType.SOLID_SLAB,
                    StructuralElementType.FLAT_SLAB,
                    StructuralElementType.RIBBED_SLAB,
                    StructuralElementType.SLAB_ON_GRADE -> {
                        val ratio = (animL / animW).coerceIn(0.45f, 2.2f)
                        val rectW = if (ratio >= 1f) size.width * 0.82f else size.height * 0.82f * ratio
                        val rectH = if (ratio >= 1f) (size.width * 0.82f) / ratio else size.height * 0.82f
                        val left = (size.width - rectW) / 2f
                        val top = (size.height - rectH) / 2f
                        drawRoundRect(fill, Offset(left, top), Size(rectW, rectH), CornerRadius(4f, 4f))
                        drawRoundRect(stroke, Offset(left, top), Size(rectW, rectH), CornerRadius(4f, 4f), style = Stroke(3f))
                        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        drawLine(rebarColor, Offset(left + 6f, size.height / 2f), Offset(left + rectW - 6f, size.height / 2f), 2f, pathEffect = dash)
                        drawLine(rebarColor, Offset(size.width / 2f, top + 6f), Offset(size.width / 2f, top + rectH - 6f), 2f, pathEffect = dash)
                    }
                    else -> {
                        val ratio = (animL / animW).coerceIn(0.45f, 2.2f)
                        val rectW = if (ratio >= 1f) size.width * 0.78f else size.height * 0.78f * ratio
                        val rectH = if (ratio >= 1f) (size.width * 0.78f) / ratio else size.height * 0.78f
                        val left = (size.width - rectW) / 2f
                        val top = (size.height - rectH) / 2f
                        drawRect(fill, Offset(left, top), Size(rectW, rectH))
                        drawRect(stroke, Offset(left, top), Size(rectW, rectH), style = Stroke(3f))
                        // Inner cover stirrup
                        drawRect(
                            rebarColor,
                            Offset(left + 7f, top + 7f),
                            Size((rectW - 14f).coerceAtLeast(8f), (rectH - 14f).coerceAtLeast(8f)),
                            style = Stroke(2f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "معاينة تفاعلية: ${elementType.titleAr.substringBefore(" (")}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "الأبعاد: ${String.format("%.2f", animL)} × ${String.format("%.2f", animW)} × ${String.format("%.2f", animH)} م  |  العدد: $count",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f))
                )
                Surface(
                    color = BlueprintTeal.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "الحجم الصافي الحي: ${((netVolumeM3 * 100).roundToInt() / 100.0)} م³",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SafetyAmberLight
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Live interactive rebar cross-section Canvas showing exact bar count & stirrup dimensions.
 */
@Composable
fun LiveSteelSectionDiagramCard(
    diameter: RebarDiameter,
    numberOfBars: Int,
    memberWidthM: Double,
    memberDepthM: Double,
    includeStirrups: Boolean,
    stirrupDiameter: RebarDiameter,
    stirrupsCount: Int,
    modifier: Modifier = Modifier
) {
    val safeBars = numberOfBars.coerceIn(1, 24)
    val ratio by animateFloatAsState(
        targetValue = (memberWidthM / memberDepthM.coerceAtLeast(0.1)).toFloat().coerceIn(0.4f, 2.0f),
        animationSpec = spring(),
        label = "steelRatio"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyDark),
        border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A192F))
                    .padding(10.dp)
            ) {
                val rectW = if (ratio >= 1f) size.width * 0.84f else size.height * 0.84f * ratio
                val rectH = if (ratio >= 1f) (size.width * 0.84f) / ratio else size.height * 0.84f
                val left = (size.width - rectW) / 2f
                val top = (size.height - rectH) / 2f

                // Concrete outline
                drawRect(Color.White.copy(alpha = 0.08f), Offset(left, top), Size(rectW, rectH))
                drawRect(Color.White.copy(alpha = 0.45f), Offset(left, top), Size(rectW, rectH), style = Stroke(2f))

                // Stirrup loop
                val coverPx = 8f
                val stW = (rectW - coverPx * 2).coerceAtLeast(12f)
                val stH = (rectH - coverPx * 2).coerceAtLeast(12f)
                if (includeStirrups) {
                    drawRoundRect(
                        color = Color(0xFF38BDF8),
                        topLeft = Offset(left + coverPx, top + coverPx),
                        size = Size(stW, stH),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(2.5f)
                    )
                }

                // Distribute rebar dots cleanly along top & bottom edges
                val barRadius = when {
                    diameter.mm >= 25 -> 5.5f
                    diameter.mm >= 16 -> 4.5f
                    else -> 3.5f
                }
                val topBars = (safeBars + 1) / 2
                val bottomBars = safeBars - topBars
                val pad = coverPx + 6f
                val innerSpan = (rectW - pad * 2).coerceAtLeast(4f)

                for (i in 0 until topBars) {
                    val x = if (topBars == 1) size.width / 2f else left + pad + (innerSpan * i / (topBars - 1))
                    drawCircle(SafetyAmberLight, barRadius, Offset(x, top + pad))
                }
                for (i in 0 until bottomBars) {
                    val x = if (bottomBars == 1) size.width / 2f else left + pad + (innerSpan * i / (bottomBars - 1))
                    drawCircle(SafetyAmberLight, barRadius, Offset(x, top + rectH - pad))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "مقطع تسليح تفاعلي (${safeBars}Ø${diameter.mm})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = if (includeStirrups) {
                        "الكانات: $stirrupsCount كانة (Ø${stirrupDiameter.mm} مم) | قطاع ${(memberWidthM * 100).roundToInt()}×${(memberDepthM * 100).roundToInt()} سم"
                    } else {
                        "بدون كانات | التسليح الرئيسي قطر د = ${diameter.mm} مم"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f))
                )
                Surface(
                    color = SafetyAmber.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "السيخة الواحدة (12م) = ${diameter.weightPer12mBarKg} كجم",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SafetyAmberLight
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Live brick wall elevation Canvas showing wall size and openings cutout ratio.
 */
@Composable
fun LiveMasonryWallDiagramCard(
    wallLengthM: Double,
    wallHeightM: Double,
    openingsAreaM2: Double,
    netAreaM2: Double,
    modifier: Modifier = Modifier
) {
    val gross = (wallLengthM * wallHeightM).coerceAtLeast(0.1)
    val openingRatio by animateFloatAsState(
        targetValue = (openingsAreaM2 / gross).toFloat().coerceIn(0f, 0.7f),
        animationSpec = spring(),
        label = "openingRatio"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF431407)),
        border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .width(116.dp)
                    .height(82.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF7C2D12))
                    .padding(6.dp)
            ) {
                // Brick course horizontal lines
                val brickColor = Color(0xFFFED7AA).copy(alpha = 0.35f)
                val rows = 5
                val rowH = size.height / rows
                for (r in 1 until rows) {
                    drawLine(brickColor, Offset(0f, r * rowH), Offset(size.width, r * rowH), 1.5f)
                }
                drawRect(Color(0xFFFDBA74), Offset.Zero, size, style = Stroke(2.5f))

                // Opening cutout (proportional)
                if (openingRatio > 0.02f) {
                    val openW = size.width * kotlin.math.sqrt(openingRatio)
                    val openH = size.height * kotlin.math.sqrt(openingRatio)
                    val left = (size.width - openW) / 2f
                    val top = size.height - openH
                    drawRect(Color(0xFF1E293B), Offset(left, top), Size(openW, openH))
                    drawRect(Color.White.copy(alpha = 0.7f), Offset(left, top), Size(openW, openH), style = Stroke(2f))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "واجهة الجدار (${String.format("%.1f", wallLengthM)}م × ${String.format("%.1f", wallHeightM)}م)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "خصم الفتحات: ${String.format("%.1f", openingsAreaM2)} م²",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f))
                )
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "المساحة الصافية للبناء: $netAreaM2 م²",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE68A)
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
