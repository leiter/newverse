import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    // Apple Sign-In helper instance
    private let appleSignInHelper = AppleSignInHelper()

    func makeUIViewController(context: Context) -> UIViewController {
        // Use the callback-based controller for handling sign-in actions
        let controller = MainViewControllerKt.MainViewControllerWithCallback(
            onGoogleSignInRequested: {
                context.coordinator.handleGoogleSignIn()
            },
            onAppleSignInRequested: {
                context.coordinator.handleAppleSignIn()
            },
            onTwitterSignInRequested: {
                print("Twitter Sign-In not yet implemented on iOS")
            }
        )

        // Add tap gesture recognizer to dismiss keyboard
        let tapGesture = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap)
        )
        tapGesture.delegate = context.coordinator
        tapGesture.cancelsTouchesInView = false
        tapGesture.delaysTouchesBegan = false
        tapGesture.delaysTouchesEnded = false

        controller.view.addGestureRecognizer(tapGesture)

        // Subscribe to keyboard notifications
        context.coordinator.setupKeyboardObservers()

        // Store reference to Apple Sign-In helper
        context.coordinator.appleSignInHelper = appleSignInHelper

        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator: NSObject, UIGestureRecognizerDelegate {
        private var isKeyboardVisible = false
        var appleSignInHelper: AppleSignInHelper?

        func setupKeyboardObservers() {
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(keyboardWillShow),
                name: UIResponder.keyboardWillShowNotification,
                object: nil
            )
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(keyboardWillHide),
                name: UIResponder.keyboardWillHideNotification,
                object: nil
            )
        }

        @objc func keyboardWillShow(_ notification: Foundation.Notification) {
            isKeyboardVisible = true
        }

        @objc func keyboardWillHide(_ notification: Foundation.Notification) {
            isKeyboardVisible = false
        }

        @objc func handleTap() {
            // Only dismiss if keyboard is currently visible
            guard isKeyboardVisible else { return }

            // Dismiss keyboard
            for window in UIApplication.shared.windows {
                window.endEditing(true)
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            return true
        }

        /// Handles Google Sign-In request from Kotlin
        func handleGoogleSignIn() {
            print("Google Sign-In requested from Kotlin")
            // TODO: Implement Google Sign-In using GoogleSignIn SDK
            // For now, notify Kotlin that it's not implemented
            shared.GoogleSignInHelper.shared.onSignInError(errorMessage: "Google Sign-In not yet implemented on iOS")
        }

        /// Handles Apple Sign-In request from Kotlin
        func handleAppleSignIn() {
            print("Apple Sign-In requested from Kotlin")

            guard let helper = appleSignInHelper else {
                print("Apple Sign-In Helper not available")
                shared.AppleSignInHelper.shared.onSignInError(errorMessage: "Apple Sign-In Helper not initialized")
                return
            }

            helper.signIn { [weak self] result in
                switch result {
                case .success(let appleResult):
                    print("Apple Sign-In native flow succeeded")

                    // Format full name if available
                    var fullName: String? = nil
                    if let nameComponents = appleResult.fullName {
                        let formatter = PersonNameComponentsFormatter()
                        fullName = formatter.string(from: nameComponents)
                    }

                    // Complete Firebase authentication via the bridge
                    AppleAuthBridge.shared.completeAppleSignIn(
                        idToken: appleResult.idToken,
                        rawNonce: appleResult.rawNonce,
                        onSuccess: { userId in
                            print("Apple Sign-In Firebase auth succeeded: \(userId)")
                            // Notify Kotlin helper of success
                            shared.AppleSignInHelper.shared.onSignInSuccess(
                                idToken: appleResult.idToken,
                                rawNonce: appleResult.rawNonce,
                                fullName: fullName,
                                email: appleResult.email
                            )
                        },
                        onError: { error in
                            print("Apple Sign-In Firebase auth failed: \(error)")
                            shared.AppleSignInHelper.shared.onSignInError(errorMessage: error)
                        }
                    )

                case .failure(let error):
                    print("Apple Sign-In native flow failed: \(error.localizedDescription)")
                    if let appleError = error as? AppleSignInError,
                       case .userCancelled = appleError {
                        shared.AppleSignInHelper.shared.onSignInCancelled()
                    } else {
                        shared.AppleSignInHelper.shared.onSignInError(errorMessage: error.localizedDescription)
                    }
                }
            }
        }

        deinit {
            NotificationCenter.default.removeObserver(self)
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
