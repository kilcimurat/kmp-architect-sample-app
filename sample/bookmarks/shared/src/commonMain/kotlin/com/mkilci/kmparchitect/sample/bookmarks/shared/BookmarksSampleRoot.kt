package com.mkilci.kmparchitect.sample.bookmarks.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mkilci.kmparchitect.core.designsystem.AppTheme
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.ui.MessageSurface
import com.mkilci.kmparchitect.presentation.bookmarks.navigation.BookmarksRoute
import com.mkilci.kmparchitect.presentation.bookmarks.navigation.bookmarksGraph
import kotlinx.serialization.Serializable

/**
 * The clearest case for the placeholder rule.
 *
 * In production, tapping a bookmark opens the article feature. This sample must not depend on that
 * feature — doubling its graph to satisfy one navigation call is exactly the regression the whole
 * topology guards against. So the effect is consumed by a typed, sample-local destination that
 * names the article that *would* have opened, and supports back.
 *
 * The alternative — an empty `is OpenArticle -> Unit` branch — would compile, pass every test, and
 * quietly turn a broken flow into a screen that does nothing when tapped.
 */
@Serializable
internal data class SampleOpenArticleRoute(val articleId: String)

@Composable
fun BookmarksSampleRoot(
    navController: NavHostController = rememberNavController(),
) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = BookmarksRoute) {
                bookmarksGraph(
                    onOpenArticle = { id: ArticleId ->
                        navController.navigate(SampleOpenArticleRoute(id.value))
                    },
                )
                composable<SampleOpenArticleRoute> { entry ->
                    val route: SampleOpenArticleRoute = entry.toRoute()
                    MessageSurface(
                        title = "Outside this sample",
                        body = "Production opens the article feature for \"${route.articleId}\". " +
                            "That feature is not part of the bookmarks sample's dependency graph.",
                        actionLabel = "Back",
                        onAction = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
