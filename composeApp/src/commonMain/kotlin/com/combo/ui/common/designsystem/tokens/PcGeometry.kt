package com.combo.ui.common.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PcSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

@Immutable
data class PcSizes(
    val control: Dp = 34.dp,
    val iconButton: Dp = 34.dp,
    val compactControl: Dp = 28.dp,
    val icon: Dp = 16.dp,
    val iconLarge: Dp = 20.dp,
    val focusOffset: Dp = 2.dp,
    val minimumTarget: Dp = 34.dp,
)

@Immutable
data class PcShapes(
    val small: RoundedCornerShape = RoundedCornerShape(4.dp),
    val control: RoundedCornerShape = RoundedCornerShape(7.dp),
    val group: RoundedCornerShape = RoundedCornerShape(9.dp),
    val dialog: RoundedCornerShape = RoundedCornerShape(12.dp),
)

@Immutable
data class PcStrokes(
    val border: Dp = 1.dp,
    val selection: Dp = 2.dp,
    val focus: Dp = 2.dp,
)

@Immutable
data class PcOpacity(val disabled: Float = 0.36f)

enum class PcDensity(val minimumTarget: Dp) {
    Mouse(34.dp),
    Touch(44.dp),
}
