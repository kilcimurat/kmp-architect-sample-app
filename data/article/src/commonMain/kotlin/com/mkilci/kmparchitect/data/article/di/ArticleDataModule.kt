package com.mkilci.kmparchitect.data.article.di

import com.mkilci.kmparchitect.data.article.DefaultArticleRepository
import com.mkilci.kmparchitect.domain.article.ArticleRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val articleDataModule: Module = module {
    single<ArticleRepository> { DefaultArticleRepository(local = get()) }
}
