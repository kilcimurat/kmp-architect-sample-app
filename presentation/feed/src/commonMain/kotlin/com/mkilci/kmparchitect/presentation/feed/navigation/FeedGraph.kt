package com.mkilci.kmparchitect.presentation.feed.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.navigation.HandleEffects
import com.mkilci.kmparchitect.presentation.feed.view.FeedScreen
import com.mkilci.kmparchitect.presentation.feed.viewmodel.FeedViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The feature's route graph. It owns effect collection and handles [FeedEffect] exhaustively — no
 * casts, no `else` branch, so adding an effect breaks this `when` at compile time.
 *
 * [onOpenArticle] is the seam for a destination this feature does not own. Production wires it to
 * the article feature; the isolated sample wires it to a sample-local placeholder. Neither the
 * ViewModel nor this graph ever learns which.
 */
fun NavGraphBuilder.feedGraph(
    onOpenArticle: (ArticleId) -> Unit,
) {
    composable<FeedRoute> {
        FeedRoute(onOpenArticle = onOpenArticle)
    }
}

@Composable
private fun FeedRoute(
    onOpenArticle: (ArticleId) -> Unit,
    viewModel: FeedViewModel = koinViewModel(),
) {
    HandleEffects(viewModel.effects) { effect ->
        when (effect) {
            is FeedEffect.OpenArticle -> onOpenArticle(effect.id)
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    FeedScreen(state = state, onAction = viewModel::onAction)
}
