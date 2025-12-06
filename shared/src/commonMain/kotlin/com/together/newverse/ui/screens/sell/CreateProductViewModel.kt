package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductUnit
import com.together.newverse.data.repository.GitLiveArticleRepository
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Create Product screen
 * Handles product creation with image upload
 */
class CreateProductViewModel(
    private val articleRepository: ArticleRepository,
    private val authRepository: AuthRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<CreateProductUiState>(CreateProductUiState.Idle)
    val uiState: StateFlow<CreateProductUiState> = _uiState.asStateFlow()

    // Form Fields
    private val _productName = MutableStateFlow("")
    val productName: StateFlow<String> = _productName.asStateFlow()

    private val _productId = MutableStateFlow("")
    val productId: StateFlow<String> = _productId.asStateFlow()

    private val _searchTerms = MutableStateFlow("")
    val searchTerms: StateFlow<String> = _searchTerms.asStateFlow()

    private val _price = MutableStateFlow("")
    val price: StateFlow<String> = _price.asStateFlow()

    private val _unit = MutableStateFlow(ProductUnit.KG.displayName)
    val unit: StateFlow<String> = _unit.asStateFlow()

    private val _category = MutableStateFlow(ProductCategory.GEMUESE.displayName)
    val category: StateFlow<String> = _category.asStateFlow()

    private val _weightPerPiece = MutableStateFlow("")
    val weightPerPiece: StateFlow<String> = _weightPerPiece.asStateFlow()

    private val _detailInfo = MutableStateFlow("")
    val detailInfo: StateFlow<String> = _detailInfo.asStateFlow()

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    // Image handling
    private val _imageData = MutableStateFlow<ByteArray?>(null)
    val imageData: StateFlow<ByteArray?> = _imageData.asStateFlow()

    private val _imageUrl = MutableStateFlow("")
    val imageUrl: StateFlow<String> = _imageUrl.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    // Form update functions
    fun onProductNameChange(value: String) {
        _productName.value = value
        clearError()
    }

    fun onProductIdChange(value: String) {
        _productId.value = value
    }

    fun onSearchTermsChange(value: String) {
        _searchTerms.value = value
    }

    fun onPriceChange(value: String) {
        _price.value = value
        clearError()
    }

    fun onUnitChange(value: String) {
        _unit.value = value
        clearError()
    }

    fun onCategoryChange(value: String) {
        _category.value = value
        clearError()
    }

    fun onWeightPerPieceChange(value: String) {
        _weightPerPiece.value = value
    }

    fun onDetailInfoChange(value: String) {
        _detailInfo.value = value
    }

    fun onAvailableChange(value: Boolean) {
        _available.value = value
    }

    fun onImageSelected(imageData: ByteArray) {
        _imageData.value = imageData
        clearError()
    }

    /**
     * Validate and save the product
     * Uploads image first (if provided), then saves product to database
     */
    fun saveProduct() {
        // Validate inputs
        val validationError = validateForm()
        if (validationError != null) {
            _uiState.value = CreateProductUiState.Error(validationError)
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateProductUiState.Saving

            try {
                // Verify user is authenticated
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    _uiState.value = CreateProductUiState.Error("User not authenticated")
                    return@launch
                }

                // Use DEFAULT_SELLER_ID for consistent article storage
                // This ensures buyer app can see seller's articles via live updates
                val sellerId = GitLiveArticleRepository.DEFAULT_SELLER_ID

                // Upload image if new image data exists
                var finalImageUrl = _imageUrl.value
                if (_imageData.value != null) {
                    _uploadProgress.value = 0f
                    val uploadResult = storageRepository.uploadImage(
                        imageData = _imageData.value!!,
                        onProgress = { progress ->
                            _uploadProgress.value = progress
                        }
                    )

                    if (uploadResult.isFailure) {
                        _uiState.value = CreateProductUiState.Error(
                            "Failed to upload image: ${uploadResult.exceptionOrNull()?.message}"
                        )
                        return@launch
                    }

                    finalImageUrl = uploadResult.getOrNull() ?: ""
                }

                // Create article object
                val article = Article(
                    id = "", // Firebase will generate ID
                    productId = _productId.value,
                    productName = _productName.value,
                    price = _price.value.toDouble(),
                    unit = _unit.value,
                    category = _category.value,
                    searchTerms = prepareSearchTerms(),
                    weightPerPiece = _weightPerPiece.value.toDoubleOrNull() ?: 0.0,
                    detailInfo = _detailInfo.value,
                    available = _available.value,
                    imageUrl = finalImageUrl
                )

                // Save to repository
                val saveResult = articleRepository.saveArticle(sellerId, article)

                if (saveResult.isSuccess) {
                    _uiState.value = CreateProductUiState.Success
                    clearForm()
                } else {
                    _uiState.value = CreateProductUiState.Error(
                        "Failed to save product: ${saveResult.exceptionOrNull()?.message}"
                    )
                }

            } catch (e: Exception) {
                println("❌ CreateProductViewModel.saveProduct: Error - ${e.message}")
                e.printStackTrace()
                _uiState.value = CreateProductUiState.Error(
                    e.message ?: "Failed to save product"
                )
            }
        }
    }

    /**
     * Validate form inputs
     * @return Error message if validation fails, null if valid
     */
    private fun validateForm(): String? {
        if (_productName.value.isBlank()) {
            return "Produktname ist erforderlich"
        }

        if (_searchTerms.value.isBlank()) {
            return "Suchbegriffe sind erforderlich"
        }

        val priceValue = _price.value.toDoubleOrNull()
        if (priceValue == null || priceValue <= 0) {
            return "Gültiger Preis ist erforderlich"
        }

        if (_unit.value.isBlank()) {
            return "Einheit ist erforderlich"
        }

        if (_category.value.isBlank()) {
            return "Kategorie ist erforderlich"
        }

        if (_imageData.value == null && _imageUrl.value.isBlank()) {
            return "Produktbild ist erforderlich"
        }

        // Validate weight per piece if unit is countable
        val unitEnum = ProductUnit.fromDisplayName(_unit.value)
        if (unitEnum?.isCountable == true) {
            val weight = _weightPerPiece.value.toDoubleOrNull()
            if (weight == null || weight <= 0) {
                return "Gewicht pro Stück ist erforderlich für zählbare Einheiten"
            }
        }

        return null
    }

    /**
     * Prepare search terms by adding product name and removing duplicates
     */
    private fun prepareSearchTerms(): String {
        val terms = mutableSetOf<String>()

        // Add product name
        terms.add(_productName.value.trim())

        // Add search terms
        _searchTerms.value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { terms.add(it) }

        return terms.joinToString(",")
    }

    /**
     * Clear the form after successful save
     */
    private fun clearForm() {
        _productName.value = ""
        _productId.value = ""
        _searchTerms.value = ""
        _price.value = ""
        _unit.value = ProductUnit.KG.displayName
        _category.value = ProductCategory.GEMUESE.displayName
        _weightPerPiece.value = ""
        _detailInfo.value = ""
        _available.value = true
        _imageData.value = null
        _imageUrl.value = ""
        _uploadProgress.value = 0f
    }

    /**
     * Clear error state
     */
    fun clearError() {
        if (_uiState.value is CreateProductUiState.Error) {
            _uiState.value = CreateProductUiState.Idle
        }
    }

    /**
     * Reset to idle state (for navigation)
     */
    fun resetState() {
        _uiState.value = CreateProductUiState.Idle
    }
}

/**
 * UI states for product creation
 */
sealed interface CreateProductUiState {
    data object Idle : CreateProductUiState
    data object Saving : CreateProductUiState
    data object Success : CreateProductUiState
    data class Error(val message: String) : CreateProductUiState
}
