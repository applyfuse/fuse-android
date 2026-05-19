package com.applyfuse.fuse.data.repository

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.data.network.HttpClient
import com.applyfuse.fuse.domain.model.FeedItem
import com.applyfuse.fuse.domain.model.FeedPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

// FUSE: FeedRepository defines the data contract for the feed
// feature. Mirrors AuthRepository's shape — a small, suspend-only
// surface that hides transport, retry, and refresh concerns from
// the reducer and ViewModel. Mirror of fuse-ios
// FeedRepositoryProtocol.
//
// This file holds interface + LiveFeedRepository + wire DTOs +
// FakeFeedRepository, exactly like AuthRepository.kt holds
// interface + LiveAuthRepository + wire DTOs + FakeAuthRepository.
// The interface + Fake shipped in row 8 (FeedViewModel needed a
// type to inject and a fake to test against); row 9 (Option 1,
// fuse-docs ARCHITECTURE.md §5 step 5) adds the Live body + wire
// DTOs + the Hilt @Binds, cohesively with the ViewModel/UI it
// unblocks. See DATA_LAYER.md decision log (Row 9).

interface FeedRepository {
    suspend fun loadFeed(page: Int): FeedPage
}

// FUSE: LiveFeedRepository — the real implementation, a THIN
// coordination layer over HttpClient (mirror of fuse-ios
// LiveFeedRepository + this repo's LiveAuthRepository).
//
// Deliberately thin: translate the domain call into a request,
// hand the decoded wire response back as a domain model. NO retry /
// refresh logic here — the injected HttpClient is the SHARED
// @Singleton RefreshingHttpClient (NetworkModule), so 401-refresh-
// retry is automatic and inherited, exactly as LiveAuthRepository
// gets it. Keeping this layer thin keeps its tests focused on "did
// the right request go out and did we map the wire shape to the
// domain shape", mirroring the iOS comment.
//
// No TokenStore dependency: unlike auth, feed never writes tokens.
// It only reads them implicitly via the refresh-protected client's
// Authorization header. Minimal dependency list = minimal test
// surface (mirror of the iOS note).
class LiveFeedRepository @Inject constructor(
    private val httpClient: HttpClient
) : FeedRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadFeed(page: Int): FeedPage {
        // FUSE: page is sent as a query parameter. The server
        // returns { items: [...], page: N, has_more: Bool }; the
        // wire DTOs below map has_more -> hasMore explicitly via
        // @SerialName (we do not rely on a global naming strategy —
        // the seam is explicit and visible, FUSE "clear mental
        // model"). Mirror of iOS LiveFeedRepository.loadFeed.
        return httpClient.get("$PATH_FEED?page=$page") { raw ->
            json.decodeFromString(FeedPageWire.serializer(), raw)
        }.toDomain()
    }

    private companion object {
        const val PATH_FEED = "/feed"
    }
}

// FUSE: Wire types — internal, NOT in domain/model/, NOT public.
// Mirror of AuthRepository.kt's UserWire/LoginResponse: the wire
// format is allowed to differ from the domain shape, and keeping
// the seam HERE (next to the only code that decodes it) makes a
// future server change a surgical one-file edit. internal (not
// private) so same-module repository tests can build fixtures.
//
// NOTE: NONE of these @Serializable classes declares its own
// companion object — kotlinx.serialization owns the companion (it
// puts serializer() there); a private companion would make the
// generated serializer() inaccessible (the exact compile error
// AuthRepository.kt documents). No shared constants are needed
// here, so there is simply no companion.
//
// FeedItem/FeedPage are PURE domain models (row 6 decision: no
// @Serializable on domain types — the wire/domain seam). These
// *Wire DTOs are the deferred "+ wire types" clause of PHASE_2.md
// row 6, landing here with LiveFeedRepository exactly as the auth
// wire types live with LiveAuthRepository (row-6 decision log
// promised this placement).
@Serializable
internal data class FeedItemWire(
    val id: String,
    val title: String,
    val body: String
) {
    fun toDomain(): FeedItem = FeedItem(id = id, title = title, body = body)
}

@Serializable
internal data class FeedPageWire(
    val items: List<FeedItemWire>,
    val page: Int,
    // FUSE: server sends snake_case has_more; map explicitly to the
    // domain's hasMore. Explicit @SerialName over a global strategy
    // = the rename is visible at the exact seam it happens.
    @SerialName("has_more")
    val hasMore: Boolean
) {
    fun toDomain(): FeedPage = FeedPage(
        items = items.map { it.toDomain() },
        page = page,
        hasMore = hasMore
    )
}

// FUSE: FakeFeedRepository — the test double for FeedViewModelTest
// and the preview/default feed repository. Mirror of iOS
// MockFeedRepository, using the in-repo FakeAuthRepository
// conventions (plain class, public mutable knobs, call-tracking
// with a private-set list). UNCHANGED from row 8 — FeedViewModelTest
// depends on this exact shape; row 9 must not perturb it.
//
// Tests script per-page responses via `stubbedPages`, or set
// `errorToThrow` for failure paths. `loadFeedCalls` records exactly
// what the ViewModel asked for, so tests can assert pagination
// behaviour ("did it request page 2 after the user scrolled?").
//
// Reducer tests do NOT use this — the reducer is pure and receives
// FeedPage directly via FeedAction.FeedLoaded. This fake exists
// only for the ViewModel layer and as a preview default.
class FakeFeedRepository(
    var stubbedPages: Map<Int, FeedPage> = emptyMap(),
    var errorToThrow: AppError? = null
) : FeedRepository {

    var loadFeedCalls: List<Int> = emptyList()
        private set

    override suspend fun loadFeed(page: Int): FeedPage {
        loadFeedCalls = loadFeedCalls + page
        errorToThrow?.let { throw it }
        return stubbedPages[page] ?: FeedPage(
            items = emptyList(),
            page = page,
            hasMore = false
        )
    }
}
