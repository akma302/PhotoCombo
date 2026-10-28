# PhotoCombo

A local photo library and nondestructive collage editor for Windows,
built with Kotlin Multiplatform and Compose Multiplatform. Photos are imported into the library;
a combo stores a configuration of frames and source photos that can be reopened and edited.

```powershell
.\gradlew.bat :composeApp:run
```

- [Project map](AGENTS.md): where to find features, data, rendering, UI, and tests.
- [Development](docs/DEVELOPMENT.md): environment, verification commands, and Git conventions.
- [Design system](docs/DESIGN_SYSTEM.md): visual requirements and component entry points.

Tool and dependency versions are defined by Gradle configuration and `gradle/libs.versions.toml`.
