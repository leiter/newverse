package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Create Product screen
 */
class CreateProductViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<CreateProductUiState>(CreateProductUiState.Idle)
    val uiState: StateFlow<CreateProductUiState> = _uiState.asStateFlow()

    private val _productName = MutableStateFlow("")
    val productName: StateFlow<String> = _productName.asStateFlow()

    private val _price = MutableStateFlow("")
    val price: StateFlow<String> = _price.asStateFlow()

    private val _unit = MutableStateFlow("")
    val unit: StateFlow<String> = _unit.asStateFlow()

    private val _category = MutableStateFlow("")
    val category: StateFlow<String> = _category.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    fun onProductNameChange(value: String) {
        _productName.value = value
    }

    fun onPriceChange(value: String) {
        _price.value = value
    }

    fun onUnitChange(value: String) {
        _unit.value = value
    }

    fun onCategoryChange(value: String) {
        _category.value = value
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
    }

    fun onAvailableChange(value: Boolean) {
        _available.value = value
    }

    fun saveProduct() {
        // Validate inputs
        if (_productName.value.isBlank()) {
            _uiState.value = CreateProductUiState.Error("Product name is required")
            return
        }

        val priceValue = _price.value.toDoubleOrNull()
        if (priceValue == null || priceValue <= 0) {
            _uiState.value = CreateProductUiState.Error("Valid price is required")
            return
        }

        if (_unit.value.isBlank()) {
            _uiState.value = CreateProductUiState.Error("Unit is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateProductUiState.Saving

            try {
                val article = Article(
                    productName = _productName.value,
                    price = priceValue,
                    unit = _unit.value,
                    category = _category.value,
                    detailInfo = _description.value,
                    available = _available.value
                )

                // TODO: Replace with actual ArticleRepository call when Firebase is integrated
                kotlinx.coroutines.delay(1000)

                _uiState.value = CreateProductUiState.Success
                clearForm()
            } catch (e: Exception) {
                _uiState.value = CreateProductUiState.Error(e.message ?: "Failed to save product")
            }
        }
    }

    private fun clearForm() {
        _productName.value = ""
        _price.value = ""
        _unit.value = ""
        _category.value = ""
        _description.value = ""
        _available.value = true
    }

    fun clearError() {
        if (_uiState.value is CreateProductUiState.Error) {
            _uiState.value = CreateProductUiState.Idle
        }
    }
}

sealed interface CreateProductUiState {
    data object Idle : CreateProductUiState
    data object Saving : CreateProductUiState
    data object Success : CreateProductUiState
    data class Error(val message: String) : CreateProductUiState
}
