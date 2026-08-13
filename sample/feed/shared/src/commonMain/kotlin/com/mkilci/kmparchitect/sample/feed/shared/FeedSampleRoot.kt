package com.mkilci.kmparchitect.sample.feed.shared

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
import com.mkilci.kmparchitect.presentation.feed.navigation.FeedRoute
import com.mkilci.kmparchitect.presentation.feed.navigation.feedGraph
import kotlinx.serialization.Serializable

/**
 * Isolated development environment for the feed feature.
 *
 * `FeedEffect.OpenArticle` normally opens the article feature, which is intentionally outside this
 * graph. The sample neither swallows the effect nor imports the article feature to satisfy it:
 * it routes to a typed, sample-local placeholder that names the request and supports back
 * navigation. Swallowing it would hide a broken flow; importing `article` would quietly destroy the
 * isolation this sample exists to provide.
 */
@Serializable
internal data class SampleOpenArticleRoute(val articleId: String)

@Composable
fun FeedSampleRoot(
    navController: NavHostController = rememberNavController(),
) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = FeedRoute) {
                feedGraph(
                    onOpenArticle = { id: ArticleId ->
                        navController.navigate(SampleOpenArticleRoute(id.value))
                    },
                )
                composable<SampleOpenArticleRoute> { entry ->
                    val route: SampleOpenArticleRoute = entry.toRoute()
                    MessageSurface(
                        title = "Outside this sample",
                        body = "Production opens the article feature for \"${route.articleId}\". " +
                            "That feature is not part of the feed sample's dependency graph.",
                        actionLabel = "Back",
                        onAction = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
