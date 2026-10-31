package com.together.newverse.ui.navigation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import com.together.newverse.domain.model.ActionLocation
import com.together.newverse.ui.components.QuickMessageInput
import com.together.newverse.ui.components.SellerAnnouncementTitle
import com.together.newverse.ui.state.BuyUiAction
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.luminance
import com.together.newverse.ui.theme.LocalBarColors
import com.together.newverse.ui.theme.SystemBarIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.ui.adaptive.LocalWindowWidthClass
import com.together.newverse.ui.adaptive.ProvideWindowWidthClass
import com.together.newverse.ui.adaptive.WindowWidthClass
import com.together.newverse.ui.screens.SplashScreen
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.BuySellerAction
import com.together.newverse.ui.state.DeepLinkRouter
import com.together.newverse.util.formatPrice
import com.together.newverse.util.formatString
import com.together.newverse.util.rememberKeyboardManager
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_basket_item_count
import newverse.shared.generated.resources.nav_messages
import newverse.shared.generated.resources.a11y_contact_options
import newverse.shared.generated.resources.a11y_seller_actions
import newverse.shared.generated.resources.a11y_hero_total
import newverse.shared.generated.resources.action_call
import newverse.shared.generated.resources.action_email
import newverse.shared.generated.resources.action_orders
import newverse.shared.generated.resources.app_name
import newverse.shared.generated.resources.back
import newverse.shared.generated.resources.contacts_title
import newverse.shared.generated.resources.nav_shopping_basket
import org.jetbrains.compose.resources.pluralStringResource
import com.together.newverse.ui.components.displayName
import com.together.newverse.ui.components.icon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.together.newverse.util.Log


private const val TAG = "AppScaffold"

/**
 * Main App Scaffold with Navigation Drawer
 *
 * Provides the overall app structure with:
 * - Navigation drawer
 * - Top app bar with menu button
 * - Navigation graph
 */
@Composable
fun AppScaffold(
    onPlatformAction: (PlatformAction) -> Unit = {}
) {
    ProvideWindowWidthClass {
        AppScaffoldContent(onPlatformAction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffoldContent(
    onPlatformAction: (PlatformAction) -> Unit
) {
    // Get the app ViewModel (BuyAppViewModel in buy flavor, SellAppViewModel in sell flavor)
    val viewModel = koinViewModel<BuyAppViewModel>()
    val appState by viewModel.state.collectAsState()

    // Observe Google Sign-In trigger
    LaunchedEffect(appState.triggerGoogleSignIn) {
        Log.d(TAG) { "AppScaffold: LaunchedEffect triggered, triggerGoogleSignIn=${appState.triggerGoogleSignIn}" }
        if (appState.triggerGoogleSignIn) {
            Log.d(TAG) { "AppScaffold: Calling onPlatformAction(GoogleSignIn)" }
            onPlatformAction(PlatformAction.GoogleSignIn)
            viewModel.resetGoogleSignInTrigger()
        }
    }

    // Observe Twitter Sign-In trigger
    LaunchedEffect(appState.triggerTwitterSignIn) {
        if (appState.triggerTwitterSignIn) {
            Log.d(TAG) { "AppScaffold: Calling onPlatformAction(TwitterSignIn)" }
            onPlatformAction(PlatformAction.TwitterSignIn)
            viewModel.resetTwitterSignInTrigger()
        }
    }

    // Observe Apple Sign-In trigger
    LaunchedEffect(appState.triggerAppleSignIn) {
        if (appState.triggerAppleSignIn) {
            Log.d(TAG) { "AppScaffold: Calling onPlatformAction(AppleSignIn)" }
            onPlatformAction(PlatformAction.AppleSignIn)
            viewModel.resetAppleSignInTrigger()
        }
    }

    // Observe incoming deep link URLs (iOS: forwarded from Swift .onOpenURL via DeepLinkRouter)
    val pendingDeepLink by DeepLinkRouter.pendingUrl.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val url = pendingDeepLink ?: return@LaunchedEffect
        DeepLinkRouter.consume()
        Log.d(TAG) { "AppScaffold: handling deep link: $url" }

        try {
            // Parse query string using Kotlin stdlib only (KMP-compatible)
            val queryStart = url.indexOf('?')
            val queryMap: Map<String, String> = if (queryStart >= 0) {
                url.substring(queryStart + 1).split("&").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx < 0) null else part.substring(0, idx) to part.substring(idx + 1)
                }.toMap()
            } else emptyMap()

            val seller   = queryMap["seller"]   ?: queryMap["sellerId"]
            val token    = queryMap["token"]
            val inviteId = queryMap["inviteId"]
            val expires  = queryMap["expires"]?.toLongOrNull()

            when {
                seller != null && token != null ->
                    viewModel.dispatch(BuySellerAction.ConnectWithToken(seller, token))
                seller != null && inviteId != null && expires != null ->
                    viewModel.dispatch(BuySellerAction.ConnectWithInvitation(seller, inviteId, expires))
                seller != null ->
                    Log.d(TAG) { "AppScaffold: deep link with bare seller ID (no token/invite) ignored: $seller" }
                else ->
                    Log.d(TAG) { "AppScaffold: deep link has no recognised seller param: $url" }
            }
        } catch (e: Exception) {
            Log.e(TAG) { "AppScaffold: failed to parse deep link '$url': ${e.message}" }
        }
    }

    // Observe Google Sign-Out trigger
    LaunchedEffect(appState.triggerGoogleSignOut) {
        if (appState.triggerGoogleSignOut) {
            Log.d(TAG) { "AppScaffold: Calling onPlatformAction(GoogleSignOut)" }
            onPlatformAction(PlatformAction.GoogleSignOut)
            viewModel.resetGoogleSignOutTrigger()
        }
    }

    // Get BasketRepository to observe cart count (actual basket items)
    val basketRepository = koinInject<BasketRepository>()
    val basketItems by basketRepository.observeBasket().collectAsState()

    // How dark the page itself is, for the screens that have no bar behind a
    // system bar. Read from the scheme rather than isSystemInDarkTheme() so a
    // forced NewverseLightTheme/NewverseDarkTheme is still obeyed.
    val darkPage = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val showSplash = appState.meta.isInitializing
    val showAuth = appState.user is com.together.newverse.ui.state.UserState.NotAuthenticated
    val showForcedLogin = appState.requiresLogin

    // Splash and the login screens render outside the Scaffold, so neither the
    // brown top bar nor the bottom bar is behind a system bar there: the icons
    // have to read against the page.
    if (showSplash || showAuth || showForcedLogin) {
        SystemBarIcons(lightStatusIcons = darkPage, lightNavIcons = darkPage)
    }

    // Check if app is still initializing
    if (showSplash) {
        // Show splash screen during initialization
        SplashScreen(
            initializationStep = appState.meta.initializationStep
        )
        return // Exit early, don't show main UI yet
    }

    // Check if user needs to authenticate (no session, show login/register screen)
    if (showAuth) {
        when (appState.auth.mode) {
            com.together.newverse.ui.state.AuthMode.REGISTER -> {
                // Show register screen
                com.together.newverse.ui.screens.common.RegisterScreen(
                    authState = appState.auth,
                    onRegister = { email, pw, name -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.Register(email, pw, name)) },
                    onNavigateToLogin = { viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.SetAuthMode(com.together.newverse.ui.state.AuthMode.LOGIN)) }
                )
            }
            else -> {
                // Default to login screen (LOGIN, FORGOT_PASSWORD, etc.)
                com.together.newverse.ui.screens.common.LoginScreen(
                    authState = appState.auth,
                    onLogin = { email, pw -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.Login(email, pw)) },
                    onLoginWithGoogle = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.LoginWithGoogle) },
                    onLoginWithTwitter = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.LoginWithTwitter) },
                    onLoginWithApple = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.LoginWithApple) },
                    onContinueAsGuest = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.ContinueAsGuest) },
                    onNavigateToRegister = { viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.SetAuthMode(com.together.newverse.ui.state.AuthMode.REGISTER)) },
                    onRequestPasswordReset = { email -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.RequestPasswordReset(email)) },
                    onShowPasswordResetDialog = { viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.ShowPasswordResetDialog) },
                    onHidePasswordResetDialog = { viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.HidePasswordResetDialog) }
                )
            }
        }
        return // Exit early, don't show main UI yet
    }

    // Check if login is required (seller flavor without authentication)
    if (showForcedLogin) {
        // Show forced login screen
        com.together.newverse.ui.screens.common.ForcedLoginScreen(
            authState = appState.auth,
            onLogin = { email, pw -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.Login(email, pw)) },
            onLoginWithGoogle = { viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.LoginWithGoogle) },
            onRequestPasswordReset = { email -> viewModel.dispatch(com.together.newverse.ui.state.BuyUserAction.RequestPasswordReset(email)) }
        )
        return // Exit early, don't show main UI yet
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardManager = rememberKeyboardManager()

    // Observe trigger to navigate to Profile and scroll to the access card
    LaunchedEffect(appState.triggerScrollToAccessInProfile) {
        if (appState.triggerScrollToAccessInProfile) {
            // Perform top-level navigation to profile
            navController.navigate(NavRoutes.Buy.Profile.route) {
                popUpTo(NavRoutes.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // The reset is now handled by the CustomerProfileScreenModern
        }
    }

    // Observe top-level navigation trigger for the basket
    LaunchedEffect(appState.navigateToBasketAsTopLevel) {
        if (appState.navigateToBasketAsTopLevel) {
            // Pop back stack to remove OrderHistoryScreen entirely before navigating
            while (navController.previousBackStackEntry != null &&
                   navController.currentBackStackEntry?.destination?.route != NavRoutes.Home.route) {
                navController.popBackStack()
            }
            navController.navigate(NavRoutes.Buy.Basket.route) {
                launchSingleTop = true
            }
            viewModel.dispatch(com.together.newverse.ui.state.BuyNavigationAction.NavigationToBasketHandled)
        }
    }

    // A refused message, said once.
    //
    // Resolved here because the ViewModel holds it as a StringResource: it has no
    // resource environment to resolve one in, and `getString` throws in a plain JVM
    // test. Cleared after showing, so a recomposition does not announce it again.
    val quickMessageError = appState.quickMessage.error
    val quickMessageErrorText = quickMessageError?.let { stringResource(it) }
    LaunchedEffect(quickMessageError) {
        if (quickMessageErrorText != null) {
            snackbarHostState.showSnackbar(quickMessageErrorText)
            viewModel.dispatch(BuyUiAction.ClearPromptError)
        }
    }

    // Observe snackbar state changes from ViewModel
    LaunchedEffect(appState.ui.snackbar) {
        appState.ui.snackbar?.let { snackbar ->
            snackbarHostState.showSnackbar(
                message = snackbar.message,
                actionLabel = snackbar.actionLabel,
                duration = when (snackbar.duration) {
                    com.together.newverse.ui.state.SnackbarDuration.SHORT -> androidx.compose.material3.SnackbarDuration.Short
                    com.together.newverse.ui.state.SnackbarDuration.LONG -> androidx.compose.material3.SnackbarDuration.Long
                    com.together.newverse.ui.state.SnackbarDuration.INDEFINITE -> androidx.compose.material3.SnackbarDuration.Indefinite
                }
            )
            // Auto-hide snackbar after showing
            viewModel.dispatch(com.together.newverse.ui.state.BuyUiAction.HideSnackbar)
        }
    }

    // Observe navigation state changes from ViewModel
    // DISABLED: This was causing navigation conflicts on iOS when using bottom bar
    // The ViewModel state wasn't syncing with direct navController navigation,
    // causing this LaunchedEffect to re-navigate back to the previous screen
    /*
    LaunchedEffect(appState.navigation.currentRoute) {
        val targetRoute = appState.navigation.currentRoute
        val currentDestination = navController.currentBackStackEntry?.destination?.route

        // Only navigate if we're not already at the target route
        if (targetRoute.route != currentDestination) {
            navController.navigate(targetRoute.route) {
                // Pop up to the start destination to avoid building up a large stack
                popUpTo(NavRoutes.Home.route) {
                    saveState = true
                }
                // Avoid multiple copies of the same destination
                launchSingleTop = true
                // Restore state when reselecting a previously selected item
                restoreState = true
            }
        }
    }
    */

    // Get current route for highlighting in drawer
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.Home.route

    // Container and content for the top bar. Soil brown in both themes; the
    // pair travels together so the title and every tint stay readable on it.
    val topBarColors = LocalBarColors.current

    // Get screen title based on current route
    val defaultAppName = stringResource(Res.string.app_name)
    val screenTitle = remember(currentRoute, appState.basketScreen.total) {
        NavRoutes.getAllRoutes()
            .find { route ->
                // Match routes with or without query parameters
                currentRoute == route.route || currentRoute.startsWith(route.route + "?")
            }
    }
    val isBasketScreen = screenTitle?.route?.startsWith(NavRoutes.Buy.Basket.route) ?: false
    val displayTitle = screenTitle?.let {
        val baseTitle = stringResource(NavRoutes.getDisplayNameRes(it))
        // For basket screen, title is handled separately in TopAppBar with custom Row layout
        baseTitle
    } ?: defaultAppName

    // The seller's published announcement for the home title, if there is one this
    // buyer has not already closed. Resolved here rather than in the title slot so
    // the check reads beside the title it replaces.
    val homeAnnouncement = if (currentRoute == NavRoutes.Home.route) {
        appState.homeTitleAnnouncement
    } else {
        null
    }

    // The seller's message box for this screen, if there is one this buyer has not
    // closed. Resolved beside the announcement because the two compete for the same
    // slot on the home screen, and the tie has to be broken somewhere visible:
    // **the announcement wins.** A sentence the seller is telling every buyer is
    // worth more than a question only some will answer, and the box comes back the
    // moment the announcement is read and closed.
    val titlePrompt = when (currentRoute) {
        NavRoutes.Home.route ->
            if (homeAnnouncement != null) null
            else appState.titlePromptFor(ActionLocation.TOPBAR_HOME_INPUT)
        NavRoutes.Buy.Profile.route ->
            appState.titlePromptFor(ActionLocation.TOPBAR_PROFILE_INPUT)
        else -> null
    }

    // Scroll behavior for collapsing toolbar (only for Home screen)
    // Using exitUntilCollapsedScrollBehavior for snappy hide/show behavior
    val scrollBehavior = if (currentRoute == NavRoutes.Home.route) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        null
    }

    // Determine if bottom bar should be shown (main screens only)
    val showBottomBar = currentRoute == NavRoutes.Home.route ||
        currentRoute.startsWith(NavRoutes.Buy.Basket.route) ||
        currentRoute == NavRoutes.Buy.Profile.route

    // On Expanded windows (tablet landscape) the tabs render as a side rail instead
    val useNavRail = LocalWindowWidthClass.current == WindowWidthClass.Expanded

    // The brown top bar is behind the status bar on every route in here. The
    // navigation bar only has the brown bottom bar behind it when that bar is
    // actually shown — not on a detail screen, and not at Expanded width, where
    // the rail takes the left edge and leaves the bottom to the page.
    SystemBarIcons(
        lightStatusIcons = true,
        lightNavIcons = (showBottomBar && !useNavRail) || darkPage,
    )

    val onNavigateToTab: (String) -> Unit = { route ->
        Log.d(TAG) { "AppScaffold: onNavigate called, route=$route" }
        if (route == NavRoutes.Home.route) {
            // For home (start destination), don't use saveState/restoreState
            // This avoids iOS state restoration issues with the start destination
            Log.d(TAG) { "AppScaffold: Navigating to HOME with inclusive popUpTo" }
            navController.navigate(route) {
                popUpTo(NavRoutes.Home.route) { inclusive = true }
                launchSingleTop = true
            }
            Log.d(TAG) { "AppScaffold: Navigation to HOME completed" }
        } else {
            Log.d(TAG) { "AppScaffold: Navigating to $route with saveState/restoreState" }
            navController.navigate(route) {
                popUpTo(NavRoutes.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            Log.d(TAG) { "AppScaffold: Navigation to $route completed" }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar && !useNavRail) {
                BuyerBottomNavigationBar(
                    currentRoute = currentRoute,
                    basketItemCount = basketItems.size,
                    showProfileBadge = appState.showProfileAttentionBadge,
                    onNavigate = onNavigateToTab
                )
            }
        },
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardManager.hide()
                })
            }
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            ),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    if (isBasketScreen) {
                        val basketCount = appState.basketScreen.items.size
                        // Fragments ("Warenkorb (3)" + "7,50 €") read as one heading.
                        val basketTitleDescription = listOf(
                            stringResource(Res.string.nav_shopping_basket),
                            pluralStringResource(
                                Res.plurals.a11y_basket_item_count,
                                basketCount,
                                basketCount
                            ),
                            formatString(
                                stringResource(Res.string.a11y_hero_total),
                                appState.basketScreen.total.formatPrice()
                            )
                        ).joinToString(", ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    heading()
                                    contentDescription = basketTitleDescription
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.nav_shopping_basket) +  " ($basketCount) ",
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        color = topBarColors.content,
                                        fontSize = 16.sp
                                    )) {
                                        append("${appState.basketScreen.total.formatPrice()} €")
                                    }
                                },
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                    } else if (titlePrompt != null) {
                        // The seller's question takes the title slot and gives it
                        // back when the buyer closes it. Checked before the
                        // announcement only because the announcement already won the
                        // contest for this slot above, where the reasoning sits.
                        QuickMessageInput(
                            prompt = titlePrompt.value,
                            onSend = { text ->
                                viewModel.dispatch(
                                    BuyUiAction.SendPrompt(titlePrompt.location, text)
                                )
                            },
                            onDismiss = {
                                viewModel.dispatch(
                                    BuyUiAction.DismissPrompt(titlePrompt.location)
                                )
                            },
                            isSending = appState.quickMessage.isSending,
                            sentCount = appState.quickMessage.sentCount
                        )
                    } else if (homeAnnouncement != null) {
                        // The seller's announcement takes the whole title slot on the
                        // home screen, and gives it back the moment the buyer closes
                        // it. Only home: the basket and profile titles carry numbers
                        // the buyer is acting on, which an announcement must not hide.
                        SellerAnnouncementTitle(
                            text = homeAnnouncement.value,
                            onDismiss = { viewModel.dispatch(BuyUiAction.DismissHomeAnnouncement) }
                        )
                    } else {
                        Text(
                            text = displayTitle,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                },
                navigationIcon = {
                    // Show back arrow for detail screens that aren't main bottom nav tabs
                    val isDetailScreen = currentRoute == NavRoutes.Buy.OrderHistory.route ||
                        currentRoute == NavRoutes.About.route ||
                        currentRoute == NavRoutes.Register.route ||
                        currentRoute == NavRoutes.Login.route ||
                        currentRoute.startsWith(NavRoutes.Buy.Messages.route) ||
                        currentRoute.startsWith(NavRoutes.Buy.ProductDetail.route) ||
                        currentRoute == NavRoutes.Buy.BuyerContacts.route ||
                        currentRoute == NavRoutes.Buy.AddBuyerContact.route ||
                        currentRoute.startsWith(NavRoutes.Buy.ConversationDetail.route.substringBefore("{")) ||
                        currentRoute == NavRoutes.Buy.Favorites.route

                    if (isDetailScreen) {
                        IconButton(
                            onClick = {
                                viewModel.dispatch(com.together.newverse.ui.state.BuyNavigationAction.NavigateBack)
                                navController.navigateUp()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back)
                            )
                        }
                    }
                },
                actions = {
                    // Show history icon on Basket screen
                    if (isBasketScreen) {
                        IconButton(onClick = {
                            navController.navigate(NavRoutes.Buy.OrderHistory.route) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.List,
                                contentDescription = stringResource(Res.string.action_orders),
                                tint = topBarColors.content
                            )
                        }
                    }
                    // Show contacts icon on Messages screen
                    if (currentRoute == NavRoutes.Buy.Messages.route) {
                        IconButton(onClick = {
                            navController.navigate(NavRoutes.Buy.BuyerContacts.route) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = stringResource(Res.string.contacts_title),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    // Buttons the seller published for this screen.
                    //
                    // Drawn before the fixed actions so the app's own affordances keep
                    // the outer edge: a seller adding a link must not shift the icons a
                    // buyer has learned the position of.
                    //
                    // A single action is its own labelled button, because the label is
                    // the whole point — "Wünsche" says what an icon could not. More
                    // than one collapses into a menu rather than eating the title.
                    // `doc/guides/seller-ui-actions.md`.
                    val publishedActions = when (currentRoute) {
                        NavRoutes.Home.route -> appState.topBarActionsFor(ActionLocation.TOPBAR_HOME)
                        NavRoutes.Buy.Profile.route -> appState.topBarActionsFor(ActionLocation.TOPBAR_PROFILE)
                        else -> emptyList()
                    }
                    if (publishedActions.isNotEmpty()) {
                        val actionUriHandler = LocalUriHandler.current
                        if (publishedActions.size == 1) {
                            val action = publishedActions.first()
                            TextButton(onClick = {
                                actionUriHandler.openUri(action.toUri())
                                // Every tap is reported; only a row the seller marked
                                // hide-on-action actually goes.
                                viewModel.dispatch(
                                    BuyUiAction.NotePublishedActionUsed(action)
                                )
                            }) {
                                Text(
                                    text = action.label,
                                    color = topBarColors.content
                                )
                            }
                        } else {
                            var showActionMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showActionMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(Res.string.a11y_seller_actions),
                                        tint = topBarColors.content
                                    )
                                }
                                DropdownMenu(
                                    expanded = showActionMenu,
                                    onDismissRequest = { showActionMenu = false }
                                ) {
                                    publishedActions.forEach { action ->
                                        DropdownMenuItem(
                                            text = { Text(action.label) },
                                            onClick = {
                                                actionUriHandler.openUri(action.toUri())
                                                viewModel.dispatch(
                                                    BuyUiAction.NotePublishedActionUsed(action)
                                                )
                                                showActionMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // The seller's own contact channels, on Profile and Home.
                    //
                    // Shown only to approved buyers: a one-tap call button handed to
                    // every anonymous installer is unvetted inbound noise for a small
                    // business. Everyone still reaches the same numbers through About.
                    // Hidden entirely when the seller has published none, rather than
                    // opening an empty menu.
                    val contacts = appState.topBarContacts
                    if (contacts.isNotEmpty() &&
                        (currentRoute == NavRoutes.Buy.Profile.route || currentRoute == NavRoutes.Home.route)
                    ) {
                        val uriHandler = LocalUriHandler.current
                        var showContactMenu by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            IconButton(onClick = { showContactMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(Res.string.a11y_contact_options),
                                    tint = topBarColors.content
                                )
                            }

                            DropdownMenu(
                                expanded = showContactMenu,
                                onDismissRequest = { showContactMenu = false }
                            ) {
                                contacts.forEach { contact ->
                                    val label = contact.label.ifBlank { contact.channel.displayName() }
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    contact.channel.icon(),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(label)
                                            }
                                        },
                                        onClick = {
                                            uriHandler.openUri(contact.toUri())
                                            showContactMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // The way into the conversation with the seller, on Profile and
                    // Home beside the contact shortcut.
                    //
                    // Restored because the seller can now start a conversation the
                    // buyer did not ask for — "part of your order is short" — and a
                    // message with no visible way in is a message the seller believes
                    // was read. `doc/guides/stock-driven-availability-design.md` § "Telling a buyer their order is short".
                    //
                    // Gated on approval like the contact shortcut: an unapproved buyer
                    // has no real orders, so nothing to be told about, and a free
                    // channel into a small business from every anonymous installer is
                    // the same inbound noise problem.
//                    if (appState.canContactSeller &&
//                        (currentRoute == NavRoutes.Buy.Profile.route || currentRoute == NavRoutes.Home.route)
//                    ) {
//                        Box(modifier = Modifier.padding(end = 8.dp)) {
//                            IconButton(onClick = {
//                                navController.navigate(NavRoutes.Buy.Messages.route) {
//                                    launchSingleTop = true
//                                }
//                            }) {
//                                Icon(
//                                    imageVector = Icons.Default.Email,
//                                    contentDescription = stringResource(Res.string.nav_messages),
//                                    tint = topBarColors.content
//                                )
//                            }
//                            if (appState.unreadMessageCount > 0) {
//                                Badge(
//                                    containerColor = MaterialTheme.colorScheme.tertiary,
//                                    contentColor = MaterialTheme.colorScheme.onTertiary,
//                                    modifier = Modifier.align(Alignment.TopEnd)
//                                ) {
//                                    Text(
//                                        text = appState.unreadMessageCount.toString(),
//                                        style = MaterialTheme.typography.labelSmall
//                                    )
//                                }
//                            }
//                        }
//                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColors.container,
                    scrolledContainerColor = topBarColors.container,
                    titleContentColor = topBarColors.content,
                    navigationIconContentColor = topBarColors.content,
                    actionIconContentColor = topBarColors.content
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Row(modifier = Modifier.padding(paddingValues)) {
            if (showBottomBar && useNavRail) {
                BuyerNavigationRail(
                    currentRoute = currentRoute,
                    basketItemCount = basketItems.size,
                    showProfileBadge = appState.showProfileAttentionBadge,
                    onNavigate = onNavigateToTab
                )
            }
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.background
            ) {
                NavGraph(
                    navController = navController,
                    appState = appState,
                    onAction = { action -> viewModel.dispatch(action) },
                    onPlatformAction = onPlatformAction,
                )
            }
        }
    }

    // Asked at the scaffold, not on a screen: where the buyer collects decides the
    // prices on the catalogue and in the basket alike, so the question has to reach
    // them wherever onboarding left them rather than waiting for a visit to the
    // profile. `needsMarketChoice` is false for a single-market seller and once the
    // question is answered or deferred, so this is quiet for every seller today.
    // See `doc/guides/market-pricing-and-access-design.md` §2a.
    if (appState.showMarketPicker || appState.needsMarketChoice) {
        com.together.newverse.ui.screens.buy.components.MarketChoiceDialog(
            markets = appState.eligibleMarkets,
            currentMarketId = appState.chosenDefaultMarket?.id.orEmpty(),
            isDemoMode = appState.isDemoMode,
            onChoose = { marketId ->
                viewModel.dispatch(
                    com.together.newverse.ui.state.BuySellerAction.ChooseDefaultMarket(marketId)
                )
            },
            onDismiss = {
                viewModel.dispatch(
                    com.together.newverse.ui.state.BuySellerAction.DismissMarketPicker
                )
            }
        )
    }
}

/**
 * Format order date key (yyyyMMdd) into a readable format for subtitle
 * e.g., "20251127" -> "Do. 27.11."
 * Uses kotlinx.datetime which works on all Android API levels.
 */
private fun formatOrderDateForSubtitle(dateKey: String): String {
    return try {
        if (dateKey.length != 8) return dateKey

        val year = dateKey.substring(0, 4).toInt()
        val month = dateKey.substring(4, 6).toInt()
        val day = dateKey.substring(6, 8).toInt()

        // Calculate day of week manually (no external library needed)
        // Using Zeller's congruence algorithm
        val adjustedMonth = if (month < 3) month + 12 else month
        val adjustedYear = if (month < 3) year - 1 else year
        val q = day
        val k = adjustedYear % 100
        val j = adjustedYear / 100
        val h = (q + (13 * (adjustedMonth + 1)) / 5 + k + k / 4 + j / 4 - 2 * j) % 7
        val dayOfWeekIndex = ((h + 5) % 7) // 0 = Monday, 6 = Sunday

        val dayOfWeek = when (dayOfWeekIndex) {
            0 -> "Mo"
            1 -> "Di"
            2 -> "Mi"
            3 -> "Do"
            4 -> "Fr"
            5 -> "Sa"
            6 -> "So"
            else -> ""
        }

        "$dayOfWeek. $day.$month."
    } catch (e: Exception) {
        dateKey
    }
}
