> **Уточнения по итогам фазы 1** (подробности — в `docs/PHASE1_CHANGELOG.md`):
> - старый `:app` не остаётся в одной Gradle-сборке с новым модулем: там Kotlin 2.0.20, KSP и Hilt старых
>   версий, а новому модулю нужен Kotlin 2.3.20. Поэтому старое приложение лежит в `legacy-android/`
>   отдельной сборкой, а в корне теперь только `:composeApp`;
> - в Compose Multiplatform 1.12.1 `material3` имеет версию 1.12.0-alpha03 (alpha), в фазе 3 это нужно решить отдельно;
> - KSP версионируется независимо от Kotlin: 2.3.11.

# План миграции PhotoCombo: Android → Kotlin Multiplatform + Compose Multiplatform (desktop, Windows)

Версия 2. Заменяет предыдущий план. Код не переписывался.

## Как устроена работа

- Все фазы выполняю я. Ваша работа — только проверка в конце фазы: вы запускаете команды в PowerShell и присылаете результат. Работа с git и репозиторием не нужна.
- Я не могу собрать проект в своём окружении (нет доступа к Maven и Gradle, нет Android SDK). Код пишется без компиляции, поэтому после каждой фазы возможны ошибки сборки. Это ожидаемо: присылаете лог, я исправляю, фаза закрывается после зелёной проверки.
- Фазы идут по возрастанию объёма, чтобы ошибки обнаруживались на небольших порциях кода.
- Всё непроверенное помечено `НЕ ПРОВЕРЕНО`.

## Принятые решения

| Вопрос | Решение | Причина |
|---|---|---|
| Android-таргет | Не сохраняю. Один KMP-модуль с таргетом desktop | Без возможности собрать проект AGP + KMP добавляют слепой риск в настройке сборки. Старый `:app` остаётся нетронутым до последней фазы как эталон для сравнения |
| Переносимость | `commonMain` не знает про JVM и desktop, платформенное спрятано за интерфейсами | Android позже добавляется реализациями интерфейсов, без переделки остального кода |
| DI | Koin 4.2.2 | ваше решение |
| Навигация | Navigation 3 (`navigation3-ui`, у Compose Multiplatform 1.12.1 версия 1.1.2) | Nav2 в таблице CMP 1.12.1 всё ещё beta (2.10.0-beta01), Nav3 нет. Back stack — обычный список, поэтому Esc и «назад» на desktop подключаются тривиально. Нет `NavController`, `navArgument`, строковых маршрутов |
| Платформа | Windows (JDK 17+ для упаковки) | ваше решение |

Что не подтверждено по Nav3 (документация CMP это не описывает): поведение «назад» на desktop и привязка ViewModel к записи back stack (`lifecycle-viewmodel-navigation3`). План на случай отказа: свой тонкий стек поверх `mutableStateListOf` и собственный `ViewModelStoreOwner`.

## Версии (проверено 2026-10-13)

| Что | Версия | Статус |
|---|---|---|
| Compose Multiplatform | 1.12.1 | подтверждено |
| Lifecycle (CMP) | 2.11.0 | подтверждено |
| Navigation 3 (CMP) | 1.1.2 | подтверждено по таблице релиза |
| Room | 2.8.5 | подтверждено, stable |
| androidx.sqlite (`sqlite-bundled`) | 2.7.1 | подтверждено |
| Koin | 4.2.2, собран на Kotlin 2.3.20 | подтверждено |
| Kotlin | 2.3.20 | выбрано по Koin. НЕ ПРОВЕРЕНО: совместимость с Gradle 9.3.0 из вашего wrapper |
| `material-icons-extended` | 1.7.3 (декабрь 2024, новее нет) | подтверждено; описана как «очень большая зависимость» |
| JDK | 17+ для упаковки | подтверждено |

Room 3 (`androidx.room3`, alpha) не берём.

## Важные факты о коде (из разбора)

- `core/*` почти чистый Compose. Привязки: `SystemClock`, `android.graphics.Bitmap` в `AverageColor`, `Lifecycle` в `PlaybackLifecycle`; JVM-only `Math.*`, `UUID`, `System.currentTimeMillis`.
- Тяжёлые места: `PhotoImporter`, `LruBitmapSource`, `PhotoStorage`, `ThumbImage`, пикер в `LibraryScreen`, `BackHandler` (три экрана).
- Hilt в 8 ViewModel, Room 2.6.1 с уже suspend/Flow DAO.
- 44 тестовых файла, около 9 на Robolectric.
- Skia (`Codec`): есть `encodedOrigin` (EXIF-ориентация) и `readPixels`. Отдельного декода с сэмплингом нет. Для JPEG Skia умеет масштабировать степенями двойки через `readPixels` в битмап меньшего размера. НЕ ПРОВЕРЕНО на практике, запасной путь — полный декод и уменьшение.

---

## Фаза 1. Каркас и слой данных

Что делаю:
1. Создаю модуль `:composeApp` (KMP, таргет `jvm("desktop")`, Compose Multiplatform 1.12.1, Kotlin 2.3.20) рядом с существующим `:app`. Каталог версий, плагины, Koin, Room KMP (`kspDesktop`, `sqlite-bundled`), `kotlinx-coroutines-swing` (без него у `viewModelScope` на desktop нет Main-диспетчера).
2. Копирую `core/*` в `commonMain` с заменами: `SystemClock` → `TimeSource.Monotonic`; `Math.pow/cbrt/round` → `kotlin.math`; `UUID` → `kotlin.uuid.Uuid` (стабильность на этом Kotlin НЕ ПРОВЕРЕНА, запасной вариант — инъекция генератора id); `System.currentTimeMillis` → инъецируемые часы. `AverageColor` переписываю на `ImageBitmap.readPixels` (результат чуть отличается от Android, это осознанное изменение).
3. Переношу слой `data`: сущности, DAO, `AppDb` с `@ConstructedBy` и `expect object`, конвертер фона, фабрика БД для Windows (`%APPDATA%\PhotoCombo`).
4. Переношу чистые JVM-тесты в `desktopTest` без изменения assertion. Добавляю один новый тест БД: временный файл, вставка, чтение с `@Relation`, транзакция.

Риски: совместимость Kotlin и Gradle 9.3.0, `JDK 25` в `gradle-daemon-jvm.properties`, поддержка `@Relation` в Room KMP (документация её не описывает).

Проверка (вам):
1. `.\gradlew.bat :composeApp:desktopTest` — должно быть зелёным. Пришлите итог по числу тестов; при ошибке — лог целиком.
2. `.\gradlew.bat :app:assembleDebug` — старое приложение всё ещё собирается.
3. Если сборка падает на версиях, пришлите вывод `.\gradlew.bat --version` и `java -version`.

## Фаза 2. Платформенный слой и домен

Что делаю:
1. Интерфейсы в `commonMain`: `ImageSource` (вместо `Uri`: имя, mime, поток), `ImageCodec`, `AppDirs`, `Logger`, `ImagePicker`. Файловая работа — через Okio (`FileSystem` внедряется, `FileSystem.SYSTEM` только в desktop-коде).
2. Desktop-реализации: кодек на Skia (границы, декод с сэмплингом, EXIF по `encodedOrigin`, поворот и отражение, JPEG/PNG), каталоги данных, пикер на AWT `FileDialog` (multi-select).
3. Переписываю `PhotoStorage` (раскладка `photos/` и `thumbs/` не меняется, `.nomedia` не нужен), `PhotoImporter`, `LruBitmapSource` (свой LRU, `Mutex`, размер записи `width*height*4/1024`, явный лимит кэша), `PhotoRepositoryImpl`, `ComboRepositoryImpl` (транзакция через `useWriterConnection { immediateTransaction }`), `PhotoAssets`.
4. Koin-модули для всего слоя.
5. Тесты: домен на фейках; кодек — на фикстурах, которые я генерирую скриптом (8 вариантов EXIF-ориентации, PNG, WebP, большое фото для сэмплинга). Старые тесты `PhotoImporter`, `PlayerAndCache` и кэша переписываю под новые типы. Ветки TRANSPOSE и TRANSVERSE, помеченные в старом коде как непроверенные, закрываю тестом.

Риски: Skia не умеет масштабный декод — тогда включается запасной путь с большим расходом памяти; WebP.

Проверка (вам):
1. `.\gradlew.bat :composeApp:desktopTest` — зелёный.
2. Положите 3–4 ваших реальных фото (в том числе с телефона, в портретной ориентации, и одно очень большое) в любую папку. Я добавлю в тесты временную переменную окружения с этой папкой — напишу точную команду в конце фазы. Результат: что тест импорта прошёл и фото получили верную ориентацию.

## Фаза 3. UI, навигация и первый запуск

Что делаю:
1. Переношу `feature` и `ui`: экраны, ViewModel, диалоги. Hilt → Koin (`koinViewModel()`, фабрики ViewModel). `SavedStateHandle` сохраняю: Koin-фабрика собирает его вручную из ключа навигации, чтобы не менять конструкторы и тесты ViewModel (доступность в KMP НЕ ПРОВЕРЕНА).
2. Навигация на Navigation 3: ключи `Library`, `Combos`, `Viewer(photoId)`, `Builder(comboId, photos)`, `Player(comboId)`; нижняя панель только на двух корневых экранах.
3. `BackDispatcher`: единый стек обработчиков вместо `BackHandler`, управляется клавишей Esc и боковой кнопкой мыши. Подключаю к трём местам (мультивыбор, выход из редактора, `ExitDialog`).
4. `PlaybackLifecycle` на события окна (сворачивание — пауза, возврат — продолжение). Поведение событий жизненного цикла CMP на desktop НЕ ПРОВЕРЕНО, реализую через состояние окна.
5. Иконки: `material-icons-extended` 1.7.3. Если не соберётся с CMP 1.12.1, пять нехватающих иконок рисую как собственные `ImageVector`.
6. `main()`, окно с минимальным размером и заголовком PhotoCombo, старт Koin.
7. Переношу тесты ViewModel без UI.

Отличие от Android: при переключении вкладок Library и Combos состояние экрана (например, режим выбора) может сбрасываться, потому что на Android оно сохранялось через `saveState/restoreState`. Если это заметно, закрепляю ViewModel этих двух экранов за окном.

Проверка (вам):
1. `.\gradlew.bat :composeApp:desktopTest` — зелёный.
2. `.\gradlew.bat :composeApp:run` и пройти сценарий: импорт нескольких фото, открытие фото на весь экран, создание комбо из выбранных фото, сохранение, список комбо, открытие в редакторе, воспроизведение, удаление фото (в том числе фото, используемого в комбо — должно блокироваться), выход с несохранёнными изменениями. Мышь пока работает как одиночное касание: перетаскивание, клик, двойной клик. Щипка и колеса ещё нет, это фаза 4.
3. Пришлите список того, что не работает или выглядит иначе, чем на телефоне.

## Фаза 4. Ввод мышью и клавиатурой, UI-тесты, охранные правила

Что делаю:
1. Соответствия жестов:

| Касание | Desktop |
|---|---|
| Щипок (EDIT/BORDER) | колесо мыши (+ Ctrl), центр масштаба в точке курсора |
| Свайп влево/вправо (просмотр, плеер) | стрелки и перетаскивание |
| Быстрый вертикальный свайп по рамке | то же перетаскивание, плюс PageUp/PageDown |
| Двойное касание (смена режима) | двойной клик |
| Долгое нажатие (мультивыбор) | долгое нажатие, правый клик, Ctrl+клик |
| «Назад» | Esc, боковая кнопка мыши |

2. Колесо шлёт `START/CHANGE/END` как обычные жесты: undo-шаг только на завершённом действии, `END` по таймауту после последнего тика колеса. Проверяю `handlePx` и `touchSlop` под курсор.
3. Переписываю около 9 Robolectric-тестов на Compose UI Test для desktop. `Espresso.pressBack` → Esc. Совпадение `performTouchInput` НЕ ПРОВЕРЕНО. Попиксельные тесты (`DrawComboBackgroundTest`, `ItemFrameParityTest`) идут на Skia без Robolectric, допуски подбираю заново.
4. Обновляю `ArchitectureRulesTest` и `SourceHygieneTest` под новые пути. Добавляю правило: `commonMain` не импортирует `android.*`, `java.*`, `javax.*`, `org.jetbrains.skia.*`.
5. Тест на колесо: серия тиков даёт ровно один undo-шаг.

Проверка (вам):
1. `.\gradlew.bat :composeApp:desktopTest` — весь набор зелёный.
2. `.\gradlew.bat :composeApp:run` и пройти ручной список: все пункты таблицы выше; в редакторе — перетаскивание рамки, изменение границы, сдвиг и масштаб содержимого колесом, Undo/Redo после жестов, режимы EDIT и BORDER, AutoFit, Fit, Fill, смена фона; в плеере — переходы и автосмена.
3. Пришлите, какие пункты работают неудобно (чувствительность колеса, порог перетаскивания).

## Фаза 5. Упаковка, документация, удаление старого кода

Что делаю:
1. Настройка упаковки Compose Desktop для Windows: имя, версия, иконка, `createDistributable` (папка с приложением и вложенным JRE), опционально MSI.
2. Удаляю модуль `:app`, Hilt, AGP, схему Room `app/schemas` переношу. Убираю `local.properties` и Android-специфику из `.idea`.
3. Обновляю `README.md`, `AGENTS.md` (команды PowerShell, структура, правила `commonMain`), `USER_GUIDE.md` (мышь и клавиатура вместо жестов, где хранятся данные, Esc).

Проверка (вам):
1. `.\gradlew.bat :composeApp:desktopTest` — зелёный на проекте без `:app`.
2. `.\gradlew.bat :composeApp:createDistributable`, запустить `.exe` из `composeApp\build\compose\binaries\main\app\PhotoCombo\` на машине без IDE. Приложение должно открыться, импортировать фото и сохранять данные между запусками.
3. MSI (по желанию): `.\gradlew.bat :composeApp:packageMsi`. Для MSI jpackage требует установленный WiX Toolset; требуемая версия для вашего JDK НЕ ПРОВЕРЕНА, поэтому MSI не входит в критерий готовности.

---

## Изменения поведения, которые не сохранятся 1:1

- `avgColor` считается иначе (фаза 1): новые фото могут получить чуть другой цвет.
- Миниатюры и сэмплинг зависят от кодека: размеры могут отличаться на 1 px.
- Кэш битмапов: на Android был 1/4 `maxMemory`, на desktop лимит задаётся явно.
- Состояние вкладок при переключении (фаза 3).
- Haptic-отклик исчезает.
- База с Android на desktop не переносится.
- Android-версии приложения после фазы 5 нет. Для возврата нужны Android-реализации интерфейсов и модуль приложения.

## Источники

- Compose Multiplatform releases: https://github.com/JetBrains/compose-multiplatform/releases
- Navigation 3 в Compose Multiplatform: https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html
- Compose compatibility and versioning: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- Room KMP: https://developer.android.com/kotlin/multiplatform/room
- Room releases: https://developer.android.com/jetpack/androidx/releases/room
- SQLite releases: https://developer.android.com/jetpack/androidx/releases/sqlite
- Koin releases: https://insert-koin.io/docs/support/releases
- Skiko `Codec`: https://github.com/JetBrains/skiko/blob/master/skiko/src/commonMain/kotlin/org/jetbrains/skia/Codec.kt
- material-icons-extended (CMP): https://mvnrepository.com/artifact/org.jetbrains.compose.material/material-icons-extended
