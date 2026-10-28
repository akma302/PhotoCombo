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
