package com.mkilci.kmparchitect.presentation.bookmarks.navigation

import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.mvi.ScreenEffect
import kotlinx.serialization.Serializable

/**
 * `OpenArticle` is the interesting one: its production destination belongs to the article feature,
 * which this feature must not depend on. The effect names the request and stops there; who handles
 * it is the graph owner's decision.
 */
sealed interface BookmarksEffect : ScreenEffect {
    data class OpenArticle(val id: ArticleId) : BookmarksEffect
}

@Serializable
data object BookmarksRoute
