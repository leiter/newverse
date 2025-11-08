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
            // For now, use preview data for demonstration
            kotlinx.coroutines.delay(500) // Simulate network delay

            // Import preview data when Firebase is not yet available
            try {
                val articles = com.together.newverse.preview.PreviewData.sampleArticles
                _uiState.value = ProductsUiState.Success(articles)
            } catch (e: Exception) {
                _uiState.value = ProductsUiState.Error("Failed to load products: ${e.message}")
            }
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
