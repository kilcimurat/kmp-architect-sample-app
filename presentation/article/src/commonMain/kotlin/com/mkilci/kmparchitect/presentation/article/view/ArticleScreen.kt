package com.mkilci.kmparchitect.presentation.article.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mkilci.kmparchitect.core.designsystem.AppTheme
import com.mkilci.kmparchitect.core.ui.LoadingSurface
import com.mkilci.kmparchitect.core.ui.MessageSurface
import com.mkilci.kmparchitect.presentation.article.model.ArticleAction
import com.mkilci.kmparchitect.presentation.article.model.ArticleState
import com.mkilci.kmparchitect.presentation.article.model.ShareOutcome

@Composable
fun ArticleScreen(
    state: ArticleState,
    onAction: (ArticleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingSurface(modifier)

        state.isMissing || state.article == null -> MessageSurface(
            title = "Article not available",
            body = "This article is not in local storage.",
            actionLabel = "Back",
            onAction = { onAction(ArticleAction.BackClicked) },
            modifier = modifier,
        )

        else -> Column(
            modifier = modifier.fillMaxSize().safeContentPadding().padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            Text(state.article.title, style = MaterialTheme.typography.headlineSmall)
            Text(state.article.sourceLabel, style = MaterialTheme.typography.labelMedium)
            Text(state.article.summary, style = MaterialTheme.typography.bodyLarge)
            Text(state.article.url, style = MaterialTheme.typography.bodySmall)

            Button(onClick = { onAction(ArticleAction.ShareClicked) }) { Text("Share") }

            state.shareOutcome?.let { outcome ->
                Text(
                    text = when (outcome) {
                        ShareOutcome.Shared -> "Shared."
                        ShareOutcome.Cancelled -> "Sharing cancelled."
                        ShareOutcome.Unavailable -> "Sharing is not available in this build."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { onAction(ArticleAction.ShareOutcomeDismissed) }) { Text("Dismiss") }
            }

            TextButton(onClick = { onAction(ArticleAction.BackClicked) }) { Text("Back") }
        }
    }
}
