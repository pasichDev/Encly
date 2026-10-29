import SwiftUI
import Shared

/// Hosts the Kotlin root view controller. It is created once per scene; the vault, the lock and the privacy
/// cover live in the Kotlin process-wide graph, not in this view.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
