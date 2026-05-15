# Implementation Plan - Post Deletion and UI Cleanup

This plan implements post deletion functionality for authors and continues the UI cleanup in the details bottom sheet.

## Proposed Changes

### UI Components

#### [DetailsBottomSheet.kt](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/src/main/java/fr/cestnous/travelwow/DetailsBottomSheet.kt)

- **Add Delete Confirmation Dialog**: Implement a `showDeleteDialog` state and an `AlertDialog` to confirm post deletion.
- **Add Delete Button**: Show a "Delete" (trash icon) button in the top right for authors.
- **Implement Deletion Logic**:
    - Delete the post document from `travelpath_posts`.
    - Delete sub-collections (steps, comments) if feasible, or focus on the main post document.
    - Dismiss the bottom sheet after successful deletion.
- **Cleanup**: Remove `showShareDialog` state and associated unused logic.

## Verification Plan

### Manual Verification
1. **Author View**:
   - Log in and open one of your own posts.
   - Verify that a trash icon is visible in the top right.
   - Click the trash icon, verify the confirmation dialog appears.
   - Confirm deletion and verify the bottom sheet closes and the post is gone from the gallery.
2. **Visitor View**:
   - Open a post authored by someone else.
   - Verify that the trash icon is NOT visible.
