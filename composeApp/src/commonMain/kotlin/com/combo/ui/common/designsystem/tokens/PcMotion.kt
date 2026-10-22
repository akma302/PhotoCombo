package com.combo.ui.common.designsystem.tokens

import androidx.compose.runtime.Immutable

@Immutable
data class PcMotion(
    val feedbackMillis: Int = 120,
    val popupMillis: Int = 160,
    val tooltipDelayMillis: Int = 500,
    val reducedMotion: Boolean = false,
) {
    init {
        require(feedbackMillis >= 0)
        require(popupMillis >= 0)
        require(tooltipDelayMillis >= 0)
    }

    val effectiveFeedbackMillis: Int get() = if (reducedMotion) 0 else feedbackMillis
    val effectivePopupMillis: Int get() = if (reducedMotion) 0 else popupMillis
}
