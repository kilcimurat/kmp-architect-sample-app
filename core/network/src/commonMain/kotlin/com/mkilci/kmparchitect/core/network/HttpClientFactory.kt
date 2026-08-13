package com.mkilci.kmparchitect.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Feature-neutral HTTP plumbing.
 *
 * The engine is a parameter, not a choice made here: Android supplies OkHttp, iOS supplies Darwin,
 * and the demo backend supplies a `MockEngine`. Because the caller owns the engine, no feature and
 * no sample inherits an opinion about where bytes come from.
 */
data class NetworkConfig(
    val baseUrl: String,
)

val networkJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(networkJson)
    }
}
