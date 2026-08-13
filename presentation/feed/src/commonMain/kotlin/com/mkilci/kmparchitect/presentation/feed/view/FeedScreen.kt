package com.mkilci.kmparchitect.presentation.feed.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mkilci.kmparchitect.core.designsystem.AppTheme
import com.mkilci.kmparchitect.core.ui.LoadingSurface
import com.mkilci.kmparchitect.core.ui.MessageSurface
import com.mkilci.kmparchitect.presentation.feed.model.FeedAction
import com.mkilci.kmparchitect.presentation.feed.model.FeedArticleUi
import com.mkilci.kmparchitect.presentation.feed.model.FeedNotice
import com.mkilci.kmparchitect.presentation.feed.model.FeedState

/**
 * Stateless content: immutable state in, actions out. It resolves nothing and navigates nowhere,
 * which is what lets a preview, a test and the sample all render the exact same screen.
 */
@Composable
fun FeedScreen(
    state: FeedState,
    onAction: (FeedAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingSurface(modifier)

        state.articles.isEmpty() -> MessageSurface(
            title = "No articles yet",
            body = "Pull in the feed to see the latest articles.",
            actionLabel = "Refresh",
            onAction = { onAction(FeedAction.RefreshClicked) },
            modifier = modifier,
        )

        else -> Column(
            modifier = modifier.fillMaxSize().safeContentPadding().padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            state.notice?.let { notice ->
                NoticeBanner(notice = notice, onDismiss = { onAction(FeedAction.NoticeDismissed) })
            }

            Button(
                onClick = { onAction(FeedAction.RefreshClicked) },
                enabled = !state.isRefreshing,
            ) {
                Text(if (state.isRefreshing) "Refreshing…" else "Refresh")
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                items(state.articles, key = { it.id.value }) { article ->
                    ArticleRow(
                        article = article,
                        onClick = { onAction(FeedAction.ArticleClicked(article.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeBanner(notice: FeedNotice, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppTheme.spacing.md)) {
            Text(
                text = when (notice) {
                    FeedNotice.Offline -> "You are offline. Showing saved articles."
                    FeedNotice.RefreshFailed -> "Could not refresh the feed."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun ArticleRow(article: FeedArticleUi, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        ) {
            Text(text = article.title, style = MaterialTheme.typography.titleMedium)
            Text(text = article.summary, style = MaterialTheme.typography.bodyMedium)
            Text(text = article.sourceLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}
