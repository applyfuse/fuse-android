package com.applyfuse.fuse.core

import kotlinx.coroutines.CancellationException

// FUSE: AppError is the single typed error model for the
// entire app. Every network or data error is mapped to one
// of these cases at the repository boundary — never inside
// a reducer or screen composable.
//
// Mapping errors early means:
// - Reducers only deal with typed domain errors
// - UI can show meaningful messages without nested when chains
// - Tests assert on typed cases, not string messages

sealed class AppError : Exception() {

    object NetworkUnavailable : AppError()

    // FUSE: The server returned 401 — token expired or invalid.
    // This is the ONLY case the refresh interceptor reacts to.
    // A 403 (Forbidden) must NOT trigger a refresh — see below.
    object Unauthorized : AppError()

    // FUSE: The server returned 403 — the caller is authenticated
    // but not permitted to perform this action. Deliberately a
    // separate case from Unauthorized: refreshing the token would
    // not help (the token is valid, the permission isn't), so the
    // RefreshingHttpClient keys off the TYPE not the status code.
    object Forbidden : AppError()

    // FUSE: The server returned 404 — the resource does not exist.
    // Its own case so a feature can render an empty/"not found"
    // state instead of a generic client-error banner.
    object NotFound : AppError()

    // FUSE: The server returned a 4xx client error.
    data class ClientError(val statusCode: Int) : AppError()

    data class ServerError(val statusCode: Int) : AppError()

    // FUSE: The server returned 422 — semantic validation failure.
    // This is the ONE error whose user-facing text comes from the
    // server's response envelope, not from AppError. message is the
    // server-supplied string; userMessage falls back to a generic
    // line when the server sends nothing usable.
    //
    // FUSE: override is required — Throwable.message already exists
    // on the supertype. Without it Kotlin 2.x promotes the hiding
    // warning to a compile error. Same reason as Unknown below.
    data class Validation(override val message: String) : AppError()

    // FUSE: The response body could not be decoded.
    object DecodingFailed : AppError()

    object Timeout : AppError()

    // FUSE: The in-flight request was cancelled (e.g. the screen
    // left composition, viewModelScope was torn down). This is
    // NORMAL structured-concurrency control flow, not a failure —
    // callers should swallow it silently and never show a banner.
    // Kept distinct from Unknown precisely so it can be filtered.
    object Cancelled : AppError()

    // FUSE: override is required — Throwable.message already exists on the
    // supertype. Without it Kotlin 2.x promotes the hiding warning to an error.
    data class Unknown(override val message: String = "") : AppError()

    // FUSE: Human-readable message shown in the UI.
    // All wording lives here — never in the reducer or screen.
    val userMessage: String
        get() = when (this) {
            is NetworkUnavailable ->
                "No internet connection. Please check your network and try again."
            is Unauthorized ->
                "Your session has expired. Please sign in again."
            is Forbidden ->
                "You don't have permission to do that."
            is NotFound ->
                "We couldn't find what you were looking for."
            is ClientError ->
                "Something went wrong ($statusCode). Please try again."
            is ServerError ->
                "Server error ($statusCode). Please try again later."
            is Validation ->
                message.ifBlank { "Please check your input and try again." }
            is DecodingFailed ->
                "Received an unexpected response. Please update the app."
            is Timeout ->
                "The request timed out. Please try again."
            is Cancelled ->
                // FUSE: Should never reach the UI — callers filter
                // Cancelled before it becomes a visible error. A
                // sensible string exists only as a last resort.
                "The request was cancelled."
            is Unknown ->
                message.ifEmpty { "An unexpected error occurred." }
        }

    companion object {
        // FUSE: Map any Throwable to a typed AppError at the
        // repository boundary.
        // Usage: } catch (e: Exception) { throw AppError.from(e) }
        //
        // FUSE: Single return (ReturnCount = 2 limit). The ordering
        // is deliberate and preserved by the when-arm order:
        //   1. An AppError passes through unchanged.
        //   2. Coroutine cancellation is control flow, not a
        //      failure — mapped to Cancelled BEFORE the message
        //      heuristics so structured-concurrency teardown never
        //      surfaces as a user-visible error.
        //   3. Otherwise classify by message heuristics.
        fun from(throwable: Throwable): AppError =
            when {
                throwable is AppError ->
                    throwable
                throwable is CancellationException ->
                    Cancelled
                throwable.message?.contains("Unable to resolve host") == true ->
                    NetworkUnavailable
                throwable.message?.contains("timeout") == true ->
                    Timeout
                else ->
                    Unknown(throwable.message ?: "")
            }
    }
}
