package com.mkilci.kmparchitect.sample.bookmarks.androidapp

import android.app.Application
import com.mkilci.kmparchitect.sample.bookmarks.shared.startBookmarksSampleKoinIfNeeded

class BookmarksSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startBookmarksSampleKoinIfNeeded()
    }
}
