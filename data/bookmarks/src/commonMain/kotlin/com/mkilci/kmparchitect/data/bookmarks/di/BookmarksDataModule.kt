package com.mkilci.kmparchitect.data.bookmarks.di

import com.mkilci.kmparchitect.data.bookmarks.DefaultBookmarkRepository
import com.mkilci.kmparchitect.domain.bookmarks.BookmarkRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val bookmarksDataModule: Module = module {
    single<BookmarkRepository> { DefaultBookmarkRepository(local = get()) }
}
