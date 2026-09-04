package com.example.baselift.View.nutrition.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.View.theme.CrystalWhite
import com.example.baselift.View.theme.ElectricBlue
import com.example.baselift.View.theme.MediumGrey
import com.example.baselift.View.theme.NeonGreen
import com.example.baselift.View.theme.PureBlack
import com.example.baselift.View.theme.SoftCoral
import com.example.baselift.View.theme.SunYellow
import com.example.baselift.View.theme.VibrantPurple
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun QuickAddMealsSection(
    templates: List<MealTemplateEntity>,
    onLogMeal: (MealTemplateEntity) -> Unit,
    onDeleteTemplate: (MealTemplateEntity) -> Unit,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllLogs by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Pair<MealTemplateEntity, () -> Unit>?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(com.example.baselift.R.string.nutrition_quick_add_meals),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.clickable { onConfigureClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Configure", tint = NeonGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(com.example.baselift.R.string.nutrition_configure), color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131313))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(com.example.baselift.R.string.nutrition_no_meals),
                    color = MediumGrey,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureBlack)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val sortedTemplates = templates.sortedByDescending { it.id }
                val displayTemplates = if (showAllLogs) sortedTemplates else sortedTemplates.take(4)

                displayTemplates.forEachIndexed { index, template ->
                    androidx.compose.runtime.key(template.id) {
                        val alphaValue = if (!showAllLogs && index == 3 && sortedTemplates.size > 4) 0.4f else 1f

                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val swipeThreshold = with(density) { 80.dp.toPx() }
                        val maxSwipe = with(density) { 120.dp.toPx() }

                        var isDragging by remember { mutableStateOf(false) }
                        var offsetX by remember { mutableFloatStateOf(0f) }
                        var isDeleted by remember { mutableStateOf(false) }
                        
                        val animatedOffset by animateFloatAsState(
                            targetValue = offsetX, 
                            animationSpec = if (isDragging) tween(0) else androidx.compose.animation.core.spring(
                                dampingRatio = 0.8f, // No bouncy, 0.8 is slight spring
                                stiffness = 300f
                            )
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isDeleted,
                        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) + 
                               androidx.compose.animation.shrinkVertically(animationSpec = tween(250, delayMillis = 200, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) +
                               androidx.compose.animation.fadeOut(tween(200))
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(alphaValue)
                                .background(Color.Transparent)
                        ) {
                        // Background delete
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            PureBlack,
                                            Color(0xFFFF3B30).copy(alpha = 0.8f)
                                        )
                                    )
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    itemToDelete = Pair(template) {
                                        coroutineScope.launch {
                                            isDeleted = true
                                            kotlinx.coroutines.delay(450)
                                            onDeleteTemplate(template)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val progress = (animatedOffset / swipeThreshold).coerceIn(0f, 1f)
                            Icon(
                                Icons.Default.Delete, 
                                contentDescription = "Delete", 
                                tint = Color.White.copy(alpha = progress), 
                                modifier = Modifier
                                    .padding(start = 24.dp)
                                    .scale(0.5f + 0.5f * progress)
                            )
                        }
                        
                        // Foreground content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                                .background(PureBlack)
                                .clickable { onLogMeal(template) }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { isDragging = true },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val prevOffset = offsetX
                                            offsetX = (offsetX + dragAmount).coerceIn(0f, maxSwipe)
                                            if (prevOffset < swipeThreshold && offsetX >= swipeThreshold) {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            if (offsetX >= swipeThreshold) {
                                                offsetX = swipeThreshold
                                            } else {
                                                offsetX = 0f
                                            }
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            offsetX = 0f
                                        }
                                    )
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                    // Icon Box
                                    val (iconVector, iconTint) = when (template.iconName) {
                                        "drink" -> Pair(androidx.compose.material.icons.Icons.Default.LocalDrink, ElectricBlue)
                                        "cake" -> Pair(androidx.compose.material.icons.Icons.Default.Cake, SunYellow)
                                        "restaurant" -> Pair(androidx.compose.material.icons.Icons.Default.Restaurant, NeonGreen)
                                        "coffee" -> Pair(androidx.compose.material.icons.Icons.Default.LocalCafe, VibrantPurple)
                                        else -> Pair(androidx.compose.material.icons.Icons.Default.Fastfood, SoftCoral)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF131313))
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(iconVector, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = template.name,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(com.example.baselift.R.string.nutrition_tap_to_log),
                                            color = MediumGrey,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.5.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "${template.calories}",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(com.example.baselift.R.string.nutrition_kcal_lower),
                                            color = MediumGrey,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                    
                    if (index < displayTemplates.size - 1) {
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                    }
                }
                
                if (sortedTemplates.size > 4) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (showAllLogs) stringResource(com.example.baselift.R.string.nutrition_show_less) else stringResource(com.example.baselift.R.string.nutrition_show_more),
                        color = MediumGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllLogs = !showAllLogs }
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (itemToDelete != null) {
        val (template, onConfirmDelete) = itemToDelete!!
        Dialog(onDismissRequest = { itemToDelete = null }) {
            val animateTrigger = remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { animateTrigger.value = true }
            val scale by animateFloatAsState(
                targetValue = if (animateTrigger.value) 1f else 0.9f, 
                animationSpec = tween(300, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
            )
            val alpha by animateFloatAsState(
                targetValue = if (animateTrigger.value) 1f else 0f, 
                animationSpec = tween(300, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureBlack)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(stringResource(com.example.baselift.R.string.nutrition_delete_template), color = SoftCoral, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(com.example.baselift.R.string.nutrition_delete_template_msg, template.name), color = CrystalWhite, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text(stringResource(com.example.baselift.R.string.workout_cancel), color = MediumGrey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirmDelete()
                            itemToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftCoral),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(com.example.baselift.R.string.workout_delete), color = PureBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
