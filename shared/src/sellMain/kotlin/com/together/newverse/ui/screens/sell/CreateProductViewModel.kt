package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.config.ProductCatalogConfig
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ProductUnit
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.StorageRepository
import com.together.newverse.ui.state.core.FormState
import com.together.newverse.ui.state.core.clearFieldError
import com.together.newverse.ui.state.core.formStateOf
import com.together.newverse.ui.state.core.submitFailure
import com.together.newverse.ui.state.core.submitSuccess
import com.together.newverse.ui.state.core.submitting
import com.together.newverse.ui.state.core.updateField
import com.together.newverse.ui.state.core.withFieldErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Data class representing the product creation form fields.
 */
data class ProductFormData(
    val productName: String = "",
    val productId: String = "",
    val searchTerms: String = "",
    val price: String = "",
    val unit: String = "",
    val category: String = "",
    val weightPerPiece: String = "",
    val detailInfo: String = "",
    val available: Boolean = true,
    val imageData: ByteArray? = null,
    val imageUrl: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ProductFormData
        return productName == other.productName &&
                productId == other.productId &&
                searchTerms == other.searchTerms &&
                price == other.price &&
                unit == other.unit &&
                category == other.category &&
                weightPerPiece == other.weightPerPiece &&
                detailInfo == other.detailInfo &&
                available == other.available &&
                imageData.contentEquals(other.imageData) &&
                imageUrl == other.imageUrl
    }

    override fun hashCode(): Int {
        var result = productName.hashCode()
        result = 31 * result + productId.hashCode()
        result = 31 * result + searchTerms.hashCode()
        result = 31 * result + price.hashCode()
        result = 31 * result + unit.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + weightPerPiece.hashCode()
        result = 31 * result + detailInfo.hashCode()
        result = 31 * result + available.hashCode()
        result = 31 * result + (imageData?.contentHashCode() ?: 0)
        result = 31 * result + imageUrl.hashCode()
        return result
    }
}

/**
 * ViewModel for Create Product screen
 * Handles product creation with image upload using FormState pattern.
 */
class CreateProductViewModel(
    private val articleRepository: ArticleRepository,
    private val authRepository: AuthRepository,
    private val storageRepository: StorageRepository,
    private val catalogConfig: ProductCatalogConfig
) : ViewModel() {

    // Form state using FormState pattern
    private val _formState = MutableStateFlow(
        formStateOf(
            ProductFormData(
                unit = catalogConfig.defaultUnit,
                category = catalogConfig.defaultCategory
            )
        )
    )
    val formState: StateFlow<FormState<ProductFormData>> = _formState.asStateFlow()

    // Upload progress (separate from form state as it's transient UI state)
    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    // Success state for navigation
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // Edit mode: holds the existing article's Firebase ID
    private var editingArticleId: String? = null
    val isEditMode: Boolean get() = editingArticleId != null

    /** Available categories from config, exposed for UI dropdowns */
    val availableCategories: List<String> = catalogConfig.categories

    /** Available units from config, exposed for UI dropdowns */
    val availableUnits: List<String> = catalogConfig.units

    // ===== Legacy compatibility properties =====
    // These expose individual fields for screens not yet migrated to FormState

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.productName", ReplaceWith("formState.value.data.productName"))
    val productName: StateFlow<String> get() = MutableStateFlow(_formState.value.data.productName)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.productId", ReplaceWith("formState.value.data.productId"))
    val productId: StateFlow<String> get() = MutableStateFlow(_formState.value.data.productId)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.searchTerms", ReplaceWith("formState.value.data.searchTerms"))
    val searchTerms: StateFlow<String> get() = MutableStateFlow(_formState.value.data.searchTerms)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.price", ReplaceWith("formState.value.data.price"))
    val price: StateFlow<String> get() = MutableStateFlow(_formState.value.data.price)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.unit", ReplaceWith("formState.value.data.unit"))
    val unit: StateFlow<String> get() = MutableStateFlow(_formState.value.data.unit)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.category", ReplaceWith("formState.value.data.category"))
    val category: StateFlow<String> get() = MutableStateFlow(_formState.value.data.category)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.weightPerPiece", ReplaceWith("formState.value.data.weightPerPiece"))
    val weightPerPiece: StateFlow<String> get() = MutableStateFlow(_formState.value.data.weightPerPiece)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.detailInfo", ReplaceWith("formState.value.data.detailInfo"))
    val detailInfo: StateFlow<String> get() = MutableStateFlow(_formState.value.data.detailInfo)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.available", ReplaceWith("formState.value.data.available"))
    val available: StateFlow<Boolean> get() = MutableStateFlow(_formState.value.data.available)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.data.imageData", ReplaceWith("formState.value.data.imageData"))
    val imageData: StateFlow<ByteArray?> get() = MutableStateFlow(_formState.value.data.imageData)

    /** @deprecated Use formState instead */
    @Deprecated("Use formState.isSubmitting or saveSuccess", ReplaceWith("formState.value.isSubmitting"))
    val uiState: StateFlow<CreateProductUiState> get() = MutableStateFlow(
        when {
            _saveSuccess.value -> CreateProductUiState.Success
            _formState.value.isSubmitting -> CreateProductUiState.Saving
            _formState.value.submitError != null -> CreateProductUiState.Error(_formState.value.submitError!!)
            _formState.value.hasErrors -> {
                val firstError = _formState.value.fieldErrors.entries.firstOrNull()
                if (firstError != null) {
                    CreateProductUiState.ValidationFailed(
                        ValidationError.entries.find { it.fieldName == firstError.key } ?: ValidationError.ProductNameRequired
                    )
                } else {
                    CreateProductUiState.Idle
                }
            }
            else -> CreateProductUiState.Idle
        }
    )

    /**
     * Load an existing article for editing.
     */
    fun loadArticle(articleId: String) {
        viewModelScope.launch {
            val currentUserId = authRepository.getCurrentUserId() ?: return@launch
            val result = articleRepository.getArticle(currentUserId, articleId)
            result.onSuccess { article ->
                editingArticleId = article.id
                _formState.value = formStateOf(
                    ProductFormData(
                        productName = article.productName,
                        productId = article.productId,
                        searchTerms = article.searchTerms,
                        price = if (article.price > 0) article.price.toString() else "",
                        unit = article.unit.ifBlank { catalogConfig.defaultUnit },
                        category = article.category.ifBlank { catalogConfig.defaultCategory },
                        weightPerPiece = if (article.weightPerPiece > 0) article.weightPerPiece.toString() else "",
                        detailInfo = article.detailInfo,
                        available = article.available,
                        imageUrl = article.imageUrl
                    )
                )
            }
        }
    }

    // Form update functions
    fun onProductNameChange(value: String) {
        _formState.update { it.updateField { data -> data.copy(productName = value) }.clearFieldError(ValidationError.ProductNameRequired.fieldName) }
    }

    fun onProductIdChange(value: String) {
        _formState.update { it.updateField { data -> data.copy(productId = value) } }
    }

    fun onSearchTermsChange(value: String) {
        _formState.update { it.updateField { data -> data.copy(searchTerms = value) }.clearFieldError(ValidationError.SearchTermsRequired.fieldName) }
    }

    fun onPriceChange(value: String) {
        _formState.update { it.updateField { data -> data.copy(price = value) }.clearFieldError(ValidationError.PriceRequired.fieldName) }
    }

    fun onUnitChange(value: String) {
        _formState.update { it.updateField { data -> data.copy(unit = value) }.clearFieldError(ValidationError.UnitRequired.fieldName) }
    }

    fun onCategoryChange(value: String) {
        _formState.update { it.updateField { data -> data.copy(category = value) }.clearFieldError(ValidationError.CategoryRequired.fieldName) }
    }

    fun onWeightPerPieceChange(value: String) {
        _formState.update { it.updateField { data -> data.copy(weightPerPiece = value) }.clearFieldError(ValidationError.WeightRequired.fieldName) }
    }

    fun onDetailInfoChange(value: String) {
        _formState.update { it.updateField { data -> data.copy(detailInfo = value) } }
    }

    fun onAvailableChange(value: Boolean) {
        _formState.update { it.updateField { data -> data.copy(available = value) } }
    }

    fun onImageSelected(imageData: ByteArray) {
        _formState.update { it.updateField { data -> data.copy(imageData = imageData) }.clearFieldError(ValidationError.ImageRequired.fieldName) }
    }

    /**
     * Validate and save the product
     * Uploads image first (if provided), then saves product to database
     */
    fun saveProduct() {
        println("📝 CreateProductViewModel.saveProduct: START (editMode=$isEditMode, articleId=$editingArticleId)")
        // Validate inputs
        val errors = validateFormData(_formState.value.data)
        if (errors.isNotEmpty()) {
            println("❌ CreateProductViewModel.saveProduct: Validation failed: $errors")
            _formState.update { it.withFieldErrors(errors) }
            return
        }

        viewModelScope.launch {
            _formState.update { it.submitting() }

            try {
                // Verify user is authenticated
                val currentUserId = authRepository.getCurrentUserId()
                if (currentUserId == null) {
                    _formState.update { it.submitFailure("User not authenticated") }
                    return@launch
                }

                val sellerId = currentUserId
                val formData = _formState.value.data

                // Upload image if new image data exists
                var finalImageUrl = formData.imageUrl
                if (formData.imageData != null) {
                    _uploadProgress.value = 0f
                    val uploadResult = storageRepository.uploadImage(
                        imageData = formData.imageData,
                        onProgress = { progress ->
                            _uploadProgress.value = progress
                        }
                    )

                    if (uploadResult.isFailure) {
                        _formState.update { it.submitFailure("Failed to upload image: ${uploadResult.exceptionOrNull()?.message}") }
                        return@launch
                    }

                    finalImageUrl = uploadResult.getOrNull() ?: ""
                }

                // Create article object (preserve ID when editing)
                val article = Article(
                    id = editingArticleId ?: "", // Firebase will generate ID for new articles
                    productId = formData.productId,
                    productName = formData.productName,
                    price = formData.price.toDouble(),
                    unit = formData.unit,
                    category = formData.category,
                    searchTerms = prepareSearchTerms(formData),
                    weightPerPiece = formData.weightPerPiece.toDoubleOrNull() ?: 0.0,
                    detailInfo = formData.detailInfo,
                    available = formData.available,
                    imageUrl = finalImageUrl
                )

                // Save to repository
                val saveResult = articleRepository.saveArticle(sellerId, article)

                if (saveResult.isSuccess) {
                    _formState.update { it.submitSuccess() }
                    _saveSuccess.value = true
                    clearForm()
                } else {
                    _formState.update { it.submitFailure("Failed to save product: ${saveResult.exceptionOrNull()?.message}") }
                }

            } catch (e: Exception) {
                println("❌ CreateProductViewModel.saveProduct: Error - ${e.message}")
                e.printStackTrace()
                _formState.update { it.submitFailure(e.message ?: "Failed to save product") }
            }
        }
    }

    /**
     * Validate form data and return field errors.
     * @return Map of field names to error messages
     */
    private fun validateFormData(data: ProductFormData): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (data.productName.isBlank()) {
            errors[ValidationError.ProductNameRequired.fieldName] = "Product name is required"
        }

        if (data.searchTerms.isBlank()) {
            errors[ValidationError.SearchTermsRequired.fieldName] = "Search terms are required"
        }

        val priceValue = data.price.toDoubleOrNull()
        if (priceValue == null || priceValue <= 0) {
            errors[ValidationError.PriceRequired.fieldName] = "Valid price is required"
        }

        if (data.unit.isBlank()) {
            errors[ValidationError.UnitRequired.fieldName] = "Unit is required"
        }

        if (data.category.isBlank()) {
            errors[ValidationError.CategoryRequired.fieldName] = "Category is required"
        }

        if (!isEditMode && data.imageData == null && data.imageUrl.isBlank()) {
            errors[ValidationError.ImageRequired.fieldName] = "Image is required"
        }

        // Validate weight per piece if unit is countable
        val unitEnum = ProductUnit.fromDisplayName(data.unit)
        if (unitEnum?.isCountable == true) {
            val weight = data.weightPerPiece.toDoubleOrNull()
            if (weight == null || weight <= 0) {
                errors[ValidationError.WeightRequired.fieldName] = "Weight per piece is required for countable units"
            }
        }

        return errors
    }

    /**
     * Prepare search terms by adding product name and removing duplicates
     */
    private fun prepareSearchTerms(data: ProductFormData): String {
        val terms = mutableSetOf<String>()

        // Add product name only for new products
        if (!isEditMode) {
            terms.add(data.productName.trim())
        }

        // Add search terms
        data.searchTerms.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { terms.add(it) }

        return terms.joinToString(",")
    }

    /**
     * Clear the form after successful save
     */
    private fun clearForm() {
        _formState.value = formStateOf(
            ProductFormData(
                unit = catalogConfig.defaultUnit,
                category = catalogConfig.defaultCategory
            )
        )
        _uploadProgress.value = 0f
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _formState.update {
            it.copy(
                submitError = null,
                fieldErrors = emptyMap()
            )
        }
    }

    /**
     * Reset to idle state (for navigation)
     */
    fun resetState() {
        _saveSuccess.value = false
        clearError()
    }
}

/**
 * UI states for product creation
 */
sealed interface CreateProductUiState {
    data object Idle : CreateProductUiState
    data object Saving : CreateProductUiState
    data object Success : CreateProductUiState
    data class ValidationFailed(val error: ValidationError) : CreateProductUiState
    data class Error(val message: String) : CreateProductUiState
}

/**
 * Validation errors for product creation form.
 * Each maps to a field name for FormState integration and a string resource for localized display.
 */
enum class ValidationError(val fieldName: String) {
    ProductNameRequired("productName"),
    SearchTermsRequired("searchTerms"),
    PriceRequired("price"),
    UnitRequired("unit"),
    CategoryRequired("category"),
    ImageRequired("image"),
    WeightRequired("weightPerPiece")
}
