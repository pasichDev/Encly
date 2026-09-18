package com.pasich.encly.presentation.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasich.encly.core.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AuthSetupViewModel @Inject constructor(
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun biometricAvailable(): Boolean = securityManager.biometricAvailable()

    fun setPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.Default) { securityManager.configurePin(pin) }
            _busy.value = false
            onResult(ok)
        }
    }

    fun enableBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        securityManager.enrollBiometric(activity, onResult)
    }

    fun finishSetup(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.IO) { securityManager.finishInitialSetup() }
            _busy.value = false
            onResult(ok)
        }
    }
}
