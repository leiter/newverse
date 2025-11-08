package com.together.newverse.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Actions that can be performed on the Products screen
 */
sealed interface ProductsAction {
    data class AddToBasket(val article: Article) : ProductsAction
    data object Refresh : ProductsAction
}

/**
 * State for the Products screen
 */
data class ProductsScreenState(
    val isLoading: Boolean = false,
    val articles: List<Article> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for Products/Browse screen
 */
class ProductsViewModel : ViewModel() {

    private val _state = MutableStateFlow(ProductsScreenState(isLoading = true))
    val state: StateFlow<ProductsScreenState> = _state.asStateFlow()

    init {
        loadProducts()
    }

    fun onAction(action: ProductsAction) {
        when (action) {
            is ProductsAction.AddToBasket -> addToBasket(action.article)
            ProductsAction.Refresh -> refresh()
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // TODO: Replace with actual repository call when Firebase is integrated
            // For now, use preview data for demonstration
            kotlinx.coroutines.delay(500) // Simulate network delay

            try {
                val articles = com.together.newverse.preview.PreviewData.sampleArticles
                _state.value = _state.value.copy(
                    isLoading = false,
                    articles = articles,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load products: ${e.message}"
                )
            }
        }
    }

    private fun addToBasket(article: Article) {
        // TODO: Implement basket functionality
    }

    private fun refresh() {
        loadProducts()
    }
}
