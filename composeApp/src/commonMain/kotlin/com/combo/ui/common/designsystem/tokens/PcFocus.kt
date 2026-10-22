package com.combo.ui.common.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Настройки света нижней грани; стандартная тема оставляет ровную заливку без свечения. */
@Immutable
data class PcFocus(
    val gradientHeight: Dp = 10.dp,
    val innerAlpha: Float = 0f,
    val haloHeight: Dp = 4.dp,
    val haloAlpha: Float = 0f,
) {
    init {
        require(gradientHeight.value.isFinite() && gradientHeight > 0.dp)
        require(haloHeight.value.isFinite() && haloHeight > 0.dp)
        require(innerAlpha in 0f..1f && haloAlpha in 0f..1f)
    }
}
