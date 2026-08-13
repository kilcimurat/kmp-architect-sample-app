package com.mkilci.kmparchitect.presentation.feed.navigation

import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.mvi.ScreenEffect
import kotlinx.serialization.Serializable

/** This feature's own effect hierarchy. It is never merged with another feature's. */
sealed interface FeedEffect : ScreenEffect {
    data class OpenArticle(val id: ArticleId) : FeedEffect
}

@Serializable
data object FeedRoute
