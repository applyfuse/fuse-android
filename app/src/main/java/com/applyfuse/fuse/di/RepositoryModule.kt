package com.applyfuse.fuse.di

import com.applyfuse.fuse.core.AppDispatchers
import com.applyfuse.fuse.data.repository.AuthRepository
import com.applyfuse.fuse.data.repository.FeedRepository
import com.applyfuse.fuse.data.repository.LiveAuthRepository
import com.applyfuse.fuse.data.repository.LiveFeedRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// FUSE: RepositoryModule tells Hilt which concrete class to
// inject whenever a constructor asks for a repository interface.
//
// @Binds is more efficient than @Provides for binding
// an interface to its implementation — no extra function body.
//
// @Singleton ensures one instance is created for the entire
// app lifetime — repositories are stateless so this is safe.

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // FUSE: Bind AuthRepository interface → LiveAuthRepository.
    // To swap implementations (e.g. for a staging build),
    // change this binding — no feature code changes needed.
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: LiveAuthRepository
    ): AuthRepository

    // FUSE: Bind FeedRepository interface → LiveFeedRepository.
    // LiveFeedRepository @Inject's HttpClient, which Hilt resolves
    // to the SAME @Singleton RefreshingHttpClient bound in
    // NetworkModule that LiveAuthRepository gets — so the feed
    // inherits 401-refresh-retry for free, sharing one client
    // across both features (fuse-docs ARCHITECTURE.md §7 pattern 5:
    // "the interceptor wraps the transport, not the repository, so
    // every repository inherits refresh for free"). Same @Binds
    // shape as bindAuthRepository — swap impl here for a staging
    // build with zero feature-code changes (FUSE rule 6:
    // repositories are interface-bound).
    @Binds
    @Singleton
    abstract fun bindFeedRepository(
        impl: LiveFeedRepository
    ): FeedRepository
}

// FUSE: Separate module for non-abstract @Provides bindings.
// Dispatchers are provided here so any class can inject
// AppDispatchers and swap them in tests.

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers()
}
