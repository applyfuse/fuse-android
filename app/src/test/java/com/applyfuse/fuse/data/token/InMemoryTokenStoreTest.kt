package com.applyfuse.fuse.data.token

import com.applyfuse.fuse.domain.model.AuthTokens
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// FUSE: InMemoryTokenStore is pure JVM (no disk, no Keystore) so it
// is tested unconditionally and fully. This is the store the
// repository/interceptor tests will depend on, so its contract
// must be airtight.
@DisplayName("InMemoryTokenStore")
class InMemoryTokenStoreTest {

    private lateinit var store: InMemoryTokenStore

    @BeforeEach
    fun setUp() {
        store = InMemoryTokenStore()
    }

    @Nested
    @DisplayName("read")
    inner class ReadTests {

        @Test
        fun `returns null before any write`() = runTest {
            assertNull(store.read())
        }

        @Test
        fun `returns the exact tokens written`() = runTest {
            store.write(AuthTokens.mock)
            assertEquals(AuthTokens.mock, store.read())
        }

        @Test
        fun `round-trips a non-null expiresAt`() = runTest {
            val tokens = AuthTokens("a", "r", expiresAt = 1_750_000_000_000L)
            store.write(tokens)
            assertEquals(tokens, store.read())
            assertEquals(1_750_000_000_000L, store.read()?.expiresAt)
        }
    }

    @Nested
    @DisplayName("write")
    inner class WriteTests {

        @Test
        fun `overwrites previously stored tokens`() = runTest {
            store.write(AuthTokens("a1", "r1"))
            store.write(AuthTokens("a2", "r2"))
            assertEquals(AuthTokens("a2", "r2"), store.read())
        }

        @Test
        fun `overwrite with null-expiry pair drops a prior expiresAt`() = runTest {
            store.write(AuthTokens("a1", "r1", expiresAt = 999L))
            store.write(AuthTokens("a2", "r2", expiresAt = null))
            assertNull(store.read()?.expiresAt)
        }
    }

    @Nested
    @DisplayName("clear")
    inner class ClearTests {

        @Test
        fun `removes stored tokens`() = runTest {
            store.write(AuthTokens.mock)
            store.clear()
            assertNull(store.read())
        }

        @Test
        fun `is a no-op when already empty`() = runTest {
            store.clear()
            assertNull(store.read())
        }
    }
}
