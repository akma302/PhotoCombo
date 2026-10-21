# AGENTS.md

> **Статус миграции на Kotlin Multiplatform (идёт).** Новый проект — модуль `composeApp`
> (Kotlin Multiplatform + Compose Multiplatform, цель — desktop/Windows). Прежнее Android-приложение
> лежит нетронутым в `legacy-android/` как отдельная Gradle-сборка и служит эталоном поведения
> до конца миграции. Ниже описана прежняя Android-структура; команды из раздела «Build & verify»
> для неё выполняются в каталоге `legacy-android/`, для нового проекта — из корня:
> `.\gradlew.bat :composeApp:desktopTest`. План: `docs/KMP_MIGRATION_PLAN.md`.

Guidance for AI agents (and humans) working in this repository. Read it fully before changing code.

## Source of truth

- `Instructions.md` is the project spec and single source of truth (Russian). Its preamble binds agents: implement `[СОГЛАСОВАНО]`/`[ДИЗАЙН]` sections without changing signatures; never implement `[БЭКЛОГ]` (§15) without an explicit task. If a task contradicts the spec, flag it and propose a spec edit instead of silently diverging.
- `README.md` mirrors the architecture; `USER_GUIDE.md` describes behavior for end users. Trust code/config over both when they conflict, then fix the document.
- The spec's §5 (package layout) predates the modular layout described below. Until the owner updates §5, the "Project structure" section of `README.md` is authoritative for packages.

## Build & verify (Windows / PowerShell)

```powershell
.\gradlew.bat :app:assembleDebug        # full build, produces app-debug.apk
.\gradlew.bat :app:testDebugUnitTest    # JVM unit tests (no emulator needed)
.\gradlew.bat :app:kspDebugKotlin       # fast Room/Hilt/KSP-only check
```

- No `ANDROID_HOME` env var: `local.properties` at repo root must contain `sdk.dir=C:\Users\akma3\AppData\Local\Android\Sdk`. Don't commit it.
- JDK 17 required; compileSdk 35, minSdk 26.
- When piping Gradle output in PowerShell, console wrapping truncates error lines — redirect via `cmd /c "... > file.log 2>&1"` then search the log.
- Run `:app:testDebugUnitTest` after every logical change. A change is not done while tests are red.

## Package layout and dependency direction

```
core/{model,background,color,render,util}   no imports from data/domain/feature/ui
data/ (+ di/)            Room entities/DAOs, PhotoStorage, entity adapters
domain/ (+ image/, di/)  repository interfaces + implementations, PhotoImporter, PhotoAssets, LruBitmapSource
feature/{library,viewer,combo_list,builder}/   builder also has draft/ and history/
ui/ (+ common/)          navigation and shared composables
```

Direction: `core ← data ← domain ← feature ← ui`. `ArchitectureRulesTest` enforces it:

- `core` never imports `data`, `domain`, `feature`, `ui`; `core.model` imports nothing from `com.combo.*`;
- `data` does not import `domain`/`feature`/`ui`; `domain` does not import `feature`/`ui`;
- `feature.X` does not import `feature.Y`; shared UI goes to `ui.common` (which must not import `feature`);
- in `feature.builder`, the subpackages `draft` and `history` never import the package root;
- there are no import cycles between packages.

If you need a new dependency that breaks a rule, move the code (shared enums to `core.model`, shared composables to `ui.common`) instead of relaxing the rule. Every ViewModel lives in its own file next to its screen.

## Architecture invariants (easy to break)

- **WYSIWYG**: all item rendering goes through the single `DrawScope.drawItem()` in `core/render/DrawItem.kt`, used by the editor and the combo list previews. Never fork or duplicate drawing logic.
- **Frame assembly**: an `ItemFrame` is built **only** via `buildItemFrame()` (`core/render/ItemFrames.kt`). Never construct `ItemFrame(...)` by hand outside that file.
- **Combo drawing**: canvas content (background + frames) is drawn **only** via `drawCombo()` (`core/render/DrawCombo.kt`). Editor-only overlays (selection outline) are the sole exception and live in the builder screen.
- **Framing**: how a photo sits in a frame is decided only in `effectiveFraming()` / `contentArgsOf()` (`core/render/Framing.kt`).
- **Crop is emergent**: no normalized crop-rect is stored anywhere. It arises from `clipRect(border) + contentScale + pan`. Content must always be drawn aspect-preserving (`TransformMath`).
- **Combo = config**: only ids + transform params live in Room; originals are never modified. Bitmaps are decoded on demand through `LruBitmapSource`; ViewModels load them through `PhotoAssets`.
- **File locations**: paths on disk come only from `PhotoStorage` (`filesDir/photos/<fileName>`, `filesDir/thumbs/<photoId>.jpg`). The layout is user data: never change it.
- **Builder drafts are mutable** by design; ViewModels emit `revision` bumps instead of copying state per gesture frame. Undo snapshots are taken only on completed actions (`START`→snapshot, `END`→checkpoint, `CANCEL`→restore) via `DraftHistory`; a gesture that changed nothing must leave no undo step. Snapshot classes (`ComboSnap`/`ItemSnap`) are data classes and rely on structural equality — keep them that way and keep their fields in sync with the drafts (`DraftMapperTest` checks it).
- **One frame — one photo**: a frame (`ItemDraft`, `ComboItemEntity`) holds exactly one `photoId` with its `contentScale`/`panX`/`panY`. There are no empty frames, no per-frame photo lists, timers, transitions or switching. A new frame is created only together with its photo (`DraftEditor.addItems`, `newComboDrafts`).
- **avgColor** is computed exactly once at import time; letterbox fill = avgColor with alpha 0.5 (`Const.FILL_ALPHA`). Never recompute at render.
- **EXIF is normalized to pixels at import**; render/decode code must not handle orientation.
- **Enum names are a storage format** (`.name` is written to Room and JSON). Never rename an enum value without a migration.

## Room gotchas

- Enums are stored as String columns (migration safety); convert via `.name` / `valueOf` accessors on entities.
- `ComboWithItems` is a flat `@Embedded` + one `@Relation` over `ComboItemEntity`. If a nested relation is ever added, the outer `@Relation` needs the explicit `entity` parameter when its target POJO itself contains `@Embedded`/`@Relation`; omitting it fails KSP with confusing "Cannot find the child entity column" errors.
- `@Relation` does not sort; the repository re-sorts items by `zIndex` after load.
- Schema exports to `app/schemas` (KSP `room.schemaLocation`). `AppDb.version` is 2; migrations live in `data/Migrations.kt` and are registered in `DatabaseFactory`. For any further entity change bump `version`, add a migration and a case in `MigrationTest` (or get an explicit decision to reset data).

## Conventions

- Gestures are hand-rolled `awaitEachGesture` loops dispatching phase events (`START|CHANGE|END|CANCEL`) to the ViewModel; do not replace them with `detectTransformGestures` (it lacks completion callbacks, which undo requires). The editor's loop is a single `pointerInput(Unit)` block: everything composition-dependent is read through `State` wrappers, never added as keys.
- Material icons come from `material-icons-extended` (the core set lacks Undo/Redo/AddAPhoto/etc. used here).
- Limits from `Const.kt` are policy (§14), not suggestions: MAX_ITEMS=10, pan-clamp, zoom bounds, deletion blocking for photos used in combos.
- Pure logic (geometry, editing operations, history, mappers, gesture classification) must stay free of Android/Compose UI types so it can be tested on the plain JVM. Put new logic there, not into composables or ViewModels.

## Tests

- New logic needs tests; a bug fix needs a regression test that fails without the fix.
- Never change an assertion, delete or `@Ignore` a test to make it pass. If a test and the code disagree, decide which one is right and say so in your report.
- Tests that do not touch Android types run on the plain JVM; screen tests use Robolectric + Compose Test (no emulator). Shared fakes are in `app/src/test/java/com/combo/ui/TestDoubles.kt`.
- Test names describe behavior; a test class covers one unit. Do not name tests after tasks, batches or ticket numbers.
- `SourceHygieneTest` guards the sources: UTF-8 without BOM, LF only, no `U+FFFD`, no runs of `????`, no code at column 0 inside blocks. Edit files with an editor or tools that preserve this. Never write source files with PowerShell `>`, `Out-File`, `Set-Content` or `WriteAllLines` (they add CRLF/BOM or break Cyrillic).

## Comments and documentation

- Code comments are in Russian. KDoc says what a thing is for and why it is shaped that way; do not narrate what the next line does.
- Comments describe the code as it is now. Do not write history ("раньше", "теперь", "was moved from…"), and do not refer to chats, task batches, review item numbers or files that are not in the repository. Use version control for history.
- If a comment states a guess, say so ("НЕ ПРОВЕРЕНО: …"). Never present an unverified claim as fact.
- When behavior changes, update `README.md` (architecture) and `USER_GUIDE.md` (user-visible behavior) in the same change.
