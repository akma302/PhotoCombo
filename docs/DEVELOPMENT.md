# Разработка PhotoCombo

## Запуск и проверка

Команды PowerShell из корня проекта:

```powershell
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:runDesignSystemGallery
.\gradlew.bat check --offline --console=plain
.\gradlew.bat :feature:builder:desktopTest --tests '*DraftHistoryTest' --offline --console=plain
.\gradlew.bat verifyModuleGraph --offline --console=plain
```

`--offline` подходит для заполненного кэша; первая сборка может потребовать сеть.
Версии launcher/wrapper/daemon — в `gradle/`, зависимости — в каталоге версий.
При проблемах с окружением: `.\gradlew.bat --version` и `gradle/gradle-daemon-jvm.properties`.

`run` открывает настоящие данные в `%APPDATA%\PhotoCombo`; галерея компонентов не использует БД.
Личные фотографии для тестов нужны только по явному выбору владельца:
`RealPhotoImportTest` без `-Prealphotos=<каталог>` пропускается.

## Что проверять и где результат

- Логика: `desktopTest` модуля владельца; `--tests` задаётся конкретной задаче модуля.
  Корневой `check` запускает тесты всех модулей и проверку графа; `:composeApp:desktopTest` — только интеграция.
- Перед публикацией кода выполнить полный `check`. Повторять успешный прогон без изменений
  или новых причин не требуется. Для документации достаточно проверки ссылок, путей, команд и `git diff --check`.
- Общий отчёт: `build/reports/tests/desktop/index.html`; XML: `<модуль>/build/test-results/desktopTest/`.
  Снимки CMP: `<модуль>/build/ui-previews/`. Фикстуры и фейки — в test-support.
- Для UI выбирать проверки по риску изменения: [cmp-ui](../.agents/skills/cmp-ui/SKILL.md).
  Сборка, автоматические действия, просмотр PNG и ручная проверка окна — разные свидетельства.

## Git

- Проверить рабочую копию и базу задачи. Ветка: `feature_<id>_<name>` или `bug_<id>_<name>`;
  свободный номер искать среди существующих веток.
- Независимые задачи — отдельные PR в main. Общая ветка для зависимых частей — при согласовании владельцем.
  Делегирование требует авторизации; если оно согласовано, отдельные worktree,
  не более двух параллельных субагентов, GPT-6.1 Sol / Medium.
- Проверенную работу закоммитить, опубликовать ветку и открыть PR при доступности GitHub.
  Описание PR содержит итоговое поведение, проверки и ограничения.
- После сообщения о merge перейти на чистый main и выполнить `git pull --ff-only`.
  Локальные ветки автоматически не удалять.
