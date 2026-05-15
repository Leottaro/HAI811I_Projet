# Implementation Plan - Dynamic Post Fetching and Sorting

Modify `PostsGallery.kt` to implement different fetching and sorting strategies based on the view mode (Map vs Grid) and the screen (Search, Liked, Profile).

## Proposed Changes

### [PostsGallery component]

#### [PostsGallery.kt](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/src/main/java/fr/cestnous/travelwow/PostsGallery.kt)

- Define a constant `GRID_POSTS_LIMIT = 50`.
- Update `fetchPosts` logic:
    - **Map Mode**: Fetch all relevant posts (up to 1000) without applying the $N$ limit.
    - **Grid Mode**: Apply a limit of `GRID_POSTS_LIMIT` posts.
    - **Sorting strategies by screen**:
        - **Main/Search Screen** (no `userIdFilter` and not Favorites): Sort randomly using `shuffled()`.
        - **Liked Screen** (`favoritesUserId != null`): Sort by like date (using the order in `likedPostIds`, reversed).
        - **Profile Screen** (`userIdFilter != null`): Sort by creation date (`createdAt` descending).
- Optimization: For Grid mode on Main screen, fetch a larger pool (e.g., 200) before shuffling and filtering to ensure we have enough variety and results after filtering.

```kotlin
// Summary of logic in fetchPosts:
val isMapMode = viewMode == GalleryViewMode.MAP
val gridLimit = 50
val fetchLimit = if (isMapMode) 1000 else 200 // Pool size

if (favoritesUserId != null) {
    // 1. Get likedPostIds from FirebaseUser
    // 2. Reverse to get most recent first
    // 3. If !isMapMode, take(gridLimit)
    // 4. Fetch posts by ID
    // 5. Sort results by original likedPostIds order
} else {
    // 1. Build query based on userIdFilter or excludeUserId
    // 2. Set limit(fetchLimit)
    // 3. Apply memory filters (categories, distance, searchQuery)
    // 4. If !isMapMode:
    //    - If userIdFilter != null: sort by createdAt DESC, take(gridLimit)
    //    - Else: shuffle(), take(gridLimit)
    // 5. If isMapMode: sort by createdAt DESC (for consistency)
}
```

## Verification Plan

### Automated Tests
- No specific automated tests available for UI/Firebase logic, will rely on manual verification.

### Manual Verification
1. **Main Screen (Grid)**:
   - Open Search screen in Grid mode.
   - Verify that posts appear in a different order or different set of posts appear on refresh (due to random sort).
   - Verify that the number of posts is limited to 50.
2. **Main Screen (Map)**:
   - Switch to Map mode.
   - Verify that all available posts are displayed on the map (should be more than 50 if database is large).
3. **Liked Screen (Grid)**:
   - Go to Favorites screen.
   - Verify that posts are sorted by the order they were liked (most recent first).
   - Verify the 50 post limit.
4. **Profile Screen (Grid)**:
   - Go to Profile screen.
   - Verify that posts are sorted by creation date (newest first).
   - Verify the 50 post limit.
5. **Filters & Search**:
   - Apply filters and search queries in both modes.
   - Verify that the limits and sorting still work as expected on the filtered results.
