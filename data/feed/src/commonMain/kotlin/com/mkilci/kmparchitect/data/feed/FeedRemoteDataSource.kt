package com.mkilci.kmparchitect.data.feed

import com.mkilci.kmparchitect.core.network.NetworkConfig
import com.mkilci.kmparchitect.data.articlestore.ArticleRecord
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/** Wire shape. It stays in this module; nothing above the data layer ever sees a DTO. */
@Serializable
internal data class FeedArticleDto(
    val id: String,
    val title: String,
    val summary: String = "",
    val source: String = "Unknown",
    val url: String,
    val publishedAt: Long,
)

/** What the remote can tell us, expressed so the repository can decide without seeing HTTP. */
sealed interface RemoteFeedResult {
    data class Available(val records: List<ArticleRecord>) : RemoteFeedResult
    data object Unreachable : RemoteFeedResult
    data object Malformed : RemoteFeedResult
}

class FeedRemoteDataSource(
    private val client: HttpClient,
    private val config: NetworkConfig,
) {
    suspend fun fetchFeed(): RemoteFeedResult = try {
        val response = client.get("${config.baseUrl}/feed")
        if (!response.status.isSuccess()) {
            RemoteFeedResult.Unreachable
        } else {
            RemoteFeedResult.Available(response.body<List<FeedArticleDto>>().map(FeedArticleDto::toRecord))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        // Ktor wraps deserialization failures in its own conversion exception, so catching
        // SerializationException directly silently misclassifies malformed payloads as "offline".
        // Walking the cause chain keeps this classification correct without coupling the data
        // source to Ktor's exception hierarchy.
        if (error.isSerializationFailure()) {
            RemoteFeedResult.Malformed
        } else {
            // A transport failure is indistinguishable from being offline at this layer; the
            // repository decides what that means for the user.
            RemoteFeedResult.Unreachable
        }
    }
}

private fun Throwable.isSerializationFailure(): Boolean =
    generateSequence(this, Throwable::cause)
        .take(MAX_CAUSE_DEPTH)
        .any { it is SerializationException }

private const val MAX_CAUSE_DEPTH = 8

private fun FeedArticleDto.toRecord() = ArticleRecord(
    id = id,
    title = title,
    summary = summary,
    source = source,
    url = url,
    publishedAtEpochMillis = publishedAt,
    bookmarked = false,
)
