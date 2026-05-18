package com.applyfuse.fuse.data.network

import com.applyfuse.fuse.core.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// FUSE: HttpClient is the transport boundary. Everything above it
// (repositories, reducers, UI) deals in typed results and AppError —
// never in HTTP status codes, OkHttp types, or JSON strings.
//
// This is where untyped network reality is converted into the typed
// domain. The status-code -> AppError mapping lives HERE and nowhere
// else, so a repository can simply `try { httpClient.get(...) }
// catch (e: AppError) { ... }` without knowing what a 422 is.
//
// Bare OkHttp, no Retrofit — the request -> response -> decode ->
// AppError path stays explicit and readable, matching FUSE's
// "clear mental model" goal. See DATA_LAYER.md decision log.
interface HttpClient {

    // FUSE: GET returning a decoded T. Throws AppError on any
    // non-2xx status, transport failure, or decode failure.
    suspend fun <T> get(path: String, deserializer: (String) -> T): T

    // FUSE: POST with a JSON body, returning a decoded T. Same
    // error contract as get().
    suspend fun <T> post(path: String, jsonBody: String, deserializer: (String) -> T): T
}

// FUSE: LiveHttpClient — the real implementation over OkHttp.
//
// The ONE job that matters here: translate every possible network
// outcome into exactly one typed AppError case. This mapping is the
// reason AppError grew Forbidden/NotFound/Validation in row 1.
//
//   401 -> Unauthorized   (the ONLY case the refresh interceptor reacts to)
//   403 -> Forbidden      (token valid, permission isn't — never refresh)
//   404 -> NotFound
//   422 -> Validation(server message)
//   4xx -> ClientError(code)
//   5xx -> ServerError(code)
//   IOException -> NetworkUnavailable / Timeout
//   decode failure -> DecodingFailed
//   cancellation -> rethrown (structured concurrency — NOT an error)
@Singleton
class LiveHttpClient @Inject constructor(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val json: Json
) : HttpClient {

    override suspend fun <T> get(path: String, deserializer: (String) -> T): T =
        execute(Request.Builder().url(baseUrl + path).get().build(), deserializer)

    override suspend fun <T> post(
        path: String,
        jsonBody: String,
        deserializer: (String) -> T
    ): T =
        execute(
            Request.Builder()
                .url(baseUrl + path)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            deserializer
        )

    // FUSE: Single funnel — every request goes through here so the
    // status -> AppError mapping exists in exactly one place.
    private suspend fun <T> execute(request: Request, deserializer: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val body = try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw response.code.toAppError(raw)
                    }
                    raw
                }
            } catch (e: CancellationException) {
                // FUSE: Coroutine cancellation is control flow, not a
                // failure. Rethrow untouched so structured-concurrency
                // teardown is never reported as a network error.
                throw e
            } catch (e: AppError) {
                throw e
            } catch (e: IOException) {
                throw e.toAppError()
            }

            try {
                deserializer(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // FUSE: Any failure turning the body into T is a
                // decode failure regardless of the concrete exception
                // (kotlinx.serialization throws SerializationException,
                // but a custom deserializer may throw anything).
                throw AppError.DecodingFailed
            }
        }

    // FUSE: HTTP status -> typed AppError. The single source of
    // truth for what each code means to the rest of the app.
    private fun Int.toAppError(rawBody: String): AppError =
        when (this) {
            UNAUTHORIZED -> AppError.Unauthorized
            FORBIDDEN -> AppError.Forbidden
            NOT_FOUND -> AppError.NotFound
            UNPROCESSABLE -> AppError.Validation(extractServerMessage(rawBody))
            in CLIENT_RANGE -> AppError.ClientError(this)
            in SERVER_RANGE -> AppError.ServerError(this)
            else -> AppError.Unknown("Unexpected HTTP status $this")
        }

    // FUSE: Transport-level failure -> AppError. OkHttp surfaces
    // timeouts as a SocketTimeoutException (an IOException subtype);
    // everything else IO-ish is treated as offline.
    private fun IOException.toAppError(): AppError =
        if (this is java.net.SocketTimeoutException) {
            AppError.Timeout
        } else {
            AppError.NetworkUnavailable
        }

    // FUSE: 422 bodies carry a server-authored message. Pull it from
    // the standard { "message": "..." } envelope; fall back to empty
    // so AppError.Validation.userMessage uses its generic line.
    private fun extractServerMessage(rawBody: String): String =
        try {
            json.parseToJsonElement(rawBody)
                .let { it as? kotlinx.serialization.json.JsonObject }
                ?.get("message")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content
                .orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }

    private companion object {
        // FUSE: OkHttp 4 idiom — String.toMediaType() extension, NOT
        // the deprecated OkHttp 3 MediaType.parse() static. (The
        // project compiles deprecation as error, so .parse() fails
        // the build — fixed here.)
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val UNPROCESSABLE = 422
        val CLIENT_RANGE = 400..499
        val SERVER_RANGE = 500..599
    }
}

// FUSE: FakeHttpClient — for repository/feature tests that must NOT
// touch the network. Mirrors FakeAuthRepository: deterministic,
// call-tracked, and able to simulate any AppError so a repository's
// error handling can be tested without a server.
//
// LiveHttpClient itself is tested separately against MockWebServer
// (LiveHttpClientTest) — that is where the real status -> AppError
// mapping is verified.
class FakeHttpClient : HttpClient {

    var nextResponse: String = "{}"
    var errorToThrow: AppError? = null

    var getCallCount: Int = 0
        private set
    var postCallCount: Int = 0
        private set
    var lastPath: String? = null
        private set
    var lastBody: String? = null
        private set

    override suspend fun <T> get(path: String, deserializer: (String) -> T): T {
        getCallCount++
        lastPath = path
        errorToThrow?.let { throw it }
        return deserializer(nextResponse)
    }

    override suspend fun <T> post(
        path: String,
        jsonBody: String,
        deserializer: (String) -> T
    ): T {
        postCallCount++
        lastPath = path
        lastBody = jsonBody
        errorToThrow?.let { throw it }
        return deserializer(nextResponse)
    }
}
