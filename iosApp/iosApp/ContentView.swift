import SwiftUI
import shared
import GoogleSignIn
import AVFoundation

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    // Apple Sign-In helper instance (Swift native helper)
    private let appleSignInHelper = NativeAppleSignInHelper()

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
            },
            onScanQrCodeRequested: {
                context.coordinator.handleScanQrCode()
            },
            onShareRequested: { text in
                context.coordinator.handleShare(text: text)
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

        // Store references needed for presenting sheets
        context.coordinator.appleSignInHelper = appleSignInHelper
        context.coordinator.registerAppleRevokeHandler()
        context.coordinator.rootViewController = controller

        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator: NSObject, UIGestureRecognizerDelegate {
        private var isKeyboardVisible = false
        var appleSignInHelper: NativeAppleSignInHelper?
        weak var rootViewController: UIViewController?

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
            guard let vc = rootViewController else {
                GoogleSignInHelper.companion.shared.onSignInError(errorMessage: "No root view controller available")
                return
            }
            GIDSignIn.sharedInstance.signIn(withPresenting: vc) { result, error in
                if let error = error {
                    let nsError = error as NSError
                    if nsError.domain == GIDSignInError.errorDomain,
                       nsError.code == GIDSignInError.canceled.rawValue {
                        GoogleSignInHelper.companion.shared.onSignInCancelled()
                    } else {
                        GoogleSignInHelper.companion.shared.onSignInError(errorMessage: error.localizedDescription)
                    }
                    return
                }
                guard let idToken = result?.user.idToken?.tokenString,
                      let accessToken = result?.user.accessToken.tokenString else {
                    GoogleSignInHelper.companion.shared.onSignInError(errorMessage: "No ID token or access token received from Google")
                    return
                }
                print("Google Sign-In succeeded")
                GoogleSignInHelper.companion.shared.onSignInSuccess(idToken: idToken, accessToken: accessToken)
            }
        }

        /// Handles QR code scan request from Kotlin
        func handleScanQrCode() {
            print("QR code scan requested from Kotlin")
            guard let vc = rootViewController else { return }
            let scannerVC = QrScannerViewController { [weak self] scannedValue in
                vc.dismiss(animated: true)
                MainViewControllerKt.handleDeepLinkUrl(url: scannedValue)
            }
            scannerVC.modalPresentationStyle = .fullScreen
            vc.present(scannerVC, animated: true)
        }

        /// Handles native share sheet request from Kotlin
        func handleShare(text: String) {
            print("Share requested from Kotlin: \(text)")
            guard let vc = rootViewController else { return }
            let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
            // iPad requires a sourceView/sourceRect for the popover
            if let popover = activityVC.popoverPresentationController {
                popover.sourceView = vc.view
                popover.sourceRect = CGRect(x: vc.view.bounds.midX, y: vc.view.bounds.midY, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }
            vc.present(activityVC, animated: true)
        }

        /// Registers the Apple token revocation handler that Kotlin calls when an
        /// Apple-backed account is deleted.
        ///
        /// Apple requires the token to be revoked on account deletion (App Store
        /// guideline 5.1.1(v)). This presents Apple's sign-in sheet again, because
        /// the authorization code revocation needs is short lived and cannot be
        /// captured at the original sign-in.
        func registerAppleRevokeHandler() {
            AppleRevokeBridge.shared.setHandler { [weak self] onSuccess, onError in
                guard let helper = self?.appleSignInHelper else {
                    onError("Apple Sign-In Helper not initialized")
                    return
                }
                // Apple's sheet must be presented from the main thread.
                DispatchQueue.main.async {
                    helper.reauthenticateAndRevoke { result in
                        switch result {
                        case .success:
                            print("Apple token revoked")
                            onSuccess()
                        case .failure(let error):
                            print("Apple token revocation failed: \(error.localizedDescription)")
                            onError(error.localizedDescription)
                        }
                    }
                }
            }
        }

        /// Handles Apple Sign-In request from Kotlin
        func handleAppleSignIn() {
            print("Apple Sign-In requested from Kotlin")

            guard let helper = appleSignInHelper else {
                print("Apple Sign-In Helper not available")
                AppleSignInHelper.companion.shared.onSignInError(errorMessage: "Apple Sign-In Helper not initialized")
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
                            AppleSignInHelper.companion.shared.onSignInSuccess(
                                idToken: appleResult.idToken,
                                rawNonce: appleResult.rawNonce,
                                fullName: fullName,
                                email: appleResult.email
                            )
                        },
                        onError: { error in
                            print("Apple Sign-In Firebase auth failed: \(error)")
                            AppleSignInHelper.companion.shared.onSignInError(errorMessage: error)
                        }
                    )

                case .failure(let error):
                    print("Apple Sign-In native flow failed: \(error.localizedDescription)")
                    if let appleError = error as? AppleSignInError,
                       case .userCancelled = appleError {
                        AppleSignInHelper.companion.shared.onSignInCancelled()
                    } else {
                        AppleSignInHelper.companion.shared.onSignInError(errorMessage: error.localizedDescription)
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

// MARK: - QR Code Scanner

class QrScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let onResult: (String) -> Void
    private var captureSession: AVCaptureSession?

    init(onResult: @escaping (String) -> Void) {
        self.onResult = onResult
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError() }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
        addCancelButton()
    }

    private func setupCamera() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device) else {
            dismiss(animated: true)
            return
        }
        let session = AVCaptureSession()
        session.addInput(input)

        let metadataOutput = AVCaptureMetadataOutput()
        session.addOutput(metadataOutput)
        metadataOutput.setMetadataObjectsDelegate(self, queue: .main)
        metadataOutput.metadataObjectTypes = [.qr]

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.frame = view.layer.bounds
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)

        captureSession = session
        DispatchQueue.global(qos: .userInitiated).async { session.startRunning() }
    }

    private func addCancelButton() {
        let button = UIButton(type: .system)
        button.setTitle("Abbrechen", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 17)
        button.tintColor = .white
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        view.addSubview(button)
        NSLayoutConstraint.activate([
            button.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            button.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24)
        ])
    }

    @objc private func cancelTapped() {
        captureSession?.stopRunning()
        dismiss(animated: true)
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue else { return }
        captureSession?.stopRunning()
        onResult(value)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        captureSession?.stopRunning()
    }
}
