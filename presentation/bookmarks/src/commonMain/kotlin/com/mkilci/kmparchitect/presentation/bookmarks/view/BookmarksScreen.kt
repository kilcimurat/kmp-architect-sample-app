package com.mkilci.kmparchitect.presentation.bookmarks.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mkilci.kmparchitect.core.designsystem.AppTheme
import com.mkilci.kmparchitect.core.ui.LoadingSurface
import com.mkilci.kmparchitect.core.ui.MessageSurface
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarksAction
import com.mkilci.kmparchitect.presentation.bookmarks.model.BookmarksState

@Composable
fun BookmarksScreen(
    state: BookmarksState,
    onAction: (BookmarksAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingSurface(modifier)

        state.bookmarks.isEmpty() -> MessageSurface(
            title = "Nothing saved yet",
            body = "Articles you bookmark appear here.",
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize().safeContentPadding().padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            items(state.bookmarks, key = { it.id.value }) { bookmark ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(BookmarksAction.BookmarkClicked(bookmark.id)) },
                ) {
                    Row(
                        modifier = Modifier.padding(AppTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bookmark.title, style = MaterialTheme.typography.titleMedium)
                            Text(bookmark.sourceLabel, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onAction(BookmarksAction.RemoveClicked(bookmark.id)) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}
