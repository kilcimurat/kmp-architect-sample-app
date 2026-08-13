package com.mkilci.kmparchitect.sample.article.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mkilci.kmparchitect.sample.article.shared.ArticleSampleRoot

class ArticleSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ArticleSampleRoot() }
    }
}
