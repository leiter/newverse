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
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.ui.navigation.AppScaffold
import com.together.newverse.ui.navigation.PlatformAction
import com.together.newverse.ui.theme.NewverseTheme
import com.together.newverse.util.GoogleSignInHelper
import com.together.newverse.util.DocumentPicker
import com.together.newverse.util.ImagePicker
import com.together.newverse.util.LocalDocumentPicker
import com.together.newverse.util.LocalImagePicker
import com.together.newverse.util.initializeImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

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

        // Register for Google Sign-In activity result
        val googleSignInLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("MainActivity", "Google Sign-In result received")

                // Handle the sign-in result
                googleSignInHelper.handleSignInResult(result.data)
                    .onSuccess { idToken ->
                        Log.d("MainActivity", "Got ID token, signing in to Firebase...")

                        // Sign in to Firebase with the ID token
                        CoroutineScope(Dispatchers.Main).launch {
                            authRepository.signInWithGoogle(idToken)
                                .onSuccess { userId ->
                                    Log.d("MainActivity", "✅ Successfully signed in with Google: $userId")
                                }
                                .onFailure { error ->
                                    Log.e("MainActivity", "❌ Failed to sign in with Google: ${error.message}")
                                }
                        }
                    }
                    .onFailure { error ->
                        Log.e("MainActivity", "❌ Failed to get ID token: ${error.message}")
                    }
            } else {
                Log.d("MainActivity", "Google Sign-In cancelled or failed: ${result.resultCode}")
            }
        }

        // Pass the platform action handler to AppScaffold
        AppScaffold(
            onPlatformAction = { action ->
                when (action) {
                    is PlatformAction.GoogleSignIn -> {
                        try {
                            Log.d("MainActivity", "🔐 MainActivity: Handling GoogleSignIn action")
                            Log.d("MainActivity", "🔐 Web Client ID: $webClientId")

                            val signInIntent = googleSignInHelper.getSignInIntent()
                            Log.d("MainActivity", "🔐 Got sign-in intent: $signInIntent")

                            googleSignInLauncher.launch(signInIntent)
                            Log.d("MainActivity", "🔐 Launcher.launch() called")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "❌ Exception launching Google Sign-In: ${e.message}", e)
                        }
                    }
                    is PlatformAction.GoogleSignOut -> {
                        try {
                            Log.d("MainActivity", "🔐 MainActivity: Handling GoogleSignOut action")
                            googleSignInHelper.signOut()
                            Log.d("MainActivity", "🔐 Google Sign-Out completed")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "❌ Exception during Google Sign-Out: ${e.message}", e)
                        }
                    }
                    is PlatformAction.TwitterSignIn -> {
                        Log.d("MainActivity", "🔐 MainActivity: Handling TwitterSignIn action")
                        // TODO: Implement Twitter sign-in
                    }
                    else -> {
                        Log.d("MainActivity", "🔐 Unhandled platform action: $action")
                    }
                }
            }
        )
    }
}
