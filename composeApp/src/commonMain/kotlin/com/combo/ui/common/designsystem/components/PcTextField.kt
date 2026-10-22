package com.combo.ui.common.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.combo.ui.common.designsystem.primitives.PcText
import com.combo.ui.common.designsystem.primitives.pcControlSurface
import com.combo.ui.common.designsystem.theme.PcTheme

/** Ввод, выделение и клавиатуру обеспечивает BasicTextField; подписи доступны через поле. */
@Composable
fun PcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    error: String? = null,
    showLabel: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val colors = PcTheme.colors
    val labelOpacity = if (enabled) 1f else PcTheme.opacity.disabled
    Column {
        if (showLabel) {
            PcText(
                label,
                modifier = Modifier.alpha(labelOpacity).clearAndSetSemantics {},
                color = colors.textSecondary,
                style = PcTheme.typography.label,
            )
            Spacer(Modifier.height(PcTheme.spacing.xs))
        }
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = colors.accent,
                backgroundColor = colors.accent.copy(alpha = 0.35f),
            ),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier
                    .semantics {
                        contentDescription = label
                        if (error != null) error(error)
                    }
                    .pcControlSurface(
                        source, colors.app, enabled,
                        borderColor = if (error == null) colors.border else colors.danger,
                        focusOnPointer = true,
                        focusColor = if (error == null) colors.accent else colors.danger,
                        glowColor = if (error == null) colors.accent else colors.danger,
                    ),
                enabled = enabled,
                readOnly = readOnly,
                textStyle = PcTheme.typography.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                interactionSource = source,
                decorationBox = { innerTextField ->
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = PcTheme.minimumTarget, minHeight = PcTheme.minimumTarget)
                            .padding(horizontal = PcTheme.spacing.md, vertical = PcTheme.spacing.sm),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        innerTextField()
                    }
                },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(PcTheme.spacing.xs))
            PcText(
                error,
                modifier = Modifier.alpha(labelOpacity).clearAndSetSemantics {},
                color = colors.danger,
                style = PcTheme.typography.caption,
            )
        }
    }
}
