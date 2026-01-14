import SwiftUI
import shared
import FirebaseCore
import FirebaseDatabase

@main
struct NewverseApp: App {

    init() {
        // Initialize Firebase (required for GitLive SDK)
        FirebaseApp.configure()

        // Enable Firebase persistence for offline support
        // Must be called before any database reference is created
        Database.database().isPersistenceEnabled = true
        print("🔥 NewverseApp: Firebase persistence enabled")

        // Initialize GitLive Firebase SDK
        GitLiveFirebaseInit.shared.initialize()

        // Initialize Koin for dependency injection
        KoinInitializerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
