package com.mkilci.kmparchitect.presentation.bookmarks.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.navigation.HandleEffects
import com.mkilci.kmparchitect.presentation.bookmarks.view.BookmarksScreen
import com.mkilci.kmparchitect.presentation.bookmarks.viewmodel.BookmarksViewModel
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.bookmarksGraph(
    onOpenArticle: (ArticleId) -> Unit,
) {
    composable<BookmarksRoute> {
        BookmarksRouteScreen(onOpenArticle = onOpenArticle)
    }
}

@Composable
private fun BookmarksRouteScreen(
    onOpenArticle: (ArticleId) -> Unit,
    viewModel: BookmarksViewModel = koinViewModel(),
) {
    HandleEffects(viewModel.effects) { effect ->
        when (effect) {
            is BookmarksEffect.OpenArticle -> onOpenArticle(effect.id)
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    BookmarksScreen(state = state, onAction = viewModel::onAction)
}
