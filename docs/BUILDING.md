# Building and testing Aftermath

## Requirements

- JDK 21 (not only a JRE)
- Network access for Gradle and NeoForge artifacts
- Minecraft/NeoForge are downloaded by the Gradle development plugin

## Linux / macOS

The repository includes a lightweight wrapper bootstrap script. If `gradle/wrapper/gradle-wrapper.jar` is absent, it downloads the wrapper JAR from the official NeoForge MDK, then runs the distribution configured in `gradle/wrapper/gradle-wrapper.properties`.

```bash
bash ./gradlew build
```

## Windows (PowerShell)

```powershell
.\gradlew.bat build
```

The first run downloads the wrapper JAR if absent and then downloads the Gradle distribution. Keep network access enabled during the first build.

## Development runs

```bash
bash ./gradlew runClient
bash ./gradlew runServer
```

For Windows, replace `bash ./gradlew` with `.\gradlew.bat`.

## What is verified today?

The repository currently has the project scaffold, server config, a diagnostic command, example data files and architecture/compatibility documentation. The GitHub Actions build is being wired up; do not treat the gameplay MVP as implemented until CI compilation and dedicated-server/in-game checks pass.
