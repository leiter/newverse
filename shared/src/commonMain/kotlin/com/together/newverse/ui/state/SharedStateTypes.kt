package com.together.newverse.ui.state

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.MarketPricing
import com.together.newverse.domain.model.ResolvedPrice
import com.together.newverse.domain.model.resolvePrice
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Conversation
import com.together.newverse.domain.model.filterByCategory
import com.together.newverse.domain.model.filterByFavourite
import com.together.newverse.domain.model.filterByQuery
import com.together.newverse.domain.model.DeliveryAddress
import com.together.newverse.domain.model.Fulfilment
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.PickupSlot
import com.together.newverse.domain.model.PriceChange
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.state.core.AsyncState
import com.together.newverse.ui.state.core.AuthAwareState

// ===== Base Interfaces =====

/**
 * Base interface for all screen states.
 * Ensures consistent loading and error handling.
 *
 * @see AsyncState For a more flexible approach with Initial/Loading/Success/Error states.
 * Consider migrating screen states to use AsyncState<T> for better composition with
 * AuthAwareState and flow operators.
 */
interface ScreenState {
    val isLoading: Boolean
    val error: ErrorState?
}

// ===== Generic Screen State Templates =====

/**
 * Generic listing state for screens that display lists.
 * Used for products, orders, etc.
 */
data class ListingState<T>(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val items: List<T> = emptyList(),
    val selectedItem: T? = null,
    val selectedItems: Set<T> = emptySet()
) : ScreenState

// Type aliases for specific listing screens
typealias ProductsScreenState = ListingState<Article>
typealias OrdersScreenState = ListingState<Order>
typealias OrderHistoryScreenState = ListingState<Order>

/**
 * Converts a ListingState to AsyncState for use with AsyncStateContent.
 * This enables gradual migration from the legacy pattern to AsyncState.
 *
 * Usage:
 * ```
 * val asyncState = orderHistoryState.toAsyncState()
 * AsyncStateContent(state = asyncState) { orders ->
 *     // Render orders
 * }
 * ```
 */
fun <T> ListingState<T>.toAsyncState(): AsyncState<List<T>> = when {
    isLoading -> AsyncState.Loading
    error != null -> AsyncState.Error(
        message = error.message,
        retryable = error.retryable
    )
    else -> AsyncState.Success(items)
}

/**
 * Converts a ScreenState to AsyncState for use with AsyncStateContent.
 * Generic version for any ScreenState with a data property.
 *
 * @param dataExtractor Function to extract the data from the screen state
 */
inline fun <S : ScreenState, T> S.toAsyncState(dataExtractor: (S) -> T?): AsyncState<T> {
    val currentError = error // Store in local val for smart cast
    return when {
        isLoading -> AsyncState.Loading
        currentError != null -> AsyncState.Error(
            message = currentError.message,
            retryable = currentError.retryable
        )
        else -> {
            val data = dataExtractor(this)
            if (data != null) {
                AsyncState.Success(data)
            } else {
                AsyncState.Initial
            }
        }
    }
}

// ===== Messaging State =====

data class MessagingScreenState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// ===== User State =====

/**
 * Legacy user state type. Consider using AuthState from AuthFlowCoordinator for
 * authentication state, which provides better integration with auth-aware flows.
 *
 * @see com.together.newverse.ui.state.core.AuthState For auth-driven state management.
 * @see com.together.newverse.ui.state.core.AuthAwareState For wrapping data with auth context.
 */
sealed interface UserState {
    data object NotAuthenticated : UserState
    data object Guest : UserState
    data object Loading : UserState
    data class LoggedIn(
        val id: String,
        val name: String,
        val email: String,
        val role: UserRole,
        val profileImageUrl: String? = null
    ) : UserState
}

enum class UserRole {
    CUSTOMER,
    SELLER,
    BOTH
}

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val pickupTime: String = "",
    val notificationsEnabled: Boolean = true,
    val newsletterEnabled: Boolean = false
)

// ===== Error State =====

/**
 * Error state with detailed information.
 *
 * Note: For simpler error handling in async operations, consider using
 * AsyncState.Error which provides message, cause, and retryable fields.
 *
 * @see com.together.newverse.ui.state.core.AsyncState.Error
 */
data class ErrorState(
    val message: String,
    val code: String? = null,
    val type: ErrorType = ErrorType.GENERAL,
    val retryable: Boolean = true,
    val details: Map<String, Any> = emptyMap()
)

enum class ErrorType {
    NETWORK,
    VALIDATION,
    AUTHENTICATION,
    AUTHORIZATION,
    NOT_FOUND,
    SERVER,
    GENERAL
}

// ===== Navigation State =====

data class NavigationState(
    val currentRoute: NavRoutes = NavRoutes.Home,
    val previousRoute: NavRoutes? = null,
    val isDrawerOpen: Boolean = false,
    val backStack: List<NavRoutes> = listOf(NavRoutes.Home),
    val pendingRoute: NavRoutes? = null
)

// ===== UI State =====

data class GlobalUiState(
    val isRefreshing: Boolean = false,
    val snackbar: SnackbarState? = null,
    val dialog: DialogState? = null,
    val bottomSheet: BottomSheetState? = null
)

data class SnackbarState(
    val message: String,
    val type: SnackbarType = SnackbarType.INFO,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.SHORT
)

enum class SnackbarType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

enum class SnackbarDuration {
    SHORT,
    LONG,
    INDEFINITE
}

/**
 * Dialog state with support for localization via extension functions.
 * For Compose UI, use the @Composable localization extensions:
 * @see DialogState.Confirmation.localizedConfirmLabel
 * @see DialogState.Confirmation.localizedCancelLabel
 * @see DialogState.Information.localizedDismissLabel
 * @see DialogState.Error.localizedTitle
 * @see DialogState.Error.localizedRetryLabel
 * @see DialogState.Error.localizedDismissLabel
 */
sealed interface DialogState {
    data class Confirmation(
        val title: String,
        val message: String,
        val confirmLabel: String = "Confirm",
        val cancelLabel: String = "Cancel"
    ) : DialogState

    data class Information(
        val title: String,
        val message: String,
        val dismissLabel: String = "OK"
    ) : DialogState

    data class Error(
        val title: String = "Error",
        val message: String,
        val retryLabel: String = "Retry",
        val dismissLabel: String = "Dismiss"
    ) : DialogState

    data class DraftBasketWarning(
        val draftItemCount: Int,
        val pendingOrderId: String,
        val pendingOrderDate: String
    ) : DialogState
}

sealed interface BottomSheetState {
    data class ProductDetail(val product: Article) : BottomSheetState
    data class CartSummary(val items: List<OrderedProduct>) : BottomSheetState
}

// ===== Basket State =====

data class BasketState(
    val items: List<OrderedProduct> = emptyList(),
    val totalAmount: Double = 0.0,
    val itemCount: Int = 0,
    val isCheckingOut: Boolean = false,
    val currentOrderId: String? = null,
    val currentOrderDate: String? = null
)

// ===== App Metadata =====

/**
 * App metadata and initialization state.
 *
 * Note: For auth initialization, consider using AuthFlowCoordinator which
 * handles auth state initialization automatically with AuthState.Initializing.
 *
 * @see com.together.newverse.ui.state.core.AuthFlowCoordinator
 * @see com.together.newverse.ui.state.core.AuthState
 */
data class AppMetaState(
    val version: String = "1.0.0",
    val buildNumber: Int = 1,
    val lastSyncTime: Long? = null,
    val isInitializing: Boolean = false,
    val isInitialized: Boolean = false,
    val initializationStep: InitializationStep = InitializationStep.NotStarted,
    val devOrderDateOffsetDays: Int = 9
)

sealed class InitializationStep {
    data object NotStarted : InitializationStep()
    data object CheckingAuth : InitializationStep()
    data object LoadingProfile : InitializationStep()
    data object LoadingOrder : InitializationStep()
    data object LoadingArticles : InitializationStep()
    data object Complete : InitializationStep()
    data class Failed(val step: String, val message: String) : InitializationStep()

    fun displayMessage(): String = when (this) {
        is NotStarted -> "Starting app..."
        is CheckingAuth -> "Checking authentication..."
        is LoadingProfile -> "Loading your profile..."
        is LoadingOrder -> "Loading current order..."
        is LoadingArticles -> "Loading products..."
        is Complete -> "Ready!"
        is Failed -> "Error: $message"
    }

    /**
     * For Compose UI, use the @Composable extension localizedMessage() instead
     * @see InitializationStep.localizedMessage
     */
}

// ===== Basket Screen State (checkout flow) =====

enum class MergeResolution {
    UNDECIDED,
    ADD,
    KEEP_EXISTING,
    USE_NEW
}

enum class MergeConflictType {
    QUANTITY_CHANGED,
    ITEM_ADDED,
    ITEM_REMOVED
}

enum class BasketMergeMode {
    CHECKOUT_EXISTING_ORDER,
    HISTORY_REORDER
}

data class MergeConflict(
    val productId: String,
    val productName: String,
    val unit: String,
    val conflictType: MergeConflictType,
    val existingQuantity: Double,
    val newQuantity: Double,
    val existingPrice: Double,
    val newPrice: Double,
    val resolution: MergeResolution = MergeResolution.UNDECIDED
)

data class BasketScreenState(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val items: List<OrderedProduct> = emptyList(),
    val total: Double = 0.0,
    val isCheckingOut: Boolean = false,
    val orderSuccess: Boolean = false,
    val orderError: String? = null,
    val orderId: String? = null,
    val orderDate: String? = null,
    val pickupDate: Long? = null,
    val createdDate: Long? = null,
    /**
     * The demo order this basket is confirming, as its date key and id, or null.
     *
     * Set when the buyer taps confirm on a demo order they still hold after being
     * approved. It is what tells checkout to stamp the new order
     * [com.together.newverse.domain.model.OrderOrigin.TRANSITIONED] rather than
     * PRODUCTION, and which demo record to clear away once the real one exists.
     */
    val confirmingDemoOrderId: String? = null,
    val confirmingDemoOrderDate: String? = null,
    val isEditMode: Boolean = false,
    val canEdit: Boolean = false,
    val isLoadingOrder: Boolean = false,
    val originalOrderItems: List<OrderedProduct> = emptyList(),
    val hasChanges: Boolean = false,
    /**
     * Whether the unsaved change to this placed order was made *on the basket screen*.
     *
     * [hasChanges] says an edit exists; this says where it came from, and that is what
     * decides whether the buyer is asked to confirm it. An edit made on the home or
     * product-detail screen is written to the standing order straight away — the buyer
     * dialled an amount and tapped add, and sending them to a third screen to repeat
     * themselves is asking the same question twice. An edit made here, among the rows
     * and the totals, keeps the *Bestellung aktualisieren* button: this is the screen
     * where a whole order is reviewed at once, and a review ends in a confirmation.
     *
     * Set by the basket screen's own mutations, cleared by anything that re-establishes
     * a baseline (a write, a load, a reset). While it is true the auto-save stands
     * down completely: committing a home add would carry the unconfirmed basket edits
     * with it, which is the one thing the confirmation exists to prevent.
     */
    val editedOnBasketScreen: Boolean = false,
    val selectedPickupDate: Long? = null,
    val availablePickupDates: List<Long> = emptyList(),
    /**
     * The market the selected date belongs to, set with it and never on its own.
     *
     * Together with [selectedPickupDate] this is the chosen `PickupSlot`. They are
     * two fields rather than one object only because every existing caller reads
     * the date; nothing writes one without the other. Empty while the seller runs a
     * single market, where the market is implied.
     */
    val selectedMarketId: String = "",
    /** One row per market-and-date the buyer may choose, already in date order. */
    val availableSlots: List<PickupSlot> = emptyList(),
    /** Names for the slot rows, by market id. Only drawn when there is more than one. */
    val marketNames: Map<String, String> = emptyMap(),
    /**
     * A market switch that would change prices, waiting to be confirmed.
     *
     * The buyer reviewed a total; recalculating it under them because they chose a
     * different stall is the one thing this feature could do that would cost
     * trust. See `doc/guides/market-pricing-and-access-design.md` §2.
     */
    val pendingSlotPriceChange: PendingSlotPriceChange? = null,
    val showDatePicker: Boolean = false,
    /**
     * The order is built and the buyer pressed Bestellen; they are confirming where
     * and when before it is written.
     *
     * The last point at which the pickup slot — and so the prices — can still be
     * changed without editing an order. `doc/guides/market-pricing-and-access-design.md`
     * §4.
     */
    val showCheckoutConfirm: Boolean = false,
    /**
     * Pickup or delivery for *this* order, defaulted from the buyer's profile and
     * changeable at the checkout confirmation.
     *
     * Per order rather than per profile: a buyer may collect this week and want it
     * brought next. The profile supplies the default and is not rewritten by the
     * choice — switching one order does not re-aim every future one.
     */
    val fulfilment: Fulfilment = Fulfilment.PICKUP,
    /** The address this order would be delivered to, seeded from the profile. */
    val deliveryAddress: DeliveryAddress = DeliveryAddress.NONE,
    /**
     * The time the buyer would like, `HH:mm`, seeded from the profile.
     *
     * When they mean to collect a pickup, and when they would like a delivery to
     * arrive — one field, because it is one question and the seller reads it the same
     * way. Neither is an appointment the seller has agreed to.
     *
     * Optional, and asked here rather than in the profile: it is a fact about one
     * order, not about the buyer. It used to be a required profile field that
     * stood between a buyer and asking for access, and no seller screen ever read it.
     * Now the order carries it and the seller sees it on the order.
     *
     * Empty is a complete answer. A time that is given has to be one the stall could
     * meet — a readable `HH:mm` between 7:00 and 18:59, the same range the profile
     * field insists on — which the picker is the only way to produce and
     * `refuseUnusablePickupTime` is the backstop for.
     */
    val pickupTime: String = "",
    /**
     * Who is collecting this order, seeded from the buyer's own name.
     *
     * Per order because the person who placed it routinely is not the person who
     * turns up. Left as it was seeded it means "me" — [Order.statedPickupName] is what
     * the seller's screens read, and it ignores a name that matches the buyer's.
     */
    val pickupName: String = "",
    /**
     * A number to reach whoever collects, seeded from the profile. Optional.
     *
     * The profile has held `telephoneNumber` all along and never sent it anywhere;
     * this is the field that actually reaches the seller, per order and clearable.
     */
    val pickupPhone: String = "",
    /**
     * What the loaded order said about [fulfilment], [deliveryAddress], [pickupTime],
     * [pickupName] and [pickupPhone] when it was read, so an edit of any of them can
     * be told from no edit at all.
     *
     * The counterpart of [originalOrderItems], and needed for the same reason: an
     * order is loaded into the basket, so the live fields alone cannot say whether the
     * buyer changed anything. Meaningless while [orderId] is null — a new order has no
     * original to differ from — which is why [fulfilmentChanged] checks that first.
     */
    val originalFulfilment: Fulfilment = Fulfilment.PICKUP,
    val originalDeliveryAddress: DeliveryAddress = DeliveryAddress.NONE,
    val originalPickupTime: String = "",
    val originalPickupName: String = "",
    val originalPickupPhone: String = "",
    /**
     * The market the loaded order stands against, as the order itself records it.
     *
     * Separate from [selectedMarketId], which means "a market the buyer chose in this
     * session" and is deliberately left empty by loading an order — it is saved into
     * the draft basket and consulted by the market-pick onboarding, neither of which
     * should hear about an order being opened. This field is read-only context: whether
     * the stall this order belongs to delivers at all.
     */
    val orderMarketId: String = "",
    val isCancelling: Boolean = false,
    val cancelSuccess: Boolean = false,
    val showReorderDatePicker: Boolean = false,
    val isReordering: Boolean = false,
    val reorderSuccess: Boolean = false,
    val showMergeDialog: Boolean = false,
    val existingOrderForMerge: Order? = null,
    val mergeConflicts: List<MergeConflict> = emptyList(),
    val isMerging: Boolean = false,
    val mergeMode: BasketMergeMode = BasketMergeMode.CHECKOUT_EXISTING_ORDER,
    val showDraftWarningDialog: Boolean = false,
    val draftItemCount: Int = 0,
    val pendingOrderIdForLoad: String? = null,
    val pendingOrderDateForLoad: String? = null,
    val showCancelConfirmDialog: Boolean = false
) : ScreenState {
    /**
     * Whether the buyer has changed how this placed order reaches them.
     *
     * Kept apart from [hasChanges], which is recomputed from the basket items by every
     * basket emission and would lose this on the next one. The address only counts
     * while the order is a delivery: a buyer who switches to pickup leaves whatever
     * they had typed standing in the fields, and [originalDeliveryAddress] is what is
     * written back as `NONE` anyway. The collection time is the mirror
     * image, counting only while the order is a pickup — a delivery arrives when the
     * seller's round reaches it, not when the buyer would have come to the stall.
     * The time is not: it is asked of both, so a change to it is an edit whichever
     * way the order reaches the buyer.
     */
    val fulfilmentChanged: Boolean
        get() = orderId != null && (
            fulfilment != originalFulfilment ||
                (fulfilment == Fulfilment.DELIVERY && deliveryAddress != originalDeliveryAddress) ||
                pickupTime != originalPickupTime
            )

    /**
     * Whether the buyer has changed who is collecting this placed order, or the
     * number to reach them on.
     *
     * Apart from [fulfilmentChanged] because it is true of a delivery and a pickup
     * alike: somebody takes receipt either way. Apart from [hasChanges] for the same
     * reason that one is — it is recomputed from the basket items on every emission
     * and would drop this.
     */
    val contactChanged: Boolean
        get() = orderId != null &&
            (pickupName != originalPickupName || pickupPhone != originalPickupPhone)

    /**
     * Anything at all to save on a placed order: its items, how it reaches the buyer,
     * or who takes receipt of it.
     */
    val hasPendingEdits: Boolean
        get() = hasChanges || fulfilmentChanged || contactChanged
}

// ===== Customer Profile Screen State =====

data class CustomerProfileScreenState(
    override val isLoading: Boolean = false,
    override val error: ErrorState? = null,
    val profile: BuyerProfile? = null,
    val photoUrl: String? = null,
    val showLogoutWarningDialog: Boolean = false,
    val showLinkAccountDialog: Boolean = false,
    val showDeleteAccountDialog: Boolean = false,
    /**
     * Whether deleting this account will present Apple's sign-in sheet.
     *
     * True only for an Apple-backed account on a platform that can revoke the
     * token, i.e. iOS. Deletion has to re-run the Apple sign-in there to obtain
     * a fresh authorization code, so the confirmation dialog says so up front
     * instead of letting the sheet appear unannounced.
     */
    val deleteRequiresAppleConfirmation: Boolean = false,
    val isLinkingAccount: Boolean = false,
    val linkAccountError: String? = null,
    val showEmailLinkingDialog: Boolean = false,
    val emailLinkingEmail: String = "",
    val emailLinkingPassword: String = "",
    val emailLinkingConfirmPassword: String = "",
    val emailLinkingError: String? = null,
    /**
     * Changing the password of an email/password account. One nested object
     * rather than six more flat fields, because the seller's profile carries
     * the identical state and the dialog is shared between the two.
     */
    val changePassword: ChangePasswordState = ChangePasswordState()
) : ScreenState

/**
 * Converts CustomerProfileScreenState to AsyncState<BuyerProfile>.
 * Use with AsyncStateContent for consistent loading/error handling.
 */
fun CustomerProfileScreenState.toProfileAsyncState(): AsyncState<BuyerProfile> =
    toAsyncState { it.profile }

/**
 * Authentication provider types
 */
enum class AuthProvider {
    ANONYMOUS,
    GOOGLE,
    EMAIL,
    TWITTER,
    APPLE;

    val displayName: String
        get() = when (this) {
            ANONYMOUS -> "Gast"
            GOOGLE -> "Google"
            EMAIL -> "E-Mail"
            TWITTER -> "Twitter"
            APPLE -> "Apple"
        }

    /**
     * For Compose UI, use the @Composable extension localizedDisplayName() instead
     * @see AuthProvider.localizedDisplayName
     */

    companion object {
        /**
         * Map Firebase provider ids to a provider.
         *
         * Read these from the auth session (AuthRepository.getProviderIds), never
         * from the user's email address: an Apple account carries a private relay
         * address, and a Google account need not be a gmail.com one.
         *
         * An account can be linked to several providers; the first recognised one
         * wins, which is the one the buyer signed in with in practice.
         */
        /**
         * Every provider linked to the account, in the order Firebase reports
         * them. An account can be backed by several - Apple plus a password,
         * say - and the profile badge shows all of them.
         */
        fun allFromProviderIds(providerIds: List<String>): List<AuthProvider> =
            providerIds.mapNotNull { id ->
                when (id) {
                    "apple.com" -> APPLE
                    "google.com" -> GOOGLE
                    "twitter.com" -> TWITTER
                    "password" -> EMAIL
                    else -> null
                }
            }.distinct()

        fun fromProviderIds(providerIds: List<String>): AuthProvider {
            for (id in providerIds) {
                when (id) {
                    "apple.com" -> return APPLE
                    "google.com" -> return GOOGLE
                    "twitter.com" -> return TWITTER
                    "password" -> return EMAIL
                }
            }
            return ANONYMOUS
        }
    }
}

// ===== Main Screen State =====

/**
 * An add the buyer asked for that could not be carried out yet, kept so it can be.
 *
 * Only ever set while a placed order is past its edit deadline: the add is refused,
 * the buyer is offered a new order, and this is what that new order starts with. The
 * article is held by id rather than as an [Article] so a catalogue refresh between
 * the two taps cannot resurrect a withdrawn one — the id is looked up again when the
 * add is applied.
 */
data class PendingBasketAdd(
    val articleId: String,
    val quantity: Double,
)

data class MainScreenState(
    override val isLoading: Boolean = true,
    override val error: ErrorState? = null,
    val articles: List<Article> = emptyList(),
    val selectedArticle: Article? = null,
    val selectedQuantity: Double = 0.0,
    /**
     * Whether the buyer has the hero card's amount field open, with the number pad
     * over the bottom bar. Raised so the bar's basket can be offered somewhere the
     * keyboard is not covering; nothing else should read it as "is typing".
     */
    val isQuantityInputFocused: Boolean = false,
    val cartItemCount: Int = 0,
    val basketItems: List<OrderedProduct> = emptyList(),
    val favouriteArticles: List<String> = emptyList(),
    /** What the chip row narrows to. See [ProductFilterState]. */
    val filterState: ProductFilterState = ProductFilterState.Unfiltered,
    val canEditOrder: Boolean = true,
    val showNewOrderSnackbar: Boolean = false,
    /** The add that is waiting for a new order to put it in. See [PendingBasketAdd]. */
    val pendingAdd: PendingBasketAdd? = null,
    val searchQuery: String = "",
    /**
     * The price conditions of the market this catalogue is being browsed against.
     *
     * The catalogue used to render `Article.price` raw, so the shelf quoted the base
     * price while the basket charged the market's — the list said 2,30 €/kg and the
     * basket 2,48 €/kg for the same article. [MarketPricing.NONE] means "as the seller
     * entered it", which is every single-market seller with no conditions, and also the
     * seller app, whose own home screen is built with a default [MainScreenState].
     *
     * Derived, never stored: `BuyAppState.mainScreenState` fills it from
     * `activePricing` on the way to the screen, so none of the many writers that
     * `copy()` this state can leave it behind.
     */
    val pricing: MarketPricing = MarketPricing.NONE
) : ScreenState {

    /**
     * What [article] costs here, and why.
     *
     * Every price the buyer is shown goes through this, so the shelf, the hero card,
     * the favourites list and the detail screen cannot each pick a different market.
     * It returns a [ResolvedPrice] rather than a `Double` on purpose: `resolvePrice`
     * takes `Article.price` as its *base*, so a gross price written back onto an
     * article and resolved a second time would charge the surcharge twice. The type
     * keeps the resolved number from ever being mistaken for a base one.
     */
    fun priceOf(article: Article): ResolvedPrice = resolvePrice(article, pricing)

    /**
     * The catalogue after everything the buyer has asked for, narrowed in one place.
     *
     * Four stages that each only ever remove, applied in a fixed order so the result
     * does not depend on which chip was tapped last:
     *
     *  1. availability — buyers only ever see what can be ordered. The seller list
     *     deliberately does not apply this baseline, which is why it is here and not
     *     in the shared matching.
     *  2. the search query, over name, search terms and category.
     *  3. the favourite axis — bypassed, positive or negative.
     *  4. the category axis.
     *
     * Stages 3 and 4 are independent, so Favoriten + Obst is the favourite fruit.
     */
    val filteredArticles: List<Article>
        get() = articles
            .filter { it.available }
            .filterByQuery(searchQuery)
            .filterByFavourite(filterState.includeFavourite, favouriteArticles)
            .filterByCategory(filterState.category)
}

/**
 * Converts MainScreenState to AsyncState<List<Article>>.
 * Use with AsyncStateContent for consistent loading/error handling.
 */
fun MainScreenState.toArticlesAsyncState(): AsyncState<List<Article>> {
    val currentError = error // Store in local val for smart cast
    return when {
        isLoading && articles.isEmpty() -> AsyncState.Loading
        currentError != null -> AsyncState.Error(
            message = currentError.message,
            retryable = currentError.retryable
        )
        else -> AsyncState.Success(filteredArticles)
    }
}

// ===== Sell-only types =====

data class ProductFormData(
    val name: String = "",
    val price: Double = 0.0,
    val unit: String = "",
    val category: String = "",
    val description: String = "",
    val inStock: Boolean = true,
    val images: List<String> = emptyList()
)

data class CheckoutData(
    val items: List<OrderedProduct> = emptyList(),
    val deliveryAddress: String = "",
    val pickupTime: String = "",
    val paymentMethod: String = "",
    val notes: String = "",
    val promoCode: String = ""
)


/**
 * A slot the buyer picked whose market prices their basket differently, held until
 * they say yes.
 */
data class PendingSlotPriceChange(
    val slot: PickupSlot,
    val changes: List<PriceChange>,
) {
    val increases: Int get() = changes.count { it.isIncrease }
    val total: Double get() = changes.sumOf { it.difference }
}
