package com.mkilci.kmparchitect.data.articlestore

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mkilci.kmparchitect.core.database.DatabaseDriverFactory
import com.mkilci.kmparchitect.data.articlestore.db.ArticleDatabase
import com.mkilci.kmparchitect.data.articlestore.db.ArticleRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightArticleLocalStore(
    driverFactory: DatabaseDriverFactory,
    private val dispatcher: CoroutineDispatcher,
    databaseName: String = DATABASE_NAME,
) : ArticleLocalStore {

    private val database = ArticleDatabase(
        driverFactory.create(ArticleDatabase.Schema, databaseName),
    )
    private val queries = database.articleQueries

    override fun observeAll(): Flow<List<ArticleRecord>> =
        queries.selectAll().asFlow().mapToList(dispatcher).mapRecords()

    override fun observeById(id: String): Flow<ArticleRecord?> =
        queries.selectById(id).asFlow().mapToOneOrNull(dispatcher).mapRecord()

    override fun observeBookmarked(): Flow<List<ArticleRecord>> =
        queries.selectBookmarked().asFlow().mapToList(dispatcher).mapRecords()

    override suspend fun upsertAll(records: List<ArticleRecord>) = withContext(dispatcher) {
        database.transaction {
            records.forEach { record ->
                // Insert-then-update rather than ON CONFLICT DO UPDATE: see Article.sq. The update
                // touches content columns only, so an existing bookmark survives a refresh.
                queries.insertIfAbsent(
                    id = record.id,
                    title = record.title,
                    summary = record.summary,
                    source = record.source,
                    url = record.url,
                    publishedAtEpochMillis = record.publishedAtEpochMillis,
                )
                queries.updateContent(
                    title = record.title,
                    summary = record.summary,
                    source = record.source,
                    url = record.url,
                    publishedAtEpochMillis = record.publishedAtEpochMillis,
                    id = record.id,
                )
            }
        }
    }

    override suspend fun setBookmarked(id: String, bookmarked: Boolean) {
        withContext(dispatcher) {
            queries.setBookmarked(bookmarked = bookmarked, id = id)
        }
    }

    private companion object {
        const val DATABASE_NAME = "articles.db"
    }
}

private fun Flow<List<ArticleRow>>.mapRecords(): Flow<List<ArticleRecord>> =
    map { rows -> rows.map(ArticleRow::toRecord) }

private fun Flow<ArticleRow?>.mapRecord(): Flow<ArticleRecord?> =
    map { row -> row?.toRecord() }

private fun ArticleRow.toRecord() = ArticleRecord(
    id = id,
    title = title,
    summary = summary,
    source = source,
    url = url,
    publishedAtEpochMillis = publishedAtEpochMillis,
    bookmarked = bookmarked,
)
