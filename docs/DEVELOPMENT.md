# Разработка и проверка PhotoCombo

Действующая инструкция для активной desktop-сборки. Архитектура описана в README,
пользовательское поведение — в USER_GUIDE.

## Окружение

Работать из корня репозитория. `settings.gradle.kts` подключает только `:composeApp`.

Источники версий, которые нужно проверять при изменениях:

| Файл | Что определяет |
|---|---|
| `gradle/libs.versions.toml` | Kotlin, Compose Multiplatform, Room, Koin и остальные зависимости |
| `gradle/wrapper/gradle-wrapper.properties` | Версию Gradle wrapper |
| `gradle/gradle-daemon-jvm.properties` | JVM Gradle daemon (сейчас JDK 25) |
| `composeApp/build.gradle.kts` | Source sets, зависимости, задачи запуска и экспорта схем Room |

Первый запуск может потребовать сеть для Gradle/JVM и
зависимостей; `--offline` использовать только при наличии нужного кэша.
JVM launcher и daemon могут различаться: для сборки учитывать строку Daemon JVM.
Строка Kotlin в `gradlew --version` относится к самому Gradle; версию компилятора
проекта определяет `gradle/libs.versions.toml`.

## Команды (Windows / PowerShell)

```powershell
.\gradlew.bat --version
.\gradlew.bat :composeApp:desktopTest --console=plain
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:runDesignSystemGallery
```

`run` открывает продукт с настоящими пользовательскими данными; галерея запускает
отдельное окно без DI, базы и личных фото. Не использовать личную библиотеку для
разрушительных проверок. Пути данных задаёт `defaultAppDataDir()`:
на Windows это `%APPDATA%\PhotoCombo`.

Для локальной проверки с заполненным кэшем:

```powershell
.\gradlew.bat :composeApp:desktopTest --offline --console=plain
.\gradlew.bat :composeApp:desktopTest --tests '*designsystem*' --offline --console=plain
```

Выборочный запуск полезен во время разработки. После изменения кода перед публикацией
выполнить полный `desktopTest`. Повторять уже зелёный прогон без новых изменений
или причин не требуется. Для изменений только документации проверить ссылки,
пути, актуальность команд и `git diff --check`; UI-тесты без изменения кода не нужны.

Сохранение полного лога (перенаправление допустимо для лога, не для исходников):

```powershell
.\gradlew.bat :composeApp:desktopTest --console=plain > "$env:TEMP\photocombo-desktop-test.log" 2>&1
$testExit = $LASTEXITCODE
Get-Content "$env:TEMP\photocombo-desktop-test.log" -Tail 30
# Проверить $testExit: 0 означает успешное выполнение Gradle.
```

При ошибке читать полный лог; не судить о результате по последней строке предупреждения.

## Какие проверки существуют

Тесты: `composeApp/src/desktopTest/kotlin/com/combo/`. Общие фейки находятся в
`TestDoubles.kt` и `ui/TestDoubles.kt` внутри этого дерева; UI-хост — `ui/UiTestBase.kt`.

- JVM-проверки геометрии, drafts/history, ViewModel и репозиториев.
- Room с временной базой; Skia-кодек, EXIF и попиксельный рендер на фикстурах.
- Compose desktop UI Test: ввод, фокус, роли, callbacks и состояния.
- `ArchitectureRulesTest` и `SourceHygieneTest`: слои, переносимость, граф DS и чистота исходников.
- `ComponentGalleryTest`: настоящие компоненты при ширинах 420/800/1200 dp,
  density 1.0/1.5 и fontScale 1.0/1.25. PNG и `font.txt` создаются в
  `composeApp/build/ui-previews/design-system/` и не коммитятся.

XML: `composeApp/build/test-results/desktopTest/`.
HTML-отчёт: `composeApp/build/reports/tests/desktopTest/index.html`.
Число тестов получать из текущего прогона, а не копировать из старых отчётов.

`RealPhotoImportTest` опционален и пропускается без явно заданного каталога:

```powershell
.\gradlew.bat :composeApp:desktopTest '-Prealphotos=C:\Photos' --console=plain
```

Этот запуск требует выбранного владельцем каталога. Синтетические фикстуры остаются
основой воспроизводимой проверки; пропущенный тест реальных фото указывать в отчёте.

## Визуальная проверка

Для UI проверить подходящие состояния: normal/hover/pressed/focus/disabled/busy/error,
мышь и клавиатуру, узкую ширину, масштаб текста, плотность и RTL при необходимости.
Декоративные изменения не должны менять геометрию, hit area или число callbacks.

Различать свидетельства: успешная сборка, автоматические действия, просмотр PNG,
ручные действия в работающем приложении. Снимки галереи не доказывают правильность
всего builder; density сцены не переключает системный DPI. Computer use применять
только для поведения, которое нельзя достаточно проверить кодом и снимками;
ориентир владельца — 1–2 обращения на задачу.

## Git и завершение задачи

- Сначала проверить `git status`, текущую ветку и базу задачи. Не терять чужие правки.
- Именование веток: `bug_<task id>_<name>` или `feature_<task id>_<name>`;
  перед выбором номера просмотреть существующие ветки и контекст задачи.
- Простая задача — отдельная ветка. Для составной задачи с согласованной схемой:
  общая ветка → ветки частей от неё → интеграция в общую → итоговый PR в `main`.
- Параллельные исполнители работают в отдельных worktree, без переключения ветки
  в общей рабочей копии. Заранее согласовать общие API и распределить файлы.
  Делегировать только при авторизации; согласованные для процесса UI ограничения —
  не более двух параллельных субагентов, GPT-6.1 Sol, Medium.
- После проверок коммитить осмысленную часть работы, делать push веток задачи
  и открывать/обновлять PR при доступности GitHub. Не публиковать credentials в логах.
- PR описывает итоговое поведение, область изменений, проверки и оставшиеся ограничения.
  Временные worktree убирать после сохранения результатов; ветки не удалять автоматически.
- После сообщения владельца о merge переходить на чистый локальный `main` и выполнять
  `git pull --ff-only`. При расхождении веток не делать reset и не терять локальные коммиты.

