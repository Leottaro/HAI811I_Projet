package fr.cestnous.travelwow.travelPath.data

import androidx.compose.runtime.Immutable
import java.util.*

@Immutable
data class TravelStep(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "",
    val latitude: Double,
    val longitude: Double,
    val images: List<String> = emptyList()
)
