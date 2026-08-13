package com.mkilci.kmparchitect.data.feed.di

import com.mkilci.kmparchitect.data.feed.DefaultFeedRepository
import com.mkilci.kmparchitect.data.feed.FeedRemoteDataSource
import com.mkilci.kmparchitect.domain.feed.FeedRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * This module binds only what it owns: the implementations declared in this project.
 *
 * The `HttpClient`, the `NetworkConfig` and the `ArticleLocalStore` are resolved, not created here —
 * choosing an engine, a base URL and a driver is a composition-root decision, and hard-coding them
 * here would mean every consumer of the feed data layer inherited them.
 */
val feedDataModule: Module = module {
    single { FeedRemoteDataSource(client = get(), config = get()) }
    single<FeedRepository> { DefaultFeedRepository(local = get(), remote = get()) }
}
