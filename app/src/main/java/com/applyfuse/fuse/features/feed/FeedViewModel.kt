package com.applyfuse.fuse.features.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.data.repository.FakeFeedRepository
import com.applyfuse.fuse.data.repository.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// FUSE: FeedViewModel owns state, calls the reducer, handles
// effects. Mirror of fuse-ios Sources/Features/Feed/FeedViewModel.
//
// DELIBERATELY does NOT extend BaseViewModel (the in-repo
// Store<S,A> analogue), unlike AuthViewModel. This is the
// user-confirmed Option C and a faithful mirror of iOS, where
// FeedViewModel is intentionally a standalone ObservableObject that
// does NOT route through the shared Store — for one specific
// reason: the effect-firing guard.
//
// BaseViewModel.send() is final and ALWAYS launches handleEffect
// post-reducer. Auth can do that: every auth action
// (LoginTapped/LogoutTapped) genuinely transitions, so an
// unconditional effect is correct. Feed cannot: feedReducer's
// invariant-3 guards (LoadInitial when items exist, LoadMore when
// hasMore=false, LoadMore/Refresh while loading) are NO-OPS that
// leave state.loading UNCHANGED. An unconditional post-reducer
// effect would fire a wasted network request for every such no-op
// — and, worse, on a rapid-scroll LoadMore-while-already-loading it
// would double-fire. The fix needs the loading value from BEFORE
// the reducer ran; BaseViewModel exposes no pre-reducer hook and
// send() is not open. So FeedViewModel implements its own send()
// with the previousLoading guard, exactly as iOS's standalone
// FeedViewModel does. BaseViewModel / AuthViewModel are left
// completely untouched (zero auth-regression risk). Recorded in
// DATA_LAYER.md decision log (Row 8).
//
// FUSE: @HiltViewModel @Inject — added row 9 (Option 1). The
// row-8 decision log flagged this as a DELIBERATE deferral: Hilt
// validates the WHOLE graph at compile time, so annotating
// @HiltViewModel before FeedRepository had a binding would have
// failed [Dagger/MissingBinding]. Row 9 added the
// FeedRepository → LiveFeedRepository @Binds (RepositoryModule)
// FIRST, so the graph now resolves and the annotation is safe.
// This closes the second of FeedViewModel's two logged deviations
// from fuse-docs ARCHITECTURE.md §3/§5/§6 (the DI mapping says the
// Android VM is @HiltViewModel) — the FIRST (standalone, not
// BaseViewModel) remains a justified per-feature deviation for the
// effect-firing guard, with §1's "pick the shape per feature"
// licence. FeedViewModel is now back on the contract's DI mapping
// while keeping its justified standalone form. Recorded in
// DATA_LAYER.md decision log (Row 9).

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    // FUSE: the single entry point for all interactions. Mirror of
    // iOS FeedViewModel.send():
    //   1. capture loading BEFORE the reducer
    //   2. run the pure reducer (state updates synchronously)
    //   3. spawn an effect ONLY if the reducer transitioned into a
    //      NEW, non-Idle loading state
    fun send(action: FeedAction) {
        val previousLoading = _state.value.loading
        _state.update { feedReducer(it, action) }
        val newLoading = _state.value.loading

        // FUSE: Two conditions, BOTH required (mirror iOS guard):
        //   1. newLoading != previousLoading — filters reducer
        //      no-ops (LoadMore when hasMore=false, LoadInitial
        //      when items exist, LoadMore/Refresh while loading).
        //      On a no-op the reducer returned state unchanged so
        //      loading is identical → no effect.
        //   2. newLoading != Idle — filters the response actions
        //      (FeedLoaded / FeedFailed / DismissError) which
        //      transition AWAY from a loading state back to Idle.
        //      Those carry their own state change; they must NOT
        //      trigger another network call (that would recurse).
        // ReturnCount: exactly one early return + the implicit end
        // (<= 2, detekt-safe).
        if (newLoading == previousLoading || newLoading == FeedLoadingState.Idle) {
            return
        }

        viewModelScope.launch {
            handleEffect(action)
        }
    }

    // FUSE: async work lives here, AFTER the reducer has updated
    // state. Mirror of iOS handleEffect. LoadInitial/Refresh load
    // page 1; LoadMore loads currentPage+1 (currentPage is the
    // last successfully-loaded page — the reducer did not touch it
    // on the LoadMore transition, so the next page is +1). The
    // response/dismiss actions never reach here (the send() guard
    // filters them: they leave loading == Idle).
    private suspend fun handleEffect(action: FeedAction) {
        when (action) {
            is FeedAction.LoadInitial,
            is FeedAction.Refresh ->
                performLoad(page = FIRST_PAGE)

            is FeedAction.LoadMore ->
                performLoad(page = _state.value.currentPage + 1)

            is FeedAction.FeedLoaded,
            is FeedAction.FeedFailed,
            is FeedAction.DismissError ->
                Unit
        }
    }

    // FUSE: load one page and feed the result back through send()
    // as a response action, so the SAME reducer + guard pipeline
    // processes it. Typed-error discipline mirrors AuthViewModel's
    // performLogin: AppError caught narrowly first; any other
    // Exception mapped to a typed AppError at the ViewModel
    // boundary via AppError.from() — never in the reducer.
    // TooGenericExceptionCaught / SwallowedException are
    // intentionally disabled in detekt.yml for exactly this
    // boundary mapping.
    private suspend fun performLoad(page: Int) {
        try {
            val result = feedRepository.loadFeed(page)
            send(FeedAction.FeedLoaded(result))
        } catch (e: AppError) {
            send(FeedAction.FeedFailed(e))
        } catch (e: Exception) {
            send(FeedAction.FeedFailed(AppError.from(e)))
        }
    }

    private companion object {
        // FUSE: LoadInitial and Refresh both fetch page 1 (mirror
        // iOS performLoad(page: 1)). Named so it is not a
        // MagicNumber and the intent is explicit.
        const val FIRST_PAGE = 1
    }
}

// FUSE: PreviewFeedViewModel for Compose @Preview — uses
// FakeFeedRepository directly without Hilt (unavailable in preview
// context). Mirror of PreviewAuthViewModel + iOS's FeedViewModel
// preview/previewLoading/previewWithItems/previewError helpers,
// adapted to the in-repo "one Preview*ViewModel class" convention
// rather than four static factories.
class PreviewFeedViewModel(
    initialState: FeedState = FeedState.Empty
) : ViewModel() {

    private val fakeRepo = FakeFeedRepository()

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    // FUSE: preview is static — no effects spawned. The preview VM
    // exists only to render a fixed state in @Preview; send() here
    // just runs the pure reducer so interactive previews still
    // reflect state transitions without touching the (fake) repo.
    fun send(action: FeedAction) {
        _state.update { feedReducer(it, action) }
    }

    // FUSE: referenced so the fake is not flagged as an unused
    // private member (detekt UnusedPrivateMember); also documents
    // that a real preview-with-data variant would drive this repo.
    fun loadPreviewData() {
        viewModelScope.launch {
            runCatching { fakeRepo.loadFeed(1) }
                .onSuccess { _state.update { s -> s.copy(items = it.items) } }
        }
    }
}
