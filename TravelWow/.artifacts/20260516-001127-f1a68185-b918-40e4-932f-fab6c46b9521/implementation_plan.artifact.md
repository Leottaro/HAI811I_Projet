# Fix Google Places API REQUEST_DENIED issue

The user is encountering a `REQUEST_DENIED` status from the Google Places API. This is typically caused by the "Places API" not being enabled in the Google Cloud Console for the provided API key, or billing not being enabled.

To help diagnose and mitigate this, I will:
1. Improve logging in `AddStepScreen.kt` to capture the `error_message` from Google API responses.
2. Add a fallback to the "New" Places API (v1) in case the Legacy API is restricted or disabled while the new one is enabled.
3. Enhance the `fetchPlaceDetails` function to be more resilient.

## Proposed Changes

### [AddStepScreen.kt](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/src/main/java/fr/cestnous/travelwow/AddStepScreen.kt)

#### Improved Logging and Resilience in `fetchPlaceDetails`

- Update `fetchPlaceDetails` to log the `error_message` field from the JSON response.
- Attempt to use `Text Search` or the `New Places API` if `Nearby Search` fails with `REQUEST_DENIED`.
- Add more descriptive logs for `Routes API` as well.

```kotlin
// In fetchPlaceDetails, within the catch/else blocks:
val status = json.optString("status")
val errorMsg = json.optString("error_message")
Log.w("PlacesAPI", "Nearby Search failed: status=$status, message=$errorMsg")
```

---

## Verification Plan

### Automated Tests
- Since this involves external API calls, I will verify the code changes by running the app and checking the Logcat for the improved error messages.
- Command: `adb logcat | grep -E "PlacesAPI|AddStepScreen"`

### Manual Verification
1. Open the "Add Step" screen.
2. Search for a location (e.g., "Parc Montcalm").
3. Observe the logs to see if the `error_message` is now visible.
4. If the fallback to the New API works, the place details should be fetched successfully even if the legacy one fails.
