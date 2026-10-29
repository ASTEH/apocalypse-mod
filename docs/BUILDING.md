# Building Aftermath

## Requirements

- Minecraft target: 1.21.1
- Java: 21
- Internet connection on first wrapper launch, unless Gradle and the wrapper are already cached

## Windows PowerShell

From the repository root:

    .\gradlew.bat clean build

If the repository was downloaded without the wrapper JAR, gradlew.bat attempts to download the official Gradle Wrapper JAR automatically.

## Linux/macOS

    bash ./gradlew clean build

## Output

The mod jar is written to:

    build/libs/aftermath-0.1.0.jar

Use the newly generated jar in a disposable test instance first. A successful Gradle build verifies compilation and packaging, not all runtime behaviour; check logs/latest.log and perform the acceptance tests in docs/MVP.md.
