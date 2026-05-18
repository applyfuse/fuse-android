package com.applyfuse.fuse.core

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

    object Unauthorized : AppError()

    data class ClientError(val statusCode: Int) : AppError()

    data class ServerError(val statusCode: Int) : AppError()

    object DecodingFailed : AppError()

    object Timeout : AppError()

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
            is ClientError ->
                "Something went wrong ($statusCode). Please try again."
            is ServerError ->
                "Server error ($statusCode). Please try again later."
            is DecodingFailed ->
                "Received an unexpected response. Please update the app."
            is Timeout ->
                "The request timed out. Please try again."
            is Unknown ->
                message.ifEmpty { "An unexpected error occurred." }
        }

    companion object {
        // FUSE: Map any Throwable to a typed AppError at the repository boundary.
        fun from(throwable: Throwable): AppError {
            if (throwable is AppError) return throwable
            return when {
                throwable.message?.contains("Unable to resolve host") == true ->
                    NetworkUnavailable
                throwable.message?.contains("timeout") == true ->
                    Timeout
                else ->
                    Unknown(throwable.message ?: "")
            }
        }
    }
}
