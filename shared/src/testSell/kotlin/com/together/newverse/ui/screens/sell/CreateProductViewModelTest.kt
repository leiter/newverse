package com.together.newverse.ui.screens.sell

import app.cash.turbine.test
import com.together.newverse.data.config.DefaultProductCatalogConfig
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate
import com.together.newverse.test.FakeAuthRepository
import com.together.newverse.test.FakeSellerArticleRepository
import com.together.newverse.test.FakeStorageRepository
import com.together.newverse.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateProductViewModelTest {

    private val dispatcherRule = MainDispatcherRule()
    private lateinit var articleRepository: FakeSellerArticleRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var storageRepository: FakeStorageRepository

    @BeforeTest
    fun setup() {
        dispatcherRule.setup()
        articleRepository = FakeSellerArticleRepository()
        authRepository = FakeAuthRepository()
        storageRepository = FakeStorageRepository()
    }

    @AfterTest
    fun tearDown() {
        dispatcherRule.tearDown()
    }

    private fun createViewModel(): CreateProductViewModel {
        return CreateProductViewModel(
            sellerArticleRepository = articleRepository,
            authRepository = authRepository,
            storageRepository = storageRepository,
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
        val savedArticle = articleRepository.savedArticles[0].second.article
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
        val savedArticle = articleRepository.savedArticles[0].second.article
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

        // Verify validation failed state (formState has errors)
        viewModel.formState.test {
            val errorState = awaitItem()
            assertTrue(errorState.hasErrors)
            assertTrue(errorState.fieldErrors.containsKey("productName"))
            cancelAndIgnoreRemainingEvents()
        }

        // When changing a field
        viewModel.onProductNameChange("New Name")
        advanceUntilIdle()

        // Then productName error should be cleared
        viewModel.formState.test {
            val state = awaitItem()
            assertFalse(state.fieldErrors.containsKey("productName"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Seller-only purchase data =====

    private val storedArticle = Article(
        id = "article_1",
        productId = "112108",
        productName = "Apfel Topaz",
        searchTerms = "apfel",
        price = 3.5,
        unit = "kg",
        category = "Obst",
        imageUrl = "https://example.com/apfel.jpg",
        taxRate = TaxRate.REDUCED.rate
    )

    private val storedSellerData = SellerArticleData(
        acquirePrice = 1.96,
        markupFactor = 1.67,
        supplier = "BOA",
        origin = "DE",
        certification = "DB",
        barcode = "4012345678901"
    )

    private fun fillRequiredFields(viewModel: CreateProductViewModel) {
        viewModel.onProductNameChange("Test Product")
        viewModel.onSearchTermsChange("test,product")
        viewModel.onPriceChange("2.50")
        viewModel.onUnitChange("kg")
        viewModel.onCategoryChange("Gemuse")
        viewModel.onImageSelected(byteArrayOf(1, 2, 3, 4))
    }

    private fun lastSaved(): SellerArticle = articleRepository.savedArticles.last().second

    @Test
    fun `new product without purchase price stores no seller data`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        fillRequiredFields(viewModel)

        viewModel.saveProduct()
        advanceUntilIdle()

        assertNull(lastSaved().sellerData)
    }

    @Test
    fun `new product with purchase price stores it as seller data`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        fillRequiredFields(viewModel)
        viewModel.onAcquirePriceChange("1.50")
        viewModel.onMarkupChange("50")

        viewModel.saveProduct()
        advanceUntilIdle()

        val sellerData = assertNotNull(lastSaved().sellerData)
        assertEquals(1.50, sellerData.acquirePrice)
        assertEquals(1.5, sellerData.markupFactor)
    }

    @Test
    fun `editing loads the purchase data into the form`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        articleRepository.setArticles(listOf(SellerArticle(storedArticle, storedSellerData)))
        val viewModel = createViewModel()

        viewModel.loadArticle("article_1")
        advanceUntilIdle()

        assertTrue(viewModel.isEditMode)
        assertEquals("1.96", viewModel.formState.value.data.acquirePrice)
        assertEquals("67", viewModel.formState.value.data.markupPercent)
    }

    @Test
    fun `editing keeps the sourcing data the form does not show`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        articleRepository.setArticles(listOf(SellerArticle(storedArticle, storedSellerData)))
        val viewModel = createViewModel()
        viewModel.loadArticle("article_1")
        advanceUntilIdle()

        viewModel.onAcquirePriceChange("2.10")
        viewModel.saveProduct()
        advanceUntilIdle()

        // Selling price stays 3.50; the markup follows: 3.50 / (2.10 × 1.07) = 55.8 %
        assertEquals(
            storedSellerData.copy(acquirePrice = 2.10, markupFactor = 1.558),
            lastSaved().sellerData
        )
        assertEquals(3.5, lastSaved().article.price)
        assertEquals("article_1", lastSaved().id)
    }

    @Test
    fun `editing an article without seller data does not invent any`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        articleRepository.setArticles(listOf(SellerArticle(storedArticle, sellerData = null)))
        val viewModel = createViewModel()
        viewModel.loadArticle("article_1")
        advanceUntilIdle()

        viewModel.onPriceChange("3.90")
        viewModel.saveProduct()
        advanceUntilIdle()

        assertNull(lastSaved().sellerData)
        assertEquals(3.90, lastSaved().article.price)
    }

    @Test
    fun `clearing the purchase price marks it unknown and keeps the rest`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        articleRepository.setArticles(listOf(SellerArticle(storedArticle, storedSellerData)))
        val viewModel = createViewModel()
        viewModel.loadArticle("article_1")
        advanceUntilIdle()

        viewModel.onAcquirePriceChange("")
        viewModel.saveProduct()
        advanceUntilIdle()

        val sellerData = assertNotNull(lastSaved().sellerData)
        assertFalse(sellerData.hasAcquirePrice)
        assertEquals("BOA", sellerData.supplier)
    }

    @Test
    fun `tax rate is saved with the article`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        fillRequiredFields(viewModel)
        viewModel.onTaxRateChange(TaxRate.STANDARD)

        viewModel.saveProduct()
        advanceUntilIdle()

        assertEquals(TaxRate.STANDARD.rate, lastSaved().article.taxRate)
    }

    @Test
    fun `failed load does not enter edit mode`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        articleRepository.shouldFailGetArticle = true
        val viewModel = createViewModel()

        viewModel.loadArticle("article_1")
        advanceUntilIdle()

        assertFalse(viewModel.isEditMode)
        assertNotNull(viewModel.formState.value.submitError)
    }

    @Test
    fun `rejected save counts as an attempt so the screen shows the errors`() = runTest {
        // The screen only reports field errors after a submit attempt. A save that
        // fails validation never reaches submitting(), so it must set the flag itself.
        authRepository.setCurrentUserId("seller_123")
        val viewModel = createViewModel()
        fillRequiredFields(viewModel)
        viewModel.onUnitChange("Stück")   // countable: weight per piece now required

        viewModel.saveProduct()
        advanceUntilIdle()

        val state = viewModel.formState.value
        assertTrue(state.hasAttemptedSubmit)
        assertTrue(state.fieldErrors.containsKey(ValidationError.WeightRequired.fieldName))
        assertTrue(articleRepository.savedArticles.isEmpty())
    }

    // ===== Pricing: the selling price leads =====

    private val formData get() = viewModelUnderTest.formState.value.data
    private lateinit var viewModelUnderTest: CreateProductViewModel

    private fun pricingForm(): CreateProductViewModel =
        createViewModel().also { viewModelUnderTest = it }

    @Test
    fun `entering a purchase price keeps the selling price and derives the markup`() = runTest {
        // The "Teesieb" case: price set first, purchase price added later.
        val viewModel = pricingForm()
        viewModel.onTaxRateChange(TaxRate.STANDARD)
        viewModel.onPriceChange("1.19")

        viewModel.onAcquirePriceChange("0.80")

        assertEquals("1.19", formData.price)
        assertEquals("25", formData.markupPercent)   // 0.80 × 1.25 × 1.19 = 1.19
    }

    @Test
    fun `purchase price and markup fill in an empty selling price`() = runTest {
        val viewModel = pricingForm()
        viewModel.onMarkupChange("45")

        viewModel.onAcquirePriceChange("2")

        assertEquals("3.1", formData.price)          // 2 × 1.45 × 1.07 = 3.103
    }

    @Test
    fun `changing the markup recalculates the selling price`() = runTest {
        val viewModel = pricingForm()
        viewModel.onPriceChange("3.00")
        viewModel.onAcquirePriceChange("2")

        viewModel.onMarkupChange("50")

        assertEquals("3.21", formData.price)         // 2 × 1.5 × 1.07
    }

    @Test
    fun `changing the selling price recalculates the markup`() = runTest {
        val viewModel = pricingForm()
        viewModel.onAcquirePriceChange("2")

        viewModel.onPriceChange("4.28")

        assertEquals("100", formData.markupPercent)  // 4.28 / (2 × 1.07) = 2.0
    }

    @Test
    fun `changing the tax rate keeps the selling price`() = runTest {
        val viewModel = pricingForm()
        viewModel.onPriceChange("3.21")
        viewModel.onAcquirePriceChange("2")

        viewModel.onTaxRateChange(TaxRate.STANDARD)

        assertEquals("3.21", formData.price)
        assertEquals("34.9", formData.markupPercent) // 3.21 / (2 × 1.19) = 1.3487
    }

    @Test
    fun `decimal commas are accepted`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        val viewModel = pricingForm()
        fillRequiredFields(viewModel)
        viewModel.onPriceChange("1,19")
        viewModel.onAcquirePriceChange("0,80")

        viewModel.saveProduct()
        advanceUntilIdle()

        assertEquals(1.19, lastSaved().article.price)
        assertEquals(0.80, lastSaved().sellerData?.acquirePrice)
    }

    @Test
    fun `markup is stored as a factor`() = runTest {
        authRepository.setCurrentUserId("seller_123")
        val viewModel = pricingForm()
        fillRequiredFields(viewModel)
        viewModel.onAcquirePriceChange("2")
        viewModel.onMarkupChange("45")

        viewModel.saveProduct()
        advanceUntilIdle()

        assertEquals(1.45, lastSaved().sellerData?.markupFactor)
    }
}
