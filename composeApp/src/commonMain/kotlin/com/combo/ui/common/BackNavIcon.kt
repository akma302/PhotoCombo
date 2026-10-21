package com.combo.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * Стрелка «назад» для слота `navigationIcon` топбара.
 *
 * Рисуется в builder и photo_viewer одинаково, поэтому вынесено сюда:
 * копии расходятся иконкой и contentDescription, а разница не видна глазом.
 *
 * Только стрелка. «Отмена» в библиотеке — не назад: по ней завершается режим выбора,
 * у неё своя иконка (`Icons.Default.Close`), свой `contentDescription` и свой testTag
 * (`LIB_CANCEL_TAG`), и она занимает слот навигации *вместо* стрелки. Подставлять её сюда
 * нельзя: тег UI-теста поехал бы вместе с иконкой, и режим выбора стало бы нечем
 * закрыть. Поэтому отмена остаётся отдельной кнопкой экрана.
 *
 * `contentDescription = "Back"` — не украшение: строку читает скринридер и по ней ищут
 * кнопку UI-тесты, поэтому менять её молча нельзя.
 */
@Composable
fun BackNavIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}
