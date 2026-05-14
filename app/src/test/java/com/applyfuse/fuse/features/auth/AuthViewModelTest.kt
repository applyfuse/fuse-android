package com.applyfuse.fuse.features.auth

import com.applyfuse.fuse.core.AppError
import com.applyfuse.fuse.core.FuseEvent
import com.applyfuse.fuse.data.repository.FakeAuthRepository
import com.applyfuse.fuse.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// FUSE: ViewModel integration tests verify the full cycle:
// send(action) → reducer → state update → effect → result action
//
// We cannot instantiate @HiltViewModel directly in unit tests,
// so we use TestAuthViewModel — a thin wrapper around BaseViewModel
// that accepts FakeAuthRepository without Hilt.
//
// runTest + advanceUntilIdle() replaces real coroutine timing,
// making tests instant and deterministic.

// FUSE: TestAuthViewModel mirrors AuthViewModel exactly but
// accepts a FakeAuthRepository directly — no Hilt needed.
private class TestAuthViewModel(
    private val repo: FakeAuthRepository
) : com.applyfuse.fuse.core.BaseViewModel<AuthState, AuthAction>(AuthState()) {

    override fun reduce(state: AuthState, action: AuthAction): AuthState =
        authReducer(state, action)

    override suspend fun handleEffect(action: AuthAction, state: AuthState) {
        when (action) {
            is AuthAction.LoginTapped -> {
                try {
                    val user = repo.login(state.email, state.password)
                    send(AuthAction.LoginSuccess(user))
                    emit(AuthEvent.NavigateToHome)
                } catch (e: AppError) {
                    send(AuthAction.LoginFailure(e))
                    emit(AuthEvent.ShowToast(e.userMessage))
                } catch (e: Exception) {
                    send(AuthAction.LoginFailure(AppError.from(e)))
                }
            }
            is AuthAction.LogoutTapped -> {
                try { repo.logout() } catch (_: Exception) {}
                send(AuthAction.LogoutSuccess)
                emit(AuthEvent.NavigateToLogin)
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("AuthViewModel integration")
class AuthViewModelTest {

    // MARK: — Initial state

    @Test
    fun `initial state is empty`() {
        val vm = TestAuthViewModel(FakeAuthRepository())
        assertEquals(AuthState(), vm.state.value)
        assertFalse(vm.state.value.isLoggedIn)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
    }

    // MARK: — Form input

    @Nested
    @DisplayName("Form input")
    inner class FormInputTests {

        @Test
        fun `email changed updates state immediately`() {
            val vm = TestAuthViewModel(FakeAuthRepository())
            vm.send(AuthAction.EmailChanged("a@b.com"))
            // FUSE: Reducer runs synchronously — state updates
            // before the next line.
            assertEquals("a@b.com", vm.state.value.email)
        }

        @Test
        fun `password changed updates state immediately`() {
            val vm = TestAuthViewModel(FakeAuthRepository())
            vm.send(AuthAction.PasswordChanged("secret"))
            assertEquals("secret", vm.state.value.password)
        }

        @Test
        fun `canSubmit true when both fields filled`() {
            val vm = TestAuthViewModel(FakeAuthRepository())
            assertFalse(vm.state.value.canSubmit)
            vm.send(AuthAction.EmailChanged("a@b.com"))
            vm.send(AuthAction.PasswordChanged("pass"))
            assertTrue(vm.state.value.canSubmit)
        }
    }

    // MARK: — Login success flow

    @Nested
    @DisplayName("Login success flow")
    inner class LoginSuccessTests {

        @Test
        fun `loginTapped sets isLoading immediately`() {
            // FUSE: isLoading must be true BEFORE the coroutine starts.
            // Confirms the reducer ran synchronously inside send().
            val vm = makeVM()
            vm.send(AuthAction.LoginTapped)
            assertTrue(vm.state.value.isLoading)
        }

        @Test
        fun `loginTapped success sets user in state`() = runTest {
            val vm = makeVM()
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertTrue(vm.state.value.isLoggedIn)
            assertEquals(User.mock, vm.state.value.user)
        }

        @Test
        fun `loginTapped success clears loading`() = runTest {
            val vm = makeVM()
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertFalse(vm.state.value.isLoading)
        }

        @Test
        fun `loginTapped success clears error`() = runTest {
            val fake = FakeAuthRepository(shouldSucceed = true)
            val vm = TestAuthViewModel(fake).also {
                it.send(AuthAction.EmailChanged("a@b.com"))
                it.send(AuthAction.PasswordChanged("pass"))
            }
            // Manually put an error in state via the reducer
            vm.send(AuthAction.LoginFailure(AppError.Unauthorized))
            assertNotNull(vm.state.value.errorMessage)

            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertNull(vm.state.value.errorMessage)
        }

        @Test
        fun `loginTapped success emits NavigateToHome event`() = runTest {
            val vm = makeVM()
            val events = mutableListOf<FuseEvent>()

            // FUSE: Collect SharedFlow events before triggering send.
            // SharedFlow doesn't replay — must subscribe first.
            val job = launch { vm.events.collect { events.add(it) } }

            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()

            assertTrue(events.any { it is AuthEvent.NavigateToHome })
            job.cancel()
        }
    }

    // MARK: — Login failure flow

    @Nested
    @DisplayName("Login failure flow")
    inner class LoginFailureTests {

        @Test
        fun `loginTapped failure sets error message`() = runTest {
            val vm = makeVM(shouldSucceed = false)
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertNotNull(vm.state.value.errorMessage)
            assertFalse(vm.state.value.isLoading)
        }

        @Test
        fun `loginTapped failure does not set user`() = runTest {
            val vm = makeVM(shouldSucceed = false)
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertNull(vm.state.value.user)
            assertFalse(vm.state.value.isLoggedIn)
        }

        @Test
        fun `loginTapped failure emits ShowToast event`() = runTest {
            val vm = makeVM(shouldSucceed = false)
            val events = mutableListOf<FuseEvent>()
            val job = launch { vm.events.collect { events.add(it) } }

            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()

            assertTrue(events.any { it is AuthEvent.ShowToast })
            job.cancel()
        }

        @Test
        fun `loginTapped failure then retry clears error`() = runTest {
            val fake = FakeAuthRepository(shouldSucceed = false)
            val vm = TestAuthViewModel(fake).also {
                it.send(AuthAction.EmailChanged("a@b.com"))
                it.send(AuthAction.PasswordChanged("pass"))
            }

            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertNotNull(vm.state.value.errorMessage)

            // Fix the repo and retry
            fake.shouldSucceed = true
            vm.send(AuthAction.LoginTapped)

            // FUSE: Reducer runs synchronously — error cleared before coroutine starts.
            assertNull(vm.state.value.errorMessage)
            assertTrue(vm.state.value.isLoading)

            advanceUntilIdle()
            assertTrue(vm.state.value.isLoggedIn)
        }
    }

    // MARK: — Error dismissal

    @Nested
    @DisplayName("Error dismissal")
    inner class ErrorDismissalTests {

        @Test
        fun `errorDismissed clears error without affecting other state`() = runTest {
            val vm = makeVM(shouldSucceed = false)
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertNotNull(vm.state.value.errorMessage)

            vm.send(AuthAction.ErrorDismissed)
            assertNull(vm.state.value.errorMessage)
            assertEquals("a@b.com", vm.state.value.email)
        }
    }

    // MARK: — Logout flow

    @Nested
    @DisplayName("Logout flow")
    inner class LogoutTests {

        @Test
        fun `logout resets state to default`() = runTest {
            val vm = makeVM()
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertTrue(vm.state.value.isLoggedIn)

            vm.send(AuthAction.LogoutTapped)
            advanceUntilIdle()
            assertEquals(AuthState(), vm.state.value)
        }

        @Test
        fun `logout emits NavigateToLogin event`() = runTest {
            val vm = makeVM()
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()

            val events = mutableListOf<FuseEvent>()
            val job = launch { vm.events.collect { events.add(it) } }

            vm.send(AuthAction.LogoutTapped)
            advanceUntilIdle()

            assertTrue(events.any { it is AuthEvent.NavigateToLogin })
            job.cancel()
        }
    }

    // MARK: — canSubmit blocks during loading

    @Nested
    @DisplayName("canSubmit")
    inner class CanSubmitTests {

        @Test
        fun `canSubmit is false while loading`() {
            // FUSE: The button must be disabled while a request
            // is in flight to prevent double-submission.
            val vm = makeVM()
            assertTrue(vm.state.value.canSubmit)
            vm.send(AuthAction.LoginTapped)
            assertFalse(vm.state.value.canSubmit)
        }
    }

    // MARK: — Repository call tracking

    @Nested
    @DisplayName("Repository tracking")
    inner class RepositoryTrackingTests {

        @Test
        fun `loginTapped calls repository exactly once`() = runTest {
            val fake = FakeAuthRepository(shouldSucceed = true)
            val vm = TestAuthViewModel(fake).also {
                it.send(AuthAction.EmailChanged("a@b.com"))
                it.send(AuthAction.PasswordChanged("pass"))
            }
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertEquals(1, fake.loginCallCount)
        }

        @Test
        fun `loginTapped passes correct email to repository`() = runTest {
            val fake = FakeAuthRepository(shouldSucceed = true)
            val vm = TestAuthViewModel(fake).also {
                it.send(AuthAction.EmailChanged("specific@test.com"))
                it.send(AuthAction.PasswordChanged("pass"))
            }
            vm.send(AuthAction.LoginTapped)
            advanceUntilIdle()
            assertEquals("specific@test.com", fake.lastLoginEmail)
        }
    }

    // MARK: — Helpers

    private fun makeVM(shouldSucceed: Boolean = true): TestAuthViewModel {
        val fake = FakeAuthRepository(shouldSucceed = shouldSucceed)
        return TestAuthViewModel(fake).also {
            it.send(AuthAction.EmailChanged("a@b.com"))
            it.send(AuthAction.PasswordChanged("password123"))
        }
    }
}
