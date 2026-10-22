package com.combo.ui.common.designsystem.primitives

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.combo.ui.common.designsystem.theme.PcTheme

/** Появление не меняет layout и координаты ввода; reduced motion убирает переход. */
@Composable
fun Modifier.pcPopupAppearance(): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(PcTheme.motion.effectivePopupMillis))
    return graphicsLayer { this.alpha = alpha }
}
