# Resolve Build Gradle Errors

The project has several issues in its build configuration:
1. `libs.versions.toml` is missing library definitions that are used in `app/build.gradle.kts`.
2. `app/build.gradle.kts` has duplicate and conflicting dependencies (multiple Firebase BOMs, duplicate libraries).
3. The root `build.gradle.kts` is missing the KSP plugin declaration.

## Proposed Changes

### [gradle/libs.versions.toml](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/gradle/libs.versions.toml)

- Add missing library definitions:
    - `androidx-lifecycle-viewmodel-compose`
    - `androidx-compose-foundation`
    - `coil-compose`
    - `cloudinary-android`
    - `maps-compose`
    - `play-services-maps`
    - `gson`
    - `androidx-room-runtime`
    - `androidx-room-ktx`
    - `androidx-room-compiler`
- Add `firebase-bom` version and library definition.

### [build.gradle.kts](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/build.gradle.kts) (Root)

- Add `alias(libs.plugins.ksp) apply false` to the `plugins` block.
- Standardize plugin declarations using `alias` where possible.

### [app/build.gradle.kts](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/build.gradle.kts)

- Remove duplicate dependency declarations.
- Consolidate Firebase dependencies to use a single BOM (34.12.0).
- Remove redundant `-ktx` Firebase dependencies (the BOM managed ones without `-ktx` are sufficient and modern).
- Ensure all library references use `libs.*`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the project builds successfully.
- Run `./gradlew help` or `./gradlew tasks` to ensure Gradle sync/configuration is successful.

### Manual Verification
- Check the "Build" and "Sync" tabs in Android Studio (if I were the user) to ensure no errors are reported.
- Since I am an agent, I will rely on `gradle_build` and `gradle_sync`.
