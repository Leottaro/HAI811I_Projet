package fr.cestnous.travelwow.travelShare.ui.map

import android.graphics.Color
import android.graphics.PorterDuff
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.cestnous.travelwow.travelShare.data.model.TravelPhoto
import fr.cestnous.travelwow.travelShare.ui.feed.FeedViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(
    onBack: () -> Unit,
    onPhotoClick: (TravelPhoto) -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
    val photos by viewModel.photos.collectAsState()
    val context = LocalContext.current

    // Important for OSMDroid: ensure configuration is loaded before use
    LaunchedEffect(Unit) {
        org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(46.2276, 2.2137))
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                photos.forEach { photo ->
                    if (photo.latitude != null && photo.longitude != null) {
                        val marker = Marker(mapView)
                        marker.position = GeoPoint(photo.latitude, photo.longitude)
                        marker.title = photo.locationName
                        
                        // Style du Pin : Rouge pur
                        val icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default)?.mutate()
                        icon?.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
                        marker.icon = icon
                        
                        // Désactiver l'infobulle (la "main" / le texte au clic)
                        marker.infoWindow = null 
                        
                        marker.setOnMarkerClickListener { _, _ ->
                            onPhotoClick(photo)
                            true // Consomme l'événement pour éviter l'affichage de l'infobulle
                        }
                        mapView.overlays.add(marker)
                    }
                }
                mapView.invalidate()
            }
        )
    }
}
