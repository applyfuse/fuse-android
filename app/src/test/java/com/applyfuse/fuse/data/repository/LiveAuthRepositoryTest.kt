package com.applyfuse.fuse.data.repository

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.data.network.FakeHttpClient
import com.applyfuse.fuse.data.token.InMemoryTokenStore
import com.applyfuse.fuse.domain.model.User
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// FUSE: LiveAuthRepository is a THIN coordination layer. These
// tests assert exactly that: the right request went out and the
// right tokens were persisted — never network behaviour (that's
// LiveHttpClientTest) and never refresh (that's
// RefreshingHttpClientTest). FakeHttpClient + InMemoryTokenStore,
// no mocks, mirror of the iOS LiveAuthRepository test focus.
@DisplayName("LiveAuthRepository")
class LiveAuthRepositoryTest {

    private lateinit var http: FakeHttpClient
    private lateinit var store: InMemoryTokenStore
    private lateinit var repo: LiveAuthRepository

    @BeforeEach
    fun setUp() {
        http = FakeHttpClient()
        store = InMemoryTokenStore()
        repo = LiveAuthRepository(http, store)
    }

    @Nested
    @DisplayName("login")
    inner class LoginTests {

        @Test
        fun `posts to auth login and returns the decoded user`() = runTest {
            http.nextResponse = """
                {"user":{"id":"u1","email":"a@b.com","name":"Ann"},
                 "accessToken":"acc","refreshToken":"ref","expiresIn":3600}
            """.trimIndent()

            val user = repo.login("a@b.com", "pw")

            assertEquals(User("u1", "a@b.com", "Ann"), user)
            assertEquals("/auth/login", http.lastPath)
            assertEquals(1, http.postCallCount)
        }

        @Test
        fun `persists the returned tokens to the store`() = runTest {
            http.nextResponse = """
                {"user":{"id":"u1","email":"a@b.com","name":"Ann"},
                 "accessToken":"acc","refreshToken":"ref","expiresIn":3600}
            """.trimIndent()

            repo.login("a@b.com", "pw")

            val stored = store.read()
            assertEquals("acc", stored?.accessToken)
            assertEquals("ref", stored?.refreshToken)
            assertTrue((stored?.expiresAt ?: 0L) > 0L)
        }

        @Test
        fun `null expiresIn yields a null expiresAt`() = runTest {
            http.nextResponse = """
                {"user":{"id":"u1","email":"a@b.com","name":"Ann"},
                 "accessToken":"acc","refreshToken":"ref"}
            """.trimIndent()

            repo.login("a@b.com", "pw")

            assertNull(store.read()?.expiresAt)
        }

        @Test
        fun `propagates the AppError the transport throws`() = runTest {
            http.errorToThrow = AppError.Validation("bad creds")

            var thrown: Throwable? = null
            try {
                repo.login("a@b.com", "pw")
            } catch (e: AppError) {
                thrown = e
            }

            assertTrue(thrown is AppError.Validation)
            assertNull(store.read())
        }
    }

    @Nested
    @DisplayName("logout")
    inner class LogoutTests {

        @Test
        fun `clears the token store`() = runTest {
            http.nextResponse = """
                {"user":{"id":"u1","email":"a@b.com","name":"Ann"},
                 "accessToken":"acc","refreshToken":"ref","expiresIn":3600}
            """.trimIndent()
            repo.login("a@b.com", "pw")
            assertTrue(store.read() != null)

            repo.logout()

            assertNull(store.read())
        }

        @Test
        fun `still clears locally when the server logout fails`() = runTest {
            http.nextResponse = """
                {"user":{"id":"u1","email":"a@b.com","name":"Ann"},
                 "accessToken":"acc","refreshToken":"ref","expiresIn":3600}
            """.trimIndent()
            repo.login("a@b.com", "pw")

            // server logout 500s — must NOT prevent local clear
            http.errorToThrow = AppError.ServerError(500)
            repo.logout()

            assertNull(store.read())
        }
    }

    @Nested
    @DisplayName("currentUser")
    inner class CurrentUserTests {

        @Test
        fun `returns null without a network call when no tokens`() = runTest {
            val user = repo.currentUser()

            assertNull(user)
            assertEquals(0, http.getCallCount)
        }

        @Test
        fun `fetches the user from me when tokens exist`() = runTest {
            http.nextResponse = """
                {"user":{"id":"u1","email":"a@b.com","name":"Ann"},
                 "accessToken":"acc","refreshToken":"ref","expiresIn":3600}
            """.trimIndent()
            repo.login("a@b.com", "pw")

            http.nextResponse = """{"id":"u1","email":"a@b.com","name":"Ann"}"""
            val user = repo.currentUser()

            assertEquals(User("u1", "a@b.com", "Ann"), user)
            assertEquals("/me", http.lastPath)
        }
    }
}
