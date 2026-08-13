package com.mkilci.kmparchitect.presentation.article.navigation

import com.mkilci.kmparchitect.core.mvi.ScreenEffect
import kotlinx.serialization.Serializable

sealed interface ArticleEffect : ScreenEffect {
    data object NavigateBack : ArticleEffect
}

/**
 * A stable identifier travels through the route; the screen reloads the article through a use case.
 * Passing the model itself would put a serialised copy of domain state in the back stack, where it
 * would go stale the moment storage changed.
 */
@Serializable
data class ArticleRoute(val articleId: String)
