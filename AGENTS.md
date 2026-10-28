# PhotoCombo: карта для агента

Desktop-приложение для Windows на KMP / Compose Multiplatform. Загружай только
материалы, относящиеся к задаче; обязательного порядка чтения документов нет.
Фактическое поведение и API ищи в коде и тестах, продуктовые изменения задаёт задача владельца.

## Где искать

Пути в таблице — от корня репозитория. В модуле общий код лежит в
`src/commonMain/kotlin/com/combo`, платформенный — в `src/desktopMain`, тесты — в `src/desktopTest`.
Названия файлов и символов ниже подходят для поиска через `rg --files` и `rg -n`.

| Нужная область | Место / точки входа |
|---|---|
| Фичи и пользовательские действия | [feature/](feature/): library, combo-list, viewer, builder; `*Screen.kt`, `*ViewModel.kt`; события редактора — `BuilderContract.kt` |
| Навигация, время жизни ViewModel, DI | [composeApp/](composeApp/src/): `AppNav.kt`, `ScreenKey.kt`, `ViewModelStoreOwners.kt`, `di/` |
| Общая галерея и размер элементов | [ui-common/](ui-common/src/commonMain/kotlin/com/combo/ui/common/): `GalleryGrid.kt`, `PhotoGrid.kt`, `GalleryScaleState.kt`; сохранение — `GallerySettingsRepository` и `GallerySettingsStorage` |
| Геометрия, вписывание, отрисовка | [core/render/](core/src/commonMain/kotlin/com/combo/core/render/): `Framing.kt`, `ItemFrames.kt`, `DrawItem.kt`, `DrawCombo.kt`; фон — соседний `background/` |
| Правки, Undo и сохранение combo | [builder/](feature/builder/src/commonMain/kotlin/com/combo/feature/builder/): `draft/`, `history/`, `FinalizeCanvas.kt`, `ComboBuilderViewModel.kt`; ввод — `EditorGestures.kt`, `EditorWheel.kt` |
| БД, файловые пути, миграции | [data/](data/src/): `Entities.kt`, `AppDb.kt`, `Migrations.kt`, `PhotoStorage.kt`; схемы — [data/schemas/](data/schemas/) |
| Репозитории, импорт и кэш изображений | [domain/](domain/src/commonMain/kotlin/com/combo/domain/): `Repositories.kt`, `Impls.kt`, `PhotoImporter.kt`, `PhotoAssets.kt`, `image/` |
| Компоненты и визуальные решения | [design-system/](design-system/src/); ориентиры и адреса токенов — [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md); работа с CMP UI — [cmp-ui](.agents/skills/cmp-ui/SKILL.md) |
| Сборка, проверки, отчёты и Git | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Модули, зависимости, версии, targets | `settings.gradle.kts`, корневой и модульные `build.gradle.kts`, `gradle/libs.versions.toml` |
| Архитектурные ограничения | `verifyModuleGraph` в корневом Gradle; `ArchitectureRulesTest` и `SourceHygieneTest` в composeApp |
| Фикстуры и UI-тестовый хост | [test-support/](test-support/src/desktopMain/); сценарии — тесты модуля владельца |
| Бизнес-лимиты и пороги | [Const.kt](core/src/commonMain/kotlin/com/combo/Const.kt) |

## Ограничения, которые нужно сохранить

- `commonMain` не импортирует JVM/Android/Skia API; адаптеры ОС находятся в `desktopMain`.
  Фичи независимы друг от друга, `ui-common` не импортирует фичи, test-support доступен только тестам.
  Точный граф проверяется кодом; не ослаблять проверки ради новой зависимости.
- Оригиналы фото не изменяются. Раскладка пользовательских файлов и сохранённые enum-имена
  совместимы с существующими данными; изменения схемы требуют миграции и её теста.
  Для тестов использовать временные каталоги вместо личной библиотеки.
- Редактор и превью используют единый рендер из core. Сохранение финализирует отдельный
  снимок: не меняет рабочий draft и Undo даже при сбое записи. Отмена жеста не создаёт checkpoint.
  Эти контракты искать в тестах `ItemFrameParityTest`, `DraftMapperTest`, `*HistoryTest`, `*Finalize*Test`.
- Исходники: UTF-8 без BOM, LF. Комментарии по-русски, о назначении кода.

## Как поддерживать эту карту

Добавляй указатель при появлении новой области, которую трудно обнаружить.
Не копируй сюда алгоритмы, сигнатуры, значения токенов, историю PR и результаты прогонов.
Команды принадлежат DEVELOPMENT, визуальные требования — DESIGN_SYSTEM; детали реализации — коду и тестам.
