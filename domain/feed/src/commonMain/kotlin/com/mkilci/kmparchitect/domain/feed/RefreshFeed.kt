package com.mkilci.kmparchitect.domain.feed

class RefreshFeed(
    private val repository: FeedRepository,
) {
    suspend operator fun invoke(): FeedRefreshResult = repository.refresh()
}
