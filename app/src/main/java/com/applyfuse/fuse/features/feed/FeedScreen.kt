package com.applyfuse.fuse.features.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.domain.model.FeedItem

// FUSE: FeedScreen is the thin Compose reader for FeedState
// (FUSE rule 2 — UI only reads state, never mutates; every
// interaction goes through send()). Mirror of fuse-ios
// Sources/Features/Feed/FeedView.swift and the in-repo
// AuthScreen.kt conventions.
//
// Public entry takes viewModel = hiltViewModel() exactly like
// AuthScreen — row 9 Option 1 closed the row-8 Hilt deferral, so
// FeedScreen's signature now matches AuthScreen with no asymmetry
// (fuse-docs ARCHITECTURE.md §3 DI mapping: Android = hiltViewModel).
//
// NO LaunchedEffect event collector — unlike AuthScreen, feed has
// NO FuseEvent (mirror of iOS, whose FeedView has no event
// channel: feed never navigates). The only one-time concern,
// pull-to-refresh "settle", is read straight off state.loading.
//
// Four content shapes (mirror iOS FeedView.content), driven purely
// by state — the screen contains zero business logic, all
// pagination/loading decisions live in the reducer + VM:
//   1. loading == Initial            → full-screen spinner
//   2. isEmpty (no items/err, idle)  → "No posts yet" placeholder
//   3. items present                 → list + load-more/footer
//   4. error != null                 → dismissible banner (overlaid
//                                       so items stay visible on a
//                                       failed loadMore — invariant 6)

@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // FUSE: first composition kicks off the initial load. The
    // reducer's LoadInitial guard (no-op if items already exist)
    // makes this safe across recompositions/config changes — the
    // screen just announces "I appeared", the reducer decides.
    LaunchedEffect(Unit) {
        viewModel.send(FeedAction.LoadInitial)
    }

    FeedContent(
        state = state,
        onAction = viewModel::send
    )
}

@Composable
private fun FeedContent(
    state: FeedState,
    onAction: (FeedAction) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07070D),
                        Color(0xFF0D0D1A)
                    )
                )
            )
    ) {
        when {
            state.loading == FeedLoadingState.Initial ->
                // FUSE: full-screen variant — the first ever load,
                // nothing else on screen yet.
                LoadingSpinner(fullScreen = true)

            state.isEmpty ->
                EmptyPlaceholder()

            else ->
                FeedList(state = state, onAction = onAction)
        }

        // FUSE: error banner is OVERLAID (Box, top-aligned), not
        // part of the when above, so a failed LoadMore on page 2
        // shows the banner WITHOUT hiding the already-loaded items
        // (feedReducer invariant 6 — failures don't wipe items —
        // surfaced visually). state.error is the raw AppError?
        // (row-7 decision); the user-facing string is derived HERE
        // at the edge via AppError.userMessage, exactly as the
        // row-7 decision log promised ("message derived at the
        // edge — row 9's FeedScreen call error.userMessage").
        AnimatedVisibility(
            visible = state.error != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ErrorBanner(
                message = state.error?.userMessage.orEmpty(),
                onDismiss = { onAction(FeedAction.DismissError) }
            )
        }
    }
}

// FUSE: one spinner, two sizes. fullScreen=true is the initial-load
// spinner (centred, larger, fills the screen); fullScreen=false is
// the load-more footer spinner (smaller, sits at the list bottom).
// Merged from the former FullScreenSpinner + FooterSpinner — they
// differed only in size/stroke and a footer wrapper, so a single
// parameterised composable is DRYer AND keeps the file at 12
// functions (detekt TooManyFunctions thresholdInFiles=12; the 4
// @Preview fns are counted by the in-files rule despite
// ignoreAnnotated, exactly as for AuthScreen which sits at 12).
@Composable
private fun LoadingSpinner(fullScreen: Boolean) {
    val sizeModifier = if (fullScreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    }
    Box(
        modifier = sizeModifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = if (fullScreen) Modifier else Modifier.size(24.dp),
            color = Color(0xFF5D52CC),
            strokeWidth = if (fullScreen) 3.dp else 2.dp
        )
    }
}

@Composable
private fun EmptyPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No posts yet",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFB8B8D8)
        )
    }
}

@Composable
private fun FeedList(
    state: FeedState,
    onAction: (FeedAction) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 72.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = state.items,
            key = { it.id }
        ) { item ->
            FeedRow(item = item)
        }

        // FUSE: footer — either the loading-more spinner or the
        // load-more button, never both. canLoadMore is a DERIVED
        // state property (hasMore && loading == Idle), so the
        // screen never re-derives pagination — it just renders
        // what state says (FUSE rule 4).
        item {
            when {
                state.loading == FeedLoadingState.LoadingMore ->
                    LoadingSpinner(fullScreen = false)

                state.canLoadMore ->
                    LoadMoreButton(
                        onClick = { onAction(FeedAction.LoadMore) }
                    )

                else -> Unit
            }
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = item.body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB8B8D8)
        )
    }
}

@Composable
private fun LoadMoreButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF5D52CC)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(
            text = "Load more",
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                color = Color.Red.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "\u26A0\uFE0F", fontSize = 16.sp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB3B3),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Text(text = "\u00D7", color = Color.Gray, fontSize = 18.sp)
        }
    }
}

// FUSE: a @Preview per meaningful state (fuse-docs ARCHITECTURE.md
// §5 step 8 + §9 "state snapshots"), driven by the FeedState
// companion fixtures — the same fixtures the reducer tests use, so
// previews and tests never drift.

@Preview(name = "Initial loading", showBackground = true, backgroundColor = 0xFF07070D)
@Composable
private fun PreviewInitialLoading() {
    FeedContent(state = FeedState.LoadingInitial, onAction = {})
}

@Preview(name = "Empty", showBackground = true, backgroundColor = 0xFF07070D)
@Composable
private fun PreviewEmpty() {
    FeedContent(state = FeedState.Empty, onAction = {})
}

@Preview(name = "Loaded", showBackground = true, backgroundColor = 0xFF07070D)
@Composable
private fun PreviewLoaded() {
    FeedContent(
        state = FeedState(
            items = listOf(
                FeedItem("1", "First post", "Body of the first post."),
                FeedItem("2", "Second post", "Body of the second post."),
                FeedItem("3", "Third post", "Body of the third post.")
            ),
            currentPage = 1,
            hasMore = true
        ),
        onAction = {}
    )
}

@Preview(name = "Error", showBackground = true, backgroundColor = 0xFF07070D)
@Composable
private fun PreviewError() {
    FeedContent(
        state = FeedState(error = AppError.NetworkUnavailable),
        onAction = {}
    )
}
