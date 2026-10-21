> **Статус миграции на Kotlin Multiplatform (идёт).** Новый проект — модуль `composeApp`
> (Kotlin Multiplatform + Compose Multiplatform, цель — desktop/Windows). Прежнее Android-приложение
> лежит нетронутым в `legacy-android/` как отдельная Gradle-сборка и служит эталоном поведения
> до конца миграции. Ниже описана прежняя Android-структура; команды из раздела «Build & verify»
> для неё выполняются в каталоге `legacy-android/`, для нового проекта — из корня:
> `.\gradlew.bat :composeApp:desktopTest`. План: `docs/KMP_MIGRATION_PLAN.md`.

# PhotoCombo

Desktop-просмотр фото создаёт pager после получения списка из репозитория, с начальной
страницей по id нажатой фотографии. Загрузка изображений начинается с этой страницы
и её соседей; обновление битмапов не сбрасывает выбранную страницу.

Приватный photo viewer + неразрушающий «combo builder» для Android.

**Combo — НЕ изображение, а конфиг** (ids исходников + параметры трансформаций), хранится в Room и рендерится на лету. Комбо — коллаж из рамок на одном холсте; в каждой рамке ровно одно фото. Таймеров, переходов, анимаций и ручного переключения нет.

Документы проекта:

| Файл | Для кого и о чём |
|---|---|
| `Instructions.md` | Спецификация, единственный источник правды о проекте |
| `README.md` | Архитектура и устройство кода (этот файл) |
| `USER_GUIDE.md` | Руководство пользователя: что делает приложение и как им пользоваться |
| `AGENTS.md` | Правила работы с репозиторием для ИИ-агентов и разработчиков |

---

## Ключевые принципы (§3 спецификации)

1. **Неразрушаемость** — оригиналы не изменяются никогда; combo = данные.
2. **Приватность по умолчанию** — файлы в `filesDir/photos/<uuid>.<ext>` + `.nomedia`; без runtime-разрешений (Photo Picker).
3. **WYSIWYG структурно** — один код отрисовки (`drawItem()` для рамки, `drawCombo()` для холста) для редактора и превью списка; расхождение невозможно по построению.
4. **Crop emergent** — нормализованный crop-rect не хранится; он возникает из `clipRect(рамка) + contentScale + pan`. Контент рисуется с сохранением аспекта всегда.
5. **Средний цвет** считается один раз при импорте (стабильно при pan/zoom, дёшево); им заливается letterbox с α = 0.5.
6. **Undo через снимки данных** — checkpoint только на завершённое действие.
7. **Drafts мутабельны + `revision` в StateFlow** — жесты на 60 Гц не плодят копий.
8. **EXIF нормализуется при импорте** — дальше про ориентацию не думаем.
9. **Одна рамка — одно фото** — ни пустых рамок, ни списков фото в рамке, ни автосмены.
10. **Enum'ы в Room хранятся строками** — миграционно безопасно; имена значений — часть формата хранения.
11. **Миграции обязательны** — `AppDb.version = 2`; любое изменение колонок или типов требует `Migration` (`data/Migrations.kt`) и теста в `MigrationTest`.

## Стек

Kotlin · Jetpack Compose + Navigation Compose · MVVM/MVI (UDF) · Room · Hilt · Coroutines + Flow · kotlinx.serialization (фон в JSON).
Жесты — собственные `pointerInput`. Без внешних image-библиотек в ядре рендера.

---

## Структура проекта

```
app/src/main/java/com/combo/
├─ Const.kt, App.kt, MainActivity.kt
├─ core/                         ядро; НЕ импортирует data/domain/feature/ui
│  ├─ model/       FramingMode (ничего не импортирует)
│  ├─ background/  BackgroundSpec/Resolver/Presets, AverageAreaOklabStrategy
│  ├─ color/       Oklab (sRGB <-> OKLab)
│  ├─ render/      Border, TransformMath, Framing, Aspect, Drafts, DrawItem,
│  │               BackgroundDraw, ItemGestures, LayoutPresets, Geometry (+CanvasTransform),
│  │               ItemFrames (buildItemFrame), DrawCombo
│  └─ util/        UndoStack, AverageColor
├─ data/           Entities, Daos, AppDb, BackgroundConverters, EntityExt, PhotoStorage,
│                  di/DatabaseModule
├─ domain/         Repositories (интерфейсы), Impls, PhotoImporter, PhotoAssets,
│                  image/LruBitmapSource, di/AppModule.kt (BindingsModule: привязки интерфейсов)
├─ feature/
│  ├─ builder/     BuilderContract, ComboBuilderViewModel, ComboBuilderScreen, EditorCanvas,
│  │               EditorGestures, SelectionOverlay, BuilderToolbar, PickPhotosDialog,
│  │               ExitDialog, ViewportController,
│  │               draft/   (ComboSnap, ComboDraftMapper, DraftEditor, FitPreset),
│  │               history/ (DraftHistory)
│  ├─ combo_list/   ComboListScreen, ComboListViewModel
│  ├─ library/      LibraryScreen, LibraryViewModel
│  └─ viewer/       PhotoViewerScreen, PhotoViewerViewModel
└─ ui/             AppNav, common/ (ThumbImage, PhotoGrid, BackNavIcon, CollectMessages)
```

Направление зависимостей: `core ← data ← domain ← feature ← ui`. Правила проверяет тест `ArchitectureRulesTest`: `core` не импортирует вышележащие слои, `feature.X` не импортирует `feature.Y` (общее выносится в `ui.common`), подпакеты `draft` и `history` не импортируют корень `feature.builder`, циклов между пакетами нет.

Реализации репозиториев лежат в `domain` рядом с интерфейсами. Интерфейсы `domain` пока оперируют Room-сущностями и типами Android (`Uri`, `ImageBitmap`); чистая доменная модель — задача следующего этапа.

---

## Архитектура

### Поток данных (UDF)

```
UI (Compose) ──events──▶ ViewModel ──suspend/Flow──▶ Repository ──▶ Room / файлы
     ▲                        │
     └────StateFlow<State>────┘
```

- ViewModel держит единый `MutableStateFlow<UiState>`; UI подписан через `collectAsStateWithLifecycle`.
- Редактор мутирует **drafts** напрямую (без копий на каждый тик жеста) и увеличивает `revision` — перерисовка идёт по нему (принцип 7).
- В Room пишем только по **Save**: одна транзакция `saveCombo(combo, items)`; `updatedAt` — ключ инвалидации кэшей превью.
- Загрузка фото во всех ViewModel идёт через `PhotoAssets` (метаданные и битмапы с учётом запрошенного размера); политику размера задаёт экран.

### Модель данных

| Таблица | Суть |
|---|---|
| `photos` | приватные файлы; уникальный `sha256` (дедупликация); `width/height` уже с учётом EXIF; `avgColor` посчитан один раз |
| `combos` | комбо: имя, размеры холста, фон (JSON `BackgroundSpec`), `createdAt`/`updatedAt` |
| `combo_items` | рамка (**любой aspect ratio**): центр, размер, `zIndex`; режим рамки `framingMode` (FREE/FIT); её единственное фото `photoId` + `contentScale/panX/panY`; FK RESTRICT на `photos` (удаление используемого фото блокируется) |

Crop **не хранится нигде** — он emergent (принцип 4). Рамка без фото невозможна по схеме: `photoId` не nullable.

### Фон (`core/background`)

Фон — это **описание** (`BackgroundSpec`: `Auto(strategyId)` / `Solid(argb)`), а не готовый цвет: `Auto` считается по содержимому холста в момент отрисовки. Дефолтная стратегия `avg.area.oklab.v1` усредняет `avgColor` всех фото рамок с весом по **видимой** площади рамки, в OKLab (альфа игнорируется, итог `alpha = 0xFF`); при нулевом суммарном весе — `Const.FALLBACK_BACKGROUND`. Площади перекрывающихся рамок не вычитаются. Новые стратегии и варианты добавляются рядом; неизвестный id стратегии молча заменяется дефолтной.

Фон рисует одна функция — `DrawScope.drawComboBackground(ResolvedBackground, topLeft, size)`, а вход для неё собирает `comboBackgroundOf(canvas, background, frames)`; ими пользуются редактор и список. У редактора холст хранится во `Float`, поэтому у функции есть перегрузка с `Float`: округление до `Int` исказило бы вес. В Room фон лежит JSON-строкой через `BackgroundConverters`; повреждённая или неизвестная запись читается как `Auto`.

### Ядро рендера (`core/render`)

Рамка рисуется одной функцией:

```kotlin
DrawScope.drawItem(ItemFrame)   // clipRect(рамка) → заливка avgColor α=0.5 → фото
```

`ItemFrame` собирает только `buildItemFrame(...)` (`ItemFrames.kt`): `fillColor` берётся из `avgColor` входящего фото, а пока битмап не загружен, кадра нет. Входные данные для неё готовят адаптеры: из драфтов — в `ComboDraftMapper.kt`, из Room-сущностей — в `EntityExt.kt`. Холст целиком рисует `drawCombo(...)` (`DrawCombo.kt`): преобразование «экран ↔ холст» (`CanvasTransform`), `clipRect` по прямоугольнику холста, фон, затем рамки по порядку. Обрезка живёт здесь же и потому действует в обоих экранах: свисающая за край рамка (центр зажат на холсте, корпус — нет) видна только внутри холста. Оверлей выделения редактора рисуется в экранных координатах и обрезается по экранному прямоугольнику холста на своей стороне.

Кадры собираются **внутри** draw-лямбды `Canvas`: чтение состояния там инвалидирует отрисовку. Не выносите сборку кадров в композицию.

Инвариант аспекта: фото рисуется через `TransformMath.contentRect(...)`, искажение невозможно по построению. Зум фото ограничен диапазоном `[fitScale·0.25, maxContentScale(vs)]`.

#### Режим рамки (AutoFit) — «persistent fit»

`FramingMode` у рамки (`combo_items.framingMode`):

- **`FREE`** — обычное поведение: `contentScale`/`panX`/`panY` хранятся и правятся жестами.
- **`FIT`** — фото **всегда** вписано в рамку целиком: оно занимает всю высоту **или** всю ширину (ту, что ограничивает), остаётся по центру, пропорции сохраняются; в свободных местах — подложка `avgColor` α = 0.5.

Режим включается кнопкой `AutoFit ON/OFF` (`TOOL_TAG_AUTOFIT`) и действует на фото выбранной рамки.

Значения **вычисляются при отрисовке**, а не хранятся: решает это только `effectiveFraming(mode, border, srcW, srcH, stored…)`, а `ContentDrawArgs` собирает `contentArgsOf(...)`. `LayoutPresets.fit` использует ту же формулу.

Следствия:

- перетаскивание грани, щипок рамки и смена формата холста пересчитывают картинку сами;
- в `FIT` события `ContentPan`/`ContentZoom` игнорируются (хранимые значения не участвуют в отрисовке), а ресайз за грань работает;
- `ItemPinch` меняет хранимые значения, но на картинку в FIT не влияет;
- **выход из FIT «запекает» вычисленные значения** в `contentScale`/`panX`/`panY`, иначе фото скакнуло бы к давним значениям. Это делают и кнопка AutoFit, и `ApplyPreset`: пресет — явный выбор пользователя по конкретному фото, поэтому рамка выходит из FIT, пресет применяется к фото рамки;
- запрос на режим, который у рамки уже стоит, ничего не меняет: ни undo-шага, ни `isDirty`;
- `SetFramingMode` входит в undo.

### Импорт фото (`PhotoImporter`)

1. Photo Picker (`PickMultipleVisualMedia`, до 20 фото) → URI, без runtime-разрешений.
2. Потоковый SHA-256 → дубликат отклоняется («already added»).
3. Копия в `filesDir/photos/<uuid>.<ext>` + `.nomedia` (пути определяет `PhotoStorage`).
4. EXIF-нормализация пикселей при необходимости; `avgColor` (уменьшение до 1×1 — приближение к среднему, не точное среднее); миниатюра ≤ 512 px в `filesDir/thumbs/<id>.jpg`.
5. Вставка `PhotoEntity`; при ошибке файлы удаляются.

Результат импорта — `ImportResult(added, duplicates, failed)`. Шифрование at-rest — [БЭКЛОГ].

### Редактор (Combo Builder)

**Режимы.** `EDIT`: перетаскивание — двигает рамку, щипок — масштабирует рамку вместе с содержимым. `BORDER`: перетаскивание внутри выбранной рамки — двигает фото, щипок — масштабирует фото, захват грани — меняет размер рамки (якорь в центре, меняется одна ось). Режим переключается кнопкой в тулбаре или двойным касанием холста.

**Жесты.** Ручной цикл `awaitEachGesture { awaitFirstDown → CHANGE → END/CANCEL }` (`EditorGestures.kt`) отправляет в ViewModel события с фазами `START|CHANGE|END|CANCEL`. Цель жеста выбирает чистая функция `classifyGesture` (ресайз за грань → сдвиг фото → перетаскивание рамки → сдвиг вида).

**Undo.** За фазы жеста отвечает `DraftHistory`: START — снимок, END — checkpoint (только если снимок изменился), CANCEL — откат. Дискретные действия ставят checkpoint до мутации. Жест, который ничего не изменил, не оставляет undo-шага и не ставит `isDirty`. В undo входят холст, фон и рамки с их фото (включая `framingMode`). **Не входят** имя комбо, сдвиг и зум вида.

**Операции над драфтами** (добавить рамки по фото, заменить фото рамки, удалить рамку, порядок слоёв, пресеты Fit/Fill, режим рамки, раскладка нового комбо) — чистые функции `DraftEditor`; ViewModel лишь ставит checkpoint и публикует результат. Предусловия проверяются до checkpoint, чтобы не появлялись пустые шаги. `+Frame` открывает окно выбора фото (`PickPhotosDialog`); N выбранных фото дают N рамок (`AddItems`): половина ширины и высоты холста, каскадный сдвиг на 4 % меньшей стороны, `FREE`, фото заполняет рамку, верхние слои, последняя добавленная выделена. Лимит комбо целиком — `MAX_ITEMS`. `Replace` (`ReplacePhoto`) подставляет другое фото в выбранную рамку: позиция, размер, поворот и режим сохраняются, фото заполняет рамку; замена на то же фото — не правка. Новое комбо из набора фото получает по рамке на фото; из пустого набора — комбо без рамок.

**Сохранение.** `zIndex` перенумеровывается подряд. Если рамок нет, `Save` показывает Snackbar «Add at least one photo» и оставляет редактор открытым. Имя по умолчанию — `Combo`.

На desktop сохранение финализирует холст по общим внешним границам всех рамок:
`ComboSnap.trimmedToFrames()` вычисляет их ограничивающий прямоугольник и переносит
центры рамок к его началу. Размеры рамок, масштабы/сдвиги фото, режимы и слои сохраняются.
Учитываются полные рамки, в том числе выступающие за рабочий холст. Размер холста
округляется вверх до целых пикселей, поэтому дробный край не обрезается (остаток менее
пикселя справа/снизу). Обрезается независимый снимок: рабочий драфт и Undo не меняются,
в том числе при ошибке записи. Повторное открытие использует финальные размеры.

**Лимиты.** `MAX_ITEMS = 10`, минимальная рамка — 8 % меньшей стороны холста, зум вида `0.5..4`.

**Битмапы.** `requiredPx = maxBorderWidth × viewport.zoom × 2` (в пределах 128..4096), перезагрузка с задержкой 300 мс после жестов.

#### Формат холста и `relayout`

Кнопка формата (`TOOL_TAG_ASPECT`) не хранит локальный индекс: размер читается из `state.canvasW/canvasH`, пресеты — в объекте `AspectPresets` (`16:9`, `9:16`, `1:1`, `4:3`, `3:4`; для произвольного размера подпись `Custom WxH`, а следующим берётся первый пресет).

Смена формата **пропорционально пересчитывает рамки** функцией `relayout(items, oldW, oldH, newW, newH, srcSizeOf)` (входит в undo):

- центр умножается на `kx = newW/oldW`, `ky = newH/oldH`; размер — на те же коэффициенты, но не меньше `MIN_BORDER_FRACTION × min(newW, newH)`;
- в режиме `FIT` фото не трогается, вписывание пересчитывается само;
- в режиме `FREE` сохраняется «приближение относительно заполнения» `r = contentScale / fillScale(старая рамка)`, а `pan` умножается на отношение размеров рамки; если `r >= 1`, `pan` ограничивается так, чтобы фото **продолжало** закрывать рамку;
- координаты и размеры не округляются; A → B → A возвращает исходные значения (если ни один размер не упёрся в минимум).

#### Вид: зум, сброс, границы рамок

- **Зум вида**: щипок по пустому месту (`PAN_VIEWPORT`) шлёт `ViewportZoom(factor, focus)`; формулу выполняет `Viewport.zoomedAround` (`ViewportController.kt`) на базе `viewportZoomPan`: точка холста под пальцами остаётся на месте, панорама считается от масштаба **до** изменения и от фактически применённого множителя (с учётом границ 0.5–4).
- **Сброс вида**: кнопка `Fit view` (`TOOL_TAG_FIT_VIEW`), событие `ResetViewport` (zoom = 1, pan = 0). В undo не входит.
- **Границы рамок**: центр рамки при перетаскивании удерживается на холсте, иначе её можно было бы потерять за краем. Ресайз за грань и щипок центр не двигают.

#### Выход с подтверждением

Системная кнопка «назад» и стрелка в верхней панели ведут в один обработчик. Если `isDirty == false`, выход сразу. Иначе диалог с тремя действиями (`DIALOG_TAG_SAVE` / `…DISCARD` / `…KEEP`):

- **Save** — отправляет `Save` и закрывает диалог; выход происходит по сигналу `saved`;
- **Discard** — выйти без сохранения;
- **Keep editing** — закрыть диалог и остаться.

Пока идёт `saving`, кнопки диалога неактивны. Если сохранять нечего, `Save` (из тулбара или диалога) показывает Snackbar и оставляет редактор открытым.

### Навигация

```
LibraryScreen (сетка, FAB «Import», мультивыбор → «Create combo»)
   └── PhotoViewerScreen (pager, pinch-zoom)
ComboListScreen (FAB «New combo», превью комбо)
   └── ComboBuilderScreen (создание / редактирование)
```

Нижняя панель переключает «Photos» и «Combos»; редактор и просмотр фото — полноэкранные.

#### Мультивыбор в библиотеке

Режим выбора включается **долгим нажатием** на фото или пунктом `Select`; долгое нажатие сразу выбирает это фото.

- Обычный тап вне режима выбора открывает полноэкранный просмотр.
- В режиме выбора тап переключает выбор; заголовок показывает `Selected: N`.
- Режим **не выключается сам**, когда выбор опустел: выход — крестик в шапке или системная кнопка «назад».
- `Create combo` передаёт id **в порядке выбора** (в этом порядке редактор раскладывает фото по рамкам). Если выбрано больше `Const.MAX_ITEMS`, лишние отбрасываются с Snackbar «Only the first N photos will be used».
- Удаление фото, которое используется в комбо, блокируется сообщением; подтверждения удаления нет ни для фото, ни для комбо.

---

## Тесты

Тесты лежат в `app/src/test/java/com/combo/`. Эмулятор не нужен.

- **Чистая логика на JVM**: геометрия (`TransformMathTest`, `ItemGesturesTest`, `CanvasTransformTest`, `ViewportTest`), фон (`BackgroundStrategyTest`, `BackgroundConvertersTest`), история (`UndoStackTest`), раскладка и режимы рамки (`AspectRelayoutTest`, `AutoFitTest`), данные (`EntityExtTest`, `MigrationTest`).
- **Редактор на JVM** (`feature/builder/`): `DraftEditorTest`, `DraftHistoryTest`, `DraftMapperTest`, `ViewportControllerTest`, `GestureClassifierTest`, `ComboBuilderViewModelTest`, `ComboBuilderBackgroundTest`.
- **Robolectric** (нужен Android-контекст или отрисовка, эмулятор не нужен): `ItemFrameParityTest` (одинаковый кадр в редакторе и списке), `DrawComboBackgroundTest`, экраны с Compose Test — `ComboBuilderGesturesTest`, `ComboBuilderToolbarTest`, `BuilderExitDialogTest`, `ui/LibraryMultiselectTest`, `ui/LibraryOpensTappedPhotoTest`, `ui/ComboListOpensBuilderTest`.
- **Защита исходников**: `SourceHygieneTest` (невалидный UTF-8, BOM, CRLF, U+FFFD, цепочки вопросительных знаков, строки кода на колонке 0 внутри блоков — мини-лексер понимает строки, raw-строки, шаблоны `${…}` и комментарии) и `ArchitectureRulesTest` (границы слоёв и циклы между пакетами).

Правьте файлы в IDE: запись через PowerShell (`>`, `Out-File`, `Set-Content`) портит кодировку и переводы строк.

---

## Сборка

Требуются JDK 17 и Android SDK 35.

```bash
./gradlew assembleDebug        # сборка APK
./gradlew testDebugUnitTest    # все JVM-тесты и UI-тесты на Robolectric
./gradlew installDebug         # установка на устройство/эмулятор (minSdk 26)
```

Kotlin 2.0.20 (compose plugin) · KSP · Room 2.6.1 · Hilt 2.52 · Compose BOM 2024.09.02.

## Статус работ (§16)

Реализовано: модель данных, ядро рендера, редактор (включая AutoFit, смену формата холста, зум и сброс вида, выход с подтверждением), репозитории и DI, импорт, все экраны, мультивыбор в библиотеке, тесты ядра и экранов.

Не реализовано ([БЭКЛОГ] §15): экспорт в файл (статичный/видео), шифрование at-rest, rotation-жесты, угловые ручки ресайза, якорь ресайза от противоположной грани, отступ между ячейками, режимы рамки кроме FREE/FIT, постраничная загрузка библиотеки, скриншот-тест WYSIWYG.

## Известные ограничения

- `avgColor` — приближение к среднему цвету (уменьшение до 1×1 с фильтрацией), а не точное среднее.
- EXIF-ориентации `TRANSPOSE` (5) и `TRANSVERSE` (7) не проверены на реальных файлах, возможна ошибка в порядке операций (см. комментарий к `PhotoImporter.normalizeExif`).
- Удаление фото и комбо выполняется без подтверждения.
- Единственную рамку удалить нельзя: чтобы начать заново, добавьте новую рамку и удалите старую.
