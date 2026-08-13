package com.mkilci.kmparchitect.sample.article.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mkilci.kmparchitect.core.designsystem.AppTheme
import com.mkilci.kmparchitect.core.ui.MessageSurface
import com.mkilci.kmparchitect.fixtures.article.ArticleFixtures
import com.mkilci.kmparchitect.presentation.article.navigation.ArticleRoute
import com.mkilci.kmparchitect.presentation.article.navigation.articleGraph
import kotlinx.serialization.Serializable

/**
 * `ArticleEffect.NavigateBack` normally pops back to whichever feature opened the article. Here the
 * article *is* the start destination, so there is nothing to pop to.
 *
 * The sample still consumes the effect rather than ignoring it: it routes to a typed placeholder
 * that says where production would have gone. An empty handler would make a broken back button look
 * like correct behaviour.
 */
@Serializable
internal data object SampleReturnedRoute

@Composable
fun ArticleSampleRoot(
    navController: NavHostController = rememberNavController(),
) {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ArticleRoute(ArticleFixtures.known.id.value),
            ) {
                articleGraph(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(SampleReturnedRoute)
                        }
                    },
                )
                composable<SampleReturnedRoute> {
                    MessageSurface(
                        title = "Outside this sample",
                        body = "Production returns to whichever feature opened this article. " +
                            "Those features are not part of the article sample's dependency graph.",
                        actionLabel = "Open the article again",
                        onAction = {
                            navController.navigate(ArticleRoute(ArticleFixtures.known.id.value)) {
                                popUpTo(SampleReturnedRoute) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}
