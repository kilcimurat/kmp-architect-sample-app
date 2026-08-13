package com.mkilci.kmparchitect.presentation.article.di

import com.mkilci.kmparchitect.core.model.ArticleId
import com.mkilci.kmparchitect.core.mvi.DefaultStateStore
import com.mkilci.kmparchitect.presentation.article.model.ArticleState
import com.mkilci.kmparchitect.presentation.article.viewmodel.ArticleViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val articlePresentationModule: Module = module {
    viewModel { (articleId: String) ->
        ArticleViewModel(
            stateStore = DefaultStateStore(ArticleState()),
            articleId = ArticleId(articleId),
            observeArticle = get(),
            shareArticle = get(),
        )
    }
}
