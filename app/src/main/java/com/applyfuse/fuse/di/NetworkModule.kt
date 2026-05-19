package com.applyfuse.fuse.di

import com.applyfuse.fuse.data.network.HttpClient
import com.applyfuse.fuse.data.network.LiveHttpClient
import com.applyfuse.fuse.data.network.RefreshingHttpClient
import com.applyfuse.fuse.data.repository.RefreshTokenRequest
import com.applyfuse.fuse.data.repository.RefreshResponse
import com.applyfuse.fuse.data.token.LiveTokenStore
import com.applyfuse.fuse.data.token.TokenStore
import com.applyfuse.fuse.domain.model.AuthTokens
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

// FUSE: NetworkModule wires the entire data-layer transport graph.
//
// IMPORTANT — rows 4 + 10 are unified here (see DATA_LAYER.md
// decision log "Rows 4+10 Hilt wiring unified"). Hilt validates the
// WHOLE dependency graph at compile time, so the moment
// LiveAuthRepository gained real (HttpClient, TokenStore)
// constructor deps in row 4, the app could not compile until those
// were bound — which PHASE_2.md had scheduled as row 10. Rows 4 and
// 10 are therefore not independently shippable; this module is the
// row-10 Hilt wiring pulled forward. Row 10 later only ADDS
// LiveFeedRepository sharing the SAME RefreshingHttpClient.
//
// Build order (mirror of DATA_LAYER.md "Hilt wiring"):
//   Json ─┐
//         ├─> bare LiveHttpClient ─┐
//   OkHttp┘  (qualified @Bare)     ├─> RefreshingHttpClient ─> HttpClient
//   LiveTokenStore ─> TokenStore ──┘   (the bound HttpClient)
//
// The refresh lambda calls the BARE client (recursion safety: a 401
// on /auth/refresh must NOT re-enter the decorator) — the contract's
// stated mechanism, and why no requiresAuth flag is needed.
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // FUSE: one shared, configured JSON for the whole app.
    // ignoreUnknownKeys so a server adding a field never breaks
    // decoding (forward-compatible wire contract).
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    // FUSE: the BARE transport. @Named("bare") so it is distinct
    // from the bound HttpClient (the RefreshingHttpClient). The
    // refresh lambda MUST use THIS, never the decorator.
    @Provides
    @Singleton
    @Named(BARE_CLIENT)
    fun provideBareHttpClient(
        okHttpClient: OkHttpClient,
        json: Json
    ): HttpClient = LiveHttpClient(
        client = okHttpClient,
        baseUrl = BASE_URL,
        json = json
    )

    // FUSE: LiveTokenStore bound as the single shared TokenStore.
    // The bare client (token provider, later), the refresh lambda,
    // and LiveAuthRepository all share THIS instance.
    @Provides
    @Singleton
    fun provideTokenStore(impl: LiveTokenStore): TokenStore = impl

    // FUSE: the bound HttpClient = RefreshingHttpClient wrapping the
    // bare client. EVERY repository that injects HttpClient gets
    // this, so 401-refresh-retry is automatic and shared.
    //
    // The refresh lambda: read current tokens, POST /auth/refresh
    // through the BARE client (recursion-safe), persist + return the
    // rotated tokens. If there is no refresh token we cannot
    // refresh — propagate Unauthorized so the user is signed out.
    @Provides
    @Singleton
    fun provideHttpClient(
        @Named(BARE_CLIENT) bare: HttpClient,
        tokenStore: TokenStore,
        json: Json
    ): HttpClient = RefreshingHttpClient(
        delegate = bare,
        tokenStore = tokenStore,
        refresh = {
            val current = tokenStore.read()
                ?: throw com.applyfuse.fuse.core.AppError.Unauthorized
            val body = json.encodeToString(
                RefreshTokenRequest.serializer(),
                RefreshTokenRequest(refreshToken = current.refreshToken)
            )
            val refreshed: AuthTokens = bare.post(PATH_REFRESH, body) { raw ->
                json.decodeFromString(RefreshResponse.serializer(), raw)
                    .toAuthTokens()
            }
            refreshed
        }
    )

    private const val BARE_CLIENT = "bare"

    // FUSE: base URL as a named const (decision-log recorded). A
    // const, not a BuildConfig field — no gradle change, trivially
    // swapped, fits FUSE's "clear mental model". Make it a
    // BuildConfig/flavor field if/when staging vs prod is needed.
    private const val BASE_URL = "https://api.applyfuse.com"

    private const val PATH_REFRESH = "/auth/refresh"
}
