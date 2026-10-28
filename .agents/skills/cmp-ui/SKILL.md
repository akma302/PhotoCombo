---
name: cmp-ui
description: "Modify and verify PhotoCombo's Compose UI: shared components, input, adaptive layout, and rendering. Use for UI tasks, not ordinary data or documentation changes."
---

# PhotoCombo CMP UI

## Entry points by task

- Existing controls, tokens, and visual constraints: [DESIGN_SYSTEM](../../../docs/DESIGN_SYSTEM.md).
- Screen integration: the relevant `feature/` module, its Screen/ViewModel, and desktopTest.
- Shared grid, thumbnails, and input hosts: `ui-common/src/`.
- Test host and fakes: `test-support/src/desktopMain/`; commands and reports: [DEVELOPMENT](../../../docs/DEVELOPMENT.md).

## Focused gallery through Hot Reload MCP

Launch the Hot Reload gallery using [DEVELOPMENT](../../../docs/DEVELOPMENT.md).
Select a component via `Gallery component` (`gallery_component_selector`); `All`
restores the overview. Use `photocombo-hot-reload` and `get_semantic_tree` to inspect
and act; refresh node IDs after UI changes. Popups may expose multiple tree roots.
If MCP or an action is unavailable, use desktop tests or the gallery and report the gap.

## Implementation considerations

A component's public contract includes caller-owned state and callbacks, a root Modifier,
an accessible name, and keyboard input. Business state belongs to the feature.
Preserve gesture completion and cancellation in editor pointerInput; check that closures
read current values after recomposition. Cancellation must not become a completed action.

## Checks by risk

- Input: actual pointer/key events, callback counts, cancellation, focus, and semantics;
  a semantics-click alone does not verify mouse behavior.
- Layout: bounds around the changed breakpoint, narrow widths, and larger fontScale;
  include density and RTL where they affect the change.
- Rendering: capture real CMP components with synthetic data and inspect representative PNGs;
  add an example of a new component to DesignSystemGallery.
- Popup/dialog: closing, focus containment and restoration, and resizing on desktop.

Choose scenarios relevant to the change. Do not update baselines or tolerances merely to
make tests pass without explaining the changed contract. Distinguish builds, automated
interactions, screenshot inspection, and manual interaction in reports; scene density does not verify Windows DPI changes.
