package com.mkilci.kmparchitect.data.articlestore

import kotlinx.coroutines.flow.Flow

/**
 * Storage-level record. Deliberately not a domain `Article`: mapping happens in the feature data
 * modules, so each feature decides what part of the row it cares about.
 */
data class ArticleRecord(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAtEpochMillis: Long,
    val bookmarked: Boolean,
)

/**
 * A focused infrastructure module shared by the article-related features.
 *
 * `feed`, `article` and `bookmarks` are separate features but they are views over one local store —
 * that is what offline-first means here. The sharing is at the storage layer, where a shared store
 * belongs; no feature imports another feature.
 *
 * It is an interface so feature data modules can be unit tested without a SQLite driver.
 */
interface ArticleLocalStore {
    fun observeAll(): Flow<List<ArticleRecord>>
    fun observeById(id: String): Flow<ArticleRecord?>
    fun observeBookmarked(): Flow<List<ArticleRecord>>
    suspend fun upsertAll(records: List<ArticleRecord>)
    suspend fun setBookmarked(id: String, bookmarked: Boolean)
}
