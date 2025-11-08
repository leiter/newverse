package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Products/Browse screen
 */
class ProductsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.value = ProductsUiState.Loading

            // TODO: Replace with actual repository call when Firebase is integrated
            // For now, show empty state
            _uiState.value = ProductsUiState.Success(emptyList())
        }
    }

    fun addToBasket(article: Article) {
        // TODO: Implement basket functionality
    }

    fun refresh() {
        loadProducts()
    }
}

sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data class Success(val articles: List<Article>) : ProductsUiState
    data class Error(val message: String) : ProductsUiState
}
