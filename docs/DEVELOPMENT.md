# Разработка и проверка PhotoCombo

Архитектура и граф модулей описаны в README, пользовательское поведение — в USER_GUIDE.

## Окружение и структура сборки

Работать из корня репозитория. `settings.gradle.kts` подключает 11 KMP-проектов
с целью desktop: core, data, domain, design-system, ui-common, четыре feature-модуля,
composeApp и test-support. Родитель `:feature` — только каталог группировки.

| Файл | Назначение |
|---|---|
| `gradle/libs.versions.toml` | Общие версии и зависимости |
| `gradle/wrapper/gradle-wrapper.properties` | Версия Gradle |
| `gradle/gradle-daemon-jvm.properties` | JVM Gradle daemon (JDK 25) |
| `build.gradle.kts` | Проверка графа, агрегатные тестовые задачи и системные свойства тестов |
| `<модуль>/build.gradle.kts` | Source sets и реальные зависимости модуля |
| `data/build.gradle.kts` | Room/KSP и экспорт схем в data/schemas |
| `composeApp/build.gradle.kts` | Запуск приложения и галереи |

Исходные Kotlin-пакеты сохранены. commonMain не импортирует платформенные API;
платформенные адаптеры находятся в desktopMain своих модулей. Типы публичных
контрактов экспортируются через api, детали подключаются через implementation.
SQLite API и Compose ripple явно выровнены на прежние версии из каталога:
после разделения compile classpath больше не получает выравнивание от desktop launcher.

Первый запуск может потребовать сеть для Gradle/JVM и зависимостей; --offline
подходит для заполненного кэша. JVM launcher и daemon могут различаться.
Строка Kotlin в gradlew --version относится к Gradle; версия компилятора проекта — в каталоге.

## Команды (Windows / PowerShell)

```powershell
.\gradlew.bat --version
.\gradlew.bat check --offline --console=plain
.\gradlew.bat verifyModuleGraph --offline --console=plain
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:runDesignSystemGallery
```

`check` выполняет desktopTest всех модулей, проверку графа и единый HTML-отчёт
`build/reports/tests/desktop/index.html`. Корневой `desktopTest` также запускает
все тесты; `desktopTestReport` добавляет единый отчёт. `:composeApp:desktopTest`
теперь проверяет только интеграцию приложения, а не весь проект.

Выборочные проверки во время разработки:

```powershell
.\gradlew.bat :design-system:desktopTest --offline --console=plain
.\gradlew.bat :feature:builder:desktopTest --tests '*DraftHistoryTest' --offline --console=plain
.\gradlew.bat :data:desktopTest --tests '*MigrationTest' --offline --console=plain
.\gradlew.bat :domain:desktopTest '-Prealphotos=C:\Photos' --console=plain
```

--tests применяется к конкретной Test-задаче модуля, а не корневому агрегатору.
RealPhotoImportTest остаётся опциональным и требует выбранного владельцем каталога;
без -Prealphotos он пропускается. Фикстуры и временные БД не используют личную библиотеку.

После изменения кода перед публикацией выполнить полный check. Уже зелёный прогон
без новых изменений или причин повторять не требуется. Для правок документации
достаточно проверки ссылок, путей, команд и git diff --check.

Лог проверки:

```powershell
.\gradlew.bat check --console=plain > "$env:TEMP\photocombo-check.log" 2>&1
$testExit = $LASTEXITCODE
Get-Content "$env:TEMP\photocombo-check.log" -Tail 30
```

run открывает приложение с настоящими данными в %APPDATA%\PhotoCombo. Галерея
работает без DI и БД. Не использовать личную библиотеку для разрушительных проверок.

## Тесты и отчёты

Тесты находятся у владельца кода в `<модуль>/src/desktopTest`. Общие фикстуры,
фейки, UiTestBase и TestScreen находятся в `test-support/src/desktopMain`;
тестовый модуль не входит в runtime classpath приложения. В composeApp остаются
сквозные проверки DI, навигации, паритета рендера, архитектуры и гигиены исходников.

ArchitectureRulesTest и SourceHygieneTest получают корень и список модулей из Gradle,
чтобы проверка не ограничилась оставшимися файлами composeApp. Внутренние правила
DS и draft/history продолжают проверяться тестами; межмодульный граф дополнительно
проверяет verifyModuleGraph. Добавление KMP-модуля без регистрации в графе — ошибка.

XML: `<модуль>/build/test-results/desktopTest/`. HTML каждого модуля:
`<модуль>/build/reports/tests/desktopTest/index.html`; общий — в корневом build/reports.
Считать тесты по всем модулям, а не по одному composeApp.

PNG создаются в build/ui-previews владельца: design-system для галереи,
feature/builder для редактора, feature/library, feature/combo-list и feature/viewer
для соответствующих экранов. ComponentGalleryTest использует настоящие компоненты
при ширинах 420/800/1200 dp, density 1/1.5 и fontScale 1/1.25.

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
- Независимые задачи и экраны — отдельные ветки и независимые PR непосредственно в
  `main`. Параллельное выполнение не является основанием объединять их в общий PR.
  Общая ветка и интеграция частей допустимы только для зависимых частей одной задачи
  при явно согласованной с владельцем схеме.
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

