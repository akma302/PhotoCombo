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

## Dependent PR stacks (explicit owner instruction only)

Use this workflow only when the owner explicitly asks for a long autonomous task with
multiple dependent branches and chained PRs. For ordinary work, follow the [Git](#git)
section above.

### Create the stack

Open the first PR from branch A into `main`, the second from B into A, the third
from C into B, and so on. Start each branch from the current published tip of its
intended base so the next PR shows the changes for its own task. Run the full
`check` before publishing each code task. Confirm and report the head branch and base
of each open PR.

PowerShell example for starting A and B. Replace the branch names with the actual
names. Run each command only after the preceding step succeeds.

```powershell
$baseBranch = 'feature_0100_base'
$headBranch = 'feature_0101_followUp'
git status --short --branch
git fetch origin
git switch --no-track -c $baseBranch origin/main
# Implement, verify, and commit A.
git push -u origin $baseBranch

git switch --no-track -c $headBranch "origin/$baseBranch"
# Implement, verify, and commit B.
git push -u origin $headBranch
```

On GitHub, open A's PR into `main` and B's PR into A.

### Sync an open PR when its existing base advances

If A gains a fix or a documentation change that B or later branches need, bring the
updated A into B. Keep A as the PR base while A remains the intended merge destination.
`git fetch origin` refreshes the local `origin/*` references from GitHub. Your current
local branch matters for the merge, which applies to the branch checked out at that step.

```powershell
git fetch origin
git switch $headBranch
git merge --ff-only "origin/$headBranch"
git merge "origin/$baseBranch"
# Resolve any merge conflicts and run the full check for code changes before pushing.
git push origin $headBranch
```

Repeat in dependency order if C and later branches need the update. The push updates
the head of the existing PR. Each PR merge advances its own base branch only, so a later
merge into A does not propagate through a PR that already merged A elsewhere.

### Change the destination after a lower PR merges

After A lands in `main`, check the destination of the next open PR before merging it.
If B should now land in `main`, change the base of PR B to `main`. Use the same procedure
for any other new intended destination. On GitHub, edit the PR title area and select
the new base. GitHub can retarget an open PR in the same repository when its former
base branch is deleted after merging. Always verify the resulting base, changed files,
and checks before the next merge. [Changing a PR's base](https://docs.github.com/en/pull-requests/how-tos/create-pull-requests/changing-the-base-branch-of-a-pull-request) · [Branch deletion](https://docs.github.com/en/pull-requests/how-tos/commit-changes/managing-branches-within-your-repository#deleting-a-branch)

### Rebase a dependent branch onto `main`

GitHub's **Rebase and merge** applies the PR commits individually on top of the target
branch and creates new SHAs. After a rebase or squash of A into `main`, B may still
descend from the original A commits. To move a linear B onto `main`, record the old
base boundary before landing A, then replay only B's commits. Do the rebase on B and
update local `main` with a fast-forward pull. [GitHub merge methods](https://docs.github.com/en/pull-requests/reference/pull-request-merges#rebase-and-merge-your-commits) · [Git rebase](https://git-scm.com/docs/git-rebase)

```powershell
# Before A lands, record the last base commit inherited by B.
git fetch origin
$oldBase = git merge-base "origin/$baseBranch" "origin/$headBranch"

# After A lands, start from the published B tip.
git fetch origin
git switch $headBranch
git merge --ff-only "origin/$headBranch"
$publishedHead = git rev-parse "origin/$headBranch"
git log --oneline "$oldBase..HEAD"
git rebase --onto origin/main $oldBase
git range-diff "$oldBase..$publishedHead" 'origin/main..HEAD'
# Verify the diff and run the full check for code changes before pushing.
git push "--force-with-lease=refs/heads/${headBranch}:$publishedHead" origin $headBranch
```

Inspect the commits selected by `git log` before rebasing. If B contains merge commits
with conflict resolutions, review how those resolutions will be preserved before using
the linear recipe. During a conflict, use `git rebase --continue` after resolving it or
`git rebase --abort` to return to the original branch. If the lease rejects the push,
fetch and inspect the new remote commits before retrying. Confirm the PR base and repeat
the update for any dependent branches that still point to the old history.

### Verify delivery to `main`

After merge confirmation, set `$landedCommit` to the resulting commit SHA from the
PR into `main`. After a rebase or squash, verify the delivered diff as well, since
the original PR SHA may be absent.

```powershell
git switch main
git pull --ff-only origin main
git merge-base --is-ancestor $landedCommit HEAD
git status --short --branch
```

An exit code of 0 from `merge-base` confirms that `main` contains that commit.

If the child PRs were already merged into intermediate branches, inspect those branch
tips for the complete change set, including late commits that did not reach the top
branch. Create a new integration branch from `origin/main`, merge the tips that contain
the missing changes, and verify the combined diff and full `check` before opening an
integration PR directly into `main`.
