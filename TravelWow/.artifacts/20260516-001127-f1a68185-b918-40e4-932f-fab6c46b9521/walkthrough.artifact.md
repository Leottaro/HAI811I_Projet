# Walkthrough - Google Places API REQUEST_DENIED Fix

I have improved the resilience and diagnostics of the Google Places API integration in `AddStepScreen.kt`.

## Changes

### [AddStepScreen.kt](file:///home/leo/Cours/S8/HAI811I_mobile/HAI811I_Projet/TravelWow/app/src/main/java/fr/cestnous/travelwow/AddStepScreen.kt)

#### 1. Improved Logging
I updated the API call handlers to log the `status` and `error_message` fields returned by Google. This will provide immediate clarity in Logcat if the request is denied due to billing, API enablement, or restrictions.

#### 2. Fallback to New Places API (v1)
I implemented a fallback mechanism. If the Legacy "Nearby Search" returns `REQUEST_DENIED`, the app now automatically attempts a "Text Search" using the New Places API (v1). This is helpful because some API keys are restricted to newer API versions.

```kotlin
// Fallback logic in fetchPlaceDetails
if (status == "REQUEST_DENIED") {
    Log.i("PlacesAPI", "Nearby Search denied. Attempting fallback to New API (v1)...")
    val fallbackInfo = fetchPlaceDetailsNewApi(query, lat, lng)
    if (fallbackInfo != null) return@withContext fallbackInfo
}
```

#### 3. Routes API Logging
Added logging for the Routes API to capture response bodies and error streams when a route cannot be calculated.

## Verification Results

### Manual Verification Steps
1. Run the app and navigate to the **Add Step** screen.
2. Search for a location or click on the map.
3. Check the **Logcat** in Android Studio (filter by `PlacesAPI` or `AddStepScreen`).

**Expected logs if Legacy API is denied but New API works:**
```text
W PlacesAPI: Nearby Search 0 results for 'Parc Montcalm' (status=REQUEST_DENIED, message=This IP, site or mobile application is not authorized to use this API key. ...)
I PlacesAPI: Nearby Search denied. Attempting fallback to New API (v1)...
D PlacesAPI: New API (v1) Success: Parc Montcalm
D AddStepScreen: infos: 'PlaceInfo(name=Parc Montcalm, ...)'
```

**If both fail:**
The logs will show the `error_message` for both, allowing you to identify if it's a billing issue (`Billing not enabled`) or an API enablement issue (`Places API has not been used in project ...`).
