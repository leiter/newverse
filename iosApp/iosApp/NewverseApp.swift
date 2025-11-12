import SwiftUI
import shared

@main
struct NewverseApp: App {

    init() {
        // Initialize Koin for dependency injection
        KoinInitializerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
