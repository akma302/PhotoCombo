# PhotoCombo: agent map

Windows desktop application built with KMP / Compose Multiplatform. Load only
material relevant to the task; there is no required document reading order.
Find actual behavior and APIs in code and tests. The owner's task defines product changes.

## Where to look

Table paths are relative to the repository root. Within a module, shared code is in
`src/commonMain/kotlin/com/combo`, platform code in `src/desktopMain`, and tests in `src/desktopTest`.
Use the file and symbol names below with `rg --files` and `rg -n`.

| Area | Location / entry points |
|---|---|
| Features and user actions | [feature/](feature/): library, combo-list, viewer, builder; `*Screen.kt`, `*ViewModel.kt`; editor events in `BuilderContract.kt` |
| Navigation, ViewModel lifetime, DI | [composeApp/](composeApp/src/): `AppNav.kt`, `ScreenKey.kt`, `ViewModelStoreOwners.kt`, `di/` |
| Shared gallery and item sizing | [ui-common/](ui-common/src/commonMain/kotlin/com/combo/ui/common/): `GalleryGrid.kt`, `PhotoGrid.kt`, `GalleryScaleState.kt`; persistence in `GallerySettingsRepository` and `GallerySettingsStorage` |
| Geometry, framing, rendering | [core/render/](core/src/commonMain/kotlin/com/combo/core/render/): `Framing.kt`, `ItemFrames.kt`, `DrawItem.kt`, `DrawCombo.kt`; backgrounds in the adjacent `background/` directory |
| Editing, Undo, combo saving | [builder/](feature/builder/src/commonMain/kotlin/com/combo/feature/builder/): `draft/`, `history/`, `FinalizeCanvas.kt`, `ComboBuilderViewModel.kt`; input in `EditorGestures.kt`, `EditorWheel.kt` |
| Database, file paths, migrations | [data/](data/src/): `Entities.kt`, `AppDb.kt`, `Migrations.kt`, `PhotoStorage.kt`; schemas in [data/schemas/](data/schemas/) |
| Repositories, import, image cache | [domain/](domain/src/commonMain/kotlin/com/combo/domain/): `Repositories.kt`, `Impls.kt`, `PhotoImporter.kt`, `PhotoAssets.kt`, `image/` |
| Components and visual decisions | [design-system/](design-system/src/); visual requirements and token locations in [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md); CMP UI work in [cmp-ui](.agents/skills/cmp-ui/SKILL.md) |
| Build, verification, reports, Git | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Modules, dependencies, versions, targets | `settings.gradle.kts`, root and module `build.gradle.kts`, `gradle/libs.versions.toml` |
| Architecture constraints | `verifyModuleGraph` in root Gradle; `ArchitectureRulesTest` and `SourceHygieneTest` in composeApp |
| Fixtures and UI test host | [test-support/](test-support/src/desktopMain/); scenarios in the owning module's tests |
| Business limits and thresholds | [Const.kt](core/src/commonMain/kotlin/com/combo/Const.kt) |

## Architecture constraints

- Dependency direction: `core ← data ← domain ← feature ← composeApp` (an arrow points
  toward a dependency). Features may also depend on `ui-common` and `design-system`.
- Features must not depend on each other; `ui-common` must not import features.
  `design-system` has no production project dependencies; its internal direction is
  `tokens ← theme ← primitives ← components`, with icons independent. `test-support` is test-only.
- `commonMain` must not import JVM/Android/Skia APIs; OS adapters belong in `desktopMain`.
  The exact module graph is enforced by `verifyModuleGraph` and package boundaries by
  `ArchitectureRulesTest`; do not weaken either check to accommodate a dependency.

## Constraints to preserve

- Original photos remain unchanged. User file layout and persisted enum names must remain
  compatible with existing data; schema changes require a migration and its test.
  Use temporary directories for tests rather than the personal library.
- The editor and previews share the core renderer. Saving finalizes a separate snapshot:
  it must not modify the working draft or Undo, even if writing fails. Gesture cancellation creates no checkpoint.
  Find these contracts in `ItemFrameParityTest`, `DraftMapperTest`, `*HistoryTest`, `*Finalize*Test`.
- Source files: UTF-8 without BOM, LF. Write comments in English and explain the code's purpose.

## Maintaining this map

Add a pointer when a new area is difficult to discover.
Do not copy algorithms, signatures, token values, PR history, or test-run results here.
Commands belong in DEVELOPMENT, visual requirements in DESIGN_SYSTEM, and implementation details in code and tests.
