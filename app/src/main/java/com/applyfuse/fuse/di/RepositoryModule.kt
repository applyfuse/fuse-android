package com.applyfuse.fuse.di

import com.applyfuse.fuse.core.AppDispatchers
import com.applyfuse.fuse.data.repository.AuthRepository
import com.applyfuse.fuse.data.repository.LiveAuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// FUSE: RepositoryModule tells Hilt which concrete class to
// inject whenever a constructor asks for AuthRepository.
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
