package com.combo.ui.common.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Мягкий свет нижней грани не требует платформенного blur и не участвует в layout. */
@Immutable
data class PcFocus(
    val gradientHeight: Dp = 10.dp,
    val innerAlpha: Float = 0.14f,
    val haloHeight: Dp = 4.dp,
    val haloAlpha: Float = 0.35f,
) {
    init {
        require(gradientHeight.value.isFinite() && gradientHeight > 0.dp)
        require(haloHeight.value.isFinite() && haloHeight > 0.dp)
        require(innerAlpha in 0f..1f && haloAlpha in 0f..1f)
    }
}
