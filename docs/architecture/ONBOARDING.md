# PhotoCombo: архитектура для нового разработчика

Разбор текущей реализации на **14 ноября 2026**, commit **`7351a27b9daa140471cae8295a4aca88441ca572`**. Это описание исходников, а не проект будущего рефакторинга. Поведение, которое видно только при запуске, не считается доказанным одним чтением кода.

Начните с [просмотрщика атласа](open-atlas.html): выбор глав, масштаб, поиск и выключатель анимации работают локально. [Единый SVG](PhotoCombo-Architecture.svg) содержит все 15 листов, включая каталог 135 Main-файлов. Главы также доступны отдельными SVG. [SOURCE_INDEX.md](SOURCE_INDEX.md) перечисляет каждый Main-файл, top-level объявления, их модификаторы, внутренние импорты и все 78 файлов тестов. `test-support` включён в Main-каталог, но не в production-приложение.

## 1. Что делает приложение

PhotoCombo — локальная библиотека фотографий и редактор коллажей для desktop. Пользователь импортирует фотографии, выбирает их для нового combo, редактирует рамки и положение фото, сохраняет конфигурацию и экспортирует сохранённый combo в PNG.

Полезно сразу различать пять объектов:

| Объект | Что содержит | Где живёт |
|---|---|---|
| Исходный выбранный файл | Байты фотографии вне библиотеки | У пользователя; импорт читает его, не перезаписывает |
| Библиотечная копия | Фото, при необходимости нормализованное по EXIF | `photos/<fileName>` |
| Сохранённый combo | Размер холста, фон, геометрия рамок, ссылки на фото | `combos` + `combo_items` в Room |
| Рабочий draft | Изменяемые рамки текущего редактора | `BuilderUiState.items`, память ViewModel |
| Экспортированный PNG | Пиксели сохранённого combo | Отдельный выбранный пользователем файл |

Сохранение combo не запекает рамки в одну картинку. Конфигурацию можно открыть снова. PNG — отдельная операция, которая читает сохранённую конфигурацию. Редактирование не изменяет библиотечные фото; однако **при импорте библиотечная копия может быть перекодирована для нормализации EXIF**. «Неразрушающее редактирование» не означает побайтовую идентичность всех копий исходнику. См. [PhotoImporter][importer], [Entities][entities], [ComboPngExporter][exporter].

## 2. Как устроены модули на самом деле

Откройте [схему модулей](01-modules.svg). Матрица справа перечисляет все прямые project-зависимости, включая `api` и `implementation`; лестница слева намеренно показывает только основной маршрут.

| Модуль | Ответственность | Зависимости production |
|---|---|---|
| `:composeApp` | Desktop entry point, DI, навигация, владельцы ViewModel | Все девять остальных production-модулей через `implementation` |
| Четыре `:feature:*` | Экран, ViewModel, правила своей фичи | `api(core, data, domain)`; `implementation(design-system, ui-common)` |
| `:ui-common` | Предметные галереи, back/input, общие UI-помощники | `api(data, design-system)`; `implementation(core, domain)` |
| `:domain` | Репозитории, импорт, кэши изображений, экспорт | `api(core, data)` |
| `:data` | Room, сущности, мапперы, файловая раскладка, настройки | `api(core)` |
| `:core` | Модель, геометрия, рендер, фон, платформенные порты и desktop-адаптеры | Нет project-зависимостей |
| `:design-system` | Токены, тема, примитивы, компоненты | Нет project-зависимостей |
| `:test-support` | Фейки, изображения для тестов, UI-хост | `api(core, data, domain, design-system, ui-common)`; потребители подключают его только в тестах |

Здесь нет изолированного domain-слоя, который полностью скрывает storage-модель. `PhotoRepository` возвращает `PhotoEntity`, а `ComboRepository` — `ComboWithItems`, `ComboEntity` и `ComboItemEntity` из `:data`. Поэтому изменение Room-сущностей потенциально затрагивает фичи. Отдельных production use-case классов для каждого действия нет: ViewModel вызывают репозитории и конкретные сервисы напрямую.

`api` раскрывает зависимость потребителю модуля. `implementation` скрывает её из публикуемого compile API, но не меняет видимость Kotlin-классов на `private`. Это разные механизмы. Реальные границы держатся на сочетании Gradle, модификаторов Kotlin и проверок импортов.

Все зарегистрированные модули сейчас имеют цель **`jvm("desktop")`**. Общий код расположен в `commonMain`, адаптеры AWT/Skia/JVM — в `desktopMain`. Это создаёт швы для переноса, но Android-, iOS- и web-цели здесь не настроены. `core` использует Compose-графику; слово «core» не означает отсутствие зависимости от UI-библиотек.

Точный граф закреплён в [root build.gradle.kts][root-build] и сверяется задачей `verifyModuleGraph`. [ArchitectureRulesTest][arch-test] проверяет расположение пакетов, запрещённые импорты, циклы между пакетами, независимость feature-модулей, внутреннее направление дизайн-системы и отсутствие прямых импортов JVM/Android/Skia в `commonMain`. Это проверка исходных импортов, не универсальный анализ всех способов обращения к платформенным API.

## 3. Запуск, внедрение зависимостей и подмена реализаций

[main()][main] вызывает `startKoin`, подключает `appModule`, `desktopModule()` и `viewModelModule`, затем создаёт Compose Window. Обёртки темы приводят к `AppNav`. Один `BackDispatcher` создаётся через `remember` рядом с окном.

[Схема контрактов](02-contracts.svg) отделяет интерфейсы от конкретных реализаций. Стрелка реализации направлена **к интерфейсу**.

| Контракт / объект | Реализация или способ создания | Регистрация / владелец |
|---|---|---|
| `PhotoRepository` | `PhotoRepositoryImpl` | `single` в appModule |
| `ComboRepository` | `ComboRepositoryImpl` | `single` в appModule |
| `GallerySettingsRepository` | `GallerySettingsRepositoryImpl` | `single` в appModule |
| `GallerySettingsStorage` | `FileGallerySettingsStorage` | `single` в appModule |
| `PhotoStorage`, `PhotoImporter`, `ComboPngExporter` | Конкретные классы без отдельного интерфейса сервиса | `single` в appModule |
| `BitmapSource` | `LruBitmapSource` | `single` в desktopModule |
| `ImageCodec` | `SkiaImageCodec` | `single` в desktopModule |
| `ImagePicker` | `DesktopImagePicker` | `single` в desktopModule |
| `ImageSource` | `FileImageSource` | По объекту на выбранный файл; не `single` |
| `PngOutput` | `DesktopPngOutput` | `single` в desktopModule |
| `AppDirs`, `Logger` | `DesktopAppDirs`, `DesktopLogger` | `single` в desktopModule |
| `WallClock`, `IdGenerator` | SAM-лямбды системного времени и UUID | `single` в desktopModule |
| `FileSystem`, `CoroutineDispatcher` | `FileSystem.SYSTEM`, `Dispatchers.IO` | `single` в desktopModule |
| `AppDb` | Room database через `createAppDb()` | `single`; `onClose { close() }` |
| `PhotoDao`, `ComboDao` | Сгенерированные Room реализации из AppDb | `single`-ссылки на DAO |
| Четыре ViewModel | Koin `viewModel { … }` | Экземпляр хранится в предоставленном ViewModelStore |
| `PhotoAssets` | Обычный конструктор внутри трёх VM | Один экземпляр на VM |
| `BackgroundResolver.Default` | `companion val` | Не Koin; удерживается классом |
| `AverageAreaOklabStrategy` | Kotlin `object : BackgroundStrategy` | Не Koin; singleton языка |

В [DesktopModules][desktop-di] `dataDir` передаётся параметром. Тест может создать временную библиотеку, а затем построить реальные DAO, файловые адаптеры и сервисы без доступа к личным данным. `WallClock` и `IdGenerator` — отдельные `fun interface`, чтобы два значения типа `() -> …` не смешивались при разрешении зависимостей по типу. `PhotoImporter` получает уже извлечённые ссылки `clock::nowMillis` и `ids::newId`.

`AppDbConstructor` — `expect object`, а actual-конструктор и реализации Room генерирует KSP. Не ищите рукописный `PhotoDaoImpl` среди исходных Main-файлов. В дизайн-системе другой случай expect/actual: `internal PcPlatformDialog` имеет рукописный desktop actual.

## 4. Навигация и состояние экранов

[AppNav][nav] содержит один `mutableStateListOf<NavKey>` и один `NavDisplay`. `ScreenKey` — sealed interface с ключами `Library`, `Combos`, `Viewer(photoId)`, `Builder(comboId?, photos)`. Переходы собираются в composition root; один feature не вызывает экран другого feature напрямую.

- `LibraryScreen` сообщает `onOpenViewer(id)` или `onCreateCombo(ids)`.
- `ComboListScreen` сообщает `onOpenBuilder(id)` или создание пустого combo.
- `Viewer` получает `photoId` как параметр composable. Его `SavedStateHandle` создаётся пустым и не используется для выбора страницы.
- `Builder` получает параметры через `EntryArgs(builderHandle(key))`. Обёртка нужна текущей Koin-интеграции: `SavedStateHandle` имеет особый путь разрешения, а desktop-вызову нужны явно построенные аргументы.

Хотя `ScreenKey.Builder.photos` — список, `builderHandle` записывает его в handle как строку через запятую, а Builder VM разбирает её обратно. Сейчас идентификаторы генерируются UUID-адаптером. Если изменить формат ID, эту промежуточную сериализацию нужно проверить.

### Два владельца ViewModel

**Root owner** создаётся в AppNav и передаётся Library и ComboList. Поэтому экземпляры этих VM сохраняются при переходах в Viewer/Builder и переключении вкладок. Выбранные фотографии и `GalleryScaleState` относятся к VM; состояние локальных Compose-виджетов следует отдельно анализировать по месту их `remember`, а не автоматически приписывать root owner.

**Entry owner** создаётся в `private EntryScope` через `rememberEntryViewModelStoreOwner()`. `DisposableEffect` вызывает `viewModelStore.clear()` при уничтожении соответствующей композиции. Для нынешних переходов это даёт отдельные Viewer/Builder VM. Строго по коду граница очистки — **onDispose композиции EntryScope**, а не отдельный реестр владельцев, привязанный к каждому ключу всего back stack. Если расширять вложенную навигацию, покрытие/возвращение записей стоит проверить отдельно.

В [ViewModelStoreOwners][owners] корневой вариант возвращает только `remember { StackViewModelStoreOwner() }`: симметричного `onDispose { clear() }` нет. Поэтому формулировка «root гарантированно clear при закрытии AppNav» не подтверждается реализацией. Аналогично `AppDb.onClose` зарегистрирован в Koin, но [main][main] не вызывает `stopKoin()` явно. Это границы текущей очистки, а не результаты обнаружения утечки профилировщиком.

### Потоки состояния и одноразовые события

| Feature | Наблюдаемое состояние | Что локально и чем отличается |
|---|---|---|
| Library | `photos`, `selected`, `selectionMode`; `GalleryScaleState` | Порядок выбора значим. `photos` использует `stateIn(WhileSubscribed(5000))`. Сообщения через buffered Channel |
| ComboList | `State(combos, photos, images, exportingComboId, exportMessage)` | VM-local assets, блокировка второго параллельного экспорта. Ошибка экспорта — поле состояния |
| Viewer | `State(photos, images)` | Pager, zoom 1…8 и offset находятся в composable; смена страницы сбрасывает transform |
| Builder | `BuilderUiState`, `bitmaps`, `selectedPhotoSize` | Draft/history в VM; диалоги, раскрытие inspector и состояние клавиш — в композиции. `saved`/`messages` через Channel |

`StateFlow` позволяет наблюдать последнее значение, а `Channel.receiveAsFlow()` используется для событий вроде «сохранено, выйти». Не заменяйте один вид другим без проверки повторной подписки и жизненного цикла экрана. Здесь нет единого универсального `UiEffect`-контракта на все фичи.

Back-обработка — LIFO в `BackDispatcher`. Последний handler получает событие. `RegisterBackHandler` регистрируется через `DisposableEffect`, хранит актуальную лямбду через `rememberUpdatedState` и снимается при dispose. Esc из Window, клавиатура/боковая кнопка мыши через BackKeyHost и кнопки экранов сходятся к этой логике или явному `onBack`. В Builder выход сначала проверяет `isDirty`, а сохранённый результат приходит через `vm.saved`.

## 5. Builder: где менять поведение

Читайте [схему редактора](04-builder.svg) вместе с [ComboBuilderViewModel][builder-vm] и [BuilderContract][builder-contract]. ViewModel диспетчеризует события и владеет политикой состояния, но правила разнесены по небольшим компонентам.

| Компонент | Доступ | Ответственность |
|---|---|---|
| `ComboBuilderScreen`, `ComboBuilderViewModel` | public | Вход фичи и её координатор |
| `BuilderEvent`, `BuilderUiState`, `GesturePhase`, `EditMode`, `SnapDragKind` | public | Контракт событий и наблюдаемого состояния |
| `BuilderTopBar`, `BuilderWorkspace`, `BuilderInspector`, `BuilderCanvasToolbar`, `BuilderViewControls` | internal | Разметка и генерация событий |
| `CanvasSizeDialog`, `ExitDialog`, `PickPhotosDialog`, `BuilderPhotoPicker` | internal | Локальные сценарии выбора/подтверждения |
| `EditorViewport` | internal | Размер экрана, CanvasTransform, захват актуального состояния |
| `EditorCanvas` | private top-level | Draw и подключение pointer/wheel modifiers внутри своего файла |
| `editorGestures`, `classifyGesture`, `nearEdge`, `editorWheelZoom` | internal | Перевод ввода в команды редактора |
| `DraftEditor` | internal object | Перестановка/добавление/удаление/замена фото, пресеты и начальная раскладка |
| `DraftHistory` | internal class | Фазы жеста, сравнение снимков, Undo/Redo |
| `ComboSnap`, `ItemSnap`, mapper extensions, `trimmedToPhotos` | internal | Снимки, адаптация и финализация сохраняемой копии |
| `FitPreset` | public enum | Разовая операция FIT/FILL |
| `ItemDraft` | public class в core | Изменяемая модель рамки; `id` val, прочие редактируемые поля var |
| `FrameSnap` / `FrameSnapState` | public types | У FrameSnap private constructor, у FrameSnapState internal constructor |
| `SnapSession` | private nested class VM | Состояние одной snap-операции |

`internal` ограничивает **модуль**, а не Kotlin-пакет. Поэтому `draft` и `history` видимы другим пакетам feature:builder. Запрет зависеть обратно от родительского пакета Builder обеспечивается отдельным architecture-тестом. `private` top-level ограничивает файл; private член — содержащий класс. `val StateFlow` не делает вложенные объекты неизменяемыми.

### Один жест — один шаг Undo

При `ItemPressed` снимается снимок до изменения и, при необходимости, рамка поднимается вперёд. В процессе жеста `ItemGestures`, `DraftEditor` и snap-логика изменяют `ItemDraft` на месте. VM повышает `revision`, чтобы наблюдатели получили изменение даже при прежних ссылках на драфты.

`DraftHistory.begin()` хранит `pendingGestureSnap`. `end()` сравнивает текущий снимок с исходным и добавляет checkpoint только при изменении. `cancel()` восстанавливает исходный снимок без добавления шага. `UndoStack<ComboSnap>` по умолчанию держит 50 шагов; новое завершённое действие очищает redo.

Снимок включает размеры холста, фон и все поля рамок. В него **не входят имя combo, viewport и выделение**. Поэтому переименование помечает состояние dirty, но не становится обычным undo-шагом холста. `isDirty` — отдельная политика VM; это не вычисление равенства draft последней сохранённой версии.

Если добавить поле в `ItemDraft`, проследите весь путь: `ItemSnap`, `toSnap`, `toDraft`, `toEntities`, `toDrafts`, а при сохранении нового значения — и схему Room. `DraftMapperTest` проверяет соответствие полей draft и snapshot. Вставить новое поле только в UI state недостаточно.

### Не смешивайте масштабы

1. **Размер холста** — canvasW/H в координатах изображения; сохранённые целочисленные размеры определяют PNG.
2. **contentScale** — размер фото относительно его исходных пикселей внутри рамки. Pan также описывает контент в координатах холста.
3. **viewport.zoom** — увеличение рабочего вида. Базовый `Viewport.scale()` вписывает весь холст без автоматического увеличения выше 1:1; пользовательский zoom умножает этот масштаб. Это не изменение PNG.

`Viewport.zoomedAround()` рассчитывает новый pan из реально применённого коэффициента после clamp. `CanvasTransform.toCanvas/toScreen` переводит координаты. Размеры ручек и snap-допуски приходят из dp в экранные px, затем учитывается масштаб вида. Не передавайте экранную дельту прямо в поля draft без соответствующего преобразования.

`FitPreset.FIT/FILL` — команда, которая один раз меняет размещение. `FramingMode.FIT` — режим: эффективный scale пересчитывается при рендере, pan становится нулевым. В режиме FREE берутся сохранённые scale/pan. При выходе из FIT правила переводят видимый результат в сохранённые значения. Смешение этих понятий даёт скачки картинки при resize или повторном открытии.

### Сохранение не редактирует рабочий draft

`save()` проверяет `loaded`, `saving` и наличие рамок. До перехода на IO создаётся `snapshot().trimmedToPhotos(photos)`. Это важно: `ItemDraft` изменяем, и чтение его позже в фоновой корутине не даёт стабильного снимка.

Финализация берёт видимые границы фото, пересекает их с холстом, объединяет, округляет наружу через floor/ceil и сдвигает центры рамок в новых координатах. При недостаточных метаданных используется площадь рамки; при пустом наборе видимых областей возвращается исходный snapshot. **Сохранённый размер холста может стать меньше выбранного в редакторе**, потому что края обрезаются вокруг видимых фото. Рабочий draft и Undo не должны измениться даже при ошибке записи.

Далее строятся `ComboEntity` и `ComboItemEntity`, zIndex перенумеровывается по порядку списка. `ComboRepository.saveCombo()` выполняет одну Room writer-транзакцию: upsert combo → delete old items → insert new items. Успех отправляет событие `saved`, которое экран превращает в `onBack()`. Ошибка сбрасывает `saving` и отправляет сообщение; CancellationException пробрасывается.

## 6. Изображения: не один общий кэш

[Схема изображений](05-images.svg) разделяет три маршрута.

**Миниатюры библиотеки.** `PhotoGrid` использует `ThumbImage`. Composable получает `PhotoRepository`, `ImageCodec` и IO dispatcher непосредственно через `koinInject`. `repo.thumbPath()` возвращает файл `thumbs/<id>.jpg`. Decode происходит в `LaunchedEffect` на IO, результат хранится в `remember(photoId)` конкретной композиции. Этот маршрут не идёт через `BitmapSource`.

**Assets экрана.** Каждый Viewer/ComboList/Builder создаёт свой [PhotoAssets][assets]. Метаданные и bitmap публикуются двумя StateFlow; `loadedReqPx` отдельно запоминает уже запрошенное качество. `ensureBitmap` пропускает загрузку, если существующий bitmap был получен для достаточного requiredPx. Это учёт запроса, не универсальная проверка реального разрешения файла.

**Общий decode/cache.** [LruBitmapSource][lru] — Koin single. Он запрашивает PhotoDao, выбирает sampleSize, использует ключ `photoId:sample` и хранит bitmap в LinkedHashMap с ручным порядком доступа. `inFlight` сводит запросы одинакового ключа к одному Deferred. Mutex защищает общие таблицы; разные ключи могут декодироваться параллельно.

| Потребитель | Политика качества | Удержание |
|---|---|---|
| ThumbImage | Готовая JPEG-миниатюра; при импорте длинная сторона sample не больше 512 | Compose `remember` |
| ComboList | `PREVIEW_PX = 800` | PhotoAssets root VM; вызова eviction нет |
| Viewer | `VIEWER_PX = 2048`, текущая страница ± один сосед | PhotoAssets entry VM; `evictExcept(keepIds)` перед новой загрузкой |
| Builder | `maxBorder × viewport.zoom × 2`, clamp 128…4096; delay 300 ms для отложенных обновлений | PhotoAssets entry VM; предыдущее bitmapJob отменяется, eviction не вызывается |
| PNG export | Для каждого photoId — максимальная потребность его рамок по эффективному scale, не выше размера оригинала | Прямой BitmapSource + локальная map операции |

Лимит LRU равен примерно четверти максимальной JVM heap в килобайтах; вес записи оценивается как `width × height × 4 / 1024`, минимум 1 KB. Это **не предел всей памяти изображений**. PhotoAssets, UI, in-flight decode, source bytes и экспортный framebuffer держат дополнительные ссылки/выделения. Вытесненный из LRU bitmap может продолжать жить в карте VM.

`SkiaImageCodec` пробует декодирование с уменьшением; если кодек не поддерживает такой размер, используется full decode + scaleDown. Поэтому маленький requiredPx не гарантирует маленький пик временной памяти.

### Где текущие гарантии заканчиваются

- `PhotoAssets.evictExcept` очищает bitmap и loadedReqPx, но не карту метаданных.
- Viewer при каждом `ensureLoaded()` создаёт новый `viewModelScope.launch`; предыдущая загрузка окна отдельно не отменяется и не проверяет актуальность keepIds перед публикацией. Старый результат потенциально может вернуться после eviction.
- `PhotoAssets.ensureBitmap()` использует `runCatching`, который охватывает и CancellationException. `PhotoImporter` также содержит широкие catch(Exception). Для новых сценариев отмены это требует проверки.
- LRU создаёт собственный `SupervisorJob + Dispatchers.Default`; это намеренно не дочерний scope первой VM, запросившей общий decode. Но у класса нет явного close/cancel для этого scope.

Это наблюдения о структуре кода и гипотезы для сценарных тестов/профилирования. В рамках подготовки документа не измерялись утечки, пики памяти и гонки при реальном листании.

## 7. Импорт и совместимость данных

Путь импорта: Library VM → ImagePicker → PhotoRepository → [PhotoImporter][importer]. Для каждого ImageSource вычисляется SHA-256 **исходных байтов**, проверяется дубликат, создаётся ID и библиотечная копия, читаются bounds/EXIF, при необходимости нормализуется ориентация, затем одним sample-decode вычисляются avgColor и миниатюра. PhotoEntity записывается после успешного создания файлов. На отдельном сбое увеличивается failed и выполняется best-effort уборка. Возвращается `ImportResult(added, duplicates, failed)`.

`PhotoStorage` централизует директории и имена. В БД хранится fileName, а не абсолютный путь. Windows dataDir — `%APPDATA%/PhotoCombo`; `defaultAppDataDir()` содержит и ветки для других desktop ОС. `createAppDb` использует BundledSQLiteDriver, schema version 2 и query context IO.

Связи Room:

- `ComboEntity 1 ← N ComboItemEntity`: удаление combo каскадно удаляет рамки.
- `PhotoEntity 1 ← N ComboItemEntity`: удаление используемого фото ограничено RESTRICT.
- `photos.sha256` имеет уникальный индекс; `comboId` и `photoId` — индексы combo_items.
- `ComboWithItems` объединяет combo и relation items. Сортировку items гарантирует репозиторий, не `@Relation`.

PhotoRepository предварительно проверяет `isPhotoUsed`, затем удаляет строку и файлы. Файловое удаление best-effort. **Общей транзакции SQLite + filesystem здесь нет.** Транзакционность `saveCombo` относится к конфигурации в БД.

Миграция `MIGRATION_1_2` удаляет старую таблицу нескольких фото рамки, переносит фото в combo_items, оставляет первое по orderIndex/id и удаляет пустые старые рамки. Сохранённые `FramingMode.name` и JSON имена BackgroundSpec — часть формата. Простое переименование enum или serial name нарушает чтение старых данных; требуется стратегия совместимости и тест миграции.

GallerySettings хранится отдельным JSON. `GallerySettingsRepositoryImpl` сериализует доступ Mutex, кеширует current и нормализует проценты к 50…200. File storage пишет temporary → atomicMove. При IOException новое значение остаётся в памяти, а saveScale возвращает false. `GalleryScaleState.change` обновляет UI во время движения; `commit` сохраняет завершённый выбор.

## 8. Рендер и PNG

[Схема рендера](07-render-export.svg) показывает общий маршрут:

```text
ItemDraft / ComboItemEntity
  → соответствующий frameContent adapter
  → FrameContent + Border + FramingMode
  → buildItemFrame → contentArgsOf → effectiveFraming
  → ItemFrame
  → drawCombo → drawItem
```

`core.render` не знает Room-сущностей: `data/EntityExt.kt` адаптирует сохранённые данные, `builder/draft/ComboDraftMapper.kt` — рабочие драфты. При этом сам `ItemDraft` физически находится в core.render; комментарий «core не знает про драфты» нельзя понимать как отсутствие этого типа в core.

`drawCombo` применяет origin/scale, обрезает по холсту, рисует фон и последовательно рамки. `drawItem` обрезает по рамке, рисует полупрозрачную заливку avgColor, затем изображение. Последний элемент рисуется сверху. Editor добавляет selection/snap overlay вне общего renderer, в экранных координатах.

Поле rotation присутствует в draft/entity/snapshot, но текущая цепочка Border → ItemFrame → drawItem не применяет поворот. Наличие поля хранения не подтверждает работающую rotation-функцию UI. Это важное различие между моделью и поддержанным поведением.

Фон хранится как рецепт `BackgroundSpec.Auto(strategy)` или `Solid(argb)`. `BackgroundResolver` ищет стратегию по id, неизвестный id заменяет default. `AverageAreaOklabStrategy` смешивает avgColor в OKLab с весом площади пересечения **рамки** с холстом. Перекрытия рамок не вычитаются; это не анализ видимых сверху пикселей окончательного кадра. `ResolvedBackground` сейчас имеет вариант Solid. `BackgroundPresets` задаёт цикл кнопки, `Oklab` — преобразования цвета, `AverageColor` — расчёт по bitmap при импорте.

`ComboPngExporter.export(id)` читает сохранённый combo, проверяет положительный размер, лимит 32 MP и наличие рамок, спрашивает место назначения, собирает metadata/images, формирует ItemFrame и рисует на ImageBitmap точного размера с масштабом 1. Затем PngOutput записывает результат. Отмена диалога возвращает null; ComboList VM показывает результат или ошибку и сбрасывает флаг экспорта в finally.

`DesktopPngOutput.chooseDestination` работает на Swing dispatcher. `write` запрещает целевую директорию внутри библиотеки PhotoCombo, пишет временный файл рядом с целью, проверяет отмену, переносит в конечный путь и очищает temporary в finally. Разрешение замены существующего файла определяется результатом native dialog и тем, совпало ли итоговое PNG-имя с выбранным именем. Это часть адаптера, не ответственность renderer.

## 9. Общий UI и переиспользуемость

[Схема reuse](08-reuse.svg) отделяет две библиотеки UI.

**design-system** имеет направление `components → primitives → theme → tokens`; icons независимы от предметной модели. PcColors, PcTypography, PcSpacing/Sizes/Shapes/Strokes/Opacity, PcMotion, PcFocus и PcDensity задают визуальный язык. PcTheme предоставляет их через CompositionLocal. PcSurface/Text/Icon/Divider/Control/PopupAppearance служат строительными блоками, PcButton/Select/Slider/Dialog и другие компоненты строятся поверх них. Production project-зависимостей нет, но Compose и coroutines остаются внешними зависимостями.

**ui-common** знает data/domain: PhotoGrid принимает PhotoEntity, ThumbImage получает репозиторий/кодек из DI, GalleryScaleState сохраняет настройки через repository. Его легче использовать между фичами PhotoCombo, чем переносить целиком в постороннее приложение. GalleryGrid<T> внутри этого модуля более общий: он занимается колонками и геометрией ячеек.

Экран Builder не импортирует Library ради выбора фото: оба используют PhotoGrid. Viewer/ComboList/Builder разделяют класс PhotoAssets, но каждый имеет собственный экземпляр. Builder/ComboList/export разделяют renderer, но передают разные transforms и источники конфигурации. Это три разных смысла reuse: общий composable, общий алгоритм/класс и общий runtime-service.

`DesignSystemGallery` и `GalleryLauncher` — отдельный путь запуска примеров. Он полезен для знакомства с компонентами и UI-проверок без открытия базы личной библиотеки.

## 10. Как безопасно войти в разработку

Рекомендуемый маршрут чтения:

1. `settings.gradle.kts`, module build files и `verifyModuleGraph`: понять разрешённые зависимости.
2. `main.kt`, три DI-модуля, `AppNav`, `ViewModelStoreOwners`: увидеть создание и владение объектами.
3. `LibraryViewModel` и `Repositories.kt`: пройти простой сценарий от пользователя к данным.
4. `PhotoImporter`, `PhotoStorage`, `Entities`, `Daos`: связать байты, файлы и метаданные.
5. `BuilderContract` → `ComboBuilderViewModel` → `DraftEditor`/`DraftHistory` → мапперы: пройти редактирование.
6. `Framing` → `ItemFrames` → `DrawCombo`/`DrawItem` → `ComboPngExporter`: проследить получение пикселя.
7. `PhotoAssets` и `LruBitmapSource`: разобраться с качеством загрузки и удержанием памяти.
8. Тесты выбранного сценария из [полного индекса](SOURCE_INDEX.md), затем UI или gallery.

| Задача | Основное место изменения | Что проверить рядом |
|---|---|---|
| Новый toolbar action | BuilderContract + VM + соответствующая панель | History checkpoint, empty action, dirty/revision |
| Новое поле рамки | ItemDraft + ItemSnap + mapper + Entity | Поле не теряется в Undo, save/load; migration при смене схемы |
| Новая логика вписывания | core.render / Framing / TransformMath | Editor/preview/export parity; FIT ↔ FREE |
| Новый фон | BackgroundSpec/Resolver/Strategy + render/UI | JSON compatibility, snapshot/Undo, fallback неизвестного id |
| Изменение загрузки изображений | Политика конкретной VM, PhotoAssets или LRU согласно обязанности | Cancellation, качество, stale results, удержание ссылок |
| Новый способ хранения/выбора файлов | Порт и desktop-адаптер + DI | Отмена dialog, файловые ошибки, временная библиотека в тестах |
| Новый UI-компонент | design-system или ui-common по степени предметной зависимости | Направление imports, keyboard/focus, gallery и размеры |
| Новый экран | Новый feature, ScreenKey/AppNav, DI | Отдельный owner, back handling, параметры и scope |

Команды из корня репозитория; доступность offline-сборки зависит от заполненного Gradle cache:

```powershell
.\gradlew.bat :composeApp:runDesignSystemGallery
.\gradlew.bat verifyModuleGraph --offline --console=plain
.\gradlew.bat :feature:builder:desktopTest --tests '*DraftHistoryTest' --offline --console=plain
.\gradlew.bat :domain:desktopTest --tests '*LruBitmapSourceTest' --offline --console=plain
.\gradlew.bat check --offline --console=plain
```

Обычный `:composeApp:run` открывает реальные пользовательские данные. Для интеграционных тестов используйте временные директории через dataDir. `RealPhotoImportTest` требует явно заданной папки `-Prealphotos=...`; без неё этот сценарий пропускается. Подробности процесса — [DEVELOPMENT.md](../DEVELOPMENT.md).

Тесты, которые особенно полезны как объяснение контрактов: AppModulesTest, AppNavViewModelTest, ItemFrameParityTest, DraftMapperTest, DraftHistoryTest, ComboBuilderFinalizationTest, PhotoImporterTest, LruBitmapSourceTest, ComboPngExporterTest, MigrationTest. Их названия — маршрут к примерам, а не заявление, что вся возможная функциональность покрыта.

## 11. Достоверность и обновление атласа

Диаграммы вручную интерпретируют конкретный код, а модульная матрица, полный индекс и manifest извлекаются из файлов. Это не автоматически вычисленный полный call graph. Короткие подписи на картах группируют связанные функции; точные типы, overloads и тела находятся в связанных исходниках. Каталог покрывает все 135 Main-файлов зарегистрированных модулей, а тестовый список — 78 файлов desktopTest на указанном снимке. Сгенерированный Room-код и внешние библиотеки показаны как зависимости, не как рукописные компоненты проекта.

`source-manifest.json` хранит commit, пути, SHA-256 файлов, объявления и импорты. Он позволяет обнаружить дрейф после изменения кода. `build_atlas.py` воспроизводит SVG, просмотрщик и индекс, но **не переосмысляет вручную написанные архитектурные объяснения**. Перед пересборкой на другом commit обновите смысловые карточки и этот текст, иначе новая дата/хеш сами по себе не доказывают актуальность схемы.

```powershell
python docs\architecture\build_atlas.py
```

Анимация пунктирных потоков декоративна и показывает направление, а не фактическую активность приложения. SVG полностью читается без JavaScript; `prefers-reduced-motion` останавливает движение. В локальном просмотрщике есть отдельный переключатель. Точные артефактные проверки и ограничения записаны в [VALIDATION.md](VALIDATION.md).

[main]: ../../composeApp/src/desktopMain/kotlin/com/combo/main.kt
[nav]: ../../composeApp/src/commonMain/kotlin/com/combo/ui/AppNav.kt
[owners]: ../../composeApp/src/commonMain/kotlin/com/combo/ui/ViewModelStoreOwners.kt
[desktop-di]: ../../composeApp/src/desktopMain/kotlin/com/combo/di/DesktopModules.kt
[root-build]: ../../build.gradle.kts
[arch-test]: ../../composeApp/src/desktopTest/kotlin/com/combo/ArchitectureRulesTest.kt
[importer]: ../../domain/src/commonMain/kotlin/com/combo/domain/PhotoImporter.kt
[entities]: ../../data/src/commonMain/kotlin/com/combo/data/Entities.kt
[exporter]: ../../domain/src/commonMain/kotlin/com/combo/domain/ComboPngExporter.kt
[builder-vm]: ../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/ComboBuilderViewModel.kt
[builder-contract]: ../../feature/builder/src/commonMain/kotlin/com/combo/feature/builder/BuilderContract.kt
[assets]: ../../domain/src/commonMain/kotlin/com/combo/domain/PhotoAssets.kt
[lru]: ../../domain/src/commonMain/kotlin/com/combo/domain/image/LruBitmapSource.kt
