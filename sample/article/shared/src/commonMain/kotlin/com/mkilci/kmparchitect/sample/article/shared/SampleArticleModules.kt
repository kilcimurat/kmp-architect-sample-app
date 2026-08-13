package com.mkilci.kmparchitect.sample.article.shared

import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.core.sharing.Sharer
import com.mkilci.kmparchitect.domain.article.ArticleRepository
import com.mkilci.kmparchitect.domain.article.ObserveArticle
import com.mkilci.kmparchitect.domain.article.ObserveArticleBookmarkState
import com.mkilci.kmparchitect.domain.article.SetArticleBookmarked
import com.mkilci.kmparchitect.domain.article.ShareArticle
import com.mkilci.kmparchitect.fixtures.article.FakeArticleRepository
import com.mkilci.kmparchitect.fixtures.article.RecordingSharer
import com.mkilci.kmparchitect.presentation.article.di.articlePresentationModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * The sample binds a recording [Sharer] rather than a real one.
 *
 * That is the safe-by-default rule in practice: a developer poking at this sample must not be able
 * to open a system share sheet, and CI must not depend on one existing. The recorded outcome is
 * fixed, so the screen's three share branches are reachable deterministically.
 */
fun sampleArticleModules(shareResult: ShareResult = ShareResult.Shared): List<Module> = listOf(
    module {
        single<ArticleRepository> { FakeArticleRepository() }
        single<Sharer> { RecordingSharer(shareResult) }
        factory { ObserveArticle(get()) }
        factory { ObserveArticleBookmarkState(get()) }
        factory { SetArticleBookmarked(get()) }
        factory { ShareArticle(get()) }
    },
    articlePresentationModule,
)

private var started = false

fun startArticleSampleKoinIfNeeded() {
    if (started) return
    started = true
    startKoin { modules(sampleArticleModules()) }
}

fun resetArticleSampleKoin() {
    if (!started) return
    stopKoin()
    started = false
}

internal fun articleSampleKoin() = KoinPlatform.getKoin()
