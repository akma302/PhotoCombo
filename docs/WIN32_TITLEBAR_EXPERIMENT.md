# Win32 title-bar experiment

This branch contains an isolated experiment, not an integration into the application window.
It opens synthetic content and does not initialize PhotoCombo's database or library. Run on
Windows from the repository root:

```powershell
.\gradlew.bat '-Dorg.gradle.java.installations.paths=C:\Program Files\Android\Android Studio\jbr' :composeApp:runWin32TitleBarExperiment --offline --console=plain
```

The experiment uses JNA to subclass the demonstration HWND and gives Windows non-client
hit-test results for the title, resize edges and caption buttons. The maximize area returns
`HTMAXBUTTON`, which is the Windows requirement for Snap Layouts. Other messages are passed
to the original AWT window procedure. This is not a dependency on a JetBrains window API.

Known limitations: native pointer handling bypasses the Compose buttons' hover and pressed
states; accessibility, keyboard focus and native caption behavior have not been verified as
an integrated system. Mixed-DPI monitor moves, multiple displays, taskbar configurations,
window lifecycle races, and automatic fallback after a native failure have not been validated.
The fixed hit regions assume the title-bar dimensions and density match the AWT scale. The
prototype is not safe to ship as production window chrome without resolving those issues.
A returned `HTMAXBUTTON` alone does not establish that the Snap Layout flyout works.
