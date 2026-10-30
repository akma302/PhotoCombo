# PhotoCombo development

## Running and verifying

PowerShell commands from the repository root:

```powershell
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:runDesignSystemGallery
.\gradlew.bat check --offline --console=plain
.\gradlew.bat :feature:builder:desktopTest --tests '*DraftHistoryTest' --offline --console=plain
.\gradlew.bat verifyModuleGraph --offline --console=plain
```

`--offline` requires a populated cache; the first build may need network access.
Launcher/wrapper/daemon configuration is in `gradle/`; dependencies are in the version catalog.
For environment issues, inspect `.\gradlew.bat --version` and `gradle/gradle-daemon-jvm.properties`.

`run` opens real user data in `%APPDATA%\PhotoCombo`; the component gallery does not use the database.
The gallery's Component selector switches between the overview and focused interactive examples;
shared GalleryGrid uses synthetic data. For Hot Reload with the JetBrains Runtime bundled with
Android Studio on Windows (adjust the path if needed):

```powershell
.\gradlew.bat '-Dorg.gradle.java.installations.paths=C:\Program Files\Android\Android Studio\jbr' :composeApp:hotRunDesktop --mainClass=com.combo.ui.GalleryLauncherKt --no-auto
```

The project-scoped MCP server can use `:composeApp:hotMcpServerDesktop --offline --quiet --console=plain`
from the project directory; Codex must trust the project configuration to load it. See the
[Codex MCP documentation](https://developers.openai.com/codex/mcp).

Tests use personal photos only when the owner explicitly selects them:
`RealPhotoImportTest` is skipped without `-Prealphotos=<directory>`.

## Verification and reports

- Logic: run the owning module's `desktopTest`; apply `--tests` to a specific module task.
  Root `check` runs all modules' tests and graph verification; `:composeApp:desktopTest` covers integration only.
- Run the full `check` before publishing code. A successful run needs no repetition without changes
  or a new reason. Documentation changes require link, path, command, and `git diff --check` validation.
- Combined report: `build/reports/tests/desktop/index.html`; XML: `<module>/build/test-results/desktopTest/`.
  CMP screenshots: `<module>/build/ui-previews/`. Fixtures and fakes are in test-support.
- Choose UI checks according to the change's risks: [cmp-ui](../.agents/skills/cmp-ui/SKILL.md).
  Builds, automated interactions, PNG inspection, and manual window interaction provide different evidence.

## Git

- Check the working tree and task base. Branch names: `feature_<id>_<name>` or `bug_<id>_<name>`;
  find an unused ID among existing branches.
- Independent tasks use separate PRs into main. A shared branch for dependent parts requires owner agreement.
  Delegation requires authorization; when agreed, use separate worktrees.
- Commit verified work, push the branch, and open a PR when GitHub is available.
  The PR description covers resulting behavior, verification, and limitations.
- After merge confirmation, switch to a clean main and run `git pull --ff-only`.
  Do not automatically delete local branches.

## Experimental JBR title bar

The isolated preview does not open the personal library:

```powershell
.\gradlew.bat :composeApp:runTitleBarPreview '-PtitlebarJava=C:\Program Files\Android\Android Studio\jbr\bin\java.exe' --offline --console=plain
```

This is an experiment, not enabled in the main application. It uses the public JetBrains
Runtime API with a normal decorated window as fallback. On the tested JBR 25.0.4 b508.27,
the service reported successful installation, but the preview did not show the intended
Compose content in the inspected window. Native dragging, resizing, window menu, DPI, and
button hit testing therefore remain unverified. The public forceHitTest API distinguishes
client and title bar areas; it does not expose a maximize-button hit-test role, so Windows
Snap Layouts for the custom maximize button is not established.
