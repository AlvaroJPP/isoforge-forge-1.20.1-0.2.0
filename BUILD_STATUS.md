# Build status — Isoforge 0.2.0

- Target: Minecraft 1.20.1, Forge 47.3.0, Java 17.
- Package: source project; no rebuilt JAR is included.
- Static checks completed: resource JSON parsing and Java delimiter balance.
- Compilation was not completed in the packaging environment because Gradle Wrapper could not download Gradle 8.8 from `services.gradle.org` (DNS/network unavailable).

## Build on Windows

Open PowerShell in this directory and run:

```powershell
.\gradlew.bat clean build
```

The output, if the build succeeds, is `build\libs\isoforge-forge-1.20.1-0.2.0.jar`. Use Java 17 and allow Gradle to download its distribution and Forge dependencies.
