package com.example.persona.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.PersonaRepository
import com.example.persona.data.network.NetworkClient
import com.example.persona.data.network.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: PersonaRepository, private val context: Context) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl

    init {
        _baseUrl.value = NetworkClient.getBaseUrl(context)
        checkSession()
    }

    fun checkSession() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            try {
                val response = repository.getCurrentUser()
                if (response.isSuccessful && response.body() != null) {
                    _currentUser.value = response.body()
                    _isLoggedIn.value = true
                } else {
                    _isLoggedIn.value = false
                    _currentUser.value = null
                }
            } catch (e: Exception) {
                _isLoggedIn.value = false
                _currentUser.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            try {
                val response = repository.login(email, password)
                if (response.isSuccessful) {
                    _errorMsg.value = null
                    checkSession()
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    _errorMsg.value = parseError(errorBody) ?: "Invalid email or password"
                }
            } catch (e: Exception) {
                _errorMsg.value = e.localizedMessage ?: "Connection error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(name: String, email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null
            try {
                val response = repository.register(name, email, password)
                if (response.isSuccessful) {
                    _errorMsg.value = null
                    checkSession()
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    _errorMsg.value = parseError(errorBody) ?: "Registration failed"
                }
            } catch (e: Exception) {
                _errorMsg.value = e.localizedMessage ?: "Connection error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (e: Exception) {
                // Ignore
            } finally {
                NetworkClient.logout()
                _isLoggedIn.value = false
                _currentUser.value = null
                onSuccess()
            }
        }
    }

    fun updateBaseUrl(newUrl: String) {
        NetworkClient.setBaseUrl(context, newUrl)
        _baseUrl.value = newUrl
        checkSession() // check session on new server url
    }

    private fun parseError(json: String?): String? {
        if (json == null) return null
        return try {
            val o = com.google.gson.JsonParser.parseString(json).asJsonObject
            if (o.has("error")) o.get("error").asString else null
        } catch (e: Exception) {
            null
        }
    }
}
