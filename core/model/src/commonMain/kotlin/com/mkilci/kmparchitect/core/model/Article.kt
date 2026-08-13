package com.mkilci.kmparchitect.core.model

import kotlin.jvm.JvmInline

/**
 * Genuinely cross-feature value objects.
 *
 * `feed`, `article` and `bookmarks` all speak about the same entity. Putting it here is what lets
 * those three features stay independent: without it, `bookmarks` would have to import `feed` to
 * name what it has bookmarked, and the isolation claim would be gone at the first cross-feature
 * requirement.
 */
@JvmInline
value class ArticleId(val value: String) {
    init {
        require(value.isNotBlank()) { "ArticleId must not be blank" }
    }
}

data class Article(
    val id: ArticleId,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAtEpochMillis: Long,
)
