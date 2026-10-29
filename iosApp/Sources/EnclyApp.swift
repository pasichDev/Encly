import SwiftUI
import UIKit

/// The app shell: a single scene that shows the Kotlin root view controller (`MainViewController` of the
/// `Shared` framework). Everything the user sees and every security rule is in Kotlin; see `iosApp/README.md`.
@main
struct EnclyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    /// No third-party keyboard in Encly: a custom keyboard is code of another vendor that sees every key the user
    /// types, notes and PIN included. The system keyboard is the only one the app accepts.
    func application(
        _ application: UIApplication,
        shouldAllowExtensionPointIdentifier extensionPointIdentifier: UIApplication.ExtensionPointIdentifier
    ) -> Bool {
        extensionPointIdentifier != .keyboard
    }
}
