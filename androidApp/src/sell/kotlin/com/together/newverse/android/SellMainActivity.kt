package com.together.newverse.android

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
import com.together.newverse.android.ui.NotificationServiceControl
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.ui.navigation.AppScaffold
import com.together.newverse.ui.navigation.NavRoutes
import com.together.newverse.ui.navigation.PlatformAction
import com.together.newverse.ui.state.SellAppViewModel
import com.together.newverse.ui.state.SnackbarType
import com.together.newverse.ui.state.SellNavigationAction
import com.together.newverse.ui.state.SellUiAction
import com.together.newverse.ui.theme.NewverseTheme
import com.together.newverse.util.DocumentPicker
import com.together.newverse.util.GoogleSignInHelper
import com.together.newverse.util.ImagePicker
import com.together.newverse.util.LocalDocumentPicker
import com.together.newverse.util.LocalImagePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sell flavor MainActivity
 *
 * Entry point for the seller/vendor app flavor.
 * Uses SellAppViewModel for state management.
 */
class SellMainActivity : ComponentActivity() {

    private val authRepository: AuthRepository by inject()

    // Web Client ID from Firebase Console (google-services.json)
    private val webClientId = "352833414422-4qt81mifve0h0v5pu1em0tnarjmq0j7j.apps.googleusercontent.com"

    // ImagePicker must be initialized before activity is started
    private lateinit var imagePicker: ImagePicker

    // DocumentPicker for importing BNN files
    private lateinit var documentPicker: DocumentPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.AppTheme)

        // Configure Coil ImageLoader with caching
        setupImageLoader()

        // Create ImagePicker BEFORE setContent (required by Activity Result API)
        imagePicker = ImagePicker(this)

        // Create DocumentPicker BEFORE setContent (required by Activity Result API)
        documentPicker = DocumentPicker(this)

        enableEdgeToEdge()
        setContent {
            NewverseTheme {
                // Provide ImagePicker and DocumentPicker to entire app via CompositionLocal
                CompositionLocalProvider(
                    LocalImagePicker provides imagePicker,
                    LocalDocumentPicker provides documentPicker
                ) {
                    AppScaffoldWithGoogleSignIn()
                }
            }
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
        val viewModel: SellAppViewModel = koinViewModel()

        // Register for Google Sign-In activity result
        val googleSignInLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("SellMainActivity", "Google Sign-In result received")

                // Handle the sign-in result
                googleSignInHelper.handleSignInResult(result.data)
                    .onSuccess { idToken ->
                        Log.d("SellMainActivity", "Got ID token, signing in to Firebase...")

                        // Sign in to Firebase with the ID token
                        CoroutineScope(Dispatchers.Main).launch {
                            authRepository.signInWithGoogle(idToken)
                                .onSuccess { userId ->
                                    Log.d("SellMainActivity", "Successfully signed in with Google: $userId")

                                    // Show success message
                                    viewModel.dispatch(SellUiAction.ShowSnackbar(
                                        message = "Signed in successfully",
                                        type = SnackbarType.SUCCESS
                                    ))

                                    // Navigate to home after short delay
                                    delay(500)
                                    viewModel.dispatch(SellNavigationAction.NavigateTo(NavRoutes.Home))
                                }
                                .onFailure { error ->
                                    Log.e("SellMainActivity", "Failed to sign in with Google: ${error.message}")

                                    // Show error message
                                    viewModel.dispatch(SellUiAction.ShowSnackbar(
                                        message = "Sign in failed: ${error.message}",
                                        type = SnackbarType.ERROR
                                    ))
                                }
                        }
                    }
                    .onFailure { error ->
                        Log.e("SellMainActivity", "Failed to get ID token: ${error.message}")

                        // Show error message
                        viewModel.dispatch(SellUiAction.ShowSnackbar(
                            message = "Failed to get ID token: ${error.message}",
                            type = SnackbarType.ERROR
                        ))
                    }
            } else {
                Log.d("SellMainActivity", "Google Sign-In cancelled or failed: ${result.resultCode}")

                // Show error message to user
                val errorMessage = when (result.resultCode) {
                    RESULT_CANCELED -> "Anmeldung abgebrochen"
                    else -> "Google-Anmeldung fehlgeschlagen. Bitte prüfe deine Internetverbindung."
                }

                Log.d("SellMainActivity", "Setting auth error: $errorMessage")
                // Set auth error state so ForcedLoginScreen can display it
                // Use Handler to ensure it runs after compose is ready
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Log.d("SellMainActivity", "Posting auth error to main thread")
                    viewModel.setAuthError(errorMessage)
                    Log.d("SellMainActivity", "Auth error set on main thread")
                }
            }
        }

        // Pass the platform action handler to AppScaffold
        AppScaffold(
            onPlatformAction = { action ->
                when (action) {
                    is PlatformAction.GoogleSignIn -> {
                        try {
                            Log.d("SellMainActivity", "Handling GoogleSignIn action")

                            val signInIntent = googleSignInHelper.getSignInIntent()
                            googleSignInLauncher.launch(signInIntent)
                        } catch (e: Exception) {
                            Log.e("SellMainActivity", "Exception launching Google Sign-In: ${e.message}", e)
                        }
                    }
                    is PlatformAction.GoogleSignOut -> {
                        try {
                            Log.d("SellMainActivity", "Handling GoogleSignOut action")
                            googleSignInHelper.signOut()
                            Log.d("SellMainActivity", "Google Sign-Out completed")
                        } catch (e: Exception) {
                            Log.e("SellMainActivity", "Exception during Google Sign-Out: ${e.message}", e)
                        }
                    }
                    is PlatformAction.TwitterSignIn -> {
                        Log.d("SellMainActivity", "Handling TwitterSignIn action")
                        // TODO: Implement Twitter sign-in
                    }
                }
            },
            notificationPlatformContent = {
                NotificationServiceControl()
            }
        )
    }
}
