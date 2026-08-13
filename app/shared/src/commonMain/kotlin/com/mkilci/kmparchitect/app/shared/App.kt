package com.mkilci.kmparchitect.app.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mkilci.kmparchitect.core.designsystem.AppTheme
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.presentation.article.navigation.ArticleRoute
import com.mkilci.kmparchitect.presentation.article.navigation.articleGraph
import com.mkilci.kmparchitect.presentation.bookmarks.navigation.BookmarksRoute
import com.mkilci.kmparchitect.presentation.bookmarks.navigation.bookmarksGraph
import com.mkilci.kmparchitect.presentation.feed.navigation.FeedRoute
import com.mkilci.kmparchitect.presentation.feed.navigation.feedGraph

/**
 * The application root, and the only place that knows all three features exist.
 *
 * `feed` and `bookmarks` both emit `OpenArticle`. Neither depends on the article feature; both
 * simply say what the user asked for. The decision that the request opens `ArticleRoute` is made
 * here — which is why an isolated sample can route the same effect somewhere else without either
 * feature changing.
 */
@Composable
fun App(
    navController: NavHostController = rememberNavController(),
) {
    AppTheme {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination?.hasRoute(FeedRoute::class) == true,
                        onClick = { navController.navigateToTab(FeedRoute) },
                        label = { Text("Feed") },
                        icon = { Text("F") },
                    )
                    NavigationBarItem(
                        selected = currentDestination?.hasRoute(BookmarksRoute::class) == true,
                        onClick = { navController.navigateToTab(BookmarksRoute) },
                        label = { Text("Saved") },
                        icon = { Text("S") },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = FeedRoute,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                feedGraph(onOpenArticle = navController::openArticle)
                bookmarksGraph(onOpenArticle = navController::openArticle)
                articleGraph(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

/** The cross-feature route decision, in the one module entitled to make it. */
private fun NavHostController.openArticle(id: ArticleId) {
    navigate(ArticleRoute(id.value))
}

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
