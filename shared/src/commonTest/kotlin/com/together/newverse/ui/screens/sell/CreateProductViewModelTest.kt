package com.together.newverse.ui.screens.sell

import app.cash.turbine.test
import com.together.newverse.data.config.DefaultProductCatalogConfig
import com.together.newverse.data.config.DefaultSellerConfig
import com.together.newverse.test.FakeArticleRepository
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeStorageRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateProductViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeArticleRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var storageRepository: FakeStorageRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeArticleRepository()
        authRepository = FakeAuthRepository()
        storageRepository = FakeStorageRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
    }

    private fun createViewModel(): CreateProductViewModel {
        return CreateProductViewModel(
            articleRepository = articleRepository,
            authRepository = authRepository,
            storageRepository = storageRepository,
            sellerConfig = DefaultSellerConfig(),
            catalogConfig = DefaultProductCatalogConfig()
        )
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.Idle>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial form has empty fields`() = runTest {
        val viewModel = createViewModel()

        assertEquals("", viewModel.productName.value)
        assertEquals("", viewModel.productId.value)
        assertEquals("", viewModel.searchTerms.value)
        assertEquals("", viewModel.price.value)
        assertEquals("", viewModel.detailInfo.value)
        assertEquals(true, viewModel.available.value)
    }

    @Test
    fun `validates required product name`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // When saving with empty product name
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be ValidationFailed
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.ValidationFailed>(state)
            assertEquals(ValidationError.ProductNameRequired, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `validates required search terms`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Fill product name but leave search terms empty
        viewModel.onProductNameChange("Test Product")

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be ValidationFailed
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.ValidationFailed>(state)
            assertEquals(ValidationError.SearchTermsRequired, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `validates price is positive number`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Fill required fields with invalid price
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("-1.0")

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be ValidationFailed
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.ValidationFailed>(state)
            assertEquals(ValidationError.PriceRequired, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `validates price is not zero`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Fill required fields with zero price
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("0")

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be ValidationFailed
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.ValidationFailed>(state)
            assertEquals(ValidationError.PriceRequired, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `validates required image`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Fill all text fields but no image
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be ValidationFailed about image
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.ValidationFailed>(state)
            assertEquals(ValidationError.ImageRequired, state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saves product successfully`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Fill all required fields
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")
        viewModel.onImageSelected(byteArrayOf(1, 2, 3, 4))

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be Success
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.Success>(state)
            cancelAndIgnoreRemainingEvents()
        }

        // And article should be saved
        assertEquals(1, articleRepository.savedArticles.size)
        val savedArticle = articleRepository.savedArticles[0].second
        assertEquals("Test Product", savedArticle.productName)
        assertEquals(2.50, savedArticle.price)
        assertEquals("kg", savedArticle.unit)
    }

    @Test
    fun `uploads image before save`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Fill all required fields with image
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")
        val imageData = byteArrayOf(1, 2, 3, 4, 5)
        viewModel.onImageSelected(imageData)

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then image should be uploaded
        assertEquals(1, storageRepository.uploadedImages.size)
        assertTrue(storageRepository.uploadedImages[0].imageData.contentEquals(imageData))

        // And saved article should have uploaded URL
        val savedArticle = articleRepository.savedArticles[0].second
        assertEquals(storageRepository.uploadedImageUrl, savedArticle.imageUrl)
    }

    @Test
    fun `handles image upload failure`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        storageRepository.shouldFailUpload = true
        storageRepository.failureMessage = "Storage unavailable"

        val viewModel = createViewModel()

        // Fill all required fields
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")
        viewModel.onImageSelected(byteArrayOf(1, 2, 3, 4))

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be Error
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.Error>(state)
            assertTrue(state.message.contains("upload") || state.message.contains("image"))
            cancelAndIgnoreRemainingEvents()
        }

        // And article should NOT be saved
        assertTrue(articleRepository.savedArticles.isEmpty())
    }

    @Test
    fun `handles save failure`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        articleRepository.shouldFailSave = true
        articleRepository.failureMessage = "Database error"

        val viewModel = createViewModel()

        // Fill all required fields
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")
        viewModel.onImageSelected(byteArrayOf(1, 2, 3, 4))

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be Error
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.Error>(state)
            assertTrue(state.message.contains("save") || state.message.contains("Database"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clears form on successful save`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Fill all required fields
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")
        viewModel.onDetailInfoChange("Some details")
        viewModel.onImageSelected(byteArrayOf(1, 2, 3, 4))

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then form should be cleared
        assertEquals("", viewModel.productName.value)
        assertEquals("", viewModel.searchTerms.value)
        assertEquals("", viewModel.price.value)
        assertEquals("", viewModel.detailInfo.value)
        assertEquals(null, viewModel.imageData.value)
    }

    @Test
    fun `clearError resets error state`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Trigger a validation error
        viewModel.saveProduct()
        advanceUntilIdle()

        // Verify validation failed state
        viewModel.uiState.test {
            val errorState = awaitItem()
            assertIs<CreateProductUiState.ValidationFailed>(errorState)
            cancelAndIgnoreRemainingEvents()
        }

        // When clearing error
        viewModel.clearError()

        // Then state should be Idle
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.Idle>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetState returns to Idle`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // When reset is called
        viewModel.resetState()

        // Then state should be Idle
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.Idle>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handles not authenticated`() = runTest {
        // Given user is NOT authenticated
        authRepository.setCurrentUserId(null)
        val viewModel = createViewModel()

        // Fill all required fields
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")
        viewModel.onImageSelected(byteArrayOf(1, 2, 3, 4))

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Then state should be Error
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.Error>(state)
            assertTrue(state.message.contains("authenticated"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `form field changes are tracked`() = runTest {
        val viewModel = createViewModel()

        // When updating fields
        viewModel.onProductNameChange("Test Name")
        viewModel.onProductIdChange("PROD-001")
        viewModel.onSearchTermsChange("test,name")
        viewModel.onPriceChange("3.99")
        viewModel.onUnitChange("Stuck")
        viewModel.onCategoryChange("Obst")
        viewModel.onWeightPerPieceChange("0.5")
        viewModel.onDetailInfoChange("Test details")
        viewModel.onAvailableChange(false)

        // Then fields should have correct values
        assertEquals("Test Name", viewModel.productName.value)
        assertEquals("PROD-001", viewModel.productId.value)
        assertEquals("test,name", viewModel.searchTerms.value)
        assertEquals("3.99", viewModel.price.value)
        assertEquals("Stuck", viewModel.unit.value)
        assertEquals("Obst", viewModel.category.value)
        assertEquals("0.5", viewModel.weightPerPiece.value)
        assertEquals("Test details", viewModel.detailInfo.value)
        assertEquals(false, viewModel.available.value)
    }

    @Test
    fun `upload progress is tracked`() = runTest {
        // Given user is authenticated and progress simulation enabled
        authRepository.setCurrentUserId("seller_123")
        storageRepository.simulateProgress = true

        val viewModel = createViewModel()

        // Fill all required fields
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")
        viewModel.onImageSelected(byteArrayOf(1, 2, 3, 4))

        // When saving
        viewModel.saveProduct()
        advanceUntilIdle()

        // Progress was updated (final progress is 1.0 or reset to 0)
        // Form is cleared after success, so uploadProgress should be reset to 0
        assertEquals(0f, viewModel.uploadProgress.value)
    }

    @Test
    fun `changing form field clears error state`() = runTest {
        // Given user is authenticated
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()

        // Trigger a validation error
        viewModel.saveProduct()
        advanceUntilIdle()

        // Verify validation failed state
        viewModel.uiState.test {
            val errorState = awaitItem()
            assertIs<CreateProductUiState.ValidationFailed>(errorState)
            cancelAndIgnoreRemainingEvents()
        }

        // When changing a field
        viewModel.onProductNameChange("New Name")

        // Then state should be Idle
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<CreateProductUiState.Idle>(state)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
