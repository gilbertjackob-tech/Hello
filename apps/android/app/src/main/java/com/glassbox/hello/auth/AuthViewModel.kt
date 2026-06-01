package com.glassbox.hello.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glassbox.hello.core.ResultState
import com.glassbox.hello.core.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _authState = MutableStateFlow<ResultState<User?>>(ResultState.Success(null))
    val authState: StateFlow<ResultState<User?>> = _authState

    private val _securityQuestion = MutableStateFlow<String?>(null)
    val securityQuestion: StateFlow<String?> = _securityQuestion

    private val _questionState = MutableStateFlow<ResultState<String?>>(ResultState.Success(null))
    val questionState: StateFlow<ResultState<String?>> = _questionState

    fun register(name: String, securityQuestion: String, securityAnswer: String) {
        _authState.value = ResultState.Loading
        viewModelScope.launch {
            val result = repository.register(name, securityQuestion, securityAnswer)
            _authState.value = when {
                result.isSuccess -> ResultState.Success(result.getOrNull())
                result.isFailure -> ResultState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun getUserQuestion(name: String) {
        _questionState.value = ResultState.Loading
        _securityQuestion.value = null
        viewModelScope.launch {
            val result = repository.getUserQuestion(name)
            _questionState.value = when {
                result.isSuccess -> {
                    val question = result.getOrNull()
                    _securityQuestion.value = question
                    ResultState.Success(question)
                }
                result.isFailure -> ResultState.Error(result.exceptionOrNull()?.message ?: "User not found")
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun login(name: String, securityAnswer: String) {
        _authState.value = ResultState.Loading
        viewModelScope.launch {
            val result = repository.login(name, securityAnswer)
            _authState.value = when {
                result.isSuccess -> ResultState.Success(result.getOrNull())
                result.isFailure -> ResultState.Error(result.exceptionOrNull()?.message ?: "Login failed")
                else -> ResultState.Error("Unknown error")
            }
        }
    }

    fun resetState() {
        _authState.value = ResultState.Success(null)
        _securityQuestion.value = null
        _questionState.value = ResultState.Success(null)
    }

    fun resetAuthState() {
        _authState.value = ResultState.Success(null)
    }

    fun resetQuestionState() {
        _securityQuestion.value = null
        _questionState.value = ResultState.Success(null)
    }
}
