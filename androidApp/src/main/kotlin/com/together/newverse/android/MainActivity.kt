package com.together.newverse.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.together.newverse.domain.repository.AuthRepository
import com.together.newverse.ui.navigation.AppScaffold
import com.together.newverse.ui.state.UnifiedAppViewModel
import com.together.newverse.ui.theme.NewverseTheme
import com.together.newverse.util.GoogleSignInHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val authRepository: AuthRepository by inject()

    // TODO: Replace with your actual Web Client ID from Firebase Console
    private val webClientId = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.AppTheme)

        enableEdgeToEdge()
        setContent {
            NewverseTheme {
                AppScaffoldWithGoogleSignIn()
            }
        }
    }

    @Composable
    private fun AppScaffoldWithGoogleSignIn() {
        val context = LocalContext.current
        val viewModel: UnifiedAppViewModel = koinInject()
        val googleSignInHelper = GoogleSignInHelper(context, webClientId)

        // Register for Google Sign-In activity result
        val googleSignInLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
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

        // Observe when user wants to sign in with Google
        val state by viewModel.state.collectAsState()

        // Listen for Google Sign-In trigger
        LaunchedEffect(state.common.triggerGoogleSignIn) {
            if (state.common.triggerGoogleSignIn) {
                Log.d("MainActivity", "🔐 Launching Google Sign-In...")
                val signInIntent = googleSignInHelper.getSignInIntent()
                googleSignInLauncher.launch(signInIntent)

                // Reset the trigger
                viewModel.resetGoogleSignInTrigger()
            }
        }

        AppScaffold()
    }
}
