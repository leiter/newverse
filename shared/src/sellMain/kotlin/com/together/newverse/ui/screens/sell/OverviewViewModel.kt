package com.together.newverse.ui.screens.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.together.newverse.domain.service.ProductImportService
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import kotlin.time.Clock
import com.together.newverse.domain.model.Product
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.toSellerArticle
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.SellerArticleRepository
import com.together.newverse.ui.state.core.AsyncState
import kotlinx.coroutines.Job
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
    private val sellerArticleRepository: SellerArticleRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val productImportService: ProductImportService
) : ViewModel() {

    private val _overviewState = MutableStateFlow<AsyncState<OverviewData>>(AsyncState.Loading)
    val overviewState: StateFlow<AsyncState<OverviewData>> = _overviewState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _currentFilter = MutableStateFlow(ProductFilter.ALL)
    val currentFilter: StateFlow<ProductFilter> = _currentFilter.asStateFlow()

    private var articles = listOf<SellerArticle>()
    private var activeOrdersCount = 0
    private var allOrders = listOf<Order>()
    private var loadJob: Job? = null

    init {
        loadOverview()
    }

    private fun loadOverview() {
        // A refresh replaces the listeners instead of stacking another pair.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _overviewState.value = AsyncState.Loading

            // Verify user is authenticated
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                _overviewState.value = AsyncState.Error("Not authenticated")
                return@launch
            }

            val sellerId = currentUserId

            // Observe both articles and orders
            launch {
                sellerArticleRepository.observeSellerArticles(sellerId)
                    .catch { e ->
                        _overviewState.value = AsyncState.Error("Failed to load articles: ${e.message}", e)
                    }
                    .collect { catalog ->
                        articles = catalog
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
                        allOrders = orders
                        activeOrdersCount = orders.count { isUpcomingPlacedOrder(it) }
                        println("📊 Active orders count: $activeOrdersCount (total: ${orders.size})")

                        // Update UI state with current data
                        updateUiState()
                    }
            }
        }
    }

    private fun updateUiState() {
        val publicArticles = articles.map { it.article }
        val filteredArticles = when (_currentFilter.value) {
            ProductFilter.ALL -> publicArticles
            ProductFilter.AVAILABLE -> publicArticles.filter { it.available }
            ProductFilter.NOT_AVAILABLE -> publicArticles.filter { !it.available }
        }

        _overviewState.value = AsyncState.Success(
            OverviewData(
                totalProducts = articles.size,
                activeOrders = activeOrdersCount,
                totalRevenue = calculateTotalRevenue(),
                recentArticles = filteredArticles,
                recentOrders = allOrders
                    .filter { isUpcomingPlacedOrder(it) }
                    .sortedByDescending { it.pickUpDate }
                    .take(5)
            )
        )
    }

    private fun isUpcomingPlacedOrder(order: Order): Boolean {
        if (order.isDemoOrder) return false
        if (order.status != OrderStatus.PLACED && order.status != OrderStatus.LOCKED) return false
        return order.pickUpDate > Clock.System.now().toEpochMilliseconds()
    }

    private fun calculateTotalRevenue(): Double {
        return allOrders
            .filter { isUpcomingPlacedOrder(it) }
            .flatMap { it.articles }
            .sumOf { it.getTotalPrice() }
    }

    fun setFilter(filter: ProductFilter) {
        _currentFilter.value = filter
        updateUiState()
    }

    fun refresh() {
        articles = emptyList()
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
                _overviewState.value = AsyncState.Error("Authentication required to delete products")
                return@launch
            }

            // Delete each article from Firebase
            articleIds.forEach { articleId ->
                try {
                    println("🗑️ Deleting article from Firebase: $articleId")
                    val result = sellerArticleRepository.deleteSellerArticle(sellerId, articleId)
                    result.onSuccess {
                        println("✅ Successfully deleted article: $articleId")
                        // Remove from local list
                        articles = articles.filterNot { it.id == articleId }
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
                _overviewState.value = AsyncState.Error("Authentication required to update products")
                return@launch
            }

            println("📝 Updating ${articleIds.size} articles to available=$available")

            // Update each article's availability in Firebase
            articleIds.forEach { articleId ->
                try {
                    // Find the article in local list
                    val article = articles.find { it.id == articleId }?.article
                    if (article != null) {
                        // Update the article with new availability
                        val updatedArticle = article.copy(available = available)
                        println("📝 Updating article in Firebase: ${article.productName} (id=$articleId) -> available=$available")

                        // Public half only: availability says nothing about purchase
                        // data, which may not even be loaded yet.
                        val result = sellerArticleRepository.saveSellerArticle(
                            sellerId,
                            SellerArticle(article = updatedArticle, sellerData = null)
                        )
                        result.onSuccess {
                            println("✅ Successfully updated article: ${article.productName}")
                            // Update local list
                            articles = articles.map {
                                if (it.id == articleId) it.copy(article = updatedArticle) else it
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

            val sellerId = currentUserId

            try {
                // One atomic update: the whole selection is imported, or none of it.
                val result = sellerArticleRepository.saveSellerArticles(
                    sellerId,
                    products.map { it.toSellerArticle() }
                )
                result.onSuccess { ids ->
                    println("✅ Imported ${ids.size} products")
                    _importState.value = ImportState.Success(importedCount = ids.size, errorCount = 0)
                }.onFailure { error ->
                    println("❌ Import failed: ${error.message}")
                    _importState.value = ImportState.Success(importedCount = 0, errorCount = products.size)
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

/**
 * Data class containing overview/dashboard data
 */
data class OverviewData(
    val totalProducts: Int,
    val activeOrders: Int,
    val totalRevenue: Double,
    val recentArticles: List<Article>,
    val recentOrders: List<Order>
)

/**
 * Filter options for product list
 */
enum class ProductFilter {
    ALL,
    AVAILABLE,
    NOT_AVAILABLE
}
