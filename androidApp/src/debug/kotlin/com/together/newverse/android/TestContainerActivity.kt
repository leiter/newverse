package com.together.newverse.android

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.*

class TestContainerActivity : AppCompatActivity(), FirebaseAuth.AuthStateListener {

    private val authRepository: AuthRepository by inject()

    val testData = TestDataHolder()

    private lateinit var loadIndicator: FrameLayout
    private lateinit var loginButton: MaterialButton
    private lateinit var createAccountButton: MaterialButton
    private lateinit var logoutButton: MaterialButton
    private lateinit var loginGoogleButton: com.google.android.gms.common.SignInButton
    private lateinit var deleteUserButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_container)

        // Initialize views
        loadIndicator = findViewById(R.id.load_indicator)
        loginButton = findViewById(R.id.login)
        createAccountButton = findViewById(R.id.create_account)
        logoutButton = findViewById(R.id.logout)
        loginGoogleButton = findViewById(R.id.login_google)
        deleteUserButton = findViewById(R.id.delete_user)

        FirebaseAuth.getInstance().addAuthStateListener(this)
        setupClicks()
    }

    private fun setupClicks() {
        loginButton.setOnClickListener {
            loading(true)
            CoroutineScope(Dispatchers.Main).launch {
                authRepository.signIn(testData.emailAddress, testData.passWord)
                    .onSuccess {
                        testData.isLoggedIn = true
                        loading(false)
                    }
                    .onFailure {
                        loading(false)
                        Toast.makeText(this@TestContainerActivity, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        createAccountButton.setOnClickListener {
            loading(true)
            CoroutineScope(Dispatchers.Main).launch {
                authRepository.signUp(testData.emailAddress, testData.passWord)
                    .onSuccess {
                        testData.isLoggedIn = true
                        loading(false)
                    }
                    .onFailure {
                        loading(false)
                        Toast.makeText(this@TestContainerActivity, "Account creation failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        logoutButton.setOnClickListener {
            loading(true)
            CoroutineScope(Dispatchers.Main).launch {
                authRepository.signOut()
                testData.isLoggedIn = false
                loading(false)
            }
        }

        deleteUserButton.setOnClickListener {
            loading(true)
            CoroutineScope(Dispatchers.Main).launch {
                authRepository.deleteAccount()
                    .onSuccess {
                        testData.isLoggedIn = false
                        loading(false)
                    }
                    .onFailure {
                        loading(false)
                        Toast.makeText(this@TestContainerActivity, "Delete failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        loginGoogleButton.setOnClickListener {
            loading(true)
            testData.isGoogleAuth = true
            // Google sign-in would be handled via activity result
        }
    }

    fun loading(show: Boolean) {
        loadIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        FirebaseAuth.getInstance().removeAuthStateListener(this)
        super.onDestroy()
    }

    override fun onAuthStateChanged(p0: FirebaseAuth) {
        testData.isLoggedIn = p0.currentUser != null
        loading(false)
    }
}

data class TestDataHolder(
    var emailAddress: String = generateEmail(),
    var passWord: String = "12345678",
    var isLoggedIn: Boolean = false,
    var isGoogleAuth: Boolean = false
)

fun generateEmail(): String {
    val leftLimit = 97
    val rightLimit = 122
    val targetLength = 10
    val random = Random()
    val stringBuilder = StringBuilder()

    for (i in 0 until targetLength) {
        val randomChar = leftLimit +
                (random.nextFloat() * (rightLimit - leftLimit + 1)).toInt()
        stringBuilder.append(randomChar.toChar())
    }
    return stringBuilder.append("@arcor.de").toString()
}
