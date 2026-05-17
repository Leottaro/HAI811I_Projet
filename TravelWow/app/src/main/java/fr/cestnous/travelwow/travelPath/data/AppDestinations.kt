package fr.cestnous.travelwow.travelPath.data

import fr.cestnous.travelwow.R

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Accueil", R.drawable.ic_home),
    FAVORITES("Favoris", R.drawable.ic_favorite),
    PROFILE("Profil", R.drawable.ic_account_box),
}
