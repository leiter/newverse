package com.together.newverse.android

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Random

class TestContainerActivity : AppCompatActivity(), FirebaseAuth.AuthStateListener {

    private val authRepository: AuthRepository by inject()
    private val articleRepository: ArticleRepository by inject()

    val testData = TestDataHolder()

    private lateinit var loadIndicator: FrameLayout
    private lateinit var loginButton: MaterialButton
    private lateinit var createAccountButton: MaterialButton
    private lateinit var logoutButton: MaterialButton
    private lateinit var loginGoogleButton: com.google.android.gms.common.SignInButton
    private lateinit var deleteUserButton: MaterialButton
    private lateinit var uploadProductsButton: MaterialButton

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
        uploadProductsButton = findViewById(R.id.upload_products)

        FirebaseAuth.getInstance().addAuthStateListener(this)
        setupClicks()
    }

    private fun setupClicks() {
        loginButton.setOnClickListener {
            loading(true)
            CoroutineScope(Dispatchers.Main).launch {
                authRepository.signInWithEmail(testData.emailAddress, testData.passWord)
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
                authRepository.signUpWithEmail(testData.emailAddress, testData.passWord)
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

        uploadProductsButton.setOnClickListener { uploadProducts() }
    }

    private fun uploadProducts() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not logged in - cannot upload", Toast.LENGTH_SHORT).show()
            return
        }
        loading(true)
        CoroutineScope(Dispatchers.Main).launch {
            var success = 0
            var failed = 0
            for (article in testData.productList) {
                articleRepository.saveArticle(uid, article)
                    .onSuccess { success++ }
                    .onFailure { failed++ }
            }
            loading(false)
            Toast.makeText(
                this@TestContainerActivity,
                "Upload done: $success OK, $failed failed",
                Toast.LENGTH_LONG
            ).show()
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
    var isGoogleAuth: Boolean = false,
    var productList: List<Article> = testArticles
)

val testArticles = listOf(
    Article(
        id = "", productName = "TEST-Mangos Gold", available = true, price = 4.50, unit = "kg",
        category = "Exotisch", searchTerms = "mango,exotisch,obst",
        detailInfo = "Goldene Bio-Mangos aus Peru. Nach Demeter-Vorgaben angebaut.",
        imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp6852941846258768194.tmp?alt=media&token=f4b2a6a2-a8fa-495b-a093-04c269e97abe",
        weightPerPiece = 0.350
    ),
    Article(
        id = "", productName = "TEST-Rote Paprika", available = true, price = 3.90, unit = "kg",
        category = "Gemuese", searchTerms = "paprika,gemuese,rot",
        detailInfo = "Knackige rote Paprika vom Biohof Waldheide.",
        imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp7534516650759375907.tmp?alt=media&token=d474b967-46e9-45b2-8931-336f9c780ee3",
        weightPerPiece = 0.200
    ),
    Article(
        id = "", productName = "TEST-Basilikum Topf", available = true, price = 2.50, unit = "Stueck",
        category = "Kraeuter", searchTerms = "basilikum,kraeuter,topf",
        detailInfo = "Frischer Basilikum im Topf. Bio-Qualitaet aus der Region.",
        imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp274159401886863829.tmp?alt=media&token=e725f46f-5ab3-440c-9586-c04c6e1b7392",
        weightPerPiece = 1.0
    ),
    Article(
        id = "", productName = "TEST-Honigmelone", available = true, price = 3.20, unit = "Stueck",
        category = "Exotisch", searchTerms = "melone,honigmelone,obst",
        detailInfo = "Suesse Honigmelone aus Spanien. Biologisch angebaut.",
        imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp1145260240680560593.tmp?alt=media&token=0670c0da-e260-4d41-b5d8-1a119ea24a64",
        weightPerPiece = 1.0
    ),
    Article(
        id = "", productName = "TEST-Demeter Kartoffeln", available = true, price = 2.80, unit = "kg",
        category = "Kartoffel", searchTerms = "kartoffel,kartoffeln",
        detailInfo = "Festkochende Demeter-Kartoffeln vom Hof Apfeltraum.",
        imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp1576373532957500855.tmp?alt=media&token=81892fcd-c346-479a-8b56-4b56d7ce8381",
        weightPerPiece = 0.060
    )
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
