package com.combo.ui.common.designsystem.components

import androidx.compose.ui.graphics.vector.ImageVector

/** Значение принадлежит потребителю; набор компонентов знает только подпись и иконку. */
data class PcOption<T>(val value: T, val label: String, val icon: ImageVector? = null)
