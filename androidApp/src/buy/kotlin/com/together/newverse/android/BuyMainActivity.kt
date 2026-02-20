package com.together.newverse.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.together.newverse.util.initializeImageLoader
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.ui.navigation.AppScaffold
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.navigation.PlatformAction
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.ui.state.BuyNavigationAction
import com.together.newverse.ui.state.BuySellerAction
import com.together.newverse.ui.state.BuyUiAction
import com.together.newverse.ui.theme.NewverseTheme
import com.together.newverse.util.GoogleSignInHelper
import com.together.newverse.util.ImagePicker
import com.together.newverse.util.LocalImagePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

/**
 * Buy flavor MainActivity
 *
 * Entry point for the buyer/customer app flavor.
 * Uses BuyAppViewModel for state management.
 */
class BuyMainActivity : ComponentActivity() {

    private val authRepository: AuthRepository by inject()

    // Web Client ID from Firebase Console (google-services.json)
    private val webClientId = "352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com"

    // ImagePicker must be initialized before activity is started
    private lateinit var imagePicker: ImagePicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.AppTheme)

        // Configure Coil ImageLoader with caching
        setupImageLoader()

        // Create ImagePicker BEFORE setContent (required by Activity Result API)
        imagePicker = ImagePicker(this)

        enableEdgeToEdge()
        setContent {
            NewverseTheme {
                // Provide ImagePicker to entire app via CompositionLocal
                CompositionLocalProvider(LocalImagePicker provides imagePicker) {
                    AppScaffoldWithGoogleSignIn()
                }
            }
        }

        // Handle deep link if app was launched via one
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "newverse" && uri.host == "connect") {
            val sellerId = uri.getQueryParameter("sellerId")
            if (!sellerId.isNullOrBlank()) {
                Log.d("BuyMainActivity", "Deep link received: connect to seller $sellerId")
                val viewModel: BuyAppViewModel by inject()
                viewModel.dispatch(BuySellerAction.ConnectToSeller(sellerId))
            }
        }
    }

    private fun launchQrScanner(viewModel: BuyAppViewModel) {
        val scanner = GmsBarcodeScanning.getClient(this)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue ?: return@addOnSuccessListener
                Log.d("BuyMainActivity", "QR scanned: $rawValue")

                // Try to parse as deep link
                val uri = Uri.parse(rawValue)
                val sellerId = if (uri.scheme == "newverse" && uri.host == "connect") {
                    uri.getQueryParameter("sellerId")
                } else {
                    // Treat raw value as seller ID directly
                    rawValue
                }

                if (!sellerId.isNullOrBlank()) {
                    viewModel.dispatch(BuySellerAction.ConnectToSeller(sellerId))
                }
            }
            .addOnFailureListener { e ->
                Log.e("BuyMainActivity", "QR scan failed: ${e.message}", e)
                viewModel.dispatch(BuyUiAction.ShowSnackbar(
                    message = "QR scan failed: ${e.message}",
                    type = SnackbarType.ERROR
                ))
            }
    }

    /**
     * Configure Coil ImageLoader with disk and memory caching
     * Uses shared multiplatform configuration
     */
    private fun setupImageLoader() {
        initializeImageLoader(this)
    }

    @Composable
    private fun AppScaffoldWithGoogleSignIn() {
        val context = LocalContext.current
        val googleSignInHelper = GoogleSignInHelper(context, webClientId)
        val viewModel: BuyAppViewModel = koinInject()

        // Register for Google Sign-In activity result
        val googleSignInLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("BuyMainActivity", "Google Sign-In result received")

                // Handle the sign-in result
                googleSignInHelper.handleSignInResult(result.data)
                    .onSuccess { idToken ->
                        Log.d("BuyMainActivity", "Got ID token, signing in to Firebase...")

                        // Sign in to Firebase with the ID token
                        CoroutineScope(Dispatchers.Main).launch {
                            authRepository.signInWithGoogle(idToken)
                                .onSuccess { userId ->
                                    Log.d("BuyMainActivity", "Successfully signed in with Google: $userId")

                                    // Show success message
                                    viewModel.dispatch(BuyUiAction.ShowSnackbar(
                                        message = "Signed in successfully",
                                        type = SnackbarType.SUCCESS
                                    ))

                                    // Navigate to home after short delay
                                    delay(500)
                                    viewModel.dispatch(BuyNavigationAction.NavigateTo(NavRoutes.Home))
                                }
                                .onFailure { error ->
                                    Log.e("BuyMainActivity", "Failed to sign in with Google: ${error.message}")

                                    // Show error message
                                    viewModel.dispatch(BuyUiAction.ShowSnackbar(
                                        message = "Sign in failed: ${error.message}",
                                        type = SnackbarType.ERROR
                                    ))
                                }
                        }
                    }
                    .onFailure { error ->
                        Log.e("BuyMainActivity", "Failed to get ID token: ${error.message}")

                        // Show error message
                        viewModel.dispatch(BuyUiAction.ShowSnackbar(
                            message = "Failed to get ID token: ${error.message}",
                            type = SnackbarType.ERROR
                        ))
                    }
            } else {
                Log.d("BuyMainActivity", "Google Sign-In cancelled or failed: ${result.resultCode}")
            }
        }

        // Pass the platform action handler to AppScaffold
        AppScaffold(
            onPlatformAction = { action ->
                when (action) {
                    is PlatformAction.GoogleSignIn -> {
                        try {
                            Log.d("BuyMainActivity", "Handling GoogleSignIn action")

                            val signInIntent = googleSignInHelper.getSignInIntent()
                            googleSignInLauncher.launch(signInIntent)
                        } catch (e: Exception) {
                            Log.e("BuyMainActivity", "Exception launching Google Sign-In: ${e.message}", e)
                        }
                    }
                    is PlatformAction.GoogleSignOut -> {
                        try {
                            Log.d("BuyMainActivity", "Handling GoogleSignOut action")
                            googleSignInHelper.signOut()
                            Log.d("BuyMainActivity", "Google Sign-Out completed")
                        } catch (e: Exception) {
                            Log.e("BuyMainActivity", "Exception during Google Sign-Out: ${e.message}", e)
                        }
                    }
                    is PlatformAction.TwitterSignIn -> {
                        Log.d("BuyMainActivity", "Handling TwitterSignIn action")
                        // TODO: Implement Twitter sign-in
                    }
                    is PlatformAction.AppleSignIn -> {
                        Log.d("BuyMainActivity", "AppleSignIn action ignored on Android")
                        // Apple Sign-In is only available on iOS
                    }
                    is PlatformAction.ScanQrCode -> {
                        Log.d("BuyMainActivity", "Handling ScanQrCode action")
                        launchQrScanner(viewModel)
                    }
                }
            }
        )
    }
}
