# Caching Liked Posts with Room

Implement local caching for liked posts to provide an immediate and offline-capable experience in the "Favorites" tab.

## Proposed Changes

### Dependencies

#### [libs.versions.toml](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/gradle/libs.versions.toml)
- Added Room version `2.8.4` and KSP version `2.2.10-2.0.2`.
- Added Room libraries (`runtime`, `ktx`, `compiler`) and KSP plugin.

#### [app/build.gradle.kts](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/build.gradle.kts)
- Applied KSP plugin.
- Added Room dependencies.

---

### Database Layer

#### [NEW] [FavoritePost.kt](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/src/main/java/fr/cestnous/travelwow/FavoritePost.kt)
- Defined `FavoritePost` Room Entity.
- Defined `FavoritePostDao` with methods to `getAll`, `insert`, `deleteByPostId`, and `clearAll`.
- Defined `TravelWowDatabase` with `TypeConverters` for `List<String>`.

---

### UI & Logic Integration

#### [DetailsBottomSheet.kt](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/src/main/java/fr/cestnous/travelwow/DetailsBottomSheet.kt)
- Updated favorite status check to use local cache first for immediate UI update.
- Updated like/unlike logic to synchronize local Room cache with Firestore.

#### [PostsGallery.kt](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/src/main/java/fr/cestnous/travelwow/PostsGallery.kt)
- Modified `fetchPosts` to load from local cache first when viewing favorites.
- Implemented background synchronization to update the cache from Firestore (adding new favorites and removing unliked ones).

#### [TravelWowApp.kt](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/src/main/java/fr/cestnous/travelwow/TravelWowApp.kt)
- Added logic to clear the local Room cache when the user logs out.

## Verification Plan

### Manual Verification
1. **Liking a Post**: Open a post, click the heart icon. Close the app and reopen it. Check if the heart is still filled immediately.
2. **Favorites Tab**: Go to the "Favoris" tab. It should load posts almost instantly from the cache.
3. **Offline Mode**: Put the device in Airplane mode. Open the app and go to "Favoris". The previously liked posts should still be visible.
4. **Logout**: Log out of the app. Log in with a different account. The "Favoris" tab should be empty (or show the other user's favorites from Firestore, not the previous user's cache).
