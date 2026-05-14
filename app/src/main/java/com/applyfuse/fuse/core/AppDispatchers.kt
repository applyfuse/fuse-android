package com.applyfuse.fuse.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// FUSE: AppDispatchers wraps coroutine dispatchers so they can
// be injected and replaced in tests.
//
// In production: real Dispatchers.Main / IO / Default
// In tests:      TestCoroutineDispatcher or UnconfinedTestDispatcher
//
// Inject AppDispatchers via Hilt into any class that needs
// to switch dispatchers explicitly.
//
// Most feature ViewModels don't need this directly — viewModelScope
// already uses Dispatchers.Main.immediate. Use AppDispatchers when
// you need explicit dispatch control in a repository or use case.

data class AppDispatchers(
    // FUSE: Use for UI updates and state emissions.
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,

    // FUSE: Use for network calls, file I/O, and database operations.
    val io: CoroutineDispatcher = Dispatchers.IO,

    // FUSE: Use for CPU-intensive work like sorting, parsing, or mapping.
    val default: CoroutineDispatcher = Dispatchers.Default
)

// FUSE: Convenience singleton for production use.
// Inject this via Hilt rather than using Dispatchers directly.
object ProductionDispatchers : AppDispatchers(
    main = Dispatchers.Main.immediate,
    io = Dispatchers.IO,
    default = Dispatchers.Default
)
