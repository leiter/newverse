package com.together.newverse.ui.state

import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.SellerUiAction
import com.together.newverse.domain.model.announcementFor
import com.together.newverse.domain.model.overrideFor
import com.together.newverse.domain.model.promptFor
import com.together.newverse.domain.model.forLocation
import com.together.newverse.domain.model.MarketPricing
import com.together.newverse.domain.model.ResolvedPrice
import com.together.newverse.domain.model.resolvePrice
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.MarketClosures
import com.together.newverse.domain.model.SellerContact
import com.together.newverse.domain.model.SellerContactDirectory
import com.together.newverse.domain.model.Invitation
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderReminderSettings

/**
 * Flattened state for Buy/Customer flavor.
 *
 * Contains only buyer-relevant fields - no dashboard, product creation,
 * or other seller-specific state.
 */
data class BuyAppState(
    // User state
    val user: UserState = UserState.Loading,
    /** Resolved from the auth session, not from the email address. */
    val authProvider: AuthProvider = AuthProvider.ANONYMOUS,
    /** Every provider linked to the account, for the profile badge. */
    val linkedProviders: List<AuthProvider> = emptyList(),
    /**
     * Whether the Firebase user is anonymous, read from the auth session.
     *
     * Defaults to false on purpose: until it resolves, a real account holder
     * must not be shown the guest branch and its account-wiping button.
     */
    val isAnonymousUser: Boolean = false,
    val requiresLogin: Boolean = false,

    // Seller connection
    val connectedSellerId: String = "",
    val connectedSellerDisplayName: String = "",
    val accessStatus: AccessStatus = AccessStatus.NONE,
    val isAccessStatusLoaded: Boolean = false,
    val isRequestingAccess: Boolean = false,
    /**
     * The access request the buyer is composing, or null when the sheet is closed.
     *
     * The request *is* the identity step, so it collects what the seller needs to
     * answer it and nothing else — see [AccessRequestDraft]. Before this, tapping
     * *Zugang anfragen* with an incomplete profile raised a dialog that sent the buyer
     * off to a profile form to satisfy a gate, which is a different thing from asking
     * them a question.
     */
    val accessRequestDraft: AccessRequestDraft? = null,
    val sellerContacts: SellerContactDirectory = SellerContactDirectory(),
    /** The seller's active markets, as the buy app last read them. */
    val sellerMarkets: List<Market> = emptyList(),
    /**
     * Which of them this buyer may collect at, per the seller. Empty means all.
     *
     * Approval says whether they may order; this says where. Read [eligibleMarkets]
     * rather than this — it applies the "empty means all" rule once.
     */
    val allowedMarkets: List<String> = emptyList(),
    /** Days the seller has marked as not happening; filtered out of the pickup dates. */
    val sellerClosures: MarketClosures = MarketClosures.NONE,
    /**
     * Buttons the seller publishes into this app. Read [topBarActionsFor] rather than
     * this, which applies the per-slot ordering and cap.
     */
    val sellerUiActions: List<SellerUiAction> = emptyList(),
    /**
     * Keys of published rows this device has been told to stop showing, read once at
     * startup from [com.together.newverse.data.config.AnnouncementDismissalStorage].
     *
     * Covers every kind of published row, not just announcements: a button the seller
     * marked [com.together.newverse.domain.model.ActionPersistence.HIDE_ON_ACTION]
     * lands here once it has been tapped, and a message box when it is closed or sent
     * through. The key is the row id plus a hash of its text, so re-wording brings it
     * back — see [com.together.newverse.domain.model.SellerUiAction.dismissalKey].
     *
     * In state rather than read from storage at render time so that the top bar does
     * not touch the disk on every recomposition, and so a dismissal takes the row away
     * on the same frame as the tap.
     */
    val dismissedRows: Set<String> = emptySet(),
    /**
     * Where a message the buyer typed into a published box currently stands.
     *
     * One slot of state for both input slots, because a buyer can only be sending
     * from the box they are looking at. See [QuickMessageState].
     */
    val quickMessage: QuickMessageState = QuickMessageState(),

    // Invitation state
    val pendingInvitations: List<Invitation> = emptyList(),
    val showConnectionConfirmDialog: ConnectionConfirmation? = null,

    // Basket
    val basket: BasketState = BasketState(),

    // Navigation
    val navigation: NavigationState = NavigationState(),

    // UI
    val ui: GlobalUiState = GlobalUiState(),

    // Platform sign-in triggers
    val triggerGoogleSignIn: Boolean = false,
    val triggerTwitterSignIn: Boolean = false,
    val triggerAppleSignIn: Boolean = false,
    val triggerGoogleSignOut: Boolean = false,

    // Screen states
    val auth: AuthScreenState = AuthScreenState(),
    val products: ProductsScreenState = ProductsScreenState(),
    val mainScreen: MainScreenState = MainScreenState(),
    val basketScreen: BasketScreenState = BasketScreenState(),
    val customerProfile: CustomerProfileScreenState = CustomerProfileScreenState(),
    /**
     * The buyer's order-reminder settings. Device-local, not part of the Firebase
     * profile, so it lives beside [customerProfile] rather than inside it.
     */
    val orderReminder: OrderReminderSettings = OrderReminderSettings(),
    /**
     * True when the reminder is switched on but the system will not show notifications
     * for this app, so the UI can say so instead of silently never reminding anyone.
     */
    val notificationsBlocked: Boolean = false,
    val orderHistory: OrderHistoryScreenState = OrderHistoryScreenState(),

    // Messaging
    val messaging: MessagingScreenState = MessagingScreenState(),
        val unreadMessageCount: Int = 0,

    // Merge dialog for past orders
    val showHistoryMergeDialog: Boolean = false,
    val tappedHistoryOrder: Order? = null,

    // Navigation trigger for navigating to the basket as a top-level destination
    val navigateToBasketAsTopLevel: Boolean = false,

    // Scroll trigger for access card on profile screen
    val triggerScrollToAccessInProfile: Boolean = false,

    /** The market picker is open, either from onboarding or from the profile. */
    val showMarketPicker: Boolean = false,
    /**
     * The buyer closed the onboarding market prompt without answering.
     *
     * Not persisted, so the question comes back next launch. It decides which prices
     * the buyer sees and what their access request asks for, which is too consequential
     * to drop on one dismissal — but it is a prompt, never a wall, so nothing blocks
     * on it either.
     */
    val marketChoiceDeferred: Boolean = false,

    // App metadata
    val meta: AppMetaState = AppMetaState()
) {
    /**
     * Only *label* the buyer as a demo buyer once the real status has been loaded from
     * Firebase, so the UI does not announce demo mode at launch and withdraw it a
     * moment later.
     *
     * Presentation only. Never decide what to **write** from this: it is false while
     * the status is still loading, which reads as "approved" and is the one answer that
     * cannot be taken back. Use [isApproved] for that.
     */
    val isDemoMode: Boolean get() = isAccessStatusLoaded && accessStatus != AccessStatus.APPROVED

    /**
     * Whether this buyer is known to be approved — loaded, and granted.
     *
     * The conservative half of [isDemoMode], and the one every write decision has to
     * ask. An unknown status is not approval: treating it as such let a buyer who
     * reached checkout before the access observer's first emission write a real order
     * into `orders/`, where the seller packs it, with none of the demo containment the
     * rest of the design relies on.
     */
    val isApproved: Boolean get() = isAccessStatusLoaded && accessStatus == AccessStatus.APPROVED

    /**
     * Whether the top-bar contact shortcut is shown.
     *
     * Gated on approval being *known and granted*, not on `!isDemoMode`: that is
     * false while the status is still loading, so it would show the menu at launch
     * and take it away a moment later. Absent until earned, never the reverse.
     *
     * This is friction, not protection — the contacts are readable in the database
     * by any signed-in user, and the About screen shows them to everyone.
     */
    val canContactSeller: Boolean get() = isApproved

    /**
     * The price conditions of the market this buyer is collecting at.
     *
     * [MarketPricing.NONE] whenever no market is settled — which is every
     * single-market seller with no conditions set, so the common case costs
     * nothing and shows nothing.
     */
    val activePricing: MarketPricing
        get() = contextualMarketId
            ?.let { id -> eligibleMarkets.firstOrNull { it.id == id } }
            ?.pricing
            ?: MarketPricing.NONE

    /** What [article] costs here, and why. */
    fun priceOf(article: Article): ResolvedPrice = resolvePrice(article, activePricing)

    /**
     * [mainScreen] with the active market's conditions attached — what every catalogue
     * screen must be given.
     *
     * `mainScreen` is stored state with a dozen writers, so a `pricing` field kept in
     * it would be one `copy()` away from going stale, which is the shape of the bug
     * this fixes: the shelf priced against no market while the basket priced against
     * the chosen one. Derived here instead, so there is exactly one writer and it
     * cannot be forgotten.
     */
    val mainScreenState: MainScreenState get() = mainScreen.copy(pricing = activePricing)

    /**
     * The market the buyer said they collect at, if it is still one they may use.
     *
     * Re-checked against [eligibleMarkets] rather than trusted: the seller can narrow a
     * buyer's scope after they chose, and a stale default would price the catalogue
     * against a stall they can no longer reach.
     */
    val chosenDefaultMarket: Market?
        get() = customerProfile.profile?.defaultMarket
            ?.takeIf { it.isNotEmpty() }
            ?.let { id -> eligibleMarkets.firstOrNull { it.id == id } }

    /**
     * Whether to ask the buyer where they collect.
     *
     * Only worth asking when there is a choice to make and they have not made it. One
     * market needs no question — `contextualMarketId` already resolves to the only one
     * there is — and a seller whose markets have not loaded yet is not a choice either.
     *
     * Left alone while unconnected: the markets belong to a seller, so there is nothing
     * to pick before there is one.
     */
    val needsMarketChoice: Boolean
        get() = connectedSellerId.isNotEmpty() &&
            eligibleMarkets.size > 1 &&
            chosenDefaultMarket == null &&
            !marketChoiceDeferred

    /**
     * The market whose conditions are moving the prices on screen, named for the
     * buyer, or empty when nothing needs saying.
     *
     * Empty for a market with no conditions, which is every seller today: a line
     * reading "Preise für Markt X" where X changes nothing is noise. Only non-empty
     * once a seller actually prices one stall differently.
     */
    val adjustedPricingMarketName: String
        get() = contextualMarketId
            ?.let { id -> eligibleMarkets.firstOrNull { it.id == id } }
            ?.takeIf { it.priceConditions.isNotEmpty() }
            ?.name
            .orEmpty()

    /**
     * The market the basket's order stands against: the slot the buyer picked in this
     * session, or failing that the market the order they are editing was placed at.
     *
     * Both are needed because loading an order leaves [BasketScreenState.selectedMarketId]
     * empty on purpose (see [BasketScreenState.orderMarketId]), so the slot alone says
     * nothing about an order being edited — and it is precisely an edit that has to
     * know whether the stall delivers. Null when neither names a market this buyer may
     * use, which [com.together.newverse.domain.model.Fulfilment.forMarket] reads as
     * "unknown", not as "pickup only".
     */
    fun marketForBasket(): Market? =
        listOf(basketScreen.selectedMarketId, basketScreen.orderMarketId)
            .firstOrNull { it.isNotEmpty() && eligibleMarkets.any { m -> m.id == it } }
            ?.let { id -> eligibleMarkets.firstOrNull { it.id == id } }

    /** The pricing of [marketId], or [MarketPricing.NONE] when it names no eligible market. */
    fun pricingOf(marketId: String): MarketPricing =
        eligibleMarkets.firstOrNull { it.id == marketId }?.pricing ?: MarketPricing.NONE

    /**
     * The revision to snapshot onto an order collected at [marketId].
     *
     * `0L` for a market with no conditions or none we know, which is what an order
     * placed before per-market pricing also carries — in both cases there is no
     * revision to disagree with later.
     */
    fun pricingRevisionOf(marketId: String): Long = pricingOf(marketId).revision

    /**
     * Every basket item's price at [marketId], keyed by `productId`, for
     * `BasketRepository.repriceItems`.
     *
     * Priced against the catalogue entries the basket was built from; an item whose
     * article is no longer in the catalogue is left out rather than guessed at, so its
     * stored price stands.
     */
    fun basketPricesAt(marketId: String): Map<String, ResolvedPrice> {
        val pricing = pricingOf(marketId)
        return basketScreen.items.mapNotNull { item ->
            val article = mainScreen.articles.firstOrNull { it.id == item.productId }
                ?: return@mapNotNull null
            item.productId to resolvePrice(article, pricing)
        }.toMap()
    }

    /**
     * The markets this buyer may actually choose between.
     *
     * A seller's market the buyer is not scoped to is not theirs to order at, so it
     * contributes no slots and cannot become their contextual market.
     */
    val eligibleMarkets: List<Market>
        get() = if (allowedMarkets.isEmpty()) sellerMarkets
        else sellerMarkets.filter { it.id in allowedMarkets }

    /**
     * Where this buyer collects.
     *
     * Resolution order, from `doc/guides/market-pricing-and-access-design.md` §4: the slot
     * selected in the basket, then the buyer's stated default, then the only market
     * there is. Null when the seller runs several and the buyer has not said which,
     * in which case they see the business's contacts and no market's.
     *
     * The basket comes first because it is the market the buyer is acting on right
     * now; a stated default is where they usually go, which is a weaker claim.
     *
     * Each candidate is checked against [eligibleMarkets] rather than taken on
     * trust: a basket slot outlives the market it names, and a default is a stored
     * string that nothing revalidates when the seller retires a stall.
     */
    val contextualMarketId: String?
        get() = listOfNotNull(
            basketScreen.selectedMarketId,
            customerProfile.profile?.defaultMarket,
        ).firstOrNull { id -> id.isNotEmpty() && eligibleMarkets.any { it.id == id } }
            ?: eligibleMarkets.singleOrNull()?.id

    /**
     * The seller's published buttons for one slot, ordered and capped.
     *
     * Not gated on approval, unlike [topBarContacts]: a contact shortcut handed to
     * every anonymous installer is unvetted inbound noise for a small business,
     * whereas a link the seller chose to publish is the thing they want seen, and a
     * button that appears only after approval reads as broken to a new install.
     */
    fun topBarActionsFor(location: ActionLocation): List<SellerUiAction> =
        sellerUiActions
            // A button the seller marked HIDE_ON_ACTION and this buyer has already
            // tapped. Filtered before the cap, like the audience, so a button this
            // buyer is done with does not use up one of the three places.
            .filterNot { it.dismissalKey in dismissedRows }
            .forLocation(
                location = location,
                // Both read the *loaded* status, so a narrowed button stays absent
                // until the app knows which side of the line this buyer is on,
                // rather than appearing at launch and vanishing. See
                // [canContactSeller].
                isApproved = canContactSeller,
                isDemo = isDemoMode
            )

    /**
     * The seller's announcement for the home title, or null — already audience-filtered
     * and already checked against what this device has dismissed.
     *
     * Null covers all three ordinary cases: the seller has published nothing, the
     * announcement is for an audience this buyer is not in, and the buyer has read it
     * and closed it. All three mean the same thing to the top bar, which falls back to
     * the screen's own title.
     */
    val homeTitleAnnouncement: SellerUiAction?
        get() = sellerUiActions
            .announcementFor(
                location = ActionLocation.TOPBAR_HOME_TITLE,
                // The *loaded* status, for the reason [topBarActionsFor] gives: a
                // narrowed announcement must not appear at launch and vanish.
                isApproved = canContactSeller,
                isDemo = isDemoMode
            )
            ?.takeUnless { it.dismissalKey in dismissedRows }

    /**
     * The seller's message box for one title slot, or null — audience-filtered and
     * already checked against what this device has suppressed.
     *
     * The same three ordinary cases as [homeTitleAnnouncement] collapse to null: none
     * published, not for this buyer, and closed by this buyer. A fourth is specific to
     * this slot and deliberate: a box is **not** hidden after a message is sent. A
     * buyer with a second wish should be able to send it, and the seller asked a
     * standing question rather than ran a one-answer poll. Closing it is how a buyer
     * says they are done — see [BuyUiAction.DismissPrompt].
     */
    fun titlePromptFor(location: ActionLocation): SellerUiAction? =
        sellerUiActions
            .promptFor(
                location = location,
                // The *loaded* status, for the reason [topBarActionsFor] gives.
                isApproved = canContactSeller,
                isDemo = isDemoMode
            )
            ?.takeUnless { it.dismissalKey in dismissedRows }

    /**
     * What the seller has put in the home bar's brand-icon seat, or null for the
     * app's own mark and the About page.
     *
     * Null is the ordinary case and is **not** "nothing to draw": the seat is occupied
     * before any seller publishes anything, because it is the place the stand-in
     * basket appears in and the width is spent whether the basket is there or not. So
     * the renderer reads this to decide whether to replace a default, never whether to
     * draw one — see [ActionLocation.TOPBAR_HOME_BRAND_ICON].
     *
     * Audience-filtered on the *loaded* status like every other slot, for the reason
     * [topBarActionsFor] gives. Deliberately **not** filtered against
     * [dismissedRows]: a buyer who taps the brand mark has not asked the top bar to
     * lose an icon, and a seat that could empty would take the basket's place with it.
     * `readyToStore` will not store a hide-on-action row here and the rules refuse
     * one, so there is nothing for a dismissal to key on.
     */
    val homeBrandIconOverride: SellerUiAction?
        get() = sellerUiActions.overrideFor(
            location = ActionLocation.TOPBAR_HOME_BRAND_ICON,
            isApproved = canContactSeller,
            isDemo = isDemoMode
        )

    /** What the top-bar menu offers, already deduplicated. */
    val topBarContacts: List<SellerContact>
        get() = if (!canContactSeller) emptyList()
        else sellerContacts.resolveFor(contextualMarketId)

    /**
     * Whether the profile tab carries an attention badge. It never does.
     *
     * Kept as a named constant rather than deleted at every call site, because the
     * rule it encodes is worth stating: a badge promises a task the buyer can finish,
     * and there is no longer any profile data the app requires of them. The name, the
     * delivery address and the collection time are each asked for at the moment they
     * matter — in the access request sheet, and at checkout — so nothing is left for
     * the profile tab to nag about.
     *
     * `MissingProfileData` and the "Profil unvollständig" banner it fed were deleted
     * with the pickup time's move to checkout. Should some genuinely required field
     * appear later, this is where its badge belongs.
     */
    val showProfileAttentionBadge: Boolean get() = false
}

/**
 * Everything belonging to the signed-in buyer, cleared.
 *
 * Sign-out, the guest wipe and account deletion all need the same set. Keeping
 * it in one place is deliberate: these three paths each used to clear a
 * slightly different subset, and every difference between them showed the next
 * user something of the previous one's - profile, favourites, order history,
 * seller connection.
 *
 * Left alone on purpose: [user], [requiresLogin] and the sign-out triggers,
 * which each caller sets to suit its flow, and the seller's catalogue in
 * [mainScreen] and [products], which is not the buyer's data.
 *
 * [BuyAppState.dismissedRows] is left alone for the same reason, and the
 * decision is worth stating: it is kept per install rather than per user, so a guest
 * who links their account does not get a banner back that they already closed. It
 * says nothing about the buyer, only that this screen has been read.
 */
fun BuyAppState.clearedForSignOut(): BuyAppState = copy(
    authProvider = AuthProvider.ANONYMOUS,
    linkedProviders = emptyList(),
    isAnonymousUser = false,
    connectedSellerId = "",
    connectedSellerDisplayName = "",
    accessStatus = AccessStatus.NONE,
    isAccessStatusLoaded = false,
    isRequestingAccess = false,
    sellerContacts = SellerContactDirectory(),
    sellerMarkets = emptyList(),
    allowedMarkets = emptyList(),
    sellerClosures = MarketClosures.NONE,
    sellerUiActions = emptyList(),
    quickMessage = QuickMessageState(),
    pendingInvitations = emptyList(),
    showConnectionConfirmDialog = null,
    basket = BasketState(),
    auth = AuthScreenState(),
    basketScreen = BasketScreenState(),
    customerProfile = CustomerProfileScreenState(),
    orderHistory = OrderHistoryScreenState(),
    messaging = MessagingScreenState(),
    unreadMessageCount = 0,
    mainScreen = mainScreen.copy(favouriteArticles = emptyList()),
    showHistoryMergeDialog = false,
    tappedHistoryOrder = null,
    navigateToBasketAsTopLevel = false,
    triggerScrollToAccessInProfile = false,
    accessRequestDraft = null,
    showMarketPicker = false,
    marketChoiceDeferred = false
)

/**
 * What the buyer is about to send a seller when asking for access.
 *
 * Two fields, because the seller's decision needs exactly two things: who is this, and
 * why should I recognise them. [name] is pre-filled from the profile when there is one
 * and is the only requirement; [note] is optional and is what actually makes the
 * request answerable (see `AccessRequest.note`).
 *
 * [nameError] is set on submit rather than while typing: telling someone their name is
 * missing before they have had a chance to type it is nagging, not validating.
 */
data class AccessRequestDraft(
    val name: String = "",
    val note: String = "",
    val nameError: Boolean = false,
    /**
     * The invite token this sheet is standing in front of, if any.
     *
     * A buyer arriving by QR link is already authorised, but the access record still
     * carries a name, so they are asked for one the same way — and on submit the token
     * is redeemed instead of a request being sent. Holding it here replaces the old
     * park-the-token-and-raise-a-dialog dance.
     */
    val pendingToken: Pair<String, String>? = null,
) {
    val canSubmit: Boolean get() = name.isNotBlank()
}

/**
 * Data for the connection confirmation dialog.
 */
data class ConnectionConfirmation(
    val invitation: Invitation,
    val sellerDisplayName: String
)
