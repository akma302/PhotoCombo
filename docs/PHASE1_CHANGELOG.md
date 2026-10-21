# Фаза 1. Каркас KMP и слой данных: changelog и инструкция проверки

Код написан без компиляции (в моём окружении нет доступа к Maven и Gradle), поэтому ошибки сборки возможны. Это нормальный исход проверки: присылаете лог, я исправляю.

## Итог

Создан новый Kotlin Multiplatform модуль `composeApp` с целью desktop. В него перенесены `core`, `Const` и слой `data` (Room KMP), а также 10 чистых unit-тестов и 2 охранных теста. Добавлены фабрика БД для Windows и два новых теста. UI, ViewModel, импорт фото и навигации в новом проекте ещё нет, это фазы 2–3.

## Структура проекта

```
PhotoCombo/
  settings.gradle.kts            include(":composeApp")
  build.gradle.kts, gradle.properties
  gradle/libs.versions.toml      версии всех библиотек
  composeApp/
    build.gradle.kts
    src/commonMain/kotlin/com/combo/   Const, core/*, data/*
    src/desktopMain/kotlin/com/combo/data/DatabaseFactory.kt
    src/desktopTest/kotlin/com/combo/  12 перенесённых и 2 новых тестовых файла
  legacy-android/                прежнее приложение, отдельная Gradle-сборка, не изменено
  docs/                          план миграции и этот файл
  README.md, AGENTS.md, USER_GUIDE.md
```

Почему `legacy-android/` вынесен в отдельную сборку: старому `:app` нужны Kotlin 2.0.20, KSP и Hilt старых версий, новому модулю нужен Kotlin 2.3.20. Разные версии одних и тех же плагинов в одной Gradle-сборке не уживаются. В фазе 5 `legacy-android/` удаляется.

## Версии

| Что | Версия |
|---|---|
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.12.1 |
| KSP | 2.3.11 (с версии 2.3.3 нумерация не привязана к Kotlin) |
| Room | 2.8.5 |
| androidx.sqlite (`sqlite-bundled`) | 2.7.1 |
| kotlinx-coroutines | 1.11.0 (в нём `-swing` для Main-диспетчера и `-test`) |
| kotlinx-serialization-json | 1.11.0 |
| JUnit | 4.13.2 |

В фазу 1 не включены `material3`, Koin, lifecycle и навигация, они появятся там, где нужны.

## Что изменено в коде

1. **`core/color/Oklab.kt`**: `Math.pow`, `Math.round`, `Math.cbrt` заменены на `kotlin.math` (`pow`, `roundToInt`, `cbrt`). На JVM `roundToInt` для `Double` реализован через `Math.round`, поэтому результат совпадает.
2. **`core/playback/AutoScheduler.kt`, `SwitchAnimator.kt`**: `SystemClock::elapsedRealtime` заменён на новую функцию `monotonicMillis()` из `core/util/MonotonicClock.kt` (на `TimeSource.Monotonic`). Параметр `now` по-прежнему подменяется в тестах. Отличие: `elapsedRealtime` считал время сна устройства, для desktop это не важно.
3. **`core/util/AverageColor.kt`**: вместо `Bitmap.createScaledBitmap(1×1)` теперь среднее по всем пикселям `ImageBitmap` (взвешенное альфой, результат непрозрачный). **Изменение поведения:** старый способ был приблизительным и зависел от реализации на устройстве, новый точнее. Новые фото могут получить чуть другой средний цвет. Вызова `AverageColor` в новом проекте пока нет (он будет в импорте, фаза 2).
4. **`data/AppDb.kt`**: добавлены `@ConstructedBy(AppDbConstructor::class)` и `expect object AppDbConstructor`, как требует Room KMP. Реализацию генерирует компилятор Room.
5. **`data/BackgroundConverters.kt`**: `object` заменён на `class`. Для Room KMP это безопаснее, чем объект, так как документация конвертеры-объекты не описывает. В `BackgroundConvertersTest` одна строка: `BackgroundConverters` → `BackgroundConverters()`. Assertions не менялись.
6. **`ArchitectureRulesTest`**: проверяет исходники из `commonMain` и `desktopMain` вместо `src/main`. Правила слоёв не менялись. **`SourceHygieneTest`**: без изменений, кроме одного комментария.
7. **Добавлено:** `desktopMain/.../DatabaseFactory.kt` (`createAppDb`, `defaultAppDataDir`: `%APPDATA%\PhotoCombo\combo.db`, встроенный драйвер SQLite), `MonotonicClock.kt`, тесты `AppDbTest` и `AverageColorTest`.
8. **Не перенесено** (будет в следующих фазах): `PlaybackLifecycle` (нужен жизненный цикл окна, фаза 3), `PhotoStorage` и DI-модули (фазы 2–3).

## Тесты

В `desktopTest` 14 файлов, по моему подсчёту 89 тестовых методов:

| Файл | Тестов |
|---|---|
| AppDbTest (новый) | 8 |
| AverageColorTest (новый) | 3 |
| ArchitectureRulesTest, SourceHygieneTest | 6 + 3 |
| BackgroundConvertersTest, BackgroundStrategyTest | 5 + 16 |
| CanvasTransformTest, EntityExtTest, EnumCycleTest | 4 + 2 + 2 |
| ItemGesturesTest, SwitchMathTest, TransformMathTest | 9 + 9 + 6 |
| TransitionsTest, UndoStackTest | 12 + 4 |

Остальные тесты остаются в `legacy-android` и проверяют старый код. Они переедут вместе с кодом, от которого зависят: тесты редактора и ViewModel (`feature.builder`) в фазе 3, Robolectric-тесты (`DrawComboBackgroundTest`, `ItemFrameParityTest`, экранные) в фазе 4.

## Что проверить вам

Распакуйте архив в **новую** папку, не поверх старого проекта.

1. В PowerShell из корня проекта:
   ```powershell
   .\gradlew.bat --version
   ```
   Первый запуск скачает Gradle 9.3.0. Если команда падает, пришлите вывод целиком и `java -version`.
2. Основная проверка:
   ```powershell
   .\gradlew.bat :composeApp:desktopTest
   ```
   Ожидается `BUILD SUCCESSFUL`. Первая сборка долгая: скачиваются зависимости, запускаются KSP и Room.
3. Откройте отчёт `composeApp\build\reports\tests\desktopTest\index.html`. Ожидается около 89 тестов и **0 failures**. Расхождение на 1–2 в числе тестов не важно, красных быть не должно.
4. Проверьте, что появился файл `composeApp\schemas\com.combo.data.AppDb\1.json`. Он подтверждает, что Room сгенерировал схему.
5. Необязательно: убедитесь, что старое приложение не пострадало (`legacy-android` не менялся):
   ```powershell
   cd legacy-android
   .\gradlew.bat :app:testDebugUnitTest
   ```

**Если что-то упало**, пришлите лог:
```powershell
cmd /c ".\gradlew.bat :composeApp:desktopTest --stacktrace > phase1.log 2>&1"
```
Достаточно первых ошибок: строк с `e:` (ошибки Kotlin), `FAILED` или `What went wrong`.

## Где вероятны проблемы

| Симптом | Вероятная причина |
|---|---|
| `Unsupported Gradle version` или ошибка плагина Kotlin | Kotlin 2.3.20 может не поддерживать Gradle 9.3.0 из вашего wrapper |
| `Cannot find a Java installation` | в `gradle/gradle-daemon-jvm.properties` указан JDK 25 |
| `Plugin 'androidx.room' not found` | репозитории в `settings.gradle.kts`: пришлите лог |
| ошибки `kspDesktop`, например про `@Relation` | Room KMP: документация не подтверждала поддержку `@Relation`, это одна из главных проверок фазы |
| `e: Unresolved reference` | ошибка в моём коде без компиляции: пришлите строки |
| падение `AverageColorTest` или ошибка загрузки Skia | работа `ImageBitmap` в тестах без окна |
| падение `BackgroundStrategyTest` или Oklab | реальное расхождение после замены `Math` на `kotlin.math`: пришлите assertion |

## Что в этой фазе не проверяется

Приложение ещё нельзя запустить: окна, импорта фото и экранов нет. Это фазы 2 и 3.

## Решение на фазу 3

В Compose Multiplatform 1.12.1 библиотека `material3` имеет версию 1.12.0-alpha03, то есть alpha. Остальные части (runtime, foundation, ui) стабильны. В фазе 3 я проверю, какую версию `material3` брать, и опишу риск в её changelog.
