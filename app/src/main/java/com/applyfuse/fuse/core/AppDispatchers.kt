package com.applyfuse.fuse.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// FUSE: AppDispatchers wraps coroutine dispatchers so they can
// be injected and replaced in tests.
//
// In production: real Dispatchers.Main / IO / Default
// In tests:      UnconfinedTestDispatcher via Dispatchers.setMain()

data class AppDispatchers(
    // FUSE: Use for UI updates and state emissions.
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,

    // FUSE: Use for network calls, file I/O, and database operations.
    val io: CoroutineDispatcher = Dispatchers.IO,

    // FUSE: Use for CPU-intensive work like sorting, parsing, or mapping.
    val default: CoroutineDispatcher = Dispatchers.Default
)

// FUSE: Convenience singleton for production use.
// Cannot extend a data class — use a val with default constructor instead.
// Inject this via Hilt rather than using Dispatchers directly.
val ProductionDispatchers = AppDispatchers()
