package com.applyfuse.fuse.data.network

import com.applyfuse.fuse.core.AppError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

// FUSE: LiveHttpClient is the ONE place HTTP reality becomes typed
// AppError. These tests run it against a real local HTTP server
// (MockWebServer) so the status -> AppError mapping is genuinely
// exercised — never assumed. This is the test the Phase 1 process
// failed to have: logic with an executing assertion behind it.
@DisplayName("LiveHttpClient transport mapping")
class LiveHttpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: LiveHttpClient

    // FUSE: identity deserializer — these tests assert on transport
    // and error mapping, not on JSON shape. Decode-failure has its
    // own dedicated test with a throwing deserializer.
    private val identity: (String) -> String = { it }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = LiveHttpClient(
            client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build(),
            baseUrl = server.url("/").toString(),
            json = Json { ignoreUnknownKeys = true }
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    // FUSE: assertion helper for suspend functions. JUnit's
    // assertThrows takes a NON-suspend lambda, so the earlier
    // `assertThrows { runTest { suspendCall } }` was wrong — a
    // nested runTest swallows/relocates the thrown exception and
    // surfaces an IllegalStateException instead. The correct idiom
    // inside an outer runTest is a direct try/catch: invoke the
    // suspend call, fail if nothing is thrown, return the caught
    // throwable for type/payload assertions. One helper, used by
    // every error test, so the pattern is correct in exactly one
    // place (FUSE: single source of truth).
    private suspend fun captureError(block: suspend () -> Unit): AppError {
        try {
            block()
        } catch (e: AppError) {
            return e
        }
        throw AssertionError("Expected an AppError to be thrown, but none was")
    }

    // MARK: — Success

    @Nested
    @DisplayName("Success")
    inner class SuccessTests {

        @Test
        fun `200 returns decoded body`() = runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
            val result = httpClient.get("/thing", identity)
            assertEquals("hello", result)
        }

        @Test
        fun `post sends body and returns decoded response`() = runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val result = httpClient.post("/thing", "{\"a\":1}", identity)
            assertEquals("ok", result)
            val recorded = server.takeRequest()
            assertEquals("{\"a\":1}", recorded.body.readUtf8())
            assertEquals("POST", recorded.method)
        }
    }

    // MARK: — Status code to AppError mapping

    @Nested
    @DisplayName("Status -> AppError")
    inner class StatusMappingTests {

        @Test
        fun `401 maps to Unauthorized`() = runTest {
            server.enqueue(MockResponse().setResponseCode(401))
            val error = captureError { httpClient.get("/x", identity) }
            assertTrue(error is AppError.Unauthorized)
        }

        @Test
        fun `403 maps to Forbidden — NOT Unauthorized`() = runTest {
            server.enqueue(MockResponse().setResponseCode(403))
            val error = captureError { httpClient.get("/x", identity) }
            assertTrue(error is AppError.Forbidden)
        }

        @Test
        fun `404 maps to NotFound`() = runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val error = captureError { httpClient.get("/x", identity) }
            assertTrue(error is AppError.NotFound)
        }

        @Test
        fun `422 maps to Validation with server message`() = runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(422)
                    .setBody("{\"message\":\"Email already taken\"}")
            )
            val error = captureError { httpClient.get("/x", identity) }
            assertTrue(error is AppError.Validation)
            assertEquals("Email already taken", (error as AppError.Validation).message)
        }

        @Test
        fun `422 with no message falls back to blank`() = runTest {
            server.enqueue(MockResponse().setResponseCode(422).setBody("{}"))
            val error = captureError { httpClient.get("/x", identity) }
            assertTrue(error is AppError.Validation)
            assertEquals("", (error as AppError.Validation).message)
        }

        @Test
        fun `other 4xx maps to ClientError with code`() = runTest {
            server.enqueue(MockResponse().setResponseCode(418))
            val error = captureError { httpClient.get("/x", identity) }
            assertTrue(error is AppError.ClientError)
            assertEquals(418, (error as AppError.ClientError).statusCode)
        }

        @Test
        fun `5xx maps to ServerError with code`() = runTest {
            server.enqueue(MockResponse().setResponseCode(503))
            val error = captureError { httpClient.get("/x", identity) }
            assertTrue(error is AppError.ServerError)
            assertEquals(503, (error as AppError.ServerError).statusCode)
        }
    }

    // MARK: — Transport failures

    @Nested
    @DisplayName("Transport failures")
    inner class TransportFailureTests {

        @Test
        fun `read timeout maps to Timeout`() = runTest {
            server.enqueue(
                MockResponse()
                    .setBody("late")
                    .setBodyDelay(5, TimeUnit.SECONDS)
            )
            val error = captureError { httpClient.get("/slow", identity) }
            assertTrue(error is AppError.Timeout)
        }

        @Test
        fun `connection failure maps to NetworkUnavailable`() = runTest {
            // FUSE: DISCONNECT_AT_START forces a transport-level
            // failure that is NOT a timeout -> NetworkUnavailable.
            server.enqueue(
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
            )
            val error = captureError { httpClient.get("/x", identity) }
            assertTrue(error is AppError.NetworkUnavailable)
        }
    }

    // MARK: — Decode failures

    @Nested
    @DisplayName("Decode failures")
    inner class DecodeFailureTests {

        @Test
        fun `deserializer throwing maps to DecodingFailed`() = runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
            val error = captureError {
                httpClient.get<String>("/x") { error("bad payload") }
            }
            assertTrue(error is AppError.DecodingFailed)
        }
    }
}
