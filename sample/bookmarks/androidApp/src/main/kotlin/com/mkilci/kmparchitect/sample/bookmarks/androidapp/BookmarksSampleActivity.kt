package com.mkilci.kmparchitect.sample.bookmarks.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mkilci.kmparchitect.sample.bookmarks.shared.BookmarksSampleRoot

class BookmarksSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { BookmarksSampleRoot() }
    }
}
