package com.combo.ui.common.designsystem.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Общие текстовые роли; платформенный хост передаёт разрешённое семейство шрифтов. */
class PcTypography(fontFamily: FontFamily = FontFamily.SansSerif) {
    val title = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontFamily = fontFamily, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal)
    val control = TextStyle(fontFamily = fontFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val controlStrong = TextStyle(fontFamily = fontFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val label = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = fontFamily, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
}
