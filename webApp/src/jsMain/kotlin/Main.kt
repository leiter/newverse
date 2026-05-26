import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.together.newverse.data.firebase.GitLiveFirebaseInit
import com.together.newverse.initKoin
import com.together.newverse.ui.navigation.MainAppScaffold
import com.together.newverse.ui.navigation.PlatformAction
import com.together.newverse.ui.theme.NewverseTheme
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    GitLiveFirebaseInit.initialize()

    ComposeViewport(document.body!!) {
        NewverseTheme {
            MainAppScaffold(
                onPlatformAction = { action ->
                    println("Web Platform Action: $action")
                    when (action) {
                        is PlatformAction.GoogleSignIn -> println("Google Sign-In not supported on web v1")
                        is PlatformAction.AppleSignIn -> println("Apple Sign-In not supported on web v1")
                        else -> {}
                    }
                }
            )
        }
    }
}
