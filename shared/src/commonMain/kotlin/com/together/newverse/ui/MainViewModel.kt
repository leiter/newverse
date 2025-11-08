package com.together.newverse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.GreetingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val greetingRepository: GreetingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadGreeting()
    }

    private fun loadGreeting() {
        viewModelScope.launch {
            val greeting = greetingRepository.getGreeting()
            val platform = greetingRepository.getPlatformGreeting()
            _uiState.value = MainUiState(
                greeting = greeting,
                platform = platform
            )
        }
    }

    fun onRefresh() {
        loadGreeting()
    }
}

data class MainUiState(
    val greeting: String = "",
    val platform: String = "",
    val isLoading: Boolean = false
)
