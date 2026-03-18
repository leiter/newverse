package com.together.newverse.ui.screens.buy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.together.newverse.ui.state.ConnectionConfirmation
import com.together.newverse.ui.state.AuthProvider
import com.together.newverse.ui.state.BuyAccountAction
import com.together.newverse.ui.state.BuyAction
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.access_request_button
import newverse.shared.generated.resources.action_favorites
import newverse.shared.generated.resources.action_help
import newverse.shared.generated.resources.action_orders
import newverse.shared.generated.resources.action_payment
import newverse.shared.generated.resources.auth_provider_anonymous
import newverse.shared.generated.resources.auth_provider_apple
import newverse.shared.generated.resources.auth_provider_email
import newverse.shared.generated.resources.auth_provider_google
import newverse.shared.generated.resources.auth_provider_twitter
import newverse.shared.generated.resources.error_email_format
import newverse.shared.generated.resources.error_phone_format
import newverse.shared.generated.resources.error_phone_invalid_chars
import newverse.shared.generated.resources.payment_cash_only_info
import newverse.shared.generated.resources.seller_connection_title
import newverse.shared.generated.resources.seller_connection_demo_mode
import newverse.shared.generated.resources.seller_connection_connected_to
import newverse.shared.generated.resources.seller_connection_enter_id
import newverse.shared.generated.resources.seller_connection_connect
import newverse.shared.generated.resources.seller_connection_reset_demo
import newverse.shared.generated.resources.seller_connection_scan_qr
import newverse.shared.generated.resources.button_cancel
import newverse.shared.generated.resources.button_confirm
import newverse.shared.generated.resources.button_edit
import newverse.shared.generated.resources.button_save
import newverse.shared.generated.resources.default_market
import newverse.shared.generated.resources.dialog_save_message
import newverse.shared.generated.resources.dialog_save_title
import newverse.shared.generated.resources.label_display_name
import newverse.shared.generated.resources.label_email
import newverse.shared.generated.resources.label_marketplace
import newverse.shared.generated.resources.label_phone
import newverse.shared.generated.resources.label_pickup_time
import newverse.shared.generated.resources.membership_discount
import newverse.shared.generated.resources.membership_regular
import newverse.shared.generated.resources.notification_newsletter
import newverse.shared.generated.resources.notification_newsletter_desc
import newverse.shared.generated.resources.notification_order_updates
import newverse.shared.generated.resources.notification_push_desc
import newverse.shared.generated.resources.pickup_time_format
import newverse.shared.generated.resources.profile_member_since
import newverse.shared.generated.resources.profile_new_customer
import newverse.shared.generated.resources.profile_no_email
import newverse.shared.generated.resources.profile_picture
import newverse.shared.generated.resources.profile_verified
import newverse.shared.generated.resources.quick_actions_title
import newverse.shared.generated.resources.section_delivery_preferences
import newverse.shared.generated.resources.section_notifications
import newverse.shared.generated.resources.section_personal_info
import org.jetbrains.compose.resources.stringResource
import com.together.newverse.util.formatString

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
    profileViewModel: CustomerProfileViewModel = koinViewModel()
) {
    val defaultMarket = stringResource(Res.string.default_market)
    val profile = state.profile
    val photoUrl = state.photoUrl

    // FormState-based personal info management
    val formState by profileViewModel.formState.collectAsState()
    val isEditingPersonalInfo by profileViewModel.isEditing.collectAsState()
    val displayName = formState.data.displayName
    val email = formState.data.email
    val phone = formState.data.phone

    // Other local state that's not part of the form
    var selectedMarket by remember { mutableStateOf(defaultMarket) }
    var pickupTime by remember { mutableStateOf("15:45") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var newsletterEnabled by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Logout Warning Dialog
    if (state.showLogoutWarningDialog) {
        LogoutWarningDialog(
            onDismiss = { onAction(BuyAccountAction.DismissLogoutWarning) },
            onLinkAccount = {
                onAction(BuyAccountAction.DismissLogoutWarning)
                onAction(BuyAccountAction.ShowLinkAccountDialog)
            },
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
            onConfirm = { onAction(BuyAccountAction.ConfirmDeleteAccount) },
            onDismiss = { onAction(BuyAccountAction.DismissDeleteAccountDialog) }
        )
    }

    // Connection Confirmation Dialog
    if (showConnectionConfirmDialog != null) {
        ConnectionConfirmDialog(
            confirmation = showConnectionConfirmDialog,
            onConfirm = { onAction(com.together.newverse.ui.state.BuySellerAction.ConfirmConnection) },
            onDismiss = { onAction(com.together.newverse.ui.state.BuySellerAction.DismissConnectionDialog) }
        )
    }

    // Load profile when screen is first displayed
    LaunchedEffect(Unit) {
        println("👤 CustomerProfileScreen: Loading customer profile")
        onAction(com.together.newverse.ui.state.BuyProfileAction.LoadCustomerProfile)
    }

    // Initialize FormState when profile loads
    LaunchedEffect(profile) {
        profile?.let {
            profileViewModel.initializeFromProfile(it)
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
                        authProvider = authProvider
                    )

                    // Personal Information Card
                    PersonalInfoCard(
                        displayName = displayName,
                        email = email,
                        phone = phone,
                        isEditing = isEditingPersonalInfo,
                        isSubmitting = formState.isSubmitting,
                        emailError = formState.getFieldError(ProfileValidation.FIELD_EMAIL),
                        phoneError = formState.getFieldError(ProfileValidation.FIELD_PHONE),
                        onDisplayNameChange = { profileViewModel.onDisplayNameChange(it) },
                        onEmailChange = { profileViewModel.onEmailChange(it) },
                        onPhoneChange = { profileViewModel.onPhoneChange(it) },
                        onEditClick = { profileViewModel.startEditing() },
                        onSaveClick = {
                            profileViewModel.saveProfile()
                        },
                        onCancelClick = {
                            profileViewModel.cancelEditing()
                        }
                    )

                    // Delivery Preferences Card
                    DeliveryPreferencesCard(
                        selectedMarket = selectedMarket,
                        pickupTime = pickupTime,
                        isEditing = isEditing,
                    )

                    // Pending Invitations Card
                    PendingInvitationsCard(
                        invitations = pendingInvitations,
                        onAccept = { invitationId ->
                            onAction(com.together.newverse.ui.state.BuySellerAction.AcceptPendingInvitation(invitationId))
                        },
                        onReject = { invitationId ->
                            onAction(com.together.newverse.ui.state.BuySellerAction.RejectPendingInvitation(invitationId))
                        }
                    )

                    // Seller Connection Card
                    SellerConnectionCard(
                        connectedSellerId = connectedSellerId,
                        connectedSellerDisplayName = connectedSellerDisplayName,
                        isDemoMode = isDemoMode,
                        onConnectToSeller = { sellerId ->
                            onAction(com.together.newverse.ui.state.BuySellerAction.ConnectToSeller(sellerId))
                        },
                        onResetToDemo = {
                            onAction(com.together.newverse.ui.state.BuySellerAction.ResetToDemo)
                        },
                        onScanQrCode = onScanQrCode
                    )

                    // Access Status Card
                    AccessStatusCard(
                        accessStatus = accessStatus,
                        buyerUUID = buyerUUID,
                        isRequestingAccess = isRequestingAccess,
                        onRequestAccess = {
                            onAction(com.together.newverse.ui.state.BuySellerAction.RequestAccess)
                        }
                    )

                    // Notification Settings Card - temporarily hidden
                    // NotificationSettingsCard(
                    //     notificationsEnabled = notificationsEnabled,
                    //     newsletterEnabled = newsletterEnabled,
                    //     isEditing = isEditing,
                    //     onNotificationToggle = { notificationsEnabled = it },
                    //     onNewsletterToggle = { newsletterEnabled = it }
                    // )

                    // Membership Card - temporarily hidden
                    // MembershipCard()

                    // Login Status Card - shows guest warning or authenticated status
                    LoginStatusCard(
                        isAnonymous = isAnonymous,
                        userEmail = userEmail ?: email.ifEmpty { null },
                        authProvider = authProvider,
                        isLinkingAccount = state.isLinkingAccount,
                        onLinkWithGoogle = { onAction(BuyAccountAction.ShowLinkAccountDialog) },
                        onLinkWithEmail = { onAction(BuyAccountAction.ShowLinkAccountDialog) },
                        onLogout = {
                            if (isAnonymous) {
                                onAction(BuyAccountAction.ShowLogoutWarning)
                            } else {
                                onAction(com.together.newverse.ui.state.BuyUserAction.Logout)
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
    authProvider: AuthProvider = AuthProvider.ANONYMOUS
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring()),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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

                // Auth status badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (authProvider) {
                        AuthProvider.ANONYMOUS -> MaterialTheme.colorScheme.errorContainer
                        AuthProvider.GOOGLE -> MaterialTheme.colorScheme.primaryContainer
                        AuthProvider.EMAIL -> MaterialTheme.colorScheme.secondaryContainer
                        AuthProvider.TWITTER -> MaterialTheme.colorScheme.tertiaryContainer
                        AuthProvider.APPLE -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (authProvider) {
                            AuthProvider.ANONYMOUS -> stringResource(Res.string.auth_provider_anonymous)
                            AuthProvider.GOOGLE -> stringResource(Res.string.auth_provider_google)
                            AuthProvider.EMAIL -> stringResource(Res.string.auth_provider_email)
                            AuthProvider.TWITTER -> stringResource(Res.string.auth_provider_twitter)
                            AuthProvider.APPLE -> stringResource(Res.string.auth_provider_apple)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when (authProvider) {
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
        }
    }
}

@Composable
private fun PersonalInfoCard(
    displayName: String,
    email: String,
    phone: String,
    isEditing: Boolean,
    isSubmitting: Boolean = false,
    emailError: String? = null,
    phoneError: String? = null,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
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
                    iconColor = MaterialTheme.colorScheme.primary
                )

                if (!isEditing) {
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

            // Save and Cancel Buttons (only show when editing)
            if (isEditing) {
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
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
private fun DeliveryPreferencesCard(
    selectedMarket: String,
    pickupTime: String,
    isEditing: Boolean,
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
            SectionHeader(
                icon = Icons.Default.LocationOn,
                title = stringResource(Res.string.section_delivery_preferences),
                iconColor = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Market Selection
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isEditing) { /* Open market selection */ },
                color = if (isEditing) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (isEditing) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(Res.string.label_marketplace),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedMarket,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (isEditing) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pickup Time
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isEditing) { /* Open time picker */ },
                color = if (isEditing) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (isEditing) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
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
                    if (isEditing) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsCard(
    notificationsEnabled: Boolean,
    newsletterEnabled: Boolean,
    isEditing: Boolean,
    onNotificationToggle: (Boolean) -> Unit,
    onNewsletterToggle: (Boolean) -> Unit
) {
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
            SectionHeader(
                icon = Icons.Default.Notifications,
                title = stringResource(Res.string.section_notifications),
                iconColor = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Order Notifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (notificationsEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.notification_order_updates),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(Res.string.notification_push_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = onNotificationToggle,
                    enabled = isEditing,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Newsletter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = if (newsletterEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.notification_newsletter),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(Res.string.notification_newsletter_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = newsletterEnabled,
                    onCheckedChange = onNewsletterToggle,
                    enabled = isEditing,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary
                    )
                )
            }
        }
    }
}

@Composable
private fun MembershipCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(Res.string.membership_regular),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(Res.string.membership_discount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

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
                    Text("OK")
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
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        if (showError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun SellerConnectionCard(
    connectedSellerId: String,
    connectedSellerDisplayName: String = "",
    isDemoMode: Boolean,
    onConnectToSeller: (String) -> Unit,
    onResetToDemo: () -> Unit,
    onScanQrCode: () -> Unit
) {
    var sellerIdInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(Res.string.seller_connection_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Status
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDemoMode) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Text(
                    text = if (isDemoMode) {
                        stringResource(Res.string.seller_connection_demo_mode)
                    } else {
                        stringResource(Res.string.seller_connection_connected_to, connectedSellerDisplayName.ifEmpty { connectedSellerId })
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = sellerIdInput,
                    onValueChange = { sellerIdInput = it },
                    label = { Text(stringResource(Res.string.seller_connection_enter_id)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = onScanQrCode) {
                            Icon(
                                imageVector = Icons.Default.AccountBox,
                                contentDescription = stringResource(Res.string.seller_connection_scan_qr)
                            )
                        }
                    }
                )
            }

            // Connect button
            Button(
                onClick = {
                    onConnectToSeller(sellerIdInput.trim())
                    sellerIdInput = ""
                },
                enabled = sellerIdInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(Res.string.seller_connection_connect))
            }

            // Reset to demo button (only when not in demo mode)
            if (!isDemoMode) {
                TextButton(
                    onClick = onResetToDemo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.seller_connection_reset_demo))
                }
            }
        }
    }
}

@Composable
private fun AccessStatusCard(
    accessStatus: AccessStatus,
    buyerUUID: String,
    isRequestingAccess: Boolean = false,
    onRequestAccess: () -> Unit = {}
) {
    val (containerColor, contentColor, message) = when (accessStatus) {
        AccessStatus.NONE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "No access token received yet. Ask your seller for a link."
        )
        AccessStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Access request pending — waiting for seller approval"
        )
        AccessStatus.APPROVED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Production mode active"
        )
        AccessStatus.BLOCKED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Access blocked by seller"
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
            if (accessStatus == AccessStatus.NONE) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestAccess,
                    enabled = !isRequestingAccess,
                    modifier = Modifier.fillMaxWidth()
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
        }
    }
}