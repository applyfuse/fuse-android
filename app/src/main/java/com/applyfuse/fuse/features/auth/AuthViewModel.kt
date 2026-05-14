package com.applyfuse.fuse.features.auth

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.core.BaseViewModel
import com.applyfuse.fuse.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// FUSE: AuthViewModel is the Android ViewModel for the Auth feature.
// It extends BaseViewModel<AuthState, AuthAction> and provides:
//   - reduce()       — calls the pure authReducer function
//   - handleEffect() — handles async work after state updates
//
// @HiltViewModel tells Hilt to manage this ViewModel's lifecycle.
// @Inject tells Hilt to provide AuthRepository via RepositoryModule.
//
// The Composable screen never creates this directly —
// it uses hiltViewModel() which Hilt manages.

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseViewModel<AuthState, AuthAction>(AuthState()) {

    // FUSE: reduce() is the bridge between BaseViewModel and
    // the feature's pure reducer function. It must not contain
    // any logic — it just delegates to authReducer().
    override fun reduce(state: AuthState, action: AuthAction): AuthState =
        authReducer(state, action)

    // FUSE: handleEffect() runs AFTER the reducer has updated state.
    // It reads the updated state if needed, calls repositories,
    // and sends result actions back through send().
    //
    // Most actions have no effect — only actions that
    // trigger async work need a branch here.
    override suspend fun handleEffect(action: AuthAction, state: AuthState) {
        when (action) {
            is AuthAction.LoginTapped  -> performLogin(state)
            is AuthAction.LogoutTapped -> performLogout()
            else                       -> Unit
        }
    }

    // FUSE: performLogin reads email and password from the
    // state snapshot passed by BaseViewModel after the reducer ran.
    // We use the snapshot rather than reading state.value directly
    // to avoid any race condition with concurrent sends.
    private suspend fun performLogin(state: AuthState) {
        try {
            val user = authRepository.login(
                email = state.email,
                password = state.password
            )
            // FUSE: Send the result action back through the reducer.
            // The reducer sets isLoading = false and stores the user.
            send(AuthAction.LoginSuccess(user))

            // FUSE: Navigate AFTER state is updated.
            // SharedFlow fires once — never replays to new collectors.
            emit(AuthEvent.NavigateToHome)

        } catch (e: AppError) {
            send(AuthAction.LoginFailure(e))

        } catch (e: Exception) {
            // FUSE: Map unknown exceptions to typed AppError
            // at the ViewModel boundary — never in the reducer.
            send(AuthAction.LoginFailure(AppError.from(e)))
        }
    }

    private suspend fun performLogout() {
        try {
            authRepository.logout()
            send(AuthAction.LogoutSuccess)
            emit(AuthEvent.NavigateToLogin)
        } catch (e: Exception) {
            // FUSE: Logout failures are non-critical — clear
            // local state regardless and navigate away.
            send(AuthAction.LogoutSuccess)
            emit(AuthEvent.NavigateToLogin)
        }
    }
}

// MARK: — Preview ViewModel

// FUSE: PreviewAuthViewModel is a standalone ViewModel for
// Compose @Preview. It uses FakeAuthRepository directly
// without Hilt — Hilt is not available in preview context.
//
// Pass a pre-built state snapshot to show any UI state:
// PreviewAuthViewModel(initialState = AuthState.Loading)

class PreviewAuthViewModel(
    initialState: AuthState = AuthState.Empty,
    private val shouldSucceed: Boolean = true
) : BaseViewModel<AuthState, AuthAction>(initialState) {

    private val fakeRepo = com.applyfuse.fuse.data.repository.FakeAuthRepository(
        shouldSucceed = shouldSucceed
    )

    override fun reduce(state: AuthState, action: AuthAction): AuthState =
        authReducer(state, action)

    override suspend fun handleEffect(action: AuthAction, state: AuthState) {
        when (action) {
            is AuthAction.LoginTapped -> {
                try {
                    val user = fakeRepo.login(state.email, state.password)
                    send(AuthAction.LoginSuccess(user))
                    emit(AuthEvent.NavigateToHome)
                } catch (e: AppError) {
                    send(AuthAction.LoginFailure(e))
                } catch (e: Exception) {
                    send(AuthAction.LoginFailure(AppError.from(e)))
                }
            }
            else -> Unit
        }
    }
}
