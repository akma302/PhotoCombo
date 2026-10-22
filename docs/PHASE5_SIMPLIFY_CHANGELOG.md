> **ИСТОРИЧЕСКИЙ ДОКУМЕНТ. Не инструкция для текущей работы.**
> Текст ниже сохраняет состояние и предположения своего этапа. Команды, версии,
> числа тестов, ограничения окружения и пункты «что дальше» могут быть устаревшими.
> Они не задают текущую задачу и не требуют выполнения. Актуальный маршрут чтения
> начинается в корневом `AGENTS.md`; текущее устройство — в `README.md`, команды —
> в `docs/DEVELOPMENT.md`. Пути здесь указаны относительно корня репозитория.

---

# Упрощение: «одна рамка — одно фото». Changelog и инструкция проверки

Код написан без компиляции (в моём окружении нет доступа к Maven и Gradle), тесты не запускались. Ошибки сборки и падения тестов возможны; присылайте лог, как раньше.

## Итог

Из приложения убраны таймеры, переходы, анимации, ручное переключение фото и плеер. Рамка показывает ровно одно фото. База данных переведена на схему версии 2 с миграцией. Добавлены «замена фото» и выбор фото для «+Frame». AutoFit оставлен.

Решения владельца: плеер удалён; AutoFit остаётся; N выбранных фото дают N рамок; добавлена замена фото; миграция БД делается; правки спецификации даны текстом (раздел 6).

## 1. Что изменилось

### Данные
- `ComboItemEntity` теперь хранит своё фото: `photoId` (FK на `photos`, RESTRICT, индекс), `contentScale`, `panX`, `panY`. Поля `switchMode`, `autoIntervalMs`, `manualTransition`, `autoTransition` удалены.
- Таблица `combo_item_contents` и классы `ComboItemContentEntity`, `ItemWithContents` удалены. `ComboWithItems` — `@Embedded combo` + `@Relation` на `items: List<ComboItemEntity>` (без вложенной связи).
- `saveCombo(combo, items)` вместо `saveCombo(combo, items, contents)`. `isPhotoUsed` проверяет `combo_items.photoId`.
- `AppDb.version = 2`. Миграция `MIGRATION_1_2` (`data/Migrations.kt`) подключена в `DatabaseFactory` через `addMigrations(*ALL_MIGRATIONS)`.

Что делает миграция: создаёт новую `combo_items` с полями фото, переносит в неё каждую рамку вместе с её фото, у которого наименьший `orderIndex` (остальные фото рамки теряются), рамки без фото отбрасывает, удаляет `combo_item_contents`, переименовывает таблицу и создаёт индексы. Комбо и фото не затрагиваются.

### Ядро (`core`)
- Удалены: пакет `core/playback` (`PlaybackEngine`, `AutoScheduler`, `SwitchAnimator`, `SwitchMath`, `PlaybackLifecycle` и его desktop-реализация), `core/render/Transitions.kt`, `core/util/EnumCycle.kt`, `core/util/MonotonicClock.kt`.
- `core/model/Model.kt`: остался только `FramingMode`. Удалены `SwitchMode`, `Direction`, `TransitionType`.
- `ItemDraft` — рамка с одним фото (`photoId`, `contentScale`, `panX`, `panY`); `ContentDraft` удалён. `ItemFrame(border, fillColor, content)`: без «входящее/исходящее» и перехода. `buildItemFrame(border, mode, content)` остаётся единственной точкой сборки кадра.
- `Const`: удалены `MAX_CONTENTS_PER_ITEM`, `DEFAULT_AUTO_INTERVAL_MS`, `FLING_*`. Остались `MAX_ITEMS = 10` и прочие лимиты.
- Из `ItemGestures` убраны `isVerticalFling` и `flingDirection`; `pinchItem`, `panContent`, `zoomContent`, `LayoutPresets.fit/fill`, `relayout` работают с `ItemDraft`.

### Редактор (`feature/builder`)
- События: удалены `ContentFling`, `SetActive`, `AddPhotos`, `AddItem`, `RemoveActiveContent`, `SetSwitchMode`, `SetManualTransition`, `SetAutoTransition`. Добавлены `AddItems(photoIds)` и `ReplacePhoto(id, photoId)`. Из `BuilderUiState` удалён `activeIndex`.
- `DraftEditor`: `addItems` (по рамке на фото, лимит 10, повторы id в одном запросе схлопываются, каскадный сдвиг), `replacePhoto` (позиция, размер, поворот и режим сохраняются, фото заполняет рамку, замена на то же фото — не правка), `applyPreset`/`applyFramingMode` для одного фото. Удалены `addContents`, `removeActiveContent`, `addItem`.
- Новое комбо из набора фото: по рамке на фото. Из пустого набора: **комбо без рамок**; `Save` с нулём рамок показывает «Add at least one photo».
- Тулбар: убраны Switch, M-Tr, A-Tr, +Photo, −Photo. `+Frame` открывает окно выбора фото; добавлена кнопка `Replace`. Остальные кнопки на месте. Новый дизайн панели — отдельная задача (план 2).
- Удалены `PageDots.kt`, `AddPhotoDialog.kt`; добавлен `PickPhotosDialog.kt` (несколько фото для `+Frame`, одно для `Replace`). Из редактора убрано распознавание флинга и клавиши PageUp/PageDown.

### Остальное
- `feature/combo_viewer/` удалён целиком; `ScreenKey.Player`, запись в `AppNav`, фабрика в `ViewModelModule`, `onPlay` и кнопка Play в `ComboListScreen`, обработка сворачивания окна в `main.kt` удалены.
- Превью списка комбо: одно фото на рамку через `buildItemFrame`.
- Просмотр одного фото (`feature/viewer`) не менялся.

### Теги и тексты, влияющие на тесты
- Удалены: `TOOL_TAG_ADD_PHOTO`. Добавлены: `TOOL_TAG_REPLACE_PHOTO` = `builder_tool_replace_photo`, `DIALOG_TAG_PICK_CONFIRM` = `builder_pick_confirm`. Остались: `TOOL_TAG_DEL`, `TOOL_TAG_ADD_ITEM`, `TOOL_TAG_AUTOFIT`, `TOOL_TAG_ASPECT`, `TOOL_TAG_FIT_VIEW`, `TOOL_TAG_SAVE`, `addPhotoCellTag(id)` (теперь тег ячейки в `PickPhotosDialog`).
- Заголовки окон: «Add frames» и «Replace photo»; кнопка подтверждения «Add N».

## 2. Тесты

Подсчёт по аннотациям `@Test` в исходниках: было 344, стало 318 (без запуска Gradle, поэтому итог `desktopTest` может немного отличаться от этой цифры).

Тесты **не подгонялись под код**: удалены только те, что проверяют удалённую по требованию функциональность; остальные переписаны под новую модель с сохранением смысла проверки. Ни одно утверждение о сохранившемся поведении не ослаблено.

### Удалены (проверяют удалённое)
| Файл | Тестов | Почему |
|---|---|---|
| `TransitionsTest` | 12 | переходов нет |
| `SwitchMathTest` | 9 | переключения фото нет |
| `PlaybackIndexSyncTest` | 2 | нет `SwitchAnimator` |
| `EnumCycleTest` | 2 | `EnumCycle` удалён вместе с enum'ами |
| `feature/combo_viewer/ComboViewerViewModelTest` | 6 | плеера нет |
| `feature/combo_viewer/HitTestItemTest` | 1 | проверяла выбор рамки в плеере |
| `ItemGesturesTest`: `slow swipe is not a vertical fling`, `fast vertical swipe is a fling`, `diagonal swipe below the dominance ratio is not a fling` | 3 | флинга нет |
| `ItemFrameParityTest`: `without animation all three paths produce static frames` | 1 | нет `transition`/`outgoing` |
| `ComboBuilderViewModelTest`: `fling on empty frame…`, `set active on empty frame…`, `undo and redo around adding photos to empty frame…`, `cancelled pan rolls content back before fling switches photo` | 4 | `ContentFling`, `SetActive`, `AddPhotos` удалены |
| `ComboBuilderToolbarTest`: `addPhotoDialogClosesExactlyWhenFrameIsFull` | 1 | окна «+Photo» и лимита 5 фото нет |
| `DraftEditorTest`: 11 тестов `addContents`, 6 тестов `removeActiveContent`, `a preset without an active photo changes nothing`, `a new frame is appended on top, centred and half of the canvas` | 19 | операций нет; вместо `addItem` — тесты `addItems` (см. «Новые») |

### Переписаны (то же поведение, новая модель; смысл проверки сохранён)
| Файл | Что изменилось |
|---|---|
| `AppDbTest`, `ComboRepositoryTest`, `EntityExtTest` | рамка хранит `photoId`; названия тестов переформулированы («items and contents» → «items»); `backgroundFrames` берёт цвет одного фото рамки |
| `ItemFrameParityTest` | паритет редактор ↔ список (плеера нет); `incoming` → `content`; тест «FREE хранит, FIT пересчитывает» — прежний |
| `DrawComboBackgroundTest`, `DrawComboClipTest` | только переименование параметра `incoming` → `content` |
| `AspectRelayoutTest` | рамка с одним фото (`item(..., scale, panX, panY)`) вместо списка `ContentDraft`; проверки те же |
| `AutoFitTest` | `ContentDraft` → поля рамки; `ApplyPreset in FIT leaves FREE and does not jump the other photos` → `… and applies the preset` (второго фото в рамке больше нет) |
| `ViewportTest`, `ItemGesturesTest` (остальные 6) | конструктор `ItemDraft` с одним фото |
| `DraftEditorTest` (остальное) | `reorder`, `deleteItem`, `applyFramingMode`, `newComboDrafts` — те же проверки на рамках с одним фото; `a preset in FIT leaves FREE…` без второго фото; `no photos means one blank frame…` → `no photos means no frames and no selection` (**требование изменилось**: пустых рамок нет) |
| `DraftMapperTest` | наборы полей `ItemDraft`↔`ItemSnap` (12), круги «снимок» и «сущности» |
| `DraftHistoryTest` | снимок рамки с полями фото; `a change inside a photo of an item…` → `a change of photo pan inside an item…` |
| `ComboBuilderBackgroundTest` | `save drops empty frames and renumbers z index` → `save renumbers z index and keeps relative order` (пустых рамок нет; перенумерация проверяется на слоях 0, 5, 9); `save does nothing when every frame is empty` → `… when there are no frames` |
| `ComboBuilderViewModelTest` | то же поведение на рамках с одним фото; «undo on empty frame» → «undo on combo without frames» |
| `BuilderExitDialogTest` | комбо из `ComboItemEntity` с `photoId`; в тесте «save without photos» проверяется `items.isEmpty()`, а не «нет фото в рамках» |
| `ComboBuilderToolbarTest` | `Del`, `AutoFit`, `+Frame` на лимите — прежние проверки; нажатие `+Frame` теперь открывает окно, а не добавляет рамку |
| `AppNavViewModelTest` | `playFromComboListOpensPlayer` → `editFromComboListOpensBuilder` (плеера нет) |
| `ComboListOpensBuilderTest` | убран `onPlay`; добавлена проверка: `Edit` открывает редактор, кнопки `Play` нет |
| `ArchitectureRulesTest` | убраны правила и синтетические случаи про `core.playback` и `combo_viewer`; законный случай `Direction` заменён на `FramingMode` |
| `TestDoubles` (обе) | без `insertContents`; `comboItemEntity(id, comboId, zIndex, framingMode, photoId)`; `FakeComboRepository.savedCombos: List<Pair<ComboEntity, List<ComboItemEntity>>>` |

### Новые
| Файл | Тест |
|---|---|
| `MigrationTest` (4) | рамка с несколькими фото сохраняет первое (наименьший `orderIndex`) со своим масштабом и сдвигом; рамка без фото отбрасывается; комбо и фото сохраняются; старая таблица удалена, `isPhotoUsed` работает по рамкам |
| `DraftEditorTest` (13) | `each photo gets its own frame…`, `frames of one request are cascaded`, `cascade keeps the frame centre on the canvas`, `an empty combo takes photos up to the limit`, `duplicate ids inside one request give one frame`, `a photo already used by another frame can be added again`, `the request is cut to the remaining room`, `checkpoint is requested before the new frames are created`; для замены: `replacing the photo keeps the frame…`, `the new photo size decides the fill scale`, `the framing mode survives a replacement`, `replacing with the same photo changes nothing`, `checkpoint is requested before the photo is replaced` |
| `ComboBuilderViewModelTest` | `new combo from three photos gets three frames…`, `adding several photos is one undo step`, `the same photo twice in one request gives one frame`, `photos over the limit are cut off when adding`, `replace photo keeps frame geometry and undo returns old photo`, `replace with the same photo is not an edit`, `replace with unknown photo does nothing` |
| `ComboBuilderToolbarTest` | `delIsDisabledForTheOnlyFrame`, `addFrameOpensThePickerAndDoesNotAddUntilConfirmed`, `pickedPhotosBecomeFrames`, `pickerDoesNotAllowMoreThanTheFreeRoom`, `replaceIsDisabledWithoutSelection`, `replaceSwapsThePhotoKeepsTheFrameAndClosesTheDialog` |
| `DraftMapperTest` | `each frame entity carries exactly the photo of its draft` (одна рамка — одно фото на уровне маппера) |
| `DraftHistoryTest` | `replacing the photo of an item counts as a change and undo restores it` |

Не менялись: `TransformMathTest`, `GestureClassifierTest`, `NearEdgeTest`, `EditorWheelTest`, `ViewportControllerTest`, `ComboBuilderGesturesTest`, `LibraryMultiselectTest`, `LibraryOpensTappedPhotoTest`, тесты импорта, кодека, кэша, фона, `SourceHygieneTest`, `AppModulesTest`. Они не используют удалённое; проверка — только вашим прогоном.

Блокировка удаления фото, занятого рамкой, покрыта существующими тестами на настоящей БД (`AppDbTest`: `a photo used in a combo cannot be deleted`, `PhotoRepositoryTest`: `deleting a photo used in a combo is blocked`); они переписаны/работают через `combo_items.photoId`. Отдельного нового теста нет.

### Правка после первого прогона

Ваш прогон: 318 тестов, 1 падение в `ComboBuilderToolbarTest` (из 10), 1 пропущенный (`RealPhotoImportTest`, как раньше). Текста падения в отчёте не было, поэтому причина — наиболее вероятная по коду, не подтверждённая логом (ошибка в приложении, не в тесте): `PickPhotosDialog` определял режим «несколько фото» как `maxPick > 1`, а `+Frame` при **одном** свободном месте (9 рамок из 10) передаёт `maxPick = 1`. Окно переключалось в режим «коснулся — добавил» и кнопки подтверждения не было. Теперь режим задаётся явным параметром `multi`. Заодно в тесте `pickerDoesNotAllowMoreThanTheFreeRoom` кандидаты поставлены первыми в списке: сетка ленивая, и ячейки за пределами окна 320 dp не существуют.

### Правка после ручной проверки

Симптом: после «Create combo» в редакторе видна одна рамка, остальные появляются после клика по свободному месту. Причина: кэш битмапов — не Compose-состояние, а `EditorViewport` получал `bitmaps`, но не передавал дальше; когда грузилось второе и третье фото, `EditorCanvas` не перекомпоновывался и draw-лямбда не перерисовывалась до следующего события (клик менял `revision`). Теперь `bitmaps` доходит до `EditorCanvas` и захватывается draw-лямбдой. Автотеста нет (нужен реальный асинхронный кэш и пиксельная проверка); проверяется глазами: все три рамки видны сразу после открытия.

## 3. Как проверить

1. Сделайте копию старой базы: `%APPDATA%\PhotoCombo\combo.db` (и рядом `-wal`/`-shm`, если есть).
2. `.\gradlew.bat :composeApp:desktopTest` — ожидается `BUILD SUCCESSFUL`. Если сборка падает, пришлите строки с `e:` (ошибки компиляции) или имена упавших тестов.
3. После сборки проверьте, что появился `composeApp\schemas\com.combo.data.AppDb\2.json`. Его создаёт Room при компиляции; в архив я его не клал.
4. Запуск на **старой** базе: `.\gradlew.bat :composeApp:run`. Старые комбо открываются, в каждой рамке одно фото (то, что было первым). Старые файлы фото и миниатюры на месте.
5. Руками:
   - В библиотеке отметить 3 фото → **Create combo** → три рамки.
   - Перенос рамки, щипок, ресайз за грань, пан и зум фото (BORDER), **Fit**, **Fill**, **AutoFit**, порядок ▲/▼, **Del**, формат, фон, **Undo/Redo**, **Save**, повторное открытие.
   - **+Frame**: открывается окно «Add frames»; отметить 2 фото → «Add 2» → появились 2 рамки, последняя выделена; одно **Undo** убирает обе.
   - **Replace** при выбранной рамке: окно «Replace photo» без текущего фото; касание заменяет фото, рамка осталась на месте и того же размера; **Undo** возвращает прежнее фото.
   - **New combo** из вкладки Combos: пустой холст; **Save** → «Add at least one photo»; после **+Frame** сохранение работает.
   - В списке комбо нет кнопки Play, в редакторе нет кнопок Switch, M-Tr, A-Tr, +Photo, −Photo и точек-«страниц».
   - Удаление фото, занятого рамкой, блокируется сообщением.

## 4. Риски и места, на которые смотреть

| Риск | Комментарий |
|---|---|
| Миграция Room KMP | Написана без запуска. SQL пересоздаёт `combo_items`; при открытии Room сверяет колонки, внешние ключи и индексы с v2 (текст SQL не сверяется). Если на шаге 4 приложение падает при старте с ошибкой про схему или миграцию, пришлите текст исключения. `MigrationTest` строит БД v1 обычным SQL через bundled-драйвер, а не через `MigrationTestHelper` (он недоступен здесь); если тест не скомпилируется из-за API `execSQL`/`SQLiteConnection`, пришлите ошибку. |
| Потеря фото при миграции | В старой рамке с несколькими фото остаётся только первое. Это следствие решения «одна рамка — одно фото», но необратимо: делайте копию БД (шаг 1). Сами файлы фото не удаляются, они остаются в библиотеке. |
| Висячие ссылки после широкого удаления | Поиск по `commonMain`, `desktopMain`, `desktopTest` не находит `SwitchMode`, `TransitionType`, `Direction`, `PlaybackEngine`, `AutoScheduler`, `SwitchAnimator`, `ContentDraft`, `ComboItemContentEntity`, `ItemWithContents`, `MAX_CONTENTS_PER_ITEM`, `combo_viewer`, кроме `data/Migrations.kt` и `MigrationTest` (там старая схема). Упоминания остались в исторических документах (`docs/KMP_MIGRATION_PLAN.md`, `docs/PHASE1_CHANGELOG.md`) — они описывают прошлое. |
| UI-тесты окна выбора | `ComboBuilderToolbarTest` кликает по ячейкам `PhotoGrid` внутри `AlertDialog` через `addPhotoCellTag` и по `DIALOG_TAG_PICK_CONFIRM`. Если окно рисуется в отдельном корне и теги не находятся, пришлите вывод теста. |
| Подтверждение удаления последней рамки | Единственную рамку по-прежнему нельзя удалить (`Del` выключена). Комбо без рамок возможно только как новое пустое комбо до первого `+Frame`. |
| `@Relation` в `ComboWithItems` | Оставлен (плоская связь по `comboId`), а не заменён отдельным запросом: схема проще прежней, и риск для Room KMP небольшой. Если KSP ругнётся на `ComboWithItems`, замена — отдельный запрос в DAO. |

## 5. Документы

Обновлены: `README.md` (пакеты, модель данных, редактор, навигация, тесты, ограничения), `USER_GUIDE.md` (понятия, кнопки, +Frame/Replace, лимиты, удалён раздел про воспроизведение), `AGENTS.md` (пакеты, правила архитектуры, инвариант «одна рамка — одно фото», миграции Room, лимиты), `MIGRATION_REPORT.md` (раздел об упрощении), `docs/IMPROVEMENTS_PLAN.md` (пометка у пункта №1).

## 6. Правки спецификации `Instructions.md`

Файла нет в архиве, поэтому правки даны текстом. Номера разделов взяты из ссылок в `README.md` и `AGENTS.md` (§3, §5, §14, §15, §16); остальное привязывайте по смыслу.

1. **Описание продукта.** Заменить «рамка может содержать несколько фото с переключением свайпом и/или по таймеру, с анимированными переходами» на «в каждой рамке ровно одно фото; таймеров, переходов, анимаций и ручного переключения нет».
2. **§3, принципы.** п. 3 (WYSIWYG): убрать «плеера» — «для редактора и превью списка». п. 9 («авто-таймеров в редакторе нет, автосмена только в плеере») заменить на «одна рамка — одно фото». п. 11 («без обратной совместимости, `version = 1`») заменить на «любое изменение схемы сопровождается миграцией; текущая версия 2».
3. **§5, раскладка пакетов.** Удалить `core/playback` и `feature/combo_viewer`; удалить из `core/render` `Transitions`; из `core/model` оставить `FramingMode`; из `core/util` убрать `EnumCycle`; в `feature/builder` вместо `PageDots`, `AddPhotoDialog` — `PickPhotosDialog`.
4. **Модель данных.** Таблица `combo_item_contents` удаляется. В `combo_items` убрать `switchMode`, `autoIntervalMs`, `manualTransition`, `autoTransition`; добавить `photoId` (FK RESTRICT на `photos`), `contentScale`, `panX`, `panY`. «Режим одно фото выводится из `contents.size == 1`» удалить.
5. **Ядро рендера.** Из описания `ItemFrame` убрать `outgoing`, `transition`; удалить раздел о переходах; `buildItemFrame(border, mode, content)`.
6. **Воспроизведение.** Раздел о `PlaybackEngine`, `AutoScheduler`, `SwitchAnimator`, паузе по сворачиванию, пороге флинга (`FLING_MIN_VELOCITY`, `FLING_DOMINANCE`) удалить целиком.
7. **Редактор.** Убрать быстрый вертикальный свайп по рамке, `PageUp/PageDown`, точки-«страницы». События `ContentFling`, `SetActive`, `AddPhotos`, `AddItem`, `RemoveActiveContent`, `SetSwitchMode`, `SetManualTransition`, `SetAutoTransition` заменить на `AddItems(photoIds)` и `ReplacePhoto(id, photoId)`. Тулбар: убрать Switch, M-Tr, A-Tr, +Photo, −Photo; `+Frame` — выбор фото, новая кнопка Replace. Пустые рамки не существуют; новое комбо из пустого набора — без рамок; `Save` без рамок показывает «Add at least one photo».
8. **Навигация.** Удалить экран плеера (`Player`) и кнопку Play в списке комбо.
9. **§14, лимиты.** Удалить `MAX_CONTENTS_PER_ITEM = 5` и `DEFAULT_AUTO_INTERVAL_MS`. `MAX_ITEMS = 10` остаётся.
10. **§15 (бэклог) и §16 (статус).** Из «реализовано» убрать переходы и воспроизведение; из бэклога убрать «изменение порядка фото внутри рамки».
11. **Тесты.** Список тестов обновить по разделу 2.

## 7. Дальше

План 2 (новая нижняя панель, вариант C) начинается после вашей проверки этой поставки. Сначала обновлённый макет (без Playback и группы фото, с «Replace photo», ширины 420, 800, 1200, светлая и тёмная темы); к коду перехожу после вашего «да».
