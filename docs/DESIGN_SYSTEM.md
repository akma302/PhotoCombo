# PhotoCombo design system

## Implementation entry points

Shared toolkit root: [designsystem/](../design-system/src/commonMain/kotlin/com/combo/ui/common/designsystem/).
Read numerical values and public APIs in source; do not duplicate them in documentation.

| Need | Location relative to the toolkit root |
|---|---|
| Colors, typography, sizes, spacing, focus, motion | `tokens/Pc*.kt` |
| Access to theme roles | `theme/PcTheme.kt` |
| Text, surfaces, decoration, shared focus | `primitives/`, including `PcControl.kt` |
| Existing control and its contract | `components/Pc*.kt` |
| Icons | `icons/PcIcons.kt` |

Desktop fonts and dialogs: `design-system/src/desktopMain/kotlin/com/combo/ui/common/`.
Live examples: `design-system/src/desktopMain/kotlin/com/combo/ui/gallery/DesignSystemGallery.kt` and `ComponentExamples.kt`; the shared grid example and launcher are in `composeApp/src/desktopMain/kotlin/com/combo/ui/GalleryLauncher.kt`.
Contract and state tests: `design-system/src/desktopTest/`.
Launch commands and screenshot locations: [DEVELOPMENT](DEVELOPMENT.md).

## Visual requirements

- Graphite tonal style: dark panels, a darker workspace, a neutral light accent,
  compact controls, and small corner radii. Segoe UI on Windows, with system SansSerif as fallback.
  Interface labels are in English.
- Use PcTheme roles for colors and geometry. Do not recolor user photos or the computed combo background with theme colors.
- Focus uses a tonal fill and bottom strip inside the outline, without an outer frame, glow, or gradient.
  Buttons and toggles show the indicator for keyboard input; text fields also show it during mouse editing.
- Danger uses ruby text; DangerFilled uses a burgundy fill with light text.
  Contrast targets: text ≥ 4.5:1, focus indicator ≥ 3:1. Errors include explanatory text.
- Hover/pressed/focus/busy states preserve the control's size and hit area.
  Action labels and groups wrap when space is limited; actions remain reachable with larger fonts.
- Builder: side inspector in wide windows, tools below the canvas in narrow windows.
  Frame/Photo selects a mode, Fit/Fill are commands, and Always fit is a separate checkbox.
- Combos: no outer card frame or repeated title; resolution and Edit/Delete appear below the preview.
  Previews preserve their full aspect ratios and share a common height within each row.

## Toolkit boundaries

Use custom components built on Compose UI/Foundation/Animation, without Material components,
third-party UI libraries, or icon packs. Icons are original, cached ImageVectors;
directional actions account for RTL. Do not copy system font files into the project.
The toolkit is independent of application models, DI, ViewModels, and navigation.
`ArchitectureRulesTest` checks the internal graph `tokens ← theme ← primitives ← components`,
with icons as a separate branch. UI implementation and verification guidance: [cmp-ui](../.agents/skills/cmp-ui/SKILL.md).
