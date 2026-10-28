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

Launch the gallery with Hot Reload using the command in DEVELOPMENT; the regular
gallery task does not connect it to Hot Reload. In a trusted PhotoCombo session,
use `photocombo-hot-reload`: check `status`, then read `get_semantic_tree`.
Open `Gallery component` (`gallery_component_selector`), find and select the named
component, then read the tree again before acting. Popups may produce multiple tree
roots; scroll the options if needed. Re-read the tree after each UI change and use
current node IDs. Select `All` to return to the overview.

Use the focused tree and supported semantic actions for behavior; take a screenshot
when visual appearance matters. Semantic clicks do not verify pointer behavior.
If the MCP server or a relevant action is unavailable, report the gap and use the
existing desktop tests or gallery rather than claiming an MCP check.

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
