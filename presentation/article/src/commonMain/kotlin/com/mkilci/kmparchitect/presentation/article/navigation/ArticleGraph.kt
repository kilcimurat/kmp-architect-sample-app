package com.mkilci.kmparchitect.presentation.article.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mkilci.kmparchitect.core.navigation.HandleEffects
import com.mkilci.kmparchitect.presentation.article.view.ArticleScreen
import com.mkilci.kmparchitect.presentation.article.viewmodel.ArticleViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.articleGraph(
    onNavigateBack: () -> Unit,
) {
    composable<ArticleRoute> { entry ->
        val route: ArticleRoute = entry.toRoute()
        ArticleRouteScreen(articleId = route.articleId, onNavigateBack = onNavigateBack)
    }
}

@Composable
private fun ArticleRouteScreen(
    articleId: String,
    onNavigateBack: () -> Unit,
) {
    // The route's identifier is passed to the ViewModel as a parameter. The ViewModel receives a
    // String-shaped id, never the NavBackStackEntry or the controller.
    val viewModel: ArticleViewModel = koinViewModel { parametersOf(articleId) }

    HandleEffects(viewModel.effects) { effect ->
        when (effect) {
            ArticleEffect.NavigateBack -> onNavigateBack()
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    ArticleScreen(state = state, onAction = viewModel::onAction)
}
