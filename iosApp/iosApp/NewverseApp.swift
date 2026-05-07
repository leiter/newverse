import SwiftUI
import shared
import FirebaseCore
import FirebaseDatabase
import GoogleSignIn

@main
struct NewverseApp: App {

    init() {
        // Initialize Firebase (required for GitLive SDK)
        FirebaseApp.configure()

        // Enable Firebase persistence for offline support
        // Must be called before any database reference is created
        Database.database().isPersistenceEnabled = true
        print("🔥 NewverseApp: Firebase persistence enabled")

        // Configure Google Sign-In with the Firebase client ID
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            print("🔐 NewverseApp: Google Sign-In configured")
        }

        // Initialize GitLive Firebase SDK
        GitLiveFirebaseInit.shared.initialize()

        // Initialize Koin for dependency injection
        KoinInitializerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Let Google Sign-In handle its own OAuth redirect first
                    if GIDSignIn.sharedInstance.handle(url) { return }
                    // Forward remaining deep links (newverse://) into the Kotlin/Compose layer
                    MainViewControllerKt.handleDeepLinkUrl(url: url.absoluteString)
                }
        }
    }
}
