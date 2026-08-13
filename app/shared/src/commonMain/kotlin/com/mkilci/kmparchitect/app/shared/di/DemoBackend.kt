package com.mkilci.kmparchitect.app.shared.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * The production app ships a deterministic backend.
 *
 * The Ktor client, content negotiation, DTO parsing and offline-first sync are all real; only the
 * transport is pinned. That keeps the sample app reproducible for readers and keeps build
 * benchmarks from depending on somebody else's uptime, without reducing the data layer to a fake.
 *
 * Swapping in a live server is a one-line change in this file — which is the point of the engine
 * being a parameter everywhere else.
 */
internal const val DEMO_BASE_URL: String = "https://demo.kmparchitect.test"

private val demoFeedJson: String = """
[
  {
    "id": "article-architecture",
    "title": "Module-level build isolation in KMP",
    "summary": "Why a feature should build without the app it belongs to.",
    "source": "Demo Backend",
    "url": "https://demo.kmparchitect.test/articles/architecture",
    "publishedAt": 1767222000000
  },
  {
    "id": "article-effects",
    "title": "Typed one-shot effects",
    "summary": "Navigation commands are events, not state.",
    "source": "Demo Backend",
    "url": "https://demo.kmparchitect.test/articles/effects",
    "publishedAt": 1767218400000
  },
  {
    "id": "article-benchmarks",
    "title": "Measuring what the diagram promised",
    "summary": "Isolation claims that survive a stopwatch.",
    "source": "Demo Backend",
    "url": "https://demo.kmparchitect.test/articles/benchmarks",
    "publishedAt": 1767214800000
  },
  {
    "id": "article-fixtures",
    "title": "One fake, two callers",
    "summary": "Why deterministic fixtures deserve their own module in KMP.",
    "source": "Demo Backend",
    "url": "https://demo.kmparchitect.test/articles/fixtures",
    "publishedAt": 1767211200000
  }
]
""".trimIndent()

internal fun demoBackendEngine(): HttpClientEngine = MockEngine { request ->
    when {
        request.url.encodedPath.endsWith("/feed") -> respond(
            content = demoFeedJson,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
        else -> respond(
            content = "",
            status = HttpStatusCode.NotFound,
        )
    }
}
