package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.config.SellerConfig
import com.together.newverse.domain.service.ProductImportService
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Article.Companion.MODE_ADDED
import com.together.newverse.domain.model.Article.Companion.MODE_CHANGED
import com.together.newverse.domain.model.Article.Companion.MODE_REMOVED
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.Product
import com.together.newverse.domain.model.toArticle
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * ViewModel for Seller Overview/Dashboard screen
 */
class OverviewViewModel(
    private val articleRepository: ArticleRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val sellerConfig: SellerConfig,
    private val productImportService: ProductImportService
) : ViewModel() {

    private val _uiState = MutableStateFlow<OverviewUiState>(OverviewUiState.Loading)
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _currentFilter = MutableStateFlow(ProductFilter.ALL)
    val currentFilter: StateFlow<ProductFilter> = _currentFilter.asStateFlow()

    private val articles = mutableListOf<Article>()
    private var activeOrdersCount = 0
    private var allOrders = listOf<Order>()

    init {
        loadOverview()
    }

    private fun loadOverview() {
        viewModelScope.launch {
            _uiState.value = OverviewUiState.Loading

            // Verify user is authenticated
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                _uiState.value = OverviewUiState.Error("Not authenticated")
                return@launch
            }

            val sellerId = sellerConfig.sellerId

            // Observe both articles and orders
            launch {
                // Observe articles for current seller
                articleRepository.getArticles(sellerId)
                    .catch { e ->
                        _uiState.value = OverviewUiState.Error("Failed to load articles: ${e.message}")
                    }
                    .collect { article ->
                        // Update articles list based on mode
                        when (article.mode) {
                            MODE_ADDED -> {
                                // Check if article already exists to avoid duplicates
                                val existingIndex = articles.indexOfFirst { it.id == article.id }
                                if (existingIndex >= 0) {
                                    // Update existing article
                                    articles[existingIndex] = article
                                } else {
                                    // Add new article
                                    articles.add(article)
                                }
                            }
                            MODE_CHANGED -> {
                                val index = articles.indexOfFirst { it.id == article.id }
                                if (index >= 0) articles[index] = article
                            }
                            MODE_REMOVED -> articles.removeAll { it.id == article.id }
                        }

                        // Update UI state with current data
                        updateUiState()
                    }
            }

            launch {
                // Observe orders for current seller
                orderRepository.observeSellerOrders(sellerId)
                    .catch { e ->
                        println("⚠️ Failed to load orders: ${e.message}")
                        // Don't fail the whole screen, just show 0 orders
                    }
                    .collect { orders ->
                        // Store all orders for revenue calculation
                        allOrders = orders
                        // Count only active orders (not completed, cancelled, or outdated)
                        activeOrdersCount = orders.count { it.isActiveOrder() }
                        println("📊 Active orders count: $activeOrdersCount (total: ${orders.size})")

                        // Update UI state with current data
                        updateUiState()
                    }
            }
        }
    }

    private fun updateUiState() {
        val filteredArticles = when (_currentFilter.value) {
            ProductFilter.ALL -> articles.toList()
            ProductFilter.AVAILABLE -> articles.filter { it.available }
            ProductFilter.NOT_AVAILABLE -> articles.filter { !it.available }
        }

        _uiState.value = OverviewUiState.Success(
            totalProducts = articles.size,
            activeOrders = activeOrdersCount,
            totalRevenue = calculateTotalRevenue(),
            recentArticles = filteredArticles,
            recentOrders = emptyList() // TODO: Get from order repository
        )
    }

    /**
     * Calculate total revenue from completed orders
     */
    private fun calculateTotalRevenue(): Double {
        return allOrders
            .filter { it.status == OrderStatus.COMPLETED || it.status == OrderStatus.LOCKED }
            .flatMap { it.articles }
            .sumOf { it.getTotalPrice() }
    }

    fun setFilter(filter: ProductFilter) {
        _currentFilter.value = filter
        updateUiState()
    }

    fun refresh() {
        articles.clear()
        activeOrdersCount = 0
        allOrders = emptyList()
        loadOverview()
    }

    fun deleteArticles(articleIds: Set<String>) {
        viewModelScope.launch {
            // Get current seller ID
            val sellerId = authRepository.getCurrentUserId()
            if (sellerId == null) {
                println("❌ Cannot delete articles: User not authenticated")
                _uiState.value = OverviewUiState.Error("Authentication required to delete products")
                return@launch
            }

            // Delete each article from Firebase
            articleIds.forEach { articleId ->
                try {
                    println("🗑️ Deleting article from Firebase: $articleId")
                    val result = articleRepository.deleteArticle(sellerId, articleId)
                    result.onSuccess {
                        println("✅ Successfully deleted article: $articleId")
                        // Remove from local list
                        articles.removeAll { it.id == articleId }
                    }.onFailure { error ->
                        println("❌ Failed to delete article $articleId: ${error.message}")
                    }
                } catch (e: Exception) {
                    println("❌ Exception deleting article $articleId: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Update UI state after all deletions
            updateUiState()
        }
    }

    fun updateArticlesAvailability(articleIds: Set<String>, available: Boolean) {
        viewModelScope.launch {
            // Get current seller ID
            val sellerId = authRepository.getCurrentUserId()
            if (sellerId == null) {
                println("❌ Cannot update articles: User not authenticated")
                _uiState.value = OverviewUiState.Error("Authentication required to update products")
                return@launch
            }

            println("📝 Updating ${articleIds.size} articles to available=$available")

            // Update each article's availability in Firebase
            articleIds.forEach { articleId ->
                try {
                    // Find the article in local list
                    val article = articles.find { it.id == articleId }
                    if (article != null) {
                        // Update the article with new availability
                        val updatedArticle = article.copy(available = available)
                        println("📝 Updating article in Firebase: ${article.productName} (id=$articleId) -> available=$available")

                        val result = articleRepository.saveArticle(sellerId, updatedArticle)
                        result.onSuccess {
                            println("✅ Successfully updated article: ${article.productName}")
                            // Update local list
                            val index = articles.indexOfFirst { it.id == articleId }
                            if (index >= 0) {
                                articles[index] = updatedArticle
                            }
                        }.onFailure { error ->
                            println("❌ Failed to update article $articleId: ${error.message}")
                        }
                    } else {
                        println("⚠️ Article $articleId not found in local list")
                    }
                } catch (e: Exception) {
                    println("❌ Exception updating article $articleId: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Update UI state after all updates
            updateUiState()
        }
    }

    /**
     * Parse products from BNN file content and prepare for preview
     */
    fun parseProducts(fileContent: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Parsing

            try {
                val products = productImportService.parse(fileContent)
                println("📦 Parsed ${products.size} products from BNN file")

                if (products.isEmpty()) {
                    _importState.value = ImportState.Error(
                        getStringOrFallback(Res.string.import_no_products_found, "No products found")
                    )
                    return@launch
                }

                // Show preview with parsed products
                _importState.value = ImportState.Preview(products)

            } catch (e: Exception) {
                println("❌ Parse failed: ${e.message}")
                _importState.value = ImportState.Error(
                    getStringOrFallback(Res.string.import_file_read_error, "File read error: ${e.message}", e.message ?: "")
                )
            }
        }
    }

    /**
     * Import selected products from preview
     */
    fun importSelectedProducts(products: List<Product>) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing

            // Verify user is authenticated
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                println("❌ Cannot import products: User not authenticated")
                _importState.value = ImportState.Error(
                    getStringOrFallback(Res.string.import_auth_required, "Authentication required")
                )
                return@launch
            }

            val sellerId = sellerConfig.sellerId

            try {
                var successCount = 0
                var errorCount = 0

                // Save each product
                products.forEach { product ->
                    try {
                        val article = product.toArticle()
                        val result = articleRepository.saveArticle(sellerId, article)
                        result.onSuccess {
                            successCount++
                            println("✅ Imported: ${product.productName}")
                        }.onFailure { error ->
                            errorCount++
                            println("❌ Failed to import ${product.productName}: ${error.message}")
                        }
                    } catch (e: Exception) {
                        errorCount++
                        println("❌ Exception importing ${product.productName}: ${e.message}")
                    }
                }

                _importState.value = ImportState.Success(
                    importedCount = successCount,
                    errorCount = errorCount
                )

                // Refresh the list to show new products
                if (successCount > 0) {
                    refresh()
                }

            } catch (e: Exception) {
                println("❌ Import failed: ${e.message}")
                _importState.value = ImportState.Error(
                    getStringOrFallback(Res.string.import_failed, "Import failed: ${e.message}", e.message ?: "")
                )
            }
        }
    }

    /**
     * Reset import state to idle
     */
    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    /**
     * Safely get a localized string, falling back to a default if resources are unavailable.
     */
    private suspend fun getStringOrFallback(
        resource: org.jetbrains.compose.resources.StringResource,
        fallback: String,
        vararg formatArgs: Any
    ): String {
        return try {
            if (formatArgs.isNotEmpty()) getString(resource, *formatArgs) else getString(resource)
        } catch (_: Exception) {
            fallback
        }
    }
}

/**
 * State for product import operation
 */
sealed interface ImportState {
    data object Idle : ImportState
    data object Parsing : ImportState
    data class Preview(val products: List<Product>) : ImportState
    data object Importing : ImportState
    data class Success(val importedCount: Int, val errorCount: Int) : ImportState
    data class Error(val message: String) : ImportState
}

sealed interface OverviewUiState {
    data object Loading : OverviewUiState
    data class Success(
        val totalProducts: Int,
        val activeOrders: Int,
        val totalRevenue: Double,
        val recentArticles: List<Article>,
        val recentOrders: List<Order>
    ) : OverviewUiState
    data class Error(val message: String) : OverviewUiState
}

/**
 * Filter options for product list
 */
enum class ProductFilter {
    ALL,
    AVAILABLE,
    NOT_AVAILABLE
}
