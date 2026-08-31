package com.together.newverse.ui.screens.buy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.AccessStatus
import com.together.newverse.domain.model.Invitation
import com.together.newverse.ui.screens.buy.components.ConnectionConfirmDialog
import com.together.newverse.ui.screens.buy.components.DeleteAccountDialog
import com.together.newverse.ui.screens.buy.components.EmailLinkingDialog
import com.together.newverse.ui.screens.buy.components.LinkAccountDialog
import com.together.newverse.ui.screens.buy.components.LoginStatusCard
import com.together.newverse.ui.screens.buy.components.LogoutWarningDialog
import com.together.newverse.ui.screens.buy.components.PendingInvitationsCard
import com.together.newverse.ui.screens.buy.components.TimePickerField
import com.together.newverse.ui.state.AuthProvider
import com.together.newverse.ui.state.BuyAccountAction
import com.together.newverse.ui.state.BuyUserAction
import com.together.newverse.ui.state.BuyAction
import com.together.newverse.ui.state.BuySellerAction
import com.together.newverse.ui.state.ConnectionConfirmation
import com.together.newverse.util.formatString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.a11y_current_mode
import newverse.shared.generated.resources.a11y_state_collapsed
import newverse.shared.generated.resources.a11y_state_expanded
import newverse.shared.generated.resources.a11y_toggle_address
import newverse.shared.generated.resources.access_request_button
import newverse.shared.generated.resources.access_status_approved
import newverse.shared.generated.resources.access_status_blocked
import newverse.shared.generated.resources.access_status_none
import newverse.shared.generated.resources.access_status_pending
import newverse.shared.generated.resources.action_favorites
import newverse.shared.generated.resources.action_orders
import newverse.shared.generated.resources.action_payment
import newverse.shared.generated.resources.about_phone
import newverse.shared.generated.resources.about_email
import newverse.shared.generated.resources.action_help
import newverse.shared.generated.resources.auth_provider_anonymous
import newverse.shared.generated.resources.auth_provider_apple
import newverse.shared.generated.resources.auth_provider_email
import newverse.shared.generated.resources.auth_provider_google
import newverse.shared.generated.resources.auth_provider_twitter
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.button_confirm
import newverse.shared.generated.resources.button_edit
import newverse.shared.generated.resources.button_ok
import newverse.shared.generated.resources.button_save
import newverse.shared.generated.resources.dialog_save_message
import newverse.shared.generated.resources.dialog_save_title
import newverse.shared.generated.resources.error_email_format
import newverse.shared.generated.resources.error_phone_format
import newverse.shared.generated.resources.error_phone_invalid_chars
import newverse.shared.generated.resources.label_display_name
import newverse.shared.generated.resources.label_email
import newverse.shared.generated.resources.label_house_number
import newverse.shared.generated.resources.label_phone
import newverse.shared.generated.resources.label_pickup_time
import newverse.shared.generated.resources.label_pickup_time_hint
import newverse.shared.generated.resources.label_self_pickup
import newverse.shared.generated.resources.label_self_pickup_hint
import newverse.shared.generated.resources.label_street
import newverse.shared.generated.resources.mode_demo
import newverse.shared.generated.resources.mode_production
import newverse.shared.generated.resources.payment_cash_only_info
import newverse.shared.generated.resources.pickup_time_empty
import newverse.shared.generated.resources.pickup_time_format
import newverse.shared.generated.resources.pickup_time_invalid_format
import newverse.shared.generated.resources.pickup_time_outside_hours
import newverse.shared.generated.resources.profile_incomplete_dialog_message
import newverse.shared.generated.resources.profile_incomplete_dialog_title
import newverse.shared.generated.resources.profile_address_optional_self_pickup
import newverse.shared.generated.resources.profile_incomplete_go_to_profile
import newverse.shared.generated.resources.profile_new_customer
import newverse.shared.generated.resources.profile_no_email
import newverse.shared.generated.resources.profile_picture
import newverse.shared.generated.resources.profile_verified
import newverse.shared.generated.resources.quick_actions_title
import newverse.shared.generated.resources.section_delivery_preferences
import newverse.shared.generated.resources.section_personal_info
import newverse.shared.generated.resources.seller_connection_scan_qr
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreenModern(
    state: com.together.newverse.ui.state.CustomerProfileScreenState,
    onAction: (BuyAction) -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    isAnonymous: Boolean = true,
    authProvider: AuthProvider = AuthProvider.ANONYMOUS,
    authProviders: List<AuthProvider> = emptyList(),
    userEmail: String? = null,
    connectedSellerId: String = "",
    connectedSellerDisplayName: String = "",
    isDemoMode: Boolean = true,
    accessStatus: AccessStatus = AccessStatus.NONE,
    buyerUUID: String = "",
    isRequestingAccess: Boolean = false,
    pendingInvitations: List<Invitation> = emptyList(),
    showConnectionConfirmDialog: ConnectionConfirmation? = null,
    onScanQrCode: () -> Unit = {},
    showProfileIncompleteDialog: Boolean = false,
    triggerScrollToAccess: Boolean = false,
    profileViewModel: CustomerProfileViewModel = koinViewModel()
) {
    val profile = state.profile
    val photoUrl = state.photoUrl

    // FormState-based personal info management
    val formState by profileViewModel.formState.collectAsState()
    val isEditingPersonalInfo by profileViewModel.isEditing.collectAsState()
    val displayName = formState.data.displayName
    val email = formState.data.email
    val phone = formState.data.phone
    val street = formState.data.street
    val houseNumber = formState.data.houseNumber

    // Gemüsedate edit state
    val isEditingPickupTime by profileViewModel.isEditingPickupTime.collectAsState()
    val pickupTime by profileViewModel.pickupTime.collectAsState()
    val pickupTimeError by profileViewModel.pickupTimeError.collectAsState()
    val isSelfPickup by profileViewModel.isSelfPickup.collectAsState()

    // Other local state that's not part of the form
    var isEditing by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Logout Warning Dialog
    if (state.showLogoutWarningDialog) {
        LogoutWarningDialog(
            onDismiss = { onAction(BuyAccountAction.DismissLogoutWarning) },
            onConfirmLogout = { onAction(BuyAccountAction.ConfirmGuestLogout) }
        )
    }

    // Link Account Dialog
    if (state.showLinkAccountDialog) {
        LinkAccountDialog(
            onDismiss = { onAction(BuyAccountAction.DismissLinkAccountDialog) },
            onLinkWithGoogle = { onAction(BuyAccountAction.LinkWithGoogle) },
            onLinkWithEmail = { onAction(BuyAccountAction.ShowEmailLinkingDialog) },
            isLinking = state.isLinkingAccount
        )
    }

    // Email Linking Dialog
    if (state.showEmailLinkingDialog) {
        EmailLinkingDialog(
            email = state.emailLinkingEmail,
            password = state.emailLinkingPassword,
            confirmPassword = state.emailLinkingConfirmPassword,
            error = state.emailLinkingError,
            isLinking = state.isLinkingAccount,
            onEmailChange = { onAction(BuyAccountAction.UpdateEmailLinkingEmail(it)) },
            onPasswordChange = { onAction(BuyAccountAction.UpdateEmailLinkingPassword(it)) },
            onConfirmPasswordChange = { onAction(BuyAccountAction.UpdateEmailLinkingConfirmPassword(it)) },
            onConfirm = {
                onAction(
                    BuyAccountAction.LinkWithEmail(
                        email = state.emailLinkingEmail,
                        password = state.emailLinkingPassword
                    )
                )
            },
            onDismiss = { onAction(BuyAccountAction.DismissEmailLinkingDialog) }
        )
    }

    // Delete Account Dialog
    if (state.showDeleteAccountDialog) {
        DeleteAccountDialog(
            isLoading = state.isLoading,
            requiresAppleConfirmation = state.deleteRequiresAppleConfirmation,
            onConfirm = { onAction(BuyAccountAction.ConfirmDeleteAccount) },
            onDismiss = { onAction(BuyAccountAction.DismissDeleteAccountDialog) }
        )
    }

    // Connection Confirmation Dialog
    if (showConnectionConfirmDialog != null) {
        ConnectionConfirmDialog(
            confirmation = showConnectionConfirmDialog,
            onConfirm = { onAction(BuySellerAction.ConfirmConnection) },
            onDismiss = { onAction(BuySellerAction.DismissConnectionDialog) }
        )
    }

    // Profile Incomplete Dialog
    if (showProfileIncompleteDialog) {
        AlertDialog(
            onDismissRequest = { onAction(BuySellerAction.DismissProfileIncompleteDialog) },
            confirmButton = {
                TextButton(
                    onClick = { onAction(BuySellerAction.DismissProfileIncompleteDialog) }
                ) {
                    Text(stringResource(Res.string.profile_incomplete_go_to_profile))
                }
            },
            title = { Text(stringResource(Res.string.profile_incomplete_dialog_title)) },
            text = { Text(stringResource(Res.string.profile_incomplete_dialog_message)) }
        )
    }

    // Scroll to access card if requested, then reset the trigger
    LaunchedEffect(triggerScrollToAccess) {
        if (triggerScrollToAccess) {
            coroutineScope.launch {
                delay(300) // Allow time for layout after navigation
                bringIntoViewRequester.bringIntoView()
                onAction(com.together.newverse.ui.state.BuyNavigationAction.ScrollToAccessInProfileHandled)
            }
        }
    }

    // Follow the profile in both directions. Clearing on null matters as much as
    // filling on load: this ViewModel survives a sign-out, so without the reset
    // the next user is shown the previous one's name, email and address.
    LaunchedEffect(profile) {
        if (profile != null) {
            profileViewModel.initializeFromProfile(profile)
        } else {
            profileViewModel.reset()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                        .padding(bottom = if (isEditing) 80.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Profile Header Card
                    ProfileHeaderCard(
                        displayName = displayName.ifEmpty { stringResource(Res.string.profile_new_customer) },
                        email = email.ifEmpty { stringResource(Res.string.profile_no_email) },
                        photoUrl = photoUrl,
                        isVerified = email.isNotEmpty(),
                        authProvider = authProvider,
                        authProviders = authProviders
                    )

                    // Personal Information Card
                    PersonalInfoCard(
                        displayName = displayName,
                        email = email,
                        phone = phone,
                        street = street,
                        houseNumber = houseNumber,
                        isSelfPickup = isSelfPickup,
                        isEditing = isEditingPersonalInfo,
                        isSubmitting = formState.isSubmitting,
                        emailError = formState.getFieldError(ProfileValidation.FIELD_EMAIL),
                        phoneError = formState.getFieldError(ProfileValidation.FIELD_PHONE),
                        onDisplayNameChange = { profileViewModel.onDisplayNameChange(it) },
                        onEmailChange = { profileViewModel.onEmailChange(it) },
                        onPhoneChange = { profileViewModel.onPhoneChange(it) },
                        onStreetChange = { profileViewModel.onStreetChange(it) },
                        onHouseNumberChange = { profileViewModel.onHouseNumberChange(it) },
                        onEditClick = { profileViewModel.startEditing() },
                        onSaveClick = {
                            profileViewModel.saveProfile()
                        },
                        onCancelClick = {
                            profileViewModel.cancelEditing()
                        }
                    )

                    // Gemüsedate Card
                    GemusedateCard(
                        pickupTime = pickupTime,
                        pickupTimeError = pickupTimeError,
                        isSelfPickup = isSelfPickup,
                        isEditing = isEditingPickupTime,
                        onEditClick = { profileViewModel.startEditingPickupTime() },
                        onSaveClick = { profileViewModel.savePickupTime() },
                        onCancelClick = { profileViewModel.cancelEditingPickupTime() },
                        onPickupTimeChange = { profileViewModel.onPickupTimeChange(it) },
                        onSelfPickupToggle = { profileViewModel.onSelfPickupToggle(it) }
                    )

                    // Demo Mode Card
                    DemoModeCard(isDemoMode = isDemoMode)

                    // Pending Invitations Card
                    PendingInvitationsCard(
                        invitations = pendingInvitations,
                        onAccept = { invitationId ->
                            onAction(BuySellerAction.AcceptPendingInvitation(invitationId))
                        },
                        onReject = { invitationId ->
                            onAction(BuySellerAction.RejectPendingInvitation(invitationId))
                        }
                    )

                    // Access Status Card
                    Box(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
                        AccessStatusCard(
                            accessStatus = accessStatus,
                            buyerUUID = buyerUUID,
                            isRequestingAccess = isRequestingAccess,
                            onRequestAccess = {
                                onAction(BuySellerAction.RequestAccess)
                            },
                            onScanQrCode = onScanQrCode
                        )
                    }

                    // Login Status Card - shows guest warning or authenticated status
                    LoginStatusCard(
                        isAnonymous = isAnonymous,
                        userEmail = userEmail ?: email.ifEmpty { null },
                        authProvider = authProvider,
                        authProviders = authProviders,
                        isLinkingAccount = state.isLinkingAccount,
                        onLinkWithGoogle = { onAction(BuyAccountAction.ShowLinkAccountDialog) },
                        onLinkWithEmail = { onAction(BuyAccountAction.ShowLinkAccountDialog) },
                        onLogout = {
                            if (isAnonymous) {
                                onAction(BuyAccountAction.ShowLogoutWarning)
                            } else {
                                onAction(BuyUserAction.Logout)
                            }
                        },
                        onDeleteAccount = { onAction(BuyAccountAction.ShowDeleteAccountDialog) }
                    )

                    // Quick Actions
                    if (!isEditing) {
                        QuickActionsCard(
                            onNavigateToOrders = onNavigateToOrders,
                            onNavigateToFavorites = onNavigateToFavorites,
                            onNavigateToAbout = onNavigateToAbout
                        )
                    }
                }
            }
        }

        // Bottom Bar for Save/Cancel when editing
        if (isEditing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                SaveBottomBar(
                    onSave = { showSaveDialog = true },
                    onCancel = { isEditing = false }
                )
            }
        }

        // Save Confirmation Dialog
        if (showSaveDialog) {
            SaveConfirmationDialog(
                onConfirm = {
                    showSaveDialog = false
                    isEditing = false
                    // Save logic here
                },
                onDismiss = { showSaveDialog = false }
            )
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    displayName: String,
    email: String,
    photoUrl: String?,
    isVerified: Boolean,
    authProvider: AuthProvider = AuthProvider.ANONYMOUS,
    authProviders: List<AuthProvider> = emptyList()
) {
    // One per linked provider; falls back to the single resolved provider while
    // the list is still loading.
    val badges = authProviders.ifEmpty { listOf(authProvider) }

    // The whole card is read as a single node: name, verification, email, providers.
    val verifiedLabel = stringResource(Res.string.profile_verified)
    val providerLabels = badges.map { authProviderLabel(it) }
    val headerSummary = buildString {
        append(displayName)
        if (isVerified) {
            append(", ")
            append(verifiedLabel)
        }
        append(", ")
        append(email)
        if (providerLabels.isNotEmpty()) {
            append(", ")
            append(providerLabels.joinToString(", "))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring()),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = headerSummary }
        ) {
            // Background pattern
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .size(100.dp)
                        .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    if (!photoUrl.isNullOrEmpty()) {
                        // Use Coil AsyncImage to load profile picture
                        coil3.compose.AsyncImage(
                            model = photoUrl,
                            contentDescription = stringResource(Res.string.profile_picture),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        // Fallback to icon if no photo
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name and verification badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(Res.string.profile_verified),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Auth status badges - one per linked provider. An account can be
                // backed by several (Apple plus a password, say).
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { provider -> AuthProviderBadge(provider) }
                }
            }
        }
    }
}

@Composable
private fun PersonalInfoCard(
    displayName: String,
    email: String,
    phone: String,
    street: String,
    houseNumber: String,
    isSelfPickup: Boolean,
    isEditing: Boolean,
    isSubmitting: Boolean = false,
    emailError: String? = null,
    phoneError: String? = null,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onStreetChange: (String) -> Unit,
    onHouseNumberChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var isAddressExpanded by remember { mutableStateOf(false) }

    // Validation states - use ProfileValidation for real-time validation
    val isEmailValid = ProfileValidation.isValidEmail(email)
    val hasValidPhoneCharacters = ProfileValidation.hasValidPhoneChars(phone)
    val isPhoneValid = hasValidPhoneCharacters && ProfileValidation.isValidPhoneNumber(phone)
    val canSave = isEmailValid && isPhoneValid && !isSubmitting

    // Error messages from FormState or computed
    val emailErrorMessage = stringResource(Res.string.error_email_format)
    val phoneFormatErrorMessage = stringResource(Res.string.error_phone_format)
    val phoneCharsErrorMessage = stringResource(Res.string.error_phone_invalid_chars)

    // Map error keys to localized messages
    val resolvedEmailError = when {
        emailError != null -> emailErrorMessage
        !isEmailValid -> emailErrorMessage
        else -> null
    }
    val resolvedPhoneError = when {
        phoneError == "phone_invalid_chars" -> phoneCharsErrorMessage
        phoneError == "phone_format" -> phoneFormatErrorMessage
        !hasValidPhoneCharacters -> phoneCharsErrorMessage
        !isPhoneValid -> phoneFormatErrorMessage
        else -> null
    }

    // Accessibility labels for the expand/collapse header
    val toggleAddressLabel = stringResource(Res.string.a11y_toggle_address)
    val addressState = stringResource(
        if (isAddressExpanded) Res.string.a11y_state_expanded else Res.string.a11y_state_collapsed
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Section Header with Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    icon = Icons.Default.Person,
                    title = stringResource(Res.string.section_personal_info),
                    iconColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(0.7f)
                        .clickable(
                            onClickLabel = toggleAddressLabel,
                            role = Role.Button
                        ) { isAddressExpanded = !isAddressExpanded }
                        .semantics { stateDescription = addressState }
                )

                if (!isEditing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Visual affordance only; the header above is the a11y toggle
                        Icon(
                            if (isAddressExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier
                                .clickable { isAddressExpanded = !isAddressExpanded }
                                .clearAndSetSemantics { },
                            tint = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.button_edit),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Collapsible content
            Column(modifier = Modifier.animateContentSize(animationSpec = spring())) {
                if (isAddressExpanded || isEditing) {
                    Spacer(modifier = Modifier.height(20.dp))

                    ModernTextField(
                        value = displayName,
                        onValueChange = onDisplayNameChange,
                        label = stringResource(Res.string.label_display_name),
                        leadingIcon = Icons.Default.Person,
                        enabled = isEditing && !isSubmitting,
                        isValid = displayName.isNotEmpty()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ModernTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = stringResource(Res.string.label_email),
                        leadingIcon = Icons.Default.Email,
                        enabled = isEditing && !isSubmitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isValid = isEmailValid,
                        errorMessage = resolvedEmailError
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ModernTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = stringResource(Res.string.label_phone),
                        leadingIcon = Icons.Default.Phone,
                        enabled = isEditing && !isSubmitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isValid = isPhoneValid,
                        errorMessage = resolvedPhoneError
                    )

                    if (isSelfPickup && !isEditing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.profile_address_optional_self_pickup),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ModernTextField(
                        value = street,
                        onValueChange = onStreetChange,
                        label = stringResource(Res.string.label_street),
                        leadingIcon = Icons.Default.LocationOn,
                        enabled = isEditing && !isSubmitting,
                        isValid = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ModernTextField(
                        value = houseNumber,
                        onValueChange = onHouseNumberChange,
                        label = stringResource(Res.string.label_house_number),
                        leadingIcon = Icons.Outlined.LocationOn,
                        enabled = isEditing && !isSubmitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isValid = true
                    )
                }
            }

            // Save and Cancel Buttons (only show when editing)
            if (isEditing) {
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onCancelClick()
                            isAddressExpanded = false
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(Res.string.button_cancel))
                    }

                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.weight(1f),
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.button_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun GemusedateCard(
    pickupTime: String,
    pickupTimeError: String?,
    isSelfPickup: Boolean,
    isEditing: Boolean,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    onPickupTimeChange: (String) -> Unit,
    onSelfPickupToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Section Header with Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    icon = Icons.Default.DateRange,
                    title = stringResource(Res.string.section_delivery_preferences),
                    iconColor = MaterialTheme.colorScheme.tertiary,
                )

                if (!isEditing) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.button_edit),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Pickup Time
            if (isEditing) {
                val isPickupTimeValid = ProfileValidation.isTimeInBusinessHours(pickupTime)
                val errorMessage = when {
                    pickupTimeError != null -> when (pickupTimeError) {
                        "pickup_time_empty" -> stringResource(Res.string.pickup_time_empty)
                        "pickup_time_invalid_format" -> stringResource(Res.string.pickup_time_invalid_format)
                        "pickup_time_outside_hours" -> stringResource(Res.string.pickup_time_outside_hours)
                        else -> null
                    }
                    else -> null
                }

                TimePickerField(
                    value = pickupTime,
                    onValueChange = onPickupTimeChange,
                    label = stringResource(Res.string.label_pickup_time),
                    hint = stringResource(Res.string.label_pickup_time_hint),
                    leadingIcon = Icons.Default.DateRange,
                    enabled = true,
                    isValid = isPickupTimeValid,
                    errorMessage = errorMessage
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        // Label + value read as one node ("Abholzeit, 14:00 Uhr")
                        Column(modifier = Modifier.semantics(mergeDescendants = true) { }) {
                            Text(
                                text = stringResource(Res.string.label_pickup_time),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatString(stringResource(Res.string.pickup_time_format), pickupTime),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selbstabholer toggle — the whole row is one switch so a screen
            // reader hears the label together with the on/off state.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isSelfPickup,
                        enabled = isEditing,
                        role = Role.Switch,
                        onValueChange = onSelfPickupToggle
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.label_self_pickup),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(Res.string.label_self_pickup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSelfPickup,
                    onCheckedChange = null,
                    enabled = isEditing,
                    modifier = Modifier.clearAndSetSemantics { },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary
                    )
                )
            }

            // Save and Cancel Buttons (only show when editing)
            if (isEditing) {
                Spacer(modifier = Modifier.height(20.dp))

                val canSavePickupTime = ProfileValidation.isTimeInBusinessHours(pickupTime)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(Res.string.button_cancel))
                    }

                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.weight(1f),
                        enabled = canSavePickupTime,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.button_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoModeCard(isDemoMode: Boolean) {
    val modeText = if (isDemoMode) {
        stringResource(Res.string.mode_demo)
    } else {
        stringResource(Res.string.mode_production)
    }
    // Read as one node that names what it is ("Modus: …") and speaks up when the
    // mode changes while the screen is open.
    val modeDescription = formatString(stringResource(Res.string.a11y_current_mode), modeText)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = modeDescription
                liveRegion = LiveRegionMode.Polite
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            val modeColor = if (isDemoMode) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (isDemoMode) Icons.Default.Info else Icons.Default.Check,
                    contentDescription = null,
                    tint = modeColor,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = modeText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = modeColor
                    )
                }
            }
        }
    }
}

// ... (rest of the file is unchanged) ...

@Composable
private fun QuickActionsCard(
    onNavigateToOrders: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    var showPaymentDialog by remember { mutableStateOf(false) }

    // Payment info dialog
    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = {
                Text(
                    text = stringResource(Res.string.action_payment),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(Res.string.payment_cash_only_info))
            },
            confirmButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text(stringResource(Res.string.button_ok))
                }
            }
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.quick_actions_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                icon = Icons.AutoMirrored.Default.List,
                text = stringResource(Res.string.action_orders),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            ) { onNavigateToOrders() }

            ActionButton(
                icon = Icons.Outlined.FavoriteBorder,
                text = stringResource(Res.string.action_favorites),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            ) { onNavigateToFavorites() }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                icon = Icons.Default.AccountBox,
                text = stringResource(Res.string.action_payment),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            ) { showPaymentDialog = true }

            ActionButton(
                icon = Icons.Default.Info,
                text = stringResource(Res.string.action_help),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            ) { onNavigateToAbout() }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ContactActionButton(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    var showMenu by remember { mutableStateOf(false) }
    val phoneNumber = stringResource(Res.string.about_phone).filter { it.isDigit() || it == '+' }
    val email = stringResource(Res.string.about_email)

    Box(modifier = modifier) {
        Card(
            onClick = { showMenu = true },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mehr",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("E-Mail")
                    }
                },
                onClick = {
                    uriHandler.openUri("mailto:$email")
                    showMenu = false
                }
            )

            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text("Anrufen")
                    }
                },
                onClick = {
                    uriHandler.openUri("tel:$phoneNumber")
                    showMenu = false
                }
            )
        }
    }
}

@Composable
private fun SaveBottomBar(
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(Res.string.button_cancel))
            }

            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.button_save))
            }
        }
    }
}

@Composable
private fun SaveConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(stringResource(Res.string.button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.button_cancel))
            }
        },
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                stringResource(Res.string.dialog_save_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                stringResource(Res.string.dialog_save_message),
                textAlign = TextAlign.Center
            )
        }
    )
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isValid: Boolean = true,
    errorMessage: String? = null
) {
    val showError = enabled && value.isNotEmpty() && !isValid && errorMessage != null

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = when {
                        showError -> MaterialTheme.colorScheme.error
                        enabled && isValid -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
            },
            trailingIcon = {
                if (enabled && value.isNotEmpty()) {
                    Icon(
                        if (isValid) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isValid) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            enabled = enabled,
            singleLine = true,
            isError = showError,
            // Supporting text is associated with the field, so a screen reader
            // reads the error when the field is focused.
            supportingText = if (showError) {
                { Text(errorMessage) }
            } else null,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun AccessStatusCard(
    accessStatus: AccessStatus,
    buyerUUID: String,
    isRequestingAccess: Boolean = false,
    onRequestAccess: () -> Unit = {},
    onScanQrCode: () -> Unit = {}
) {
    val statusNone = stringResource(Res.string.access_status_none)
    val statusPending = stringResource(Res.string.access_status_pending)
    val statusApproved = stringResource(Res.string.access_status_approved)
    val statusBlocked = stringResource(Res.string.access_status_blocked)

    val (containerColor, contentColor, message) = when (accessStatus) {
        AccessStatus.NONE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            statusNone
        )
        AccessStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            statusPending
        )
        AccessStatus.APPROVED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            statusApproved
        )
        AccessStatus.BLOCKED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            statusBlocked
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (accessStatus) {
                        AccessStatus.APPROVED -> Icons.Filled.CheckCircle
                        AccessStatus.BLOCKED -> Icons.Filled.Close
                        else -> Icons.Filled.Info
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
            if (buyerUUID.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buyerUUID,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            if (accessStatus != AccessStatus.APPROVED) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (accessStatus == AccessStatus.NONE) {
                        Button(
                            onClick = onRequestAccess,
                            enabled = !isRequestingAccess,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isRequestingAccess) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(Res.string.access_request_button))
                        }
                    }
                    OutlinedButton(
                        onClick = onScanQrCode
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBox,
                            contentDescription = stringResource(Res.string.seller_connection_scan_qr)
                        )
                    }
                }
            }
        }
    }
}

/** Localized display name for an auth provider, shared by the badge and the header summary. */
@Composable
private fun authProviderLabel(provider: AuthProvider): String = when (provider) {
    AuthProvider.ANONYMOUS -> stringResource(Res.string.auth_provider_anonymous)
    AuthProvider.GOOGLE -> stringResource(Res.string.auth_provider_google)
    AuthProvider.EMAIL -> stringResource(Res.string.auth_provider_email)
    AuthProvider.TWITTER -> stringResource(Res.string.auth_provider_twitter)
    AuthProvider.APPLE -> stringResource(Res.string.auth_provider_apple)
}

@Composable
private fun AuthProviderBadge(provider: AuthProvider) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when (provider) {
            AuthProvider.ANONYMOUS -> MaterialTheme.colorScheme.errorContainer
            AuthProvider.GOOGLE -> MaterialTheme.colorScheme.primaryContainer
            AuthProvider.EMAIL -> MaterialTheme.colorScheme.secondaryContainer
            AuthProvider.TWITTER -> MaterialTheme.colorScheme.tertiaryContainer
            AuthProvider.APPLE -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = authProviderLabel(provider),
            style = MaterialTheme.typography.labelMedium,
            color = when (provider) {
                AuthProvider.ANONYMOUS -> MaterialTheme.colorScheme.onErrorContainer
                AuthProvider.GOOGLE -> MaterialTheme.colorScheme.onPrimaryContainer
                AuthProvider.EMAIL -> MaterialTheme.colorScheme.onSecondaryContainer
                AuthProvider.TWITTER -> MaterialTheme.colorScheme.onTertiaryContainer
                AuthProvider.APPLE -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
