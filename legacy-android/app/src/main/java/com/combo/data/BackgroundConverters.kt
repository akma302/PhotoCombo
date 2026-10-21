package com.combo.data

import androidx.room.TypeConverter
import com.combo.core.background.BackgroundSpec
import kotlinx.serialization.json.Json

/**
 * Фон хранится не цветом (`Int`), а описанием [BackgroundSpec]: JSON в колонке TEXT.
 * Прежний формат с цветом в отдельных столбцах не поддерживается и миграции для него нет:
 * по решению владельца обратная совместимость не нужна (приложение 0.1.0, данные
 * разработки можно сбросить).
 */
object BackgroundConverters {

    /**
     * `encodeDefaults = true`: id стратегии пишется в данные явно, чтобы смена дефолта
     * в коде позже не «переписала» уже сохранённые комбо. `ignoreUnknownKeys` — терпимость
     * к полям будущих версий.
     */
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @TypeConverter
    fun toJson(spec: BackgroundSpec): String = json.encodeToString(BackgroundSpec.serializer(), spec)

    /**
     * Мусор (в т.ч. неизвестный `type`) НЕ бросает, а даёт Auto — это защита от данных
     * новой версии приложения, а не миграция.
     */
    @TypeConverter
    fun fromJson(raw: String): BackgroundSpec =
        runCatching { json.decodeFromString(BackgroundSpec.serializer(), raw) }
            .getOrDefault(BackgroundSpec.Auto())
}