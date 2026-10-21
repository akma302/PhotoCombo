# Отчёт о миграции PhotoCombo: Android → Kotlin Multiplatform + Compose Multiplatform (desktop, Windows)

Дата отчёта: 2026-10-16. Источники: `docs/KMP_MIGRATION_PLAN.md`, `docs/PHASE1_CHANGELOG.md`, фактические результаты прогонов Gradle. Отчёт ведётся по ходу миграции: фазы 1–4 ниже, фаза 5 ещё не начата.

## Статус фаз

| Фаза | Содержание | Статус |
|---|---|---|
| 1 | Каркас KMP-модуля и слой данных (Room KMP) | Закрыта, проверена пользователем |
| 2 | Платформенный слой и домен (кодек, хранилище, импорт, DI) | Закрыта, проверена пользователем (включая импорт реальных фото) |
| 3 | UI, навигация, ViewModel, `main()` | Код готов, тесты зелёные, дымовой запуск пройден; ждёт ручной проверки |
| 4 | Ввод мышью и клавиатурой, UI-тесты, охранные правила | Код готов, тесты зелёные, дымовой запуск пройден; ждёт ручной проверки |
| 5 | Упаковка, документация, удаление `legacy-android/` | Не начата |

Общее состояние: в корне проекта — одна Gradle-сборка с модулем `:composeApp` (KMP, таргет `desktop`). Прежнее Android-приложение лежит в `legacy-android/` отдельной сборкой, не изменяется и служит эталоном поведения до фазы 5.

## Команды проверки

```powershell
.\gradlew.bat :composeApp:desktopTest                 # весь набор тестов нового проекта
.\gradlew.bat :composeApp:run                         # запуск desktop-приложения
.\gradlew.bat :composeApp:desktopTest --tests "com.combo.RealPhotoImportTest" -Prealphotos="<папка с фото>"
cd legacy-android; .\gradlew.bat :app:testDebugUnitTest   # эталон: старое приложение всё ещё собирается и тестируется
```

## Фаза 1. Каркас и слой данных

Что сделано:

- Создан модуль `:composeApp` (Kotlin Multiplatform, `jvm("desktop")`, Kotlin 2.3.20, Compose Multiplatform 1.12.1, KSP 2.3.11).
- Перенесены `Const` и весь `core/*` в `commonMain` с заменами JVM-зависимостей: `SystemClock` → `TimeSource.Monotonic` (`MonotonicClock.kt`), `Math.*` → `kotlin.math`, `System.currentTimeMillis` → инъецируемые часы.
- `core/util/AverageColor.kt` переписан на `ImageBitmap.readPixels` (средний цвет теперь считается точнее, чем прежним `createScaledBitmap(1×1)`; новые фото могут получить чуть другой `avgColor`).
- Перенесён слой `data`: сущности, DAO, `AppDb` с `@ConstructedBy`/`expect object` (требование Room KMP), конвертер фона.
- Добавлена фабрика БД для Windows: `desktopMain/.../DatabaseFactory.kt`, файл `%APPDATA%\PhotoCombo\combo.db`, встроенный драйвер SQLite.
- Перенесены чистые JVM-тесты без изменения assertions; добавлены `AppDbTest` (файл БД, вставка, чтение с `@Relation`, транзакция) и `AverageColorTest`.
- `ArchitectureRulesTest` перенастроен на исходники `commonMain`/`desktopMain`; `SourceHygieneTest` перенесён.

Почему `legacy-android/` вынесен отдельно: старому `:app` нужны Kotlin 2.0.20, KSP и Hilt старых версий, новому модулю — Kotlin 2.3.20; в одной сборке эти версии не уживаются.

Проверка фазы: `.\gradlew.bat :composeApp:desktopTest` — зелёный (89 тестов на момент закрытия), схема Room сгенерирована (`composeApp/schemas/com.combo.data.AppDb/1.json`), старое приложение не пострадало. Закрыта.

## Фаза 2. Платформенный слой и домен

Что сделано:

- Интерфейсы в `commonMain`: `ImageSource` (имя, mime, поток — вместо `Uri`), `ImageCodec`, `ImagePicker`, `AppDirs`, `Logger`, `WallClock`, `IdGenerator`. Файловая работа — через Okio (`FileSystem` внедряется).
- Desktop-реализации: `SkiaImageCodec` (границы, декод с сэмплингом, EXIF через `encodedOrigin`, поворот и отражение, JPEG/PNG/WebP), `FileImageSource`, `DesktopImagePicker` (AWT `FileDialog` с multi-select), `DesktopLogger`, `DesktopAppDirs`/`DefaultAppDataDir`, `DatabaseFactory`.
- Переписаны под новые типы: `PhotoStorage` (раскладка `photos/` и `thumbs/` не менялась), `PhotoImporter` (EXIF нормализуется в пиксели при импорте, `avgColor` считается один раз), `LruBitmapSource` (свой LRU, `Mutex`, явный лимит кэша), `PhotoRepositoryImpl`, `ComboRepositoryImpl` (одна транзакция на конфиг), `PhotoAssets`.
- Koin-модули: `appModule` (домен и репозитории) и `desktopModule` (платформенные реализации).
- Тесты: домен — на фейках; кодек — на генерируемых фикстурах (8 вариантов EXIF-ориентации, PNG, WebP, большое фото для сэмплинга); ветки TRANSPOSE/TRANSVERSE, которые в старом коде были непроверенными, закрыты тестом; переписаны `PhotoImporterTest`, `LruBitmapSourceTest`, тесты кэша. Добавлен `RealPhotoImportTest` — импорт реальных фотографий из каталога, задаваемого ключом `-Prealphotos` (без него тест пропускается).

Проверка фазы (пользователь): `desktopTest` зелёный — 142 теста, 0 падений, 1 пропущенный (`RealPhotoImportTest`); отдельно прогнан импорт реальных фото с телефона (портретная ориентация, одно очень большое) — импорт прошёл, ориентация верная. Закрыта 2026-10-16.

## Фаза 3. UI, навигация и первый запуск

Что сделано:

- Перенесены все экраны и ViewModel из legacy: `feature.library`, `feature.viewer`, `feature.combo_list`, `feature.combo_viewer`, `feature.builder` (включая `draft/`, `history/`), общие компоненты в `ui.common` (`PhotoGrid`, `ThumbImage`, `CollectMessages`, `BackNavIcon`).
- Hilt заменён на Koin: `di/ViewModelModule.kt` с фабриками ViewModel; `SavedStateHandle` собирается вручную из ключа навигации (в KMP на desktop нет реестра, из которого Koin достал бы его сам; с поправкой фазы 4 — обёртка `EntryArgs`), поэтому конструкторы ViewModel и их тесты не менялись.
- Навигация на Navigation 3: ключи `ScreenKey.Library`, `Combos`, `Viewer(photoId)`, `Builder(comboId?, photos)`, `Player(comboId)`; нижняя панель вкладок показывается только на двух корневых экранах; переключение вкладки повторяет поведение `navigateTop + singleTop`.
- Отклонение от плана: декоратор `lifecycle-viewmodel-navigation3` не использован. Вместо него — собственные владельцы ViewModel: `rememberEntryViewModelStoreOwner()` для каждой записи стека и `rememberRootViewModelStoreOwner()` для вкладок Library/Combos (`ui/ViewModelStoreOwners.kt`). Причина: `koinViewModel` на одном владельце окна возвращал бы один и тот же экземпляр всем записям одного класса — билдер второго комбо открылся бы поверх состояния первого. Побочная польза: состояние вкладок при переключении не сбрасывается (пункт «Отличие от Android» из плана решён закреплением ViewModel за окном).
- `BackDispatcher` (единый LIFO-стек обработчиков «назад») лежит в `ui.common`: Esc обрабатывается на уровне окна (`onPreviewKeyEvent` в `main.kt`) и сначала попадает в верхний обработчик — режим выбора в Library, выход из редактора, `ExitDialog`; если никто не взял, срабатывает нижний обработчик — шаг назад по стеку. Первоначально `BackDispatcher` лежал в `ui`, но `ArchitectureRulesTest` поймал цикл `ui ↔ feature` (экраны импортировали `RegisterBackHandler`); перенос в `ui.common` цикл устранил.
- `PlaybackLifecycle` (пауза при сворачивании окна): `LocalWindowState` из Compose оказался недоступен, поэтому введён собственный `LocalWindowMinimized`, который `main.kt` заполняет из `WindowState.isMinimized`; плеер останавливается при сворачивании и возобновляется при разворачивании.
- `main.kt`: старт Koin (`appModule` + `desktopModule` + `viewModelModule`), окно 480×860 в телефонной пропорции, минимальный размер 420×700, заголовок PhotoCombo, `MaterialTheme`.
- `build.gradle.kts`: добавлен блок `compose.desktop { application { ... } }` — появилась задача `:composeApp:run`.
- Добавлены зависимости, которых не было в фазах 1–2: `material3` (из CMP 1.12.1, версия 1.12.0-alpha03 — риск, помеченный в фазе 1, закрыт: собирается и работает), `material-icons-extended` 1.7.3 (пять недостающих иконок не пришлось рисовать вручную), lifecycle 2.11.0, `navigation3-ui` 1.1.2, `koin-compose`/`koin-compose-viewmodel` 4.2.2.
- Осознанные изменения относительно legacy: `LibraryViewModel` принимает `ImagePicker` (`import()` без `Uri`); `ComboBuilderViewModel` принимает `IdGenerator`, `WallClock` и IO-диспетчер (сохранение: `ids.newId()`, `clock.nowMillis()`, `launch(ioDispatcher)`); `ThumbImage` декодирует через `ImageCodec` (нет `BitmapFactory`); `collectAsState` вместо `collectAsStateWithLifecycle`; haptic-отклик отсутствует.

Проверка фазы:

1. `.\gradlew.bat :composeApp:desktopTest` — 237 тестов, 0 падений, 1 пропущенный (`RealPhotoImportTest` без `-Prealphotos`), включая `ArchitectureRulesTest` (слои и отсутствие циклов) и `SourceHygieneTest`.
2. Дымовой запуск `.\gradlew.bat :composeApp:run` — окно стартует, Koin и навигация поднимаются без исключений.
3. Ручной сценарий за пользователем: импорт фото → открытие фото на весь экран → создание комбо из выбранных → сохранение → список комбо → открытие в редакторе → воспроизведение → удаление фото (фото из комбо должно блокироваться) → выход с несохранёнными изменениями; Esc — как системная кнопка «назад».

## Фаза 4. Ввод мышью и клавиатурой, UI-тесты, охранные правила

Что сделано:

- Соответствия жестов из плана реализованы:
  - щипок — колесо мыши (`EditorWheel.kt`): в EDIT невыделенная рамка масштабируется целиком, у выбранной в BORDER — только её фото, по пустому месту — вид; Ctrl+колесо — всегда вид; центр масштаба — в точке курсора;
  - свайп влево/вправо (просмотр фото, плеер) — стрелки ←/→; в плеере и редакторе — ещё и перетаскивание мышью;
  - быстрый вертикальный свайп по рамке — перетаскивание, плюс PageUp/PageDown (редактор — смена фото в рамке, плеер — переключение);
  - двойное касание — двойной клик (смена режима EDIT/BORDER в редакторе);
  - долгое нажатие (мультивыбор) — правый клик или Ctrl+клик по ячейке;
  - «назад» — Esc (на уровне окна) и боковая кнопка мыши (`isBackPressed` в `BackKeyHost`).
- Колесо шлёт `START/CHANGE/END` как обычные жесты: серия тиков — один undo-шаг (снимок на первом тике, checkpoint на `END` по паузе 250 мс). Отдельный тест — `EditorWheelTest.wheelSeriesOnFrameZoomsItAsASingleUndoStep`.
- 11 Robolectric-классов старого приложения переписаны на desktop-тесты:
  - экранные (Compose UI Test, `runComposeUiTest`): `LibraryMultiselectTest`, `LibraryOpensTappedPhotoTest`, `ComboListOpensBuilderTest`, `BuilderExitDialogTest`, `ComboBuilderToolbarTest`, `ComboBuilderGesturesTest`; `Espresso.pressBack` → Esc (`pressEsc()`), ввод — `performMouseInput`;
  - попиксельные на Skia без Robolectric: `DrawComboBackgroundTest`, `ItemFrameParityTest`;
  - ViewModel-уровня: `PlayerAndCacheTest` → `ComboViewerViewModelTest` (тикер: автосмена, сон, пробуждение, лишние перерисовки) + `HitTestItemTest` (верхняя рамка при перекрытии); сценарий единиц размера записи кэша уже был покрыт `LruBitmapSourceTest`;
  - `ComboBuilderViewModelTest` и `GestureClassifierTest` перенесены ещё в фазе 3 как чистые тесты.
- Общий каркас UI-тестов: `UiTestBase` (диспетчеры, Koin, backDispatcher), `TestBackHost.kt` (`uiTestKoin`/`TestScreen`/`pressEsc`), `TestDoubles.kt`; добавлен `BackKeyHostTest` (Esc и боковая кнопка на уровне окна).
- Многопальцевые сценарии касаний (второй палец посреди жеста, медленный щипок) мышью не воспроизводятся: роль щипка занимает колесо (`EditorWheelTest`), два сценария второго пальца отброшены — причины описаны в KDoc `ComboBuilderGesturesTest` (9 legacy-тестов → 4 жестами + 3 колесом + 2 отброшено).
- `ComboViewerViewModel` принимает часы (`now`, по умолчанию `monotonicMillis`) для `SwitchAnimator` и `AutoScheduler`: тесты плеера двигают виртуальным временем и переходы, и таймеры. В skiko-тестах задержки pointerInput-корутин продвигает только `mainClock.advanceTimeBy` (пауза серии колеса, таймаут двойного тапа) — задокументировано в тестах.
- Охранное правило в `ArchitectureRulesTest`: `commonMain` не импортирует `android.*`, `java.*`, `javax.*`, `org.jetbrains.skia.*` (+ самопроверки правила).
- Падение при переходе «Create combo» из библиотеки (найдено на ручной проверке): Koin не читает `SavedStateHandle` из `parametersOf` — при таком типе параметра фабрики он строит handle из `CreationExtras`, где на desktop нет `SavedStateRegistryOwner`. Handle передаётся обёрткой `EntryArgs` (`di/ViewModelModule.kt`); регрессия — `AppNavViewModelTest` (Library → Builder и ComboList → Player через настоящий `AppNav`).
- Доработки по ручной проверке (план — `docs/IMPROVEMENTS_PLAN.md`): починен ресайз за грань (`nearEdge` сравнивал канвасные координаты с экранными — работал только у граней с канвасной координатой ≈ 0; регрессия — `ComboBuilderGesturesTest.resizingBorderKeepsWorkingOnRepeatedDrags`, юнит-тесты `NearEdgeTest`); добавлена обрезка по границам холста в `drawCombo` (пиксельный тест `DrawComboClipTest`, оверлей выделения обрезан на стороне экрана); добавлена кнопка «+Frame» (`DraftEditor.addItem`, `BuilderEvent.AddItem`, `TOOL_TAG_ADD_ITEM` — рамка по центру, 50 %×50 %, `FREE`, верхний слой, сразу выделена; на лимите `MAX_ITEMS` кнопка выключена).

Проверка фазы:

1. `.\gradlew.bat :composeApp:desktopTest` — 331 тест, 0 падений, 1 пропущенный (`RealPhotoImportTest` без `-Prealphotos`).
2. Дымовой запуск `.\gradlew.bat :composeApp:run` — окно стартует без исключений.
3. Ручной список за пользователем (из плана): пункты таблицы ввода; в редакторе — перетаскивание рамки, изменение границы, сдвиг и масштаб содержимого колесом, Undo/Redo после жестов, режимы EDIT и BORDER, AutoFit, Fit, Fill, смена фона; в плеере — переходы и автосмена; отдельно — удобство чувствительности колеса и порога перетаскивания.

## Тесты нового проекта (итог)

49 классов, 344 теста, 0 падений, 1 пропущенный. По фазам:

| Класс | Тестов | Фаза |
|---|---|---|
| AppDbTest | 10 | 1 (+2 в фазе 2) |
| ArchitectureRulesTest | 6 | 1 (+правило `commonMain` в фазе 4) |
| AverageColorTest | 3 | 1 |
| BackgroundConvertersTest | 5 | 1 |
| BackgroundStrategyTest | 16 | 1 |
| CanvasTransformTest | 4 | 1 |
| EntityExtTest | 2 | 1 |
| EnumCycleTest | 2 | 1 |
| ItemGesturesTest | 9 | 1 |
| SourceHygieneTest | 3 | 1 |
| SwitchMathTest | 9 | 1 |
| TransformMathTest | 6 | 1 |
| TransitionsTest | 12 | 1 |
| UndoStackTest | 4 | 1 |
| AppModulesTest | 2 | 2 |
| ComboRepositoryTest | 5 | 2 |
| ExifOrientationTest | 11 | 2 |
| LruBitmapSourceTest | 6 | 2 |
| PhotoImporterTest | 10 | 2 |
| PhotoRepositoryTest | 5 | 2 |
| RealPhotoImportTest | 1 (skip без флага) | 2 |
| SkiaImageCodecTest | 11 | 2 |
| BackDispatcherTest | 5 | 3 |
| ComboBuilderViewModelTest | 14 | 3 (+2 в доработках после фазы 4) |
| DraftEditorTest | 40 | 3 (+2 в доработках после фазы 4) |
| DraftHistoryTest | 13 | 3 |
| DraftMapperTest | 4 | 3 |
| GestureClassifierTest | 11 | 3 |
| ViewportControllerTest | 12 | 3 |
| AppNavViewModelTest | 2 | 4 |
| AspectRelayoutTest | 11 | 4 |
| AutoFitTest | 19 | 4 |
| BackKeyHostTest | 2 | 4 |
| BuilderExitDialogTest | 9 | 4 |
| ComboBuilderBackgroundTest | 6 | 4 |
| ComboBuilderGesturesTest | 5 | 4 (+1 в доработках) |
| ComboBuilderToolbarTest | 5 | 4 (+1 в доработках) |
| ComboListOpensBuilderTest | 1 | 4 |
| ComboViewerViewModelTest | 6 | 4 |
| DrawComboBackgroundTest | 3 | 4 |
| DrawComboClipTest | 1 | 4 (доработки) |
| EditorWheelTest | 4 | 4 |
| HitTestItemTest | 1 | 4 |
| ItemFrameParityTest | 3 | 4 |
| LibraryMultiselectTest | 9 | 4 |
| LibraryOpensTappedPhotoTest | 1 | 4 |
| NearEdgeTest | 6 | 4 (доработки) |
| PlaybackIndexSyncTest | 2 | 4 |
| ViewportTest | 7 | 4 |

Число тестов по завершении фаз: фаза 1 — 89, фаза 2 — 142, фаза 3 — 237, фаза 4 — 331 (0 падений, 1 пропущенный); после доработок по итогам ручной проверки — 344 (49 классов). Все Robolectric-тесты старого приложения перенесены; в `legacy-android/` тесты остаются только как эталон до фазы 5.

## Изменения поведения относительно Android-версии

Накоплено к текущему моменту:

- `avgColor` считается точнее (фаза 1); миниатюры и сэмплинг могут отличаться на 1 px (фаза 2).
- Кэш битмапов: лимит задаётся явно, а не долей `maxMemory`.
- Haptic-отклик отсутствует.
- База данных с Android на desktop не переносится.
- Состояние вкладок Library/Combos при переключении НЕ сбрасывается (в отличие от опасения в плане — решено закреплением ViewModel за окном).
- `material3` имеет статус alpha (1.12.0-alpha03); собирается и работает, но риск остаётся.
- Ввод мышью/клавиатурой вместо касаний (фаза 4): щипок — колесо (+Ctrl), свайпы — стрелки/перетаскивание, долгое нажатие — правый/Ctrl-клик, «назад» — Esc/боковая кнопка мыши; многопальцевые жесты (второй палец посреди жеста) на desktop отсутствуют — их роль выполняет колесо.

## НЕ ПРОВЕРЕНО / открытые вопросы

- Esc внутри `AlertDialog` из material3 на desktop: диалог — отдельное окно и, вероятно, сам обрабатывает Esc (`Keep editing`); собственный обработчик зарегистрирован как страховка. Поведение подтверждается ручной проверкой.
- Ctrl+колесо (всегда вид) и боковая кнопка мыши не воспроизводятся UI-инжектором skiko-тестов (события без модификаторов клавиатуры; только Primary/Secondary/Tertiary) — проверяются только руками.
- Чувствительность колеса (шаг 1.1) и порог перетаскивания — оцениваются на ручной проверке фазы 4.

## Что дальше

- Фаза 4 закрывается после ручной проверки пользователем (список выше).
- Фаза 5: упаковка (`createDistributable`), обновление `README.md`/`AGENTS.md`/`USER_GUIDE.md`, удаление `legacy-android/`.

## Упрощение модели: «одна рамка — одно фото»

Решение владельца после ручной проверки фазы 4: убрать таймеры, переходы, анимации, ручное переключение и плеер; одна рамка показывает одно фото. Что именно изменено, какие тесты удалены, переписаны и добавлены, как проверить — в `docs/PHASE5_SIMPLIFY_CHANGELOG.md`. Схема БД — версия 2 (миграция `MIGRATION_1_2`).
