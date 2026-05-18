package com.applyfuse.fuse.data.token

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.applyfuse.fuse.domain.model.AuthTokens
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// FUSE: TokenStore is the persistence boundary for the auth session.
// Everything above it (AuthRepository, the refresh interceptor)
// reads/writes AuthTokens without knowing whether they live in
// memory or in EncryptedSharedPreferences.
//
// Single shared instance in the Hilt graph: the bare HttpClient
// reads it for the Authorization header, the refresh interceptor
// writes new tokens after a refresh, the auth repo writes on login
// and clears on logout.
interface TokenStore {

    // FUSE: null when there is no session (logged out / first run).
    suspend fun read(): AuthTokens?

    suspend fun write(tokens: AuthTokens)

    suspend fun clear()
}

// FUSE: InMemoryTokenStore — for tests and Compose previews. No
// disk, no Keystore, fully deterministic. Repository/interceptor
// tests use this; LiveTokenStore is exercised separately (and only
// on an Android runtime — see LiveTokenStoreTest).
class InMemoryTokenStore : TokenStore {

    private var tokens: AuthTokens? = null

    override suspend fun read(): AuthTokens? = tokens

    override suspend fun write(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}

// FUSE: LiveTokenStore — real persistence via
// EncryptedSharedPreferences (AES-256, key in the Android Keystore).
//
// All disk I/O is moved to Dispatchers.IO. The EncryptedSharedPrefs
// instance is created lazily: building the MasterKey touches the
// Android Keystore, which only exists on a real Android runtime —
// so construction is cheap and the Keystore work is deferred to
// first use (this is also what makes the probe-and-skip test
// strategy work; see LiveTokenStoreTest).
@Singleton
class LiveTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) : TokenStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun read(): AuthTokens? = withContext(Dispatchers.IO) {
        val access = prefs.getString(KEY_ACCESS, null)
        val refresh = prefs.getString(KEY_REFRESH, null)
        // FUSE: a session is valid only if BOTH tokens are present;
        // a half-written pair is treated as no session.
        if (access != null && refresh != null) {
            AuthTokens(access, refresh)
        } else {
            null
        }
    }

    override suspend fun write(tokens: AuthTokens) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "fuse_secure_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
