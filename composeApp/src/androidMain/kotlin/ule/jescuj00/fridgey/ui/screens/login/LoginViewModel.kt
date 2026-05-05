package ule.jescuj00.fridgey.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ule.jescuj00.fridgey.data.auth.SignInCancelledException
import ule.jescuj00.fridgey.domain.usecase.auth.SignInWithGoogleUseCase

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val signedInUserId: String? = null
)

class LoginViewModel(
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onGoogleSignInClicked() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = signInWithGoogleUseCase()
                _uiState.update { it.copy(isLoading = false, signedInUserId = user.uid) }
            } catch (e: SignInCancelledException) {
                _uiState.update { it.copy(isLoading = false) }   // silent
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al iniciar sesión"
                    )
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}
