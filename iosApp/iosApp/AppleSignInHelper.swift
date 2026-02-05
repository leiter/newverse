import AuthenticationServices
import CryptoKit
import Foundation

/// Helper class for handling Apple Sign-In on iOS.
/// Uses ASAuthorizationController for native Apple Sign-In flow.
class AppleSignInHelper: NSObject {

    /// Result type for Apple Sign-In
    struct AppleSignInResult {
        let idToken: String
        let rawNonce: String
        let fullName: PersonNameComponents?
        let email: String?
    }

    /// Completion handler type
    typealias CompletionHandler = (Result<AppleSignInResult, Error>) -> Void

    private var completion: CompletionHandler?
    private var currentNonce: String?

    /// Starts the Apple Sign-In flow
    /// - Parameter completion: Callback with the sign-in result
    func signIn(completion: @escaping CompletionHandler) {
        self.completion = completion

        let nonce = randomNonceString()
        currentNonce = nonce

        let appleIDProvider = ASAuthorizationAppleIDProvider()
        let request = appleIDProvider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)

        let authorizationController = ASAuthorizationController(authorizationRequests: [request])
        authorizationController.delegate = self
        authorizationController.presentationContextProvider = self
        authorizationController.performRequests()
    }

    /// Generates a random nonce string for security
    private func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        var randomBytes = [UInt8](repeating: 0, count: length)
        let errorCode = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
        if errorCode != errSecSuccess {
            fatalError("Unable to generate nonce. SecRandomCopyBytes failed with OSStatus \(errorCode)")
        }

        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")

        let nonce = randomBytes.map { byte in
            charset[Int(byte) % charset.count]
        }

        return String(nonce)
    }

    /// Hashes the nonce using SHA256
    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashedData = SHA256.hash(data: inputData)
        let hashString = hashedData.compactMap {
            String(format: "%02x", $0)
        }.joined()

        return hashString
    }
}

// MARK: - ASAuthorizationControllerDelegate
extension AppleSignInHelper: ASAuthorizationControllerDelegate {

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            completion?(.failure(AppleSignInError.invalidCredential))
            return
        }

        guard let nonce = currentNonce else {
            completion?(.failure(AppleSignInError.invalidState))
            return
        }

        guard let appleIDToken = appleIDCredential.identityToken else {
            completion?(.failure(AppleSignInError.missingIdentityToken))
            return
        }

        guard let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
            completion?(.failure(AppleSignInError.unableToSerializeToken))
            return
        }

        let result = AppleSignInResult(
            idToken: idTokenString,
            rawNonce: nonce,
            fullName: appleIDCredential.fullName,
            email: appleIDCredential.email
        )

        completion?(.success(result))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        if let authError = error as? ASAuthorizationError {
            switch authError.code {
            case .canceled:
                completion?(.failure(AppleSignInError.userCancelled))
            case .failed:
                completion?(.failure(AppleSignInError.authorizationFailed(authError.localizedDescription)))
            case .invalidResponse:
                completion?(.failure(AppleSignInError.invalidResponse))
            case .notHandled:
                completion?(.failure(AppleSignInError.notHandled))
            case .unknown:
                completion?(.failure(AppleSignInError.unknown(authError.localizedDescription)))
            case .notInteractive:
                completion?(.failure(AppleSignInError.notInteractive))
            @unknown default:
                completion?(.failure(AppleSignInError.unknown(authError.localizedDescription)))
            }
        } else {
            completion?(.failure(error))
        }
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding
extension AppleSignInHelper: ASAuthorizationControllerPresentationContextProviding {

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else {
            fatalError("No window available for Apple Sign-In presentation")
        }
        return window
    }
}

// MARK: - Errors
enum AppleSignInError: LocalizedError {
    case invalidCredential
    case invalidState
    case missingIdentityToken
    case unableToSerializeToken
    case userCancelled
    case authorizationFailed(String)
    case invalidResponse
    case notHandled
    case notInteractive
    case unknown(String)

    var errorDescription: String? {
        switch self {
        case .invalidCredential:
            return "Invalid Apple credential received"
        case .invalidState:
            return "Invalid state: Nonce was not set"
        case .missingIdentityToken:
            return "Missing identity token from Apple"
        case .unableToSerializeToken:
            return "Unable to serialize token to string"
        case .userCancelled:
            return "User cancelled Apple Sign-In"
        case .authorizationFailed(let message):
            return "Authorization failed: \(message)"
        case .invalidResponse:
            return "Invalid response from Apple"
        case .notHandled:
            return "Authorization not handled"
        case .notInteractive:
            return "Non-interactive authorization is not supported"
        case .unknown(let message):
            return "Unknown error: \(message)"
        }
    }
}
