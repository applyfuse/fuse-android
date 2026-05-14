package com.applyfuse.fuse.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// FUSE: BaseViewModel is the Android equivalent of Store<S,A> on iOS.
// It owns state, calls the reducer on every send(), and handles
// async effects after the reducer runs.
//
// Every feature ViewModel extends this class and provides:
//   - reduce()       — calls its feature reducer (pure function)
//   - handleEffect() — handles async work after state updates

abstract class BaseViewModel<S, A>(
    initialState: S
) : ViewModel() {

    // FUSE: StateFlow is the Android equivalent of @Published.
    // Compose UI collects it via collectAsStateWithLifecycle().
    // private(set) equivalent: only this class can call _state.update()
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    // FUSE: SharedFlow with extraBufferCapacity = 1 for one-time events.
    // Unlike StateFlow, SharedFlow has no initial value and does not
    // replay emissions to new collectors — perfect for navigation
    // events, toasts, and dialogs that must fire exactly once.
    private val _events = MutableSharedFlow<FuseEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // FUSE: reduce() must call the feature's reducer function.
    // It must be a pure function — no async, no side effects.
    // Example implementation:
    //   override fun reduce(state: AuthState, action: AuthAction) =
    //       authReducer(state, action)
    protected abstract fun reduce(state: S, action: A): S

    // FUSE: handleEffect() is where async work lives.
    // It runs after the reducer has already updated state.
    // Override in your feature ViewModel to handle effects.
    protected open suspend fun handleEffect(action: A, state: S) {}

    // FUSE: send() is the single entry point for all interactions.
    // Step 1 — reducer runs, state updates immediately and synchronously.
    // Step 2 — effects run asynchronously in viewModelScope.
    fun send(action: A) {
        _state.update { reduce(it, action) }
        viewModelScope.launch {
            handleEffect(action, _state.value)
        }
    }

    // FUSE: emit() sends a one-time navigation or UI event.
    // Collect in Compose via LaunchedEffect(Unit) { vm.events.collect { } }
    protected suspend fun emit(event: FuseEvent) {
        _events.emit(event)
    }
}

// FUSE: FuseEvent is the base interface for all one-time
// navigation and side-effect events. Each feature defines
// its own sealed class conforming to this interface.
// Example:
//   sealed class AuthEvent : FuseEvent {
//       object NavigateToHome : AuthEvent()
//       data class ShowError(val message: String) : AuthEvent()
//   }
interface FuseEvent
