# Resolved Build Gradle Errors

I have successfully resolved the build errors in the `TravelWow` project.

## Changes Implemented

### [libs.versions.toml](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/gradle/libs.versions.toml)
- Added missing library definitions for:
    - Room (`room-runtime`, `room-ktx`, `room-compiler`)
    - Coil, Cloudinary, Gson, Maps Compose, Play Services Maps.
    - Firebase BOM.
- Added KSP plugin version definition.

### Root [build.gradle.kts](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/build.gradle.kts)
- Declared the KSP plugin in the `plugins` block.
- Standardized plugin declarations using the `alias` syntax.

### App [build.gradle.kts](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/build.gradle.kts)
- Cleaned up duplicate and conflicting dependency declarations.
- Consolidated Firebase dependencies to use a single BOM (v34.12.0).
- Standardized all dependency declarations to use the version catalog (`libs.*`).

## Verification Summary
- **Gradle Sync**: Completed successfully.
- **Build**: Successfully executed `:app:assembleDebug` with no errors.
