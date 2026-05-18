package com.applyfuse.fuse.features.auth

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.core.BaseViewModel
import com.applyfuse.fuse.data.repository.AuthRepository
import com.applyfuse.fuse.data.repository.FakeAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseViewModel<AuthState, AuthAction>(AuthState()) {

    override fun reduce(state: AuthState, action: AuthAction): AuthState =
        authReducer(state, action)

    override suspend fun handleEffect(action: AuthAction, state: AuthState) {
        when (action) {
            is AuthAction.LoginTapped -> performLogin(state)
            is AuthAction.LogoutTapped -> performLogout()
            else -> Unit
        }
    }

    private suspend fun performLogin(state: AuthState) {
        try {
            val user = authRepository.login(
                email = state.email,
                password = state.password
            )
            send(AuthAction.LoginSuccess(user))
            emit(AuthEvent.NavigateToHome)
        } catch (e: AppError) {
            send(AuthAction.LoginFailure(e))
        } catch (e: Exception) {
            // FUSE: Map unknown exceptions to typed AppError at the ViewModel
            // boundary — never in the reducer. TooGenericExceptionCaught and
            // SwallowedException are intentionally disabled in detekt.yml.
            send(AuthAction.LoginFailure(AppError.from(e)))
        }
    }

    private suspend fun performLogout() {
        try {
            authRepository.logout()
        } catch (e: Exception) {
            // FUSE: Logout failures are non-critical — clear local state
            // regardless and navigate away. Exception intentionally caught
            // and not re-thrown (see detekt.yml SwallowedException exemption).
        }
        send(AuthAction.LogoutSuccess)
        emit(AuthEvent.NavigateToLogin)
    }
}

// FUSE: PreviewAuthViewModel for Compose @Preview — uses FakeAuthRepository
// directly without Hilt, which is unavailable in preview context.
class PreviewAuthViewModel(
    initialState: AuthState = AuthState.Empty,
    private val shouldSucceed: Boolean = true
) : BaseViewModel<AuthState, AuthAction>(initialState) {

    private val fakeRepo = FakeAuthRepository(shouldSucceed = shouldSucceed)

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
