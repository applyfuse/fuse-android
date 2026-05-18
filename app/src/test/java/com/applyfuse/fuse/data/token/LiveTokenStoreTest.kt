package com.applyfuse.fuse.data.token

import android.content.Context
import com.applyfuse.fuse.domain.model.AuthTokens
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

// FUSE: probe-and-skip — the Android analogue of iOS XCTSkip
// (DATA_LAYER.md's stated, user-confirmed strategy).
//
// LiveTokenStore depends on EncryptedSharedPreferences + the
// Android Keystore, neither of which exists in a plain JVM unit
// test (ci-local.sh). @BeforeEach attempts REAL construction and
// forces the lazy EncryptedSharedPreferences to initialise; if the
// Android runtime is absent (always, in the JVM run) JUnit5
// assumeTrue SKIPS these tests rather than failing them. On an
// instrumented/emulator run the SAME tests execute the real
// AES-256 encryption round-trip.
//
// COVERAGE CAVEAT: this means LiveTokenStore's encryption path is
// NOT exercised by ci-local.sh. That gap is deliberate, contract-
// consistent, and recorded explicitly in the DATA_LAYER.md decision
// log so it is visible, not silent.
@DisplayName("LiveTokenStore (probe-and-skip; needs an Android runtime)")
class LiveTokenStoreTest {

    private var store: LiveTokenStore? = null

    @BeforeEach
    fun setUp() {
        val built: LiveTokenStore? = try {
            val context = mockk<Context>(relaxed = true)
            val candidate = LiveTokenStore(context)
            // Force the lazy EncryptedSharedPreferences (and thus the
            // MasterKey -> Android Keystore) to initialise. This is
            // the call that fails in a plain JVM environment.
            runBlocking { candidate.read() }
            candidate
        } catch (t: Throwable) {
            null
        }
        assumeTrue(
            built != null,
            "Android Keystore/EncryptedSharedPreferences unavailable in JVM " +
                "— skipping LiveTokenStore tests (run on an emulator/device)"
        )
        store = built
    }

    @Test
    fun `write then read round-trips through EncryptedSharedPreferences`() = runTest {
        val s = requireNotNull(store)
        s.write(AuthTokens.mock)
        assertEquals(AuthTokens.mock, s.read())
    }

    @Test
    fun `clear removes persisted tokens`() = runTest {
        val s = requireNotNull(store)
        s.write(AuthTokens.mock)
        s.clear()
        assertNull(s.read())
    }
}
