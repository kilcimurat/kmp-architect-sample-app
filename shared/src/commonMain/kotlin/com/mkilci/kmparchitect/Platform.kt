package com.mkilci.kmparchitect

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform