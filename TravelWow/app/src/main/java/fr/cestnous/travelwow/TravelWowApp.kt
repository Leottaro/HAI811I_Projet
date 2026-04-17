package fr.cestnous.travelwow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelWowApp(onLogout: () -> Unit) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<Int?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                when (currentDestination) {
                    AppDestinations.HOME -> SearchTopBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it }
                    )
                    else -> TopAppBar(
                        title = { Text("TravelWow") },
                        actions = {
                            IconButton(onClick = onLogout) {
                                Icon(
                                    painterResource(R.drawable.ic_account_box),
                                    contentDescription = "Logout"
                                )
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> SearchScreen(
                    onItemClick = { index ->
                        selectedItem = index
                        showBottomSheet = true
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.FAVORITES -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    Text("Favoris", modifier = Modifier.align(Alignment.Center))
                }
                AppDestinations.PROFILE -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    Text("Profil", modifier = Modifier.align(Alignment.Center))
                }
            }

            if (showBottomSheet) {
                DetailsBottomSheet(
                    selectedItem = selectedItem,
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Bienvenue, $name!",
        modifier = modifier.padding(16.dp)
    )
}
