import SwiftUI
import shared
import FirebaseCore

@main
struct NewverseApp: App {

    init() {
        // Initialize Firebase (required for GitLive SDK)
        FirebaseApp.configure()

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
